package app.rigweave.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

enum class DigiMode(val label: String) { CW("CW"), RTTY("RTTY"), SSTV("SSTV") }
enum class DigiTxPhase { SAFE, SEQUENCING, PTT_CONFIRMED }

data class SstvChoice(val index: Int, val label: String, val width: Int, val height: Int)

val SstvChoices = listOf(
    SstvChoice(0, "PD-50", 320, 256), SstvChoice(1, "PD-90", 320, 256),
    SstvChoice(2, "PD-120", 640, 496), SstvChoice(3, "PD-160", 512, 400),
    SstvChoice(4, "PD-180", 640, 496), SstvChoice(5, "PD-240", 640, 496),
    SstvChoice(6, "PD-290", 800, 616), SstvChoice(7, "Robot 24", 160, 120),
    SstvChoice(8, "Robot 36", 320, 240), SstvChoice(9, "Robot 72", 320, 240),
    SstvChoice(10, "Scottie 1", 320, 256), SstvChoice(11, "Scottie 2", 320, 256),
    SstvChoice(12, "Scottie DX", 320, 256), SstvChoice(13, "Martin 1", 320, 256),
    SstvChoice(14, "Martin 2", 320, 256),
)

class DigiController(
    private val context: Context,
    private val routes: AudioMonitorController,
    private val transport: UsbRadioTransport,
    private val flex: FlexRadioController,
    private val radioFamily: () -> RadioFamily,
    stationCallsign: () -> String,
) : AutoCloseable {
    private val prefs = context.getSharedPreferences("rigweave-digi", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val receiving = AtomicBoolean(false)
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private var recorder: AudioRecord? = null
    private var flexRxOwned = false
    private var nativeHandle = 0L
    private var sstvRgb = ByteArray(0)
    private var sstvWidth = 0
    private var sstvHeight = 0
    private var sourceRgb = ByteArray(0)
    private var sourceWidth = 0
    private var sourceHeight = 0

    var mode by mutableStateOf(runCatching { DigiMode.valueOf(prefs.getString("mode", DigiMode.CW.name)!!) }.getOrDefault(DigiMode.CW)); private set
    var status by mutableStateOf("RX stopped · choose a mode and start the DigiRig input"); private set
    var transcript by mutableStateOf(""); private set
    var rxActive by mutableStateOf(false); private set
    var txActive by mutableStateOf(false); private set
    var txPhase by mutableStateOf(DigiTxPhase.SAFE); private set
    var txArmed by mutableStateOf(false); private set
    var txText by mutableStateOf(prefs.getString("tx_text", null) ?: stationCallsign().trim().uppercase().let { call ->
        if (call.isBlank()) "" else "CQ CQ DE $call $call K"
    }); private set
    var cwWpm by mutableIntStateOf(prefs.getInt("cw_wpm", 20)); private set
    var cwPitchHz by mutableFloatStateOf(prefs.getFloat("cw_pitch", 700f)); private set
    var rttyReverse by mutableStateOf(prefs.getBoolean("rtty_reverse", false)); private set
    var rttyAfcHz by mutableFloatStateOf(0f); private set
    var rttyAfcLocked by mutableStateOf(false); private set
    var sstvChoice by mutableStateOf(SstvChoices.firstOrNull { it.index == prefs.getInt("sstv_mode", 2) } ?: SstvChoices[2]); private set
    var sstvLine by mutableIntStateOf(-1); private set
    var sstvComplete by mutableStateOf(false); private set
    var sstvFskId by mutableStateOf(""); private set
    var imageRevision by mutableIntStateOf(0); private set
    var sourceReady by mutableStateOf(false); private set

    fun selectMode(value: DigiMode) {
        if (mode == value) return
        stopRx("Mode changed")
        disarm()
        mode = value
        prefs.edit().putString("mode", value.name).apply()
        transcript = ""
        status = "${value.label} selected · RX is stopped"
    }

    fun selectSstv(value: SstvChoice) {
        if (sstvChoice == value) return
        sstvChoice = value
        prefs.edit().putInt("sstv_mode", value.index).apply()
        sourceReady = false
        sourceRgb = ByteArray(0)
        disarm()
    }

    fun arm() {
        if (txActive) return
        txArmed = !txArmed
        status = if (txArmed) "TX armed for one ${mode.label} transmission · tap SEND to transmit" else "TX disarmed"
    }

    fun disarm() { txArmed = false }

    fun updateTxText(value: String) {
        txText = value.take(240)
        prefs.edit().putString("tx_text", txText).apply()
        disarm()
    }

    fun updateCwWpm(value: Int) {
        cwWpm = value.coerceIn(8, 45)
        prefs.edit().putInt("cw_wpm", cwWpm).apply()
        disarm()
    }

    fun updateCwPitch(value: Float) {
        cwPitchHz = value.coerceIn(400f, 1_000f)
        prefs.edit().putFloat("cw_pitch", cwPitchHz).apply()
        disarm()
    }

    fun updateRttyReverse(value: Boolean) {
        rttyReverse = value
        prefs.edit().putBoolean("rtty_reverse", value).apply()
        disarm()
    }

    fun clear() {
        transcript = ""
        sstvLine = -1
        sstvComplete = false
        sstvFskId = ""
        sstvRgb = ByteArray(0)
        imageRevision++
    }

    fun setSstvSource(bitmap: Bitmap) {
        val target = sstvChoice
        val scaled = Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
        val pixels = IntArray(target.width * target.height)
        scaled.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
        sourceRgb = ByteArray(pixels.size * 3)
        pixels.forEachIndexed { index, argb ->
            sourceRgb[index * 3] = (argb shr 16).toByte()
            sourceRgb[index * 3 + 1] = (argb shr 8).toByte()
            sourceRgb[index * 3 + 2] = argb.toByte()
        }
        sourceWidth = target.width
        sourceHeight = target.height
        sourceReady = true
        scaled.takeIf { it !== bitmap }?.recycle()
        disarm()
        status = "${target.label} source prepared at ${target.width} × ${target.height}"
    }

    fun currentSstvBitmap(): Bitmap? {
        if (sstvWidth <= 0 || sstvHeight <= 0 || sstvRgb.size != sstvWidth * sstvHeight * 3) return null
        val pixels = IntArray(sstvWidth * sstvHeight) { index ->
            val at = index * 3
            (0xff shl 24) or ((sstvRgb[at].toInt() and 0xff) shl 16) or
                ((sstvRgb[at + 1].toInt() and 0xff) shl 8) or (sstvRgb[at + 2].toInt() and 0xff)
        }
        return Bitmap.createBitmap(pixels, sstvWidth, sstvHeight, Bitmap.Config.ARGB_8888)
    }

    @SuppressLint("MissingPermission")
    fun startRx() {
        if (rxActive) return
        if (radioFamily() == RadioFamily.FLEXRADIO) {
            startFlexRx()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is required for DigiRig receive audio"
            return
        }
        routes.refreshDevices()
        val input = routes.selectedRxDevice()
        if (input == null) {
            status = "Select one unambiguous USB RX input in Settings · Audio"
            return
        }
        if (!routes.acquireAudio(AudioOwners.DIGI_RX, pauseMonitor = true)) {
            status = "${routes.audioOwner} owns the selected audio route"
            return
        }
        val rate = listOf(48_000, 44_100, 12_000).firstOrNull {
            AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        }
        if (rate == null) {
            routes.releaseAudio(AudioOwners.DIGI_RX)
            status = "The selected DigiRig input has no compatible mono sample rate"
            return
        }
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum * 4, rate / 2)).build()
        }.getOrElse {
            routes.releaseAudio(AudioOwners.DIGI_RX)
            status = "DigiRig input could not initialize: ${it.message}"
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED || !record.setPreferredDevice(input)) {
            record.release()
            routes.releaseAudio(AudioOwners.DIGI_RX)
            status = "Android could not bind the decoder to the selected USB input"
            return
        }
        AutomaticGainControl.create(record.audioSessionId)?.enabled = false
        NoiseSuppressor.create(record.audioSessionId)?.enabled = false
        AcousticEchoCanceler.create(record.audioSessionId)?.enabled = false
        nativeHandle = NativeCore.digiCreate(12_000, cwPitchHz, rttyReverse)
        if (nativeHandle == 0L) {
            record.release()
            routes.releaseAudio(AudioOwners.DIGI_RX)
            status = "The native modem could not start"
            return
        }
        recorder = record
        receiving.set(true)
        rxActive = true
        record.startRecording()
        status = "${mode.label} RX live · ${routes.inputName} · ${rate / 1000} kHz capture"
        rxJob = scope.launch(Dispatchers.IO) { receiveLoop(record, rate) }
    }

    private fun startFlexRx() {
        nativeHandle = NativeCore.digiCreate(12_000, cwPitchHz, rttyReverse)
        if (nativeHandle == 0L) {
            status = "The native modem could not start"
            return
        }
        val wasEnabled = flex.rxAudioEnabled
        flex.setDigitalRxSink(::receiveFlexPcm)
        if (!wasEnabled && !flex.enableRxAudio()) {
            flex.setDigitalRxSink(null)
            NativeCore.digiDestroy(nativeHandle)
            nativeHandle = 0L
            status = "Flex network RX audio could not start · ${flex.detail}"
            return
        }
        flexRxOwned = !wasEnabled
        receiving.set(true)
        rxActive = true
        status = "${mode.label} RX live · Flex VITA network audio"
    }

    private fun receiveFlexPcm(samples: FloatArray, sampleRate: Int, channels: Int) {
        if (!receiving.get() || nativeHandle == 0L || channels <= 0) return
        val frames = samples.size / channels
        if (frames <= 0) return
        val mono = FloatArray(frames) { frame ->
            var sum = 0f
            repeat(channels) { channel -> sum += samples[frame * channels + channel] }
            sum / channels
        }
        feedNative(resampleFloat12k(mono, sampleRate))
    }

    private fun receiveLoop(record: AudioRecord, rate: Int) {
        val block = ShortArray((rate / 20).coerceAtLeast(512))
        try {
            while (receiving.get()) {
                val count = record.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) error("USB audio read failed: $count")
                val samples = resample12k(block, count, rate)
                feedNative(samples)
            }
        } catch (failure: Throwable) {
            if (receiving.get()) scope.launch { stopRx("RX stopped: ${failure.message}") }
        }
    }

    @Synchronized
    private fun feedNative(samples: FloatArray) {
        if (!receiving.get() || nativeHandle == 0L || samples.isEmpty()) return
        val result = when (mode) {
            DigiMode.CW -> NativeCore.digiFeedCw(nativeHandle, samples)
            DigiMode.RTTY -> NativeCore.digiFeedRtty(nativeHandle, samples)
            DigiMode.SSTV -> NativeCore.digiFeedSstv(nativeHandle, samples)
        }
        scope.launch { applyDecode(result) }
    }

    private fun applyDecode(value: String) {
        val json = runCatching { JSONObject(value) }.getOrNull() ?: return
        when (mode) {
            DigiMode.CW -> {
                transcript = json.optString("text")
                val wpm = json.optInt("wpm")
                status = "CW RX live · ${if (wpm > 0) "$wpm WPM" else "acquiring timing"}"
            }
            DigiMode.RTTY -> {
                transcript = json.optString("text")
                rttyAfcHz = json.optDouble("afcHz").toFloat()
                rttyAfcLocked = json.optBoolean("locked")
                status = "RTTY RX live · AFC ${if (rttyAfcLocked) "locked" else "acquiring"}"
            }
            DigiMode.SSTV -> {
                sstvLine = json.optInt("line", -1)
                sstvComplete = json.optBoolean("complete")
                sstvWidth = json.optInt("width")
                sstvHeight = json.optInt("height")
                sstvFskId = json.optString("fskId")
                if (sstvWidth > 0 && sstvHeight > 0) {
                    sstvRgb = NativeCore.digiSstvImage(nativeHandle)
                    imageRevision++
                }
                status = if (sstvComplete) "SSTV image complete${sstvFskId.takeIf(String::isNotBlank)?.let { " · ID $it" }.orEmpty()}"
                else if (sstvLine >= 0) "SSTV receiving · line ${sstvLine + 1} / $sstvHeight"
                else "SSTV RX live · waiting for VIS header"
            }
        }
    }

    fun stopRx(reason: String = "RX stopped") {
        receiving.set(false)
        flex.setDigitalRxSink(null)
        if (flexRxOwned) flex.disableRxAudio()
        flexRxOwned = false
        rxJob?.cancel()
        rxJob = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        if (nativeHandle != 0L) NativeCore.digiDestroy(nativeHandle)
        nativeHandle = 0
        rxActive = false
        routes.releaseAudio(AudioOwners.DIGI_RX)
        status = reason
    }

    fun send() {
        if (!txArmed || txActive) {
            status = "Arm TX first; arming is valid for one transmission only"
            return
        }
        val text = txText.trim()
        if (mode != DigiMode.SSTV && text.isBlank()) {
            status = "Enter text before transmitting"
            disarm()
            return
        }
        if (mode == DigiMode.SSTV && !sourceReady) {
            status = "Choose an image before SSTV transmit"
            disarm()
            return
        }
        txArmed = false
        txJob = scope.launch { transmit(text) }
    }

    private suspend fun transmit(text: String) {
        stopRx("RX paused for transmit")
        val samples = withContext(Dispatchers.Default) {
            when (mode) {
                DigiMode.CW -> NativeCore.digiEncodeCw(text, cwWpm, cwPitchHz, 48_000)
                DigiMode.RTTY -> NativeCore.digiEncodeRtty(text, 48_000, rttyReverse)
                DigiMode.SSTV -> NativeCore.digiEncodeSstv(sstvChoice.index, sourceRgb, sourceWidth, sourceHeight, 48_000)
            }
        }
        if (samples.isEmpty()) {
            status = "The ${mode.label} encoder rejected this transmission"
            return
        }
        txActive = true
        txPhase = DigiTxPhase.SEQUENCING
        try {
            if (radioFamily() == RadioFamily.FLEXRADIO) transmitFlex(samples) else transmitElecraft(samples)
        } finally {
            txActive = false
            txPhase = DigiTxPhase.SAFE
            routes.releaseAudio(AudioOwners.DIGI_TX)
        }
    }

    private suspend fun transmitFlex(samples: FloatArray) {
        val pcm = CanonicalVoicePcm(ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort() })
        if (!flex.startDigitalTx(pcm)) {
            status = "Flex digital TX refused · enable and arm the Flex transmit interlock first"
            return
        }
        val confirmDeadline = android.os.SystemClock.elapsedRealtime() + 2_000L
        while (flex.tx.state == FlexTxState.KEYING && android.os.SystemClock.elapsedRealtime() < confirmDeadline) delay(50)
        if (flex.tx.state != FlexTxState.TRANSMITTING) {
            flex.stopTransmit("digital PTT confirmation timed out")
            status = "Flex PTT was not confirmed; transmission stopped"
            return
        }
        txPhase = DigiTxPhase.PTT_CONFIRMED
        status = "Flex PTT confirmed · ${mode.label} on air"
        delay(pcm.durationMillis + 600L)
        flex.stopTransmit("digital transmission complete")
        status = "Flex digital TX complete · RX"
    }

    private suspend fun transmitElecraft(samples: FloatArray) {
        routes.refreshDevices()
        val output = routes.selectedTxDevice()
        if (output == null) {
            status = "Select one unambiguous DigiRig TX output in Settings · Audio"
            return
        }
        if (!routes.acquireAudio(AudioOwners.DIGI_TX, pauseMonitor = true)) {
            status = "${routes.audioOwner} owns the selected audio route"
            return
        }
        val minimum = AudioTrack.getMinBufferSize(48_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = runCatching {
            AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum * 4, 48_000)).setTransferMode(AudioTrack.MODE_STREAM).build()
        }.getOrElse {
            status = "DigiRig TX output could not initialize: ${it.message}"
            return
        }
        if (!track.setPreferredDevice(output)) {
            track.release()
            status = "Android could not bind TX audio to the selected DigiRig output"
            return
        }
        try {
            if (transport.send("MD6;") !is UsbResult.Connected) {
                status = "CAT refused DATA mode; transmitter stayed in RX"
                return
            }
            if (transport.send("TX;") !is UsbResult.Connected || transport.confirmTq(true).transmitting != true) {
                transport.send("RX;")
                status = "PTT did not confirm; transmitter returned to RX"
                return
            }
            txPhase = DigiTxPhase.PTT_CONFIRMED
            status = "Elecraft ${mode.label} TX live · ${routes.txOutputName}"
            track.play()
            var offset = 0
            while (offset < samples.size) {
                val count = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                if (count <= 0) error("USB TX audio write failed: $count")
                offset += count
            }
            delay(150)
        } catch (failure: Throwable) {
            status = "Digital TX stopped: ${failure.message}"
        } finally {
            runCatching { track.stop() }
            track.release()
            transport.send("RX;")
            val rx = runCatching { transport.confirmTq(false).transmitting }.getOrNull()
            status = if (rx == false) "Elecraft digital TX complete · RX confirmed"
            else "RX UNCONFIRMED · verify the radio before transmitting again"
        }
    }

    private fun resample12k(input: ShortArray, count: Int, sourceRate: Int): FloatArray {
        if (sourceRate == 12_000) return FloatArray(count) { input[it] / 32768f }
        val outputCount = (count.toLong() * 12_000L / sourceRate).toInt().coerceAtLeast(1)
        return FloatArray(outputCount) { index ->
            val position = index.toDouble() * sourceRate / 12_000.0
            val left = position.toInt().coerceIn(0, count - 1)
            val right = (left + 1).coerceAtMost(count - 1)
            val fraction = (position - left).toFloat()
            ((input[left] * (1f - fraction) + input[right] * fraction) / 32768f)
        }
    }

    private fun resampleFloat12k(input: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == 12_000) return input
        if (sourceRate == 24_000) {
            return FloatArray(input.size / 2) { index ->
                (input[index * 2] + input[index * 2 + 1]) * 0.5f
            }
        }
        val outputCount = (input.size.toLong() * 12_000L / sourceRate).toInt().coerceAtLeast(1)
        return FloatArray(outputCount) { index ->
            val position = index.toDouble() * sourceRate / 12_000.0
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            input[left] * (1f - fraction) + input[right] * fraction
        }
    }

    override fun close() {
        stopRx("Digi closed")
        txJob?.cancel()
        scope.cancel()
    }
}

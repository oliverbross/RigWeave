// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

data class RxDspSettings(
    val noiseBlanker: Boolean = true,
    val automaticNotch: Boolean = false,
    val noiseReduction: Float = .28f,
    val agc: Boolean = true,
    val agcHangMillis: Int = 250,
    val squelchDb: Float = -105f,
    val outputGain: Float = .8f,
    val stereoMix: Float = 0f,
)

class TciRxAudioController(
    private val context: Context,
    private val routes: AudioMonitorController,
) {
    private data class Frame(val receiver: Int, val rate: Int, val samples: FloatArray)
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayBlockingQueue<Frame>(8)
    private val active = AtomicBoolean(false)
    private val lifecycle = LifecycleGeneration()
    private val preferences = context.getSharedPreferences("rigweave-tci-rx-dsp", Context.MODE_PRIVATE)
    private var handle = NativeRxDsp.create()
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var armedReceiver = -1
    private var armedRate = 0
    private var profileKey = "default"
    private var settingsKey = "default|0"

    var settings by mutableStateOf(RxDspSettings()); private set
    var running by mutableStateOf(false); private set
    var status by mutableStateOf("TCI RX audio stopped"); private set
    var inputLevelDb by mutableFloatStateOf(-120f); private set
    var outputLevelDb by mutableFloatStateOf(-120f); private set
    var squelched by mutableStateOf(false); private set
    var clippedFraction by mutableFloatStateOf(0f); private set
    var droppedFrames by mutableStateOf(0L); private set
    var gainReductionDb by mutableFloatStateOf(0f); private set
    var notchFrequencyHz by mutableFloatStateOf(0f); private set
    var blankedImpulses by mutableStateOf(0L); private set
    var processingLatencyMs by mutableFloatStateOf(0f); private set
    var underflowFrames by mutableStateOf(0L); private set

    fun update(value: RxDspSettings) {
        settings = value.copy(
            noiseReduction = value.noiseReduction.coerceIn(0f, 1f),
            agcHangMillis = value.agcHangMillis.coerceIn(0, 2_000),
            squelchDb = value.squelchDb.coerceIn(-120f, -20f),
            outputGain = value.outputGain.coerceIn(0f, 4f),
            stereoMix = value.stereoMix.coerceIn(-1f, 1f),
        )
        persist(settingsKey, settings)
    }

    fun selectProfile(value: String) {
        if (active.get()) stop("TCI RX audio profile changed")
        profileKey = value.take(120).ifBlank { "default" }
        settingsKey = "$profileKey|0"
        settings = load(settingsKey)
    }

    @Synchronized
    fun start(receiver: Int, sampleRate: Int): Boolean {
        if (active.get()) {
            if (receiver == armedReceiver && sampleRate == armedRate) return true
            stop("Receiver or sample rate changed")
        }
        if (sampleRate !in 8_000..384_000) {
            status = "Unsupported TCI RX audio sample rate"
            return false
        }
        if (!routes.acquireAudio(AudioOwners.TCI_RX_AUDIO, pauseMonitor = true)) {
            status = "${routes.audioOwner} owns the audio route"
            return false
        }
        val output = routes.speakerDevice()
        if (output == null) {
            routes.releaseAudio(AudioOwners.TCI_RX_AUDIO)
            status = "No tablet speaker output detected"
            return false
        }
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minimum <= 0) {
            routes.releaseAudio(AudioOwners.TCI_RX_AUDIO)
            status = "TCI RX audio format is unavailable"
            return false
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val created = runCatching {
            AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum * 4, sampleRate / 10 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
        }.getOrElse {
            routes.releaseAudio(AudioOwners.TCI_RX_AUDIO)
            status = "Tablet speaker could not initialize"
            return false
        }
        if (!created.setPreferredDevice(output)) {
            created.release()
            routes.releaseAudio(AudioOwners.TCI_RX_AUDIO)
            status = "Android refused the selected TCI RX output route"
            return false
        }
        armedReceiver = receiver
        armedRate = sampleRate
        settingsKey = "$profileKey|$receiver"
        settings = load(settingsKey)
        queue.clear()
        track = created
        val generation = lifecycle.next()
        active.set(true)
        running = true
        status = "TCI RX audio armed · RX ${receiver + 1} · ${sampleRate / 1000} kHz"
        created.play()
        worker = Thread({ playback(created, output.id, generation) }, "RigWeave-TCI-RX-Audio").apply { start() }
        return true
    }

    fun push(receiver: Int, sampleRate: Int, channels: Int, values: FloatArray) {
        if (!active.get() || receiver != armedReceiver || sampleRate != armedRate || values.isEmpty()) return
        val mono = if (channels <= 1) values.copyOf() else FloatArray(values.size / channels) { frame ->
            val left = values[frame * channels]
            val right = values[frame * channels + 1]
            val mix = settings.stereoMix
            left * (.5f * (1f - mix)) + right * (.5f * (1f + mix))
        }
        if (!queue.offer(Frame(receiver, sampleRate, mono))) {
            queue.poll()
            queue.offer(Frame(receiver, sampleRate, mono))
            main.post { droppedFrames += 1 }
        }
    }

    private fun playback(output: AudioTrack, deviceId: Int, generation: Long) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (active.get() && track === output) {
            val frame = try { queue.poll(500, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { return }
            if (frame == null) {
                main.post { if (lifecycle.isCurrent(generation)) underflowFrames += 1 }
                continue
            }
            val value = settings
            val metrics = NativeRxDsp.process(handle, frame.samples, frame.rate, value.noiseBlanker,
                value.automaticNotch, value.noiseReduction, value.agc, value.agcHangMillis, value.squelchDb, value.outputGain)
            var written = 0
            while (written < frame.samples.size && active.get()) {
                val count = output.write(frame.samples, written, frame.samples.size - written, AudioTrack.WRITE_BLOCKING)
                if (count <= 0) {
                    fail(generation, "TCI RX audio output failed")
                    return
                }
                written += count
            }
            if (output.routedDevice?.id != deviceId) {
                fail(generation, "TCI RX audio route was lost; output stopped")
                return
            }
            if (metrics.size >= 8) main.post {
                if (lifecycle.isCurrent(generation)) {
                    inputLevelDb = metrics[0]
                    outputLevelDb = metrics[1]
                    squelched = metrics[2] > .5f
                    clippedFraction = metrics[3]
                    gainReductionDb = metrics[4]
                    notchFrequencyHz = metrics[5]
                    blankedImpulses += metrics[6].toLong()
                    processingLatencyMs = metrics[7]
                    status = "TCI RX audio live · RX ${frame.receiver + 1} · ${frame.rate / 1000} kHz"
                }
            }
        }
    }

    private fun fail(generation: Long, reason: String) = main.post {
        if (lifecycle.isCurrent(generation)) stop(reason)
    }

    @Synchronized
    fun stop(reason: String = "Operator stopped TCI RX audio") {
        lifecycle.retire()
        active.set(false)
        running = false
        worker?.interrupt()
        track?.let { runCatching { it.pause() }; runCatching { it.flush() }; it.release() }
        track = null
        worker = null
        armedReceiver = -1
        armedRate = 0
        queue.clear()
        routes.releaseAudio(AudioOwners.TCI_RX_AUDIO)
        status = reason
    }

    @Synchronized
    fun close() {
        stop("TCI RX audio closed")
        lifecycle.close()
        if (handle != 0L) NativeRxDsp.destroy(handle)
        handle = 0
    }

    private fun persist(key: String, value: RxDspSettings) {
        val row = JSONObject().put("blanker", value.noiseBlanker).put("notch", value.automaticNotch)
            .put("nr", value.noiseReduction.toDouble()).put("agc", value.agc).put("hang", value.agcHangMillis)
            .put("squelch", value.squelchDb.toDouble()).put("gain", value.outputGain.toDouble())
            .put("mix", value.stereoMix.toDouble())
        preferences.edit().putString(key, row.toString()).apply()
    }

    private fun load(key: String): RxDspSettings = runCatching {
        val row = JSONObject(preferences.getString(key, "{}"))
        RxDspSettings(row.optBoolean("blanker", true), row.optBoolean("notch"), row.optDouble("nr", .28).toFloat(),
            row.optBoolean("agc", true), row.optInt("hang", 250), row.optDouble("squelch", -105.0).toFloat(),
            row.optDouble("gain", .8).toFloat(), row.optDouble("mix", 0.0).toFloat())
    }.getOrDefault(RxDspSettings())
}

package app.rigweave.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Receive-only USB audio monitor. It never writes audio to the radio. */
class AudioMonitorController(private val context: Context) {
    private data class AudioFrame(val samples: ShortArray, val count: Int)

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val frames = ArrayBlockingQueue<AudioFrame>(8)
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null
    private var automaticGainControl: AutomaticGainControl? = null

    var enabled by mutableStateOf(false); private set
    var status by mutableStateOf("Audio monitor stopped"); private set
    var inputName by mutableStateOf("No USB audio input selected"); private set
    var outputName by mutableStateOf("Tablet speaker not selected"); private set
    var level by mutableFloatStateOf(0f); private set
    var gain by mutableFloatStateOf(1f); private set

    fun updateGain(value: Float) { gain = value.coerceIn(0f, 12f) }

    fun refreshDevices() {
        val input = usbInput()
        val output = speakerOutput()
        inputName = input?.productName?.toString()?.ifBlank { "USB audio input" } ?: "No USB audio input detected"
        outputName = output?.productName?.toString()?.ifBlank { "Built-in speaker" } ?: "No built-in speaker detected"
    }

    fun start() {
        if (running.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Microphone permission is required for USB audio monitoring"; return
        }
        stop()
        val input = usbInput()
        val output = speakerOutput()
        refreshDevices()
        if (input == null) { status = "No USB audio input detected"; return }
        if (output == null) { status = "No tablet speaker output detected"; return }
        val rate = listOf(48_000, 44_100).firstOrNull {
            AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0 &&
                AudioTrack.getMinBufferSize(it, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: run { status = "No compatible audio sample rate"; return }
        val recordMinimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val playMinimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frameSamples = rate / 50
        val record = runCatching {
            AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(recordMinimum * 4, frameSamples * 8)).build()
        }.getOrElse { status = "USB audio input could not initialize: ${it.message}"; return }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val track = runCatching {
            AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(playMinimum * 4, frameSamples * 8))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
        }.getOrElse { record.release(); status = "Tablet speaker could not initialize: ${it.message}"; return }
        if (!record.setPreferredDevice(input)) {
            record.release(); track.release(); status = "Android refused the USB input route"; return
        }
        if (!track.setPreferredDevice(output)) {
            record.release(); track.release(); status = "Android refused the tablet speaker route"; return
        }
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) main.post { stop() }
            }.build()
        audioManager.requestAudioFocus(focus)
        focusRequest = focus
        automaticGainControl = if (AutomaticGainControl.isAvailable())
            AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true } else null
        recorder = record; player = track; frames.clear()
        record.startRecording(); track.setVolume(1f); track.play()
        running.set(true); enabled = true
        status = "Starting USB audio monitor · ${rate / 1000} kHz"
        captureThread = Thread({ captureLoop(record, input, frameSamples, rate) }, "RigWeave-USB-Capture").apply { start() }
        playbackThread = Thread({ playbackLoop(track, output) }, "RigWeave-Speaker-Playback").apply { start() }
    }

    fun stop() {
        running.set(false); enabled = false
        captureThread?.interrupt(); playbackThread?.interrupt()
        recorder?.let { runCatching { it.stop() }; it.release() }
        player?.let { runCatching { it.pause() }; runCatching { it.flush() }; it.release() }
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        automaticGainControl?.release()
        recorder = null; player = null; captureThread = null; playbackThread = null
        focusRequest = null; automaticGainControl = null
        frames.clear(); level = 0f; status = "Audio monitor stopped"
    }

    fun close() = stop()

    private fun captureLoop(record: AudioRecord, requestedInput: AudioDeviceInfo, frameSamples: Int, rate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var quietFrames = 0
        var routeTicks = 0
        while (running.get() && recorder === record) {
            val samples = ShortArray(frameSamples)
            val count = record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
            if (count <= 0) { fail("USB audio read failed ($count)"); return }
            var energy = 0.0
            val multiplier = gain
            for (index in 0 until count) {
                val value = (samples[index] * multiplier).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                samples[index] = value.toShort(); energy += value.toDouble() * value
            }
            val rms = (sqrt(energy / count) / Short.MAX_VALUE).toFloat()
            quietFrames = if (rms < 0.0005f) quietFrames + 1 else 0
            main.post { level = (0.8f * level + 0.2f * (rms * 4f)).coerceIn(0f, 1f) }
            if (!frames.offer(AudioFrame(samples, count))) {
                frames.poll(); frames.offer(AudioFrame(samples, count))
            }
            if (++routeTicks >= 50) {
                routeTicks = 0
                val actual = record.routedDevice
                val routed = actual?.id == requestedInput.id
                main.post {
                    inputName = actual?.productName?.toString()?.ifBlank { "USB audio input" } ?: "USB route unavailable"
                    status = when {
                        !routed -> "Input route changed; requesting USB audio again"
                        quietFrames > 100 -> "Routes active · monitoring USB microphone input"
                        else -> "Monitoring USB input through tablet speaker · ${rate / 1000} kHz"
                    }
                }
                if (!routed) record.setPreferredDevice(requestedInput)
            }
        }
    }

    private fun playbackLoop(track: AudioTrack, requestedOutput: AudioDeviceInfo) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var routeTicks = 0
        while (running.get() && player === track) {
            val frame = try { frames.poll(500, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { return }
            if (frame == null) continue
            var written = 0
            while (written < frame.count && running.get()) {
                val result = track.write(frame.samples, written, frame.count - written, AudioTrack.WRITE_BLOCKING)
                if (result <= 0) { fail("Tablet speaker write failed ($result)"); return }
                written += result
            }
            if (++routeTicks >= 50) {
                routeTicks = 0
                val actual = track.routedDevice
                main.post { outputName = actual?.productName?.toString()?.ifBlank { "Built-in speaker" } ?: "Speaker route unavailable" }
                if (actual?.id != requestedOutput.id) track.setPreferredDevice(requestedOutput)
            }
        }
    }

    private fun fail(value: String) = main.post {
        running.set(false); enabled = false; status = value; level = 0f
    }

    private fun usbInput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    private fun speakerOutput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }
}

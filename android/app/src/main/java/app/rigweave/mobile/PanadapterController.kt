package app.rigweave.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class PanadapterController(
    private val context: Context,
    private val audio: AudioMonitorController,
    private val radioState: () -> RadioState,
    private val sendCat: (String) -> Unit,
) {
    private data class NativeBuffer(
        val meta: LongArray,
        val metrics: FloatArray,
        val trace: FloatArray,
        val waterfall: FloatArray,
        val peak: FloatArray,
        val waterfallPixels: IntArray,
        var waterfallReady: Boolean = false,
    )

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("rigweave-app", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val nativeHandle = NativePanadapter.create()
    private val running = AtomicBoolean(false)
    private val publicationPending = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var buffers = emptyArray<NativeBuffer>()
    private var bufferIndex = 0
    private var requestedDeviceId = -1
    private var requestedDeviceKey = ""
    private var lastPublishedNanos = 0L
    private var lastWaterfallNanos = 0L
    private var performanceWindowNanos = 0L
    private var performancePublishedFrames = 0
    private var performanceWaterfallRows = 0
    private var waterfallSmoothedPower = FloatArray(0)
    private var palette = IntArray(256)
    private var wantedLive = false
    private var pendingQsy: PanadapterQsy? = null
    private val replayRunning = AtomicBoolean(false)
    private var replayThread: Thread? = null
    @Volatile private var recordingFile: RandomAccessFile? = null
    @Volatile private var recordingTargetFrames = 0L
    @Volatile private var recordingWrittenFrames = 0L
    private val calibrating = AtomicBoolean(false)
    private val levelCalibrating = AtomicBoolean(false)
    private var calibrationTargetFrames = 0L
    private var calibrationCount = 0L
    private var calibrationSumI = 0.0
    private var calibrationSumQ = 0.0
    private var calibrationSumII = 0.0
    private var calibrationSumQQ = 0.0
    private var calibrationSumIQ = 0.0
    private var calibrationKnownOffsetHz = 0f
    private var calibrationPeakMin = Int.MAX_VALUE
    private var calibrationPeakMax = Int.MIN_VALUE
    private var calibrationAwaitSequence = Long.MAX_VALUE
    private var levelCalibrationKnownDbm = 0f
    private var levelCalibrationUncertaintyDb = 0f
    private var levelCalibrationNotes = ""
    private var levelCalibrationDeadlineMs = 0L
    private val levelCalibrationSamples = ArrayList<Float>(96)

    private val routeListener = AudioRouting.OnRoutingChangedListener { routing ->
        val record = routing as? AudioRecord ?: return@OnRoutingChangedListener
        val actual = record.routedDevice
        if (running.get() && (actual == null || actual.id != requestedDeviceId)) {
            failRoute("Android rerouted I/Q away from the selected input")
        }
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == requestedDeviceId }) failRoute("Selected stereo I/Q device detached")
            audio.refreshDevices()
        }
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            audio.refreshDevices()
            if (wantedLive && lifecycle == PanadapterLifecycle.ROUTE_LOST &&
                audio.inputCandidates.any { it.stableKey == requestedDeviceKey }) main.postDelayed({ start() }, 500)
        }
    }

    var settings by mutableStateOf(PanadapterSettings.decode(prefs.getString("panadapter_settings", null)))
        private set
    var lifecycle by mutableStateOf(PanadapterLifecycle.STOPPED); private set
    var status by mutableStateOf("Panadapter stopped"); private set
    var routeProof by mutableStateOf(PanadapterRouteProof()); private set
    var frame by mutableStateOf<PanadapterFrame?>(null); private set
    var waterfallBitmap by mutableStateOf<Bitmap?>(null); private set
    var waterfallHead by mutableIntStateOf(0); private set
    var waterfallRevision by mutableIntStateOf(0); private set
    var lastQsy by mutableStateOf<PanadapterQsy?>(null); private set
    var supportExportPath by mutableStateOf(""); private set
    var recordingStatus by mutableStateOf("Not recording"); private set
    var lastRecordingPath by mutableStateOf(""); private set
    var calibrationStatus by mutableStateOf("Calibration idle"); private set
    var calibrationCandidate by mutableStateOf<PanadapterCalibrationCandidate?>(null); private set
    var levelCalibrationCandidate by mutableStateOf<PanadapterLevelCalibrationCandidate?>(null); private set
    var publishedFps by mutableStateOf(0f); private set
    var waterfallFps by mutableStateOf(0f); private set
    var latencyEstimateMs by mutableStateOf(0f); private set
    val inputCandidates: List<AudioRouteDescriptor> get() = audio.inputCandidates
    val selectedInput: AudioRouteDescriptor? get() = audio.selectedRx

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, main)
        rebuildPalette()
    }

    fun updateSettings(value: PanadapterSettings, restart: Boolean = lifecycle == PanadapterLifecycle.LIVE) {
        var next = value.validated()
        val measuredProfileChanged = next.measuredFlatnessOffsetsCsv != settings.measuredFlatnessOffsetsCsv ||
            next.measuredFlatnessGainsCsv != settings.measuredFlatnessGainsCsv
        if (next.measuredFlatnessEnabled && measuredProfileChanged && parseMeasuredFlatness(next).isNotEmpty()) {
            next = next.copy(
                measuredFlatnessDeviceKey = selectedInput?.stableKey.orEmpty(),
                measuredFlatnessRate = routeProof.configuredRate.takeIf { it > 0 } ?: next.requestedRate,
                measuredFlatnessRadio = radioState().model,
                measuredFlatnessEpochMs = System.currentTimeMillis(),
            )
        }
        settings = next
        prefs.edit().putString("panadapter_settings", settings.encode()).apply()
        rebuildPalette()
        if (restart) { stop(keepWanted = true); main.postDelayed({ start() }, 200) }
    }

    fun hasRecordPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun selectInput(sessionId: Int) = audio.selectRxInput(sessionId)

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked immediately below; builder failures are caught per candidate.
    fun start() {
        if (running.get() || lifecycle == PanadapterLifecycle.STARTING) return
        wantedLive = true
        stopReplay()
        val radio = radioState()
        if (!hasRecordPermission()) { lifecycle = PanadapterLifecycle.ERROR; status = "RECORD_AUDIO permission is required"; return }
        if (!radio.connected || radio.model != "KX3") { lifecycle = PanadapterLifecycle.ERROR; status = "Connect and identify a KX3 before enabling wideband RX I/Q"; return }
        if (radio.transmitting) { lifecycle = PanadapterLifecycle.ERROR; status = "Receive I/Q cannot start while the KX3 is transmitting"; return }
        audio.refreshDevices()
        val selected = audio.selectedRx ?: run { lifecycle = PanadapterLifecycle.ERROR; status = "Select one external stereo USB input"; return }
        val device = audio.selectedRxDevice() ?: run { lifecycle = PanadapterLifecycle.ERROR; status = "Selected USB input is no longer connected"; return }
        if (selected.channelCounts.isNotEmpty() && selected.channelCounts.none { it >= 2 }) {
            lifecycle = PanadapterLifecycle.ERROR; status = "Selected input reports no stereo capability"; return
        }
        if (!audio.acquireAudio("PANADAPTER", pauseMonitor = true)) {
            lifecycle = PanadapterLifecycle.ERROR; status = "${audio.audioOwner} owns the audio input"; return
        }
        lifecycle = PanadapterLifecycle.STARTING; status = "Negotiating selected stereo USB route"
        requestedDeviceId = device.id; requestedDeviceKey = selected.stableKey
        val rates = buildList { add(settings.requestedRate); if (settings.requestedRate == 96_000 && settings.allow48kFallback) add(48_000) }.distinct()
        var chosen: AudioRecord? = null
        var chosenRate = 0
        var chosenMinimum = 0
        var preferred = false
        for (rate in rates) {
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) continue
            val candidate = runCatching {
                AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build())
                    .setBufferSizeInBytes(max(minimum * 4, rate / 5 * 4)).build()
            }.getOrNull() ?: continue
            if (candidate.state != AudioRecord.STATE_INITIALIZED) { candidate.release(); continue }
            preferred = candidate.setPreferredDevice(device)
            if (!preferred) { candidate.release(); continue }
            chosen = candidate; chosenRate = rate; chosenMinimum = minimum; break
        }
        val record = chosen ?: run {
            audio.releaseAudio("PANADAPTER"); lifecycle = PanadapterLifecycle.ERROR
            status = "Selected input could not initialize as 96/48 kHz stereo PCM"; return
        }
        val configured = settings.copy(requestedRate = chosenRate)
        if (!NativePanadapter.configure(nativeHandle, chosenRate, configured.fftSize, configured.overlapPercent,
                configured.window.nativeValue, configured.displayFloorDb, configured.displayTopDb,
                configured.attack, configured.release, configured.averageFrames, configured.peakHold,
                configured.peakDecayDbPerSecond, configured.genericKx3Flatness, configured.swapIq,
                configured.invertI, configured.invertQ, configured.conjugate, configured.iTrim, configured.qTrim,
                configured.zoomDecimation, configured.zoomOffsetHz)) {
            record.release(); audio.releaseAudio("PANADAPTER"); lifecycle = PanadapterLifecycle.ERROR
            status = "Native DSP rejected the selected configuration"; return
        }
        NativePanadapter.setIqCorrection(nativeHandle, configured.iqAReal, configured.iqAImag,
            configured.iqBReal, configured.iqBImag,
            configured.iqCorrectionEnabled && configured.calibrationDeviceKey == selected.stableKey && configured.calibrationRate == chosenRate)
        buffers = Array(3) { NativeBuffer(LongArray(9), FloatArray(10), FloatArray(configured.fftSize),
            FloatArray(configured.fftSize), FloatArray(configured.fftSize), IntArray(waterfallWidth(configured.fftSize))) }
        rebuildWaterfall(configured.fftSize)
        record.addOnRoutingChangedListener(routeListener, main)
        recorder = record
        runCatching { record.startRecording() }.onFailure {
            record.removeOnRoutingChangedListener(routeListener); record.release(); recorder = null
            audio.releaseAudio("PANADAPTER"); lifecycle = PanadapterLifecycle.ERROR; status = "AudioRecord start failed: ${it.message}"; return
        }
        val actual = record.routedDevice
        val channels = record.channelCount
        val actualMatches = actual?.id == device.id
        val active = if (Build.VERSION.SDK_INT >= 29) runCatching { record.activeRecordingConfiguration }.getOrNull() else null
        routeProof = PanadapterRouteProof(
            selected.stableKey, preferred, actual?.let { audio.inputCandidates.firstOrNull { row -> row.sessionId == it.id }?.stableKey } ?: "None",
            settings.requestedRate, record.sampleRate, channels, record.audioFormat, chosenMinimum / 4,
            active?.clientFormat?.toString() ?: record.format.toString(),
            if (Build.VERSION.SDK_INT >= 29) active?.format?.toString() ?: "Unavailable" else "Unavailable on API ${Build.VERSION.SDK_INT}",
            record.audioSource, record.audioSessionId,
        )
        if (!actualMatches || channels < 2 || record.sampleRate !in setOf(48_000, 96_000)) {
            stop("Active route/format did not prove selected stereo 48/96 kHz I/Q")
            lifecycle = PanadapterLifecycle.ERROR; return
        }
        running.set(true); lifecycle = PanadapterLifecycle.LIVE
        performanceWindowNanos = System.nanoTime(); performancePublishedFrames = 0; performanceWaterfallRows = 0
        lastPublishedNanos = 0L; lastWaterfallNanos = 0L
        publishedFps = 0f; waterfallFps = 0f
        status = "LIVE · ${selected.name} · ${record.sampleRate / 1000} kHz stereo · ${configured.fftSize} FFT"
        // The AudioRecord owns a generously sized internal buffer; bounded ~16.7 ms reads let
        // presentation hit 30 fps without changing the proven route, format, FFT or RF span.
        val samples = ShortArray(record.sampleRate / 60 * 2)
        captureThread = Thread({ captureLoop(record, samples) }, "RigWeave-IQ-Capture").apply { start() }
    }

    fun stop(reason: String = "Panadapter stopped", keepWanted: Boolean = false) {
        wantedLive = keepWanted
        running.set(false)
        val record = recorder
        recorder = null
        record?.removeOnRoutingChangedListener(routeListener)
        record?.let { runCatching { it.stop() }; it.release() }
        captureThread?.interrupt(); captureThread = null
        audio.releaseAudio("PANADAPTER")
        stopRecording()
        calibrating.set(false)
        levelCalibrating.set(false)
        if (lifecycle != PanadapterLifecycle.ROUTE_LOST) lifecycle = PanadapterLifecycle.STOPPED
        status = reason
    }

    private fun failRoute(reason: String) {
        if (!running.get() && lifecycle != PanadapterLifecycle.STARTING) return
        lifecycle = PanadapterLifecycle.ROUTE_LOST
        stop(reason, keepWanted = true)
        lifecycle = PanadapterLifecycle.ROUTE_LOST
    }

    private fun captureLoop(record: AudioRecord, samples: ShortArray) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val recordingBytes = ByteArray(samples.size * 2)
        while (running.get() && recorder === record) {
            val count = record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
            if (count <= 0) { failRoute("Stereo I/Q read failed ($count)"); return }
            val discontinuity = count % 2 != 0
            appendRecording(samples, count, recordingBytes, record.sampleRate)
            accumulateCalibration(samples, count, record.sampleRate)
            val ready = NativePanadapter.push(nativeHandle, samples, count, discontinuity)
            val now = System.nanoTime()
            if (ready && publicationPending.compareAndSet(false, true)) {
                if (!spectrumFrameDue(now)) { publicationPending.set(false); continue }
                val buffer = buffers[bufferIndex]
                val copied = NativePanadapter.snapshot(nativeHandle, buffer.meta, buffer.metrics,
                    buffer.trace, buffer.waterfall, buffer.peak)
                if (copied > 0) {
                    applyMeasuredFlatness(buffer, copied)
                    buffer.waterfallReady = waterfallRowDue(now)
                    if (buffer.waterfallReady) {
                        prepareWaterfallRow(buffer.waterfall, buffer.waterfallPixels)
                    }
                    bufferIndex = (bufferIndex + 1) % buffers.size
                    main.post {
                        publish(buffer, copied)
                        publicationPending.set(false)
                    }
                } else publicationPending.set(false)
            }
        }
    }

    private fun publish(buffer: NativeBuffer, count: Int) {
        val previous = frame
        val next = PanadapterFrame(
            buffer.meta[0], buffer.meta[1], buffer.meta[2], buffer.meta[3], buffer.meta[4].toInt(),
            buffer.meta[5].toInt(), count, buffer.meta[7].toInt(), buffer.meta[8].toInt(),
            buffer.metrics[0], buffer.metrics[1], buffer.metrics[2], buffer.metrics[3], buffer.metrics[4],
            buffer.metrics[5], buffer.metrics[6], buffer.metrics[7], buffer.metrics[8], buffer.metrics[9],
            buffer.metrics[7].isFinite() && buffer.metrics[7] in -0.995f..0.995f &&
                buffer.metrics[9].isFinite() && buffer.metrics[9] < .995f &&
                buffer.metrics[5] > -100f && buffer.metrics[6] > -100f && buffer.metrics[8] < .01f,
            buffer.trace, buffer.waterfall, buffer.peak,
        )
        frame = next
        performancePublishedFrames++
        collectLevelCalibration(next)
        if (calibrating.get()) {
            strongestSignal(next)?.first?.let { peak ->
                calibrationPeakMin = minOf(calibrationPeakMin, peak)
                calibrationPeakMax = maxOf(calibrationPeakMax, peak)
            }
        }
        val candidate = calibrationCandidate
        if (candidate != null && candidate.rejectionAfterDb == null && next.sequence >= calibrationAwaitSequence) {
            val after = strongestSignal(next)?.second ?: -140f
            calibrationCandidate = candidate.copy(rejectionAfterDb = after)
            calibrationStatus = "Preview ready · image rejection ${"%.1f".format(candidate.rejectionBeforeDb)} → ${"%.1f".format(after)} dB"
        }
        if (buffer.waterfallReady) {
            appendWaterfallRow(buffer.waterfallPixels)
            buffer.waterfallReady = false
            performanceWaterfallRows++
        }
        updatePerformanceMetrics(next)
        if (previous != null && isMaterialCenterChange(lastRenderedCenter, effectiveCenter(), next.rbwHz)) clearWaterfall()
        lastRenderedCenter = effectiveCenter()
        observeRadioState(radioState())
    }

    private var lastRenderedCenter = 0L
    private fun spectrumFrameDue(now: Long): Boolean {
        val interval = 33_000_000L
        if (lastPublishedNanos == 0L) { lastPublishedNanos = now; return true }
        val elapsed = now - lastPublishedNanos
        if (elapsed < interval) return false
        lastPublishedNanos += (elapsed / interval) * interval
        return true
    }

    private fun waterfallRowDue(now: Long): Boolean {
        val interval = 1_000_000_000L / settings.waterfallLineRate
        if (lastWaterfallNanos == 0L) { lastWaterfallNanos = now; return false }
        val elapsed = now - lastWaterfallNanos
        if (elapsed < interval) return false
        // Preserve fractional cadence instead of resetting to now, which aliases 25 rows/s
        // against a 30 fps publisher into only ~15 rows/s.
        lastWaterfallNanos += (elapsed / interval) * interval
        return true
    }
    private fun updatePerformanceMetrics(value: PanadapterFrame) {
        val now = System.nanoTime()
        if (performanceWindowNanos == 0L) performanceWindowNanos = now
        val elapsed = now - performanceWindowNanos
        if (elapsed >= 2_000_000_000L) {
            publishedFps = performancePublishedFrames * 1_000_000_000f / elapsed
            waterfallFps = performanceWaterfallRows * 1_000_000_000f / elapsed
            performancePublishedFrames = 0; performanceWaterfallRows = 0; performanceWindowNanos = now
        }
        val rate = routeProof.configuredRate.takeIf { it > 0 } ?: value.sampleRate
        latencyEstimateMs = if (rate > 0) (routeProof.bufferFrames + value.fftSize) * 1_000f / rate else 0f
    }
    fun effectiveCenter(): Long {
        val radio = radioState()
        val age = if (radio.updatedMonotonicMs > 0) SystemClock.elapsedRealtime() - radio.updatedMonotonicMs else Long.MAX_VALUE
        return if (radio.connected && age < 2_500) radio.effectiveRxHz.takeIf { it > 0 } ?: radio.frequencyHz else 0L
    }

    fun levelCalibrationActive(): Boolean {
        val configured = settings
        val radio = radioState()
        return configured.levelCalibrationEnabled && configured.levelCalibrationDeviceKey == routeProof.requestedDevice &&
            configured.levelCalibrationRate == routeProof.configuredRate && configured.levelCalibrationRadio == radio.model &&
            configured.levelCalibrationBand == bandForFrequency(effectiveCenter())
    }

    fun measuredFlatnessActive(): Boolean {
        val configured = settings
        return configured.measuredFlatnessEnabled && parseMeasuredFlatness(configured).isNotEmpty() &&
            configured.measuredFlatnessDeviceKey == routeProof.requestedDevice &&
            configured.measuredFlatnessRate == routeProof.configuredRate && configured.measuredFlatnessRadio == radioState().model
    }

    fun observeRadioState(radio: RadioState) {
        val pending = pendingQsy ?: return
        val observed = if (pending.vfo == 1) radio.frequencyBHz else radio.frequencyHz
        if (observed == pending.requestedHz) { lastQsy = pending.copy(observedRevision = radio.revision); pendingQsy = null }
        else if (radio.revision > pending.observedRevision + 8) pendingQsy = null
    }

    fun tune(frequencyHz: Long, vfo: Int = radioState().rxVfo): String {
        val radio = radioState()
        if (!radio.connected || radio.model != "KX3" || radio.transmitting) return "QSY blocked: KX3 is not in live receive"
        val rounded = roundedPanadapterFrequency(frequencyHz, settings.qsyStepHz)
        val previous = if (vfo == 1) radio.frequencyBHz else radio.frequencyHz
        if (rounded !in 100_000L..54_000_000L || previous <= 0) return "QSY blocked: frequency is outside the verified HF/6 m range"
        pendingQsy = PanadapterQsy(vfo, previous, rounded, radio.revision)
        sendCat("${if (vfo == 1) "FB" else "FA"}%011d;".format(rounded))
        return "QSY requested · ${formatRadioFrequency(rounded)} MHz"
    }

    fun undoLastQsy(): String {
        val qsy = lastQsy ?: return "No confirmed panadapter QSY to undo"
        val radio = radioState()
        val current = if (qsy.vfo == 1) radio.frequencyBHz else radio.frequencyHz
        if (current != qsy.requestedHz) { lastQsy = null; return "Undo invalidated: the radio was tuned after the panadapter QSY" }
        pendingQsy = PanadapterQsy(qsy.vfo, current, qsy.previousHz, radio.revision)
        sendCat("${if (qsy.vfo == 1) "FB" else "FA"}%011d;".format(qsy.previousHz))
        lastQsy = null
        return "Undo requested · ${formatRadioFrequency(qsy.previousHz)} MHz"
    }

    fun resetPeakHold() = NativePanadapter.resetPeakHold(nativeHandle)

    fun startRecording(seconds: Int = 10): String {
        if (lifecycle != PanadapterLifecycle.LIVE || !routeProof.verified) return "Recording requires verified live stereo I/Q"
        if (recordingFile != null) return "I/Q recording is already active"
        val duration = seconds.coerceIn(1, 60)
        return runCatching {
            val directory = File(context.filesDir, "panadapter/recordings").apply { mkdirs() }
            val base = "iq-${System.currentTimeMillis()}"
            val file = File(directory, "$base.wav")
            val handle = RandomAccessFile(file, "rw")
            handle.setLength(0); handle.write(ByteArray(44))
            recordingTargetFrames = routeProof.configuredRate.toLong() * duration
            recordingWrittenFrames = 0
            recordingFile = handle
            lastRecordingPath = file.absolutePath
            File(directory, "$base.json").writeText(JSONObject().put("version", 1)
                .put("created_at", System.currentTimeMillis()).put("sample_rate", routeProof.configuredRate)
                .put("channels", 2).put("encoding", "PCM_SIGNED_16_LE")
                .put("selected_product", selectedInput?.name ?: "unknown")
                .put("selected_usb", selectedInput?.usbIdentity ?: "unavailable")
                .put("settings", settings.encode()).toString(2))
            recordingStatus = "Recording bounded ${duration}s stereo I/Q"
            recordingStatus
        }.getOrElse { "Recording failed: ${it.message}" }
    }

    fun stopRecording(): String {
        val file = recordingFile ?: return "Not recording"
        recordingFile = null
        return runCatching {
            val frames = recordingWrittenFrames
            val dataBytes = frames * 4L
            file.seek(0); file.write(wavHeader(routeProof.configuredRate.takeIf { it > 0 } ?: settings.requestedRate, dataBytes))
            file.fd.sync(); file.close()
            recordingStatus = "Saved ${"%.1f".format(frames / (routeProof.configuredRate.takeIf { it > 0 } ?: settings.requestedRate).toFloat())}s stereo I/Q"
            recordingStatus
        }.getOrElse { recordingStatus = "Recording finalization failed: ${it.message}"; recordingStatus }
    }

    private fun appendRecording(samples: ShortArray, count: Int, bytes: ByteArray, sampleRate: Int) {
        val file = recordingFile ?: return
        val remainingSamples = ((recordingTargetFrames - recordingWrittenFrames) * 2L).coerceAtLeast(0L)
        val sampleCount = minOf(count.toLong(), remainingSamples).toInt()
        if (sampleCount <= 0) { main.post { stopRecording() }; return }
        for (index in 0 until sampleCount) {
            val value = samples[index].toInt()
            bytes[index * 2] = (value and 0xff).toByte(); bytes[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
        }
        runCatching { file.write(bytes, 0, sampleCount * 2) }.onFailure { main.post { stopRecording(); recordingStatus = "Recording write failed: ${it.message}" } }
        recordingWrittenFrames += sampleCount / 2L
        if (recordingWrittenFrames >= recordingTargetFrames) main.post { stopRecording() }
    }

    fun replayLastRecording(): String {
        val path = lastRecordingPath
        if (path.isBlank() || !File(path).isFile) return "No bounded I/Q recording is available"
        stop(); wantedLive = false
        if (!replayRunning.compareAndSet(false, true)) return "Replay is already active"
        lifecycle = PanadapterLifecycle.REPLAY; status = "Deterministic I/Q replay"
        replayThread = Thread({
            runCatching {
                FileInputStream(path).use { input ->
                    val header = ByteArray(44); require(input.read(header) == 44)
                    val headerView = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE")
                    val rate = headerView.getInt(24); val channels = headerView.getShort(22).toInt(); val bits = headerView.getShort(34).toInt()
                    require(rate in setOf(48_000, 96_000) && channels == 2 && bits == 16)
                    require(configureNativeForRate(rate))
                    val byteBuffer = ByteArray(rate / 25 * 4); val shortBuffer = ShortArray(byteBuffer.size / 2)
                    while (replayRunning.get()) {
                        val read = input.read(byteBuffer)
                        if (read <= 0) break
                        val even = read - read % 4
                        ByteBuffer.wrap(byteBuffer, 0, even).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, even / 2)
                        if (NativePanadapter.push(nativeHandle, shortBuffer, even / 2, false)) publishNativeReplay()
                    }
                }
            }.onFailure { error -> main.post { lifecycle = PanadapterLifecycle.ERROR; status = "Replay failed: ${error.message}" } }
            replayRunning.set(false)
            main.post { if (lifecycle == PanadapterLifecycle.REPLAY) { lifecycle = PanadapterLifecycle.STOPPED; status = "Replay complete" } }
        }, "RigWeave-IQ-Replay").apply { start() }
        return "Replay started"
    }

    fun stopReplay() {
        replayRunning.set(false); replayThread?.interrupt(); replayThread = null
        if (lifecycle == PanadapterLifecycle.REPLAY) { lifecycle = PanadapterLifecycle.STOPPED; status = "Replay stopped" }
    }

    private fun configureNativeForRate(rate: Int): Boolean {
        val configured = settings
        val ok = NativePanadapter.configure(nativeHandle, rate, configured.fftSize, configured.overlapPercent,
            configured.window.nativeValue, configured.displayFloorDb, configured.displayTopDb,
            configured.attack, configured.release, configured.averageFrames, configured.peakHold,
            configured.peakDecayDbPerSecond, configured.genericKx3Flatness, configured.swapIq,
            configured.invertI, configured.invertQ, configured.conjugate, configured.iTrim, configured.qTrim,
            configured.zoomDecimation, configured.zoomOffsetHz)
        if (ok) {
            val selectedKey = selectedInput?.stableKey.orEmpty()
            NativePanadapter.setIqCorrection(nativeHandle, configured.iqAReal, configured.iqAImag,
                configured.iqBReal, configured.iqBImag,
                configured.iqCorrectionEnabled && configured.calibrationDeviceKey == selectedKey && configured.calibrationRate == rate)
            buffers = Array(3) { NativeBuffer(LongArray(9), FloatArray(10), FloatArray(configured.fftSize),
                FloatArray(configured.fftSize), FloatArray(configured.fftSize), IntArray(waterfallWidth(configured.fftSize))) }
            bufferIndex = 0
            main.post { rebuildWaterfall(configured.fftSize) }
        }
        return ok
    }

    private fun publishNativeReplay() {
        if (!publicationPending.compareAndSet(false, true)) return
        val buffer = buffers[bufferIndex]
        val copied = NativePanadapter.snapshot(nativeHandle, buffer.meta, buffer.metrics, buffer.trace, buffer.waterfall, buffer.peak)
        if (copied > 0) {
            applyMeasuredFlatness(buffer, copied)
            val now = System.nanoTime()
            buffer.waterfallReady = waterfallRowDue(now)
            if (buffer.waterfallReady) {
                prepareWaterfallRow(buffer.waterfall, buffer.waterfallPixels)
            }
            bufferIndex = (bufferIndex + 1) % buffers.size
            main.post { publish(buffer, copied); publicationPending.set(false) }
        }
        else publicationPending.set(false)
    }

    fun startCalibration(knownOffsetHz: Float, seconds: Int = 3): String {
        val current = frame ?: return "Calibration requires live I/Q"
        if (lifecycle != PanadapterLifecycle.LIVE || !routeProof.verified || !current.validStereo) return "Calibration requires verified independent stereo channels"
        if (abs(knownOffsetHz) < 500f || abs(knownOffsetHz) > current.effectiveSampleRate * .42f) return "Place marker A on one known stable tone clearly off centre"
        if (current.peakDb - current.floorDb < 20f || current.clippedFraction > .001f) return "Calibration tone must be stable, unclipped, and at least 20 dB above floor"
        NativePanadapter.setIqCorrection(nativeHandle, 1f, 0f, 0f, 0f, false)
        calibrationTargetFrames = routeProof.configuredRate.toLong() * seconds.coerceIn(2, 8)
        calibrationCount = 0; calibrationSumI = 0.0; calibrationSumQ = 0.0
        calibrationSumII = 0.0; calibrationSumQQ = 0.0; calibrationSumIQ = 0.0
        calibrationKnownOffsetHz = knownOffsetHz; calibrationPeakMin = Int.MAX_VALUE; calibrationPeakMax = Int.MIN_VALUE
        calibrationCandidate = null; calibrationStatus = "Collecting known tone · keep the radio and generator stable"
        calibrating.set(true)
        return calibrationStatus
    }

    private fun accumulateCalibration(samples: ShortArray, count: Int, sampleRate: Int) {
        if (!calibrating.get()) return
        var index = 0
        while (index + 1 < count && calibrationCount < calibrationTargetFrames) {
            var i = samples[index].toDouble() / 32768.0
            var q = samples[index + 1].toDouble() / 32768.0
            if (settings.swapIq) { val temporary = i; i = q; q = temporary }
            if (settings.invertI) i = -i
            if (settings.invertQ) q = -q
            i *= settings.iTrim
            q *= settings.qTrim
            if (settings.conjugate) q = -q
            calibrationSumI += i; calibrationSumQ += q; calibrationSumII += i * i; calibrationSumQQ += q * q; calibrationSumIQ += i * q
            calibrationCount++; index += 2
        }
        if (calibrationCount >= calibrationTargetFrames && calibrating.compareAndSet(true, false)) finishCalibration(sampleRate)
    }

    private fun finishCalibration(sampleRate: Int) {
        val n = calibrationCount.toDouble(); val meanI = calibrationSumI / n; val meanQ = calibrationSumQ / n
        val ii = calibrationSumII / n - meanI * meanI; val qq = calibrationSumQQ / n - meanQ * meanQ
        val iq = calibrationSumIQ / n - meanI * meanQ; val denominator = ii + qq
        val kReal = (ii - qq) / denominator; val kImag = 2.0 * iq / denominator; val magnitude2 = kReal * kReal + kImag * kImag
        val beforeSignal = frame?.let(::strongestSignal)
        val expectedSign = if (calibrationKnownOffsetHz > 0) 1 else -1
        val observedSign = beforeSignal?.first?.let { if (it >= (frame?.fftSize ?: 0) / 2) 1 else -1 } ?: 0
        val rejectionBefore = beforeSignal?.second ?: -140f
        val error = when {
            denominator < 1.0e-8 -> "Calibration rejected: tone level is too low"
            magnitude2 >= .90 * .90 -> "Calibration rejected: channels are duplicated or coefficients are implausible"
            calibrationPeakMax - calibrationPeakMin > 2 -> "Calibration rejected: tone frequency was unstable"
            observedSign != expectedSign -> "Calibration rejected: strongest tone is on the opposite side of marker A"
            rejectionBefore < 6f -> "Calibration rejected: multiple strong tones or an ambiguous image"
            else -> null
        }
        if (error != null) {
            main.post { calibrationStatus = error; restoreSavedCorrection() }
            return
        }
        val numerator = -1.0 + sqrt(1.0 - magnitude2)
        val bReal = if (magnitude2 < 1.0e-10) 0f else (numerator * kReal / magnitude2).toFloat()
        val bImag = if (magnitude2 < 1.0e-10) 0f else (numerator * kImag / magnitude2).toFloat()
        if (!bReal.isFinite() || !bImag.isFinite() || kotlin.math.hypot(bReal, bImag) > 1f) {
            main.post { calibrationStatus = "Calibration rejected: correction is not finite or plausible"; restoreSavedCorrection() }
            return
        }
        NativePanadapter.setIqCorrection(nativeHandle, 1f, 0f, bReal, bImag, true)
        main.post {
            calibrationCandidate = PanadapterCalibrationCandidate(bReal, bImag, calibrationKnownOffsetHz, rejectionBefore)
            calibrationAwaitSequence = (frame?.sequence ?: 0) + 8
            calibrationStatus = "Previewing stable I/Q correction · do not change the known tone"
        }
    }

    fun confirmCalibration(): String {
        val candidate = calibrationCandidate ?: return "No calibration preview to confirm"
        val after = candidate.rejectionAfterDb ?: return "Wait for the corrected preview"
        if (after < 35f || after < candidate.rejectionBeforeDb + 6f) return "Calibration not saved: preview did not reach a credible improvement"
        updateSettings(settings.copy(iqAReal = 1f, iqAImag = 0f, iqBReal = candidate.bReal,
            iqBImag = candidate.bImag, iqCorrectionEnabled = true,
            calibrationDeviceKey = routeProof.requestedDevice, calibrationRate = routeProof.configuredRate), restart = false)
        calibrationCandidate = null; calibrationStatus = "Calibration saved · ${"%.1f".format(after)} dB image rejection"
        return calibrationStatus
    }

    fun cancelCalibration(): String {
        calibrating.set(false); calibrationCandidate = null; restoreSavedCorrection()
        calibrationStatus = "Calibration cancelled; saved profile restored"
        return calibrationStatus
    }

    fun startLevelCalibration(knownDbm: Float, uncertaintyDb: Float, notes: String): String {
        val current = frame ?: return "Level calibration requires live I/Q"
        if (lifecycle != PanadapterLifecycle.LIVE || !routeProof.verified || !current.validStereo)
            return "Level calibration requires verified live independent stereo I/Q"
        if (knownDbm !in -180f..20f || uncertaintyDb !in 0f..30f)
            return "Enter a plausible known level and uncertainty"
        if (current.clippedFraction > .001f || current.peakDb - current.floorDb < 15f)
            return "Known reference must be unclipped and clearly above the floor"
        levelCalibrationKnownDbm = knownDbm
        levelCalibrationUncertaintyDb = uncertaintyDb
        levelCalibrationNotes = notes.trim().take(160)
        levelCalibrationSamples.clear()
        levelCalibrationCandidate = null
        levelCalibrationDeadlineMs = SystemClock.elapsedRealtime() + 3_000L
        levelCalibrating.set(true)
        calibrationStatus = "Measuring known reference for 3 seconds · do not change gain"
        return calibrationStatus
    }

    private fun collectLevelCalibration(value: PanadapterFrame) {
        if (!levelCalibrating.get()) return
        if (value.peakDb.isFinite()) levelCalibrationSamples += value.peakDb
        if (SystemClock.elapsedRealtime() < levelCalibrationDeadlineMs) return
        levelCalibrating.set(false)
        if (levelCalibrationSamples.size < 20 || value.clippedFraction > .001f) {
            calibrationStatus = "Level calibration rejected: reference capture was incomplete or clipped"
            return
        }
        val sorted = levelCalibrationSamples.sorted()
        val median = sorted[sorted.size / 2]
        val spread = sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)] - sorted[sorted.size / 10]
        if (spread > 3f) {
            calibrationStatus = "Level calibration rejected: reference varied ${"%.1f".format(spread)} dB"
            return
        }
        levelCalibrationCandidate = PanadapterLevelCalibrationCandidate(
            levelCalibrationKnownDbm, median, levelCalibrationKnownDbm - median,
            levelCalibrationUncertaintyDb, levelCalibrationNotes,
        )
        calibrationStatus = "Level preview · ${"%.1f".format(median)} dBFS = ${"%.1f".format(levelCalibrationKnownDbm)} dBm"
    }

    fun confirmLevelCalibration(): String {
        val candidate = levelCalibrationCandidate ?: return "No level calibration preview to confirm"
        val radio = radioState()
        updateSettings(settings.copy(
            levelCalibrationEnabled = true,
            dbfsToDbmOffset = candidate.offsetDb,
            levelCalibrationFrequencyHz = effectiveCenter(),
            levelCalibrationDeviceKey = routeProof.requestedDevice,
            levelCalibrationRate = routeProof.configuredRate,
            levelCalibrationRadio = radio.model,
            levelCalibrationBand = bandForFrequency(effectiveCenter()),
            levelCalibrationEpochMs = System.currentTimeMillis(),
            levelCalibrationUncertaintyDb = candidate.uncertaintyDb,
            levelCalibrationNotes = candidate.notes,
        ), restart = false)
        levelCalibrationCandidate = null
        calibrationStatus = "Measured level profile saved · input gain changes invalidate it"
        return calibrationStatus
    }

    fun cancelLevelCalibration(): String {
        levelCalibrating.set(false); levelCalibrationSamples.clear(); levelCalibrationCandidate = null
        calibrationStatus = "Level calibration cancelled"
        return calibrationStatus
    }

    private fun restoreSavedCorrection() {
        NativePanadapter.setIqCorrection(nativeHandle, settings.iqAReal, settings.iqAImag,
            settings.iqBReal, settings.iqBImag,
            settings.iqCorrectionEnabled && settings.calibrationDeviceKey == routeProof.requestedDevice &&
                settings.calibrationRate == routeProof.configuredRate)
    }

    private fun applyMeasuredFlatness(buffer: NativeBuffer, count: Int) {
        val configured = settings
        if (!measuredFlatnessActive()) return
        val points = parseMeasuredFlatness(configured)
        if (points.isEmpty()) return
        val rate = buffer.meta[5].toFloat().takeIf { it > 0f } ?: return
        for (index in 0 until count.coerceAtMost(buffer.trace.size)) {
            val offset = (index.toFloat() / count - .5f) * rate
            val correction = measuredFlatnessCorrection(points, offset)
            buffer.trace[index] += correction
            buffer.waterfall[index] += correction
            buffer.peak[index] += correction
        }
    }

    private fun strongestSignal(value: PanadapterFrame): Pair<Int, Float>? {
        if (value.trace.isEmpty()) return null
        val center = value.trace.size / 2
        var peak = -1; var peakDb = -140f
        for (index in value.trace.indices) if (abs(index - center) > 8 && value.trace[index] > peakDb) { peak = index; peakDb = value.trace[index] }
        if (peak < 0) return null
        val mirror = (2 * center - peak).coerceIn(0, value.trace.lastIndex)
        return peak to (peakDb - value.trace[mirror])
    }

    private fun wavHeader(sampleRate: Int, dataBytes: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt((36L + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(sampleRate); putInt(sampleRate * 4)
        putShort(4); putShort(16); put("data".toByteArray()); putInt(dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }.array()

    fun clearWaterfall() {
        waterfallBitmap?.eraseColor(Color.BLACK); waterfallHead = 0; waterfallRevision++
    }

    private fun rebuildWaterfall(fftSize: Int) {
        val width = waterfallWidth(fftSize)
        waterfallSmoothedPower = FloatArray(width)
        waterfallBitmap?.recycle()
        waterfallBitmap = Bitmap.createBitmap(width, settings.waterfallRows, Bitmap.Config.ARGB_8888)
        waterfallBitmap?.eraseColor(Color.BLACK)
        waterfallHead = 0; waterfallRevision++
    }

    private fun waterfallWidth(fftSize: Int): Int = minOf(1_024, fftSize)

    private fun prepareWaterfallRow(values: FloatArray, pixels: IntArray) {
        if (pixels.isEmpty()) return
        if (waterfallSmoothedPower.size != pixels.size) waterfallSmoothedPower = FloatArray(pixels.size)
        val configured = settings
        val colors = palette
        val group = values.size.toFloat() / pixels.size
        for (pixel in pixels.indices) {
            val start = (pixel * group).toInt()
            val end = max(start + 1, ((pixel + 1) * group).toInt()).coerceAtMost(values.size)
            var power = 0.0
            for (index in start until end) power += 10.0.pow(values[index] / 10.0)
            val meanPower = (power / (end - start).coerceAtLeast(1)).toFloat()
            val alpha = 1f / configured.waterfallAverageFrames
            waterfallSmoothedPower[pixel] = if (waterfallSmoothedPower[pixel] == 0f) meanPower
                else waterfallSmoothedPower[pixel] + alpha * (meanPower - waterfallSmoothedPower[pixel])
            val db = (10.0 / ln(10.0) * ln(waterfallSmoothedPower[pixel].coerceAtLeast(1.0e-14f).toDouble())).toFloat()
            val normalized = ((db - configured.waterfallMinDb) / (configured.waterfallMaxDb - configured.waterfallMinDb)).coerceIn(0f, 1f)
            val corrected = normalized.pow(configured.waterfallGamma)
            pixels[pixel] = colors[(corrected * 255).toInt().coerceIn(0, 255)]
        }
    }

    private fun appendWaterfallRow(pixels: IntArray) {
        val bitmap = waterfallBitmap ?: return
        if (pixels.size != bitmap.width) return
        waterfallHead = if (waterfallHead == 0) bitmap.height - 1 else waterfallHead - 1
        bitmap.setPixels(pixels, 0, bitmap.width, 0, waterfallHead, bitmap.width, 1)
        waterfallRevision++
    }

    private fun rebuildPalette() {
        palette = IntArray(256) { index ->
            val t = index / 255f
            when (settings.palette) {
                PanadapterPalette.GREYSCALE -> Color.rgb((t * 255).toInt(), (t * 255).toInt(), (t * 255).toInt())
                PanadapterPalette.VIRIDIS -> Color.rgb((68 + 185 * t).toInt().coerceIn(0, 255),
                    (1 + 220 * t).toInt().coerceIn(0, 255), (84 + 30 * (1 - t)).toInt().coerceIn(0, 255))
                PanadapterPalette.FLIGHTLINE -> when {
                    t < .33f -> Color.rgb(4, (t * 120).toInt(), (35 + t * 300).toInt().coerceAtMost(130))
                    t < .72f -> Color.rgb(((t - .33f) * 520).toInt(), (70 + (t - .33f) * 360).toInt(), 32)
                    else -> Color.rgb(255, (210 + (t - .72f) * 160).toInt().coerceAtMost(255), ((t - .72f) * 700).toInt().coerceAtMost(255))
                }
            }
        }
    }

    fun exportSupportSnapshot(): String = runCatching {
        val radio = radioState(); val current = frame
        val output = JSONObject().put("version", 1).put("created_at", System.currentTimeMillis())
            .put("app", "RigWeave Android").put("device_model", Build.MODEL).put("android", Build.VERSION.RELEASE)
            .put("lifecycle", lifecycle.name).put("status", status)
            .put("selected_product", audio.selectedRx?.name ?: "none")
            .put("selected_usb", audio.selectedRx?.usbIdentity ?: "unavailable")
            .put("preferred_accepted", routeProof.preferredAccepted).put("actual_route_match", routeProof.verified)
            .put("requested_rate", routeProof.requestedRate).put("configured_rate", routeProof.configuredRate)
            .put("channels", routeProof.configuredChannels).put("encoding", routeProof.encoding)
            .put("client_format", routeProof.clientFormat).put("device_format", routeProof.deviceFormat)
            .put("fft", current?.fftSize ?: settings.fftSize).put("hop", current?.hopSize ?: 0)
            .put("sequence", current?.sequence ?: 0).put("drops", current?.discontinuities ?: 0)
            .put("published_fps", publishedFps).put("waterfall_fps", waterfallFps)
            .put("capture_to_display_estimate_ms", latencyEstimateMs)
            .put("peak_dbfs", current?.peakDb ?: -140f).put("floor_dbfs", current?.floorDb ?: -140f)
            .put("stereo_valid", current?.validStereo ?: false).put("cat_model", radio.model)
            .put("measured_flatness", measuredFlatnessActive())
            .put("level_calibrated", levelCalibrationActive())
            .put("level_units", if (levelCalibrationActive()) "dBm" else "dBFS")
            .put("dbfs_to_dbm_offset", settings.dbfsToDbmOffset)
            .put("cat_revision", radio.revision).put("transmitting", radio.transmitting)
        val directory = File(context.filesDir, "panadapter").apply { mkdirs() }
        val file = File(directory, "support-${System.currentTimeMillis()}.json")
        file.writeText(output.toString(2)); supportExportPath = file.absolutePath; "Support snapshot exported"
    }.getOrElse { "Support export failed: ${it.message}" }

    fun close() {
        stop(); stopReplay(); wantedLive = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        waterfallBitmap?.recycle(); waterfallBitmap = null
        NativePanadapter.destroy(nativeHandle)
    }
}

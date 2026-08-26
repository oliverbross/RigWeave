// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class ScannerMode { MEMORY, RANGE, FFT_SPAN }
enum class ScannerState { STOPPED, ARMED, TUNING, DWELLING, HOLDING, ERROR }
enum class ScannerResumePolicy { CARRIER_DROP, TIMED, MANUAL }

data class ScannerConfig(
    val mode: ScannerMode = ScannerMode.MEMORY,
    val startHz: Long = 14_000_000,
    val endHz: Long = 14_350_000,
    val stepHz: Long = 1_000,
    val radioMode: String = "USB",
    val filterHz: Int = 2_700,
    val thresholdDb: Float = -90f,
    val dwellMillis: Long = 1_500,
    val resumePolicy: ScannerResumePolicy = ScannerResumePolicy.CARRIER_DROP,
    val skipHz: Set<Long> = emptySet(),
) {
    fun validated() = copy(
        startHz = startHz.coerceIn(100_000, 10_500_000_000),
        endHz = endHz.coerceIn(startHz + 1, 10_500_000_000),
        stepHz = stepHz.coerceIn(10, 1_000_000),
        filterHz = filterHz.coerceIn(50, 100_000),
        thresholdDb = thresholdDb.coerceIn(-140f, 0f),
        dwellMillis = dwellMillis.coerceIn(100, 60_000),
        skipHz = skipHz.take(256).toSet(),
    )
}

data class ScannerSnapshot(
    val state: ScannerState = ScannerState.STOPPED,
    val mode: ScannerMode = ScannerMode.MEMORY,
    val currentHz: Long = 0,
    val signalDb: Float? = null,
    val candidateCount: Int = 0,
    val cycles: Long = 0,
    val stopReason: String = "Explicit Start required",
)

class ReceiveOnlyScannerController(
    private val tuneReceive: suspend (Long, String, Int) -> Boolean,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    var config by mutableStateOf(ScannerConfig()); private set
    var snapshot by mutableStateOf(ScannerSnapshot()); private set

    fun updateConfig(value: ScannerConfig) {
        require(snapshot.state == ScannerState.STOPPED) { "Stop scanning before changing its operating context" }
        config = value.validated()
    }

    fun startMemory(memories: List<RadioPreset>) = start(memories.map { it.frequencyHz to it.mode })

    fun startRange() {
        val value = config.validated()
        val channels = generateSequence(value.startHz) { it + value.stepHz }
            .takeWhile { it <= value.endHz }.filterNot(value.skipHz::contains).take(10_000)
            .map { it to value.radioMode }.toList()
        start(channels)
    }

    fun startFft(trace: FloatArray, centerHz: Long, spanHz: Long) {
        val candidates = fftCandidates(trace, centerHz, spanHz, config.thresholdDb, 10_000)
            .filterNot(config.skipHz::contains).map { it to config.radioMode }
        start(candidates)
    }

    private fun start(channels: List<Pair<Long, String>>) {
        if (scanJob != null || channels.isEmpty()) {
            if (channels.isEmpty()) snapshot = snapshot.copy(state = ScannerState.ERROR, stopReason = "No receive-only scan candidates")
            return
        }
        snapshot = ScannerSnapshot(ScannerState.ARMED, config.mode, candidateCount = channels.size, stopReason = "")
        scanJob = scope.launch {
            var cycles = 0L
            while (isActive) {
                for ((frequency, mode) in channels) {
                    if (!isActive) break
                    snapshot = snapshot.copy(state = ScannerState.TUNING, currentHz = frequency, cycles = cycles)
                    if (!tuneReceive(frequency, mode, config.filterHz)) {
                        stop("Receive tune unavailable")
                        return@launch
                    }
                    snapshot = snapshot.copy(state = ScannerState.DWELLING, currentHz = frequency)
                    delay(config.dwellMillis)
                    if (config.resumePolicy == ScannerResumePolicy.MANUAL) {
                        snapshot = snapshot.copy(state = ScannerState.HOLDING, stopReason = "Manual resume selected")
                        return@launch
                    }
                }
                cycles++
            }
        }
    }

    fun stop(reason: String = "Stopped by operator") {
        scanJob?.cancel(); scanJob = null
        snapshot = snapshot.copy(state = ScannerState.STOPPED, stopReason = reason)
    }

    fun onBackground() = stop("Background safety stop")
    fun onRadioDisconnect() = stop("Radio disconnected")
    fun onProfileChange() = stop("Radio profile changed")
    fun onManualTune() { if (snapshot.state != ScannerState.STOPPED) stop("Manual operator tune") }
    fun globalStop() = stop("Global Stop")
    override fun close() { stop("Controller closed"); scope.cancel() }
}

internal fun fftCandidates(trace: FloatArray, centerHz: Long, spanHz: Long, thresholdDb: Float, maximum: Int): List<Long> {
    if (trace.size < 3 || spanHz <= 0 || maximum <= 0) return emptyList()
    return (1 until trace.lastIndex).asSequence()
        .filter { trace[it].isFinite() && trace[it] >= thresholdDb && trace[it] >= trace[it - 1] && trace[it] > trace[it + 1] }
        .sortedByDescending { trace[it] }.take(maximum)
        .map { centerHz - spanHz / 2 + spanHz * it / trace.size }.toList()
}

data class BandStackEntry(val frequencyHz: Long, val mode: String, val filterHz: Int, val receiverId: String, val epoch: Long)

class BandStackStore(context: Context) {
    private val prefs = context.getSharedPreferences("rigweave-sdr", Context.MODE_PRIVATE)
    var depth by mutableStateOf(prefs.getInt("band_stack_depth", 3).coerceIn(1, 12)); private set
    var stacks by mutableStateOf(load()); private set

    fun updateDepth(value: Int) {
        depth = value.coerceIn(1, 12)
        stacks = stacks.mapValues { it.value.take(depth) }
        persist()
    }

    fun record(band: String, entry: BandStackEntry) {
        if (band.isBlank() || entry.frequencyHz <= 0) return
        stacks = stacks + (band to (listOf(entry) + stacks[band].orEmpty().filterNot { it.frequencyHz == entry.frequencyHz && it.mode == entry.mode }).take(depth))
        persist()
    }

    fun cycle(band: String, currentHz: Long): BandStackEntry? {
        val rows = stacks[band].orEmpty()
        if (rows.isEmpty()) return null
        val index = rows.indexOfFirst { it.frequencyHz == currentHz }
        return rows[(index + 1).mod(rows.size)]
    }

    private fun load(): Map<String, List<BandStackEntry>> = runCatching {
        val root = JSONObject(prefs.getString("band_stacks_v1", "{}"))
        root.keys().asSequence().associateWith { band ->
            val rows = root.getJSONArray(band)
            List(rows.length().coerceAtMost(12)) { index -> rows.getJSONObject(index).let { row ->
                BandStackEntry(row.getLong("hz"), row.getString("mode"), row.getInt("filter"), row.optString("receiver"), row.getLong("epoch"))
            } }
        }
    }.getOrDefault(emptyMap())

    private fun persist() {
        val root = JSONObject()
        stacks.forEach { (band, entries) -> root.put(band, JSONArray(entries.map { JSONObject()
            .put("hz", it.frequencyHz).put("mode", it.mode).put("filter", it.filterHz)
            .put("receiver", it.receiverId).put("epoch", it.epoch) })) }
        prefs.edit().putInt("band_stack_depth", depth).putString("band_stacks_v1", root.toString()).apply()
    }
}

enum class RfEvidenceClass { OBSERVED, HISTORICAL, OUTLOOK }
enum class RfPrecision { EXACT, GRID, COARSE }

data class RfObservation(
    val id: String,
    val source: String,
    val evidence: RfEvidenceClass,
    val epoch: Long,
    val callsign: String,
    val band: String,
    val mode: String,
    val transmitterLatitude: Double,
    val transmitterLongitude: Double,
    val receiverLatitude: Double,
    val receiverLongitude: Double,
    val precision: RfPrecision,
    val snr: Int? = null,
    val worked: Boolean? = null,
    val confirmed: Boolean? = null,
    val needed: Set<String> = emptySet(),
    val contestMultiplier: Boolean? = null,
    val contestDuplicate: Boolean? = null,
    val chaserPriority: Int? = null,
    val transmitterRegion: String = "",
    val receiverRegion: String = "",
    val continent: String = "",
    val entity: String = "",
    val cqZone: Int? = null,
    val ituZone: Int? = null,
    val bandHealth: String = "",
)

data class RfFilters(
    val sources: Set<String> = emptySet(),
    val bands: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
    val evidence: Set<RfEvidenceClass> = emptySet(),
    val maximumAgeSeconds: Long = 3_600,
    val callsign: String = "",
    val worked: Boolean? = null,
    val confirmed: Boolean? = null,
    val needed: String? = null,
    val contestMultipliersOnly: Boolean = false,
    val hideContestDuplicates: Boolean = false,
    val minimumChaserPriority: Int? = null,
    val longPath: Boolean = false,
    val transmitterRegions: Set<String> = emptySet(),
    val receiverRegions: Set<String> = emptySet(),
    val continents: Set<String> = emptySet(),
    val entities: Set<String> = emptySet(),
    val cqZones: Set<Int> = emptySet(),
    val ituZones: Set<Int> = emptySet(),
    val minimumDistanceKm: Double? = null,
    val maximumDistanceKm: Double? = null,
    val minimumBearingDegrees: Double? = null,
    val maximumBearingDegrees: Double? = null,
    val maximumSourceAgeSeconds: Long? = null,
    val bandHealth: Set<String> = emptySet(),
    val outlookMinutes: Set<Int> = setOf(30, 60, 120),
)

data class GeoArcPoint(val latitude: Double, val longitude: Double)

class RfObservationController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var generation = 0L
    var observations by mutableStateOf<List<RfObservation>>(emptyList()); private set
    var filtered by mutableStateOf<List<RfObservation>>(emptyList()); private set
    var filters by mutableStateOf(RfFilters()); private set
    var filterMillis by mutableStateOf(0L); private set
    var selectedId by mutableStateOf<String?>(null); private set

    fun submit(rows: List<RfObservation>) {
        observations = rows.takeLast(100_000)
        refilter(filters)
    }

    fun updateFilters(value: RfFilters) { filters = value; refilter(value) }
    fun resetFilters() = updateFilters(RfFilters())
    fun select(id: String?) { selectedId = id?.takeIf { key -> filtered.any { it.id == key } } }

    private fun refilter(value: RfFilters) {
        val localGeneration = ++generation
        val source = observations
        scope.launch {
            val start = System.nanoTime()
            val now = Instant.now().epochSecond
            val needle = value.callsign.trim().uppercase(Locale.US)
            val result = source.asSequence().filter { row ->
                val distance = greatCircleDistanceKm(row.transmitterLatitude, row.transmitterLongitude,
                    row.receiverLatitude, row.receiverLongitude)
                val bearing = initialBearingDegrees(row.transmitterLatitude, row.transmitterLongitude,
                    row.receiverLatitude, row.receiverLongitude)
                (value.sources.isEmpty() || row.source in value.sources) &&
                    (value.bands.isEmpty() || row.band in value.bands) &&
                    (value.modes.isEmpty() || row.mode.uppercase(Locale.US) in value.modes) &&
                    (value.evidence.isEmpty() || row.evidence in value.evidence) &&
                    now - row.epoch <= value.maximumAgeSeconds &&
                    (needle.isBlank() || needle in row.callsign.uppercase(Locale.US)) &&
                    (value.worked == null || row.worked == value.worked) &&
                    (value.confirmed == null || row.confirmed == value.confirmed) &&
                    (value.needed == null || value.needed in row.needed) &&
                    (!value.contestMultipliersOnly || row.contestMultiplier == true) &&
                    (!value.hideContestDuplicates || row.contestDuplicate != true) &&
                    (value.minimumChaserPriority == null || (row.chaserPriority ?: Int.MIN_VALUE) >= value.minimumChaserPriority) &&
                    (value.transmitterRegions.isEmpty() || row.transmitterRegion in value.transmitterRegions) &&
                    (value.receiverRegions.isEmpty() || row.receiverRegion in value.receiverRegions) &&
                    (value.continents.isEmpty() || row.continent in value.continents) &&
                    (value.entities.isEmpty() || row.entity in value.entities) &&
                    (value.cqZones.isEmpty() || row.cqZone in value.cqZones) &&
                    (value.ituZones.isEmpty() || row.ituZone in value.ituZones) &&
                    (value.minimumDistanceKm == null || distance >= value.minimumDistanceKm) &&
                    (value.maximumDistanceKm == null || distance <= value.maximumDistanceKm) &&
                    bearingInRange(bearing, value.minimumBearingDegrees, value.maximumBearingDegrees) &&
                    (value.maximumSourceAgeSeconds == null || now - row.epoch <= value.maximumSourceAgeSeconds) &&
                    (value.bandHealth.isEmpty() || row.bandHealth in value.bandHealth) &&
                    (row.evidence != RfEvidenceClass.OUTLOOK || value.outlookMinutes.any { now - row.epoch <= it * 60L })
            }.take(100_000).toList()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (localGeneration == generation) {
                filtered = result
                filterMillis = elapsed
                if (selectedId !in result.map(RfObservation::id).toSet()) selectedId = null
            }
        }
    }

    fun close() = scope.cancel()
}

internal fun greatCircle(aLat: Double, aLon: Double, bLat: Double, bLon: Double, longPath: Boolean = false, points: Int = 64): List<GeoArcPoint> {
    val aPhi = Math.toRadians(aLat); val aLambda = Math.toRadians(aLon)
    val bPhi = Math.toRadians(bLat); val bLambda = Math.toRadians(bLon)
    val av = doubleArrayOf(cos(aPhi) * cos(aLambda), cos(aPhi) * sin(aLambda), sin(aPhi))
    val bv = doubleArrayOf(cos(bPhi) * cos(bLambda), cos(bPhi) * sin(bLambda), sin(bPhi))
    var omega = acos((av[0] * bv[0] + av[1] * bv[1] + av[2] * bv[2]).coerceIn(-1.0, 1.0))
    if (omega < 1e-9) return listOf(GeoArcPoint(aLat, aLon), GeoArcPoint(bLat, bLon))
    if (longPath) omega -= 2 * PI
    val denominator = sin(omega)
    return List(points.coerceIn(2, 256)) { index ->
        val t = index.toDouble() / (points.coerceIn(2, 256) - 1)
        val first = sin((1 - t) * omega) / denominator
        val second = sin(t * omega) / denominator
        val x = first * av[0] + second * bv[0]
        val y = first * av[1] + second * bv[1]
        val z = first * av[2] + second * bv[2]
        GeoArcPoint(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), normalizeLongitude(Math.toDegrees(atan2(y, x))))
    }
}

internal fun propagationControlPoints(row: RfObservation, longPath: Boolean): List<GeoArcPoint> {
    val distance = greatCircleDistanceKm(row.transmitterLatitude, row.transmitterLongitude, row.receiverLatitude, row.receiverLongitude)
    if (distance < 1_000) return emptyList()
    val hops = (distance / 3_500.0).toInt().coerceIn(1, 5)
    val path = greatCircle(row.transmitterLatitude, row.transmitterLongitude, row.receiverLatitude, row.receiverLongitude, longPath, hops * 2 + 1)
    return (1..hops).map { path[(it * (path.lastIndex.toDouble() / (hops + 1))).toInt().coerceIn(1, path.lastIndex - 1)] }
}

internal fun greatCircleDistanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat)
    val dp = p2 - p1; val dl = Math.toRadians(bLon - aLon)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 6371.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
}

internal fun initialBearingDegrees(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val p1 = Math.toRadians(aLat)
    val p2 = Math.toRadians(bLat)
    val dl = Math.toRadians(bLon - aLon)
    return (Math.toDegrees(atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl))) + 360.0) % 360.0
}

private fun bearingInRange(value: Double, minimum: Double?, maximum: Double?): Boolean {
    if (minimum == null && maximum == null) return true
    val low = minimum ?: 0.0
    val high = maximum ?: 360.0
    return if (low <= high) value in low..high else value >= low || value <= high
}

private fun normalizeLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

data class AnnouncementSettings(
    val enabled: Boolean = false,
    val frequency: Boolean = true,
    val bandMode: Boolean = true,
    val allocationWarning: Boolean = true,
    val highSwr: Boolean = true,
    val addressedDigi: Boolean = false,
    val routeLoss: Boolean = true,
    val speechRate: Float = 1f,
    val voiceName: String = "",
)

class SpokenAnnouncementController(
    context: Context,
    private val transmitting: () -> Boolean,
    private val voiceMacroBusy: () -> Boolean,
    private val quietProfile: () -> Boolean,
) : TextToSpeech.OnInitListener, AutoCloseable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = appContext.getSharedPreferences("rigweave-sdr", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = TextToSpeech(appContext, this)
    private var tuneJob: Job? = null
    private var lastHighSwrAt = 0L
    var available by mutableStateOf(false); private set
    var speaking by mutableStateOf(false); private set
    var settings by mutableStateOf(load()); private set

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { scope.launch { speaking = true } }
            override fun onDone(utteranceId: String?) = releaseAudioFocus()
            @Deprecated("Android TTS callback")
            override fun onError(utteranceId: String?) = releaseAudioFocus()
        })
    }

    override fun onInit(status: Int) {
        available = status == TextToSpeech.SUCCESS
        if (available) applyEngineSettings()
    }

    fun update(value: AnnouncementSettings) {
        settings = value.copy(speechRate = value.speechRate.coerceIn(.5f, 2f))
        prefs.edit().putString("announcements_v1", JSONObject()
            .put("enabled", settings.enabled).put("frequency", settings.frequency).put("band_mode", settings.bandMode)
            .put("allocation", settings.allocationWarning).put("swr", settings.highSwr).put("digi", settings.addressedDigi)
            .put("route", settings.routeLoss).put("rate", settings.speechRate.toDouble()).put("voice", settings.voiceName).toString()).apply()
        applyEngineSettings()
        if (!settings.enabled) stop()
    }

    fun announceTuning(frequencyHz: Long, band: String, mode: String) {
        if (!settings.frequency || frequencyHz <= 0) return
        tuneJob?.cancel()
        tuneJob = scope.launch { delay(650); speak(tuningAnnouncementText(frequencyHz, band, mode)) }
    }

    fun announceBandMode(band: String, mode: String) { if (settings.bandMode) speak("$band, $mode") }
    fun announceAllocationWarning(message: String) { if (settings.allocationWarning) speak(message) }
    fun announceHighSwr(swr: Double?) {
        val now = System.currentTimeMillis()
        if (settings.highSwr && swr != null && swr >= 3.0 && now - lastHighSwrAt >= 10_000) {
            lastHighSwrAt = now
            speak("Warning. High S W R, ${"%.1f".format(Locale.US, swr)}")
        }
    }
    fun announceAddressedDigi(message: String) { if (settings.addressedDigi) speak(message.take(180)) }
    fun announceRouteLoss(message: String) { if (settings.routeLoss) speak(message.take(180)) }

    private fun speak(text: String) {
        if (!announcementAllowed(settings.enabled, available, quietProfile(), transmitting(), voiceMacroBusy(), text)) return
        @Suppress("DEPRECATION")
        val focus = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rigweave-${UUID.randomUUID()}") != TextToSpeech.SUCCESS) {
            releaseAudioFocus()
        }
    }

    fun stop() { tuneJob?.cancel(); tuneJob = null; engine.stop(); releaseAudioFocus() }
    fun globalStop() = stop()
    override fun close() { stop(); engine.shutdown(); scope.cancel() }

    private fun applyEngineSettings() {
        if (!available) return
        engine.setSpeechRate(settings.speechRate)
        if (settings.voiceName.isNotBlank()) engine.voices?.firstOrNull { it.name == settings.voiceName }?.let { engine.voice = it }
    }

    private fun releaseAudioFocus() {
        @Suppress("DEPRECATION")
        audioManager.abandonAudioFocus(null)
        scope.launch { speaking = false }
    }

    private fun load(): AnnouncementSettings = runCatching {
        val row = JSONObject(prefs.getString("announcements_v1", "{}"))
        AnnouncementSettings(row.optBoolean("enabled"), row.optBoolean("frequency", true), row.optBoolean("band_mode", true),
            row.optBoolean("allocation", true), row.optBoolean("swr", true), row.optBoolean("digi"), row.optBoolean("route", true),
            row.optDouble("rate", 1.0).toFloat(), row.optString("voice"))
    }.getOrDefault(AnnouncementSettings())
}

private fun spokenFrequency(hz: Long): String = "%.6f megahertz".format(Locale.US, hz / 1_000_000.0)

internal fun tuningAnnouncementText(frequencyHz: Long, band: String, mode: String): String =
    "$band, $mode, ${spokenFrequency(frequencyHz)}"

internal fun announcementAllowed(enabled: Boolean, available: Boolean, quiet: Boolean, transmitting: Boolean,
    voiceMacroBusy: Boolean, text: String): Boolean =
    enabled && available && !quiet && !transmitting && !voiceMacroBusy && text.isNotBlank()

class DebugSdrLab(
    private val runtime: TciRuntimeState,
    private val panadapter: PanadapterController,
    private val rf: RfObservationController,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var active by mutableStateOf(false); private set
    private var job: Job? = null

    fun start() {
        check(BuildConfig.DEBUG) { "Debug SDR lab is unavailable in release builds" }
        if (active) return
        active = true
        val now = Instant.now().epochSecond
        val receivers = listOf(
            TciReceiverSnapshot("tci:0", 0, "DEMO RX 1", active = true, listening = true, vfoAHz = 14_074_000, vfoBHz = 14_095_600,
                mode = "DIGU", passbandHz = 3_000, sampleRate = 96_000, iqRunning = true),
            TciReceiverSnapshot("tci:1", 1, "DEMO RX 2", vfoAHz = 7_074_000, vfoBHz = 7_038_600,
                mode = "DIGU", passbandHz = 3_000, sampleRate = 96_000, iqRunning = true),
        )
        runtime.publish(TciRuntimeSnapshot(1, TciConnectionState.READY, "TCI", "DEBUG", "DEMO · NO RADIO", true, 2, 2,
            "tci:0", "tci:0", receivers))
        rf.submit((0 until 120).map { index -> RfObservation("demo-$index", listOf("PSK", "WSPR", "RBN")[index % 3],
            RfEvidenceClass.OBSERVED, now - index * 20L, "DEMO${index + 1}", listOf("20m", "40m", "15m")[index % 3],
            if (index % 2 == 0) "FT8" else "WSPR", -12.0, 130.0, -70.0 + index % 35 * 4.0,
            -170.0 + index * 17.0 % 340.0, RfPrecision.GRID, -24 + index % 30) })
        job = scope.launch {
            var phase = 0.0
            while (isActive && active) {
                repeat(2) { receiver ->
                    val samples = FloatArray(4_096)
                    val tone = if (receiver == 0) 1_100.0 else 2_400.0
                    for (index in samples.indices step 2) {
                        val t = phase + index / 2.0
                        samples[index] = (.45 * sin(2 * PI * tone * t / 96_000.0) + .04 * sin(t * .17)).toFloat()
                        samples[index + 1] = (.45 * cos(2 * PI * tone * t / 96_000.0) + .04 * cos(t * .13)).toFloat()
                    }
                    panadapter.pushTciIq(receiver, 96_000, samples)
                }
                phase += 2_048
                delay(22)
            }
        }
    }

    fun stop() {
        active = false; job?.cancel(); job = null
        panadapter.detachTciSources("Debug SDR lab stopped")
        runtime.publish(TciRuntimeSnapshot(lastError = "DEMO · NO RADIO stopped"))
        rf.submit(emptyList())
    }

    override fun close() { stop(); scope.cancel() }
}

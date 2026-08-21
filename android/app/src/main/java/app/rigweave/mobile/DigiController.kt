package app.rigweave.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.math.roundToInt

enum class DigiMode(
    val label: String,
    val slotDecoder: Int? = null,
    val slotMillis: Long = 0,
    val txSupported: Boolean = true,
    val family: String = label,
) {
    FT8("FT8", 0, 15_000), FT4("FT4", 1, 7_500), FT2("FT2", 11, 3_750),
    FST4("FST4-15", 2, 15_000, true, "FST4"),
    FST4_30("FST4-30", 3, 30_000, true, "FST4"),
    FST4_60("FST4-60", 4, 60_000, true, "FST4"),
    FST4_120("FST4-120", 5, 120_000, true, "FST4"),
    FST4_300("FST4-300", 6, 300_000, true, "FST4"),
    Q65_15A("Q65-15A", 100, 15_000, true, "Q65"), Q65_15B("Q65-15B", 101, 15_000, true, "Q65"),
    Q65_15C("Q65-15C", 102, 15_000, true, "Q65"), Q65_15D("Q65-15D", 103, 15_000, true, "Q65"),
    Q65_15E("Q65-15E", 104, 15_000, true, "Q65"), Q65("Q65-30A", 105, 30_000, true, "Q65"),
    Q65_30B("Q65-30B", 106, 30_000, true, "Q65"), Q65_30C("Q65-30C", 107, 30_000, true, "Q65"),
    Q65_30D("Q65-30D", 108, 30_000, true, "Q65"), Q65_30E("Q65-30E", 109, 30_000, true, "Q65"),
    Q65_60A("Q65-60A", 110, 60_000, true, "Q65"), Q65_60B("Q65-60B", 111, 60_000, true, "Q65"),
    Q65_60C("Q65-60C", 112, 60_000, true, "Q65"), Q65_60D("Q65-60D", 113, 60_000, true, "Q65"),
    Q65_60E("Q65-60E", 114, 60_000, true, "Q65"), Q65_120A("Q65-120A", 115, 120_000, true, "Q65"),
    Q65_120B("Q65-120B", 116, 120_000, true, "Q65"), Q65_120C("Q65-120C", 117, 120_000, true, "Q65"),
    Q65_120D("Q65-120D", 118, 120_000, true, "Q65"), Q65_120E("Q65-120E", 119, 120_000, true, "Q65"),
    Q65_300A("Q65-300A", 120, 300_000, true, "Q65"), Q65_300B("Q65-300B", 121, 300_000, true, "Q65"),
    Q65_300C("Q65-300C", 122, 300_000, true, "Q65"), Q65_300D("Q65-300D", 123, 300_000, true, "Q65"),
    Q65_300E("Q65-300E", 124, 300_000, true, "Q65"),
    MSK144_5("MSK144-5", 130, 5_000, true, "MSK144"),
    MSK144_10("MSK144-10", 131, 10_000, true, "MSK144"),
    MSK144("MSK144-15", 132, 15_000, true, "MSK144"),
    MSK144_30("MSK144-30", 133, 30_000, true, "MSK144"),
    JT65("JT65A", 9, 60_000, true, "JT65"),
    JT65_B("JT65B", 12, 60_000, true, "JT65"),
    JT65_C("JT65C", 13, 60_000, true, "JT65"),
    WSPR("WSPR", 10, 120_000, true),
    CW("CW"), RTTY("RTTY"), PSK31("PSK31"), SSTV("SSTV");

    val isSlotted get() = slotDecoder != null
}

val DigiModeFamilies = listOf(
    DigiMode.FT8, DigiMode.FT4, DigiMode.FT2, DigiMode.FST4, DigiMode.Q65,
    DigiMode.MSK144, DigiMode.JT65, DigiMode.WSPR, DigiMode.CW, DigiMode.RTTY,
    DigiMode.PSK31, DigiMode.SSTV,
)
enum class DigiTxPhase { SAFE, SEQUENCING, PTT_CONFIRMED }

data class DigiDecodeRow(val text: String, val frequencyHz: Float, val dtSeconds: Float, val snrDb: Float)

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
    private val stationCallsign: () -> String,
    private val stationGrid: () -> String,
    private val dependencies: DigiDependencies,
) : AutoCloseable {
    private val prefs = context.getSharedPreferences("rigweave-digi", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val receiving = AtomicBoolean(false)
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private var slotDecodeJob: Job? = null
    private var pskDecodeJob: Job? = null
    private var issJob: Job? = null
    private var recorder: AudioRecord? = null
    private var flexRxOwned = false
    private var nativeHandle = 0L
    private var sstvRgb = ByteArray(0)
    private var sstvWidth = 0
    private var sstvHeight = 0
    private var sourceRgb = ByteArray(0)
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceOriginal: Bitmap? = null
    private var lastSavedSstvSignature = ""
    private var sourceFilename = ""
    private var slotPcm = FloatArray(0)
    private var slotUsed = 0
    private var slotSkipSamples = -1
    private var pskPcm = FloatArray(0)
    private var pskLastDecoded = 0
    private val sessionStore = DigiSessionStore(context)
    private val rawRecorder = DigiRawRecorder(context)
    private val sequencer = DigiFtSequencer(stationCallsign)
    private val interop = DigiWsjtInterop(::haltTx, ::clear, ::redecodeLastSlot)
    private var sessionId = ""
    private var lastFrameMonotonicMs = 0L
    private var framesReceived = 0L
    private var lastSpectrumPublishedMs = 0L
    private var lastCompletedSlot = FloatArray(0)
    private var lastCompletedSlotMode: DigiMode? = null
    private var closed = false
    private var lastRadioIdentity = ""
    private var lastRadioFrequency = 0L
    private var lastRadioConnected = false
    private var resumeRxOnForeground = false
    private var lastStableRoute = ""

    var settings by mutableStateOf(DigiSettingsDocument.parse(prefs.getString("settings_v1", null))); private set

    var mode by mutableStateOf(runCatching { DigiMode.valueOf(prefs.getString("mode", DigiMode.CW.name)!!) }.getOrDefault(DigiMode.CW)); private set
    var status by mutableStateOf("RX stopped · choose a mode and start the DigiRig input"); private set
    var transcript by mutableStateOf(""); private set
    var rxActive by mutableStateOf(false); private set
    var txActive by mutableStateOf(false); private set
    var txPhase by mutableStateOf(DigiTxPhase.SAFE); private set
    var txArmed by mutableStateOf(false); private set
    var txEnabled by mutableStateOf(false); private set
    var lastTxText by mutableStateOf(""); private set
    var txText by mutableStateOf(prefs.getString("tx_text_${mode.name}", null) ?: defaultTxText(mode)); private set
    var dxCall by mutableStateOf(prefs.getString("dx_call", "").orEmpty()); private set
    var cwWpm by mutableIntStateOf(prefs.getInt("cw_wpm", 20)); private set
    var cwPitchHz by mutableFloatStateOf(prefs.getFloat("cw_pitch", 700f)); private set
    var rttyReverse by mutableStateOf(false); private set
    var rttyAfcHz by mutableFloatStateOf(0f); private set
    var rttyAfcLocked by mutableStateOf(false); private set
    var pskCarrierHz by mutableFloatStateOf(1_000f); private set
    var sstvChoice by mutableStateOf(SstvChoices.firstOrNull { it.index == prefs.getInt("sstv_mode", 2) } ?: SstvChoices[2]); private set
    var sstvLine by mutableIntStateOf(-1); private set
    var sstvComplete by mutableStateOf(false); private set
    var sstvFskId by mutableStateOf(""); private set
    var imageRevision by mutableIntStateOf(0); private set
    var sourceRevision by mutableIntStateOf(0); private set
    var sourceReady by mutableStateOf(false); private set
    var sstvFrameOffset by mutableFloatStateOf(0f); private set
    var slotProgress by mutableFloatStateOf(0f); private set
    var decodedRows by mutableStateOf(emptyList<DigiDecodeRow>()); private set
    var decodeHistory by mutableStateOf(runCatching { sessionStore.recentDecodes() }.getOrDefault(emptyList())); private set
    var selectedDecode by mutableStateOf<DigiDecodeEvent?>(null); private set
    var operatingView by mutableStateOf(DigiOperatingView.CLASSIC); private set
    var spectrumRow by mutableStateOf(FloatArray(384)); private set
    var waterfallRows by mutableStateOf(emptyList<FloatArray>()); private set
    var waterfallState by mutableStateOf(DigiWaterfallState.LIVE); private set
    var audioHealth by mutableStateOf(DigiAudioHealth()); private set
    var sstvHealth by mutableStateOf(SstvReceiveHealth()); private set
    var ftSequence by mutableStateOf(sequencer.snapshot); private set
    var gallery by mutableStateOf(runCatching { sessionStore.gallery() }.getOrDefault(emptyList())); private set
    var diagnostics by mutableStateOf(emptyList<DigiDiagnostic>()); private set
    var pendingDraft by mutableStateOf<DigiQsoDraft?>(null); private set
    var lastLoggedQsoId by mutableStateOf(""); private set
    var issSessionEnabled by mutableStateOf(false); private set
    var issPass by mutableStateOf<DigiIssPass?>(null); private set
    var rawRecordingActive by mutableStateOf(false); private set
    val interopState get() = interop.state
    val rxAudioHz get() = settings.rxOffsets[mode.family] ?: defaultAudioOffset(mode)
    val txAudioHz get() = settings.txOffsets[mode.family] ?: defaultAudioOffset(mode)
    val capability get() = DigiCapabilities.forMode(mode)
    fun isModeVisible(value: DigiMode): Boolean {
        val entry = DigiCapabilities.forMode(value)
        return entry.visible && entry.stableId !in settings.hiddenModeIds
    }

    init {
        runCatching { DigiMode.valueOf(settings.selectedMode) }.getOrNull()?.let { mode = it }
        cwWpm = settings.cwWpm
        cwPitchHz = settings.cwPitchHz
        pskCarrierHz = settings.pskCarrierHz
        rttyReverse = settings.rttyReverse
        sstvChoice = SstvChoices.firstOrNull { it.index == settings.sstvMode } ?: SstvChoices[2]
        if (settings.udpEnabled) interop.start(settings.udpHost, settings.udpPort, settings.companionMode)
    }

    fun selectMode(value: DigiMode) {
        if (mode == value) return
        stopRx("Mode changed")
        disarm()
        txEnabled = false
        prefs.edit().putString("tx_text_${mode.name}", txText).apply()
        mode = value
        prefs.edit().putString("mode", value.name).apply()
        updateSettings { it.copy(selectedMode = value.name) }
        txText = prefs.getString("tx_text_${value.name}", null) ?: defaultTxText(value)
        transcript = ""
        decodedRows = emptyList()
        resetSlotBuffer()
        resetPskBuffer()
        status = "${value.label} selected · RX is stopped"
        if (value == DigiMode.SSTV && settings.sstvAutoArm) scope.launch { startRx() }
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

    fun updateTxEnabled(value: Boolean) {
        if (value && settings.companionMode) {
            status = "Companion mode owns decode interoperability; local TX remains disabled"
            return
        }
        txEnabled = value
        if (!value) { disarm(); sequencer.stop(); ftSequence = sequencer.snapshot }
        status = if (value) "TX enabled for this Digi session · choose Call CQ, Call, or explicit SEND" else "Digi TX disabled"
        journal("TX_ENABLE", if (value) "enabled" else "disabled")
        publishInteropStatus()
    }

    fun disarm() { txArmed = false }

    fun updateTxText(value: String) {
        txText = value.take(240)
        prefs.edit().putString("tx_text_${mode.name}", txText).apply()
        disarm()
    }

    fun updateDxCall(value: String) {
        dxCall = value.uppercase().filter { it.isLetterOrDigit() || it == '/' }.take(14)
        prefs.edit().putString("dx_call", dxCall).apply()
        disarm()
    }

    fun applyStandardMessage(step: Int) {
        val mine = stationCallsign().trim().uppercase()
        val grid = stationGrid().trim().uppercase().take(4)
        val target = dxCall.trim().uppercase()
        val message = when (step) {
            0 -> listOf("CQ", mine, grid).filter(String::isNotBlank).joinToString(" ")
            1 -> listOf(target, mine, grid).filter(String::isNotBlank).joinToString(" ")
            2 -> "$target $mine -10"
            3 -> "$target $mine R-10"
            4 -> "$target $mine RR73"
            5 -> "$target $mine 73"
            else -> return
        }
        updateTxText(message.trim())
    }

    fun startCq() {
        if (mode !in setOf(DigiMode.FT8, DigiMode.FT4) || !txEnabled) {
            status = "Enable TX in FT8 or FT4 before starting CQ"
            return
        }
        sequencer.startCq(Instant.now().epochSecond); ftSequence = sequencer.snapshot
        applyStandardMessage(0); txArmed = true; send()
    }

    fun selectDecode(event: DigiDecodeEvent) {
        selectedDecode = event
        if (event.callsign.isNotBlank()) updateDxCall(event.callsign)
    }

    fun callSelected() {
        val event = selectedDecode ?: run { status = "Select a decoded station first"; return }
        if (mode !in setOf(DigiMode.FT8, DigiMode.FT4) || !txEnabled) {
            status = "Enable TX in FT8 or FT4 before calling a decoded station"
            return
        }
        sequencer.answer(event.callsign, Instant.now().epochSecond); ftSequence = sequencer.snapshot
        updateDxCall(event.callsign); applyStandardMessage(1); txArmed = true; send()
    }

    fun stopSequence() {
        sequencer.stop(); ftSequence = sequencer.snapshot; txEnabled = false; haltTx()
    }

    private fun queueSequenceStep() {
        if (!settings.ftAutoSequence || !txEnabled || txActive || mode !in setOf(DigiMode.FT8, DigiMode.FT4)) return
        val step = when (ftSequence.state) {
            FtSequenceState.ANSWERING -> 2
            FtSequenceState.REPORT_SENT -> 3
            FtSequenceState.R_REPORT_SENT -> 4
            FtSequenceState.FINAL_73 -> 5
            else -> return
        }
        applyStandardMessage(step); txArmed = true; send()
    }

    fun prepareSelectedDraft() {
        val event = selectedDecode ?: run { status = "Select a decode to prepare a QSO"; return }
        prepareDraft(event.callsign, event.grid, "", "")
    }

    private fun prepareDraft(callsign: String, grid: String, sent: String, received: String) {
        if (callsign.isBlank()) return
        val radio = dependencies.radioState()
        val capability = DigiCapabilities.forMode(mode)
        val activation = dependencies.activationContext()
        val now = Instant.now().epochSecond
        pendingDraft = DigiQsoDraft(
            callsign.uppercase(Locale.US), grid.uppercase(Locale.US), sent, received,
            ftSequence.startedEpoch.takeIf { it > 0 } ?: now, now, radio.frequencyHz, bandForFrequency(radio.frequencyHz),
            capability.adifMode, capability.adifSubmode, rxAudioHz, stationCallsign(), dependencies.stationProfile(),
            dependencies.stationLocation(), stationGrid(), dependencies.operatorCallsign(),
            activationContext = listOf(activation.first, activation.second).filter(String::isNotBlank).joinToString(":"),
        )
        sessionStore.saveDraft(requireNotNull(pendingDraft), completed = ftSequence.state == FtSequenceState.COMPLETE)
        status = "QSO draft ready for ${callsign.uppercase(Locale.US)} · review before logging"
        if (settings.ftAutoLog && ftSequence.state == FtSequenceState.COMPLETE) logPendingDraft()
    }

    fun logPendingDraft(): Boolean {
        val draft = pendingDraft ?: return false
        val country = dependencies.cty.country(draft.callsign)
        val qso = Qso(
            id = UUID.randomUUID().toString(), callsign = draft.callsign, frequencyHz = draft.dialFrequencyHz,
            mode = draft.mode, rstSent = draft.sentReport, rstReceived = draft.receivedReport, createdAt = draft.startEpoch,
            notes = draft.comment, country = country, band = draft.band, grid = draft.grid,
            operatorCallsign = draft.operatorCallsign, stationCallsign = draft.stationCallsign,
            stationProfileId = draft.stationProfile, stationLocation = draft.stationLocation, myGrid = draft.stationGrid,
            submode = draft.submode, durationSeconds = (draft.endEpoch - draft.startEpoch).coerceAtLeast(0),
            activationSessionId = draft.activationContext.substringAfter(':', ""), activationProgram = draft.activationContext.substringBefore(':', ""),
        )
        val saved = dependencies.mutations.save(qso)
        if (saved) {
            lastLoggedQsoId = qso.id; pendingDraft = null
            interop.send(WsjtDatagram.qsoLogged("RigWeave", draft))
            status = "QSO logged locally · Wavelog delivery follows the existing outbox · UNDO available"
            journal("QSO_LOGGED", "mutation coordinator")
        } else status = "Duplicate QSO remains visible; review the existing record before saving"
        return saved
    }

    fun undoLastLog() {
        val id = lastLoggedQsoId
        if (id.isBlank()) return
        dependencies.mutations.delete(id, QsoDeleteIntent.LOCAL_ONLY)
        lastLoggedQsoId = ""; status = "Last Digi QSO removed locally; an unsent Wavelog create was cancelled when safe"
    }

    fun openSelectedLogbook() { selectedDecode?.callsign?.takeIf(String::isNotBlank)?.let(dependencies.onOpenLogbook) }
    fun openSelectedDx() { selectedDecode?.callsign?.takeIf(String::isNotBlank)?.let(dependencies.onOpenDx) }
    fun openSelectedCallbook() { selectedDecode?.callsign?.takeIf(String::isNotBlank)?.let(dependencies.onOpenCallbook) }

    fun appendTxText(value: String) {
        val separator = if (txText.isBlank() || txText.endsWith(' ')) "" else " "
        updateTxText((txText + separator + value).take(240))
    }

    private fun defaultTxText(value: DigiMode): String {
        val call = stationCallsign().trim().uppercase()
        val grid = stationGrid().trim().uppercase().take(4)
        return when (value) {
            DigiMode.WSPR -> listOf(call, grid, "30").filter(String::isNotBlank).joinToString(" ")
            DigiMode.SSTV -> ""
            DigiMode.CW, DigiMode.RTTY, DigiMode.PSK31 -> if (call.isBlank()) "" else "CQ CQ DE $call $call K"
            else -> listOf("CQ", call, grid).filter(String::isNotBlank).joinToString(" ")
        }
    }

    fun updateCwWpm(value: Int) {
        cwWpm = value.coerceIn(8, 45)
        prefs.edit().putInt("cw_wpm", cwWpm).apply()
        updateSettings { it.copy(cwWpm = cwWpm) }
        disarm()
    }

    fun updateCwPitch(value: Float) {
        cwPitchHz = value.coerceIn(400f, 1_000f)
        prefs.edit().putFloat("cw_pitch", cwPitchHz).apply()
        updateSettings { it.copy(cwPitchHz = cwPitchHz) }
        disarm()
    }

    fun updateRttyReverse(value: Boolean) {
        rttyReverse = value
        prefs.edit().putBoolean("rtty_reverse", value).apply()
        updateSettings { it.copy(rttyReverse = value) }
        disarm()
    }

    fun updateSettings(change: (DigiSettingsDocument) -> DigiSettingsDocument) {
        settings = change(settings).copy(version = 1)
        prefs.edit().putString("settings_v1", settings.toJson()).apply()
    }

    fun setUdpEnabled(enabled: Boolean) {
        updateSettings { it.copy(udpEnabled = enabled) }
        if (enabled) interop.start(settings.udpHost, settings.udpPort, settings.companionMode) else interop.stop()
        if (enabled) publishInteropStatus()
    }

    fun setCompanionMode(enabled: Boolean) {
        if (enabled) { stopRx("Companion mode enabled"); haltTx() }
        updateSettings { it.copy(companionMode = enabled, udpEnabled = if (enabled) true else it.udpEnabled) }
        if (settings.udpEnabled) interop.start(settings.udpHost, settings.udpPort, enabled)
    }

    fun toggleRawRecording() {
        rawRecordingActive = if (rawRecorder.active) {
            rawRecorder.stop()
            status = "Bounded raw recording saved in app-private storage"
            false
        } else {
            val started = rawRecorder.start()
            status = if (started) "Raw audio recording started · maximum 10 minutes" else "Raw recording could not start"
            started
        }
        updateSettings { it.copy(rawRecording = rawRecordingActive) }
    }

    fun selectOperatingView(value: DigiOperatingView) { operatingView = value }
    fun updateWaterfallState(value: DigiWaterfallState) { waterfallState = value }
    fun snapWaterfallLive() { waterfallState = DigiWaterfallState.LIVE }

    fun updateWaterfall(settingsValue: DigiWaterfallSettings) {
        val sanitized = settingsValue.copy(
            lowHz = settingsValue.lowHz.coerceIn(0f, 3_800f),
            highHz = settingsValue.highHz.coerceIn((settingsValue.lowHz + 200f).coerceAtMost(4_000f), 4_000f),
            floor = settingsValue.floor.coerceIn(0f, .95f), gain = settingsValue.gain.coerceIn(.25f, 4f),
            contrast = settingsValue.contrast.coerceIn(.5f, 3f),
        )
        updateSettings { it.copy(waterfall = sanitized) }
    }

    fun waterfallTap(fraction: Float, explicitTx: Boolean = false) {
        val value = (settings.waterfall.lowHz + settings.waterfall.run { highHz - lowHz } * fraction.coerceIn(0f, 1f)).coerceIn(200f, 3_500f)
        when (capability.waterfall) {
            DigiWaterfallBehavior.MARK_SPACE_CENTER -> {
                updateSettings { it.copy(rttyCarrierHz = value, rxOffsets = it.rxOffsets + (mode.family to value)) }
                restartRxForNet("RTTY mark/space centred at ${value.roundToInt()} Hz")
            }
            DigiWaterfallBehavior.CARRIER -> {
                pskCarrierHz = value
                updateSettings { it.copy(pskCarrierHz = value, rxOffsets = it.rxOffsets + (mode.family to value)) }
                resetPskBuffer(); status = "PSK31 carrier set to ${value.roundToInt()} Hz · reacquiring"
            }
            DigiWaterfallBehavior.CW_PITCH -> updateCwPitch(value)
            DigiWaterfallBehavior.AUDIO_OFFSET -> updateSettings { document ->
                if (explicitTx) document.copy(txOffsets = document.txOffsets + (mode.family to value))
                else document.copy(rxOffsets = document.rxOffsets + (mode.family to value),
                    txOffsets = if (document.holdTx) document.txOffsets else document.txOffsets + (mode.family to value))
            }
            DigiWaterfallBehavior.VIEW_ONLY -> status = "SSTV waterfall is view-only"
        }
        disarm()
        journal("WATERFALL_NET", "${mode.name}:${value.roundToInt()}:${if (explicitTx) "TX" else "RX"}")
    }

    private fun restartRxForNet(detail: String) {
        val restart = rxActive
        if (restart) stopRx(detail)
        if (restart) startRx() else status = detail
    }

    fun reacquire() {
        when (mode) {
            DigiMode.RTTY -> restartRxForNet("RTTY decoder reacquiring ${rxAudioHz.roundToInt()} Hz centre")
            DigiMode.PSK31 -> { resetPskBuffer(); status = "PSK31 reacquiring ${pskCarrierHz.roundToInt()} Hz carrier" }
            else -> Unit
        }
    }

    private fun defaultAudioOffset(value: DigiMode) = when (value) {
        DigiMode.CW -> cwPitchHz
        DigiMode.RTTY -> settings.rttyCarrierHz
        DigiMode.PSK31 -> settings.pskCarrierHz
        else -> 1_500f
    }

    private fun journal(state: String, detail: String) {
        val sanitized = detail.replace(Regex("\\b[A-Z]{1,3}[0-9][A-Z0-9/]{1,10}\\b"), "[CALL]").take(160)
        diagnostics = (diagnostics + DigiDiagnostic(Instant.now().epochSecond, state, sanitized)).takeLast(20)
    }

    fun clear() {
        transcript = ""
        decodedRows = emptyList()
        resetSlotBuffer()
        resetPskBuffer()
        sstvLine = -1
        sstvComplete = false
        sstvFskId = ""
        sstvRgb = ByteArray(0)
        imageRevision++
    }

    fun setSstvSource(bitmap: Bitmap, filename: String = "") {
        sourceOriginal?.recycle()
        sourceOriginal = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        sourceFilename = filename.take(160)
        sstvFrameOffset = 0f
        prepareSstvSource()
    }

    fun updateSstvFrameOffset(value: Float) {
        sstvFrameOffset = value.coerceIn(-1f, 1f)
        prepareSstvSource()
    }

    private fun prepareSstvSource() {
        val bitmap = sourceOriginal ?: return
        val target = sstvChoice
        val scale = maxOf(target.width.toFloat() / bitmap.width, target.height.toFloat() / bitmap.height)
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(target.width)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(target.height)
        val expanded = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val extraX = (scaledWidth - target.width).coerceAtLeast(0)
        val extraY = (scaledHeight - target.height).coerceAtLeast(0)
        val left = ((extraX / 2f) + sstvFrameOffset * extraX / 2f).roundToInt().coerceIn(0, extraX)
        val top = ((extraY / 2f) + sstvFrameOffset * extraY / 2f).roundToInt().coerceIn(0, extraY)
        val scaled = Bitmap.createBitmap(expanded, left, top, target.width, target.height).copy(Bitmap.Config.ARGB_8888, true)
        expanded.takeIf { it !== bitmap && it !== scaled }?.recycle()
        if (settings.sstvCallsignOverlay && stationCallsign().isNotBlank()) {
            Canvas(scaled).drawText(stationCallsign().uppercase(Locale.US), 12f, target.height - 14f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = (target.height / 14f).coerceAtLeast(18f); setShadowLayer(3f, 1f, 1f, Color.BLACK) })
        }
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
        sourceRevision++
        scaled.recycle()
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

    fun currentSstvSourceBitmap(): Bitmap? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceRgb.size != sourceWidth * sourceHeight * 3) return null
        val pixels = IntArray(sourceWidth * sourceHeight) { index ->
            val at = index * 3
            (0xff shl 24) or ((sourceRgb[at].toInt() and 0xff) shl 16) or
                ((sourceRgb[at + 1].toInt() and 0xff) shl 8) or (sourceRgb[at + 2].toInt() and 0xff)
        }
        return Bitmap.createBitmap(pixels, sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
    }

    @SuppressLint("MissingPermission")
    fun startRx() {
        if (rxActive) return
        if (settings.companionMode || !interop.localModemAllowed) {
            status = "Companion mode is active; local decoder and PTT authority are disabled"
            return
        }
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
        nativeHandle = NativeCore.digiCreate(12_000, cwPitchHz, rttyReverse, settings.rttyCarrierHz)
        if (nativeHandle == 0L) {
            record.release()
            routes.releaseAudio(AudioOwners.DIGI_RX)
            status = "The native modem could not start"
            return
        }
        recorder = record
        lastStableRoute = routes.selectedRx?.stableKey.orEmpty()
        receiving.set(true)
        rxActive = true
        record.startRecording()
        status = "${mode.label} RX live · ${routes.inputName} · ${rate / 1000} kHz capture"
        sessionId = sessionStore.beginSession(mode.name, Instant.now().epochSecond)
        audioHealth = DigiAudioHealth(DigiAudioHealthState.INITIALIZING, routes.selectedRx?.stableKey.orEmpty(), "USB", rate, 1,
            audioOwner = routes.audioOwner, detail = "Waiting for capture")
        if (mode == DigiMode.SSTV) sstvHealth = SstvReceiveHealth(SstvReceiveHealthState.STARTED_NO_AUDIO_YET)
        rxJob = scope.launch(Dispatchers.IO) { receiveLoop(record, rate, input.id) }
        publishInteropStatus()
    }

    private fun startFlexRx() {
        nativeHandle = NativeCore.digiCreate(12_000, cwPitchHz, rttyReverse, settings.rttyCarrierHz)
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
        sessionId = sessionStore.beginSession(mode.name, Instant.now().epochSecond)
        audioHealth = DigiAudioHealth(DigiAudioHealthState.INITIALIZING, "flex-vita", "FLEX", sampleRate = 0, channels = 0,
            audioOwner = AudioOwners.DIGI_RX, detail = "Waiting for network audio")
        publishInteropStatus()
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
        scope.launch { audioHealth = audioHealth.copy(sampleRate = sampleRate, channels = channels, source = "FLEX") }
        feedNative(resampleFloat12k(mono, sampleRate))
    }

    private fun receiveLoop(record: AudioRecord, rate: Int, requestedDeviceId: Int) {
        val block = ShortArray((rate / 20).coerceAtLeast(512))
        var routeChecks = 0
        try {
            while (receiving.get()) {
                val count = record.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) error("USB audio read failed: $count")
                val samples = resample12k(block, count, rate)
                feedNative(samples)
                if (++routeChecks >= 20) {
                    routeChecks = 0
                    if (record.routedDevice?.id != requestedDeviceId) {
                        scope.launch { audioHealth = audioHealth.copy(state = DigiAudioHealthState.ROUTE_LOST); stopRx("Selected USB route was lost"); haltTx() }
                        return
                    }
                }
            }
        } catch (failure: Throwable) {
            if (receiving.get()) scope.launch { stopRx("RX stopped: ${failure.message}") }
        }
    }

    @Synchronized
    private fun feedNative(samples: FloatArray) {
        if (!receiving.get() || nativeHandle == 0L || samples.isEmpty()) return
        observeAudio(samples)
        if (mode.isSlotted) {
            feedSlot(samples)
            return
        }
        val result = when (mode) {
            DigiMode.CW -> NativeCore.digiFeedCw(nativeHandle, samples)
            DigiMode.RTTY -> NativeCore.digiFeedRtty(nativeHandle, samples)
            DigiMode.PSK31 -> {
                feedPsk31(samples)
                return
            }
            DigiMode.SSTV -> NativeCore.digiFeedSstv(nativeHandle, samples)
            else -> return
        }
        scope.launch { applyDecode(result) }
    }

    private fun observeAudio(samples: FloatArray) {
        if (rawRecorder.active && !rawRecorder.append(samples)) {
            rawRecordingActive = false
            updateSettings { it.copy(rawRecording = false) }
        }
        framesReceived += samples.size
        lastFrameMonotonicMs = android.os.SystemClock.elapsedRealtime()
        var square = 0.0
        var peak = 0f
        var clipped = 0
        samples.forEach { sample ->
            square += sample * sample
            val level = kotlin.math.abs(sample)
            if (level > peak) peak = level
            if (level >= .98f) clipped++
        }
        val rms = sqrt(square / samples.size).toFloat()
        val clippedFraction = clipped.toFloat() / samples.size
        val healthState = when {
            clippedFraction > .005f -> DigiAudioHealthState.CLIPPING
            peak < .001f -> DigiAudioHealthState.SILENT
            rms < .01f -> DigiAudioHealthState.LOW_LEVEL
            else -> DigiAudioHealthState.LIVE
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSpectrumPublishedMs < 100L) return
        lastSpectrumPublishedMs = now
        val display = settings.waterfall
        val row = NativeCore.digiSpectrum(samples, 12_000, display.lowHz, display.highHz, 384, display.window.ordinal)
        scope.launch {
            audioHealth = audioHealth.copy(state = healthState, rms = rms, peak = peak, clippedFraction = clippedFraction,
                framesReceived = framesReceived, lastFrameAgeMillis = 0, audioOwner = routes.audioOwner)
            if (mode == DigiMode.SSTV) sstvHealth = sstvHealth.copy(
                state = if (peak < .001f) SstvReceiveHealthState.SILENT else if (sstvLine >= 0) SstvReceiveHealthState.DECODING else SstvReceiveHealthState.LISTENING,
                peak = peak, lastSampleAgeMillis = 0,
            )
            if (row.size == 384) {
                spectrumRow = row
                if (waterfallState == DigiWaterfallState.LIVE) waterfallRows = (waterfallRows + row).takeLast(900)
            }
        }
    }

    private fun resetSlotBuffer() {
        val required = mode.slotMillis.takeIf { mode.isSlotted }?.let { (it * 12).toInt() } ?: 0
        slotPcm = if (required > 0) FloatArray(required) else FloatArray(0)
        slotUsed = 0
        slotSkipSamples = -1
        slotProgress = 0f
    }

    private fun resetPskBuffer() {
        pskDecodeJob?.cancel()
        pskDecodeJob = null
        pskPcm = FloatArray(0)
        pskLastDecoded = 0
    }

    private fun feedPsk31(samples: FloatArray) {
        val maximum = 120 * 12_000
        pskPcm = if (pskPcm.size + samples.size <= maximum) pskPcm + samples
        else (pskPcm + samples).takeLast(maximum).toFloatArray()
        if (pskPcm.size - pskLastDecoded < 12_000 || pskDecodeJob?.isActive == true) return
        pskLastDecoded = pskPcm.size
        val snapshot = pskPcm.copyOf()
        pskDecodeJob = scope.launch(Dispatchers.Default) {
            val value = NativeCore.digiDecodePsk31(snapshot, pskCarrierHz)
            withContext(Dispatchers.Main.immediate) { if (mode == DigiMode.PSK31) applyDecode(value) }
        }
    }

    private fun feedSlot(samples: FloatArray) {
        if (slotPcm.isEmpty()) resetSlotBuffer()
        if (slotPcm.isEmpty()) return
        var source = 0
        if (slotSkipSamples < 0) {
            val slotMs = mode.slotMillis
            val phaseMs = System.currentTimeMillis().mod(slotMs)
            slotSkipSamples = if (phaseMs <= 120L) 0 else (((slotMs - phaseMs) * 12L).toInt())
            if (slotSkipSamples > 0) status = "${mode.label} RX live · waiting for the next UTC slot"
        }
        if (slotSkipSamples > 0) {
            val skipped = minOf(samples.size, slotSkipSamples)
            slotSkipSamples -= skipped
            source += skipped
            if (source == samples.size) return
            status = "${mode.label} UTC slot started · capturing"
        }
        while (source < samples.size) {
            val count = minOf(samples.size - source, slotPcm.size - slotUsed)
            samples.copyInto(slotPcm, slotUsed, source, source + count)
            slotUsed += count
            source += count
            slotProgress = slotUsed.toFloat() / slotPcm.size
            if (slotUsed == slotPcm.size) {
                val decoder = mode.slotDecoder ?: return
                val captured = slotPcm.copyOf()
                lastCompletedSlot = captured
                lastCompletedSlotMode = mode
                slotUsed = 0
                slotProgress = 0f
                if (slotDecodeJob?.isActive == true) {
                    status = "${mode.label} slot captured · previous decode still running"
                    continue
                }
                status = "${mode.label} slot captured · decoding ${captured.size / 12_000}s"
                slotDecodeJob = scope.launch(Dispatchers.Default) {
                    val result = NativeCore.digiDecodeSlot(decoder, captured, 12_000)
                    withContext(Dispatchers.Main.immediate) { applySlotDecode(result, mode) }
                }
            }
        }
    }

    private fun applySlotDecode(value: String, decodedMode: DigiMode) {
        if (mode != decodedMode) return
        val json = runCatching { JSONObject(value) }.getOrNull()
        val error = json?.optString("error").orEmpty()
        if (json == null || error.isNotBlank() && error != "null") {
            status = "${decodedMode.label} decode failed${error.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
            return
        }
        val rows = json.optJSONArray("decodes")
        decodedRows = buildList {
            if (rows != null) repeat(rows.length()) { index ->
                rows.optJSONObject(index)?.let { row ->
                    add(DigiDecodeRow(
                        row.optString("text"), row.optDouble("frequencyHz").toFloat(),
                        row.optDouble("dtSeconds").toFloat(), row.optDouble("snrDb").toFloat(),
                    ))
                }
            }
        }
        enrichSlotRows(decodedRows, decodedMode)
        transcript = decodedRows.joinToString("\n") { row ->
            "%+4.0f dB  %+.2f  %7.1f Hz  %s".format(row.snrDb, row.dtSeconds, row.frequencyHz, row.text)
        }
        status = if (decodedRows.isEmpty()) "${decodedMode.label} slot decoded · no qualified signals"
        else "${decodedMode.label} · ${decodedRows.size} signal${if (decodedRows.size == 1) "" else "s"} decoded"
    }

    private fun enrichSlotRows(rows: List<DigiDecodeRow>, decodedMode: DigiMode) {
        if (rows.isEmpty()) return
        val now = Instant.now().epochSecond
        val period = (decodedMode.slotMillis / 1_000L).coerceAtLeast(1)
        val periodStart = now - now.mod(period)
        val parsed = rows.map { it to DigiFtParser.parse(it.text) }
        val calls = parsed.map { it.second.from }.filter(String::isNotBlank).distinct()
        scope.launch(Dispatchers.IO) {
            val summaries = OperationsLogRepository(dependencies.database).callsigns(calls)
            val needs = dependencies.needsByCallsign().mapKeys { it.key.uppercase(Locale.US) }
            val watch = dependencies.liveSpots().filter(AndroidDXSpot::watchlisted).map { it.callsign.uppercase(Locale.US) }.toSet()
            val stationPoint = maidenheadCenter(stationGrid())
            val events = parsed.map { (row, message) ->
                val call = message.from.uppercase(Locale.US)
                val cty = dependencies.cty.lookup(call)
                val remote = maidenheadCenter(message.grid)
                val summary = summaries[call]
                val identity = "$sessionId|${decodedMode.name}|$periodStart|${row.frequencyHz}|${row.text}"
                DigiDecodeEvent(
                    UUID.nameUUIDFromBytes(identity.toByteArray()).toString(), sessionId.ifBlank { "reference" }, now,
                    decodedMode.name, periodStart, row.snrDb, row.dtSeconds, row.frequencyHz, row.text,
                    call, message.grid, cty?.country.orEmpty(), cty?.continent.orEmpty(),
                    if (stationPoint != null && remote != null) distanceKm(stationPoint, remote) else 0.0,
                    if (stationPoint != null && remote != null) initialBearingDegrees(stationPoint, remote).toDouble() else 0.0,
                    worked = (summary?.qsos ?: 0) > 0, confirmed = (summary?.confirmed ?: 0) > 0,
                    needs = needs[call].orEmpty(), watchlisted = call in watch,
                )
            }
            sessionStore.appendDecodes(events, settings.decodeRetentionDays)
            withContext(Dispatchers.Main.immediate) {
                decodeHistory = (decodeHistory + events).distinctBy(DigiDecodeEvent::id).takeLast(3_000)
                events.forEach { event ->
                    interop.send(WsjtDatagram.decode("RigWeave", true, ((event.periodStartEpoch % 86_400) * 1_000).toInt(),
                        event.snr.roundToInt(), event.dt.toDouble(), event.audioHz.roundToInt(), decodedMode.label, event.text))
                    if (decodedMode in setOf(DigiMode.FT8, DigiMode.FT4) && sequencer.decode(DigiFtParser.parse(event.text))) {
                        ftSequence = sequencer.snapshot
                        if (ftSequence.state == FtSequenceState.COMPLETE) prepareDraft(ftSequence.lockedCall, event.grid, ftSequence.sentReport, ftSequence.receivedReport)
                        else queueSequenceStep()
                    }
                }
            }
        }
    }

    private fun applyDecode(value: String) {
        val json = runCatching { JSONObject(value) }.getOrNull() ?: return
        when (mode) {
            DigiMode.CW -> {
                transcript = json.optString("text").takeLast(4_000)
                val wpm = json.optInt("wpm")
                status = "CW RX live · ${if (wpm > 0) "$wpm WPM" else "acquiring timing"}"
            }
            DigiMode.RTTY -> {
                transcript = json.optString("text").takeLast(4_000)
                rttyAfcHz = json.optDouble("afcHz").toFloat()
                rttyAfcLocked = json.optBoolean("locked")
                status = "RTTY RX live · AFC ${if (rttyAfcLocked) "locked" else "acquiring"}"
            }
            DigiMode.PSK31 -> {
                transcript = json.optString("text").takeLast(4_000)
                pskCarrierHz = json.optDouble("carrierHz", 1_000.0).toFloat()
                status = "PSK31 RX live · carrier ${"%.1f".format(pskCarrierHz)} Hz"
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
                sstvHealth = sstvHealth.copy(
                    state = if (sstvComplete) SstvReceiveHealthState.COMPLETE else if (sstvLine >= 0) SstvReceiveHealthState.DECODING else SstvReceiveHealthState.LISTENING,
                    lastVis = json.optInt("mode", -1).takeIf { it >= 0 }?.toString().orEmpty(),
                    mode = SstvChoices.firstOrNull { it.index == json.optInt("mode", -1) }?.label.orEmpty(),
                    line = sstvLine, totalLines = sstvHeight,
                )
                if (sstvComplete) persistCompletedSstv()
                status = if (sstvComplete) "SSTV image complete${sstvFskId.takeIf(String::isNotBlank)?.let { " · ID $it" }.orEmpty()}"
                else if (sstvLine >= 0) "SSTV receiving · line ${sstvLine + 1} / $sstvHeight"
                else "SSTV RX live · waiting for VIS header"
            }
            else -> Unit
        }
    }

    private fun persistCompletedSstv() {
        val signature = "$sessionId:$sstvWidth:$sstvHeight:$sstvFskId:${sstvRgb.contentHashCode()}"
        if (signature == lastSavedSstvSignature) return
        val bitmap = currentSstvBitmap() ?: return
        lastSavedSstvSignature = signature
        scope.launch(Dispatchers.IO) {
            val item = runCatching { sessionStore.saveSstvPng(bitmap, sstvHealth.mode.ifBlank { "SSTV" }, Instant.now().epochSecond,
                dependencies.radioState().frequencyHz, dependencies.stationProfile(), sstvFskId, sourceFilename, settings.galleryQuotaMb) }
            bitmap.recycle()
            withContext(Dispatchers.Main.immediate) {
                item.onSuccess { saved ->
                    gallery = sessionStore.gallery()
                    sstvHealth = sstvHealth.copy(decodedCount = sstvHealth.decodedCount + 1, lastCompletedEpoch = saved.completedEpoch)
                    status = "SSTV image saved to the private gallery"
                }.onFailure { status = "SSTV completed but gallery save failed: ${it.message}" }
            }
        }
    }

    fun renameGallery(id: String, caption: String) { sessionStore.updateGallery(id, caption = caption); gallery = sessionStore.gallery() }
    fun pinGallery(id: String, pinned: Boolean) { sessionStore.updateGallery(id, pinned = pinned); gallery = sessionStore.gallery() }
    fun deleteGallery(id: String) { if (sessionStore.deleteGallery(id)) gallery = sessionStore.gallery() }

    fun setIssSession(enabled: Boolean) {
        issJob?.cancel(); issJob = null
        issSessionEnabled = enabled
        txEnabled = false; disarm()
        if (!enabled) { if (mode == DigiMode.SSTV && rxActive) stopRx("ISS SSTV receive session disabled"); return }
        if (mode != DigiMode.SSTV) selectMode(DigiMode.SSTV)
        status = "ISS SSTV receive-only session enabled · 145.800 MHz downlink · review tune before changing radio"
        issJob = scope.launch {
            while (issSessionEnabled) {
                val pass = dependencies.nextIssPass(); issPass = pass
                val now = Instant.now().epochSecond
                if (pass != null && now in pass.aosEpoch..pass.losEpoch && !rxActive) startRx()
                if (pass != null && now > pass.losEpoch && rxActive) stopRx("ISS pass ended · SSTV RX disarmed")
                delay(15_000)
            }
        }
    }

    fun requestIssReceiveReview() {
        if (!issSessionEnabled) { status = "Enable the receive-only ISS SSTV session first"; return }
        dependencies.requestIssReceiveReview()
    }

    fun decodeRecording(uri: Uri) {
        stopRx("Opening reference recording")
        val decoder = mode.slotDecoder
        slotDecodeJob?.cancel()
        slotDecodeJob = scope.launch {
            status = "Reading ${mode.label} reference recording"
            val recording = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Recording could not be opened")
                    decodePcmWav(bytes)
                }
            }
            val (sourceRate, sourcePcm) = recording.getOrElse {
                status = "${mode.label} recording failed: ${it.message ?: "invalid WAV"}"
                return@launch
            }
            // tempo-sstv owns a band-limited arbitrary-rate resampler. Feeding
            // the original recording preserves its narrow VIS/sync pulses;
            // the simple 12-kHz adapter used by the text/WSJT decoders can
            // erase enough timing detail to prevent line acquisition.
            val decoderRate = if (mode == DigiMode.SSTV) sourceRate else 12_000
            val pcm = if (decoderRate == sourceRate) sourcePcm else resampleFloat12k(sourcePcm, sourceRate)
            if (decoder != null) {
                val result = withContext(Dispatchers.Default) { NativeCore.digiDecodeSlot(decoder, pcm, 12_000) }
                applySlotDecode(result, mode)
            } else if (mode == DigiMode.PSK31) {
                applyDecode(withContext(Dispatchers.Default) { NativeCore.digiDecodePsk31(pcm, pskCarrierHz) })
                status = "PSK31 reference recording decoded"
            } else {
                decodeStreamingRecording(pcm, decoderRate)
            }
        }
    }

    fun redecodeLastSlot() {
        val captured = lastCompletedSlot.copyOf()
        val capturedMode = lastCompletedSlotMode
        val decoder = capturedMode?.slotDecoder
        if (captured.isEmpty() || capturedMode == null || decoder == null) {
            status = "No completed slot is available for re-decode"
            return
        }
        if (slotDecodeJob?.isActive == true) return
        slotDecodeJob = scope.launch(Dispatchers.Default) {
            val result = NativeCore.digiDecodeSlot(decoder, captured, 12_000)
            withContext(Dispatchers.Main.immediate) {
                if (mode == capturedMode) applySlotDecode(result, capturedMode)
            }
        }
    }

    private suspend fun decodeStreamingRecording(pcm: FloatArray, sampleRate: Int = 12_000) {
        val selected = mode
        val handle = NativeCore.digiCreate(sampleRate, cwPitchHz, rttyReverse, settings.rttyCarrierHz)
        if (handle == 0L) {
            status = "The native ${selected.label} decoder could not start"
            return
        }
        nativeHandle = handle
        try {
            val final = withContext(Dispatchers.Default) {
                var result = "{}"
                var at = 0
                while (at < pcm.size) {
                    val end = minOf(at + 1_024, pcm.size)
                    val block = pcm.copyOfRange(at, end)
                    result = when (selected) {
                        DigiMode.CW -> NativeCore.digiFeedCw(handle, block)
                        DigiMode.RTTY -> NativeCore.digiFeedRtty(handle, block)
                        DigiMode.PSK31 -> NativeCore.digiDecodePsk31(pcm, pskCarrierHz)
                        DigiMode.SSTV -> NativeCore.digiFeedSstv(handle, block)
                        else -> "{}"
                    }
                    at = end
                }
                result
            }
            applyDecode(final)
            status = when (selected) {
                DigiMode.CW, DigiMode.RTTY, DigiMode.PSK31 -> if (transcript.isBlank()) {
                    "${selected.label} reference recording contained no qualified decode"
                } else {
                    "${selected.label} reference recording decoded"
                }
                DigiMode.SSTV -> if (sstvComplete) {
                    "SSTV reference image decoded"
                } else {
                    "SSTV reference recording ended before a complete image"
                }
                else -> "${selected.label} reference recording decoded"
            }
        } finally {
            NativeCore.digiDestroy(handle)
            if (nativeHandle == handle) nativeHandle = 0L
        }
    }

    private fun decodePcmWav(bytes: ByteArray): Pair<Int, FloatArray> {
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WAVE") { "Not a RIFF/WAVE recording" }
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        var format = 0
        var channels = 0
        var rate = 0
        var bits = 0
        var dataAt = -1
        var dataSize = 0
        while (at + 8 <= bytes.size) {
            val id = bytes.copyOfRange(at, at + 4).decodeToString()
            val size = input.getInt(at + 4).coerceAtLeast(0)
            val body = at + 8
            if (body + size > bytes.size) break
            if (id == "fmt " && size >= 16) {
                format = input.getShort(body).toInt() and 0xffff
                channels = input.getShort(body + 2).toInt() and 0xffff
                rate = input.getInt(body + 4)
                bits = input.getShort(body + 14).toInt() and 0xffff
            } else if (id == "data") {
                dataAt = body
                dataSize = size
            }
            at = body + size + (size and 1)
        }
        require(channels in 1..8 && rate in 8_000..192_000 && dataAt >= 0) { "WAV format or audio data is missing" }
        require((format == 1 && bits == 16) || (format == 3 && bits == 32)) { "Use PCM16 or Float32 WAV audio" }
        val bytesPerSample = bits / 8
        val frames = dataSize / (bytesPerSample * channels)
        val mono = FloatArray(frames)
        repeat(frames) { frame ->
            var sum = 0f
            repeat(channels) { channel ->
                val sampleAt = dataAt + (frame * channels + channel) * bytesPerSample
                sum += if (format == 1) input.getShort(sampleAt) / 32768f else input.getFloat(sampleAt)
            }
            mono[frame] = sum / channels
        }
        return rate to mono
    }

    fun stopRx(reason: String = "RX stopped") {
        receiving.set(false)
        flex.setDigitalRxSink(null)
        if (flexRxOwned) flex.disableRxAudio()
        flexRxOwned = false
        rxJob?.cancel()
        rxJob = null
        slotDecodeJob?.cancel()
        slotDecodeJob = null
        pskDecodeJob?.cancel()
        pskDecodeJob = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        if (nativeHandle != 0L) NativeCore.digiDestroy(nativeHandle)
        nativeHandle = 0
        rxActive = false
        routes.releaseAudio(AudioOwners.DIGI_RX)
        if (sessionId.isNotBlank()) runCatching { sessionStore.endSession(sessionId, Instant.now().epochSecond, reason.take(40)) }
        sessionId = ""
        audioHealth = audioHealth.copy(state = if (reason.contains("route", true)) DigiAudioHealthState.ROUTE_LOST else DigiAudioHealthState.NO_CAPTURE,
            lastFrameAgeMillis = if (lastFrameMonotonicMs == 0L) Long.MAX_VALUE else android.os.SystemClock.elapsedRealtime() - lastFrameMonotonicMs,
            detail = reason)
        sstvHealth = sstvHealth.copy(state = SstvReceiveHealthState.STOPPED)
        status = reason
        resetSlotBuffer()
        publishInteropStatus()
    }

    fun send() {
        if (!txEnabled || !txArmed || txActive || settings.companionMode) {
            status = "Enable TX and arm one explicit transmission first"
            return
        }
        val text = txText.trim()
        if (!mode.txSupported) {
            status = "${mode.label} transmit is not implemented"
            disarm()
            return
        }
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
        lastTxText = if (mode == DigiMode.SSTV) "${sstvChoice.label} image" else text
        txJob = scope.launch { transmit(text) }
    }

    fun haltTx() {
        disarm()
        txEnabled = false
        sequencer.stop(); ftSequence = sequencer.snapshot
        txJob?.cancel()
        issJob?.cancel(); issJob = null
        txJob = null
        txActive = false
        txPhase = DigiTxPhase.SAFE
        status = "Digital transmission stopped by operator · RX requested"
        scope.launch {
            flex.stopTransmit("operator stopped digital transmission")
            transport.send("RX;")
            routes.releaseAudio(AudioOwners.DIGI_TX)
        }
        publishInteropStatus()
    }

    private suspend fun transmit(text: String) {
        val selectedMode = mode
        val selectedFrequency = dependencies.radioState().frequencyHz
        val resumeRx = rxActive
        stopRx("RX paused for transmit")
        val samples = withContext(Dispatchers.Default) {
            when (mode) {
                DigiMode.CW -> NativeCore.digiEncodeCw(text, cwWpm, cwPitchHz, 48_000)
                DigiMode.RTTY -> NativeCore.digiEncodeRtty(text, 48_000, rttyReverse)
                DigiMode.PSK31 -> upsample12to48(NativeCore.digiEncodePsk31(text, pskCarrierHz))
                DigiMode.SSTV -> NativeCore.digiEncodeSstv(sstvChoice.index, sourceRgb, sourceWidth, sourceHeight, 48_000)
                else -> mode.slotDecoder?.let { decoder ->
                    val encoded = NativeCore.digiEncodeSlot(decoder, text, txAudioHz)
                    if (encoded.isEmpty()) FloatArray(0) else upsample12to48(encoded)
                } ?: FloatArray(0)
            }
        }
        if (samples.isEmpty()) {
            status = "The ${mode.label} encoder rejected this transmission"
            return
        }
        txActive = true
        txPhase = DigiTxPhase.SEQUENCING
        publishInteropStatus()
        try {
            if (mode.isSlotted) {
                val phase = System.currentTimeMillis().mod(mode.slotMillis)
                val wait = if (phase < 100L) 0L else mode.slotMillis - phase
                status = "${mode.label} armed · next UTC slot in ${"%.1f".format(wait / 1_000.0)}s"
                delay(wait)
            }
            if (!txEnabled || mode != selectedMode || dependencies.radioState().frequencyHz != selectedFrequency) {
                txEnabled = false
                status = "Transmission cancelled because mode, frequency, or TX enable changed"
                return
            }
            withTimeout(capability.maximumTxMillis + 5_000L) {
                if (radioFamily() == RadioFamily.FLEXRADIO) transmitFlex(samples) else transmitElecraft(samples)
            }
            if (selectedMode in setOf(DigiMode.FT8, DigiMode.FT4)) {
                sequencer.transmitted(); ftSequence = sequencer.snapshot
            }
        } finally {
            txActive = false
            txPhase = DigiTxPhase.SAFE
            routes.releaseAudio(AudioOwners.DIGI_TX)
            publishInteropStatus()
            if (resumeRx && txEnabled && !settings.companionMode) startRx()
        }
    }

    private fun publishInteropStatus() {
        if (!settings.udpEnabled) return
        val radio = dependencies.radioState()
        interop.send(WsjtDatagram.status(
            "RigWeave", radio.frequencyHz, mode.label, dxCall, "", mode.label,
            txEnabled, txActive, rxActive, rxAudioHz.roundToInt(), txAudioHz.roundToInt(),
            stationCallsign(), stationGrid(),
        ))
    }

    fun onForegroundChanged(foreground: Boolean) {
        if (!foreground) {
            resumeRxOnForeground = rxActive
            stopRx("Digi paused while app is in the background")
            haltTx(); interop.stop(); journal("BACKGROUND", "RX stopped and TX cleared")
            return
        }
        if (settings.udpEnabled) interop.start(settings.udpHost, settings.udpPort, settings.companionMode)
        if (resumeRxOnForeground && settings.resumeExactRouteRx && radioFamily() != RadioFamily.FLEXRADIO) {
            routes.refreshDevices()
            if (routes.selectedRx?.stableKey == lastStableRoute) startRx()
            else audioHealth = audioHealth.copy(state = DigiAudioHealthState.ROUTE_LOST, detail = "Selected route did not return")
        }
        resumeRxOnForeground = false
    }

    fun onRadioStateChanged(value: RadioState) {
        val first = lastRadioIdentity.isBlank()
        val identityChanged = !first && value.identity != lastRadioIdentity
        val frequencyChanged = !first && lastRadioFrequency > 0 && kotlin.math.abs(value.frequencyHz - lastRadioFrequency) > 50
        val disconnected = !first && lastRadioConnected && !value.connected
        lastRadioIdentity = value.identity; lastRadioFrequency = value.frequencyHz
        lastRadioConnected = value.connected
        if (identityChanged || frequencyChanged || disconnected) {
            txEnabled = false; disarm()
            if (ftSequence.state !in setOf(FtSequenceState.IDLE, FtSequenceState.STOPPED)) {
                sequencer.stop(); ftSequence = sequencer.snapshot
            }
            if (disconnected) haltTx()
            journal("RADIO_CHANGE", "TX cleared")
        }
    }

    fun refreshRouteHealth() {
        routes.refreshDevices()
        if (rxActive && radioFamily() != RadioFamily.FLEXRADIO && routes.selectedRx?.stableKey != lastStableRoute) {
            audioHealth = audioHealth.copy(state = DigiAudioHealthState.ROUTE_LOST, detail = "Selected USB route disappeared")
            stopRx("Selected Digi route was lost; RX stopped and TX disarmed")
            haltTx()
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

    private fun upsample12to48(input: FloatArray): FloatArray = FloatArray(input.size * 4) { index ->
        val position = index / 4f
        val left = position.toInt().coerceIn(0, input.lastIndex)
        val right = (left + 1).coerceAtMost(input.lastIndex)
        val fraction = position - left
        input[left] * (1f - fraction) + input[right] * fraction
    }

    override fun close() {
        if (closed) return
        closed = true
        stopRx("Digi closed")
        txJob?.cancel()
        txEnabled = false; disarm()
        runCatching { runBlocking(Dispatchers.IO) {
            runCatching { transport.send("RX;") }
            runCatching { flex.stopTransmit("Digi controller closed") }
        } }
        interop.close()
        rawRecorder.close()
        sessionStore.close()
        sourceOriginal?.recycle(); sourceOriginal = null
        scope.cancel()
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.rigweave.mobile.contest.*
import app.rigweave.mobile.dxchaser.*
import app.rigweave.mobile.keyer.*
import app.rigweave.mobile.n1mm.*
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class ContestReadOnlySnapshot(
    val activeSession: ContestSession? = null,
    val opportunityContractVersion: Int = 1,
    val claims: List<ContestClaimSnapshot> = emptyList(),
    val n1mmEnabled: Boolean = false,
    val n1mmArmed: Boolean = false,
    val n1mmPeers: Int = 0,
    val lastError: String = "",
)

fun interface ContestReadOnlyPort { fun snapshot(): ContestReadOnlySnapshot }

internal fun contestKeyerRole(value: ContestOperatingRole): KeyerOperatingRole = when (value) {
    ContestOperatingRole.RUN -> KeyerOperatingRole.CONTEST_RUN
    ContestOperatingRole.SEARCH_AND_POUNCE -> KeyerOperatingRole.CONTEST_S_AND_P
}

internal fun contestKeyerMode(value: ContestMode): KeyerMode? = when (value) {
    ContestMode.CW -> KeyerMode.CW
    ContestMode.SSB -> KeyerMode.VOICE
    ContestMode.DIGITAL, ContestMode.MIXED -> null
}

data class ContestMutationReceipt(
    val saved: Boolean,
    val qsoId: String = "",
    val serialCommitted: Boolean = false,
    val detail: String,
)

class ContestKeyerAdapter(
    private val dispatch: () -> KeyerDispatchPort?,
    private val profiles: KeyerProfileStore,
    private val operatingContext: () -> OperatingContextSnapshot,
) {
    fun selectProfile(session: ContestSession) {
        val role = contestKeyerRole(session.role)
        val mode = contestKeyerMode(session.category.mode) ?: return
        profiles.profiles.firstOrNull { it.role == role && it.mode == mode }?.let { profile ->
            if (profiles.activeProfileId != profile.id) {
                dispatch()?.stop(KeyerStopReason.ProfileChanged)
                profiles.activate(profile.id)
            }
        }
    }

    fun submit(
        intent: ContestKeyerIntent,
        session: ContestSession,
        callsign: String,
        rstSent: String,
        rstReceived: String,
        exchange: String,
        reservation: ContestSerialReservation? = null,
        qsoId: String = "",
    ): KeyerDispatchResult {
        val port = dispatch() ?: return KeyerDispatchResult.Rejected(KeyerFailureReason.BackendUnavailable, "Keyer runtime is unavailable")
        val snapshot = operatingContext()
        if (intent.sessionId != session.id || intent.expectedContextGeneration != snapshot.generation)
            return KeyerDispatchResult.Rejected(KeyerFailureReason.ContextChanged, "Contest or operating context generation changed")
        val mode = contestKeyerMode(intent.mode) ?: return KeyerDispatchResult.Rejected(
            KeyerFailureReason.ModeUnsupported, "Contest DIGITAL/MIXED has no CW/voice keyer fallback")
        val role = contestKeyerRole(intent.role)
        val serial = reservation?.takeIf {
            it.sessionId == session.id && it.state == ContestSerialState.RESERVED &&
                it.owner == reservationOwner(qsoId, snapshot.generation)
        }?.serial?.toString().orEmpty()
        val active = profiles.activeProfile()
        if (active.role != role || active.mode != mode)
            return KeyerDispatchResult.Rejected(KeyerFailureReason.ProfileChanged, "Select the matching Contest keyer profile")
        val messageId = "contest-${intent.type.name.lowercase(Locale.US).replace('_', '-')}"
        return port.submit(KeyerAction.SendMessage(messageId), KeyerContextSnapshot(
            operatingGeneration = snapshot.generation,
            foregroundGeneration = snapshot.generation,
            radioIdentity = snapshot.radioIdentity.value,
            connected = snapshot.connected.value,
            foreground = snapshot.foreground.value,
            mode = mode,
            profileId = active.id,
            modeSupported = snapshot.mode.value.uppercase(Locale.US).let { radioMode ->
                if (mode == KeyerMode.CW) radioMode.startsWith("CW") else radioMode in setOf("USB", "LSB", "SSB")
            },
            role = role,
            myCall = snapshot.stationCallsign.value,
            call = callsign,
            rst = rstReceived,
            rstSent = rstSent,
            rstRecv = rstReceived,
            serial = serial,
            exchange = exchange,
            grid = snapshot.stationGrid.value,
            band = snapshot.band.value,
        ))
    }

    companion object {
        fun reservationOwner(qsoId: String, generation: Long) = "qso:$qsoId:g$generation"
    }
}

class ContestQsoMutationAdapter(
    private val mutations: QsoMutationCoordinator,
    private val store: ContestSessionStore,
    private val serials: ContestSerialAuthority,
    private val onAccepted: (Qso) -> Unit = {},
) {
    private val exchange = ContestExchangeEngine()

    fun save(
        session: ContestSession,
        definition: ContestDefinition,
        draft: ContestQsoDraft,
        reservation: ContestSerialReservation?,
    ): ContestMutationReceipt {
        if (draft.callsign.isBlank() || draft.frequencyHz <= 0)
            return failure(reservation, session.id, "Callsign and exact frequency are required")
        val issues = exchange.validate(definition, draft.received)
        if (issues.any { it.truth in setOf(ContestTruth.INVALID, ContestTruth.INCOMPLETE) })
            return failure(reservation, session.id, issues.joinToString { it.reason })
        val canonical = ContestQsoMapper.toCanonical(session, definition, draft)
        if (!mutations.save(canonical, QsoOrigin.OPERATOR))
            return failure(reservation, session.id, "Canonical QSO mutation failed or matched an existing record")
        return runCatching {
            store.linkQso(session.id, canonical.id, "local:${canonical.createdAt}:${canonical.id}")
            val committed = reservation?.let { serials.commit(it.id, session.id, canonical.id); true } ?: false
            onAccepted(canonical)
            ContestMutationReceipt(true, canonical.id, committed,
                "Canonical QSO saved; existing Wavelog outbox owns delivery")
        }.getOrElse { error ->
            ContestMutationReceipt(false, canonical.id, false,
                "Canonical QSO saved but Contest link requires review: ${error.javaClass.simpleName}")
        }
    }

    private fun failure(reservation: ContestSerialReservation?, sessionId: ContestSessionId, detail: String): ContestMutationReceipt {
        if (reservation?.state == ContestSerialState.RESERVED) runCatching { serials.release(reservation.id, sessionId) }
        return ContestMutationReceipt(false, detail = detail)
    }
}

class ContestRuntime(
    context: Context,
    database: QsoDatabase,
    mutations: QsoMutationCoordinator,
    private val profiles: KeyerProfileStore,
    private val keyer: () -> KeyerDispatchPort?,
    private val operatingContext: () -> OperatingContextSnapshot,
) : ContestReadOnlyPort, AutoCloseable {
    private val prefs = context.getSharedPreferences("rigweave-contest-settings", Context.MODE_PRIVATE)
    val store = ContestSessionStore(context)
    val sessionController = ContestSessionController(store)
    private val serials = ContestSerialAuthority(store)
    private val registry = ContestRuleRegistry()
    val definition: ContestDefinition = registry.all().first().definition
    private val opportunityEvaluator = DefaultContestOpportunityEvaluator(registry)
    private val canonicalReader = ContestCanonicalQsoReader(store, database)
    private val keyerAdapter = ContestKeyerAdapter(keyer, profiles, operatingContext)
    private val mutationAdapter = ContestQsoMutationAdapter(mutations, store, serials)
    private var closed = false
    private var foreground = true
    private var networkArmed = false
    private val n1mmConfig = N1mmNetworkConfig(
        enabled = prefs.getBoolean("n1mm_enabled", false),
        mode = runCatching { N1mmMode.valueOf(prefs.getString("n1mm_mode", N1mmMode.OFF.name).orEmpty()) }.getOrDefault(N1mmMode.OFF),
        stationName = "RigWeave",
        operatorCall = operatingContext().operatorCallsign.value,
        contestName = definition.cabrilloContestName,
        ruleVersion = definition.version.value,
        bindAddress = if (prefs.getBoolean("n1mm_lan_opt_in", false)) prefs.getString("n1mm_bind_address", "127.0.0.1").orEmpty() else "127.0.0.1",
        interfaceName = if (prefs.getBoolean("n1mm_lan_opt_in", false)) "operator-selected" else "loopback",
        lanBroadcastOptIn = prefs.getBoolean("n1mm_lan_opt_in", false),
    )
    val n1mm = N1mmNetworkController(n1mmConfig, onCommand = { _, decision ->
        lastMessage = "N1MM ${decision.name}; no radio, keyer, Digi, or silent logging authority"
    })

    var activeSession by mutableStateOf(loadOrCreateSession()); private set
    var workspacePage by mutableStateOf(ContestWorkspacePage.SETUP); private set
    var callsign by mutableStateOf(""); private set
    var exchangeText by mutableStateOf(""); private set
    var lastMessage by mutableStateOf("Contest setup ready; nothing is armed"); private set

    private fun loadOrCreateSession(): ContestSession {
        val stored = prefs.getString("last_session_id", null)?.let { store.loadSession(ContestSessionId(it)) }
        if (stored != null) return sessionController.restored(stored).also(store::saveSession)
        val now = Instant.now().epochSecond
        return ContestSession(
            id = ContestSessionId(UUID.randomUUID().toString()), definitionId = definition.id,
            ruleVersion = definition.version, name = definition.humanName, utcStart = now, utcEnd = now + 86_400,
            stationCallsign = operatingContext().stationCallsign.value,
            stationGrid = operatingContext().stationGrid.value, station = ContestEntityInfo(),
            category = ContestCategory(mode = definition.mode), operators = listOf(operatingContext().operatorCallsign.value),
        ).also { session -> store.saveSession(session); prefs.edit().putString("last_session_id", session.id.value).apply() }
    }

    fun setPage(value: ContestWorkspacePage) { workspacePage = value }
    fun updateCallsign(value: String) { callsign = value.uppercase(Locale.US).filter { it.isLetterOrDigit() || it == '/' }.take(16) }
    fun setExchange(value: String) { exchangeText = value.take(80) }

    fun startSession() {
        if (!foreground) { lastMessage = "Return to foreground before starting Contest"; return }
        activeSession = when (activeSession.state) {
            ContestSessionState.DRAFT -> sessionController.transition(
                sessionController.transition(activeSession, ContestSessionState.READY), ContestSessionState.RUNNING)
            ContestSessionState.READY, ContestSessionState.PAUSED, ContestSessionState.STOPPED ->
                sessionController.transition(activeSession, ContestSessionState.RUNNING)
            else -> activeSession
        }
        keyerAdapter.selectProfile(activeSession)
        lastMessage = "Contest running; Keyer and N1MM remain separately armed"
        reconcileNetwork()
    }

    fun pause(reason: String = "OPERATOR") {
        if (activeSession.state == ContestSessionState.RUNNING)
            activeSession = sessionController.transition(activeSession, ContestSessionState.PAUSED)
        networkArmed = false
        n1mm.close()
        keyer()?.stop(KeyerStopReason.ContextChanged)
        lastMessage = "Contest paused · $reason"
    }

    fun changeRole(role: ContestOperatingRole) {
        if (activeSession.role == role) return
        keyer()?.stop(KeyerStopReason.ProfileChanged)
        activeSession = sessionController.changeRole(activeSession, role)
        keyerAdapter.selectProfile(activeSession)
        lastMessage = "Contest role changed; pending Keyer and repeat-CQ work cleared"
    }

    fun setNetworkArmed(value: Boolean) {
        networkArmed = value && n1mmConfig.enabled && activeSession.state == ContestSessionState.RUNNING && foreground
        if (!networkArmed) n1mm.close() else reconcileNetwork()
        lastMessage = if (networkArmed) "N1MM explicitly armed under ${n1mmConfig.mode}" else "N1MM stopped"
    }

    fun reviewTrustedMode() {
        lastMessage = when {
            n1mmConfig.mode == N1mmMode.OFF -> "N1MM is OFF; no peer traffic is accepted"
            !n1mmConfig.lanBroadcastOptIn -> "N1MM is loopback-only; trusted-LAN mode is not enabled"
            else -> "Trusted-LAN review required: verify bind interface, subnet, contest, rule version, and every peer pin before arming"
        }
    }

    fun validateExport(format: String) {
        val qsos = priorDrafts()
        lastMessage = if (format.equals("CABRILLO", true)) {
            val result = ContestExport.cabrillo(activeSession, definition, activeSession.score, qsos.asSequence())
            "Cabrillo ${result.state.name.replace('_', ' ')} · ${qsos.size} QSO(s) · ${result.issues.size} issue(s)"
        } else {
            val records = ContestExport.adif(activeSession, definition, qsos.asSequence()).count().coerceAtLeast(1) - 1
            "ADIF validation complete · $records QSO record(s) ready for an explicit file export"
        }
    }

    private fun reconcileNetwork() {
        if (networkArmed && foreground && activeSession.state == ContestSessionState.RUNNING && !n1mm.active)
            runCatching { n1mm.start() }.onFailure { lastMessage = "N1MM start rejected: ${it.message}" }
        if ((!networkArmed || !foreground || activeSession.state != ContestSessionState.RUNNING) && n1mm.active) n1mm.close()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) pause("BACKGROUND") else reconcileNetwork()
    }

    fun reserveSerial(qsoId: String): ContestSerialReservation? = if (definition.serialRequired)
        serials.reserve(activeSession, ContestKeyerAdapter.reservationOwner(qsoId, operatingContext().generation)) else null

    fun logCurrent(): ContestMutationReceipt {
        if (activeSession.state != ContestSessionState.RUNNING)
            return ContestMutationReceipt(false, detail = "Start the Contest session before logging")
        val context = operatingContext()
        val band = ContestBand.entries.firstOrNull { it.label.equals(context.band.value, true) }
            ?: return ContestMutationReceipt(false, detail = "Current band is outside the Contest rule")
        val mode = when {
            context.mode.value.startsWith("CW", true) -> ContestMode.CW
            context.mode.value in setOf("USB", "LSB", "SSB") -> ContestMode.SSB
            context.mode.value in setOf("FT8", "FT4") -> ContestMode.DIGITAL
            else -> return ContestMutationReceipt(false, detail = "Current radio mode is outside the Contest rule")
        }
        val qsoId = UUID.randomUUID().toString()
        val reservation = reserveSerial(qsoId)
        val received = ContestExchangeEngine().parse(definition, exchangeText)
        val sent = buildMap {
            if (reservation != null) put(ContestExchangeField.SERIAL, reservation.serial.toString())
        }
        val draft = ContestQsoDraft(qsoId, callsign, Instant.now().epochSecond, context.receiveFrequencyHz.value,
            band, mode, if (mode == ContestMode.CW) "599" else "59", if (mode == ContestMode.CW) "599" else "59",
            sent = sent, received = received)
        val receipt = mutationAdapter.save(activeSession, definition, draft, reservation)
        if (receipt.saved) {
            val rows = priorDrafts()
            activeSession = activeSession.copy(score = ContestScoreEngine().rebuild(definition, rows, Instant.now().epochSecond))
            store.saveSession(activeSession)
            callsign = ""; exchangeText = ""
        }
        lastMessage = receipt.detail
        return receipt
    }

    fun dispatchKeyer(intent: ContestKeyerIntent): KeyerDispatchResult = keyerAdapter.submit(
        intent, activeSession, callsign, if (intent.mode == ContestMode.CW) "599" else "59",
        if (intent.mode == ContestMode.CW) "599" else "59", exchangeText).also { result ->
        lastMessage = when (result) {
            is KeyerDispatchResult.Accepted -> "Contest keyer intent accepted by the one Keyer controller"
            is KeyerDispatchResult.Rejected -> "Contest keyer intent blocked: ${result.detail}"
        }
    }

    fun opportunity(callsign: String, band: String, mode: String): ContestOpportunityState? {
        if (activeSession.state != ContestSessionState.RUNNING) return null
        val contestBand = ContestBand.entries.firstOrNull { it.label.equals(band, true) } ?: return null
        val contestMode = when (mode.uppercase(Locale.US)) { "FT8", "FT4" -> ContestMode.DIGITAL; "CW" -> ContestMode.CW; "SSB", "USB", "LSB" -> ContestMode.SSB; else -> return null }
        return opportunityEvaluator.evaluate(ContestOpportunityInput(activeSession, callsign, contestBand, contestMode,
            ContestEntityInfo(), priorDrafts(), snapshot().claims, Instant.now().epochSecond))
    }

    private fun priorDrafts(): List<ContestQsoDraft> {
        val page = canonicalReader.page(activeSession.id, limit = 500)
        return page.rows.mapNotNull { qso ->
            val band = ContestBand.entries.firstOrNull { it.label.equals(qso.band, true) } ?: return@mapNotNull null
            val mode = when (qso.mode.uppercase(Locale.US)) { "CW" -> ContestMode.CW; "SSB", "USB", "LSB" -> ContestMode.SSB; else -> ContestMode.DIGITAL }
            ContestQsoDraft(qso.id, qso.callsign, qso.createdAt, qso.frequencyHz, band, mode, qso.rstSent, qso.rstReceived)
        }
    }

    fun digitalCompatible(): Boolean = activeSession.state != ContestSessionState.RUNNING ||
        definition.allowedModes.any { it in setOf(ContestMode.DIGITAL, ContestMode.MIXED) }

    fun workspaceState() = ContestWorkspaceState(activeSession, definition, workspacePage, callsign, exchangeText,
        score = activeSession.score, networkMode = if (n1mm.active) n1mmConfig.mode.name else "OFF",
        peers = n1mm.peerSnapshots().map { it.station })

    override fun snapshot() = ContestReadOnlySnapshot(activeSession, claims = emptyList(),
        n1mmEnabled = n1mmConfig.enabled, n1mmArmed = networkArmed, n1mmPeers = n1mm.peerSnapshots().size,
        lastError = lastMessage.takeIf { it.contains("error", true) || it.contains("reject", true) }.orEmpty())

    override fun close() {
        if (closed) return
        closed = true
        networkArmed = false
        n1mm.close()
        keyer()?.stop(KeyerStopReason.ContextChanged)
        store.close()
    }
}

class DxChaserInputAdapter(
    private val digi: DigiController,
    private val operatingContext: () -> OperatingContextSnapshot,
    private val foreground: () -> Boolean,
    private val keyer: () -> KeyerQueueSnapshot,
    private val contest: ContestRuntime,
    private val cooldowns: () -> List<DxChaserCooldownSnapshot>,
    private val rarity: () -> Map<String, DxChaserRarity>,
    private val evidence: () -> List<DxChaserProviderEvidence> = { emptyList() },
) : DxChaserLocalDecodePort {
    override fun snapshot(): DxChaserInputSnapshot {
        val context = operatingContext()
        val digiState = digi.integrationSnapshot()
        val now = Instant.now().epochSecond
        val contestRows = digiState.decodes.asSequence().map(DigiDecodeEvent::callsign).filter(String::isNotBlank).distinct().take(100)
            .associate { call ->
                val state = contest.opportunity(call, bandForFrequency(digiState.dialFrequencyHz), digiState.mode.name)
                call.uppercase(Locale.US) to DxChaserContestOpportunity(
                    validBandMode = state?.validBandMode?.let { it == ContestTruth.VALID },
                    duplicate = state?.dupe?.let { it == ContestDupeState.DUPLICATE },
                    newMultipliers = state?.newMultipliers?.map { it.name }?.toSet().orEmpty(),
                    workedMultipliers = state?.workedMultipliers?.map { it.name }?.toSet().orEmpty(),
                    unknownMultipliers = state?.unknownMultipliers?.map { it.name }?.toSet().orEmpty(),
                    expectedExchangeHint = state?.expectedExchangeHint.orEmpty(), claimedBy = state?.claimedBy,
                )
            }
        val contestSnapshot = DxChaserContestSnapshot(contest.activeSession.id.value,
            contest.activeSession.state == ContestSessionState.RUNNING, contest.digitalCompatible(), contestRows)
        val queue = keyer()
        val safety = DxChaserSafetySnapshot(
            radioConnected = context.connected.value, receiveActive = digiState.rxActive && !digiState.txActive,
            routeHealthy = digiState.audioHealthy, digiAudioHealthy = digiState.audioHealthy,
            txActive = digiState.txActive, sequenceActive = digiState.exchange.state !in setOf(
                FtExchangeState.IDLE, FtExchangeState.COMPLETE, FtExchangeState.FAILED, FtExchangeState.STOPPED),
            foreground = foreground(), digiModeEligible = digiState.mode in setOf(DigiMode.FT8, DigiMode.FT4),
            localModemAuthority = digiState.localModemAuthority, txEnabledByOperator = digiState.txEnabled,
            contestCompatible = contest.digitalCompatible(), keyerIdle = queue.active == null && queue.pending == null,
            rxConfirmed = digiState.txPhase != DigiTxPhase.RX_UNCONFIRMED,
        )
        val local = digiState.decodes.filter {
            it.automaticFtEligible(digiState.mode.name, digiState.dialFrequencyHz, digiState.sessionId, digiState.capturedSlotStartMillis)
        }.takeLast(500).map { event ->
            val needs = buildMap {
                if (!event.worked) put(DxChaserNeedDimension.DXCC, DxChaserNeedState.NEEDED)
                if (!event.confirmed) put(DxChaserNeedDimension.BAND_ENTITY, DxChaserNeedState.NEEDED)
            }
            val text = event.text.uppercase(Locale.US)
            DxChaserLocalDecode(
                id = event.id, sessionId = event.sessionId,
                slotIdentity = "${event.sessionId}:${event.slotStartMillis}:${event.mode}",
                slotStartMillis = event.slotStartMillis, epochSeconds = event.epoch,
                source = when (event.decodeSource) {
                    DigiDecodeSource.LIVE_CAPTURE -> DxChaserDecodeSource.LIVE_CAPTURE
                    DigiDecodeSource.REDECODE_LIVE_SLOT -> DxChaserDecodeSource.REDECODE_LIVE_SLOT
                    DigiDecodeSource.REFERENCE_RECORDING -> DxChaserDecodeSource.REFERENCE_RECORDING
                    DigiDecodeSource.COMPANION -> DxChaserDecodeSource.COMPANION
                },
                exactSlotTiming = event.exactSlotTiming, mode = event.mode,
                band = bandForFrequency(event.dialFrequencyHz), dialFrequencyHz = event.dialFrequencyHz,
                audioFrequencyHz = event.audioHz.toInt(), callsign = event.callsign,
                baseCallsign = DigiFtParser.baseCall(event.callsign), grid = event.grid, entity = event.country,
                snr = event.snr.toInt(), message = event.text,
                messageType = when {
                    text.startsWith("CQ ") -> DxChaserMessageType.CQ
                    text.contains(context.stationCallsign.value.uppercase(Locale.US)) -> DxChaserMessageType.ADDRESSED_TO_OPERATOR
                    else -> DxChaserMessageType.BYSTANDER
                },
                stationProfileId = context.stationProfileId.value, radioIdentity = context.radioIdentity.value,
                needs = DxChaserNeedFacts(needs, event.worked, event.confirmed, event.watchlisted),
            )
        }.toList()
        return DxChaserInputSnapshot(context.generation, foreground(), now, context.stationProfileId.value,
            context.stationCallsign.value, context.stationGrid.value, context.radioIdentity.value,
            context.radioFamily.value, digiState.dialFrequencyHz, bandForFrequency(digiState.dialFrequencyHz),
            digiState.mode.name, digiState.sessionId, digiState.capturedSlotStartMillis, safety, local,
            evidence().take(500), rarity(), cooldowns().take(100), contest = contestSnapshot)
    }
}

class DxChaserRuntime(
    context: Context,
    private val database: QsoDatabase,
    private val digi: DigiController,
    operatingContext: () -> OperatingContextSnapshot,
    foreground: () -> Boolean,
    keyer: () -> KeyerQueueSnapshot,
    private val contest: ContestRuntime,
    private val requestReceiveReview: (DxChaserActionIntent) -> Unit,
    private val openWorkspaceAction: (DxChaserActionIntent) -> Unit,
    evidence: () -> List<DxChaserProviderEvidence> = { emptyList() },
) : AutoCloseable {
    internal val store = DxChaserStore(context)
    private val settingsStore = DxChaserSettingsStore(context)
    var settings by mutableStateOf(settingsStore.load()); private set
    private lateinit var controller: DxChaserController
    private val input = DxChaserInputAdapter(digi, operatingContext, foreground, keyer, contest,
        { store.activeCooldowns(Instant.now().epochSecond) }, store::rarity, evidence)
    private var closed = false
    private var lastExchangeState = FtExchangeState.IDLE
    private var lastLoggedQsoId = ""
    var snapshot by mutableStateOf(DxChaserReadOnlySnapshot()); private set
    var lastMessage by mutableStateOf("DX Chaser inactive; opening this page never starts a session"); private set

    init {
        controller = DxChaserController(DxChaserDependencies(input, DxChaserActionPort(::routeAction),
            settings = { settings }, journal = store))
        snapshot = controller.snapshot()
    }

    private fun routeAction(intent: DxChaserActionIntent) {
        when (intent.type) {
            DxChaserActionType.PREPARE_FT_CALL -> {
                val result = digi.prepareExactLiveCall(DigiExactCallRequest(intent.localDecodeId,
                    intent.slotIdentity.substringBefore(':'), intent.slotIdentity.split(':').getOrNull(1)?.toLongOrNull() ?: 0,
                    intent.mode, intent.dialFrequencyHz ?: 0, intent.callsign))
                when (result) {
                    is DigiPrepareResult.Accepted -> { controller.prepareAccepted(); lastMessage = "Exact local decode prepared through Digi" }
                    is DigiPrepareResult.Rejected -> { controller.prepareRejected(result.reason); lastMessage = result.reason }
                }
            }
            DxChaserActionType.REQUEST_RECEIVE_BAND_REVIEW -> requestReceiveReview(intent)
            DxChaserActionType.OPEN_DX_DETAILS, DxChaserActionType.OPEN_LOGBOOK_HISTORY,
            DxChaserActionType.ADD_WATCH, DxChaserActionType.REMOVE_WATCH -> openWorkspaceAction(intent)
            DxChaserActionType.STOP_CHASE -> digi.stopSequence()
            else -> Unit
        }
        snapshot = controller.snapshot().copy(databaseCounts = store.counts())
    }

    fun startAssist() = start(DxChaserMode.ASSIST)
    fun startDryRun() = start(DxChaserMode.DRY_RUN)
    fun startChase(): Boolean {
        if (!input.snapshot().safety.preparationPermitted) {
            lastMessage = "Chase blocked by the visible Digi/Contest/Keyer safety interlocks"
            snapshot = controller.snapshot().copy(databaseCounts = store.counts())
            return false
        }
        start(DxChaserMode.CHASE_SESSION)
        return true
    }

    private fun start(mode: DxChaserMode) {
        if (closed) return
        controller.start(mode, UUID.randomUUID().toString())
        controller.refresh()
        lastMessage = "$mode started by explicit operator action"
        snapshot = controller.snapshot().copy(databaseCounts = store.counts())
    }

    fun select(candidate: DxChaserCandidateSnapshot) { controller.select(candidate); snapshot = controller.snapshot().copy(databaseCounts = store.counts()) }
    fun review(value: DxChaserCrossBandOpportunity) { controller.review(value); snapshot = controller.snapshot().copy(databaseCounts = store.counts()) }
    fun stop(reason: String = "OPERATOR_STOP") { controller.stop(reason); digi.stopSequence(); snapshot = controller.snapshot().copy(databaseCounts = store.counts()) }
    fun contextLost(reason: String) { controller.contextLost(reason); snapshot = controller.snapshot().copy(databaseCounts = store.counts()) }

    fun updateSettings(value: DxChaserSettingsDocument) {
        settings = value.clamped(); settingsStore.save(settings)
        controller.contextLost("SETTINGS_CHANGED")
        snapshot = controller.snapshot().copy(databaseCounts = store.counts())
    }

    fun poll() {
        if (closed) return
        controller.refresh()
        val state = digi.integrationSnapshot()
        val exchange = state.exchange
        if (lastExchangeState != exchange.state) {
            when {
                exchange.state !in setOf(FtExchangeState.IDLE, FtExchangeState.STOPPED, FtExchangeState.FAILED) &&
                    lastExchangeState in setOf(FtExchangeState.IDLE, FtExchangeState.STOPPED, FtExchangeState.FAILED) -> controller.sequenceStarted()
                exchange.state in setOf(FtExchangeState.R_REPORT_TX_PENDING, FtExchangeState.WAIT_RR73,
                    FtExchangeState.RR73_TX_PENDING, FtExchangeState.WAIT_FINAL_73, FtExchangeState.FINAL_73_TX_PENDING) -> controller.remoteEngaged()
                exchange.state == FtExchangeState.FAILED -> controller.qsoFailed(exchange.completionReason.ifBlank { "DIGI_SEQUENCE_FAILED" })
                exchange.state == FtExchangeState.STOPPED -> controller.stop("DIGI_OPERATOR_STOP")
            }
            lastExchangeState = exchange.state
        }
        if (state.txPhase == DigiTxPhase.RX_UNCONFIRMED) controller.qsoFailed("RX_UNCONFIRMED")
        if (state.lastLoggedQsoId.isNotBlank() && state.lastLoggedQsoId != lastLoggedQsoId) {
            val qso = database.qso(state.lastLoggedQsoId)
            val target = controller.snapshot().currentTarget?.candidate?.baseCallsign
            if (qso != null && target != null && DigiFtParser.baseCall(qso.callsign) == target) controller.qsoCompleted()
            lastLoggedQsoId = state.lastLoggedQsoId
        }
        snapshot = controller.snapshot().copy(databaseCounts = store.counts())
    }

    override fun close() {
        if (closed) return
        closed = true
        controller.close()
        store.close()
    }
}

class OperatorStopRouter(
    private val digi: DigiController,
    private val keyer: KeyerDispatchPort,
    private val repeatCq: RepeatCqController,
    private val contest: ContestRuntime,
    private val chaser: DxChaserRuntime,
) {
    fun stopAll(reason: String = "OPERATOR_STOP") {
        digi.stopSequence()
        keyer.stop(KeyerStopReason.Operator)
        repeatCq.stop()
        chaser.stop(reason)
        contest.setNetworkArmed(false)
    }
}

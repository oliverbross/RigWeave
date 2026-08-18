package app.rigweave.mobile

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

enum class FlexConnectionState(val label: String) {
    IDLE("NO LAN RADIOS"), SEARCHING("SEARCHING LAN"), SIGNED_OUT("SIGNED OUT"), SIGNING_IN("SIGNING IN"),
    DISCOVERING_WAN("DISCOVERING SMARTLINK RADIOS"), CONNECTING("CONNECTING"), VALIDATING("VALIDATING WAN SESSION"),
    WAITING_HANDLE("WAITING FOR FLEX HANDLE"), CONNECTED("CONNECTED"), NO_STATION("NO GUI STATION"),
    NO_SLICE("NO EXISTING FLEX SLICE"), LOST("CONNECTION LOST"), RECONNECTING("RECONNECTING"),
    OWNER_CONFIG_REQUIRED("OWNER CONFIGURATION VALUES REQUIRED")
}

class FlexRadioController(context: Context, private val preferredStation: () -> String, private val saveStation: (String) -> Unit) : RadioBackend {
    override val capabilities = setOf(RadioCapability.RECEIVE_STATE, RadioCapability.RECEIVE_TUNE, RadioCapability.FILTER)
    private val native = NativeCore.flexCreate()
    private val controllerJob = SupervisorJob()
    private val scope = CoroutineScope(controllerJob + Dispatchers.IO)
    private val sequence = AtomicInteger(1)
    private var socket: Socket? = null
    private var reader: Job? = null
    private var keepalive: Job? = null
    private var discovery: Job? = null
    private var reconnect: Job? = null
    private var operatorEstablished = false
    private var lastTarget: FlexDiscovery? = null
    private val smartLinkConfig = SmartLinkConfig.issued()
    private val refreshStore = SmartLinkRefreshStore(context)
    private var authSession: SmartLinkAuthSession? = null
    private var accessToken: String? = null
    private val refreshMutex = Mutex()
    private var broker: SmartLinkBrokerClient? = null
    val radios = mutableStateListOf<FlexDiscovery>()
    val smartLinkRadios = mutableStateListOf<SmartLinkRadio>()
    var snapshot by mutableStateOf(FlexSnapshot()); private set
    var selectedSliceIndex by mutableStateOf<Int?>(null); private set
    var connectionState by mutableStateOf(FlexConnectionState.IDLE); private set
    var detail by mutableStateOf("FlexRadio is not connected"); private set
    val smartLinkConfigured get() = smartLinkConfig.complete
    override val state get() = snapshot.toRadioState(selectedSliceIndex)

    init {
        if (smartLinkConfigured) refreshStore.load()?.let { stored -> scope.launch {
            refreshMutex.withLock {
                SmartLinkAuth.refresh(smartLinkConfig, stored)?.let { tokens ->
                    accessToken = tokens.idToken; refreshStore.save(tokens.refreshToken)
                    withContext(Dispatchers.Main) { connectionState = FlexConnectionState.DISCOVERING_WAN; discoverSmartLinkRadios() }
                }
            }
        } }
    }

    fun beginSmartLinkSignIn(): Uri? {
        if (!smartLinkConfigured) { connectionState = FlexConnectionState.OWNER_CONFIG_REQUIRED; return null }
        val session = SmartLinkAuth.begin(smartLinkConfig) ?: return null
        authSession = session; connectionState = FlexConnectionState.SIGNING_IN; return session.authorizationUri
    }

    fun completeSmartLinkSignIn(redirect: Uri) {
        val session = authSession ?: return
        val tokens = SmartLinkAuth.validateRedirect(smartLinkConfig, session, redirect) ?: run { detail = "SmartLink redirect validation failed"; connectionState = FlexConnectionState.SIGNED_OUT; return }
        authSession = null
        accessToken = tokens.idToken
        refreshStore.save(tokens.refreshToken)
        detail = "SmartLink sign-in complete"
        connectionState = FlexConnectionState.DISCOVERING_WAN
        discoverSmartLinkRadios()
    }

    fun logout() { accessToken = null; authSession = null; broker?.close(); broker = null; smartLinkRadios.clear(); refreshStore.clear(); connectionState = FlexConnectionState.SIGNED_OUT; detail = "SmartLink signed out" }

    private fun discoverSmartLinkRadios() {
        val token = accessToken ?: return
        scope.launch {
            val result = runCatching {
                broker?.close(); SmartLinkBrokerClient(smartLinkConfig).also { broker = it }.connectAndList(token)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { smartLinkRadios.clear(); smartLinkRadios.addAll(it); detail = "${it.size} SmartLink radio(s) available" }
                    .onFailure { detail = it.message ?: "SmartLink broker discovery failed"; connectionState = FlexConnectionState.LOST }
            }
        }
    }

    suspend fun connectSmartLink(radio: SmartLinkRadio): Boolean {
        disconnectSession(); connectionState = FlexConnectionState.CONNECTING; detail = "Requesting ${radio.nickname.ifBlank { radio.model }} through SmartLink"
        return runCatching {
            val endpoint = withContext(Dispatchers.IO) { broker?.request(radio) ?: error("SmartLink broker did not provide a direct endpoint") }
            connectionState = FlexConnectionState.VALIDATING
            val connected = withContext(Dispatchers.IO) { connectValidatedWan(endpoint) }
            establishCommandChannel(connected, operatorInitiated = true)
        }.getOrElse { detail = it.message ?: "SmartLink connection failed"; connectionState = FlexConnectionState.LOST; false }
    }

    fun discoverLan(seconds: Int = 4) {
        discovery?.cancel(); radios.clear(); connectionState = FlexConnectionState.SEARCHING
        discovery = scope.launch {
            val seen = linkedMapOf<String, Pair<FlexDiscovery, Long>>()
            try {
                DatagramSocket(null).use { udp ->
                    udp.reuseAddress = true; udp.broadcast = true; udp.soTimeout = 350
                    udp.bind(InetSocketAddress(4992))
                    val deadline = android.os.SystemClock.elapsedRealtime() + seconds.coerceIn(1, 10) * 1_000L
                    while (isActive && android.os.SystemClock.elapsedRealtime() < deadline) {
                        val buffer = ByteArray(4096); val packet = DatagramPacket(buffer, buffer.size)
                        try { udp.receive(packet); parseFlexDiscovery(buffer.copyOf(packet.length))?.let { radio ->
                            seen[radio.serial.ifBlank { radio.ip }] = radio to android.os.SystemClock.elapsedRealtime()
                            val fresh = seen.values.filter { android.os.SystemClock.elapsedRealtime() - it.second < 4_000 }.map { it.first }
                            withContext(Dispatchers.Main) { radios.clear(); radios.addAll(fresh) }
                        } } catch (_: SocketTimeoutException) { }
                    }
                }
                withContext(Dispatchers.Main) { connectionState = if (radios.isEmpty()) FlexConnectionState.IDLE else FlexConnectionState.SIGNED_OUT }
            } catch (error: Exception) {
                if (error !is CancellationException) withContext(Dispatchers.Main) { detail = error.message ?: "LAN discovery failed"; connectionState = FlexConnectionState.LOST }
            }
        }
    }

    suspend fun connectLan(radio: FlexDiscovery, operatorInitiated: Boolean = true): Boolean {
        lastTarget = radio
        disconnectSession()
        connectionState = FlexConnectionState.CONNECTING; detail = "Connecting to ${radio.nickname.ifBlank { radio.model }}"
        return runCatching {
            val connected = withContext(Dispatchers.IO) { Socket().apply { connect(InetSocketAddress(radio.ip, radio.port), 5_000); soTimeout = 750 } }
            establishCommandChannel(connected, operatorInitiated)
        }.getOrElse { detail = it.message ?: "Flex connection failed"; connectionState = FlexConnectionState.LOST; false }
    }

    private suspend fun establishCommandChannel(connected: Socket, operatorInitiated: Boolean): Boolean {
        socket = connected; connectionState = FlexConnectionState.WAITING_HANDLE
        reader = scope.launch { readLoop(connected) }
        val handleDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (snapshot.handle == 0L && android.os.SystemClock.elapsedRealtime() < handleDeadline) delay(100)
        if (snapshot.handle == 0L) error("Flex connected without a nonzero client handle")
        sendBody(NativeCore.flexIdentity("RigWeave"))
        NativeCore.flexSubscriptions().lineSequence().filter(String::isNotBlank).forEach(::sendBody)
        keepalive = scope.launch { while (isActive) { delay(5_000); sendBody(NativeCore.flexKeepalive()) } }
        updateSelection(); connectionState = effectiveState(); if (operatorInitiated) operatorEstablished = true
        return true
    }

    override suspend fun connect(): Boolean = lastTarget?.let { connectLan(it) } ?: false

    private suspend fun readLoop(connected: Socket) {
        val buffer = ByteArray(8192)
        try {
            while (scope.isActive && !connected.isClosed) {
                val count = try { connected.getInputStream().read(buffer) } catch (_: SocketTimeoutException) { continue }
                if (count < 0) break
                NativeCore.flexFeed(native, buffer.copyOf(count)); snapshot = parseFlexSnapshot(NativeCore.flexState(native)); updateSelection()
                connectionState = effectiveState()
            }
        } catch (_: CancellationException) { throw CancellationException() }
        catch (error: Exception) { detail = error.message ?: "Flex connection lost" }
        finally { if (socket === connected && !connected.isClosed) {
            runCatching { connected.close() }; socket = null; connectionState = FlexConnectionState.LOST; detail = "Flex command channel closed"
            scheduleReconnect()
        } }
    }

    private fun scheduleReconnect() {
        val target = lastTarget ?: return
        if (!operatorEstablished || reconnect?.isActive == true) return
        reconnect = scope.launch {
            repeat(3) { attempt ->
                delay((attempt + 1) * 1_000L); connectionState = FlexConnectionState.RECONNECTING
                if (connectLan(target, operatorInitiated = false)) return@launch
            }
            connectionState = FlexConnectionState.LOST; detail = "Reconnect stopped after 3 bounded attempts"
        }
    }

    private fun effectiveState(): FlexConnectionState = when {
        snapshot.handle == 0L -> FlexConnectionState.WAITING_HANDLE
        snapshot.clients.none { it.gui && it.connected } -> FlexConnectionState.NO_STATION
        snapshot.selected(selectedSliceIndex) == null -> FlexConnectionState.NO_SLICE
        else -> FlexConnectionState.CONNECTED
    }

    private fun updateSelection() {
        if (snapshot.selected(selectedSliceIndex) != null) return
        val station = preferredStation()
        val stationHandles = snapshot.clients.filter { it.connected && it.station == station }.map { it.handle }.toSet()
        val compatible = snapshot.slices.filter { it.inUse && !it.tx && (station.isBlank() || it.clientHandle in stationHandles) }
        selectedSliceIndex = when { compatible.size == 1 -> compatible.single().index; else -> null }
    }

    fun selectStation(station: String) { saveStation(station); selectedSliceIndex = null; updateSelection(); connectionState = effectiveState() }
    fun selectSlice(index: Int) { selectedSliceIndex = snapshot.slices.firstOrNull { it.index == index && it.inUse && !it.tx }?.index; connectionState = effectiveState() }

    @Synchronized private fun sendBody(body: String): Boolean {
        if (body.isBlank()) return false
        val active = socket ?: return false
        val seq = sequence.getAndUpdate { if (it == Int.MAX_VALUE) 1 else it + 1 }
        return runCatching { active.getOutputStream().write("C$seq|$body\n".toByteArray(Charsets.US_ASCII)); active.getOutputStream().flush(); true }.getOrDefault(false)
    }

    override suspend fun tune(request: ReceiveTuneRequest): Boolean {
        val slice = snapshot.selected(selectedSliceIndex) ?: return false
        val frequency = NativeCore.flexFrequency(slice.index, request.frequencyHz)
        if (!sendBody(frequency)) return false
        val mode = request.mode?.takeIf(String::isNotBlank) ?: return true
        return sendBody(NativeCore.flexMode(slice.index, mode))
    }

    suspend fun setFilter(lowHz: Int, highHz: Int): Boolean = snapshot.selected(selectedSliceIndex)?.let {
        sendBody(NativeCore.flexFilter(it.letter, lowHz, highHz))
    } ?: false

    private fun disconnectSession() {
        keepalive?.cancel(); reader?.cancel(); keepalive = null; reader = null
        runCatching { socket?.close() }; socket = null; snapshot = FlexSnapshot(); selectedSliceIndex = null
    }

    override suspend fun disconnect() {
        operatorEstablished = false; reconnect?.cancel(); reconnect = null; disconnectSession()
        connectionState = if (smartLinkConfigured) FlexConnectionState.SIGNED_OUT else FlexConnectionState.OWNER_CONFIG_REQUIRED
        detail = "FlexRadio disconnected"
    }

    override fun close() {
        operatorEstablished = false; discovery?.cancel(); reconnect?.cancel(); keepalive?.cancel(); reader?.cancel(); broker?.close(); broker = null
        runCatching { socket?.close() }; socket = null; controllerJob.cancel(); NativeCore.flexDestroy(native)
    }
}

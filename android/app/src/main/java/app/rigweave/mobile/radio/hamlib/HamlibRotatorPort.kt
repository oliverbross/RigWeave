package app.rigweave.mobile.radio.hamlib

import app.rigweave.mobile.rotator.CapabilitySupport
import app.rigweave.mobile.rotator.RotatorBackend
import app.rigweave.mobile.rotator.RotatorCapability
import app.rigweave.mobile.rotator.RotatorCapabilitySnapshot
import app.rigweave.mobile.rotator.RotatorDeviceProfile
import app.rigweave.mobile.rotator.RotatorHamlibCapabilitySnapshot
import app.rigweave.mobile.rotator.RotatorHamlibModelDescriptor
import app.rigweave.mobile.rotator.RotatorHamlibPort
import app.rigweave.mobile.rotator.RotatorHamlibSession
import app.rigweave.mobile.rotator.RotatorMovementState
import app.rigweave.mobile.rotator.RotatorStateSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class NativeHamlibRotatorPort(
    private val serialPort: (RotatorDeviceProfile) -> HamlibSerialTransportPort? = { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : RotatorHamlibPort {
    private data class Model(
        val descriptor: RotatorHamlibModelDescriptor,
        val capabilities: RotatorCapabilitySnapshot,
    )
    private data class Active(
        val handle: Long,
        val profile: RotatorDeviceProfile,
        val readOnly: Boolean,
        val transport: HamlibSerialTransportPort?,
        val bridge: Job?,
        var generation: Long = 0,
    )

    private val sessions = mutableMapOf<String, Active>()
    private val models: List<Model> by lazy { parseModels(NativeHamlib.rotatorModels()) }

    override suspend fun enumerateModels(): List<RotatorHamlibModelDescriptor> = models.map(Model::descriptor)

    override suspend fun capabilities(modelId: Int): RotatorHamlibCapabilitySnapshot =
        RotatorHamlibCapabilitySnapshot(modelId, models.firstOrNull { it.descriptor.id == modelId }?.capabilities
            ?: RotatorCapabilitySnapshot(provenance = "hamlib-model-unavailable"))

    override suspend fun open(profile: RotatorDeviceProfile, readOnly: Boolean): RotatorHamlibSession {
        require(profile.backend == RotatorBackend.EMBEDDED_HAMLIB)
        val modelId = requireNotNull(profile.hamlibModelId)
        val handle = NativeHamlib.rotatorCreate(modelId).also { check(it != 0L) { "Hamlib rotator model unavailable" } }
        var transport: HamlibSerialTransportPort? = null
        var bridge: Job? = null
        try {
            checked(NativeHamlib.rotatorReadOnly(handle, readOnly))
            profile.hamlibSerial?.let { serial ->
                val serialProfile = HamlibSerialProfile(serial.stableIdentityHash, serial.baud, serial.dataBits,
                    serial.stopBits, when (serial.parity) { "E" -> 1; "O" -> 2; else -> 0 },
                    timeoutMs = serial.readTimeoutMs, rts = if (serial.rts) 1 else 0, dtr = if (serial.dtr) 1 else 0)
                transport = requireNotNull(serialPort(profile)) { "application USB serial authority unavailable" }
                transport!!.configure(serialProfile)
                checked(NativeHamlib.rotatorConfigure(handle, serialProfile))
                bridge = startBridge(handle, transport!!)
            }
            profile.hamlibTcp?.let { tcp ->
                require(tcp.lanOptIn) { "Hamlib rotator LAN profile is not enabled" }
                checked(NativeHamlib.rotatorConfigure(handle, HamlibNetworkProfile(tcp.host, tcp.port, tcp.readTimeoutMs, true)))
            }
            require(profile.hamlibSerial != null || profile.hamlibTcp != null) { "Hamlib rotator transport is not configured" }
            checked(NativeHamlib.rotatorOpen(handle))
            return RotatorHamlibSession(UUID.randomUUID().toString(), modelId).also { session ->
                synchronized(sessions) { sessions[session.id] = Active(handle, profile, readOnly, transport, bridge) }
            }
        } catch (failure: Throwable) {
            bridge?.cancel()
            runCatching { transport?.disconnect() }
            runCatching { NativeHamlib.rotatorClose(handle) }
            NativeHamlib.rotatorDestroy(handle)
            throw failure
        }
    }

    override suspend fun close(session: RotatorHamlibSession) {
        val active = synchronized(sessions) { sessions.remove(session.id) } ?: return
        runCatching { NativeHamlib.rotatorClose(active.handle) }
        active.bridge?.cancelAndJoin()
        runCatching { active.transport?.disconnect() }
        NativeHamlib.rotatorDestroy(active.handle)
    }

    override suspend fun poll(session: RotatorHamlibSession): RotatorStateSnapshot {
        val active = active(session)
        val row = JSONObject(NativeHamlib.rotatorPoll(active.handle))
        checked(row.getInt("code"))
        active.generation++
        return RotatorStateSnapshot(
            profileId = active.profile.id,
            displayName = active.profile.name,
            backend = active.profile.backend,
            protocol = active.profile.protocol,
            transport = active.profile.transport,
            connected = true,
            ready = true,
            azimuthDeg = row.getDouble("azimuth"),
            elevationDeg = row.getDouble("elevation"),
            movement = RotatorMovementState.UNKNOWN,
            lastUpdate = Instant.now(),
            generation = active.generation,
            limits = active.profile.limits,
        )
    }

    override suspend fun setPosition(session: RotatorHamlibSession, azimuthDeg: Double, elevationDeg: Double?): Boolean {
        val active = active(session)
        if (active.readOnly || !active.profile.limits.contains(azimuthDeg, elevationDeg)) return false
        return NativeHamlib.rotatorSetPosition(active.handle, azimuthDeg, elevationDeg ?: 0.0) == 0
    }

    override suspend fun stop(session: RotatorHamlibSession): Boolean = NativeHamlib.rotatorStop(active(session).handle) == 0

    override suspend fun park(session: RotatorHamlibSession): Boolean {
        val active = active(session)
        return !active.readOnly && NativeHamlib.rotatorPark(active.handle) == 0
    }

    private fun active(session: RotatorHamlibSession): Active =
        synchronized(sessions) { sessions[session.id] } ?: error("Hamlib rotator session is closed")

    private fun startBridge(handle: Long, port: HamlibSerialTransportPort): Job = scope.launch {
        val applicationToHamlib = launch {
            try {
                while (isActive) {
                    val input = port.read(4_096, 250)
                    if (input.isNotEmpty() && NativeHamlib.rotatorBridgeWrite(handle, input) < 0) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
        }
        val hamlibToApplication = launch {
            try {
                while (isActive) {
                    val output = NativeHamlib.rotatorBridgeRead(handle, 4_096, 250)
                    if (output.isNotEmpty() && port.write(output) < 0) break
                }
            } catch (cancelled: CancellationException) { throw cancelled }
        }
        listOf(applicationToHamlib, hamlibToApplication).joinAll()
    }

    private fun checked(status: Int) {
        check(status == 0) { NativeHamlib.error(status) }
    }

    private fun parseModels(payload: String): List<Model> {
        val rows = JSONArray(payload)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val values = buildMap {
                put(RotatorCapability.AZIMUTH, CapabilitySupport.SUPPORTED)
                put(RotatorCapability.ELEVATION,
                    if (row.getDouble("maxEl") > row.getDouble("minEl")) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED)
                put(RotatorCapability.POSITION_QUERY, support(row.getBoolean("getPosition")))
                put(RotatorCapability.ABSOLUTE_MOVE, support(row.getBoolean("setPosition")))
                put(RotatorCapability.STOP, support(row.getBoolean("stop")))
                put(RotatorCapability.PARK, support(row.getBoolean("park")))
                put(RotatorCapability.LIMITS, CapabilitySupport.SUPPORTED)
            }
            Model(RotatorHamlibModelDescriptor(row.getInt("id"), row.getString("manufacturer"),
                row.getString("model"), row.getString("status")),
                RotatorCapabilitySnapshot(values, "embedded Hamlib ${NativeHamlib.libraryInfo()}"))
        }
    }

    private fun support(value: Boolean) = if (value) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED
}

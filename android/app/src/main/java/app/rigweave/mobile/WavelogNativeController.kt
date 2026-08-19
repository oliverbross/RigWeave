package app.rigweave.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WavelogNativeController(
    private val database: QsoDatabase,
    private val legacy: WavelogController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = WavelogSyncStore(database)

    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("API v2 not inspected"); private set
    var inspection by mutableStateOf<WavelogConnectionInspection?>(null); private set
    var binding by mutableStateOf(store.activeBinding()); private set
    var lastSummary by mutableStateOf<WavelogSyncSummary?>(null); private set
    var progressPage by mutableStateOf(0); private set
    var openConflicts by mutableStateOf(binding?.let { store.openConflicts(it.id) } ?: 0); private set

    fun inspect() = runOperation("Inspecting Wavelog API v2…") {
        require(legacy.apiKey.startsWith("wl2_")) { "Enter a Wavelog API v2 wl2_ token in Settings" }
        val result = client().let { WavelogSyncEngine(database, store, it).inspectConnection() }
        withContext(Dispatchers.Main) {
            inspection = result
            val capabilities = result.token.capabilities
            val access = if (capabilities.canWriteQsos) "read/write" else if (capabilities.canReadQsos) "read-only" else "no QSO scope"
            status = "${result.token.owner.ifBlank { "Token owner" }} · $access · ${result.stations.size} stations"
        }
    }

    fun bindStation(station: WavelogV2Station) = runOperation("Saving station binding…") {
        val inspected = inspection ?: error("Inspect the API v2 token first")
        val capabilities = inspected.token.capabilities
        require(capabilities.canReadQsos) { "The token needs qso:read scope" }
        val current = store.activeBinding()
        val saved = WavelogBinding(
            id = current?.id ?: java.util.UUID.randomUUID().toString(),
            baseUrl = normalizeWavelogV2Root(legacy.baseURL).removeSuffix("/api/v2"),
            credentialAlias = "android-keystore:wavelog",
            apiGeneration = WavelogApiGeneration.V2,
            capabilities = capabilities,
            tokenOwner = inspected.token.owner,
            remoteStationId = station.id,
            remoteStationUuid = station.uuid,
            remoteStationName = listOf(station.name, station.callsign, station.grid).filter(String::isNotBlank).joinToString(" · "),
            localStationProfileId = station.id,
            state = if (capabilities.canWriteQsos) WavelogBindingState.ENABLED else WavelogBindingState.READ_ONLY,
            downstreamPolicy = "WAVELOG_AUTHORITY",
        )
        store.saveBinding(saved)
        withContext(Dispatchers.Main) {
            binding = saved
            legacy.setStation(station.id)
            status = "Bound to ${saved.remoteStationName} · ${if (saved.state == WavelogBindingState.READ_ONLY) "read-only replica" else "two-way sync"}"
        }
    }

    fun initialSync() = synchronize("Initial sync") { engine, active, progress -> engine.initialSync(active, progress) }
    fun quickSync() = synchronize("Quick sync") { engine, active, progress -> engine.quickSync(active, onProgress = progress) }
    fun fullReconcile() = synchronize("Full reconciliation") { engine, active, progress -> engine.fullReconcile(active, progress) }

    private fun synchronize(label: String,
        operation: (WavelogSyncEngine, WavelogBinding, (Int, WavelogSyncSummary) -> Unit) -> WavelogSyncSummary
    ) = runOperation("$label started…") {
        val active = binding ?: error("Bind a Wavelog station first")
        val progress: (Int, WavelogSyncSummary) -> Unit = { page, summary ->
            scope.launch(Dispatchers.Main) { progressPage = page; lastSummary = summary; status = "$label · page $page" }
        }
        val summary = operation(WavelogSyncEngine(database, store, client()), active, progress)
        withContext(Dispatchers.Main) {
            lastSummary = summary
            openConflicts = store.openConflicts(active.id)
            status = "$label complete · ${summary.imported} imported · ${summary.pulled} pulled · ${summary.queuedForPush} queued · ${summary.conflicts} conflicts"
        }
    }

    private fun client() = WavelogApiV2Client(legacy.baseURL, legacy.apiKey)

    private fun runOperation(start: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true; status = start
        scope.launch {
            try { block() }
            catch (error: WavelogApiException) { withContext(Dispatchers.Main) { status = safeError(error.errorClass, error.message) } }
            catch (error: Exception) { withContext(Dispatchers.Main) { status = safeError(WavelogErrorClass.VALIDATION, error.message) } }
            finally { withContext(Dispatchers.Main) { busy = false } }
        }
    }

    private fun safeError(type: WavelogErrorClass, message: String?) =
        "${type.name.lowercase().replace('_', ' ')} · ${message.orEmpty().take(240).ifBlank { "Operation failed" }}"

    fun close() = scope.cancel()
}

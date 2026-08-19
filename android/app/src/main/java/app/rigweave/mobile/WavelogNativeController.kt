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
import java.util.concurrent.atomic.AtomicBoolean

class WavelogNativeController(
    private val database: QsoDatabase,
    private val legacy: WavelogController,
    private val mutations: QsoMutationCoordinator = QsoMutationCoordinator(database),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = WavelogSyncStore(database)
    private val cancelRequested = AtomicBoolean(false)
    private var lastAutomaticSync = 0L

    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("API v2 not inspected"); private set
    var inspection by mutableStateOf<WavelogConnectionInspection?>(null); private set
    var binding by mutableStateOf(store.activeBinding()); private set
    var lastSummary by mutableStateOf<WavelogSyncSummary?>(null); private set
    var progressPage by mutableStateOf(0); private set
    var conflicts by mutableStateOf(binding?.let { store.conflicts(it.id) } ?: emptyList()); private set
    val openConflicts get() = conflicts.count { it.state == WavelogConflictState.OPEN }
    val localStationIds get() = mutations.localStationIds()

    fun inspect() = runOperation("Inspecting Wavelog API v2…") {
        require(legacy.apiKey.startsWith("wl2_")) { "Enter a Wavelog API v2 wl2_ token in Settings" }
        val result = engine().inspectConnection()
        withContext(Dispatchers.Main) {
            inspection = result
            val access = when {
                result.token.capabilities.canWriteQsos -> "read/write"
                result.token.capabilities.canReadQsos -> "read-only"
                else -> "no QSO scope"
            }
            status = "${result.token.owner.ifBlank { "Token owner" }} · $access · ${result.stations.size} stations"
        }
    }

    fun bindStation(station: WavelogV2Station, localStationId: String) =
        runOperation("Saving explicit station mapping…") {
            val inspected = inspection ?: error("Inspect the API v2 token first")
            require(localStationId in localStationIds) { "Select an existing local station profile" }
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
                remoteStationName = listOf(station.name, station.callsign, station.grid)
                    .filter(String::isNotBlank).joinToString(" · "),
                localStationProfileId = localStationId,
                state = if (capabilities.canWriteQsos) WavelogBindingState.ENABLED else WavelogBindingState.READ_ONLY,
                downstreamPolicy = "WAVELOG_AUTHORITY",
            )
            store.saveBinding(saved)
            withContext(Dispatchers.Main) {
                binding = saved
                conflicts = store.conflicts(saved.id)
                legacy.setStation(station.id)
                status = "Mapped local ${localStationLabel(localStationId)} ↔ ${saved.remoteStationName} · " +
                    if (saved.state == WavelogBindingState.READ_ONLY) "read-only replica" else "two-way sync"
            }
        }

    fun initialSync() = synchronize("Initial sync") { engine, active, cancelled, progress ->
        engine.initialSync(active, onProgress = progress, isCancelled = cancelled)
    }

    fun quickSync() = synchronize("Quick sync") { engine, active, cancelled, progress ->
        engine.quickSync(active, isCancelled = cancelled, onProgress = progress)
    }

    fun fullReconcile() = synchronize("Full reconciliation") { engine, active, cancelled, progress ->
        engine.fullReconcile(active, onProgress = progress, isCancelled = cancelled)
    }

    fun cancelSync() {
        if (busy) {
            cancelRequested.set(true)
            status = "Cancellation requested · current page will finish safely"
        }
    }

    fun onForeground() {
        val now = System.currentTimeMillis() / 1_000
        val active = binding
        if (busy || active == null || now - lastAutomaticSync < 30 ||
            !legacy.apiKey.startsWith("wl2_") || !active.capabilities.canReadQsos) return
        lastAutomaticSync = now
        synchronize("Automatic foreground quick sync") { engine, selected, cancelled, progress ->
            engine.quickSync(selected, isCancelled = cancelled, onProgress = progress)
        }
    }

    fun onConnectivityAvailable() {
        scope.launch(Dispatchers.Main) { onForeground() }
    }

    fun resolveConflict(conflict: WavelogConflict, resolution: WavelogConflictState,
        mergedFields: Map<String, String>? = null) = runOperation("Resolving conflict…") {
        val active = binding ?: error("Binding is no longer active")
        val merged = mergedFields?.let(::CanonicalQso)
        engine().resolveConflict(active, conflict, resolution, merged)
        withContext(Dispatchers.Main) {
            conflicts = store.conflicts(active.id)
            status = "Conflict resolved · ${resolution.name.lowercase().replace('_', ' ')}"
        }
    }

    private fun synchronize(
        label: String,
        operation: (
            WavelogSyncEngine,
            WavelogBinding,
            () -> Boolean,
            (Int, WavelogSyncSummary) -> Unit,
        ) -> WavelogSyncSummary,
    ) = runOperation("$label started…") {
        val active = binding ?: error("Bind a Wavelog station first")
        val progress: (Int, WavelogSyncSummary) -> Unit = { page, summary ->
            scope.launch(Dispatchers.Main) {
                progressPage = page
                lastSummary = summary
                status = "$label · page $page · ${summary.imported + summary.linked + summary.pulled} processed"
            }
        }
        val summary = operation(engine(), active, cancelRequested::get, progress)
        withContext(Dispatchers.Main) {
            lastSummary = summary
            conflicts = store.conflicts(active.id)
            status = if (summary.cancelled)
                "$label cancelled safely · resume starts at page ${summary.resumedFromPage + summary.pages}"
            else "$label ${if (summary.completed) "complete" else "paused"} · ${summary.imported} imported · " +
                "${summary.pulled} pulled · ${summary.queuedForPush} queued · ${summary.conflicts} conflicts"
        }
    }

    private fun engine() = WavelogSyncEngine(database, store, client(), mutations)
    private fun client() = WavelogApiV2Client(legacy.baseURL, legacy.apiKey)
    fun localStationLabel(id: String) = if (id == DEFAULT_LOCAL_STATION) "default / unassigned" else id

    private fun runOperation(start: String, block: suspend () -> Unit) {
        if (busy) return
        cancelRequested.set(false)
        busy = true
        status = start
        scope.launch {
            try {
                block()
            } catch (error: WavelogApiException) {
                withContext(Dispatchers.Main) { status = safeError(error.errorClass, error.message) }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { status = safeError(WavelogErrorClass.VALIDATION, error.message) }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    private fun safeError(type: WavelogErrorClass, message: String?) =
        "${type.name.lowercase().replace('_', ' ')} · ${message.orEmpty().take(240).ifBlank { "Operation failed" }}"

    fun close() = scope.cancel()
}

package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale

internal data class SatellitePassRow(
    val satellite: SatelliteCatalogueEntry,
    val pass: OrbitalPass,
    val transponder: SatelliteTransponder?,
)

internal data class SatelliteObserverProfile(val id: String, val label: String, val grid: String)

private data class RefreshedSatelliteProviders(
    val elements: SatelliteProviderData<SatelliteCatalogueEntry>,
    val transponders: SatelliteProviderData<SatelliteTransponder>,
    val status: SatelliteProviderData<AmsatStatusSummary>,
    val timers: SatelliteProviderData<SatelliteTimer>,
)

internal class SatelliteOperationsController(
    context: Context,
    private val database: QsoDatabase,
) {
    private val appContext = context.applicationContext
    private val providers = SatelliteProviderRepository(appContext)
    private val prefs = appContext.getSharedPreferences("satellite_operations_v1", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var elements by mutableStateOf(providers.elements()); private set
    var transponders by mutableStateOf(providers.transponders()); private set
    var status by mutableStateOf(providers.amsatStatus()); private set
    var timers by mutableStateOf(providers.timers()); private set
    var passes by mutableStateOf<List<SatellitePassRow>>(emptyList()); private set
    var selectedPass by mutableStateOf<SatellitePassRow?>(null); private set
    var groundTrack by mutableStateOf<List<OrbitalPoint>>(emptyList()); private set
    var skyTrack by mutableStateOf<List<OrbitalPoint>>(emptyList()); private set
    var livePoint by mutableStateOf<OrbitalPoint?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf("Offline prediction uses the last-good validated element cache."); private set

    var observerGrid by mutableStateOf(prefs.getString("observer_grid", "JN88TQ").orEmpty()); private set
    var windowHours by mutableStateOf(prefs.getInt("window_hours", 24).coerceIn(1, 72)); private set
    var minimumElevation by mutableStateOf(prefs.getFloat("minimum_elevation", 10f).toDouble().coerceIn(0.0, 90.0)); private set
    var favouritesOnly by mutableStateOf(prefs.getBoolean("favourites_only", true)); private set
    var utc by mutableStateOf(prefs.getBoolean("utc", true)); private set
    var modeFilter by mutableStateOf(prefs.getString("mode_filter", "").orEmpty()); private set
    var favourites by mutableStateOf(prefs.getStringSet("favourites", emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()); private set

    val nextFavouritePass: SatellitePassRow? get() = passes.firstOrNull { it.satellite.noradId in favourites } ?: passes.firstOrNull()

    init { refresh(false) }

    fun refresh(force: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                val e = providers.refreshElements(force)
                val t = providers.refreshTransponders(force)
                val s = providers.refreshAmsat(force)
                val timers = providers.refreshTimers(force)
                RefreshedSatelliteProviders(e, t, s, timers)
            }
            elements = refreshed.elements
            transponders = refreshed.transponders
            status = refreshed.status
            this@SatelliteOperationsController.timers = refreshed.timers
            busy = false
            message = "Providers refreshed; predictions remain local in the pinned SGP4 engine."
            predict()
        }
    }

    fun predict() {
        val point = maidenheadCenter(observerGrid)
        if (point == null) { message = "Enter a valid Maidenhead observer grid."; passes = emptyList(); return }
        if (busy) return
        busy = true
        scope.launch {
            val now = Instant.now().epochSecond
            val end = now + windowHours * 3600L
            val observer = SatelliteObserver(point.latitude, point.longitude)
            val source = elements.rows.asSequence()
                .filter { !favouritesOnly || it.noradId in favourites }
                .filter { entry -> modeFilter.isBlank() || transponders.rows.rowsFor(entry.noradId).any { it.mode.contains(modeFilter, true) || it.uplinkMode.contains(modeFilter, true) } }
                .take(250).toList()
            val calculated = withContext(Dispatchers.Default) {
                source.flatMap { entry ->
                    when (val result = NativeSatellite.passes(entry.elements, observer, now, end, minimumPeakDeg = minimumElevation, maximumPasses = 12)) {
                        is SatelliteNativeResult.Success -> result.value.map { SatellitePassRow(entry, it, preferredTransponder(entry.noradId)) }
                        is SatelliteNativeResult.Error -> emptyList()
                    }
                }.sortedBy { it.pass.aos }.take(250)
            }
            passes = calculated
            busy = false
            message = when {
                elements.rows.isEmpty() -> "No valid orbital elements. Refresh CelesTrak or add a manual TLE."
                source.isEmpty() && favouritesOnly -> "No favourites selected. Show all satellites or add favourites in Catalogue."
                calculated.isEmpty() -> "No passes meet the selected interval/elevation/filter."
                else -> "${calculated.size} local predictions · no network propagation"
            }
        }
    }

    fun select(row: SatellitePassRow) {
        selectedPass = row
        val point = maidenheadCenter(observerGrid) ?: return
        scope.launch {
            val observer = SatelliteObserver(point.latitude, point.longitude)
            val groundStart = row.pass.aos - 30 * 60
            val groundEnd = row.pass.los + 30 * 60
            val tracks = withContext(Dispatchers.Default) {
                val ground = NativeSatellite.samples(row.satellite.elements, observer, groundStart, groundEnd, 60, 500, false)
                val skyStep = ((row.pass.durationSeconds / 180).coerceIn(2, 30)).toInt()
                val sky = NativeSatellite.samples(row.satellite.elements, observer, row.pass.aos, row.pass.los, skyStep, 300, true)
                (ground as? SatelliteNativeResult.Success)?.value.orEmpty() to (sky as? SatelliteNativeResult.Success)?.value.orEmpty()
            }
            groundTrack = tracks.first; skyTrack = tracks.second
            updateLivePoint()
        }
    }

    fun updateLivePoint(epoch: Long = Instant.now().epochSecond) {
        val selected = selectedPass ?: return
        val point = maidenheadCenter(observerGrid) ?: return
        scope.launch {
            livePoint = withContext(Dispatchers.Default) {
                (NativeSatellite.propagate(selected.satellite.elements, SatelliteObserver(point.latitude, point.longitude), epoch) as? SatelliteNativeResult.Success)?.value
            }
        }
    }

    fun updatePreferences(grid: String = observerGrid, hours: Int = windowHours, elevation: Double = minimumElevation,
        favouritesOnly: Boolean = this.favouritesOnly, utc: Boolean = this.utc, mode: String = modeFilter) {
        observerGrid = grid.trim().uppercase(Locale.US); windowHours = hours.coerceIn(1,72); minimumElevation = elevation.coerceIn(0.0,90.0)
        this.favouritesOnly = favouritesOnly; this.utc = utc; modeFilter = mode.trim().uppercase(Locale.US)
        prefs.edit().putString("observer_grid", observerGrid).putInt("window_hours", windowHours).putFloat("minimum_elevation", minimumElevation.toFloat())
            .putBoolean("favourites_only", favouritesOnly).putBoolean("utc", utc).putString("mode_filter", modeFilter).apply()
    }

    fun toggleFavourite(noradId: Long) {
        favourites = if (noradId in favourites) favourites - noradId else favourites + noradId
        prefs.edit().putStringSet("favourites", favourites.map(Long::toString).toSet()).apply()
    }

    fun observerProfiles(currentGrid: String, activationGrid: String?): List<SatelliteObserverProfile> = buildList {
        currentGrid.takeIf(String::isNotBlank)?.let { add(SatelliteObserverProfile("CURRENT", "Current station · $it", it)) }
        database.satelliteObserverProfiles().forEach { (id, grid) -> add(SatelliteObserverProfile("PROFILE:$id", "Station $id · $grid", grid)) }
        activationGrid?.takeIf(String::isNotBlank)?.let { add(SatelliteObserverProfile("ACTIVATION", "Activation plan · $it", it)) }
    }.distinctBy { it.id + it.grid }

    fun prepareDraft(row: SatellitePassRow): String = satelliteFastEntryDraft(row)

    fun saveManualElements(id: Long, name: String, one: String, two: String): Boolean {
        val candidate = SatelliteElements("TLE", name.trim(), one.trim(), two.trim(), Instant.now().epochSecond, "MANUAL")
        val valid = NativeSatellite.propagate(candidate, SatelliteObserver(0.0, 0.0), Instant.now().epochSecond, Long.MAX_VALUE) is SatelliteNativeResult.Success
        if (!valid) { message = "Manual TLE rejected by the pinned SGP4 parser."; return false }
        return providers.saveManualElements(id,name,one,two).also { elements = providers.elements(); if(it) message="Manual TLE saved and labelled MANUAL" }
    }
    fun removeManualElements(id: Long) { providers.removeManualElements(id); elements=providers.elements(); message="Manual element override removed" }
    fun saveManualTransponder(row: SatelliteTransponder) { providers.saveManualTransponder(row); transponders=providers.transponders(); message="Local transponder override saved" }
    fun removeManualTransponder(id: String) { providers.removeManualTransponder(id); transponders=providers.transponders() }
    fun transpondersFor(noradId: Long) = transponders.rows.rowsFor(noradId)
    private fun preferredTransponder(noradId: Long) = transponders.rows.rowsFor(noradId).firstOrNull { it.downlinkLowHz != null && it.alive && it.providerStatus.equals("active",true) }
        ?: transponders.rows.rowsFor(noradId).firstOrNull { it.downlinkLowHz != null }
    private fun List<SatelliteTransponder>.rowsFor(id: Long) = filter { it.noradId == id }
    fun close() = scope.cancel()
}

internal fun satelliteFastEntryDraft(row: SatellitePassRow): String {
    val transponder = row.transponder
    val downlink = transponder?.downlinkLowHz
    val uplink = transponder?.uplinkLowHz
    val band = downlink?.let(::bandForFrequency).orEmpty()
    val qsoMode = transponder?.mode?.uppercase(Locale.US)?.let { label ->
        when {
            "FM" in label -> "FM"
            "CW" in label -> "CW"
            "SSB" in label || "USB" in label || "LSB" in label -> "SSB"
            else -> null
        }
    }
    return buildString {
        append("<PROP_MODE:SAT>\n<SAT_NAME:${row.satellite.name}>\n")
        transponder?.mode?.takeIf(String::isNotBlank)?.let { append("<SAT_MODE:$it>\n") }
        qsoMode?.let { append("<MODE:$it>\n") }
        band.takeIf(String::isNotBlank)?.let { append("<BAND:$it>\n") }
        downlink?.let { append("<FREQ:${"%.6f".format(Locale.US, it / 1_000_000.0)}>\n") }
        uplink?.let { append("<FREQ_RX:${"%.6f".format(Locale.US, it / 1_000_000.0)}>\n") }
        if (downlink != null && qsoMode != null) append("${"%.6f".format(Locale.US, downlink / 1_000_000.0)} $qsoMode\n")
        else if (band.isNotBlank() && qsoMode != null) append("$band $qsoMode\n")
        append("# Review before save · AOS ${row.pass.aos} · TCA ${row.pass.tca} · LOS ${row.pass.los}\n")
    }
}

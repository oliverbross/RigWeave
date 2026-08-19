package app.rigweave.mobile

/*
THESIS: A native ham-radio operations clock: one glance from station state to workable RF activity.
OWN-WORLD: RigWeave Flightline instrumentation, with OpenHamClock's pinned dashboard density and map-first hierarchy.
STORY: Identify the station and time, read propagation, inspect live paths, then act through the existing DX or Portable workspaces.
FIRST VIEWPORT: UTC/local clocks, CAT truth, solar indices, world activity, live DX, PSK reception, portable activity, and next passes.
FORM: Wide screens use a fixed three-column console; compact screens preserve the same priority in a vertical operating stack.
*/

import android.content.Context
import app.rigweave.mobile.hamclock.HamClockContest
import app.rigweave.mobile.hamclock.HamClockDxpedition
import app.rigweave.mobile.hamclock.HamClockFeed
import app.rigweave.mobile.hamclock.HamClockPanelId
import app.rigweave.mobile.hamclock.HamClockPanelPreference
import app.rigweave.mobile.hamclock.HamClockMapLayerPreference
import app.rigweave.mobile.hamclock.HamClockNamedProfile
import app.rigweave.mobile.hamclock.HamClockPublicProviders
import app.rigweave.mobile.hamclock.HamClockSettingsStore
import app.rigweave.mobile.hamclock.HamClockSolarCelestialSnapshot
import app.rigweave.mobile.hamclock.HamClockUserSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val HcBg = Color(0xFF081015)
private val HcPanel = Color(0xFF111C22)
private val HcRaised = Color(0xFF182831)
private val HcLine = Color(0xFF32434C)
private val HcInk = Color(0xFFEAF0ED)
private val HcMuted = Color(0xFF91A1A9)
private val HcAmber = Color(0xFFF0AD35)
private val HcGreen = Color(0xFF43D17C)
private val HcCyan = Color(0xFF42C7D8)
private val HcRed = Color(0xFFE65B54)
private val HcYellow = Color(0xFFF3D054)
private val HcClock = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val HcDate = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)
private val HcLabelSize = 11.sp
private val HcMetaSize = 11.sp
private val HcRowSize = 12.sp

private data class HamClockDashboardConfig(
    val station: Boolean = true, val weather: Boolean = true, val bandActivity: Boolean = false,
    val signal: Boolean = true, val dxpeditions: Boolean = true, val cluster: Boolean = true,
    val solar: Boolean = true, val target: Boolean = true, val voacap: Boolean = true,
    val portable: Boolean = false, val satellites: Boolean = false, val contests: Boolean = true,
)

private fun HamClockUserSettings.dashboardConfig(): HamClockDashboardConfig {
    fun value(id: String, fallback: Boolean) = panels.firstOrNull { it.id == id }?.visible ?: fallback
    return HamClockDashboardConfig(
        value(HamClockPanelId.STATION, true), value(HamClockPanelId.WEATHER, true),
        value(HamClockPanelId.BAND_ACTIVITY, false), value(HamClockPanelId.PSK_REPORTER, true),
        value(HamClockPanelId.DX_EXPEDITIONS, true), value(HamClockPanelId.DX_CLUSTER, true),
        value(HamClockPanelId.SOLAR, true), value(HamClockPanelId.DX_TARGET, true),
        value(HamClockPanelId.VOACAP, true), value(HamClockPanelId.PORTABLE, false),
        value(HamClockPanelId.SATELLITES, false), value(HamClockPanelId.CONTESTS, true),
    )
}

private fun HamClockUserSettings.withDashboardConfig(value: HamClockDashboardConfig): HamClockUserSettings {
    val visibility = mapOf(
        HamClockPanelId.STATION to value.station, HamClockPanelId.WEATHER to value.weather,
        HamClockPanelId.BAND_ACTIVITY to value.bandActivity, HamClockPanelId.PSK_REPORTER to value.signal,
        HamClockPanelId.DX_EXPEDITIONS to value.dxpeditions, HamClockPanelId.DX_CLUSTER to value.cluster,
        HamClockPanelId.SOLAR to value.solar, HamClockPanelId.DX_TARGET to value.target,
        HamClockPanelId.VOACAP to value.voacap, HamClockPanelId.PORTABLE to value.portable,
        HamClockPanelId.SATELLITES to value.satellites, HamClockPanelId.CONTESTS to value.contests,
    )
    return copy(panels = panels.map { panel -> visibility[panel.id]?.let { panel.copy(visible = it) } ?: panel })
}

@Composable
internal fun HamClockHomeScreen(
    radio: RadioState,
    app: AppController,
    features: FeatureController,
    neuralDx: NeuralDxController,
    portable: PortableController,
    database: QsoDatabase,
    wavelog: WavelogController,
    cty: CtyController,
    publicProviders: HamClockPublicProviders,
    operations: OperationsController,
    send: (String) -> Unit,
    openDx: () -> Unit,
    openPortable: () -> Unit,
    openProgress: () -> Unit,
    openOperations: () -> Unit,
) {
    val context = LocalContext.current
    val stationId = wavelog.stationId.takeIf { wavelog.logMode == LogMode.WAVELOG }
    val stationGrid = wavelog.selectedStation?.grid?.ifBlank { null } ?: app.stationGrid
    val stationCall = wavelog.selectedStation?.callsign?.ifBlank { null }
        ?: app.stationCallsign.ifBlank { features.clusterCallsign }
    val hamClockPrefs = remember(context) { context.getSharedPreferences("rigweave-hamclock-layout", Context.MODE_PRIVATE) }
    val settingsStore = remember(context) { HamClockSettingsStore(context) }
    var settingsDocument by remember { mutableStateOf(settingsStore.snapshot()) }
    var contestFeed by remember { mutableStateOf<HamClockFeed<List<HamClockContest>>?>(null) }
    var dxpeditionFeed by remember { mutableStateOf<HamClockFeed<List<HamClockDxpedition>>?>(null) }
    var celestialFeed by remember { mutableStateOf<HamClockFeed<HamClockSolarCelestialSnapshot>?>(null) }
    fun mapFlag(id: String, fallback: Boolean) = settingsDocument.settings.map.layers.firstOrNull { it.id == id }?.visible ?: fallback
    var now by remember { mutableStateOf(Instant.now()) }
    var showPaths by remember { mutableStateOf(mapFlag("dx_paths", true)) }
    var showGreyline by remember { mutableStateOf(mapFlag("grayline", true)) }
    var showSignals by remember { mutableStateOf(mapFlag("psk_reporter", false)) }
    var showPortable by remember { mutableStateOf(mapFlag("portable", true)) }
    var showSatellites by remember { mutableStateOf(mapFlag("satellites", false)) }
    var showLoggedQsos by remember { mutableStateOf(mapFlag("logged_qsos", hamClockPrefs.getBoolean("map_logged_qsos", false))) }
    var showLightning by remember { mutableStateOf(mapFlag("lightning", hamClockPrefs.getBoolean("map_lightning", false))) }
    var dashboardConfig by remember { mutableStateOf(settingsDocument.settings.dashboardConfig()) }
    var configureDashboard by rememberSaveable { mutableStateOf(false) }
    fun decodeFilter(key: String) = hamClockPrefs.getString(key, "").orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
    var spotFilters by remember { mutableStateOf(SpotFilters(decodeFilter("cluster_bands"), decodeFilter("cluster_modes"),
        decodeFilter("cluster_cs"), decodeFilter("cluster_ds"))) }
    var activeSpotFilter by remember { mutableStateOf<SpotFilterDimension?>(null) }
    var spotStatuses by remember { mutableStateOf<Map<String, SpotLogStatus>>(emptyMap()) }
    var recentQsos by remember { mutableStateOf<List<Qso>>(emptyList()) }
    fun updateDashboard(value: HamClockDashboardConfig) {
        dashboardConfig = value
        settingsDocument = settingsStore.updateSettings { it.withDashboardConfig(value) }
    }
    fun repositionPanel(id: String, column: Int?, orderDelta: Int) {
        val current = settingsDocument.settings.panels.firstOrNull { it.id == id } ?: return
        settingsDocument = settingsStore.setPanel(current.copy(
            column = column ?: current.column,
            order = (current.order + orderDelta).coerceIn(0, 999),
        ))
    }
    fun saveDashboardProfile() {
        settingsStore.saveProfile("Layout ${settingsStore.profiles().size + 1}")
        settingsDocument = settingsStore.snapshot()
    }
    fun applyDashboardProfile(id: String) {
        settingsDocument = settingsStore.applyProfile(id)
        dashboardConfig = settingsDocument.settings.dashboardConfig()
        fun visible(layer: String, fallback: Boolean) = settingsDocument.settings.map.layers
            .firstOrNull { it.id == layer }?.visible ?: fallback
        showPaths = visible("dx_paths", showPaths); showGreyline = visible("grayline", showGreyline)
        showSignals = visible("psk_reporter", showSignals); showPortable = visible("portable", showPortable)
        showSatellites = visible("satellites", showSatellites); showLoggedQsos = visible("logged_qsos", showLoggedQsos)
        showLightning = visible("lightning", showLightning)
    }
    fun updateMapFlag(key: String, layerId: String, value: Boolean, apply: (Boolean) -> Unit) {
        apply(value)
        hamClockPrefs.edit().putBoolean(key, value).apply()
        val old = settingsDocument.settings.map.layers.firstOrNull { it.id == layerId }
            ?: HamClockMapLayerPreference(layerId, visible = value)
        settingsDocument = settingsStore.setMapLayer(old.copy(visible = value))
    }
    fun updateSpotFilters(value: SpotFilters) {
        spotFilters = value
        hamClockPrefs.edit().putString("cluster_bands", value.bands.joinToString(","))
            .putString("cluster_modes", value.modes.joinToString(","))
            .putString("cluster_cs", value.callStatuses.joinToString(","))
            .putString("cluster_ds", value.dxccStatuses.joinToString(",")).apply()
    }

    LaunchedEffect(Unit) {
        while (true) { now = Instant.now(); delay(1_000) }
    }
    LaunchedEffect(features.liveSpots, stationId, cty.dataRevision) {
        neuralDx.ingest(features.liveSpots, stationId, cty, stationCall)
    }
    LaunchedEffect(stationGrid, stationCall, stationId) {
        while (true) {
            val epoch = Instant.now().epochSecond
            portable.markForegroundAge(epoch)
            if (neuralDx.lastRefreshEpoch == 0L || epoch - neuralDx.lastRefreshEpoch > 15 * 60) {
                neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots)
            }
            if (!features.solar.valid || epoch - features.solar.observedEpoch > 60 * 60) features.refreshSolar()
            val potaAge = portable.providerStatus(PortableProgram.POTA).fetchedAt
            val wwffAge = portable.providerStatus(PortableProgram.WWFF).fetchedAt
            if (potaAge == 0L || wwffAge == 0L || epoch - minOf(potaAge, wwffAge) > 5 * 60) portable.refreshAll()
            delay(60_000)
        }
    }
    LaunchedEffect(portable.pota.spots.size, portable.wwffSpots.size, portable.sotaSpots.size,
        radio.frequencyHz, stationGrid, portable.lastQsoRevision) {
        portable.refreshOpportunities(now.epochSecond, radio.frequencyHz, stationGrid)
    }

    val mapSpots = remember(features.liveSpots, neuralDx.enrichedSpots) {
        mergeEnrichedSpots(features.liveSpots, neuralDx.enrichedSpots)
    }
    LaunchedEffect(mapSpots, stationId, cty.dataRevision) {
        val identities = mapSpots.map { spot -> val entity = cty.lookup(spot.callsign); SpotLogIdentity(
            spot.id, spot.callsign, entity?.dxcc.orEmpty(), entity?.country.orEmpty().ifBlank { spot.country }, spot.band, spot.mode) }
        var observedRevision = Long.MIN_VALUE
        while (true) {
            val revision = database.changeToken()
            if (revision != observedRevision) {
                spotStatuses = withContext(Dispatchers.IO) { database.spotStatuses(identities, stationId) }
                observedRevision = revision
            }
            delay(2_000)
        }
    }
    val visibleMapSpots = remember(mapSpots, spotStatuses, spotFilters) {
        mapSpots.filter { spotMatchesFilters(it, spotStatuses[it.id], spotFilters) }
    }
    val propagationRepository = remember(context) { HamClockPropagationRepository(context) }
    var pathPrediction by remember { mutableStateOf(HamClockPropagationSnapshot()) }
    val selectedDxTarget = preferredDxTarget(visibleMapSpots)
    val stationPoint = maidenheadCenter(stationGrid)
    val targetPoint = selectedDxTarget?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { GeoPoint(it.latitude, it.longitude) }
    LaunchedEffect(stationPoint) {
        while (true) {
            val latitude = stationPoint?.latitude ?: 0.0
            val longitude = stationPoint?.longitude ?: 0.0
            val refreshed = withContext(Dispatchers.IO) {
                Triple(publicProviders.contests.refresh(), publicProviders.dxpeditions.refresh(),
                    publicProviders.solarCelestial.refresh(latitude, longitude))
            }
            contestFeed = refreshed.first
            dxpeditionFeed = refreshed.second
            celestialFeed = refreshed.third
            delay(5 * 60_000L)
        }
    }
    LaunchedEffect(stationPoint, targetPoint, radio.mode, radio.powerW) {
        if (stationPoint == null || targetPoint == null) {
            pathPrediction = HamClockPropagationSnapshot(error = "Station or target geometry unavailable")
        } else while (true) {
            pathPrediction = propagationRepository.prediction(stationPoint, targetPoint, radio.mode.ifBlank { "SSB" },
                radio.powerW.takeIf { it > 0 } ?: 100)
            delay(10 * 60_000L)
        }
    }
    LaunchedEffect(database) {
        var observedRevision = Long.MIN_VALUE
        while (true) {
            val revision = database.changeToken()
            if (revision != observedRevision) {
                recentQsos = withContext(Dispatchers.IO) { database.recent(120) }
                observedRevision = revision
            }
            delay(5_000)
        }
    }
    val portableMinute = now.epochSecond / 60
    val portableMapSpots = remember(portable.rankedOpportunities, portableMinute) {
        portable.rankedOpportunities.asSequence().map { it.spot }
            .filter { it.activeAt(portableMinute * 60) && it.latitude != null && it.longitude != null }
            .take(160).toList()
    }
    val sidePanels = settingsDocument.settings.panels.filter { it.visible && it.id != HamClockPanelId.MAP }
    val leftPanels = sidePanels.filter { it.column <= 0 }.sortedBy(HamClockPanelPreference::order)
    val rightPanels = sidePanels.filter { it.column > 0 }.sortedBy(HamClockPanelPreference::order)

    @Composable fun SidePanel(panel: HamClockPanelPreference, modifier: Modifier) {
        when (panel.id) {
            HamClockPanelId.STATION -> StationPanel(stationCall, stationGrid, radio, wavelog, app, send, modifier)
            HamClockPanelId.WEATHER -> WeatherPanel(neuralDx.weather, modifier)
            HamClockPanelId.PSK_REPORTER -> SignalPanel(neuralDx.mySignal, openDx, modifier)
            HamClockPanelId.DX_EXPEDITIONS -> DxpeditionPanel(dxpeditionFeed, openDx, modifier)
            HamClockPanelId.BAND_ACTIVITY -> BandConditionsPanel(neuralDx.bandActivity, openProgress, modifier)
            HamClockPanelId.DX_CLUSTER -> DxPanel(visibleMapSpots, mapSpots.size, features.clusterStatus,
                spotFilters, { activeSpotFilter = it }, openDx, modifier)
            HamClockPanelId.SOLAR -> SolarPanel(features, celestialFeed, modifier)
            HamClockPanelId.DX_TARGET -> DxTargetPanel(visibleMapSpots, stationGrid, modifier)
            HamClockPanelId.VOACAP -> VoacapPanel(visibleMapSpots, features, pathPrediction, modifier)
            HamClockPanelId.PORTABLE -> PortablePanel(portable, openPortable, modifier)
            HamClockPanelId.SATELLITES -> SatellitePanel(operations.satellites, {
                operations.openSection("SATELLITES"); openOperations()
            }, modifier)
            HamClockPanelId.CONTESTS -> ContestsPanel(contestFeed, modifier)
        }
    }
    fun sidePanelHeight(id: String) = when (id) {
        HamClockPanelId.STATION -> 260.dp; HamClockPanelId.WEATHER -> 220.dp
        HamClockPanelId.PSK_REPORTER -> 190.dp; HamClockPanelId.DX_EXPEDITIONS -> 210.dp
        HamClockPanelId.BAND_ACTIVITY -> 260.dp; HamClockPanelId.DX_CLUSTER -> 350.dp
        HamClockPanelId.SOLAR -> 190.dp; HamClockPanelId.DX_TARGET -> 190.dp
        HamClockPanelId.VOACAP -> 240.dp; HamClockPanelId.PORTABLE -> 240.dp
        HamClockPanelId.SATELLITES, HamClockPanelId.CONTESTS -> 220.dp
        else -> 200.dp
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HcBg).windowInsetsPadding(WindowInsets.safeDrawing).testTag("openhamclock-home")) {
        val wide = maxWidth >= 960.dp && maxHeight >= 700.dp
        if (wide) {
            Column(Modifier.fillMaxSize().padding(10.dp).testTag("openhamclock-safe-content"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HamClockHeader(now, stationCall, stationGrid, radio, features, neuralDx, {
                    neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots, true)
                    features.refreshSolar(); portable.refreshAll()
                }) { configureDashboard = true }
                OperationsHomeSummary(operations, openOperations)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(Modifier.widthIn(min = 208.dp, max = 250.dp).weight(.22f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(leftPanels, key = HamClockPanelPreference::id) { panel ->
                            SidePanel(panel, Modifier.fillMaxWidth().height(sidePanelHeight(panel.id) * panel.rowSpan))
                        }
                    }
                    Column(Modifier.weight(.56f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapPanel(visibleMapSpots, stationGrid, cty, neuralDx.mySignal.reports, portableMapSpots,
                            recentQsos, neuralDx.lightning.strikes,
                            neuralDx.satellites, showPaths, { updateMapFlag("map_paths", "dx_paths", it) { showPaths = it } },
                            showGreyline, { updateMapFlag("map_greyline", "grayline", it) { showGreyline = it } },
                            showSignals, { updateMapFlag("map_psk", "psk_reporter", it) { showSignals = it } },
                            showPortable, { updateMapFlag("map_portable", "portable", it) { showPortable = it } },
                            showSatellites, { updateMapFlag("map_satellites", "satellites", it) { showSatellites = it } },
                            showLoggedQsos, { updateMapFlag("map_logged_qsos", "logged_qsos", it) { showLoggedQsos = it } },
                            showLightning, { updateMapFlag("map_lightning", "lightning", it) { showLightning = it } }, openDx, Modifier.weight(1f))
                        PropagationStrip(neuralDx, Modifier.heightIn(min = 98.dp, max = 118.dp))
                    }
                    LazyColumn(Modifier.widthIn(min = 250.dp, max = 310.dp).weight(.25f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rightPanels, key = HamClockPanelPreference::id) { panel ->
                            SidePanel(panel, Modifier.fillMaxWidth().height(sidePanelHeight(panel.id) * panel.rowSpan))
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(8.dp).testTag("openhamclock-safe-content"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { HamClockHeader(now, stationCall, stationGrid, radio, features, neuralDx, {
                    neuralDx.refresh(stationCall, stationGrid, stationId, features.liveSpots, true)
                    features.refreshSolar(); portable.refreshAll()
                }) { configureDashboard = true } }
                item { OperationsHomeSummary(operations, openOperations) }
                item { MapPanel(visibleMapSpots, stationGrid, cty, neuralDx.mySignal.reports, portableMapSpots,
                    recentQsos, neuralDx.lightning.strikes,
                    neuralDx.satellites, showPaths, { updateMapFlag("map_paths", "dx_paths", it) { showPaths = it } },
                    showGreyline, { updateMapFlag("map_greyline", "grayline", it) { showGreyline = it } },
                    showSignals, { updateMapFlag("map_psk", "psk_reporter", it) { showSignals = it } },
                    showPortable, { updateMapFlag("map_portable", "portable", it) { showPortable = it } },
                    showSatellites, { updateMapFlag("map_satellites", "satellites", it) { showSatellites = it } },
                    showLoggedQsos, { updateMapFlag("map_logged_qsos", "logged_qsos", it) { showLoggedQsos = it } },
                    showLightning, { updateMapFlag("map_lightning", "lightning", it) { showLightning = it } }, openDx,
                    Modifier.height(if (maxWidth < 500.dp) 290.dp else 390.dp)) }
                if (maxWidth < 600.dp) {
                    if (dashboardConfig.station) item { StationPanel(stationCall, stationGrid, radio, wavelog, app, send, Modifier.fillMaxWidth()) }
                    if (dashboardConfig.weather) item { WeatherPanel(neuralDx.weather, Modifier.fillMaxWidth()) }
                } else if (dashboardConfig.station || dashboardConfig.weather) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dashboardConfig.station) StationPanel(stationCall, stationGrid, radio, wavelog, app, send, Modifier.weight(1f))
                        if (dashboardConfig.weather) WeatherPanel(neuralDx.weather, Modifier.weight(1f))
                    }
                }
                if (dashboardConfig.cluster) item { DxPanel(visibleMapSpots, mapSpots.size, features.clusterStatus,
                    spotFilters, { activeSpotFilter = it }, openDx, Modifier.fillMaxWidth()) }
                if (dashboardConfig.signal) item { SignalPanel(neuralDx.mySignal, openDx, Modifier.fillMaxWidth()) }
                if (dashboardConfig.dxpeditions) item { DxpeditionPanel(dxpeditionFeed, openDx, Modifier.fillMaxWidth()) }
                if (dashboardConfig.solar) item { SolarPanel(features, celestialFeed, Modifier.fillMaxWidth()) }
                if (dashboardConfig.target) item { DxTargetPanel(visibleMapSpots, stationGrid, Modifier.fillMaxWidth()) }
                if (dashboardConfig.voacap) item { VoacapPanel(visibleMapSpots, features, pathPrediction, Modifier.fillMaxWidth()) }
                if (dashboardConfig.portable) item { PortablePanel(portable, openPortable, Modifier.fillMaxWidth()) }
                if (dashboardConfig.bandActivity) item { BandConditionsPanel(neuralDx.bandActivity, openProgress, Modifier.fillMaxWidth()) }
                if (dashboardConfig.satellites) item { SatellitePanel(operations.satellites, {
                    operations.openSection("SATELLITES"); openOperations()
                }, Modifier.fillMaxWidth()) }
                if (dashboardConfig.contests) item { ContestsPanel(contestFeed, Modifier.fillMaxWidth()) }
                item { PropagationStrip(neuralDx, Modifier.fillMaxWidth()) }
            }
        }
        if (configureDashboard) HamClockConfigDialog(dashboardConfig, settingsDocument.settings,
            settingsDocument.profiles, settingsDocument.activeProfileId, ::updateDashboard, ::repositionPanel,
            ::saveDashboardProfile, ::applyDashboardProfile) { configureDashboard = false }
        activeSpotFilter?.let { dimension ->
            SpotFilterOverlay(dimension, spotFilters,
                (spotModeOptions + mapSpots.map { canonicalSpotMode(it.mode) }).distinct().sorted(),
                { activeSpotFilter = null }, { updateSpotFilters(it); activeSpotFilter = null }, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun OperationsHomeSummary(operations: OperationsController, open: () -> Unit) {
    val now = Instant.now().epochSecond
    val activeDx = operations.dxItems.count { it.startEpoch != null && it.endEpoch != null && now in it.startEpoch..it.endEpoch }
    val activeContests = operations.contestItems.count { now in it.startEpoch..it.endEpoch }
    Surface(color = HcPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.EventNote, null, tint = HcAmber)
            Column(Modifier.weight(1f)) {
                Text("OPERATIONS", color = HcAmber, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("$activeDx active DX · $activeContests active contests · ${operations.nextPlan?.title ?: "no upcoming plan"}", color = HcInk)
            }
            Text("OPEN", color = HcAmber, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HamClockHeader(now: Instant, call: String, grid: String, radio: RadioState,
    features: FeatureController, neuralDx: NeuralDxController, refresh: () -> Unit, configure: () -> Unit) {
    val utc = now.atZone(ZoneOffset.UTC)
    val local = now.atZone(ZoneId.systemDefault())
    Surface(color = HcPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, HcLine, RoundedCornerShape(8.dp))) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (maxWidth < 760.dp) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderIdentity(call, grid, Modifier.weight(1f))
                    StatusPill(if (radio.connected) "CAT LIVE" else "CAT OFFLINE", radio.connected)
                    ConfigButton(configure); SyncButton(neuralDx.refreshing, refresh)
                }
                HeaderReadouts(utc.format(HcClock), utc.format(HcDate), local.format(HcClock), local.format(HcDate), features)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderIdentity(call, grid, Modifier.weight(1f))
                ClockReadout("UTC", utc.format(HcClock), utc.format(HcDate))
                ClockReadout("LOCAL", local.format(HcClock), local.format(HcDate))
                SolarMetrics(features)
                StatusPill(if (radio.connected) "CAT LIVE" else "CAT OFFLINE", radio.connected)
                ConfigButton(configure); SyncButton(neuralDx.refreshing, refresh)
            }
        }
    }
}

@Composable private fun ConfigButton(configure: () -> Unit) {
    OutlinedButton(configure, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Tune, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("LAYOUT")
    }
}

@Composable private fun HamClockConfigDialog(config: HamClockDashboardConfig, settings: HamClockUserSettings,
    profiles: List<HamClockNamedProfile>, activeProfileId: String?, update: (HamClockDashboardConfig) -> Unit,
    reposition: (String, Int?, Int) -> Unit, saveProfile: () -> Unit, applyProfile: (String) -> Unit,
    dismiss: () -> Unit) {
    val moduleIds = listOf(HamClockPanelId.STATION, HamClockPanelId.WEATHER, HamClockPanelId.BAND_ACTIVITY,
        HamClockPanelId.PSK_REPORTER, HamClockPanelId.DX_EXPEDITIONS, HamClockPanelId.DX_CLUSTER,
        HamClockPanelId.SOLAR, HamClockPanelId.DX_TARGET, HamClockPanelId.VOACAP, HamClockPanelId.PORTABLE,
        HamClockPanelId.SATELLITES, HamClockPanelId.CONTESTS)
    val modules = listOf(
        Triple("DE station", config.station) { value: Boolean -> config.copy(station = value) },
        Triple("Local weather", config.weather) { value: Boolean -> config.copy(weather = value) },
        Triple("Band activity", config.bandActivity) { value: Boolean -> config.copy(bandActivity = value) },
        Triple("PSK Reporter", config.signal) { value: Boolean -> config.copy(signal = value) },
        Triple("DXpeditions", config.dxpeditions) { value: Boolean -> config.copy(dxpeditions = value) },
        Triple("DX cluster", config.cluster) { value: Boolean -> config.copy(cluster = value) },
        Triple("Solar conditions", config.solar) { value: Boolean -> config.copy(solar = value) },
        Triple("DX target", config.target) { value: Boolean -> config.copy(target = value) },
        Triple("VOACAP estimate", config.voacap) { value: Boolean -> config.copy(voacap = value) },
        Triple("Portable activators", config.portable) { value: Boolean -> config.copy(portable = value) },
        Triple("Satellite passes", config.satellites) { value: Boolean -> config.copy(satellites = value) },
        Triple("Contest calendar", config.contests) { value: Boolean -> config.copy(contests = value) },
    )
    AlertDialog(onDismissRequest = dismiss, title = { Text("Configure Open Ham Clock") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item { Text("Choose modules, place them left or right, and change their order. Layout profiles and all view state are saved locally and survive app upgrades.", color = HcMuted) }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("LAYOUT PROFILES", color = HcAmber, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f))
                        OutlinedButton(saveProfile, modifier = Modifier.heightIn(min = 48.dp)) { Text("SAVE CURRENT") }
                    }
                    if (profiles.isEmpty()) Text("No saved profile yet", color = HcMuted, fontSize = 10.sp)
                    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.sortedBy(HamClockNamedProfile::name).forEach { profile ->
                            FilterChip(profile.id == activeProfileId, { applyProfile(profile.id) }, { Text(profile.name) })
                        }
                    }
                    HorizontalDivider(color = HcLine)
                }
            }
            items(modules.size) { index ->
                val (label, checked, change) = modules[index]
                val id = moduleIds[index]
                val preference = settings.panels.firstOrNull { it.id == id }
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { update(change(!checked)) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = HcInk, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked, { update(change(it)) })
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("POSITION", color = HcMuted, fontSize = 9.sp, modifier = Modifier.width(56.dp))
                        FilterChip(preference?.column == 0, { reposition(id, 0, 0) }, { Text("LEFT") })
                        FilterChip(preference?.column != 0, { reposition(id, 2, 0) }, { Text("RIGHT") })
                        OutlinedButton({ reposition(id, null, -10) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("↑ UP") }
                        OutlinedButton({ reposition(id, null, 10) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("↓ DOWN") }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text("Done") } })
}

@Composable private fun HeaderIdentity(call: String, grid: String, modifier: Modifier) = Column(modifier) {
    Text("RIGWEAVE · OPEN HAM CLOCK", color = HcAmber, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, fontSize = 15.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("${call.ifBlank { "CALL NOT SET" }} · ${grid.ifBlank { "GRID NOT SET" }}", color = HcInk,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun HeaderReadouts(utc: String, utcDate: String, local: String, localDate: String, features: FeatureController) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ClockReadout("UTC", utc, utcDate); ClockReadout("LOCAL", local, localDate); SolarMetrics(features)
    }
}

@Composable private fun SolarMetrics(features: FeatureController) {
    Metric("SFI", features.solar.flux.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—", HcAmber)
    Metric("A", features.solar.aIndex.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—",
        if (features.solar.aIndex >= 30) HcRed else HcYellow)
    Metric("KP", features.solar.kpIndex.takeIf { features.solar.valid }?.let { "%.1f".format(Locale.US, it) } ?: "—",
        if (features.solar.kpIndex >= 5) HcRed else HcGreen)
}

@Composable private fun SyncButton(refreshing: Boolean, refresh: () -> Unit) {
    Button(refresh, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(if (refreshing) "…" else "SYNC")
    }
}

@Composable private fun ClockReadout(label: String, time: String, date: String) = Column(horizontalAlignment = Alignment.End) {
    Text("$label  $time", color = HcInk, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Black)
    Text(date.uppercase(), color = HcMuted, fontSize = 10.sp)
}

@Composable private fun Metric(label: String, value: String, color: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = HcMuted, fontSize = HcLabelSize, fontWeight = FontWeight.Bold, letterSpacing = .15.sp)
    Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Black)
}

@Composable private fun StatusPill(text: String, good: Boolean) {
    val color = if (good) HcGreen else HcRed
    Surface(color = color.copy(alpha = .14f), shape = RoundedCornerShape(4.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable private fun Module(title: String, subtitle: String = "", onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = HcPanel, shape = RoundedCornerShape(7.dp), modifier = modifier.border(1.dp, HcLine, RoundedCornerShape(7.dp))) {
        Column(Modifier.fillMaxSize().padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = HcAmber, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (subtitle.isNotBlank()) Text(subtitle, color = HcMuted, fontFamily = FontFamily.Monospace, fontSize = HcMetaSize,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = HcLine)
            content()
        }
    }
}

@Composable private fun MapPanel(rows: List<AndroidDXSpot>, stationGrid: String, cty: CtyController,
    signalReports: List<SignalReport>, portableSpots: List<PortableSpot>, loggedQsos: List<Qso>, lightning: List<LightningStrike>,
    satellites: List<SatellitePosition>,
    showPaths: Boolean, setPaths: (Boolean) -> Unit, showGreyline: Boolean, setGreyline: (Boolean) -> Unit,
    showSignals: Boolean, setSignals: (Boolean) -> Unit, showPortable: Boolean, setPortable: (Boolean) -> Unit,
    showSatellites: Boolean, setSatellites: (Boolean) -> Unit, showLoggedQsos: Boolean, setLoggedQsos: (Boolean) -> Unit,
    showLightning: Boolean, setLightning: (Boolean) -> Unit, openDx: () -> Unit, modifier: Modifier) {
    Module("World activity", "${rows.size} SPOTS", openDx, modifier) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(showPaths, { setPaths(!showPaths) }, { Text("REPORTING PATHS") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showGreyline, { setGreyline(!showGreyline) }, { Text("SUN / GRAYLINE") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showSignals, { setSignals(!showSignals) }, { Text("PSK REPORTER") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showPortable, { setPortable(!showPortable) }, { Text("PORTABLE") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showSatellites, { setSatellites(!showSatellites) }, { Text("SATELLITES") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showLoggedQsos, { setLoggedQsos(!showLoggedQsos) }, { Text("LOGGED QSOS") }, Modifier.heightIn(min = 48.dp))
            FilterChip(showLightning, { setLightning(!showLightning) }, { Text("LIGHTNING") }, Modifier.heightIn(min = 48.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(HcRaised)) {
            DxWorldCanvas(rows, stationGrid, false, cty, showPaths, Modifier.fillMaxSize(),
                showGreyline, if (showSignals) signalReports else emptyList(),
                if (showPortable) portableSpots else emptyList(), if (showSatellites) satellites else emptyList(),
                if (showLoggedQsos) loggedQsos else emptyList(), if (showLightning) lightning else emptyList())
            if (rows.isEmpty() && (!showSignals || signalReports.isEmpty()) &&
                (!showPortable || portableSpots.isEmpty()) && (!showSatellites || satellites.isEmpty()) &&
                (!showLoggedQsos || loggedQsos.isEmpty()) && (!showLightning || lightning.isEmpty())) {
                Text("Waiting for live map activity", color = HcMuted, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

internal fun mergeEnrichedSpots(live: List<AndroidDXSpot>, enriched: List<AndroidDXSpot>): List<AndroidDXSpot> {
    if (live.isEmpty() || enriched.isEmpty()) return live
    val enrichedById = enriched.associateBy(AndroidDXSpot::id)
    return live.map { current -> enrichedById[current.id]?.let { resolved -> current.copy(
        country = resolved.country.ifBlank { current.country },
        continent = resolved.continent.ifBlank { current.continent },
        cqZone = resolved.cqZone.takeUnless { it == 0 } ?: current.cqZone,
        ituZone = resolved.ituZone.takeUnless { it == 0 } ?: current.ituZone,
        latitude = resolved.latitude.takeUnless { it == 0.0 } ?: current.latitude,
        longitude = resolved.longitude.takeUnless { it == 0.0 } ?: current.longitude,
    ) } ?: current }
}

@Composable private fun StationPanel(call: String, grid: String, radio: RadioState, wavelog: WavelogController,
    app: AppController, send: (String) -> Unit, modifier: Modifier) {
    Module("DE station", if (wavelog.logMode == LogMode.WAVELOG) "WAVELOG" else "LOCAL", modifier = modifier) {
        KeyValue("CALL", call.ifBlank { "NOT SET" }, HcCyan)
        KeyValue("GRID", grid.ifBlank { "NOT SET" }, HcInk)
        KeyValue("RADIO", radio.model, if (radio.connected) HcGreen else HcMuted)
        KeyValue("VFO A", if (radio.connected && radio.frequencyHz > 0) "%.3f MHz".format(Locale.US, radio.frequencyHz / 1_000_000.0) else "NO LIVE STATE", if (radio.connected) HcAmber else HcMuted)
        KeyValue("MODE", if (radio.connected) radio.mode else "—", HcInk)
        KeyValue("POWER", if (radio.connected && radio.powerW > 0) "${radio.powerW} W" else "—", HcInk)
        KeyValue("TX SAFETY", if (app.transmitArmed) "ARMED" else "SAFE / RX", if (app.transmitArmed) HcRed else HcGreen)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FieldProfile.entries.forEach { profile ->
                FilterChip(app.fieldProfile == profile, { app.setProfile(profile) }, { Text(profile.name, fontSize = HcMetaSize) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            app.favoriteBands.forEach { value -> OutlinedButton({
                value.toDoubleOrNull()?.let { send("FA%011d;".format((it * 1_000_000).toLong())) }
            }, enabled = radio.connected, modifier = Modifier.heightIn(min = 48.dp)) { Text(value, fontFamily = FontFamily.Monospace, fontSize = HcMetaSize) } }
        }
    }
}

@Composable private fun WeatherPanel(weather: NeuralWeather, modifier: Modifier) {
    Module("Local weather", weather.source, modifier = modifier) {
        if (!weather.available) EmptyLine(weather.error.ifBlank { "Weather awaiting station grid and internet" }) else {
            KeyValue("TEMP", weather.temperatureC?.let { "%.1f °C".format(Locale.US, it) } ?: "—", HcCyan)
            KeyValue("HUMIDITY", weather.humidityPercent?.let { "$it%" } ?: "—", HcInk)
            KeyValue("PRESSURE", weather.pressureHpa?.let { "%.0f hPa".format(Locale.US, it) } ?: "—", HcInk)
            KeyValue("WIND", weather.windKmh?.let { "%.0f km/h".format(Locale.US, it) } ?: "—", HcInk)
            KeyValue("TROPO", weather.tropoIndex?.toString() ?: "—", when ((weather.tropoIndex ?: 0)) { in 6..Int.MAX_VALUE -> HcGreen; else -> HcMuted })
            KeyValue("DUCTING", weather.ductingRisk ?: "—", HcYellow)
        }
    }
}

@Composable private fun DxPanel(spots: List<AndroidDXSpot>, total: Int, status: String, filters: SpotFilters,
    openFilter: (SpotFilterDimension) -> Unit, openDx: () -> Unit, modifier: Modifier) {
    Module("DX cluster", "${spots.size}/$total · ${status.substringAfter("DX cluster ").uppercase()}", openDx, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpotFilterDimension.entries.forEach { dimension ->
                val count = filters.count(dimension)
                OutlinedButton({ openFilter(dimension) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(if (count == 0) dimension.title else "${dimension.title} $count", maxLines = 1, fontSize = HcLabelSize)
                }
            }
        }
        if (total == 0) EmptyLine(status) else if (spots.isEmpty()) EmptyLine("No spots match the selected filters") else spots.take(7).forEach { spot ->
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(spot.callsign, color = if (spot.watchlisted) HcYellow else HcCyan, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(72.dp))
                Text("${spot.country.ifBlank { "Unknown" }} · de ${spot.spotter}", color = HcMuted, fontSize = HcMetaSize,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("%.3f".format(Locale.US, spot.frequencyHz / 1_000_000.0), color = HcInk,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = HcRowSize,
                    textAlign = TextAlign.End, modifier = Modifier.width(62.dp))
                Text("${spot.band} ${spot.mode}", color = HcMuted, fontSize = HcMetaSize, maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            }
        }
    }
}

@Composable private fun SolarPanel(features: FeatureController,
    celestial: HamClockFeed<HamClockSolarCelestialSnapshot>?, modifier: Modifier) {
    val snapshot = celestial?.value
    Module("Solar conditions", celestial?.let { "${it.state.name} · ${it.source.substringBefore(" · ")}" }
        ?: if (features.solar.valid) ageLabel(features.solar.observedEpoch) else "AWAITING NOAA", modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric("SFI", features.solar.flux.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—", HcAmber)
            Metric("A", features.solar.aIndex.takeIf { features.solar.valid }?.roundToInt()?.toString() ?: "—",
                if (features.solar.aIndex >= 30) HcRed else HcYellow)
            Metric("KP", features.solar.kpIndex.takeIf { features.solar.valid }?.let { "%.1f".format(Locale.US, it) } ?: "—",
                if (features.solar.kpIndex >= 5) HcRed else HcGreen)
        }
        if (snapshot != null) {
            val moon = snapshot.moon
            val sun = snapshot.sunTimes
            val time = { epoch: Long? -> epoch?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "—" }
            KeyValue("X-RAY", "${snapshot.xray.currentClass} now · ${snapshot.xray.peakClass} peak", HcCyan)
            KeyValue("MOON", "${moon.name.name.replace('_', ' ')} · ${(moon.illumination * 100).roundToInt()}%", HcInk)
            KeyValue("DAYLIGHT", "↑ ${time(sun.sunriseEpoch)} · ↓ ${time(sun.sunsetEpoch)}", HcAmber)
        }
        Text(celestial?.error?.takeIf(String::isNotBlank)
            ?: "NOAA GOES · NASA SDO · local astronomy · grayline live on map", color = HcMuted, fontSize = HcMetaSize,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun preferredDxTarget(spots: List<AndroidDXSpot>): AndroidDXSpot? = spots.maxWithOrNull(
    compareBy<AndroidDXSpot> { it.watchlisted }.thenBy { it.score }.thenBy { it.receivedEpoch }
)

@Composable private fun DxTargetPanel(spots: List<AndroidDXSpot>, stationGrid: String, modifier: Modifier) {
    val target = preferredDxTarget(spots)
    val station = maidenheadCenter(stationGrid)
    val remote = target?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let { GeoPoint(it.latitude, it.longitude) }
    val computedDistance = if (station != null && remote != null) distanceKm(station, remote).roundToInt() else null
    val computedBearing = if (station != null && remote != null) initialBearingDegrees(station, remote) else null
    Module("DX target", target?.callsign ?: "NO LIVE TARGET", modifier = modifier) {
        if (target == null) EmptyLine("A live cluster spot will become the target") else {
            KeyValue("ENTITY", target.country.ifBlank { "Unknown" }, HcCyan)
            KeyValue("RF", "%.3f MHz · ${target.band} ${target.mode}".format(Locale.US, target.frequencyHz / 1_000_000.0), HcInk)
            KeyValue("PATH", listOfNotNull((target.distanceKm.takeIf { it > 0 } ?: computedDistance)?.let { "$it km" },
                (target.bearingDegrees.takeIf { it > 0 } ?: computedBearing)?.let { "%03d°".format(it) }).joinToString(" · ").ifBlank { "Awaiting station geometry" }, HcAmber)
            Text(target.reason.ifBlank { target.comment }.ifBlank { "Highest-ranked live RigWeave observation" },
                color = HcMuted, fontSize = HcMetaSize, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun VoacapPanel(spots: List<AndroidDXSpot>, features: FeatureController,
    prediction: HamClockPropagationSnapshot, modifier: Modifier) {
    val target = preferredDxTarget(spots)
    val zone = when (target?.continent?.uppercase()) { "EU" -> "EUROPE"; "AF" -> "AFRICA"; "AS" -> "ASIA"; "NA", "SA" -> "AMERICAS"; else -> "OCEANIA" }
    val rows = prediction.bands.takeIf { it.isNotEmpty() }?.take(6)
    val title = if (prediction.authoritative) "DX-target P.533" else "DX path estimate"
    Module(title, target?.let { "${it.callsign} · ${prediction.model.ifBlank { zone }}" } ?: zone, modifier = modifier) {
        (rows?.map { it.band to it.reliability } ?: listOf("40m", "20m", "15m", "10m").map { band ->
            band to hamClockReliability(band, zone, features.solar.flux, features.solar.kpIndex)
        }).forEach { (band, reliability) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(band, color = HcMuted, fontSize = 10.sp, modifier = Modifier.width(34.dp))
                Box(Modifier.weight(1f).height(7.dp).background(HcRaised, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(reliability / 100f).height(7.dp).background(if (reliability >= 70) HcGreen else HcAmber, RoundedCornerShape(4.dp)))
                }
                Text("$reliability%", color = HcInk, fontFamily = FontFamily.Monospace, modifier = Modifier.width(42.dp))
            }
        }
        val limits = listOfNotNull(prediction.mufMHz?.let { "MUF %.1f".format(Locale.US, it) },
            prediction.lufMHz?.let { "LUF %.1f MHz".format(Locale.US, it) }).joinToString(" · ")
        if (limits.isNotBlank()) Text(limits, color = HcCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(if (prediction.authoritative) "ITU-R P.533 path result · ${prediction.source}"
            else "ESTIMATE · ${prediction.source.ifBlank { prediction.error.ifBlank { "live SFI/Kp regional fallback" } }}",
            color = if (prediction.authoritative) HcGreen else HcMuted, fontSize = 9.sp, maxLines = 2)
    }
}

private fun hamClockReliability(band: String, zone: String, sfi: Float, kp: Float): Int {
    val base = mapOf("80m" to 58, "40m" to 70, "30m" to 76, "20m" to 82, "17m" to 72,
        "15m" to 64, "12m" to 50, "10m" to 42)[band] ?: 50
    val flux = ((sfi - 80) / 2).roundToInt().coerceIn(-15, 25)
    val storm = (kp * 5).roundToInt()
    val distance = when (zone) { "EUROPE" -> 8; "AFRICA" -> 2; "ASIA" -> -4; "AMERICAS" -> -7; else -> -8 }
    return (base + flux - storm + distance).coerceIn(0, 100)
}

@Composable private fun DxpeditionPanel(feed: HamClockFeed<List<HamClockDxpedition>>?, openDx: () -> Unit, modifier: Modifier) {
    val rows = feed?.value.orEmpty()
    Module("DXpeditions", feed?.let { "NG3K · ${it.state.name}" } ?: "NG3K", openDx, modifier) {
        if (rows.isEmpty()) EmptyLine(feed?.error?.ifBlank { "No current DXpedition entries" } ?: "DXpedition feed has not refreshed")
        else rows.take(7).forEach { item ->
            Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.callsign, color = HcYellow, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.width(70.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(item.entity.ifBlank { item.information }, item.dateText, item.modes.take(3).joinToString())
                    .filter(String::isNotBlank).joinToString(" · "), color = HcInk, fontSize = HcMetaSize,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun ContestsPanel(feed: HamClockFeed<List<HamClockContest>>?, modifier: Modifier) {
    val rows = feed?.value.orEmpty()
    Module("Contests", feed?.let { "WA7BNM · ${it.state.name}" } ?: "WA7BNM", modifier = modifier) {
        if (rows.isEmpty()) EmptyLine(feed?.error?.ifBlank { "No active or upcoming contests" } ?: "Contest calendar has not refreshed")
        else rows.take(5).forEach { contest ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(contest.mode.uppercase(), color = if (contest.status.name == "ACTIVE") HcGreen else HcAmber,
                    fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(contest.name, color = HcInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val start = Instant.ofEpochSecond(contest.startEpoch).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd MMM HH:mm 'UTC'"))
                    Text(start, color = HcMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable private fun SignalPanel(signal: NeuralMySignal, openDx: () -> Unit, modifier: Modifier) {
    val reports = signal.reports
    Module("Who hears me", "PSK REPORTER", openDx, modifier) {
        if (!signal.available) EmptyLine(signal.error.ifBlank { "PSK Reporter unavailable or no station callsign set" })
        else if (reports.isEmpty()) EmptyLine("PSK Reporter returned no recent reception reports") else reports.take(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(row.callsign, color = HcGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${row.band} ${row.mode}", color = HcInk, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(row.snr?.let { "$it dB" } ?: "—", color = HcMuted, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable private fun PortablePanel(portable: PortableController, openPortable: () -> Unit, modifier: Modifier) {
    val opportunities = portable.rankedOpportunities
    val potaCount = portable.pota.spots.size
    val wwffCount = portable.wwffSpots.size
    Module("Portable activators", "POTA $potaCount · WWFF $wwffCount", openPortable, modifier) {
        val potaStatus = portable.providerStatus(PortableProgram.POTA)
        val wwffStatus = portable.providerStatus(PortableProgram.WWFF)
        if (opportunities.isEmpty()) EmptyLine(listOf(potaStatus, wwffStatus).joinToString(" · ") {
            it.error.ifBlank { "${it.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.count} spots" }
        }) else opportunities.take(4).forEach { opportunity ->
            val spot = opportunity.spot
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spot.callsign, color = HcYellow, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(spot.references.joinToString { it.code }, color = HcMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("%.3f".format(Locale.US, spot.frequencyHz / 1_000_000.0), color = HcInk, fontFamily = FontFamily.Monospace)
            }
        }
        Text("SOTA live feed requires provider approval", color = HcMuted, fontSize = 9.sp)
    }
}

@Composable private fun BandConditionsPanel(activity: Map<String, Int>, openProgress: () -> Unit, modifier: Modifier) {
    Module("Band activity", "NEURAL DX WINDOW", openProgress, modifier) {
        val bands = listOf("80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m")
        bands.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row.forEach { band ->
                val count = activity[band] ?: 0
                Surface(color = (if (count > 0) HcGreen else HcRaised).copy(alpha = if (count > 0) .18f else 1f),
                    shape = RoundedCornerShape(3.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(band, color = if (count > 0) HcGreen else HcMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(count.toString(), color = HcInk, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        } }
    }
}

@Composable private fun SatellitePanel(controller: SatelliteOperationsController, openSatellites: () -> Unit, modifier: Modifier) {
    val next = controller.nextFavouritePass
    val state = controller.elements.metadata.state.name.replace('_', ' ')
    Module("Satellite operations", state, openSatellites, modifier) {
        if (next == null) {
            EmptyLine(controller.message)
        } else {
            val now = Instant.now().epochSecond
            val untilAos = (next.pass.aos - now).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(next.satellite.name, color = HcCyan, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        "AOS ${Instant.ofEpochSecond(next.pass.aos).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))} · " +
                            "T− ${untilAos / 3600}h ${(untilAos % 3600) / 60}m · ${next.pass.maximumElevationDeg.roundToInt()}°",
                        color = HcInk,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text("OPEN", color = HcGreen, fontWeight = FontWeight.Bold)
            }
            Text("Favourite-first · local pinned SGP4 · tap for passes, track and sky plot", color = HcMuted)
        }
    }
}

@Composable private fun PropagationStrip(neuralDx: NeuralDxController, modifier: Modifier) {
    Module("Propagation intelligence", neuralDx.status, modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("PREDICTIONS", neuralDx.predictions.size.toString(), HcCyan)
            Metric("WSPR HF", neuralDx.wspr.hf.size.toString(), HcGreen)
            Metric("WSPR VHF", neuralDx.wspr.vhf.size.toString(), HcGreen)
            Metric("WORLD CELLS", neuralDx.world.size.toString(), HcYellow)
            val next = neuralDx.predictions.maxByOrNull { it.probability }
            Column(Modifier.weight(1f)) {
                Text(next?.let { "${it.callsign} · ${it.band} ${it.mode} · ${it.probability}%" } ?: "Learning from live RF and log history",
                    color = HcInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(next?.reason ?: "Predictions appear only when measured evidence is available", color = HcMuted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun KeyValue(key: String, value: String, color: Color) = Row(Modifier.fillMaxWidth()) {
    Text(key, color = HcMuted, fontSize = HcLabelSize, fontWeight = FontWeight.Bold,
        letterSpacing = .15.sp, modifier = Modifier.weight(1f))
    Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun EmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().background(HcRaised, RoundedCornerShape(4.dp)).padding(10.dp)) {
        Text(text, color = HcMuted, fontSize = 11.sp)
    }
}

private fun ageLabel(epoch: Long): String {
    val seconds = (Instant.now().epochSecond - epoch).coerceAtLeast(0)
    return when { seconds < 60 -> "NOW"; seconds < 3600 -> "${seconds / 60}m AGO"; else -> "${seconds / 3600}h AGO" }
}

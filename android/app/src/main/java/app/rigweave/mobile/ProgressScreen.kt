package app.rigweave.mobile

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import kotlin.math.max

private val ProgressBackground = Color(0xFF091015)
private val ProgressPanel = Color(0xFF121C22)
private val ProgressRaised = Color(0xFF1B2A32)
private val ProgressInk = Color(0xFFE8F0F2)
private val ProgressMuted = Color(0xFF8EA2AA)
private val ProgressAmber = Color(0xFFE9A72B)
private val ProgressGreen = Color(0xFF48C78E)
private val ProgressBlue = Color(0xFF65A6C7)
private enum class ProgressSection(val label: String) {
    OVERVIEW("OVERVIEW"), ACTIVITY("ACTIVITY"), GEOGRAPHY("GEOGRAPHY"), CONFIRMATIONS("CONFIRMATIONS"),
    OPERATORS("OPERATORS"), PORTABLE("PORTABLE"), SATELLITE("SATELLITE"), NEEDS("NEEDS"), AWARDS("AWARDS")
}

@Composable
internal fun ProgressScreen(
    controller: ProgressController,
    features: FeatureController,
    portable: PortableController,
    syncHub: SyncHubController,
    cty: CtyController,
    currentStationId: String,
    currentCallsign: String,
    compact: Boolean,
    outlook: NeuralOutlookSnapshot,
    openDx: () -> Unit,
    openOutlook: () -> Unit,
    openDxEvidence: (String) -> Unit,
    openPortable: () -> Unit,
    openLogbook: () -> Unit,
    openLogbookFilter: (LogbookFilter) -> Unit,
    openSync: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(ProgressSection.OVERVIEW) }
    var goalDialog by remember { mutableStateOf(false) }
    val filters = controller.filters
    LaunchedEffect(currentStationId, currentCallsign) {
        if (!filters.allStations && filters.stationProfileId.isBlank() && filters.stationCallsign.isBlank())
            controller.updateFilters(filters.copy(stationProfileId = currentStationId, stationCallsign = currentCallsign))
    }
    val portableSpots = portable.pota.spots.map(PotaSpot::toPortable) + portable.sotaSpots + portable.wwffSpots
    val deliveredStates = setOf(DeliveryState.ACCEPTED, DeliveryState.ACCEPTED_DUPLICATE, DeliveryState.ACCEPTED_MODIFIED)
    val attention = syncHub.records.count { it.state !in deliveredStates }
    LaunchedEffect(filters, features.liveSpots, portableSpots, attention, controller.goalStore.goals, cty.dataRevision) {
        controller.refresh(filters, features.liveSpots, portableSpots, attention, cty, portable.sotaCatalogue)
    }
    LaunchedEffect(filters, features.liveSpots, portableSpots, attention, cty.dataRevision) {
        while (true) {
            delay(1_000)
            controller.refresh(filters, features.liveSpots, portableSpots, attention, cty, portable.sotaCatalogue)
        }
    }
    val snapshot = controller.snapshot
    val baseFilter = progressLogbookFilter(filters)
    Surface(Modifier.fillMaxSize(), color = ProgressBackground) {
        LazyColumn(Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LOG INTELLIGENCE", color = ProgressInk, style = MaterialTheme.typography.headlineMedium)
                        Text(if (filters.allStations) "ALL LOCAL DATA" else currentCallsign.ifBlank { "CURRENT STATION" },
                            color = ProgressMuted)
                    }
                    if (controller.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = ProgressAmber)
                    OutlinedButton({ goalDialog = true }, enabled = controller.goalStore.goals.size < 4) { Text("PIN GOAL") }
                }
            }
            item { ProgressKpiStrip(snapshot, baseFilter, openLogbookFilter) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val stationChoice = when {
                        filters.allStations -> "ALL"
                        filters.stationProfileId == currentStationId && filters.stationCallsign == currentCallsign -> "CURRENT"
                        filters.stationProfileId.isNotBlank() -> "PROFILE:${filters.stationProfileId}"
                        filters.stationCallsign.isNotBlank() -> "CALL:${filters.stationCallsign}"
                        else -> "CURRENT"
                    }
                    val stationChoices = buildList {
                        add("CURRENT" to "Current station")
                        add("ALL" to "All local data")
                        controller.stationProfiles.forEach { add("PROFILE:$it" to "Profile $it") }
                        controller.stationCallsigns.forEach { add("CALL:$it" to it) }
                    }
                    ProgressSingleFilterMenu("Station", stationChoice, stationChoices) { value ->
                        controller.updateFilters(when {
                            value == "ALL" -> filters.copy(allStations = true, stationProfileId = "", stationCallsign = "")
                            value.startsWith("PROFILE:") -> filters.copy(allStations = false, stationProfileId = value.substringAfter(':'), stationCallsign = "")
                            value.startsWith("CALL:") -> filters.copy(allStations = false, stationProfileId = "", stationCallsign = value.substringAfter(':'))
                            else -> filters.copy(allStations = false, stationProfileId = currentStationId, stationCallsign = currentCallsign)
                        })
                    }
                    ProgressSingleFilterMenu("Period", filters.period, ProgressPeriod.entries.map { it to it.label }) {
                        controller.updateFilters(filters.copy(period = it))
                    }
                    ProgressMultiFilterMenu("Bands", filters.selectedBands(),
                        listOf("160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m").map { it to it.uppercase() }) {
                        controller.updateFilters(filters.copy(bands = it, band = ""))
                    }
                    ProgressMultiFilterMenu("Mode", filters.selectedModeFamilies(),
                        ProgressMode.entries.filterNot { it == ProgressMode.ALL }.map { it to it.label }) {
                        controller.updateFilters(filters.copy(modeFamilies = it, mode = ProgressMode.ALL))
                    }
                    ProgressMultiFilterMenu("Submode", filters.selectedSubmodes(), controller.submodes.map { it to it }) {
                        controller.updateFilters(filters.copy(submodes = it, submode = ""))
                    }
                    ProgressMultiFilterMenu("Operator", filters.selectedOperators(), controller.operators.map { it to "OP $it" }) {
                        controller.updateFilters(filters.copy(operators = it, operator = ""))
                    }
                    ProgressMultiFilterMenu("Confirm", filters.selectedConfirmationSources(),
                        listOf("PAPER" to "Paper QSL", "LOTW" to "LoTW", "EQSL" to "eQSL", "QRZ" to "QRZ", "CLUBLOG" to "Club Log", "DCL" to "DCL")) {
                        controller.updateFilters(filters.copy(confirmationSources = it, confirmationSource = ""))
                    }
                    ProgressMultiFilterMenu("Programme", filters.selectedPortablePrograms(),
                        listOf("POTA" to "POTA", "SOTA" to "SOTA", "WWFF" to "WWFF")) {
                        controller.updateFilters(filters.copy(portablePrograms = it, portableProgram = ""))
                    }
                    FilterChip(filters.includeConflicted, { controller.updateFilters(filters.copy(includeConflicted = !filters.includeConflicted)) }, { Text("Conflicts") })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressSection.entries.forEach { value -> FilterChip(section == value, { section = value }, { Text(value.label, fontSize = 11.sp) }) }
                }
            }
            if (snapshot.goals.isNotEmpty()) item { GoalsCard(snapshot.goals, controller) }
            item {
                val live = controller.bandHealthSnapshot
                val selectedBands = filters.selectedBands()
                val selected = live.rows.firstOrNull { row -> selectedBands.any { it.equals(row.band, true) } }
                    ?: live.rows.firstOrNull()
                val forecast = outlook.topBands.firstOrNull { row -> selectedBands.any { it.equals(row.band, true) } }
                    ?: outlook.topBands.firstOrNull()
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (!compact && maxWidth >= 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiveRfCard(live, selected, baseFilter, openDxEvidence, openLogbookFilter, Modifier.weight(1f))
                        EmpiricalOutlookCard(outlook, forecast, baseFilter, openOutlook, openLogbookFilter, Modifier.weight(1f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiveRfCard(live, selected, baseFilter, openDxEvidence, openLogbookFilter, Modifier.fillMaxWidth())
                        EmpiricalOutlookCard(outlook, forecast, baseFilter, openOutlook, openLogbookFilter, Modifier.fillMaxWidth())
                    }
                }
            }
            when (section) {
                ProgressSection.OVERVIEW -> overviewItems(snapshot, compact, openSync, baseFilter, openLogbookFilter)
                ProgressSection.ACTIVITY -> activityItems(snapshot, compact)
                ProgressSection.GEOGRAPHY -> geographyItems(snapshot, compact, baseFilter, openLogbookFilter)
                ProgressSection.CONFIRMATIONS -> confirmationItems(snapshot, baseFilter, openLogbookFilter)
                ProgressSection.OPERATORS -> operatorItems(snapshot, baseFilter, openLogbookFilter)
                ProgressSection.PORTABLE -> portableItems(snapshot, portable, openPortable, baseFilter, openLogbookFilter)
                ProgressSection.SATELLITE -> satelliteItems(snapshot, baseFilter, openLogbookFilter)
                ProgressSection.NEEDS -> needsItems(snapshot, openDx, openPortable, baseFilter, openLogbookFilter)
                ProgressSection.AWARDS -> awardsItems(snapshot, controller.selectedAward, controller::selectAward,
                    baseFilter, openLogbookFilter)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (goalDialog) GoalDialog(controller, { goalDialog = false })
}

private fun androidx.compose.foundation.lazy.LazyListScope.satelliteItems(
    snapshot: ProgressSnapshot,
    base: LogbookFilter,
    openLogbook: (LogbookFilter) -> Unit,
) {
    val satellite = snapshot.satellite
    val satelliteFilter = base.copy(propagation = "SAT")
    item {
        ProgressCard("SATELLITE LOG INTELLIGENCE") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("QSOs", satellite.qsos.toString(), action = { openLogbook(satelliteFilter) })
                Kpi("SATELLITES", satellite.satellites.toString(), action = { openLogbook(satelliteFilter.copy(satellite = "*")) })
                Kpi("CALLS", satellite.uniqueCalls.toString(), action = { openLogbook(satelliteFilter) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("GRIDS", satellite.grids.toString(), action = { openLogbook(satelliteFilter.copy(grid = "*")) })
                Kpi("CONF GRIDS", satellite.confirmed.toString(), action = { openLogbook(satelliteFilter.copy(grid = "*", confirmationSource = "AWARD")) })
                Kpi("ROVER GRIDS", satellite.ownGrids.toString(), action = { openLogbook(satelliteFilter) })
            }
            Text("Local log estimates only · confirmations use paper QSL or LoTW · official award credit is not claimed", color = ProgressMuted)
        }
    }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val birds = satellite.bySatellite.map { ProgressBucket(it.key, it.value) }
            val modes = satellite.byMode.map { ProgressBucket(it.key, it.value) }
            if (maxWidth > 700.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("SATELLITES", birds, Modifier.weight(1f))
                ChartCard("MODES", modes, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("SATELLITES", birds, Modifier.fillMaxWidth())
                ChartCard("MODES", modes, Modifier.fillMaxWidth())
            }
        }
    }
    item {
        ProgressCard("WORKED / CONFIRMED GRID MATRIX") {
            satellite.workedConfirmed.entries.take(20).forEach { (name, count) ->
                TextButton({ openLogbook(satelliteFilter.copy(satellite = name)) }, Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(name, modifier = Modifier.weight(1f)); Text("${count.worked} QSOs · ${count.confirmed} confirmed grids")
                    }
                }
            }
            val favourite = satellite.bySatellite.maxByOrNull { it.value }
            Text(favourite?.let { "Best local history · ${it.key} · ${it.value} QSOs" } ?: "No satellite history in the current filter", color = ProgressMuted)
        }
    }
    item {
        ProgressCard("RECENT ACTIVITY & BREAKDOWN") {
            Text("Bands · ${satellite.byBand.entries.joinToString(" · ") { "${it.key} ${it.value}" }.ifBlank { "none" }}", color = ProgressMuted)
            Text("Recent · ${satellite.recentActivity.entries.take(12).joinToString(" · ") { "${it.key} ${it.value}" }.ifBlank { "none" }}", color = ProgressMuted)
            Text("Next Needed is intentionally not inferred from local history; current catalogue/status opportunities remain in Operations → Satellites.", color = ProgressMuted)
        }
    }
    item { OutlinedButton({ openLogbook(satelliteFilter) }, Modifier.fillMaxWidth()) { Text("OPEN SATELLITE LOGBOOK") } }
}

@Composable
private fun ProgressKpiStrip(
    snapshot: ProgressSnapshot,
    baseFilter: LogbookFilter,
    openLogbook: (LogbookFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Kpi("QSOs", snapshot.totalQsos.toString(), action = { openLogbook(baseFilter) })
        Kpi("UNIQUE CALLS", snapshot.uniqueCalls.toString(), action = { openLogbook(baseFilter) })
        Kpi("ACTIVE DAYS", snapshot.activeDays.toString(), action = { openLogbook(baseFilter) })
        Kpi("DXCC WORKED / CONF", "${snapshot.dxcc.worked} / ${snapshot.dxcc.confirmed}", action = { openLogbook(logbookFilterForDimension("dxcc", "*", baseFilter)) })
        Kpi("DXCC-MAPPED COUNTRIES", snapshot.countries.toString(), action = { openLogbook(logbookFilterForDimension("country", "*", baseFilter)) })
        Kpi("CONTINENTS", detailedValue(snapshot, snapshot.continents.size), action = { openLogbook(logbookFilterForDimension("continent", "*", baseFilter)) })
        Kpi("GRIDS WORKED / CONF", "${snapshot.grids} / ${snapshot.gridsConfirmed}", action = { openLogbook(logbookFilterForDimension("grid", "*", baseFilter)) })
        Kpi("CQ ZONES", detailedValue(snapshot, snapshot.cqZones.size), action = { openLogbook(baseFilter.copy(cqZone = "*")) })
        Kpi("ITU ZONES", detailedValue(snapshot, snapshot.ituZones.size), action = { openLogbook(baseFilter.copy(ituZone = "*")) })
        Kpi("STATES", detailedValue(snapshot, snapshot.states.worked), action = { openLogbook(logbookFilterForDimension("state", "*", baseFilter)) })
        Kpi("BEST DX", snapshot.longestDistanceKm?.let { "%,.0f km".format(it) } ?: "UNKNOWN", action = { openLogbook(baseFilter.copy(distance = ">0", sort = LogbookSort.DISTANCE)) })
        Kpi("AVERAGE VALID DX", snapshot.averageDistanceKm?.let { "%,.0f km".format(it) } ?: "UNKNOWN", action = { openLogbook(baseFilter.copy(distance = ">0")) })
        Kpi("QRP QSOs / DXCC", "${snapshot.qrpQsos} / ${snapshot.qrpDxcc}", action = { openLogbook(baseFilter.copy(txPower = "1..5")) })
        Kpi("UNCONFIRMED DXCC", snapshot.unconfirmedDxccCount.toString(), snapshot.unconfirmedDxccCount > 0,
            { openLogbook(baseFilter.copy(dxcc = "*", confirmationSource = "UNCONFIRMED")) })
        Kpi("SYNC ATTENTION", snapshot.syncAttention.toString(), snapshot.syncAttention > 0,
            { openLogbook(baseFilter.copy(syncRelation = "ATTENTION")) })
    }
}

@Composable
private fun LiveRfCard(
    live: app.rigweave.mobile.hamclock.HamClockBandHealthSnapshot,
    selected: app.rigweave.mobile.hamclock.HamClockBandHealthRow?,
    baseFilter: LogbookFilter,
    openDxEvidence: (String) -> Unit,
    openLogbook: (LogbookFilter) -> Unit,
    modifier: Modifier,
) {
    ProgressCard("LIVE RF / BAND HEALTH", modifier = modifier) {
        Text("OPERATIONAL LIVE EVIDENCE · NOT PROPAGATION FORECAST · NOT AWARD CREDIT",
            color = ProgressAmber, fontWeight = FontWeight.Bold)
        Text(selected?.let { "${it.band} · ${it.state} · confidence ${it.confidence} · ${it.reasons.joinToString(" · ")}" }
            ?: "No shared live RF snapshot", color = ProgressInk)
        Text("Sources · ${live.sourceStates.entries.joinToString(" · ") { "${it.key} ${it.value}" }}", color = ProgressMuted)
        if (live.message.isNotBlank()) Text(live.message, color = ProgressAmber)
        val historical = selected?.let { row -> live.historical.filter { it.band.equals(row.band, true) } }.orEmpty()
        Text("Historical projection · ${historical.sumOf { it.qsoCount }} QSOs · ${historical.sumOf { it.comparableWindowCount }} comparable UTC windows",
            color = ProgressMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ openDxEvidence(selected?.band.orEmpty()) }) { Text("DX RF EVIDENCE") }
            OutlinedButton({ selected?.let { openLogbook(baseFilter.copy(band = it.band)) } },
                enabled = selected != null) { Text("ADVANCED LOGBOOK") }
        }
    }
}

@Composable
private fun EmpiricalOutlookCard(
    outlook: NeuralOutlookSnapshot,
    forecast: OutlookForecast?,
    baseFilter: LogbookFilter,
    openOutlook: () -> Unit,
    openLogbook: (LogbookFilter) -> Unit,
    modifier: Modifier,
) {
    ProgressCard("EMPIRICAL OUTLOOK", modifier = modifier) {
        Text("OBSERVED SUPPORT · NOT P.533 · NOT AWARD CREDIT", color = ProgressAmber, fontWeight = FontWeight.Bold)
        Text(forecast?.let { "${it.window.minutes} min · ${it.band} · ${it.label.name.replace('_', ' ')} · ${it.confidence} · ${it.sourceCount} sources" }
            ?: "Insufficient evidence · ${outlook.calibration.label}", color = ProgressInk)
        Text("Calibration · ${outlook.calibration.label}", color = ProgressMuted)
        Text("Logbook history is context only; it cannot make live evidence or award credit.", color = ProgressMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(openOutlook) { Text("INSIGHT & OUTLOOK") }
            OutlinedButton({ forecast?.let { openLogbook(baseFilter.copy(band = it.band)) } },
                enabled = forecast != null) { Text("ADVANCED LOGBOOK") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(
    snapshot: ProgressSnapshot, compact: Boolean, openSync: () -> Unit, baseFilter: LogbookFilter,
    openLogbook: (LogbookFilter) -> Unit,
) {
    if (snapshot.syncAttention > 0) item { OutlinedButton(openSync, Modifier.fillMaxWidth()) { Text("OPEN SYNC HUB") } }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSO ACTIVITY · UTC", snapshot.activity, Modifier.weight(1f), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), loading = !snapshot.detailed)
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSO ACTIVITY · UTC", snapshot.activity, Modifier.fillMaxWidth(), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
            }
        }
    }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val operatorRows = snapshot.operators.map { ProgressBucket(it.key, it.value) }
            val confirmationRows = snapshot.confirmations.map { ProgressBucket(it.key, it.value) }
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("OPERATORS", operatorRows, Modifier.weight(1f), loading = !snapshot.detailed)
                ChartCard("CONFIRMATION SOURCES", confirmationRows, Modifier.weight(1f), "Award confirmation remains LoTW or paper QSL only", measure = "source matches")
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("OPERATORS", operatorRows, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
                ChartCard("CONFIRMATION SOURCES", confirmationRows, Modifier.fillMaxWidth(), "Award confirmation remains LoTW or paper QSL only", measure = "source matches")
            }
        }
    }
    if (snapshot.satellite.qsos > 0 || snapshot.antennas.isNotEmpty()) item {
        ProgressCard("SATELLITE & ANTENNA ANALYTICS") {
            if (snapshot.satellite.qsos > 0) {
                Text("SATELLITE · ${snapshot.satellite.qsos} QSOs · ${snapshot.satellite.satellites} birds · ${snapshot.satellite.grids} grids · ${snapshot.satellite.confirmed} confirmed")
                Text(snapshot.satellite.bySatellite.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" }, color = ProgressMuted)
            }
            snapshot.antennas.take(8).forEach { antenna ->
                Text("${antenna.path} · ${antenna.qsos} QSOs · ${antenna.confirmed} confirmed · best ${antenna.bestDistanceKm?.let { "%,.0f km".format(it) } ?: "unknown"}")
            }
        }
    }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatmapCard(snapshot.heatmap, Modifier.weight(1f), loading = !snapshot.detailed)
                ChartCard("DISTANCE DISTRIBUTION", snapshot.distance, Modifier.weight(1f),
                    snapshot.coverage["Distance"]?.label.orEmpty(), ProgressChartOrder.PRESERVE, loading = !snapshot.detailed)
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatmapCard(snapshot.heatmap, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
                ChartCard("DISTANCE DISTRIBUTION", snapshot.distance, Modifier.fillMaxWidth(), snapshot.coverage["Distance"]?.label.orEmpty(), ProgressChartOrder.PRESERVE, loading = !snapshot.detailed)
            }
        }
    }
    item {
        ProgressCard("CONTACT MAP") {
            if (snapshot.contacts.isEmpty()) Text("No valid contact grids in this scope.", color = ProgressMuted)
            else {
                ProgressContactMap(snapshot.contacts, Modifier.fillMaxWidth().height(if (compact) 240.dp else 320.dp))
                Text("${snapshot.contacts.size} unique valid grids · map failure does not affect statistics", color = ProgressMuted, fontSize = 11.sp)
            }
        }
    }
    item { OutlinedButton({ openLogbook(baseFilter) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("OPEN FILTERED LOGBOOK") } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.activityItems(snapshot: ProgressSnapshot, compact: Boolean) {
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSOs BY YEAR", snapshot.years.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
                ChartCard("QSOs BY MONTH · LAST 18 UTC MONTHS", snapshot.months.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSOs BY YEAR", snapshot.years.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
                ChartCard("QSOs BY MONTH · LAST 18 UTC MONTHS", snapshot.months.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed)
            }
        }
    }
    item { ChartCard("RECENT DAILY ACTIVITY · LAST 18 UTC DAYS", snapshot.recentDays.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), order = ProgressChartOrder.CHRONOLOGICAL, loading = !snapshot.detailed) }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), loading = !snapshot.detailed)
                ChartCard("MODE FAMILY", snapshot.modes.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), loading = !snapshot.detailed)
                ChartCard("MODE / SUBMODE", snapshot.submodes.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f), loading = !snapshot.detailed)
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
                ChartCard("MODE FAMILY", snapshot.modes.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
                ChartCard("MODE / SUBMODE", snapshot.submodes.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth(), loading = !snapshot.detailed)
            }
        }
    }
    item {
        var local by rememberSaveable { mutableStateOf(false) }
        ProgressCard("WHEN QSOs HAPPEN · HOUR × WEEKDAY", largeTitle = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!local, { local = false }, { Text("UTC") })
                FilterChip(local, { local = true }, { Text("Device local time") })
            }
            if (snapshot.detailed) ActivityHeatmap(if (local) snapshot.localHeatmap else snapshot.heatmap, Modifier.fillMaxWidth())
            else PendingChart()
            Text(if (local) "Bins use the device's local weekday and wall-clock hour." else "Bins use the logged UTC weekday and hour.", color = ProgressMuted, fontSize = 17.sp)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.geographyItems(
    snapshot: ProgressSnapshot, compact: Boolean, base: LogbookFilter, open: (LogbookFilter) -> Unit,
) {
    item { ProgressCard("DXCC / COUNTRY · WORKED VS CONFIRMED") {
        if (snapshot.geography.isEmpty()) Text("DXCC data is unavailable in this scope.", color = ProgressMuted)
        snapshot.geography.take(100).forEach { row -> IntelligenceRow("${row.code} · ${row.label}",
            "${row.count.worked} QSOs · ${row.count.confirmed} locally confirmed") { open(logbookFilterForDimension("dxcc", row.code, base)) } }
        Text(snapshot.coverage["DXCC"]?.label.orEmpty(), color = ProgressMuted, fontSize = 11.sp)
    } }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DimensionCard("CONTINENTS", snapshot.continents, "continent", base, open, Modifier.weight(1f))
                DimensionCard("CQ ZONES", snapshot.cqZones, "cqzone", base, open, Modifier.weight(1f))
                DimensionCard("ITU ZONES", snapshot.ituZones, "ituzone", base, open, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DimensionCard("CONTINENTS", snapshot.continents, "continent", base, open, Modifier.fillMaxWidth())
                DimensionCard("CQ ZONES", snapshot.cqZones, "cqzone", base, open, Modifier.fillMaxWidth())
                DimensionCard("ITU ZONES", snapshot.ituZones, "ituzone", base, open, Modifier.fillMaxWidth())
            }
        }
    }
    item { ProgressCard("STATES / SUBDIVISIONS") {
        val was = snapshot.awards[AwardKind.WAS]
        if (was == null || was.units.isEmpty()) Text("No valid U.S. state data in this scope.", color = ProgressMuted)
        was?.units?.forEach { unit -> IntelligenceRow(unit.code, "${unit.qsos} QSOs · ${if (unit.confirmed) "confirmed" else "unconfirmed"}") {
            open(logbookFilterForDimension("state", unit.code, base))
        } }
        Text(snapshot.coverage["U.S. state"]?.label.orEmpty(), color = ProgressMuted, fontSize = 11.sp)
    } }
    item { ProgressCard("GRIDS & CONTACT MAP") {
        Text("${snapshot.grids} worked · ${snapshot.gridsConfirmed} locally confirmed", color = ProgressInk)
        if (snapshot.contacts.isEmpty()) Text("No valid contact grids in this scope.", color = ProgressMuted)
        else ProgressContactMap(snapshot.contacts, Modifier.fillMaxWidth().height(if (compact) 240.dp else 340.dp))
    } }
    item { ProgressCard("BEST DX") {
        if (snapshot.bestDx.isEmpty()) Text("Distance is unknown for this scope.", color = ProgressMuted)
        snapshot.bestDx.forEach { row -> IntelligenceRow("${row.callsign} · ${row.country.ifBlank { "Country unavailable" }}",
            "%,.0f km · %s · %s".format(row.distanceKm, row.band, row.mode)) { open(logbookFilterForDimension("callsign", row.callsign, base)) } }
    } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.confirmationItems(
    snapshot: ProgressSnapshot, base: LogbookFilter, open: (LogbookFilter) -> Unit,
) {
    item { ProgressCard("LOCAL CONFIRMATION SOURCES") {
        if (snapshot.totalQsos == 0) Text("No QSOs in this scope.", color = ProgressMuted)
        snapshot.confirmationDetails.forEach { (source, progress) ->
            IntelligenceRow(source, "${progress.confirmed} of ${progress.total} · ${progress.percent?.let { "%.1f%%".format(it) } ?: "unknown"}") {
                open(base.copy(confirmationSource = source.replace("Paper QSL", "PAPER").replace("Club Log", "CLUBLOG")))
            }
        }
        Text("Upload acceptance is never counted as confirmation. Award estimates use paper QSL or LoTW only.", color = ProgressMuted, fontSize = 11.sp)
    } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.operatorItems(
    snapshot: ProgressSnapshot, base: LogbookFilter, open: (LogbookFilter) -> Unit,
) {
    item { OperatorDimensionCard("OPERATORS", snapshot.operators, "operator", base, open) }
    item { OperatorDimensionCard("LOCAL STATION PROFILES", snapshot.stationProfiles, "stationprofile", base, open) }
    item { OperatorDimensionCard("STATION CALLSIGNS", snapshot.stationCallsigns, "stationcallsign", base, open) }
    item { OperatorDimensionCard("RADIO FAMILY / MODEL", snapshot.radios, "radio", base, open) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.needsItems(
    snapshot: ProgressSnapshot, openDx: () -> Unit, openPortable: () -> Unit,
    base: LogbookFilter, openLogbook: (LogbookFilter) -> Unit,
) {
    item { ProgressCard("LIVE NOW") {
        if (snapshot.needs.isEmpty()) Text("No resolved live activity currently advances this scope.", color = ProgressMuted)
        snapshot.needs.take(20).forEach { need ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(need.title, color = ProgressInk)
                    Text(need.detail, color = ProgressMuted, fontSize = 12.sp)
                    Text(need.reasons.joinToString(" · "), color = ProgressAmber, fontSize = 11.sp)
                }
                OutlinedButton(if (need.target == NeedTarget.DX) openDx else openPortable) {
                    Text(if (need.target == NeedTarget.DX) "OPEN IN DX" else "OPEN IN PORTABLE")
                }
            }
            HorizontalDivider(color = ProgressRaised)
        }
        Text("SOTA LIVE · UNAVAILABLE", color = ProgressMuted, fontSize = 11.sp)
    } }
    item { ProgressCard("WORKED, NOT LOTW/QSL CONFIRMED") {
        if (snapshot.unconfirmedDxcc.isEmpty()) Text("No unconfirmed DXCC-style entities in this scope.", color = ProgressMuted)
        snapshot.unconfirmedDxcc.take(20).forEach { (dxcc, qsos) ->
            val digital = buildList {
                if (qsos.any { it.qrzReceived.uppercase(Locale.US) in setOf("Y","V") }) add("QRZ confirmation recorded")
                if (qsos.any { it.eqslReceived.uppercase(Locale.US) in setOf("Y","V") }) add("eQSL confirmation recorded")
            }
            IntelligenceRow("$dxcc · ${qsos.size} QSO${if (qsos.size == 1) "" else "s"}",
                qsos.map { it.band }.filter(String::isNotBlank).distinct().joinToString()) {
                openLogbook(logbookFilterForDimension("dxcc", dxcc, base.copy(confirmationSource = "UNCONFIRMED")))
            }
            if (digital.isNotEmpty()) Text(digital.joinToString(" · "), color = ProgressBlue, fontSize = 11.sp)
        }
        OutlinedButton({ openLogbook(base.copy(dxcc = "*", confirmationSource = "UNCONFIRMED")) }, Modifier.fillMaxWidth()) { Text("OPEN LOGBOOK") }
    } }
    item { ProgressCard("BAND / MODE GAPS") {
        CountRows(snapshot.dxccByBand, "band")
        HorizontalDivider(color = ProgressRaised)
        CountRows(snapshot.dxccByMode, "mode")
    } }
    item { ProgressCard("DATA QUALITY") {
        snapshot.coverage.forEach { (label, coverage) ->
            Text("${coverage.total - coverage.available} QSOs missing $label · ${coverage.label}", color = ProgressInk)
        }
    } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.awardsItems(
    snapshot: ProgressSnapshot, selected: AwardKind, select: (AwardKind) -> Unit,
    base: LogbookFilter, open: (LogbookFilter) -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AwardKind.entries.forEach { kind -> FilterChip(selected == kind, { select(kind) }, { Text(kind.label) }) }
        }
    }
    val award = snapshot.awards[selected]
    item { ProgressCard(selected.label.uppercase(Locale.US)) {
        Text(selected.rule, color = ProgressInk)
        Text("WORKED · ${award?.count?.worked ?: 0}${award?.target?.let { " / $it" }.orEmpty()}", color = ProgressInk, style = MaterialTheme.typography.titleLarge)
        Text("LOCALLY CONFIRMED · ${award?.count?.confirmed ?: 0}${award?.target?.let { " / $it" }.orEmpty()}", color = ProgressGreen)
        award?.target?.takeIf { it > 0 }?.let { target -> LinearProgressIndicator({ (award.count.worked / target.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = ProgressGreen) }
        Text(award?.coverage?.label.orEmpty(), color = ProgressMuted, fontSize = 11.sp)
        if (award?.coverage?.available != award?.coverage?.total) Text("Required ADIF coverage is incomplete; missing values remain unknown, not zero.", color = ProgressAmber, fontSize = 11.sp)
        if (!award?.warning.isNullOrBlank()) Text(award?.warning.orEmpty(), color = ProgressAmber, fontSize = 11.sp)
    } }
    item { ProgressCard("BAND / MODE MATRIX") {
        CountRows(award?.byBand.orEmpty(), "band")
        HorizontalDivider(color = ProgressRaised)
        CountRows(award?.byMode.orEmpty(), "mode")
    } }
    item { ProgressCard("WORKED UNITS") {
        if (award?.units.isNullOrEmpty()) Text("No qualifying units in this scope.", color = ProgressMuted)
        award?.units?.take(250)?.forEach { unit -> IntelligenceRow("${unit.code} · ${unit.label}",
            "${unit.qsos} QSO${if (unit.qsos == 1) "" else "s"} · ${if (unit.confirmed) "locally confirmed" else "unconfirmed"}") {
            val scoped = if (selected == AwardKind.QRP) base.copy(txPower = "1..5") else base
            open(logbookFilterForDimension(selected.filterKey, unit.code, scoped))
        } }
    } }
    if (!award?.missing.isNullOrEmpty()) item { ProgressCard("MISSING UNITS") {
        Text(award?.missing?.joinToString(" · ").orEmpty(), color = ProgressMuted)
    } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.portableItems(
    snapshot: ProgressSnapshot, portable: PortableController, openPortable: () -> Unit,
    base: LogbookFilter, openLogbook: (LogbookFilter) -> Unit,
) {
    item { ProgressCard("HUNTER PROGRESS") {
        Text("POTA · ${snapshot.portable.potaHunted.size} unique parks", color = ProgressInk)
        Text("SOTA · ${snapshot.portable.sotaHunted.size} unique summits", color = ProgressInk)
        Text("SOTA catalogue coverage · ${snapshot.portable.sotaAssociations.size} associations · ${snapshot.portable.sotaRegions.size} regions", color = ProgressMuted)
        Text("WWFF · ${snapshot.portable.wwffHunted.size} unique references · no worldwide denominator", color = ProgressInk)
        Text("P2P · ${snapshot.portable.p2pQsos} local QSOs", color = ProgressInk)
        CountRows(snapshot.portable.portableByBand.mapValues { ProgressCount(it.value, 0) }, "band", confirmed = false)
    } }
    item { ProgressCard("ACTIVATOR PROGRESS") {
        Text("${snapshot.portable.activations.size} retained sessions · ${snapshot.portable.potaActivated.size} own parks", color = ProgressInk)
        Text("${snapshot.portable.successfulActivations} local ≥10-QSO park/day estimates", color = ProgressAmber)
        snapshot.portable.bestRoverDay?.let { Text("Best rover day · ${it.first} · ${it.second} parks", color = ProgressInk) }
        snapshot.portable.activations.take(12).forEach { row ->
            HorizontalDivider(color = ProgressRaised)
            Text(row.ownParks.joinToString().ifBlank { "POTA session" }, color = ProgressInk)
            Text("${row.qsos} QSOs · ${row.uniqueCalls} calls · ${row.p2p} P2P · ${row.durationMinutes} min" +
                (row.qsoRate?.let { " · %.1f QSO/h".format(it) } ?: ""), color = ProgressMuted)
        }
    } }
    item { ProgressCard("PROGRAM LIMITS") {
        Text("SOTA live · ${portable.sotaStatus.error.ifBlank { portable.sotaStatus.kind.label }}", color = ProgressMuted)
        Text("WWFF remains list-only without a full directory. No official SOTA points or WWFF credit is calculated.", color = ProgressMuted)
    } }
    item { Button(openPortable, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("OPEN PORTABLE CHASE") } }
    item { OutlinedButton({ openLogbook(base.copy(portableProgram = base.portableProgram.ifBlank { "ANY" })) },
        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("OPEN PORTABLE QSOs IN LOGBOOK") } }
}

@Composable private fun <T> ProgressSingleFilterMenu(
    label: String, selected: T, choices: List<Pair<T, String>>, change: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.first == selected }?.second ?: "All"
    Box {
        OutlinedButton({ open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("$label · $selectedLabel", maxLines = 1)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 520.dp)) {
            choices.distinctBy { it.first }.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, leadingIcon = { Checkbox(value == selected, null) },
                    onClick = { change(value); open = false })
            }
        }
    }
}

@Composable private fun <T> ProgressMultiFilterMenu(
    label: String, selected: Set<T>, choices: List<Pair<T, String>>, change: (Set<T>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = when (selected.size) {
        0 -> "All"
        1 -> choices.firstOrNull { it.first in selected }?.second ?: "1 selected"
        else -> "${selected.size} selected"
    }
    Box {
        OutlinedButton({ open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("$label · $selectedLabel", maxLines = 1)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 560.dp)) {
            DropdownMenuItem(text = { Text("All") }, leadingIcon = { Checkbox(selected.isEmpty(), null) },
                onClick = { change(emptySet()) })
            choices.distinctBy { it.first }.forEach { (value, text) ->
                val checked = value in selected
                DropdownMenuItem(text = { Text(text) }, leadingIcon = { Checkbox(checked, null) },
                    onClick = { change(if (checked) selected - value else selected + value) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Done", fontWeight = FontWeight.Bold) }, onClick = { open = false })
        }
    }
}

@Composable private fun Kpi(label: String, value: String, alert: Boolean = false, action: (() -> Unit)? = null) {
    Card(onClick = action ?: {}, modifier = Modifier.widthIn(min = 126.dp),
        colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = ProgressMuted, fontSize = 10.sp)
            Text(value, color = if (alert) ProgressAmber else ProgressInk, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun detailedValue(snapshot:ProgressSnapshot,value:Int)=if(snapshot.detailed)value.toString() else "…"

@Composable private fun PendingChart() {
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text("Calculating this breakdown in the background…", color = ProgressMuted, fontSize = 11.sp)
    }
}

@Composable private fun IntelligenceRow(title: String, detail: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ProgressInk, maxLines = 1)
            if (detail.isNotBlank()) Text(detail, color = ProgressMuted, fontSize = 11.sp, maxLines = 2)
        }
        TextButton(open) { Text("VIEW") }
    }
    HorizontalDivider(color = ProgressRaised)
}

@Composable private fun DimensionCard(
    title: String, rows: Map<String, ProgressCount>, key: String, base: LogbookFilter,
    open: (LogbookFilter) -> Unit, modifier: Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = ProgressAmber)
            if (rows.isEmpty()) Text("Data unavailable in this scope.", color = ProgressMuted)
            rows.forEach { (value, count) -> IntelligenceRow(value, "${count.worked} QSOs · ${count.confirmed} confirmed") {
                open(logbookFilterForDimension(key, value, base))
            } }
        }
    }
}

@Composable private fun OperatorDimensionCard(
    title: String, rows: Map<String, Int>, key: String, base: LogbookFilter, open: (LogbookFilter) -> Unit,
) {
    ProgressCard(title) {
        if (rows.isEmpty()) Text("Data unavailable in this scope.", color = ProgressMuted)
        rows.toList().sortedByDescending { it.second }.forEach { (value, count) -> IntelligenceRow(value, "$count QSOs") {
            open(if (value == "UNKNOWN") base else logbookFilterForDimension(key, value, base))
        } }
    }
}

@Composable private fun ActivityHeatmap(rows: List<ProgressHeatCell>, modifier: Modifier) {
    if (rows.isEmpty()) { Text("No activity in this scope.", color = ProgressMuted); return }
    val highest = max(1, rows.maxOf(ProgressHeatCell::count))
    val total = rows.sumOf(ProgressHeatCell::count)
    val byCell = rows.associateBy { it.day to it.hour }
    val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Each cell is a QSO count. Rows are weekdays; columns are UTC/local hours 00–23. Darker cells mean more activity.",
            color = ProgressMuted, fontSize = 17.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(52.dp))
            repeat(24) { hour -> Text(if (hour % 2 == 0) hour.toString().padStart(2, '0') else "", color = ProgressMuted,
                fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)) }
        }
        weekdays.forEachIndexed { day, label ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = ProgressMuted, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                repeat(24) { hour ->
                    val count = byCell[day to hour]?.count ?: 0
                    Box(Modifier.weight(1f).height(36.dp).padding(.5.dp).background(
                        ProgressGreen.copy(alpha = if (count == 0) .06f else .18f + .82f * count / highest), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center) {
                        if (count > 0) Text(chartCompactCount(count), color = ProgressInk, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        val peak = rows.maxByOrNull(ProgressHeatCell::count)
        Text("${formatChartCount(total)} QSOs in the matrix · peak ${peak?.let { "${weekdays[it.day]} ${it.hour.toString().padStart(2, '0')}:00–${((it.hour + 1) % 24).toString().padStart(2, '0')}:00 · ${formatChartCount(it.count)} QSOs" } ?: "none"}",
            color = ProgressInk, fontSize = 17.sp)
    }
}

@Composable private fun ProgressCard(
    title: String,
    largeTitle: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ProgressAmber, style = MaterialTheme.typography.titleSmall,
                fontSize = if (largeTitle) 22.sp else MaterialTheme.typography.titleSmall.fontSize)
            content()
        }
    }
}

internal enum class ProgressChartOrder { CHRONOLOGICAL, RANKED, PRESERVE }

internal fun visibleProgressChartRows(rows: List<ProgressBucket>, order: ProgressChartOrder, limit: Int = 18): List<ProgressBucket> =
    when (order) {
        ProgressChartOrder.CHRONOLOGICAL -> rows.sortedBy(ProgressBucket::label).takeLast(limit)
        ProgressChartOrder.RANKED -> rows.sortedByDescending(ProgressBucket::count).take(limit)
        ProgressChartOrder.PRESERVE -> rows.take(limit)
    }

@Composable private fun ChartCard(
    title: String, rows: List<ProgressBucket>, modifier: Modifier, coverage: String = "",
    order: ProgressChartOrder = ProgressChartOrder.RANKED, measure: String = "QSOs", loading: Boolean = false,
) {
    val visible = visibleProgressChartRows(rows, order)
    val peak = rows.maxByOrNull(ProgressBucket::count)
    val total = rows.sumOf(ProgressBucket::count)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ProgressAmber, fontSize = 22.sp)
            if (loading) PendingChart()
            else if (rows.none { it.count > 0 }) Text("No data in this scope.", color = ProgressMuted)
            else {
                Text("${formatChartCount(total)} $measure · ${if (rows.size > visible.size) "showing ${visible.size} of ${rows.size}" else "${rows.size} buckets"} · peak ${peak?.label.orEmpty()} ${formatChartCount(peak?.count ?: 0)}",
                    color = ProgressInk, fontSize = 17.sp)
                BarChart(visible, Modifier.fillMaxWidth().height(210.dp))
                Text(when (order) {
                    ProgressChartOrder.CHRONOLOGICAL -> "Oldest to newest; missing calendar periods are shown as zero where applicable."
                    ProgressChartOrder.RANKED -> "Highest-volume categories first."
                    ProgressChartOrder.PRESERVE -> "Buckets follow the stated range order."
                }, color = ProgressMuted, fontSize = 15.sp)
            }
            if (coverage.isNotBlank()) Text(coverage, color = ProgressMuted, fontSize = 17.sp)
        }
    }
}

@Composable private fun BarChart(rows: List<ProgressBucket>, modifier: Modifier) {
    val highest = max(1, rows.maxOfOrNull(ProgressBucket::count) ?: 1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        rows.forEach { row ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(chartCompactCount(row.count), color = ProgressInk, fontSize = 12.sp, maxLines = 1)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(.72f).fillMaxHeight(row.count.toFloat() / highest)
                        .background(ProgressBlue, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
                Text(shortChartLabel(row.label), color = ProgressMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun formatChartCount(value:Int)=String.format(Locale.US,"%,d",value)
private fun chartCompactCount(value:Int)=when{
    value>=1_000_000->String.format(Locale.US,"%.1fM",value/1_000_000.0)
    value>=10_000->String.format(Locale.US,"%.1fk",value/1_000.0)
    else->formatChartCount(value)
}
private fun shortChartLabel(value:String)=when{
    value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))->value.substring(5)
    value.matches(Regex("\\d{4}-\\d{2}"))->value.substring(2)
    else->value.take(9)
}

@Composable private fun HeatmapCard(rows: List<ProgressHeatCell>, modifier: Modifier, loading:Boolean=false) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HOUR × DAY · UTC", color = ProgressAmber)
            if (loading) PendingChart()
            else if (rows.isEmpty()) Text("No activity in this scope.", color = ProgressMuted)
            else ActivityHeatmap(rows, Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun EstimateCard(title: String, count: ProgressCount, target: Int, content: @Composable ColumnScope.() -> Unit) {
    ProgressCard(title) {
        Text("WORKED LOCALLY · ${count.worked} / $target", color = ProgressInk)
        Text("LOTW/QSL CONFIRMED LOCALLY · ${count.confirmed} / $target", color = ProgressGreen)
        Text("OFFICIAL STATUS UNKNOWN", color = ProgressMuted, fontSize = 11.sp)
        content()
    }
}

@Composable private fun CountRows(rows: Map<String, ProgressCount>, label: String, confirmed: Boolean = true) {
    if (rows.isEmpty()) Text("No $label data in this scope.", color = ProgressMuted)
    rows.toList().sortedBy { it.first }.forEach { (key, value) ->
        Text(if (confirmed) "$key · ${value.worked} worked · ${value.confirmed} confirmed" else "$key · ${value.worked}",
            color = ProgressInk)
    }
}

@Composable private fun GoalsCard(rows: List<GoalProgress>, controller: ProgressController) {
    ProgressCard("PINNED GOALS") {
        rows.forEach { progress ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(progress.goal.name, color = ProgressInk)
                    Text("${progress.current} / ${progress.goal.target} · ${progress.remaining} remaining", color = ProgressMuted)
                    LinearProgressIndicator({ progress.percent / 100f }, Modifier.fillMaxWidth(), color = ProgressGreen)
                }
                TextButton({ controller.goalStore.remove(progress.goal.id); controller.goalsChanged() }) { Text("REMOVE") }
            }
        }
    }
}

@Composable private fun GoalDialog(controller: ProgressController, dismiss: () -> Unit) {
    var metric by remember { mutableStateOf(ProgressGoalMetric.TOTAL_QSOS) }
    var target by remember { mutableStateOf("100") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("PIN A LOCAL GOAL") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressGoalMetric.entries.forEach { value -> FilterChip(metric == value, { metric = value }, { Text(value.label) }) }
            }
            OutlinedTextField(target, { target = it.filter(Char::isDigit).take(7) }, label = { Text("Integer target") })
            Text("Personal goal · not an official award claim", color = ProgressMuted)
        }
    }, confirmButton = { Button({
        target.toIntOrNull()?.let { controller.goalStore.add(metric, it); controller.goalsChanged() }
        dismiss()
    }, enabled = target.toIntOrNull()?.let { it > 0 } == true) { Text("PIN") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}

@Composable private fun ProgressContactMap(rows: List<ProgressContactPoint>, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRows by rememberUpdatedState(rows)
    val mapView = remember { MapLibre.getInstance(context.applicationContext); MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> mapView.onStart(); Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause(); Lifecycle.Event.ON_STOP -> mapView.onStop(); else -> Unit
        } }
        lifecycle.addObserver(observer)
        mapView.getMapAsync { value ->
            map = value
            value.uiSettings.isAttributionEnabled = false
            value.uiSettings.isLogoEnabled = false
            value.uiSettings.isAttributionEnabled = true
            value.uiSettings.isLogoEnabled = true
            value.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { ready = true }
        }
        onDispose { lifecycle.removeObserver(observer); mapView.onPause(); mapView.onStop(); mapView.onDestroy(); map = null }
    }
    LaunchedEffect(map, ready, rows) {
        val value = map ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        value.clear()
        currentRows.groupBy { (it.latitude / 5).toInt() to (it.longitude / 5).toInt() }.values.forEach { group ->
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawCircle(12f, 12f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(101,166,199) })
            value.addMarker(MarkerOptions().position(LatLng(group.map(ProgressContactPoint::latitude).average(),
                group.map(ProgressContactPoint::longitude).average())).title(if (group.size == 1) group.first().grid else "${group.size} contact grids")
                .icon(IconFactory.getInstance(context).fromBitmap(bitmap)))
        }
        if (rows.size == 1) value.cameraPosition = CameraPosition.Builder().target(LatLng(rows[0].latitude,rows[0].longitude)).zoom(5.0).build()
        else runCatching {
            val bounds = LatLngBounds.Builder().also { builder -> rows.forEach { builder.include(LatLng(it.latitude,it.longitude)) } }.build()
            value.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,60),400)
        }
    }
    Box(modifier.background(ProgressBackground).border(1.dp,ProgressRaised,RoundedCornerShape(8.dp))) {
        AndroidView({ mapView }, Modifier.fillMaxSize())
        Text("OpenFreeMap © OpenMapTiles · OpenStreetMap", color = ProgressMuted, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(ProgressPanel.copy(alpha=.85f)).padding(4.dp))
    }
}

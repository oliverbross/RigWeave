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
import androidx.compose.material3.CircularProgressIndicator
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
    openDx: () -> Unit,
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
            item { LocalEstimateBanner() }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!filters.allStations && filters.stationProfileId == currentStationId && filters.stationCallsign == currentCallsign, {
                        controller.updateFilters(filters.copy(allStations = false, stationProfileId = currentStationId, stationCallsign = currentCallsign))
                    }, { Text("Current station") })
                    FilterChip(filters.allStations, { controller.updateFilters(filters.copy(allStations = true, stationProfileId = "", stationCallsign = "")) }, { Text("All local data") })
                    controller.stationProfiles.forEach { profile -> FilterChip(!filters.allStations && filters.stationProfileId == profile, {
                        controller.updateFilters(filters.copy(allStations = false, stationProfileId = profile, stationCallsign = ""))
                    }, { Text("Profile $profile") }) }
                    controller.stationCallsigns.forEach { call -> FilterChip(!filters.allStations && filters.stationProfileId.isBlank() && filters.stationCallsign == call, {
                        controller.updateFilters(filters.copy(allStations = false, stationProfileId = "", stationCallsign = call))
                    }, { Text(call) }) }
                    ProgressPeriod.entries.forEach { value -> FilterChip(filters.period == value, { controller.updateFilters(filters.copy(period = value)) }, { Text(value.label) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filters.operator.isBlank(), { controller.updateFilters(filters.copy(operator = "")) }, { Text("All operators") })
                    controller.operators.forEach { value -> FilterChip(filters.operator.equals(value, true),
                        { controller.updateFilters(filters.copy(operator = value)) }, { Text("OP $value") }) }
                    FilterChip(filters.submode.isBlank(), { controller.updateFilters(filters.copy(submode = "")) }, { Text("All submodes") })
                    controller.submodes.forEach { value -> FilterChip(filters.submode.equals(value, true),
                        { controller.updateFilters(filters.copy(submode = value)) }, { Text(value) }) }
                    listOf("" to "All confirmations", "PAPER" to "Paper QSL", "LOTW" to "LoTW", "EQSL" to "eQSL",
                        "QRZ" to "QRZ", "CLUBLOG" to "Club Log").forEach { (value, label) ->
                        FilterChip(filters.confirmationSource == value, { controller.updateFilters(filters.copy(confirmationSource = value)) }, { Text(label) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "80m", "40m", "20m", "15m", "10m").forEach { value ->
                        FilterChip(filters.band == value, { controller.updateFilters(filters.copy(band = value)) }, { Text(value.ifBlank { "All bands" }) })
                    }
                    ProgressMode.entries.forEach { value -> FilterChip(filters.mode == value, { controller.updateFilters(filters.copy(mode = value)) }, { Text(value.label) }) }
                    listOf("", "POTA", "SOTA", "WWFF").forEach { value -> FilterChip(filters.portableProgram == value,
                        { controller.updateFilters(filters.copy(portableProgram = value)) }, { Text(value.ifBlank { "All programmes" }) }) }
                    FilterChip(filters.includeConflicted, { controller.updateFilters(filters.copy(includeConflicted = !filters.includeConflicted)) }, { Text("Conflicts") })
                    FilterChip(filters.includeDeleted, { controller.updateFilters(filters.copy(includeDeleted = !filters.includeDeleted)) }, { Text("Deleted") })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProgressSection.entries.forEach { value -> FilterChip(section == value, { section = value }, { Text(value.label, fontSize = 11.sp) }) }
                }
            }
            if (snapshot.goals.isNotEmpty()) item { GoalsCard(snapshot.goals, controller) }
            when (section) {
                ProgressSection.OVERVIEW -> overviewItems(snapshot, compact, openSync, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.ACTIVITY -> activityItems(snapshot, compact)
                ProgressSection.GEOGRAPHY -> geographyItems(snapshot, compact, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.CONFIRMATIONS -> confirmationItems(snapshot, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.OPERATORS -> operatorItems(snapshot, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.PORTABLE -> portableItems(snapshot, portable, openPortable, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.SATELLITE -> satelliteItems(snapshot, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.NEEDS -> needsItems(snapshot, openDx, openPortable, progressLogbookFilter(filters), openLogbookFilter)
                ProgressSection.AWARDS -> awardsItems(snapshot, controller.selectedAward, controller::selectAward,
                    progressLogbookFilter(filters), openLogbookFilter)
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

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(
    snapshot: ProgressSnapshot, compact: Boolean, openSync: () -> Unit, baseFilter: LogbookFilter,
    openLogbook: (LogbookFilter) -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("QSOs", snapshot.totalQsos.toString(), action = { openLogbook(baseFilter) })
            Kpi("UNIQUE CALLS", snapshot.uniqueCalls.toString(), action = { openLogbook(baseFilter) })
            Kpi("ACTIVE DAYS", snapshot.activeDays.toString(), action = { openLogbook(baseFilter) })
            Kpi("DXCC WORKED / CONF", "${snapshot.dxcc.worked} / ${snapshot.dxcc.confirmed}", action = { openLogbook(logbookFilterForDimension("dxcc", "*", baseFilter)) })
            Kpi("COUNTRIES", snapshot.countries.toString(), action = { openLogbook(logbookFilterForDimension("country", "*", baseFilter)) })
            Kpi("CONTINENTS", snapshot.continents.size.toString(), action = { openLogbook(logbookFilterForDimension("continent", "*", baseFilter)) })
            Kpi("GRIDS WORKED / CONF", "${snapshot.grids} / ${snapshot.gridsConfirmed}", action = { openLogbook(logbookFilterForDimension("grid", "*", baseFilter)) })
            Kpi("CQ ZONES", snapshot.cqZones.size.toString(), action = { openLogbook(baseFilter.copy(cqZone = "*")) })
            Kpi("ITU ZONES", snapshot.ituZones.size.toString(), action = { openLogbook(baseFilter.copy(ituZone = "*")) })
            Kpi("STATES", snapshot.states.worked.toString(), action = { openLogbook(logbookFilterForDimension("state", "*", baseFilter)) })
            Kpi("BEST DX", snapshot.longestDistanceKm?.let { "%,.0f km".format(it) } ?: "UNKNOWN", action = { openLogbook(baseFilter.copy(distance = ">0", sort = LogbookSort.DISTANCE)) })
            Kpi("AVERAGE VALID DX", snapshot.averageDistanceKm?.let { "%,.0f km".format(it) } ?: "UNKNOWN", action = { openLogbook(baseFilter.copy(distance = ">0")) })
            Kpi("QRP QSOs / DXCC", "${snapshot.qrpQsos} / ${snapshot.qrpDxcc}", action = { openLogbook(baseFilter.copy(txPower = "1..5")) })
            Kpi("UNCONFIRMED DXCC", snapshot.unconfirmedDxccCount.toString(), snapshot.unconfirmedDxccCount > 0,
                { openLogbook(baseFilter.copy(dxcc = "*", confirmationSource = "UNCONFIRMED")) })
            Kpi("SYNC ATTENTION", snapshot.syncAttention.toString(), snapshot.syncAttention > 0,
                { openLogbook(baseFilter.copy(syncRelation = "ATTENTION")) })
        }
    }
    if (snapshot.syncAttention > 0) item { OutlinedButton(openSync, Modifier.fillMaxWidth()) { Text("OPEN SYNC HUB") } }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSO ACTIVITY · UTC", snapshot.activity, Modifier.weight(1f))
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSO ACTIVITY · UTC", snapshot.activity, Modifier.fillMaxWidth())
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
            }
        }
    }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val operatorRows = snapshot.operators.map { ProgressBucket(it.key, it.value) }
            val confirmationRows = snapshot.confirmations.map { ProgressBucket(it.key, it.value) }
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("OPERATORS", operatorRows, Modifier.weight(1f))
                ChartCard("CONFIRMATION SOURCES", confirmationRows, Modifier.weight(1f), "Award confirmation remains LoTW or paper QSL only")
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("OPERATORS", operatorRows, Modifier.fillMaxWidth())
                ChartCard("CONFIRMATION SOURCES", confirmationRows, Modifier.fillMaxWidth(), "Award confirmation remains LoTW or paper QSL only")
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
                HeatmapCard(snapshot.heatmap, Modifier.weight(1f))
                ChartCard("DISTANCE DISTRIBUTION", snapshot.distance, Modifier.weight(1f),
                    snapshot.coverage["Distance"]?.label.orEmpty())
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatmapCard(snapshot.heatmap, Modifier.fillMaxWidth())
                ChartCard("DISTANCE DISTRIBUTION", snapshot.distance, Modifier.fillMaxWidth(), snapshot.coverage["Distance"]?.label.orEmpty())
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
                ChartCard("QSOs BY YEAR", snapshot.years.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
                ChartCard("QSOs BY MONTH", snapshot.months.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("QSOs BY YEAR", snapshot.years.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
                ChartCard("QSOs BY MONTH", snapshot.months.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
            }
        }
    }
    item { ChartCard("RECENT DAILY ACTIVITY", snapshot.recentDays.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth()) }
    item {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (!compact && maxWidth > 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
                ChartCard("MODE FAMILY", snapshot.modes.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
                ChartCard("MODE / SUBMODE", snapshot.submodes.map { ProgressBucket(it.key, it.value) }, Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartCard("BAND DISTRIBUTION", snapshot.bands.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
                ChartCard("MODE FAMILY", snapshot.modes.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
                ChartCard("MODE / SUBMODE", snapshot.submodes.map { ProgressBucket(it.key, it.value) }, Modifier.fillMaxWidth())
            }
        }
    }
    item {
        var local by rememberSaveable { mutableStateOf(false) }
        ProgressCard("UTC HOUR × WEEKDAY") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!local, { local = false }, { Text("UTC") })
                FilterChip(local, { local = true }, { Text("Device local time") })
            }
            ActivityHeatmap(if (local) snapshot.localHeatmap else snapshot.heatmap, Modifier.fillMaxWidth().height(170.dp))
            Text(if (local) "Derived with the current device timezone." else "All logged QSO timestamps displayed in UTC.", color = ProgressMuted, fontSize = 11.sp)
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
        Text("SOTA LIVE · APPROVAL REQUIRED · NO REQUEST", color = ProgressMuted, fontSize = 11.sp)
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

@Composable private fun LocalEstimateBanner() {
    Surface(color = ProgressAmber.copy(alpha = .14f), shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, ProgressAmber.copy(alpha = .5f), RoundedCornerShape(8.dp))) {
        Text("LOCAL ESTIMATE · NOT OFFICIAL AWARD CREDIT", color = ProgressAmber,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
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
    Canvas(modifier) {
        val cellWidth = size.width / 24f
        val cellHeight = size.height / 7f
        rows.forEach { cell -> drawRoundRect(ProgressGreen.copy(alpha = .15f + .85f * cell.count / highest),
            androidx.compose.ui.geometry.Offset(cell.hour * cellWidth, cell.day * cellHeight),
            androidx.compose.ui.geometry.Size(cellWidth - 1, cellHeight - 1), CornerRadius(2f)) }
    }
}

@Composable private fun ProgressCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ProgressAmber, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable private fun ChartCard(title: String, rows: List<ProgressBucket>, modifier: Modifier, coverage: String = "") {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ProgressAmber)
            if (rows.none { it.count > 0 }) Text("No data in this scope.", color = ProgressMuted)
            else BarChart(rows.takeLast(18), Modifier.fillMaxWidth().height(150.dp))
            if (coverage.isNotBlank()) Text(coverage, color = ProgressMuted, fontSize = 11.sp)
        }
    }
}

@Composable private fun BarChart(rows: List<ProgressBucket>, modifier: Modifier) {
    val highest = max(1, rows.maxOfOrNull(ProgressBucket::count) ?: 1)
    Canvas(modifier) {
        val gap = 4.dp.toPx()
        val width = (size.width - gap * (rows.size - 1).coerceAtLeast(0)) / rows.size.coerceAtLeast(1)
        rows.forEachIndexed { index, row ->
            val height = size.height * row.count / highest
            drawRoundRect(ProgressBlue, androidx.compose.ui.geometry.Offset(index * (width + gap), size.height - height),
                androidx.compose.ui.geometry.Size(width, height), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable private fun HeatmapCard(rows: List<ProgressHeatCell>, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ProgressPanel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("HOUR × DAY · UTC", color = ProgressAmber)
            if (rows.isEmpty()) Text("No activity in this scope.", color = ProgressMuted)
            else {
                val highest = max(1, rows.maxOf(ProgressHeatCell::count))
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val cellWidth = size.width / 24f
                    val cellHeight = size.height / 7f
                    rows.forEach { cell ->
                        drawRoundRect(ProgressGreen.copy(alpha = .15f + .85f * cell.count / highest),
                            androidx.compose.ui.geometry.Offset(cell.hour * cellWidth, cell.day * cellHeight),
                            androidx.compose.ui.geometry.Size(cellWidth - 1, cellHeight - 1), CornerRadius(2f))
                    }
                }
            }
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
            value.setStyle(Style.Builder().fromJson(progressMapStyle())) { ready = true }
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
        Text("© CARTO · © OpenStreetMap contributors", color = ProgressMuted, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(ProgressPanel.copy(alpha=.85f)).padding(4.dp))
    }
}

private fun progressMapStyle() = """{"version":8,"name":"RigWeave Progress","sources":{"carto":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20},"labels":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_only_labels/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":20}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#091015"}},{"id":"carto","type":"raster","source":"carto"},{"id":"labels","type":"raster","source":"labels"}]}"""

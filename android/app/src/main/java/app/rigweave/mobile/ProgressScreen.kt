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
private enum class ProgressSection(val label: String) { OVERVIEW("OVERVIEW"), NEEDS("NEEDS"), AWARDS("AWARDS"), PORTABLE("PORTABLE") }

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
    openSync: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(ProgressSection.OVERVIEW) }
    var period by rememberSaveable { mutableStateOf(ProgressPeriod.ALL) }
    var allStations by rememberSaveable { mutableStateOf(false) }
    var selectedProfile by rememberSaveable { mutableStateOf(currentStationId) }
    var selectedCallsign by rememberSaveable { mutableStateOf(currentCallsign) }
    var band by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(ProgressMode.ALL) }
    var goalDialog by remember { mutableStateOf(false) }
    val filters = ProgressFilters(allStations, selectedProfile, selectedCallsign, period, band, mode)
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
                        Text("PROGRESS", color = ProgressInk, style = MaterialTheme.typography.headlineMedium)
                        Text(if (allStations) "ALL LOCAL DATA" else currentCallsign.ifBlank { "CURRENT STATION" },
                            color = ProgressMuted)
                    }
                    if (controller.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = ProgressAmber)
                    OutlinedButton({ goalDialog = true }, enabled = controller.goalStore.goals.size < 4) { Text("PIN GOAL") }
                }
            }
            item { LocalEstimateBanner() }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!allStations && selectedProfile == currentStationId && selectedCallsign == currentCallsign, {
                        allStations = false; selectedProfile = currentStationId; selectedCallsign = currentCallsign
                    }, { Text("Current station") })
                    FilterChip(allStations, { allStations = true; selectedProfile = ""; selectedCallsign = "" }, { Text("All local data") })
                    controller.stationProfiles.forEach { profile -> FilterChip(!allStations && selectedProfile == profile, {
                        allStations = false; selectedProfile = profile; selectedCallsign = ""
                    }, { Text("Profile $profile") }) }
                    controller.stationCallsigns.forEach { call -> FilterChip(!allStations && selectedProfile.isBlank() && selectedCallsign == call, {
                        allStations = false; selectedProfile = ""; selectedCallsign = call
                    }, { Text(call) }) }
                    ProgressPeriod.entries.forEach { value -> FilterChip(period == value, { period = value }, { Text(value.label) }) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("", "80m", "40m", "20m", "15m", "10m").forEach { value ->
                        FilterChip(band == value, { band = value }, { Text(value.ifBlank { "All bands" }) })
                    }
                    ProgressMode.entries.forEach { value -> FilterChip(mode == value, { mode = value }, { Text(value.label) }) }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProgressSection.entries.forEachIndexed { index, value ->
                        SegmentedButton(section == value, { section = value },
                            SegmentedButtonDefaults.itemShape(index, ProgressSection.entries.size)) { Text(value.label, fontSize = if (compact) 10.sp else 12.sp) }
                    }
                }
            }
            if (snapshot.goals.isNotEmpty()) item { GoalsCard(snapshot.goals, controller) }
            when (section) {
                ProgressSection.OVERVIEW -> overviewItems(snapshot, compact, openSync, openLogbook)
                ProgressSection.NEEDS -> needsItems(snapshot, openDx, openPortable, openLogbook)
                ProgressSection.AWARDS -> awardsItems(snapshot)
                ProgressSection.PORTABLE -> portableItems(snapshot, portable, openPortable)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (goalDialog) GoalDialog(controller, { goalDialog = false })
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(
    snapshot: ProgressSnapshot, compact: Boolean, openSync: () -> Unit, openLogbook: () -> Unit,
) {
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("QSOs", snapshot.totalQsos.toString())
            Kpi("CALLS", snapshot.uniqueCalls.toString())
            Kpi("DXCC-STYLE", "${snapshot.dxcc.worked} / ${snapshot.dxcc.confirmed}")
            Kpi("COUNTRIES", snapshot.countries.toString())
            Kpi("GRIDS", snapshot.grids.toString())
            Kpi("LONGEST", snapshot.longestDistanceKm?.let { "%,.0f km".format(it) } ?: "UNKNOWN")
            Kpi("QRP QSOs", snapshot.qrpQsos.toString())
            Kpi("SYNC ATTENTION", snapshot.syncAttention.toString(), snapshot.syncAttention > 0, openSync)
        }
    }
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
    item { OutlinedButton(openLogbook, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("OPEN FILTERED LOGBOOK") } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.needsItems(
    snapshot: ProgressSnapshot, openDx: () -> Unit, openPortable: () -> Unit, openLogbook: () -> Unit,
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
            Text("$dxcc · ${qsos.size} QSO${if (qsos.size == 1) "" else "s"} · ${qsos.map { it.band }.filter(String::isNotBlank).distinct().joinToString()}",
                color = ProgressInk)
            if (digital.isNotEmpty()) Text(digital.joinToString(" · "), color = ProgressBlue, fontSize = 11.sp)
        }
        OutlinedButton(openLogbook, Modifier.fillMaxWidth()) { Text("OPEN LOGBOOK") }
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

private fun androidx.compose.foundation.lazy.LazyListScope.awardsItems(snapshot: ProgressSnapshot) {
    item { EstimateCard("DXCC-STYLE LOCAL ESTIMATE", snapshot.dxcc, 100) {
        CountRows(snapshot.dxccByMode, "mode")
        Text("5-band matrix · 80 / 40 / 20 / 15 / 10 m", color = ProgressMuted)
        CountRows(snapshot.dxccByBand.filterKeys { it in setOf("80m","40m","20m","15m","10m") }, "band")
    } }
    item { EstimateCard("WAS-STYLE LOCAL ESTIMATE", snapshot.states, 50) {
        Text(canonicalUsStates.sorted().chunked(10).joinToString("\n") { row -> row.joinToString("  ") },
            color = ProgressMuted, fontSize = 12.sp)
        Text(snapshot.coverage["U.S. state"]?.label.orEmpty(), color = ProgressMuted)
    } }
    item { EstimateCard("WAZ-STYLE LOCAL ESTIMATE", snapshot.zones, 40) {
        Text((1..40).chunked(10).joinToString("\n") { row -> row.joinToString("  ") }, color = ProgressMuted, fontSize = 12.sp)
        Text(snapshot.coverage["CQ zone"]?.label.orEmpty(), color = ProgressMuted)
    } }
    item { ProgressCard("QRP DXCC-STYLE LOCAL ESTIMATE") {
        Text("${snapshot.qrpDxcc} / 100 unique DXCC identifiers at known power ≤ 5 W", color = ProgressInk)
        LinearProgressIndicator({ (snapshot.qrpDxcc / 100f).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = ProgressGreen)
        Text(snapshot.coverage["TX power"]?.label.orEmpty(), color = ProgressMuted)
    } }
    item { ProgressCard("POTA LOCAL MILESTONE PREVIEW") {
        Text("${snapshot.portable.potaHunted.size} hunted · ${snapshot.portable.potaActivated.size} activated", color = ProgressInk)
        Text("Next standard unique-park milestone · ${snapshot.portable.nextPotaMilestone}", color = ProgressAmber)
        Text("${snapshot.portable.p2pQsos} P2P QSOs · verify official credit in POTA", color = ProgressMuted)
    } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.portableItems(
    snapshot: ProgressSnapshot, portable: PortableController, openPortable: () -> Unit,
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

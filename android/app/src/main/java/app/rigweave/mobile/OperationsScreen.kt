package app.rigweave.mobile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OpsAmber = Color(0xFFE9A72B)
private val OpsPanel = Color(0xFF1B2228)
private val OpsInk = Color(0xFFF4F0E7)
private val OpsMuted = Color(0xFFA5ADB2)

@Composable
internal fun OperationsScreen(
    controller: OperationsController,
    portable: PortableController,
    activation: PotaActivationController,
    features: FeatureController,
    progress: ProgressController,
    mutations: QsoMutationCoordinator,
    wavelog: WavelogController,
    callbook: CallbookController,
    cty: CtyController,
    app: AppController,
    compact: Boolean,
    openDx: () -> Unit,
    openPortable: () -> Unit,
    openLogbook: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(controller.section) }
    LaunchedEffect(controller.section) { selected = controller.section }
    LaunchedEffect(Unit) { while (true) { delay(30 * 60_000L); controller.refresh(false) } }
    Column(Modifier.fillMaxSize().background(Color(0xFF111519)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("OPERATIONS", color = OpsInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text("CALENDAR · CONTESTS · ACTIVATION PLANNING", color = OpsMuted) }
            OutlinedButton({ controller.refresh(true) }, enabled = !controller.refreshing) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(5.dp)); Text(if (controller.refreshing) "REFRESHING" else "REFRESH")
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("DX CALENDAR", "CONTESTS", "ACTIVATION PLANNER").forEach { label ->
                FilterChip(selected == label, { selected = label; controller.openSection(label) }, { Text(label) })
            }
        }
        when (selected) {
            "CONTESTS" -> ContestOperations(controller, progress, mutations, wavelog, callbook, app, openLogbook)
            "ACTIVATION PLANNER" -> ActivationPlanner(controller, portable, activation, app, openPortable)
            else -> DxOperations(controller, features, progress, cty, openDx, openLogbook)
        }
    }
}

@Composable private fun ProviderStrip(metadata: OperationsCacheMetadata) {
    Surface(color = OpsPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip({}, { Text(metadata.state.label) }, enabled = false)
            Text(metadata.source, color = OpsInk, modifier = Modifier.weight(1f))
            Text(if (metadata.lastSuccessEpoch > 0) "Saved ${localTime(metadata.lastSuccessEpoch)}" else "No successful fetch", color = OpsMuted)
        }
    }
    if (metadata.error.isNotBlank()) Text("Last refresh: ${metadata.error}. Last-good data remains visible.", color = OpsAmber)
}

@Composable private fun DxOperations(controller: OperationsController, features: FeatureController, progress: ProgressController,
    cty: CtyController, openDx: () -> Unit, openLogbook: () -> Unit) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf(controller.focusDxCall) }
    LaunchedEffect(controller.focusDxCall) { if (controller.focusDxCall.isNotBlank()) { search = controller.focusDxCall; controller.clearFocus() } }
    var group by rememberSaveable { mutableStateOf("ALL") }
    val now = Instant.now().epochSecond
    val rows = controller.dxItems.filter { item ->
        val matches = search.isBlank() || listOf(item.callsign, item.entity, item.dateText, item.status).any { it.contains(search, true) }
        matches && (group == "ALL" || dxGroup(item, now) == group)
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ProviderStrip(controller.dxMetadata) }
        item { OutlinedTextField(search, { search = it }, label = { Text("Call, entity, date or status") }, modifier = Modifier.fillMaxWidth()) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ALL", "ACTIVE NOW", "STARTING SOON", "UPCOMING", "RECENTLY ENDED").forEach { value ->
                FilterChip(group == value, { group = value }, { Text(value) })
            }
        } }
        if (rows.isEmpty()) item { EmptyOperations("No DX calendar entries match. Provider state is shown above.") }
        items(rows, key = DxCalendarItem::id) { item ->
            val entity = cty.lookup(item.callsign)
            val exact = progress.qsoSnapshot.filter { it.callsign.equals(item.callsign, true) }
            val dxcc = entity?.dxcc.orEmpty()
            val entityRows = progress.qsoSnapshot.filter { dxcc.isNotBlank() && it.dxcc == dxcc }
            val live = features.liveSpots.firstOrNull { it.callsign.equals(item.callsign, true) }
            val needs = progress.snapshot.needs.firstOrNull { it.dxSpot?.callsign.equals(item.callsign, true) }?.reasons.orEmpty()
            Surface(color = OpsPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, OpsAmber.copy(.25f), RoundedCornerShape(8.dp))) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row { Text(item.callsign, color = OpsAmber, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text(dxGroup(item, now), color = OpsInk) }
                    Text(listOf(item.entity.ifBlank { entity?.country.orEmpty() }, item.dateText, item.modes.joinToString(), item.bands.joinToString()).filter(String::isNotBlank).joinToString(" · "), color = OpsInk)
                    Text("LOCAL HISTORY · exact ${exact.size} · entity ${entityRows.size} · DXCC ${dxcc.ifBlank { "unresolved" }}", color = OpsMuted)
                    if (needs.isNotEmpty()) Text("NEEDS · ${needs.joinToString()}", color = OpsAmber)
                    Text("${item.provider} · ${item.status}${item.qsl.takeIf(String::isNotBlank)?.let { " · QSL $it" }.orEmpty()}", color = OpsMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton({ progress.requestLogbook(logbookFilterForDimension("callsign", item.callsign)); openLogbook() }) { Text("LOGBOOK") }
                        TextButton({ features.setWatchlist((features.watchlistText.lineSequence().toList() + item.callsign).joinToString("\n")) }) { Text("WATCH IN DX") }
                        TextButton({ copyText(context, "DX callsign", item.callsign) }) { Text("COPY CALL") }
                        if (item.sourceUrl.startsWith("https://")) TextButton({ openUrl(context, item.sourceUrl) }) { Text("SOURCE") }
                        if (live != null) TextButton(openDx) { Text("LIVE SPOT") }
                    }
                }
            }
        }
    }
}

private fun dxGroup(item: DxCalendarItem, now: Long): String = when {
    item.endEpoch != null && item.endEpoch < now -> "RECENTLY ENDED"
    item.startEpoch != null && item.endEpoch != null && now in item.startEpoch..item.endEpoch -> "ACTIVE NOW"
    item.startEpoch != null && item.startEpoch - now <= 3 * 86_400 -> "STARTING SOON"
    else -> "UPCOMING"
}

@Composable private fun ContestOperations(controller: OperationsController, progress: ProgressController, mutations: QsoMutationCoordinator,
    wavelog: WavelogController, callbook: CallbookController, app: AppController, openLogbook: () -> Unit) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }; var mode by rememberSaveable { mutableStateOf("ALL") }
    var group by rememberSaveable { mutableStateOf("ALL") }; var utc by rememberSaveable { mutableStateOf(false) }
    var fastDraft by remember { mutableStateOf<String?>(null) }
    val now = Instant.now().epochSecond
    val rows = controller.contestItems.filter { row ->
        (search.isBlank() || row.name.contains(search, true)) && (mode == "ALL" || row.mode == mode) &&
            (group == "ALL" || contestGroup(row, now) == group)
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ProviderStrip(controller.contestMetadata) }
        item { OutlinedTextField(search, { search = it }, label = { Text("Search contests") }, modifier = Modifier.fillMaxWidth()) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ALL", "ACTIVE NOW", "TODAY", "THIS WEEKEND", "NEXT 7 DAYS", "LATER").forEach { value -> FilterChip(group == value, { group = value }, { Text(value) }) }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (listOf("ALL") + controller.contestItems.map(ContestCalendarItem::mode).distinct()).forEach { value -> FilterChip(mode == value, { mode = value }, { Text(value) }) }
            FilterChip(utc, { utc = !utc }, { Text(if (utc) "UTC" else "LOCAL") })
        } }
        if (rows.isEmpty()) item { EmptyOperations("No contests match. Malformed and expired provider rows are skipped.") }
        items(rows, key = ContestCalendarItem::id) { item ->
            val qsoCount = item.contestId.takeIf(String::isNotBlank)?.let { id -> progress.qsoSnapshot.count { it.contestId.equals(id, true) } }
            Surface(color = OpsPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row { Text(item.name, color = OpsAmber, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text(contestGroup(item, now), color = OpsInk) }
                Text("${formatContestTime(item.startEpoch, utc)} → ${formatContestTime(item.endEpoch, utc)} · ${item.mode}", color = OpsInk)
                Text("${item.provider} · ${((item.endEpoch - item.startEpoch) / 3600.0).let { "%.1f h".format(Locale.US, it) }}" +
                    (item.contestId.takeIf(String::isNotBlank)?.let { " · ADIF $it · local QSOs ${qsoCount ?: 0}" } ?: " · ADIF contest ID not deterministically known"), color = OpsMuted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.sourceUrl.startsWith("https://")) TextButton({ openUrl(context, item.sourceUrl) }) { Text("RULES") }
                    TextButton({ copyText(context, "Contest", "${item.name}\n${formatContestTime(item.startEpoch, true)}–${formatContestTime(item.endEpoch, true)} UTC") }) { Text("COPY") }
                    TextButton({ shareContestIcs(context, item) }) { Text("SHARE ICS") }
                    TextButton({ fastDraft = "DATE ${Instant.ofEpochSecond(item.startEpoch).atZone(ZoneOffset.UTC).toLocalDate()}\n" +
                        item.contestId.takeIf(String::isNotBlank)?.let { "<CONTEST_ID:$it>\n" }.orEmpty() + "# ${item.name}\n" }) { Text("FAST ENTRY") }
                    if (item.contestId.isNotBlank()) TextButton({
                        progress.requestLogbook(logbookFilterForDimension("contest", item.contestId)); openLogbook()
                    }) { Text("LOGBOOK") }
                }
            } }
        }
    }
    fastDraft?.let { initial -> FastEntryDialog(mutations, wavelog, callbook, app.stationCallsign,
        { _, _ -> controller.refresh(false) }, initialDraft = initial) { fastDraft = null } }
}

@Composable private fun ActivationPlanner(controller: OperationsController, portable: PortableController,
    activation: PotaActivationController, app: AppController, openPortable: () -> Unit) {
    val context = LocalContext.current
    var grid by rememberSaveable { mutableStateOf(app.stationGrid.ifBlank { "JN88TQ" }) }
    var point by remember { mutableStateOf(maidenheadCenter(grid) ?: GeoPoint(48.6875, 16.625)) }
    var radius by rememberSaveable { mutableStateOf(100.0) }
    var program by rememberSaveable { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<ActivationPlan?>(null) }
    var delete by remember { mutableStateOf<ActivationPlan?>(null) }
    var cq by rememberSaveable { mutableStateOf(false) }; var itu by rememberSaveable { mutableStateOf(false) }; var states by rememberSaveable { mutableStateOf(false) }
    var pota by rememberSaveable { mutableStateOf(true) }; var sota by rememberSaveable { mutableStateOf(true) }; var wwff by rememberSaveable { mutableStateOf(true) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lastKnownPoint(context)?.let { selected -> point = selected; grid = maidenheadGrid(selected.latitude, selected.longitude) }
    }
    LaunchedEffect(grid) { maidenheadCenter(grid)?.let { point = it }; portable.pota.searchParks("", stationGrid = grid, nearby = true); portable.sotaCatalogue.search("", stationGrid = grid, nearby = true) }
    val nearbyPota = portable.pota.parkResults.filter { (it.distanceKm ?: Double.MAX_VALUE) <= radius }
    val nearbySota = portable.sotaCatalogue.results.filter { (it.distanceKm ?: Double.MAX_VALUE) <= radius }
    val nearbyWwff = portable.recentWwff("").mapNotNull { ref ->
        val p = if (ref.latitude != null && ref.longitude != null) GeoPoint(ref.latitude, ref.longitude) else null
        p?.let { ref to distanceKm(point, it) }
    }.filter { it.second <= radius }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(grid, { grid = it.uppercase(Locale.US).take(8) }, label = { Text("Grid") }, modifier = Modifier.weight(1f))
            OutlinedButton({
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    lastKnownPoint(context)?.let { point = it; grid = maidenheadGrid(it.latitude, it.longitude) }
                else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }, modifier = Modifier.heightIn(min = 56.dp)) { Icon(Icons.Outlined.MyLocation, null); Text(" USE LOCATION") }
        } }
        item { WorldPlanningMap(point, grid) { selected -> point = selected; grid = maidenheadGrid(selected.latitude, selected.longitude) } }
        item { Text("${"%.5f".format(point.latitude)}, ${"%.5f".format(point.longitude)} · ${distanceBearing(app.stationGrid, point)}", color = OpsInk) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("ALL", "POTA", "SOTA", "WWFF").forEach { value -> FilterChip(program == value, { program = value }, { Text(value) }) }
            listOf(25.0, 50.0, 100.0, 250.0).forEach { value -> FilterChip(radius == value, { radius = value }, { Text("${value.toInt()} km") }) }
        } }
        item { Text("OVERLAYS", color = OpsAmber, fontWeight = FontWeight.Black); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(cq, { cq = !cq }, { Text("CQ ZONES") }); FilterChip(itu, { itu = !itu }, { Text("ITU ZONES") }); FilterChip(states, { states = !states }, { Text("STATES") })
            FilterChip(pota, { pota = !pota }, { Text("POTA") }); FilterChip(sota, { sota = !sota }, { Text("SOTA") }); FilterChip(wwff, { wwff = !wwff }, { Text("WWFF") })
        }; if (cq || itu || states) Text("CQ/ITU/state boundary polygons are not packaged in this Android build; toggles are retained but no boundary is fabricated.", color = OpsMuted) }
        item { Text("NEARBY REFERENCES", color = OpsAmber, fontWeight = FontWeight.Black) }
        if ((program == "ALL" || program == "POTA") && pota) items(nearbyPota.take(20), key = { "P${it.reference}" }) { row -> ReferenceRow("POTA", row.reference, row.name, row.distanceKm, row.bearingDegrees, if (row.active) "ACTIVE CATALOGUE" else "RETIRED") { editing = planFor("POTA", row.reference, row.name, row.grid.ifBlank { grid }, row.latitude ?: point.latitude, row.longitude ?: point.longitude) } }
        if ((program == "ALL" || program == "SOTA") && sota) items(nearbySota.take(20), key = { "S${it.code}" }) { row -> ReferenceRow("SOTA", row.code, row.name, row.distanceKm, row.bearingDegrees, if (row.active) "ACTIVE CATALOGUE" else "EXPIRED") { editing = planFor("SOTA", row.code, row.name, row.grid.ifBlank { grid }, row.latitude ?: point.latitude, row.longitude ?: point.longitude) } }
        if ((program == "ALL" || program == "WWFF") && wwff) items(nearbyWwff.take(20), key = { "W${it.first.code}" }) { (row, distance) -> ReferenceRow("WWFF", row.code, row.name, distance, initialBearingDegrees(point, GeoPoint(row.latitude!!, row.longitude!!)), "RECENT SPOT/AGENDA CACHE") { editing = planFor("WWFF", row.code, row.name, row.grid.ifBlank { grid }, row.latitude!!, row.longitude!!) } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("SAVED PLANS", color = OpsAmber, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Button({ editing = planFor("GENERAL", "", "New activation", grid, point.latitude, point.longitude) }) { Text("NEW PLAN") } } }
        if (controller.plans.isEmpty()) item { EmptyOperations("No saved activation plans. Plans are local and survive app upgrades.") }
        items(controller.plans, key = ActivationPlan::id) { plan -> Surface(color = OpsPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
            Text(plan.title, color = OpsInk, fontWeight = FontWeight.Bold); Text(activationPlanSummary(plan), color = OpsMuted)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton({ editing = plan }) { Text("EDIT") }; TextButton({ controller.duplicate(plan) }) { Text("DUPLICATE") }
                TextButton({ sharePlanIcs(context, plan) }) { Text("SHARE") }; TextButton({ copyText(context, "Activation plan", activationPlanSummary(plan)) }) { Text("COPY") }
                TextButton({ delete = plan }) { Text("DELETE") }
                TextButton({
                    if (plan.program == "POTA") activation.preparePlan(potaSetupForActivationPlan(plan, app.stationCallsign))
                    openPortable()
                }) { Text(if (plan.program == "POTA") "PREPARE POTA" else "OPEN PORTABLE") }
            }
        } } }
    }
    editing?.let { plan -> PlanEditor(plan, { controller.save(it); editing = null }, { editing = null }) }
    delete?.let { plan -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete activation plan?") }, text = { Text(plan.title) },
        confirmButton = { Button({ controller.delete(plan.id); delete = null }) { Text("DELETE") } }, dismissButton = { TextButton({ delete = null }) { Text("CANCEL") } }) }
}

@Composable private fun WorldPlanningMap(point: GeoPoint, grid: String, select: (GeoPoint) -> Unit) {
    Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF15262B), RoundedCornerShape(8.dp)).border(1.dp, OpsAmber.copy(.45f), RoundedCornerShape(8.dp))
        .pointerInput(Unit) { detectTapGestures { tap -> select(GeoPoint(90.0 - tap.y / size.height * 180.0, tap.x / size.width * 360.0 - 180.0)) } }) {
        for (lon in -120..120 step 60) drawLine(Color(0xFF31515A), Offset(((lon + 180) / 360f) * size.width, 0f), Offset(((lon + 180) / 360f) * size.width, size.height))
        for (lat in -60..60 step 30) drawLine(Color(0xFF31515A), Offset(0f, ((90 - lat) / 180f) * size.height), Offset(size.width, ((90 - lat) / 180f) * size.height))
        maidenheadCell(grid)?.let { cell ->
            val left = ((cell.west + 180) / 360f * size.width).toFloat(); val right = ((cell.east + 180) / 360f * size.width).toFloat()
            val top = ((90 - cell.north) / 180f * size.height).toFloat(); val bottom = ((90 - cell.south) / 180f * size.height).toFloat()
            drawRect(OpsAmber, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top), style = Stroke(3f))
        }
        drawCircle(Color(0xFFE4544D), 7f, Offset(((point.longitude + 180) / 360f * size.width).toFloat(), ((90 - point.latitude) / 180f * size.height).toFloat()))
    }
}

@Composable private fun ReferenceRow(program: String, code: String, name: String, distance: Double?, bearing: Int?, status: String, add: () -> Unit) {
    ListItem(headlineContent = { Text("$program · $code · $name", fontWeight = FontWeight.Bold) }, supportingContent = { Text("${distance?.let { "%.0f km".format(it) } ?: "distance unknown"} · ${bearing?.let { "$it°" } ?: "bearing unknown"} · $status") },
        trailingContent = { TextButton(add) { Text("PLAN") } }, colors = ListItemDefaults.colors(containerColor = OpsPanel))
}

@Composable private fun PlanEditor(initial: ActivationPlan, save: (ActivationPlan) -> Unit, dismiss: () -> Unit) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }; var refs by remember(initial.id) { mutableStateOf(initial.references.joinToString(", ")) }
    var grid by remember(initial.id) { mutableStateOf(initial.grid) }; var start by remember(initial.id) { mutableStateOf(Instant.ofEpochSecond(initial.startEpoch).toString()) }
    var duration by remember(initial.id) { mutableStateOf(initial.durationMinutes.toString()) }; var notes by remember(initial.id) { mutableStateOf(initial.notes) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Activation plan") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(refs, { refs = it.uppercase(Locale.US) }, label = { Text("References") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(grid, { grid = it.uppercase(Locale.US) }, label = { Text("Grid") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(start, { start = it }, label = { Text("Start UTC ISO-8601") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duration minutes") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()) }
    } }, confirmButton = { Button({ save(initial.copy(title = title.trim(), references = refs.split(',', ' ', '\n').filter(String::isNotBlank), grid = grid,
        startEpoch = runCatching { Instant.parse(start).epochSecond }.getOrDefault(initial.startEpoch), durationMinutes = duration.toIntOrNull()?.coerceIn(15, 1440) ?: 120, notes = notes)) }, enabled = title.isNotBlank() && maidenheadCell(grid) != null && runCatching { Instant.parse(start) }.isSuccess) { Text("SAVE") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}

@Composable private fun EmptyOperations(text: String) { Surface(color = OpsPanel, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text(text, color = OpsMuted, modifier = Modifier.padding(14.dp)) } }

private fun planFor(program: String, ref: String, name: String, grid: String, lat: Double, lon: Double) = ActivationPlan(
    title = name.ifBlank { "$program activation" }, program = program, references = listOf(ref).filter(String::isNotBlank), grid = grid,
    latitude = lat, longitude = lon, startEpoch = Instant.now().epochSecond + 3600)

private fun localTime(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
private fun formatContestTime(epoch: Long, utc: Boolean) = Instant.ofEpochSecond(epoch).atZone(if (utc) ZoneOffset.UTC else ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE dd MMM HH:mm"))
private fun distanceBearing(stationGrid: String, target: GeoPoint): String = maidenheadCenter(stationGrid)?.let { "%.0f km · %03d° from %s".format(distanceKm(it, target), initialBearingDegrees(it, target), stationGrid) } ?: "station grid unavailable"

private fun copyText(context: Context, label: String, value: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value)) }
private fun openUrl(context: Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

private fun shareContestIcs(context: Context, item: ContestCalendarItem) {
    val plan = ActivationPlan(id = "contest-${item.id}", title = item.name, program = "CONTEST", grid = "AA00AA", latitude = 0.0, longitude = 0.0,
        startEpoch = item.startEpoch, durationMinutes = ((item.endEpoch - item.startEpoch) / 60).toInt().coerceAtLeast(1), notes = item.sourceUrl)
    sharePlanIcs(context, plan)
}

private fun sharePlanIcs(context: Context, plan: ActivationPlan) {
    runCatching {
        val dir = File(context.cacheDir, "operations-exports").apply(File::mkdirs)
        val file = File(dir, plan.title.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "rigweave-plan" } + ".ics")
        file.writeText(activationPlanIcs(plan))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/calendar"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share calendar file"))
    }
}

private fun lastKnownPoint(context: Context): GeoPoint? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java)
    return manager.getProviders(true).mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }?.let { GeoPoint(it.latitude, it.longitude) }
}

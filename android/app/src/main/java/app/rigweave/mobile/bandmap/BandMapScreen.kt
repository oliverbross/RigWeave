// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile.bandmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rigweave.mobile.CtyController
import app.rigweave.mobile.AppController
import app.rigweave.mobile.SPOT_STATUS_CS
import app.rigweave.mobile.SPOT_STATUS_DS
import app.rigweave.mobile.SpotLogIdentity
import app.rigweave.mobile.SpotLogStatus
import app.rigweave.mobile.FeatureController
import app.rigweave.mobile.NeuralDxController
import app.rigweave.mobile.OperatingContextSnapshot
import app.rigweave.mobile.PortableController
import app.rigweave.mobile.QsoDatabase
import app.rigweave.mobile.WorkspaceAction
import app.rigweave.mobile.WorkspaceDestination
import app.rigweave.mobile.ContestRuntime
import app.rigweave.mobile.DxChaserRuntime
import app.rigweave.mobile.toPortable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.abs

private val MapBackground = Color(0xFF10171C)
private val MapPanel = Color(0xFF1A242B)
private val MapGrid = Color(0xFF51606A)
private val MapText = Color(0xFFF4F0E7)
private val MapMuted = Color(0xFFAAB5BC)
private val MapCyan = Color(0xFF4BC3D5)
private val MapAmber = Color(0xFFF0B33C)
private val MapGreen = Color(0xFF52CB82)
private val MapMagenta = Color(0xFFE77CB6)

@Composable
internal fun BandMapScreen(
    controller: BandMapController,
    database: QsoDatabase,
    features: FeatureController,
    neuralDx: NeuralDxController,
    portable: PortableController,
    cty: CtyController,
    contest: ContestRuntime,
    chaser: DxChaserRuntime,
    operatingContext: OperatingContextSnapshot,
    keyer: BandMapKeyerContext,
    app: AppController,
    onAction: (WorkspaceAction) -> Unit,
) {
    val databaseRevision = database.changeToken()
    val needs by produceState(BandMapNeedsSnapshot(), operatingContext.stationProfileId.value,
        operatingContext.stationCallsign.value, databaseRevision) {
        value = withContext(Dispatchers.IO) { database.bandMapNeedsSnapshot(operatingContext.stationProfileId.value, operatingContext.stationCallsign.value) }
    }
    val observations = remember(features.liveSpots, features.rbnObservations, neuralDx.mySignal.reports,
        neuralDx.wsprPersonal.reports, portable.pota.spots, portable.sotaSpots, portable.wwffSpots) {
        BandMapSourceAdapters.cluster(features.liveSpots) + BandMapSourceAdapters.rbn(features.rbnObservations) +
            BandMapSourceAdapters.signal(neuralDx.mySignal.reports, false) + BandMapSourceAdapters.signal(neuralDx.wsprPersonal.reports, true) +
            BandMapSourceAdapters.portable(portable.pota.spots.map { it.toPortable() } + portable.sotaSpots + portable.wwffSpots)
    }
    val contestSnapshot = contest.snapshot()
    val chaserSnapshot = chaser.snapshot
    LaunchedEffect(observations, needs, operatingContext.generation, contestSnapshot, chaserSnapshot, keyer) {
        controller.submit(BandMapInputs(observations, operatingContext, needs, contestSnapshot, contest::opportunity,
            chaserSnapshot, keyer, cty::lookup,
            providerHealth = mapOf(
                BandMapSource.DX_CLUSTER to (features.clusterConnection.state.name == "CONNECTED"),
                BandMapSource.RBN to (features.rbnSourceSnapshot.state.name == "CURRENT"),
                BandMapSource.PSK_REPORTER to neuralDx.mySignal.available,
                BandMapSource.PERSONAL_WSPR to neuralDx.wsprPersonal.reports.isNotEmpty(),
                BandMapSource.POTA to portable.pota.spots.isNotEmpty(),
                BandMapSource.SOTA to portable.sotaSpots.isNotEmpty(),
                BandMapSource.WWFF to portable.wwffSpots.isNotEmpty(),
            )))
    }
    val snapshot = controller.snapshot
    val statuses by produceState<Map<String, SpotLogStatus>>(emptyMap(), snapshot.generation,
        operatingContext.stationProfileId.value, cty.dataRevision) {
        val identities = snapshot.rankedSpots.map { ranked ->
            val entity = cty.lookup(ranked.spot.callsign)
            SpotLogIdentity(ranked.spot.id, ranked.spot.callsign, entity?.dxcc.orEmpty(), entity?.country.orEmpty(),
                ranked.spot.band, ranked.spot.modeFamily.name)
        }
        value = withContext(Dispatchers.IO) { database.spotStatuses(identities, operatingContext.stationProfileId.value) }
    }
    var selected by remember(snapshot.selectedSpotId, snapshot.rankedSpots) {
        mutableStateOf(snapshot.rankedSpots.firstOrNull { it.spot.id == snapshot.selectedSpotId })
    }
    var filterOpen by remember { mutableStateOf(false) }
    val keyboardModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key in setOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)) return@onPreviewKeyEvent false
        val rows = snapshot.rankedSpots
        val index = rows.indexOfFirst { it.spot.id == selected?.spot?.id }.let { if (it < 0) 0 else it }
        when (event.key) {
            Key.DirectionDown, Key.N -> rows.getOrNull((index + 1).coerceAtMost(rows.lastIndex))?.let { selected = it; controller.select(it.spot.id) }
            Key.DirectionUp, Key.P -> rows.getOrNull((index - 1).coerceAtLeast(0))?.let { selected = it; controller.select(it.spot.id) }
            Key.F -> filterOpen = true
            Key.Plus, Key.Equals -> snapshot.selectedBands.firstOrNull()?.let { controller.zoom(it, .5) }
            Key.Minus -> snapshot.selectedBands.firstOrNull()?.let { controller.zoom(it, 2.0) }
            Key.Escape -> { selected = null; controller.select(null); filterOpen = false }
            else -> return@onPreviewKeyEvent false
        }
        true
    }
    Column(keyboardModifier.fillMaxSize().focusable().background(MapBackground).padding(10.dp).testTag("band-map-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BandMapHeader(snapshot, keyer, { filterOpen = true })
        PresetAndLayoutControls(controller)
        BandControls(controller)
        if (snapshot.unavailableReasons.isNotEmpty()) Text(snapshot.unavailableReasons.joinToString(" · "), color = MapAmber, fontSize = 12.sp)
        Box(Modifier.weight(1f)) {
            when (snapshot.layout) {
                BandMapLayoutMode.GRID_OVERVIEW -> BandGrid(snapshot, controller, statuses, app) { selected = it }
                BandMapLayoutMode.MULTI_HORIZONTAL -> VerticalBandColumns(snapshot, controller, statuses, app) { selected = it }
                BandMapLayoutMode.SINGLE_EXPANDED -> SingleExpanded(snapshot, controller, statuses, app, operatingContext) { selected = it }
                BandMapLayoutMode.MULTI_VERTICAL -> HorizontalBandRows(snapshot, controller, statuses, app) { selected = it }
            }
        }
    }
    selected?.let { ranked -> SpotDetail(ranked, statuses[ranked.spot.id], app, snapshot.contextGeneration, controller, onAction, { controller.toggleMark(ranked.spot, it) }) { selected = null; controller.select(null) } }
    if (filterOpen) FilterDialog(controller) { filterOpen = false }
}

@Composable private fun BandMapHeader(snapshot: BandMapUiSnapshot, keyer: BandMapKeyerContext, openFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("INTELLIGENT BAND MAPS", color = MapText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("${snapshot.rankedSpots.size} visible · ${snapshot.diagnostic.sourceObservations} observations · ${snapshot.diagnostic.rebuildMillis} ms",
                color = MapMuted, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("KEYER ${if (keyer.availability.available) "AVAILABLE" else "READ-ONLY"} · ${keyer.queue.state}", color = MapCyan, fontSize = 11.sp,
                modifier = Modifier.semantics { contentDescription = "Keyer read only status ${keyer.availability.available}, queue ${keyer.queue.state}" })
            OutlinedButton(openFilters) { Text("FILTERS") }
        }
    }
}

@Composable private fun PresetAndLayoutControls(controller: BandMapController) {
    val settings = controller.settings
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        settings.presets.forEach { preset -> FilterChip(settings.activePresetId == preset.id,
            { controller.updateSettings { it.copy(activePresetId = preset.id, selectedLayout = preset.layout) } }, { Text(preset.label) }) }
        Spacer(Modifier.width(8.dp))
        BandMapLayoutMode.entries.forEach { layout -> FilterChip(settings.selectedLayout == layout,
            { controller.updateSettings { it.copy(selectedLayout = layout) } }, { Text(layout.name.replace('_', ' ')) }) }
    }
}

@Composable private fun BandControls(controller: BandMapController) {
    val selected = controller.settings.selectedBands
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            selected.forEachIndexed { index, band -> OutlinedButton({ if (index > 0) controller.updateSettings { current ->
                val reordered = current.selectedBands.toMutableList(); val value = reordered.removeAt(index); reordered.add(index - 1, value); current.copy(selectedBands = reordered)
            } }, enabled = index > 0) { Text("← $band") } }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            bandMapVisibleBands.map { name -> bandMapBands.first { it.name == name } }.forEach { band -> FilterChip(band.name in selected, {
            controller.updateSettings { current ->
                val next = if (band.name in current.selectedBands) current.selectedBands - band.name else current.selectedBands + band.name
                current.copy(selectedBands = next.ifEmpty { current.selectedBands })
            }
            }, { Text(band.name) }) }
        }
    }
}

@Composable private fun HorizontalBandRows(snapshot: BandMapUiSnapshot, controller: BandMapController, statuses: Map<String, SpotLogStatus>, app: AppController, select: (BandMapRankedSpot) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(snapshot.selectedBands, key = { it }) { band -> HorizontalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, statuses, app, select) }
    }
}

@Composable private fun VerticalBandColumns(snapshot: BandMapUiSnapshot, controller: BandMapController, statuses: Map<String, SpotLogStatus>, app: AppController, select: (BandMapRankedSpot) -> Unit) {
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        snapshot.selectedBands.forEach { band -> VerticalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, statuses, app, select) }
    }
}

@Composable private fun BandGrid(snapshot: BandMapUiSnapshot, controller: BandMapController, statuses: Map<String, SpotLogStatus>, app: AppController, select: (BandMapRankedSpot) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(snapshot.selectedBands, key = { it }) { band ->
            val rows = snapshot.rankedSpots.filter { it.spot.band == band }
            Surface(Modifier.heightIn(min = 140.dp).clickable { controller.updateSettings { it.copy(selectedBands = listOf(band), selectedLayout = BandMapLayoutMode.SINGLE_EXPANDED) } },
                color = MapPanel, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(10.dp)) {
                Text(band.uppercase(), color = MapAmber, fontWeight = FontWeight.Black)
                Text("${rows.size} visible · ${rows.count { it.spot.contest.newMultipliers.isNotEmpty() }} multipliers", color = MapMuted, fontSize = 11.sp)
                MiniFrequencyAxis(controller.visibleSegment(band))
                rows.take(4).forEach { SpotLabel(it, statuses[it.spot.id], app, select, showFrequency = true) }
                if (rows.size > 5) Text("+${rows.size - 5} more", color = MapCyan, fontSize = 11.sp)
            } }
        }
    }
}

@Composable private fun SingleExpanded(snapshot: BandMapUiSnapshot, controller: BandMapController, statuses: Map<String, SpotLogStatus>, app: AppController, operatingContext: OperatingContextSnapshot, select: (BandMapRankedSpot) -> Unit) {
    val band = snapshot.selectedBands.firstOrNull() ?: return
    Column {
        val segment = controller.visibleSegment(band)
        Text("SINGLE BAND · $band · ${formatBandMapFrequency(segment.lowerHz)} – ${formatBandMapFrequency(segment.upperHz)}", color = MapAmber, fontWeight = FontWeight.Bold)
        BandViewportControls(band, controller)
        Text("RX ${formatBandMapFrequency(operatingContext.receiveFrequencyHz.value)} · TX ${operatingContext.transmitFrequencyHz.value?.takeIf { it > 0 }?.let(::formatBandMapFrequency) ?: "same / unavailable"} · ${if (operatingContext.split.value) "SPLIT" else "SIMPLEX"} · PASSBAND ${operatingContext.receiveBandwidthHz.value.takeIf { it > 0 }?.let { "$it Hz" } ?: "unavailable"}", color = MapMuted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        HorizontalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, statuses, app, select, expanded = true, radioContext = operatingContext)
    }
}

@Composable private fun HorizontalLane(band: String, rows: List<BandMapRankedSpot>, controller: BandMapController,
    statuses: Map<String, SpotLogStatus>, app: AppController, select: (BandMapRankedSpot) -> Unit, expanded: Boolean = false,
    radioContext: OperatingContextSnapshot? = null) {
    val segment = controller.visibleSegment(band)
    Surface(color = MapPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().height(if (expanded) 420.dp else 150.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)
            .pointerInput(band, segment) { detectTransformGestures { centroid, pan, zoom, _ ->
                if (abs(zoom - 1f) > .02f) controller.zoom(band, 1.0 / zoom, segment.lowerHz + (segment.spanHz * (centroid.x / size.width.coerceAtLeast(1))).toLong())
                if (abs(pan.x) > 3f) controller.pan(band, (-pan.x / size.width.coerceAtLeast(1)).toDouble())
            } }
            .pointerInput(band, segment) { detectTapGestures(onDoubleTap = { point ->
                controller.zoom(band, .5, segment.lowerHz + (segment.spanHz * (point.x / size.width.coerceAtLeast(1))).toLong())
            }) }
            .pointerInput(band, segment) { awaitPointerEventScope { while (true) {
                val event = awaitPointerEvent(); if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull() ?: continue
                    val delta = change.scrollDelta.y
                    if (delta != 0f) { controller.zoom(band, if (delta > 0f) 1.35 else .74); change.consume() }
                }
            } } }) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val placements = BandMapLayoutEngine.place(rows.map { it.spot }, segment, widthPx.roundToInt().coerceAtLeast(1), if (expanded) 38 else 25)
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height - 22.dp.toPx(); drawLine(MapGrid, Offset(0f, y), Offset(size.width, y), 2f)
                BandMapDisplayPlans.forBand(band, controller.settings.iaruRegion).segments.forEach { plan ->
                    val left = BandMapLayoutEngine.coordinate(plan.lowerHz, segment) * size.width
                    val right = BandMapLayoutEngine.coordinate(plan.upperHz, segment) * size.width
                    if (right > 0 && left < size.width) drawRect(segmentColor(plan.kind).copy(alpha = .10f), Offset(left.coerceAtLeast(0f), 0f),
                        androidx.compose.ui.geometry.Size((right.coerceAtMost(size.width) - left.coerceAtLeast(0f)).coerceAtLeast(0f), y))
                }
                BandMapLayoutEngine.ticks(segment, size.width.roundToInt()).forEach { tick ->
                    val x = tick.position * size.width; drawLine(MapGrid, Offset(x, y - if (tick.major) 15f else 8f), Offset(x, y + 5f), if (tick.major) 2f else 1f)
                }
                radioContext?.let { context ->
                    val rx = context.receiveFrequencyHz.value
                    val bandwidth = context.receiveBandwidthHz.value
                    if (rx in segment.lowerHz..segment.upperHz && bandwidth > 0) {
                        val left = BandMapLayoutEngine.coordinate(rx - bandwidth / 2, segment) * size.width
                        val right = BandMapLayoutEngine.coordinate(rx + bandwidth / 2, segment) * size.width
                        drawRect(MapCyan.copy(alpha = .16f), Offset(left.coerceAtLeast(0f), 0f), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), y))
                    }
                    if (rx in segment.lowerHz..segment.upperHz) { val x = BandMapLayoutEngine.coordinate(rx, segment) * size.width; drawLine(MapCyan, Offset(x, 0f), Offset(x, y), 3f) }
                    context.transmitFrequencyHz.value?.takeIf { context.split.value && it in segment.lowerHz..segment.upperHz }?.let { tx ->
                        val x = BandMapLayoutEngine.coordinate(tx, segment) * size.width; drawLine(MapMagenta, Offset(x, 0f), Offset(x, y), 3f)
                    }
                }
            }
            Text("${band.uppercase()} · ${formatBandMapFrequency(segment.lowerHz)}–${formatBandMapFrequency(segment.upperHz)}", color = MapAmber,
                fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopStart))
            BandMapLayoutEngine.ticks(segment, widthPx.roundToInt()).filter(BandMapTick::major).forEach { tick ->
                Text(formatBandMapFrequency(tick.frequencyHz), color = MapMuted, fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomStart).offset { IntOffset((tick.position * widthPx).roundToInt().coerceIn(0, (widthPx - 70).roundToInt().coerceAtLeast(0)), 0) })
            }
            rows.forEach { ranked -> placements.firstOrNull { it.id == ranked.spot.id }?.let { placed ->
                Box(Modifier.offset { IntOffset((placed.primary * widthPx).roundToInt().coerceIn(0, (widthPx - 105).roundToInt().coerceAtLeast(0)), placed.lane * 28) }
                    .widthIn(max = 150.dp)) { SpotLabel(ranked, statuses[ranked.spot.id], app, select, showFrequency = expanded,
                        stackMembers = placed.memberIds.mapNotNull { id -> rows.firstOrNull { it.spot.id == id } }) }
            } }
        }
    }
}

@Composable private fun VerticalLane(band: String, rows: List<BandMapRankedSpot>, controller: BandMapController, statuses: Map<String, SpotLogStatus>, app: AppController, select: (BandMapRankedSpot) -> Unit) {
    val segment = controller.visibleSegment(band)
    Surface(color = MapPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.width(230.dp).fillMaxHeight()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)
            .pointerInput(band, segment) { detectTransformGestures { centroid, pan, zoom, _ ->
                if (abs(zoom - 1f) > .02f) controller.zoom(band, 1.0 / zoom, segment.lowerHz + (segment.spanHz * (centroid.y / size.height.coerceAtLeast(1))).toLong())
                if (abs(pan.y) > 3f) controller.pan(band, (-pan.y / size.height.coerceAtLeast(1)).toDouble())
            } }
            .pointerInput(band, segment) { awaitPointerEventScope { while (true) {
                val event = awaitPointerEvent(); if (event.type == PointerEventType.Scroll) {
                    val change = event.changes.firstOrNull() ?: continue; val delta = change.scrollDelta.y
                    if (delta != 0f) { controller.zoom(band, if (delta > 0f) 1.35 else .74); change.consume() }
                }
            } } }) {
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val placements = BandMapLayoutEngine.place(rows.map { it.spot }, segment, heightPx.roundToInt().coerceAtLeast(1))
            Canvas(Modifier.fillMaxSize()) {
                val x = 18.dp.toPx(); drawLine(MapGrid, Offset(x, 0f), Offset(x, size.height), 2f)
                BandMapLayoutEngine.ticks(segment, size.height.roundToInt()).forEach { tick ->
                    val y = tick.position * size.height; drawLine(MapGrid, Offset(x - 5f, y), Offset(x + if (tick.major) 15f else 8f, y), 1f)
                }
            }
            Text("${band.uppercase()} · ${formatBandMapFrequency(segment.lowerHz)}–${formatBandMapFrequency(segment.upperHz)}", color = MapAmber,
                fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
            BandMapLayoutEngine.ticks(segment, heightPx.roundToInt()).filter(BandMapTick::major).forEach { tick ->
                Text(formatBandMapFrequency(tick.frequencyHz), color = MapMuted, fontSize = 8.sp,
                    modifier = Modifier.offset { IntOffset(0, (tick.position * heightPx).roundToInt().coerceIn(0, (heightPx - 18).roundToInt().coerceAtLeast(0))) })
            }
            rows.forEach { ranked -> placements.firstOrNull { it.id == ranked.spot.id }?.let { placed ->
                Box(Modifier.offset { IntOffset(28 + placed.lane * 6, (placed.primary * heightPx).roundToInt().coerceIn(0, (heightPx - 28).roundToInt().coerceAtLeast(0))) }
                    .width(190.dp)) { SpotLabel(ranked, statuses[ranked.spot.id], app, select,
                        stackMembers = placed.memberIds.mapNotNull { id -> rows.firstOrNull { it.spot.id == id } }) }
            } }
        }
    }
}

@Composable private fun SpotLabel(ranked: BandMapRankedSpot, status: SpotLogStatus?, app: AppController, select: (BandMapRankedSpot) -> Unit, showFrequency: Boolean = false, stackMembers: List<BandMapRankedSpot> = listOf(ranked)) {
    val spot = ranked.spot
    var stackOpen by remember(stackMembers.map { it.spot.id }) { mutableStateOf(false) }
    val accent = when { spot.contest.newMultipliers.isNotEmpty() -> MapMagenta; spot.need.entity == BandMapNeedTruth.NEEDED -> MapGreen; spot.chaser.eligible == true -> MapCyan; else -> MapAmber }
    Row(Modifier.padding(1.dp).background(MapBackground.copy(alpha = .92f), RoundedCornerShape(4.dp)).border(1.dp, accent, RoundedCornerShape(4.dp))
        .clickable { if (stackMembers.size > 1) stackOpen = true else select(ranked) }.padding(horizontal = 5.dp, vertical = 3.dp)
        .semantics { contentDescription = "${spot.callsign}, ${spot.band}, ${spot.frequencyHz} hertz, priority ${ranked.score}, ${ranked.explanation}" },
        verticalAlignment = Alignment.CenterVertically) {
        Text(spot.callsign, color = MapText, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        status?.let {
            Text(" CS ${it.callStatus}", color = Color(app.spotStatusColour(SPOT_STATUS_CS, it.callStatus)), fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(" DS ${it.dxccStatus}", color = Color(app.spotStatusColour(SPOT_STATUS_DS, it.dxccStatus)), fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (showFrequency) Text(" ${formatBandMapFrequency(spot.frequencyHz)}", color = MapMuted, fontSize = 9.sp)
        if (spot.sources.size > 1) Text(" ·${spot.sources.size}", color = MapCyan, fontSize = 9.sp)
        if (stackMembers.size > 1) Text(" +${stackMembers.size - 1}", color = MapMagenta, fontWeight = FontWeight.Black, fontSize = 9.sp)
    }
    if (stackOpen) AlertDialog(onDismissRequest = { stackOpen = false }, title = { Text("${stackMembers.size} spots at this position") },
        text = { LazyColumn { items(stackMembers.take(20), key = { it.spot.id }) { member ->
            TextButton({ stackOpen = false; select(member) }, modifier = Modifier.fillMaxWidth()) { Text("${member.spot.callsign} · ${formatBandMapFrequency(member.spot.frequencyHz)}") }
        } } }, confirmButton = { TextButton({ stackOpen = false }) { Text("CLOSE") } })
}

@Composable private fun MiniFrequencyAxis(segment: BandMapSegment) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f; drawLine(MapGrid, Offset(0f, y), Offset(size.width, y), 1f)
            BandMapLayoutEngine.ticks(segment, size.width.roundToInt()).forEach { tick ->
                val x = tick.position * size.width; drawLine(MapGrid, Offset(x, y - if (tick.major) 7f else 4f), Offset(x, y + 4f), 1f)
            }
        }
        BandMapLayoutEngine.ticks(segment, widthPx.roundToInt()).filter(BandMapTick::major).forEach { tick ->
            Text(formatBandMapFrequency(tick.frequencyHz), color = MapMuted, fontSize = 8.sp,
                modifier = Modifier.offset { IntOffset((tick.position * widthPx).roundToInt().coerceIn(0, (widthPx - 60).roundToInt().coerceAtLeast(0)), 16) })
        }
    }
}

@Composable private fun BandViewportControls(band: String, controller: BandMapController) {
    val segment = controller.visibleSegment(band)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ controller.zoom(band, .5) }) { Text("+") }
            OutlinedButton({ controller.zoom(band, 2.0) }) { Text("−") }
            OutlinedButton({ controller.pan(band, -.25) }) { Text("←") }
            OutlinedButton({ controller.pan(band, .25) }) { Text("→") }
            OutlinedButton({ controller.resetViewport(band) }) { Text("RESET") }
            FilterChip(controller.settings.linkedZoom, { controller.updateSettings { it.copy(linkedZoom = !it.linkedZoom) } }, { Text("LINKED ZOOM") })
        }
        Text("Viewport ${formatBandMapFrequency(segment.lowerHz)} – ${formatBandMapFrequency(segment.upperHz)} · pinch, drag, double-tap and keyboard +/− supported where focus is owned",
            color = MapMuted, fontSize = 10.sp)
        Text("Operating guidance · ${controller.settings.iaruRegion.name.replace('_', ' ')} · CW solid · DATA diagonal · PHONE dots · not regulatory authority",
            color = MapMuted, fontSize = 10.sp)
    }
}

private fun formatBandMapFrequency(value: Long): String = if (value >= 1_000_000L) "%.3f MHz".format(value / 1_000_000.0) else "%.1f kHz".format(value / 1_000.0)

private fun segmentColor(kind: BandMapOperatingSegmentKind): Color = when (kind) {
    BandMapOperatingSegmentKind.CW -> MapAmber
    BandMapOperatingSegmentKind.DATA -> MapCyan
    BandMapOperatingSegmentKind.PHONE -> MapGreen
    BandMapOperatingSegmentKind.FM_REPEATER -> MapMagenta
    BandMapOperatingSegmentKind.BEACON_SATELLITE -> MapMuted
    BandMapOperatingSegmentKind.CUSTOM -> MapText
}

@Composable private fun SpotDetail(ranked: BandMapRankedSpot, status: SpotLogStatus?, app: AppController, contextGeneration: Long, controller: BandMapController,
    onAction: (WorkspaceAction) -> Unit, mark: (BandMapMarkKind) -> Unit, dismiss: () -> Unit) {
    val spot = ranked.spot
    AlertDialog(onDismissRequest = dismiss, title = { Text("${spot.callsign} · ${spot.band} · ${"%.3f".format(spot.frequencyHz / 1_000_000.0)} MHz") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Text(ranked.explanation, color = MapAmber, fontWeight = FontWeight.Bold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CS ${status?.callStatus ?: "—"}", color = Color(app.spotStatusColour(SPOT_STATUS_CS, status?.callStatus)), fontWeight = FontWeight.Bold)
                Text("DS ${status?.dxccStatus ?: "—"}", color = Color(app.spotStatusColour(SPOT_STATUS_DS, status?.dxccStatus)), fontWeight = FontWeight.Bold)
            } }
            item { Text("Sources: ${spot.observations.joinToString { "${it.source.name} ${it.spotterCallsign}" }}") }
            item { Text("Need: entity ${spot.need.entity} · band ${spot.need.band} · mode ${spot.need.mode} · slot ${spot.need.bandMode}") }
            item { Text("Contest: ${if (spot.contest.active) "active" else "inactive"} · dupe ${spot.contest.duplicate ?: "unknown"} · multipliers ${spot.contest.newMultipliers.ifEmpty { setOf("none") }}") }
            item { Text("DX Chaser: ${if (!spot.chaser.available) "unavailable" else "${spot.chaser.priorityTier} · eligible ${spot.chaser.eligible}"} · target ${spot.chaser.currentTarget} · engaged ${spot.chaser.engagedTarget}") }
            item { Text("Evidence: ${spot.evidence.joinToString { "${it.kind}:${it.status}" }.ifBlank { "unavailable" }}") }
            item { Text("Observed ${Instant.now().epochSecond - spot.newestObservationEpoch}s ago · ${spot.spotters.size} independent spotters") }
            item { HorizontalDivider(); Text("Actions are receive-reviewed. Selection, marks and navigation send no CAT or TX.", color = MapMuted, fontSize = 11.sp) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BandMapMarkKind.entries.forEach { kind -> OutlinedButton({ mark(kind) }) { Text(kind.name) } }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ controller.workspaceAction(spot, WorkspaceDestination.RADIO, contextGeneration)?.let(onAction) }) { Text("REVIEW RX") }
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.DX, contextGeneration)?.let(onAction) }) { Text("DX DETAILS") }
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.LOGBOOK, contextGeneration)?.let(onAction) }) { Text("HISTORY") }
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.DX_CHASER, contextGeneration)?.let(onAction) }) { Text("OPEN CHASER") }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.CALLBOOK, contextGeneration)?.let(onAction) }) { Text("CALLBOOK") }
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.CONTEST, contextGeneration)?.let(onAction) }) { Text("CONTEST") }
                OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.DIGI, contextGeneration)?.let(onAction) }) { Text("PREPARE DIGI") }
                if (spot.portablePrograms.isNotEmpty()) OutlinedButton({ controller.workspaceAction(spot, WorkspaceDestination.PORTABLE, contextGeneration)?.let(onAction) }) { Text("ACTIVATION") }
            } }
        } }, confirmButton = { TextButton(dismiss) { Text("CLOSE") } })
}

@Composable private fun FilterDialog(controller: BandMapController, dismiss: () -> Unit) {
    val preset = controller.settings.presets.firstOrNull { it.id == controller.settings.activePresetId } ?: builtInBandMapPresets.first()
    var draft by remember(preset.id) { mutableStateOf(preset.filter) }
    var segmentProfile by remember(preset.id) { mutableStateOf(BandMapSegmentProfile.WHOLE) }
    var customLower by remember(preset.id) { mutableStateOf("") }; var customUpper by remember(preset.id) { mutableStateOf("") }
    fun <T> toggle(set: Set<T>, value: T) = if (value in set) set - value else set + value
    AlertDialog(onDismissRequest = dismiss, title = { Text("Band Map filters") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 570.dp)) {
        item { OutlinedTextField(draft.search, { draft = draft.copy(search = it.take(80)) }, label = { Text("Callsign, comment or reference") }) }
        item { Text("MODE FAMILY", fontWeight = FontWeight.Bold); ChipFlow { BandMapModeFamily.entries.forEach { value -> FilterChip(value in draft.modes, { draft = draft.copy(modes = toggle(draft.modes, value)) }, { Text(value.name) }) } } }
        item { Text("SOURCE", fontWeight = FontWeight.Bold); ChipFlow { BandMapSource.entries.forEach { value -> FilterChip(value in draft.sources, { draft = draft.copy(sources = toggle(draft.sources, value)) }, { Text(value.name.replace('_', ' ')) }) } } }
        item { Text("DISPLAY SEGMENT · not a legal band plan", fontWeight = FontWeight.Bold); ChipFlow { BandMapSegmentProfile.entries.forEach { value -> FilterChip(segmentProfile == value, {
            segmentProfile = value
            if (value != BandMapSegmentProfile.CUSTOM) draft = draft.copy(segments = controller.settings.selectedBands.map { bandMapDisplaySegment(it, value) })
        }, { Text(value.name.replace('_', ' ')) }) } } }
        if (segmentProfile == BandMapSegmentProfile.CUSTOM) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(customLower, { customLower = it.filter(Char::isDigit).take(12) }, label = { Text("Start kHz") }, modifier = Modifier.weight(1f))
            OutlinedTextField(customUpper, { customUpper = it.filter(Char::isDigit).take(12) }, label = { Text("End kHz") }, modifier = Modifier.weight(1f))
        } }
        item { Text("AGE / DIVERSITY", fontWeight = FontWeight.Bold); ChipFlow {
            listOf(300L, 900L, 3_600L, 10_800L).forEach { seconds -> FilterChip(draft.maximumAgeSeconds == seconds, { draft = draft.copy(maximumAgeSeconds = seconds) }, { Text(if (seconds < 3_600) "${seconds / 60}m" else "${seconds / 3_600}h") }) }
            (0..3).forEach { count -> FilterChip(draft.minimumSpotters == count, { draft = draft.copy(minimumSpotters = count) }, { Text("$count+ spotters") }) }
            (0..3).forEach { count -> FilterChip(draft.minimumSourceDiversity == count, { draft = draft.copy(minimumSourceDiversity = count) }, { Text("$count+ sources") }) }
        } }
        item { Text("SPOTTER CONTINENT", fontWeight = FontWeight.Bold); ChipFlow { listOf("AF", "AS", "EU", "NA", "OC", "SA").forEach { value -> FilterChip(value in draft.spotterContinents, { draft = draft.copy(spotterContinents = toggle(draft.spotterContinents, value)) }, { Text(value) }) } } }
        item { Text("TARGET CONTINENT", fontWeight = FontWeight.Bold); ChipFlow { listOf("AF", "AS", "EU", "NA", "OC", "SA").forEach { value -> FilterChip(value in draft.targetContinents, { draft = draft.copy(targetContinents = toggle(draft.targetContinents, value)) }, { Text(value) }) } } }
        item { Text("NEEDS", fontWeight = FontWeight.Bold); ChipFlow { listOf("ENTITY", "BAND", "MODE", "BAND_MODE", "GRID", "CQ", "ITU", "PORTABLE", "UNKNOWN").forEach { value -> FilterChip(value in draft.requiredNeeds, { draft = draft.copy(requiredNeeds = toggle(draft.requiredNeeds, value)) }, { Text(value) }) } } }
        item { Text("CONTEST / CHASER", fontWeight = FontWeight.Bold); ChipFlow {
            FilterChip(draft.contestOnly, { draft = draft.copy(contestOnly = !draft.contestOnly) }, { Text("CONTEST ACTIVE") })
            FilterChip(draft.multipliersOnly, { draft = draft.copy(multipliersOnly = !draft.multipliersOnly) }, { Text("NEW MULT") })
            FilterChip(draft.hideDuplicates, { draft = draft.copy(hideDuplicates = !draft.hideDuplicates) }, { Text("HIDE DUPES") })
            FilterChip(draft.chaserEligibleOnly, { draft = draft.copy(chaserEligibleOnly = !draft.chaserEligibleOnly) }, { Text("CHASER ELIGIBLE") })
        } }
        item { Text("PORTABLE", fontWeight = FontWeight.Bold); ChipFlow { listOf("POTA", "SOTA", "WWFF").forEach { value -> FilterChip(value in draft.portablePrograms, { draft = draft.copy(portablePrograms = toggle(draft.portablePrograms, value)) }, { Text(value) }) } } }
        item { Text("CURRENT EVIDENCE STATUS", fontWeight = FontWeight.Bold); ChipFlow { BandMapEvidenceStatus.entries.forEach { value -> FilterChip(value in draft.evidenceStatuses, { draft = draft.copy(evidenceStatuses = toggle(draft.evidenceStatuses, value)) }, { Text(value.name) }) } } }
        item { ChipFlow {
            FilterChip(draft.showUnknown, { draft = draft.copy(showUnknown = !draft.showUnknown) }, { Text("SHOW UNKNOWN") })
            FilterChip(draft.showStale, { draft = draft.copy(showStale = !draft.showStale) }, { Text("SHOW STALE") })
        }; Text("Filters remain separate from priority ranking. Unknown provider data is not silently classified as needed.", color = MapMuted, fontSize = 11.sp) }
    } }, confirmButton = { Button({
        if (segmentProfile == BandMapSegmentProfile.CUSTOM) {
            val band = controller.settings.selectedBands.first(); val definition = bandMapBands.first { it.name == band }
            val low = customLower.toLongOrNull()?.times(1_000); val high = customUpper.toLongOrNull()?.times(1_000)
            if (low != null && high != null && low >= definition.lowerHz && high <= definition.upperHz && high > low) draft = draft.copy(segments = listOf(BandMapSegment(band, "Custom display range", low, high)))
        }
        controller.updateSettings { current -> current.copy(presets = current.presets.map { if (it.id == preset.id) it.copy(filter = draft) else it }) }; dismiss()
    }) { Text("APPLY") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}

@Composable private fun ChipFlow(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(5.dp), content = content)

@Composable
internal fun BandMapSettingsPanel(controller: BandMapController) {
    val value = controller.settings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("INTELLIGENT BAND MAPS", fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(value.enabled, { controller.updateSettings { it.copy(enabled = !it.enabled) } }, { Text(if (value.enabled) "ENABLED" else "DISABLED") })
            FilterChip(value.navigationVisible, { controller.updateSettings { it.copy(navigationVisible = !it.navigationVisible) } }, { Text("SHOW IN NAV") })
            FilterChip(value.palette == "COLOUR_VISION_FRIENDLY", { controller.updateSettings { it.copy(palette = "COLOUR_VISION_FRIENDLY") } }, { Text("COLOUR-VISION PALETTE") })
        }
        Text("DISPLAY GUIDANCE REGION", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BandMapIaruRegion.entries.forEach { region -> FilterChip(value.iaruRegion == region,
                { controller.updateSettings { it.copy(iaruRegion = region) } }, { Text(region.name.replace('_', ' ')) }) }
        }
        Text("RigWeave reviewed IARU display guidance 2026-08 · operating guidance only, not country-specific regulatory authority · 60m uncertainty explicit",
            style = MaterialTheme.typography.bodySmall)
        Text("Settings, editable built-in presets, layouts, selected bands and local marks are included in configuration backup. Restore never triggers CAT, Keyer, Digi or DX Chaser actions.",
            style = MaterialTheme.typography.bodySmall)
        Text("Schema $BAND_MAP_SETTINGS_SCHEMA · ${value.presets.size} presets · ${value.marks.size} local marks", style = MaterialTheme.typography.labelSmall)
    }
}

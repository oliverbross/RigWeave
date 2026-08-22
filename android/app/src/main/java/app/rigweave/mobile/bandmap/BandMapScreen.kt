// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile.bandmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
            Key.Escape -> { selected = null; controller.select(null); filterOpen = false }
            else -> return@onPreviewKeyEvent false
        }
        true
    }
    Column(keyboardModifier.fillMaxSize().background(MapBackground).padding(10.dp).testTag("band-map-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BandMapHeader(snapshot, keyer, { filterOpen = true })
        PresetAndLayoutControls(controller)
        BandControls(controller)
        if (snapshot.unavailableReasons.isNotEmpty()) Text(snapshot.unavailableReasons.joinToString(" · "), color = MapAmber, fontSize = 12.sp)
        Box(Modifier.weight(1f)) {
            when (snapshot.layout) {
                BandMapLayoutMode.GRID_OVERVIEW -> BandGrid(snapshot, controller) { selected = it }
                BandMapLayoutMode.MULTI_HORIZONTAL -> VerticalBandColumns(snapshot, controller) { selected = it }
                BandMapLayoutMode.SINGLE_EXPANDED -> SingleExpanded(snapshot, controller) { selected = it }
                BandMapLayoutMode.MULTI_VERTICAL -> HorizontalBandRows(snapshot, controller) { selected = it }
            }
        }
    }
    selected?.let { ranked -> SpotDetail(ranked, snapshot.contextGeneration, controller, onAction, { controller.toggleMark(ranked.spot, it) }) { selected = null; controller.select(null) } }
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
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        bandMapBands.forEach { band -> FilterChip(band.name in selected, {
            controller.updateSettings { current ->
                val next = if (band.name in current.selectedBands) current.selectedBands - band.name else current.selectedBands + band.name
                current.copy(selectedBands = next.ifEmpty { current.selectedBands })
            }
        }, { Text(band.name) }) }
    }
}

@Composable private fun HorizontalBandRows(snapshot: BandMapUiSnapshot, controller: BandMapController, select: (BandMapRankedSpot) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(snapshot.selectedBands, key = { it }) { band -> HorizontalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, select) }
    }
}

@Composable private fun VerticalBandColumns(snapshot: BandMapUiSnapshot, controller: BandMapController, select: (BandMapRankedSpot) -> Unit) {
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        snapshot.selectedBands.forEach { band -> VerticalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, select) }
    }
}

@Composable private fun BandGrid(snapshot: BandMapUiSnapshot, controller: BandMapController, select: (BandMapRankedSpot) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(snapshot.selectedBands, key = { it }) { band ->
            val rows = snapshot.rankedSpots.filter { it.spot.band == band }
            Surface(Modifier.heightIn(min = 140.dp).clickable { controller.updateSettings { it.copy(selectedBands = listOf(band), selectedLayout = BandMapLayoutMode.SINGLE_EXPANDED) } },
                color = MapPanel, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(10.dp)) {
                Text(band.uppercase(), color = MapAmber, fontWeight = FontWeight.Black)
                Text("${rows.size} visible · ${rows.count { it.spot.contest.newMultipliers.isNotEmpty() }} multipliers", color = MapMuted, fontSize = 11.sp)
                rows.take(5).forEach { SpotLabel(it, select) }
                if (rows.size > 5) Text("+${rows.size - 5} more", color = MapCyan, fontSize = 11.sp)
            } }
        }
    }
}

@Composable private fun SingleExpanded(snapshot: BandMapUiSnapshot, controller: BandMapController, select: (BandMapRankedSpot) -> Unit) {
    val band = snapshot.selectedBands.firstOrNull() ?: return
    Column {
        Text("SINGLE BAND · $band · exact frequency scale", color = MapAmber, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        HorizontalLane(band, snapshot.rankedSpots.filter { it.spot.band == band }, controller, select, expanded = true)
    }
}

@Composable private fun HorizontalLane(band: String, rows: List<BandMapRankedSpot>, controller: BandMapController,
    select: (BandMapRankedSpot) -> Unit, expanded: Boolean = false) {
    val definition = bandMapBands.first { it.name == band }; val segment = BandMapSegment(band, lowerHz = definition.lowerHz, upperHz = definition.upperHz)
    Surface(color = MapPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().height(if (expanded) 420.dp else 150.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val placements = BandMapLayoutEngine.place(rows.map { it.spot }, segment, widthPx.roundToInt().coerceAtLeast(1), if (expanded) 38 else 25)
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height - 22.dp.toPx(); drawLine(MapGrid, Offset(0f, y), Offset(size.width, y), 2f)
                BandMapLayoutEngine.ticks(segment, size.width.roundToInt()).forEach { tick ->
                    val x = tick.position * size.width; drawLine(MapGrid, Offset(x, y - if (tick.major) 15f else 8f), Offset(x, y + 5f), if (tick.major) 2f else 1f)
                }
            }
            Text(band.uppercase(), color = MapAmber, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart))
            rows.forEach { ranked -> placements.firstOrNull { it.id == ranked.spot.id }?.let { placed ->
                Box(Modifier.offset { IntOffset((placed.primary * widthPx).roundToInt().coerceIn(0, (widthPx - 105).roundToInt().coerceAtLeast(0)), placed.lane * 28) }
                    .widthIn(max = 108.dp)) { SpotLabel(ranked, select) }
            } }
        }
    }
}

@Composable private fun VerticalLane(band: String, rows: List<BandMapRankedSpot>, controller: BandMapController, select: (BandMapRankedSpot) -> Unit) {
    val definition = bandMapBands.first { it.name == band }; val segment = BandMapSegment(band, lowerHz = definition.lowerHz, upperHz = definition.upperHz)
    Surface(color = MapPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.width(230.dp).fillMaxHeight()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
            val placements = BandMapLayoutEngine.place(rows.map { it.spot }, segment, heightPx.roundToInt().coerceAtLeast(1))
            Canvas(Modifier.fillMaxSize()) {
                val x = 18.dp.toPx(); drawLine(MapGrid, Offset(x, 0f), Offset(x, size.height), 2f)
                BandMapLayoutEngine.ticks(segment, size.height.roundToInt()).forEach { tick ->
                    val y = tick.position * size.height; drawLine(MapGrid, Offset(x - 5f, y), Offset(x + if (tick.major) 15f else 8f, y), 1f)
                }
            }
            Text(band.uppercase(), color = MapAmber, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd))
            rows.forEach { ranked -> placements.firstOrNull { it.id == ranked.spot.id }?.let { placed ->
                Box(Modifier.offset { IntOffset(28 + placed.lane * 6, (placed.primary * heightPx).roundToInt().coerceIn(0, (heightPx - 28).roundToInt().coerceAtLeast(0))) }
                    .width(175.dp)) { SpotLabel(ranked, select) }
            } }
        }
    }
}

@Composable private fun SpotLabel(ranked: BandMapRankedSpot, select: (BandMapRankedSpot) -> Unit) {
    val spot = ranked.spot
    val accent = when { spot.contest.newMultipliers.isNotEmpty() -> MapMagenta; spot.need.entity == BandMapNeedTruth.NEEDED -> MapGreen; spot.chaser.eligible == true -> MapCyan; else -> MapAmber }
    Row(Modifier.padding(1.dp).background(MapBackground.copy(alpha = .92f), RoundedCornerShape(4.dp)).border(1.dp, accent, RoundedCornerShape(4.dp))
        .clickable { select(ranked) }.padding(horizontal = 5.dp, vertical = 3.dp)
        .semantics { contentDescription = "${spot.callsign}, ${spot.band}, ${spot.frequencyHz} hertz, priority ${ranked.score}, ${ranked.explanation}" },
        verticalAlignment = Alignment.CenterVertically) {
        Text(spot.callsign, color = MapText, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (spot.sources.size > 1) Text(" ·${spot.sources.size}", color = MapCyan, fontSize = 9.sp)
    }
}

@Composable private fun SpotDetail(ranked: BandMapRankedSpot, contextGeneration: Long, controller: BandMapController,
    onAction: (WorkspaceAction) -> Unit, mark: (BandMapMarkKind) -> Unit, dismiss: () -> Unit) {
    val spot = ranked.spot
    AlertDialog(onDismissRequest = dismiss, title = { Text("${spot.callsign} · ${spot.band} · ${"%.3f".format(spot.frequencyHz / 1_000_000.0)} MHz") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Text(ranked.explanation, color = MapAmber, fontWeight = FontWeight.Bold) }
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
        } }, confirmButton = { TextButton(dismiss) { Text("CLOSE") } })
}

@Composable private fun FilterDialog(controller: BandMapController, dismiss: () -> Unit) {
    val preset = controller.settings.presets.firstOrNull { it.id == controller.settings.activePresetId } ?: builtInBandMapPresets.first()
    var search by remember(preset.id) { mutableStateOf(preset.filter.search) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Band Map filters") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search, { search = it.take(80) }, label = { Text("Callsign, comment or reference") })
        Text("Filters remain separate from priority ranking. Unknown provider data is not silently classified as needed.", color = MapMuted, fontSize = 11.sp)
        Text("Active preset: ${preset.label} · sources ${preset.filter.sources.ifEmpty { BandMapSource.entries.toSet() }}")
    } }, confirmButton = { Button({ controller.updateSettings { current -> current.copy(presets = current.presets.map { if (it.id == preset.id) it.copy(filter = it.filter.copy(search = search)) else it }) }; dismiss() }) { Text("APPLY") } },
        dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}

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
        Text("Settings, editable built-in presets, layouts, selected bands and local marks are included in configuration backup. Restore never triggers CAT, Keyer, Digi or DX Chaser actions.",
            style = MaterialTheme.typography.bodySmall)
        Text("Schema $BAND_MAP_SETTINGS_SCHEMA · ${value.presets.size} presets · ${value.marks.size} local marks", style = MaterialTheme.typography.labelSmall)
    }
}

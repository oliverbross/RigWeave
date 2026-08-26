// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val SdrChassis = Color(0xFF111519)
private val SdrPanel = Color(0xFF1B2228)
private val SdrRaised = Color(0xFF283139)
private val SdrInk = Color(0xFFF4F0E7)
private val SdrMuted = Color(0xFFA5ADB2)
private val SdrAmber = Color(0xFFE9A72B)
private val SdrHold = Color(0xFFF4C94E)
private val SdrHealthy = Color(0xFF42C77B)
private val SdrDanger = Color(0xFFE4544D)

private enum class TciCockpitPage { RECEIVERS, PANADAPTER, SCANNER }

@Composable
fun TciRadioCockpit(
    runtime: TciRuntimeState,
    platform: RadioRuntimeSnapshot,
    panadapter: PanadapterController,
    rxAudio: TciRxAudioController,
    scanner: ReceiveOnlyScannerController,
    memories: List<RadioPreset>,
    dispatch: (RadioPlatformAction) -> Unit,
    connect: () -> Unit,
    disconnect: () -> Unit,
    debugLab: DebugSdrLab?,
) {
    val state = runtime.snapshot
    var page by remember { mutableStateOf(TciCockpitPage.RECEIVERS) }
    Column(Modifier.fillMaxSize().background(SdrChassis).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("TCI RADIO · RECEIVE ONLY", color = SdrAmber, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("${state.device} · ${state.protocol} ${state.protocolVersion} · ${state.state}", color = if (state.ready) SdrHealthy else SdrHold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.ready) Button(connect, modifier = Modifier.heightIn(min = 48.dp)) { Text("CONNECT") }
                else OutlinedButton(disconnect, modifier = Modifier.heightIn(min = 48.dp)) { Text("DISCONNECT") }
                if (BuildConfig.DEBUG && debugLab != null) OutlinedButton({ if (debugLab.active) debugLab.stop() else debugLab.start() }) {
                    Text(if (debugLab.active) "STOP DEMO" else "DEMO · NO RADIO")
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TciCockpitPage.entries.forEach { item -> FilterChip(page == item, { page = item }, { Text(item.name) }) }
            SdrTruthChip("RX ${state.receivers.size}/${state.declaredReceiverCount}", state.receivers.isNotEmpty())
            SdrTruthChip("IQ DROP ${state.droppedFrames}", state.droppedFrames == 0L)
            SdrTruthChip("TX BLOCKED", true)
        }
        when (page) {
            TciCockpitPage.RECEIVERS -> ReceiverCockpit(state, rxAudio, dispatch)
            TciCockpitPage.PANADAPTER -> TciPanadapterPanel(state, panadapter, dispatch)
            TciCockpitPage.SCANNER -> ScannerPanel(scanner, memories, panadapter.frame, state, dispatch)
        }
        if (!state.ready && debugLab?.active != true) SdrEmptyState(
            "TCI data unavailable",
            "Configure a TCI profile in Settings, then explicitly Connect. The app remains disconnected, receive-only and TX locked.")
        if (platform.lastSanitizedError != null || state.lastError != null) {
            Text("STATUS · ${state.lastError ?: platform.lastSanitizedError}", color = SdrDanger)
        }
    }
}

@Composable
private fun ReceiverCockpit(state: TciRuntimeSnapshot, rxAudio: TciRxAudioController, dispatch: (RadioPlatformAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val content: @Composable (TciReceiverSnapshot) -> Unit = { receiver ->
            ReceiverInstrument(receiver, {
                dispatch(RadioPlatformAction(RadioActionClass.SAFE_SET, "select_receiver", longValue = receiver.backendIndex.toLong()))
            }, {
                dispatch(RadioPlatformAction(RadioActionClass.SAFE_SET, "listen_receiver", longValue = receiver.backendIndex.toLong()))
            }, { action ->
                val routeReady = action != "audio_start" ||
                    rxAudio.start(receiver.backendIndex, receiver.sampleRate.coerceAtLeast(48_000))
                if (action == "audio_stop") rxAudio.stop("Operator stopped TCI RX audio")
                if (routeReady) dispatch(
                    RadioPlatformAction(RadioActionClass.SAFE_SET,
                        if (action == "unmute") "mute" else action,
                        longValue = when (action) { "mute" -> 1L; "unmute" -> 0L; else -> null }),
                )
            })
        }
        if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.receivers.take(2).forEach { receiver -> Box(Modifier.weight(1f)) { content(receiver) } }
            StreamHealthRail(state, Modifier.widthIn(min = 190.dp, max = 260.dp).fillMaxHeight())
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.receivers.take(2).forEach { receiver -> content(receiver) }
            StreamHealthRail(state, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReceiverInstrument(receiver: TciReceiverSnapshot, select: () -> Unit, listen: () -> Unit, stream: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (receiver.active) SdrAmber.copy(alpha = .13f) else SdrPanel),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "TCI ${receiver.label}, ${receiver.mode}, ${receiver.effectiveRxHz} hertz" }) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(receiver.label, color = SdrAmber, fontWeight = FontWeight.Black)
                Text(buildString { if (receiver.active) append("CONTROL "); if (receiver.listening) append("LISTEN ") }.ifBlank { "AVAILABLE" },
                    color = if (receiver.active || receiver.listening) SdrHealthy else SdrMuted)
            }
            Text(formatRadioFrequency(receiver.effectiveRxHz), color = SdrInk, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black, fontSize = 34.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SdrMeasure("VFO A", formatRadioFrequency(receiver.vfoAHz))
                SdrMeasure("VFO B", formatRadioFrequency(receiver.vfoBHz))
                SdrMeasure("MODE", receiver.mode)
                SdrMeasure("PASSBAND", receiver.passbandHz?.let { "$it Hz" } ?: "UNKNOWN")
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(select, modifier = Modifier.heightIn(min = 48.dp)) { Text("CONTROL") }
                OutlinedButton(listen, modifier = Modifier.heightIn(min = 48.dp)) { Text("LISTEN") }
                OutlinedButton({ stream(if (receiver.iqRunning) "iq_stop" else "iq_start") }) { Text(if (receiver.iqRunning) "STOP IQ" else "START IQ") }
                OutlinedButton({ stream(if (receiver.audioRunning) "audio_stop" else "audio_start") }) { Text(if (receiver.audioRunning) "STOP AUDIO" else "START AUDIO") }
                OutlinedButton({ stream(if (receiver.muted) "unmute" else "mute") }) { Text(if (receiver.muted) "UNMUTE" else "MUTE") }
            }
            Text("${if (receiver.enabled) "RX ENABLED" else "RX DISABLED"} · IQ ${if (receiver.iqRunning) "LIVE" else "STOPPED"} · AUDIO ${if (receiver.audioRunning) "LIVE" else "STOPPED"} · ${receiver.sampleRate} Hz · DROP ${receiver.droppedFrames}", color = SdrMuted)
        }
    }
}

@Composable
private fun StreamHealthRail(state: TciRuntimeSnapshot, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SdrRaised)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("STREAM / SAFETY", color = SdrAmber, fontWeight = FontWeight.Bold)
            SdrMeasure("CONNECTION", state.state.name)
            SdrMeasure("RECEIVERS", "${state.receivers.size}")
            SdrMeasure("MALFORMED", "${state.malformedFrames}")
            SdrMeasure("OVERLOAD DROP", "${state.droppedFrames}")
            SdrMeasure("TX STREAMS IGNORED", "${state.blockedTxFrames}")
            Text("PTT, TUNE, TX audio, drive and automatic transmit are unavailable in Android TCI v1.", color = SdrHold, fontSize = 11.sp)
        }
    }
}

@Composable
fun TciPanadapterPanel(state: TciRuntimeSnapshot, controller: PanadapterController, dispatch: (RadioPlatformAction) -> Unit,
    scanner: ReceiveOnlyScannerController? = null, memories: List<RadioPreset> = emptyList()) {
    var dual by remember { mutableStateOf(true) }
    var fit by remember { mutableStateOf(true) }
    var peak by remember { mutableStateOf(true) }
    var palette by remember { mutableIntStateOf(0) }
    var floor by remember { mutableFloatStateOf(-125f) }
    var top by remember { mutableFloatStateOf(-35f) }
    var displayMode by remember { mutableStateOf("BOTH") }
    var scannerOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(dual, { dual = !dual }, { Text(if (dual) "DUAL RECEIVER" else "SELECTED RECEIVER") })
            FilterChip(fit, { fit = !fit }, { Text("FIT ${if (fit) "ON" else "OFF"}") })
            FilterChip(peak, { peak = !peak }, { Text("PEAK HOLD ${if (peak) "ON" else "OFF"}") })
            FilterChip(false, { palette = (palette + 1) % 4 }, { Text(listOf("AMBER", "VIRIDIS", "ICE", "MONO")[palette]) })
            OutlinedButton(controller::resetPeakHold) { Text("RESET PEAK") }
            if (scanner != null) FilterChip(scannerOpen, { scannerOpen = !scannerOpen }, { Text("SCANNER") })
        }
        if (scannerOpen && scanner != null) {
            ScannerPanel(scanner, memories, controller.frame, state, dispatch)
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("BOTH", "SPECTRUM", "WATERFALL").forEach { value ->
                FilterChip(displayMode == value, { displayMode = value }, { Text(value) })
            }
            listOf(1, 2, 4, 8).forEach { frames -> FilterChip(controller.settings.averageFrames == frames,
                { controller.updateSettings(controller.settings.copy(averageFrames = frames)) }, { Text("AVG $frames") }) }
            listOf(0f, 5f, 10f).forEach { decay -> FilterChip(controller.settings.peakDecayDbPerSecond == decay,
                { controller.updateSettings(controller.settings.copy(peakDecayDbPerSecond = decay)) }, { Text("DECAY ${decay.toInt()}") }) }
        }
        if (!fit) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FLOOR ${floor.toInt()}", color = SdrMuted); Slider(floor, { floor = it.coerceAtMost(top - 20) }, valueRange = -140f..-50f, modifier = Modifier.weight(1f))
            Text("TOP ${top.toInt()}", color = SdrMuted); Slider(top, { top = it.coerceAtLeast(floor + 20) }, valueRange = -100f..0f, modifier = Modifier.weight(1f))
        }
        val displays = controller.tciDisplays.toSortedMap().values.take(if (dual) 2 else 1).toList()
        if (displays.isEmpty()) SdrEmptyState("TCI I/Q unavailable", "Start I/Q on a receiver after the TCI connection reports READY, or use DEMO · NO RADIO in a debug build.")
        else BoxWithConstraints(Modifier.weight(1f)) {
            val sideBySide = dual && maxWidth >= 780.dp
            if (sideBySide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                displays.forEach { display ->
                    val centerHz = state.receivers.firstOrNull { it.backendIndex == display.receiverIndex }?.effectiveRxHz
                    TciSpectrumInstrument(display, centerHz, fit, peak, palette, floor, top, displayMode, dispatch, Modifier.weight(1f))
                }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displays.forEach { display ->
                    val centerHz = state.receivers.firstOrNull { it.backendIndex == display.receiverIndex }?.effectiveRxHz
                    TciSpectrumInstrument(display, centerHz, fit, peak, palette, floor, top, displayMode, dispatch, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TciSpectrumInstrument(display: TciPanadapterDisplay, centerHz: Long?, fit: Boolean, peak: Boolean, palette: Int,
    manualFloor: Float, manualTop: Float, displayMode: String, dispatch: (RadioPlatformAction) -> Unit, modifier: Modifier) {
    val frame = display.frame
    val fittedFloor = if (fit) frame.floorDb.coerceAtMost(frame.peakDb - 30f) else manualFloor
    val fittedTop = if (fit) max(frame.peakDb + 4f, fittedFloor + 30f) else manualTop
    var viewZoom by remember(display.receiverIndex) { mutableFloatStateOf(1f) }
    var viewPan by remember(display.receiverIndex) { mutableFloatStateOf(0f) }
    var reviewOffsetHz by remember(display.receiverIndex) { mutableStateOf(0L) }
    var pointerHz by remember(display.receiverIndex) { mutableStateOf<Long?>(null) }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RX ${display.receiverIndex + 1} · ${display.sampleRate / 1000} kHz · ${frame.fftSize} FFT", color = SdrAmber, fontWeight = FontWeight.Bold)
                Text("FIT ${"%.0f".format(fittedFloor)} / ${"%.0f".format(fittedTop)} dB · DROP ${display.droppedFrames}", color = SdrMuted)
            }
            if (displayMode != "WATERFALL") Canvas(Modifier.fillMaxWidth().weight(if (displayMode == "BOTH") .44f else 1f)
                .pointerInput(centerHz, display.sampleRate) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val width = size.width.coerceAtLeast(1).toFloat()
                        viewZoom = (viewZoom * gestureZoom).coerceIn(1f, 32f)
                        viewPan = (viewPan - pan.x / width / viewZoom).coerceIn(-.5f, .5f)
                        centerHz?.let { center ->
                            val offset = ((centroid.x / width - .5f + viewPan) * display.sampleRate / viewZoom).toLong()
                            pointerHz = center + offset
                            reviewOffsetHz = offset
                        }
                    }
                }
                .semantics { contentDescription = "TCI receiver ${display.receiverIndex + 1} live spectrum with VFO, passband, band plan and explicit QSY review" }) {
                drawRect(Color(0xFF07090B))
                val trace = frame.trace
                if (trace.size > 1) {
                    val visibleBins = (trace.size / viewZoom).toInt().coerceAtLeast(2)
                    val centerBin = ((.5f + viewPan) * trace.lastIndex).toInt().coerceIn(0, trace.lastIndex)
                    val firstBin = (centerBin - visibleBins / 2).coerceIn(0, (trace.size - visibleBins).coerceAtLeast(0))
                    val lastBin = (firstBin + visibleBins - 1).coerceAtMost(trace.lastIndex)
                    val path = Path()
                    for (index in firstBin..lastBin) {
                        val value = trace[index]
                        val x = size.width * (index - firstBin) / (lastBin - firstBin).coerceAtLeast(1).toFloat()
                        val y = size.height * (1f - ((value - fittedFloor) / (fittedTop - fittedFloor)).coerceIn(0f, 1f))
                        if (index == firstBin) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, SdrAmber, style = Stroke(1.5.dp.toPx()))
                    if (peak) {
                        val peakPath = Path()
                        for (index in firstBin..lastBin) {
                            val value = frame.peakHold[index]
                            val x = size.width * (index - firstBin) / (lastBin - firstBin).coerceAtLeast(1).toFloat()
                            val y = size.height * (1f - ((value - fittedFloor) / (fittedTop - fittedFloor)).coerceIn(0f, 1f))
                            if (index == firstBin) peakPath.moveTo(x, y) else peakPath.lineTo(x, y)
                        }
                        drawPath(peakPath, SdrHold.copy(alpha = .65f), style = Stroke(1.dp.toPx()))
                    }
                }
                val passbandCenter = (.5f + reviewOffsetHz.toFloat() * viewZoom / display.sampleRate).coerceIn(0f, 1f)
                drawRect(SdrHealthy.copy(alpha = .13f), topLeft = Offset(size.width * (passbandCenter - .06f).coerceAtLeast(0f), 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * .12f, size.height))
                drawLine(SdrHealthy, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                val segments = listOf(0f to .18f, .18f to .38f, .38f to .86f, .86f to 1f)
                val colors = listOf(SdrAmber, Color(0xFF43C7D9), SdrHealthy, SdrHold)
                segments.forEachIndexed { index, segment -> drawRect(colors[index].copy(alpha = .45f),
                    topLeft = Offset(size.width * segment.first, size.height - 7.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width * (segment.second - segment.first), 7.dp.toPx())) }
            }
            if (displayMode != "SPECTRUM") WaterfallCanvas(display.waterfallRows, fittedFloor, fittedTop, palette,
                Modifier.fillMaxWidth().weight(if (displayMode == "BOTH") .56f else 1f))
            Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${pointerHz?.let(::formatRadioFrequency) ?: "touch for frequency"} · zoom ${"%.1f".format(viewZoom)}× · ${listOf("amber", "viridis", "ice", "mono")[palette]} ${fittedFloor.toInt()}…${fittedTop.toInt()} dB",
                    color = SdrMuted, fontSize = 10.sp)
                TextButton({
                    centerHz?.let { dispatch(RadioPlatformAction(RadioActionClass.SAFE_SET, "frequency", longValue = it + reviewOffsetHz)) }
                }, enabled = centerHz != null) { Text("QSY REVIEW") }
            }
        }
    }
}

@Composable
private fun WaterfallCanvas(rows: List<FloatArray>, floor: Float, top: Float, palette: Int, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "True scrolling TCI waterfall with colour scale" }) {
        drawRect(Color.Black)
        if (rows.isEmpty()) return@Canvas
        val rowHeight = size.height / rows.size
        rows.forEachIndexed { yIndex, row ->
            val step = (row.size / 256).coerceAtLeast(1)
            for (xIndex in row.indices step step) {
                val value = ((row[xIndex] - floor) / (top - floor)).coerceIn(0f, 1f)
                val color = waterfallColor(value, palette)
                val x = size.width * xIndex / row.size
                drawRect(color, Offset(x, yIndex * rowHeight), androidx.compose.ui.geometry.Size(size.width * step / row.size + 1, rowHeight + 1))
            }
        }
    }
}

private fun waterfallColor(value: Float, palette: Int): Color = when (palette) {
    1 -> Color(value * .35f, value, .55f + value * .4f)
    2 -> Color(value * .4f, value * .85f, 1f)
    3 -> Color(value, value, value)
    else -> Color(value, value * .55f, value * .08f)
}

@Composable
fun ScannerPanel(scanner: ReceiveOnlyScannerController, memories: List<RadioPreset>, frame: PanadapterFrame?,
    tci: TciRuntimeSnapshot, dispatch: (RadioPlatformAction) -> Unit) {
    val snapshot = scanner.snapshot
    var mode by remember { mutableStateOf(scanner.config.mode) }
    var startMHz by remember { mutableStateOf("%.6f".format(scanner.config.startHz / 1_000_000.0)) }
    var endMHz by remember { mutableStateOf("%.6f".format(scanner.config.endHz / 1_000_000.0)) }
    var stepKhz by remember { mutableStateOf("%.3f".format(scanner.config.stepHz / 1_000.0)) }
    var radioMode by remember { mutableStateOf(scanner.config.radioMode) }
    var filterHz by remember { mutableStateOf(scanner.config.filterHz.toString()) }
    var skipList by remember { mutableStateOf(scanner.config.skipHz.sorted().joinToString(",")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("RECEIVE-ONLY SCANNER", color = SdrAmber, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { ScannerMode.entries.forEach { value ->
            FilterChip(mode == value, { mode = value; if (snapshot.state == ScannerState.STOPPED) scanner.updateConfig(scanner.config.copy(mode = value)) }, { Text(value.name.replace('_', ' ')) }) } }
        SdrMeasure("STATE", snapshot.state.name)
        SdrMeasure("CURRENT", formatRadioFrequency(snapshot.currentHz))
        SdrMeasure("CANDIDATES", "${snapshot.candidateCount}")
        SdrMeasure("DWELL", "${scanner.config.dwellMillis} ms · ${scanner.config.resumePolicy}")
        if (snapshot.state == ScannerState.STOPPED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(startMHz, { startMHz = it.take(14) }, label = { Text("Start MHz") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(endMHz, { endMHz = it.take(14) }, label = { Text("End MHz") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(stepKhz, { stepKhz = it.take(10) }, label = { Text("Step kHz") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(radioMode, { radioMode = it.uppercase().take(12) }, label = { Text("Mode") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(filterHz, { filterHz = it.filter(Char::isDigit).take(6) }, label = { Text("Filter Hz") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton({
                    scanner.updateConfig(scanner.config.copy(
                        mode = mode,
                        startHz = ((startMHz.toDoubleOrNull() ?: 14.0) * 1_000_000).toLong(),
                        endHz = ((endMHz.toDoubleOrNull() ?: 14.35) * 1_000_000).toLong(),
                        stepHz = ((stepKhz.toDoubleOrNull() ?: 1.0) * 1_000).toLong(),
                        radioMode = radioMode.ifBlank { "USB" },
                        filterHz = filterHz.toIntOrNull() ?: 2_700,
                        skipHz = skipList.split(',').mapNotNull { token -> token.trim().toLongOrNull() }.toSet(),
                    ))
                }) { Text("APPLY") }
            }
            OutlinedTextField(skipList, { skipList = it.take(1024) }, label = { Text("Skip frequencies · Hz, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Threshold ${scanner.config.thresholdDb.toInt()} dB · dwell ${scanner.config.dwellMillis} ms", color = SdrMuted)
            Slider(scanner.config.thresholdDb, { scanner.updateConfig(scanner.config.copy(thresholdDb = it)) }, valueRange = -140f..0f)
            Slider(scanner.config.dwellMillis.toFloat(), { scanner.updateConfig(scanner.config.copy(dwellMillis = it.toLong())) }, valueRange = 100f..10_000f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ScannerResumePolicy.entries.forEach { policy ->
                FilterChip(scanner.config.resumePolicy == policy, { scanner.updateConfig(scanner.config.copy(resumePolicy = policy)) }, { Text(policy.name.replace('_', ' ')) })
            } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ when (mode) {
                ScannerMode.MEMORY -> scanner.startMemory(memories)
                ScannerMode.RANGE -> scanner.startRange()
                ScannerMode.FFT_SPAN -> frame?.let { scanner.startFft(it.trace, tci.receivers.firstOrNull { row -> row.active }?.effectiveRxHz ?: 0, it.sampleRate.toLong()) }
            } }, enabled = snapshot.state == ScannerState.STOPPED && tci.ready) { Text("START") }
            OutlinedButton({ scanner.stop() }, enabled = snapshot.state != ScannerState.STOPPED) { Text("STOP") }
            OutlinedButton({ dispatch(RadioPlatformAction(RadioActionClass.SAFE_SET, "frequency", longValue = snapshot.currentHz)) }, enabled = snapshot.currentHz > 0) { Text("HOLD / REVIEW") }
        }
        Text("Explicit Start only. Background, profile change, disconnect, manual tune and Global Stop disarm scanning. No PTT, TUNE or TX command exists.", color = SdrHold)
    }
}

@Composable
fun RfIntelligenceWorkspace(controller: RfObservationController, existing: @Composable () -> Unit) {
    var page by remember { mutableStateOf("INTELLIGENCE") }
    Column(Modifier.fillMaxSize().background(SdrChassis)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("INTELLIGENCE", "RF MAP", "RF GLOBE").forEach { value -> FilterChip(page == value, { page = value }, { Text(value) }) }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                "RF MAP" -> RfMapGlobeScreen(controller, globe = false)
                "RF GLOBE" -> RfMapGlobeScreen(controller, globe = true)
                else -> existing()
            }
        }
    }
}

@Composable
fun DigiRfPathWrapper(controller: RfObservationController, existing: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { existing() }
        Card(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = SdrPanel)) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Text("DIGI / WSPR SELECTED PATH · sequence visualisation is not RF proof", color = SdrAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                RfCanvas(controller.filtered.take(32), globe = false, centerLat = 0.0, centerLon = 0.0,
                    zoom = 1f, longPath = false, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
fun RfMapGlobeScreen(controller: RfObservationController, globe: Boolean) {
    var filtersOpen by remember { mutableStateOf(false) }
    var centerLat by remember { mutableDoubleStateOf(-12.0) }
    var centerLon by remember { mutableDoubleStateOf(130.0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize().background(SdrChassis).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(if (globe) "INTERACTIVE ORTHOGRAPHIC RF GLOBE" else "RF EVIDENCE MAP", color = SdrAmber, fontWeight = FontWeight.Black)
                Text("${controller.filtered.size}/${controller.observations.size} bounded observations · filter ${controller.filterMillis} ms", color = SdrMuted) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton({ filtersOpen = true }) { Text("FILTERS") }
                OutlinedButton(controller::resetFilters) { Text("RESET FILTERS") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(globe) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                centerLon = normalizeUiLongitude(centerLon - pan.x / size.width * 180 / zoom)
                centerLat = (centerLat + pan.y / size.height * 90 / zoom).coerceIn(-90.0, 90.0)
                zoom = (zoom * gestureZoom).coerceIn(.7f, 6f)
            }
        }) { RfCanvas(controller.filtered, globe, centerLat, centerLon, zoom, controller.filters.longPath, Modifier.fillMaxSize()) }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("OBSERVED", color = SdrHealthy); Text("HISTORICAL", color = SdrMuted); Text("EMPIRICAL OUTLOOK", color = SdrHold)
            Text("COARSE locations are hollow · selection never tunes", color = SdrMuted)
        }
    }
    if (filtersOpen) RfFilterDialog(controller, { filtersOpen = false })
}

@Composable
private fun RfCanvas(rows: List<RfObservation>, globe: Boolean, centerLat: Double, centerLon: Double, zoom: Float,
    longPath: Boolean, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = if (globe) "Interactive RF globe with paths, control points, grayline and filters" else "Interactive flat RF map with paths, control points, grayline and filters" }) {
        drawRect(Color(0xFF071014))
        if (globe) drawCircle(Color(0xFF102C35), radius = minOf(size.width, size.height) * .46f * zoom.coerceAtMost(1.15f), center = center)
        val visible = rows.takeLast(4_096)
        visible.forEach { row ->
            val color = when (row.evidence) { RfEvidenceClass.OBSERVED -> SdrHealthy; RfEvidenceClass.HISTORICAL -> SdrMuted; RfEvidenceClass.OUTLOOK -> SdrHold }
            var previous: Offset? = null
            greatCircle(row.transmitterLatitude, row.transmitterLongitude, row.receiverLatitude, row.receiverLongitude,
                longPath, if (globe) 72 else 48).forEach { point ->
                val projected = projectRf(point.latitude, point.longitude, globe, centerLat, centerLon, zoom, size.width, size.height)
                val prior = previous
                if (projected != null && prior != null && (globe || kotlin.math.abs(projected.x - prior.x) < size.width * .45f)) {
                    drawLine(color.copy(alpha = .34f), prior, projected, 1.dp.toPx(), StrokeCap.Round)
                }
                previous = projected
            }
            val receiver = projectRf(row.receiverLatitude, row.receiverLongitude, globe, centerLat, centerLon, zoom, size.width, size.height)
            if (receiver != null) drawCircle(if (row.precision == RfPrecision.COARSE) Color.Transparent else color, 2.5.dp.toPx(), receiver,
                style = if (row.precision == RfPrecision.COARSE) Stroke(1.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
            propagationControlPoints(row, longPath).forEach { point ->
                projectRf(point.latitude, point.longitude, globe, centerLat, centerLon, zoom, size.width, size.height)?.let {
                    drawCircle(color.copy(alpha = .8f), 1.8.dp.toPx(), it)
                }
            }
        }
        val stationRow = rows.lastOrNull()
        val station = stationRow?.let { projectRf(it.receiverLatitude, it.receiverLongitude, globe, centerLat, centerLon, zoom, size.width, size.height) }
        if (station != null) drawCircle(SdrAmber, 6.dp.toPx(), station)
        drawLine(SdrHold.copy(alpha = .3f), Offset(0f, size.height * .45f), Offset(size.width, size.height * .62f), 18.dp.toPx())
    }
}

private fun projectRf(latitude: Double, longitude: Double, globe: Boolean, centerLat: Double, centerLon: Double,
    zoom: Float, width: Float, height: Float): Offset? {
    if (!globe) return Offset(((normalizeUiLongitude(longitude - centerLon) + 180) / 360 * width * zoom).toFloat(),
        ((90 - latitude + centerLat) / 180 * height * zoom).toFloat())
    val lat = Math.toRadians(latitude); val lon = Math.toRadians(longitude - centerLon); val cLat = Math.toRadians(centerLat)
    val z = sin(cLat) * sin(lat) + cos(cLat) * cos(lat) * cos(lon)
    if (z < 0) return null
    val radius = minOf(width, height) * .46f * zoom
    val x = radius * cos(lat).toFloat() * sin(lon).toFloat()
    val y = radius * (cos(cLat) * sin(lat) - sin(cLat) * cos(lat) * cos(lon)).toFloat()
    return Offset(width / 2 + x, height / 2 - y)
}

@Composable
private fun RfFilterDialog(controller: RfObservationController, close: () -> Unit) {
    var draft by remember { mutableStateOf(controller.filters) }
    AlertDialog(onDismissRequest = close, title = { Text("RF evidence filters") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.callsign, { draft = draft.copy(callsign = it.take(24)) }, label = { Text("Callsign") })
            fun words(value: String) = value.split(',').map { it.trim().uppercase(Locale.US) }.filter(String::isNotBlank).toSet()
            OutlinedTextField(draft.sources.sorted().joinToString(","), { draft = draft.copy(sources = words(it)) },
                label = { Text("Sources · comma separated") })
            OutlinedTextField(draft.bands.sorted().joinToString(","), { draft = draft.copy(bands = words(it)) },
                label = { Text("Bands / all bands") })
            OutlinedTextField(draft.modes.sorted().joinToString(","), { draft = draft.copy(modes = words(it)) },
                label = { Text("Modes") })
            OutlinedTextField(draft.entities.sorted().joinToString(","), { draft = draft.copy(entities = words(it)) },
                label = { Text("DXCC / entity") })
            OutlinedTextField(draft.continents.sorted().joinToString(","), { draft = draft.copy(continents = words(it)) },
                label = { Text("Continents") })
            OutlinedTextField(draft.receiverRegions.sorted().joinToString(","), { draft = draft.copy(receiverRegions = words(it)) },
                label = { Text("Receive regions") })
            OutlinedTextField(draft.transmitterRegions.sorted().joinToString(","), { draft = draft.copy(transmitterRegions = words(it)) },
                label = { Text("Transmit regions") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.minimumDistanceKm?.toInt()?.toString().orEmpty(),
                    { draft = draft.copy(minimumDistanceKm = it.toDoubleOrNull()) }, label = { Text("Min km") }, modifier = Modifier.weight(1f))
                OutlinedTextField(draft.maximumDistanceKm?.toInt()?.toString().orEmpty(),
                    { draft = draft.copy(maximumDistanceKm = it.toDoubleOrNull()) }, label = { Text("Max km") }, modifier = Modifier.weight(1f))
                OutlinedTextField(draft.minimumBearingDegrees?.toInt()?.toString().orEmpty(),
                    { draft = draft.copy(minimumBearingDegrees = it.toDoubleOrNull()) }, label = { Text("Bearing from") }, modifier = Modifier.weight(1f))
                OutlinedTextField(draft.maximumBearingDegrees?.toInt()?.toString().orEmpty(),
                    { draft = draft.copy(maximumBearingDegrees = it.toDoubleOrNull()) }, label = { Text("Bearing to") }, modifier = Modifier.weight(1f))
            }
            Text("EVIDENCE", color = SdrAmber); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RfEvidenceClass.entries.forEach { value ->
                FilterChip(value in draft.evidence, { draft = draft.copy(evidence = if (value in draft.evidence) draft.evidence - value else draft.evidence + value) }, { Text(value.name) }) } }
            Text("OUTLOOK WINDOWS", color = SdrAmber); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 60, 120).forEach { value ->
                FilterChip(value in draft.outlookMinutes, { draft = draft.copy(outlookMinutes = if (value in draft.outlookMinutes) draft.outlookMinutes - value else draft.outlookMinutes + value) }, { Text("$value min") }) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.contestMultipliersOnly, { draft = draft.copy(contestMultipliersOnly = it) }); Text("Contest multipliers only") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.hideContestDuplicates, { draft = draft.copy(hideContestDuplicates = it) }); Text("Hide contest duplicates") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.longPath, { draft = draft.copy(longPath = it) }); Text("Explicit long path") }
            Text("Time window · ${draft.maximumAgeSeconds / 60} minutes", color = SdrMuted)
            Slider(draft.maximumAgeSeconds.toFloat(), { draft = draft.copy(maximumAgeSeconds = it.toLong()) }, valueRange = 300f..7_200f)
        } }, confirmButton = { Button({ controller.updateFilters(draft); close() }) { Text("APPLY") } },
        dismissButton = { TextButton(close) { Text("CANCEL") } })
}

@Composable
fun SdrSettingsPanel(runtime: TciRuntimeState, rxAudio: TciRxAudioController, scanner: ReceiveOnlyScannerController, rf: RfObservationController,
    announcements: SpokenAnnouncementController, bandStacks: BandStackStore, debugLab: DebugSdrLab?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrRaised)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TCI / MULTI-RECEIVER / RX AUDIO", color = SdrAmber, fontWeight = FontWeight.Bold)
            Text("${runtime.snapshot.state} · ${runtime.snapshot.receivers.size} receivers · restore is always disconnected with IQ, RX audio and scanner stopped", color = SdrMuted)
            Text("RX output route starts only after an explicit operator action. TLS certificate validation is never disabled.", color = SdrHold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(rxAudio.status, color = if (rxAudio.running) SdrHealthy else SdrMuted, modifier = Modifier.weight(1f))
                if (rxAudio.running) OutlinedButton({ rxAudio.stop() }) { Text("STOP RX AUDIO") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rxAudio.settings.noiseBlanker, { rxAudio.update(rxAudio.settings.copy(noiseBlanker = it)) })
                Text("Impulse blanker")
                Checkbox(rxAudio.settings.automaticNotch, { rxAudio.update(rxAudio.settings.copy(automaticNotch = it)) })
                Text("Automatic notch")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rxAudio.settings.agc, { rxAudio.update(rxAudio.settings.copy(agc = it)) })
                Text("AGC with bounded hang")
            }
            Text("SPECTRAL NOISE REDUCTION", color = SdrMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("OFF" to 0f, "LOW" to .25f, "MEDIUM" to .55f, "HIGH" to .85f).forEach { (label, level) ->
                    FilterChip(kotlin.math.abs(rxAudio.settings.noiseReduction - level) < .05f,
                        { rxAudio.update(rxAudio.settings.copy(noiseReduction = level)) }, { Text(label) })
                }
            }
            Text("AGC hang · ${rxAudio.settings.agcHangMillis} ms", color = SdrMuted)
            Slider(rxAudio.settings.agcHangMillis.toFloat(),
                { rxAudio.update(rxAudio.settings.copy(agcHangMillis = it.toInt())) }, valueRange = 0f..2_000f)
            Text("Squelch · ${rxAudio.settings.squelchDb.toInt()} dB", color = SdrMuted)
            Slider(rxAudio.settings.squelchDb,
                { rxAudio.update(rxAudio.settings.copy(squelchDb = it)) }, valueRange = -120f..-20f)
            Text("Stereo mix · ${"%.1f".format(rxAudio.settings.stereoMix)}", color = SdrMuted)
            Slider(rxAudio.settings.stereoMix,
                { rxAudio.update(rxAudio.settings.copy(stereoMix = it)) }, valueRange = -1f..1f)
            if (BuildConfig.DEBUG && debugLab != null) OutlinedButton({ if (debugLab.active) debugLab.stop() else debugLab.start() }) { Text(if (debugLab.active) "STOP DEMO · NO RADIO" else "START DEMO · NO RADIO") }
        } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrRaised)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SCANNER", color = SdrAmber, fontWeight = FontWeight.Bold)
            Text("${scanner.config.mode} · ${scanner.config.startHz}–${scanner.config.endHz} Hz · ${scanner.config.resumePolicy}", color = SdrMuted)
            Text("Active scanning is never persisted or restored.", color = SdrHold)
        } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrRaised)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RF MAP / GLOBE", color = SdrAmber, fontWeight = FontWeight.Bold)
            Text("${rf.observations.size} observations · ${rf.filtered.size} visible · live, historical and empirical outlook remain separate", color = SdrMuted)
            Text("100,000-row cap · batched Canvas rendering · no WebView, tile service or automatic tune", color = SdrHold)
        } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrRaised)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SPOKEN ANNOUNCEMENTS · SYSTEM TTS", color = SdrAmber, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (announcements.available) "System voice available" else "System TTS unavailable", color = if (announcements.available) SdrHealthy else SdrHold)
                Switch(announcements.settings.enabled, { announcements.update(announcements.settings.copy(enabled = it)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(announcements.settings.frequency, { announcements.update(announcements.settings.copy(frequency = it)) }); Text("Frequency after tuning settles") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(announcements.settings.bandMode, { announcements.update(announcements.settings.copy(bandMode = it)) }); Text("Band and mode changes") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(announcements.settings.routeLoss, { announcements.update(announcements.settings.copy(routeLoss = it)) }); Text("Critical route loss") }
            Text("Speech rate · ${"%.1f".format(announcements.settings.speechRate)}", color = SdrMuted)
            Slider(announcements.settings.speechRate, { announcements.update(announcements.settings.copy(speechRate = it)) }, valueRange = .5f..2f)
            Text("Suppressed during TX, voice macros and quiet profile. Global Stop cancels speech. TTS can never feed radio TX audio.", color = SdrHold)
        } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrRaised)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BAND STACKS", color = SdrAmber, fontWeight = FontWeight.Bold)
            Text("Bounded depth · ${bandStacks.depth}", color = SdrMuted)
            Slider(bandStacks.depth.toFloat(), { bandStacks.updateDepth(it.toInt()) }, valueRange = 1f..12f, steps = 10)
            Text("Band stacks store frequency, mode, filter, receiver and timestamp. Recall is explicit and never starts a connection.", color = SdrHold)
        } }
    }
}

@Composable
fun SdrHealthPanel(runtime: TciRuntimeState, rxAudio: TciRxAudioController, panadapter: PanadapterController, scanner: ReceiveOnlyScannerController,
    rf: RfObservationController, announcements: SpokenAnnouncementController) {
    val tci = runtime.snapshot
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ANDROID SDR HEALTH", color = SdrAmber, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SdrHealthCard("TCI", "${tci.state} · ${tci.protocol} ${tci.protocolVersion}", tci.ready, Modifier.weight(1f))
            SdrHealthCard("RX DSP", "${if (rxAudio.running) "LIVE" else "STOPPED"} · out ${rxAudio.outputLevelDb.toInt()} dB · GR ${rxAudio.gainReductionDb.toInt()} dB · notch ${rxAudio.notchFrequencyHz.toInt()} Hz · blank ${rxAudio.blankedImpulses} · drop ${rxAudio.droppedFrames}/${rxAudio.underflowFrames} · ${"%.1f".format(rxAudio.processingLatencyMs)} ms",
                rxAudio.clippedFraction < .02f, Modifier.weight(1f))
            SdrHealthCard("RECEIVERS", "${tci.receivers.size} · IQ drop ${tci.droppedFrames}", tci.droppedFrames == 0L, Modifier.weight(1f))
            SdrHealthCard("PANADAPTER", "${panadapter.tciDisplays.size} TCI contexts · ${panadapter.status}", panadapter.tciDisplays.isNotEmpty(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SdrHealthCard("SCANNER", "${scanner.snapshot.state} · ${scanner.snapshot.stopReason}", scanner.snapshot.state != ScannerState.ERROR, Modifier.weight(1f))
            SdrHealthCard("RF GLOBE", "${rf.filtered.size}/${rf.observations.size} · ${rf.filterMillis} ms", rf.observations.size <= 100_000, Modifier.weight(1f))
            SdrHealthCard("TTS", if (announcements.available) "AVAILABLE · ${if (announcements.speaking) "SPEAKING" else "IDLE"}" else "UNAVAILABLE", announcements.available, Modifier.weight(1f))
        }
        Text("Support metrics are bounded and sanitized. Raw IQ, raw audio, raw TCI payloads, credentials, private endpoint values and precise callsign coordinates are excluded.", color = SdrMuted)
    }
}

@Composable
fun TciProfileDialog(app: AppController, close: () -> Unit) {
    var name by remember { mutableStateOf("TCI Radio") }
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("50001") }
    var secure by remember { mutableStateOf(false) }
    var rate by remember { mutableIntStateOf(96_000) }
    var receiver by remember { mutableIntStateOf(0) }
    val valid = name.isNotBlank() && host.isNotBlank() && host.none { it.isWhitespace() || it == '/' } && port.toIntOrNull() in 1..65_535
    AlertDialog(onDismissRequest = close, title = { Text("Add TCI radio") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profile creation never connects. Android TCI v1 remains receive-only and TX locked.", color = SdrHold)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Display name") }, singleLine = true)
            OutlinedTextField(host, { host = it.take(253) }, label = { Text("Host") }, singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(secure, { secure = it }); Text(if (secure) "wss · normal certificate validation" else "ws") }
            Text("Preferred I/Q rate", color = SdrAmber); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(48_000, 96_000, 192_000).forEach { value ->
                FilterChip(rate == value, { rate = value }, { Text("${value / 1000} kHz") }) } }
            Text("Initial receiver", color = SdrAmber); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..1).forEach { value ->
                FilterChip(receiver == value, { receiver = value }, { Text("RX ${value + 1}") }) } }
            Text("Safe reconnect defaults OFF. RX audio and IQ never restore active.", color = SdrMuted)
        } },
        confirmButton = { Button({
            val id = "tci.${name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48)}.${System.currentTimeMillis().toString(36)}"
            app.upsertTciProfile(RadioConnectionProfile(RadioProfileId(id), name.trim(), RadioBackendKind.NATIVE_TCI,
                RadioModelId("TCI:${id.take(80)}"), "TCI", "TCI receive-only server", RadioTransportType.TCI,
                host = host.trim(), port = port.toInt(), readOnly = false, automaticSafeReconnect = false,
                secureWebSocket = secure, preferredIqSampleRate = rate, preferredInitialReceiver = receiver))
            close()
        }, enabled = valid) { Text("ADD DISCONNECTED") } },
        dismissButton = { TextButton(close) { Text("CANCEL") } })
}

@Composable private fun SdrTruthChip(label: String, healthy: Boolean) = Surface(color = (if (healthy) SdrHealthy else SdrHold).copy(alpha = .13f), shape = RoundedCornerShape(40)) {
    Text(label, color = if (healthy) SdrHealthy else SdrHold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable private fun SdrMeasure(label: String, value: String) = Column { Text(label, color = SdrMuted, fontSize = 9.sp); Text(value, color = SdrInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }

@Composable private fun SdrEmptyState(title: String, detail: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SdrPanel)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(title, color = SdrInk, fontWeight = FontWeight.Bold); Text(detail, color = SdrMuted) }
}

@Composable private fun SdrHealthCard(title: String, detail: String, healthy: Boolean, modifier: Modifier) = Card(modifier, colors = CardDefaults.cardColors(containerColor = SdrRaised)) {
    Column(Modifier.padding(9.dp)) { Text(title, color = if (healthy) SdrHealthy else SdrHold, fontWeight = FontWeight.Bold); Text(detail, color = SdrMuted, fontSize = 10.sp) }
}

private fun normalizeUiLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

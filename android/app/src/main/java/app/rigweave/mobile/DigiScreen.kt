package app.rigweave.mobile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/*
THESIS: Nexus's cockpit model on Android: scope, working panes, and an immovable TX dock.
OWN-WORLD: deep navy instrument surfaces, cyan navigation, green RX, amber sequence, red stop.
STORY: choose a cockpit, monitor receive, compose one transmission, arm deliberately, send,
and retain an immediate path back to RX.
FIRST VIEWPORT: compact radio truth and mode rail, bounded scope, mode-specific panes, fixed dock.
FORM: Nexus cockpit translated to adaptive Compose; panes reflow by measured width and never clip.
*/

private val NexusBg = Color(0xFF061016)
private val NexusPanel = Color(0xFF0B171E)
private val NexusRaised = Color(0xFF10242D)
private val NexusInset = Color(0xFF040B0F)
private val NexusLine = Color(0xFF29424D)
private val NexusText = Color(0xFFEAF4F6)
private val NexusMuted = Color(0xFF93AAB4)
private val NexusCyan = Color(0xFF35D3E7)
private val NexusGreen = Color(0xFF43DD8B)
private val NexusAmber = Color(0xFFFFB347)
private val NexusRed = Color(0xFFFF5B66)
private val NexusBlue = Color(0xFF65A9FF)
private val CockpitShape = RoundedCornerShape(6.dp)

@Composable
fun DigiScreen(controller: DigiController, radio: RadioState, compact: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(NexusBg).navigationBarsPadding()) {
        val wide = !compact && maxWidth >= 900.dp
        Column(Modifier.fillMaxSize()) {
            DigiCockpitHeader(controller, radio)
            DigitalModeRail(controller)
            HorizontalDivider(color = NexusLine)
            when (controller.mode) {
                DigiMode.SSTV -> SstvCockpit(controller, wide, Modifier.weight(1f))
                DigiMode.CW, DigiMode.RTTY, DigiMode.PSK31 ->
                    StreamCockpit(controller, wide, Modifier.weight(1f))
                else -> WeakSignalCockpit(controller, wide, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DigiCockpitHeader(controller: DigiController, radio: RadioState) {
    FlowRow(
        Modifier.fillMaxWidth().background(NexusPanel).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 150.dp)) {
            Text("DIGITAL", color = NexusCyan, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(controller.mode.label, color = NexusText, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        HeaderTruth("RADIO", if (radio.connected) "${radio.model} · ${radio.frequencyText}" else "OFFLINE", if (radio.connected) NexusGreen else NexusRed)
        HeaderTruth("RX", if (controller.rxActive) "LIVE" else "STOPPED", if (controller.rxActive) NexusGreen else NexusMuted)
        HeaderTruth("TX", when (controller.txPhase) {
            DigiTxPhase.PTT_CONFIRMED -> "ON AIR"
            DigiTxPhase.SEQUENCING -> "QUEUED"
            DigiTxPhase.SAFE -> if (controller.txArmed) "ARMED" else "SAFE"
        }, when {
            controller.txPhase == DigiTxPhase.PTT_CONFIRMED -> NexusRed
            controller.txActive || controller.txArmed -> NexusAmber
            else -> NexusMuted
        })
        Spacer(Modifier.weight(1f))
        CockpitButton(if (controller.rxActive) "STOP RX" else "START RX", if (controller.rxActive) NexusAmber else NexusGreen) {
            if (controller.rxActive) controller.stopRx() else controller.startRx()
        }
        CockpitButton("CLEAR", NexusBlue, filled = false) { controller.clear() }
        CockpitButton("STOP TX", NexusRed, enabled = controller.txActive || controller.txArmed) { controller.haltTx() }
    }
}

@Composable
private fun HeaderTruth(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Column {
            Text(label, color = NexusMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = NexusText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun DigitalModeRail(controller: DigiController) {
    val weak = controller.mode.isSlotted
    Row(
        Modifier.fillMaxWidth().background(NexusInset).horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NexusModeTab("FT", weak) { controller.selectMode(DigiMode.FT8) }
        NexusModeTab("CW", controller.mode == DigiMode.CW) { controller.selectMode(DigiMode.CW) }
        NexusModeTab("RTTY", controller.mode == DigiMode.RTTY) { controller.selectMode(DigiMode.RTTY) }
        NexusModeTab("PSK", controller.mode == DigiMode.PSK31) { controller.selectMode(DigiMode.PSK31) }
        NexusModeTab("SSTV", controller.mode == DigiMode.SSTV) { controller.selectMode(DigiMode.SSTV) }
        Spacer(Modifier.width(6.dp))
        Text("NEXUS COCKPIT", color = NexusMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun NexusModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Black) },
        modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = NexusPanel,
            labelColor = NexusMuted,
            selectedContainerColor = NexusCyan,
            selectedLabelColor = NexusBg,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = NexusLine,
            selectedBorderColor = NexusCyan,
        ),
    )
}

@Composable
private fun WeakSignalCockpit(controller: DigiController, wide: Boolean, modifier: Modifier) {
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WeakModePicker(controller)
        SignalScope(controller)
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecodePane(controller, Modifier.weight(1.65f).fillMaxHeight())
                WeakTxPane(controller, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DecodePane(controller, Modifier.fillMaxWidth().requiredHeightIn(min = 280.dp))
                WeakTxPane(controller, Modifier.fillMaxWidth().requiredHeightIn(min = 300.dp))
            }
        }
        WeakSignalDock(controller)
    }
}

@Composable
private fun WeakModePicker(controller: DigiController) {
    val families = DigiModeFamilies.filter { it.isSlotted }
    Column(Modifier.fillMaxWidth().background(NexusPanel, CockpitShape).border(1.dp, NexusLine, CockpitShape).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            families.forEach { value ->
                FilterChip(
                    selected = controller.mode.family == value.family,
                    onClick = { controller.selectMode(value) },
                    label = { Text(value.family, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusBlue, selectedLabelColor = NexusBg),
                )
            }
        }
        val variants = DigiMode.entries.filter { it.family == controller.mode.family }
        if (variants.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                variants.forEach { value ->
                    FilterChip(
                        selected = controller.mode == value,
                        onClick = { controller.selectMode(value) },
                        label = { Text(value.label.removePrefix("${value.family}-").removePrefix(value.family).ifBlank { value.label }) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusAmber, selectedLabelColor = NexusBg),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalScope(controller: DigiController) {
    Column(Modifier.fillMaxWidth().background(NexusInset, CockpitShape).border(1.dp, NexusLine, CockpitShape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AUDIO SCOPE", color = NexusCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                if (controller.mode.isSlotted) "${(controller.slotProgress * 100).roundToInt()}% · ${controller.mode.slotMillis / 1_000.0}s UTC" else controller.status,
                color = if (controller.rxActive) NexusGreen else NexusMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            val baseline = size.height * 0.72f
            drawLine(NexusLine, Offset(0f, baseline), Offset(size.width, baseline), 2f)
            repeat(13) { index ->
                val x = size.width * index / 12f
                drawLine(NexusLine, Offset(x, baseline - if (index % 3 == 0) 12f else 7f), Offset(x, baseline + 4f), 1f)
            }
            if (controller.mode.isSlotted) {
                val x = size.width * controller.slotProgress.coerceIn(0f, 1f)
                drawLine(if (controller.txActive) NexusRed else NexusAmber, Offset(x, 0f), Offset(x, size.height), 3f)
            }
            if (controller.rxActive) drawCircle(NexusGreen, 6f, Offset(size.width * 0.08f, baseline - 16f), style = Stroke(2f))
        }
    }
}

@Composable
private fun DecodePane(controller: DigiController, modifier: Modifier) {
    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(controller::decodeRecording)
    }
    CockpitPane("DECODE", if (controller.rxActive) "LIVE" else "RX STOPPED", NexusGreen, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CockpitButton("OPEN WAV", NexusBlue, filled = false, modifier = Modifier.weight(1f)) { recordingPicker.launch("audio/*") }
            CockpitButton("ERASE", NexusMuted, filled = false) { controller.clear() }
        }
        HorizontalDivider(color = NexusLine)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            TableHead("SNR", Modifier.width(48.dp))
            TableHead("DT", Modifier.width(54.dp))
            TableHead("AUDIO", Modifier.width(68.dp))
            TableHead("MESSAGE", Modifier.weight(1f))
        }
        if (controller.decodedRows.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(NexusInset), contentAlignment = Alignment.Center) {
                Text(
                    if (controller.rxActive) "Listening for ${controller.mode.label}\nDecoded messages appear here." else "Start RX or open a reference WAV.",
                    color = NexusMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).background(NexusInset)) {
                controller.decodedRows.asReversed().forEachIndexed { index, row ->
                    Row(Modifier.fillMaxWidth().background(if (index % 2 == 0) NexusInset else NexusPanel).padding(horizontal = 4.dp, vertical = 8.dp)) {
                        DecodeCell("%+.0f".format(row.snrDb), Modifier.width(48.dp), if (row.snrDb >= 0) NexusGreen else NexusText)
                        DecodeCell("%+.1f".format(row.dtSeconds), Modifier.width(54.dp))
                        DecodeCell("%.0f".format(row.frequencyHz), Modifier.width(68.dp), NexusCyan)
                        DecodeCell(row.text, Modifier.weight(1f), NexusText)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakTxPane(controller: DigiController, modifier: Modifier) {
    CockpitPane("TX MESSAGES", if (controller.txArmed) "ARMED ONCE" else "SAFE", NexusAmber, modifier) {
        if (controller.mode != DigiMode.WSPR) {
            NexusTextField(controller.dxCall, controller::updateDxCall, "DX CALL", singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("CQ", "TX 1", "TX 2", "TX 3", "RR73", "73").forEachIndexed { index, label ->
                    CockpitButton(label, if (index == 0) NexusGreen else NexusBlue, filled = false) { controller.applyStandardMessage(index) }
                }
            }
        }
        NexusTextField(
            controller.txText,
            controller::updateTxText,
            if (controller.mode == DigiMode.WSPR) "CALL GRID DBM" else "NEXT MESSAGE",
            singleLine = controller.mode.isSlotted,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = !controller.mode.isSlotted),
        )
        if (controller.lastTxText.isNotBlank()) {
            Text("SENT · ${controller.lastTxText}", color = NexusMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        Text(controller.status, color = if (controller.status.contains("UNCONFIRMED")) NexusRed else NexusMuted, fontSize = 11.sp)
    }
}

@Composable
private fun WeakSignalDock(controller: DigiController) {
    Row(
        Modifier.fillMaxWidth().background(NexusRaised, CockpitShape).border(1.dp, NexusLine, CockpitShape)
            .horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CockpitButton("CALL CQ", NexusGreen, filled = false) { controller.applyStandardMessage(0) }
        if (controller.mode != DigiMode.WSPR) CockpitButton("ANSWER", NexusBlue, filled = false) { controller.applyStandardMessage(1) }
        VerticalDivider(Modifier.height(34.dp), color = NexusLine)
        CockpitButton(if (controller.txArmed) "TX ARMED" else "ARM TX", NexusAmber) { controller.arm() }
        CockpitButton("SEND ${controller.mode.label}", NexusGreen, enabled = controller.txArmed && !controller.txActive) { controller.send() }
        CockpitButton("STOP TX", NexusRed, enabled = controller.txActive || controller.txArmed) { controller.haltTx() }
        Text(
            when (controller.txPhase) {
                DigiTxPhase.PTT_CONFIRMED -> "ON AIR"
                DigiTxPhase.SEQUENCING -> "WAITING FOR UTC SLOT"
                DigiTxPhase.SAFE -> if (controller.txArmed) "ONE TRANSMISSION ARMED" else "RECEIVE ONLY"
            },
            color = if (controller.txActive) NexusRed else if (controller.txArmed) NexusAmber else NexusMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun StreamCockpit(controller: DigiController, wide: Boolean, modifier: Modifier) {
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StreamControls(controller)
        SignalScope(controller)
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranscriptPane(controller, Modifier.weight(1.55f).fillMaxHeight())
                StreamTxPane(controller, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TranscriptPane(controller, Modifier.fillMaxWidth().requiredHeightIn(min = 260.dp))
                StreamTxPane(controller, Modifier.fillMaxWidth().requiredHeightIn(min = 290.dp))
            }
        }
        StreamDock(controller)
    }
}

@Composable
private fun StreamControls(controller: DigiController) {
    Column(Modifier.fillMaxWidth().background(NexusPanel, CockpitShape).border(1.dp, NexusLine, CockpitShape).padding(8.dp)) {
        when (controller.mode) {
            DigiMode.CW -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PITCH · ${controller.cwPitchHz.roundToInt()} HZ", color = NexusCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Slider(controller.cwPitchHz, controller::updateCwPitch, valueRange = 400f..1_000f, modifier = Modifier.semantics { contentDescription = "CW pitch" })
                }
                Column(Modifier.weight(1f)) {
                    Text("KEYER · ${controller.cwWpm} WPM", color = NexusAmber, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Slider(controller.cwWpm.toFloat(), { controller.updateCwWpm(it.roundToInt()) }, valueRange = 8f..45f, steps = 36, modifier = Modifier.semantics { contentDescription = "CW speed" })
                }
            }
            DigiMode.RTTY -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BAUDOT · 45.45 BAUD · 170 HZ SHIFT", color = NexusCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("AFC ${"%+.1f".format(controller.rttyAfcHz)} Hz · ${if (controller.rttyAfcLocked) "LOCKED" else "SEARCHING"}", color = if (controller.rttyAfcLocked) NexusGreen else NexusAmber, fontSize = 11.sp)
                }
                Text("REVERSE", color = NexusMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Switch(controller.rttyReverse, controller::updateRttyReverse)
            }
            DigiMode.PSK31 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("BPSK31 · 31.25 BAUD · VARICODE", color = NexusCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("CARRIER ${"%.1f".format(controller.pskCarrierHz)} HZ", color = NexusGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            else -> Unit
        }
    }
}

@Composable
private fun TranscriptPane(controller: DigiController, modifier: Modifier) {
    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(controller::decodeRecording) }
    CockpitPane("RECEIVE", if (controller.rxActive) "STREAMING" else "STOPPED", NexusGreen, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CockpitButton("OPEN WAV", NexusBlue, filled = false, modifier = Modifier.weight(1f)) { recordingPicker.launch("audio/*") }
            CockpitButton("ERASE", NexusMuted, filled = false) { controller.clear() }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(NexusInset).border(1.dp, NexusLine).padding(10.dp)) {
            Text(
                controller.transcript.ifBlank { if (controller.rxActive) "Listening…" else "Start RX or open a reference WAV." },
                color = if (controller.transcript.isBlank()) NexusMuted else NexusText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun StreamTxPane(controller: DigiController, modifier: Modifier) {
    CockpitPane("TRANSMIT", if (controller.txArmed) "ARMED ONCE" else "KEYBOARD", NexusAmber, modifier) {
        NexusTextField(controller.txText, controller::updateTxText, "TYPE TO SEND", singleLine = false, modifier = Modifier.fillMaxWidth().weight(1f))
        if (controller.lastTxText.isNotBlank()) {
            Text("LAST SENT", color = NexusMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(controller.lastTxText, color = NexusGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 3)
        }
        Text(controller.status, color = if (controller.status.contains("UNCONFIRMED")) NexusRed else NexusMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StreamDock(controller: DigiController) {
    Row(
        Modifier.fillMaxWidth().background(NexusRaised, CockpitShape).border(1.dp, NexusLine, CockpitShape)
            .horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("CQ", "DE", "599", "RR", "TU", "73", "K").forEach { token ->
            CockpitButton(token, NexusBlue, filled = false) { controller.appendTxText(token) }
        }
        VerticalDivider(Modifier.height(34.dp), color = NexusLine)
        CockpitButton(if (controller.txArmed) "TX ARMED" else "ARM TX", NexusAmber) { controller.arm() }
        CockpitButton("SEND", NexusGreen, enabled = controller.txArmed && !controller.txActive) { controller.send() }
        CockpitButton("STOP TX", NexusRed, enabled = controller.txActive || controller.txArmed) { controller.haltTx() }
    }
}

@Composable
private fun SstvCockpit(controller: DigiController, wide: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selected ->
            val bitmap = runCatching {
                context.contentResolver.openInputStream(selected)?.use(BitmapFactory::decodeStream)
            }.getOrNull()
            if (bitmap != null) controller.setSstvSource(bitmap)
        }
    }
    val recordingPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(controller::decodeRecording)
    }
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().background(NexusPanel, CockpitShape).border(1.dp, NexusLine, CockpitShape)
                .horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SstvChoices.forEach { choice ->
                FilterChip(
                    selected = controller.sstvChoice == choice,
                    onClick = { controller.selectSstv(choice) },
                    label = { Text(choice.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NexusAmber, selectedLabelColor = NexusBg),
                )
            }
        }
        if (wide) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SstvRxPane(controller, { recordingPicker.launch("audio/*") }, Modifier.weight(1f).fillMaxHeight())
                SstvTxPane(controller, { imagePicker.launch("image/*") }, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SstvRxPane(controller, { recordingPicker.launch("audio/*") }, Modifier.fillMaxWidth().requiredHeightIn(min = 320.dp))
                SstvTxPane(controller, { imagePicker.launch("image/*") }, Modifier.fillMaxWidth().requiredHeightIn(min = 320.dp))
            }
        }
        SstvDock(controller)
    }
}

@Composable
private fun SstvRxPane(controller: DigiController, openRecording: () -> Unit, modifier: Modifier) {
    val revision = controller.imageRevision
    val image = remember(revision) { controller.currentSstvBitmap()?.asImageBitmap() }
    CockpitPane("SSTV RECEIVE", when {
        controller.sstvComplete -> "COMPLETE"
        controller.sstvLine >= 0 -> "LINE ${controller.sstvLine + 1}"
        else -> "WAITING FOR VIS"
    }, NexusGreen, modifier) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black).border(1.dp, NexusLine), contentAlignment = Alignment.Center) {
            if (image != null) Image(image, "Decoded SSTV image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("Waiting for a valid VIS header", color = NexusMuted, fontFamily = FontFamily.Monospace)
        }
        CockpitButton("OPEN WAV", NexusBlue, filled = false, modifier = Modifier.fillMaxWidth(), onClick = openRecording)
        if (controller.sstvFskId.isNotBlank()) Text("FSK ID · ${controller.sstvFskId}", color = NexusGreen, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SstvTxPane(controller: DigiController, openImage: () -> Unit, modifier: Modifier) {
    val revision = controller.sourceRevision
    val source = remember(revision) { controller.currentSstvSourceBitmap()?.asImageBitmap() }
    CockpitPane("SSTV TRANSMIT", if (controller.sourceReady) "IMAGE READY" else "NO IMAGE", NexusAmber, modifier) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black).border(1.dp, NexusLine), contentAlignment = Alignment.Center) {
            if (source != null) Image(source, "SSTV transmit image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("Choose an image to prepare ${controller.sstvChoice.label}", color = NexusMuted, fontFamily = FontFamily.Monospace)
        }
        CockpitButton("CHOOSE IMAGE", NexusBlue, filled = false, modifier = Modifier.fillMaxWidth(), onClick = openImage)
        Text("${controller.sstvChoice.label} · ${controller.sstvChoice.width} × ${controller.sstvChoice.height}", color = NexusMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SstvDock(controller: DigiController) {
    Row(
        Modifier.fillMaxWidth().background(NexusRaised, CockpitShape).border(1.dp, NexusLine, CockpitShape)
            .horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CockpitButton(if (controller.rxActive) "STOP RX" else "START RX", if (controller.rxActive) NexusAmber else NexusGreen) {
            if (controller.rxActive) controller.stopRx() else controller.startRx()
        }
        CockpitButton(if (controller.txArmed) "TX ARMED" else "ARM TX", NexusAmber, enabled = controller.sourceReady) { controller.arm() }
        CockpitButton("SEND IMAGE", NexusGreen, enabled = controller.sourceReady && controller.txArmed && !controller.txActive) { controller.send() }
        CockpitButton("STOP", NexusRed, enabled = controller.txActive || controller.txArmed) { controller.haltTx() }
        Text(controller.status, color = if (controller.status.contains("UNCONFIRMED")) NexusRed else NexusMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CockpitPane(
    title: String,
    state: String,
    accent: Color,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(NexusPanel, CockpitShape).border(1.dp, NexusLine, CockpitShape)) {
        Row(
            Modifier.fillMaxWidth().background(NexusRaised).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(state, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CockpitButton(
    label: String,
    color: Color,
    filled: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = CockpitShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = NexusBg,
                disabledContainerColor = NexusLine,
                disabledContentColor = NexusMuted,
            ),
        ) { Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            shape = CockpitShape,
            border = BorderStroke(1.dp, if (enabled) color else NexusLine),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (enabled) color else NexusMuted),
        ) { Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp) }
    }
}

@Composable
private fun NexusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        shape = CockpitShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NexusText,
            unfocusedTextColor = NexusText,
            focusedBorderColor = NexusCyan,
            unfocusedBorderColor = NexusLine,
            focusedLabelColor = NexusCyan,
            unfocusedLabelColor = NexusMuted,
            cursorColor = NexusCyan,
            focusedContainerColor = NexusInset,
            unfocusedContainerColor = NexusInset,
        ),
    )
}

@Composable
private fun TableHead(text: String, modifier: Modifier) {
    Text(text, modifier.padding(vertical = 4.dp), color = NexusMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
}

@Composable
private fun DecodeCell(text: String, modifier: Modifier, color: Color = NexusText) {
    Text(text, modifier, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

package app.rigweave.mobile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlin.math.roundToInt

/*
THESIS: A cycle-sequencer console, not a desktop modem squeezed onto Android.
OWN-WORLD: Flightline graphite, amber timing, green receive truth, red transmit gates.
STORY: verify route and radio, acquire the signal, decode, arm once, transmit, return to RX.
FIRST VIEWPORT: radio/audio truth rail, live cycle timeline, decoded traffic, one-shot TX controls.
FORM: wide screens use mode/timeline, traffic, and sequencer columns; compact screens preserve the
same order in one scroll. Direction candidate 3 from surface seed e9cb623e.
*/

private val DigiChassis = Color(0xFF090B0D)
private val DigiPanel = Color(0xFF111519)
private val DigiInset = Color(0xFF07090B)
private val DigiLine = Color(0xFF2A3035)
private val DigiText = Color(0xFFE8ECEF)
private val DigiMuted = Color(0xFFAAB4BC)
private val DigiAmber = Color(0xFFF5A623)
private val DigiGreen = Color(0xFF47D18C)
private val DigiRed = Color(0xFFFF5A5F)

@Composable
fun DigiScreen(controller: DigiController, radio: RadioState, compact: Boolean) {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().background(DigiChassis)) {
        val wide = !compact && maxWidth >= 900.dp
        Column(Modifier.fillMaxSize()) {
            DigiTruthRail(controller, radio)
            Divider(color = DigiLine)
            if (wide) {
                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(0.8f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DigiModeDeck(controller)
                        DigiCycle(controller)
                    }
                    DigiTraffic(controller, Modifier.weight(1.45f).fillMaxHeight())
                    DigiSequencer(controller, radio, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DigiModeDeck(controller)
                    DigiCycle(controller)
                    DigiTraffic(controller, Modifier.fillMaxWidth().requiredHeightIn(min = 280.dp))
                    DigiSequencer(controller, radio, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DigiTruthRail(controller: DigiController, radio: RadioState) {
    val rxColor = if (controller.rxActive) DigiGreen else DigiMuted
    val txColor = if (controller.txPhase == DigiTxPhase.PTT_CONFIRMED) DigiRed else if (controller.txActive || controller.txArmed) DigiAmber else DigiMuted
    FlowRow(
        Modifier.fillMaxWidth().background(DigiPanel).padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Truth("RADIO", if (radio.connected) "${radio.model} · ${radio.frequencyText}" else "OFFLINE", if (radio.connected) DigiGreen else DigiRed)
        Truth("MODE", controller.mode.label, DigiAmber)
        Truth("RX", if (controller.rxActive) "LIVE" else "STOPPED", rxColor)
        Truth("TX", when (controller.txPhase) {
            DigiTxPhase.PTT_CONFIRMED -> "PTT CONFIRMED"
            DigiTxPhase.SEQUENCING -> "SEQUENCING"
            DigiTxPhase.SAFE -> if (controller.txArmed) "ARMED ONCE" else "SAFE"
        }, txColor)
    }
}

@Composable
private fun Truth(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Column {
            Text(label, color = DigiMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = DigiText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DigiModeDeck(controller: DigiController) {
    Column(
        Modifier.fillMaxWidth().background(DigiPanel, RoundedCornerShape(12.dp))
            .border(1.dp, DigiLine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("MODE DECK", color = DigiMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DigiMode.entries.forEach { value ->
                FilterChip(controller.mode == value, { controller.selectMode(value) }, { Text(value.label) },
                    Modifier.heightIn(min = 48.dp), colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DigiAmber, selectedLabelColor = DigiChassis))
            }
        }
        when (controller.mode) {
            DigiMode.CW -> {
                Text("Pitch ${controller.cwPitchHz.roundToInt()} Hz", color = DigiText)
                Slider(controller.cwPitchHz, controller::updateCwPitch, Modifier.semantics {
                    contentDescription = "CW pitch ${controller.cwPitchHz.roundToInt()} hertz"
                }, valueRange = 400f..1_000f)
                Text("Speed ${controller.cwWpm} WPM", color = DigiText)
                Slider(controller.cwWpm.toFloat(), { controller.updateCwWpm(it.roundToInt()) }, Modifier.semantics {
                    contentDescription = "CW speed ${controller.cwWpm} words per minute"
                }, valueRange = 8f..45f, steps = 36)
            }
            DigiMode.RTTY -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("45.45 baud · 170 Hz shift", color = DigiText)
                    Text("2125 / 2295 Hz AFSK · ITA2 USOS", color = DigiMuted, fontSize = 12.sp)
                }
                Text("Reverse", color = DigiMuted)
                Spacer(Modifier.width(8.dp))
                Switch(controller.rttyReverse, controller::updateRttyReverse)
            }
            DigiMode.SSTV -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SstvChoices.forEach { choice ->
                    FilterChip(controller.sstvChoice == choice, { controller.selectSstv(choice) }, { Text(choice.label) },
                        Modifier.heightIn(min = 48.dp), colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DigiAmber, selectedLabelColor = DigiChassis))
                }
            }
        }
    }
}

@Composable
private fun DigiCycle(controller: DigiController) {
    Column(
        Modifier.fillMaxWidth().background(DigiInset, RoundedCornerShape(12.dp))
            .border(1.dp, DigiLine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ROUTE / STATE", color = DigiMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(if (controller.rxActive) "CAPTURING" else "IDLE", color = if (controller.rxActive) DigiGreen else DigiMuted, fontWeight = FontWeight.Black)
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val y = size.height / 2
            drawLine(DigiLine, Offset(0f, y), Offset(size.width, y), 2f)
            val segments = 12
            repeat(segments + 1) { index ->
                val x = size.width * index / segments
                drawLine(if (index == 0) DigiAmber else DigiLine, Offset(x, y - 7), Offset(x, y + 7), if (index == 0) 3f else 1f)
            }
            if (controller.rxActive) drawCircle(DigiGreen, 6f, Offset(size.width * 0.12f, y), style = Stroke(3f))
            if (controller.txActive) drawLine(DigiRed, Offset(0f, y), Offset(size.width, y), 4f)
        }
        Text(controller.status, color = if (controller.status.contains("UNCONFIRMED")) DigiRed else DigiText, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ if (controller.rxActive) controller.stopRx() else controller.startRx() },
                colors = ButtonDefaults.buttonColors(containerColor = if (controller.rxActive) DigiRed else DigiGreen, contentColor = DigiChassis)) {
                Text(if (controller.rxActive) "STOP RX" else "START RX", fontWeight = FontWeight.Black)
            }
            OutlinedButton({ controller.clear() }) { Text("CLEAR") }
        }
    }
}

@Composable
private fun DigiTraffic(controller: DigiController, modifier: Modifier) {
    Column(
        modifier.background(DigiInset, RoundedCornerShape(12.dp))
            .border(1.dp, DigiLine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("DECODED TRAFFIC", color = DigiMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
            if (!controller.rxActive) {
                Text("RX STOPPED", color = DigiMuted, fontSize = 11.sp)
            } else when (controller.mode) {
                DigiMode.CW -> Text(if (controller.transcript.isBlank()) "LISTENING" else "COPY", color = DigiGreen, fontSize = 11.sp)
                DigiMode.RTTY -> Text("AFC ${"%+.1f".format(controller.rttyAfcHz)} Hz", color = if (controller.rttyAfcLocked) DigiGreen else DigiAmber, fontSize = 11.sp)
                DigiMode.SSTV -> Text(if (controller.sstvComplete) "IMAGE COMPLETE" else if (controller.sstvLine >= 0) "LINE ${controller.sstvLine + 1}" else "WAITING FOR VIS", color = if (controller.sstvComplete) DigiGreen else DigiAmber, fontSize = 11.sp)
            }
        }
        Divider(color = DigiLine)
        if (controller.mode == DigiMode.SSTV) {
            val revision = controller.imageRevision
            val image = remember(revision) { controller.currentSstvBitmap()?.asImageBitmap() }
            Box(Modifier.fillMaxWidth().weight(1f, fill = false).requiredHeightIn(min = 230.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, "Decoded SSTV image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("No SSTV image yet\nStart RX and wait for a valid VIS header.", color = DigiMuted)
            }
            if (controller.sstvFskId.isNotBlank()) Text("FSK ID · ${controller.sstvFskId}", color = DigiGreen, fontFamily = FontFamily.Monospace)
        } else {
            Text(
                controller.transcript.ifBlank { "No decoded characters yet. The decoder remains silent on unqualified noise." },
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                color = if (controller.transcript.isBlank()) DigiMuted else DigiText,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
        }
    }
}

@Composable
private fun DigiSequencer(controller: DigiController, radio: RadioState, modifier: Modifier) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }
            .getOrNull()?.let(controller::setSstvSource)
    }
    Column(
        modifier.background(DigiPanel, RoundedCornerShape(12.dp))
            .border(1.dp, DigiLine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("ONE-SHOT SEQUENCER", color = DigiMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("Audio route is acquired before PTT. Elecraft TX and RX are verified through fresh TQ responses; Flex uses its existing interlock gate.", color = DigiMuted, fontSize = 12.sp)
        if (controller.mode == DigiMode.SSTV) {
            OutlinedButton({ imagePicker.launch("image/*") }, Modifier.fillMaxWidth()) {
                Text(if (controller.sourceReady) "CHANGE ${controller.sstvChoice.label} IMAGE" else "CHOOSE ${controller.sstvChoice.label} IMAGE")
            }
        } else {
            OutlinedTextField(
                controller.txText,
                controller::updateTxText,
                Modifier.fillMaxWidth().weight(1f, fill = false),
                label = { Text("Transmit text") },
                minLines = 4,
                maxLines = 9,
            )
        }
        Divider(color = DigiLine)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ controller.arm() }, Modifier.weight(1f), enabled = radio.connected && !controller.txActive,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (controller.txArmed) DigiAmber else DigiText)) {
                Text(if (controller.txArmed) "DISARM" else "ARM ONCE", fontWeight = FontWeight.Black)
            }
            Button({ controller.send() }, Modifier.weight(1f), enabled = radio.connected && controller.txArmed && !controller.txActive,
                colors = ButtonDefaults.buttonColors(containerColor = DigiRed, contentColor = Color.White)) {
                Text(when (controller.txPhase) { DigiTxPhase.PTT_CONFIRMED -> "ON AIR"; DigiTxPhase.SEQUENCING -> "SEQUENCING"; DigiTxPhase.SAFE -> "SEND" }, fontWeight = FontWeight.Black)
            }
        }
        Text("Arm clears after one attempt, mode changes, image changes, or text edits.", color = DigiMuted, fontSize = 11.sp)
    }
}

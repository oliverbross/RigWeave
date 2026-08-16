package app.rigweave.mobile

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RigWeaveTheme { RigWeaveApp() } }
    }
}

private val Chassis = Color(0xFF111519)
private val Panel = Color(0xFF1B2228)
private val Raised = Color(0xFF283139)
private val Ink = Color(0xFFF4F0E7)
private val Muted = Color(0xFFA5ADB2)
private val Amber = Color(0xFFE9A72B)
private val Hold = Color(0xFFF4C94E)
private val Healthy = Color(0xFF42C77B)
private val Danger = Color(0xFFE4544D)

@Composable private fun RigWeaveTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Amber, onPrimary = Color(0xFF201708), background = Chassis,
        surface = Panel, surfaceVariant = Raised, outline = Color(0xFF4A555D), onBackground = Ink, onSurface = Ink),
    content = content,
)

private enum class Destination(val label: String) {
    HOME("Home"), RADIO("Radio"), CONTROLS("Controls"), LOGBOOK("Logbook"), PRESETS("Presets"), DX("DX"), SETTINGS("Settings")
}
private enum class SettingsSection(val label: String) {
    DEFAULT("Default"), LOG("Log"), CLUSTER("Cluster"), MACROS("Macros"), ALERTS("Alerts"),
    SAFETY("Safety"), AUDIO("Audio"), HEALTH("Health"), DIAG("Diag"), ABOUT("About")
}
private enum class QsoEditorTab(val label: String) { QSO("QSO"), STATION("Station"), GENERAL("General"), NOTES("Notes"), QSL("QSL") }

@Composable private fun RigWeaveApp() {
    val context = LocalContext.current
    val core = remember { NativeCore.create() }
    val transport = remember { UsbRadioTransport(context) }
    val database = remember { QsoDatabase(context) }
    val features = remember { FeatureController(context) }
    val wavelog = remember { WavelogController(context, database) }
    val callbook = remember { CallbookController(context) }
    val cty = remember { CtyController(context) }
    val audio = remember { AudioMonitorController(context) }
    val app = remember { AppController(context) }
    var radio by remember { mutableStateOf(NativeCore.parseState(NativeCore.state(core))) }
    var usbDetail by remember { mutableStateOf("No USB CAT adapter opened") }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var pendingRisk by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun accept(result: UsbResult) {
        when (result) {
            is UsbResult.Connected -> { NativeCore.feed(core, result.frames); radio = NativeCore.parseState(NativeCore.state(core)); usbDetail = result.detail }
            is UsbResult.PermissionRequired -> usbDetail = result.detail
            is UsbResult.Unavailable -> usbDetail = result.detail
        }
    }
    val connect: () -> Unit = { scope.launch { accept(transport.connect()) } }
    val direct: (String) -> Unit = { command -> scope.launch { accept(transport.send(command)) } }
    val send: (String) -> Unit = { raw ->
        val command = raw.uppercase()
        val risky = command.startsWith("TX") || command.startsWith("KY") ||
            command in setOf("SWT11;", "SWH11;", "SWT44;", "SWT16;", "SWH16;")
        if (risky) pendingRisk = command else direct(command)
    }
    LaunchedEffect(transport) { audio.refreshDevices(); while (true) { delay(450); transport.poll()?.let(::accept) } }
    DisposableEffect(Unit) { onDispose {
        scope.launch { transport.disconnect() }; audio.close(); features.close(); wavelog.close(); callbook.close(); cty.close()
        NativeCore.destroy(core); database.close()
    } }
    pendingRisk?.let { command -> AlertDialog(
        onDismissRequest = { pendingRisk = null }, title = { Text("Confirm radio action") },
        text = { Text("Send $command once? This may key or tune the transmitter.") },
        confirmButton = { Button({ direct(command); pendingRisk = null }) { Text("Send once") } },
        dismissButton = { TextButton({ pendingRisk = null }) { Text("Cancel") } },
    ) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Chassis)) {
        if (maxWidth >= 700.dp) Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = Panel) {
                Text("RW", color = Amber, fontWeight = FontWeight.Black, modifier = Modifier.padding(18.dp))
                Destination.entries.forEach { item -> NavigationRailItem(destination == item, { destination = item },
                    { Icon(navIcon(item), item.label) }, label = { Text(item.label) }) }
            }
            Screen(destination, radio, usbDetail, database, features, wavelog, callbook, cty, audio, app, connect, send, direct)
        } else Scaffold(bottomBar = { NavigationBar(containerColor = Panel) {
            Destination.entries.forEach { item -> NavigationBarItem(destination == item, { destination = item },
                { Icon(navIcon(item), item.label) }, label = { Text(item.label, fontSize = 9.sp) }) }
        } }) { padding -> Box(Modifier.padding(padding)) {
            Screen(destination, radio, usbDetail, database, features, wavelog, callbook, cty, audio, app, connect, send, direct)
        } }
    }
}

private fun navIcon(item: Destination) = when (item) {
    Destination.HOME -> Icons.Outlined.Home
    Destination.RADIO -> Icons.Outlined.SettingsInputAntenna
    Destination.CONTROLS -> Icons.Outlined.Tune
    Destination.LOGBOOK -> Icons.AutoMirrored.Outlined.List
    Destination.PRESETS -> Icons.Outlined.Bookmarks
    Destination.DX -> Icons.Outlined.Public
    Destination.SETTINGS -> Icons.Outlined.Settings
}

@Composable private fun Screen(destination: Destination, radio: RadioState, detail: String, database: QsoDatabase,
    features: FeatureController, wavelog: WavelogController, callbook: CallbookController, cty: CtyController, audio: AudioMonitorController, app: AppController,
    connect: () -> Unit, send: (String) -> Unit, direct: (String) -> Unit) {
    when (destination) {
        Destination.HOME -> HomeScreen(radio, app, send)
        Destination.RADIO -> RadioScreen(radio, detail, app, database, wavelog, callbook, cty, features, connect, send, direct)
        Destination.CONTROLS -> ControlsScreen(radio, app, send)
        Destination.LOGBOOK -> LogbookScreen(radio, database, wavelog)
        Destination.PRESETS -> PresetsScreen(radio, app, send)
        Destination.DX -> DXScreen(features, send)
        Destination.SETTINGS -> SettingsScreen(radio, detail, database, features, wavelog, callbook, cty, audio, app, direct)
    }
}

@Composable private fun Header(title: String, state: RadioState? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("RIGWEAVE", color = Amber, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(title.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall) }
        state?.let { StatusChip(if (it.connected) "CAT LIVE" else "CAT OFFLINE", it.connected) }
    }
}

@Composable private fun StatusChip(text: String, good: Boolean) {
    Surface(color = (if (good) Healthy else Danger).copy(alpha = .15f), shape = MaterialTheme.shapes.small) {
        Text(text, color = if (good) Healthy else Danger, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable private fun Instrument(state: RadioState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF201708))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("VFO A · ${state.mode}", color = Amber, fontWeight = FontWeight.Bold)
                Text(if (state.transmitting) "TRANSMIT" else "RECEIVE", color = if (state.transmitting) Danger else Healthy, fontWeight = FontWeight.Bold)
            }
            Text(state.frequencyText, color = Amber, fontFamily = FontFamily.Monospace, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("VFO B  ${if (state.frequencyBHz > 0) formatRadioFrequency(state.frequencyBHz) else "—.———"} MHz",
                    color = if (state.split) Hold else Muted, fontFamily = FontFamily.Monospace)
                Text(listOfNotNull(if (state.split) "SPLIT" else null, if (state.rit) "RIT" else null,
                    if (state.xit) "XIT" else null, if (state.preamp) "PRE" else null, if (state.attenuator) "ATT" else null).joinToString("  "), color = Hold)
            }
            Text(if (state.transmitting && state.swrTenths >= 0) "RF ${state.rfOutputTenths / 10.0} W · SWR ${state.swrTenths / 10.0}:1"
                else "S-METER  ${state.meter}", color = Muted, style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator({ if (state.transmitting) (state.rfOutputTenths / 120f).coerceIn(0f, 1f) else (state.meter / 30f).coerceIn(0f, 1f) },
                Modifier.fillMaxWidth(), color = Amber)
        }
    }
}

@Composable private fun HomeScreen(state: RadioState, app: AppController, send: (String) -> Unit) = Page {
    Header("Field dashboard", state)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldProfile.entries.forEach { profile -> FilterChip(app.fieldProfile == profile, { app.setProfile(profile) }, { Text(profile.name) }) }
    }
    Instrument(state)
    Text("QUICK FREQUENCIES", color = Muted)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        app.favoriteBands.forEach { value -> OutlinedButton({
            value.toDoubleOrNull()?.let { send("FA%011d;".format((it * 1_000_000).toLong())) }
        }, enabled = state.connected) { Text(value, fontFamily = FontFamily.Monospace) } }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HealthTile("RADIO", if (state.connected) state.model else "Not connected", state.connected, Modifier.weight(1f))
        HealthTile("OPERATING", if (state.connected) "${state.mode} · ${state.bandwidthHz} Hz" else "No live state", state.connected, Modifier.weight(1f))
        HealthTile("TX SAFETY", if (app.transmitArmed) "ARMED" else "SAFE / RX", !app.transmitArmed, Modifier.weight(1f))
    }
}

@Composable private fun HealthTile(label: String, value: String, good: Boolean, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(14.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = if (good) Healthy else Ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
    } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RadioScreen(state: RadioState, detail: String, app: AppController, database: QsoDatabase,
    wavelog: WavelogController, callbook: CallbookController, cty: CtyController, features: FeatureController, connect: () -> Unit, send: (String) -> Unit, direct: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF090B0C)).navigationBarsPadding().padding(10.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kx3StatusRail(state, detail, connect, direct)
                CompactKx3Face(state, send, Modifier.fillMaxWidth().weight(1f))
            }
            Row(Modifier.fillMaxWidth().weight(2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactLogger(state, database, wavelog, callbook, cty, app, send, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactKx3TuningDeck(state, send, Modifier.fillMaxWidth().weight(1f))
                    LiveSpotsPanel(features, send, Modifier.fillMaxWidth().weight(3f))
                }
            }
        }
    }
}

@Composable private fun CompactKx3Face(state: RadioState, send: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFF0B0D0E), shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF454C50)), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                FaceplateScrew(); Spacer(Modifier.weight(1f))
                Text("ELECRAFT KX3", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 3.sp, style = MaterialTheme.typography.labelLarge)
                Text("  ·  RIGWEAVE", color = Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                FaceplateScrew()
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Kx3KeyMatrix(
                    columns = listOf(
                        listOf(Kx3KeySpec("BAND +", "SWT08;"), Kx3KeySpec("BAND −", "SWT41;"), Kx3KeySpec("FREQ ENT", "SWT10;")),
                        listOf(Kx3KeySpec("RCL", "SWH08;"), Kx3KeySpec("STORE", "SWH41;"), Kx3KeySpec("SCAN", "SWH10;")),
                        listOf(Kx3KeySpec("MSG", "SWT11;", true), Kx3KeySpec("ATU TUNE", "SWT44;", true), Kx3KeySpec("XMIT", "SWT16;", true)),
                        listOf(Kx3KeySpec("REC", "SWH11;", true), Kx3KeySpec("ANT", "SWH44;"), Kx3KeySpec("TUNE", "SWH16;", true)),
                    ), state.connected, send, Modifier.width(318.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Kx3CompactLcd(state, send, Modifier.weight(4f).fillMaxWidth())
                    Kx3ReceiveKeyRow(state.connected, send, Modifier.weight(1f).fillMaxWidth())
                }
                Kx3KeyMatrix(
                    columns = listOf(
                        listOf(Kx3KeySpec("MODE", "SWT14;"), Kx3KeySpec("DATA", "SWT17;"), Kx3KeySpec("RIT", "SWT18;")),
                        listOf(Kx3KeySpec("ALT", "SWH14;"), Kx3KeySpec("TEXT", "SWH17;"), Kx3KeySpec("PF1", "SWH18;")),
                        listOf(Kx3KeySpec("A/B", "SWT24;"), Kx3KeySpec("A → B", "SWT25;"), Kx3KeySpec("XIT", "SWT26;")),
                        listOf(Kx3KeySpec("REV", "SWH24;"), Kx3KeySpec("SPLIT", "SWH25;"), Kx3KeySpec("PF2", "SWH26;")),
                    ), state.connected, send, Modifier.width(318.dp).fillMaxHeight())
            }
        }
    }
}

private data class Kx3KeySpec(val label: String, val command: String, val requiresArm: Boolean = false)

@Composable private fun Kx3KeyMatrix(columns: List<List<Kx3KeySpec>>, connected: Boolean,
    send: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        columns.forEachIndexed { columnIndex, column ->
            Column(Modifier.weight(1f).fillMaxHeight()) {
                column.forEach { key ->
                    Kx3DirectKey(key.label, connected, { send(key.command) },
                        Modifier.weight(1f), secondary = columnIndex % 2 == 1, risky = key.requiresArm,
                        bold = columnIndex == 1 || columnIndex == 2)
                }
            }
        }
    }
}

@Composable private fun Kx3ReceiveKeyRow(connected: Boolean, send: (String) -> Unit, modifier: Modifier = Modifier) {
    val keys = listOf(
        Kx3KeySpec("PRE", "SWT19;"), Kx3KeySpec("NR", "SWH19;"),
        Kx3KeySpec("ATTN", "SWT27;"), Kx3KeySpec("NB", "SWH27;"),
        Kx3KeySpec("APF", "SWT20;"), Kx3KeySpec("NTCH", "SWH20;"),
        Kx3KeySpec("SPOT", "SWT28;"), Kx3KeySpec("CWT", "SWH28;"),
        Kx3KeySpec("CMP", "SWT21;"), Kx3KeySpec("PITCH", "SWH21;"),
        Kx3KeySpec("DLY", "SWT29;"), Kx3KeySpec("VOX", "SWH29;"),
    )
    Row(modifier) {
        keys.forEachIndexed { index, key ->
            Kx3DirectKey(key.label, connected, { send(key.command) }, Modifier.weight(1f), secondary = index % 2 == 1,
                bold = true, compact = true)
        }
    }
}

@Composable private fun Kx3PairKeys(main: String, secondary: String, enabled: Boolean, mainAction: () -> Unit,
    secondaryAction: () -> Unit, modifier: Modifier = Modifier, risky: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Kx3DirectKey(main, enabled, mainAction, Modifier.weight(1f), risky)
        Kx3DirectKey(secondary, enabled, secondaryAction, Modifier.weight(1f), secondary = true, risky = risky)
    }
}

@Composable private fun Kx3DirectKey(label: String, enabled: Boolean, action: () -> Unit, modifier: Modifier = Modifier,
    secondary: Boolean = false, risky: Boolean = false, bold: Boolean = true, compact: Boolean = false) {
    val shape = RoundedCornerShape(4.dp)
    val cap = if (enabled) listOf(Color(0xFF818789), Color(0xFF555B5D), Color(0xFF363A3C))
        else listOf(Color(0xFF34383A), Color(0xFF282C2E), Color(0xFF202426))
    val edge = if (risky) Color(0xFFB39A57) else Color(0xFFA4AAAC)
    Button(action, enabled = enabled, modifier = modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 48.dp).padding(1.dp),
        shape = shape, border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) edge else Color(0xFF454A4C)),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Ink,
            disabledContainerColor = Color.Transparent, disabledContentColor = Muted),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp, disabledElevation = 0.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(cap)), contentAlignment = Alignment.Center) {
            Text(label, color = if (!enabled) Muted else if (secondary) Hold else Ink,
                fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
                fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, softWrap = false)
        }
    }
}

private enum class Kx3LcdPicker { BAND, MODE, FILTER }

@Composable private fun Kx3CompactLcd(state: RadioState, send: (String) -> Unit, modifier: Modifier = Modifier) {
    val lcdInk = Color(0xFF291D03)
    val splitInk = Color(0xFF8E1717)
    var picker by remember { mutableStateOf<Kx3LcdPicker?>(null) }
    Surface(shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF343839)), modifier = modifier) {
        Row(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8C945), Color(0xFFE3A00E)))).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kx3OriginalMeter(state, lcdInk, Modifier.fillMaxHeight().weight(.40f))
            Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth().weight(.61f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.fillMaxHeight().weight(1f).clickable(enabled = state.connected) { picker = Kx3LcdPicker.BAND }
                        .padding(horizontal = 42.dp, vertical = 13.dp)) {
                        SegmentedReadout(if (state.connected) formatRadioFrequency(state.frequencyHz) else "--.---.---", lcdInk,
                            Modifier.fillMaxSize())
                    }
                    Kx3VfoIndicator("A", state.mode, state.transmitting, lcdInk, splitInk, { picker = Kx3LcdPicker.MODE },
                        Modifier.width(58.dp).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().weight(.39f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Kx3OriginalAnnunciators(state, lcdInk, { picker = Kx3LcdPicker.FILTER }, Modifier.fillMaxHeight().weight(1.22f))
                    SegmentedReadout(if (state.frequencyBHz > 0) formatRadioFrequency(state.frequencyBHz) else "--.---.---",
                        if (state.split) splitInk else lcdInk, Modifier.fillMaxHeight().weight(.78f).padding(vertical = 8.dp))
                    Kx3VfoIndicator("B", if (state.split) "SPLIT" else "", state.split, lcdInk, splitInk,
                        {}, Modifier.width(58.dp).fillMaxHeight())
                }
            }
        }
    }
    picker?.let { selected ->
        Kx3LcdPickerDialog(selected, state.mode, { command -> send(command); picker = null }, { picker = null })
    }
}

private fun agcLabel(value: Int) = when (value) { 2 -> "AGC-F"; 4 -> "AGC-S"; 0 -> "AGC OFF"; else -> "AGC --" }
private fun displayBandwidth(value: Int) = if (value > 0) "$value Hz" else "--"
private fun tenths(value: Int) = if (value >= 0) "%.1f".format(value / 10f) else "--"

@Composable private fun Kx3VfoIndicator(vfo: String, mode: String, active: Boolean, ink: Color, activeInk: Color,
    modeAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(mode, color = if (active) activeInk else ink, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1,
            modifier = Modifier.clickable(enabled = mode.isNotBlank(), onClick = modeAction).padding(horizontal = 3.dp, vertical = 2.dp))
        Box(Modifier.border(1.dp, if (active) activeInk else ink).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(vfo, color = if (active) activeInk else ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Text(if (active) "TX" else "", color = activeInk, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

@Composable private fun Kx3OriginalAnnunciators(state: RadioState, ink: Color, bandwidthAction: () -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ANT1", color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("ATU", color = ink.copy(alpha = if (state.powerW > 0) 1f else .28f), fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(if (state.rit) "RIT" else if (state.xit) "XIT" else "RIT", color = ink.copy(alpha = if (state.rit || state.xit) 1f else .28f),
                fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(agcLabel(state.agcMode), color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("PRE", color = ink.copy(alpha = if (state.preamp) 1f else .28f), fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("ATT", color = ink.copy(alpha = if (state.attenuator) 1f else .28f), fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("CWT", color = ink.copy(alpha = if (state.cwt) 1f else .28f), fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("XFIL  FL2", color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("BW ${state.bandwidthHz}", color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = state.connected, onClick = bandwidthAction).padding(horizontal = 3.dp, vertical = 2.dp))
            Text("PWR ${state.powerW}W", color = ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

private fun kx3FilterWidths(mode: String): List<Int> = when (mode) {
    "CW", "CW-R" -> listOf(100, 200, 300, 400, 500, 1000)
    "AM" -> listOf(3000, 4000, 5000, 6000, 7000, 8000)
    "FM" -> listOf(5000, 7000, 9000, 11000, 13000, 15000)
    else -> listOf(1800, 2100, 2400, 2700, 3000, 3500)
}

@Composable private fun Kx3LcdPickerDialog(picker: Kx3LcdPicker, mode: String, select: (String) -> Unit,
    dismiss: () -> Unit) {
    val choices = when (picker) {
        Kx3LcdPicker.BAND -> listOf("160m" to "BN00;", "80m" to "BN01;", "60m" to "BN02;", "40m" to "BN03;",
            "30m" to "BN04;", "20m" to "BN05;", "17m" to "BN06;", "15m" to "BN07;", "12m" to "BN08;", "10m" to "BN09;")
        Kx3LcdPicker.MODE -> listOf("LSB" to "MD1;", "USB" to "MD2;", "CW" to "MD3;",
            "CW-R" to "MD7;", "AM" to "MD5;", "FM" to "MD4;")
        Kx3LcdPicker.FILTER -> kx3FilterWidths(mode).map { width ->
            (if (width >= 1000) "${width / 1000.0} kHz" else "$width Hz") to "BW%04d;".format(width / 10)
        }
    }
    val columns = if (picker == Kx3LcdPicker.BAND) 5 else 3
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(when (picker) {
            Kx3LcdPicker.BAND -> "SELECT BAND"
            Kx3LcdPicker.MODE -> "SELECT MODE"
            Kx3LcdPicker.FILTER -> "FILTER WIDTH · $mode"
        }, color = Amber, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, command) ->
                            OutlinedButton({ select(command) }, Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(label, fontWeight = FontWeight.Black, maxLines = 1)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(dismiss) { Text("CLOSE") } })
}

@Composable private fun Kx3OriginalMeter(state: RadioState, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kx3BarMeter("S", "1  3  5  7  9  +20", state.meter / 21f, ink, Modifier.weight(1f).fillMaxHeight())
            Kx3CwtMeter(state.cwt, ink, Modifier.weight(.72f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kx3BarMeter("SWR", "1  1.5  2  3", ((state.swrTenths - 10) / 25f), ink, Modifier.weight(1f).fillMaxHeight())
            Kx3BarMeter("RF", "0   5   10", state.rfOutputTenths / 120f, ink, Modifier.weight(1f).fillMaxHeight())
        }
        Canvas(Modifier.fillMaxWidth().weight(.68f)) {
            val center = size.width * .42f
            val halfWidth = (state.bandwidthHz.coerceIn(100, 4000) / 4000f) * size.width * .18f + size.width * .06f
            val path = Path().apply {
                moveTo(center - halfWidth, size.height * .76f)
                lineTo(center - halfWidth * .68f, size.height * .24f)
                lineTo(center + halfWidth * .68f, size.height * .24f)
                lineTo(center + halfWidth, size.height * .76f)
            }
            drawPath(path, ink, style = Stroke(2.dp.toPx()))
            drawLine(ink, Offset(size.width * .12f, size.height * .76f), Offset(size.width * .74f, size.height * .76f), 1.5.dp.toPx())
            if (state.cwt) {
                drawLine(ink, Offset(size.width * .86f, size.height * .2f), Offset(size.width * .86f, size.height * .76f), 2.dp.toPx())
                drawCircle(ink, 2.5.dp.toPx(), Offset(size.width * .86f, size.height * .18f))
            }
        }
        Text("I   XFIL · ${displayBandwidth(state.bandwidthHz)}   FL2", color = ink, fontWeight = FontWeight.Black,
            fontSize = 12.sp, maxLines = 1)
    }
}

@Composable private fun Kx3BarMeter(title: String, scale: String, progress: Float, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$title  $scale", color = ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
            fontSize = 12.sp, maxLines = 1, softWrap = false)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val bounded = progress.coerceIn(0f, 1f)
            drawRect(ink.copy(alpha = .13f), size = size)
            drawRect(ink, size = Size(size.width * bounded, size.height))
            repeat(7) { tick ->
                val x = size.width * tick / 6f
                drawLine(ink.copy(alpha = .62f), Offset(x, 0f), Offset(x, size.height * .48f), 1.dp.toPx())
            }
        }
    }
}

@Composable private fun Kx3CwtMeter(active: Boolean, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("CWT", color = ink.copy(alpha = if (active) 1f else .38f), fontWeight = FontWeight.Black,
            fontSize = 12.sp, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val barWidth = size.width / 12f
            repeat(7) { index ->
                val x = size.width * (index + 1) / 8f - barWidth / 2f
                drawRect(ink.copy(alpha = if (active) .9f else .16f), Offset(x, size.height * .62f),
                    Size(barWidth, size.height * .28f))
            }
            val center = size.width / 2f
            drawLine(ink.copy(alpha = if (active) 1f else .38f), Offset(center, 0f), Offset(center, size.height * .48f), 2.dp.toPx())
            val pointer = Path().apply {
                moveTo(center - 5.dp.toPx(), size.height * .42f)
                lineTo(center + 5.dp.toPx(), size.height * .42f)
                lineTo(center, size.height * .59f)
                close()
            }
            drawPath(pointer, ink.copy(alpha = if (active) 1f else .38f))
        }
    }
}

private fun segmentMask(character: Char) = when (character) {
    '0' -> 0b0111111; '1' -> 0b0000110; '2' -> 0b1011011; '3' -> 0b1001111
    '4' -> 0b1100110; '5' -> 0b1101101; '6' -> 0b1111101; '7' -> 0b0000111
    '8' -> 0b1111111; '9' -> 0b1101111; '-' -> 0b1000000; else -> 0
}

@Composable private fun SegmentedReadout(value: String, ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val units = value.sumOf { if (it == '.' || it == ':') .28 else 1.0 }.toFloat().coerceAtLeast(1f)
        val scale = minOf(size.height, size.width / (units * .62f + (value.length - 1).coerceAtLeast(0) * .08f))
        val digitWidth = scale * .54f
        val gap = scale * .08f
        val dotWidth = scale * .17f
        val totalWidth = value.sumOf { if (it == '.' || it == ':') dotWidth.toDouble() else digitWidth.toDouble() }.toFloat() + gap * (value.length - 1).coerceAtLeast(0)
        var x = (size.width - totalWidth).coerceAtLeast(0f) / 2f
        val y = (size.height - scale) / 2f
        val thickness = scale * .068f
        fun horizontal(top: Float, active: Boolean) {
            val path = Path().apply {
                moveTo(x + thickness * .55f, top); lineTo(x + digitWidth - thickness * .55f, top)
                lineTo(x + digitWidth - thickness, top + thickness); lineTo(x + thickness, top + thickness); close()
            }
            drawPath(path, ink.copy(alpha = if (active) 1f else .025f))
        }
        fun vertical(left: Float, top: Float, bottom: Float, active: Boolean) {
            val path = Path().apply {
                moveTo(left, top + thickness * .55f); lineTo(left + thickness, top + thickness)
                lineTo(left + thickness, bottom - thickness); lineTo(left, bottom - thickness * .55f)
                lineTo(left - thickness * .15f, bottom - thickness); lineTo(left - thickness * .15f, top + thickness); close()
            }
            drawPath(path, ink.copy(alpha = if (active) 1f else .025f))
        }
        value.forEach { character ->
            if (character == '.') {
                drawCircle(ink, radius = thickness * .72f, center = Offset(x + dotWidth * .45f, y + scale - thickness * .65f))
                x += dotWidth + gap
            } else if (character == ':') {
                drawCircle(ink, radius = thickness * .55f, center = Offset(x + dotWidth * .45f, y + scale * .34f))
                drawCircle(ink, radius = thickness * .55f, center = Offset(x + dotWidth * .45f, y + scale * .68f))
                x += dotWidth + gap
            } else {
                val mask = segmentMask(character)
                val middle = y + scale * .50f - thickness * .5f
                horizontal(y, mask and 0b0000001 != 0)
                vertical(x + digitWidth - thickness, y, middle + thickness, mask and 0b0000010 != 0)
                vertical(x + digitWidth - thickness, middle, y + scale, mask and 0b0000100 != 0)
                horizontal(y + scale - thickness, mask and 0b0001000 != 0)
                vertical(x, middle, y + scale, mask and 0b0010000 != 0)
                vertical(x, y, middle + thickness, mask and 0b0100000 != 0)
                horizontal(middle, mask and 0b1000000 != 0)
                x += digitWidth + gap
            }
        }
    }
}

@Composable private fun CompactKx3TuningDeck(state: RadioState, send: (String) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(100) }
    var af by remember(state.afGain) { mutableFloatStateOf(state.afGain.toFloat()) }
    var rf by remember(state.rfGain) { mutableFloatStateOf(state.rfGain.toFloat()) }
    var monitor by remember(state.monitorLevel) { mutableFloatStateOf(state.monitorLevel.coerceAtLeast(0).toFloat()) }
    var width by remember(state.bandwidthHz) { mutableFloatStateOf(state.bandwidthHz.coerceIn(100, 4000).toFloat()) }
    var shift by remember(state.ifShiftHz) { mutableFloatStateOf(state.ifShiftHz.takeIf { it >= 0 }?.toFloat() ?: 1500f) }
    var mic by remember(state.micGain) { mutableFloatStateOf(state.micGain.coerceAtLeast(0).toFloat()) }
    var keyer by remember(state.keyerSpeed) { mutableFloatStateOf(state.keyerSpeed.takeIf { it >= 8 }?.toFloat() ?: 20f) }
    var power by remember(state.powerW) { mutableFloatStateOf(state.powerW.coerceIn(0, 12).toFloat()) }
    Surface(color = Color(0xFF111516), shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF434A4D)), modifier = modifier) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                InlineKx3Slider("AF", af, 0f..255f, { af = it }, { send("AG%03d;".format(af.toInt())) }, state.connected)
                InlineKx3Slider("RF", rf, 0f..250f, { rf = it }, { send("RG%03d;".format(rf.toInt())) }, state.connected)
                InlineKx3Slider("MON", monitor, 0f..60f, { monitor = it }, { send("ML%03d;".format(monitor.toInt())) }, state.connected)
            }
            Kx3DeckDivider()
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                InlineKx3Slider("I/WID", width, 100f..4000f, { width = it },
                    { send("BW%04d;".format((width.toInt() / 10).coerceIn(0, 9999))) }, state.connected)
                InlineKx3Slider("II/SHT", shift, 300f..3000f, { shift = it },
                    { send("IS %04d;".format(shift.toInt())) }, state.connected)
                InlineKx3Button("NORM", state.connected) { send("IS 9999;") }
            }
            Kx3DeckDivider()
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                InlineKx3Slider("KEYER", keyer, 8f..50f, { keyer = it }, { send("KS%03d;".format(keyer.toInt())) }, state.connected)
                InlineKx3Slider("MIC", mic, 0f..60f, { mic = it }, { send("MG%03d;".format(mic.toInt())) }, state.connected)
                InlineKx3Slider("PWR", power, 0f..12f, { power = it }, { send("PC%03d;".format(power.toInt())) }, state.connected)
            }
            Kx3VfoWheel(state, step, send, { step = when (step) { 10 -> 100; 100 -> 1000; 1000 -> 10000; else -> 10 } }, Modifier.fillMaxHeight().aspectRatio(1f))
        }
    }
}

@Composable private fun InlineKx3Slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit,
    finish: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(37.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) Ink else Muted, fontWeight = FontWeight.Black, fontSize = 12.sp,
            letterSpacing = .15.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(50.dp))
        Slider(value, change, enabled = enabled, valueRange = range, onValueChangeFinished = finish, modifier = Modifier.weight(1f))
        Text(value.toInt().toString(), color = if (enabled) Hold else Muted, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.width(32.dp))
    }
}

@Composable private fun Kx3DeckDivider() = Box(Modifier.width(1.dp).fillMaxHeight(.84f).background(Color(0xFF394044)))

@Composable private fun InlineKx3Button(label: String, enabled: Boolean, action: () -> Unit) {
    Button(action, enabled = enabled, modifier = Modifier.fillMaxWidth().height(37.dp), shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33383B), contentColor = Hold),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .2.sp)
    }
}

@Composable private fun LiveSpotsPanel(features: FeatureController, send: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color(0xFF121617), shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444B4E)), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LIVE DX SPOTS", color = Amber, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text(features.clusterStatus, color = if (features.liveSpots.isNotEmpty()) Healthy else Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (features.liveSpots.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No live spots yet. Connect the DX cluster in Settings.", color = Muted)
            } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val spots = features.liveSpots.take(20)
                items(spots.size) { index -> val spot = spots[index]
                    Surface(onClick = { send("FA%011d;".format(spot.frequencyHz)) }, color = Color(0xFF1A2023), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(spot.callsign, color = if (spot.watchlisted) Hold else Ink, fontWeight = FontWeight.Black)
                                Text("${spot.country} · ${spot.band} · ${spot.mode}", color = Muted, style = MaterialTheme.typography.labelSmall) }
                            Text(formatRadioFrequency(spot.frequencyHz), color = Amber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp)); Text("TUNE", color = Healthy, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Kx3StatusRail(state: RadioState, detail: String, connect: () -> Unit, direct: (String) -> Unit) {
    Surface(color = Color(0xFF15191B), shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF363D40)), modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("KX3 TOUCH REMOTE", color = Amber, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            StatusChip(if (state.connected) "CAT LIVE" else "CAT OFFLINE", state.connected)
            Text(if (state.connected) "${state.model} · ${state.mode}" else detail, color = Muted,
                style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text(Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'")),
                color = Ink, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            OutlinedButton(connect, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(if (state.connected) "REFRESH" else "CONNECT")
            }
            Button({ direct("RX;") }, enabled = state.connected, modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Healthy, contentColor = Chassis),
                contentPadding = PaddingValues(horizontal = 14.dp)) { Text("EMERGENCY RX", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable private fun FaceplateScrew() {
    Canvas(Modifier.size(20.dp)) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF8E969A), Color(0xFF24282A))), radius = size.minDimension / 2)
        rotate(45f) { drawLine(Color(0xFF111314), Offset(size.width * .25f, size.height / 2), Offset(size.width * .75f, size.height / 2), 2.dp.toPx()) }
    }
}

@Composable private fun Kx3Lcd(state: RadioState, app: AppController, modifier: Modifier = Modifier) {
    val lcd = Color(0xFFEFB323); val lcdInk = Color(0xFF291D03)
    Surface(shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF343839)),
        modifier = modifier) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF7C53B), lcd, Color(0xFFD7950D)))).padding(14.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VFO A", color = lcdInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(app.stationCallsign, app.stationName, app.stationGrid).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "KX3 REMOTE" },
                        color = lcdInk, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(if (state.transmitting) "TX" else "RX", color = lcdInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    Text(if (state.connected) formatRadioFrequency(state.frequencyHz) else "--.---.---", color = lcdInk,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light,
                        fontSize = if (maxWidth >= 720.dp) 62.sp else 46.sp, modifier = Modifier.align(Alignment.Center))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("VFO B", color = lcdInk, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text(if (state.frequencyBHz > 0) formatRadioFrequency(state.frequencyBHz) else "--.---.---", color = lcdInk,
                        fontFamily = FontFamily.Monospace, fontSize = 24.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${state.model}  ${state.mode}  ${if (state.split) "SPLIT" else "SIMPLEX"}", color = lcdInk, fontWeight = FontWeight.SemiBold)
                    Text("BW ${state.bandwidthHz.takeIf { it > 0 } ?: "--"}  RIT ${if (state.rit) "ON" else "OFF"}  XIT ${if (state.xit) "ON" else "OFF"}", color = lcdInk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AGC  PRE ${if (state.preamp) "ON" else "OFF"}  ATTN ${if (state.attenuator) "ON" else "OFF"}", color = lcdInk)
                    Text("PWR ${state.powerW.takeIf { it > 0 }?.let { "$it W" } ?: "--"}", color = lcdInk)
                }
                Kx3Meter(state, lcdInk)
                Surface(color = lcdInk.copy(alpha = .92f), modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CW DECODE", color = lcd, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(12.dp))
                        Text("—", color = Color(0xFFF8D45D), fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable private fun Kx3Meter(state: RadioState, ink: Color) {
    val fraction = if (state.transmitting) (state.rfOutputTenths / 120f).coerceIn(0f, 1f) else (state.meter / 30f).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.transmitting) "SWR ${if (state.swrTenths >= 0) state.swrTenths / 10f else "--"}" else "S-METER ${state.meter}", color = ink, fontWeight = FontWeight.Bold)
            Text(if (state.transmitting) "RF ${if (state.rfOutputTenths >= 0) state.rfOutputTenths / 10f else "--"} W" else "S 1   3   5   7   9   +20   +40   +60", color = ink, fontFamily = FontFamily.Monospace)
        }
        Canvas(Modifier.fillMaxWidth().height(23.dp)) {
            val baseline = size.height * .78f
            drawLine(ink, Offset(0f, baseline), Offset(size.width, baseline), 2.dp.toPx())
            repeat(16) { index -> val x = size.width * index / 15f
                drawLine(ink, Offset(x, baseline), Offset(x, if (index % 2 == 0) 0f else size.height * .28f), 1.dp.toPx()) }
            drawRect(ink.copy(alpha = .86f), Offset(0f, size.height * .42f), androidx.compose.ui.geometry.Size(size.width * fraction, size.height * .22f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun FlightButton(main: String, secondary: String, enabled: Boolean, tap: () -> Unit, hold: () -> Unit,
    modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .background(Brush.verticalGradient(if (accent) listOf(Color(0xFF454123), Color(0xFF1E1D14)) else listOf(Color(0xFF3C4144), Color(0xFF17191A))), MaterialTheme.shapes.small)
        .border(1.dp, if (enabled) Color(0xFF666E72) else Color(0xFF292D2F), MaterialTheme.shapes.small)
        .combinedClickable(enabled = enabled, onClick = tap, onLongClick = hold)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(main, color = if (enabled) Ink else Muted.copy(alpha = .78f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text(secondary, color = if (enabled) Hold else Muted.copy(alpha = .62f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun CompactLogger(state: RadioState, database: QsoDatabase, wavelog: WavelogController,
    callbook: CallbookController, cty: CtyController, app: AppController, send: (String) -> Unit, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(QsoEditorTab.QSO) }
    var call by remember { mutableStateOf("") }; var sent by remember { mutableStateOf("59") }; var received by remember { mutableStateOf("59") }
    var name by remember { mutableStateOf("") }; var qth by remember { mutableStateOf("") }; var grid by remember { mutableStateOf("") }
    var iota by remember { mutableStateOf("") }; var sota by remember { mutableStateOf("") }; var wwff by remember { mutableStateOf("") }; var pota by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }; var dxcc by remember { mutableStateOf("") }; var continent by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }; var cqZone by remember { mutableStateOf("") }; var ituZone by remember { mutableStateOf("") }
    var stateName by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var propagation by remember { mutableStateOf("") }; var antennaPath by remember { mutableStateOf("") }
    var qslSent by remember { mutableStateOf("N") }; var qslMethod by remember { mutableStateOf("") }
    var qslVia by remember { mutableStateOf("") }; var qslMessage by remember { mutableStateOf("") }
    var enrichment by remember { mutableStateOf("Enter a callsign") }; var status by remember { mutableStateOf("LOCAL FIRST") }
    var lookupGeneration by remember { mutableStateOf(0) }
    val selectedStation = wavelog.selectedStation
    val utc = wavelog.synchronizedNow().atZone(ZoneOffset.UTC)
    fun clear() {
        lookupGeneration++
        call = ""; sent = "59"; received = "59"; name = ""; qth = ""; grid = ""; iota = ""; sota = ""; wwff = ""; pota = ""
        comment = ""; notes = ""; country = ""; dxcc = ""; continent = ""; region = ""; cqZone = ""; ituZone = ""
        stateName = ""; email = ""; propagation = ""; antennaPath = ""; qslSent = "N"; qslMethod = ""; qslVia = ""; qslMessage = ""
        enrichment = "Enter a callsign"; tab = QsoEditorTab.QSO
    }
    fun applyCty() {
        cty.lookup(call)?.let { row ->
            country = country.ifBlank { row.country }; dxcc = dxcc.ifBlank { row.dxcc }; continent = continent.ifBlank { row.continent }
            region = region.ifBlank { row.region }; cqZone = cqZone.ifBlank { row.cqZone }; ituZone = ituZone.ifBlank { row.ituZone }
            enrichment = "CTY.DAT fallback"
        }
    }
    fun enrich() {
        val requestedCall = call.trim().uppercase()
        if (requestedCall.isBlank()) return
        val generation = ++lookupGeneration
        enrichment = "Looking up $requestedCall…"
        callbook.lookup(requestedCall) { row ->
            if (generation != lookupGeneration || call.trim().uppercase() != requestedCall) return@lookup
            if (row == null) applyCty() else {
                name = row.name.ifBlank { name }; qth = row.qth.ifBlank { qth }; country = row.country.ifBlank { country }; grid = row.grid.ifBlank { grid }
                dxcc = row.dxcc.ifBlank { dxcc }; continent = row.continent.ifBlank { continent }; region = row.region.ifBlank { region }
                cqZone = row.cqZone.ifBlank { cqZone }; ituZone = row.ituZone.ifBlank { ituZone }; stateName = row.state.ifBlank { stateName }
                email = row.email.ifBlank { email }; applyCty(); enrichment = "${callbook.provider} + CTY.DAT"
            }
        }
    }
    LaunchedEffect(call) {
        lookupGeneration++
        val candidate = call.trim()
        if (candidate.length >= 3) { delay(700); if (candidate == call.trim()) enrich() }
    }
    Surface(color = Color(0xFF121617), shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444B4E)), modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("LOG QSO", color = Amber, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text(status, color = if (status.startsWith("SAVED")) Healthy else Muted, style = MaterialTheme.typography.labelSmall)
            }
            ScrollableTabRow(tab.ordinal, containerColor = Color(0xFF202526), edgePadding = 0.dp, divider = {}) {
                QsoEditorTab.entries.forEach { item -> Tab(tab == item, { tab = item }, text = { Text(item.label, fontWeight = FontWeight.Bold) }) }
            }
            Column(Modifier.weight(1f).padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                when (tab) {
                    QsoEditorTab.QSO -> {
                        InstrumentStrip(Color(0xFF78909C)) {
                            LiveField("UTC date", utc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), Modifier.width(116.dp))
                            LiveField("UTC time", utc.format(DateTimeFormatter.ofPattern("HH:mm:ss")), Modifier.width(100.dp))
                            OutlinedTextField(call, { call = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '/' } }, label = { Text("Callsign") },
                                singleLine = true, modifier = Modifier.width(178.dp), colors = logFieldColors())
                            OutlinedButton(::enrich, enabled = call.length >= 3, modifier = Modifier.width(92.dp).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp)) { Text("LOOKUP", style = MaterialTheme.typography.labelMedium) }
                        }
                        Surface(color = (if (enrichment.contains("QRZ") || enrichment.contains("HamQTH")) Healthy else Color(0xFF78909C)).copy(alpha = .14f),
                            shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("ENRICHMENT  ·  $enrichment", color = if (enrichment.contains("QRZ") || enrichment.contains("HamQTH")) Healthy else Muted,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        InstrumentStrip(Amber) {
                            LiveField("Mode", state.mode.ifBlank { "—" }, Modifier.width(72.dp)); LiveField("Band", bandForFrequency(state.frequencyHz), Modifier.width(72.dp))
                            LiveField("Frequency", if (state.frequencyHz > 0) formatRadioFrequency(state.frequencyHz) else "—", Modifier.width(118.dp))
                            LogField("RST S", sent, { sent = it.take(3) }, Modifier.width(68.dp)); LogField("RST R", received, { received = it.take(3) }, Modifier.width(68.dp))
                            LogField("Name", name, { name = it }, Modifier.weight(1f))
                        }
                        InstrumentStrip(Healthy) {
                            LogField("IOTA", iota, { iota = it.uppercase() }, Modifier.weight(1f)); LogField("SOTA", sota, { sota = it.uppercase() }, Modifier.weight(1f))
                            LogField("WWFF", wwff, { wwff = it.uppercase() }, Modifier.weight(1f)); LogField("POTA", pota, { pota = it.uppercase() }, Modifier.weight(1f))
                        }
                        InstrumentStrip(Color(0xFF65A6C7)) {
                            LogField("Location / QTH", qth, { qth = it }, Modifier.weight(1f)); LogField("Gridsquare", grid, { grid = it.uppercase() }, Modifier.weight(1f))
                            LogField("Comment", comment, { comment = it }, Modifier.weight(1f))
                        }
                        InstrumentStrip(Hold, Modifier.height(56.dp)) {
                            app.macroLabels.forEachIndexed { index, label -> Kx3DirectKey(label.ifBlank { listOf("CQ", "EXCH", "TU")[index] },
                                state.connected && app.cwMacrosArmed, { app.macroTexts[index].takeIf(String::isNotBlank)?.let { send("KY${it};") } }, Modifier.weight(1f), secondary = true) }
                        }
                    }
                    QsoEditorTab.STATION -> {
                        ChoiceField("Station location", selectedStation?.label ?: app.stationName.ifBlank { "Local station" },
                            wavelog.stations.map { it.id to it.label }, wavelog.stationId, wavelog::setStation)
                        LiveField("Radio", if (state.connected) "Elecraft KX3" else "Elecraft KX3 · CAT offline")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiveField("Frequency RX", if (state.frequencyHz > 0) formatRadioFrequency(state.frequencyHz) else "—", Modifier.weight(1f))
                            LiveField("Band RX", bandForFrequency(state.frequencyHz), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiveField("Transmit power", if (state.connected) "${state.powerW} W" else "—", Modifier.weight(1f))
                            LiveField("Operator callsign", app.stationCallsign.ifBlank { selectedStation?.callsign.orEmpty() }.ifBlank { "Not configured" }, Modifier.weight(1f))
                        }
                    }
                    QsoEditorTab.GENERAL -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LogField("DXCC", dxcc, { dxcc = it.filter(Char::isDigit) }, Modifier.weight(1f)); ChoiceField("Continent", continentName(continent),
                                continentChoices, continent, { continent = it }, Modifier.weight(1f)); LogField("Region", region, { region = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceField("CQ zone", cqZone, (0..40).map { it.toString() to it.toString() }, cqZone, { cqZone = it }, Modifier.weight(1f))
                            ChoiceField("ITU zone", ituZone, (0..90).map { it.toString() to it.toString() }, ituZone, { ituZone = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceField("Propagation mode", propagationLabel(propagation), propagationChoices, propagation, { propagation = it }, Modifier.weight(1f))
                            ChoiceField("Antenna path", antennaPathLabel(antennaPath), antennaPathChoices, antennaPath, { antennaPath = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LogField("State", stateName, { stateName = it.uppercase() }, Modifier.weight(1f)); LogField("E-mail", email, { email = it }, Modifier.weight(1f))
                        }
                        LogField("Country", country, { country = it }, Modifier.fillMaxWidth())
                    }
                    QsoEditorTab.NOTES -> LogField("QSO note · exported to third-party services", notes, { notes = it }, Modifier.fillMaxWidth().heightIn(min = 190.dp), singleLine = false)
                    QsoEditorTab.QSL -> {
                        ChoiceField("Sent", qslSentLabel(qslSent), qslSentChoices, qslSent, { qslSent = it })
                        ChoiceField("Method", qslMethodLabel(qslMethod), qslMethodChoices, qslMethod, { qslMethod = it })
                        LogField("Via", qslVia, { qslVia = it }, Modifier.fillMaxWidth())
                        LogField("QSL message · exported to third-party services", qslMessage, { qslMessage = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), singleLine = false)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(::clear, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("CLEAR") }
                Button({
                    val now = wavelog.synchronizedNow(); val id = NativeCore.qsoIdentity(call, DateTimeFormatter.ISO_INSTANT.format(now), state.frequencyHz, state.mode)
                    applyCty(); val station = wavelog.selectedStation
                    val qso = Qso(id, call, state.frequencyHz, state.mode, sent, received, now.epochSecond, name, qth, notes, country,
                        band = bandForFrequency(state.frequencyHz), grid = grid, iota = iota, sotaRef = sota, wwffRef = wwff, potaRef = pota,
                        comment = comment, frequencyRxHz = state.frequencyHz, bandRx = bandForFrequency(state.frequencyHz), txPowerW = state.powerW,
                        operatorCallsign = app.stationCallsign.ifBlank { station?.callsign.orEmpty() }, stationCallsign = station?.callsign ?: app.stationCallsign,
                        stationProfileId = if (wavelog.logMode == LogMode.WAVELOG) wavelog.stationId else "", stationLocation = station?.name ?: app.stationName,
                        myGrid = station?.grid ?: app.stationGrid, myCountry = station?.country.orEmpty(), myDxcc = station?.dxcc.orEmpty(),
                        myCqZone = station?.cqZone.orEmpty(), myItuZone = station?.ituZone.orEmpty(), myState = station?.state.orEmpty(),
                        myIota = station?.iota.orEmpty(), mySotaRef = station?.sotaRef.orEmpty(), myWwffRef = station?.wwffRef.orEmpty(), myPotaRef = station?.potaRef.orEmpty(),
                        radioModel = "Elecraft KX3", dxcc = dxcc, continent = continent, region = region, cqZone = cqZone,
                        ituZone = ituZone, state = stateName, email = email, propagationMode = propagation, antennaPath = antennaPath,
                        qslSent = qslSent, qslMethod = qslMethod, qslVia = qslVia, qslMessage = qslMessage,
                        syncState = if (wavelog.logMode == LogMode.WAVELOG) "pending" else "local")
                    if (call.isBlank() || !state.connected || state.frequencyHz <= 0) status = "CALL / LIVE CAT REQUIRED"
                    else if (!database.save(qso)) status = "DUPLICATE NOT SAVED"
                    else {
                        if (wavelog.logMode == LogMode.WAVELOG) { wavelog.enqueue(id, database.toADIF(qso)); status = "SAVED · TWO-WAY SYNC QUEUED" }
                        else status = "SAVED · LOCAL ADIF"
                        clear()
                    }
                }, enabled = state.connected && call.isNotBlank(), modifier = Modifier.weight(2f).heightIn(min = 48.dp)) {
                    Text(if (wavelog.logMode == LogMode.WAVELOG) "SAVE & SYNC QSO" else "SAVE LOCAL QSO", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable private fun InstrumentStrip(tint: Color, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) = Row(
    modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(tint.copy(alpha = .13f), Color.Transparent)), RoundedCornerShape(7.dp))
        .border(1.dp, tint.copy(alpha = .32f), RoundedCornerShape(7.dp)).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable private fun LogField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) =
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = singleLine, modifier = modifier,
        colors = logFieldColors())

@Composable private fun logFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Amber, unfocusedBorderColor = Color(0xFF58656C),
    focusedContainerColor = Color(0xFF26271F), unfocusedContainerColor = Color(0xFF1A2024),
    focusedLabelColor = Amber, unfocusedLabelColor = Muted)

@Composable private fun LiveField(label: String, value: String, modifier: Modifier = Modifier) = OutlinedTextField(value, {}, label = { Text(label) },
    readOnly = true, singleLine = true, modifier = modifier, colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF445057), unfocusedBorderColor = Color(0xFF445057),
        focusedContainerColor = Color(0xFF14191C), unfocusedContainerColor = Color(0xFF14191C),
        focusedLabelColor = Hold, unfocusedLabelColor = Hold, focusedTextColor = Ink, unfocusedTextColor = Ink))

@Composable private fun ChoiceField(label: String, display: String, choices: List<Pair<String, String>>, selected: String,
    change: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Color(0xFF1A2024), MaterialTheme.shapes.extraLarge), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text(display.ifBlank { "None" }, maxLines = 1) }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 340.dp)) {
            choices.forEach { (value, text) -> DropdownMenuItem({ Text(text) }, onClick = { change(value); expanded = false },
                trailingIcon = { if (selected == value) Icon(Icons.Outlined.Check, null) }) }
        }
    }
}

private val continentChoices = listOf("AF" to "Africa", "AN" to "Antarctica", "AS" to "Asia", "EU" to "Europe", "NA" to "North America", "OC" to "Oceania", "SA" to "South America")
private fun continentName(code: String) = continentChoices.firstOrNull { it.first == code }?.second ?: code
private val propagationChoices = listOf("" to "None", "AS" to "Aircraft Scatter", "AUR" to "Aurora", "AUE" to "Aurora-E", "BS" to "Back scatter",
    "ECH" to "EchoLink", "EME" to "Earth-Moon-Earth", "ES" to "Sporadic E", "FAI" to "Field Aligned Irregularities", "F2" to "F2 Reflection",
    "GW" to "Ground Wave", "INET" to "Internet-assisted", "ION" to "Ionoscatter", "IRL" to "IRLP", "LOS" to "Line of Sight", "MS" to "Meteor scatter",
    "RPT" to "Repeater or transponder", "RS" to "Rain scatter", "SAT" to "Satellite", "TEP" to "Trans-equatorial", "TR" to "Tropospheric ducting")
private fun propagationLabel(code: String) = propagationChoices.firstOrNull { it.first == code }?.second ?: code
private val antennaPathChoices = listOf("" to "None", "G" to "Greyline", "O" to "Other", "S" to "Short Path", "L" to "Long Path")
private fun antennaPathLabel(code: String) = antennaPathChoices.firstOrNull { it.first == code }?.second ?: code
private val qslSentChoices = listOf("N" to "No", "Y" to "Yes", "R" to "Requested", "Q" to "Queued", "I" to "Ignore")
private fun qslSentLabel(code: String) = qslSentChoices.firstOrNull { it.first == code }?.second ?: code
private val qslMethodChoices = listOf("" to "None", "B" to "Bureau", "D" to "Direct", "E" to "Electronic", "M" to "Manager")
private fun qslMethodLabel(code: String) = qslMethodChoices.firstOrNull { it.first == code }?.second ?: code

@Composable private fun Kx3TuningDeck(state: RadioState, send: (String) -> Unit, direct: (String) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(100) }
    var af by remember(state.afGain) { mutableFloatStateOf(state.afGain.toFloat()) }
    var rf by remember(state.rfGain) { mutableFloatStateOf(state.rfGain.toFloat()) }
    var bw by remember(state.bandwidthHz) { mutableFloatStateOf(state.bandwidthHz.coerceIn(100, 4000).toFloat()) }
    var power by remember(state.powerW) { mutableFloatStateOf(state.powerW.coerceIn(0, 12).toFloat()) }
    Surface(color = Color(0xFF121617), shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444B4E)), modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TUNING DECK", color = Amber, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text("DRAG TO TUNE", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Kx3Knob("AF", af, 0f..255f, { af = it }, { send("AG%03d;".format(af.toInt())) }, Modifier.weight(1f))
                        Kx3Knob("RF", rf, 0f..255f, { rf = it }, { send("RG%03d;".format(rf.toInt())) }, Modifier.weight(1f))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Kx3Knob("BW", bw, 100f..4000f, { bw = it }, { send("BW%04d;".format(bw.toInt())) }, Modifier.weight(1f), "${bw.toInt()} Hz")
                        Kx3Knob("PWR", power, 0f..12f, { power = it }, { send("PC%03d;".format(power.toInt())) }, Modifier.weight(1f), "${power.toInt()} W")
                    }
                }
                Kx3VfoWheel(state, step, send, { step = when (step) { 10 -> 100; 100 -> 1000; 1000 -> 10000; else -> 10 } }, Modifier.weight(1.1f))
            }
            OutlinedButton({ direct("RX;") }, enabled = state.connected, modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Healthy)) { Text("EMERGENCY RX", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable private fun Kx3Knob(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit,
    finish: () -> Unit, modifier: Modifier = Modifier, display: String = value.toInt().toString()) {
    val normalized = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Column(modifier.pointerInput(range) { detectDragGestures(onDragEnd = finish) { event, drag ->
        event.consume(); change((value - drag.y * (range.endInclusive - range.start) / 180f).coerceIn(range))
    } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.sizeIn(maxWidth = 68.dp, maxHeight = 68.dp).aspectRatio(1f)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF777D80), Color(0xFF292D2F), Color(0xFF090A0B))))
            drawCircle(Color(0xFF8D9396), style = Stroke(1.dp.toPx()))
            rotate(-130f + normalized * 260f) {
                drawLine(Hold, Offset(size.width / 2, size.height * .12f), Offset(size.width / 2, size.height * .33f), 3.dp.toPx())
            }
        }
        Text(display, color = Hold, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun Kx3VfoWheel(state: RadioState, step: Int, send: (String) -> Unit, cycleStep: () -> Unit, modifier: Modifier = Modifier) {
    var wheelTarget by remember { mutableFloatStateOf(0f) }
    var previousFrequency by remember { mutableLongStateOf(state.frequencyHz) }
    var pendingFrequency by remember { mutableLongStateOf(-1L) }
    var commandPixels by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val currentFrequency by rememberUpdatedState(state.frequencyHz)
    LaunchedEffect(state.frequencyHz, step) {
        if (state.frequencyHz <= 0) return@LaunchedEffect
        if (!dragging && previousFrequency > 0 && state.frequencyHz != previousFrequency) {
            val turns = ((state.frequencyHz - previousFrequency).toFloat() / step.toFloat()).coerceIn(-12f, 12f)
            wheelTarget += turns * 15f
        }
        previousFrequency = state.frequencyHz
    }
    val wheelRotation by animateFloatAsState(wheelTarget, tween(durationMillis = 60), label = "KX3 VFO rotation")
    Box(modifier.aspectRatio(1f).pointerInput(step, state.connected) {
        fun finishGesture() {
            dragging = false
            pendingFrequency = -1L
            commandPixels = 0f
        }
        detectDragGestures(
            onDragStart = {
                dragging = true
                pendingFrequency = currentFrequency
                commandPixels = 0f
            },
            onDragEnd = ::finishGesture,
            onDragCancel = ::finishGesture,
        ) { event, drag ->
            event.consume()
            wheelTarget += drag.x * .72f
            commandPixels += drag.x
            var changed = false
            while (abs(commandPixels) >= 12f && state.connected) {
                val direction = if (commandPixels > 0f) 1 else -1
                pendingFrequency = (pendingFrequency + direction * step).coerceAtLeast(0)
                commandPixels -= direction * 12f
                changed = true
            }
            if (changed) send("FA%011d;".format(pendingFrequency))
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF050607)); drawCircle(Color(0xFF697176), style = Stroke(5.dp.toPx()))
            drawCircle(Color(0xFF232729), radius = size.minDimension * .38f)
            rotate(wheelRotation) {
                repeat(24) { index -> rotate(index * 15f) {
                    val end = if (index % 3 == 0) size.height * .115f else size.height * .09f
                    drawLine(Color(0xFF929A9E), Offset(size.width / 2, size.height * .025f), Offset(size.width / 2, end),
                        if (index % 3 == 0) 3.dp.toPx() else 2.dp.toPx())
                } }
                drawCircle(Hold, radius = 5.dp.toPx(), center = Offset(size.width / 2, size.height * .145f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF777E82), Color(0xFF25292B), Color(0xFF0B0C0D))), radius = size.minDimension * .28f)
                drawLine(Color(0xFFAAB0B3), Offset(size.width / 2, size.height * .235f), Offset(size.width / 2, size.height * .33f), 3.dp.toPx())
            }
        }
        Surface(onClick = cycleStep, color = Color(0xFF1A1D1F), shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF666E72)), modifier = Modifier.size(94.dp)) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("VFO", color = Ink, fontWeight = FontWeight.Black)
                Text(if (step >= 1000) "${step / 1000} kHz" else "$step Hz", color = Hold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable private fun FrequencyCard(state: RadioState, send: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("TUNING", color = Amber, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("MHz") }, modifier = Modifier.weight(1f), singleLine = true)
            Button({ value.toDoubleOrNull()?.let { send("FA%011d;".format((it * 1_000_000).toLong())) } }, enabled = state.connected) { Text("SET VFO A") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ send("DN;") }, enabled = state.connected) { Text("VFO −") }
            OutlinedButton({ send("UP;") }, enabled = state.connected) { Text("VFO +") }
            listOf("LSB" to "1", "USB" to "2", "CW" to "3", "FM" to "4", "AM" to "5", "DATA" to "6").forEach { (label, code) ->
                FilterChip(state.mode == label, { send("MD$code;") }, { Text(label) }, enabled = state.connected)
            }
        }
    } }
}

@Composable private fun AdjustmentCard(state: RadioState, send: (String) -> Unit) {
    var af by remember(state.afGain) { mutableFloatStateOf(state.afGain.toFloat()) }
    var rf by remember(state.rfGain) { mutableFloatStateOf(state.rfGain.toFloat()) }
    var bw by remember(state.bandwidthHz) { mutableFloatStateOf(state.bandwidthHz.coerceAtLeast(100).toFloat()) }
    var power by remember(state.powerW) { mutableFloatStateOf(state.powerW.coerceIn(0, 12).toFloat()) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp)) {
        Text("RADIO CONTROLS", color = Amber, fontWeight = FontWeight.Bold)
        SliderLine("AF GAIN", af, 0f..255f, { af = it }, { send("AG%03d;".format(af.toInt())) })
        SliderLine("RF GAIN", rf, 0f..255f, { rf = it }, { send("RG%03d;".format(rf.toInt())) })
        SliderLine("BANDWIDTH", bw, 100f..4000f, { bw = it }, { send("BW%04d;".format(bw.toInt())) }, "${bw.toInt()} Hz")
        SliderLine("POWER LIMIT", power, 0f..12f, { power = it }, { send("PC%03d;".format(power.toInt())) }, "${power.toInt()} W")
    } }
}

@Composable private fun SliderLine(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit, finish: () -> Unit, display: String = value.toInt().toString()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Muted); Text(display, fontFamily = FontFamily.Monospace) }
    Slider(value, change, valueRange = range, onValueChangeFinished = finish)
}

@Composable private fun AudioCard(audio: AudioMonitorController) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) audio.start() }
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("USB RECEIVE AUDIO", color = Amber, fontWeight = FontWeight.Bold); Text("StarTech input → tablet speaker", color = Muted) }
            Switch(audio.enabled, { if (it) permission.launch(Manifest.permission.RECORD_AUDIO) else audio.stop() })
        }
        Text("IN  ${audio.inputName}", color = Muted); Text("OUT ${audio.outputName}", color = Muted)
        LinearProgressIndicator({ audio.level }, Modifier.fillMaxWidth(), color = Healthy)
        SliderLine("MONITOR GAIN", audio.gain, 0f..12f, audio::updateGain, {}, "%.1fx".format(audio.gain))
        Text(audio.status, color = if (audio.enabled) Healthy else Muted)
        Text("Receive-only. Headphones are recommended to prevent feedback.", color = Hold)
    } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ControlsScreen(state: RadioState, app: AppController, send: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }; var selected by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Header("KX3 controls", state)
            Button({ editing = !editing; if (!editing) selected = -1 }) { Text(if (editing) "DONE" else "EDIT ORDER") }
        }
        Text(if (editing) "Select a control, then move it earlier or later." else "Tap sends the white function · press and hold sends the yellow function.", color = Muted)
        LazyVerticalGrid(GridCells.Fixed(6), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(app.controlOrder.size) { position ->
                val index = app.controlOrder[position]; val control = AppController.controls[index]
                Surface(color = if (selected == position) Amber.copy(alpha = .25f) else Raised,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(92.dp)
                        .border(if (selected == position) 2.dp else 1.dp, if (selected == position) Amber else Color(0xFF4A555D), MaterialTheme.shapes.small)
                        .combinedClickable(enabled = editing || state.connected, onClick = {
                            if (editing) selected = position else send(control.tapCommand)
                        }, onLongClick = { if (!editing) send(control.holdCommand) })) {
                    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(control.tapLabel, color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(control.holdLabel, color = Hold, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
        if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ app.moveControl(selected, -1); selected = (selected - 1).coerceAtLeast(0) }, enabled = selected > 0, modifier = Modifier.weight(1f)) { Text("‹ EARLIER") }
            OutlinedButton({ app.moveControl(selected, 1); selected = (selected + 1).coerceAtMost(app.controlOrder.lastIndex) }, enabled = selected in 0 until app.controlOrder.lastIndex, modifier = Modifier.weight(1f)) { Text("LATER ›") }
            OutlinedButton({ app.resetControlOrder(); selected = -1 }, modifier = Modifier.weight(1f)) { Text("RESET") }
        }
    }
}

@Composable private fun PresetsScreen(state: RadioState, app: AppController, send: (String) -> Unit) {
    var editing by remember { mutableStateOf<RadioPreset?>(null) }; var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Header("Radio presets", state); Button({ adding = true }) { Text("ADD PRESET") }
        }
        Text("QUICK BAND", color = Muted, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("160m" to 1_850_000L, "80m" to 3_550_000L, "40m" to 7_100_000L, "30m" to 10_120_000L,
                "20m" to 14_200_000L, "17m" to 18_100_000L, "15m" to 21_250_000L, "12m" to 24_930_000L, "10m" to 28_400_000L, "6m" to 50_150_000L)
                .forEach { (label, hz) -> OutlinedButton({ send("FA%011d;".format(hz)) }, enabled = state.connected) { Text(label) } }
        }
        Text("QUICK MODE", color = Muted, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("CW", "USB", "LSB", "AM", "DATA").forEach { mode -> FilterChip(state.mode == mode, { send("MD${modeCode(mode)};") }, { Text(mode) }, enabled = state.connected) }
        }
        if (app.presets.isEmpty()) Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Text("NO USER PRESETS YET\nTap ADD PRESET to save the current frequency, mode and filter.", color = Muted, modifier = Modifier.padding(24.dp))
        } else LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(app.presets) { preset -> Card(colors = CardDefaults.cardColors(containerColor = Color(preset.color))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(formatRadioFrequency(preset.frequencyHz), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("${preset.mode} · ${preset.bandwidthHz} Hz", color = Ink.copy(alpha = .8f))
                    Row { Button({ send("FA%011d;MD%s;BW%04d;".format(preset.frequencyHz, modeCode(preset.mode), preset.bandwidthHz)) }, enabled = state.connected) { Text("APPLY") }
                        TextButton({ editing = preset }) { Text("EDIT", color = Ink) } }
                }
            } }
        }
    }
    if (adding || editing != null) PresetDialog(state, app, editing, onClose = { adding = false; editing = null })
}

@Composable private fun PresetDialog(state: RadioState, app: AppController, preset: RadioPreset?, onClose: () -> Unit) {
    var frequency by remember(preset) { mutableStateOf(preset?.let { formatRadioFrequency(it.frequencyHz) } ?: if (state.frequencyHz > 0) formatRadioFrequency(state.frequencyHz) else "") }
    var mode by remember(preset) { mutableStateOf(preset?.mode ?: state.mode.ifBlank { "CW" }) }
    var bandwidth by remember(preset) { mutableIntStateOf(preset?.bandwidthHz ?: state.bandwidthHz.coerceAtLeast(500)) }
    var colorIndex by remember(preset) { mutableIntStateOf(AppController.presetColors.indexOf(preset?.color).coerceAtLeast(0)) }
    val filters = when (mode) {
        "CW" -> listOf(100, 200, 300, 400, 500, 1000); "DATA" -> listOf(200, 400, 500, 1000, 2000, 3000)
        "AM" -> listOf(3000, 4000, 5000, 6000, 7000, 8000); else -> listOf(1800, 2100, 2400, 2700, 3000, 3500)
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (preset == null) "ADD PRESET" else "EDIT PRESET") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(frequency, { frequency = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Frequency · 14.200.000 or 14200000") }, singleLine = true)
            Text("MODE", color = Muted); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("CW", "USB", "LSB", "AM", "DATA").forEach { value -> FilterChip(mode == value, { mode = value; bandwidth = when (value) { "CW" -> 500; "AM" -> 6000; "DATA" -> 1000; else -> 2400 } }, { Text(value) }) }
            }
            Text("FILTER", color = Muted); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                filters.forEach { value -> FilterChip(bandwidth == value, { bandwidth = value }, { Text("$value") }) }
            }
            Text("COLOUR", color = Muted); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppController.presetColors.forEachIndexed { index, color -> Surface(color = Color(color), shape = CircleShape,
                    modifier = Modifier.size(34.dp).border(if (colorIndex == index) 3.dp else 1.dp, if (colorIndex == index) Ink else Muted, CircleShape)
                        .combinedClickable(onClick = { colorIndex = index }, onLongClick = {})) {} }
            }
            if (preset != null) Row { TextButton({ app.deletePreset(preset.slot); onClose() }) { Text("DELETE", color = Danger) }
                TextButton({ app.movePreset(preset.slot, -1) }) { Text("MOVE LEFT") }; TextButton({ app.movePreset(preset.slot, 1) }) { Text("MOVE RIGHT") } }
        } },
        confirmButton = { Button({
            val digits = frequency.filter(Char::isDigit); val hz = digits.toLongOrNull()?.let { if (it < 1_000_000) (frequency.toDoubleOrNull()?.times(1_000_000))?.toLong() else it }
            if (hz != null) app.savePreset(preset?.slot ?: ((app.presets.maxOfOrNull { it.slot } ?: -1) + 1).coerceAtMost(11), hz, mode, bandwidth, colorIndex)
            onClose()
        }) { Text(if (preset == null) "ADD" else "SAVE") } }, dismissButton = { TextButton(onClose) { Text("CANCEL") } })
}

private fun modeCode(mode: String) = when (mode.uppercase()) { "LSB" -> "1"; "USB" -> "2"; "CW" -> "3"; "FM" -> "4"; "AM" -> "5"; else -> "6" }
private enum class DXView { LIVE, SMART, BANDMAP, PULSE, WORLD, WATCH }

@Composable private fun DXScreen(features: FeatureController, send: (String) -> Unit) {
    var view by remember { mutableStateOf(DXView.LIVE) }; var selected by remember { mutableStateOf<AndroidDXSpot?>(null) }; var band by remember { mutableStateOf("ALL") }
    Page {
        Header("Unified DX intelligence"); Text(features.clusterStatus, color = Muted); Text(features.dxSummary, color = Amber)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DXView.entries.forEach { FilterChip(view == it, { view = it }, { Text(it.name) }) }
        }
        val source = when (view) { DXView.WATCH -> features.watchSpots; DXView.SMART -> features.spots; else -> features.liveSpots }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("ALL") + source.map { it.band }.filter { it.isNotBlank() }.distinct()).forEach { value -> FilterChip(band == value, { band = value }, { Text(value) }) }
        }
        when (view) {
            DXView.BANDMAP -> features.dxBands.forEach { TableRow(it.band, "${it.uniqueCalls} calls · ${it.spots5m}/5m · ${it.spots60m}/60m", "${it.surgePercent}%", it.surge) }
            DXView.PULSE -> features.dxRegions.forEach { TableRow(it.region, "${it.uniqueCalls} calls · ${it.spots15m}/15m · ${it.spots60m}/60m", "${it.activityPercent}%", it.anomaly) }
            DXView.WORLD -> Text(if (features.dxWorld.isEmpty()) "No live world matrix yet" else "World activity cells: ${features.dxWorld.sumOf { it.size }}", color = Muted)
            else -> {
                val rows = source.filter { band == "ALL" || it.band == band }.take(20)
                if (rows.isEmpty()) Text("No accepted live DX spots. Connect the cluster in Settings; no fixture data is shown.", color = Muted)
                rows.forEach { spot -> Card(onClick = { selected = spot }, colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text(spot.callsign, fontWeight = FontWeight.Black, color = if (spot.watchlisted) Hold else Ink); Text(spot.country, color = Muted) }
                        Column(horizontalAlignment = Alignment.End) { Text(formatRadioFrequency(spot.frequencyHz), color = Amber, fontFamily = FontFamily.Monospace); Text("${spot.band} · ${spot.mode} · ${spot.score}", color = Muted) }
                    }
                } }
            }
        }
    }
    selected?.let { spot -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(spot.callsign) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${formatRadioFrequency(spot.frequencyHz)} MHz · ${spot.band} · ${spot.mode}")
            Text(listOf(spot.country, spot.continent, if (spot.distanceKm > 0) "${spot.distanceKm} km @ ${spot.bearingDegrees}°" else "").filter { it.isNotBlank() }.joinToString(" · "))
            Text("Score ${spot.score} · confidence ${spot.confidence} · ${spot.samples} samples")
            Text(spot.reason.ifBlank { spot.comment }.ifBlank { "No additional analysis" }, color = Muted)
        } }, confirmButton = { Button({ send("FA%011d;".format(spot.frequencyHz)); selected = null }) { Text("Tune VFO A") } },
        dismissButton = { TextButton({ selected = null }) { Text("Close") } })
    }
}

@Composable private fun TableRow(title: String, detail: String, trailing: String, alert: Boolean) {
    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.Bold) }, supportingContent = { Text(detail) },
        trailingContent = { Text(trailing, color = if (alert) Hold else Muted) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable private fun LogbookScreen(state: RadioState, database: QsoDatabase, wavelog: WavelogController) {
    var call by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var qth by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }; var sent by remember { mutableStateOf("59") }; var received by remember { mutableStateOf("59") }
    var message by remember { mutableStateOf("") }; var records by remember { mutableStateOf(database.list()) }
    Page {
        Header("Local-first logbook", state); Text("NEW QSO", color = Amber, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(call, { call = it.uppercase() }, label = { Text("Callsign") }, modifier = Modifier.weight(1f))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
            OutlinedTextField(qth, { qth = it }, label = { Text("QTH") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(sent, { sent = it }, label = { Text("RST sent") }, modifier = Modifier.weight(1f))
            OutlinedTextField(received, { received = it }, label = { Text("RST received") }, modifier = Modifier.weight(1f))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.weight(2f))
        }
        Button({
            val now = wavelog.synchronizedNow()
            if (call.isBlank() || !state.connected || state.frequencyHz <= 0) message = "Connect the radio and enter a callsign"
            else { val id = NativeCore.qsoIdentity(call, DateTimeFormatter.ISO_INSTANT.format(now), state.frequencyHz, state.mode)
                val qso = Qso(id, call, state.frequencyHz, state.mode, sent, received, now.epochSecond, name, qth, notes,
                    band = bandForFrequency(state.frequencyHz), frequencyRxHz = state.frequencyHz,
                    bandRx = bandForFrequency(state.frequencyHz), txPowerW = state.powerW,
                    syncState = if (wavelog.logMode == LogMode.WAVELOG) "pending" else "local")
                val saved = database.save(qso); message = if (saved) "Saved locally before sync" else "Immediate duplicate not saved"
                if (saved) {
                    if (wavelog.logMode == LogMode.WAVELOG) wavelog.enqueue(id, database.toADIF(qso))
                    call = ""; records = database.list()
                }
            }
        }, enabled = state.connected) { Text("Save QSO locally") }
        Text(message, color = Muted); Text("LOCAL LOG · ${records.size}    WAVELOG · ${wavelog.contacts.size} cached · ${wavelog.pendingCount} queued", color = Amber)
        records.take(25).forEach { qso -> ListItem(headlineContent = { Text(qso.callsign, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("${formatRadioFrequency(qso.frequencyHz)} MHz · ${qso.mode} · ${qso.name} ${qso.qth}".trim()) },
            trailingContent = { Text(Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
    }
}

@Composable private fun SettingsScreen(state: RadioState, detail: String, database: QsoDatabase, features: FeatureController, wavelog: WavelogController,
    callbook: CallbookController, cty: CtyController,
    audio: AudioMonitorController, app: AppController, direct: (String) -> Unit) {
    var section by remember { mutableStateOf(SettingsSection.DEFAULT) }
    var host by remember { mutableStateOf(features.clusterHost) }; var port by remember { mutableStateOf(features.clusterPort.toString()) }
    var fallbackHost by remember { mutableStateOf(features.fallbackHost) }; var fallbackPort by remember { mutableStateOf(features.fallbackPort.toString()) }
    var fallback2Host by remember { mutableStateOf(features.fallback2Host) }; var fallback2Port by remember { mutableStateOf(features.fallback2Port.toString()) }
    var callsign by remember { mutableStateOf(features.clusterCallsign) }; var watch by remember { mutableStateOf("") }; var raw by remember { mutableStateOf("") }
    var stationCall by remember { mutableStateOf(app.stationCallsign) }; var stationName by remember { mutableStateOf(app.stationName) }
    var stationGrid by remember { mutableStateOf(app.stationGrid) }; var repeatSeconds by remember { mutableIntStateOf(app.cqRepeatSeconds) }
    var callbookProvider by remember { mutableStateOf(callbook.provider) }; var callbookUser by remember { mutableStateOf(callbook.username) }
    var callbookPassword by remember { mutableStateOf(callbook.password) }
    val macroLabels = remember { mutableStateListOf(*app.macroLabels.toTypedArray()) }
    val macroTexts = remember { mutableStateListOf(*app.macroTexts.toTypedArray()) }
    var profile by remember { mutableStateOf(app.fieldProfile) }; var brightness by remember { mutableFloatStateOf(app.brightness.toFloat()) }
    var autoDim by remember { mutableStateOf(app.autoDim) }; var tones by remember { mutableStateOf(app.alertTones) }
    var quiet by remember { mutableStateOf(app.quietAlerts) }; var program by remember { mutableStateOf(app.activationProgram) }
    var activation by remember { mutableStateOf(app.activationReference) }; var systemMessage by remember { mutableStateOf("No recovery operation run this session") }
    var restorePayload by remember { mutableStateOf<String?>(null) }; val context = LocalContext.current
    val exportRecovery = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(app.recoveryText()) } }
            .onSuccess { systemMessage = "Recovery file exported" }.onFailure { error -> systemMessage = "Export failed: ${error.message}" } }
    }
    val openRecovery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty() }
            .onSuccess { payload -> systemMessage = app.reviewRecovery(payload); if (systemMessage.startsWith("Valid")) restorePayload = payload }
            .onFailure { error -> systemMessage = "Restore review failed: ${error.message}" } }
    }
    val exportAdif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-adif")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(database.exportADIF()) } }
            .onSuccess { systemMessage = "ADIF exported from tablet database" }.onFailure { error -> systemMessage = "ADIF export failed: ${error.message}" } }
    }
    val importAdif = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> database.importADIF(reader.readText()) } ?: (0 to 0) }
            .onSuccess { result -> systemMessage = "ADIF import · ${result.first} added · ${result.second} skipped" }
            .onFailure { error -> systemMessage = "ADIF import failed: ${error.message}" } }
    }
    Page {
        Header("Complete station settings", state)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SettingsSection.entries.forEach { item -> FilterChip(section == item, { section = item }, { Text(item.label) }) }
        }
        if (section == SettingsSection.DEFAULT || section == SettingsSection.MACROS) SettingsCard(if (section == SettingsSection.DEFAULT) "LOCAL STATION" else "CW MACROS") {
            if (section == SettingsSection.DEFAULT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(stationCall, { stationCall = it.uppercase() }, label = { Text("Callsign") }, modifier = Modifier.weight(1f))
                OutlinedTextField(stationName, { stationName = it }, label = { Text("Operator / station") }, modifier = Modifier.weight(1f))
                OutlinedTextField(stationGrid, { stationGrid = it.uppercase() }, label = { Text("Grid") }, modifier = Modifier.weight(1f))
            }
            Button({ app.saveLocalSettings(stationCall, stationName, stationGrid, repeatSeconds, macroLabels, macroTexts) }) { Text("SAVE DEFAULTS") }
            } else {
            repeat(3) { index -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(macroLabels[index], { macroLabels[index] = it.uppercase().take(11) }, label = { Text("Macro ${index + 1} label") }, modifier = Modifier.weight(1f))
                OutlinedTextField(macroTexts[index], { macroTexts[index] = it.uppercase().take(24) }, label = { Text("CW text") }, modifier = Modifier.weight(3f))
            } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CQ repeat"); Slider(repeatSeconds.toFloat(), { repeatSeconds = it.toInt() }, valueRange = 2f..120f, modifier = Modifier.weight(1f)); Text("$repeatSeconds s")
                FilterChip(app.cwMacrosArmed, { app.updateCwMacrosArmed(!app.cwMacrosArmed) }, { Text(if (app.cwMacrosArmed) "CW ARMED" else "CW SAFE") })
            }
            Button({ app.saveLocalSettings(stationCall, stationName, stationGrid, repeatSeconds, macroLabels, macroTexts) }) { Text("SAVE MACROS") }
            }
        }
        if (section == SettingsSection.ALERTS) SettingsCard("FIELD / DISPLAY / ALERTS") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { FieldProfile.entries.forEach { value -> FilterChip(profile == value, { profile = value }, { Text(value.name) }) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Brightness", modifier = Modifier.width(90.dp)); Slider(brightness, { brightness = it }, valueRange = 10f..100f, modifier = Modifier.weight(1f)); Text("${brightness.toInt()}%") }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(autoDim, { autoDim = it }); Text("Auto-dim") }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(tones, { tones = it }); Text("Audible tones") }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(quiet, { quiet = it }); Text("Quiet non-critical") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("NONE", "POTA", "SOTA", "WWFF").forEach { value -> FilterChip(program == value, { program = value }, { Text(value) }) } }
            if (program != "NONE") OutlinedTextField(activation, { activation = it.uppercase() }, label = { Text("Activation reference") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ app.saveFieldSettings(profile, brightness.toInt(), autoDim, tones, quiet, program, activation) }) { Text("SAVE FIELD SETTINGS") }
                OutlinedButton({ systemMessage = "Non-critical alerts snoozed for 15 minutes" }) { Text("SNOOZE 15") }
                OutlinedButton({ systemMessage = "Non-critical alerts snoozed for 60 minutes" }) { Text("SNOOZE 60") }
            }
        }
        if (section == SettingsSection.SAFETY) SettingsCard("TRANSMIT SAFETY") {
            Text("ATU/TX and CW macro arms are session-only. They clear when the app, CAT, or USB session ends. CQ repeat stops on disconnect, disarm, or leaving CW.", color = Hold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(app.transmitArmed, { app.updateTransmitArmed(!app.transmitArmed) }, { Text(if (app.transmitArmed) "ATU / TX ARMED" else "ATU / TX SAFE") })
                FilterChip(app.cwMacrosArmed, { app.updateCwMacrosArmed(!app.cwMacrosArmed) }, { Text(if (app.cwMacrosArmed) "CW MACROS ARMED" else "CW MACROS SAFE") })
                Button({ direct("RX;"); app.updateTransmitArmed(false); app.updateCwMacrosArmed(false) }, enabled = state.connected,
                    colors = ButtonDefaults.buttonColors(containerColor = Healthy, contentColor = Chassis)) { Text("FORCE RX & DISARM") }
            }
        }
        if (section == SettingsSection.CLUSTER) SettingsCard("DX CLUSTER") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.weight(2f))
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, modifier = Modifier.weight(1f))
                OutlinedTextField(callsign, { callsign = it.uppercase() }, label = { Text("Callsign") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fallbackHost, { fallbackHost = it }, label = { Text("Fallback 1 host") }, modifier = Modifier.weight(2f))
                OutlinedTextField(fallbackPort, { fallbackPort = it.filter(Char::isDigit) }, label = { Text("Port") }, modifier = Modifier.weight(1f))
                OutlinedTextField(fallback2Host, { fallback2Host = it }, label = { Text("Fallback 2 host") }, modifier = Modifier.weight(2f))
                OutlinedTextField(fallback2Port, { fallback2Port = it.filter(Char::isDigit) }, label = { Text("Port") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(watch, { watch = it.uppercase() }, label = { Text("Watchlist · max 32 calls") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ features.setWatchlist(watch); features.connectCluster(host, port.toIntOrNull() ?: 7300, callsign,
                    fallbackHost, fallbackPort.toIntOrNull() ?: 7300, fallback2Host, fallback2Port.toIntOrNull() ?: 7300) }, enabled = callsign.isNotBlank()) { Text("Connect") }
                OutlinedButton(features::disconnectCluster) { Text("Disconnect") }; OutlinedButton(features::refreshSolar) { Text("Refresh NOAA") }
            }
            Text(features.clusterStatus, color = Muted)
        }
        if (section == SettingsSection.LOG) SettingsCard("LOCAL LOG & WAVELOG") {
            Text("LOG DESTINATION", color = Amber, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(wavelog.logMode == LogMode.LOCAL, { wavelog.updateLogMode(LogMode.LOCAL) }, { Text("LOCAL ADIF") })
                FilterChip(wavelog.logMode == LogMode.WAVELOG, { wavelog.updateLogMode(LogMode.WAVELOG) }, { Text("WAVELOG · TWO-WAY") })
            }
            Text(if (wavelog.logMode == LogMode.LOCAL) "QSOs stay in the tablet database and export as ADIF."
                else "Every QSO is saved on the tablet first, queued offline, then uploaded and remote changes are downloaded when connectivity returns.", color = Muted)
            if (wavelog.logMode == LogMode.LOCAL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ importAdif.launch(arrayOf("application/x-adif", "text/plain", "*/*")) }) { Text("IMPORT ADIF") }
                    OutlinedButton({ exportAdif.launch("rigweave-local.adi") }) { Text("EXPORT ADIF") }
                }
            } else {
                OutlinedTextField(wavelog.baseURL, wavelog::updateBaseURL, label = { Text("HTTPS base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(wavelog.apiKey, wavelog::updateApiKey, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (wavelog.stations.isEmpty()) OutlinedTextField(wavelog.stationId, wavelog::setStation, label = { Text("Station ID") })
                else ChoiceField("Station location", wavelog.selectedStation?.label.orEmpty(), wavelog.stations.map { it.id to it.label },
                    wavelog.stationId, wavelog::setStation)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ wavelog.loadStations() }) { Text("Load stations") }; Button({ wavelog.syncQueue() }) { Text("Sync queue") }; Button({ wavelog.fullSync() }) { Text("Full log") } }
                Text(wavelog.status, color = Muted)
            }
            Text("QRZ.COM / HAMQTH ENRICHMENT", color = Amber, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("QRZ", "HamQTH").forEach { value -> FilterChip(callbookProvider == value, { callbookProvider = value }, { Text(value) }) }
                OutlinedTextField(callbookUser, { callbookUser = it }, label = { Text("Username") }, modifier = Modifier.weight(1f))
                OutlinedTextField(callbookPassword, { callbookPassword = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
                Button({ callbook.configure(callbookProvider, callbookUser, callbookPassword) }) { Text("SAVE") }
            }
            Text("The logger Enrich action fills Name and QTH before SQLite save and Wavelog queueing.", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(cty::update) { Text("UPDATE CTY.DAT") }; Text(cty.status, color = Muted)
            }
        }
        if (section == SettingsSection.AUDIO) SettingsCard("USB RECEIVE AUDIO") { AudioCard(audio) }
        if (section == SettingsSection.HEALTH) SettingsCard("SYSTEM HEALTH") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthTile("UI", "RUNNING", true, Modifier.weight(1f)); HealthTile("CAT / USB", if (state.connected) "LIVE" else "OFFLINE", state.connected, Modifier.weight(1f))
                HealthTile("NETWORK / DX", features.clusterStatus, features.liveSpots.isNotEmpty(), Modifier.weight(1f)); HealthTile("WAVELOG", wavelog.status, wavelog.pendingCount == 0, Modifier.weight(1f))
            }
            Text("CAT · $detail", color = Muted); Text("AUDIO IN · ${audio.inputName}", color = Muted); Text("AUDIO OUT · ${audio.outputName}", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ systemMessage = app.backupNow() }) { Text("BACKUP NOW") }
                OutlinedButton({ systemMessage = app.verifyBackup() }) { Text("VERIFY BACKUP") }
                OutlinedButton({ systemMessage = app.backupNow(); if (systemMessage.startsWith("Backup")) exportRecovery.launch("rigweave-recovery.json") }) { Text("EXPORT RECOVERY") }
                OutlinedButton({ openRecovery.launch(arrayOf("application/json", "text/plain")) }) { Text("RESTORE REVIEW") }
                OutlinedButton(audio::refreshDevices) { Text("RESCAN AUDIO") }
            }
            Text(systemMessage, color = Muted)
        }
        if (section == SettingsSection.DIAG) SettingsCard("CAT DIAGNOSTICS") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(wavelog::testConnection) { Text("TEST WAVELOG") }
                OutlinedButton({ callbook.configure(callbookProvider, callbookUser, callbookPassword); callbook.test() }) { Text("TEST QRZ / HAMQTH") }
                OutlinedButton(wavelog::loadStations) { Text("LOAD STATIONS") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(wavelog.ntpServer, wavelog::updateNtpServer, label = { Text("NTP server") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(wavelog::checkTime, modifier = Modifier.heightIn(min = 48.dp)) { Text("SYNC NTP TIME") }
            }
            Text(wavelog.status, color = Muted); Text(callbook.status, color = Muted); Text(wavelog.timeStatus, color = Muted)
            val command = raw.trim().let { if (it.endsWith(';')) it else "$it;" }
            Row { OutlinedTextField(raw, { raw = it.uppercase() }, label = { Text("Safe CAT command") }, modifier = Modifier.weight(1f))
                Button({ direct(command); raw = "" }, enabled = state.connected && NativeCore.classify(command) in 1..2) { Text("Send") } }
        }
        if (section == SettingsSection.ABOUT) SettingsCard("ABOUT RIGWEAVE") {
            Text("Radio. Spectrum. Spots. Logs.", color = Amber)
            Text("Local-first tablet control and logging. Wavelog, callbook, CTY.DAT and DX-cluster integrations are optional.", color = Muted)
            Text("WSJT-X is intentionally not exposed in Settings yet.", color = Muted)
        }
    }
    restorePayload?.let { payload -> AlertDialog(onDismissRequest = { restorePayload = null }, title = { Text("RESTORE REVIEW") },
        text = { Text("${app.reviewRecovery(payload)}\n\nRestoring replaces saved station, preset, control-order, field, and connection preferences. Live radio state and QSOs are not changed.") },
        confirmButton = { Button({ systemMessage = app.restoreRecovery(payload); restorePayload = null }, colors = ButtonDefaults.buttonColors(containerColor = Danger)) { Text("RESTORE SETTINGS") } },
        dismissButton = { TextButton({ restorePayload = null }) { Text("CANCEL") } }) }
}

@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Amber, fontWeight = FontWeight.Bold); content()
    } }
}

@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    }
}

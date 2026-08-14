package app.rigweave.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RigWeaveTheme { RigWeaveApp() } }
    }
}

private val Amber = Color(0xFFF59C29)
private val Panel = Color(0xFF1A1E22)
private val Background = Color(0xFF101316)

@Composable private fun RigWeaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Amber, background = Background, surface = Panel), content = content)
}

enum class Destination(val label: String) { Home("Home"), Radio("Radio"), Log("Log"), Panadapter("Panadapter"), Settings("Settings") }

@Composable fun RigWeaveApp() {
    val context = LocalContext.current
    val coreHandle = remember { NativeCore.create() }
    val transport = remember { UsbRadioTransport(context) }
    val database = remember { QsoDatabase(context) }
    var state by remember { mutableStateOf(NativeCore.parseState(NativeCore.state(coreHandle))) }
    var usbDetail by remember { mutableStateOf("No USB serial adapter opened") }
    var destination by remember { mutableStateOf(Destination.Home) }
    val scope = rememberCoroutineScope()
    fun applyResult(result: UsbResult) {
        when (result) {
            is UsbResult.Connected -> { NativeCore.feed(coreHandle, result.frames); state = NativeCore.parseState(NativeCore.state(coreHandle)); usbDetail = result.detail }
            is UsbResult.PermissionRequired -> usbDetail = result.detail
            is UsbResult.Unavailable -> usbDetail = result.detail
        }
    }
    val connect: () -> Unit = { scope.launch { applyResult(transport.connect()) } }
    val send: (String) -> Unit = { command -> scope.launch { applyResult(transport.send(command)) } }
    DisposableEffect(Unit) { onDispose { scope.launch { transport.disconnect() }; NativeCore.destroy(coreHandle); database.close() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 700.dp
        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon(item), null) }, label = { Text(item.label) }) }
                }
                Screen(destination, state, usbDetail, database, connect, send)
            }
        } else {
            Scaffold(bottomBar = { NavigationBar { Destination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon(item), null) }, label = { Text(item.label) }) } } }) { padding ->
                Box(Modifier.padding(padding)) { Screen(destination, state, usbDetail, database, connect, send) }
            }
        }
    }
}

private fun icon(destination: Destination) = when (destination) {
    Destination.Home -> Icons.Outlined.Home; Destination.Radio -> Icons.Outlined.SettingsInputAntenna
    Destination.Log -> Icons.Outlined.List; Destination.Panadapter -> Icons.Outlined.ShowChart; Destination.Settings -> Icons.Outlined.Settings
}

@Composable private fun Screen(destination: Destination, state: RadioState, usbDetail: String, database: QsoDatabase, connect: () -> Unit, send: (String) -> Unit) {
    when (destination) {
        Destination.Home -> HomeScreen(state)
        Destination.Radio -> RadioScreen(state, usbDetail, connect, send)
        Destination.Log -> LogScreen(state, database)
        Destination.Panadapter -> OfflinePanadapter()
        Destination.Settings -> SettingsScreen(state, usbDetail)
    }
}

@Composable private fun Brand() { Column { Text("RIGWEAVE", color = Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Radio. Spectrum. Spots. Logs.", color = Color.Gray, fontSize = 12.sp) } }

@Composable private fun Status(state: RadioState) {
    AssistChip(onClick = {}, label = { Text(state.status) }, colors = AssistChipDefaults.assistChipColors(labelColor = if (state.connected) Color(0xFF51D98B) else Color(0xFFFF6868)))
}

@Composable private fun RadioCard(state: RadioState) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Status(state); Text(state.model) }
            Text(state.frequencyText, fontSize = 48.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text("${state.mode}  •  ${if (state.transmitting) "TX" else "RX"}  •  ${state.identity}", color = Color.Gray)
            LinearProgressIndicator(progress = { state.meter / 100f }, modifier = Modifier.fillMaxWidth(), color = Amber)
        }
    }
}

@Composable private fun HomeScreen(state: RadioState) = ScreenColumn { Brand(); RadioCard(state); Text("Waiting for a real KX3/KX2 connection. This build contains no demo or simulated radio state.", color = Color.Gray) }

@Composable private fun RadioScreen(state: RadioState, detail: String, connect: () -> Unit, send: (String) -> Unit) {
    var frequency by remember { mutableStateOf("") }
    var rawCat by remember { mutableStateOf("") }
    ScreenColumn {
        Brand(); RadioCard(state); Button(onClick = connect) { Text("Connect USB CAT") }; Text(detail, color = Color.Gray)
        Text("VFO A", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(frequency, { frequency = it }, label = { Text("Frequency MHz") }, modifier = Modifier.weight(1f))
            Button(onClick = { frequency.toDoubleOrNull()?.let { send("FA%011d;".format((it * 1_000_000).toLong())) } }) { Text("Set") }
        }
        Text("Mode", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1" to "LSB", "2" to "USB", "3" to "CW", "6" to "DATA").forEach { (code, label) ->
                OutlinedButton(onClick = { send("MD$code;") }) { Text(label) }
            }
        }
        Text("Raw KX3/KX2 CAT", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(rawCat, { rawCat = it.uppercase() }, label = { Text("CAT command") }, modifier = Modifier.weight(1f))
            Button(onClick = { if (rawCat.isNotBlank()) { send(rawCat); rawCat = "" } }) { Text("Send") }
        }
        Text("All CAT commands are passed directly to the connected radio.", color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable private fun LogScreen(state: RadioState, database: QsoDatabase) {
    var callsign by remember { mutableStateOf("") }; var frequency by remember { mutableStateOf("") }; var mode by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf("59") }; var received by remember { mutableStateOf("59") }; var message by remember { mutableStateOf("") }; var records by remember { mutableStateOf(database.list()) }
    val formatter = DateTimeFormatter.ISO_INSTANT
    ScreenColumn {
        Brand(); Text("New local QSO", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(callsign, { callsign = it.uppercase() }, label = { Text("Callsign") })
        OutlinedTextField(frequency, { frequency = it }, label = { Text("Frequency MHz") })
        OutlinedTextField(mode, { mode = it.uppercase() }, label = { Text("Mode") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(sent, { sent = it }, label = { Text("RST sent") }, modifier = Modifier.weight(1f)); OutlinedTextField(received, { received = it }, label = { Text("RST received") }, modifier = Modifier.weight(1f)) }
        Button(onClick = {
            val now = Instant.now(); val hz = if (state.connected) state.frequencyHz else ((frequency.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong(); val actualMode = if (state.connected) state.mode else mode
            if (callsign.isBlank() || hz <= 0 || actualMode.isBlank()) { message = "Callsign, frequency and mode are required" } else {
                val id = NativeCore.qsoIdentity(callsign, formatter.format(now), hz, actualMode)
                val saved = database.save(Qso(id, callsign, hz, actualMode, sent, received, now.epochSecond)); message = if (saved) "QSO saved locally" else "Immediate duplicate not saved"; records = database.list(); if (saved) callsign = ""
            }
        }) { Text("Save QSO") }
        Text(message, color = Color.Gray)
        Text("Recent QSOs", style = MaterialTheme.typography.titleLarge)
        records.take(20).forEach { qso -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(qso.callsign, fontWeight = FontWeight.Bold); Text("%.3f MHz • ${qso.mode}".format(qso.frequencyHz / 1_000_000.0), color = Color.Gray); Text(NativeCore.adif(qso.id, qso.callsign, Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd")), Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HHmmss")), qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived), color = Color.Gray, fontSize = 10.sp, maxLines = 2) } } }
    }
}

@Composable private fun OfflinePanadapter() = ScreenColumn { Brand(); Status(RadioState()); Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.SignalWifiOff, null, modifier = Modifier.size(48.dp)); Text("No physical audio source"); Text("No generated spectrum is shown.", color = Color.Gray) } } }

@Composable private fun SettingsScreen(state: RadioState, detail: String) = ScreenColumn { Brand(); Text("Radio: ${if (state.connected) state.model else "Awaiting ID response"}"); Text("USB: $detail"); Text("Shared core ${NativeCore.version()}"); Text("Automatic polling begins only after explicit Connect. Radio controls and raw CAT are unrestricted.", color = Color.Gray) }

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = { item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content) } })
}

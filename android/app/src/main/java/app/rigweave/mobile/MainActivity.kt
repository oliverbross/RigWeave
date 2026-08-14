package app.rigweave.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ShowChart
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

enum class Destination(val label: String) { Home("Home"), Radio("Radio"), Spots("Spots"), DX("DX"), Log("Log"), Panadapter("Panadapter"), Digital("Digital"), Settings("Settings") }

@Composable fun RigWeaveApp() {
    val context = LocalContext.current
    val coreHandle = remember { NativeCore.create() }
    val transport = remember { UsbRadioTransport(context) }
    val database = remember { QsoDatabase(context) }
    val features = remember { FeatureController(context) }
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
    DisposableEffect(Unit) { onDispose { scope.launch { transport.disconnect() }; features.close(); NativeCore.destroy(coreHandle); database.close() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 700.dp
        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Spacer(Modifier.height(12.dp))
                    Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon(item), null) }, label = { Text(item.label) }) }
                }
                Screen(destination, state, usbDetail, database, features, connect, send)
            }
        } else {
            Scaffold(bottomBar = { NavigationBar { Destination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(icon(item), null) }, label = { Text(item.label) }) } } }) { padding ->
                Box(Modifier.padding(padding)) { Screen(destination, state, usbDetail, database, features, connect, send) }
            }
        }
    }
}

private fun icon(destination: Destination) = when (destination) {
    Destination.Home -> Icons.Outlined.Home; Destination.Radio -> Icons.Outlined.SettingsInputAntenna
    Destination.Spots -> Icons.Outlined.CellTower; Destination.DX -> Icons.Outlined.Public
    Destination.Log -> Icons.AutoMirrored.Outlined.List; Destination.Panadapter -> Icons.AutoMirrored.Outlined.ShowChart
    Destination.Digital -> Icons.Outlined.GraphicEq; Destination.Settings -> Icons.Outlined.Settings
}

@Composable private fun Screen(destination: Destination, state: RadioState, usbDetail: String, database: QsoDatabase, features: FeatureController, connect: () -> Unit, send: (String) -> Unit) {
    when (destination) {
        Destination.Home -> HomeScreen(state)
        Destination.Radio -> RadioScreen(state, usbDetail, connect, send)
        Destination.Spots -> SpotsScreen(features, send)
        Destination.DX -> DXScreen(features)
        Destination.Log -> LogScreen(state, database)
        Destination.Panadapter -> PanadapterScreen(features, state)
        Destination.Digital -> DigitalScreen(features)
        Destination.Settings -> SettingsScreen(state, usbDetail, features)
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

@Composable private fun SpotsScreen(features: FeatureController, send: (String) -> Unit) = ScreenColumn {
    Brand(); Text(features.clusterStatus, color = Color.Gray); Text(features.dxSummary, style = MaterialTheme.typography.titleMedium)
    if (features.spots.isEmpty()) Text("No live spots. Connect a real DX cluster; no fixtures are loaded.", color = Color.Gray)
    features.spots.forEach { spot -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(spot.callsign, fontWeight = FontWeight.Bold); Text(spot.band) }
        Text("%.3f MHz · ${spot.mode} · ${spot.country}".format(spot.frequencyHz / 1_000_000.0), color = Color.Gray)
        if (spot.comment.isNotBlank()) Text(spot.comment, fontSize = 12.sp)
        Button(onClick = { send("FA%011d;".format(spot.frequencyHz)) }) { Text("Tune") }
    } } }
}

@Composable private fun DXScreen(features: FeatureController) = ScreenColumn {
    Brand(); Text(features.dxSummary, style = MaterialTheme.typography.titleLarge)
    Button(onClick = features::refreshSolar) { Text("Refresh NOAA space weather") }
    Text("DX ranking, CTY resolution, watchlists and surge analysis run in the shared Tab5 engine.", color = Color.Gray)
}

@Composable private fun PanadapterScreen(features: FeatureController, state: RadioState) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) features.startAudio() }
    ScreenColumn {
        Brand(); Text(features.audioStatus, color = Color(0xFF9AA8B5))
        if (features.spectrum.isEmpty()) Box(Modifier.fillMaxWidth().height(460.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.SignalWifiOff, null, modifier = Modifier.size(48.dp)); Text("No physical stereo I/Q source"); Text("Connect USB audio and start capture. No generated spectrum is shown.", color = Color(0xFF9AA8B5)) } }
        else {
            Canvas(Modifier.fillMaxWidth().height(460.dp)) {
                val bins = features.spectrum; val spectrumHeight = size.height * 0.41f
                val floor = features.noiseFloor + features.panFloorOffsetDb; val range = features.panRangeDb
                drawRect(Color(0xFF050912))
                val rows = features.waterfall
                if (rows.isNotEmpty()) {
                    val rowHeight = (size.height - spectrumHeight) / rows.size
                    val cellWidth = size.width / rows.first().size
                    rows.forEachIndexed { y, row -> row.forEachIndexed { x, db ->
                        val level = ((db - floor) / range).coerceIn(0f, 1f)
                        drawRect(panColor(level, features.panPalette),
                            topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, spectrumHeight + y * rowHeight),
                            size = androidx.compose.ui.geometry.Size(cellWidth + 1f, rowHeight + 1f))
                    } }
                }
                for (step in 0..8) {
                    val x = size.width * step / 8f
                    drawLine(Color.White.copy(alpha = if (step == 4) 0.22f else 0.07f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = if (step == 4) 1.5f else 0.7f)
                }
                for (step in 0..4) {
                    val y = spectrumHeight * step / 4f
                    drawLine(Color.White.copy(alpha = 0.09f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 0.7f)
                }
                if (bins.size > 1) for (index in 1 until bins.size) {
                    val x1 = size.width * (index - 1f) / (bins.size - 1f); val x2 = size.width * index / (bins.size - 1f)
                    val y1 = spectrumHeight * (1f - ((bins[index - 1] - floor) / range).coerceIn(0f, 1f)); val y2 = spectrumHeight * (1f - ((bins[index] - floor) / range).coerceIn(0f, 1f))
                    drawLine(Amber, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 2f)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−24 kHz", fontFamily = FontFamily.Monospace, color = Color(0xFF9AA8B5), fontSize = 11.sp)
                Text(if (state.frequencyHz > 0) "%.3f MHz".format(state.frequencyHz / 1_000_000.0) else "VFO", fontFamily = FontFamily.Monospace, color = Amber, fontSize = 12.sp)
                Text("+24 kHz", fontFamily = FontFamily.Monospace, color = Color(0xFF9AA8B5), fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("PEAK %.1f dBFS".format(features.spectrum.maxOrNull() ?: -140f), fontFamily = FontFamily.Monospace)
                Text("FLOOR %.1f dBFS".format(features.noiseFloor), fontFamily = FontFamily.Monospace)
            }
        }
        Text("Dynamic range ${features.panRangeDb.toInt()} dB", color = Color(0xFF9AA8B5)); Slider(value = features.panRangeDb, onValueChange = { features.panRangeDb = it }, valueRange = 40f..110f, steps = 34)
        Text("Black level ${features.panFloorOffsetDb.toInt()} dB from floor", color = Color(0xFF9AA8B5)); Slider(value = features.panFloorOffsetDb, onValueChange = { features.panFloorOffsetDb = it }, valueRange = -20f..10f, steps = 29)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Aether", "Ocean", "Fire").forEachIndexed { index, label -> FilterChip(selected = features.panPalette == index, onClick = { features.panPalette = index }, label = { Text(label) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { permission.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text("Start physical capture") }; Button(onClick = features::stopAudio) { Text("Stop") } }
    }
}

private fun panColor(level: Float, palette: Int): Color {
    val t = level.coerceIn(0f, 1f)
    val stops = when (palette) {
        1 -> listOf(Color(0xFF000412), Color(0xFF00336E), Color(0xFF00B8D2), Color(0xFFE0FFFF))
        2 -> listOf(Color(0xFF05030A), Color(0xFF480C5C), Color(0xFFD42D2D), Color(0xFFFFAE26), Color(0xFFFFFFDC))
        else -> listOf(Color(0xFF020612), Color(0xFF0E1C4A), Color(0xFF0084AE), Color(0xFF5DE2AA), Color(0xFFF7C948), Color(0xFFFF583E))
    }
    val scaled = t * (stops.size - 1); val index = scaled.toInt().coerceIn(0, stops.size - 2)
    return androidx.compose.ui.graphics.lerp(stops[index], stops[index + 1], scaled - index)
}

@Composable private fun DigitalScreen(features: FeatureController) {
    var port by remember { mutableStateOf("2237") }
    ScreenColumn { Brand(); OutlinedTextField(port, { port = it }, label = { Text("WSJT-X UDP port") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { port.toIntOrNull()?.let(features::startWSJTX) }) { Text("Listen") }; Button(onClick = features::stopWSJTX) { Text("Stop") } }; Text(features.wsjtxStatus, color = Color.Gray); Text(features.wsjtxMessage, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
}

@Composable private fun SettingsScreen(state: RadioState, detail: String, features: FeatureController) {
    var host by remember { mutableStateOf("dxc.ve7cc.net") }; var port by remember { mutableStateOf("23") }; var callsign by remember { mutableStateOf("") }; var watchlist by remember { mutableStateOf("") }
    ScreenColumn { Brand(); Text("Radio: ${if (state.connected) state.model else "Awaiting ID response"}"); Text("USB: $detail"); Text("Shared core ${NativeCore.version()}"); Text("DX cluster", style = MaterialTheme.typography.titleLarge); OutlinedTextField(callsign, { callsign = it.uppercase() }, label = { Text("Operator callsign") }); OutlinedTextField(host, { host = it }, label = { Text("Host") }); OutlinedTextField(port, { port = it }, label = { Text("Port") }); OutlinedTextField(watchlist, { watchlist = it.uppercase(); features.setWatchlist(it) }, label = { Text("Watchlist") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { port.toIntOrNull()?.let { features.connectCluster(host, it, callsign) } }) { Text("Connect") }; Button(onClick = features::disconnectCluster) { Text("Disconnect") } }; Text(features.clusterStatus, color = Color.Gray); Text("Automatic CAT polling begins only after explicit Connect. Radio controls and raw CAT are unrestricted.", color = Color.Gray) }
}

@Composable private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = { item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content) } })
}

package app.rigweave.mobile

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val FlexAmber = Color(0xFFE9A72B)
private val FlexGreen = Color(0xFF42C77B)
private val FlexMuted = Color(0xFFA5ADB2)

@Composable
fun FlexRadioScreen(controller: FlexRadioController, openLog: () -> Unit) {
    val scope = rememberCoroutineScope()
    var authUri by remember { mutableStateOf<Uri?>(null) }
    var frequency by remember(controller.selectedSliceIndex, controller.snapshot) {
        mutableStateOf(controller.snapshot.selected(controller.selectedSliceIndex)?.frequencyHz?.toString().orEmpty())
    }
    authUri?.let { uri -> SmartLinkAuthDialog(uri, { controller.completeSmartLinkSignIn(it); authUri = null }, { authUri = null }) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("FLEXRADIO", color = FlexAmber, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("LAN / SMARTLINK · RECEIVE-ONLY", color = FlexMuted, fontWeight = FontWeight.Bold) }
                Surface(color = if (controller.connectionState == FlexConnectionState.CONNECTED) FlexGreen.copy(alpha = .18f) else FlexAmber.copy(alpha = .14f), shape = MaterialTheme.shapes.small) {
                    Text(controller.connectionState.label, color = if (controller.connectionState == FlexConnectionState.CONNECTED) FlexGreen else FlexAmber,
                        fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            if (wide) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FlexRadios(controller, Modifier.weight(1f)); FlexStationSlice(controller, frequency, { frequency = it }, openLog, Modifier.weight(1.25f))
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { FlexRadios(controller, Modifier.fillMaxWidth()) }
                item { FlexStationSlice(controller, frequency, { frequency = it }, openLog, Modifier.fillMaxWidth()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ controller.discoverLan() }) { Text("SEARCH LAN") }
                OutlinedButton({ authUri = controller.beginSmartLinkSignIn() }, enabled = controller.smartLinkConfigured) { Text("SIGN IN") }
                OutlinedButton({ scope.launch { controller.disconnect() } }) { Text("DISCONNECT") }
                OutlinedButton({
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    controller.logout()
                }) { Text("LOGOUT") }
            }
            if (!controller.smartLinkConfigured) Text("OWNER CONFIGURATION VALUES REQUIRED · populate FLEX_SMARTLINK_CLIENT_ID in the ignored flex-developer.properties file or environment. Official Auth0 redirect and SmartLink server defaults are built in.", color = FlexAmber)
            Text(controller.detail, color = FlexMuted)
            Text("RigWeave is an independent GPLv3 application. Flex protocol core includes GPLv3 code derived from Nexus by KD9TAW.", color = FlexMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SmartLinkAuthDialog(authorizationUri: Uri, onRedirect: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val allowedHost = authorizationUri.host
    val webView = remember(authorizationUri) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                private fun inspect(uri: Uri): Boolean {
                    if (uri.scheme != "https" || uri.host != allowedHost) return true
                    if (!uri.fragment.isNullOrBlank() && uri.fragment.orEmpty().contains("state=")) {
                        onRedirect(uri)
                        return true
                    }
                    return false
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) =
                    request?.url?.let(::inspect) ?: true
                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let(Uri::parse)?.takeIf { !it.fragment.isNullOrBlank() }?.let(::inspect)
                }
            }
            loadUrl(authorizationUri.toString())
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.destroy()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(.92f).fillMaxHeight(.9f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SMARTLINK SIGN IN", color = FlexAmber, fontWeight = FontWeight.Black)
                    OutlinedButton(onDismiss) { Text("CANCEL") }
                }
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable private fun FlexRadios(controller: FlexRadioController, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RADIOS", color = FlexAmber, fontWeight = FontWeight.Black)
        if (controller.radios.isEmpty()) Text(if (controller.connectionState == FlexConnectionState.SEARCHING) "SEARCHING LAN" else "NO LAN RADIOS", color = FlexMuted)
        controller.radios.forEach { radio -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF283139))) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(radio.nickname.ifBlank { radio.model }, fontWeight = FontWeight.Bold); Text("${radio.model} · ${radio.callsign.ifBlank { radio.serial }}", color = FlexMuted); Text("${radio.ip}:${radio.port} · ${radio.status}", color = FlexMuted, fontFamily = FontFamily.Monospace) }
                Button({ scope.launch { controller.connectLan(radio) } }) { Text("CONNECT") }
            }
        } }
        if (controller.smartLinkRadios.isNotEmpty()) {
            HorizontalDivider(); Text("SMARTLINK RADIOS", color = FlexAmber, fontWeight = FontWeight.Black)
            controller.smartLinkRadios.forEach { radio -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(radio.nickname.ifBlank { radio.model }, fontWeight = FontWeight.Bold); Text("${radio.model} · ${radio.callsign} · ${radio.status}", color = FlexMuted) }
                Button({ scope.launch { controller.connectSmartLink(radio) } }) { Text("CONNECT WAN") }
            } }
        }
        HorizontalDivider()
        Text("DIAGNOSTICS", color = FlexAmber, fontWeight = FontWeight.Bold)
        Text("V ${controller.snapshot.version.ifBlank { "—" }} · H ${controller.snapshot.handle.takeIf { it != 0L }?.toString(16)?.uppercase() ?: "—"}", color = FlexMuted, fontFamily = FontFamily.Monospace)
    }
    }
}

@Composable private fun FlexStationSlice(controller: FlexRadioController, frequency: String, setFrequency: (String) -> Unit, openLog: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope(); val selected = controller.snapshot.selected(controller.selectedSliceIndex)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228))) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("STATIONS AND EXISTING SLICES", color = FlexAmber, fontWeight = FontWeight.Black)
        val stations = controller.snapshot.clients.filter { it.connected && it.gui && it.station.isNotBlank() }.map { it.station }.distinct()
        if (stations.isEmpty()) Text("NO GUI STATION", color = FlexMuted) else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { stations.forEach { station -> FilterChip(controller.snapshot.clients.any { it.station == station && controller.snapshot.selected(controller.selectedSliceIndex)?.clientHandle == it.handle }, { controller.selectStation(station) }, { Text(station) }) } }
        val slices = controller.snapshot.slices.filter { it.inUse && !it.tx }
        if (slices.isEmpty()) Text("NO EXISTING FLEX SLICE\nStart SmartSDR/Maestro or another authorised GUI client.", color = FlexMuted)
        else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { slices.forEach { slice -> FilterChip(controller.selectedSliceIndex == slice.index, { controller.selectSlice(slice.index) }, { Text("${slice.letter} · ${slice.mode}") }) } }
        Text(selected?.frequencyHz?.let(::formatRadioFrequency)?.plus(" MHz") ?: "—.——— MHz", color = FlexAmber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 38.sp)
        Text("${selected?.mode ?: "—"} · FILTER ${selected?.filterWidthHz ?: 0} Hz · RX ANT ${selected?.rxAntenna?.ifBlank { "—" } ?: "—"} · observed TX ${if (selected?.tx == true) "YES" else "NO"}", color = FlexMuted)
        OutlinedTextField(frequency, { setFrequency(it.filter(Char::isDigit).take(11)) }, label = { Text("Frequency Hz") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ frequency.toLongOrNull()?.let { scope.launch { controller.tune(ReceiveTuneRequest(it)) } } }, enabled = selected != null && frequency.toLongOrNull() != null) { Text("SET RECEIVE FREQUENCY") }
            listOf(-1000L, -100L, 100L, 1000L).forEach { step -> OutlinedButton({ selected?.let { scope.launch { controller.tune(ReceiveTuneRequest(it.frequencyHz + step)) } } }, enabled = selected != null) { Text(if (step > 0) "+$step" else "$step") } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("LSB", "USB", "CW", "DIGU", "DIGL", "AM", "FM").forEach { mode -> FilterChip(selected?.mode == mode, { selected?.let { scope.launch { controller.tune(ReceiveTuneRequest(it.frequencyHz, mode)) } } }, { Text(mode) }) } }
        Button(openLog, enabled = selected != null, modifier = Modifier.heightIn(min = 48.dp)) { Text("OPEN LOG") }
        Text("No TX, PTT, MOX, TUNE, slice creation, DAX, stream, antenna, power or profile commands are available.", color = FlexGreen, fontWeight = FontWeight.Bold)
    } }
}

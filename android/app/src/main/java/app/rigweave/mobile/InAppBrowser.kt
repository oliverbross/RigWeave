package app.rigweave.mobile

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.URI

internal fun validatedInAppBrowserUrl(candidate: String): String? = runCatching {
    URI(candidate.trim()).takeIf { uri ->
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }?.toString()
}.getOrNull()

@Stable
internal class InAppBrowserState {
    var url by mutableStateOf<String?>(null)
        private set
    fun open(candidate: String) { url = validatedInAppBrowserUrl(candidate) }
    fun close() { url = null }
}

internal val LocalInAppBrowserState = staticCompositionLocalOf<InAppBrowserState?> { null }

@Composable
internal fun rememberInAppBrowserState(): InAppBrowserState = remember { InAppBrowserState() }

@Composable
internal fun InAppBrowserDialog(state: InAppBrowserState) {
    val url = state.url ?: return
    val context = LocalContext.current
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    Dialog(onDismissRequest = state::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f).fillMaxHeight(.90f), shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ webView?.goBack() }, enabled = webView?.canGoBack() == true) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    IconButton({ webView?.goForward() }, enabled = webView?.canGoForward() == true) { Icon(Icons.Outlined.ArrowForward, "Forward") }
                    IconButton({ webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload") }
                    Text(Uri.parse(url).host.orEmpty(), modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url))) } }) {
                        Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(5.dp)); Text("EXTERNAL")
                    }
                    IconButton(state::close) { Icon(Icons.Outlined.Close, "Close") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(factory = { browserContext ->
                    WebView(browserContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                return !target.startsWith("https://", ignoreCase = true)
                            }
                            override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        }
                        webView = this
                        loadUrl(url)
                    }
                }, update = { webView = it }, modifier = Modifier.fillMaxSize())
            }
        }
    }
    DisposableEffect(url) { onDispose { webView?.stopLoading(); webView?.destroy() } }
}

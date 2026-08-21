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
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }?.toString()
}.getOrNull()

internal fun validatedExternalBrowserUrl(candidate: String): String? = runCatching {
    val uri = URI(candidate.trim())
    when (uri.scheme?.lowercase()) {
        "https", "http" -> uri.takeIf { !it.host.isNullOrBlank() && it.rawUserInfo == null }
        "mailto", "tel" -> uri.takeIf { it.rawSchemeSpecificPart.isNotBlank() }
        else -> null
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
    var currentUrl by remember(url) { mutableStateOf(url) }
    var pageTitle by remember(url) { mutableStateOf("") }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var canGoForward by remember(url) { mutableStateOf(false) }
    var pendingExternalUrl by remember(url) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = state::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f).fillMaxHeight(.90f), shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ webView?.goBack() }, enabled = canGoBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    IconButton({ webView?.goForward() }, enabled = canGoForward) { Icon(Icons.Outlined.ArrowForward, "Forward") }
                    IconButton({ webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload") }
                    Column(Modifier.weight(1f)) {
                        Text(pageTitle.ifBlank { "Secure browser" }, maxLines = 1)
                        Text(Uri.parse(currentUrl).host.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    TextButton({
                        validatedInAppBrowserUrl(currentUrl)?.let {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }
                    }) {
                        Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(5.dp)); Text("EXTERNAL")
                    }
                    IconButton(state::close) { Icon(Icons.Outlined.Close, "Close") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(factory = { browserContext ->
                    WebView(browserContext).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                if (validatedInAppBrowserUrl(target) != null) return false
                                pendingExternalUrl = validatedExternalBrowserUrl(target)
                                return true
                            }
                            override fun onPageStarted(view: WebView?, target: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                                validatedInAppBrowserUrl(target.orEmpty())?.let { currentUrl = it }
                            }
                            override fun onPageFinished(view: WebView?, target: String?) {
                                loading = false
                                validatedInAppBrowserUrl(target.orEmpty())?.let { currentUrl = it }
                                pageTitle = view?.title.orEmpty()
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                        }
                        setDownloadListener { target, _, _, _, _ ->
                            pendingExternalUrl = validatedExternalBrowserUrl(target)
                        }
                        webView = this
                        loadUrl(url)
                    }
                }, update = { webView = it }, modifier = Modifier.fillMaxSize())
            }
        }
    }
    pendingExternalUrl?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("Open outside RigWeave?") },
            text = { Text(target) },
            confirmButton = {
                Button({
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                    pendingExternalUrl = null
                }) { Text("OPEN EXTERNALLY") }
            },
            dismissButton = { TextButton({ pendingExternalUrl = null }) { Text("CANCEL") } },
        )
    }
    DisposableEffect(url) {
        onDispose {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.removeAllViews()
            webView?.destroy()
        }
    }
}

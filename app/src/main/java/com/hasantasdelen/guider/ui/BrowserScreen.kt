package com.hasantasdelen.guider.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hasantasdelen.guider.data.GuideType
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.util.L
import com.hasantasdelen.guider.web.AdBlock
import com.hasantasdelen.guider.web.DownloadProgress
import com.hasantasdelen.guider.web.GuideDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.URLEncoder

fun gameFaqsSearch(q: String) = "https://gamefaqs.gamespot.com/search?game=" + URLEncoder.encode(q, "UTF-8")
fun neoseekerSearch(q: String) = "https://www.neoseeker.com/search/?q=" + URLEncoder.encode(q, "UTF-8")
fun webSearch(q: String) = "https://duckduckgo.com/?q=" + URLEncoder.encode(q, "UTF-8")

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(gameId: String, initialUrl: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val game = Library.game(gameId)
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var url by remember { mutableStateOf(initialUrl ?: gameFaqsSearch(game?.title ?: "")) }
    var address by remember { mutableStateOf(url) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dl by remember { mutableStateOf<DownloadProgress?>(null) }
    var dlJob by remember { mutableStateOf<Job?>(null) }
    var keepOn by remember { mutableStateOf(true) }
    var shortcutDialog by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(dl != null && keepOn) {
        view.keepScreenOn = dl != null && keepOn
        onDispose { view.keepScreenOn = false }
    }
    val detected = GuideDownloader.detect(url)

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    fun startDownload(whole: Boolean) {
        val wv = webView ?: return
        val pageUrl = wv.url ?: return
        val ua = wv.settings.userAgentString
        dl = DownloadProgress(0, 1, 0, 0)
        wv.evaluateJavascript("document.documentElement.outerHTML") { res ->
            val html = try { org.json.JSONTokener(res).nextValue() as? String } catch (e: Exception) { null }
            if (html == null) { dl = null; toast(L("Could not read page", "Sayfa okunamadı")); return@evaluateJavascript }
            dlJob = scope.launch {
                try {
                    val g = GuideDownloader.download(html, pageUrl, ua, whole) { p -> scope.launch(Dispatchers.Main) { dl = p } }
                    withContext(Dispatchers.IO) { Library.saveContentGuide(gameId, g.title, g.type, g.content, sourceUrl = pageUrl) }
                    toast(L("Guide saved: ", "Rehber kaydedildi: ") + g.title)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    toast(L("Download cancelled", "İndirme iptal edildi"))
                } catch (e: Exception) {
                    toast(L("Download failed: ", "İndirme başarısız: ") + (e.message ?: ""))
                } finally {
                    dl = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(L("Find Guide", "Rehber Bul")) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    actions = {
                        IconButton(onClick = { webView?.goBack() }, enabled = canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("Back", "Geri")) }
                        IconButton(onClick = { webView?.goForward() }, enabled = canFwd) { Icon(Icons.AutoMirrored.Filled.ArrowForward, L("Forward", "İleri")) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                val q = game?.title ?: ""
                                DropdownMenuItem(text = { Text("GameFAQs") }, onClick = { menu = false; webView?.loadUrl(gameFaqsSearch(q)) })
                                DropdownMenuItem(text = { Text("Neoseeker") }, onClick = { menu = false; webView?.loadUrl(neoseekerSearch(q)) })
                                DropdownMenuItem(text = { Text(L("Web search (manuals, maps)", "Web araması (kılavuz, harita)")) }, onClick = { menu = false; webView?.loadUrl(webSearch("$q guide")) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(L("Save this page", "Bu sayfayı kaydet")) }, onClick = { menu = false; startDownload(true) })
                                DropdownMenuItem(text = { Text(L("Add as shortcut URL", "Kısayol URL olarak ekle")) }, onClick = { menu = false; shortcutDialog = true })
                                DropdownMenuItem(text = { Text(L("Reload", "Yenile")) }, onClick = { menu = false; webView?.reload() })
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    leadingIcon = { Icon(Icons.Filled.Language, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val a = address.trim()
                        val target = if (a.contains(' ') || !a.contains('.')) webSearch(a) else if (a.startsWith("http")) a else "https://$a"
                        webView?.loadUrl(target)
                    })
                )
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detected != null) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2EBD59))
                        Spacer(Modifier.width(8.dp))
                        Text(L("Guide page detected", "Rehber sayfası algılandı"), color = Color(0xFF2EBD59), modifier = Modifier.weight(1f))
                        Button(onClick = { startDownload(false) }) {
                            Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text(L("Download Guide", "Rehberi İndir"))
                        }
                    } else {
                        Text(
                            L("Open a guide on GameFAQs or Neoseeker, or save any page.", "GameFAQs/Neoseeker'da bir rehber aç ya da herhangi bir sayfayı kaydet."),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { startDownload(true) }) { Text(L("Save page", "Sayfayı kaydet")) }
                    }
                }
            }
        }
    ) { pad ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(pad),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            if (Prefs.adBlock && AdBlock.isAd(request?.url?.host)) {
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return null
                        }

                        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                            u?.let { url = it; address = it }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                            u?.let { url = it; address = it }
                            canBack = canGoBack(); canFwd = canGoForward()
                        }

                        override fun onPageFinished(view: WebView?, u: String?) {
                            canBack = canGoBack(); canFwd = canGoForward()
                        }
                    }
                    setDownloadListener { dUrl, ua, contentDisposition, mime, _ ->
                        val name = URLUtil.guessFileName(dUrl, contentDisposition, mime)
                        val type = Library.typeForName(name)
                        if (type == null || type == GuideType.CBZ) {
                            toast(L("Unsupported file: ", "Desteklenmeyen dosya: ") + name); return@setDownloadListener
                        }
                        toast(L("Downloading ", "İndiriliyor ") + name)
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) { GuideDownloader.downloadBytes(dUrl, ua) }
                                val ext = name.substringAfterLast('.', "bin").lowercase()
                                withContext(Dispatchers.IO) { Library.saveBytesGuide(gameId, name.substringBeforeLast('.'), type, ext, bytes, dUrl) }
                                toast(L("Saved: ", "Kaydedildi: ") + name)
                            } catch (e: Exception) {
                                toast(L("Download failed: ", "İndirme başarısız: ") + (e.message ?: ""))
                            }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { it.destroy() }
        )
    }

    dl?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { Button(onClick = { dlJob?.cancel(); dl = null }) { Text(L("Cancel", "İptal")) } },
            title = { Text(L("Downloading Guide", "Rehber İndiriliyor")) },
            text = {
                Column {
                    Text(L("Downloading page ${p.pageDone} of ${p.pageTotal}", "Sayfa indiriliyor: ${p.pageDone} / ${p.pageTotal}"))
                    LinearProgressIndicator(progress = { if (p.pageTotal == 0) 0f else p.pageDone.toFloat() / p.pageTotal }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (p.imageTotal > 0) {
                        Text(L("Downloading image ${p.imageDone} of ${p.imageTotal}", "Resim indiriliyor: ${p.imageDone} / ${p.imageTotal}"))
                        LinearProgressIndicator(progress = { p.imageDone.toFloat() / p.imageTotal }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepOn, onCheckedChange = { keepOn = it })
                        Text(L("Keep screen on", "Ekranı açık tut"))
                    }
                }
            }
        )
    }

    if (shortcutDialog) {
        ShortcutDialog(initialUrl = url, initialName = webView?.title ?: "", onDismiss = { shortcutDialog = false }) { n, u ->
            Library.addShortcut(gameId, n, u); shortcutDialog = false
            toast(L("Shortcut added", "Kısayol eklendi"))
        }
    }
}

@Composable
fun ShortcutDialog(initialUrl: String, initialName: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var u by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Add a shortcut URL", "Kısayol URL ekle")) },
        text = {
            Column {
                Text(L("This will be a shortcut to the website of your choosing.", "Seçtiğin web sitesine bir kısayol eklenecek."))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text(L("Name", "Ad")) }, singleLine = true)
                OutlinedTextField(u, { u = it }, label = { Text("URL") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && u.isNotBlank(), onClick = {
                onAdd(name.trim(), if (u.startsWith("http")) u.trim() else "https://" + u.trim())
            }) { Text(L("Add Shortcut", "Kısayol Ekle")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel", "İptal")) } }
    )
}

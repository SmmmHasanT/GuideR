package com.hasantasdelen.guider.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hasantasdelen.guider.data.Guide
import com.hasantasdelen.guider.data.GuideType
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.data.ReaderState
import com.hasantasdelen.guider.tts.Speaker
import com.hasantasdelen.guider.tts.TtsBar
import com.hasantasdelen.guider.tts.detectLanguage
import com.hasantasdelen.guider.tts.rememberSpeaker
import com.hasantasdelen.guider.ui.LocalAppTheme
import com.hasantasdelen.guider.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener

private fun decodeJsResult(result: String?): String? {
    if (result == null || result == "null") return null
    return try {
        JSONTokener(result).nextValue() as? String
    } catch (e: Exception) {
        null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlReaderScreen(guide: Guide, onBack: () -> Unit, onPixie: (() -> Unit)?, onImage: (String) -> Unit) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var rs by remember { mutableStateOf(Library.readerState(guide.id)) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var canFwd by remember { mutableStateOf(false) }
    var loaded by remember { mutableIntStateOf(0) }

    val speaker = rememberSpeaker()
    var ttsVisible by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(guide.language ?: "en") }

    var toc by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var curHead by remember { mutableIntStateOf(-1) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var findCount by remember { mutableIntStateOf(0) }
    var findIdx by remember { mutableIntStateOf(0) }

    KeepScreenOn(Prefs.keepScreenOn || speaker.playing)

    val isUrl = guide.type == GuideType.URL
    val startUrl = remember(guide.id) {
        if (isUrl) guide.path else Library.guideFile(guide).toURI().toString()
    }

    fun js(code: String, cb: ((String?) -> Unit)? = null) {
        webView?.evaluateJavascript(code) { cb?.invoke(it) }
    }

    fun inject() {
        js(HtmlJs.script(HtmlJs.css(theme, Prefs.themeHtml && !isUrl)))
    }

    fun loadSegments(then: () -> Unit) {
        js("GuideR.collect()") { res ->
            val json = decodeJsResult(res) ?: return@js
            scope.launch {
                val segs = withContext(Dispatchers.Default) {
                    val arr = JSONArray(json)
                    val texts = (0 until arr.length()).map { arr.getString(it) }
                    if (guide.language == null) {
                        lang = detectLanguage(texts.joinToString(" ").take(5000))
                    }
                    texts.flatMapIndexed { i, t -> Speaker.segmentsFor(i, t, lang) }
                }
                speaker.setLanguage(lang)
                speaker.setSegments(segs)
                then()
            }
        }
    }

    fun startFromVisible() {
        ttsVisible = true
        js("GuideR.tap=true")
        loadSegments {
            js("GuideR.firstVisible()") { r -> speaker.playFromBlock(r?.toIntOrNull() ?: 0) }
        }
    }

    fun startAtBlock(b: Int) {
        ttsVisible = true
        js("GuideR.tap=true")
        if (speaker.segments.isEmpty()) loadSegments { speaker.playFromBlock(b) } else speaker.playFromBlock(b)
    }

    LaunchedEffect(speaker.current, ttsVisible) {
        val seg = speaker.segments.getOrNull(speaker.current)
        if (ttsVisible && seg != null) js("GuideR.highlight(${seg.block},${Prefs.ttsAutoScroll})")
        if (!ttsVisible) js("if(window.GuideR){GuideR.clear();GuideR.tap=false;}")
    }
    LaunchedEffect(Prefs.theme, Prefs.themeHtml, loaded) { if (loaded > 0) inject() }
    LaunchedEffect(rs.fontSize, webView) { webView?.settings?.textZoom = rs.fontSize * 100 / 16 }
    LaunchedEffect(query, searching) {
        if (searching && query.length >= 2) webView?.findAllAsync(query) else webView?.clearMatches()
    }

    val main = remember { Handler(Looper.getMainLooper()) }
    val openImage by rememberUpdatedState(onImage)
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onBlockLongPress(i: Int) { main.post { startAtBlock(i) } }

            @JavascriptInterface
            fun onBlockTap(i: Int) { main.post { if (ttsVisible) speaker.playFromBlock(i) } }

            @JavascriptInterface
            fun onImage(src: String) { main.post { openImage(src) } }

            @JavascriptInterface
            fun onScroll(pos: Double, head: Int) {
                main.post {
                    curHead = head
                    if (!isUrl && loaded > 0) {
                        rs = rs.copy(position = (pos * 1_000_000).toInt())
                        Library.saveReaderState(guide.id, rs)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                ReaderTopBar(guide.title, onBack) {
                    if (canBack) IconButton(onClick = { webView?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    if (canFwd) IconButton(onClick = { webView?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, L("Search", "Ara")) }
                    IconButton(onClick = {
                        js("GuideR.toc()") { r ->
                            val json = decodeJsResult(r) ?: "[]"
                            val arr = JSONArray(json)
                            toc = (0 until arr.length()).map {
                                val o = arr.getJSONObject(it)
                                TocEntry(o.getString("t"), it, o.optInt("l", 0).coerceIn(0, 3))
                            }
                            showToc = true
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.List, L("Contents", "İçindekiler")) }
                    IconButton(onClick = {
                        if (ttsVisible) { speaker.stop(); ttsVisible = false } else startFromVisible()
                    }) { Icon(if (ttsVisible) Icons.Filled.HeadsetOff else Icons.Filled.Headphones, L("Read aloud", "Sesli oku")) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.TextFields, null) }
                    if (onPixie != null) IconButton(onClick = onPixie) { Icon(Icons.Filled.AutoAwesome, "Pixie AI") }
                }
                if (searching) {
                    SearchRow(query, { query = it }, findCount, findIdx,
                        onPrev = { webView?.findNext(false) }, onNext = { webView?.findNext(true) },
                        onClose = { searching = false; query = "" })
                }
            }
        },
        bottomBar = {
            Column {
                if (Prefs.showTocBar && toc.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val t = (curHead - 1).coerceAtLeast(0); js("GuideR.heads[$t]&&GuideR.heads[$t].scrollIntoView({block:'start'})")
                            }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                            Text(toc.getOrNull(curHead)?.title ?: "—", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                val t = (curHead + 1).coerceAtMost(toc.size - 1); js("GuideR.heads[$t]&&GuideR.heads[$t].scrollIntoView({block:'start'})")
                            }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        }
                    }
                }
                if (ttsVisible) TtsBar(speaker, showCaption = false, onClose = { ttsVisible = false }, onStartFromVisible = { startFromVisible() })
            }
        }
    ) { pad ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(pad),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.textZoom = rs.fontSize * 100 / 16
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(bridge, "GuideRAndroid")
                    webChromeClient = WebChromeClient()
                    var restored = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            inject()
                            canBack = canGoBack(); canFwd = canGoForward()
                            loaded++
                            js("GuideR.toc()") { r ->
                                val json = decodeJsResult(r) ?: return@js
                                val arr = JSONArray(json)
                                toc = (0 until arr.length()).map {
                                    val o = arr.getJSONObject(it)
                                    TocEntry(o.getString("t"), it, o.optInt("l", 0).coerceIn(0, 3))
                                }
                            }
                            if (!restored && !isUrl && rs.position > 0) {
                                restored = true
                                val p = rs.position / 1_000_000.0
                                scope.launch {
                                    for (d in listOf(150L, 600L, 1500L)) { delay(d); js("GuideR.setPos($p)") }
                                }
                            }
                            if (ttsVisible) { speaker.setSegments(emptyList()) }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            canBack = canGoBack(); canFwd = canGoForward()
                        }
                    }
                    setFindListener { active, count, _ -> findIdx = active; findCount = count }
                    setOnKeyListener { _, keyCode, event ->
                        if (!Prefs.controllerEnabled || event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> { js("GuideR.scrollBy(${Prefs.scrollStep})"); true }
                            KeyEvent.KEYCODE_DPAD_UP -> { js("GuideR.scrollBy(-${Prefs.scrollStep})"); true }
                            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> { js("GuideR.page(1)"); true }
                            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> { js("GuideR.page(-1)"); true }
                            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (!ttsVisible) startFromVisible() else speaker.toggle(); true
                            }
                            KeyEvent.KEYCODE_BUTTON_R2 -> { speaker.next(); true }
                            KeyEvent.KEYCODE_BUTTON_L2 -> { speaker.prev(); true }
                            else -> false
                        }
                    }
                    loadUrl(startUrl)
                    requestFocus()
                    webView = this
                }
            },
            onRelease = { it.destroy() }
        )
    }

    if (showToc) {
        TocDialog(toc, curHead.coerceAtLeast(0), onPick = { e ->
            showToc = false
            js("GuideR.heads[${e.block}]&&GuideR.heads[${e.block}].scrollIntoView({block:'start'})")
        }, onDismiss = { showToc = false })
    }
    if (showSettings) {
        ReadingSettingsSheet(rs, isTxt = false, onChange = { rs = it; Library.saveReaderState(guide.id, it) }, onDismiss = { showSettings = false })
    }
}

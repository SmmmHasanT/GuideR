package com.hasantasdelen.guider.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hasantasdelen.guider.data.Guide
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Match(val block: Int, val start: Int, val end: Int)

@Composable
fun TxtReaderScreen(guide: Guide, onBack: () -> Unit, onPixie: (() -> Unit)?) {
    var raw by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(guide.id) {
        try {
            raw = withContext(Dispatchers.IO) {
                val bytes = Library.guideFile(guide).readBytes()
                decodeText(bytes)
            }
        } catch (e: Exception) {
            loadError = e.message ?: "error"
        }
    }
    val text = raw
    if (text == null) {
        Scaffold(topBar = { ReaderTopBar(guide.title, onBack) {} }) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                if (loadError != null) Text(loadError!!) else CircularProgressIndicator()
            }
        }
    } else {
        TxtReaderContent(guide, text, onBack, onPixie)
    }
}

/** Old guides are often Latin-1 / CP437; try UTF-8 first and fall back. */
fun decodeText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE)
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE)
    val dec = Charsets.UTF_8.newDecoder()
    return try {
        dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        String(bytes, charset("windows-1252"))
    }
}

@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun TxtReaderContent(guide: Guide, raw: String, onBack: () -> Unit, onPixie: (() -> Unit)?) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    var rs by remember { mutableStateOf(Library.readerState(guide.id)) }
    fun updateRs(n: ReaderState) { rs = n; Library.saveReaderState(guide.id, n) }

    var blocks by remember { mutableStateOf<List<TxtBlock>>(emptyList()) }
    var toc by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var parsed by remember { mutableStateOf(false) }
    LaunchedEffect(rs.smartFormat) {
        val (b, t) = withContext(Dispatchers.Default) {
            val b = TxtDocument.parse(raw, rs.smartFormat)
            b to TxtDocument.detectToc(raw, b)
        }
        blocks = b; toc = t; parsed = true
    }

    val speaker = rememberSpeaker()
    var ttsVisible by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(guide.language ?: "en") }
    LaunchedEffect(raw) {
        if (guide.language == null) lang = detectLanguage(raw.take(6000))
        speaker.setLanguage(lang)
    }
    // (Re)build speech segments whenever the block list changes
    LaunchedEffect(blocks, lang, ttsVisible) {
        if (ttsVisible && blocks.isNotEmpty()) {
            val segs = withContext(Dispatchers.Default) {
                blocks.flatMapIndexed { i, b -> Speaker.segmentsFor(i, b.text, lang) }
            }
            if (segs != speaker.segments) speaker.setSegments(segs)
        }
    }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var matchIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, blocks) {
        if (query.length < 2) { matches = emptyList(); return@LaunchedEffect }
        matches = withContext(Dispatchers.Default) {
            val out = ArrayList<Match>()
            for ((i, b) in blocks.withIndex()) {
                var p = b.text.indexOf(query, 0, ignoreCase = true)
                while (p >= 0 && out.size < 5000) {
                    out.add(Match(i, p, p + query.length)); p = b.text.indexOf(query, p + 1, ignoreCase = true)
                }
            }
            out
        }
        matchIdx = 0
    }

    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    KeepScreenOn(Prefs.keepScreenOn || speaker.playing)

    if (!parsed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val listState = remember(rs.smartFormat) {
        LazyListState(rs.position.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)), rs.offset)
    }
    val layouts = remember(blocks) { HashMap<Int, TextLayoutResult>() }

    // persist reading position
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(600)
            .collect { (i, o) -> rs = rs.copy(position = i, offset = o); Library.saveReaderState(guide.id, rs) }
    }

    fun jumpTo(block: Int) = scope.launch { listState.scrollToItem(block.coerceIn(0, blocks.size - 1)) }

    LaunchedEffect(matchIdx, matches) {
        matches.getOrNull(matchIdx)?.let { m ->
            val vis = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (m.block !in vis) listState.scrollToItem(m.block)
        }
    }

    // Auto-scroll to follow speech
    LaunchedEffect(speaker.current) {
        val seg = speaker.segments.getOrNull(speaker.current) ?: return@LaunchedEffect
        if (!Prefs.ttsAutoScroll || !ttsVisible) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == seg.block }
        if (item == null) {
            listState.animateScrollToItem(seg.block)
        } else {
            val layout = layouts[seg.block]
            val lineTop = layout?.let { it.getLineTop(it.getLineForOffset(seg.start.coerceIn(0, it.layoutInput.text.length))) } ?: 0f
            val y = item.offset + lineTop
            val viewEnd = info.viewportEndOffset
            if (y > viewEnd * 0.75f || y < 0) listState.animateScrollBy(y - viewEnd * 0.3f)
        }
    }

    fun startFromVisible() {
        ttsVisible = true
        scope.launch {
            // wait for segments to be ready
            var tries = 0
            while (speaker.segments.isEmpty() && tries < 50) { kotlinx.coroutines.delay(50); tries++ }
            speaker.playFromBlock(listState.firstVisibleItemIndex)
        }
    }

    fun startAt(block: Int, offset: Int) {
        ttsVisible = true
        scope.launch {
            var tries = 0
            while (speaker.segments.isEmpty() && tries < 50) { kotlinx.coroutines.delay(50); tries++ }
            speaker.playFromBlock(block, offset)
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }

    Scaffold(
        topBar = {
            Column {
                ReaderTopBar(guide.title, onBack) {
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, L("Search", "Ara")) }
                    IconButton(onClick = { showToc = true }) { Icon(Icons.AutoMirrored.Filled.List, L("Contents", "İçindekiler")) }
                    IconButton(onClick = { if (ttsVisible) { speaker.stop(); ttsVisible = false } else startFromVisible() }) {
                        Icon(if (ttsVisible) Icons.Filled.HeadsetOff else Icons.Filled.Headphones, L("Read aloud", "Sesli oku"))
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.TextFields, L("Reading settings", "Okuma ayarları")) }
                    if (onPixie != null) {
                        IconButton(onClick = onPixie) { Icon(Icons.Filled.AutoAwesome, "Pixie AI") }
                    }
                }
                if (searching) {
                    SearchRow(query, { query = it }, matches.size, matchIdx,
                        onPrev = { if (matches.isNotEmpty()) matchIdx = (matchIdx - 1 + matches.size) % matches.size },
                        onNext = { if (matches.isNotEmpty()) matchIdx = (matchIdx + 1) % matches.size },
                        onClose = { searching = false; query = "" })
                }
            }
        },
        bottomBar = {
            if (ttsVisible) {
                TtsBar(speaker, showCaption = false, onClose = { ttsVisible = false }, onStartFromVisible = { startFromVisible() })
            }
        }
    ) { pad ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (!Prefs.controllerEnabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val page = listState.layoutInfo.viewportSize.height * 0.85f
                    val step = Prefs.scrollStep.toFloat()
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { listState.animateScrollBy(step) }; true }
                        Key.DirectionUp -> { scope.launch { listState.animateScrollBy(-step) }; true }
                        Key.ButtonR1, Key.PageDown, Key.DirectionRight -> { scope.launch { listState.animateScrollBy(page) }; true }
                        Key.ButtonL1, Key.PageUp, Key.DirectionLeft -> { scope.launch { listState.animateScrollBy(-page) }; true }
                        Key.ButtonY, Key.MediaPlayPause, Key.Spacebar -> {
                            if (!ttsVisible) startFromVisible() else if (speaker.playing) speaker.pause() else speaker.play(); true
                        }
                        Key.ButtonR2, Key.MediaNext -> { speaker.next(); true }
                        Key.ButtonL2, Key.MediaPrevious -> { speaker.prev(); true }
                        Key.ButtonX -> { showToc = true; true }
                        else -> false
                    }
                }
        ) {
            val screenW = maxWidth
            val hScroll = rememberScrollState()
            val wrap = rs.lineWrap
            val fontSize = rs.fontSize.sp
            val proseFamily = fontFamilyFor(if (rs.font == "default") (if (rs.monospace) "mono" else "sans") else rs.font)
            val searchBg = theme.accent.copy(alpha = 0.35f)
            val curSearchBg = theme.accent.copy(alpha = 0.7f)
            val seg = if (ttsVisible) speaker.segments.getOrNull(speaker.current) else null
            val curMatch = matches.getOrNull(matchIdx)
            Box(if (wrap) Modifier.fillMaxSize() else Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                LazyColumn(
                    state = listState,
                    modifier = if (wrap) Modifier.fillMaxSize() else Modifier.fillMaxHeight().wrapContentWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    itemsIndexed(blocks, key = { i, _ -> i }) { i, b ->
                        val annotated = buildAnnotatedString {
                            append(b.text)
                            if (matches.isNotEmpty()) {
                                for (m in matches) {
                                    if (m.block == i) addStyle(SpanStyle(background = if (m == curMatch) curSearchBg else searchBg), m.start, m.end)
                                }
                            }
                            if (seg != null && seg.block == i) {
                                val s = seg.start.coerceIn(0, b.text.length); val e = seg.end.coerceIn(s, b.text.length)
                                addStyle(SpanStyle(background = theme.highlight), s, e)
                                if (Prefs.ttsWordHighlight && speaker.wordStart >= 0) {
                                    val ws = (s + speaker.wordStart).coerceIn(s, e)
                                    val we = (s + speaker.wordEnd).coerceIn(ws, e)
                                    if (we > ws) addStyle(SpanStyle(background = theme.accent.copy(alpha = 0.55f), fontWeight = FontWeight.Bold), ws, we)
                                }
                            }
                        }
                        val isPre = b.pre
                        Text(
                            annotated,
                            fontFamily = if (isPre) FontFamily.Monospace else proseFamily,
                            fontSize = fontSize,
                            lineHeight = fontSize * 1.35f,
                            color = theme.text,
                            softWrap = wrap || !isPre,
                            textAlign = if (rs.justify && !isPre) TextAlign.Justify else TextAlign.Start,
                            onTextLayout = { layouts[i] = it },
                            modifier = Modifier
                                .then(if (!wrap && !isPre) Modifier.width(screenW - 28.dp) else Modifier)
                                .padding(bottom = (rs.fontSize * 0.8f).dp)
                                .pointerInput(i, ttsVisible) {
                                    detectTapGestures(
                                        onLongPress = { pos ->
                                            val off = layouts[i]?.getOffsetForPosition(pos) ?: 0
                                            startAt(i, off)
                                        },
                                        onTap = { pos ->
                                            if (ttsVisible) {
                                                val off = layouts[i]?.getOffsetForPosition(pos) ?: 0
                                                startAt(i, off)
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
            if (blocks.isEmpty()) {
                Text(L("This guide is empty.", "Bu rehber boş."), Modifier.align(Alignment.Center), color = Color.Gray)
            }
        }
    }

    if (showToc) {
        TocDialog(toc, listState.firstVisibleItemIndex, onPick = { showToc = false; jumpTo(it.block) }, onDismiss = { showToc = false })
    }
    if (showSettings) {
        ReadingSettingsSheet(rs, isTxt = true, onChange = { updateRs(it) }, onDismiss = { showSettings = false })
    }
}

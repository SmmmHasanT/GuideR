package com.hasantasdelen.guider.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hasantasdelen.guider.data.Guide
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.tts.Speaker
import com.hasantasdelen.guider.tts.TtsBar
import com.hasantasdelen.guider.tts.detectLanguage
import com.hasantasdelen.guider.tts.rememberSpeaker
import com.hasantasdelen.guider.util.L
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// ======================= PDF =======================

@Composable
fun PdfReaderScreen(guide: Guide, onBack: () -> Unit, onPixie: (() -> Unit)?) {
    val ctx = LocalContext.current
    val file = remember { Library.guideFile(guide) }
    val scope = rememberCoroutineScope()
    val renderer = remember {
        try {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(renderer) { onDispose { try { renderer?.close() } catch (_: Exception) {} } }
    val rs = remember { Library.readerState(guide.id) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rs.position)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            Library.saveReaderState(guide.id, rs.copy(position = it))
        }
    }
    val speaker = rememberSpeaker()
    var ttsVisible by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }
    KeepScreenOn(Prefs.keepScreenOn || speaker.playing)

    fun startTts() {
        ttsVisible = true
        if (speaker.segments.isNotEmpty()) { speaker.playFromBlock(listState.firstVisibleItemIndex); return }
        extracting = true
        scope.launch {
            val segs = withContext(Dispatchers.IO) {
                try {
                    PDFBoxResourceLoader.init(ctx.applicationContext)
                    PDDocument.load(file).use { doc ->
                        val stripper = PDFTextStripper()
                        val pages = (1..doc.numberOfPages).map { p ->
                            stripper.startPage = p; stripper.endPage = p
                            stripper.getText(doc)
                        }
                        val lang = guide.language ?: detectLanguage(pages.joinToString(" ").take(5000))
                        speaker.setLanguage(lang)
                        pages.flatMapIndexed { i, t ->
                            // join hard-wrapped lines within paragraphs
                            val clean = t.replace(Regex("(?<![.!?:])\\n(?!\\n)"), " ")
                            Speaker.segmentsFor(i, clean, lang)
                        }
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            extracting = false
            speaker.setSegments(segs)
            if (segs.isEmpty()) speaker.error = L("No readable text in this PDF (scanned pages?).", "Bu PDF'te okunabilir metin yok (taranmış sayfa olabilir).")
            else speaker.playFromBlock(listState.firstVisibleItemIndex)
        }
    }

    LaunchedEffect(speaker.current) {
        val seg = speaker.segments.getOrNull(speaker.current) ?: return@LaunchedEffect
        if (ttsVisible && Prefs.ttsAutoScroll && listState.layoutInfo.visibleItemsInfo.none { it.index == seg.block }) {
            listState.animateScrollToItem(seg.block)
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }

    Scaffold(
        topBar = {
            ReaderTopBar(guide.title, onBack) {
                IconButton(onClick = { if (ttsVisible) { speaker.stop(); ttsVisible = false } else startTts() }) {
                    Icon(if (ttsVisible) Icons.Filled.HeadsetOff else Icons.Filled.Headphones, L("Read aloud", "Sesli oku"))
                }
                if (onPixie != null) IconButton(onClick = onPixie) { Icon(Icons.Filled.AutoAwesome, "Pixie AI") }
            }
        },
        bottomBar = {
            Column {
                if (extracting) LinearProgressIndicator(Modifier.fillMaxWidth())
                speaker.error?.takeIf { !it.startsWith("missing_lang") && ttsVisible }?.let { Text(it, Modifier.padding(8.dp)) }
                if (ttsVisible) TtsBar(speaker, showCaption = true, onClose = { ttsVisible = false }, onStartFromVisible = { startTts() })
            }
        }
    ) { pad ->
        if (renderer == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text(L("Could not open PDF.", "PDF açılamadı.")) }
            return@Scaffold
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(pad).focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (!Prefs.controllerEnabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val page = listState.layoutInfo.viewportSize.height * 0.85f
                    when (e.key) {
                        Key.DirectionDown -> { scope.launch { listState.animateScrollBy(Prefs.scrollStep.toFloat()) }; true }
                        Key.DirectionUp -> { scope.launch { listState.animateScrollBy(-Prefs.scrollStep.toFloat()) }; true }
                        Key.ButtonR1, Key.PageDown, Key.DirectionRight -> { scope.launch { listState.animateScrollBy(page) }; true }
                        Key.ButtonL1, Key.PageUp, Key.DirectionLeft -> { scope.launch { listState.animateScrollBy(-page) }; true }
                        Key.ButtonY -> { if (!ttsVisible) startTts() else speaker.toggle(); true }
                        else -> false
                    }
                }
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }.coerceIn(200, 1800)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(renderer.pageCount) { i ->
                    val highlighted = ttsVisible && speaker.segments.getOrNull(speaker.current)?.block == i
                    PdfPage(renderer, i, widthPx, highlighted)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int, widthPx: Int, highlighted: Boolean) {
    val ratio = remember(index) {
        synchronized(renderer) {
            renderer.openPage(index).use { it.height.toFloat() / it.width.toFloat() }
        }
    }
    val bmp by produceState<Bitmap?>(null, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            try {
                synchronized(renderer) {
                    renderer.openPage(index).use { page ->
                        val w = widthPx
                        val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f / ratio)
            .then(if (highlighted) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .background(Color.White)
    ) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth) }
    }
}

// ======================= Zoomable image =======================

@Composable
fun ZoomableImage(model: Any?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .then(
                if (enabled) Modifier
                    .pointerInput(model) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    offset = if (scale == 1f) Offset.Zero else offset + pan
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(model, "dt") {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            if (scale == 1f) offset = Offset.Zero
                        })
                    }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            }
        )
    }
}

/** Converts an <img src> value from the HTML reader into something Coil can load. */
fun imageModelFromSrc(src: String): Any? = when {
    src.startsWith("data:") -> try {
        Base64.decode(src.substringAfter(","), Base64.DEFAULT)
    } catch (e: Exception) {
        null
    }
    src.startsWith("file://") -> File(java.net.URI(src))
    else -> src
}

@Composable
fun ImageDialog(src: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(remember(src) { imageModelFromSrc(src) }, Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color.White)
            }
        }
    }
}

// ======================= Image + annotations =======================

@Serializable
data class Mark(
    val kind: String, // pen, rect, oval, text
    val color: Int,
    val points: List<Float> = emptyList(), // normalized x,y pairs
    val text: String = "",
)

@Serializable
data class Annotations(val marks: List<Mark> = emptyList())

private val annJson = Json { ignoreUnknownKeys = true }

private fun annFile(guide: Guide) = File(Library.guidesDir, guide.path + ".ann.json")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageReaderScreen(guide: Guide, onBack: () -> Unit) {
    val file = remember { Library.guideFile(guide) }
    var marks by remember {
        mutableStateOf(
            try { annJson.decodeFromString<Annotations>(annFile(guide).readText()).marks } catch (e: Exception) { emptyList() }
        )
    }
    fun save(m: List<Mark>) {
        marks = m
        try { annFile(guide).writeText(annJson.encodeToString(Annotations(m))) } catch (_: Exception) {}
    }
    var tool by remember { mutableStateOf<String?>(null) } // null = view/zoom
    var color by remember { mutableStateOf(Color(0xFFFF5252)) }
    var pendingTextAt by remember { mutableStateOf<Offset?>(null) }
    val ratio = remember {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth > 0) o.outWidth.toFloat() / o.outHeight else 1f
    }
    Scaffold(
        topBar = {
            ReaderTopBar(guide.title, onBack) {
                IconButton(onClick = { tool = if (tool == null) "pen" else null }) {
                    Icon(if (tool == null) Icons.Filled.Edit else Icons.Filled.ZoomIn, L("Annotate", "Not al"))
                }
            }
        },
        bottomBar = {
            if (tool != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("pen" to Icons.Filled.Gesture, "rect" to Icons.Filled.CropSquare, "oval" to Icons.Filled.RadioButtonUnchecked, "text" to Icons.Filled.TextFields).forEach { (k, ic) ->
                            IconToggleButton(checked = tool == k, onCheckedChange = { tool = k }) { Icon(ic, k) }
                        }
                        Spacer(Modifier.width(8.dp))
                        listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color.White, Color.Black).forEach { c ->
                            Box(
                                Modifier.padding(3.dp).size(26.dp).clip(CircleShape).background(c)
                                    .border(if (c == color) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { color = c }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { if (marks.isNotEmpty()) save(marks.dropLast(1)) }) { Icon(Icons.AutoMirrored.Filled.Undo, L("Undo", "Geri al")) }
                        IconButton(onClick = { save(emptyList()) }) { Icon(Icons.Filled.DeleteSweep, L("Clear", "Temizle")) }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Color.Black), contentAlignment = Alignment.Center) {
            if (tool == null) {
                Box(Modifier.fillMaxSize()) {
                    ZoomableImage(file, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.aspectRatio(ratio)) { MarksCanvas(marks, null) }
                    }
                }
            } else {
                Box(Modifier.aspectRatio(ratio)) {
                    AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    var size by remember { mutableStateOf(IntSize(1, 1)) }
                    var live by remember { mutableStateOf<Mark?>(null) }
                    Box(
                        Modifier.fillMaxSize().onSizeChanged { size = it }
                            .pointerInput(tool, color) {
                                if (tool == "text") {
                                    detectTapGestures { p -> pendingTextAt = Offset(p.x / size.width, p.y / size.height) }
                                } else {
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            live = Mark(tool!!, color.toArgb(), listOf(p.x / size.width, p.y / size.height, p.x / size.width, p.y / size.height))
                                        },
                                        onDragEnd = { live?.let { save(marks + it) }; live = null },
                                        onDragCancel = { live = null },
                                        onDrag = { change, _ ->
                                            val p = change.position
                                            val nx = p.x / size.width; val ny = p.y / size.height
                                            live = live?.let { m ->
                                                if (m.kind == "pen") m.copy(points = m.points + listOf(nx, ny))
                                                else m.copy(points = listOf(m.points[0], m.points[1], nx, ny))
                                            }
                                        }
                                    )
                                }
                            }
                    ) { MarksCanvas(marks + listOfNotNull(live), null) }
                }
            }
        }
    }
    pendingTextAt?.let { at ->
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextAt = null },
            confirmButton = {
                TextButton(onClick = {
                    if (txt.isNotBlank()) save(marks + Mark("text", color.toArgb(), listOf(at.x, at.y), txt))
                    pendingTextAt = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pendingTextAt = null }) { Text(L("Cancel", "İptal")) } },
            title = { Text(L("Add note", "Not ekle")) },
            text = { OutlinedTextField(txt, { txt = it }) }
        )
    }
}

@Composable
private fun MarksCanvas(marks: List<Mark>, @Suppress("UNUSED_PARAMETER") unused: Any?) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val strokeW = with(density) { 3.dp.toPx() }
        for (m in marks) {
            val c = Color(m.color)
            when (m.kind) {
                "pen" -> {
                    if (m.points.size >= 4) {
                        val path = Path()
                        path.moveTo(m.points[0] * w, m.points[1] * h)
                        var i = 2
                        while (i + 1 < m.points.size) { path.lineTo(m.points[i] * w, m.points[i + 1] * h); i += 2 }
                        drawPath(path, c, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                "rect", "oval" -> if (m.points.size >= 4) {
                    val x0 = minOf(m.points[0], m.points[2]) * w; val y0 = minOf(m.points[1], m.points[3]) * h
                    val x1 = maxOf(m.points[0], m.points[2]) * w; val y1 = maxOf(m.points[1], m.points[3]) * h
                    if (m.kind == "rect") drawRect(c, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(strokeW))
                    else drawOval(c, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(strokeW))
                }
                "text" -> if (m.points.size >= 2) {
                    val paint = android.graphics.Paint().apply {
                        color = m.color; textSize = with(density) { 18.dp.toPx() }; isAntiAlias = true
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText(m.text, m.points[0] * w, m.points[1] * h, paint)
                }
            }
        }
    }
}

// ======================= CBZ =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CbzReaderScreen(guide: Guide, onBack: () -> Unit) {
    val dir = remember { Library.guideFile(guide) }
    val pages = remember { dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList() }
    val rs = remember { Library.readerState(guide.id) }
    val pager = rememberPagerState(initialPage = rs.position.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { pages.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { Library.saveReaderState(guide.id, rs.copy(position = it)) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }
    Scaffold(topBar = {
        ReaderTopBar("${guide.title}  (${pager.currentPage + 1}/${pages.size})", onBack) {}
    }) { pad ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(pad).background(Color.Black).focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (!Prefs.controllerEnabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.ButtonR1, Key.DirectionRight, Key.PageDown -> { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }; true }
                        Key.ButtonL1, Key.DirectionLeft, Key.PageUp -> { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }; true }
                        else -> false
                    }
                }
        ) { i ->
            ZoomableImage(pages[i], Modifier.fillMaxSize())
        }
    }
}

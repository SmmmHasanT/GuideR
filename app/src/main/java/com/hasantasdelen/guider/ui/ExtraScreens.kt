package com.hasantasdelen.guider.ui

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.hasantasdelen.guider.MainActivity
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.reader.SwitchRow
import com.hasantasdelen.guider.tts.Speaker
import com.hasantasdelen.guider.tts.TtsSettingsDialog
import com.hasantasdelen.guider.tts.rememberSpeaker
import com.hasantasdelen.guider.util.GuideTranslator
import com.hasantasdelen.guider.util.L
import com.hasantasdelen.guider.util.Lang
import com.hasantasdelen.guider.web.Achievement
import com.hasantasdelen.guider.web.ChatMsg
import com.hasantasdelen.guider.web.Pixie
import com.hasantasdelen.guider.web.RaGame
import com.hasantasdelen.guider.web.RetroAchievements
import kotlinx.coroutines.launch

// ======================= Pixie AI =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixieScreen(gameId: String?, onBack: () -> Unit, onSettings: () -> Unit) {
    val game = gameId?.let { Library.game(it) }
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val speaker = rememberSpeaker()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || busy) return
        input = ""
        msgs.add(ChatMsg(true, q))
        busy = true
        scope.launch {
            val answer = try {
                Pixie.ask(msgs.toList(), game?.title)
            } catch (e: Exception) {
                "⚠ " + (e.message ?: "error")
            }
            msgs.add(ChatMsg(false, answer))
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixie AI" + (game?.let { " · ${it.title}" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(8.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text(L("Ask about this game…", "Bu oyunla ilgili sor…")) }, maxLines = 4)
                IconButton(onClick = { send() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.Send, null) }
            }
        }
    ) { pad ->
        if (!Prefs.pixieEnabled || Prefs.geminiKey.isBlank()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    L("Pixie uses Google Gemini with web search. Add your own Gemini API key in Settings (free key: aistudio.google.com). Google may charge depending on your usage.",
                        "Pixie, web aramalı Google Gemini kullanır. Ayarlar'a kendi Gemini API anahtarını ekle (ücretsiz anahtar: aistudio.google.com). Kullanımına göre Google ücret alabilir."),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSettings) { Text(L("Open Settings", "Ayarları Aç")) }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 560.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            SelectionContainer { Text(m.text) }
                            if (!m.fromUser) {
                                IconButton(onClick = {
                                    speaker.setSegments(Speaker.segmentsFor(0, m.text, if (Lang.isTr) "tr" else "en"))
                                    speaker.play(0)
                                }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.AutoMirrored.Filled.VolumeUp, L("Read aloud", "Sesli oku")) }
                            }
                        }
                    }
                }
            }
            if (busy) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
        }
    }
}

// ======================= RetroAchievements =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(gameId: String, onBack: () -> Unit, onSettings: () -> Unit) {
    val game = Library.game(gameId) ?: return
    val ctx = LocalContext.current
    var raId by remember { mutableStateOf(game.raGameId) }
    var linkInput by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<RaGame?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("order") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(raId, reload) {
        val id = raId ?: return@LaunchedEffect
        if (Prefs.raUser.isBlank() || Prefs.raKey.isBlank()) return@LaunchedEffect
        loading = true; error = null
        try { data = RetroAchievements.progress(id) } catch (e: Exception) { error = e.message }
        loading = false
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("RetroAchievements") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                if (raId != null) {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, null) }
                    IconButton(onClick = { Library.updateGame(gameId) { it.copy(raGameId = null) }; raId = null; data = null }) { Icon(Icons.Filled.LinkOff, L("Unlink", "Bağlantıyı kaldır")) }
                }
            }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (Prefs.raUser.isBlank() || Prefs.raKey.isBlank()) {
                Column(Modifier.padding(24.dp)) {
                    Text(L("Enter your RetroAchievements username and Web API key in Settings (retroachievements.org → Settings → Keys).",
                        "Ayarlar'a RetroAchievements kullanıcı adını ve Web API anahtarını gir (retroachievements.org → Settings → Keys)."))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSettings) { Text(L("Open Settings", "Ayarları Aç")) }
                }
                return@Column
            }
            if (raId == null) {
                Column(Modifier.padding(24.dp)) {
                    Text(L("Link \"${game.title}\" to a RetroAchievements game. Paste its page URL or game ID.",
                        "\"${game.title}\" oyununu bir RetroAchievements oyununa bağla. Oyun sayfasının adresini ya da ID'sini yapıştır."))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(linkInput, { linkInput = it }, singleLine = true, placeholder = { Text("https://retroachievements.org/game/1234") }, modifier = Modifier.fillMaxWidth())
                    Row {
                        Button(onClick = {
                            RetroAchievements.parseGameId(linkInput)?.let { id ->
                                Library.updateGame(gameId) { it.copy(raGameId = id) }; raId = id
                            }
                        }, modifier = Modifier.padding(top = 10.dp)) { Text(L("Link", "Bağla")) }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/searchresults.php?s=" + Uri.encode(game.title))))
                        }, modifier = Modifier.padding(top = 10.dp)) { Text(L("Search on site", "Sitede ara")) }
                    }
                }
                return@Column
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            val d = data ?: return@Column
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                d.icon?.let { AsyncImage(it, null, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleMedium)
                    Text(d.console, style = MaterialTheme.typography.bodySmall)
                    Text("${d.numAwarded}/${d.achievements.size} · ${d.completion}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (d.achievements.isNotEmpty()) {
                LinearProgressIndicator(progress = { d.numAwarded.toFloat() / d.achievements.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to L("All", "Tümü"), "locked" to L("Locked", "Kilitli"), "unlocked" to L("Unlocked", "Açılmış"), "missable" to L("Missable", "Kaçırılabilir")).forEach { (k, n) ->
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(n) })
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("order" to L("Default", "Varsayılan"), "points" to L("Points", "Puan"), "title" to L("Name", "Ad")).forEach { (k, n) ->
                    FilterChip(selected = sort == k, onClick = { sort = k }, label = { Text(n) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) })
                }
            }
            val list = remember(d, filter, sort) {
                d.achievements.filter {
                    when (filter) {
                        "locked" -> !it.earned
                        "unlocked" -> it.earned
                        "missable" -> it.type == "missable"
                        else -> true
                    }
                }.sortedWith(
                    when (sort) {
                        "points" -> compareByDescending<Achievement> { it.points }
                        "title" -> compareBy<Achievement> { it.title.lowercase() }
                        else -> compareBy<Achievement> { it.order }
                    }
                )
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(a.badgeUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).alpha(if (a.earned) 1f else 0.6f))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                when (a.type) {
                                    "missable" -> Icon(Icons.Filled.Warning, L("Missable", "Kaçırılabilir"), Modifier.padding(start = 4.dp).size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFFFFB300))
                                    "progression" -> Icon(Icons.Filled.Flag, L("Progression", "İlerleme"), Modifier.padding(start = 4.dp).size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFF42A5F5))
                                    "win_condition" -> Icon(Icons.Filled.EmojiEvents, L("Win condition", "Bitirme"), Modifier.padding(start = 4.dp).size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFFFFD54F))
                                }
                            }
                            Text(a.description, style = MaterialTheme.typography.bodySmall)
                            a.dateEarned?.let { Text((if (a.earnedHardcore) "★ " else "") + it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        Text("${a.points}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// ======================= Settings =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val speaker = rememberSpeaker()
    var voiceDialog by remember { mutableStateOf(false) }
    var voiceHelp by remember { mutableStateOf(false) }
    var engineMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(L("Settings", "Ayarlar")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Section(L("Appearance", "Görünüm"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Theme", "Tema"), Modifier.weight(1f))
                Box {
                    OutlinedButton(onClick = { themeMenu = true }) { Text(currentTheme().name) }
                    DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                        Themes.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { Prefs.theme = t.id; themeMenu = false }) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("App language", "Uygulama dili"), Modifier.weight(1f))
                Box {
                    val names = mapOf("system" to L("System", "Sistem"), "tr" to "Türkçe", "en" to "English")
                    OutlinedButton(onClick = { langMenu = true }) { Text(names[Prefs.appLanguage] ?: Prefs.appLanguage) }
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        names.forEach { (k, n) ->
                            DropdownMenuItem(text = { Text(n) }, onClick = {
                                langMenu = false
                                Prefs.appLanguage = k
                                AppCompatDelegate.setApplicationLocales(
                                    if (k == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(k)
                                )
                            })
                        }
                    }
                }
            }
            SwitchRow(L("Grid layout on home screen", "Ana ekranda ızgara görünümü"), Prefs.gridLayout) { Prefs.gridLayout = it }
            Text(L("Default font size", "Varsayılan yazı boyutu") + ": ${Prefs.defaultFontSize} sp")
            Slider(Prefs.defaultFontSize.toFloat(), { Prefs.defaultFontSize = it.toInt() }, valueRange = 10f..32f, steps = 21)

            Section(L("Reading", "Okuma"))
            SwitchRow(L("Apply theme colors to HTML guides", "HTML rehberlere tema renklerini uygula"), Prefs.themeHtml) { Prefs.themeHtml = it }
            SwitchRow(L("Keep screen on while reading", "Okurken ekranı açık tut"), Prefs.keepScreenOn) { Prefs.keepScreenOn = it }
            SwitchRow(L("Show TOC navigation bar (HTML)", "İçindekiler gezinme çubuğunu göster (HTML)"), Prefs.showTocBar) { Prefs.showTocBar = it }

            Section(L("Controller", "Kumanda"))
            SwitchRow(L("Enable controller support", "Kumanda desteğini aç"), Prefs.controllerEnabled) { Prefs.controllerEnabled = it }
            Text(
                L("D-pad: scroll · L1/R1: page · Y: play/pause reading · L2/R2: previous/next sentence · X: contents",
                    "Yön tuşları: kaydır · L1/R1: sayfa · Y: sesli okumayı başlat/duraklat · L2/R2: önceki/sonraki cümle · X: içindekiler"),
                style = MaterialTheme.typography.bodySmall
            )
            Text(L("Scroll step", "Kaydırma adımı") + ": ${Prefs.scrollStep}px")
            Slider(Prefs.scrollStep.toFloat(), { Prefs.scrollStep = it.toInt() }, valueRange = 40f..600f)

            Section(L("Read aloud (Text-to-Speech)", "Sesli okuma (Metin Okuma)"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Speech engine", "Ses motoru"), Modifier.weight(1f))
                Box {
                    val engines = remember(speaker.ready) { speaker.engines() }
                    OutlinedButton(onClick = { engineMenu = true }) {
                        Text(engines.firstOrNull { it.name == Prefs.ttsEngine }?.label ?: L("System default", "Sistem varsayılanı"), maxLines = 1)
                    }
                    DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                        DropdownMenuItem(text = { Text(L("System default", "Sistem varsayılanı")) }, onClick = { Prefs.ttsEngine = ""; engineMenu = false; speaker.restartEngine() })
                        engines.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { Prefs.ttsEngine = e.name; engineMenu = false; speaker.restartEngine() }) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { voiceDialog = true }) { Text(L("Voice, speed, language", "Ses, hız, dil")) }
                OutlinedButton(onClick = {
                    speaker.setLanguage(if (Lang.isTr) "tr" else "en")
                    speaker.setSegments(listOf(com.hasantasdelen.guider.tts.Segment(0, 0, 0,
                        L("This is how GuideR reads your guides.", "GuideR rehberlerini böyle okuyacak."))))
                    speaker.play(0)
                }) { Text(L("Test", "Dene")) }
            }
            TextButton(onClick = {
                voiceHelp = true
            }) { Text(L("Install voices (e.g. Turkish)", "Ses paketi yükle (ör. Türkçe)")) }
            TextButton(onClick = {
                com.hasantasdelen.guider.tts.openTtsSettings(ctx)
            }) { Text(L("Android text-to-speech settings", "Android metin okuma ayarları")) }

            Section(L("Translation", "Çeviri"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Default target language", "Varsayılan hedef dil"), Modifier.weight(1f))
                Box {
                    OutlinedButton(onClick = { targetMenu = true }) { Text(GuideTranslator.languages.firstOrNull { it.first == Prefs.translateTarget }?.second ?: Prefs.translateTarget) }
                    DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                        GuideTranslator.languages.forEach { (c, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { Prefs.translateTarget = c; targetMenu = false }) }
                    }
                }
            }

            Section(L("Web", "Web"))
            SwitchRow(L("Block ads while browsing", "Gezinirken reklamları engelle"), Prefs.adBlock) { Prefs.adBlock = it }

            Section("Pixie AI")
            SwitchRow(L("Enable Pixie AI", "Pixie AI'yı aç"), Prefs.pixieEnabled) { Prefs.pixieEnabled = it }
            OutlinedTextField(Prefs.geminiKey, { Prefs.geminiKey = it }, label = { Text("Gemini API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(Prefs.geminiModel, { Prefs.geminiModel = it }, label = { Text(L("Model", "Model")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(L("Requests go to Google Gemini with your key and may incur costs.", "İstekler senin anahtarınla Google Gemini'ye gider; ücret çıkabilir."), style = MaterialTheme.typography.bodySmall)

            Section("RetroAchievements")
            OutlinedTextField(Prefs.raUser, { Prefs.raUser = it }, label = { Text(L("Username", "Kullanıcı adı")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(Prefs.raKey, { Prefs.raKey = it }, label = { Text("Web API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

            Section(L("Other", "Diğer"))
            OutlinedButton(onClick = {
                val i = Intent(ctx, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
                ctx.startActivity(i)
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(L("Open another window", "Başka pencere aç")) }
            Spacer(Modifier.height(20.dp))
            Text("GuideR · " + L("Game guide reader with read-aloud", "Sesli okumalı oyun rehberi okuyucu"), style = MaterialTheme.typography.bodySmall)
            Text(L("No telemetry, no tracking. Your library stays on your device.", "Telemetri yok, takip yok. Kütüphanen cihazında kalır."), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(40.dp))
        }
    }
    if (voiceDialog) TtsSettingsDialog(speaker) { voiceDialog = false }
    if (voiceHelp) com.hasantasdelen.guider.tts.VoiceInstallDialog(speaker, "tr") { voiceHelp = false }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

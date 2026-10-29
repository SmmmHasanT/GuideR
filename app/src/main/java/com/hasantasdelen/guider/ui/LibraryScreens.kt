package com.hasantasdelen.guider.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hasantasdelen.guider.data.Game
import com.hasantasdelen.guider.data.Guide
import com.hasantasdelen.guider.data.GuideType
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.util.GuideTranslator
import com.hasantasdelen.guider.util.L
import com.hasantasdelen.guider.util.formatSize
import com.hasantasdelen.guider.util.newId
import com.hasantasdelen.guider.web.GameHit
import com.hasantasdelen.guider.web.GameMeta
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// ======================= Cover =======================

@Composable
fun GameCover(game: Game, modifier: Modifier = Modifier) {
    val model: Any? = game.coverPath?.let { File(it) } ?: game.coverUrl
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(model, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                game.title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ======================= Home =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onGame: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val games by Library.games.collectAsStateWithLifecycle()
    val sorted = remember(games) { games.sortedBy { it.title.lowercase() } }
    var filter by remember { mutableStateOf("") }
    var filtering by remember { mutableStateOf(false) }
    val shown = remember(sorted, filter) { if (filter.isBlank()) sorted else sorted.filter { it.title.contains(filter, true) } }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("GuideR", fontWeight = FontWeight.Medium) },
                    actions = {
                        IconButton(onClick = { filtering = !filtering; if (!filtering) filter = "" }) { Icon(Icons.Filled.Search, L("Filter", "Filtrele")) }
                        IconButton(onClick = { Prefs.gridLayout = !Prefs.gridLayout }) {
                            Icon(if (Prefs.gridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, L("Layout", "Görünüm"))
                        }
                        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, L("Add game", "Oyun ekle")) }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, L("Settings", "Ayarlar")) }
                    }
                )
                if (filtering) {
                    OutlinedTextField(filter, { filter = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        placeholder = { Text(L("Filter games", "Oyunlarda ara")) })
                }
            }
        }
    ) { pad ->
        if (games.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(L("Your library is empty", "Kütüphanen boş"), style = MaterialTheme.typography.titleLarge)
                Text(
                    L("Add a game, then download or import its guides. Every guide can be read aloud.",
                        "Bir oyun ekle, sonra rehberlerini indir ya da içe aktar. Her rehber sesli okunabilir."),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(L("Add Game", "Oyun Ekle")) }
            }
        } else if (Prefs.gridLayout) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(shown, key = { it.id }) { g ->
                    Column(Modifier.clickable { onGame(g.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            GameCover(g, Modifier.fillMaxWidth().aspectRatio(0.75f))
                            if (g.guides.isNotEmpty()) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) { Text("${g.guides.size}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Text(g.title, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onGame(g.id) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameCover(g, Modifier.size(56.dp, 72.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(g.year, g.platforms?.take(60)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${g.guides.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ======================= Add game =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameScreen(onBack: () -> Unit, onAdded: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GameHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var customDialog by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        loading = true; error = null
        try {
            results = GameMeta.search(query.trim())
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(L("Add Game", "Oyun Ekle")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { TextButton(onClick = { customDialog = true }) { Text(L("Add Custom Game", "Özel Oyun Ekle")) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                placeholder = { Text(L("Search game title", "Oyun adı ara")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            if (loading || adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { h ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !adding) {
                            adding = true
                            scope.launch {
                                val d = try { GameMeta.details(h) } catch (e: Exception) { null }
                                val cover = d?.coverBytes?.let { Library.saveCoverBytes(it) }
                                val title = h.title.replace(Regex("\\s*\\((\\d{4} )?video game\\)$"), "")
                                val g = Game(newId(), title, coverPath = cover, year = d?.year, platforms = d?.platforms)
                                Library.addGame(g)
                                adding = false
                                onAdded(g.id)
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(52.dp, 68.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (h.thumbnail != null) AsyncImage(h.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(h.title, style = MaterialTheme.typography.titleMedium)
                            Text(h.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    if (query.length >= 2 && !loading) {
                        TextButton(onClick = { customDialog = true }, modifier = Modifier.padding(12.dp)) {
                            Text(L("Can't find it? Add \"$query\" as a custom game", "Bulamadın mı? \"$query\" adını özel oyun olarak ekle"))
                        }
                    }
                }
            }
        }
    }
    if (customDialog) {
        CustomGameDialog(initial = query, onDismiss = { customDialog = false }) { name, coverUri ->
            customDialog = false
            scope.launch {
                val cover = coverUri?.let { Library.copyCover(ctx, it) }
                val g = Game(newId(), name, coverPath = cover, custom = true)
                Library.addGame(g)
                onAdded(g.id)
            }
        }
    }
}

@Composable
fun CustomGameDialog(initial: String, onDismiss: () -> Unit, onCreate: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { cover = it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Add Custom Game", "Özel Oyun Ekle")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(L("Game name", "Oyun adı")) }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text(L("Choose artwork", "Kapak seç")) }
                    if (cover != null) {
                        Spacer(Modifier.width(10.dp))
                        AsyncImage(cover, null, Modifier.size(40.dp, 52.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), cover) }) { Text(L("Add", "Ekle")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel", "İptal")) } }
    )
}

// ======================= Game detail =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: String,
    onBack: () -> Unit,
    onOpenGuide: (Guide) -> Unit,
    onFind: () -> Unit,
    onPixie: () -> Unit,
    onAchievements: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val games by Library.games.collectAsStateWithLifecycle()
    val game = games.firstOrNull { it.id == gameId }
    if (game == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var addSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shortcut by remember { mutableStateOf(false) }
    var renameGuide by remember { mutableStateOf<Guide?>(null) }
    var deleteGuide by remember { mutableStateOf<Guide?>(null) }
    var translateGuide by remember { mutableStateOf<Guide?>(null) }
    var translating by remember { mutableStateOf<String?>(null) }
    var translateJob by remember { mutableStateOf<Job?>(null) }
    var editGame by remember { mutableStateOf(false) }
    val storage = remember(game.guides) { Library.gameStorageBytes(game) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var n = 0
            for (u in uris) if (Library.importUri(ctx, gameId, u) != null) n++
            if (n > 0) Toast.makeText(ctx, L("$n file(s) imported", "$n dosya içe aktarıldı"), Toast.LENGTH_SHORT).show()
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val p = Library.copyCover(ctx, uri)
            if (p != null) Library.updateGame(gameId) { it.copy(coverPath = p) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = onAchievements) { Icon(Icons.Filled.EmojiEvents, "RetroAchievements") }
                if (Prefs.pixieEnabled) IconButton(onClick = onPixie) { Icon(Icons.Filled.AutoAwesome, "Pixie AI") }
                IconButton(onClick = { addSheet = true }) { Icon(Icons.Filled.Add, L("Add guide", "Rehber ekle")) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, L("Delete game", "Oyunu sil")) }
            }
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row {
                    GameCover(game, Modifier.size(110.dp, 140.dp).clickable { coverPicker.launch("image/*") })
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(game.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { editGame = true })
                        game.year?.let { Text(L("Released: ", "Çıkış: ") + it, style = MaterialTheme.typography.bodyMedium) }
                        game.platforms?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        Text(L("Storage: ", "Depolama: ") + formatSize(storage), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                Text(L("Guides, Maps and Charts", "Rehberler, Haritalar ve Tablolar"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
            if (game.guides.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(L("No guides yet.", "Henüz rehber yok."))
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text(L("Select", "Seç")) }
                            OutlinedButton(onClick = onFind) { Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text(L("Find", "Bul")) }
                        }
                    }
                }
            }
            items(game.guides, key = { it.id }) { g ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenGuide(g) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (g.type == GuideType.URL) g.path else formatSize(g.sizeBytes),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(g.type.name, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (g.type == GuideType.TXT) {
                                DropdownMenuItem(text = { Text(L("Translate", "Çevir")) }, leadingIcon = { Icon(Icons.Filled.Translate, null) },
                                    onClick = { menu = false; translateGuide = g })
                            }
                            DropdownMenuItem(text = { Text(L("Rename", "Yeniden adlandır")) }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menu = false; renameGuide = g })
                            DropdownMenuItem(text = { Text(L("Delete", "Sil")) }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; deleteGuide = g })
                        }
                    }
                }
            }
        }
    }

    if (addSheet) {
        ModalBottomSheet(onDismissRequest = { addSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L("Add a Guide, Map or Chart", "Rehber, Harita veya Tablo Ekle"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { addSheet = false; importer.launch(arrayOf("*/*")) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text(L("Select", "Seç"))
                    }
                    Text(L("OR", "VEYA"), Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { addSheet = false; onFind() }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text(L("Find", "Bul"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(L(".TXT, .HTML, .PDF, .CBZ and image files are supported", ".TXT, .HTML, .PDF, .CBZ ve resim dosyaları desteklenir"), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { addSheet = false; shortcut = true }) { Icon(Icons.Filled.Link, null); Spacer(Modifier.width(6.dp)); Text(L("Add a shortcut URL", "Kısayol URL ekle")) }
            }
        }
    }

    if (shortcut) {
        ShortcutDialog("", "", onDismiss = { shortcut = false }) { n, u -> Library.addShortcut(gameId, n, u); shortcut = false }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("Delete game?", "Oyun silinsin mi?")) },
            text = { Text(L("\"${game.title}\" and all of its guides will be deleted.", "\"${game.title}\" ve tüm rehberleri silinecek.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Library.deleteGame(gameId); onBack() }) { Text(L("Delete", "Sil")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("Cancel", "İptal")) } }
        )
    }

    deleteGuide?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGuide = null },
            title = { Text(L("Delete guide?", "Rehber silinsin mi?")) },
            text = { Text(g.title) },
            confirmButton = { TextButton(onClick = { Library.deleteGuide(gameId, g.id); deleteGuide = null }) { Text(L("Delete", "Sil")) } },
            dismissButton = { TextButton(onClick = { deleteGuide = null }) { Text(L("Cancel", "İptal")) } }
        )
    }

    renameGuide?.let { g ->
        var t by remember(g.id) { mutableStateOf(g.title) }
        AlertDialog(
            onDismissRequest = { renameGuide = null },
            title = { Text(L("Rename", "Yeniden adlandır")) },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { Library.updateGuide(gameId, g.id) { it.copy(title = t.trim().ifBlank { it.title }) }; renameGuide = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { renameGuide = null }) { Text(L("Cancel", "İptal")) } }
        )
    }

    if (editGame) {
        var t by remember { mutableStateOf(game.title) }
        AlertDialog(
            onDismissRequest = { editGame = false },
            title = { Text(L("Edit game", "Oyunu düzenle")) },
            text = {
                Column {
                    OutlinedTextField(t, { t = it }, singleLine = true, label = { Text(L("Name", "Ad")) })
                    TextButton(onClick = { editGame = false; coverPicker.launch("image/*") }) { Text(L("Set custom artwork", "Özel kapak ayarla")) }
                }
            },
            confirmButton = { TextButton(onClick = { Library.updateGame(gameId) { it.copy(title = t.trim().ifBlank { it.title }) }; editGame = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { editGame = false }) { Text(L("Cancel", "İptal")) } }
        )
    }

    translateGuide?.let { g ->
        var target by remember { mutableStateOf(Prefs.translateTarget) }
        var langMenu by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (translating == null) translateGuide = null },
            title = { Text(L("Translate guide", "Rehberi çevir")) },
            text = {
                Column {
                    Text(L("On-device translation with Google ML Kit. The language model (~30 MB) is downloaded once.",
                        "Google ML Kit ile cihaz üzerinde çeviri. Dil modeli (~30 MB) bir kez indirilir."), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(onClick = { langMenu = true }, enabled = translating == null) {
                            Text(GuideTranslator.languages.firstOrNull { it.first == target }?.second ?: target)
                        }
                        DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                            GuideTranslator.languages.forEach { (c, n) ->
                                DropdownMenuItem(text = { Text(n) }, onClick = { target = c; langMenu = false })
                            }
                        }
                    }
                    translating?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = translating == null, onClick = {
                    Prefs.translateTarget = target
                    translating = L("Preparing…", "Hazırlanıyor…")
                    translateJob = scope.launch {
                        try {
                            GuideTranslator.translate(gameId, g, target) { p ->
                                val s = when (p.stage) {
                                    "detect" -> L("Detecting language…", "Dil algılanıyor…")
                                    "model" -> L("Downloading language model…", "Dil modeli indiriliyor…")
                                    "translate" -> L("Translating ${p.done}/${p.total}", "Çevriliyor ${p.done}/${p.total}")
                                    else -> L("Saving…", "Kaydediliyor…")
                                }
                                scope.launch { translating = s }
                            }
                            Toast.makeText(ctx, L("Translated guide added", "Çevrilen rehber eklendi"), Toast.LENGTH_SHORT).show()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                        } catch (e: Exception) {
                            Toast.makeText(ctx, L("Translation failed: ", "Çeviri başarısız: ") + e.message, Toast.LENGTH_LONG).show()
                        }
                        translating = null
                        translateGuide = null
                    }
                }) { Text(L("Translate", "Çevir")) }
            },
            dismissButton = {
                TextButton(onClick = { translateJob?.cancel(); translating = null; translateGuide = null }) { Text(L("Cancel", "İptal")) }
            }
        )
    }
}

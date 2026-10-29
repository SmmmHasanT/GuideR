package com.hasantasdelen.guider.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.data.ReaderState
import com.hasantasdelen.guider.ui.Themes
import com.hasantasdelen.guider.util.L

@Composable
fun KeepScreenOn(enabled: Boolean = Prefs.keepScreenOn) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "mono" -> FontFamily.Monospace
    "serif" -> FontFamily.Serif
    "sans" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

@Composable
fun SearchRow(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    index: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(L("Search in guide", "Rehberde ara")) },
            modifier = Modifier.weight(1f).focusRequester(fr),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )
        Text(if (count == 0) "0/0" else "${index + 1}/$count", modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, null) }
        IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, null) }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, null) }
    }
}

@Composable
fun TocDialog(entries: List<TocEntry>, currentBlock: Int, onPick: (TocEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Close", "Kapat")) } },
        title = { Text(L("Table of Contents", "İçindekiler")) },
        text = {
            if (entries.isEmpty()) {
                Text(L("No sections detected in this guide.", "Bu rehberde bölüm bulunamadı."))
            } else {
                val curIdx = entries.indexOfLast { it.block <= currentBlock }.coerceAtLeast(0)
                val state = rememberLazyListState(initialFirstVisibleItemIndex = (curIdx - 3).coerceAtLeast(0))
                LazyColumn(state = state, modifier = Modifier.heightIn(max = 480.dp)) {
                    itemsIndexed(entries) { i, e ->
                        Text(
                            e.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (i == curIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(e) }
                                .padding(start = (e.level * 16).dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
    state: ReaderState,
    isTxt: Boolean,
    onChange: (ReaderState) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(L("Reading Settings", "Okuma Ayarları"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(L("Font Size", "Yazı Boyutu") + ": ${state.fontSize} sp")
            Slider(
                value = state.fontSize.toFloat(),
                onValueChange = { onChange(state.copy(fontSize = it.toInt())) },
                valueRange = 10f..36f,
                steps = 25
            )
            if (isTxt) {
                Text(L("Font", "Yazı Tipi"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("mono" to L("Monospace", "Sabit"), "sans" to "Sans", "serif" to "Serif").forEach { (k, n) ->
                        RadioButton(selected = state.font == k || (k == "mono" && state.font == "default" && state.monospace), onClick = {
                            onChange(state.copy(font = k, monospace = k == "mono"))
                        })
                        Text(n, Modifier.padding(end = 12.dp))
                    }
                }
                SwitchRow(L("Smart Text Formatting", "Akıllı Metin Biçimlendirme"), state.smartFormat) { onChange(state.copy(smartFormat = it)) }
                SwitchRow(L("Line Wrap", "Satır Kaydırma"), state.lineWrap) { onChange(state.copy(lineWrap = it)) }
                SwitchRow(L("Justified Text", "İki Yana Yasla"), state.justify) { onChange(state.copy(justify = it)) }
            } else {
                SwitchRow(L("Apply theme colors to HTML", "Tema renklerini HTML'e uygula"), Prefs.themeHtml) { Prefs.themeHtml = it }
            }
            Spacer(Modifier.height(12.dp))
            Text(L("Theme", "Tema"))
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Themes) { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { Prefs.theme = t.id }) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(t.bg)
                                .border(if (Prefs.theme == t.id) 3.dp else 1.dp, t.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("Aa", color = t.text) }
                        Text(t.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("Back", "Geri")) } },
        actions = actions,
    )
}

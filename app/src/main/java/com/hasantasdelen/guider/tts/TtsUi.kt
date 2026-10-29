package com.hasantasdelen.guider.tts

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.hasantasdelen.guider.data.Prefs
import com.hasantasdelen.guider.util.L
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Detects the dominant language of a text sample with ML Kit (on-device). Falls back to "en". */
suspend fun detectLanguage(sample: String): String {
    if (sample.isBlank()) return "en"
    return try {
        val id = LanguageIdentification.getClient()
        val tag = id.identifyLanguage(sample.take(4000)).await()
        id.close()
        if (tag == "und") "en" else tag
    } catch (e: Exception) {
        "en"
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    return speaker
}

/** Bottom player bar shown in every reader. */
@Composable
fun TtsBar(
    speaker: Speaker,
    showCaption: Boolean,
    onClose: () -> Unit,
    onStartFromVisible: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val err = speaker.error
            if (err != null && err.startsWith("missing_lang:")) {
                val lang = err.removePrefix("missing_lang:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L("No voice installed for \"$lang\".", "\"$lang\" dili için ses yüklü değil."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    }) { Text(L("Install", "Yükle")) }
                }
            }
            if (showCaption) {
                val seg = speaker.segments.getOrNull(speaker.current)
                if (seg != null) {
                    Text(
                        seg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { speaker.prev() }) { Icon(Icons.Filled.SkipPrevious, L("Previous", "Önceki")) }
                FilledIconButton(onClick = {
                    if (speaker.playing) speaker.pause()
                    else if (speaker.current < 0) onStartFromVisible() else speaker.play()
                }) {
                    Icon(if (speaker.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, L("Play/Pause", "Oynat/Duraklat"))
                }
                IconButton(onClick = { speaker.next() }) { Icon(Icons.Filled.SkipNext, L("Next", "Sonraki")) }
                IconButton(onClick = { onStartFromVisible() }) {
                    Icon(Icons.Filled.MyLocation, L("Read from screen", "Ekrandan oku"))
                }
                Spacer(Modifier.weight(1f))
                val total = speaker.segments.size
                if (total > 0) {
                    Text(
                        "${(speaker.current + 1).coerceAtLeast(0)}/$total",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = {
                    val steps = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)
                    val i = steps.indexOfFirst { it >= Prefs.ttsRate - 0.01f }
                    Prefs.ttsRate = steps[(i + 1) % steps.size]
                    if (speaker.playing) speaker.play(speaker.current)
                }) { Text(String.format(Locale.US, "%.2fx", Prefs.ttsRate)) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Tune, L("Voice settings", "Ses ayarları")) }
                IconButton(onClick = { speaker.stop(); onClose() }) { Icon(Icons.Filled.Close, L("Close", "Kapat")) }
            }
        }
    }
    if (showSettings) {
        TtsSettingsDialog(speaker) { showSettings = false }
    }
}

val TtsLanguages = listOf(
    "auto" to "Otomatik / Auto",
    "tr" to "Türkçe", "en-US" to "English (US)", "en-GB" to "English (UK)", "es" to "Español",
    "pt" to "Português", "de" to "Deutsch", "fr" to "Français", "it" to "Italiano",
    "pl" to "Polski", "ja" to "日本語", "ru" to "Русский",
)

@Composable
fun TtsSettingsDialog(speaker: Speaker, onDismiss: () -> Unit) {
    var rate by remember { mutableFloatStateOf(Prefs.ttsRate) }
    var pitch by remember { mutableFloatStateOf(Prefs.ttsPitch) }
    var lang by remember { mutableStateOf(Prefs.ttsLanguage) }
    var voice by remember { mutableStateOf(Prefs.ttsVoice) }
    var langMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                Prefs.ttsRate = rate; Prefs.ttsPitch = pitch; Prefs.ttsLanguage = lang; Prefs.ttsVoice = voice
                speaker.applySettings()
                if (speaker.playing) speaker.play(speaker.current)
                onDismiss()
            }) { Text(L("Apply", "Uygula")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel", "İptal")) } },
        title = { Text(L("Voice settings", "Ses ayarları")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(L("Speed", "Hız") + String.format(Locale.US, ": %.2fx", rate))
                Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.5f..3f)
                Text(L("Pitch", "Ton") + String.format(Locale.US, ": %.2f", pitch))
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.5f..2f)
                Spacer(Modifier.height(8.dp))
                Text(L("Language", "Dil"), style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { langMenu = true }) {
                        Text(TtsLanguages.firstOrNull { it.first == lang }?.second ?: lang)
                    }
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        TtsLanguages.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { lang = code; voice = ""; langMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(L("Voice", "Ses"), style = MaterialTheme.typography.labelLarge)
                val voices = remember(lang) {
                    val all = speaker.voices()
                    if (lang == "auto") all else all.filter { it.locale.language == Locale.forLanguageTag(lang).language }
                }
                Box {
                    OutlinedButton(onClick = { voiceMenu = true }) {
                        Text(voice.ifBlank { L("Default", "Varsayılan") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                        DropdownMenuItem(text = { Text(L("Default", "Varsayılan")) }, onClick = { voice = ""; voiceMenu = false })
                        voices.forEach { v ->
                            val net = if (v.isNetworkConnectionRequired) " ☁" else ""
                            DropdownMenuItem(
                                text = { Text("${v.locale.toLanguageTag()} · ${v.name}$net", maxLines = 1) },
                                onClick = { voice = v.name; voiceMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = Prefs.ttsWordHighlight, onCheckedChange = { Prefs.ttsWordHighlight = it })
                    Text(L("Highlight spoken word", "Okunan kelimeyi vurgula"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = Prefs.ttsAutoScroll, onCheckedChange = { Prefs.ttsAutoScroll = it })
                    Text(L("Auto scroll", "Otomatik kaydır"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = Prefs.ttsSkipSymbols, onCheckedChange = { Prefs.ttsSkipSymbols = it })
                    Text(L("Skip ASCII decorations (====, ----)", "ASCII süslerini atla (====, ----)"))
                }
            }
        }
    )
}

/** Small helper used by readers: a translucent highlight color. */
@Composable
fun ttsHighlightModifier(active: Boolean): Modifier =
    if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier

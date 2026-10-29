package com.hasantasdelen.guider.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hasantasdelen.guider.data.Prefs
import java.text.BreakIterator
import java.util.Locale

/** One spoken unit. [block] is the index of the paragraph / html block / pdf page it belongs to. */
data class Segment(val block: Int, val start: Int, val end: Int, val text: String)

/**
 * Wrapper around Android TextToSpeech that reads a list of [Segment]s one after another,
 * exposing the current segment and word range as Compose state so readers can highlight
 * and auto-scroll.
 */
class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val ctx = context.applicationContext

    var ready by mutableStateOf(false)

        private set
    var playing by mutableStateOf(false)
        private set
    var current by mutableIntStateOf(-1)
        private set
    var wordStart by mutableIntStateOf(-1)
        private set
    var wordEnd by mutableIntStateOf(-1)
        private set
    var error by mutableStateOf<String?>(null)

    var segments: List<Segment> = emptyList()

        private set
    private var generation = 0
    private var queuedUpTo = -1
    private var language: String = "en"
    private var pendingPlay: Int? = null

    private var tts: TextToSpeech? = null

    init {
        createEngine()
    }

    private fun createEngine() {
        val engine = Prefs.ttsEngine.ifBlank { null }
        ready = false
        tts = TextToSpeech(ctx, { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(listener)
                    applySettings()
                    pendingPlay?.let { pendingPlay = null; play(it) }
                } else {
                    error = "TTS init failed"
                }
            }
        }, engine)
    }

    fun restartEngine() {
        stop()
        tts?.shutdown()
        createEngine()
    }

    fun setLanguage(lang: String) {
        language = lang
        if (ready) applySettings()
    }

    fun applySettings() {
        val t = tts ?: return
        val langTag = if (Prefs.ttsLanguage == "auto") language else Prefs.ttsLanguage
        val loc = Locale.forLanguageTag(langTag)
        val res = t.setLanguage(loc)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            error = "missing_lang:$langTag"
        } else {
            error = null
        }
        val voiceName = Prefs.ttsVoice
        if (voiceName.isNotBlank()) {
            try {
                t.voices?.firstOrNull { it.name == voiceName && it.locale.language == loc.language }?.let { t.voice = it }
            } catch (_: Exception) {
            }
        }
        t.setSpeechRate(Prefs.ttsRate)
        t.setPitch(Prefs.ttsPitch)
    }

    fun voices(): List<Voice> = try {
        tts?.voices?.toList()?.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name })) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun engines(): List<TextToSpeech.EngineInfo> = try {
        tts?.engines ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun setSegments(list: List<Segment>) {
        stop()
        segments = list
        current = -1
    }

    fun play(from: Int = if (current >= 0) current else 0) {
        if (segments.isEmpty()) return
        if (!ready) {
            pendingPlay = from; return
        }
        val start = from.coerceIn(0, segments.size - 1)
        generation++
        applySettings()
        current = start
        wordStart = -1; wordEnd = -1
        playing = true
        queuedUpTo = start - 1
        tts?.stop()
        enqueue(start, TextToSpeech.QUEUE_FLUSH)
        if (start + 1 < segments.size) enqueue(start + 1, TextToSpeech.QUEUE_ADD)
    }

    private fun enqueue(index: Int, mode: Int) {
        val seg = segments[index]
        val params = Bundle()
        tts?.speak(seg.text, mode, params, "$generation:$index")
        queuedUpTo = maxOf(queuedUpTo, index)
    }

    fun pause() {
        generation++
        playing = false
        tts?.stop()
    }

    fun toggle() = if (playing) pause() else play()

    fun stop() {
        pause()
        wordStart = -1; wordEnd = -1
    }

    fun next() {
        if (segments.isEmpty()) return
        val n = (current + 1).coerceAtMost(segments.size - 1)
        if (playing) play(n) else { current = n }
    }

    fun prev() {
        if (segments.isEmpty()) return
        val n = (current - 1).coerceAtLeast(0)
        if (playing) play(n) else { current = n }
    }

    /** Jump to the first segment that belongs to [block] (and contains [charOffset] if given). */
    fun playFromBlock(block: Int, charOffset: Int = -1) {
        val idx = indexFor(block, charOffset)
        if (idx >= 0) play(idx)
    }

    fun indexFor(block: Int, charOffset: Int = -1): Int {
        var best = -1
        for ((i, s) in segments.withIndex()) {
            if (s.block == block) {
                if (charOffset < 0) return i
                if (best < 0) best = i
                if (charOffset >= s.start && charOffset < s.end) return i
                if (s.start <= charOffset) best = i
            } else if (s.block > block) {
                if (best < 0) best = i
                break
            }
        }
        return best
    }

    fun shutdown() {
        generation++
        try {
            tts?.stop(); tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun parse(id: String?): Pair<Int, Int>? {
        val p = id?.split(':') ?: return null
        if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val (gen, idx) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                current = idx
                wordStart = -1; wordEnd = -1
                val nxt = idx + 1
                if (nxt < segments.size && nxt > queuedUpTo) enqueue(nxt, TextToSpeech.QUEUE_ADD)
            }
        }

        override fun onDone(utteranceId: String?) {
            val (gen, idx) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation) return@post
                if (idx >= segments.size - 1) {
                    playing = false
                    wordStart = -1; wordEnd = -1
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { if (parse(utteranceId)?.first == generation) playing = false }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { if (parse(utteranceId)?.first == generation) playing = false }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val (gen, idx) = parse(utteranceId) ?: return
            main.post {
                if (gen != generation || idx != current) return@post
                wordStart = start; wordEnd = end
            }
        }
    }

    companion object {
        private val decor = Regex("[=\\-_*~#+.|<>/\\\\^]{3,}")
        private val hasWord = Regex("[\\p{L}\\p{N}]")

        /** Replace decoration/ASCII-art runs with spaces, keeping string length (so offsets still map). */
        fun speakable(text: String): String {
            val sb = StringBuilder(text)
            if (Prefs.ttsSkipSymbols) {
                decor.findAll(text).forEach { m -> for (i in m.range) sb.setCharAt(i, ' ') }
            }
            for (i in sb.indices) if (sb[i] == '\n' || sb[i] == '\r' || sb[i] == '\t') sb.setCharAt(i, ' ')
            return sb.toString()
        }

        /** Split one block of text into sentence segments. */
        fun segmentsFor(block: Int, text: String, lang: String): List<Segment> {
            val out = ArrayList<Segment>()
            val spoken = speakable(text)
            val it = BreakIterator.getSentenceInstance(Locale.forLanguageTag(lang))
            it.setText(spoken)
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                addPiece(out, block, spoken, start, end)
                start = end
                end = it.next()
            }
            return out
        }

        private fun addPiece(out: MutableList<Segment>, block: Int, spoken: String, s: Int, e: Int) {
            var a = s
            val max = 3000
            while (a < e) {
                var b = minOf(e, a + max)
                if (b < e) {
                    val sp = spoken.lastIndexOf(' ', b)
                    if (sp > a + 100) b = sp
                }
                val piece = spoken.substring(a, b)
                if (hasWord.containsMatchIn(piece)) {
                    // trim leading whitespace so the highlight starts on the first word
                    val lead = piece.length - piece.trimStart().length
                    val trail = piece.length - piece.trimEnd().length
                    out.add(Segment(block, a + lead, b - trail, piece.trim()))
                }
                a = b
            }
        }
    }
}

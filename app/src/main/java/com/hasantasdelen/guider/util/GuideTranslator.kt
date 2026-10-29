package com.hasantasdelen.guider.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.hasantasdelen.guider.data.Guide
import com.hasantasdelen.guider.data.GuideType
import com.hasantasdelen.guider.data.Library
import com.hasantasdelen.guider.reader.TxtDocument
import com.hasantasdelen.guider.reader.decodeText
import com.hasantasdelen.guider.tts.detectLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GuideTranslator {
    /** All ML Kit languages with a display name. */
    val languages: List<Pair<String, String>> by lazy {
        TranslateLanguage.getAllLanguages().map { code ->
            code to java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.forLanguageTag(code))
                .replaceFirstChar { it.uppercase() }
        }.sortedBy { it.second }
    }

    data class Progress(val stage: String, val done: Int, val total: Int)

    private val multiSpace = Regex("\\S {3,}\\S")

    /**
     * Translates a TXT guide on-device. Produces a new TXT guide next to the original.
     * Reflowable paragraphs are translated whole; preformatted lines are translated line by line
     * only if they look like sentences (tables / ASCII art are kept as-is).
     */
    suspend fun translate(gameId: String, guide: Guide, target: String, onProgress: (Progress) -> Unit): Guide =
        withContext(Dispatchers.IO) {
            val raw = decodeText(Library.guideFile(guide).readBytes())
            onProgress(Progress("detect", 0, 0))
            val srcTag = guide.language ?: detectLanguage(raw.take(6000))
            val source = TranslateLanguage.fromLanguageTag(srcTag) ?: TranslateLanguage.ENGLISH
            val tgt = TranslateLanguage.fromLanguageTag(target) ?: throw IllegalArgumentException("Unsupported: $target")
            if (source == tgt) throw IllegalArgumentException(L("Guide is already in this language.", "Rehber zaten bu dilde."))
            val translator = Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(tgt).build()
            )
            try {
                onProgress(Progress("model", 0, 0))
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                val blocks = TxtDocument.parse(raw, true)
                val out = StringBuilder()
                for ((i, b) in blocks.withIndex()) {
                    ensureActive()
                    onProgress(Progress("translate", i, blocks.size))
                    if (!b.pre) {
                        out.append(translator.translate(b.text).await())
                    } else {
                        val lines = b.text.split('\n')
                        for ((li, line) in lines.withIndex()) {
                            val t = line.trim()
                            val words = t.split(' ').count { w -> w.any { it.isLetter() } }
                            if (words >= 2 && !multiSpace.containsMatchIn(t) && !TxtDocument.isSeparator(t)) {
                                val indent = line.length - line.trimStart().length
                                out.append(" ".repeat(indent)).append(translator.translate(t).await())
                            } else {
                                out.append(line)
                            }
                            if (li < lines.size - 1) out.append('\n')
                        }
                    }
                    out.append("\n\n")
                }
                onProgress(Progress("save", blocks.size, blocks.size))
                Library.saveContentGuide(
                    gameId, "${guide.title} (${target.uppercase()})", GuideType.TXT, out.toString(),
                    sourceUrl = guide.sourceUrl, language = target
                )
            } finally {
                translator.close()
            }
        }
}

package com.hasantasdelen.guider.reader

/** A displayable paragraph of a TXT guide. [pre] blocks keep their line breaks and use monospace. */
data class TxtBlock(val text: String, val pre: Boolean, val firstLine: Int)

data class TocEntry(val title: String, val block: Int, val level: Int = 0)

object TxtDocument {

    private val sepChars = "=-*#~_+:.<>|/\\^"

    fun isSeparator(line: String): Boolean {
        val t = line.trim()
        if (t.length < 5) return false
        var symbols = 0
        for (c in t) {
            if (c == ' ') continue
            if (sepChars.indexOf(c) < 0) return false
            symbols++
        }
        return symbols >= 5
    }

    private val decorRun = Regex("[=\\-*#~_]{4,}")
    private val internalSpaces = Regex("\\S {3,}\\S")
    private val boxChars = Regex("[|+]{1}[-=]{2,}|[-=]{2,}[|+]|[│┃┌┐└┘├┤┬┴┼═║╔╗╚╝]")
    private val listStart = Regex("^\\s*([-*•o>]|\\d{1,3}[.)]|[a-zA-Z][.)])\\s+")

    /** Decide whether a paragraph looks like prose that can be safely reflowed. */
    private fun isProse(lines: List<String>): Boolean {
        if (lines.isEmpty()) return false
        if (lines.any { isSeparator(it) || decorRun.containsMatchIn(it) }) return false
        if (lines.any { boxChars.containsMatchIn(it) }) return false
        if (lines.any { internalSpaces.containsMatchIn(it.trimEnd()) }) return false
        if (lines.size == 1) return lines[0].trim().length > 50 || lines[0].trim().endsWith('.')
        // list items: keep as preformatted so bullets stay on their own lines
        if (lines.count { listStart.containsMatchIn(it) } >= 2) return false
        val longLines = lines.dropLast(1).count { it.trim().length >= 40 }
        return longLines >= (lines.size - 1) * 0.6
    }

    private fun reflow(lines: List<String>): String {
        val sb = StringBuilder()
        for (l in lines) {
            val t = l.trim()
            if (t.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(t)
            } else if (sb.endsWith("-") && sb.length > 1 && sb[sb.length - 2].isLetter() && t.firstOrNull()?.isLowerCase() == true) {
                sb.setLength(sb.length - 1); sb.append(t)
            } else {
                sb.append(' ').append(t)
            }
        }
        return sb.toString()
    }

    fun parse(raw: String, smart: Boolean): List<TxtBlock> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
        val lines = text.split('\n')
        val out = ArrayList<TxtBlock>()
        var i = 0
        while (i < lines.size) {
            // skip blank lines
            while (i < lines.size && lines[i].isBlank()) i++
            if (i >= lines.size) break
            val start = i
            val para = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                para.add(lines[i].trimEnd()); i++
            }
            if (smart && isProse(para)) {
                out.add(TxtBlock(reflow(para), false, start))
            } else {
                // very large preformatted chunks are split to keep the lazy list smooth
                para.chunked(40).forEachIndexed { ci, chunk ->
                    out.add(TxtBlock(chunk.joinToString("\n"), true, start + ci * 40))
                }
            }
        }
        return out
    }

    // ---------------- Table of contents detection ----------------

    private val codeTag = Regex("[\\[{(]([A-Za-z0-9]{2,3}[0-9]{1,4}|[A-Z]{2,6}\\d{0,3}|\\d{1,3}[A-Za-z]{1,3})[\\]})]")
    private val wrappedTitle = Regex("^\\s*[=\\-*#~_+]{3,}\\s*(.+?)\\s*[=\\-*#~_+]{3,}\\s*([\\[{(][A-Za-z0-9]{2,8}[\\]})])?\\s*$")
    private val numberedCaps = Regex("^\\s*(\\d{1,2}(\\.\\d{1,2}){0,2}\\.?|[IVX]{1,5}\\.)\\s+[A-Z0-9][A-Z0-9 '&:,!?/()\\-]{3,}$")
    private val leaders = Regex("\\.{3,}.*$|\\s+\\d+\\s*$")
    private val contentsHeader = Regex("^\\s*(table of contents|contents|index|i[cç]indekiler)\\s*:?\\s*$", RegexOption.IGNORE_CASE)

    private fun normalize(t: String) = t.lowercase()
        .replace(codeTag, " ")
        .replace(leaders, " ")
        .replace(Regex("^[\\s\\d.ivx]+(?=[a-z])"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun cleanTitle(t: String): String {
        var s = t.trim()
        wrappedTitle.find(s)?.let { s = it.groupValues[1] }
        s = s.replace(Regex("[=\\-*#~_+]{3,}"), " ").replace(Regex("\\.{3,}"), " ").replace(Regex("\\s{2,}"), " ").trim()
        s = s.trim('=', '-', '*', '#', '~', '_', '+', '|', ' ')
        return s
    }

    fun detectToc(raw: String, blocks: List<TxtBlock>): List<TocEntry> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        fun lineAt(i: Int) = if (i in lines.indices) lines[i] else ""

        data class Cand(val line: Int, val title: String, val code: String?, val strong: Boolean)

        val cands = ArrayList<Cand>()
        var inContents = false
        var contentsEndGuard = 0
        for (i in lines.indices) {
            val l = lines[i]
            val t = l.trim()
            if (t.isEmpty()) continue
            if (contentsHeader.matches(t)) { inContents = true; contentsEndGuard = 0; continue }
            if (t.length > 90 || !t.any { it.isLetter() }) continue
            val sepAround = isSeparator(lineAt(i - 1)) || isSeparator(lineAt(i + 1))
            val wrapped = wrappedTitle.matches(t) && wrappedTitle.find(t)!!.groupValues[1].any { it.isLetter() }
            val code = codeTag.find(t)?.value?.uppercase()
            val numbered = numberedCaps.matches(t)
            val isSep = isSeparator(t)
            if (isSep) {
                if (inContents) contentsEndGuard++
                if (contentsEndGuard >= 2) inContents = false
                continue
            }
            val strong = (sepAround && t.length <= 70) || wrapped
            if (strong || code != null && t.length <= 80 || numbered) {
                cands.add(Cand(i, cleanTitle(t), code, strong && !inContents))
            }
        }
        if (cands.isEmpty()) return emptyList()

        // Deduplicate: entries listed in the contents table appear again at the real section;
        // prefer the strong (separator-adjacent) or the last occurrence.
        val byKey = LinkedHashMap<String, Cand>()
        for (c in cands) {
            val key = c.code ?: normalize(c.title)
            if (key.isBlank()) continue
            val prev = byKey[key]
            if (prev == null) byKey[key] = c
            else if (!prev.strong || c.strong) byKey[key] = c
        }
        var chosen = byKey.values.sortedBy { it.line }
        // If too many weak entries, keep only strong ones
        if (chosen.count { it.strong } >= 4 && chosen.size > 200) chosen = chosen.filter { it.strong }

        // map line number to block index
        val starts = blocks.map { it.firstLine }
        fun blockFor(line: Int): Int {
            var lo = 0; var hi = starts.size - 1; var ans = 0
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                if (starts[mid] <= line) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            return ans
        }
        val result = ArrayList<TocEntry>()
        var lastBlock = -1
        for (c in chosen) {
            val b = blockFor(c.line)
            if (b == lastBlock && result.isNotEmpty()) continue
            val level = Regex("^\\s*\\d+\\.\\d+").find(c.title)?.let { 1 } ?: 0
            result.add(TocEntry(c.title.take(80), b, level))
            lastBlock = b
        }
        return result
    }
}

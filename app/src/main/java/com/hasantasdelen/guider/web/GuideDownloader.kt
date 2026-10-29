package com.hasantasdelen.guider.web

import android.util.Base64
import android.webkit.CookieManager
import com.hasantasdelen.guider.data.GuideType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

object AdBlock {
    private val hosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googletagservices.com", "adservice.google.com",
        "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "adnxs.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "scorecardresearch.com", "moatads.com",
        "quantserve.com", "2mdn.net", "adsafeprotected.com", "casalemedia.com", "sharethrough.com",
        "yieldmo.com", "teads.tv", "indexww.com", "smartadserver.com", "bidswitch.net", "gumgum.com",
        "media.net", "zemanta.com", "permutive.com", "adform.net", "adsrvr.org", "33across.com",
        "lijit.com", "sovrn.com", "triplelift.com", "contextweb.com", "advertising.com", "yahoo.com/ads",
        "imasdk.googleapis.com", "pagead2.googlesyndication.com", "securepubads.g.doubleclick.net",
        "connatix.com", "primis.tech", "vidazoo.com", "aniview.com", "springserve.com", "kargo.com",
        "nativo.com", "mgid.com", "revcontent.com", "adthrive.com", "ezoic.net", "ezojs.com",
        "id5-sync.com", "liadm.com", "rlcdn.com", "bluekai.com", "krxd.net", "exelator.com",
        "demdex.net", "tapad.com", "crwdcntrl.net", "agkn.com", "mathtag.com", "everesttech.net",
        "cdn.confiant-integrations.net", "confiant-integrations.net", "doubleverify.com", "admanmedia.com",
        "tremorhub.com", "spotxchange.com", "springserve.net", "adlightning.com", "btloader.com",
        "cmp.inmobi.com", "cdn.privacy-mgmt.com",
    )

    fun isAd(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return hosts.any { h == it || h.endsWith(".$it") }
    }
}

data class DownloadProgress(val pageDone: Int, val pageTotal: Int, val imageDone: Int, val imageTotal: Int)

data class DownloadedGuide(val title: String, val type: GuideType, val content: String)

enum class GuideSite { GAMEFAQS, NEOSEEKER }

object GuideDownloader {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gamefaqsGuide = Regex("https?://(www\\.)?gamefaqs\\.gamespot\\.com/[^/]+/[^/]+/faqs/(\\d+)")
    private val neoseekerGuide = Regex("https?://(www\\.)?neoseeker\\.com/[^/]+/faqs/(\\d+)")

    fun detect(url: String?): GuideSite? {
        if (url == null) return null
        if (gamefaqsGuide.containsMatchIn(url)) return GuideSite.GAMEFAQS
        if (neoseekerGuide.containsMatchIn(url)) return GuideSite.NEOSEEKER
        return null
    }

    private fun basePath(url: String): String? =
        (gamefaqsGuide.find(url) ?: neoseekerGuide.find(url))?.value?.substringAfter("://")?.substringAfter('/')?.let { "/$it" }

    private fun get(url: String, ua: String): ByteArray {
        val b = Request.Builder().url(url).header("User-Agent", ua)
        CookieManager.getInstance().getCookie(url)?.let { b.header("Cookie", it) }
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code} – $url")
            return r.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun getWithType(url: String, ua: String): Pair<ByteArray, String?> {
        val b = Request.Builder().url(url).header("User-Agent", ua)
        CookieManager.getInstance().getCookie(url)?.let { b.header("Cookie", it) }
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return (r.body?.bytes() ?: ByteArray(0)) to r.header("Content-Type")
        }
    }

    fun downloadBytes(url: String, ua: String): ByteArray = get(url, ua)

    private fun cleanTitle(doc: Document): String {
        val h1 = doc.selectFirst("h1.page-title, h1")?.text()?.trim()
        val t = doc.title().replace(Regex("\\s*[-|]\\s*(GameFAQs|Neoseeker).*$"), "").trim()
        return (if (!h1.isNullOrBlank() && h1.length < 140) h1 else t).ifBlank { "Guide" }
    }

    /** Returns the text of the largest preformatted block if the page looks like a TXT guide. */
    private fun findTxt(doc: Document): String? {
        val inFaq = doc.select("#faqtext pre, div.faqtext pre, #faqwrap pre")
        val pres = if (inFaq.isNotEmpty()) inFaq else doc.select("pre")
        if (pres.isEmpty()) return null
        val joined = if (inFaq.isNotEmpty()) inFaq.joinToString("\n") { it.wholeText() } else pres.maxByOrNull { it.wholeText().length }!!.wholeText()
        return if (joined.length > 1500) joined else null
    }

    private fun contentRoot(doc: Document): Element {
        val selectors = listOf("#faqwrap", "div.ffaq", "div.faqtext", "#faqtext", "div.guide_content", "div.guide-content", "article", "main", "#content", "#main")
        for (s in selectors) {
            val e = doc.selectFirst(s)
            if (e != null && e.text().length > 400) return e
        }
        // fallback: div with the most text and few links
        var best: Element = doc.body()
        var bestScore = 0.0
        for (d in doc.select("div, section")) {
            val len = d.text().length
            if (len < 400) continue
            val linkLen = d.select("a").sumOf { it.text().length }
            val density = linkLen.toDouble() / len
            if (density > 0.5) continue
            val score = len * (1 - density) - d.select("div").size * 5
            if (score > bestScore) { bestScore = score; best = d }
        }
        return best
    }

    private fun strip(root: Element) {
        root.select("script, style, noscript, iframe, form, button, input, select, ins, .ad, .ads, [id^=div-gpt], [class*=advert], [class*=sponsor], nav, footer, .ftoc_toggle").remove()
    }

    private fun stripUrl(u: String) = u.substringBefore('#')

    /**
     * Downloads the guide the WebView is showing. [pageHtml] is the live DOM from the WebView so
     * the first page doesn't need a network round trip.
     */
    suspend fun download(
        pageHtml: String,
        pageUrl: String,
        userAgent: String,
        whole: Boolean,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadedGuide = withContext(Dispatchers.IO) {
        val first = Jsoup.parse(pageHtml, pageUrl)
        val title = cleanTitle(first)
        val site = detect(pageUrl)

        if (site != null) {
            findTxt(first)?.let { txt ->
                onProgress(DownloadProgress(1, 1, 0, 0))
                return@withContext DownloadedGuide(title, GuideType.TXT, txt)
            }
        }

        // Collect pages of a multi-page HTML guide
        val pages = ArrayList<String>()
        pages.add(stripUrl(pageUrl))
        val base = basePath(pageUrl)
        if (site != null && base != null && !whole) {
            for (a in first.select("a[href]")) {
                val abs = stripUrl(a.absUrl("href"))
                if (abs.isBlank()) continue
                val path = try { java.net.URI(abs).path ?: "" } catch (e: Exception) { "" }
                val q = try { java.net.URI(abs).query } catch (e: Exception) { null }
                val samePrefix = path == base || path.startsWith("$base/") || path.startsWith("$base-")
                val pageQuery = q == null || q.startsWith("page=")
                if (samePrefix && pageQuery && abs !in pages && pages.size < 400) pages.add(abs)
            }
        }
        // Main page first, remaining in document order
        val roots = ArrayList<Element>()
        for ((i, url) in pages.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(DownloadProgress(i, pages.size, 0, 0))
            val doc = if (i == 0) first else {
                try {
                    Jsoup.parse(String(get(url, userAgent), Charsets.UTF_8), url)
                } catch (e: Exception) {
                    if (i == 1) throw e else continue
                }
            }
            val root = if (whole) doc.body() else contentRoot(doc)
            strip(root)
            root.attr("data-gr-page", i.toString())
            roots.add(root)
        }
        onProgress(DownloadProgress(pages.size, pages.size, 0, 0))

        // rewrite links between pages to local anchors
        val pageIndex = pages.withIndex().associate { it.value to it.index }
        for (r in roots) {
            for (a in r.select("a[href]")) {
                val abs = a.absUrl("href")
                val idx = pageIndex[stripUrl(abs)]
                if (idx != null) {
                    val frag = abs.substringAfter('#', "")
                    a.attr("href", if (frag.isNotBlank()) "#$frag" else "#gr-p$idx")
                } else if (abs.isNotBlank()) {
                    a.attr("href", abs)
                }
            }
        }

        // inline images
        val imgs = roots.flatMap { it.select("img") }
        val cache = HashMap<String, String?>()
        for ((i, img) in imgs.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(DownloadProgress(pages.size, pages.size, i, imgs.size))
            val src = listOf("data-src", "data-original", "data-lazy-src", "src").map { img.absUrl(it) }.firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            img.removeAttr("srcset"); img.removeAttr("loading"); img.removeAttr("data-src")
            if (src == null) continue
            val dataUri = cache.getOrPut(src) {
                try {
                    val (bytes, ct) = getWithType(src, userAgent)
                    if (bytes.size > 8_000_000) null
                    else "data:${ct?.substringBefore(';') ?: "image/png"};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                } catch (e: Exception) {
                    null
                }
            }
            if (dataUri != null) img.attr("src", dataUri) else img.attr("src", src)
        }
        onProgress(DownloadProgress(pages.size, pages.size, imgs.size, imgs.size))

        val body = StringBuilder()
        for ((i, r) in roots.withIndex()) {
            body.append("<section id=\"gr-p").append(i).append("\">\n")
            body.append(r.html())
            body.append("\n</section>\n")
        }
        val safeTitle = org.jsoup.nodes.Entities.escape(title)
        val html = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>$safeTitle</title>
<style>body{font-family:sans-serif;line-height:1.55;padding:10px;margin:0 auto;max-width:1000px}img{max-width:100%;height:auto}table{border-collapse:collapse}td,th{border:1px solid #8884;padding:4px}pre{white-space:pre-wrap}</style>
</head><body>
<h1>$safeTitle</h1>
$body
<p style="opacity:.6;font-size:smaller">Source: ${org.jsoup.nodes.Entities.escape(pageUrl)}</p>
</body></html>"""
        DownloadedGuide(title, GuideType.HTML, html)
    }
}

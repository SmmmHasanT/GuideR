package com.hasantasdelen.guider.web

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.text.Normalizer

/**
 * Box art from the libretro thumbnail archive (used by RetroArch).
 * Covers nearly every retro console game, including Japan-only releases. No API key needed.
 */
object Libretro {
    data class Console(val system: String, val short: String)

    val consoles = listOf(
        Console("Nintendo - Super Nintendo Entertainment System", "SNES / Super Famicom"),
        Console("Nintendo - Nintendo Entertainment System", "NES / Famicom"),
        Console("Nintendo - Family Computer Disk System", "Famicom Disk System"),
        Console("Nintendo - Nintendo 64", "Nintendo 64"),
        Console("Nintendo - Game Boy", "Game Boy"),
        Console("Nintendo - Game Boy Color", "Game Boy Color"),
        Console("Nintendo - Game Boy Advance", "Game Boy Advance"),
        Console("Nintendo - Nintendo DS", "Nintendo DS"),
        Console("Nintendo - Nintendo 3DS", "Nintendo 3DS"),
        Console("Nintendo - GameCube", "GameCube"),
        Console("Nintendo - Wii", "Wii"),
        Console("Nintendo - Virtual Boy", "Virtual Boy"),
        Console("Sega - Master System - Mark III", "Master System"),
        Console("Sega - Mega Drive - Genesis", "Mega Drive / Genesis"),
        Console("Sega - Mega-CD - Sega CD", "Mega-CD / Sega CD"),
        Console("Sega - 32X", "32X"),
        Console("Sega - Saturn", "Saturn"),
        Console("Sega - Dreamcast", "Dreamcast"),
        Console("Sega - Game Gear", "Game Gear"),
        Console("Sony - PlayStation", "PlayStation"),
        Console("Sony - PlayStation 2", "PlayStation 2"),
        Console("Sony - PlayStation Portable", "PSP"),
        Console("NEC - PC Engine - TurboGrafx 16", "PC Engine / TurboGrafx-16"),
        Console("NEC - PC Engine CD - TurboGrafx-CD", "PC Engine CD"),
        Console("SNK - Neo Geo", "Neo Geo"),
        Console("SNK - Neo Geo Pocket Color", "Neo Geo Pocket Color"),
        Console("Bandai - WonderSwan Color", "WonderSwan Color"),
        Console("Atari - 2600", "Atari 2600"),
        Console("FBNeo - Arcade Games", "Arcade"),
    )

    data class Hit(val console: Console, val fileName: String) {
        /** File name without extension, e.g. "Feda - The Emblem of Justice (Japan)" */
        val name: String get() = fileName.removeSuffix(".png")
        /** Clean title without region/revision tags. */
        val title: String get() = name.replace(Regex("\\s*\\([^)]*\\)"), "").replace(Regex("\\s*\\[[^]]*]"), "").trim()
        val tags: String get() = Regex("\\(([^)]*)\\)").findAll(name).joinToString(", ") { it.groupValues[1] }
        val url: String get() = "https://thumbnails.libretro.com/" + Uri.encode(console.system) + "/Named_Boxarts/" + Uri.encode(fileName)
        val fallbackUrl: String
            get() = "https://raw.githubusercontent.com/libretro-thumbnails/" + console.system.replace(' ', '_') +
                "/master/Named_Boxarts/" + Uri.encode(fileName)
    }

    private val memory = HashMap<String, List<String>>()

    private fun cacheFile(ctx: Context, c: Console) =
        File(File(ctx.filesDir, "libretro").apply { mkdirs() }, c.system.replace(Regex("[^A-Za-z0-9]+"), "_") + ".txt")

    private fun http(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "GuideR/1.0").build()
        GuideDownloader.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    /** Box art file names for a console. Cached on device for 30 days. */
    suspend fun index(ctx: Context, c: Console): List<String> = withContext(Dispatchers.IO) {
        memory[c.system]?.let { return@withContext it }
        val f = cacheFile(ctx, c)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 30L * 24 * 3600 * 1000
        if (fresh) {
            val l = f.readLines().filter { it.isNotBlank() }
            if (l.isNotEmpty()) { memory[c.system] = l; return@withContext l }
        }
        val names = (try {
            // 1) directory listing on the libretro server
            val html = http("https://thumbnails.libretro.com/" + Uri.encode(c.system) + "/Named_Boxarts/")
            Jsoup.parse(html).select("a[href]").map { Uri.decode(it.attr("href")) }
                .filter { it.endsWith(".png", true) && !it.contains('/') }
        } catch (e: Exception) {
            emptyList()
        }).ifEmpty {
            // 2) GitHub mirror
            val repo = c.system.replace(' ', '_')
            val root = JSONObject(http("https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/master"))
            val tree = root.getJSONArray("tree")
            val sha = (0 until tree.length()).map { tree.getJSONObject(it) }.first { it.optString("path") == "Named_Boxarts" }.getString("sha")
            val sub = JSONObject(http("https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/$sha")).getJSONArray("tree")
            (0 until sub.length()).map { sub.getJSONObject(it).optString("path") }.filter { it.endsWith(".png", true) }
        }.distinct().sorted()
        if (names.isNotEmpty()) {
            try { f.writeText(names.joinToString("\n")) } catch (_: Exception) {}
            memory[c.system] = names
        } else if (f.exists()) {
            return@withContext f.readLines().filter { it.isNotBlank() }
        }
        names
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    private val junk = Regex("\\((Beta|Proto|Sample|Demo|Kiosk|Pirate|Unl|Aftermarket|Hack)[^)]*\\)", RegexOption.IGNORE_CASE)

    suspend fun search(ctx: Context, c: Console, query: String): List<Hit> = withContext(Dispatchers.Default) {
        val words = norm(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        val all = index(ctx, c)
        val hits = all.filter { n -> val t = norm(n.removeSuffix(".png")); words.all { w -> t.contains(w) } }
        val clean = hits.filterNot { junk.containsMatchIn(it) }.ifEmpty { hits }
        val q = norm(query)
        clean.sortedWith(compareBy<String>(
            { if (norm(it.removeSuffix(".png")).startsWith(q)) 0 else 1 },
            { it.length },
        )).take(60).map { Hit(c, it) }
    }

    suspend fun download(hit: Hit): ByteArray? = withContext(Dispatchers.IO) {
        for (u in listOf(hit.url, hit.fallbackUrl)) {
            try {
                val req = Request.Builder().url(u).header("User-Agent", "GuideR/1.0").build()
                GuideDownloader.http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes()?.let { if (it.isNotEmpty()) return@withContext it }
                }
            } catch (_: Exception) {
            }
        }
        null
    }
}

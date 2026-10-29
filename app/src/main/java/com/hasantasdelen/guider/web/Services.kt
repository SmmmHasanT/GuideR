package com.hasantasdelen.guider.web

import com.hasantasdelen.guider.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val UA = "GuideR/1.0 (Android guide reader; contact: github.com/SmmmHasanT/GuideR)"

private fun getJson(url: String): JSONObject {
    val req = Request.Builder().url(url).header("User-Agent", UA).build()
    GuideDownloader.http.newCall(req).execute().use { r ->
        val body = r.body?.string() ?: "{}"
        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}: " + body.take(200))
        return JSONObject(body)
    }
}

// ---------------- Game metadata from Wikipedia / Wikidata (no API key needed) ----------------

data class GameHit(
    val title: String,
    val description: String,
    val thumbnail: String?,
    val wikidata: String?,
)

data class GameDetails(val year: String?, val platforms: String?, val coverBytes: ByteArray?)

object GameMeta {
    suspend fun search(query: String): List<GameHit> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("$query video game", "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
            "&gsrsearch=$q&gsrlimit=15&prop=pageimages%7Cdescription%7Cpageprops&piprop=thumbnail&pithumbsize=400" +
            "&pilicense=any&ppprop=wikibase_item"
        val pages = getJson(url).optJSONObject("query")?.optJSONArray("pages") ?: JSONArray()
        val list = (0 until pages.length()).map { pages.getJSONObject(it) }
            .sortedBy { it.optInt("index", 99) }
            .map { p ->
                GameHit(
                    title = p.optString("title"),
                    description = p.optString("description", ""),
                    thumbnail = p.optJSONObject("thumbnail")?.optString("source"),
                    wikidata = p.optJSONObject("pageprops")?.optString("wikibase_item"),
                )
            }
        // games first
        list.sortedByDescending { it.description.contains("game", ignoreCase = true) }
    }

    suspend fun details(hit: GameHit): GameDetails = withContext(Dispatchers.IO) {
        var year: String? = Regex("(19|20)\\d{2}").find(hit.description)?.value
        var platforms: String? = null
        try {
            val qid = hit.wikidata
            if (!qid.isNullOrBlank()) {
                val ent = getJson("https://www.wikidata.org/wiki/Special:EntityData/$qid.json")
                    .getJSONObject("entities").getJSONObject(qid)
                val claims = ent.optJSONObject("claims")
                val pubs = claims?.optJSONArray("P577")
                if (pubs != null && pubs.length() > 0) {
                    val times = (0 until pubs.length()).mapNotNull {
                        pubs.getJSONObject(it).optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time")
                    }
                    times.mapNotNull { Regex("\\+(\\d{4})").find(it)?.groupValues?.get(1) }.minOrNull()?.let { year = it }
                }
                val plats = claims?.optJSONArray("P400")
                if (plats != null && plats.length() > 0) {
                    val ids = (0 until plats.length()).mapNotNull {
                        plats.getJSONObject(it).optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")
                    }.distinct().take(25)
                    if (ids.isNotEmpty()) {
                        val lab = getJson(
                            "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=labels&languages=en&ids=" +
                                ids.joinToString("%7C")
                        ).optJSONObject("entities")
                        platforms = ids.mapNotNull { lab?.optJSONObject(it)?.optJSONObject("labels")?.optJSONObject("en")?.optString("value") }
                            .joinToString(", ")
                    }
                }
            }
        } catch (_: Exception) {
        }
        val cover = hit.thumbnail?.let {
            try {
                val req = Request.Builder().url(it).header("User-Agent", UA).build()
                GuideDownloader.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
            } catch (e: Exception) {
                null
            }
        }
        GameDetails(year, platforms?.ifBlank { null }, cover)
    }
}

// ---------------- Pixie AI (Gemini with Google Search grounding) ----------------

data class ChatMsg(val fromUser: Boolean, val text: String)

object Pixie {
    suspend fun ask(history: List<ChatMsg>, gameTitle: String?): String = withContext(Dispatchers.IO) {
        val key = Prefs.geminiKey.trim()
        require(key.isNotBlank()) { "API key missing" }
        val contents = JSONArray()
        for (m in history) {
            contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        val sys = buildString {
            append("You are Pixie, a concise video game guide assistant inside the GuideR app. ")
            append("Use web search to find accurate walkthrough info. Avoid unnecessary spoilers unless asked. ")
            append("Answer in the same language as the user's question. ")
            if (!gameTitle.isNullOrBlank()) append("The user is currently playing: $gameTitle.")
        }
        val body = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val model = Prefs.geminiModel.ifBlank { "gemini-2.5-flash" }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        GuideDownloader.http.newCall(req).execute().use { r ->
            val txt = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val msg = try { JSONObject(txt).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
                throw IllegalStateException(msg ?: "HTTP ${r.code}")
            }
            val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return@use "…"
            (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
        }
    }
}

// ---------------- RetroAchievements ----------------

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val badge: String,
    val earned: Boolean,
    val earnedHardcore: Boolean,
    val dateEarned: String?,
    val type: String?,
    val order: Int,
) {
    val badgeUrl get() = "https://media.retroachievements.org/Badge/$badge" + (if (earned) ".png" else "_lock.png")
}

data class RaGame(
    val title: String,
    val console: String,
    val icon: String?,
    val achievements: List<Achievement>,
    val numAwarded: Int,
    val completion: String,
)

object RetroAchievements {
    fun parseGameId(input: String): Int? =
        Regex("game/(\\d+)").find(input)?.groupValues?.get(1)?.toIntOrNull() ?: input.trim().toIntOrNull()

    suspend fun progress(gameId: Int): RaGame = withContext(Dispatchers.IO) {
        val u = URLEncoder.encode(Prefs.raUser.trim(), "UTF-8")
        val k = URLEncoder.encode(Prefs.raKey.trim(), "UTF-8")
        val o = getJson("https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?z=$u&y=$k&u=$u&g=$gameId")
        val achObj = o.optJSONObject("Achievements") ?: JSONObject()
        val list = ArrayList<Achievement>()
        for (key in achObj.keys()) {
            val a = achObj.getJSONObject(key)
            val de = a.optString("DateEarned", "").takeIf { it.isNotBlank() && it != "null" }
            val dh = a.optString("DateEarnedHardcore", "").takeIf { it.isNotBlank() && it != "null" }
            list.add(
                Achievement(
                    id = a.optInt("ID"),
                    title = a.optString("Title"),
                    description = a.optString("Description"),
                    points = a.optInt("Points"),
                    badge = a.optString("BadgeName"),
                    earned = de != null || dh != null,
                    earnedHardcore = dh != null,
                    dateEarned = dh ?: de,
                    type = a.optString("type", "").takeIf { it.isNotBlank() && it != "null" },
                    order = a.optInt("DisplayOrder"),
                )
            )
        }
        RaGame(
            title = o.optString("Title"),
            console = o.optString("ConsoleName"),
            icon = o.optString("ImageIcon").takeIf { it.isNotBlank() }?.let { "https://media.retroachievements.org$it" },
            achievements = list,
            numAwarded = o.optInt("NumAwardedToUser"),
            completion = o.optString("UserCompletion"),
        )
    }
}

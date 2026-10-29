package com.hasantasdelen.guider.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hasantasdelen.guider.util.newId
import com.hasantasdelen.guider.util.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

object Library {
    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    val guidesDir: File get() = File(appContext.filesDir, "guides").apply { mkdirs() }
    val coversDir: File get() = File(appContext.filesDir, "covers").apply { mkdirs() }
    private val libFile: File get() = File(appContext.filesDir, "library.json")
    private val stateDir: File get() = File(appContext.filesDir, "state").apply { mkdirs() }

    fun init(context: Context) {
        appContext = context.applicationContext
        _games.value = try {
            if (libFile.exists()) json.decodeFromString<LibraryData>(libFile.readText()).games else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun persist(list: List<Game>) {
        _games.value = list
        val tmp = File(libFile.parentFile, "library.json.tmp")
        tmp.writeText(json.encodeToString(LibraryData(list)))
        tmp.renameTo(libFile)
    }

    fun game(id: String): Game? = _games.value.firstOrNull { it.id == id }

    fun guide(gameId: String, guideId: String): Guide? = game(gameId)?.guides?.firstOrNull { it.id == guideId }

    fun guideFile(guide: Guide): File = File(guidesDir, guide.path)

    fun addGame(game: Game) = persist(_games.value + game)

    fun updateGame(id: String, transform: (Game) -> Game) =
        persist(_games.value.map { if (it.id == id) transform(it) else it })

    fun deleteGame(id: String) {
        game(id)?.let { g ->
            File(guidesDir, g.id).deleteRecursively()
            g.coverPath?.let { File(it).delete() }
        }
        persist(_games.value.filterNot { it.id == id })
    }

    fun addGuide(gameId: String, guide: Guide) = updateGame(gameId) { it.copy(guides = it.guides + guide) }

    fun updateGuide(gameId: String, guideId: String, transform: (Guide) -> Guide) =
        updateGame(gameId) { g -> g.copy(guides = g.guides.map { if (it.id == guideId) transform(it) else it }) }

    fun deleteGuide(gameId: String, guideId: String) {
        guide(gameId, guideId)?.let { gd ->
            if (gd.type != GuideType.URL) {
                val f = guideFile(gd)
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
            File(stateDir, "${gd.id}.json").delete()
        }
        updateGame(gameId) { g -> g.copy(guides = g.guides.filterNot { it.id == guideId }) }
    }

    fun gameStorageBytes(game: Game): Long =
        File(guidesDir, game.id).takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    // ---------------- reader state ----------------
    fun readerState(guideId: String): ReaderState {
        val f = File(stateDir, "$guideId.json")
        return try {
            if (f.exists()) json.decodeFromString<ReaderState>(f.readText()) else ReaderState(fontSize = Prefs.defaultFontSize)
        } catch (e: Exception) {
            ReaderState()
        }
    }

    fun saveReaderState(guideId: String, state: ReaderState) {
        try {
            File(stateDir, "$guideId.json").writeText(json.encodeToString(state))
        } catch (_: Exception) {
        }
    }

    // ---------------- importing ----------------
    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (_: Exception) {
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "guide"
    }

    fun typeForName(name: String): GuideType? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "text", "faq", "md", "nfo" -> GuideType.TXT
            "html", "htm", "xhtml", "mht" -> GuideType.HTML
            "pdf" -> GuideType.PDF
            "png", "jpg", "jpeg", "gif", "webp", "bmp" -> GuideType.IMAGE
            "cbz", "zip" -> GuideType.CBZ
            else -> null
        }
    }

    /** Copies a picked document into app storage and registers it under the game. */
    suspend fun importUri(context: Context, gameId: String, uri: Uri): Guide? = withContext(Dispatchers.IO) {
        val name = displayName(context, uri)
        val type = typeForName(name) ?: GuideType.TXT
        val id = newId()
        val dir = File(guidesDir, gameId).apply { mkdirs() }
        val title = name.substringBeforeLast('.')
        val relPath: String
        val size: Long
        if (type == GuideType.CBZ) {
            val out = File(dir, id).apply { mkdirs() }
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    var idx = 0
                    while (entry != null) {
                        val en = entry.name
                        if (!entry.isDirectory && typeForName(en) == GuideType.IMAGE) {
                            val safe = String.format("%05d_", idx++) + safeFileName(en.substringAfterLast('/'))
                            File(out, safe).outputStream().use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext null
            relPath = "$gameId/$id"
            size = out.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            val ext = name.substringAfterLast('.', "txt").lowercase()
            val f = File(dir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            relPath = "$gameId/${f.name}"
            size = f.length()
        }
        val guide = Guide(id = id, title = title, type = type, path = relPath, sizeBytes = size)
        addGuide(gameId, guide)
        guide
    }

    /** Stores raw text/html content as a new guide. */
    fun saveContentGuide(gameId: String, title: String, type: GuideType, content: String, sourceUrl: String? = null, language: String? = null): Guide {
        val id = newId()
        val dir = File(guidesDir, gameId).apply { mkdirs() }
        val ext = if (type == GuideType.HTML) "html" else "txt"
        val f = File(dir, "$id.$ext")
        f.writeText(content)
        val guide = Guide(id, title, type, "$gameId/${f.name}", f.length(), sourceUrl = sourceUrl, language = language)
        addGuide(gameId, guide)
        return guide
    }

    fun saveBytesGuide(gameId: String, title: String, type: GuideType, ext: String, bytes: ByteArray, sourceUrl: String? = null): Guide {
        val id = newId()
        val dir = File(guidesDir, gameId).apply { mkdirs() }
        val f = File(dir, "$id.$ext")
        f.writeBytes(bytes)
        val guide = Guide(id, title, type, "$gameId/${f.name}", f.length(), sourceUrl = sourceUrl)
        addGuide(gameId, guide)
        return guide
    }

    fun addShortcut(gameId: String, title: String, url: String): Guide {
        val guide = Guide(newId(), title, GuideType.URL, url)
        addGuide(gameId, guide)
        return guide
    }

    suspend fun copyCover(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val f = File(coversDir, newId() + ".img")
            context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun saveCoverBytes(bytes: ByteArray): String {
        val f = File(coversDir, newId() + ".img")
        f.writeBytes(bytes)
        return f.absolutePath
    }
}

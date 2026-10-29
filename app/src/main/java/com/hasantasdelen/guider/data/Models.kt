package com.hasantasdelen.guider.data

import kotlinx.serialization.Serializable

@Serializable
enum class GuideType { TXT, HTML, PDF, IMAGE, CBZ, URL }

@Serializable
data class Guide(
    val id: String,
    val title: String,
    val type: GuideType,
    /** Relative path inside the app's guides directory, or a URL for [GuideType.URL]. */
    val path: String,
    val sizeBytes: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val sourceUrl: String? = null,
    val language: String? = null,
)

@Serializable
data class Game(
    val id: String,
    val title: String,
    val coverPath: String? = null,
    val coverUrl: String? = null,
    val year: String? = null,
    val platforms: String? = null,
    val raGameId: Int? = null,
    val custom: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val guides: List<Guide> = emptyList(),
)

@Serializable
data class LibraryData(val games: List<Game> = emptyList())

/** Per-guide reader state (position + display options). */
@Serializable
data class ReaderState(
    val position: Int = 0,
    val offset: Int = 0,
    val fontSize: Int = 16,
    val monospace: Boolean = true,
    val lineWrap: Boolean = true,
    val smartFormat: Boolean = true,
    val justify: Boolean = false,
    val font: String = "default",
)

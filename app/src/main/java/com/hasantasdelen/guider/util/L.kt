package com.hasantasdelen.guider.util

import java.util.Locale

/**
 * Tiny two-language helper (Turkish / English).
 * Usage: L("Settings", "Ayarlar")
 */
object Lang {
    @Volatile
    var isTr: Boolean = Locale.getDefault().language == "tr"
}

fun L(en: String, tr: String): String = if (Lang.isTr) tr else en

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)

fun safeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60).ifBlank { "file" }

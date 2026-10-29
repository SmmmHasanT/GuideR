package com.hasantasdelen.guider.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.hasantasdelen.guider.data.Prefs

data class AppTheme(
    val id: String,
    val name: String,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val accent: Color,
    val dark: Boolean,
    val highlight: Color,
) {
    fun cssBg() = hex(bg)
    fun cssText() = hex(text)
    fun cssAccent() = hex(accent)
    fun cssHighlight() = hex(highlight)
    private fun hex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
}

val Themes = listOf(
    AppTheme("oled", "OLED Black", Color(0xFF000000), Color(0xFF1C1B22), Color(0xFFE6E1E5), Color(0xFFD0BCFF), true, Color(0xFF5B4A8C)),
    AppTheme("paper", "Paperwhite", Color(0xFFF7F3EA), Color(0xFFECE6D8), Color(0xFF2B2620), Color(0xFF7A5C2E), false, Color(0xFFFFE08A)),
    AppTheme("matrix", "Matrix", Color(0xFF000A00), Color(0xFF0B1F0B), Color(0xFF33FF66), Color(0xFF00CC44), true, Color(0xFF0E5A20)),
    AppTheme("midnight", "Midnight", Color(0xFF0D1321), Color(0xFF1D2D44), Color(0xFFD8E2F0), Color(0xFF7FB3FF), true, Color(0xFF2E4A7A)),
    AppTheme("plum", "Plum", Color(0xFF1E1020), Color(0xFF2E1A31), Color(0xFFF0DDF0), Color(0xFFE29BE8), true, Color(0xFF6A3570)),
    AppTheme("dune_night", "Dune Night", Color(0xFF1A140D), Color(0xFF2A2116), Color(0xFFEBD9B4), Color(0xFFE0A458), true, Color(0xFF6B4B22)),
    AppTheme("dune_day", "Dune Day", Color(0xFFF2E3C6), Color(0xFFE6D2AC), Color(0xFF3A2A16), Color(0xFFB0662A), false, Color(0xFFF7C77A)),
    AppTheme("cyber", "Cyberguide", Color(0xFF0A0420), Color(0xFF1A0E3A), Color(0xFF9EF6FF), Color(0xFFFF3EA5), true, Color(0xFF55207A)),
    AppTheme("pumpkin", "Pumpkin", Color(0xFF170D05), Color(0xFF2A1708), Color(0xFFFFE2C4), Color(0xFFFF8A1F), true, Color(0xFF7A3E0C)),
    AppTheme("velvet", "Velvet", Color(0xFF1A0508), Color(0xFF2E0D13), Color(0xFFF5D6DA), Color(0xFFE0485F), true, Color(0xFF6E1A27)),
    AppTheme("sage", "Sage", Color(0xFFE8EEE4), Color(0xFFD6E0D0), Color(0xFF1F2A1C), Color(0xFF4F7A45), false, Color(0xFFC4E0A8)),
    AppTheme("fuchsia", "Fuchsia", Color(0xFF16031A), Color(0xFF2A0832), Color(0xFFFFD9FB), Color(0xFFFF4FD8), true, Color(0xFF7A1A6E)),
    AppTheme("harbor", "Harbor", Color(0xFF0B1A1E), Color(0xFF15303A), Color(0xFFD5ECEF), Color(0xFF4FC3C9), true, Color(0xFF1F5A60)),
    AppTheme("rose", "Rose", Color(0xFFFBEFF1), Color(0xFFF3DDE2), Color(0xFF3A1E24), Color(0xFFC2456A), false, Color(0xFFF7B9CB)),
)

fun currentTheme(): AppTheme = Themes.firstOrNull { it.id == Prefs.theme } ?: Themes[0]

val LocalAppTheme = staticCompositionLocalOf { Themes[0] }

@Composable
fun GuideRTheme(content: @Composable () -> Unit) {
    val t = currentTheme()
    val scheme = if (t.dark) {
        darkColorScheme(
            primary = t.accent,
            onPrimary = t.bg,
            secondary = t.accent,
            background = t.bg,
            onBackground = t.text,
            surface = t.bg,
            onSurface = t.text,
            surfaceVariant = t.surface,
            onSurfaceVariant = t.text.copy(alpha = 0.75f),
            surfaceContainer = t.surface,
            surfaceContainerHigh = t.surface,
            surfaceContainerHighest = t.surface,
            surfaceContainerLow = t.surface,
            primaryContainer = t.highlight,
            onPrimaryContainer = t.text,
            secondaryContainer = t.highlight,
            onSecondaryContainer = t.text,
        )
    } else {
        lightColorScheme(
            primary = t.accent,
            onPrimary = Color.White,
            secondary = t.accent,
            background = t.bg,
            onBackground = t.text,
            surface = t.bg,
            onSurface = t.text,
            surfaceVariant = t.surface,
            onSurfaceVariant = t.text.copy(alpha = 0.75f),
            surfaceContainer = t.surface,
            surfaceContainerHigh = t.surface,
            surfaceContainerHighest = t.surface,
            surfaceContainerLow = t.surface,
            primaryContainer = t.highlight,
            onPrimaryContainer = t.text,
            secondaryContainer = t.highlight,
            onSecondaryContainer = t.text,
        )
    }
    CompositionLocalProvider(LocalAppTheme provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

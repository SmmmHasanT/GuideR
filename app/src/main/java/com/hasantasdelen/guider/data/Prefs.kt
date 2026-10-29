package com.hasantasdelen.guider.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** App-wide settings. Every property is Compose-observable and persisted. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("guider_prefs", Context.MODE_PRIVATE)
        loaded = true
        all.forEach { it.load() }
    }

    private var loaded = false
    private val all = mutableListOf<Pref<*>>()

    private abstract class Pref<T>(val key: String, val def: T) : ReadWriteProperty<Any?, T> {
        var state by mutableStateOf(def)
        init { Prefs.all.add(this) }
        abstract fun read(): T
        abstract fun write(v: T)
        fun load() { state = read() }
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            if (Prefs.loaded) write(value)
        }
    }

    private class BoolPref(key: String, def: Boolean) : Pref<Boolean>(key, def) {
        override fun read() = Prefs.sp.getBoolean(key, def)
        override fun write(v: Boolean) = Prefs.sp.edit().putBoolean(key, v).apply()
    }

    private class IntPref(key: String, def: Int) : Pref<Int>(key, def) {
        override fun read() = Prefs.sp.getInt(key, def)
        override fun write(v: Int) = Prefs.sp.edit().putInt(key, v).apply()
    }

    private class FloatPref(key: String, def: Float) : Pref<Float>(key, def) {
        override fun read() = Prefs.sp.getFloat(key, def)
        override fun write(v: Float) = Prefs.sp.edit().putFloat(key, v).apply()
    }

    private class StrPref(key: String, def: String) : Pref<String>(key, def) {
        override fun read() = Prefs.sp.getString(key, def) ?: def
        override fun write(v: String) = Prefs.sp.edit().putString(key, v).apply()
    }

    // Appearance
    var theme by StrPref("theme", "oled")
    var gridLayout by BoolPref("grid", true)
    var appLanguage by StrPref("app_lang", "system")
    var defaultFontSize by IntPref("def_font", 16)
    var themeHtml by BoolPref("theme_html", true)
    var keepScreenOn by BoolPref("keep_screen_on", true)

    // Controls
    var controllerEnabled by BoolPref("controller", true)
    var showTocBar by BoolPref("toc_bar", false)
    var scrollStep by IntPref("scroll_step", 180)

    // Text to speech
    var ttsRate by FloatPref("tts_rate", 1.0f)
    var ttsPitch by FloatPref("tts_pitch", 1.0f)
    var ttsLanguage by StrPref("tts_lang", "auto")
    var ttsVoice by StrPref("tts_voice", "")
    var ttsEngine by StrPref("tts_engine", "")
    var ttsWordHighlight by BoolPref("tts_word", true)
    var ttsAutoScroll by BoolPref("tts_scroll", true)
    var ttsSkipSymbols by BoolPref("tts_skip_symbols", true)

    // Web
    var adBlock by BoolPref("adblock", true)

    // Pixie AI (Gemini)
    var pixieEnabled by BoolPref("pixie", true)
    var geminiKey by StrPref("gemini_key", "")
    var geminiModel by StrPref("gemini_model", "gemini-2.5-flash")

    // RetroAchievements
    var raUser by StrPref("ra_user", "")
    var raKey by StrPref("ra_key", "")

    // Game metadata source: "" = Wikipedia, otherwise a libretro system name
    var coverConsole by StrPref("cover_console", "")

    // Translation
    var translateTarget by StrPref("translate_target", "tr")
}

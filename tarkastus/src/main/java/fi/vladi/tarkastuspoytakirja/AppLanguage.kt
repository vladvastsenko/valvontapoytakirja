package fi.vladi.tarkastuspoytakirja

import android.content.Context
import java.util.Locale

/** Device-local UI language, shared by both report activities. */
object AppLanguage {
    private const val PREFS = "tarkastus_cloud_language"
    private const val KEY = "ui_language"
    val options = listOf("en" to "English", "fi" to "Suomi", "sv" to "Svenska", "nb" to "Norsk", "da" to "Dansk", "et" to "Eesti", "lv" to "Latviešu", "lt" to "Lietuvių", "ru" to "Русский", "fr" to "Français", "es" to "Español", "it" to "Italiano", "de" to "Deutsch", "pl" to "Polski")
    val supported = options.map { it.first }.toSet()
    fun label(code: String): String = options.firstOrNull { it.first == code }?.second ?: "English"

    fun flaggedLabel(code: String): String = "${when (code) {
        "en" -> "🇬🇧"; "fi" -> "🇫🇮"; "sv" -> "🇸🇪"; "nb" -> "🇳🇴"
        "da" -> "🇩🇰"; "et" -> "🇪🇪"; "lv" -> "🇱🇻"; "lt" -> "🇱🇹"
        "ru" -> "🇷🇺"; "fr" -> "🇫🇷"; "es" -> "🇪🇸"; "it" -> "🇮🇹"
        "de" -> "🇩🇪"; "pl" -> "🇵🇱"; else -> "🌐"
    }} ${label(code)}"

    fun selected(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "en")?.takeIf { it in supported } ?: "en"

    fun hasSelection(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun save(context: Context, language: String) {
        require(language in supported)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
    }

    fun wrap(context: Context): Context {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(selected(context)))
        return context.createConfigurationContext(configuration)
    }
}

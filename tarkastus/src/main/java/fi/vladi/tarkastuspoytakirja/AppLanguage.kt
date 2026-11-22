package fi.vladi.tarkastuspoytakirja

import android.content.Context
import java.util.Locale

/** Device-local UI language, shared by both report activities. */
object AppLanguage {
    private const val PREFS = "tarkastus_cloud_language"
    private const val KEY = "ui_language"
    val supported = setOf("fi", "sv", "ru", "en", "et", "lt", "lv", "nb", "da")

    fun selected(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "fi")?.takeIf { it in supported } ?: "fi"

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

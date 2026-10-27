package dev.sxch.tvweb.data

import android.content.Context
import android.content.SharedPreferences

enum class SearchEngine(val label: String, val queryUrl: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
}

enum class UserAgentMode(val label: String, val value: String?) {
    TV("TV", "Mozilla/5.0 (Linux; Android 14; SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"),
    DESKTOP("Desktop", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"),
    MOBILE("Mobile", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"),
    SYSTEM("System", null),
}

data class Settings(
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val userAgent: UserAgentMode = UserAgentMode.TV,
    val blockAds: Boolean = true,
    val saveHistory: Boolean = true,
    val homepage: String = "",
)

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var settings: Settings
        get() = Settings(
            searchEngine = enumOf(prefs.getString(KEY_ENGINE, null), SearchEngine.DUCKDUCKGO),
            userAgent = enumOf(prefs.getString(KEY_UA, null), UserAgentMode.TV),
            blockAds = prefs.getBoolean(KEY_ADS, true),
            saveHistory = prefs.getBoolean(KEY_HISTORY, true),
            homepage = prefs.getString(KEY_HOME, "") ?: "",
        )
        set(value) {
            prefs.edit()
                .putString(KEY_ENGINE, value.searchEngine.name)
                .putString(KEY_UA, value.userAgent.name)
                .putBoolean(KEY_ADS, value.blockAds)
                .putBoolean(KEY_HISTORY, value.saveHistory)
                .putString(KEY_HOME, value.homepage)
                .apply()
        }

    fun update(change: (Settings) -> Settings) {
        settings = change(settings)
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_UA = "user_agent"
        const val KEY_ADS = "block_ads"
        const val KEY_HISTORY = "save_history"
        const val KEY_HOME = "homepage"
    }
}

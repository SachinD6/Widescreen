package dev.sxch.tvweb.browser

import android.content.Context
import android.net.Uri

data class DrmApp(val label: String, val packageName: String)

object DrmSites {

    private val map: Map<String, DrmApp> = mapOf(
        "netflix.com" to DrmApp("Netflix", "com.netflix.ninja"),
        "primevideo.com" to DrmApp("Prime Video", "com.amazon.amazonvideo.livingroom"),
        "amazon.com" to DrmApp("Prime Video", "com.amazon.amazonvideo.livingroom"),
        "disneyplus.com" to DrmApp("Disney+", "com.disney.disneyplus"),
        "max.com" to DrmApp("Max", "com.wbd.stream"),
        "hbomax.com" to DrmApp("Max", "com.wbd.stream"),
        "hulu.com" to DrmApp("Hulu", "com.hulu.plus"),
        "peacocktv.com" to DrmApp("Peacock", "com.peacocktv.peacockandroid"),
        "paramountplus.com" to DrmApp("Paramount+", "com.cbs.app"),
        "crunchyroll.com" to DrmApp("Crunchyroll", "com.crunchyroll.crunchyroid"),
        "appletv.apple.com" to DrmApp("Apple TV", "com.apple.atve.androidtv.appletv"),
    )

    fun appFor(url: String): DrmApp? {
        val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("").lowercase()
        if (host.isEmpty()) return null
        var candidate = host
        while (candidate.contains('.')) {
            map[candidate]?.let { return it }
            candidate = candidate.substringAfter('.')
        }
        return null
    }

    fun isInstalled(context: Context, app: DrmApp): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(app.packageName, 0)
            true
        }.getOrDefault(false)
}

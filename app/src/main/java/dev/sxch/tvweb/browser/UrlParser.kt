package dev.sxch.tvweb.browser

import dev.sxch.tvweb.data.SearchEngine
import java.net.URLEncoder

sealed interface NavTarget {
    data class Address(val url: String) : NavTarget
    data class Query(val query: String, val url: String) : NavTarget
}

object UrlParser {

    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val hostLike = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$")
    private val localHost = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?([/?#].*)?$")

    fun parse(raw: String, engine: SearchEngine): NavTarget {
        val input = raw.trim()
        if (input.isEmpty()) return NavTarget.Query("", engine.queryUrl.format(""))
        if (input.startsWith("about:")) return NavTarget.Address(input)
        if (scheme.containsMatchIn(input)) {
            return if (input.startsWith("http://") || input.startsWith("https://")) {
                NavTarget.Address(input)
            } else {
                NavTarget.Query(input, searchUrl(input, engine))
            }
        }
        if (hostLike.matches(input) || localHost.matches(input)) {
            return NavTarget.Address("https://$input")
        }
        return NavTarget.Query(input, searchUrl(input, engine))
    }

    private fun searchUrl(query: String, engine: SearchEngine): String =
        engine.queryUrl.format(URLEncoder.encode(query, "UTF-8"))
}

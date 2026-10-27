package dev.sxch.tvweb.browser

import android.content.Context

class Blocklist private constructor(private val domains: Array<String>) {

    val size: Int get() = domains.size

    fun isBlocked(host: String): Boolean {
        var candidate = host.lowercase().substringBefore(':').trimEnd('.')
        while (candidate.contains('.')) {
            if (domains.binarySearch(candidate) >= 0) return true
            candidate = candidate.substringAfter('.')
            if (!candidate.contains('.')) return false
        }
        return false
    }

    companion object {
        private const val ASSET = "blocklist.txt"

        fun load(context: Context): Blocklist =
            context.assets.open(ASSET).bufferedReader().useLines { lines ->
                Blocklist(
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && it.contains('.') }
                        .toList()
                        .sorted()
                        .toTypedArray()
                )
            }

        val EMPTY = Blocklist(emptyArray())
    }
}

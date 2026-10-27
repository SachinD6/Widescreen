package dev.sxch.tvweb.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap

object Downloader {

    fun enqueue(
        context: Context,
        url: String,
        userAgent: String,
        mime: String,
        fileName: String,
    ): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val name = fileName.ifBlank { nameFromUrl(url, mime) }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("TV Web download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        if (userAgent.isNotBlank()) request.addRequestHeader("User-Agent", userAgent)
        if (mime.isNotBlank()) request.setMimeType(mime)
        return runCatching {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            true
        }.getOrDefault(false)
    }

    fun nameFromUrl(url: String, mime: String): String {
        val path = runCatching { Uri.parse(url).path ?: "" }.getOrDefault("")
        val candidate = path.substringAfterLast('/').substringBefore('?')
        if (candidate.isNotBlank() && candidate.contains('.')) return candidate
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        return "download-${System.currentTimeMillis()}.$ext"
    }

    fun nameFromDisposition(disposition: String): String {
        val marker = "filename="
        val index = disposition.indexOf(marker, ignoreCase = true)
        if (index < 0) return ""
        return disposition.substring(index + marker.length)
            .trim()
            .trim('"')
            .substringBefore(';')
            .substringAfterLast('/')
    }
}

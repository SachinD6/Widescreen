package dev.sxch.tvweb.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sxch.tvweb.BuildConfig
import dev.sxch.tvweb.data.Settings
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

data class Tab(
    val id: Long,
    val url: String,
    val title: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val progress: Int,
    val loading: Boolean,
)

interface BrowserHost {
    fun onTabStateChanged()
    fun onBackRequested()
    fun onVisit(url: String, title: String)
    fun onBlocked(host: String)
    fun onLoadError(message: String)
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
    fun onDownloadRequest(url: String, userAgent: String, mime: String, name: String)
    fun onMenuRequested()
}

@SuppressLint("SetJavaScriptEnabled")
class BrowserSession(
    private val context: Context,
    private val settings: () -> Settings,
    private val blocklist: () -> Blocklist,
    private val host: BrowserHost,
) {

    private val views = LinkedHashMap<Long, TvWebView>()
    private var nextId = 1L
    private var activeId = 0L
    private val blocked = AtomicInteger(0)
    val blockedCount: Int get() = blocked.get()
    val tabCount: Int get() = views.size

    fun openTab(url: String, activate: Boolean = true): Long {
        requireTabCapacity()
        val id = nextId++
        val view = createView(id)
        views[id] = view
        if (activate || activeId == 0L) activeId = id
        view.loadUrl(url)
        host.onTabStateChanged()
        return id
    }

    fun loadInActive(url: String) {
        val view = views[activeId]
        if (view == null) {
            openTab(url)
        } else {
            view.loadUrl(url)
            host.onTabStateChanged()
        }
    }

    fun activeView(): TvWebView? = views[activeId]

    val activeTabId: Long get() = activeId

    fun activate(id: Long) {
        if (!views.containsKey(id)) return
        activeId = id
        blocked.set(0)
        host.onTabStateChanged()
    }

    fun closeTab(id: Long) {
        val view = views.remove(id) ?: return
        detachAndDestroy(view)
        if (activeId == id) {
            activeId = views.keys.lastOrNull() ?: 0L
        }
        host.onTabStateChanged()
    }

    fun closeActive() {
        if (activeId != 0L) closeTab(activeId)
    }

    fun snapshot(): List<Tab> = views.map { (id, view) ->
        Tab(
            id = id,
            url = view.url ?: "",
            title = view.title ?: "",
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            progress = view.progress,
            loading = view.progress in 1..99,
        )
    }

    fun activeTab(): Tab? = snapshot().firstOrNull { it.id == activeId }

    fun reload() {
        activeView()?.reload()
    }

    fun goBack(): Boolean {
        val view = activeView() ?: return false
        return if (view.canGoBack()) {
            view.goBack()
            blocked.set(0)
            true
        } else {
            false
        }
    }

    fun goForward() {
        activeView()?.let {
            if (it.canGoForward()) {
                it.goForward()
                blocked.set(0)
            }
        }
    }

    fun pauseExcept(keep: Long) {        views.forEach { (id, view) -> if (id != keep) view.pauseMedia() }
    }

    fun pauseAll() {
        views.values.forEach { it.pauseMedia() }
    }

    fun resumeActive() {
        activeView()?.onResume()
    }

    fun pauseActive() {
        activeView()?.onPause()
    }

    fun destroy() {
        CookieManager.getInstance().flush()
        views.values.forEach { detachAndDestroy(it) }
        views.clear()
        activeId = 0L
    }

    private fun detachAndDestroy(view: TvWebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.destroy()
    }

    private fun requireTabCapacity() {
        while (views.size >= MAX_TABS) {
            val victimId = views.keys.firstOrNull { it != activeId }
                ?: views.keys.firstOrNull()
                ?: return
            val victim = views.remove(victimId)
            if (victim != null) detachAndDestroy(victim)
            if (activeId == victimId) activeId = views.keys.lastOrNull() ?: 0L
        }
    }

    private fun createView(id: Long): TvWebView {
        val current = settings()
        val view = TvWebView(context)
        view.id = View.generateViewId()

        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            userAgentString = current.userAgent.value ?: WebSettings.getDefaultUserAgent(context)
            textZoom = 100
            setSupportMultipleWindows(false)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            WebViewCompat.startSafeBrowsing(context) { }
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        view.onBack = { host.onBackRequested() }
        view.onMenu = { host.onMenuRequested() }

        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                webView: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                if (!settings().blockAds || request.isForMainFrame) return null
                val hostName = request.url.host ?: return null
                if (!blocklist().isBlocked(hostName)) return null
                if (id == activeId) blocked.incrementAndGet()
                host.onBlocked(hostName)
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0)),
                )
            }

            override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
                if (id == activeId) blocked.set(0)
                view.resetPageFocus()
                host.onTabStateChanged()
            }

            override fun onPageFinished(webView: WebView, url: String?) {
                if (settings().blockAds) view.applyCosmeticFilters()
                val title = webView.title ?: ""
                if (!url.isNullOrBlank()) host.onVisit(url, title)
                host.onTabStateChanged()
            }

            override fun onReceivedError(
                webView: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                val description = error.description?.toString().orEmpty()
                host.onLoadError(description.ifBlank { "Page failed to load" })
            }

            override fun onRenderProcessGone(
                deadView: WebView,
                detail: android.webkit.RenderProcessGoneDetail,
            ): Boolean {
                views.remove(id)
                detachAndDestroy(view)
                if (activeId == id) activeId = views.keys.lastOrNull() ?: 0L
                host.onLoadError("The page crashed. Try again.")
                host.onTabStateChanged()
                return true
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(webView: WebView, newProgress: Int) {
                host.onTabStateChanged()
            }

            override fun onReceivedTitle(webView: WebView, title: String?) {
                host.onTabStateChanged()
            }

            override fun onShowCustomView(
                customView: View,
                callback: WebChromeClient.CustomViewCallback,
            ) {
                host.onEnterFullscreen(customView, callback)
            }

            override fun onHideCustomView() {
                host.onExitFullscreen()
            }
        }

        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            host.onDownloadRequest(
                url,
                userAgent ?: "",
                mimeType ?: "",
                Downloader.nameFromDisposition(contentDisposition ?: ""),
            )
        }

        return view
    }

    private companion object {
        const val MAX_TABS = 8
    }
}

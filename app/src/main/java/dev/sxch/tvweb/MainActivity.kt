package dev.sxch.tvweb

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import dev.sxch.tvweb.browser.Blocklist
import dev.sxch.tvweb.browser.BrowserHost
import dev.sxch.tvweb.browser.BrowserSession
import dev.sxch.tvweb.browser.Downloader
import dev.sxch.tvweb.browser.DrmApp
import dev.sxch.tvweb.browser.DrmSites
import dev.sxch.tvweb.browser.NavTarget
import dev.sxch.tvweb.browser.PageState
import dev.sxch.tvweb.browser.UrlParser
import dev.sxch.tvweb.data.BrowserStore
import dev.sxch.tvweb.data.SearchEngine
import dev.sxch.tvweb.data.SettingsStore
import dev.sxch.tvweb.data.Shortcut
import dev.sxch.tvweb.data.UserAgentMode
import dev.sxch.tvweb.ui.ChromeBar
import dev.sxch.tvweb.ui.HomeView
import dev.sxch.tvweb.ui.InputPanel
import dev.sxch.tvweb.ui.PanelView
import dev.sxch.tvweb.ui.Ui

sealed interface Surface {
    data object Home : Surface
    data object Browser : Surface
    data class Panel(val kind: PanelKind) : Surface
}

enum class PanelKind { INPUT, MENU, TABS, SETTINGS, BOOKMARKS, HISTORY, DRM, DOWNLOAD }

private data class PendingDownload(val url: String, val userAgent: String, val mime: String, val name: String)

class MainActivity : Activity(), BrowserHost {

    private lateinit var prefs: SettingsStore
    private lateinit var store: BrowserStore
    private lateinit var session: BrowserSession

    @Volatile
    private var blocklist: Blocklist = Blocklist.EMPTY

    private lateinit var root: FrameLayout
    private lateinit var home: HomeView
    private lateinit var browserLayer: LinearLayout
    private lateinit var chrome: ChromeBar
    private lateinit var webContainer: FrameLayout
    private lateinit var scrim: View
    private lateinit var overlayHost: FrameLayout

    private var surface: Surface = Surface.Home
    private var returnSurface: Surface = Surface.Home
    private var lastPageState = PageState()
    private var pendingDownload: PendingDownload? = null
    private var drmShownFor: String = ""
    private var errorText: String = ""
    private var cinemaMode = false
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBlockedHost: String = ""

    private val handler = Handler(Looper.getMainLooper())

    private val probeTick = object : Runnable {
        override fun run() {
            if (surface == Surface.Browser) {
                session.activeView()?.takeIf { it.parent != null }?.probe { state ->
                    lastPageState = state
                    refreshChrome()
                }
            }
            handler.postDelayed(this, PROBE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SettingsStore(this)
        store = BrowserStore(this)
        buildLayout()

        session = BrowserSession(this, { prefs.settings }, { blocklist }, this)

        Thread {
            val loaded = Blocklist.load(this)
            blocklist = loaded
            runOnUiThread { refreshChrome() }
        }.apply { name = "blocklist-load"; isDaemon = true }.start()

        handleIntent(intent)
        showSurface(if (session.tabCount > 0) Surface.Browser else Surface.Home)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("http://") || data.startsWith("https://")) navigate(data)
    }

    private fun buildLayout() {
        root = FrameLayout(this)
        root.setBackgroundColor(Ui.color(this, R.color.background))

        home = HomeView(this).apply {
            onNavigate = { url -> navigate(url) }
            onSearch = { openInput("") }
            onTabs = { showPanel(PanelKind.TABS) }
            onBookmarks = { showPanel(PanelKind.BOOKMARKS) }
            onSettings = { showPanel(PanelKind.SETTINGS) }
            onRemoveShortcut = { shortcut -> confirmRemoveShortcut(shortcut) }
        }

        chrome = ChromeBar(this).apply {
            onBack = { navigateBack() }
            onForward = { session.goForward() }
            onAddress = { openInput(session.activeTab()?.url ?: "") }
            onVideo = { watchFullscreen() }
            onTabs = { showPanel(PanelKind.TABS) }
            onMenu = { showPanel(PanelKind.MENU) }
        }

        webContainer = FrameLayout(this)

        browserLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(chrome, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(webContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }

        scrim = View(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE
        }
        overlayHost = FrameLayout(this).apply { visibility = View.GONE }

        root.addView(home, matchParent())
        root.addView(browserLayer, matchParent())
        root.addView(scrim, matchParent())
        root.addView(overlayHost, matchParent())
        setContentView(root)
    }

    private fun matchParent(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )

    private fun showSurface(next: Surface) {
        surface = next
        home.visibility = if (next == Surface.Home) View.VISIBLE else View.GONE
        browserLayer.visibility = if (next == Surface.Browser) View.VISIBLE else View.GONE

        val panelVisible = next is Surface.Panel
        scrim.visibility = if (panelVisible) View.VISIBLE else View.GONE
        overlayHost.visibility = if (panelVisible) View.VISIBLE else View.GONE
        if (!panelVisible) {
            overlayHost.removeAllViews()
            hideKeyboard()
        } else {
            root.bringChildToFront(scrim)
            root.bringChildToFront(overlayHost)
        }

        when (next) {
            Surface.Home -> {
                exitCinemaMode()
                session.pauseAll()
                home.render(store.shortcuts(), session.tabCount)
                home.focusDefault()
            }
            Surface.Browser -> {
                attachActiveWebView()
                refreshChrome()
                if (cinemaMode) {
                    chrome.visibility = View.GONE
                    session.activeView()?.requestFocus()
                } else {
                    chrome.visibility = View.VISIBLE
                    (session.activeView() ?: chrome).requestFocus()
                }
            }
            is Surface.Panel -> openPanel(next.kind)
        }
    }

    private fun openPanel(kind: PanelKind) {
        val panel: View = when (kind) {
            PanelKind.INPUT -> buildInputPanel()
            PanelKind.MENU -> buildMenuPanel()
            PanelKind.TABS -> buildTabsPanel()
            PanelKind.SETTINGS -> buildSettingsPanel()
            PanelKind.BOOKMARKS -> buildBookmarksPanel()
            PanelKind.HISTORY -> buildHistoryPanel()
            PanelKind.DRM -> buildDrmPanel()
            PanelKind.DOWNLOAD -> buildDownloadPanel()
        }
        overlayHost.removeAllViews()
        overlayHost.addView(panel, FrameLayout.LayoutParams(            Ui.dp(this, 760f),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            topMargin = Ui.dp(this@MainActivity, 40f)
            bottomMargin = Ui.dp(this@MainActivity, 40f)
        })
        (panel as? PanelView)?.focusFirst()
        overlayHost.post { (panel as? PanelView)?.focusFirst() }
    }

    private fun closePanel() {
        val target = when {
            returnSurface == Surface.Home -> Surface.Home
            session.tabCount > 0 -> Surface.Browser
            else -> Surface.Home
        }
        showSurface(target)
    }

    private fun checkDrm() {
        val url = session.activeTab()?.url ?: return
        if (url == drmShownFor) return
        if (DrmSites.appFor(url) == null) return
        drmShownFor = url
        showPanel(PanelKind.DRM)
    }

    // region panels

    private fun buildInputPanel(): PanelView {
        val panel = InputPanel(this)
        panel.onSubmit = { value ->
            closePanel()
            navigate(value)
        }
        panel.onVoice = { startVoice() }
        panel.onCancel { closePanel() }
        return panel
    }

    private fun buildMenuPanel(): PanelView {
        val tab = session.activeTab()
        val panel = PanelView(this, "Actions", fillsHeight = true).subtitle("MENU opens this list anywhere in the page")
        panel.addRow(R.drawable.ic_search, "Search or enter address", "") { openPanel(PanelKind.INPUT) }
        panel.addRow(R.drawable.ic_home, "Home", if (prefs.settings.homepage.isBlank()) "Start page" else hostOf(prefs.settings.homepage)) {
            closePanel()
            openHome()
        }
        if (tab?.canGoBack == true) panel.addRow(R.drawable.ic_back, "Back", "History") { closePanel(); navigateBack() }
        if (tab?.canGoForward == true) panel.addRow(R.drawable.ic_forward, "Forward", "") { closePanel(); session.goForward() }
        panel.addRow(R.drawable.ic_reload, "Reload", "") { closePanel(); session.reload() }
        panel.addRow(
            R.drawable.ic_play,
            "Watch fullscreen",
            if (lastPageState.hasVideo) "Video found" else "No video found",
        ) {
            closePanel()
            watchFullscreen()
        }
        val url = tab?.url ?: ""
        val bookmarked = url.isNotBlank() && store.isBookmarked(url)
        panel.addRow(
            R.drawable.ic_bookmark,
            if (bookmarked) "Remove bookmark" else "Bookmark this page",
            "",
        ) {
            if (bookmarked) store.removeBookmark(url) else store.addBookmark(url, tab?.title ?: url)
            toast(if (bookmarked) "Bookmark removed" else "Bookmarked")
            closePanel()
        }
        panel.addRow(R.drawable.ic_external, "Add site to start page", "") {
            if (url.isNotBlank()) {
                store.addShortcut(tab?.title?.ifBlank { url } ?: url, url)
                toast("Added to start page")
            }
            closePanel()
        }
        panel.addRow(R.drawable.ic_plus, "New tab", "Opens a blank tab") {
            session.openTab("about:blank")
            lastPageState = PageState()
            closePanel()
            showSurface(Surface.Browser)
            openInput("")
        }
        panel.addRow(R.drawable.ic_close, "Close tab", "Leaves the others open") {
            closePanel()
            session.closeActive()
            lastPageState = PageState()
            if (session.tabCount == 0) showSurface(Surface.Home) else showSurface(Surface.Browser)
        }
        panel.addRow(R.drawable.ic_tabs, "Tabs", "${session.tabCount} open") { showPanel(PanelKind.TABS) }
        panel.addRow(R.drawable.ic_history, "History", "") { showPanel(PanelKind.HISTORY) }
        panel.addRow(R.drawable.ic_sliders, "Settings", "") { showPanel(PanelKind.SETTINGS) }
        return panel
    }

    private fun buildTabsPanel(): PanelView {
        val panel = PanelView(this, "Tabs", fillsHeight = true).subtitle("OK switches tab. Hold OK to close.")
        val tabs = session.snapshot()
        if (tabs.isEmpty()) {
            panel.addRow(R.drawable.ic_tabs, "No tabs open", "") {
                closePanel()
                openInput("")
            }
            return panel
        }
        tabs.forEach { tab ->
            val row = panel.addRow(
                R.drawable.ic_tabs,
                tab.title.ifBlank { tab.url.ifBlank { "New tab" } }.take(60),
                if (tab.id == session.activeTab()?.id) "Active" else hostOf(tab.url),
            ) {
                session.activate(tab.id)
                lastPageState = PageState()
                closePanel()
            }
            row.setOnLongClickListener {
                session.closeTab(tab.id)
                if (session.tabCount == 0) {
                    closePanel()
                    showSurface(Surface.Home)
                } else {
                    openPanel(PanelKind.TABS)
                }
                true
            }
        }
        panel.addRow(R.drawable.ic_plus, "New tab", "") {
            closePanel()
            openInput("")
        }
        return panel
    }

    private fun buildBookmarksPanel(): PanelView {
        val panel = PanelView(this, "Bookmarks", fillsHeight = true).subtitle("Hold OK to remove a bookmark")
        val bookmarks = store.bookmarks()
        if (bookmarks.isEmpty()) {
            panel.addRow(R.drawable.ic_bookmark, "No bookmarks yet", "") {
                closePanel()
                showPanel(PanelKind.MENU)
            }
            return panel
        }
        bookmarks.forEach { bookmark ->
            val row = panel.addRow(R.drawable.ic_bookmark, bookmark.title.take(60), hostOf(bookmark.url)) {
                closePanel()
                navigate(bookmark.url)
            }
            row.setOnLongClickListener {
                store.removeBookmark(bookmark.url)
                openPanel(PanelKind.BOOKMARKS)
                true
            }
        }
        return panel
    }

    private fun buildSettingsPanel(): PanelView {
        val current = prefs.settings
        val panel = PanelView(this, "Settings", fillsHeight = true).subtitle("OK changes a value")
        panel.addRow(R.drawable.ic_search, "Search engine", current.searchEngine.label) {
            val next = nextEnum(current.searchEngine, SearchEngine.entries)
            prefs.update { it.copy(searchEngine = next) }
            openPanel(PanelKind.SETTINGS)
        }
        panel.addRow(R.drawable.ic_globe, "Browser identity", current.userAgent.label) {
            val next = nextEnum(current.userAgent, UserAgentMode.entries)
            prefs.update { it.copy(userAgent = next) }
            toast("Reload the page to apply")
            openPanel(PanelKind.SETTINGS)
        }
        panel.addRow(R.drawable.ic_shield, "Block ads", if (current.blockAds) "On" else "Off") {
            prefs.update { it.copy(blockAds = !current.blockAds) }
            openPanel(PanelKind.SETTINGS)
        }
        panel.addRow(R.drawable.ic_history, "Save history", if (current.saveHistory) "On" else "Off") {
            prefs.update { it.copy(saveHistory = !current.saveHistory) }
            openPanel(PanelKind.SETTINGS)
        }
        panel.addRow(R.drawable.ic_home, "Homepage", current.homepage.ifBlank { "Start page" }.take(40)) {
            val url = session.activeTab()?.url ?: return@addRow
            prefs.update { it.copy(homepage = url) }
            toast("Homepage set")
            openPanel(PanelKind.SETTINGS)
        }
        if (current.homepage.isNotBlank()) {
            panel.addRow(R.drawable.ic_close, "Clear homepage", "Back to the start page") {
                prefs.update { it.copy(homepage = "") }
                openPanel(PanelKind.SETTINGS)
            }
        }
        panel.addRow(R.drawable.ic_close, "Clear history", "Cannot be undone") {
            store.clearHistory()
            toast("History cleared")
            openPanel(PanelKind.SETTINGS)
        }
        panel.addRow(R.drawable.ic_shield, "Ad block list", "${blocklist.size} hosts") { }
        panel.addRow(R.drawable.ic_globe, "Version", BuildConfig.VERSION_NAME) { }
        return panel
    }

    private fun buildHistoryPanel(): PanelView {
        val panel = PanelView(this, "History", fillsHeight = true).subtitle("Most recent first. Hold OK to remove an entry.")
        val entries = store.history(80)
        if (entries.isEmpty()) {
            panel.addRow(R.drawable.ic_history, "No history yet", "") { closePanel() }
            return panel
        }
        entries.forEach { entry ->
            val row = panel.addRow(R.drawable.ic_history, entry.title.ifBlank { hostOf(entry.url) }.take(60), hostOf(entry.url)) {
                closePanel()
                navigate(entry.url)
            }
            row.setOnLongClickListener {
                store.removeHistory(entry.url)
                openPanel(PanelKind.HISTORY)
                true
            }
        }
        return panel
    }

    private fun buildDrmPanel(): PanelView {
        val url = session.activeTab()?.url ?: ""
        val app = DrmSites.appFor(url)
        val installed = app != null && DrmSites.isInstalled(this, app)
        val panel = PanelView(this, "This site needs its own app")
            .subtitle(
                "${app?.label ?: "This service"} streams with DRM copy protection, which the " +
                    "system web engine cannot decode. Video will not play here."
            )
        if (app != null) {
            panel.addRow(
                R.drawable.ic_external,
                if (installed) "Open ${app.label}" else "${app.label} app not found",
                if (installed) "Installed" else "Install it from the TV store",
            ) {
                closePanel()
                if (!installed) return@addRow
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    runCatching { startActivity(intent) }
                        .onFailure { toast("Could not open ${app.label}") }
                } else {
                    toast("No launcher activity")
                }
            }
        }
        panel.addRow(R.drawable.ic_external, "Continue in the browser anyway", "") { closePanel() }
        return panel
    }

    private fun buildDownloadPanel(): PanelView {
        val pending = pendingDownload
        val panel = PanelView(this, "Download file")
            .subtitle(pending?.name ?: "This file will be saved to your Downloads folder")
        panel.addRow(R.drawable.ic_download, "Start download", "") {
            val current = pendingDownload
            closePanel()
            if (current != null) startDownload(current)
        }
        panel.addRow(R.drawable.ic_close, "Cancel", "") { closePanel() }
        return panel
    }

    // endregion

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun showPanel(kind: PanelKind) {
        if (surface !is Surface.Panel) {
            returnSurface = if (session.tabCount > 0 || surface == Surface.Home) surface else Surface.Home
        }
        showSurface(Surface.Panel(kind))
    }

    private fun navigate(raw: String) {
        val target = UrlParser.parse(raw, prefs.settings.searchEngine)
        val url = when (target) {
            is NavTarget.Address -> target.url
            is NavTarget.Query -> target.url
        }
        errorText = ""
        lastPageState = PageState()
        exitCinemaMode()
        session.loadInActive(url)
        showSurface(Surface.Browser)
    }

    private fun openHome() {
        val homepage = prefs.settings.homepage
        if (homepage.isBlank()) {
            showSurface(Surface.Home)
        } else {
            navigate(homepage)
        }
    }

    private fun openInput(prefill: String) {
        showPanel(PanelKind.INPUT)
        (overlayHost.getChildAt(0) as? InputPanel)?.prefill(if (prefill.startsWith("about:")) "" else prefill)
    }

    private fun navigateBack() {
        if (!session.goBack()) {
            showSurface(Surface.Home)
        } else {
            lastPageState = PageState()
            refreshChrome()
        }
    }

    private fun watchFullscreen() {
        val view = session.activeView() ?: return
        if (!cinemaMode) {
            cinemaMode = true
            chrome.visibility = View.GONE
            toast("Cinema mode. Press BACK to bring the toolbar back")
        }
        view.requestFocus()
        view.requestVideoFullscreen { }
    }

    private fun exitCinemaMode() {
        if (!cinemaMode) return
        cinemaMode = false
        chrome.visibility = View.VISIBLE
        chrome.requestFocus()
    }

    private fun clearFullscreenView() {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        fullscreenCallback = null
        cinemaMode = false
        chrome.visibility = View.VISIBLE
    }

    private fun attachActiveWebView() {
        clearFullscreenView()
        val view = session.activeView() ?: return
        session.pauseExcept(session.activeTabId)
        webContainer.removeAllViews()
        (view.parent as? ViewGroup)?.removeView(view)
        webContainer.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun refreshChrome() {
        val tab = session.activeTab()
        val settings = prefs.settings
        val status = buildString {
            if (errorText.isNotEmpty()) {
                append(errorText)
                append("   ")
            } else {
                if (settings.blockAds) append("Ads blocked ${session.blockedCount}")
                if (lastBlockedHost.isNotEmpty()) append(" (last $lastBlockedHost)")
                if (lastPageState.hasVideo) append("   Video ready")
                append("   ")
            }
            append("Arrows move the ring, OK selects, MENU opens actions")
        }
        chrome.render(
            url = tab?.url ?: "",
            canGoBack = tab?.canGoBack == true,
            canGoForward = tab?.canGoForward == true,
            loading = tab?.loading == true,
            progressValue = tab?.progress ?: 0,
            hasVideo = lastPageState.hasVideo,
            tabCount = session.tabCount,
            status = status,
        )
    }

    private fun confirmRemoveShortcut(shortcut: Shortcut) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${shortcut.title}?")
            .setMessage("It disappears from the start page.")
            .setPositiveButton("Remove") { _, _ ->
                store.removeShortcut(shortcut.id)
                home.render(store.shortcuts(), session.tabCount)
                home.focusDefault()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search or say a site")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_VOICE)
        } catch (e: ActivityNotFoundException) {
            toast("No voice search app on this device")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                closePanel()
                navigate(spoken)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    handleBack()
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    when (surface) {
                        Surface.Browser -> {
                            showPanel(PanelKind.MENU)
                            return true
                        }
                        Surface.Home -> {
                            openInput("")
                            return true
                        }
                        else -> Unit
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (surface == Surface.Browser && !cinemaMode) {
                        val view = session.activeView()
                        if (view != null && view.hasFocus() && view.atTop && !view.pageHoldsKeys) {
                            chrome.focusAddress()
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack() {
        if (fullscreenView != null) {
            onExitFullscreen()
            return
        }
        if (cinemaMode) {
            exitCinemaMode()
            return
        }
        when (surface) {
            is Surface.Panel -> closePanel()
            Surface.Browser -> navigateBack()
            Surface.Home -> finish()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(probeTick, PROBE_INTERVAL_MS)
        session.resumeActive()
    }

    override fun onPause() {
        handler.removeCallbacks(probeTick)
        session.pauseAll()
        session.pauseActive()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session.destroy()
        super.onDestroy()
    }

    // region BrowserHost

    override fun onTabStateChanged() {
        runOnUiThread {
            if (surface == Surface.Browser) {
                attachIfNeeded()
                refreshChrome()
                checkDrm()
            } else if (surface == Surface.Home) {
                home.updateTabCount(session.tabCount)
            }
        }
    }

    private fun attachIfNeeded() {
        val view = session.activeView() ?: return
        if (view.parent !== webContainer) {
            attachActiveWebView()
        }
    }

    override fun onBackRequested() {
        runOnUiThread { handleBack() }
    }

    override fun onVisit(url: String, title: String) {
        if (prefs.settings.saveHistory) store.recordVisit(url, title)
        session.activeView()?.armPageFocus()
    }

    override fun onBlocked(host: String) {
        runOnUiThread {
            lastBlockedHost = host
            if (surface == Surface.Browser) refreshChrome()
        }
    }

    override fun onLoadError(message: String) {
        runOnUiThread {
            errorText = message
            if (surface == Surface.Browser) refreshChrome()
        }
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        runOnUiThread {
            fullscreenView = view
            fullscreenCallback = callback
            root.addView(view, matchParent())
            chrome.visibility = View.GONE
            cinemaMode = true
        }
    }

    override fun onExitFullscreen() {
        runOnUiThread {
            val callback = fullscreenCallback
            fullscreenView?.let { root.removeView(it) }
            fullscreenView = null
            fullscreenCallback = null
            if (session.activeView() != null) callback?.onCustomViewHidden()
            cinemaMode = false
            chrome.visibility = View.VISIBLE
        }
    }

    override fun onDownloadRequest(url: String, userAgent: String, mime: String, name: String) {
        runOnUiThread {
            val fileName = name.ifBlank { Downloader.nameFromUrl(url, mime) }
            pendingDownload = PendingDownload(url, userAgent, mime, fileName)
            showPanel(PanelKind.DOWNLOAD)
        }
    }

    override fun onMenuRequested() {
        runOnUiThread { showPanel(PanelKind.MENU) }
    }

    // endregion

    private fun startDownload(request: PendingDownload) {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingDownload = request
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE,
            )
            return
        }
        val started = Downloader.enqueue(this, request.url, request.userAgent, request.mime, request.name)
        toast(if (started) "Download started" else "This file cannot be downloaded")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_STORAGE) return
        val request = pendingDownload
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && request != null) {
            startDownload(request)
        } else {
            toast("Storage permission is needed to save files")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("").removePrefix("www.").take(40)

    private fun <T : Enum<T>> nextEnum(current: T, values: List<T>): T {
        val index = values.indexOf(current)
        return values[(index + 1) % values.size]
    }

    private companion object {
        const val PROBE_INTERVAL_MS = 900L
        const val REQUEST_VOICE = 1001
        const val REQUEST_STORAGE = 1002
    }
}

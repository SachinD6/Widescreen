package dev.sxch.tvweb.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
class TvWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    var onBack: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    private var pageOwnsKeys = false
    private var activeTag = ""

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }

    val atTop: Boolean get() = !canScrollVertically(-1)

    val pageHoldsKeys: Boolean get() = pageOwnsKeys

    fun resetPageFocus() {
        pageOwnsKeys = false
        activeTag = ""
    }

    fun probe(callback: (PageState) -> Unit) {
        evaluateJavascript(PageAgent.PROBE) { raw ->
            val state = runCatching {
                val json = JSONObject(unquote(raw ?: "{}"))
                PageState(
                    videoCount = json.optInt("v"),
                    activeTag = json.optString("t"),
                )
            }.getOrDefault(PageState())
            activeTag = state.activeTag
            pageOwnsKeys = activeTag == "INPUT" ||
                activeTag == "TEXTAREA" ||
                activeTag == "SELECT" ||
                activeTag == "IFRAME"
            callback(state)
        }
    }

    fun requestVideoFullscreen(onResult: (Boolean) -> Unit) {
        evaluateJavascript(PageAgent.FULLSCREEN_VIDEO) { raw ->
            onResult(unquote(raw ?: "0") == "1")
        }
    }

    fun pauseMedia() {
        evaluateJavascript(PageAgent.PAUSE_ALL, null)
    }

    fun applyCosmeticFilters() {
        evaluateJavascript(PageAgent.COSMETIC, null)
    }

    private fun scrollPage(delta: Int) {
        evaluateJavascript(PageAgent.scrollBy(delta), null)
    }

    private fun tap(event: KeyEvent) {
        evaluateJavascript(PageAgent.TAP) { raw ->
            if (unquote(raw ?: "") == "native-frame") {
                super.dispatchKeyEvent(event)
            }
        }
    }

    private fun pageHandlesConfirmNatively(): Boolean =
        activeTag == "SELECT" || activeTag == "IFRAME" || activeTag == "TEXTAREA"

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    onBack?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    onMenu?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (!pageHandlesConfirmNatively()) {
                        if (event.repeatCount == 0) tap(event)
                        return true
                    }
                }
            }
            if (!pageOwnsKeys) {
                val step = (resources.displayMetrics.density * 240).toInt()
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        scrollPage(step)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        scrollPage(-step)
                        return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                        scrollPage(-step * 3)
                        return true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                        scrollPage(step * 3)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return trimmed
    }
}

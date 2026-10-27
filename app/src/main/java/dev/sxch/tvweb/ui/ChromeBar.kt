package dev.sxch.tvweb.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.sxch.tvweb.R

class ChromeBar(context: Context) : LinearLayout(context) {

    var onBack: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null
    var onAddress: (() -> Unit)? = null
    var onVideo: (() -> Unit)? = null
    var onTabs: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    private val backChip = Chip(context, "Back")
    private val forwardChip = Chip(context, "Fwd")
    private val addressChip = Chip(context, "Search or enter address")
    private val videoChip = Chip(context, "Watch", accent = true)
    private val tabsChip = Chip(context, "Tabs")
    private val menuChip = Chip(context, "Menu")
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        isIndeterminate = false
    }
    private val statusView = TextView(context).apply {
        styleText(12f, R.color.text_dim)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(context, 20f), 0, 0, 0)
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#E60B0E10"))
        setPadding(Ui.dp(context, 20f), Ui.dp(context, 12f), Ui.dp(context, 20f), Ui.dp(context, 6f))

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        backChip.setOnClickListener { onBack?.invoke() }
        forwardChip.setOnClickListener { onForward?.invoke() }
        addressChip.setOnClickListener { onAddress?.invoke() }
        videoChip.setOnClickListener { onVideo?.invoke() }
        tabsChip.setOnClickListener { onTabs?.invoke() }
        menuChip.setOnClickListener { onMenu?.invoke() }

        row.addView(backChip, chipParams())
        row.addView(forwardChip, chipParams())
        row.addView(addressChip, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Ui.dp(context, 12f)
        })
        row.addView(videoChip, chipParams())
        row.addView(tabsChip, chipParams())
        row.addView(menuChip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 18f)))
        addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 2f)))

        videoChip.visibility = View.GONE
        progress.visibility = View.INVISIBLE
    }

    fun render(
        url: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        loading: Boolean,
        progressValue: Int,
        hasVideo: Boolean,
        tabCount: Int,
        status: String,
    ) {
        backChip.isEnabled = canGoBack
        backChip.alpha = if (canGoBack) 1f else 0.4f
        forwardChip.isEnabled = canGoForward
        forwardChip.alpha = if (canGoForward) 1f else 0.4f

        addressChip.text = url.ifBlank { "Search or enter address" }
        videoChip.visibility = if (hasVideo) View.VISIBLE else View.GONE
        tabsChip.text = if (tabCount > 1) "Tabs ($tabCount)" else "Tabs"
        statusView.text = status

        if (loading && progressValue in 0..99) {
            progress.visibility = View.VISIBLE
            progress.progress = progressValue
        } else {
            progress.visibility = View.INVISIBLE
        }
    }

    fun focusAddress() {
        addressChip.requestFocus()
    }


    private fun chipParams(): LayoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
    ).apply { rightMargin = Ui.dp(context, 12f) }
}

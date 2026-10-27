package dev.sxch.tvweb.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.sxch.tvweb.R

class ChromeBar(context: Context) : LinearLayout(context) {

    var onBack: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null
    var onReload: (() -> Unit)? = null
    var onAddress: (() -> Unit)? = null
    var onVideo: (() -> Unit)? = null
    var onTabs: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    private val backButton = TvButton(context, R.drawable.ic_back)
    private val forwardButton = TvButton(context, R.drawable.ic_forward)
    private val reloadButton = TvButton(context, R.drawable.ic_reload)
    private val videoButton = TvButton(context, R.drawable.ic_play, "Watch")
    private val tabsButton = TvButton(context, R.drawable.ic_tabs)
    private val menuButton = TvButton(context, R.drawable.ic_more)

    private lateinit var progressTrack: FrameLayout
    private val addressPill: LinearLayout
    private val addressView = TextView(context)
    private val progressFill = View(context)
    private val statusView = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#E6000000"))
        setPadding(Ui.dp(context, 34f), Ui.dp(context, 14f), Ui.dp(context, 34f), Ui.dp(context, 8f))
        clipChildren = false
        clipToPadding = false

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        addressPill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            background = Ui.rounded(context, R.color.surface, 22f, 225)
            setPadding(Ui.dp(context, 20f), Ui.dp(context, 10f), Ui.dp(context, 20f), Ui.dp(context, 10f))
            addView(
                Ui.iconView(context, R.drawable.ic_globe, tintRes = R.color.text_dim),
                LayoutParams(Ui.dp(context, 18f), Ui.dp(context, 18f)).apply {
                    rightMargin = Ui.dp(context, 10f)
                },
            )
            addressView.apply {
                styleText(16f, R.color.text)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            addView(addressView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            setOnClickListener { onAddress?.invoke() }
            setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.02f else 1f)
                    .scaleY(if (hasFocus) 1.02f else 1f)
                    .setDuration(150).start()
                background = Ui.rounded(
                    context,
                    if (hasFocus) R.color.surface_raised else R.color.surface,
                    22f,
                    235,
                )
                v.elevation = if (hasFocus) Ui.dp(context, 6f).toFloat() else 0f
            }
        }

        backButton.setOnClickListener { onBack?.invoke() }
        forwardButton.setOnClickListener { onForward?.invoke() }
        reloadButton.setOnClickListener { onReload?.invoke() }
        videoButton.setOnClickListener { onVideo?.invoke() }
        tabsButton.setOnClickListener { onTabs?.invoke() }
        menuButton.setOnClickListener { onMenu?.invoke() }

        row.addView(backButton, buttonParams())
        row.addView(forwardButton, buttonParams())
        row.addView(separator(), separatorParams())
        row.addView(reloadButton, buttonParams())
        row.addView(addressPill, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(context, 4f)
            rightMargin = Ui.dp(context, 4f)
        })
        row.addView(videoButton, buttonParams())
        row.addView(tabsButton, buttonParams())
        row.addView(menuButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        statusView.apply {
            styleText(12f, R.color.text_dim)
            letterSpacing = 0.02f
            setPadding(Ui.dp(context, 4f), Ui.dp(context, 8f), 0, 0)
        }

        progressTrack = FrameLayout(context).apply {
            addView(progressFill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        progressFill.setBackgroundColor(Ui.color(context, R.color.accent))

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(progressTrack, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 2f)))
        progressTrack.visibility = View.INVISIBLE
        videoButton.visibility = View.GONE
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
        backButton.isEnabled = canGoBack
        forwardButton.isEnabled = canGoForward
        addressView.text = url.ifBlank { "Search or enter address" }
        addressView.setTextColor(
            Ui.color(context, if (url.isBlank()) R.color.text_dim else R.color.text),
        )
        videoButton.visibility = if (hasVideo) View.VISIBLE else View.GONE
        tabsButton.setLabel("")
        tabsButton.contentDescription = "Tabs, $tabCount open"
        tabsButton.alpha = if (tabCount > 1) 1f else 0.55f
        statusView.text = status

        if (loading && progressValue in 0..99) {
            progressTrack.visibility = View.VISIBLE
            val width = (screenWidthPx(context) * (progressValue / 100f)).toInt()
            progressFill.layoutParams = (progressFill.layoutParams as FrameLayout.LayoutParams)
                .apply { this.width = width }
        } else {
            progressTrack.visibility = View.INVISIBLE
        }
    }

    fun focusAddress() {
        addressPill.requestFocus()
    }

    private fun buttonParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            rightMargin = Ui.dp(context, 10f)
        }

    private fun separator(): View = View(context).apply {
        setBackgroundColor(Ui.color(context, R.color.stroke))
        alpha = 0.7f
    }

    private fun separatorParams(): LayoutParams = LayoutParams(
        Ui.dp(context, 1f),
        Ui.dp(context, 24f),
    ).apply {
        rightMargin = Ui.dp(context, 14f)
    }
}

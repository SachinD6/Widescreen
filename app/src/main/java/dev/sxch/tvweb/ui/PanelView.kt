package dev.sxch.tvweb.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.sxch.tvweb.R

/** Uses the window size: displayMetrics reports the panel, which can be 4K behind a 1080p output. */
fun screenHeightPx(context: Context): Int {
    val config = context.resources.configuration
    return (config.screenHeightDp * context.resources.displayMetrics.density).toInt()
}

fun screenWidthPx(context: Context): Int {
    val config = context.resources.configuration
    return (config.screenWidthDp * context.resources.displayMetrics.density).toInt()
}

class CappedScrollView(context: Context) : ScrollView(context) {

    var maxHeight: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}

/**
 * tvOS-style panel: a frosted card, a strong title, and full-width rows whose focused state
 * inverts to white with a dark label.
 */
open class PanelView(
    context: Context,
    title: String,
    val fillsHeight: Boolean = false,
) : LinearLayout(context) {

    private val titleView = TextView(context).apply {
        styleText(28f)
        text = title
    }

    private val subtitleView = TextView(context).apply {
        styleText(15f, R.color.text_dim)
        setPadding(0, Ui.dp(context, 6f), 0, Ui.dp(context, 18f))
        visibility = View.GONE
    }

    val content = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(0, Ui.dp(context, 2f), Ui.dp(context, 10f), Ui.dp(context, 2f))
    }

    val rows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        background = Ui.rounded(context, R.color.surface, 24f, 250)
        setPadding(Ui.dp(context, 34f), Ui.dp(context, 30f), Ui.dp(context, 34f), Ui.dp(context, 26f))

        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = CappedScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            maxHeight = screenHeightPx(context) - Ui.dp(context, 240f)
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        if (fillsHeight) {
            addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    fun title(value: String): PanelView = apply { titleView.text = value }

    fun subtitle(value: String): PanelView = apply {
        subtitleView.text = value
        subtitleView.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
    }

    fun addRow(
        iconRes: Int,
        label: String,
        value: String = "",
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rowBackground(focused = false, enabled = onClick != null)
            setPadding(Ui.dp(context, 18f), Ui.dp(context, 15f), Ui.dp(context, 18f), Ui.dp(context, 15f))
            isFocusable = onClick != null
            isFocusableInTouchMode = onClick != null
        }

        val icon: ImageView? = if (iconRes != 0) {
            Ui.iconView(context, iconRes, tintRes = R.color.text_dim).also {
                row.addView(it, LayoutParams(Ui.dp(context, 22f), Ui.dp(context, 22f)).apply {
                    rightMargin = Ui.dp(context, 16f)
                })
            }
        } else {
            null
        }

        val name = TextView(context).apply {
            styleText(18f)
            text = label
            maxLines = 1
        }
        val detail = TextView(context).apply {
            styleText(15f, R.color.text_dim)
            text = value
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
        }

        row.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(detail, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        if (onClick != null) {
            row.setOnClickListener { onClick() }
            row.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.015f else 1f)
                    .scaleY(if (hasFocus) 1.015f else 1f)
                    .setDuration(140).start()
                v.background = rowBackground(hasFocus, enabled = true)
                val onLight = hasFocus
                name.setTextColor(Ui.color(context, if (onLight) R.color.text_on_light else R.color.text))
                detail.setTextColor(
                    Ui.color(context, if (onLight) R.color.text_on_light else R.color.text_dim),
                )
                icon?.imageTintList = ColorStateList.valueOf(
                    Ui.color(context, if (onLight) R.color.text_on_light else R.color.text_dim),
                )
            }
        } else {
            name.setTextColor(Ui.color(context, R.color.text_dim))
        }

        content.addView(row, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Ui.dp(context, 8f) })
        rows += row
        return row
    }

    fun addSection(title: String) {
        content.addView(TextView(context).apply {
            styleText(12f, R.color.text_dim)
            text = title.uppercase()
            letterSpacing = 0.16f
            setPadding(Ui.dp(context, 4f), Ui.dp(context, 16f), 0, Ui.dp(context, 10f))
        })
    }

    fun addCustom(view: View) {
        content.addView(view, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Ui.dp(context, 10f) })
    }

    open fun focusFirst() {
        (rows.firstOrNull { it.isFocusable } ?: content).requestFocus()
    }

    private fun rowBackground(focused: Boolean, enabled: Boolean) = when {
        focused -> Ui.rounded(context, R.color.surface_focused, 14f)
        enabled -> Ui.rounded(context, R.color.surface_raised, 14f, 235)
        else -> Ui.rounded(context, R.color.surface_raised, 14f, 140)
    }
}

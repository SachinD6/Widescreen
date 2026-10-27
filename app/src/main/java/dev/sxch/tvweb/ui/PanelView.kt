package dev.sxch.tvweb.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.sxch.tvweb.R

class Chip @JvmOverloads constructor(
    context: Context,
    text: String = "",
    accent: Boolean = false,
) : TextView(context) {

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        setPadding(Ui.dp(context, 16f), Ui.dp(context, 10f), Ui.dp(context, 16f), Ui.dp(context, 10f))
        background = if (accent) Ui.accentBackground(context) else Ui.cardBackground(context, 8f)
        setTextColor(Ui.brightText(context))
        if (accent) setTextColor(Ui.solidText(context, R.color.text_accent))
        isFocusable = true
        isFocusableInTouchMode = true
        this.text = text
    }

    fun label(value: String): Chip = apply { text = value }
}

class CappedScrollView(context: Context) : ScrollView(context) {

    var maxHeight: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}

open class PanelView(context: Context, title: String) : LinearLayout(context) {

    private val titleView = TextView(context).apply {
        styleText(26f)
        text = title
        setTextColor(Ui.solidText(context, R.color.accent))
        setPadding(0, 0, 0, Ui.dp(context, 6f))
    }

    private val subtitleView = TextView(context).apply {
        styleText(15f)
        setPadding(0, 0, 0, Ui.dp(context, 18f))
    }

    val content = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(0, Ui.dp(context, 4f), Ui.dp(context, 8f), Ui.dp(context, 4f))
    }

    val rows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        background = Ui.cardBackground(context, 16f)
        setPadding(Ui.dp(context, 32f), Ui.dp(context, 28f), Ui.dp(context, 32f), Ui.dp(context, 20f))
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroll = CappedScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            maxHeight = resources.displayMetrics.heightPixels -
                Ui.dp(context, 220f)
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun title(value: String): PanelView = apply { titleView.text = value }

    fun subtitle(value: String): PanelView = apply { subtitleView.text = value }

    fun addRow(label: String, value: String = "", onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.cardBackground(context, 8f)
            setPadding(Ui.dp(context, 20f), Ui.dp(context, 16f), Ui.dp(context, 20f), Ui.dp(context, 16f))
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val name = TextView(context).apply {
            styleText(18f, R.color.text_dim)
            text = label
        }
        val detail = TextView(context).apply {
            styleText(18f, R.color.accent)
            text = value
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        row.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(detail, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.isFocusable = false
        }
        content.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Ui.dp(context, 8f) })
        rows += row
        return row
    }

    fun addSection(title: String) {
        content.addView(TextView(context).apply {
            styleText(13f, R.color.text_dim)
            text = title.uppercase()
            setPadding(Ui.dp(context, 4f), Ui.dp(context, 14f), 0, Ui.dp(context, 10f))
        })
    }

    fun addCustom(view: View) {
        content.addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = Ui.dp(context, 8f) })
    }

    open fun focusFirst() {
        (rows.firstOrNull { it.isFocusable } ?: content).requestFocus()
    }
}

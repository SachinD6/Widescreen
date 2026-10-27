package dev.sxch.tvweb.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.sxch.tvweb.R

/**
 * Pill control. Unfocused it is a soft translucent capsule; focused it becomes solid white
 * with a black label, which is how tvOS renders a focused button.
 */
class TvButton @JvmOverloads constructor(
    context: Context,
    iconRes: Int = 0,
    label: String = "",
    private val accent: Boolean = false,
) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private val labelView = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(
            Ui.dp(context, if (label.isEmpty()) 14f else 20f),
            Ui.dp(context, 10f),
            Ui.dp(context, if (label.isEmpty()) 14f else 20f),
            Ui.dp(context, 10f),
        )

        if (iconRes != 0) {
            iconView.setImageResource(iconRes)
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iconView, LayoutParams(Ui.dp(context, 22f), Ui.dp(context, 22f)).apply {
                if (label.isNotEmpty()) rightMargin = Ui.dp(context, 10f)
            })
        }

        labelView.apply {
            styleText(16f)
            text = label
            maxLines = 1
            letterSpacing = 0.01f
        }
        if (label.isNotEmpty()) {
            addView(labelView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        background = Ui.rounded(
            context,
            if (accent) R.color.accent else R.color.surface_raised,
            22f,
            240,
        )
        applyColors(focused = false, enabled = true)
        Ui.focusLift(this, scale = 1.05f, liftDp = 8f)
        setOnFocusChangeListener { _, hasFocus ->
            animate().scaleX(if (hasFocus) 1.05f else 1f)
                .scaleY(if (hasFocus) 1.05f else 1f)
                .setDuration(150).start()
            elevation = if (hasFocus) Ui.dp(context, 8f).toFloat() else 0f
            applyColors(hasFocus, isEnabled)
        }
    }

    fun setLabel(value: String) {
        labelView.text = value
    }

    fun setIcon(res: Int) {
        iconView.setImageResource(res)
    }

    fun setIconVisible(visible: Boolean) {
        iconView.visibility = if (visible) VISIBLE else GONE
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        applyColors(hasFocus(), enabled)
        alpha = if (enabled) 1f else 0.35f
        isFocusable = enabled
    }

    private fun applyColors(focused: Boolean, enabled: Boolean) {
        val onLight = focused
        background = if (onLight) {
            Ui.rounded(context, R.color.surface_focused, 22f)
        } else {
            Ui.rounded(context, if (accent) R.color.accent else R.color.surface_raised, 22f, 240)
        }
        val tint = when {
            onLight -> Ui.color(context, R.color.text_on_light)
            accent -> Ui.color(context, R.color.text)
            else -> Ui.color(context, R.color.text)
        }
        labelView.setTextColor(tint)
        iconView.imageTintList = ColorStateList.valueOf(tint)
    }
}

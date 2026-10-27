package dev.sxch.tvweb.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.sxch.tvweb.R

/**
 * Content tile. Focus is carried by three signals at once, because a TV is watched from
 * across a room: the tile grows, the surface lifts, and a white rim appears.
 */
class TvCard(
    context: Context,
    iconRes: Int,
    title: String,
    private val onActivate: () -> Unit,
) : LinearLayout(context) {

    private fun surface(lifted: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = Ui.dp(context, 16f).toFloat()
        setColor(
            Ui.color(context, if (lifted) R.color.surface_raised else R.color.surface),
        )
        if (lifted) setStroke(Ui.dp(context, 2f), Ui.color(context, R.color.surface_focused))
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        isFocusable = true
        isFocusableInTouchMode = true
        clipChildren = false
        clipToPadding = false
        setPadding(Ui.dp(context, 22f), Ui.dp(context, 20f), Ui.dp(context, 22f), Ui.dp(context, 20f))
        background = surface(false)

        val icon = Ui.iconView(context, iconRes, tintRes = R.color.text)
        addView(icon, LayoutParams(Ui.dp(context, 26f), Ui.dp(context, 26f)).apply {
            bottomMargin = Ui.dp(context, 12f)
        })

        val titleView = TextView(context).apply {
            styleText(19f)
            text = title
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setOnClickListener { onActivate() }
        setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.07f else 1f)
                .scaleY(if (hasFocus) 1.07f else 1f)
                .setDuration(170)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            v.background = surface(hasFocus)
            v.elevation = if (hasFocus) Ui.dp(context, 18f).toFloat() else 0f
        }
    }
}

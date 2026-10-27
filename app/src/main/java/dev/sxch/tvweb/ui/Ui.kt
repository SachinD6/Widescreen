package dev.sxch.tvweb.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.sxch.tvweb.R

fun TextView.styleText(sizeSp: Float, textRes: Int = R.color.text) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(Ui.solidText(context, textRes))
    includeFontPadding = false
    gravity = Gravity.CENTER_VERTICAL
}

object Ui {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
            .toInt()

    fun color(context: Context, res: Int): Int = ContextCompat.getColor(context, res)

    fun cardBackground(context: Context, radius: Float = 10f): Drawable {
        val r = dp(context, radius).toFloat()
        val normal = GradientDrawable().apply {
            cornerRadius = r
            setColor(color(context, R.color.surface))
            setStroke(dp(context, 1f), color(context, R.color.stroke))
        }
        val focused = GradientDrawable().apply {
            cornerRadius = r
            setColor(color(context, R.color.surface_focused))
            setStroke(dp(context, 3f), color(context, R.color.accent))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    fun accentBackground(context: Context, radius: Float = 8f): Drawable {
        val r = dp(context, radius).toFloat()
        val normal = GradientDrawable().apply {
            cornerRadius = r
            setColor(color(context, R.color.accent))
        }
        val focused = GradientDrawable().apply {
            cornerRadius = r
            setColor(color(context, R.color.text))
            setStroke(dp(context, 3f), color(context, R.color.accent))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    fun brightText(context: Context): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(),
        ),
        intArrayOf(
            color(context, R.color.text),
            color(context, R.color.accent),
            color(context, R.color.text_dim),
        ),
    )

    fun solidText(context: Context, res: Int = R.color.text): ColorStateList =
        ColorStateList.valueOf(color(context, res))

}

package dev.sxch.tvweb.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.sxch.tvweb.R

fun TextView.styleText(sizeSp: Float, colorRes: Int = R.color.text) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(ContextCompat.getColor(context, colorRes))
    includeFontPadding = false
    gravity = Gravity.CENTER_VERTICAL
}

object Ui {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
            .toInt()

    fun color(context: Context, res: Int): Int = ContextCompat.getColor(context, res)

    fun rounded(context: Context, colorRes: Int, radiusDp: Float, alpha: Int = 255): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color(context, colorRes))
            this.alpha = alpha
        }

    /**
     * Apple TV focus treat: the item grows toward the viewer and picks up a shadow.
     * Parents must set clipChildren=false so the growth is not cut off.
     */
    fun focusLift(view: View, scale: Float = 1.06f, liftDp: Float = 10f) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) scale else 1f
            v.animate()
                .scaleX(target)
                .scaleY(target)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .start()
            v.stateListAnimator = null
            v.elevation = if (hasFocus) dp(v.context, liftDp).toFloat() else 0f
        }
    }

    fun iconView(
        context: Context,
        iconRes: Int,
        sizeDp: Float = 24f,
        tintRes: Int = R.color.text,
    ): ImageView = ImageView(context).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(color(context, tintRes))
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    fun iconDrawable(context: Context, iconRes: Int, tintRes: Int = R.color.text): Drawable? =
        ContextCompat.getDrawable(context, iconRes)?.mutate()?.apply {
            setTint(color(context, tintRes))
        }

    fun glyphForHost(host: String): Int = when {
        host.contains("youtube") || host.contains("twitch") || host.contains("vimeo") -> R.drawable.ic_play
        host.contains("wiki") || host.contains("medium") || host.contains("substack") -> R.drawable.ic_book
        host.contains("reddit") || host.contains("forum") || host.contains("news") -> R.drawable.ic_chat
        host.contains("archive") || host.contains("github") || host.contains("gitlab") -> R.drawable.ic_archive
        else -> R.drawable.ic_globe
    }
}

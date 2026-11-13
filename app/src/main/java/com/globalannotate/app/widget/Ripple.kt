package com.globalannotate.app.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Miuix-style touch feedback: a masked ripple instead of a flat pressed colour.
 * Views draw their own background, so the ripple goes into the foreground slot.
 */
object Ripple {

    fun color(context: Context): Int = ContextCompat.getColor(context, R.color.ga_ripple)

    fun circle(context: Context): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(color(context)),
        null,
        GradientDrawable().apply { shape = GradientDrawable.OVAL },
    )

    fun rounded(context: Context, radiusPx: Float): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(color(context)),
        null,
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
        },
    )

    fun View.applyCircleRipple() {
        foreground = circle(context)
    }

    fun View.applyRoundedRipple(radiusPx: Float) {
        foreground = rounded(context, radiusPx)
    }
}

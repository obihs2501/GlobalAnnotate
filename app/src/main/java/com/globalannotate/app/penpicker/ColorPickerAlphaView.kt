package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet

/** Opacity rail: checkerboard under a transparent → opaque gradient. */
class ColorPickerAlphaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        val saved = canvas.save()
        val path = android.graphics.Path().apply {
            addRoundRect(track, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        drawChecker(canvas, track, track.height() / 2f)
        canvas.restoreToCount(saved)

        trackPaint.shader = horizontalGradient(
            Color.TRANSPARENT,
            baseColor or 0xFF000000.toInt(),
        )
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }

    override fun thumbColor(): Int = baseColor
}

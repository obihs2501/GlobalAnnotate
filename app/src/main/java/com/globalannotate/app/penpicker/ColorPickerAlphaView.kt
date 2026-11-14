package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Opacity rail: checkerboard under a transparent → current-colour gradient. */
class ColorPickerAlphaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        drawChecker(canvas, track, radius)
        trackPaint.shader = horizontalGradient(
            Color.TRANSPARENT,
            baseColor or 0xFF000000.toInt(),
        )
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.shader = null
    }

    override fun thumbCentreColor(): Int = baseColor

    override fun ringColor(): Int = Color.WHITE
}

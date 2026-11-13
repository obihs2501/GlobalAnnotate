package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Hue rail: full-spectrum gradient, value 0..1 maps to 0..360°. */
class ColorPickerHueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        trackPaint.shader = horizontalGradient(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED,
        )
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }

    val hue: Float get() = progress * 360f
}

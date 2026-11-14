package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Hue rail, 0…360°, drawn like Mi Canvas `ColorPickerHueView`. */
class ColorPickerHueView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    init { max = 360 }

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        trackPaint.shader = horizontalGradient(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED,
        )
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.shader = null
    }

    override fun ringColor(): Int = Color.WHITE
}

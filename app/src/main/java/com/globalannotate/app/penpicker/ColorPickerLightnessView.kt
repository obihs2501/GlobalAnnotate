package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Brightness rail: black → the current colour at full value. */
class ColorPickerLightnessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        val bright = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))
        trackPaint.shader = horizontalGradient(Color.BLACK, bright)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }
}

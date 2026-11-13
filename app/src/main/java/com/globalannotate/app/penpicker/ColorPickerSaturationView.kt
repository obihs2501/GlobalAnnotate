package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Saturation rail: white → the current hue at full saturation. */
class ColorPickerSaturationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        val full = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        trackPaint.shader = horizontalGradient(Color.WHITE, full)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }
}

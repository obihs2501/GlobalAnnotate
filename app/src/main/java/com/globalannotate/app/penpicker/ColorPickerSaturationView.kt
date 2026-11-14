package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Saturation rail: grey → base colour. */
class ColorPickerSaturationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        val grey = Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))
        val full = Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))
        trackPaint.shader = horizontalGradient(grey, full)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.shader = null
    }

    override fun thumbCentreColor(): Int = baseColor

    override fun ringColor(): Int = Color.WHITE
}

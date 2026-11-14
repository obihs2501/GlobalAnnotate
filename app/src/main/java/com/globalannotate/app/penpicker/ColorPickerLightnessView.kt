package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet

/** Lightness rail: black → base colour → white, like Mi Canvas. */
class ColorPickerLightnessView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PickerBar(context, attrs) {

    override fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        val mid = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))
        trackPaint.shader = horizontalGradient(Color.BLACK, mid, Color.WHITE)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.shader = null
    }

    override fun thumbCentreColor(): Int = baseColor

    override fun ringColor(): Int = Color.WHITE
}

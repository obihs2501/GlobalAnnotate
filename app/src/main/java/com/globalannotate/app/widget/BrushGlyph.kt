package com.globalannotate.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import com.globalannotate.app.R
import com.globalannotate.app.model.PenStyle

/**
 * Mi Canvas brush artwork. The APK ships separate light and dark variants of
 * every pen; we bundle the dark set and invert luminance for the light theme so
 * the icons always match the surrounding surface, exactly like the original.
 * Selection is expressed only by the host lifting the glyph — no ring.
 */
class BrushGlyph(context: Context) : View(context) {

    var style: PenStyle = PenStyle.INK_PEN
        set(value) { field = value; invalidate() }
    var color: Int = Color.parseColor("#F4511E")
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }
    var large: Boolean = false
        set(value) { field = value; invalidate() }

    private val dst = RectF()
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val invertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val nightMatrix = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(nightMatrix)
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun artworkFor(style: PenStyle): Int = when (style) {
        PenStyle.PENCIL -> R.drawable.brush_pencil
        PenStyle.BALLPOINT -> R.drawable.brush_ballpoint
        PenStyle.INK_PEN -> R.drawable.brush_pen
        PenStyle.MARKER -> R.drawable.brush_marker
        PenStyle.HIGHLIGHTER -> R.drawable.brush_highlighter
        PenStyle.LASER -> R.drawable.brush_laser
        PenStyle.ERASER -> R.drawable.brush_eraser_pixel
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val bitmap = (context.getDrawable(artworkFor(style)) as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val drawWidth = h * ratio
            val left = (w - drawWidth) / 2f
            dst.set(left, 0f, left + drawWidth, h)
            canvas.drawBitmap(bitmap, null, dst, if (isNight()) null else lightPaint)

            if (!style.isEraser) {
                markerPaint.color = color
                val markerWidth = (drawWidth * 0.24f).coerceAtLeast(4f)
                canvas.drawRoundRect(
                    cx - markerWidth / 2f, h * 0.575f,
                    cx + markerWidth / 2f, h * 0.695f,
                    markerWidth * 0.45f, markerWidth * 0.45f, markerPaint,
                )
            }
        }
    }
}

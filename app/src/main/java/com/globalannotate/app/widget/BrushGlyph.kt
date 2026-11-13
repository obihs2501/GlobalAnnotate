package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import com.globalannotate.app.R
import com.globalannotate.app.model.PenStyle
import kotlin.math.min

/**
 * Mi Canvas' original brush artwork, extracted from the app's own resources
 * (transparent PNG/WebP, multiple densities). The colour chip on the barrel is
 * drawn at runtime in the current ink colour, exactly as the original does.
 * The host toolbar lifts the selected glyph so the chosen pen "pulls out".
 */
class BrushGlyph(context: Context) : View(context) {

    var style: PenStyle = PenStyle.PEN
        set(value) { field = value; invalidate() }
    var color: Int = Color.parseColor("#F4511E")
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }
    var large: Boolean = false
    var accentRing: Boolean = false
        set(value) { field = value; invalidate() }

    private val dst = RectF()
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(34, 255, 255, 255)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private fun artworkFor(style: PenStyle): Int = when (style) {
        PenStyle.PENCIL -> R.drawable.brush_pencil
        PenStyle.BALLPOINT -> R.drawable.brush_ballpoint
        PenStyle.PEN -> R.drawable.brush_pen
        PenStyle.MARKER -> R.drawable.brush_marker
        PenStyle.HIGHLIGHTER -> R.drawable.brush_highlighter
        PenStyle.LASER -> R.drawable.brush_laser
        PenStyle.ERASER_PIXEL -> R.drawable.brush_eraser_pixel
        PenStyle.ERASER_STROKE -> R.drawable.brush_eraser_stroke
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val unit = min(w, h)
        if (checked) canvas.drawCircle(cx, h * 0.52f, unit * 0.46f, haloPaint)

        val bitmap = (context.getDrawable(artworkFor(style)) as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val drawHeight = h
            val drawWidth = drawHeight * ratio
            val left = (w - drawWidth) / 2f
            dst.set(left, 0f, left + drawWidth, h)
            canvas.drawBitmap(bitmap, null, dst, null)

            if (!style.isEraser) {
                markerPaint.color = color
                val markerWidth = (drawWidth * 0.24f).coerceAtLeast(4f)
                val top = h * 0.575f
                val bottom = h * 0.695f
                canvas.drawRoundRect(
                    cx - markerWidth / 2f, top,
                    cx + markerWidth / 2f, bottom,
                    markerWidth * 0.45f, markerWidth * 0.45f, markerPaint,
                )
            }
        }

        if (checked) {
            ringPaint.color = Color.parseColor("#FF5B52")
            ringPaint.strokeWidth = w * 0.045f
            canvas.drawCircle(cx, h * 0.52f, unit * 0.46f, ringPaint)
        }
        if (accentRing && !checked) {
            ringPaint.color = if (style.isEraser) Color.parseColor("#9AA0A6") else color
            ringPaint.strokeWidth = w * 0.035f
            canvas.drawCircle(cx, h * 0.52f, unit * 0.43f, ringPaint)
        }
    }
}

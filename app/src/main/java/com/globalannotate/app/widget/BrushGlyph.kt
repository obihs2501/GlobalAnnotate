package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.PenStyle

/**
 * One brush in the bar, composed exactly like Mi Canvas composes its picker:
 * the body artwork untouched, the head tinted with the ink colour, and the
 * highlight layer on top (pencil / marker / watercolour ship one).
 */
class BrushGlyph @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : View(context, attrs) {

    var style: PenStyle = PenStyle.INK_PEN
        set(value) { field = value; invalidate() }
    var color: Int = Color.parseColor("#E8464A")
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }

    private val dst = RectF()
    private val tint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

    private fun isNight(): Boolean {
        val capsule = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(capsule) + 0.587f * Color.green(capsule) + 0.114f * Color.blue(capsule)
        return lum < 128f
    }

    private fun prefix(style: PenStyle): String = when (style) {
        PenStyle.BALLPOINT -> "ballpoint"
        PenStyle.PENCIL -> "pencil"
        PenStyle.INK_PEN -> "pen"
        PenStyle.MARKER, PenStyle.HIGHLIGHTER -> "mark"
        PenStyle.WATER_COLOR -> "watercolor"
        PenStyle.ERASER -> "eraser"
    }

    private fun drawableId(name: String): Int =
        resources.getIdentifier(name, "drawable", context.packageName)

    private fun bitmapOf(id: Int): Bitmap? {
        if (id == 0) return null
        return runCatching {
            (ContextCompat.getDrawable(context, id) as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }.onFailure { android.util.Log.w("BrushGlyph", "drawable $id failed: $it") }.getOrNull()
    }

    /** Resolve the first name that exists; the night artwork is a fallback too. */
    private fun firstBitmap(vararg names: String): Bitmap? {
        for (name in names) {
            val bmp = bitmapOf(drawableId(name))
            if (bmp != null) return bmp
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val night = isNight()
        val suffix = if (night) "_dark" else ""
        val name = prefix(style)
        if (style == PenStyle.ERASER) {
            val body = firstBitmap("pk_eraser_tool") ?: return
            // The eraser artwork is a white luminance mask; tint it so it has a
            // body on a white capsule, exactly like the original's rubber look.
            val eraserPaint = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
            ).apply {
                colorFilter = PorterDuffColorFilter(
                    if (night) Color.parseColor("#8E8E8E") else Color.parseColor("#D9D9D9"),
                    PorterDuff.Mode.SRC_IN,
                )
            }
            fit(body, w, h, canvas, eraserPaint)
            return
        }
        val body = firstBitmap("pk_${name}_body$suffix", "pk_${name}_body") ?: return
        fit(body, w, h, canvas, null)
        val head = firstBitmap("pk_${name}_head$suffix", "pk_${name}_head")
        if (head != null) {
            tint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            fit(head, w, h, canvas, tint)
        }
        val highlight = firstBitmap("pk_${name}_highlight$suffix", "pk_${name}_highlight")
        if (highlight != null) fit(highlight, w, h, canvas, null)
    }

    private fun fit(src: Bitmap, w: Float, h: Float, canvas: Canvas, paint: android.graphics.Paint?) {
        val ratio = src.width.toFloat() / src.height.toFloat()
        val drawHeight = h
        val drawWidth = drawHeight * ratio
        val left = (w - drawWidth) / 2f
        dst.set(left, 0f, left + drawWidth, h)
        canvas.drawBitmap(src, null, dst, paint)
    }
}

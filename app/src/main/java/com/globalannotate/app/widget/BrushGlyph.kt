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

    /**
     * Seat depth in dp, drawn inside the view's own canvas: 0 is the out position
     * (tip at the capsule top, base on the capsule floor), OUTWARD_DP is seated.
     * A seated pen's tail runs past the window edge and is clipped there, so the
     * outward slide gives back exactly the length it hides.
     */
    var artOffsetDp: Float = 18f
        set(value) { field = value; invalidate() }

    /** Drawn length of the pen, independent of the slot's travel headroom. */
    var artLengthDp: Float = 124f
        set(value) { field = value; invalidate() }

    /** Orb mode: centre the artwork on the disc instead of seating it at the top. */
    var centreVertically: Boolean = false
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
            canvas.save()
            canvas.translate(0f, artOffsetDp * resources.displayMetrics.density)
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
            fit(body, w, h, canvas, eraserPaint, "eraser_body")
            canvas.restore()
            return
        }
        canvas.save()
        canvas.translate(0f, artOffsetDp * resources.displayMetrics.density)
        val body = firstBitmap("pk_${name}_body$suffix", "pk_${name}_body") ?: return
        fit(body, w, h, canvas, null, "${name}_body")
        val head = firstBitmap("pk_${name}_head$suffix", "pk_${name}_head")
        if (head != null) {
            tint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            fit(head, w, h, canvas, tint, "${name}_head")
        }
        val highlight = firstBitmap("pk_${name}_highlight$suffix", "pk_${name}_highlight")
        if (highlight != null) fit(highlight, w, h, canvas, null, "${name}_highlight")
        canvas.restore()
    }

    private fun fit(
        src: Bitmap,
        w: Float,
        h: Float,
        canvas: Canvas,
        paint: android.graphics.Paint?,
        label: String = "",
    ) {
        val ratio = src.width.toFloat() / src.height.toFloat()
        val drawHeight = (artLengthDp * resources.displayMetrics.density).coerceAtMost(h)
        val drawWidth = drawHeight * ratio
        val left = (w - drawWidth) / 2f
        // The bar seats tools from the capsule floor (top = 0); the orb centres the
        // pen on its disc. Centring was previously applied to both, which made the
        // orb's pen float above the ball and read oversized.
        val top = if (centreVertically) (h - drawHeight) / 2f else 0f
        dst.set(left, top + artOffsetDp * resources.displayMetrics.density, left + drawWidth, top + drawHeight + artOffsetDp * resources.displayMetrics.density)
        if (label.isNotEmpty() && logged.add(label)) {
            // visible overhang differs whenever a bitmap carries transparent
            // padding at the top. Log it once to compare the tools.
            android.util.Log.i(
                "BrushGlyph",
                "$label src=${src.width}x${src.height} dst=$dst " +
                    "opaqueTop=${opaqueTopFraction(src)} view=${w}x$h offset=$artOffsetDp",
            )
        }
        canvas.drawBitmap(src, null, dst, paint)
    }

    private val logged = HashSet<String>()

    /** First row that is not fully transparent, as a fraction of the height. */
    private fun opaqueTopFraction(src: Bitmap): Float {
        val pixels = IntArray(src.width)
        for (y in 0 until src.height) {
            src.getPixels(pixels, 0, src.width, 0, y, src.width, 1)
            for (p in pixels) {
                if ((p ushr 24) != 0) return y.toFloat() / src.height
            }
        }
        return 0f
    }
}

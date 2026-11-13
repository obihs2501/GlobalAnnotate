package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.PenStyle

/**
 * Mi Canvas toolbar brush, rebuilt the way the original composes it: a neutral
 * body silhouette (`pk_*_body`) plus a tiny ink-coloured head (`pk_*_head`).
 * The APK only ships the dark-theme body artwork, so the light theme inverts
 * luminance instead of swapping assets. Selection lifts the glyph — no ring.
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
    private var headPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    /**
     * Decide from the resolved capsule colour instead of uiMode: an overlay
     * Service keeps the configuration it was created with, so uiMode goes stale
     * after the user switches theme. The colour resource always follows.
     */
    private fun isNight(): Boolean {
        val capsule = ContextCompat.getColor(context, R.color.ga_capsule_bg)
        val luminance = 0.299f * Color.red(capsule) +
            0.587f * Color.green(capsule) +
            0.114f * Color.blue(capsule)
        return luminance < 128f
    }

    private fun bodyFor(style: PenStyle): Int = when (style) {
        PenStyle.PENCIL -> R.drawable.glyph_pencil_body
        PenStyle.BALLPOINT -> R.drawable.glyph_ballpoint_body
        PenStyle.INK_PEN -> R.drawable.glyph_pen_body
        PenStyle.MARKER, PenStyle.HIGHLIGHTER -> R.drawable.glyph_mark_body
        PenStyle.WATER_COLOR -> R.drawable.glyph_watercolor_body
        PenStyle.LASER -> R.drawable.glyph_pen_body
        PenStyle.ERASER -> R.drawable.glyph_eraser
    }

    private fun headFor(style: PenStyle): Int = when (style) {
        PenStyle.PENCIL -> R.drawable.glyph_pencil_head
        PenStyle.BALLPOINT -> R.drawable.glyph_ballpoint_head
        PenStyle.INK_PEN, PenStyle.LASER -> R.drawable.glyph_pen_head
        PenStyle.MARKER, PenStyle.HIGHLIGHTER -> R.drawable.glyph_mark_head
        PenStyle.WATER_COLOR -> R.drawable.glyph_watercolor_head
        PenStyle.ERASER -> 0
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val body = (context.getDrawable(bodyFor(style)) as? BitmapDrawable)?.bitmap ?: return
        val ratio = body.width.toFloat() / body.height.toFloat()
        val drawWidth = h * ratio
        val left = (w - drawWidth) / 2f
        dst.set(left, 0f, left + drawWidth, h)
        canvas.drawBitmap(body, null, dst, if (isNight()) null else lightPaint)

        // The head is the ink-colour accent on the pen tip in the original.
        val headRes = headFor(style)
        if (headRes != 0) {
            val head = (context.getDrawable(headRes) as? BitmapDrawable)?.bitmap ?: return
            headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(head, null, dst, headPaint)
        }

        // Laser has no asset in the original; it marks its brush with a beam dot.
        if (style == PenStyle.LASER) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawCircle(w / 2f, h * 0.14f, drawWidth * 0.16f, dot)
        }
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
 * Mi Canvas toolbar brush: a neutral body plus an ink-coloured head, exactly the
 * way the APK composes `pk_*_body` + `pk_*_head`. The APK ships a light set for
 * the light theme and a night set for the dark theme, so both are bundled and
 * picked by the resolved capsule colour (an overlay service keeps a stale
 * uiMode, so uiMode cannot be trusted here).
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
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun isNight(): Boolean {
        val capsule = ContextCompat.getColor(context, R.color.ga_capsule_bg)
        val luminance = 0.299f * Color.red(capsule) +
            0.587f * Color.green(capsule) +
            0.114f * Color.blue(capsule)
        return luminance < 128f
    }

    private fun bodyFor(style: PenStyle, night: Boolean): Int = when (style) {
        PenStyle.PENCIL -> if (night) R.drawable.glyph_pencil_body_dark else R.drawable.glyph_pencil_body
        PenStyle.BALLPOINT ->
            if (night) R.drawable.glyph_ballpoint_body_dark else R.drawable.glyph_ballpoint_body
        PenStyle.INK_PEN -> if (night) R.drawable.glyph_pen_body_dark else R.drawable.glyph_pen_body
        PenStyle.MARKER, PenStyle.HIGHLIGHTER ->
            if (night) R.drawable.glyph_mark_body_dark else R.drawable.glyph_mark_body
        PenStyle.WATER_COLOR ->
            if (night) R.drawable.glyph_watercolor_body_dark else R.drawable.glyph_watercolor_body
        PenStyle.LASER -> if (night) R.drawable.glyph_pen_body_dark else R.drawable.glyph_pen_body
        PenStyle.ERASER -> R.drawable.glyph_eraser
    }

    private fun headFor(style: PenStyle, night: Boolean): Int = when (style) {
        PenStyle.PENCIL -> if (night) R.drawable.glyph_pencil_head_dark else R.drawable.glyph_pencil_head
        PenStyle.BALLPOINT ->
            if (night) R.drawable.glyph_ballpoint_head_dark else R.drawable.glyph_ballpoint_head
        PenStyle.INK_PEN, PenStyle.LASER ->
            if (night) R.drawable.glyph_pen_head_dark else R.drawable.glyph_pen_head
        PenStyle.MARKER, PenStyle.HIGHLIGHTER ->
            if (night) R.drawable.glyph_mark_head_dark else R.drawable.glyph_mark_head
        PenStyle.WATER_COLOR ->
            if (night) R.drawable.glyph_watercolor_head_dark else R.drawable.glyph_watercolor_head
        PenStyle.ERASER -> 0
    }

    private fun bitmap(res: Int) =
        (ContextCompat.getDrawable(context, res) as? BitmapDrawable)?.bitmap

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val night = isNight()

        val body = bitmap(bodyFor(style, night)) ?: return
        // The eraser artwork has its own aspect (498x891); keep it undistorted.
        val bodyRatio = body.width.toFloat() / body.height.toFloat()
        val drawHeight = if (style.isEraser) h * 0.82f else h
        val drawWidth = drawHeight * bodyRatio
        val left = (w - drawWidth) / 2f
        val top = (h - drawHeight) / 2f
        dst.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(body, null, dst, null)

        // Head pieces are positioned inside the same 144x369 canvas, so they use
        // the body rect and only differ by their tint.
        val headRes = headFor(style, night)
        if (headRes != 0) {
            val head = bitmap(headRes) ?: return
            tintPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(head, null, dst, tintPaint)
        }
    }
}

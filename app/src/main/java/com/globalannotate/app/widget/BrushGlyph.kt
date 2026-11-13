package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.globalannotate.app.model.PenStyle

/**
 * Painterly brush glyph, mirroring Mi Canvas: a shaded pen body, a distinct nib
 * per brush type, and a small colour marker. The selected brush gets a soft
 * circular highlight and a red accent ring.
 */
class BrushGlyph(context: Context) : View(context) {

    var style: PenStyle = PenStyle.PEN
        set(value) { field = value; invalidate() }
    var color: Int = Color.parseColor("#F4511E")
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nibPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(38, 255, 255, 255)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF5B52")
        strokeWidth = 3f
    }
    private val rect = RectF()
    private val nib = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (checked) {
            canvas.drawCircle(w / 2f, h / 2f, kotlin.math.min(w, h) * 0.46f, highlightPaint)
        }

        val left = w * 0.36f
        val right = w * 0.64f
        val bodyTop = h * 0.30f
        val bodyBottom = h * 0.88f

        bodyPaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.parseColor("#5A5C61"),
                Color.parseColor("#2A2C30"),
                Color.parseColor("#141518"),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        rect.set(left, bodyTop, right, bodyBottom)
        canvas.drawRoundRect(rect, w * 0.12f, w * 0.12f, bodyPaint)

        // nib
        nib.reset()
        nibPaint.shader = null
        nibPaint.color = Color.parseColor("#B9BCC2")
        when (style) {
            PenStyle.PEN -> {
                nib.moveTo(w * 0.50f, h * 0.06f)
                nib.lineTo(left, h * 0.34f)
                nib.lineTo(right, h * 0.34f)
                nib.close()
            }
            PenStyle.PENCIL -> {
                nibPaint.color = Color.parseColor("#C9A987")
                nib.moveTo(w * 0.50f, h * 0.05f)
                nib.lineTo(left - w * 0.02f, h * 0.34f)
                nib.lineTo(right + w * 0.02f, h * 0.34f)
                nib.close()
            }
            PenStyle.MARKER -> {
                nib.moveTo(left - w * 0.06f, h * 0.12f)
                nib.lineTo(right + w * 0.06f, h * 0.18f)
                nib.lineTo(right, h * 0.34f)
                nib.lineTo(left, h * 0.34f)
                nib.close()
            }
            PenStyle.HIGHLIGHTER -> {
                nib.moveTo(left - w * 0.08f, h * 0.16f)
                nib.lineTo(right + w * 0.08f, h * 0.10f)
                nib.lineTo(right + w * 0.05f, h * 0.34f)
                nib.lineTo(left - w * 0.05f, h * 0.34f)
                nib.close()
            }
            PenStyle.LASER -> {
                nib.moveTo(w * 0.50f, h * 0.04f)
                nib.lineTo(left + w * 0.04f, h * 0.34f)
                nib.lineTo(right - w * 0.04f, h * 0.34f)
                nib.close()
                nibPaint.color = Color.parseColor("#E6E8EC")
            }
            PenStyle.ERASER_PIXEL -> {
                nibPaint.color = Color.parseColor("#D8DADE")
                rect.set(left - w * 0.04f, h * 0.16f, right + w * 0.04f, h * 0.34f)
                canvas.drawRoundRect(rect, w * 0.08f, w * 0.08f, nibPaint)
                nib.reset()
            }
            PenStyle.ERASER_STROKE -> {
                nibPaint.color = Color.parseColor("#D8DADE")
                rect.set(left - w * 0.04f, h * 0.16f, right + w * 0.04f, h * 0.34f)
                canvas.drawRoundRect(rect, w * 0.08f, w * 0.08f, nibPaint)
                nib.reset()
            }
        }
        if (!nib.isEmpty) canvas.drawPath(nib, nibPaint)

        // colour marker on the barrel
        markerPaint.color = color
        rect.set(w * 0.455f, h * 0.58f, w * 0.545f, h * 0.70f)
        canvas.drawRoundRect(rect, w * 0.045f, w * 0.045f, markerPaint)

        if (checked) {
            ringPaint.strokeWidth = w * 0.055f
            canvas.drawCircle(w / 2f, h / 2f, kotlin.math.min(w, h) * 0.46f, ringPaint)
        }
    }
}

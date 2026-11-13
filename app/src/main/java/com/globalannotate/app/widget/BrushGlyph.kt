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
import kotlin.math.min

/**
 * Painterly "pen standing in the rack" glyph, in the spirit of Mi Canvas.
 * A shaded cylindrical barrel (5-stop gradient + specular strip + metal ring),
 * a distinct nib per brush, and a colour marker on the barrel. The host toolbar
 * lifts the selected glyph with translationY so the chosen pen "pulls out" of
 * the rack.
 */
class BrushGlyph(context: Context) : View(context) {

    var style: PenStyle = PenStyle.PEN
        set(value) { field = value; invalidate() }
    var color: Int = Color.parseColor("#F4511E")
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }

    var large: Boolean = false
        set(value) { field = value; invalidate() }
    var accentRing: Boolean = false
        set(value) { field = value; invalidate() }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val metalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nibPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nibDetail = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val nib = Path()

    init {
        haloPaint.style = Paint.Style.FILL
        haloPaint.color = Color.argb(34, 255, 255, 255)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.color = Color.parseColor("#FF5B52")
        nibDetail.style = Paint.Style.STROKE
        nibDetail.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val unit = min(w, h)
        val left = if (large) w * 0.38f else w * 0.30f
        val right = if (large) w * 0.62f else w * 0.70f
        val bodyTop = h * 0.30f
        val bodyBottom = h * 0.92f
        val corner = w * 0.10f

        if (checked) {
            canvas.drawCircle(cx, h * 0.52f, unit * 0.46f, haloPaint)
        }

        // Cylindrical barrel: bright edge -> shadow -> core -> shadow -> bright edge
        bodyPaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(
                Color.parseColor("#74777D"),
                Color.parseColor("#303236"),
                Color.parseColor("#18191C"),
                Color.parseColor("#33353A"),
                Color.parseColor("#6C6F75"),
            ),
            floatArrayOf(0f, 0.18f, 0.5f, 0.84f, 1f),
            Shader.TileMode.CLAMP,
        )
        rect.set(left, bodyTop, right, bodyBottom)
        canvas.drawRoundRect(rect, corner, corner, bodyPaint)

        // Specular strip
        highlightPaint.color = Color.argb(70, 255, 255, 255)
        rect.set(left + w * 0.055f, bodyTop + h * 0.02f, left + w * 0.105f, bodyBottom - h * 0.02f)
        canvas.drawRoundRect(rect, w * 0.025f, w * 0.025f, highlightPaint)

        // Metal ring below the nib
        metalPaint.color = Color.parseColor("#C6C9CF")
        rect.set(left, h * 0.325f, right, h * 0.355f)
        canvas.drawRect(rect, metalPaint)
        metalPaint.color = Color.parseColor("#7A7D83")
        rect.set(left, h * 0.352f, right, h * 0.362f)
        canvas.drawRect(rect, metalPaint)

        drawNib(canvas, w, h, left, right)

        // Colour marker on the barrel
        markerPaint.color = color
        rect.set(cx - w * 0.045f, h * 0.60f, cx + w * 0.045f, h * 0.73f)
        canvas.drawRoundRect(rect, w * 0.04f, w * 0.04f, markerPaint)

        if (checked) {
            ringPaint.strokeWidth = w * 0.045f
            canvas.drawCircle(cx, h * 0.52f, unit * 0.46f, ringPaint)
        }
        if (accentRing && !checked) {
            ringPaint.color = if (style.isEraser) Color.parseColor("#9AA0A6") else color
            ringPaint.strokeWidth = w * 0.035f
            canvas.drawCircle(cx, h * 0.52f, unit * 0.43f, ringPaint)
        }
    }

    private fun drawNib(canvas: Canvas, w: Float, h: Float, left: Float, right: Float) {
        nib.reset()
        nibPaint.shader = null
        nibPaint.color = Color.parseColor("#B9BCC2")
        nibDetail.color = Color.parseColor("#4A4C50")
        nibDetail.strokeWidth = w * 0.022f
        val cx = w / 2f

        when (style) {
            PenStyle.PEN -> {
                nib.moveTo(cx, h * 0.02f)
                nib.lineTo(left + w * 0.02f, h * 0.31f)
                nib.lineTo(right - w * 0.02f, h * 0.31f)
                nib.close()
                canvas.drawPath(nib, nibPaint)
                canvas.drawLine(cx, h * 0.09f, cx, h * 0.26f, nibDetail)
                nibPaint.color = Color.parseColor("#E4E6EA")
                canvas.drawCircle(cx, h * 0.25f, w * 0.035f, nibPaint)
            }
            PenStyle.PENCIL -> {
                nibPaint.color = Color.parseColor("#D8B68C")
                nib.moveTo(cx, h * 0.02f)
                nib.lineTo(left - w * 0.01f, h * 0.31f)
                nib.lineTo(right + w * 0.01f, h * 0.31f)
                nib.close()
                canvas.drawPath(nib, nibPaint)
                nibPaint.color = Color.parseColor("#3A3C40")
                val tip = Path().apply {
                    moveTo(cx, h * 0.02f)
                    lineTo(cx - w * 0.035f, h * 0.10f)
                    lineTo(cx + w * 0.035f, h * 0.10f)
                    close()
                }
                canvas.drawPath(tip, nibPaint)
            }
            PenStyle.MARKER -> {
                nib.moveTo(left - w * 0.05f, h * 0.10f)
                nib.lineTo(right + w * 0.05f, h * 0.16f)
                nib.lineTo(right, h * 0.31f)
                nib.lineTo(left, h * 0.31f)
                nib.close()
                canvas.drawPath(nib, nibPaint)
                nibPaint.color = color
                rect.set(left - w * 0.03f, h * 0.12f, right + w * 0.03f, h * 0.18f)
                canvas.drawRect(rect, nibPaint)
            }
            PenStyle.HIGHLIGHTER -> {
                nib.moveTo(left - w * 0.08f, h * 0.15f)
                nib.lineTo(right + w * 0.08f, h * 0.09f)
                nib.lineTo(right + w * 0.05f, h * 0.31f)
                nib.lineTo(left - w * 0.05f, h * 0.31f)
                nib.close()
                canvas.drawPath(nib, nibPaint)
                nibPaint.color = color
                rect.set(left - w * 0.07f, h * 0.12f, right + w * 0.07f, h * 0.19f)
                canvas.drawRect(rect, nibPaint)
            }
            PenStyle.LASER -> {
                nib.moveTo(cx, h * 0.02f)
                nib.lineTo(left + w * 0.07f, h * 0.31f)
                nib.lineTo(right - w * 0.07f, h * 0.31f)
                nib.close()
                nibPaint.color = Color.parseColor("#E6E8EC")
                canvas.drawPath(nib, nibPaint)
                nibPaint.color = Color.parseColor("#FF5B52")
                canvas.drawCircle(cx, h * 0.055f, w * 0.055f, nibPaint)
            }
            PenStyle.ERASER_PIXEL -> {
                nibPaint.color = Color.parseColor("#DEE0E4")
                rect.set(left - w * 0.04f, h * 0.13f, right + w * 0.04f, h * 0.31f)
                canvas.drawRoundRect(rect, w * 0.07f, w * 0.07f, nibPaint)
                nibPaint.color = Color.parseColor("#9AA0A6")
                rect.set(left - w * 0.04f, h * 0.27f, right + w * 0.04f, h * 0.31f)
                canvas.drawRect(rect, nibPaint)
            }
            PenStyle.ERASER_STROKE -> {
                nibPaint.color = Color.parseColor("#DEE0E4")
                rect.set(left - w * 0.04f, h * 0.13f, right + w * 0.04f, h * 0.31f)
                canvas.drawRoundRect(rect, w * 0.07f, w * 0.07f, nibPaint)
                nibDetail.color = Color.parseColor("#8A8E94")
                nibDetail.strokeWidth = w * 0.03f
                val wave = Path().apply {
                    moveTo(left, h * 0.22f)
                    quadTo(cx, h * 0.14f, right, h * 0.22f)
                }
                canvas.drawPath(wave, nibDetail)
            }
        }
    }
}

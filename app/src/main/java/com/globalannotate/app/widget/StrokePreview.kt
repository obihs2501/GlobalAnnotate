package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.globalannotate.app.model.BrushConfig

/**
 * The 110dp sample stroke at the top of the pen popup. Mi Canvas ships a PNG
 * per pen; we draw the same shape live so it follows colour and size.
 */
class StrokePreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val grain = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Mi Canvas clips the sample to the popup's 36dp corners.
        val radius = 36f * resources.displayMetrics.density
        val path = Path().apply {
            addRoundRect(
                android.graphics.RectF(0f, 0f, w, h),
                floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                Path.Direction.CW,
            )
        }
        val saved = canvas.save()
        canvas.clipPath(path)
        val style = brush.style
        val c = brush.effectiveColor
        paint.color = c
        paint.strokeCap = if (style.squareCap) Paint.Cap.SQUARE else Paint.Cap.ROUND
        paint.strokeWidth = style.toPixels(brush.size, resources.displayMetrics.density)
            .coerceIn(1.5f, h * 0.35f)
        path.reset()
        path.moveTo(w * 0.10f, h * 0.62f)
        path.cubicTo(w * 0.30f, h * 0.92f, w * 0.44f, h * 0.14f, w * 0.64f, h * 0.44f)
        path.cubicTo(w * 0.78f, h * 0.62f, w * 0.84f, h * 0.24f, w * 0.92f, h * 0.36f)

        if (style.softEdge) {
            for (pass in 0 until 3) {
                canvas.drawPath(path, Paint(paint).apply {
                    alpha = (Color.alpha(c) * (0.55f - pass * 0.15f)).toInt()
                    strokeWidth = paint.strokeWidth * (1f + pass * 0.45f)
                })
            }
            return
        }
        if (style.glow) {
            canvas.drawPath(path, Paint(paint).apply { alpha = 70; strokeWidth = paint.strokeWidth * 2.4f })
        }
        canvas.drawPath(path, paint)
        if (style.inkHighlight) {
            canvas.drawPath(path, Paint(paint).apply {
                strokeWidth = paint.strokeWidth * 0.3f
                color = Color.argb(80, 255, 255, 255)
            })
        }
        canvas.restoreToCount(saved)
        if (style.grain) {
            grain.color = Color.argb(
                (Color.alpha(c) * 0.45f).toInt(), Color.red(c), Color.green(c), Color.blue(c),
            )
            var t = 0f
            while (t < 1f) {
                canvas.drawCircle(
                    w * (0.10f + t * 0.82f),
                    h * (0.56f - 0.22f * kotlin.math.sin(t * 9.4f)),
                    0.6f * resources.displayMetrics.density,
                    grain,
                )
                t += 0.012f
            }
        }
    }
}

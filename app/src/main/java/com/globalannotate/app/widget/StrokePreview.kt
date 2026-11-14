package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.globalannotate.app.canvas.InkEngine
import com.globalannotate.app.canvas.InkPoint
import com.globalannotate.app.canvas.InkStroke
import com.globalannotate.app.model.BrushConfig
import kotlin.math.sin

/**
 * The 110dp sample at the top of a brush popup. Mi Canvas ships a PNG of the
 * brush's signature stroke; we render the very same engine that paints the
 * canvas, so what you see is what the pen will do.
 */
class StrokePreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; sample = null; invalidate() }

    private val engine = InkEngine().also { it.setDensity(resources.displayMetrics.density) }
    private var sample: InkStroke? = null
    private val density = resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sample = null
    }

    private fun build(): InkStroke {
        val b = brush
        val stroke = InkStroke(
            color = if (b.style.isEraser) Color.parseColor("#D0D0D0") else b.effectiveColor,
            style = b.style,
        )
        val n = 48
        val base = if (b.style.isEraser) {
            b.style.toPixels(b.eraserSize, density)
        } else {
            b.style.toPixels(b.size, density)
        }
        // Mi Canvas renders its sample as a diagonal stroke across the preview,
        // width ~= size * density, with the pen's own profile.
        val width = (b.style.toPixels(b.size, density) * 0.9f).coerceAtMost(height * 0.55f)
        val x0 = width * 0.22f
        val x1 = this.width - width * 0.22f
        val y0 = height * 0.72f
        val y1 = height * 0.28f
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            val pressure = (0.85f - 0.25f * kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0.5f, 1f)
            val w = when (b.style) {
                com.globalannotate.app.model.PenStyle.INK_PEN ->
                    width * (0.55f + 0.45f * kotlin.math.sin(Math.PI * t).toFloat())
                else -> width
            }
            stroke.points.add(InkPoint(x, y, w.coerceAtLeast(1f), 0.6f))
        }
        return stroke
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val s = sample ?: build().also { sample = it }
        engine.draw(canvas, s, 0f)
    }
}

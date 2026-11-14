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
        // Mi Canvas' sample is one S-wave across the strip: it dips, rises and
        // dips again, the stroke is thinnest at both ends. The old diagonal is
        // what made the preview look wrong next to the reference.
        val width = base.coerceAtMost(height * 0.5f)
        val margin = width * 0.6f + 2f * density
        val x0 = margin
        val x1 = this.width - margin
        val cy = height * 0.5f
        val amp = (height * 0.20f).coerceAtLeast(2f * density)
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = x0 + (x1 - x0) * t
            val y = cy + amp * kotlin.math.sin(2f * Math.PI.toFloat() * t)
            // Pressure: gentle start, full body, gentle end.
            val pressure = 0.35f + 0.65f * kotlin.math.sin(Math.PI.toFloat() * t)
            val w = (width * pressure).coerceAtLeast(1f)
            stroke.points.add(InkPoint(x, y, w, 0.6f))
        }
        return stroke
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val s = sample ?: build().also { sample = it }
        engine.draw(canvas, s, 0f)
    }
}

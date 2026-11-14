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
        val padX = width * 0.10f
        val padY = height * 0.30f
        val span = width - padX * 2
        val amp = (height * 0.5f - padY).coerceAtLeast(6f)
        val n = 72
        val base = if (b.style.isEraser) {
            b.style.toPixels(b.eraserSize, density)
        } else {
            b.style.toPixels(b.size, density)
        }
        for (i in 0..n) {
            val t = i / n.toFloat()
            val pressure = (0.15f + 0.85f * sin(Math.PI * t).toFloat()).coerceIn(0.05f, 1f)
            val x = padX + span * t
            val y = height / 2f + amp * sin((t * 2.2f) * Math.PI.toFloat() - 0.9f)
            val w = when (b.style) {
                com.globalannotate.app.model.PenStyle.BALLPOINT -> base
                com.globalannotate.app.model.PenStyle.INK_PEN ->
                    base * (0.18f + 1.25f * pressure * (0.45f + 1.1f * b.sensitivity))
                com.globalannotate.app.model.PenStyle.MARKER,
                com.globalannotate.app.model.PenStyle.HIGHLIGHTER -> base
                com.globalannotate.app.model.PenStyle.WATER_COLOR -> base * (0.7f + 0.5f * pressure)
                else -> base * (0.85f + 0.3f * pressure)
            }
            stroke.points.add(InkPoint(x, y, w.coerceAtLeast(1f), 0.35f))
        }
        return stroke
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val s = sample ?: build().also { sample = it }
        engine.draw(canvas, s, 0f)
    }
}

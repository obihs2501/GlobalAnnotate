package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle

/**
 * Pen settings popup. Which rows appear is driven by the pen type, matching
 * Mi Canvas' `m24536I1lii()`:
 *   - 粗细 (size): every pen, range = that pen's own min..max
 *   - 不透明度 (alpha): every pen
 *   - 灵敏度: fountain pen ("钢笔灵敏度") and pencil ("铅笔灵敏度") only
 *   - 笔尖大小: fountain pen only
 * The eraser never opens this panel.
 */
class BrushPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    enum class ParamKind { SIZE, SENSITIVITY, TIP, ALPHA }

    interface Listener {
        fun onParam(kind: ParamKind, value: Float)
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val preview: StrokePreview
    private val rows = LinearLayout(context)
    private val sliders = mutableMapOf<ParamKind, TrackSlider>()
    private val valueViews = mutableMapOf<ParamKind, TextView>()

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; rebuild(value) }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(20).toInt(), dp(14).toInt(), dp(20).toInt(), dp(18).toInt())

        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(context).apply {
            text = "笔刷设置"
            textSize = 14f
            setTextColor(Color.parseColor("#E8EAED"))
            layoutParams = LinearLayout.LayoutParams(0, dp(36).toInt(), 1f)
        })
        head.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_close
            onTap = { listener.onClose() }
            layoutParams = LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt())
        })
        addView(head)

        preview = StrokePreview(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(84).toInt())
        }
        addView(preview)
        addView(rows, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun rebuild(brush: BrushConfig) {
        preview.brush = brush
        val style = brush.style
        if (style.isEraser) return
        rows.removeAllViews()
        sliders.clear()
        valueViews.clear()

        addSlider(ParamKind.SIZE, "笔画粗细", sizeProgress(brush, style))
        if (style.showSensitivity) {
            addSlider(ParamKind.SENSITIVITY, style.sensitivityLabel, brush.sensitivity)
        }
        if (style.showTipSize) {
            addSlider(ParamKind.TIP, "笔尖大小", (brush.tipSize - 1f) / 3f)
        }
        addSlider(ParamKind.ALPHA, "不透明度", brush.alpha / 255f, checkered = true)
    }

    private fun sizeProgress(brush: BrushConfig, style: PenStyle): Float {
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        return ((brush.size - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun addSlider(kind: ParamKind, label: String, initial: Float, checkered: Boolean = false) {
        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#E8EAED"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val valueView = TextView(context).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#C9CDD4"))
        }
        head.addView(valueView)
        rows.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(12).toInt()
        })

        val slider = TrackSlider(context).apply {
            progress = initial
            this.checkered = checkered
            knobFilled = checkered
            gradientFrom = Color.TRANSPARENT
            gradientTo = brush.color
            onProgress = { p ->
                valueView.text = display(kind, p)
                listener.onParam(kind, p)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(28).toInt())
        }
        rows.addView(slider)
        sliders[kind] = slider
        valueViews[kind] = valueView
        valueView.text = display(kind, initial)
    }

    private fun display(kind: ParamKind, p: Float): String {
        val style = brush.style
        return when (kind) {
            ParamKind.SIZE -> {
                val span = (style.maxSize - style.minSize)
                (style.minSize + (p * span).toInt()).toString()
            }
            ParamKind.SENSITIVITY -> "${(p * 100f).toInt()}%"
            ParamKind.TIP -> (1 + (p * 3f).toInt()).toString()
            ParamKind.ALPHA -> "${(p * 100f).toInt()}%"
        }
    }

    private fun dp(value: Int): Float = value * density

    /** Live preview of the actual stroke texture for the current pen. */
    private class StrokePreview(context: Context) : View(context) {
        var brush: BrushConfig = BrushConfig()
            set(value) { field = value; invalidate() }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val path = Path()
        private val grain = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val style = brush.style
            val c = brush.effectiveColor
            paint.color = c
            paint.strokeCap = if (style.squareCap) Paint.Cap.SQUARE else Paint.Cap.ROUND
            paint.strokeWidth = style.toPixels(brush.size, resources.displayMetrics.density)
                .coerceIn(1.5f, h * 0.4f)
            path.reset()
            path.moveTo(w * 0.08f, h * 0.62f)
            path.cubicTo(w * 0.30f, h * 0.92f, w * 0.44f, h * 0.12f, w * 0.64f, h * 0.42f)
            path.cubicTo(w * 0.78f, h * 0.60f, w * 0.84f, h * 0.20f, w * 0.93f, h * 0.34f)
            if (style.glow) {
                val halo = Paint(paint).apply { alpha = 70; strokeWidth = paint.strokeWidth * 2.4f }
                canvas.drawPath(path, halo)
            }
            canvas.drawPath(path, paint)
            if (style.inkHighlight) {
                val sheen = Paint(paint).apply {
                    strokeWidth = paint.strokeWidth * 0.3f
                    color = Color.argb(80, 255, 255, 255)
                }
                canvas.drawPath(path, sheen)
            }
            if (style.grain) {
                grain.color = Color.argb(
                    (Color.alpha(c) * 0.45f).toInt(),
                    Color.red(c), Color.green(c), Color.blue(c),
                )
                var t = 0f
                while (t < 1f) {
                    val x = w * (0.08f + t * 0.85f)
                    val y = h * (0.55f - 0.25f * kotlin.math.sin(t * 9.4f))
                    canvas.drawCircle(x, y, 0.6f * resources.displayMetrics.density, grain)
                    t += 0.012f
                }
            }
        }
    }
}

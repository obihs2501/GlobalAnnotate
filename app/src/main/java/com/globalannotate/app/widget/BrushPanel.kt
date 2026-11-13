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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle

/**
 * Pen settings popup, structure copied from Mi Canvas
 * `penpicker_popup_layout_pen_set.xml`: 110dp preview image on top, then a
 * scrolling property container with 20dp horizontal padding.
 * Rows follow `m24536I1lii()`: size + alpha always; sensitivity for the
 * fountain pen and pencil; tip size for the fountain pen only.
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

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; rebuild(value) }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        layoutParams = LayoutParams(dp(288).toInt(), WRAP_CONTENT)

        preview = StrokePreview(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(110).toInt())
        }
        addView(preview)

        rows.orientation = VERTICAL
        val scroller = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            setPadding(dp(20).toInt(), 0, dp(20).toInt(), dp(16).toInt())
            addView(rows, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        addView(scroller)
    }


    private fun rebuild(brush: BrushConfig) {
        preview.brush = brush
        val style = brush.style
        if (style.isEraser) return
        rows.removeAllViews()

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
        head.addView(bodyText(label).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val valueView = bodyText("")
        head.addView(valueView)
        rows.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(14).toInt()
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
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(26).toInt())
        }
        rows.addView(slider, LinearLayout.LayoutParams(MATCH_PARENT, dp(26).toInt()).apply {
            topMargin = dp(4).toInt()
        })
        valueView.text = display(kind, initial)
    }

    private fun bodyText(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
        includeFontPadding = false
    }

    private fun display(kind: ParamKind, p: Float): String {
        val style = brush.style
        return when (kind) {
            ParamKind.SIZE -> (style.minSize + (p * (style.maxSize - style.minSize)).toInt()).toString()
            ParamKind.SENSITIVITY -> "${(p * 100f).toInt()}%"
            ParamKind.TIP -> (1 + (p * 3f).toInt()).toString()
            ParamKind.ALPHA -> "${(p * 100f).toInt()}%"
        }
    }

    private fun dp(value: Int): Float = value * density

    private class StrokePreview(context: Context) : View(context) {
        var brush: BrushConfig = BrushConfig()
            set(value) { field = value; invalidate() }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
                .coerceIn(1.5f, h * 0.35f)
            path.reset()
            path.moveTo(w * 0.10f, h * 0.62f)
            path.cubicTo(w * 0.30f, h * 0.92f, w * 0.44f, h * 0.14f, w * 0.64f, h * 0.44f)
            path.cubicTo(w * 0.78f, h * 0.62f, w * 0.84f, h * 0.24f, w * 0.92f, h * 0.36f)
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
            if (style.grain) {
                grain.color = Color.argb(
                    (Color.alpha(c) * 0.45f).toInt(), Color.red(c), Color.green(c), Color.blue(c),
                )
                var t = 0f
                while (t < 1f) {
                    canvas.drawCircle(
                        w * (0.10f + t * 0.82f),
                        h * (0.56f - 0.22f * kotlin.math.sin(t * 9.4f)),
                        0.6f * resources.displayMetrics.density, grain,
                    )
                    t += 0.012f
                }
            }
        }
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig

/** Brush settings panel: live stroke preview + four Mi Canvas-style sliders. */
class BrushPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    enum class ParamKind { WIDTH, PRESSURE, TIP, OPACITY }

    interface Listener {
        fun onParam(kind: ParamKind, value: Float)
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val preview: StrokePreview
    private val sliders = mutableMapOf<ParamKind, TrackSlider>()
    private val values = mutableMapOf<ParamKind, TextView>()

    var brush: BrushConfig = BrushConfig()
        set(value) {
            field = value
            preview.brush = value
            syncFromBrush()
        }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(20).toInt(), dp(16).toInt(), dp(20).toInt(), dp(20).toInt())

        val closeRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
        }
        closeRow.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_close
            onTap = { listener.onClose() }
            layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt())
        })
        addView(closeRow)

        preview = StrokePreview(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(90).toInt())
        }
        addView(preview)

        addSlider(ParamKind.WIDTH, "笔画粗细", 0.12f)
        addSlider(ParamKind.PRESSURE, "压力灵敏度", 0.5f)
        addSlider(ParamKind.TIP, "笔尖大小", 0.2f)
        addSlider(ParamKind.OPACITY, "不透明度", 1f, checkered = true)
        syncFromBrush()
    }

    private fun addSlider(kind: ParamKind, label: String, initial: Float, checkered: Boolean = false) {
        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#E8EAED"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val valueView = TextView(context).apply {
            text = "0"
            textSize = 13f
            setTextColor(Color.parseColor("#C9CDD4"))
        }
        head.addView(name)
        head.addView(valueView)
        addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12).toInt() })

        val slider = TrackSlider(context).apply {
            progress = initial
            this.checkered = checkered
            knobFilled = checkered
            gradientFrom = Color.TRANSPARENT
            onProgress = { p ->
                valueView.text = display(kind, p)
                listener.onParam(kind, p)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(30).toInt())
        }
        addView(slider)
        sliders[kind] = slider
        values[kind] = valueView
    }

    private fun display(kind: ParamKind, p: Float): String = when (kind) {
        ParamKind.WIDTH -> (1f + p * 59f).toInt().toString()
        ParamKind.PRESSURE -> "${(p * 100f).toInt()}%"
        ParamKind.TIP -> (1f + p * 9f).toInt().toString()
        ParamKind.OPACITY -> "${(p * 100f).toInt()}%"
    }

    private fun syncFromBrush() {
        val b = brush
        sliders[ParamKind.WIDTH]?.progress = (b.widthDp - 1f) / 59f
        sliders[ParamKind.PRESSURE]?.progress = b.pressureSensitivity
        sliders[ParamKind.TIP]?.progress = (b.minWidthDp - 1f) / 9f
        sliders[ParamKind.OPACITY]?.apply {
            progress = b.opacity / 255f
            gradientTo = b.color
        }
        values[ParamKind.WIDTH]?.text = b.widthDp.toInt().toString()
        values[ParamKind.PRESSURE]?.text = "${(b.pressureSensitivity * 100f).toInt()}%"
        values[ParamKind.TIP]?.text = b.minWidthDp.toInt().toString()
        values[ParamKind.OPACITY]?.text = "${b.opacity * 100 / 255}%"
        invalidate()
    }

    private fun dp(value: Int): Float = value * density

    private class StrokePreview(context: Context) : View(context) {
        var brush: BrushConfig = BrushConfig()
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val c = brush.color
            paint.color = Color.argb(
                minOf(brush.style.alpha, brush.opacity),
                Color.red(c), Color.green(c), Color.blue(c),
            )
            paint.strokeWidth = (brush.widthDp * resources.displayMetrics.density).coerceIn(3f, h * 0.35f)
            path.reset()
            path.moveTo(w * 0.10f, h * 0.62f)
            path.cubicTo(w * 0.32f, h * 0.88f, w * 0.45f, h * 0.12f, w * 0.65f, h * 0.40f)
            path.cubicTo(w * 0.78f, h * 0.58f, w * 0.84f, h * 0.22f, w * 0.92f, h * 0.36f)
            canvas.drawPath(path, paint)
        }
    }
}

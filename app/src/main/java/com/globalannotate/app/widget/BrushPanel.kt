package com.globalannotate.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.penpicker.SelectPropertyView

/**
 * Pen settings popup. The layout is Mi Canvas `penpicker_popup_layout_pen_set`
 * copied verbatim: 110dp preview, then a scrolling `property_container` whose
 * rows are `penpicker_layout_pen_property_item` (title + value on one line,
 * slider below).
 */
class BrushPanel(context: Context, private val listener: Listener) :
    LinearLayout(context) {

    enum class ParamKind { SIZE, SENSITIVITY, TIP, ALPHA }

    interface Listener {
        fun onParam(kind: ParamKind, value: Float)
        fun onClose()
    }

    private val preview: StrokePreview
    private val container: LinearLayout

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; rebuild(value) }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_layout_pen_set, this, true)
        preview = findViewById(R.id.iv_pen_review)
        container = findViewById(R.id.property_container)
    }

    private fun rebuild(brush: BrushConfig) {
        preview.brush = brush
        container.removeAllViews()
        val style = brush.style
        if (style.isEraser) return

        addRow(ParamKind.SIZE, "笔画粗细", formatSize(brush.size), sizeProgress(brush, style))
        if (style.showSensitivity) {
            addRow(
                ParamKind.SENSITIVITY,
                style.sensitivityLabel,
                "${(brush.sensitivity * 100).toInt()}%",
                brush.sensitivity,
            )
        }
        if (style.showTipSize) {
            addRow(ParamKind.TIP, "笔尖大小", "${brush.tipSize.toInt()}", (brush.tipSize - 1f) / 3f)
        }
        addRow(ParamKind.ALPHA, "不透明度", "${(brush.alpha * 100 / 255)}%", brush.alpha / 255f)
    }

    private fun formatSize(size: Float): String = size.toInt().toString()

    private fun sizeProgress(brush: BrushConfig, style: PenStyle): Float {
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        return ((brush.size - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun addRow(kind: ParamKind, label: String, value: String, progress: Float) {
        val row = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_layout_pen_property_item, container, false)
        val title = row.findViewById<TextView>(R.id.penpicker_property_title)
        val valueView = row.findViewById<TextView>(R.id.penpicker_property_value)
        val bar = row.findViewById<SelectPropertyView>(R.id.pk_property_seekbar)

        title.text = label
        valueView.text = value
        bar.progress = progress
        bar.onProgress = { p ->
            val style = brush.style
            when (kind) {
                ParamKind.SIZE -> {
                    val size = style.minSize + p * (style.maxSize - style.minSize)
                    valueView.text = formatSize(size)
                    listener.onParam(kind, size)
                }
                ParamKind.SENSITIVITY -> {
                    valueView.text = "${(p * 100).toInt()}%"
                    listener.onParam(kind, p)
                }
                ParamKind.TIP -> {
                    valueView.text = "${(1 + p * 3).toInt()}"
                    listener.onParam(kind, 1f + p * 3f)
                }
                ParamKind.ALPHA -> {
                    valueView.text = "${(p * 100).toInt()}%"
                    listener.onParam(kind, p * 255f)
                }
            }
        }
        container.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT).apply {
                if (container.childCount > 0) topMargin = (14 * resources.displayMetrics.density).toInt()
            },
        )
    }
}

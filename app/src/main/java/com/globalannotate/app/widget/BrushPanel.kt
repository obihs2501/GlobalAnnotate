package com.globalannotate.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.penpicker.ColorPickerAlphaView
import com.globalannotate.app.penpicker.PickerBar
import com.globalannotate.app.penpicker.SelectPropertyView

/**
 * Brush settings popup, Mi Canvas `penpicker_popup_layout_pen_set` verbatim:
 * 110dp live preview, then a scrolling list of `penpicker_layout_pen_property_item`
 * rows (title + value, slider below). Which rows appear is per brush, exactly as
 * the original shows them:
 *
 *  圆珠笔 笔画粗细 / 不透明度
 *  铅笔   笔画粗细 / 倾斜角灵敏度 / 不透明度
 *  钢笔   笔画粗细 / 压力灵敏度 / 笔尖大小 / 不透明度
 *  马克笔 水彩笔 笔画粗细 / 不透明度
 */
class BrushPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    enum class ParamKind { SIZE, SENSITIVITY, TIP, ALPHA }

    interface Listener {
        fun onParam(kind: ParamKind, value: Float)
    }

    private val preview: StrokePreview
    private val container: LinearLayout
    private val density = resources.displayMetrics.density
    private var current: BrushConfig = BrushConfig()
    private var rows = mutableListOf<Row>()

    var brush: BrushConfig
        get() = current
        set(value) {
            val styleChanged = value.style != current.style
            current = value
            preview.brush = value
            if (styleChanged || rows.isEmpty()) build() else syncRows()
        }

    private data class Row(
        val kind: ParamKind,
        val bar: PickerBar,
        val value: TextView,
    )

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_layout_pen_set, this, true)
        preview = findViewById(R.id.iv_pen_review)
        container = findViewById(R.id.property_container)
        container.removeAllViews()
    }

    private fun addRow(kind: ParamKind, title: String, value: String, progress: Float, alpha: Boolean): Row {
        val row = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_layout_pen_property_item, container, false)
        row.findViewById<TextView>(R.id.penpicker_property_title).text = title
        val valueView = row.findViewById<TextView>(R.id.penpicker_property_value)
        valueView.text = value
        val old = row.findViewById<View>(R.id.pk_property_seekbar)
        val parent = old.parent as android.view.ViewGroup
        val index = parent.indexOfChild(old)
        parent.removeView(old)
        val bar: PickerBar = if (alpha) {
            ColorPickerAlphaView(context).also {
                it.layoutParams = old.layoutParams
                it.baseColor = current.color
            }
        } else {
            SelectPropertyView(context).also { it.layoutParams = old.layoutParams }
        }
        parent.addView(bar, index)
        bar.progress = progress
        bar.onProgress = { p ->
            valueView.text = format(kind, p)
            listener.onParam(kind, p)
        }
        container.addView(
            row,
            LinearLayout.LayoutParams(LAYOUT_MATCH, LAYOUT_WRAP).apply {
                if (container.childCount > 0) topMargin = 0
            },
        )
        return Row(kind, bar, valueView)
    }

    private fun format(kind: ParamKind, p: Float): String = when (kind) {
        ParamKind.SIZE -> (current.style.minSize + p * (current.style.maxSize - current.style.minSize)).toInt().toString()
        ParamKind.SENSITIVITY -> "${(p * 100).toInt()}%"
        ParamKind.TIP -> "${(1 + p * 3).toInt()}"
        ParamKind.ALPHA -> "${(p * 100).toInt()}%"
    }

    private fun sizeProgress(): Float {
        val span = (current.style.maxSize - current.style.minSize).coerceAtLeast(1)
        return ((current.size - current.style.minSize) / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun build() {
        container.removeAllViews()
        rows = mutableListOf()
        val b = current
        if (b.style.isEraser) return
        rows.add(
            addRow(
                ParamKind.SIZE, "笔画粗细", b.size.toInt().toString(), sizeProgress(), alpha = false,
            ),
        )
        b.style.titleSensitivity?.let { title ->
            rows.add(
                addRow(ParamKind.SENSITIVITY, title, "${(b.sensitivity * 100).toInt()}%", b.sensitivity, false),
            )
        }
        if (b.style.showTip) {
            rows.add(addRow(ParamKind.TIP, "笔尖大小", "${b.tipSize}", (b.tipSize - 1) / 3f, false))
        }
        rows.add(
            addRow(ParamKind.ALPHA, "不透明度", "${(b.alpha * 100 / 255)}%", b.alpha / 255f, alpha = true),
        )
    }

    private fun syncRows() {
        for (row in rows) {
            when (row.kind) {
                ParamKind.SIZE -> row.bar.progress = sizeProgress()
                ParamKind.SENSITIVITY -> row.bar.progress = current.sensitivity
                ParamKind.TIP -> row.bar.progress = (current.tipSize - 1) / 3f
                ParamKind.ALPHA -> {
                    row.bar.progress = current.alpha / 255f
                    row.bar.baseColor = current.color
                }
            }
        }
    }

    fun updateColor(color: Int) {
        current = current.copy(color = color)
        preview.brush = current
        rows.forEach { it.bar.baseColor = color }
    }

    private companion object {
        const val LAYOUT_MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        const val LAYOUT_WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.mc.MiCanvasUi
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.penpicker.PickerBar
import com.globalannotate.app.penpicker.SelectPropertyView

/**
 * Brush settings popup, Mi Canvas `penpicker_popup_layout_pen_set` verbatim:
 * 110dp preview, then the property rows (`penpicker_layout_pen_property_item`:
 * title + value, slider below).
 *
 * When Mi Canvas is installed its own `SelectPropertyView` / `SelectAlphaView`
 * are instantiated inside those rows, so the slider is the original class with
 * its original track, ring and drawing code; only the value mapping is ours.
 */
class BrushPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    enum class ParamKind { SIZE, SENSITIVITY, TIP, ALPHA }

    interface Listener {
        fun onParam(kind: ParamKind, value: Float)
    }

    private val preview: StrokePreview
    private val container: LinearLayout
    private var current: BrushConfig = BrushConfig()
    private val rows = mutableListOf<Row>()

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
        val value: TextView,
        val bar: View,
        val min: Int,
        val max: Int,
        val label: (Float) -> String,
    )

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_layout_pen_set, this, true)
        preview = findViewById(R.id.iv_pen_review)
        container = findViewById(R.id.property_container)
        container.removeAllViews()
        MiCanvasUi.init(context)
    }

    private fun addRow(
        kind: ParamKind,
        title: String,
        value: String,
        min: Int,
        max: Int,
        current: Int,
        alpha: Boolean,
        label: (Float) -> String,
    ) {
        val row = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_layout_pen_property_item, container, false)
        row.findViewById<TextView>(R.id.penpicker_property_title).text = title
        val valueView = row.findViewById<TextView>(R.id.penpicker_property_value)
        valueView.text = value
        val old = row.findViewById<View>(R.id.pk_property_seekbar)
        val parent = old.parent as ViewGroup
        val index = parent.indexOfChild(old)

        val mcClass = if (alpha) {
            "com.xiaomi.penpicker.popup.SelectAlphaView"
        } else {
            "com.xiaomi.penpicker.popup.SelectPropertyView"
        }
        val mcBar = MiCanvasUi.createSeekBar(mcClass)
        if (mcBar != null && alpha) MiCanvasUi.clearWhiteStop(mcBar)
        val bar: View = if (mcBar != null) {
            parent.removeView(old)
            mcBar.layoutParams = old.layoutParams
            parent.addView(mcBar, index)
            mcBar.max = max - min
            mcBar.progress = (current - min).coerceIn(0, max - min)
            mcBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val raw = min + progress
                    valueView.text = label(raw.toFloat())
                    listener.onParam(kind, raw.toFloat())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
            mcBar
        } else {
            val fallback = if (alpha) {
                com.globalannotate.app.penpicker.ColorPickerAlphaView(context)
            } else {
                SelectPropertyView(context)
            }
            fallback.layoutParams = old.layoutParams
            parent.removeView(old)
            parent.addView(fallback, index)
            fallback.progress = if (max == min) 0f else (current - min).toFloat() / (max - min)
            fallback.onProgress = { p ->
                val raw = min + p * (max - min)
                valueView.text = label(raw)
                listener.onParam(kind, raw)
            }
            fallback
        }
        if (alpha) updateAlphaGradient(bar, currentColor())
        container.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        rows.add(Row(kind, valueView, bar, min, max, label))
    }

    private fun currentColor(): Int = current.color

    /** Their alpha view paints white → colour; keep the second stop in sync. */
    private fun updateAlphaGradient(bar: View, color: Int) {
        if (bar is SeekBar) MiCanvasUi.call(bar, "m16933I1Iili", color)
        else (bar as? PickerBar)?.baseColor = color
    }

    private fun sizeLabel(value: Float): String = value.toInt().toString()

    private fun build() {
        container.removeAllViews()
        rows.clear()
        val b = current
        val style = b.style
        if (style.isEraser) return
        addRow(
            ParamKind.SIZE, "笔画粗细", sizeLabel(b.size),
            style.minSize, style.maxSize, b.size.toInt(), alpha = false,
        ) { sizeLabel(it) }
        style.titleSensitivity?.let { title ->
            addRow(
                ParamKind.SENSITIVITY, title, "${(b.sensitivity * 100).toInt()}%",
                0, 100, (b.sensitivity * 100).toInt(), alpha = false,
            ) { "${it.toInt()}%" }
        }
        if (style.showTip) {
            addRow(
                ParamKind.TIP, "笔尖大小", b.tipSize.toString(),
                1, 4, b.tipSize, alpha = false,
            ) { it.toInt().toString() }
        }
        addRow(
            ParamKind.ALPHA, "不透明度", "${b.alpha * 100 / 255}%",
            0, 255, b.alpha, alpha = true,
        ) { "${(it * 100 / 255).toInt()}%" }
    }

    private fun syncRows() {
        for (row in rows) {
            val raw = when (row.kind) {
                ParamKind.SIZE -> current.size.toInt()
                ParamKind.SENSITIVITY -> (current.sensitivity * 100).toInt()
                ParamKind.TIP -> current.tipSize
                ParamKind.ALPHA -> current.alpha
            }
            val bar = row.bar
            if (bar is SeekBar) {
                bar.progress = (raw - row.min).coerceIn(0, row.max - row.min)
            } else if (bar is PickerBar) {
                bar.progress = if (row.max == row.min) 0f
                else (raw - row.min).toFloat() / (row.max - row.min)
            }
            row.value.text = row.label(raw.toFloat())
            if (row.kind == ParamKind.ALPHA) updateAlphaGradient(bar, current.color)
        }
    }

    fun updateColor(color: Int) {
        current = current.copy(color = color)
        preview.brush = current
        rows.forEach { if (it.kind == ParamKind.ALPHA) updateAlphaGradient(it.bar, color) }
    }
}

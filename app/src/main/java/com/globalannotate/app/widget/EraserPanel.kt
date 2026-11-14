package com.globalannotate.app.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.penpicker.FilterSortView
import com.globalannotate.app.penpicker.SelectPropertyView

/**
 * Eraser popup, Mi Canvas `penpicker_popup_layout_eraser_set`: a two-tab pill
 * (像素橡皮 / 笔画橡皮). Pixel mode shows 橡皮大小; stroke mode replaces the
 * slider with the hint 笔画橡皮无需调整粗细.
 */
class EraserPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onMode(mode: EraserMode)
        fun onSize(size: Float)
    }

    private val tabs: FilterSortView
    private val content: FrameLayout
    private val hint: TextView
    private val valueView: TextView
    private val slider: SelectPropertyView
    private val sliderRow: View

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; sync(value) }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_layout_eraser_set, this, true)
        tabs = findViewById(R.id.penpicker_filterSortView)
        content = findViewById(R.id.penpicker_eraser_content)
        hint = findViewById(R.id.penpicker_eraser_stroke_hint)

        tabs.addView(FilterSortView.TabView(context).apply {
            text = "像素橡皮"
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        })
        tabs.addView(FilterSortView.TabView(context).apply {
            text = "笔画橡皮"
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        })
        tabs.selectedIndex = 0
        tabs.onTabSelected = { index ->
            val mode = if (index == 0) EraserMode.PIXEL else EraserMode.STROKE
            listener.onMode(mode)
            brush = brush.copy(eraserMode = mode)
        }

        val row = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_layout_pen_property_item, content, false)
        sliderRow = row
        row.findViewById<TextView>(R.id.penpicker_property_title).text = "橡皮大小"
        valueView = row.findViewById(R.id.penpicker_property_value)
        slider = row.findViewById(R.id.pk_property_seekbar)
        slider.onProgress = { p ->
            val style = PenStyle.ERASER
            val size = (style.minSize + p * (style.maxSize - style.minSize)).toInt()
            valueView.text = size.toString()
            listener.onSize(size.toFloat())
        }
        content.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun sync(brush: BrushConfig) {
        val style = PenStyle.ERASER
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        slider.progress = ((brush.eraserSize - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
        valueView.text = brush.eraserSize.toInt().toString()
        tabs.selectedIndex = if (brush.eraserMode == EraserMode.STROKE) 1 else 0
        val stroke = brush.eraserMode == EraserMode.STROKE
        hint.visibility = if (stroke) View.VISIBLE else View.GONE
        sliderRow.visibility = if (stroke) View.GONE else View.VISIBLE
    }
}

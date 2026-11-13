package com.globalannotate.app.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
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
 * Eraser popup using Mi Canvas `penpicker_popup_layout_eraser_set` unchanged:
 * 20dp padding, a FilterSortView with two tabs, the content frame, and the
 * "stroke eraser needs no thickness" hint. Size slider is 4..32 like the APK.
 */
class EraserPanel(context: Context, private val listener: Listener) :
    LinearLayout(context) {

    interface Listener {
        fun onMode(mode: EraserMode)
        fun onSize(size: Float)
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val tabs: FilterSortView
    private val content: FrameLayout
    private val hint: TextView
    private val sizeRow: LinearLayout
    private val sizeValue: TextView
    private val slider: SelectPropertyView

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; sync(value) }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_layout_eraser_set, this, true)
        tabs = findViewById(R.id.penpicker_filterSortView)
        content = findViewById(R.id.penpicker_eraser_content)
        hint = findViewById(R.id.penpicker_eraser_stroke_hint)

        val pixelTab = FilterSortView.TabView(context).apply {
            text = "像素橡皮"
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        }
        val strokeTab = FilterSortView.TabView(context).apply {
            text = "笔画橡皮"
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        }
        tabs.addView(pixelTab)
        tabs.addView(strokeTab)
        tabs.onTabSelected = { index ->
            listener.onMode(if (index == 0) EraserMode.PIXEL else EraserMode.STROKE)
        }

        // Size row mirrors penpicker_layout_pen_property_item: label + value, bar below.
        val row = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_layout_pen_property_item, content, false)
        sizeRow = row as LinearLayout
        row.findViewById<TextView>(R.id.penpicker_property_title).text = "大小"
        sizeValue = row.findViewById(R.id.penpicker_property_value)
        slider = row.findViewById(R.id.pk_property_seekbar)
        slider.onProgress = { p ->
            val style = PenStyle.ERASER
            val size = (style.minSize + p * (style.maxSize - style.minSize)).toInt()
            sizeValue.text = size.toString()
            listener.onSize(size.toFloat())
        }
        content.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun sync(brush: BrushConfig) {
        val style = PenStyle.ERASER
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        slider.progress = ((brush.eraserSize - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
        sizeValue.text = brush.eraserSize.toInt().toString()
        tabs.selectedIndex = if (brush.eraserMode == EraserMode.STROKE) 1 else 0
        val stroke = brush.eraserMode == EraserMode.STROKE
        hint.visibility = if (stroke) View.VISIBLE else View.GONE
        sizeRow.visibility = if (stroke) View.GONE else View.VISIBLE
    }
}

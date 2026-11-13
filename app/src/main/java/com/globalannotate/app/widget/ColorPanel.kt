package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.penpicker.ColorPickPanelView
import com.globalannotate.app.penpicker.ColorPickerAlphaView
import com.globalannotate.app.penpicker.ColorPickerHueView
import com.globalannotate.app.penpicker.ColorPickerLightnessView
import com.globalannotate.app.penpicker.ColorPickerSaturationView
import com.globalannotate.app.penpicker.FilterSortView
import com.globalannotate.app.penpicker.PickerStrawTool

/**
 * Colour popup. Every dimension comes from the ported Mi Canvas layouts:
 * `penpicker_popup_color_picker` (360x571, title + straw, FilterSortView tabs,
 * pager, 72dp display block and two recent-colour rows), plus the grid and
 * slider pages.
 */
class ColorPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onColor(color: Int)
        fun onOpacity(alpha: Int)
        fun onPipette()
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val root: View
    private val tabs: FilterSortView
    private val pager: FrameLayout
    private val gridPage: View
    private val sliderPage: View
    private val grid: ColorPickPanelView
    private val display: ImageView
    private val gridAlpha: ColorPickerAlphaView
    private val hueBar: ColorPickerHueView
    private val satBar: ColorPickerSaturationView
    private val lightBar: ColorPickerLightnessView
    private val sliderAlpha: ColorPickerAlphaView
    private val alphaLabel: TextView
    private val swatchRows = mutableListOf<LinearLayout>()

    private var hsv = floatArrayOf(0f, 0.75f, 0.85f)
    private var alpha = 255

    var color: Int
        get() = Color.HSVToColor(alpha, hsv)
        set(value) {
            val a = Color.alpha(value)
            if (a != 0) alpha = a
            Color.colorToHSV(value, hsv)
            syncFromState()
        }

    var opacity: Int
        get() = alpha
        set(value) {
            alpha = value.coerceIn(0, 255)
            syncFromState()
        }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker, this, true)
        root = findViewById(R.id.ll_root)
        tabs = findViewById(R.id.penpicker_popup_tab)
        pager = findViewById(R.id.penpicker_custom_view_pager)
        display = findViewById(R.id.penpicker_display_block)

        gridPage = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_popup_color_picker_grid, pager, false)
        sliderPage = LayoutInflater.from(context)
            .inflate(R.layout.penpicker_popup_color_picker_slider, pager, false)
        pager.addView(gridPage)
        pager.addView(sliderPage)
        sliderPage.visibility = View.GONE

        grid = gridPage.findViewById(R.id.penpicker_color_picker_panel)
        gridAlpha = gridPage.findViewById(R.id.grid_alpha_seek_bar)
        alphaLabel = gridPage.findViewById(R.id.grid_alpha_progress)
        hueBar = sliderPage.findViewById(R.id.slider_hue)
        satBar = sliderPage.findViewById(R.id.slider_saturation)
        lightBar = sliderPage.findViewById(R.id.slider_lightness)
        sliderAlpha = sliderPage.findViewById(R.id.slider_alpha_seek_bar)

        findViewById<PickerStrawTool>(R.id.penpicker_btn_straw).onTap = { listener.onPipette() }
        findViewById<TextView>(R.id.tv_title)

        tabs.onTabSelected = { index ->
            gridPage.visibility = if (index == 0) View.VISIBLE else View.GONE
            sliderPage.visibility = if (index == 1) View.VISIBLE else View.GONE
        }

        grid.onColorPicked = { picked ->
            Color.colorToHSV(picked, hsv)
            emitColor()
        }
        gridAlpha.onProgress = { p -> alpha = (p * 255).toInt(); emitColor(); listener.onOpacity(alpha) }
        hueBar.onProgress = { p -> hsv[0] = p * 360f; emitColor() }
        satBar.onProgress = { p -> hsv[1] = p; emitColor() }
        lightBar.onProgress = { p -> hsv[2] = p; emitColor() }
        sliderAlpha.onProgress = { p -> alpha = (p * 255).toInt(); emitColor(); listener.onOpacity(alpha) }

        bindRecentColors()
        syncFromState()
    }

    private fun bindRecentColors() {
        val rows = listOf(
            findViewById<LinearLayout>(R.id.penpicker_recent_color_list_1),
            findViewById<LinearLayout>(R.id.penpicker_recent_color_list_2),
        )
        val recent = BrushConfig.PALETTE.take(8)
        rows.forEachIndexed { rowIndex, row ->
            swatchRows.add(row)
            for (i in 0..3) {
                val c = recent[rowIndex * 4 + i]
                val swatch = SwatchView(context, c).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                        if (i > 0) marginStart = dp(12)
                    }
                    setOnClickListener {
                        Color.colorToHSV(c, hsv)
                        emitColor()
                    }
                }
                row.addView(swatch)
            }
        }
    }

    private fun emitColor() {
        val argb = Color.HSVToColor(alpha, hsv)
        syncViews(argb)
        listener.onColor(argb)
    }

    private fun syncFromState() {
        syncViews(Color.HSVToColor(alpha, hsv))
    }

    private fun syncViews(argb: Int) {
        display.setBackgroundColor(argb)
        grid.selectedColor = Color.rgb(Color.red(argb), Color.green(argb), Color.blue(argb))
        gridAlpha.progress = alpha / 255f
        gridAlpha.baseColor = Color.rgb(Color.red(argb), Color.green(argb), Color.blue(argb))
        sliderAlpha.progress = alpha / 255f
        sliderAlpha.baseColor = Color.rgb(Color.red(argb), Color.green(argb), Color.blue(argb))
        hueBar.progress = hsv[0] / 360f
        satBar.baseColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))
        satBar.progress = hsv[1]
        lightBar.baseColor = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))
        lightBar.progress = hsv[2]
        alphaLabel.text = "${(alpha * 100 / 255)}%"
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.penpicker.ColorPickPanelView
import com.globalannotate.app.penpicker.ColorPickerAlphaView
import com.globalannotate.app.penpicker.ColorPickerHueView
import com.globalannotate.app.penpicker.ColorPickerLightnessView
import com.globalannotate.app.penpicker.ColorPickerSaturationView
import com.globalannotate.app.penpicker.FilterSortView
import com.globalannotate.app.mc.MiCanvasUi
import com.globalannotate.app.penpicker.PickerStrawTool

/**
 * Colour popup, Mi Canvas `penpicker_popup_color_picker` verbatim: 360dp wide,
 * 36dp radius, title + eyedropper, a two-tab pill, the 13x9 swatch board,
 * opacity rail, colour block and the four recent colours. The slider tab is the
 * original hue / saturation / lightness / opacity page.
 */
class ColorPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onColor(color: Int)
        fun onAlpha(alpha: Int)
        fun onPipette()
    }

    private val density = resources.displayMetrics.density
    private val tabs: FilterSortView
    private val gridPage: View
    private val sliderPage: View
    private val board: ColorPickPanelView
    private val gridAlpha: ColorPickerAlphaView
    private val gridAlphaValue: TextView
    private val hue: ColorPickerHueView
    private val saturation: ColorPickerSaturationView
    private val lightness: ColorPickerLightnessView
    private val sliderAlpha: ColorPickerAlphaView
    private val sliderAlphaValue: TextView
    private val block: ColorSwatchBlock
    private val recent = mutableListOf<Swatch>()

    private var alpha: Int = 255
    private val hsv = floatArrayOf(352f, 0.70f, 1f)

    var color: Int = Color.parseColor("#E8464A")
        set(value) {
            field = value
            Color.colorToHSV(value, hsv)
            syncViews(fromGrid = true)
        }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker, this, true)
        tabs = findViewById(R.id.penpicker_popup_tab)
        val pager = findViewById<FrameLayout>(R.id.penpicker_custom_view_pager)
        block = findViewById(R.id.penpicker_display_block)

        gridPage = LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker_grid, pager, false)
        sliderPage = LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker_slider, pager, false)
        pager.addView(gridPage)
        pager.addView(sliderPage)

        board = gridPage.findViewById(R.id.penpicker_color_picker_panel)
        installMiCanvasBoard()
        gridAlpha = gridPage.findViewById(R.id.grid_alpha_seek_bar)
        gridAlphaValue = gridPage.findViewById(R.id.grid_alpha_progress)

        hue = sliderPage.findViewById(R.id.slider_hue)
        saturation = sliderPage.findViewById(R.id.slider_saturation)
        lightness = sliderPage.findViewById(R.id.slider_lightness)
        sliderAlpha = sliderPage.findViewById(R.id.slider_alpha_seek_bar)
        sliderAlphaValue = sliderPage.findViewById(R.id.slider_alpha_progress)

        tabs.onTabSelected = { index -> showPage(index) }
        tabs.selectedIndex = 0

        findViewById<PickerStrawTool>(R.id.penpicker_btn_straw).onTap = { listener.onPipette() }
        board.onColorPicked = { picked ->
            Color.colorToHSV(picked, hsv)
            applyColor(picked)
        }
        gridAlpha.onProgress = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            gridAlphaValue.text = "${(p * 100).toInt()}%"
            listener.onAlpha(alpha)
        }
        gridAlpha.onCommit = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            listener.onAlpha(alpha)
        }

        hue.onProgress = { p ->
            hsv[0] = p * 360f
            applyHsv()
        }
        saturation.onProgress = { p ->
            hsv[1] = p
            applyHsv()
        }
        lightness.onProgress = { p ->
            hsv[2] = p
            applyHsv()
        }
        sliderAlpha.onProgress = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            sliderAlphaValue.text = "${(p * 100).toInt()}%"
            listener.onAlpha(alpha)
        }
        sliderAlpha.onCommit = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            listener.onAlpha(alpha)
        }

        buildRecent()
        showPage(0)
    }

    /**
     * Swap in Mi Canvas' own `ColorPickPanelView` when its APK is present: the
     * 13x9 board, its rounding, border and selection ring are then literally the
     * original code and original colour array.
     */
    private var mcBoard: View? = null

    private fun installMiCanvasBoard() {
        MiCanvasUi.init(context)
        if (!MiCanvasUi.available) return
        val mcContext = MiCanvasUi.context ?: return
        val view = MiCanvasUi.createView(
            "com.xiaomi.penpicker.colorpicker.ColorPickPanelView", mcContext, null,
        ) ?: return
        val parent = board.parent as? android.view.ViewGroup ?: return
        val index = parent.indexOfChild(board)
        val lp = board.layoutParams
        parent.removeView(board)
        view.layoutParams = lp
        parent.addView(view, index)
        view.setOnTouchListener { _, _ ->
            view.post { readMcBoard() }
            false
        }
        mcBoard = view
    }

    private fun applyToMcBoard(color: Int) {
        val view = mcBoard ?: return
        runCatching { view.javaClass.getMethod("setColor", Int::class.javaPrimitiveType).invoke(view, color) }
    }

    private fun readMcBoard() {
        val view = mcBoard ?: return
        val picked = runCatching {
            view.javaClass.getMethod("getCurrentColor").invoke(view) as Int
        }.getOrNull() ?: return
        if ((picked and 0xFFFFFF) == (color and 0xFFFFFF)) return
        Color.colorToHSV(picked, hsv)
        applyColor(picked)
    }

    private fun showPage(index: Int) {
        gridPage.visibility = if (index == 0) View.VISIBLE else View.GONE
        sliderPage.visibility = if (index == 1) View.VISIBLE else View.GONE
    }

    private fun applyHsv() {
        val c = Color.HSVToColor(hsv)
        applyColor(c)
    }

    private fun applyColor(c: Int) {
        // Keep the colour opaque inside the picker; opacity is its own rail.
        color = c or 0xFF000000.toInt()
        Color.colorToHSV(color, hsv)
        syncViews(fromGrid = false)
        listener.onColor(color)
    }

    private fun syncViews(fromGrid: Boolean) {
        board.setColor(color)
        applyToMcBoard(color)
        block.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        hue.baseColor = color
        saturation.baseColor = color
        lightness.baseColor = color
        gridAlpha.baseColor = color
        sliderAlpha.baseColor = color
        if (fromGrid) {
            hue.progress = hsv[0] / 360f
            saturation.progress = hsv[1]
            lightness.progress = hsv[2]
        }
        gridAlpha.progress = alpha / 255f
        sliderAlpha.progress = alpha / 255f
        gridAlphaValue.text = "${alpha * 100 / 255}%"
        sliderAlphaValue.text = "${alpha * 100 / 255}%"
        recent.forEach { it.checked = sameRgb(it.color, color) }
    }

    private fun sameRgb(a: Int, b: Int): Boolean =
        (a and 0xFFFFFF) == (b and 0xFFFFFF)

    private fun buildRecent() {
        val row1 = findViewById<LinearLayout>(R.id.penpicker_recent_color_list_1)
        for (c in BrushConfig.RECENT_COLORS) {
            val sw = Swatch(c)
            sw.setOnClickListener {
                color = c
                applyColor(c)
            }
            recent.add(sw)
            row1.addView(sw, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(8) })
        }
        findViewById<LinearLayout>(R.id.penpicker_recent_color_list_2).visibility = View.GONE
    }

    private fun dp(v: Int): Int = (v * density).toInt()

    fun setAlphaValue(a: Int) {
        alpha = a
        syncViews(fromGrid = false)
    }

    private inner class Swatch(val color: Int) : View(context) {
        var checked = false
            set(value) { field = value; invalidate() }
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            setOnClickListener { }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 1f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = android.graphics.Color.parseColor("#E8464A")
            if (checked) {
                canvas.drawCircle(cx, cy, r - 1.5f * density, paint)
            } else {
                paint.strokeWidth = 1f * density
                paint.color = android.graphics.Color.argb(60, 0, 0, 0)
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }
}

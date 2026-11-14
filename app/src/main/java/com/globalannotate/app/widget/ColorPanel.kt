package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.mc.MiCanvasUi
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.penpicker.ColorPickPanelView
import com.globalannotate.app.penpicker.FilterSortView
import com.globalannotate.app.penpicker.PickerBar
import com.globalannotate.app.penpicker.PickerStrawTool

/**
 * Colour popup, Mi Canvas `penpicker_popup_color_picker` verbatim: 338dp wide,
 * 36dp radius, title + eyedropper, the two-tab pill, the 13x9 board, the opacity
 * rail, the colour block and the recent colours.
 *
 * The board and all four rails are Mi Canvas' own classes
 * (`ColorPickPanelView`, `ColorPickerHueView`, `…SaturationView`,
 * `…LightnessView`, `…AlphaView`) loaded from its APK; this class only feeds
 * them the current colour and reads back what the user picked.
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
    private var oursBoard: ColorPickPanelView? = null
    private var mcBoard: View? = null
    private val gridAlphaValue: TextView
    private val sliderAlphaValue: TextView
    private val block: ColorSwatchBlock
    private val recent = mutableListOf<Swatch>()

    private val hueRail = Rail()
    private val saturationRail = Rail()
    private val lightnessRail = Rail()
    private val gridAlphaRail = Rail()
    private val sliderAlphaRail = Rail()

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
        MiCanvasUi.init(context)

        gridPage = LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker_grid, pager, false)
        sliderPage = LayoutInflater.from(context).inflate(R.layout.penpicker_popup_color_picker_slider, pager, false)
        pager.addView(gridPage)
        pager.addView(sliderPage)

        gridAlphaValue = gridPage.findViewById(R.id.grid_alpha_progress)
        sliderAlphaValue = sliderPage.findViewById(R.id.slider_alpha_progress)

        installBoard(gridPage)
        hueRail.install(
            sliderPage, R.id.slider_hue,
            "com.xiaomi.penpicker.colorpicker.ColorPickerHueView", 360,
        )
        saturationRail.install(
            sliderPage, R.id.slider_saturation,
            "com.xiaomi.penpicker.colorpicker.ColorPickerSaturationView", 100,
        )
        lightnessRail.install(
            sliderPage, R.id.slider_lightness,
            "com.xiaomi.penpicker.colorpicker.ColorPickerLightnessView", 100,
        )
        gridAlphaRail.install(
            gridPage, R.id.grid_alpha_seek_bar,
            "com.xiaomi.penpicker.colorpicker.ColorPickerAlphaView", 255, alphaLike = true,
        )
        sliderAlphaRail.install(
            sliderPage, R.id.slider_alpha_seek_bar,
            "com.xiaomi.penpicker.colorpicker.ColorPickerAlphaView", 255, alphaLike = true,
        )

        tabs.selectedIndex = 0
        tabs.onTabSelected = { index ->
            gridPage.visibility = if (index == 0) View.VISIBLE else View.GONE
            sliderPage.visibility = if (index == 1) View.VISIBLE else View.GONE
        }
        gridPage.visibility = View.VISIBLE
        sliderPage.visibility = View.GONE

        findViewById<PickerStrawTool>(R.id.penpicker_btn_straw).onTap = { listener.onPipette() }

        hueRail.onProgress = { p ->
            hsv[0] = p * 360f
            applyHsv()
        }
        saturationRail.onProgress = { p ->
            hsv[1] = p
            applyHsv()
        }
        lightnessRail.onProgress = { p ->
            hsv[2] = p
            applyHsv()
        }
        gridAlphaRail.onProgress = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            gridAlphaValue.text = "${(p * 100).toInt()}%"
            listener.onAlpha(alpha)
        }
        sliderAlphaRail.onProgress = { p ->
            alpha = (p * 255).toInt().coerceIn(0, 255)
            sliderAlphaValue.text = "${(p * 100).toInt()}%"
            listener.onAlpha(alpha)
        }

        buildRecent()
        syncViews(fromGrid = true)
    }

    // ── board ──────────────────────────────────────────────────────────────

    private fun installBoard(parent: View) {
        val ours = parent.findViewById<ColorPickPanelView>(R.id.penpicker_color_picker_panel)
        oursBoard = ours
        if (!MiCanvasUi.available) return
        val mcContext = MiCanvasUi.context ?: return
        val view = MiCanvasUi.createView(
            "com.xiaomi.penpicker.colorpicker.ColorPickPanelView", mcContext, null,
        ) ?: return
        val container = ours.parent as? android.view.ViewGroup ?: return
        val index = container.indexOfChild(ours)
        val lp = ours.layoutParams
        container.removeView(ours)
        view.layoutParams = lp
        container.addView(view, index)
        view.setOnTouchListener { _, _ ->
            view.post { readBoard() }
            false
        }
        mcBoard = view
    }

    private fun setBoardColor(c: Int) {
        mcBoard?.let { MiCanvasUi.call(it, "setColor", c) }
        oursBoard?.setColor(c)
    }

    private fun readBoard() {
        val view = mcBoard ?: return
        val picked = runCatching { view.javaClass.getMethod("getCurrentColor").invoke(view) as Int }
            .getOrNull() ?: return
        if ((picked and 0xFFFFFF) == (color and 0xFFFFFF)) return
        Color.colorToHSV(picked, hsv)
        applyColor(picked)
    }

    // ── rails ──────────────────────────────────────────────────────────────

    /** Wraps either Mi Canvas' slider or our fallback in one small API. */
    private inner class Rail {
        var onProgress: ((Float) -> Unit)? = null
        private var seek: SeekBar? = null
        private var bar: PickerBar? = null
        private var alphaLike = false

        fun install(parent: View, id: Int, className: String, max: Int, alphaLike: Boolean = false) {
            this.alphaLike = alphaLike
            val original = parent.findViewById<View>(id) ?: return
            val container = original.parent as? android.view.ViewGroup ?: return
            val index = container.indexOfChild(original)
            val lp = original.layoutParams
            val mc = MiCanvasUi.createSeekBar(className)
            if (mc != null) {
                container.removeView(original)
                mc.layoutParams = lp
                mc.max = max
                if (alphaLike) MiCanvasUi.clearWhiteStop(mc)
                mc.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) onProgress?.invoke(progress.toFloat() / max)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
                container.addView(mc, index)
                seek = mc
            } else if (original is PickerBar) {
                original.max = max
                original.onProgress = { p -> onProgress?.invoke(p) }
                bar = original
            }
        }

        fun setProgress(p: Float) {
            val s = seek
            if (s != null) {
                s.progress = (p.coerceIn(0f, 1f) * s.max).toInt()
            } else {
                bar?.progress = p
            }
        }

        fun setGradient(color: Int) {
            val target = seek ?: return
            val method = when {
                alphaLike -> "m16882I1Iili"
                target.javaClass.name.endsWith("SaturationView") -> "m16888I1Iili"
                target.javaClass.name.endsWith("LightnessView") -> "m16887I1Iili"
                else -> return
            }
            MiCanvasUi.call(target, method, color)
        }

        fun setBaseColor(color: Int) {
            bar?.baseColor = color
        }
    }

    // ── state ──────────────────────────────────────────────────────────────

    private fun applyHsv() {
        applyColor(Color.HSVToColor(hsv))
    }

    private fun applyColor(c: Int) {
        color = c or 0xFF000000.toInt()
        Color.colorToHSV(color, hsv)
        syncViews(fromGrid = false)
        listener.onColor(color)
    }

    private fun syncViews(fromGrid: Boolean) {
        setBoardColor(color)
        block.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        if (fromGrid) {
            hueRail.setProgress(hsv[0] / 360f)
            saturationRail.setProgress(hsv[1])
            lightnessRail.setProgress(hsv[2])
        }
        hueRail.setBaseColor(color)
        saturationRail.setBaseColor(color)
        lightnessRail.setBaseColor(color)
        saturationRail.setGradient(color)
        lightnessRail.setGradient(color)
        gridAlphaRail.setGradient(color)
        sliderAlphaRail.setGradient(color)
        gridAlphaRail.setBaseColor(color)
        sliderAlphaRail.setBaseColor(color)
        gridAlphaRail.setProgress(alpha / 255f)
        sliderAlphaRail.setProgress(alpha / 255f)
        gridAlphaValue.text = "${alpha * 100 / 255}%"
        sliderAlphaValue.text = "${alpha * 100 / 255}%"
        recent.forEach { it.checked = sameRgb(it.color, color) }
    }

    private fun sameRgb(a: Int, b: Int): Boolean = (a and 0xFFFFFF) == (b and 0xFFFFFF)

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
            set(value) {
                field = value
                invalidate()
            }
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        init { isClickable = true }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 1f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = Color.parseColor("#E8464A")
            if (checked) {
                canvas.drawCircle(cx, cy, r - 1.5f * density, paint)
            } else {
                paint.strokeWidth = density
                paint.color = Color.argb(60, 0, 0, 0)
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.util.Log
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.mc.McBridge
import com.globalannotate.app.mc.MiCanvasUi
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.penpicker.ColorPickPanelView
import com.globalannotate.app.penpicker.FilterSortView
import com.globalannotate.app.penpicker.PickerBar
import com.globalannotate.app.penpicker.PickerStrawTool
import java.lang.reflect.Proxy

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
    /** True once Mi Canvas' own popup content replaced our copy. */
    private var remoteActive = false
    private var remotePopup: android.widget.PopupWindow? = null
    private var remoteContent: View? = null
    private var remoteSetColour: java.lang.reflect.Method? = null
    /** Guards the colour setter while a change comes from Mi Canvas itself. */
    private var remoteEcho = false
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
            if (remoteActive) {
                // Mi Canvas' popup keeps its own state; only push real changes.
                if (!remoteEcho) pushRemote(value)
            } else {
                syncViews(fromGrid = true)
            }
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
        installMiCanvasPopup()
    }

    // ── Mi Canvas popup ────────────────────────────────────────────────────

    /**
     * Everything above is the fallback for a device without Mi Canvas installed.
     * When its APK is present the whole popup comes from there: the miuix tab is
     * theirs, the board is theirs, both rail pages are theirs, the block and the
     * recent colours are theirs — we only take the content view out of their
     * PopupWindow (the arrow chrome and window sizing belong to its presenter)
     * and drive it through the two entry points it expects.
     */
    private fun installMiCanvasPopup() {
        val popup = MiCanvasUi.createColorPopup() ?: return
        val content = popup.contentView ?: return
        (content.parent as? ViewGroup)?.removeView(content)
        remotePopup = popup
        remoteContent = content
        removeAllViews()
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        remoteActive = true

        remoteSetColour = runCatching {
            popup.javaClass.getMethod(MC_SET_COLOUR, Integer.TYPE)
        }.getOrNull()
        if (remoteSetColour == null) {
            Log.w(TAG, "setColour not found; the popup will only report changes")
        }
        bindRemoteListener(popup)
        // The straw runs our own screen picker instead of their floating one.
        findInTree(content) { it.javaClass.name == MC_STRAW }?.setOnClickListener {
            listener.onPipette()
        }
        syncViews(fromGrid = true)
    }

    /**
     * Mi Canvas notifies colour changes through an interface with a single
     * method; the interface name and its method name are obfuscated, so the proxy
     * is matched by shape and the model is read by name.
     */
    private fun bindRemoteListener(popup: android.widget.PopupWindow) {
        val loader = popup.javaClass.classLoader
        val iface = runCatching { Class.forName(MC_LISTENER, true, loader) }.getOrNull() ?: return
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface)) { _, _, args ->
            (args?.firstOrNull())?.let { readRemoteModel(it) }
            null
        }
        var installed = false
        for (field in popup.javaClass.declaredFields) {
            if (field.type != iface) continue
            installed = runCatching {
                field.isAccessible = true
                field.set(popup, proxy)
                true
            }.getOrDefault(false) || installed
        }
        if (!installed) Log.w(TAG, "listener field for $iface not found")
    }

    /** `l1IIiiι` carries an HSL model; `lIIIlll()` is its ARGB colour. */
    private fun readRemoteModel(model: Any) {
        val hsl = runCatching { model.javaClass.getMethod("m25502lIil11").invoke(model) }.getOrNull() ?: return
        val argb = runCatching { hsl.javaClass.getMethod("lIIIlll").invoke(hsl) as Int }.getOrNull() ?: return
        val a = runCatching { hsl.javaClass.getMethod("m1087l1IIii").invoke(hsl) as Int }
            .getOrDefault(Color.alpha(argb))
            .coerceIn(3, 255)
        if ((argb and 0xFFFFFF) == (color and 0xFFFFFF) && a == alpha) return
        remoteEcho = true
        color = argb or 0xFF000000.toInt()
        remoteEcho = false
        alpha = a
        listener.onColor(color)
        listener.onAlpha(a)
        recent.forEach { it.checked = sameRgb(it.color, color) }
    }

    private fun pushRemote(argb: Int) {
        val method = remoteSetColour ?: return
        val withAlpha = (alpha shl 24) or (argb and 0xFFFFFF)
        runCatching { method.invoke(remotePopup, withAlpha) }
            .onFailure { Log.w(TAG, "push colour failed: $it") }
    }

    private fun findInTree(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findInTree(view.getChildAt(i), predicate)?.let { return it }
        }
        return null
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
        // Both directions go through the bridge: the class comes from Mi Canvas'
        // APK, so a renamed member must still be found by signature.
        mcBoard?.let { McBridge.setBoardColour(it, c) }
        oursBoard?.setColor(c)
    }

    private fun readBoard() {
        val view = mcBoard ?: return
        val picked = McBridge.boardColour(view) ?: return
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
            if (target.javaClass.name.endsWith("HueView")) {
                // Hue's spectrum is static; nothing to feed.
                return
            }
            McBridge.setColour(target, color)
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
        if (remoteActive) pushRemote(color) else syncViews(fromGrid = false)
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

    private companion object {
        const val TAG = "GA.ColorPanel"
        /** Their popup's colour setter; jadx calls it `m25427I1lii`. */
        const val MC_SET_COLOUR = "I\u03b91li\u03b9i"
        /** `l1lII1` + 4x iota + `Il.IIiI` + iota + `Iiii1` — the change listener. */
        const val MC_LISTENER = "l1lII1\u03b9\u03b9\u03b9\u03b9Il.IIiI\u03b9Iiii1"
        const val MC_STRAW = "com.xiaomi.penpicker.common.tools.PickerStrawTool"
    }
}

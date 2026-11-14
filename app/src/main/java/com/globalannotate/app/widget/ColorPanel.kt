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
import com.globalannotate.app.mc.MiCanvasPopupShell
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
        fun onEditorColor(color: Int)
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
    /** The whole original arrow subtree, when Mi Canvas' popup could be lifted. */
    private var remoteShell: MiCanvasPopupShell? = null
    /** Mi Canvas' own `penpicker_display_block` inside the shell, if it was found. */
    private var shellBlock: View? = null
    private var remoteSetColour: java.lang.reflect.Method? = null
    /** Guards the colour setter while a change comes from Mi Canvas itself. */
    private var remoteEcho = false
    private val gridAlphaValue: TextView
    private val sliderAlphaValue: TextView
    private val block: ColorSwatchBlock
    private val recent = mutableListOf<Swatch>()
    /** Recent-colour dots injected into Mi Canvas' popup (no presenter to fill them). */
    private val shellDots = mutableListOf<RecentDot>()

    private val hueRail = Rail()
    private val saturationRail = Rail()
    private val lightnessRail = Rail()
    private val gridAlphaRail = Rail()
    private val sliderAlphaRail = Rail()

    private var alpha: Int = 255
    private val hsv = floatArrayOf(352f, 0.70f, 1f)

    /**
     * Guards the H/S/V state while a rail is dragged. RGB -> HSV is lossy near
     * black/grey, so re-deriving it on every frame rewrote hue and saturation.
     */
    private var holdingHsv = false

    var color: Int = Color.parseColor("#E8464A")
        set(value) {
            field = value
            if (holdingHsv) return
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
            // Deterministic swap: the unselected page is hidden immediately. An
            // earlier version hid it from an animation end-action, which a
            // cancel() skipped — the slider page then stayed visible on top of
            // the grid and swallowed every tap, so 网格取色 appeared dead.
            val incoming = if (index == 0) gridPage else sliderPage
            val outgoing = if (index == 0) sliderPage else gridPage
            val dir = if (index == 0) -1f else 1f
            incoming.animate().cancel()
            outgoing.animate().cancel()
            outgoing.visibility = View.GONE
            outgoing.alpha = 1f
            outgoing.translationX = 0f
            incoming.visibility = View.VISIBLE
            incoming.alpha = 0f
            incoming.translationX = 24f * density * dir
            incoming.animate()
                .alpha(1f).translationX(0f)
                .setDuration(160)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
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
        gridAlphaRail.onProgress = { p -> applyAlpha(p) }
        sliderAlphaRail.onProgress = { p -> applyAlpha(p) }

        buildRecent()
        syncViews(fromGrid = true)
        installMiCanvasPopup()
    }


    /** Both opacity rails, both percentage labels and the swatch follow one drag. */
    private fun applyAlpha(p: Float) {
        alpha = (p * 255).toInt().coerceIn(3, 255)
        val text = "${(p * 100).toInt()}%"
        gridAlphaValue.text = text
        sliderAlphaValue.text = text
        block.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        updateShellBlock()

        listener.onAlpha(alpha)
    }

    /**
     * Repaint Mi Canvas' own display block. It is an `ImageView` whose shape is a
     * background drawable, and the presenter normally tints it with a
     * `PorterDuffColorFilter`; without that it kept its default colour while the
     * grid selection moved.
     */
    private fun updateShellBlock() {
        val view = shellBlock ?: return
        val bg = view.background ?: return
        bg.mutate().colorFilter = android.graphics.PorterDuffColorFilter(
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
            android.graphics.PorterDuff.Mode.SRC,
        )
        view.invalidate()
    }
    /**
     * Mi Canvas' own popup is the visible surface: its arrow, direction,
     * animation and measured geometry are exactly the reference. What is missing
     * is the presenter (`p452lilii.I1Iili`) that normally binds the grid/slider
     * pages' callbacks, so we bind the opacity rails ourselves below.
     */
    private fun installMiCanvasPopup() {
        val started = System.currentTimeMillis()
        val popup = MiCanvasUi.createColorPopup() ?: return
        remotePopup = popup
        remoteSetColour = runCatching {
            popup.javaClass.getMethod(MC_SET_COLOUR, Integer.TYPE)
        }.getOrNull()
        bindRemoteListener(popup)

        val shell = MiCanvasPopupShell.from(popup)
        if (shell != null) {
            remoteShell = shell
            remoteActive = true
            bindShellOpacityRails(shell)
            bindShellRecentColors(shell)
            val blockId = MiCanvasUi.id("penpicker_display_block")
            shellBlock = if (blockId != 0) shell.root.findViewById(blockId) else null
            updateShellBlock()
            // The straw runs our own screen picker instead of their floating one.
            findInTree(shell.root) { it.javaClass.name == MC_STRAW }?.setOnClickListener {
                listener.onPipette()
            }
            Log.i(TAG, "Mi Canvas popup shell ready in ${System.currentTimeMillis() - started} ms")
            return
        }

        val content = popup.contentView ?: return
        (content.parent as? ViewGroup)?.removeView(content)
        remoteContent = content
        removeAllViews()
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        remoteActive = true
        findInTree(content) { it.javaClass.name == MC_STRAW }?.setOnClickListener {
            listener.onPipette()
        }
        syncViews(fromGrid = true)
        Log.i(TAG, "Mi Canvas popup content ready in ${System.currentTimeMillis() - started} ms")
    }

    /**
     * The presenter normally calls `addOnColorChangeListener` on both page
     * views. Without it the two opacity rails are inflated, drawable and
     * touchable, but their changes go nowhere. Attach our own listeners by the
     * original resource ids so dragging updates the brush alpha.
     */
    /**
     * The presenter normally wires the two page views' colour callbacks. The
     * opacity rails themselves are plain `AppCompatSeekBar`s whose internal
     * listener forwards to that (absent) presenter, so dragging them changed
     * nothing. Take the listener over directly: one listener per SeekBar in
     * Android, which is fine because we own the whole colour state now.
     */
    private fun bindShellOpacityRails(shell: MiCanvasPopupShell) {
        for (name in listOf("grid_alpha_seek_bar", "slider_alpha_seek_bar")) {
            val id = MiCanvasUi.id(name)
            if (id == 0) continue
            val view = shell.root.findViewById<View>(id) ?: continue
            if (view !is SeekBar) continue
            view.max = 255
            view.progress = alpha.coerceIn(3, 255)
            view.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val next = value.coerceIn(3, 255)
                    if (next == alpha) return
                    alpha = next
                    listener.onAlpha(next)
                }
                override fun onStartTrackingTouch(b: SeekBar) = Unit
                override fun onStopTrackingTouch(b: SeekBar) {
                    // Refresh Mi Canvas' own display block / other rail without
                    // re-entering the colour listener chain.
                    pushRemote(color)
                }
            })
            Log.i(TAG, "opacity rail bound: $name")
        }
    }


    /**
     * Mi Canvas creates twelve recent-colour dots but leaves them GONE until its
     * presenter pushes the recent list. Without that presenter the bottom row
     * was blank. Fill the first row with our own dots (26dp, 12.8dp pitch, as in
     * `m25424Ili1Ii11`) and wire them to the same colour path as the board.
     */
    private fun bindShellRecentColors(shell: MiCanvasPopupShell) {
        val id = MiCanvasUi.id("penpicker_recent_color_list_1")
        if (id == 0) return
        val row = shell.root.findViewById<View>(id) as? LinearLayout ?: return
        row.removeAllViews()
        val size = dp(26)
        shellDots.clear()
        for (c in BrushConfig.RECENT_COLORS.take(6)) {
            val dot = RecentDot(c)
            dot.setOnClickListener {
                remoteEcho = true
                try {
                    color = c or 0xFF000000.toInt()
                    syncViews(fromGrid = true)
                    listener.onEditorColor(color)
                } finally {
                    remoteEcho = false
                }
                pushRemote(color)
            }
            shellDots.add(dot)
            row.addView(dot, LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(13) })
        }
        val row2 = MiCanvasUi.id("penpicker_recent_color_list_2")
        if (row2 != 0) shell.root.findViewById<View>(row2)?.visibility = View.GONE
    }

    /** A flat colour dot with the white inner ring Mi Canvas uses for selection. */
    private inner class RecentDot(val swatch: Int) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        var checked = false
            set(value) { field = value; invalidate() }

        init { isClickable = true }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val r = minOf(width, height) / 2f - 1f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = swatch
            canvas.drawCircle(width / 2f, height / 2f, r, paint)
            if (checked) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 1.25f * density
                paint.color = Color.WHITE
                canvas.drawCircle(width / 2f, height / 2f, r * 0.76f, paint)
            }
        }
    }
    /**
     * `l1lII1ιιιιIl.l1IIiiι` with kind 1 is an alpha-only event and carries no
     * HSL model. Handle it before the HSL lookup so a stray alpha callback is
     * not silently dropped.
     */
    private fun readAlphaModel(model: Any): Boolean {
        val kind = runCatching { model.javaClass.getMethod("Iiliill").invoke(model) as Int }.getOrNull()
            ?: return false
        if (kind != 1) return false
        val value = runCatching {
            model.javaClass.getMethod("I\u03b911lii").invoke(model) as Int
        }.getOrNull() ?: return true
        val next = value.coerceIn(3, 255)
        if (next != alpha) {
            alpha = next
            listener.onAlpha(next)
        }
        return true
    }

    /** The real arrow subtree, when Mi Canvas' popup could be taken over whole. */
    fun originalShell(): MiCanvasPopupShell? = remoteShell
    /**
     * Mi Canvas notifies colour changes through an interface with a single
     * method; the interface name and its method name are obfuscated, so the proxy
     * is matched by shape and the model is read by name.
     */
    private fun bindRemoteListener(popup: android.widget.PopupWindow) {
        val loader = popup.javaClass.classLoader
        val iface = runCatching { Class.forName(MC_LISTENER, true, loader) }.getOrNull()
        if (iface == null) {
            Log.w(TAG, "listener interface $MC_LISTENER not found")
            return
        }
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface)) { _, method, args ->
            Log.i(TAG, "remote ${method.name}")
            (args?.firstOrNull())?.let { readRemoteModel(it) }
            null
        }
        // The field may live on any superclass of the popup.
        var installed = 0
        var cls: Class<*>? = popup.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (field.type != iface) continue
                val ok = runCatching {
                    field.isAccessible = true
                    field.set(popup, proxy)
                    true
                }.getOrDefault(false)
                if (ok) installed++
            }
            cls = cls.superclass
        }
        Log.i(TAG, "listener fields installed=$installed")
    }

    /** `l1IIiiι` carries an HSL model; `lIIIlll()` is its ARGB colour. */
    private fun readRemoteModel(model: Any) {
        // Alpha-only events carry no HSL model; handle them first.
        if (readAlphaModel(model)) return
        // Real names, not jadx's `mNNNNN…` placeholders: `lιIil11()` is the HSL
        // model, `lIIIlll()` its ARGB colour and `l1IIiiι()` its alpha.
        val hsl = runCatching { model.javaClass.getMethod("l\u03b9Iil11").invoke(model) }.getOrNull()
        if (hsl == null) {
            Log.w(TAG, "colour model ${model.javaClass.name} unreadable")
            return
        }
        val argb = runCatching { hsl.javaClass.getMethod("lIIIlll").invoke(hsl) as Int }.getOrNull() ?: return
        val a = runCatching { hsl.javaClass.getMethod("l1IIii\u03b9").invoke(hsl) as Int }
            .getOrDefault(Color.alpha(argb))
            .coerceIn(3, 255)
        if ((argb and 0xFFFFFF) == (color and 0xFFFFFF) && a == alpha) return
        // Keep the echo guard up for the WHOLE dispatch. It used to be cleared
        // before listener.onColor(), and the service writes straight back into
        // color, which pushed the colour into Mi Canvas again and produced a
        // feedback loop (dragging one rail moved the others, jerky animation).
        remoteEcho = true
        try {
            color = argb or 0xFF000000.toInt()
            alpha = a
            listener.onEditorColor(color)
            listener.onAlpha(a)
            recent.forEach { it.checked = sameRgb(it.color, color) }
            shellDots.forEach { it.checked = sameRgb(it.swatch, color) }
        } finally {
            remoteEcho = false
        }
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
        // Keep H/S/V authoritative while a rail is dragged. Going through the
        // colour setter here re-derived HSV from an RGB that is nearly grey at
        // low lightness, which silently reset hue and saturation.
        holdingHsv = true
        color = Color.HSVToColor(hsv) or 0xFF000000.toInt()
        holdingHsv = false
        syncViews(fromGrid = false)
        listener.onEditorColor(color)
    }

    private fun applyColor(c: Int) {
        color = c or 0xFF000000.toInt()
        // A colour from the board/recent swatches is an external change, so the
        // three rails must be re-seated from it.
        syncViews(fromGrid = true)
        listener.onEditorColor(color)
    }

    /**
     * Feed the rails a *stable* hue/saturation. Mi Canvas' saturation and
     * lightness gradients are built from the pure hue, not from the current
     * (possibly dark or desaturated) RGB; rebuilding them from the dragged
     * colour is what made the other two channels appear to change.
     */
    private fun syncViews(fromGrid: Boolean) {
        setBoardColor(color)
        block.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        updateShellBlock()
        val pureHue = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        val pureSat = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceAtLeast(0.001f), 1f))
        if (fromGrid) {
            hueRail.setProgress(hsv[0] / 360f)
            saturationRail.setProgress(hsv[1])
            lightnessRail.setProgress(hsv[2])
        }
        // During a rail drag only the thumb moves. Re-seeding the gradients or
        // pushing progress back into the other bars fights the drag and makes
        // the animation stutter.
        if (fromGrid) {
            hueRail.setBaseColor(pureHue)
            saturationRail.setBaseColor(pureHue)
            lightnessRail.setBaseColor(pureSat)
            saturationRail.setGradient(pureHue)
            lightnessRail.setGradient(pureSat)
            gridAlphaRail.setGradient(color)
            sliderAlphaRail.setGradient(color)
            gridAlphaRail.setBaseColor(color)
            sliderAlphaRail.setBaseColor(color)
        }
        gridAlphaValue.text = "${alpha * 100 / 255}%"
        sliderAlphaValue.text = "${alpha * 100 / 255}%"
        if (fromGrid) {
            gridAlphaRail.setProgress(alpha / 255f)
            sliderAlphaRail.setProgress(alpha / 255f)
        }
        recent.forEach { it.checked = sameRgb(it.color, color) }
        shellDots.forEach { it.checked = sameRgb(it.swatch, color) }
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
        alpha = a.coerceIn(3, 255)
        val p = alpha / 255f
        gridAlphaRail.setProgress(p)
        sliderAlphaRail.setProgress(p)
        val text = "${alpha * 100 / 255}%"
        gridAlphaValue.text = text
        sliderAlphaValue.text = text
        block.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        updateShellBlock()
        if (remoteActive) pushRemote(color)
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

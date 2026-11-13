package com.globalannotate.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.globalannotate.app.canvas.AnnotationCanvasView
import com.globalannotate.app.ink.SmartInk
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.stylus.PenEngineClient
import com.globalannotate.app.toolbar.ToolbarView
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.BrushPanel
import com.globalannotate.app.widget.ColorPanel
import com.globalannotate.app.widget.EraserPanel
import com.globalannotate.app.widget.GesturePanel
import com.globalannotate.app.widget.IconButton
import com.globalannotate.app.widget.MoreMenu
import com.globalannotate.app.widget.RadialPaletteView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overlay host. Popups are anchored above the brush that opened them, the bar
 * auto-collapses as soon as the pen touches the surface (Mi Canvas behaviour),
 * and exiting is a dedicated back button in the top-left corner.
 */
class AnnotationService : Service(),
    ToolbarView.Listener,
    RadialPaletteView.Listener,
    ColorPanel.Listener,
    BrushPanel.Listener,
    EraserPanel.Listener,
    GesturePanel.Listener,
    MoreMenu.Listener {

    private lateinit var windowManager: WindowManager
    private lateinit var canvas: AnnotationCanvasView
    private lateinit var canvasParams: WindowManager.LayoutParams
    private lateinit var toolbar: ToolbarView
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var orb: BrushGlyph
    private lateinit var orbParams: WindowManager.LayoutParams
    private lateinit var palette: RadialPaletteView
    private lateinit var paletteParams: WindowManager.LayoutParams
    private lateinit var colorPanel: ColorPanel
    private lateinit var colorPanelParams: WindowManager.LayoutParams
    private lateinit var brushPanel: BrushPanel
    private lateinit var brushPanelParams: WindowManager.LayoutParams
    private lateinit var eraserPanel: EraserPanel
    private lateinit var eraserPanelParams: WindowManager.LayoutParams
    private lateinit var gesturePanel: GesturePanel
    private lateinit var gesturePanelParams: WindowManager.LayoutParams
    private lateinit var moreMenu: MoreMenu
    private lateinit var moreMenuParams: WindowManager.LayoutParams

    private var mode: OverlayMode = OverlayMode.DRAW
    private var active = false
    private var toolbarVisible = true
    private var autoMinimize = true
    private var penEngine: PenEngineClient? = null
    private val penTip = PointF(0f, 0f)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gestureMap = mutableMapOf<String, GestureAction>()
    private var screenWidth = 0

    private val panels: List<View>
        get() = listOf(colorPanel, brushPanel, eraserPanel, gesturePanel, moreMenu)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = windowManager.maximumWindowMetrics.bounds
        screenWidth = bounds.width()
        penTip.set(screenWidth / 2f, bounds.height() / 2f)

        canvas = AnnotationCanvasView(this).apply {
            onGesture = ::applyGesture
            onStateChanged = ::refreshToolbar
            onPenDown = { if (autoMinimize) collapseBar() }
        }
        canvas.setOnHoverListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER ||
                event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
            ) penTip.set(event.x, event.y)
            false
        }

        toolbar = ToolbarView(this, this)
        orb = BrushGlyph(this).apply {
            large = true
            setBackgroundResource(R.drawable.bg_orb)
            elevation = dp(6).toFloat()
            setOnClickListener { toggleToolbar() }
        }
        palette = RadialPaletteView(this).apply { listener = this@AnnotationService }
        colorPanel = ColorPanel(this, this)
        brushPanel = BrushPanel(this, this)
        eraserPanel = EraserPanel(this, this)
        gesturePanel = GesturePanel(this, this)
        moreMenu = MoreMenu(this, this)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val passiveFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        canvasParams = params(MATCH, MATCH, baseFlags)
        // Fixed size: a WRAP overlay window measures to 0 before its content
        // lays out, which made the whole bar disappear.
        toolbarParams = params(dp(620), dp(94), passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(24)
        }
        orbParams = params(dp(72), dp(72), passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(24)
        }
        paletteParams = params(MATCH, MATCH, passiveFlags)
        colorPanelParams = anchoredParams(dp(360), dp(colorPanel.requiredHeightDp()), passiveFlags)
        brushPanelParams = anchoredParams(dp(288), dp(420), passiveFlags)
        eraserPanelParams = anchoredParams(dp(288), dp(200), passiveFlags)
        gesturePanelParams = anchoredParams(dp(288), dp(320), passiveFlags)
        moreMenuParams = anchoredParams(dp(240), WRAP, passiveFlags)

        windowManager.addView(canvas, canvasParams)
        windowManager.addView(toolbar, toolbarParams)
        windowManager.addView(palette, paletteParams)
        windowManager.addView(colorPanel, colorPanelParams)
        windowManager.addView(brushPanel, brushPanelParams)
        windowManager.addView(eraserPanel, eraserPanelParams)
        windowManager.addView(gesturePanel, gesturePanelParams)
        windowManager.addView(moreMenu, moreMenuParams)
        windowManager.addView(orb, orbParams)

        panels.forEach { it.visibility = View.GONE }
        restoreBrush()
        restoreGestures()
        gesturePanel.setActions(gestureMap)
        Session.service = this
        Session.canvas = canvas
        setMode(OverlayMode.DRAW)
        setActive(false)
        notifyModule(Actions.SET_MODULE_ENABLED, true)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) handleRemoteAction(intent)
        return START_STICKY
    }

    fun handleRemoteAction(intent: Intent) {
        when (intent.action) {
            Actions.LAUNCH -> setActive(true)
            Actions.TOGGLE -> setActive(!active)
            Actions.GESTURE -> {
                val name = intent.getStringExtra(Actions.EXTRA_GESTURE) ?: return
                val gesture = runCatching { Gesture.valueOf(name) }.getOrNull() ?: return
                applyRemoteGesture(gesture)
            }
            Actions.SET_SPLIT_MODE -> setMode(
                if (intent.getBooleanExtra(Actions.EXTRA_ENABLED, false)) OverlayMode.ROOT_SPLIT
                else OverlayMode.DRAW,
            )
        }
    }

    private fun setActive(value: Boolean) {
        active = value
        Session.active = value
        canvas.visibility = if (value) View.VISIBLE else View.GONE
        orb.visibility = if (value) View.VISIBLE else View.GONE
        applyToolbarVisibility()
        if (!value) {
            palette.hide()
            closePanels()
        }
        applyCanvasFlags()
        syncPenEngine()
        refreshToolbar()
    }

    private fun setMode(next: OverlayMode) {
        mode = next
        Session.rootSplitActive = next == OverlayMode.ROOT_SPLIT
        canvas.mode = next
        applyCanvasFlags()
        syncPenEngine()
        notifyModule(Actions.SET_SPLIT_MODE, next == OverlayMode.ROOT_SPLIT)
        refreshToolbar()
    }

    private fun applyCanvasFlags() {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        when (mode) {
            OverlayMode.DRAW -> canvasParams.alpha = 1f
            OverlayMode.PASSTHROUGH, OverlayMode.ROOT_SPLIT -> {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                canvasParams.alpha = 0.79f
            }
        }
        canvasParams.flags = flags
        if (canvas.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(canvas, canvasParams) }
        }
        if (active && mode == OverlayMode.DRAW) canvas.requestGestureFocus()
        else canvas.clearGestureFocus()
    }

    private fun syncPenEngine() {
        if (active && mode == OverlayMode.DRAW) {
            if (penEngine == null) penEngine = PenEngineClient(this)
            penEngine?.connect()
        } else {
            penEngine?.disconnect()
            penEngine = null
        }
    }

    private fun toggleToolbar() {
        toolbarVisible = !toolbarVisible
        if (toolbarVisible && !active) setActive(true)
        applyToolbarVisibility()
    }

    private fun collapseBar() {
        if (!toolbarVisible) return
        toolbarVisible = false
        applyToolbarVisibility()
        closePanels()
    }

    private fun applyToolbarVisibility() {
        if (::toolbar.isInitialized) {
            toolbar.visibility = if (active && toolbarVisible) View.VISIBLE else View.GONE
        }
    }

    private fun applyRemoteGesture(gesture: Gesture) {
        if (!active) { setActive(true); return }
        applyGesture(gesture)
    }

    /** Gestures go through the configurable map, defaulting to sensible actions. */
    private fun applyGesture(gesture: Gesture) {
        val action = gestureMap[gesture.name] ?: defaultAction(gesture)
        performAction(action)
    }

    private fun defaultAction(gesture: Gesture): GestureAction = when (gesture) {
        Gesture.SQUEEZE -> GestureAction.OPEN_COLOR
        Gesture.DOUBLE_TAP -> GestureAction.TOGGLE_ERASER
        Gesture.SLIDE_UP -> GestureAction.INCREASE_WIDTH
        Gesture.SLIDE_DOWN -> GestureAction.DECREASE_WIDTH
    }

    private fun performAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.TOGGLE_OVERLAY -> setActive(!active)
            GestureAction.TOGGLE_ERASER -> {
                canvas.brush = if (canvas.brush.style.isEraser) {
                    canvas.brush.copy(style = PenStyle.INK_PEN, size = PenStyle.INK_PEN.defaultSize.toFloat())
                } else {
                    canvas.brush.copy(style = PenStyle.ERASER)
                }
                refreshToolbar()
            }
            GestureAction.CYCLE_TOOL -> {
                val order = PenStyle.entries
                val next = order[(order.indexOf(canvas.brush.style) + 1) % order.size]
                canvas.brush = canvas.brush.copy(style = next, size = next.defaultSize.toFloat(), alpha = next.defaultAlpha)
                refreshToolbar()
            }
            GestureAction.UNDO -> canvas.undo()
            GestureAction.REDO -> canvas.redo()
            GestureAction.CLEAR -> canvas.clear()
            GestureAction.INCREASE_WIDTH -> adjustSize(1)
            GestureAction.DECREASE_WIDTH -> adjustSize(-1)
            GestureAction.TOGGLE_TOOLBAR -> toggleToolbar()
            GestureAction.OPEN_COLOR -> togglePalette()
            GestureAction.CYCLE_COLOR -> cycleColor()
        }
    }

    /** Squeeze → next quick colour, the "换颜色" shortcut. */
    private fun cycleColor() {
        // Cycle the full palette so it reaches every colour the panel offers.
        val paletteColors = BrushConfig.PALETTE
        val index = paletteColors.indexOf(canvas.brush.color)
        val next = paletteColors[(index + 1).mod(paletteColors.size)]
        canvas.brush = canvas.brush.copy(color = next)
        refreshToolbar()
        colorPanel.color = next
    }

    private fun adjustSize(delta: Int) {
        val b = canvas.brush
        canvas.brush = if (b.style.isEraser) {
            b.copy(eraserSize = (b.eraserSize + delta).coerceIn(
                PenStyle.ERASER.minSize.toFloat(), PenStyle.ERASER.maxSize.toFloat(),
            ))
        } else {
            b.copy(size = (b.size + delta).coerceIn(
                b.style.minSize.toFloat(), b.style.maxSize.toFloat(),
            ))
        }
        refreshToolbar()
    }

    private fun togglePalette() {
        if (!active) { setActive(true); return }
        if (palette.visibility == View.VISIBLE) palette.hide()
        else palette.showAt(penTip.x, penTip.y)
    }

    override fun onPaletteAction(action: RadialPaletteView.PaletteAction) {
        when (action) {
            RadialPaletteView.PaletteAction.PEN -> canvas.brush = canvas.brush.copy(style = PenStyle.INK_PEN)
            RadialPaletteView.PaletteAction.ERASER -> canvas.brush = canvas.brush.copy(style = PenStyle.ERASER)
            RadialPaletteView.PaletteAction.RED -> canvas.brush = canvas.brush.copy(color = Color.parseColor("#D93025"))
            RadialPaletteView.PaletteAction.BLUE -> canvas.brush = canvas.brush.copy(color = Color.parseColor("#1A73E8"))
            RadialPaletteView.PaletteAction.BLACK -> canvas.brush = canvas.brush.copy(color = Color.parseColor("#202124"))
            RadialPaletteView.PaletteAction.UNDO -> canvas.undo()
            RadialPaletteView.PaletteAction.CLEAR -> canvas.clear()
            RadialPaletteView.PaletteAction.CLOSE -> Unit
        }
        refreshToolbar()
    }

    private fun refreshToolbar() {
        if (::toolbar.isInitialized) {
            toolbar.update(canvas.brush, mode, canvas.canUndo(), canvas.canRedo())
        }
        if (::orb.isInitialized) {
            orb.style = canvas.brush.style
            orb.color = canvas.brush.color
        }
        saveBrush()
    }

    // ── ToolbarView.Listener ────────────────────────────────────────────────
    override fun onStyle(style: PenStyle, anchorX: Int) {
        val same = canvas.brush.style == style
        if (!same) {
            canvas.brush = canvas.brush.copy(
                style = style,
                size = style.defaultSize.toFloat(),
                alpha = style.defaultAlpha,
            )
            refreshToolbar()
        }
        if (style.isEraser) {
            eraserPanel.brush = canvas.brush
            brushPanelParams.height = dp(brushPanel.requiredHeightDp())
            showPanel(eraserPanel, anchorX, dp(288))
        } else {
            brushPanel.brush = canvas.brush
            brushPanelParams.height = dp(brushPanel.requiredHeightDp())
            showPanel(brushPanel, anchorX, dp(288))
        }
    }

    override fun onColor(color: Int) {
        canvas.brush = canvas.brush.copy(color = color)
        refreshToolbar()
    }

    override fun onOpenColorPanel(anchorX: Int) {
        colorPanel.color = canvas.brush.color
        colorPanel.opacity = canvas.brush.alpha
        colorPanelParams.height = dp(colorPanel.requiredHeightDp())
        showPanel(colorPanel, anchorX, dp(360))
    }

    override fun onOpenMoreMenu(anchorX: Int) = showPanel(moreMenu, anchorX, dp(240))

    override fun onUndo() { canvas.undo() }
    override fun onRedo() { canvas.redo() }
    override fun onCollapse() = collapseBar()
    override fun onQuit() { stopSelf() }

    // ── ColorPanel.Listener ─────────────────────────────────────────────────
    override fun onOpacity(alpha: Int) {
        canvas.brush = canvas.brush.copy(alpha = alpha)
        refreshToolbar()
    }
    override fun onPipette() = pickColorFromScreen()
    override fun onClose() = closePanels()

    // ── BrushPanel.Listener ─────────────────────────────────────────────────
    override fun onParam(kind: BrushPanel.ParamKind, value: Float) {
        val b = canvas.brush
        canvas.brush = when (kind) {
            BrushPanel.ParamKind.SIZE ->
                b.copy(size = b.style.minSize + value * (b.style.maxSize - b.style.minSize))
            BrushPanel.ParamKind.SENSITIVITY -> b.copy(sensitivity = value)
            BrushPanel.ParamKind.TIP -> b.copy(tipSize = 1f + value * 3f)
            BrushPanel.ParamKind.ALPHA -> b.copy(alpha = (value * 255f).toInt().coerceIn(0, 255))
        }
        refreshToolbar()
        brushPanel.brush = canvas.brush
    }

    // ── EraserPanel.Listener ────────────────────────────────────────────────
    override fun onMode(mode: EraserMode) {
        canvas.brush = canvas.brush.copy(eraserMode = mode)
        refreshToolbar()
        eraserPanel.brush = canvas.brush
    }
    override fun onSize(size: Float) {
        canvas.brush = canvas.brush.copy(eraserSize = size)
        refreshToolbar()
    }

    // ── GesturePanel.Listener ───────────────────────────────────────────────
    override fun onAction(gesture: String, action: GestureAction) {
        gestureMap[gesture] = action
        saveGestures()
    }

    override fun onExpandedChanged() {
        gesturePanelParams.height = dp(gesturePanel.requiredHeightDp())
        gesturePanel.post {
            runCatching { windowManager.updateViewLayout(gesturePanel, gesturePanelParams) }
        }
    }

    // ── MoreMenu.Listener ───────────────────────────────────────────────────
    override fun onGestureSettings() {
        gesturePanel.setActions(gestureMap)
        gesturePanelParams.height = dp(gesturePanel.requiredHeightDp())
        showPanel(gesturePanel, screenWidth / 2, dp(288))
    }
    override fun onAutoMinimize(enabled: Boolean) { autoMinimize = enabled }
    override fun onFingerDraw(enabled: Boolean) { canvas.allowFinger = enabled }
    override fun onCycleMode() {
        setMode(
            when (mode) {
                OverlayMode.DRAW -> OverlayMode.PASSTHROUGH
                OverlayMode.PASSTHROUGH -> OverlayMode.ROOT_SPLIT
                OverlayMode.ROOT_SPLIT -> OverlayMode.DRAW
            },
        )
        closePanels()
    }
    override fun onSavePng() { export(pdf = false); closePanels() }
    override fun onSavePdf() { export(pdf = true); closePanels() }
    override fun onClear() { canvas.clear(); closePanels() }

    /** Shows the popup above the tapped brush; tapping the same entry closes it. */
    private fun showPanel(target: View, anchorX: Int, widthPx: Int) {
        val alreadyOpen = target.visibility == View.VISIBLE
        panels.forEach { it.visibility = View.GONE }
        if (alreadyOpen) {
            palette.hide()
            return
        }
        val lp = paramsFor(target)
        lp.x = (anchorX - widthPx / 2).coerceIn(dp(8), (screenWidth - widthPx - dp(8)).coerceAtLeast(dp(8)))
        target.alpha = 0f
        target.scaleX = PANEL_ENTER_SCALE
        target.scaleY = PANEL_ENTER_SCALE
        target.visibility = View.VISIBLE
        runCatching { windowManager.updateViewLayout(target, lp) }
        target.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(PANEL_ANIM_MS).setInterpolator(PanelInterpolator).start()
        palette.hide()
    }

    private fun paramsFor(view: View): WindowManager.LayoutParams = when (view) {
        colorPanel -> colorPanelParams
        brushPanel -> brushPanelParams
        eraserPanel -> eraserPanelParams
        gesturePanel -> gesturePanelParams
        else -> moreMenuParams
    }

    private fun closePanels() {
        panels.forEach { view ->
            if (view.visibility != View.VISIBLE) return@forEach
            view.animate().cancel()
            view.animate().alpha(0f).scaleX(PANEL_ENTER_SCALE).scaleY(PANEL_ENTER_SCALE)
                .setDuration(PANEL_ANIM_MS).setInterpolator(PanelInterpolator)
                .withEndAction { view.visibility = View.GONE }.start()
        }
    }

    private fun pickColorFromScreen() {
        scope.launch {
            val screenBitmap = withOverlaysHidden {
                withContext(Dispatchers.IO) { Exporter.captureScreenRoot() }
            }
            if (screenBitmap == null) {
                Exporter.lastCaptureError?.let { Log.w(TAG, "pipette failed: $it") }
                Toast.makeText(this@AnnotationService, "取色失败（需要 root 授权）", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val picked = SmartInk.pickColor(screenBitmap, penTip.x.toInt(), penTip.y.toInt())
            screenBitmap.recycle()
            canvas.brush = canvas.brush.copy(color = picked)
            colorPanel.color = picked
            refreshToolbar()
            Toast.makeText(
                this@AnnotationService,
                "已取色 #" + "%06X".format(picked and 0xFFFFFF),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun export(pdf: Boolean) {
        scope.launch {
            val bitmap = composeExportBitmap()
            val name = withContext(Dispatchers.IO) {
                if (pdf) Exporter.savePdf(this@AnnotationService, bitmap)
                else Exporter.savePng(this@AnnotationService, bitmap)
            }
            bitmap.recycle()
            val message = if (name != null) getString(R.string.export_ok, name) else getString(R.string.export_failed)
            Toast.makeText(this@AnnotationService, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun composeExportBitmap(): Bitmap {
        val background = withOverlaysHidden {
            withContext(Dispatchers.IO) { Exporter.captureScreenRoot() }
        }
        return if (background != null) {
            val scaled = Bitmap.createScaledBitmap(background, canvas.width, canvas.height, true)
            background.recycle()
            val result = canvas.render(background = scaled)
            scaled.recycle()
            result
        } else {
            Exporter.lastCaptureError?.let { Log.w(TAG, "root screenshot unavailable: $it") }
            canvas.render(paperColor = Color.WHITE)
        }
    }

    private suspend fun <T> withOverlaysHidden(block: suspend () -> T): T {
        val views = listOf(canvas, toolbar, orb, palette) + panels
        // Fade window alpha instead of toggling visibility: no relayout pass,
        // so the bar does not visibly flash when it comes back.
        val states = views.map { it to paramsFor(it).alpha }
        states.forEach { (view, _) ->
            runCatching {
                val lp = paramsFor(view)
                lp.alpha = 0f
                windowManager.updateViewLayout(view, lp)
            }
        }
        return try {
            delay(OVERLAY_HIDE_SETTLE_MS)
            block()
        } finally {
            states.forEach { (view, alpha) ->
                runCatching {
                    val lp = paramsFor(view)
                    lp.alpha = alpha
                    windowManager.updateViewLayout(view, lp)
                }
            }
        }
    }

    private fun saveBrush() {
        val b = canvas.brush
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_STYLE, b.style.name)
            .putInt(KEY_COLOR, b.color)
            .putFloat(KEY_SIZE, b.size)
            .putInt(KEY_ALPHA, b.alpha)
            .putFloat(KEY_SENSITIVITY, b.sensitivity)
            .putFloat(KEY_TIP, b.tipSize)
            .putString(KEY_ERASER_MODE, b.eraserMode.name)
            .putFloat(KEY_ERASER_SIZE, b.eraserSize)
            .apply()
    }

    private fun restoreBrush() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        val style = runCatching {
            PenStyle.valueOf(p.getString(KEY_STYLE, PenStyle.INK_PEN.name) ?: PenStyle.INK_PEN.name)
        }.getOrDefault(PenStyle.INK_PEN)
        val eraserMode = runCatching {
            EraserMode.valueOf(p.getString(KEY_ERASER_MODE, EraserMode.PIXEL.name) ?: EraserMode.PIXEL.name)
        }.getOrDefault(EraserMode.PIXEL)
        canvas.brush = BrushConfig(
            style = style,
            color = p.getInt(KEY_COLOR, Color.parseColor("#D93025")),
            size = p.getFloat(KEY_SIZE, style.defaultSize.toFloat()),
            alpha = p.getInt(KEY_ALPHA, style.defaultAlpha),
            sensitivity = p.getFloat(KEY_SENSITIVITY, 0.5f),
            tipSize = p.getFloat(KEY_TIP, 1f),
            eraserMode = eraserMode,
            eraserSize = p.getFloat(KEY_ERASER_SIZE, PenStyle.ERASER.defaultSize.toFloat()),
        )
    }

    private fun saveGestures() {
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        gestureMap.forEach { (k, v) -> editor.putString("gesture_$k", v.name) }
        editor.apply()
    }

    private fun restoreGestures() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        for (gesture in Gesture.entries) {
            val saved = p.getString("gesture_${gesture.name}", null) ?: continue
            runCatching { GestureAction.valueOf(saved) }
                .onSuccess { gestureMap[gesture.name] = it }
        }
    }

    private fun notifyModule(action: String, enabled: Boolean) {
        val intent = Intent(action)
            .putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN)
            .putExtra(Actions.EXTRA_ENABLED, enabled)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        runCatching { sendBroadcast(intent) }
    }

    private fun params(width: Int, height: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

    /** Popups sit above the bar, horizontally aligned to the tapped control. */
    private fun anchoredParams(width: Int, height: Int, flags: Int): WindowManager.LayoutParams =
        params(width, height, flags).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            y = dp(122)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val toggle = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, GestureLaunchReceiver::class.java)
                .setAction(Actions.TOGGLE)
                .putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(toggle)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        notifyModule(Actions.SET_MODULE_ENABLED, false)
        notifyModule(Actions.SET_SPLIT_MODE, false)
        penEngine?.disconnect()
        penEngine = null
        (listOf(canvas, toolbar, palette, orb) + panels).forEach { view ->
            if (view.isAttachedToWindow) runCatching { windowManager.removeView(view) }
        }
        if (Session.service === this) Session.service = null
        if (Session.canvas === canvas) Session.canvas = null
        Session.rootSplitActive = false
        Session.active = false
        scope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AnnotationService"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "global_annotate"
        private const val OVERLAY_HIDE_SETTLE_MS = 160L
        private const val PANEL_ANIM_MS = 150L
        private const val PANEL_ENTER_SCALE = 0.96f
        private val PanelInterpolator = PathInterpolator(0f, 0f, 0.2f, 1f)
        private const val MATCH = WindowManager.LayoutParams.MATCH_PARENT
        private const val WRAP = WindowManager.LayoutParams.WRAP_CONTENT
        private const val PREFS = "global_annotate_brush"
        private const val KEY_STYLE = "style"
        private const val KEY_COLOR = "color"
        private const val KEY_SIZE = "size"
        private const val KEY_ALPHA = "alpha"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_TIP = "tip"
        private const val KEY_ERASER_MODE = "eraser_mode"
        private const val KEY_ERASER_SIZE = "eraser_size"

        var isRunning: Boolean = false
            private set
    }
}

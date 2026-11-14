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
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.globalannotate.app.canvas.AnnotationCanvasView
import com.globalannotate.app.export.LongCapture
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.root.HelperClient
import com.globalannotate.app.root.HelperProtocol
import com.globalannotate.app.sys.Hidden
import com.globalannotate.app.toolbar.ToolbarView
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.BrushPanel
import com.globalannotate.app.widget.ColorPanel
import com.globalannotate.app.widget.EraserPanel
import com.globalannotate.app.widget.GesturePanel
import com.globalannotate.app.widget.MoreMenu
import com.globalannotate.app.widget.RadialPaletteView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Overlay host.
 *
 * Input model (the reason this app needs root):
 *
 *  - the root helper owns a stylus-only input window, so **the pen annotates
 *    and the finger keeps scrolling the app underneath** at the same time —
 *    Huawei's 全局批注 behaviour, with no mode switch;
 *  - the finger spy also drives scroll-follow, so ink stays glued to the page;
 *  - the helper reads the pen's raw keyboard, so a squeeze summons the palette
 *    from any app even though the overlay never takes focus.
 *
 * Without root the service still works: the canvas becomes touchable and the
 * pen draws through the normal touch path.
 */
class AnnotationService : Service(),
    ToolbarView.Listener,
    ToolbarView.DragListener,
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
    private lateinit var popupHost: FrameLayout
    private lateinit var popupParams: WindowManager.LayoutParams
    private lateinit var dismissLayer: View
    private lateinit var palette: RadialPaletteView
    private lateinit var paletteParams: WindowManager.LayoutParams

    private lateinit var colorPanel: ColorPanel
    private lateinit var brushPanel: BrushPanel
    private lateinit var eraserPanel: EraserPanel
    private lateinit var gesturePanel: GesturePanel
    private lateinit var moreMenu: MoreMenu

    private var helper: HelperClient? = null
    private var helperReady = 0
    private var active = false
    private var toolbarVisible = false
    private var autoMinimize = true
    private var fingerDraw = false
    private var openPanel: View? = null
    /** Guards the delayed end action of a panel close animation. */
    private var panelEpoch = 0

    private var screenWidth = 0
    private var screenHeight = 0
    private val density get() = resources.displayMetrics.density
    private val penTip = PointF()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gestureMap = mutableMapOf<String, GestureAction>()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var lastFingerY = 0f
    private var fingerPointerDown = false
    private var fingerScrolling = false
    private var fingerTouchedOutside = false
    private var squeezeCount = 0
    private var lastSqueezeMs = 0L
    private var busyExporting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        Hidden.unlocked

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshScreenSize()
        penTip.set(screenWidth / 2f, screenHeight / 2f)

        canvas = AnnotationCanvasView(this).apply {
            onStateChanged = { refreshToolbar() }
            onPenDown = { if (autoMinimize) collapseBar() }
        }
        // In the normal mode the helper's trusted surface owns the pen and this
        // window is NOT_TOUCHABLE, so touches only arrive here in the fallback
        // mode or when the user asked a finger to paint. allowFinger carries
        // that choice down to the canvas.
        canvas.setOnTouchListener { _, event ->
            if (!canvas.allowFinger && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                return@setOnTouchListener false
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) closePanels()
            canvas.injectStylusEvent(event)
            true
        }

        toolbar = ToolbarView(this, this).apply { dragListener = this@AnnotationService }
        orb = BrushGlyph(this).apply {
            // Mi Canvas' minimized ball: a 96dp disc with a 73dp pen, not the
            // 56dp satchel we used before.
            artOffsetDp = 0f
            artLengthDp = 73f
            val capsule = androidx.core.content.ContextCompat.getColor(
                this@AnnotationService, R.color.penpicker_background_color,
            )
            val night = 0.299f * android.graphics.Color.red(capsule) +
                0.587f * android.graphics.Color.green(capsule) +
                0.114f * android.graphics.Color.blue(capsule) < 128f
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (night) 0xFF242424.toInt() else 0xFFF2F2F2.toInt())
            }
            elevation = dp(8).toFloat()
            setOnClickListener { expandBar() }
        }
        popupHost = FrameLayout(this)
        dismissLayer = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { closePanels() }
        }
        palette = RadialPaletteView(this).apply {
            listener = this@AnnotationService
            visibility = View.GONE
        }

        colorPanel = ColorPanel(this, this)
        brushPanel = BrushPanel(this, this)
        eraserPanel = EraserPanel(this, this)
        moreMenu = MoreMenu(this, this)
        gesturePanel = GesturePanel(this, this)
        gesturePanel.setWillNotDraw(false)

        popupHost.addView(dismissLayer, FrameLayout.LayoutParams(MATCH, MATCH))
        popupHost.clipChildren = false
        popupHost.clipToPadding = false
        for (panel in listOf<View>(colorPanel, brushPanel, eraserPanel, moreMenu, gesturePanel)) {
            panel.visibility = View.GONE
            // Mi Canvas lifts popups with a 150-blur MiShadow; elevation is the
            // closest the platform lets an overlay window draw.
            panel.elevation = dp(20).toFloat()
            popupHost.addView(panel, FrameLayout.LayoutParams(WRAP, WRAP))
        }
        popupHost.visibility = View.GONE

        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val helperFlags = base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        canvasParams = overlay(MATCH, MATCH, helperFlags)
        val barWidth = toolbar.contentWidthPx().coerceAtLeast(dp(360))
        toolbarParams = overlay(barWidth, dp(BAR_HEIGHT_DP), base).apply {
            x = prefs.getInt(KEY_BAR_X, -1).let { if (it >= 0) it else (screenWidth - barWidth) / 2 }
            // 36dp keeps the capsule clear of the launcher dock and the gesture
            // bar; the window is 18dp taller than the capsule for the tools'
            // outward travel, so this is measured from the window top.
            y = prefs.getInt(KEY_BAR_Y, -1).let { if (it >= 0) it else screenHeight - dp(BAR_HEIGHT_DP) - dp(100) }
        }
        orbParams = overlay(dp(96), dp(96), base).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dp(16 + 96)
            y = screenHeight - dp(16 + 96)
        }
        popupParams = overlay(MATCH, MATCH, base)
        paletteParams = overlay(MATCH, MATCH, helperFlags)

        windowManager.addView(canvas, canvasParams)
        windowManager.addView(orb, orbParams)
        windowManager.addView(toolbar, toolbarParams)
        windowManager.addView(popupHost, popupParams)
        windowManager.addView(palette, paletteParams)
        // popupHost and palette start hidden and have no surface yet; they are
        // excluded when they are first shown instead.
        for (view in listOf(canvas, toolbar, orb)) {
            excludeFromCapture(view)
        }

        restoreBrush()
        restoreGestures()
        gesturePanel.actions = gestureMap
        toolbar.update(canvas.brush, false, false)
        // A START_STICKY restart arrives with a null intent and no window state;
        // start from an explicit idle state instead of trusting setActive()'s
        // "nothing changed" guard.
        enterIdle()
        Session.service = this
        Session.canvas = canvas
        com.globalannotate.app.mc.MiCanvasUi.init(this)
        startHelper()
        scope.launch {
            delay(3500)
            if (helperReady == 0) {
                Log.w(TAG, "helper not ready, falling back to a touchable canvas")
                applyInputMode()
            }
        }
        notifyModule(Actions.SET_MODULE_ENABLED, true)
        isRunning = true
    }

    // ── helper ─────────────────────────────────────────────────────────────

    private fun startHelper() {
        val apk = applicationInfo.sourceDir
        val client = HelperClient(apk, object : HelperClient.Callback {
            override fun onHelperReady(flags: Int) {
                helperReady = flags
                Log.i(TAG, "helper ready flags=$flags")
                applyInputMode()
                // Root access can arrive after activation; until now the exclusion
                // rects were computed while the helper did not exist yet, so a pen
                // tap on the toolbar painted a stroke instead of pressing a button.
                updateExclusion()
                if (active) {
                    helper?.setOpacityLifted(true)
                }
            }

            override fun onStylus(event: MotionEvent) = dispatchStylus(event)
            override fun onFinger(event: MotionEvent) = dispatchFinger(event)
            override fun onPenKey(scanCode: Int, value: Int, uptimeMs: Long) =
                dispatchPenKey(scanCode, value)

            override fun onHelperLog(message: String) {
                Log.i(TAG, "helper: $message")
            }

            override fun onHelperExit() {
                helperReady = 0
                applyInputMode()
                helper = null
                if (active) toast("root 助手未运行，已切换到兼容模式")
            }
        })
        helper = client
        client.start()
    }

    /**
     * The root helper owns a trusted `INTERCEPTS_STYLUS` surface (only root can
     * declare one — an app-owned window aborts the input dispatcher), so the
     * canvas itself stays NOT_TOUCHABLE and the pen arrives as frames.
     *
     * Without the helper the canvas has to be touchable, which takes touches
     * away from the app underneath while annotation is active; that is the
     * documented fallback rather than a silent no-op.
     */
    private fun applyInputMode() {
        val intercept = helperReady and HelperProtocol.READY_INTERCEPT != 0
        // With the helper intercepting the pen the window stays NOT_TOUCHABLE so
        // a finger keeps scrolling the app underneath. "用手指涂画" flips exactly
        // that: the window becomes touchable and the finger paints instead.
        val touchable = !intercept || fingerDraw
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        canvasParams.flags = flags
        canvasParams.alpha = 1f
        canvas.allowFinger = touchable
        if (canvas.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(canvas, canvasParams) }
        }
        helper?.setIntercept(active && intercept)
    }

    // ── stylus / finger / keys ─────────────────────────────────────────────

    private fun dispatchStylus(event: MotionEvent) {
        penTip.set(event.x, event.y)
        if (palette.isOpen()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> palette.onHover(event.x, event.y)
                MotionEvent.ACTION_DOWN -> palette.onHover(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    palette.commit(event.x, event.y)
                    palette.hide()
                }
                else -> Unit
            }
            return
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) closePanels()
        canvas.injectStylusEvent(event)
    }

    private fun dispatchFinger(event: MotionEvent) {
        // The finger is painting on the canvas itself now, so the spy's
        // scroll-follow would fight the stroke it is producing.
        if (fingerDraw) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingerPointerDown = true
                fingerScrolling = false
                fingerTouchedOutside = false
                lastFingerY = event.y
                if (openPanel != null) {
                    val panel = openPanel ?: return
                    val loc = IntArray(2)
                    panel.getLocationOnScreen(loc)
                    val inside = event.x >= loc[0] && event.x <= loc[0] + panel.width &&
                        event.y >= loc[1] && event.y <= loc[1] + panel.height
                    if (!inside) {
                        closePanels()
                        fingerTouchedOutside = true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fingerPointerDown || fingerTouchedOutside) return
                val dy = event.y - lastFingerY
                lastFingerY = event.y
                if (abs(dy) < 0.01f) return
                if (!fingerScrolling && abs(dy) > 2.5f * density) fingerScrolling = true
                if (fingerScrolling && active && event.pointerCount == 1) {
                    canvas.scrollBy(-dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (fingerPointerDown && fingerScrolling) canvas.pruneOffscreen()
                fingerPointerDown = false
                fingerScrolling = false
                fingerTouchedOutside = false
            }
        }
    }

    private fun dispatchPenKey(scanCode: Int, value: Int) {
        if (value != 1) return
        when (scanCode) {
            GestureKeys.SCANCODE_SQUEEZE_RAW -> onSqueeze()
            GestureKeys.SCANCODE_DOUBLE_TAP_RAW -> applyGesture(Gesture.DOUBLE_TAP)
            else -> Log.d(TAG, "pen key scan=$scanCode")
        }
    }

    private fun onSqueeze() {
        val now = System.currentTimeMillis()
        if (now - lastSqueezeMs < 320) {
            squeezeCount++
        } else {
            squeezeCount = 1
        }
        lastSqueezeMs = now
        if (squeezeCount >= 2) {
            squeezeCount = 0
            applyGesture(Gesture.DOUBLE_TAP)
            return
        }
        if (!active) {
            setActive(true)
            return
        }
        if (palette.isOpen()) {
            palette.commit(penTip.x, penTip.y)
            palette.visibility = View.GONE
            return
        }
        applyGesture(Gesture.SQUEEZE)
    }

    // ── activation ─────────────────────────────────────────────────────────

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        Session.active = value
        canvas.visibility = if (value) View.VISIBLE else View.GONE
        orb.visibility = if (value) View.VISIBLE else View.GONE
        if (value) {
            expandBar()
            helper?.setOpacityLifted(true)
            helper?.setIntercept(helperReady and HelperProtocol.READY_INTERCEPT != 0)
        } else {
            toolbarVisible = false
            closePanels()
            palette.hide()
            toolbar.visibility = View.GONE
            helper?.setIntercept(false)
            helper?.setOpacityLifted(false)
        }
        refreshToolbar()
        updateExclusion()
    }

    /**
     * The idle state a fresh service starts in: every overlay hidden and the
     * helper not intercepting. onCreate calls this directly because setActive()
     * returns early when the requested value already matches, which left a
     * START_STICKY restart with a visible, uncollapsible toolbar.
     */
    private fun enterIdle() {
        active = false
        Session.active = false
        toolbarVisible = false
        canvas.visibility = View.GONE
        orb.visibility = View.GONE
        toolbar.visibility = View.GONE
        palette.hide()
        closePanels(animate = false)
        helper?.setIntercept(false)
        helper?.setOpacityLifted(false)
        refreshToolbar()
        updateExclusion()
    }

    /** Bounds change with the rotation; everything that clamps against them re-reads. */
    private fun refreshScreenSize() {
        val bounds = windowManager.maximumWindowMetrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshScreenSize()
        // Keep the bar and the ball on screen: the saved position was clamped
        // against the bounds of the previous orientation.
        toolbarParams.x = toolbarParams.x.coerceIn(0, (screenWidth - toolbarParams.width).coerceAtLeast(0))
        toolbarParams.y = toolbarParams.y.coerceIn(0, (screenHeight - toolbarParams.height).coerceAtLeast(0))
        if (toolbar.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(toolbar, toolbarParams) }
        }
        updateExclusion()
    }

    private fun expandBar() {
        if (!active) {
            setActive(true)
            return
        }
        toolbarVisible = true
        toolbar.visibility = View.VISIBLE
        refreshToolbar()
        updateExclusion()
        updateExclusionAfterLayout()
    }

    private fun collapseBar() {
        if (!toolbarVisible) return
        toolbarVisible = false
        toolbar.visibility = View.GONE
        closePanels()
        updateExclusion()
    }

    private fun applyGesture(gesture: Gesture) {
        val action = gestureMap[gesture.name] ?: defaultAction(gesture)
        performAction(action)
    }

    private fun defaultAction(gesture: Gesture): GestureAction = when (gesture) {
        Gesture.SQUEEZE -> GestureAction.OPEN_PALETTE
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
                    canvas.brush.copy(
                        style = PenStyle.INK_PEN,
                        size = PenStyle.INK_PEN.defaultSize.toFloat(),
                        alpha = PenStyle.INK_PEN.defaultAlpha,
                    )
                } else {
                    canvas.brush.copy(style = PenStyle.ERASER)
                }
                refreshToolbar()
            }
            GestureAction.CYCLE_TOOL -> {
                val order = PenStyle.toolbarOrder
                val next = order[(order.indexOf(canvas.brush.style) + 1) % order.size]
                canvas.brush = canvas.brush.copy(
                    style = next, size = next.defaultSize.toFloat(), alpha = next.defaultAlpha,
                )
                refreshToolbar()
            }
            GestureAction.UNDO -> canvas.undo()
            GestureAction.REDO -> canvas.redo()
            GestureAction.CLEAR -> canvas.clear()
            GestureAction.INCREASE_WIDTH -> adjustSize(1)
            GestureAction.DECREASE_WIDTH -> adjustSize(-1)
            GestureAction.TOGGLE_TOOLBAR -> if (toolbarVisible) collapseBar() else expandBar()
            GestureAction.OPEN_COLOR -> showColorPanel(penTip.x.toInt())
            GestureAction.CYCLE_COLOR -> cycleColor()
            GestureAction.OPEN_PALETTE -> openPalette()
        }
    }

    private fun openPalette() {
        if (palette.isOpen()) {
            palette.hide()
            return
        }
        if (!active) setActive(true)
        palette.showAt(penTip.x, penTip.y)
        palette.visibility = View.VISIBLE
        // Its surface only exists once the window is visible.
        excludeFromCapture(palette)
    }

    private fun cycleColor() {
        // Rotate through the bar's quick colours AND the panel's palette so the
        // gesture keeps producing visibly different ink. Keep every open panel
        // in sync, otherwise the palette looks stuck.
        // De-duplicate: a colour that appears in both lists would otherwise restart
        // the cycle at its first copy and the gesture would look stuck.
        val colors = (BrushConfig.QUICK_COLORS + BrushConfig.RECENT_COLORS).distinct()
        val index = colors.indexOf(canvas.brush.color)
        val next = colors[(index + 1).mod(colors.size)]
        canvas.brush = canvas.brush.copy(color = next)
        colorPanel.color = next
        brushPanel.updateColor(next)
        refreshToolbar()
    }

    private fun adjustSize(delta: Int) {
        val b = canvas.brush
        canvas.brush = if (b.style.isEraser) {
            b.copy(eraserSize = (b.eraserSize + delta).coerceIn(
                PenStyle.ERASER.minSize.toFloat(), PenStyle.ERASER.maxSize.toFloat(),
            ))
        } else {
            b.copy(size = (b.size + delta).coerceIn(b.style.minSize.toFloat(), b.style.maxSize.toFloat()))
        }
        refreshToolbar()
    }

    override fun onPalettePick(action: RadialPaletteView.Action) {
        palette.visibility = View.GONE
        val style = when (action) {
            RadialPaletteView.Action.BALLPOINT -> PenStyle.BALLPOINT
            RadialPaletteView.Action.PENCIL -> PenStyle.PENCIL
            RadialPaletteView.Action.INK_PEN -> PenStyle.INK_PEN
            RadialPaletteView.Action.MARKER -> PenStyle.MARKER
            RadialPaletteView.Action.WATER_COLOR -> PenStyle.WATER_COLOR
            RadialPaletteView.Action.ERASER -> PenStyle.ERASER
            else -> null
        }
        when {
            style != null -> {
                canvas.brush = canvas.brush.copy(
                    style = style,
                    size = style.defaultSize.toFloat(),
                    alpha = style.defaultAlpha,
                )
                refreshToolbar()
            }
            action == RadialPaletteView.Action.UNDO -> canvas.undo()
            action == RadialPaletteView.Action.COLOR -> showColorPanel(penTip.x.toInt())
        }
    }

    override fun onPaletteDismiss() {
        palette.visibility = View.GONE
    }

    // ── toolbar listener ───────────────────────────────────────────────────

    override fun onTool(style: PenStyle, anchorX: Int) {
        Log.i(TAG, "tool tap $style at $anchorX")
        val same = canvas.brush.style == style
        if (!same) {
            // Mi Canvas: the first tap only selects the tool; its settings popup
            // opens when the already-selected tool is tapped again.
            canvas.brush = canvas.brush.copy(
                style = style,
                size = style.defaultSize.toFloat(),
                alpha = style.defaultAlpha,
                sensitivity = if (style == PenStyle.PENCIL) 0f else 0.38f,
            )
            refreshToolbar()
            closePanels()
            return
        }
        if (style.isEraser) {
            eraserPanel.brush = canvas.brush
            showPanel(eraserPanel, anchorX, dp(288), 0)
        } else {
            brushPanel.brush = canvas.brush
            brushPanel.updateColor(canvas.brush.color)
            showPanel(brushPanel, anchorX, dp(288), 0)
        }
    }

    override fun onColor(color: Int) {
        canvas.brush = canvas.brush.copy(color = color)
        colorPanel.color = color
        brushPanel.updateColor(color)
        refreshToolbar()
    }

    override fun onOpenColorPanel(anchorX: Int) = showColorPanel(anchorX)

    override fun onOpenMore(anchorX: Int) {
        Log.i(TAG, "more tap at $anchorX")
        moreMenu.setChecks(autoMinimize, fingerDraw)
        moreMenu.setLongShotEnabled(helper != null)
        showPanel(moreMenu, anchorX, dp(200), 0)
    }

    override fun onUndo() { canvas.undo() }
    override fun onRedo() { canvas.redo() }

    override fun onDragStart() = Unit
    override fun onDrag(dx: Float, dy: Float) {
        toolbarParams.x = (toolbarParams.x + dx.toInt()).coerceIn(0, (screenWidth - toolbarParams.width).coerceAtLeast(0))
        toolbarParams.y = (toolbarParams.y + dy.toInt()).coerceIn(0, (screenHeight - toolbarParams.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(toolbar, toolbarParams) }
    }

    override fun onDragEnd() {
        prefs.edit().putInt(KEY_BAR_X, toolbarParams.x).putInt(KEY_BAR_Y, toolbarParams.y).apply()
        updateExclusion()
    }

    // ── panels ─────────────────────────────────────────────────────────────

    private fun showColorPanel(anchorX: Int) {
        colorPanel.color = canvas.brush.color
        colorPanel.setAlphaValue(canvas.brush.alpha)
        showPanel(colorPanel, anchorX, dp(338), dp(536))
    }

    private fun showPanel(panel: View, anchorX: Int, widthPx: Int, fixedHeightPx: Int) {
        val already = openPanel === panel && popupHost.visibility == View.VISIBLE
        closePanels(animate = false)
        if (already) return
        // A close animation may still be in flight without an openPanel (it was
        // cleared on close); invalidate its end action before showing again.
        panelEpoch++
        val lp = panel.layoutParams as FrameLayout.LayoutParams
        lp.width = widthPx
        measurePanel(panel, widthPx)
        lp.height = if (fixedHeightPx > 0) fixedHeightPx else panel.measuredHeight
        lp.height = lp.height.coerceIn(dp(64), screenHeight - dp(80))
        val maxX = (screenWidth - widthPx - dp(8)).coerceAtLeast(0)
        lp.leftMargin = (anchorX - widthPx / 2).coerceIn(dp(8), maxX)
        val above = toolbarParams.y - lp.height - dp(10)
        lp.topMargin = if (above >= dp(12)) above else (toolbarParams.y - dp(8) - lp.height).coerceAtLeast(dp(12))
        panel.layoutParams = lp
        // Deterministic order: pose the panel, make the host visible, let one
        // frame lay out, then play the enter animation. Starting the animator
        // before the first layout used to leave the panel blank for a frame.
        // Mi Canvas ArrowPopupView: scale 0.6 -> 1.0 over 280ms with
        // Decelerate(1.5), alpha 0 -> 1 over 100ms.
        panel.alpha = 0f
        panel.scaleX = 0.6f
        panel.scaleY = 0.6f
        panel.visibility = View.VISIBLE
        panel.invalidate()
        openPanel = panel
        popupHost.visibility = View.VISIBLE
        popupHost.invalidate()
        excludeFromCapture(popupHost)
        updateExclusionAfterLayout()
        panel.post {
            if (openPanel === panel) {
                panel.animate().scaleX(1f).scaleY(1f)
                    .setDuration(280)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
                panel.animate().alpha(1f).setDuration(100).start()
            }
        }
        updateExclusion()
    }

    private fun measurePanel(panel: View, widthPx: Int) {
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
    }

    private fun closePanels(animate: Boolean = true) {
        val panel = openPanel ?: return
        openPanel = null
        // Bumping the epoch invalidates the end action of any close animation
        // already in flight: it used to fire 150ms later and hide a panel that
        // had just been reopened.
        val epoch = ++panelEpoch
        if (animate) {
            // Exit: scale 1.0 -> 0.6 and alpha -> 0, both 150ms, Accelerate(2).
            panel.animate().scaleX(0.6f).scaleY(0.6f)
                .setDuration(150)
                .setInterpolator(android.view.animation.AccelerateInterpolator(2f))
                .withEndAction {
                    if (epoch == panelEpoch) {
                        panel.visibility = View.GONE
                        popupHost.visibility = View.GONE
                    }
                }.start()
            panel.animate().alpha(0f).setDuration(150).start()
        } else {
            panel.animate().cancel()
            panel.visibility = View.GONE
            popupHost.visibility = View.GONE
        }
        updateExclusion()
    }

    /** Tell the helper which screen rects must keep their normal input routing. */
    /**
     * The rects are built from a view's laid-out size, so a bar or panel that was
     * just made visible still reports width 0 for the rest of this frame. Without
     * this second pass the toolbar never made it into the exclusion region at all
     * and a pen tap on it painted a stroke instead of pressing the button.
     */
    private fun updateExclusionAfterLayout() {
        toolbar.postDelayed({ updateExclusion() }, 64)
    }

    private fun updateExclusion() {
        val client = helper ?: return
        if (helperReady and HelperProtocol.READY_INTERCEPT == 0) return
        val rects = ArrayList<Rect>()
        fun add(view: View, visible: Boolean) {
            if (!visible || view.width == 0) return
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            rects.add(Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height))
        }
        add(toolbar, toolbarVisible)
        add(orb, active)
        openPanel?.let { add(it, true) }
        // The helper's excluded region is in the display's *current* space, the
        // same space getLocationOnScreen() reports: a rotation mapping here sent
        // the toolbar rect somewhere else and the pen kept painting on it. Measured
        // on the tablet at ROTATION_90 (landscape) with a pen tap inside the
        // mapped rect: not intercepted, while the real toolbar rect still was.
        client.setExcludedRegion(rects)
    }

    // ── panel listeners ────────────────────────────────────────────────────

    override fun onParam(kind: BrushPanel.ParamKind, value: Float) {
        val b = canvas.brush
        // Values arrive in the sliders' own units: size in pen units, sensitivity
        // in percent, tip 1..4, alpha 0..255.
        canvas.brush = when (kind) {
            BrushPanel.ParamKind.SIZE ->
                b.copy(size = value.coerceIn(b.style.minSize.toFloat(), b.style.maxSize.toFloat()))
            BrushPanel.ParamKind.SENSITIVITY -> b.copy(sensitivity = (value / 100f).coerceIn(0f, 1f))
            BrushPanel.ParamKind.TIP -> b.copy(tipSize = value.toInt().coerceIn(1, 4))
            // Mi Canvas' minAlphaValue is 3, so the ink can never be fully invisible.
            BrushPanel.ParamKind.ALPHA -> b.copy(alpha = value.toInt().coerceIn(3, 255))
        }
        refreshToolbar()
        brushPanel.brush = canvas.brush
    }

    override fun onMode(mode: EraserMode) {
        canvas.brush = canvas.brush.copy(eraserMode = mode)
        refreshToolbar()
        if (::eraserPanel.isInitialized) eraserPanel.brush = canvas.brush
    }

    override fun onSize(size: Float) {
        canvas.brush = canvas.brush.copy(eraserSize = size)
        refreshToolbar()
    }

    override fun onAlpha(alpha: Int) {
        canvas.brush = canvas.brush.copy(alpha = alpha)
        refreshToolbar()
    }

    override fun onPipette() = pickColorFromScreen()

    override fun onAction(gesture: String, action: GestureAction) {
        gestureMap[gesture] = action
        saveGestures()
    }

    override fun onGestureSettings() {
        gesturePanel.actions = gestureMap
        showPanel(gesturePanel, screenWidth / 2, dp(288), 0)
    }

    override fun onLongShot() { closePanels(); captureLongShot() }
    override fun onSavePng() { closePanels(); exportPng() }
    override fun onSavePdf() { closePanels(); exportPdf() }
    override fun onClear() { canvas.clear(); closePanels() }
    override fun onAutoMinimize(enabled: Boolean) { autoMinimize = enabled }
    override fun onFingerDraw(enabled: Boolean) { fingerDraw = enabled; applyInputMode() }
    override fun onQuit() { closePanels(); stopSelf() }

    // ── remote actions ─────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleRemoteAction(it) }
        return START_STICKY
    }

    fun handleRemoteAction(intent: Intent) {
        when (intent.action) {
            Actions.LAUNCH -> setActive(true)
            Actions.TOGGLE -> setActive(!active)
            Actions.GESTURE -> {
                val name = intent.getStringExtra(Actions.EXTRA_GESTURE) ?: return
                val gesture = runCatching { Gesture.valueOf(name) }.getOrNull() ?: return
                if (!active) setActive(true) else applyGesture(gesture)
            }
            Actions.SET_SPLIT_MODE -> Unit
            "com.globalannotate.action.DUMPMC" -> {
                for (name in listOf(
                    "com.xiaomi.penpicker.colorpicker.ColorPickerAlphaView",
                    "com.xiaomi.penpicker.colorpicker.ColorPickerSaturationView",
                    "com.xiaomi.penpicker.colorpicker.ColorPickerLightnessView",
                    "com.xiaomi.penpicker.popup.SelectAlphaView",
                )) com.globalannotate.app.mc.MiCanvasUi.dumpMethods(name)
            }
        }
    }

    private fun refreshToolbar() {
        toolbar.update(canvas.brush, canvas.canUndo(), canvas.canRedo())
        orb.style = canvas.brush.style
        orb.color = canvas.brush.color
        saveBrush()
    }

    // ── capture / export ───────────────────────────────────────────────────

    private fun withBackground(block: suspend (Bitmap) -> Unit) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { Exporter.captureScreenRoot() }
            if (bitmap == null) {
                toast("截图失败：${Exporter.lastCaptureError ?: "需要 root 授权"}")
                return@launch
            }
            block(bitmap)
        }
    }

    /**
     * Runs one capture-backed job at a time. `busyExporting` used to be cleared
     * only on the success path, so a failed capture left it stuck and every
     * later export silently did nothing.
     */
    private fun launchExclusive(block: suspend () -> Unit) {
        if (busyExporting) return
        busyExporting = true
        scope.launch {
            try {
                block()
            } catch (error: Throwable) {
                Log.w(TAG, "export failed", error)
                toast(getString(R.string.export_failed))
            } finally {
                busyExporting = false
            }
        }
    }

    private suspend fun captureBackground(): Bitmap? {
        val bitmap = withContext(Dispatchers.IO) { Exporter.captureScreenRoot() }
        if (bitmap == null) {
            toast("截图失败：${Exporter.lastCaptureError ?: "需要 root 授权"}")
        }
        return bitmap
    }

    /**
     * `Bitmap.createScaledBitmap` hands back the source itself when the size
     * already matches; recycling that left `canvas.render` drawing a recycled
     * bitmap, which exported blank or crashed.
     */
    private fun scaledToCanvas(background: Bitmap): Bitmap =
        if (background.width == canvas.width && background.height == canvas.height) {
            background
        } else {
            Bitmap.createScaledBitmap(background, canvas.width, canvas.height, true)
        }

    /**
     * `setSkipScreenshot` needs a live SurfaceControl, which does not exist right
     * after addView(); the one-off attempt always failed and was never retried,
     * so the overlays stayed inside root screenshots (double ink, eyedropper
     * sampling the UI instead of the page).
     */
    private fun excludeFromCapture(view: View, retries: Int = 12) {
        if (Hidden.setSkipScreenshot(view, true)) return
        if (retries <= 0) {
            Log.w(TAG, "skip-screenshot never applied: ${view.javaClass.simpleName}")
            return
        }
        view.postDelayed({ excludeFromCapture(view, retries - 1) }, 80)
    }

    private fun exportPng() {
        launchExclusive {
            val background = captureBackground() ?: return@launchExclusive
            val scaled = scaledToCanvas(background)
            val result = canvas.render(background = scaled)
            if (scaled !== background) scaled.recycle()
            background.recycle()
            val name = withContext(Dispatchers.IO) {
                Exporter.savePng(this@AnnotationService, result)
            }
            result.recycle()
            toast(if (name != null) getString(R.string.export_ok, name) else getString(R.string.export_failed))
        }
    }

    private fun exportPdf() {
        launchExclusive {
            val background = captureBackground() ?: return@launchExclusive
            val scaled = scaledToCanvas(background)
            val result = canvas.render(background = scaled)
            if (scaled !== background) scaled.recycle()
            background.recycle()
            val name = withContext(Dispatchers.IO) {
                Exporter.savePdf(this@AnnotationService, result)
            }
            result.recycle()
            toast(if (name != null) getString(R.string.export_ok, name) else getString(R.string.export_failed))
        }
    }

    /**
     * 滚动批注 → 长图: scroll the page through the helper, capture each screen,
     * stitch by overlap, and composite the ink that belongs to each frame.
     */
    private fun captureLongShot() {
        if (busyExporting) return
        val client = helper
        if (client == null || helperReady == 0) {
            toast("长截图需要 root 助手")
            return
        }
        busyExporting = true
        toast("正在滚动截取…")
        scope.launch {
            var accumulated: Bitmap? = null
            try {
                val first = captureComposite()
                if (first == null) {
                    toast("截图失败：${Exporter.lastCaptureError ?: "需要 root 授权"}")
                    return@launch
                }
                accumulated = first.copy(Bitmap.Config.ARGB_8888, false)
                first.recycle()
                var stagnant = 0
                for (i in 0 until MAX_LONG_SCREENS - 1) {
                    val before = canvas.scrollOffset
                    val swipe = "${screenWidth / 2},${(screenHeight * 0.75f).toInt()}," +
                        "${screenWidth / 2},${(screenHeight * 0.32f).toInt()},320"
                    client.send("${HelperProtocol.CMD_INJECT} $swipe")
                    delay(520)
                    val frame = captureComposite() ?: break
                    val moved = abs(canvas.scrollOffset - before) > 4f
                    if (!moved) {
                        frame.recycle()
                        stagnant++
                        if (stagnant >= 2) break
                        continue
                    }
                    val current = accumulated ?: break
                    val overlap = LongCapture.findOverlap(current, frame)
                    if (overlap < 40) {
                        frame.recycle()
                        break
                    }
                    val heightBefore = current.height
                    val grown = LongCapture.append(current, frame, overlap)
                    accumulated = grown
                    // append() recycles both inputs; at MAX_HEIGHT it returns the
                    // accumulated bitmap unchanged, so stop instead of capturing
                    // frames that can never be stitched.
                    if (grown.height <= heightBefore) break
                }
                val result = accumulated
                if (result == null || result.height <= screenHeight + 4) {
                    result?.recycle()
                    accumulated = null
                    toast("页面没有可滚动的内容")
                    return@launch
                }
                val pngName = withContext(Dispatchers.IO) {
                    Exporter.savePng(this@AnnotationService, result)
                }
                withContext(Dispatchers.IO) { Exporter.savePdf(this@AnnotationService, result) }
                result.recycle()
                accumulated = null
                toast(
                    if (pngName != null) "长图已保存（${pngName}）+ PDF" else getString(R.string.export_failed),
                )
            } catch (error: Throwable) {
                Log.w(TAG, "long capture failed", error)
                toast(getString(R.string.export_failed))
            } finally {
                busyExporting = false
                accumulated?.let { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private suspend fun captureComposite(): Bitmap? {
        val background = withContext(Dispatchers.IO) { Exporter.captureScreenRoot() } ?: return null
        val ink = canvas.renderInk()
        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(result)
        c.drawBitmap(background, 0f, 0f, null)
        c.drawBitmap(
            ink, null,
            Rect(0, 0, background.width, background.height),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        background.recycle()
        ink.recycle()
        return result
    }

    private fun pickColorFromScreen() {
        withBackground { bitmap ->
            val x = penTip.x.toInt().coerceIn(0, bitmap.width - 1)
            val y = penTip.y.toInt().coerceIn(0, bitmap.height - 1)
            val picked = bitmap.getPixel(x, y)
            bitmap.recycle()
            val opaque = picked or 0xFF000000.toInt()
            canvas.brush = canvas.brush.copy(color = opaque)
            colorPanel.color = opaque
            brushPanel.updateColor(opaque)
            refreshToolbar()
            toast("已取色 #" + "%06X".format(opaque and 0xFFFFFF))
        }
    }

    // ── persistence ────────────────────────────────────────────────────────

    private fun saveBrush() {
        val b = canvas.brush
        prefs.edit()
            .putString(KEY_STYLE, b.style.name)
            .putInt(KEY_COLOR, b.color)
            .putFloat(KEY_SIZE, b.size)
            .putInt(KEY_ALPHA, b.alpha)
            .putFloat(KEY_SENSITIVITY, b.sensitivity)
            .putInt(KEY_TIP, b.tipSize)
            .putString(KEY_ERASER_MODE, b.eraserMode.name)
            .putFloat(KEY_ERASER_SIZE, b.eraserSize)
            .apply()
    }

    private fun restoreBrush() {
        val p = prefs
        val style = runCatching {
            PenStyle.valueOf(p.getString(KEY_STYLE, PenStyle.INK_PEN.name) ?: PenStyle.INK_PEN.name)
        }.getOrDefault(PenStyle.INK_PEN)
        val eraserMode = runCatching {
            EraserMode.valueOf(p.getString(KEY_ERASER_MODE, EraserMode.PIXEL.name) ?: EraserMode.PIXEL.name)
        }.getOrDefault(EraserMode.PIXEL)
        // Values may have been stored under a different type by older builds,
        // so read them as numbers instead of trusting getInt/getFloat.
        val all = p.all
        fun num(key: String): Number? = all[key] as? Number
        canvas.brush = BrushConfig(
            style = style,
            color = num(KEY_COLOR)?.toInt() ?: Color.parseColor("#E8464A"),
            size = num(KEY_SIZE)?.toFloat() ?: style.defaultSize.toFloat(),
            alpha = num(KEY_ALPHA)?.toInt() ?: style.defaultAlpha,
            sensitivity = num(KEY_SENSITIVITY)?.toFloat() ?: 0.38f,
            tipSize = (num(KEY_TIP)?.toFloat() ?: 2f).toInt().coerceIn(1, 4),
            eraserMode = eraserMode,
            eraserSize = num(KEY_ERASER_SIZE)?.toFloat() ?: PenStyle.ERASER.defaultSize.toFloat(),
        )
    }

    private fun saveGestures() {
        val editor = prefs.edit()
        gestureMap.forEach { (k, v) -> editor.putString("gesture_$k", v.name) }
        editor.apply()
    }

    private fun restoreGestures() {
        for (gesture in Gesture.entries) {
            val saved = prefs.getString("gesture_${gesture.name}", null) ?: continue
            runCatching { GestureAction.valueOf(saved) }
                .onSuccess { gestureMap[gesture.name] = it }
        }
    }

    // ── plumbing ───────────────────────────────────────────────────────────

    private fun notifyModule(action: String, enabled: Boolean) {
        val intent = Intent(action)
            .putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN)
            .putExtra(Actions.EXTRA_ENABLED, enabled)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        runCatching { sendBroadcast(intent) }
    }

    private fun overlay(width: Int, height: Int, flags: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

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
        helper?.stop()
        helper = null
        (listOf(canvas, toolbar, orb, popupHost, palette)).forEach { view ->
            if (view.isAttachedToWindow) runCatching { windowManager.removeView(view) }
        }
        if (Session.service === this) Session.service = null
        if (Session.canvas === canvas) Session.canvas = null
        Session.active = false
        scope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AnnotationService"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "global_annotate"
        private const val BAR_HEIGHT_DP = ToolbarView.WINDOW_HEIGHT_DP
        private const val MAX_LONG_SCREENS = 12
        private const val MATCH = WindowManager.LayoutParams.MATCH_PARENT
        private const val WRAP = WindowManager.LayoutParams.WRAP_CONTENT
        private val PANEL_EASE = PathInterpolator(0f, 0f, 0.2f, 1f)
        private const val PREFS = "global_annotate_brush"
        // v2 keys: old builds stored a position for a 102dp window, which made
        // the taller bar sink into the dock after the upgrade.
        private const val KEY_BAR_X = "bar_x_v2"
        private const val KEY_BAR_Y = "bar_y_v2"
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

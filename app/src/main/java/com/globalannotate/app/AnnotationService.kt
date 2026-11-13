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
 * Overlay host. Like Mi Canvas, exiting is a dedicated back button in the
 * top-left corner — far from the ⋮ menu and the export items — instead of
 * sitting next to them in the bottom bar.
 */
class AnnotationService : Service(),
    ToolbarView.Listener,
    RadialPaletteView.Listener,
    ColorPanel.Listener,
    BrushPanel.Listener,
    EraserPanel.Listener,
    MoreMenu.Listener {

    private lateinit var windowManager: WindowManager
    private lateinit var canvas: AnnotationCanvasView
    private lateinit var canvasParams: WindowManager.LayoutParams
    private lateinit var toolbar: ToolbarView
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var orb: BrushGlyph
    private lateinit var orbParams: WindowManager.LayoutParams
    private lateinit var exitButton: IconButton
    private lateinit var exitParams: WindowManager.LayoutParams
    private lateinit var palette: RadialPaletteView
    private lateinit var paletteParams: WindowManager.LayoutParams
    private lateinit var colorPanel: ColorPanel
    private lateinit var colorPanelParams: WindowManager.LayoutParams
    private lateinit var brushPanel: BrushPanel
    private lateinit var brushPanelParams: WindowManager.LayoutParams
    private lateinit var eraserPanel: EraserPanel
    private lateinit var eraserPanelParams: WindowManager.LayoutParams
    private lateinit var moreMenu: MoreMenu
    private lateinit var moreMenuParams: WindowManager.LayoutParams

    private var mode: OverlayMode = OverlayMode.DRAW
    private var active = false
    private var toolbarVisible = true
    private var penEngine: PenEngineClient? = null
    private val penTip = PointF(0f, 0f)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val panels: List<View> get() = listOf(colorPanel, brushPanel, eraserPanel, moreMenu)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val screen = windowManager.maximumWindowMetrics.bounds
        penTip.set(screen.width() / 2f, screen.height() / 2f)

        canvas = AnnotationCanvasView(this).apply {
            onGesture = ::applyGesture
            onStateChanged = ::refreshToolbar
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
        exitButton = IconButton(this).apply {
            iconRes = R.drawable.ic_back
            contentDescription = "退出批注"
            setBackgroundResource(R.drawable.bg_orb)
            elevation = dp(6).toFloat()
            onTap = { stopSelf() }
        }
        palette = RadialPaletteView(this).apply { listener = this@AnnotationService }
        colorPanel = ColorPanel(this, this)
        brushPanel = BrushPanel(this, this)
        eraserPanel = EraserPanel(this, this)
        moreMenu = MoreMenu(this, this)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val passiveFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        canvasParams = params(MATCH, MATCH, baseFlags)
        toolbarParams = params((screen.width() * 0.76f).toInt(), WRAP, passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(30)
        }
        orbParams = params(dp(88), dp(88), passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(26)
            y = dp(26)
        }
        exitParams = params(dp(48), dp(48), passiveFlags).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(16)
        }
        paletteParams = params(MATCH, MATCH, passiveFlags)
        colorPanelParams = params(dp(360), WRAP, passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(170)
        }
        brushPanelParams = params(dp(320), WRAP, passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(170)
        }
        eraserPanelParams = params(dp(280), WRAP, passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(170)
        }
        moreMenuParams = params(dp(260), WRAP, passiveFlags).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(170)
        }

        windowManager.addView(canvas, canvasParams)
        windowManager.addView(toolbar, toolbarParams)
        windowManager.addView(palette, paletteParams)
        windowManager.addView(colorPanel, colorPanelParams)
        windowManager.addView(brushPanel, brushPanelParams)
        windowManager.addView(eraserPanel, eraserPanelParams)
        windowManager.addView(moreMenu, moreMenuParams)
        windowManager.addView(orb, orbParams)
        windowManager.addView(exitButton, exitParams)

        panels.forEach { it.visibility = View.GONE }
        restoreBrush()
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
        exitButton.visibility = if (value) View.VISIBLE else View.GONE
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

    private fun applyToolbarVisibility() {
        if (::toolbar.isInitialized) {
            toolbar.visibility = if (active && toolbarVisible) View.VISIBLE else View.GONE
        }
    }

    private fun applyRemoteGesture(gesture: Gesture) {
        if (!active) { setActive(true); return }
        applyGesture(gesture)
    }

    private fun applyGesture(gesture: Gesture) {
        when (gesture) {
            Gesture.SQUEEZE -> togglePalette()
            Gesture.DOUBLE_TAP -> switchTool()
            Gesture.SLIDE_UP -> adjustSize(1)
            Gesture.SLIDE_DOWN -> adjustSize(-1)
        }
    }

    private fun togglePalette() {
        if (!active) { setActive(true); return }
        if (palette.visibility == View.VISIBLE) palette.hide()
        else palette.showAt(penTip.x, penTip.y)
    }

    private fun switchTool() {
        canvas.brush = if (canvas.brush.style.isEraser) {
            canvas.brush.copy(style = PenStyle.INK_PEN, size = PenStyle.INK_PEN.defaultSize.toFloat())
        } else {
            canvas.brush.copy(style = PenStyle.ERASER)
        }
        refreshToolbar()
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
    override fun onStyle(style: PenStyle) {
        canvas.brush = canvas.brush.copy(style = style, size = style.defaultSize.toFloat(), alpha = style.defaultAlpha)
        refreshToolbar()
        if (style.isEraser) {
            eraserPanel.brush = canvas.brush
            showPanel(eraserPanel)
        } else {
            closePanels()
        }
    }

    override fun onColor(color: Int) {
        canvas.brush = canvas.brush.copy(color = color)
        refreshToolbar()
    }

    override fun onOpenColorPanel() {
        colorPanel.color = canvas.brush.color
        colorPanel.opacity = canvas.brush.alpha
        showPanel(colorPanel)
    }

    override fun onOpenBrushPanel() {
        if (canvas.brush.style.isEraser) {
            eraserPanel.brush = canvas.brush
            showPanel(eraserPanel)
        } else {
            brushPanel.brush = canvas.brush
            showPanel(brushPanel)
        }
    }

    override fun onOpenMoreMenu() = showPanel(moreMenu)
    override fun onUndo() { canvas.undo() }
    override fun onRedo() { canvas.redo() }
    override fun onCollapse() {
        toolbarVisible = false
        applyToolbarVisibility()
    }

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

    // ── MoreMenu.Listener ───────────────────────────────────────────────────
    override fun onInsertText() = notImplemented("插入文本")
    override fun onInsertShape() = notImplemented("插入形状")
    override fun onAutoMinimize(enabled: Boolean) = Unit
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

    private fun notImplemented(name: String) {
        Toast.makeText(this, "$name 暂未实现", Toast.LENGTH_SHORT).show()
    }

    /** Tapping the same entry again closes the panel. */
    private fun showPanel(target: View) {
        val alreadyOpen = target.visibility == View.VISIBLE
        panels.forEach { it.visibility = View.GONE }
        if (!alreadyOpen) target.visibility = View.VISIBLE
        palette.hide()
    }

    private fun closePanels() {
        panels.forEach { it.visibility = View.GONE }
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
        val views = listOf(canvas, toolbar, orb, exitButton, palette) + panels
        val states = views.map { it to it.visibility }
        states.forEach { (view, _) -> view.visibility = View.INVISIBLE }
        return try {
            delay(OVERLAY_HIDE_SETTLE_MS)
            block()
        } finally {
            states.forEach { (view, state) -> view.visibility = state }
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
        (listOf(canvas, toolbar, palette, orb, exitButton) + panels).forEach { view ->
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

package com.globalannotate.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.globalannotate.app.Gesture
import com.globalannotate.app.OverlayMode
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.stylus.FocusPenGestureDetector
import kotlin.math.hypot

/**
 * Ink is kept on an offscreen ARGB_8888 bitmap so erasers can use
 * PorterDuff.Mode.CLEAR. A stroke history is kept alongside it for undo/redo,
 * brush-style aware re-rendering and fading laser strokes.
 */
class AnnotationCanvasView(context: Context) : View(context) {

    private val strokes = mutableListOf<InkStroke>()
    private val redoStack = mutableListOf<InkStroke>()
    private var active: InkStroke? = null
    private var ink: Bitmap? = null
    private var inkCanvas: Canvas? = null
    private var hoverPoint: PointF? = null
    private val density = resources.displayMetrics.density
    private val fadeHandler = Handler(Looper.getMainLooper())

    var brush: BrushConfig = BrushConfig()
    var mode: OverlayMode = OverlayMode.DRAW
    var allowFinger: Boolean = false
    var onGesture: ((Gesture) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null

    // Compatibility accessors used by AnnotationService.
    var penColor: Int
        get() = brush.color
        set(value) { brush = brush.copy(color = value) }
    var baseWidth: Float
        get() = brush.widthDp
        set(value) { brush = brush.copy(widthDp = value.coerceIn(1f, 60f)) }
    var eraserMode: Boolean
        get() = brush.style.isEraser
        set(value) {
            brush = if (value) brush.copy(style = PenStyle.ERASER_PIXEL)
            else brush.copy(style = PenStyle.PEN)
        }
    var style: PenStyle
        get() = brush.style
        set(value) {
            brush = brush.copy(style = value, widthDp = value.defaultWidthDp)
            onStateChanged?.invoke()
        }
    var pressureEnabled: Boolean
        get() = brush.pressureEnabled
        set(value) { brush = brush.copy(pressureEnabled = value) }

    private val detector = FocusPenGestureDetector { gesture -> onGesture?.invoke(gesture) }

    /** Resolved stroke width in px. Erasers are pressure-sensitive too. */
    private fun widthFor(pressure: Float): Float {
        val base = brush.widthDp * density
        val p = if (brush.pressureEnabled && brush.style.pressureSensitive) {
            pressure.coerceIn(0.05f, 1f).takeIf { it > 0f } ?: 0.5f
        } else 0.5f
        return (base * (0.45f + 0.85f * p)).coerceAtLeast(1f)
    }

    private fun paintFor(stroke: InkStroke): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (stroke.eraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.BLACK
        } else {
            val a = stroke.style.alpha
            color = Color.argb(a, Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recreateInk()
    }

    private fun recreateInk() {
        ink?.recycle()
        ink = null
        inkCanvas = null
        if (width <= 0 || height <= 0) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        ink = bitmap
        inkCanvas = Canvas(bitmap)
        rebuildInk()
    }

    private fun rebuildInk() {
        val canvas = inkCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        strokes.forEach { drawStroke(canvas, it) }
        invalidate()
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        val points = stroke.points
        if (points.isEmpty()) return
        val paint = paintFor(stroke)
        if (points.size == 1) {
            paint.strokeWidth = points[0].width
            canvas.drawPoint(points[0].x, points[0].y, paint)
            return
        }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            paint.strokeWidth = ((a.width + b.width) * 0.5f).coerceAtLeast(0.5f)
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    private fun drawTail(stroke: InkStroke) {
        val canvas = inkCanvas ?: return
        val points = stroke.points
        val paint = paintFor(stroke)
        if (points.size == 1) {
            paint.strokeWidth = points[0].width
            canvas.drawPoint(points[0].x, points[0].y, paint)
            return
        }
        val a = points[points.size - 2]
        val b = points[points.size - 1]
        paint.strokeWidth = ((a.width + b.width) * 0.5f).coerceAtLeast(0.5f)
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    /** Stroke eraser: remove the last stroke intersecting the pen-down point. */
    private fun eraseStrokeAt(x: Float, y: Float) {
        val radius = widthFor(0.5f) * 2f
        val index = strokes.indexOfLast { stroke ->
            !stroke.style.isEraser && stroke.points.any { point ->
                hypot(point.x - x, point.y - y) < radius + point.width
            }
        }
        if (index >= 0) {
            strokes.removeAt(index)
            redoStack.clear()
            rebuildInk()
            onStateChanged?.invoke()
        }
    }

    private fun begin(x: Float, y: Float, pressure: Float) {
        if (brush.style.isStrokeEraser) {
            eraseStrokeAt(x, y)
            return
        }
        redoStack.clear()
        val stroke = InkStroke(color = brush.color, style = brush.style, baseWidth = brush.widthDp)
        stroke.points.add(InkPoint(x, y, widthFor(pressure)))
        strokes.add(stroke)
        active = stroke
        drawTail(stroke)
        invalidate()
        onStateChanged?.invoke()
    }

    private fun move(x: Float, y: Float, pressure: Float) {
        val stroke = active ?: return
        stroke.points.add(InkPoint(x, y, widthFor(pressure)))
        drawTail(stroke)
        invalidate()
    }

    private fun end() {
        val finished = active ?: return
        active = null
        if (finished.style.fades) {
            fadeHandler.postDelayed({
                if (strokes.remove(finished)) {
                    rebuildInk()
                    onStateChanged?.invoke()
                }
            }, LASER_FADE_MS)
        }
        onStateChanged?.invoke()
    }

    fun injectStylusEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event.x, event.y, event.pressure)
            MotionEvent.ACTION_MOVE -> {
                if (event.historySize > 0) {
                    for (i in 0 until event.historySize) {
                        move(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalPressure(i))
                    }
                }
                move(event.x, event.y, event.pressure)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> end()
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverPoint = PointF(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverPoint = null
                invalidate()
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode != OverlayMode.DRAW) return false
        val tool = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        if (!allowFinger && tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) {
            return true
        }
        injectStylusEvent(event)
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean = injectStylusEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (detector.onKey(event.keyCode, event.action, event.repeatCount, event.scanCode)) return true
        return super.dispatchKeyEvent(event)
    }

    fun requestGestureFocus() {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun clearGestureFocus() {
        clearFocus()
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ink?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        hoverPoint?.let { point ->
            val preview = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = brush.color
                alpha = 140
                strokeWidth = 2f
            }
            canvas.drawCircle(point.x, point.y, widthFor(0.5f) * 0.5f, preview)
        }
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        rebuildInk()
        onStateChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        rebuildInk()
        onStateChanged?.invoke()
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
        active = null
        fadeHandler.removeCallbacksAndMessages(null)
        rebuildInk()
        onStateChanged?.invoke()
    }

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Transparent ink layer, optionally composited over a captured screen. */
    fun render(background: Bitmap? = null, paperColor: Int? = null): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        paperColor?.let { canvas.drawColor(it) }
        background?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        ink?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        return result
    }

    private companion object {
        const val LASER_FADE_MS = 1400L
    }
}

package com.globalannotate.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.globalannotate.app.Gesture
import com.globalannotate.app.OverlayMode
import com.globalannotate.app.stylus.FocusPenGestureDetector

/**
 * Ink is kept on an offscreen ARGB_8888 bitmap so the eraser can use
 * PorterDuff.Mode.CLEAR (hardware-accelerated View canvases do not honour it).
 * A stroke history is kept alongside it for undo/redo and for re-rendering after
 * a size change.
 */
class AnnotationCanvasView(context: Context) : View(context) {

    private val strokes = mutableListOf<InkStroke>()
    private val redoStack = mutableListOf<InkStroke>()
    private var active: InkStroke? = null
    private var ink: Bitmap? = null
    private var inkCanvas: Canvas? = null
    private var hoverPoint: PointF? = null

    var penColor: Int = Color.parseColor("#D93025")
    var baseWidth: Float = 6f
    var eraserWidth: Float = 48f
    var eraserMode: Boolean = false
    var allowFinger: Boolean = false
    var mode: OverlayMode = OverlayMode.DRAW

    var onGesture: ((Gesture) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null

    private val detector = FocusPenGestureDetector { gesture -> onGesture?.invoke(gesture) }

    private fun widthFor(pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f).takeIf { it > 0f } ?: 0.5f
        return if (eraserMode) eraserWidth else (baseWidth * (0.45f + 0.85f * p)).coerceAtLeast(1f)
    }

    private fun paintFor(stroke: InkStroke): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (stroke.eraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.BLACK
        } else {
            color = stroke.color
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

    /** Replays the whole history. Used by undo/redo and after a resize. */
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

    private fun begin(x: Float, y: Float, pressure: Float) {
        redoStack.clear()
        val stroke = InkStroke(color = penColor, eraser = eraserMode)
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
        active = null
        onStateChanged?.invoke()
    }

    /**
     * Feeds one stylus event. In [OverlayMode.ROOT_SPLIT] these arrive from
     * system_server through the binder sink; in [OverlayMode.DRAW] they arrive
     * from [onTouchEvent].
     */
    fun injectStylusEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        val tool = event.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(event.x, event.y, event.pressure)
            MotionEvent.ACTION_MOVE -> {
                // Coalesced samples keep fast strokes smooth without a prediction SDK.
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
            // Consume finger input so it neither draws nor leaks into the app below.
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
                color = penColor
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
}

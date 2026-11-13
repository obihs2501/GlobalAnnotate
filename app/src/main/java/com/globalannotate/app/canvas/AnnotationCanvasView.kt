package com.globalannotate.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.stylus.FocusPenGestureDetector
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Ink surface. Every [PenStyle] has its own renderer so the strokes actually
 * look different, not just wider:
 *
 *  - 铅笔      three jittered hairlines + graphite dust
 *  - 圆珠笔    constant hairline with a thin sheen
 *  - 钢笔      variable-width ribbon that tapers at both ends
 *  - 马克笔    flat square-cap ribbon, translucent, darkens on overlap
 *  - 荧光笔    same ribbon, very low alpha, wide
 *  - 激光笔    glow + 1.4s fade
 *  - 橡皮      CLEAR with size only (no alpha, no pressure)
 *
 * While the pen is down the newest segment is painted incrementally for speed;
 * on pen-up the whole stroke is re-rendered so taper and strands are exact.
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
    var onPenDown: (() -> Unit)? = null

    private val detector = FocusPenGestureDetector { gesture -> onGesture?.invoke(gesture) }

    private fun sizeFor(): Float =
        if (brush.style.isEraser) brush.eraserSize else brush.size

    private fun widthFor(pressure: Float): Float {
        val px = brush.style.toPixels(sizeFor(), density)
        if (brush.style.isEraser) return px
        return when (brush.style) {
            PenStyle.PENCIL -> {
                val p = pressure.coerceIn(0.15f, 1f)
                px * (0.55f + 0.45f * p * (0.5f + brush.sensitivity))
            }
            PenStyle.INK_PEN -> {
                val p = pressure.coerceIn(0.05f, 1f)
                px * (0.20f + 1.30f * p * (0.4f + brush.sensitivity))
            }
            PenStyle.BALLPOINT -> px * 0.92f
            else -> px
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
        strokes.forEach { renderStroke(canvas, it) }
        invalidate()
    }

    // ── renderers ───────────────────────────────────────────────────────────

    private fun fillPaintFor(stroke: InkStroke): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        if (stroke.style.isEraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.BLACK
        } else {
            color = stroke.color
        }
    }

    private fun renderStroke(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        when (stroke.style) {
            PenStyle.PENCIL -> renderPencil(canvas, stroke)
            PenStyle.BALLPOINT -> renderBallpoint(canvas, stroke)
            PenStyle.LASER -> renderLaser(canvas, stroke)
            PenStyle.INK_PEN -> renderRibbon(canvas, stroke, taper = true)
            PenStyle.MARKER, PenStyle.HIGHLIGHTER, PenStyle.ERASER ->
                renderRibbon(canvas, stroke, taper = false)
        }
    }

    /** Filled quad strip: real variable width, optional tapered ends. */
    private fun renderRibbon(canvas: Canvas, stroke: InkStroke, taper: Boolean) {
        val pts = stroke.points
        val paint = fillPaintFor(stroke)
        if (pts.size == 1) {
            canvas.drawCircle(pts[0].x, pts[0].y, pts[0].width / 2f, paint)
            return
        }
        val left = ArrayList<FloatArray>(pts.size)
        val right = ArrayList<FloatArray>(pts.size)
        for (i in pts.indices) {
            val prev = pts[(i - 1).coerceAtLeast(0)]
            val next = pts[(i + 1).coerceAtMost(pts.size - 1)]
            var dx = next.x - prev.x
            var dy = next.y - prev.y
            val len = hypot(dx, dy).coerceAtLeast(0.0001f)
            dx /= len
            dy /= len
            var half = pts[i].width / 2f
            if (taper) {
                val t = i / (pts.size - 1f)
                val pinch = minOf(t, 1f - t) * 2f
                half *= 0.30f + 0.70f * pinch
            }
            left.add(floatArrayOf(pts[i].x - dy * half, pts[i].y + dx * half))
            right.add(floatArrayOf(pts[i].x + dy * half, pts[i].y - dx * half))
        }
        val path = Path()
        path.moveTo(left[0][0], left[0][1])
        for (i in 1 until left.size) path.lineTo(left[i][0], left[i][1])
        for (i in right.indices.reversed()) path.lineTo(right[i][0], right[i][1])
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun renderBallpoint(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        val base = pts.first().width
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            strokeWidth = base
        }
        val path = pathOf(pts)
        canvas.drawPath(path, line)
        canvas.drawPath(path, Paint(line).apply {
            strokeWidth = (base * 0.28f).coerceAtLeast(0.6f)
            color = Color.argb(90, 255, 255, 255)
        })
    }

    private fun renderLaser(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        val base = pts.first().width
        val path = pathOf(pts)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            strokeWidth = base
        }
        canvas.drawPath(path, Paint(core).apply {
            alpha = 60
            strokeWidth = base * 2.8f
        })
        canvas.drawPath(path, core)
    }

    /** Dry graphite: three wobbling hairlines plus dust. */
    private fun renderPencil(canvas: Canvas, stroke: InkStroke) {
        val pts = stroke.points
        val base = pts.first().width
        val color = stroke.color
        for (s in 0 until 3) {
            val offset = (s - 1) * base * 0.30f
            val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = (base * 0.34f).coerceAtLeast(0.7f)
                this.color = Color.argb(
                    (Color.alpha(color) * 0.55f).toInt(),
                    Color.red(color), Color.green(color), Color.blue(color),
                )
            }
            val path = Path()
            for (i in pts.indices) {
                val p = pts[i]
                val prev = pts[(i - 1).coerceAtLeast(0)]
                val next = pts[(i + 1).coerceAtMost(pts.size - 1)]
                var dx = next.x - prev.x
                var dy = next.y - prev.y
                val len = hypot(dx, dy).coerceAtLeast(0.0001f)
                dx /= len
                dy /= len
                val wobble = (noise(p.x, p.y, s) - 0.5f) * base * 0.55f
                val x = p.x + (-dy) * (offset + wobble)
                val y = p.y + dx * (offset + wobble)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, hair)
        }
        val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(
                (Color.alpha(color) * 0.35f).toInt(),
                Color.red(color), Color.green(color), Color.blue(color),
            )
        }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val n = (hypot(b.x - a.x, b.y - a.y) / 2.5f).toInt().coerceIn(0, 8)
            for (k in 0 until n) {
                val t = noise(a.x, b.y, k + i)
                val jx = (noise(b.x, a.y, k + i + 7) - 0.5f) * base * 1.4f
                val jy = (noise(a.y, b.x, k + i + 13) - 0.5f) * base * 1.4f
                canvas.drawCircle(
                    a.x + (b.x - a.x) * t + jx,
                    a.y + (b.y - a.y) * t + jy,
                    0.55f * density, dust,
                )
            }
        }
    }

    private fun pathOf(pts: List<InkPoint>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }

    /** Fast incremental paint while the pen is down. */
    private fun drawTail(stroke: InkStroke) {
        val canvas = inkCanvas ?: return
        val pts = stroke.points
        if (pts.size == 1) {
            canvas.drawCircle(pts[0].x, pts[0].y, pts[0].width / 2f, fillPaintFor(stroke))
            return
        }
        val a = pts[pts.size - 2]
        val b = pts[pts.size - 1]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = if (stroke.style.squareCap) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = ((a.width + b.width) * 0.5f).coerceAtLeast(0.6f)
            if (stroke.style.isEraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                color = Color.BLACK
            } else {
                color = stroke.color
            }
        }
        if (stroke.style.glow) {
            canvas.drawLine(a.x, a.y, b.x, b.y, Paint(paint).apply {
                alpha = 60
                strokeWidth = paint.strokeWidth * 2.4f
            })
        }
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        if (stroke.style.inkHighlight) {
            canvas.drawLine(a.x, a.y, b.x, b.y, Paint(paint).apply {
                strokeWidth = paint.strokeWidth * 0.3f
                color = Color.argb(90, 255, 255, 255)
            })
        }
    }

    private fun noise(x: Float, y: Float, i: Int): Float =
        abs((sin(x * 12.9898f + y * 78.233f + i * 37.719f) * 43758.5453f) % 1f)

    // ── stroke lifecycle ────────────────────────────────────────────────────

    private fun eraseStrokeAt(x: Float, y: Float) {
        val radius = widthFor(0.5f) * 1.6f
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
        if (brush.style.isEraser && brush.eraserMode == EraserMode.STROKE) {
            eraseStrokeAt(x, y)
            return
        }
        redoStack.clear()
        val stroke = InkStroke(
            color = if (brush.style.isEraser) Color.BLACK else brush.effectiveColor,
            style = brush.style,
            baseWidth = sizeFor(),
        )
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
        // Re-render so taper / strands / ribbon geometry are exact.
        rebuildInk()
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
            MotionEvent.ACTION_DOWN -> {
                onPenDown?.invoke()
                begin(event.x, event.y, event.pressure)
            }
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
                color = if (brush.style.isEraser) Color.WHITE else brush.color
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

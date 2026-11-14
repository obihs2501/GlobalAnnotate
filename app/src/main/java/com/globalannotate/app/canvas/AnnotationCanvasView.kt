package com.globalannotate.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Ink surface.
 *
 * Strokes are stored in **content coordinates** (screen y + [scrollOffset]), so
 * annotating a long article the way Huawei's 滚动批注 does works: the finger
 * scrolls the app underneath, the ink stays glued to the page, and a new stroke
 * can continue from where the last one was left.
 *
 * The rendered ink lives in one screen-sized bitmap; while a finger scroll is in
 * flight the bitmap is shifted by the same delta, and the exact re-render
 * follows as soon as scrolling settles.
 */
class AnnotationCanvasView(context: Context) : View(context) {

    private val engine = InkEngine().also { it.setDensity(resources.displayMetrics.density) }
    private val density = resources.displayMetrics.density

    val strokes = mutableListOf<InkStroke>()
    private val redoStack = mutableListOf<InkStroke>()
    private var active: InkStroke? = null
    private var ink: Bitmap? = null
    private var spare: Bitmap? = null
    private var inkCanvas: Canvas? = null
    /** Ink before the active stroke, so the stroke is repainted whole. */
    private var baseInk: Bitmap? = null

    var brush: BrushConfig = BrushConfig()
    var allowFinger: Boolean = false
    var scrollOffset: Float = 0f
        private set
    var hoverPoint: PointF? = null
    var onStateChanged: (() -> Unit)? = null
    var onPenDown: (() -> Unit)? = null
    var onOutsideTouch: (() -> Unit)? = null
    private var hoverPressure = 0f
    private var hoverTilt = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val rebuildTask = Runnable { rebuildInk() }
    private var rebuildPending = false

    private var downTime = 0L
    private var lastMoveTime = 0L
    private var anchorX = 0f
    private var anchorY = 0f

    // ── sizing ─────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ink?.recycle()
        spare?.recycle()
        ink = null
        spare = null
        inkCanvas = null
        ensureInk()
        rebuildInk()
    }

    private fun ensureInk() {
        if (width <= 0 || height <= 0) return
        if (ink != null && ink!!.width == width && ink!!.height == height) return
        ink = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        inkCanvas = Canvas(ink!!)
    }

    // ── brush maths ────────────────────────────────────────────────────────

    private fun baseWidth(): Float =
        if (brush.style.isEraser) brush.style.toPixels(brush.eraserSize, density)
        else brush.style.toPixels(brush.size, density)

    private fun widthFor(pressure: Float, tilt: Float): Float {
        val base = baseWidth()
        if (brush.style.isEraser) return base
        val s = brush.sensitivity.coerceIn(0f, 1f)
        return when (brush.style) {
            PenStyle.BALLPOINT -> base
            PenStyle.PENCIL -> {
                val tiltBoost = 1f + tilt.coerceIn(0f, 1f) * 1.2f * s
                base * tiltBoost * (0.85f + 0.3f * pressure)
            }
            PenStyle.INK_PEN -> {
                val tip = 0.75f + brush.tipSize * 0.12f
                base * tip * (0.18f + 1.25f * pressure.coerceIn(0.02f, 1f) * (0.45f + 1.1f * s))
            }
            PenStyle.MARKER, PenStyle.HIGHLIGHTER -> base * (0.92f + 0.08f * pressure)
            PenStyle.WATER_COLOR -> base * (0.7f + 0.5f * pressure)
            PenStyle.ERASER -> base
        }
    }

    private fun tiltOf(event: MotionEvent): Float {
        // AXIS_TILT is 0..pi/2; normalise, then weight by the pen's tilt angle.
        val tilt = runCatching { event.getAxisValue(MotionEvent.AXIS_TILT) }.getOrDefault(0f)
        return (tilt / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
    }

    // ── stroke lifecycle ───────────────────────────────────────────────────

    fun injectStylusEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onPenDown?.invoke()
                begin(event.x, event.y + scrollOffset, event.pressure, tiltOf(event))
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.historySize > 0) {
                    for (i in 0 until event.historySize) {
                        move(
                            event.getHistoricalX(i),
                            event.getHistoricalY(i) + scrollOffset,
                            event.getHistoricalPressure(i),
                            tiltOf(event),
                        )
                    }
                }
                move(event.x, event.y + scrollOffset, event.pressure, tiltOf(event))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> end()
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverPoint = PointF(event.x, event.y)
                hoverPressure = event.pressure
                hoverTilt = tiltOf(event)
                invalidate()
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverPoint = null
                invalidate()
            }
        }
        return true
    }

    private fun begin(x: Float, y: Float, pressure: Float, tilt: Float) {
        if (brush.style.isEraser && brush.eraserMode == EraserMode.STROKE) {
            eraseStrokeAt(x, y)
            return
        }
        redoStack.clear()
        val stroke = InkStroke(
            color = if (brush.style.isEraser) Color.BLACK else brush.effectiveColor,
            style = brush.style,
        )
        stroke.points.add(InkPoint(x, y, widthFor(pressure, tilt), tilt))
        strokes.add(stroke)
        active = stroke
        downTime = System.currentTimeMillis()
        lastMoveTime = downTime
        anchorX = x
        anchorY = y
        // Snapshot once, then repaint the whole stroke on every sample. Drawing
        // only the newest segments left visible seams whose edges pulled the
        // line sideways.
        baseInk?.recycle()
        baseInk = ink?.copy(Bitmap.Config.ARGB_8888, false)
        redrawActive()
        invalidate()
        onStateChanged?.invoke()
    }

    private fun move(x: Float, y: Float, pressure: Float, tilt: Float) {
        val stroke = active ?: return
        val last = stroke.points.last()
        // Drop samples that carry no new position: they only cost render time.
        if (hypot(x - last.x, y - last.y) < max(0.7f, last.width * 0.06f)) return
        stroke.points.add(InkPoint(x, y, widthFor(pressure, tilt), tilt))
        val now = System.currentTimeMillis()
        if (hypot(x - anchorX, y - anchorY) > 14 * density) {
            anchorX = x
            anchorY = y
            lastMoveTime = now
        }
        redrawActive()
        invalidate()
    }

    private fun end() {
        val finished = active ?: return
        active = null
        val now = System.currentTimeMillis()
        val held = now - lastMoveTime
        if (held in 480..2200 && !finished.style.isEraser && finished.points.size > 8) {
            val snapped = ShapeSnap.recognise(finished.points)
            if (snapped != null) {
                finished.points.clear()
                finished.points.addAll(snapped)
                finished.snapped = "shape"
            }
        }
        baseInk?.recycle()
        baseInk = null
        rebuildInk()
        onStateChanged?.invoke()
    }

    /** Repaint [active] on top of the pre-stroke snapshot. */
    private fun redrawActive() {
        val stroke = active ?: return
        val canvas = inkCanvas ?: return
        ensureInk()
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        baseInk?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        engine.draw(canvas, stroke, scrollOffset)
    }

    private fun rebuildInk() {
        rebuildPending = false
        ensureInk()
        val canvas = inkCanvas ?: return
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val lo = scrollOffset - 64f
        val hi = scrollOffset + height + 64f
        for (stroke in strokes) {
            val b = stroke.bounds()
            if (b[3] < lo || b[1] > hi) continue
            engine.draw(canvas, stroke, scrollOffset)
        }
        invalidate()
    }

    private fun scheduleRebuild() {
        if (rebuildPending) return
        rebuildPending = true
        handler.postDelayed(rebuildTask, 90)
    }

    private fun eraseStrokeAt(x: Float, y: Float) {
        val radius = widthFor(0.5f, 0f) * 0.9f
        val cy = y + scrollOffset
        val index = strokes.indexOfLast { stroke ->
            !stroke.isEraser && stroke.points.any { p -> hypot(p.x - x, p.y - cy) < radius + p.width * 0.5f }
        }
        if (index >= 0) {
            strokes.removeAt(index)
            redoStack.clear()
            rebuildInk()
            onStateChanged?.invoke()
        }
    }

    // ── scrolling ──────────────────────────────────────────────────────────

    /**
     * Content follows the finger. [dy] is the finger's screen delta; the ink and
     * every stored stroke slide by the same amount.
     */
    fun scrollBy(dy: Float) {
        if (dy == 0f) return
        val next = (scrollOffset + dy).coerceAtLeast(0f)
        val applied = next - scrollOffset
        if (applied == 0f) return
        scrollOffset = next
        val current = ink
        if (current != null) {
            val shifted = spare ?: Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
            spare = ink
            val c = Canvas(shifted)
            c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            c.drawBitmap(current, 0f, applied, null)
            ink = shifted
            inkCanvas = Canvas(shifted)
        }
        invalidate()
        scheduleRebuild()
    }

    /** After a scroll settles, any stroke dragged off the top is discarded. */
    fun pruneOffscreen() {
        val keep = strokes.filter { it.bounds()[3] > scrollOffset - 200f }
        if (keep.size != strokes.size) {
            strokes.clear()
            strokes.addAll(keep)
            onStateChanged?.invoke()
        }
    }

    // ── frame ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ink?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        hoverPoint?.let { p -> drawHover(canvas, p.x, p.y) }
    }

    private fun drawHover(canvas: Canvas, x: Float, y: Float) {
        val w = widthFor(hoverPressure.coerceAtLeast(0.15f), hoverTilt)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = if (brush.style.isEraser) Color.WHITE else brush.color
            alpha = 170
        }
        when (brush.style) {
            PenStyle.MARKER, PenStyle.HIGHLIGHTER -> {
                val half = w / 2f
                canvas.save()
                canvas.rotate(hoverTilt * 45f, x, y)
                canvas.drawRoundRect(x - half, y - half, x + half, y + half, 3f, 3f, paint)
                canvas.restore()
            }
            PenStyle.PENCIL -> {
                paint.style = Paint.Style.FILL
                paint.alpha = 110
                canvas.drawCircle(x, y, max(w * 0.5f, 2f), paint)
            }
            else -> canvas.drawCircle(x, y, max(w * 0.5f, 2f), paint)
        }
        if (brush.style.isEraser) {
            paint.style = Paint.Style.STROKE
            paint.alpha = 90
            canvas.drawCircle(x, y, 2.5f * density, paint)
        }
    }

    // ── history ────────────────────────────────────────────────────────────

    fun undo(): Boolean {
        if (strokes.isEmpty()) return false
        redoStack.add(strokes.removeAt(strokes.size - 1))
        rebuildInk()
        onStateChanged?.invoke()
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        rebuildInk()
        onStateChanged?.invoke()
        return true
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

    /** Composite the ink (already offset-aware) over [background]. */
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

    /** Ink only, transparent, for stitching into a long capture. */
    fun renderInk(): Bitmap =
        ink?.copy(Bitmap.Config.ARGB_8888, false)
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    fun strokeCount(): Int = strokes.size
}

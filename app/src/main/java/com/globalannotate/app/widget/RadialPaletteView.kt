package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.globalannotate.app.model.PenStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Squeeze palette, modelled on the tool palette Apple Pencil Pro pops up at the
 * tip: a compact arc of the five pens plus eraser, undo and colour. Slide the
 * pen to a slot and lift to pick it; lift in the centre to dismiss.
 */
class RadialPaletteView(context: Context) : View(context) {

    interface Listener {
        fun onPalettePick(action: Action)
        fun onPaletteDismiss()
    }

    enum class Action { BALLPOINT, PENCIL, INK_PEN, MARKER, WATER_COLOR, ERASER, UNDO, COLOR }

    var listener: Listener? = null
    private var anchorX = 0f
    private var anchorY = 0f
    private var hovered = -1
    private var visible = false
    private val density = resources.displayMetrics.density

    private val items = listOf(
        Action.BALLPOINT to "圆珠笔",
        Action.PENCIL to "铅笔",
        Action.INK_PEN to "钢笔",
        Action.MARKER to "马克笔",
        Action.WATER_COLOR to "水彩笔",
        Action.ERASER to "橡皮",
        Action.UNDO to "撤销",
        Action.COLOR to "颜色",
    )

    // `arc_pen_picker_*` from Mi Canvas resources (values/dimens.xml), not a
    // guessed approximation: inner 160 / middle 192 / outer 224 / outermost 246dp.
    private val inner get() = 160 * density
    private val middle get() = 192 * density
    private val outer get() = 224 * density
    private val outermost get() = 246 * density
    private val itemRadius get() = (outer - inner) / 2f
    private val ringRadius get() = middle

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12.5f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        alpha = 200
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
    }

    /** Mi Canvas `arc_pen_picker_*`: right-hand fan 82deg -> -42deg on a 192dp ring. */
    private val startAngle = Math.toRadians(82.0)
    private val endAngle = Math.toRadians(-42.0)

    fun showAt(x: Float, y: Float) {
        anchorX = x.coerceIn(outermost, width - outermost)
        anchorY = y.coerceIn(outermost, height - outermost)
        hovered = -1
        visible = true
        invalidate()
    }

    fun hide() {
        visible = false
        hovered = -1
        invalidate()
    }

    fun isOpen(): Boolean = visible

    /** Evenly spaced along the original 82deg -> -42deg arc, on the middle ring. */
    fun positionOf(index: Int): FloatArray {
        val t = if (items.size == 1) 0.5 else index.toDouble() / (items.size - 1)
        val angle = startAngle + (endAngle - startAngle) * t
        return floatArrayOf(
            anchorX + (cos(angle) * middle).toFloat(),
            anchorY + (sin(angle) * middle).toFloat(),
        )
    }
    override fun onDraw(canvas: Canvas) {
        if (!visible) return
        // A soft disc behind the arc so labels stay legible on any page.
        bgPaint.color = Color.argb(150, 20, 20, 20)
        canvas.drawCircle(anchorX, anchorY, ringRadius + itemRadius + 12 * density, bgPaint)

        for ((index, item) in items.withIndex()) {
            val p = positionOf(index)
            canvas.drawCircle(p[0], p[1] + 2 * density, itemRadius + 3 * density, shadowPaint)
            itemPaint.color = if (index == hovered) Color.parseColor("#3482FF") else Color.argb(235, 48, 48, 48)
            canvas.drawCircle(p[0], p[1], itemRadius, itemPaint)
            // Tiny glyph: first char of the label reads clearly at 23dp.
            textPaint.color = Color.WHITE
            val first = item.second.substring(0, 1)
            canvas.drawText(first, p[0], p[1] + textPaint.textSize * 0.36f, textPaint)
        }
        canvas.drawText("松手选择 · 中心取消", anchorX, anchorY + 4 * density, labelPaint)
    }

    /**
     * The pen never "touches" this window (it is a floating overlay above the
     * annotation layer), so selection travels through [onHover] while the user
     * moves the pen inside the arc, and [commit] when the squeeze is released.
     */
    fun onHover(x: Float, y: Float) {
        if (!visible) return
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in items.indices) {
            val p = positionOf(i)
            val d = hypot(x - p[0], y - p[1])
            if (d < itemRadius * 1.5f && d < bestDist) {
                best = i
                bestDist = d
            }
        }
        if (best != hovered) {
            hovered = best
            invalidate()
        }
    }

    /** @return true when something was picked. */
    fun commit(x: Float, y: Float): Boolean {
        onHover(x, y)
        val picked = hovered
        hide()
        if (picked >= 0) {
            listener?.onPalettePick(items[picked].first)
            return true
        }
        if (hypot(x - anchorX, y - anchorY) < ringRadius * 0.55f) {
            listener?.onPaletteDismiss()
            return false
        }
        listener?.onPaletteDismiss()
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Fallback for finger taps / AOSP stylus injection in service mode.
        onHover(event.x, event.y)
        if (event.actionMasked == MotionEvent.ACTION_UP) commit(event.x, event.y)
        return true
    }
}

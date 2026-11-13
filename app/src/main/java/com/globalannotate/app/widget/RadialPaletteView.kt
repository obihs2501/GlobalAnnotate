package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Apple Pencil Pro-style radial tool ring, summoned by squeezing the pen.
 * It appears next to the pen tip; picking a sector executes it and closes the ring.
 */
class RadialPaletteView(context: Context) : View(context) {

    enum class PaletteAction { PEN, ERASER, RED, BLUE, BLACK, UNDO, CLEAR, CLOSE }

    interface Listener {
        fun onPaletteAction(action: PaletteAction)
    }

    var listener: Listener? = null

    private val actions = enumValues<PaletteAction>()
    private val labels = listOf("笔", "橡皮", "红", "蓝", "黑", "撤销", "清空", "关闭")
    private val colors = listOf(
        Color.parseColor("#EFEFEF"), Color.parseColor("#E4E7EC"),
        Color.parseColor("#F4C7C3"), Color.parseColor("#BBD3F2"), Color.parseColor("#C9CDD4"),
        Color.parseColor("#EFEFEF"), Color.parseColor("#EFEFEF"), Color.parseColor("#E4E7EC"),
    )

    private val outerRadius = dp(112).toFloat()
    private val innerRadius = dp(34).toFloat()
    private val center = PointF(0f, 0f)
    private var pressedIndex = -1

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD8A8")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 90, 95, 105)
        strokeWidth = dp(1).toFloat()
    }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 250, 251, 253)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#202124")
        textSize = dp(13).toFloat()
        textAlign = Paint.Align.CENTER
    }

    init {
        visibility = GONE
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun showAt(x: Float, y: Float) {
        center.set(x, y)
        pressedIndex = -1
        visibility = VISIBLE
        bringToFront()
        invalidate()
    }

    fun hide() {
        visibility = GONE
        pressedIndex = -1
        invalidate()
    }

    private fun clampedCenter(): PointF {
        val r = outerRadius
        val cx = if (width > 2 * r) center.x.coerceIn(r, width - r) else width / 2f
        val cy = if (height > 2 * r) center.y.coerceIn(r, height - r) else height / 2f
        return PointF(cx, cy)
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val c = clampedCenter()
        val rect = android.graphics.RectF(
            c.x - outerRadius, c.y - outerRadius, c.x + outerRadius, c.y + outerRadius,
        )
        for (i in labels.indices) {
            val start = -90f + i * 45f - 22.5f
            sectorPaint.color = colors[i]
            canvas.drawArc(rect, start, 45f, true, if (i == pressedIndex) pressedPaint else sectorPaint)
            canvas.drawArc(rect, start, 45f, true, ringPaint)
            val mid = Math.toRadians((start + 22.5f).toDouble())
            val lx = c.x + (outerRadius * 0.70f * cos(mid)).toFloat()
            val ly = c.y + (outerRadius * 0.70f * sin(mid)).toFloat()
            canvas.drawText(labels[i], lx, ly + textPaint.textSize / 3f, textPaint)
        }
        canvas.drawCircle(c.x, c.y, innerRadius, holePaint)
        canvas.drawCircle(c.x, c.y, innerRadius, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = clampedCenter()
        val dx = event.x - c.x
        val dy = event.y - c.y
        val distance = hypot(dx, dy)
        val index = sectorIndex(dx, dy)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = index
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                pressedIndex = index
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val chosen = pressedIndex
                hide()
                if (chosen in actions.indices && distance in innerRadius..outerRadius) {
                    listener?.onPaletteAction(actions[chosen])
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> hide()
        }
        return true
    }

    private fun sectorIndex(dx: Float, dy: Float): Int {
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        var normalized = angle + 90f + 22.5f
        if (normalized < 0f) normalized += 360f
        return (normalized / 45f).toInt() % 8
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Kept so callers can clamp a pen-tip position without knowing the radius. */
        fun minimumRadiusDp(): Int = 112
    }
}

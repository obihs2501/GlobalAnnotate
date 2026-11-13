package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Apple Pencil Pro-style radial tool ring, summoned by squeezing the pen.
 * Styled with HyperOS/Miuix surfaces: translucent Cloud card, #0D84FF accent,
 * colour dots per sector instead of tinted wedges.
 */
class RadialPaletteView(context: Context) : View(context) {

    enum class PaletteAction { PEN, ERASER, RED, BLUE, BLACK, UNDO, CLEAR, CLOSE }

    interface Listener {
        fun onPaletteAction(action: PaletteAction)
    }

    var listener: Listener? = null

    private val actions = enumValues<PaletteAction>()
    private val labels = listOf("钢笔", "橡皮", "红", "蓝", "黑", "撤销", "清空", "关闭")
    private val dots = listOf(
        Color.parseColor("#202124"),
        Color.parseColor("#9AA0A6"),
        Color.parseColor("#D93025"),
        Color.parseColor("#0D84FF"),
        Color.parseColor("#202124"),
        Color.parseColor("#9AA0A6"),
        Color.parseColor("#9AA0A6"),
        Color.parseColor("#9AA0A6"),
    )

    private val outerRadius = dp(112).toFloat()
    private val innerRadius = dp(34).toFloat()
    private val center = PointF(0f, 0f)
    private var pressedIndex = -1

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E5F2FF")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(26, 0, 0, 0)
        strokeWidth = dp(1).toFloat()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F7FBFF")
    }
    private val holeRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#0D84FF")
        strokeWidth = dp(2).toFloat()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3C4043")
        textSize = dp(12).toFloat()
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
        val rect = RectF(c.x - outerRadius, c.y - outerRadius, c.x + outerRadius, c.y + outerRadius)

        for (i in labels.indices) {
            val start = -90f + i * 45f - 22.5f
            sectorPaint.color = Color.parseColor("#F7F9FC")
            canvas.drawArc(rect, start, 45f, true, if (i == pressedIndex) pressedPaint else sectorPaint)
            canvas.drawArc(rect, start, 45f, true, ringPaint)

            val mid = Math.toRadians((start + 22.5f).toDouble())
            val lx = c.x + (outerRadius * 0.70f * cos(mid)).toFloat()
            val ly = c.y + (outerRadius * 0.70f * sin(mid)).toFloat()

            dotPaint.color = dots[i]
            canvas.drawCircle(lx, ly - dp(5), dp(6), dotPaint)
            canvas.drawText(labels[i], lx, ly + dp(13), textPaint)
        }

        canvas.drawCircle(c.x, c.y, innerRadius, holePaint)
        canvas.drawCircle(c.x, c.y, innerRadius, holeRing)
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

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    companion object {
        fun minimumRadiusDp(): Int = 112
    }
}

package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Mi Canvas `ColorPickPanelView`: 13 columns x 9 rows straight out of
 * `creation_colors`, clip-rounded to 16dp, a white 2dp ring on the selected
 * cell and a 0.5dp border around the whole board.
 */
class ColorPickPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onColorPicked: ((Int) -> Unit)? = null

    var selectedColor: Int = Color.parseColor("#E8464A")
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
    }
    private val clipPath = Path()
    private val cellRect = RectF()

    private val colors: IntArray = CreationPalette.COLORS
    private val cols = CreationPalette.COLUMNS
    private val rows = CreationPalette.ROWS

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val padL = paddingLeft.toFloat()
        val padT = paddingTop.toFloat()
        val bw = (width - paddingLeft - paddingRight).toFloat()
        val bh = (height - paddingTop - paddingBottom).toFloat()
        val cw = bw / cols
        val ch = bh / rows

        val radius = 16f * density
        clipPath.reset()
        clipPath.addRoundRect(padL, padT, padL + bw, padT + bh, radius, radius, Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clipPath)

        paint.style = Paint.Style.FILL
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val index = r * cols + c
                if (index >= colors.size) break
                paint.color = colors[index]
                canvas.drawRect(
                    padL + c * cw, padT + r * ch,
                    padL + (c + 1) * cw, padT + (r + 1) * ch, paint,
                )
            }
        }
        canvas.restoreToCount(saved)

        border.color = ContextCompat.getColor(context, R.color.penpicker_color_picker_panel_stroke)
        canvas.drawRoundRect(padL, padT, padL + bw, padT + bh, radius, radius, border)

        val index = colors.indexOf(selectedColor)
        if (index >= 0) {
            val c = index % cols
            val r = index / cols
            cellRect.set(padL + c * cw, padT + r * ch, padL + (c + 1) * cw, padT + (r + 1) * ch)
            val inset = ring.strokeWidth / 2f
            cellRect.inset(inset, inset)
            val edge = c == 0 || c == cols - 1 || r == 0 || r == rows - 1
            if (edge) {
                val clip = Path().apply { addRoundRect(padL, padT, padL + bw, padT + bh, radius, radius, Path.Direction.CW) }
                val ringPath = Path().apply { addRect(cellRect, Path.Direction.CW) }
                ringPath.op(clip, Path.Op.INTERSECT)
                canvas.drawPath(ringPath, ring)
            } else {
                canvas.drawRect(cellRect, ring)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) {
            parent?.requestDisallowInterceptTouchEvent(event.actionMasked == MotionEvent.ACTION_DOWN)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pickAt(event.x, event.y)
            return true
        }
        pickAt(event.x, event.y)
        return true
    }

    private fun pickAt(x: Float, y: Float) {
        val bw = (width - paddingLeft - paddingRight).toFloat()
        val bh = (height - paddingTop - paddingBottom).toFloat()
        val c = ((x - paddingLeft) / (bw / cols)).toInt()
        val r = ((y - paddingTop) / (bh / rows)).toInt()
        if (c !in 0 until cols || r !in 0 until rows) return
        val index = r * cols + c
        if (index >= colors.size) return
        selectedColor = colors[index]
        invalidate()
        onColorPicked?.invoke(selectedColor)
    }

    fun setColor(color: Int) {
        selectedColor = color
    }
}

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

/**
 * Mi Canvas `ColorPickPanelView`: 12 columns x 8 rows, the last column is a
 * white→black ramp, corners rounded by `penpicker_color_picker_panel_view_corner_radius`.
 */
class ColorPickPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onColorPicked: ((Int) -> Unit)? = null

    var selectedColor: Int = Color.RED
        set(value) { field = value; invalidate() }

    private val cols = 12
    private val rows = 8
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val cell = RectF()

    fun colorAt(col: Int, row: Int): Int {
        val t = row / (rows - 1f)
        if (col == cols - 1) {
            val v = (255 * (1f - t)).toInt().coerceIn(0, 255)
            return Color.rgb(v, v, v)
        }
        val hue = col * 360f / (cols - 1)
        return Color.HSVToColor(floatArrayOf(hue, 0.10f + t * 0.86f, 1f - t * 0.52f))
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 16f * density
        val saved = canvas.save()
        canvas.clipPath(
            Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
            },
        )
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                paint.color = colorAt(c, r)
                cell.set(c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)
                canvas.drawRect(cell, paint)
            }
        }
        canvas.restoreToCount(saved)

        // Selected cell gets a hairline ring, like the original picker.
        outer@ for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (colorAt(c, r) != selectedColor) continue
                cell.set(c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)
                ring.color = if (isLight(selectedColor)) Color.BLACK else Color.WHITE
                canvas.drawRect(cell.left + 2f, cell.top + 2f, cell.right - 2f, cell.bottom - 2f, ring)
                break@outer
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val c = (event.x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
            val r = (event.y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            val picked = colorAt(c, r)
            if (picked != selectedColor) {
                selectedColor = picked
                onColorPicked?.invoke(picked)
            }
            return true
        }
        return true
    }

    private fun isLight(color: Int): Boolean =
        0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color) > 150f
}

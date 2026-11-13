package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The 72dp current-colour block from Mi Canvas: 17.24dp rounded shape, a
 * checkerboard behind it so partial opacity is visible, then the colour.
 */
class ColorSwatchBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var color: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checker = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val radius = 17.24f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        val saved = canvas.save()
        canvas.clipPath(path)
        val cell = (rect.height() / 4f).coerceAtLeast(1f)
        var y = rect.top
        var row = 0
        while (y < rect.bottom) {
            var x = rect.left
            var col = 0
            while (x < rect.right) {
                checker.color = if ((row + col) % 2 == 0) {
                    Color.parseColor("#9A9A9A")
                } else {
                    Color.parseColor("#D0D0D0")
                }
                canvas.drawRect(x, y, (x + cell).coerceAtMost(rect.right), (y + cell).coerceAtMost(rect.bottom), checker)
                x += cell
                col++
            }
            y += cell
            row++
        }
        paint.color = color
        canvas.drawRect(rect, paint)
        canvas.restoreToCount(saved)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 0.5f
        paint.color = Color.parseColor("#1A000000")
        canvas.drawPath(path, paint)
    }
}

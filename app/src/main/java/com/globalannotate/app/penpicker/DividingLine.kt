package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/** Mi Canvas `DividingLine`: 8dp of air with a 0.75dp hairline in the middle. */
class DividingLine @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (8 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = ContextCompat.getColor(context, R.color.penpicker_dividing_line_color)
        val inset = 20f * density
        canvas.drawLine(inset, height / 2f, width - inset, height / 2f, paint)
    }
}

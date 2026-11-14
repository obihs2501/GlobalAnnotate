package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/** The 72dp colour block at the bottom of the colour popup. */
class ColorSwatchBlock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var color: Int = Color.parseColor("#A8D4F5")
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            16f * density, 16f * density, paint,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f * density
        paint.color = ContextCompat.getColor(context, R.color.penpicker_color_picker_panel_stroke)
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            16f * density, 16f * density, paint,
        )
        paint.style = Paint.Style.FILL
    }
}

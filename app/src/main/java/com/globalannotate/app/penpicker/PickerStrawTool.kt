package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/** Mi Canvas `PickerStrawTool`: the eyedropper button in the colour title row. */
class PickerStrawTool @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = ContextCompat.getDrawable(context, R.drawable.picker_straw)!!.mutate()

    init {
        isClickable = true
        setOnClickListener { onTap?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f
        bgPaint.color = context.getColor(R.color.penpicker_circle_button_background_color)
        canvas.drawCircle(cx, cy, radius, bgPaint)

        icon.setTint(context.getColor(R.color.penpicker_colorpicker_text_title))
        val size = (radius * 1.45f).toInt()
        icon.setBounds(
            (width - size) / 2, (height - size) / 2,
            (width + size) / 2, (height + size) / 2,
        )
        icon.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = minOf(w, h)
        setMeasuredDimension(side, side)
    }
}

package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/** Mi Canvas `PickerStrawTool`: the 44dp eyedropper button in the title row. */
class PickerStrawTool @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onTap: (() -> Unit)? = null
    var checked: Boolean = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = true
        setOnClickListener { onTap?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f
        bg.color = ContextCompat.getColor(context, R.color.penpicker_circle_button_background_color)
        canvas.drawCircle(cx, cy, radius, bg)

        ink.color = ContextCompat.getColor(context, R.color.penpicker_colorpicker_text_title)
        val s = radius * 0.52f
        // Pipette: a slanted barrel with a small bulb.
        val path = Path().apply {
            moveTo(cx - s * 0.75f, cy + s * 0.75f)
            lineTo(cx + s * 0.25f, cy - s * 0.25f)
        }
        canvas.drawPath(path, ink)
        canvas.drawCircle(cx + s * 0.55f, cy - s * 0.55f, s * 0.42f, ink)
        canvas.drawLine(cx - s * 0.9f, cy + s * 0.9f, cx - s * 0.45f, cy + s * 0.45f, ink)
    }
}

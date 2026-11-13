package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import kotlin.math.min

/** A single round colour swatch with an accent selection ring. */
class SwatchView(context: Context, val color: Int) : View(context) {

    var checked: Boolean = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.34f

        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = ContextCompat.getColor(context, R.color.ga_divider)
        canvas.drawCircle(cx, cy, radius, paint)

        if (checked) {
            paint.strokeWidth = 2.5f * density
            paint.color = ContextCompat.getColor(context, R.color.ga_accent)
            canvas.drawCircle(cx, cy, min(width, height) * 0.46f, paint)
        }
    }
}

/** Horizontal palette + eyedropper button, used inside the colour popup. */
class ColorStrip(
    context: Context,
    private val onColor: (Int) -> Unit,
    private val onPipette: () -> Unit,
) : LinearLayout(context) {

    private val swatches = mutableListOf<SwatchView>()
    private val density = resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val item = dp(28).toInt()
        val gap = dp(2).toInt()
        for (c in BrushConfig.PALETTE) {
            val swatch = SwatchView(context, c)
            swatch.setOnClickListener {
                setColor(c)
                onColor(c)
            }
            swatches.add(swatch)
            addView(swatch, LayoutParams(item, item).apply { marginStart = gap; marginEnd = gap })
        }
        addView(IconButton(context).apply {
            iconRes = R.drawable.ic_pipette
            onTap = { onPipette() }
            layoutParams = LayoutParams(dp(36).toInt(), dp(36).toInt()).apply {
                marginStart = dp(4).toInt()
            }
        })
    }

    fun setColor(color: Int) {
        swatches.forEach { it.checked = it.color == color }
        invalidate()
    }

    private fun dp(value: Int): Float = value * density
}

/** Live preview of the current brush: colour, alpha and width. */
class BrushPreview(context: Context) : View(context) {

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        val c = brush.effectiveColor
        paint.color = c
        val maxWidth = (height * 0.55f).coerceAtLeast(2f)
        paint.strokeWidth = brush.style.toPixels(brush.size, density).coerceIn(2f, maxWidth)
        val pad = width * 0.12f
        canvas.drawLine(pad, height / 2f, width - pad, height / 2f, paint)
    }
}

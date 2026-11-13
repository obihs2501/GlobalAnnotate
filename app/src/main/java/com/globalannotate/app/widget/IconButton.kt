package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min

/** Circular glass icon button with a red accent ring when checked. */
class IconButton(context: Context) : View(context) {

    var iconRes: Int = 0
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }
    var enabledVisual: Boolean = true
        set(value) { field = value; alpha = if (value) 1f else 0.35f; invalidate() }
    var onTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF5B52")
    }
    private var drawable: Drawable? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { if (enabledVisual) onTap?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.44f
        backgroundPaint.color = if (checked) Color.argb(90, 255, 255, 255) else Color.argb(38, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        val icon = context.getDrawable(iconRes)?.mutate()
        drawable = icon
        icon ?: return
        icon.setTint(if (checked) Color.parseColor("#FF5B52") else Color.parseColor("#E8EAED"))
        val size = (min(width, height) - dp(12).toInt()).coerceAtLeast(1)
        val left = (width - size) / 2
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)

        if (checked) {
            ringPaint.strokeWidth = 2.5f * density
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }

    private fun dp(value: Int): Float = value * density
}

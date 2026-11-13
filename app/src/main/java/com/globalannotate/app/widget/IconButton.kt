package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min

/** Square icon button with an accent selection state, used across the toolbar. */
class IconButton(context: Context) : View(context) {

    var iconRes: Int = 0
        set(value) { field = value; invalidate() }
    var selected: Boolean = false
        set(value) { field = value; invalidate() }
    var enabledVisual: Boolean = true
        set(value) { field = value; alpha = if (value) 1f else 0.35f; invalidate() }
    var onTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var drawable: Drawable? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { if (enabledVisual) onTap?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(2)
        bounds.set(pad, pad, width - pad, height - pad)
        if (selected) {
            backgroundPaint.color = Color.parseColor("#E5F2FF")
            backgroundPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(bounds, dp(11), dp(11), backgroundPaint)
        }
        val icon = context.getDrawable(iconRes)?.mutate()
        drawable = icon
        icon ?: return
        val tint = if (selected) Color.parseColor("#0D84FF") else Color.parseColor("#3C4043")
        icon.setTint(tint)
        val size = (min(width, height) - dp(11).toInt()).coerceAtLeast(1)
        val left = (width - size) / 2
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    private fun dp(value: Int): Float = value * density
}

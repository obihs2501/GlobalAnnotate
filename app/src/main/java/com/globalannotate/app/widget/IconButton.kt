package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import kotlin.math.min

/** Circular icon button using the shared theme tokens (no accent ring). */
class IconButton(context: Context) : View(context) {

    var iconRes: Int = 0
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }
    var enabledVisual: Boolean = true
        set(value) { field = value; alpha = if (value) 1f else 0.35f; invalidate() }
    var onTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var drawable: Drawable? = null

    init {
        isClickable = true
        isFocusable = true
        foreground = Ripple.circle(context)
        setOnClickListener { if (enabledVisual) onTap?.invoke() }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.5f
        bgPaint.color = ContextCompat.getColor(context, R.color.ga_circle_bg)
        canvas.drawCircle(cx, cy, radius, bgPaint)

        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return
        drawable = icon
        icon.setTint(
            if (checked) ContextCompat.getColor(context, R.color.ga_accent)
            else ContextCompat.getColor(context, R.color.ga_icon_tint),
        )
        val size = (min(width, height) - dp(14).toInt()).coerceAtLeast(1)
        val left = (width - size) / 2
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    private fun dp(value: Int): Float = value * density
}

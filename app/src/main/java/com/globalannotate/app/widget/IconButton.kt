package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import kotlin.math.min

/**
 * The round 45dp control used for undo / redo / more: a neutral disc with a
 * 30dp glyph, matching the Mi Canvas bar (disc #3E3E3E, glyph #F5F5F5 in the
 * dark theme).
 */
class IconButton @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : View(context, attrs) {

    var iconRes: Int = 0
        set(value) { field = value; invalidate() }
    var checked: Boolean = false
        set(value) { field = value; invalidate() }
    var enabledVisual: Boolean = true
        set(value) { field = value; alpha = if (value) 1f else 0.35f; invalidate() }
    var onTap: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        val radius = min(width, height) / 2f
        bgPaint.color = ContextCompat.getColor(context, R.color.ga_button_bg)
        canvas.drawCircle(cx, cy, radius, bgPaint)

        if (iconRes == 0) return
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return
        drawable = icon
        icon.setTint(
            if (checked) ContextCompat.getColor(context, R.color.ga_accent)
            else ContextCompat.getColor(context, R.color.ga_icon),
        )
        val size = min(width, height) * 0.67f
        val left = ((width - size) / 2f).toInt()
        val top = ((height - size) / 2f).toInt()
        icon.setBounds(left, top, left + size.toInt(), top + size.toInt())
        icon.draw(canvas)
    }
}

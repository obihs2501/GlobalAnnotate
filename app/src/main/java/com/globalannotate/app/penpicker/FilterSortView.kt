package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Replacement for miuix `FilterSortView` with the same geometry: a pill track
 * with a filled pill behind the selected tab. The XML keeps the original
 * `FilterSortView$TabView` children; this view only paints and selects them.
 */
class FilterSortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onTabSelected: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val track = GradientDrawable()
    private var selected = 0

    /** Visible pill behind the selected tab, drawn by the tabs themselves. */
    var selectedIndex: Int
        get() = selected
        set(value) {
            selected = value.coerceIn(0, maxOf(childCount - 1, 0))
            for (i in 0 until childCount) {
                val tab = getChildAt(i) as? TextView ?: continue
                tab.background = if (i == selected) pill(tabFill()) else null
                tab.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (i == selected) R.color.penpicker_colorpicker_text_title else R.color.penpicker_pen_set_text,
                    ),
                )
            }
            onTabSelected?.invoke(selected)
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        track.setColor(trackFill())
        track.cornerRadius = h / 2f
        background = track
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        for (i in 0 until childCount) {
            val tab = getChildAt(i) as? TextView ?: continue
            tab.background = if (i == selected) pill(tabFill()) else null
        }
    }

    override fun onViewAdded(child: android.view.View) {
        super.onViewAdded(child)
        val index = childCount - 1
        child.isClickable = true
        child.setOnClickListener { selectedIndex = index }
        (child as? TextView)?.apply {
            gravity = Gravity.CENTER
            textSize = 14f
            includeFontPadding = false
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (index == selected) R.color.penpicker_colorpicker_text_title else R.color.penpicker_pen_set_text,
                ),
            )
            background = if (index == selected) pill(tabFill()) else null
        }
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(color)
    }

    private fun trackFill(): Int =
        if (isNight()) Color.parseColor("#1F1F1F") else Color.parseColor("#F0F0F0")

    private fun tabFill(): Int =
        if (isNight()) Color.parseColor("#3A3A3A") else Color.WHITE

    private fun isNight(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
    }

    /** `FilterSortView$TabView` equivalent: a centred pill label. */
    class TabView @JvmOverloads constructor(
        context: Context,
        attrs: android.util.AttributeSet? = null,
    ) : TextView(context, attrs)

    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}

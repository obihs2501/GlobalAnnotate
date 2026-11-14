package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Miuix `FilterSortView`: a pill container with a raised pill on the selected
 * tab. Geometry measured from Mi Canvas: 37.8dp tall, 4dp inner padding,
 * 32dp tab height, 16.7sp labels.
 */
class FilterSortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onTabSelected: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val container = GradientDrawable()
    private var selected = 0

    var selectedIndex: Int
        get() = selected
        set(value) {
            if (value == selected && childCount > 0 && getChildAt(selected)?.background != null) return
            selected = value.coerceIn(0, maxOf(childCount - 1, 0))
            restyle()
            onTabSelected?.invoke(selected)
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        container.setColor(if (isNight()) Color.parseColor("#1C1C1C") else Color.parseColor("#EEEEEE"))
        container.cornerRadius = h / 2f
        background = container
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        val index = childCount - 1
        child.isClickable = true
        child.setOnClickListener { selectedIndex = index }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        restyle()
    }

    private fun restyle() {
        val night = isNight()
        val selectedFill = if (night) Color.parseColor("#3C3C3C") else Color.parseColor("#E2E2E2")
        val textOn = if (night) Color.WHITE else Color.BLACK
        val textOff = if (night) Color.parseColor("#B3FFFFFF") else Color.parseColor("#99000000")
        for (i in 0 until childCount) {
            val tab = getChildAt(i) as? TextView ?: continue
            if (tab.textSize != 16.7f) {
                tab.textSize = 16.7f
                tab.includeFontPadding = false
                tab.gravity = Gravity.CENTER
                tab.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            tab.background = if (i == selected) {
                GradientDrawable().apply {
                    setColor(selectedFill)
                    cornerRadius = dp(16).toFloat()
                }
            } else null
            tab.setTextColor(if (i == selected) textOn else textOff)
        }
    }

    private fun isNight(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    /** `FilterSortView$TabView` stand-in. */
    class TabView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : TextView(context, attrs)
}

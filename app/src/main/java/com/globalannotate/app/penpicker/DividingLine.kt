package com.globalannotate.app.penpicker

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/** Mi Canvas `DividingLine`: a hairline separator inside popups. */
class DividingLine @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.penpicker_dividing_line_color))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val hairline = (resources.displayMetrics.density * 0.5f).toInt().coerceAtLeast(1)
        setMeasuredDimension(width, hairline)
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * The ⋮ popup. Exit is intentionally NOT here — it lives on its own back button
 * so a mis-tap can never quit instead of exporting.
 */
class MoreMenu(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onGestureSettings()
        fun onAutoMinimize(enabled: Boolean)
        fun onFingerDraw(enabled: Boolean)
        fun onCycleMode()
        fun onSavePng()
        fun onSavePdf()
        fun onClear()
    }

    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_menu)
        elevation = dp(8)
        setPadding(dp(8).toInt(), dp(10).toInt(), dp(8).toInt(), dp(10).toInt())
        layoutParams = LayoutParams(dp(288).toInt(), WRAP_CONTENT)

        row("笔身手势", R.drawable.ic_pressure) { listener.onGestureSettings() }
        separator()
        checkRow("自动最小化", true) { listener.onAutoMinimize(it) }
        checkRow("用手指涂画", false) { listener.onFingerDraw(it) }
        separator()
        row("切换批注模式", R.drawable.ic_layers) { listener.onCycleMode() }
        row("导出 PNG", R.drawable.ic_image) { listener.onSavePng() }
        row("导出 PDF", R.drawable.ic_pdf) { listener.onSavePdf() }
        row("清空画布", R.drawable.ic_trash) { listener.onClear() }
    }

    private fun row(text: String, icon: Int, click: () -> Unit) {
        val r = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10).toInt(), dp(10).toInt(), dp(10).toInt(), dp(10).toInt())
            isClickable = true
            setOnClickListener { click() }
        }
        r.addView(IconButton(context).apply {
            iconRes = icon
            layoutParams = LinearLayout.LayoutParams(dp(32).toInt(), dp(32).toInt())
        })
        r.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(12).toInt()
            }
        })
        addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun checkRow(text: String, initial: Boolean, change: (Boolean) -> Unit) {
        var enabled = initial
        val r = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14).toInt(), dp(12).toInt(), dp(14).toInt(), dp(12).toInt())
            isClickable = true
        }
        r.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_accent))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val check = TextView(context).apply {
            this.text = if (enabled) "✓" else ""
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.ga_accent))
        }
        r.addView(check)
        r.setOnClickListener {
            enabled = !enabled
            check.text = if (enabled) "✓" else ""
            change(enabled)
        }
        addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun separator() {
        addView(
            View(context).apply { setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider)) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1).apply {
                topMargin = dp(6).toInt()
                bottomMargin = dp(6).toInt()
            },
        )
    }

    private fun dp(value: Int): Float = value * density
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R

/** The ⋮ popup menu. Exit is deliberately NOT here — it stays on the main bar. */
class MoreMenu(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onInsertText()
        fun onInsertShape()
        fun onAutoMinimize(enabled: Boolean)
        fun onFingerDraw(enabled: Boolean)
        fun onCycleMode()
        fun onSavePng()
        fun onSavePdf()
        fun onClear()
    }

    private val density = resources.displayMetrics.density
    private var autoMinimize = true
    private var fingerDraw = false

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(8).toInt(), dp(8).toInt(), dp(8).toInt(), dp(8).toInt())

        row("插入文本", R.drawable.ic_pdf) { listener.onInsertText() }
        row("插入形状", R.drawable.ic_layers) { listener.onInsertShape() }
        separator()
        checkRow("自动最小化", true) { enabled ->
            autoMinimize = enabled
            listener.onAutoMinimize(enabled)
        }
        checkRow("用手指涂画", false) { enabled ->
            fingerDraw = enabled
            listener.onFingerDraw(enabled)
        }
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
            setPadding(dp(14).toInt(), dp(10).toInt(), dp(14).toInt(), dp(10).toInt())
            isClickable = true
            setOnClickListener { click() }
        }
        r.addView(IconButton(context).apply {
            iconRes = icon
            layoutParams = LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt())
        })
        r.addView(TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
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
            setTextColor(Color.parseColor("#4A9BFF"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val check = TextView(context).apply {
            this.text = if (enabled) "✓" else ""
            textSize = 16f
            setTextColor(Color.parseColor("#4A9BFF"))
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
        val v = View(context).apply {
            setBackgroundColor(Color.argb(40, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
        }
        addView(v, LinearLayout.LayoutParams(MATCH_PARENT, 1).apply {
            topMargin = dp(6).toInt(); bottomMargin = dp(6).toInt()
        })
    }

    private fun dp(value: Int): Float = value * density
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle

/**
 * Eraser popup, mirroring Mi Canvas' `EraserPopupWindow`:
 * two tabs (像素橡皮 / 笔画橡皮) and a single size slider (4..32).
 * No sensitivity and no opacity — the eraser does not expose them.
 */
class EraserPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onMode(mode: EraserMode)
        fun onSize(size: Float)
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val pixelTab: TextView
    private val strokeTab: TextView
    private val sizeSlider: TrackSlider
    private val sizeValue: TextView

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; sync(value) }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(18).toInt(), dp(12).toInt(), dp(18).toInt(), dp(16).toInt())

        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(context).apply {
            text = "橡皮"
            textSize = 14f
            setTextColor(Color.parseColor("#E8EAED"))
            layoutParams = LinearLayout.LayoutParams(0, dp(34).toInt(), 1f)
        })
        head.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_close
            onTap = { listener.onClose() }
            layoutParams = LinearLayout.LayoutParams(dp(32).toInt(), dp(32).toInt())
        })
        addView(head)

        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = dp(16)
            }
            setPadding(dp(4).toInt(), dp(4).toInt(), dp(4).toInt(), dp(4).toInt())
        }
        pixelTab = tab("像素橡皮") { listener.onMode(EraserMode.PIXEL) }
        strokeTab = tab("笔画橡皮") { listener.onMode(EraserMode.STROKE) }
        tabRow.addView(pixelTab)
        tabRow.addView(strokeTab)
        addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(38).toInt()).apply {
            topMargin = dp(10).toInt()
        })

        val sizeHead = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sizeHead.addView(TextView(context).apply {
            text = "大小"
            textSize = 13f
            setTextColor(Color.parseColor("#E8EAED"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        sizeValue = TextView(context).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#C9CDD4"))
        }
        sizeHead.addView(sizeValue)
        addView(sizeHead, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(12).toInt()
        })

        sizeSlider = TrackSlider(context).apply {
            onProgress = { p ->
                val style = PenStyle.ERASER
                val size = (style.minSize + p * (style.maxSize - style.minSize)).toInt()
                sizeValue.text = size.toString()
                listener.onSize(size.toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(28).toInt())
        }
        addView(sizeSlider)
    }

    private fun sync(brush: BrushConfig) {
        val style = PenStyle.ERASER
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        sizeSlider.progress = ((brush.eraserSize - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
        sizeValue.text = brush.eraserSize.toInt().toString()
        selectTab(brush.eraserMode)
    }

    private fun selectTab(mode: EraserMode) {
        pixelTab.setTabSelected(mode == EraserMode.PIXEL)
        strokeTab.setTabSelected(mode == EraserMode.STROKE)
    }

    private fun tab(text: String, click: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnClickListener { click() }
            setTabSelected(false)
        }

    private fun TextView.setTabSelected(selected: Boolean) {
        background = GradientDrawable().apply {
            setColor(if (selected) Color.parseColor("#4A4A4E") else Color.TRANSPARENT)
            cornerRadius = dp(14)
        }
    }

    private fun dp(value: Int): Float = value * density
}

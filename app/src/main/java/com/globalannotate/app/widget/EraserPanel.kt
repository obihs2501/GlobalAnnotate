package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.EraserMode
import com.globalannotate.app.model.PenStyle

/**
 * Eraser popup, structure copied from Mi Canvas
 * `penpicker_popup_layout_eraser_set.xml`: FilterSortView tabs on top, then the
 * content area, 20dp horizontal padding. Only the two tabs (像素 / 笔画) and a
 * single size slider (4..32) — no sensitivity, no opacity.
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
    private lateinit var hint: TextView

    var brush: BrushConfig = BrushConfig()
        set(value) { field = value; sync(value) }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(20).toInt(), dp(20).toInt(), dp(20).toInt(), dp(16).toInt())
        layoutParams = LayoutParams(dp(288).toInt(), WRAP_CONTENT)

        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.ga_tab_bg))
                cornerRadius = dp(20)
            }
            setPadding(dp(3).toInt(), dp(3).toInt(), dp(3).toInt(), dp(3).toInt())
        }
        pixelTab = tab("像素橡皮") { listener.onMode(EraserMode.PIXEL) }
        strokeTab = tab("笔画橡皮") { listener.onMode(EraserMode.STROKE) }
        tabRow.addView(pixelTab)
        tabRow.addView(strokeTab)
        addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40).toInt()))

        val sizeHead = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sizeHead.addView(bodyText("大小").apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        sizeValue = bodyText("")
        sizeHead.addView(sizeValue)
        addView(sizeHead, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(18).toInt()
        })

        sizeSlider = TrackSlider(context).apply {
            onProgress = { p ->
                val style = PenStyle.ERASER
                val size = (style.minSize + p * (style.maxSize - style.minSize)).toInt()
                sizeValue.text = size.toString()
                listener.onSize(size.toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(26).toInt())
        }
        addView(sizeSlider, LinearLayout.LayoutParams(MATCH_PARENT, dp(28).toInt()).apply {
            topMargin = dp(8).toInt()
        })

        // Stroke eraser has no size, exactly like Mi Canvas: a centred hint
        // replaces the slider (pk_eraser_stroke_hint, 31dp / 25dp margins).
        hint = TextView(context).apply {
            text = "笔画橡皮无需调整粗细"
            textSize = 14f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_hint))
            visibility = GONE
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(31).toInt()
                bottomMargin = dp(25).toInt()
            }
        }
        addView(hint)
    }

    private fun sync(brush: BrushConfig) {
        val style = PenStyle.ERASER
        val span = (style.maxSize - style.minSize).coerceAtLeast(1)
        sizeSlider.progress = ((brush.eraserSize - style.minSize) / span.toFloat()).coerceIn(0f, 1f)
        sizeValue.text = brush.eraserSize.toInt().toString()
        selectTab(brush.eraserMode)
        val stroke = brush.eraserMode == EraserMode.STROKE
        hint.visibility = if (stroke) VISIBLE else GONE
        sizeSlider.visibility = if (stroke) GONE else VISIBLE
    }

    private fun selectTab(mode: EraserMode) {
        pixelTab.setTabSelected(mode == EraserMode.PIXEL)
        strokeTab.setTabSelected(mode == EraserMode.STROKE)
        if (::hint.isInitialized) {
            val stroke = mode == EraserMode.STROKE
            hint.visibility = if (stroke) VISIBLE else GONE
            sizeSlider.visibility = if (stroke) GONE else VISIBLE
        }
    }

    private fun tab(text: String, click: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnClickListener { click() }
            setTabSelected(false)
        }

    private fun TextView.setTabSelected(selected: Boolean) {
        background = GradientDrawable().apply {
            setColor(
                if (selected) ContextCompat.getColor(context, R.color.ga_tab_selected)
                else Color.TRANSPARENT,
            )
            cornerRadius = dp(17)
        }
        setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.ga_text_title else R.color.ga_text_primary,
            ),
        )
    }

    private fun bodyText(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
        includeFontPadding = false
    }

    private fun dp(value: Int): Float = value * density
}

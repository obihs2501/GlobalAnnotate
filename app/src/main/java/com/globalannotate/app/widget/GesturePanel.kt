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
import com.globalannotate.app.GestureAction
import com.globalannotate.app.R

/**
 * Pen-barrel gesture mapping (Focus Pen Pro has no buttons, so its squeeze /
 * double tap / slide gestures are the equivalent of Apple Pencil's squeeze and
 * double-tap). Every row cycles through the available actions on tap.
 */
class GesturePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onAction(gesture: String, action: GestureAction)
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val valueViews = mutableMapOf<String, TextView>()
    private val current = mutableMapOf<String, GestureAction>()

    private val rows = listOf(
        "SQUEEZE" to "轻捏笔身",
        "DOUBLE_TAP" to "双击笔身",
        "SLIDE_UP" to "笔身上滑",
        "SLIDE_DOWN" to "笔身下滑",
    )

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(20).toInt(), dp(18).toInt(), dp(20).toInt(), dp(16).toInt())
        layoutParams = LayoutParams(dp(288).toInt(), WRAP_CONTENT)

        addView(TextView(context).apply {
            text = "笔身手势"
            textSize = 20f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_title))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(6).toInt()
        })

        for ((key, label) in rows) {
            addRow(key, label)
        }
    }

    fun setActions(map: Map<String, GestureAction>) {
        map.forEach { (k, v) -> current[k] = v }
        current.forEach { (k, v) -> valueViews[k]?.text = labelOf(v) }
    }

    private fun addRow(key: String, label: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(0, dp(12).toInt(), 0, dp(12).toInt())
        }
        row.addView(TextView(context).apply {
            text = label
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val value = TextView(context).apply {
            text = labelOf(current[key] ?: GestureAction.NONE)
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_accent))
        }
        row.addView(value)
        valueViews[key] = value
        row.setOnClickListener {
            val next = nextAction(current[key] ?: GestureAction.NONE)
            current[key] = next
            value.text = labelOf(next)
            listener.onAction(key, next)
        }
        addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        addView(View_divider(context), LinearLayout.LayoutParams(MATCH_PARENT, 1))
    }

    private fun nextAction(action: GestureAction): GestureAction {
        val order = GestureAction.values()
        return order[(order.indexOf(action) + 1) % order.size]
    }

    private fun labelOf(action: GestureAction): String = when (action) {
        GestureAction.NONE -> "无"
        GestureAction.TOGGLE_OVERLAY -> "显示/收起批注"
        GestureAction.TOGGLE_ERASER -> "笔 ↔ 橡皮"
        GestureAction.UNDO -> "撤销"
        GestureAction.REDO -> "重做"
        GestureAction.CLEAR -> "清空"
        GestureAction.INCREASE_WIDTH -> "加粗"
        GestureAction.DECREASE_WIDTH -> "变细"
        GestureAction.CYCLE_TOOL -> "切换笔刷"
        GestureAction.TOGGLE_TOOLBAR -> "收起工具栏"
        GestureAction.OPEN_COLOR -> "打开调色板"
        GestureAction.CYCLE_COLOR -> "切换颜色"
    }

    private fun View_divider(context: Context): android.view.View =
        android.view.View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider))
        }

    private fun dp(value: Int): Float = value * density
}

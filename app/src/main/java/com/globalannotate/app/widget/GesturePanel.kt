package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.Gesture
import com.globalannotate.app.GestureAction
import com.globalannotate.app.R

/**
 * Pen-gesture mapping. Mi Canvas has no such screen, so this follows Miuix:
 * a 288dp card with a 36dp radius, 56dp rows, 14sp labels and a 32dp-radius
 * press state. Tapping a row cycles to the next action.
 */
class GesturePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onAction(gesture: String, action: GestureAction)
    }

    private val density = resources.displayMetrics.density
    private val rows = LinkedHashMap<Gesture, Row>()

    private data class Row(val title: TextView, val action: TextView, val view: View)

    var actions: Map<String, GestureAction> = emptyMap()
        set(value) {
            field = value
            refresh()
        }

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.penpicker_background_color))
            cornerRadius = 36f * density
        }
        setPadding(dp(20), dp(20), dp(20), dp(20))

        addView(TextView(context).apply {
            text = "笔身手势"
            textSize = 20f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.penpicker_colorpicker_text_title))
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })

        for (gesture in Gesture.entries) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                background = ripple()
                isClickable = true
                setOnClickListener { cycle(gesture) }
            }
            val title = TextView(context).apply {
                text = gestureLabel(gesture)
                textSize = 16f
                includeFontPadding = false
                setTextColor(ContextCompat.getColor(context, R.color.penpicker_pen_set_text))
            }
            val action = TextView(context).apply {
                textSize = 14f
                includeFontPadding = false
                gravity = Gravity.END
                setTextColor(Color.parseColor("#3482FF"))
            }
            row.addView(title, LinearLayout.LayoutParams(0, MATCH, 1f))
            row.addView(action, LinearLayout.LayoutParams(WRAP, MATCH).apply { })
            addView(row, LinearLayout.LayoutParams(MATCH, dp(56)))
            rows[gesture] = Row(title, action, row)
        }
        refresh()
    }

    private fun cycle(gesture: Gesture) {
        val current = actions[gesture.name] ?: defaultFor(gesture)
        val order = GestureAction.entries
        val next = order[(order.indexOf(current) + 1) % order.size]
        actions = actions + (gesture.name to next)
        listener.onAction(gesture.name, next)
        refresh()
    }

    private fun refresh() {
        for ((gesture, row) in rows) {
            val action = actions[gesture.name] ?: defaultFor(gesture)
            row.action.text = actionLabel(action)
        }
    }

    private fun gestureLabel(gesture: Gesture): String = when (gesture) {
        Gesture.SQUEEZE -> "轻捏笔身"
        Gesture.DOUBLE_TAP -> "双击笔身"
        Gesture.SLIDE_UP -> "笔身上滑"
        Gesture.SLIDE_DOWN -> "笔身下滑"
    }

    private fun defaultFor(gesture: Gesture): GestureAction = when (gesture) {
        Gesture.SQUEEZE -> GestureAction.OPEN_PALETTE
        Gesture.DOUBLE_TAP -> GestureAction.TOGGLE_ERASER
        Gesture.SLIDE_UP -> GestureAction.INCREASE_WIDTH
        Gesture.SLIDE_DOWN -> GestureAction.DECREASE_WIDTH
    }

    private fun actionLabel(action: GestureAction): String = when (action) {
        GestureAction.NONE -> "无"
        GestureAction.TOGGLE_OVERLAY -> "显示/隐藏批注"
        GestureAction.TOGGLE_ERASER -> "笔/橡皮"
        GestureAction.CYCLE_TOOL -> "切换笔刷"
        GestureAction.UNDO -> "撤销"
        GestureAction.REDO -> "重做"
        GestureAction.CLEAR -> "清空"
        GestureAction.INCREASE_WIDTH -> "加粗"
        GestureAction.DECREASE_WIDTH -> "变细"
        GestureAction.TOGGLE_TOOLBAR -> "收起/展开工具栏"
        GestureAction.OPEN_COLOR -> "打开调色盘"
        GestureAction.CYCLE_COLOR -> "换颜色"
        GestureAction.OPEN_PALETTE -> "笔尖工具盘"
    }

    private fun ripple() = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = 32f * density
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.GestureAction
import com.globalannotate.app.R

/**
 * Pen-barrel gesture mapping. Mi Canvas has no equivalent screen, so this
 * follows the Miuix specification instead of the pen-picker popups:
 *  - dialog surface: `DialogDefaults.CornerRadius` = 32dp, 320dp wide
 *  - 24dp content padding, 56dp rows, 0.5dp `dividerLine` hairlines
 *  - title / row / value use the Miuix title + body roles (20 / 16 / 14sp)
 *  - muted secondary text uses the summary role (60% onSurface)
 *  - ripple feedback, and the action list expands inline with a check mark
 */
class GesturePanel(context: Context, private val listener: Listener) : android.widget.FrameLayout(context) {

    interface Listener {
        fun onAction(gesture: String, action: GestureAction)
        fun onExpandedChanged()
        fun onClose()
    }

    /** Only the actions that make sense from the pen barrel. */
    private val shownActions = listOf(
        GestureAction.NONE,
        GestureAction.TOGGLE_TOOLBAR,
        GestureAction.TOGGLE_OVERLAY,
        GestureAction.TOGGLE_ERASER,
        GestureAction.CYCLE_TOOL,
        GestureAction.OPEN_COLOR,
        GestureAction.CYCLE_COLOR,
        GestureAction.UNDO,
        GestureAction.REDO,
        GestureAction.CLEAR,
        GestureAction.INCREASE_WIDTH,
        GestureAction.DECREASE_WIDTH,
    )

    private val gestures = listOf(
        "SQUEEZE" to "轻捏笔身",
        "DOUBLE_TAP" to "双击笔身",
        "SLIDE_UP" to "笔身上滑",
        "SLIDE_DOWN" to "笔身下滑",
    )

    private val density = resources.displayMetrics.density
    private val current = mutableMapOf<String, GestureAction>()
    private val valueViews = mutableMapOf<String, TextView>()
    private val listViews = mutableMapOf<String, LinearLayout>()
    private val expanded = mutableSetOf<String>()

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(32).toFloat()
            setColor(ContextCompat.getColor(context, R.color.ga_panel_bg))
        }
        elevation = dp(8).toFloat()
        setPadding(dp(24), dp(24), dp(24), dp(16))
        layoutParams = LayoutParams(dp(320), WRAP_CONTENT)

        // The list can outgrow the popup, so it scrolls like any Miuix dialog.
        addView(
            android.widget.ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                addView(content, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            },
            LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )

        content.addView(TextView(context).apply {
            text = "笔身手势"
            textSize = 20f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_title))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        for ((key, label) in gestures) addGestureRow(key, label)
    }

    fun setActions(map: Map<String, GestureAction>) {
        current.putAll(map)
        current.forEach { (k, v) ->
            valueViews[k]?.text = labelOf(v)
            refreshChecks(k)
        }
    }

    private fun addGestureRow(key: String, label: String) {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(0, dp(16), 0, dp(16))
            foreground = Ripple.rounded(context, dp(16).toFloat())
        }
        header.addView(TextView(context).apply {
            text = label
            textSize = 16f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        val value = TextView(context).apply {
            text = labelOf(current[key] ?: GestureAction.NONE)
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_hint))
        }
        header.addView(value)
        valueViews[key] = value

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        for (action in shownActions) list.addView(actionRow(key, action))
        listViews[key] = list

        header.setOnClickListener {
            val show = !expanded.contains(key)
            if (show) expanded.add(key) else expanded.remove(key)
            list.visibility = if (show) View.VISIBLE else View.GONE
            listener.onExpandedChanged()
        }

        content.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(hairline(), LinearLayout.LayoutParams(MATCH_PARENT, 1))
    }

    private fun actionRow(gesture: String, action: GestureAction): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(12), dp(12), dp(4), dp(12))
            foreground = Ripple.rounded(context, dp(12).toFloat())
        }
        val check = TextView(context).apply {
            text = ""
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.ga_accent))
            tag = action
        }
        row.addView(TextView(context).apply {
            text = labelOf(action)
            textSize = 16f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(check)
        row.setOnClickListener {
            current[gesture] = action
            valueViews[gesture]?.text = labelOf(action)
            refreshChecks(gesture)
            listener.onAction(gesture, action)
        }
        row.tag = check
        return row
    }

    private fun refreshChecks(gesture: String) {
        val list = listViews[gesture] ?: return
        val selected = current[gesture] ?: GestureAction.NONE
        for (i in 0 until list.childCount) {
            val row = list.getChildAt(i) as? LinearLayout ?: continue
            val check = row.tag as? TextView ?: continue
            check.text = if (check.tag == selected) "✓" else ""
        }
    }

    private fun hairline(): View = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider))
    }

    private fun labelOf(action: GestureAction): String = when (action) {
        GestureAction.NONE -> "无"
        GestureAction.TOGGLE_OVERLAY -> "显示 / 收起批注"
        GestureAction.TOGGLE_ERASER -> "笔 ↔ 橡皮"
        GestureAction.CYCLE_TOOL -> "切换笔刷"
        GestureAction.UNDO -> "撤销"
        GestureAction.REDO -> "重做"
        GestureAction.CLEAR -> "清空"
        GestureAction.INCREASE_WIDTH -> "加粗"
        GestureAction.DECREASE_WIDTH -> "变细"
        GestureAction.TOGGLE_TOOLBAR -> "收起工具栏"
        GestureAction.OPEN_COLOR -> "打开调色板"
        GestureAction.CYCLE_COLOR -> "切换颜色"
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}

package com.globalannotate.app.widget

import android.content.Context
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
 * Pen-barrel gesture mapping. Tapping a gesture expands the full action list
 * inline (with a check mark on the current one) so an action can be chosen
 * directly instead of cycling through them one tap at a time.
 */
class GesturePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onAction(gesture: String, action: GestureAction)
        fun onExpandedChanged()
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val current = mutableMapOf<String, GestureAction>()
    private val valueViews = mutableMapOf<String, TextView>()
    private val listViews = mutableMapOf<String, LinearLayout>()
    private val expanded = mutableSetOf<String>()

    private val gestures = listOf(
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
            bottomMargin = dp(4).toInt()
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

    fun requiredHeightDp(): Int = 18 + 28 + gestures.size * 46 + expanded.size * (GestureAction.entries.size * 36) + 16

    private fun addGestureRow(key: String, label: String) {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(0, dp(12).toInt(), 0, dp(12).toInt())
        }
        header.addView(TextView(context).apply {
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
        header.addView(value)
        valueViews[key] = value

        val list = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
        }
        for (action in GestureAction.entries) {
            list.addView(actionRow(key, action))
        }
        listViews[key] = list

        header.setOnClickListener {
            val show = !expanded.contains(key)
            if (show) expanded.add(key) else expanded.remove(key)
            list.visibility = if (show) View.VISIBLE else View.GONE
            // the popup must grow/shrink to fit the expanded list
            listener.onExpandedChanged()
        }

        addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(list, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider))
        }, LinearLayout.LayoutParams(MATCH_PARENT, 1))
    }

    private fun actionRow(gesture: String, action: GestureAction): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setPadding(dp(12).toInt(), dp(8).toInt(), dp(4).toInt(), dp(8).toInt())
        }
        val name = TextView(context).apply {
            text = labelOf(action)
            textSize = 14f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val check = TextView(context).apply {
            text = ""
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.ga_accent))
            tag = action
        }
        row.addView(name)
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

    private fun labelOf(action: GestureAction): String = when (action) {
        GestureAction.NONE -> "无"
        GestureAction.TOGGLE_OVERLAY -> "显示/收起批注"
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

    private fun dp(value: Int): Float = value * density
}

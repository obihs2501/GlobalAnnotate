package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.Gesture
import com.globalannotate.app.GestureAction
import com.globalannotate.app.R

/**
 * Pen-gesture mapping, rebuilt as a Miuix preference screen in the View layer
 * (no Compose dependency): a rounded card, a grouped section with a caption,
 * 56dp rows, and an inline dropdown of radio options whose selected row carries
 * a check mark. Tapping no longer cycles blindly through every action.
 */
class GesturePanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onAction(gesture: String, action: GestureAction)
    }

    private val density = resources.displayMetrics.density
    private val accent = Color.parseColor("#3482FF")
    private val titleColor = ContextCompat.getColor(context, R.color.penpicker_colorpicker_text_title)
    private val bodyColor = ContextCompat.getColor(context, R.color.penpicker_pen_set_text)
    private val subColor = if (night()) Color.parseColor("#B3FFFFFF").toInt() else Color.parseColor("#99000000").toInt()
    private val optionBg = if (night()) Color.argb(26, 255, 255, 255) else Color.argb(8, 0, 0, 0)
    private var scroller: ScrollView? = null
    private val rows = LinkedHashMap<Gesture, Row>()
    private var expanded: Gesture? = null

    private data class Row(val value: TextView, val chevron: TextView, val options: LinearLayout)

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
            setTextColor(titleColor)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })

        addView(TextView(context).apply {
            text = "按住或滑动笔身时执行的操作"
            textSize = 13f
            includeFontPadding = false
            setTextColor(subColor)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })

        val scroller = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        this.scroller = scroller
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        scroller.addView(list, LayoutParams(MATCH, WRAP))
        addView(scroller, LinearLayout.LayoutParams(MATCH, 0, 1f))

        for (gesture in Gesture.entries) {
            val header = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                background = pressBackground()
                isClickable = true
                setOnClickListener { toggle(gesture) }
            }
            header.addView(TextView(context).apply {
                text = gestureLabel(gesture)
                textSize = 16f
                includeFontPadding = false
                setTextColor(bodyColor)
            }, LinearLayout.LayoutParams(0, MATCH, 1f))
            val value = TextView(context).apply {
                textSize = 14f
                includeFontPadding = false
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setTextColor(subColor)
            }
            header.addView(value, LinearLayout.LayoutParams(WRAP, MATCH))
            val chevron = TextView(context).apply {
                text = "⌄"
                textSize = 16f
                includeFontPadding = false
                setTextColor(subColor)
                setPadding(dp(12), 0, 0, 0)
            }
            header.addView(chevron, LinearLayout.LayoutParams(WRAP, WRAP))
            list.addView(header, LinearLayout.LayoutParams(MATCH, dp(56)))

            val options = LinearLayout(context).apply {
                orientation = VERTICAL
                visibility = View.GONE
                background = GradientDrawable().apply {
                    setColor(optionBg)
                    cornerRadius = 20f * density
                }
                setPadding(0, dp(4), 0, dp(4))
                // Miuix shows the choices as an inline expanding dropdown.
            }
            for (action in GestureAction.entries) {
                val option = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(16), 0)
                    background = pressBackground()
                    isClickable = true
                    setOnClickListener { choose(gesture, action) }
                }
                // Miuix puts the check mark in the leading position, so the
                // selected row is obvious without scrolling to the row's tail.
                val mark = TextView(context).apply {
                    text = "✓"
                    textSize = 16f
                    includeFontPadding = false
                    setTextColor(accent)
                    tag = action
                    setPadding(0, 0, dp(12), 0)
                }
                option.addView(mark, LinearLayout.LayoutParams(WRAP, WRAP))
                option.addView(TextView(context).apply {
                    text = actionLabel(action)
                    textSize = 14f
                    includeFontPadding = false
                    setTextColor(bodyColor)
                }, LinearLayout.LayoutParams(0, MATCH, 1f))
                options.addView(option, LinearLayout.LayoutParams(MATCH, dp(44)))
            }
            list.addView(options, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(2)
                bottomMargin = dp(2)
            })
            rows[gesture] = Row(value, chevron, options)
        }
        refresh()
    }

    private fun toggle(gesture: Gesture) {
        val previous = expanded
        if (previous != null) rows[previous]?.options?.visibility = View.GONE
        expanded = if (previous == gesture) null else gesture
        if (expanded == gesture) rows[gesture]?.options?.visibility = View.VISIBLE
        refresh()
        // Let the host re-measure: the dropdown changes the panel's height.
        (parent as? View)?.requestLayout()
        requestLayout()
        // Bring the freshly opened list into view; the panel is a fixed-height
        // card and the group sat below the fold.
        if (expanded == gesture) {
            val options = rows[gesture]?.options ?: return
            scroller?.post {
                val target = (options.parent as? View)?.let { list ->
                    var y = 0
                    var child: View? = options
                    while (child != null && child !== list) {
                        y += child.top
                        child = child.parent as? View
                    }
                    y
                } ?: 0
                scroller?.smoothScrollTo(0, (target - dp(8)).coerceAtLeast(0))
            }
        }
    }

    private fun choose(gesture: Gesture, action: GestureAction) {
        actions = actions + (gesture.name to action)
        listener.onAction(gesture.name, action)
        expanded = null
        rows[gesture]?.options?.visibility = View.GONE
        refresh()
        (parent as? View)?.requestLayout()
        requestLayout()
    }

    private fun refresh() {
        for ((gesture, row) in rows) {
            val selected = actions[gesture.name] ?: defaultFor(gesture)
            row.value.text = actionLabel(selected)
            row.chevron.rotation = if (expanded == gesture) 180f else 0f
            for (i in 0 until row.options.childCount) {
                val option = row.options.getChildAt(i) as? LinearLayout ?: continue
                val mark = option.getChildAt(1) as? TextView ?: continue
                mark.visibility = if (mark.tag == selected) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    private fun pressBackground() = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = 20f * density
    }
    /** Same test the rest of the UI uses: the capsule colour decides the theme. */
    private fun night(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
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

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}

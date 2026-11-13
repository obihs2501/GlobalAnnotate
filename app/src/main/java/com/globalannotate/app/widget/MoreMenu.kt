package com.globalannotate.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.globalannotate.app.R

/**
 * The ⋮ menu, built from Mi Canvas `penpicker_more_action_view`: a 200dp,
 * 24dp-radius card with an action list, a hairline, then the checkable list.
 * Rows come from `more_action_item_view` / `more_action_checked_item_view`.
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
        fun onClose()
    }

    private val itemList: LinearLayout
    private val checkedList: LinearLayout

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_more_action_view, this, true)
        itemList = findViewById(R.id.more_action_item_list)
        checkedList = findViewById(R.id.more_action_checked_item_list)

        item("笔身手势", R.drawable.ic_pressure) { listener.onGestureSettings() }
        item("导出 PNG", R.drawable.ic_image) { listener.onSavePng() }
        item("导出 PDF", R.drawable.ic_pdf) { listener.onSavePdf() }
        item("清空画布", R.drawable.ic_trash) { listener.onClear() }

        check("自动最小化", true) { listener.onAutoMinimize(it) }
        check("用手指涂画", false) { listener.onFingerDraw(it) }
    }

    private fun item(text: String, icon: Int, click: () -> Unit) {
        val row = LayoutInflater.from(context).inflate(R.layout.more_action_item_view, itemList, false)
        row.findViewById<ImageView>(R.id.more_action_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.more_action_text).text = text
        row.setBackgroundResource(R.drawable.penpicker_more_item_press_bg)
        row.setOnClickListener { click() }
        itemList.addView(row)
    }

    private fun check(text: String, initial: Boolean, change: (Boolean) -> Unit) {
        var enabled = initial
        val row = LayoutInflater.from(context)
            .inflate(R.layout.more_action_checked_item_view, checkedList, false)
        val label = row.findViewById<TextView>(R.id.more_action_checked_item_text)
        val mark = row.findViewById<ImageView>(R.id.more_action_checked_item_icon)
        label.text = text
        mark.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        row.setBackgroundResource(R.drawable.penpicker_more_item_press_bg)
        row.setOnClickListener {
            enabled = !enabled
            mark.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
            change(enabled)
        }
        checkedList.addView(row)
    }

    /** Reflect the service state when the menu is shown. */
    fun setChecks(autoMinimize: Boolean, fingerDraw: Boolean) {
        (checkedList.getChildAt(0)
            ?.findViewById<ImageView>(R.id.more_action_checked_item_icon))?.visibility =
            if (autoMinimize) View.VISIBLE else View.INVISIBLE
        (checkedList.getChildAt(1)
            ?.findViewById<ImageView>(R.id.more_action_checked_item_icon))?.visibility =
            if (fingerDraw) View.VISIBLE else View.INVISIBLE
    }
}

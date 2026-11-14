package com.globalannotate.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * The ⋮ menu, built from Mi Canvas `penpicker_more_action_view`: a 200dp,
 * 24dp-radius card, an action list, a hairline, then the checkable list.
 * Rows come from `more_action_item_view` / `more_action_checked_item_view`.
 */
class MoreMenu(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onGestureSettings()
        fun onLongShot()
        fun onSavePng()
        fun onSavePdf()
        fun onClear()
        fun onAutoMinimize(enabled: Boolean)
        fun onFingerDraw(enabled: Boolean)
        /** Disable finger scroll-follow (palm rejection for the ink layer). */
        fun onFingerScroll(enabled: Boolean)
        /** Let finger touches reach the app underneath (off = annotation layer eats them). */
        fun onTouchPassthrough(enabled: Boolean)
        fun onQuit()
    }

    private val itemList: LinearLayout
    private val checkedList: LinearLayout

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.penpicker_more_action_view, this, true)
        itemList = findViewById(R.id.more_action_item_list)
        checkedList = findViewById(R.id.more_action_checked_item_list)

        item("笔身手势", R.drawable.ic_pressure) { listener.onGestureSettings() }
        item("长截图", R.drawable.ic_layers) { listener.onLongShot() }
        item("保存图片", R.drawable.ic_image) { listener.onSavePng() }
        item("保存 PDF", R.drawable.ic_pdf) { listener.onSavePdf() }
        item("清空批注", R.drawable.ic_trash) { listener.onClear() }
        item("退出批注", R.drawable.ic_close) { listener.onQuit() }

        check("自动最小化", true) { listener.onAutoMinimize(it) }
        check("用手指涂画", false) { listener.onFingerDraw(it) }
        check("手指滑动跟随", true) { listener.onFingerScroll(it) }
        check("手指事件透传", true) { listener.onTouchPassthrough(it) }
    }

    private fun item(text: String, icon: Int, click: () -> Unit) {
        val row = LayoutInflater.from(context).inflate(R.layout.more_action_item_view, itemList, false)
        row.findViewById<ImageView>(R.id.more_action_icon).apply {
            setImageResource(icon)
            setColorFilter(ContextCompat.getColor(context, R.color.ga_icon))
        }
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

    fun setChecks(
        autoMinimize: Boolean,
        fingerDraw: Boolean,
        fingerScroll: Boolean,
        touchPassthrough: Boolean,
    ) {
        set(0, autoMinimize)
        set(1, fingerDraw)
        set(2, fingerScroll)
        set(3, touchPassthrough)
    }

    private fun set(index: Int, enabled: Boolean) {
        (checkedList.getChildAt(index)
            ?.findViewById<ImageView>(R.id.more_action_checked_item_icon))?.visibility =
            if (enabled) View.VISIBLE else View.INVISIBLE
    }

    fun setLongShotEnabled(enabled: Boolean) {
        itemList.getChildAt(1)?.alpha = if (enabled) 1f else 0.4f
    }
}

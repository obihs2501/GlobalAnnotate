package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.globalannotate.app.OverlayMode

/** Deliberately plain View-based toolbar: no Compose/Miuix dependency, easy to build. */
class ToolbarView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onPen()
        fun onEraser()
        fun onColor()
        fun onWidth(delta: Float)
        fun onUndo()
        fun onRedo()
        fun onClear()
        fun onCycleMode()
        fun onSavePng()
        fun onSavePdf()
        fun onQuit()
    }

    private val modeButton: Button
    private val eraserButton: Button
    private val undoButton: Button
    private val redoButton: Button

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.argb(232, 248, 249, 251))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        elevation = dp(8).toFloat()

        modeButton = button("模式") { listener.onCycleMode() }
        addView(button("笔") { listener.onPen() })
        eraserButton = button("橡皮") { listener.onEraser() }
        addView(eraserButton)
        addView(button("颜色") { listener.onColor() })
        addView(button("细") { listener.onWidth(-1f) })
        addView(button("粗") { listener.onWidth(1f) })
        undoButton = button("撤销") { listener.onUndo() }
        redoButton = button("重做") { listener.onRedo() }
        addView(undoButton)
        addView(redoButton)
        addView(button("清空") { listener.onClear() })
        addView(button("PNG") { listener.onSavePng() })
        addView(button("PDF") { listener.onSavePdf() })
        addView(button("退出") { listener.onQuit() })
    }

    private fun button(text: String, onClick: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 12f
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minimumWidth = 0
            minWidth = 0
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

    fun update(mode: OverlayMode, eraser: Boolean, canUndo: Boolean, canRedo: Boolean) {
        modeButton.text = when (mode) {
            OverlayMode.DRAW -> "模式:拦截"
            OverlayMode.PASSTHROUGH -> "模式:穿透"
            OverlayMode.ROOT_SPLIT -> "模式:分流"
        }
        eraserButton.text = if (eraser) "橡皮✓" else "橡皮"
        undoButton.isEnabled = canUndo
        redoButton.isEnabled = canRedo
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

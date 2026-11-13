package com.globalannotate.app.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.globalannotate.app.OverlayMode
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.widget.BrushPreview
import com.globalannotate.app.widget.ColorStrip
import com.globalannotate.app.widget.IconButton

/**
 * Two-row HyperOS-style capsule toolbar.
 * Row 1: brush types | palette + eyedropper | live stroke preview
 * Row 2: width slider | pressure toggle | history | mode / export / close
 *
 * Visual language follows the Miuix/HyperOS Surface conventions used by the
 * sibling DrawAnywhere project: 26dp squircle-ish capsule, translucent
 * surfaceContainerHigh background, #0D84FF accent, 8dp spacing.
 */
class ToolbarView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onStyle(style: PenStyle)
        fun onColor(color: Int)
        fun onPipette()
        fun onWidth(widthDp: Float)
        fun onPressure(enabled: Boolean)
        fun onUndo()
        fun onRedo()
        fun onClear()
        fun onCycleMode()
        fun onSavePng()
        fun onSavePdf()
        fun onQuit()
    }

    private val density = resources.displayMetrics.density
    private val brushButtons = mutableMapOf<PenStyle, IconButton>()
    private val colorStrip: ColorStrip
    private val preview = BrushPreview(context)
    private val widthSeek: SeekBar
    private val widthValue: TextView
    private val pressureButton: IconButton
    private val undoButton: IconButton
    private val redoButton: IconButton
    private val modeButton: IconButton
    private var updating = false

    private val brushIcons = listOf(
        PenStyle.PEN to R.drawable.ic_pen,
        PenStyle.PENCIL to R.drawable.ic_pencil,
        PenStyle.MARKER to R.drawable.ic_marker,
        PenStyle.HIGHLIGHTER to R.drawable.ic_highlighter,
        PenStyle.LASER to R.drawable.ic_laser,
        PenStyle.ERASER_PIXEL to R.drawable.ic_eraser,
        PenStyle.ERASER_STROKE to R.drawable.ic_eraser_stroke,
    )

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_toolbar)
        elevation = dp(8)
        setPadding(dp(10).toInt(), dp(8).toInt(), dp(10).toInt(), dp(8).toInt())

        // ── Row 1 ──────────────────────────────────────────────────────────
        val row1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for ((style, icon) in brushIcons) {
            val button = iconButton(icon) { listener.onStyle(style) }
            brushButtons[style] = button
            row1.addView(button)
        }
        row1.addView(divider())
        colorStrip = ColorStrip(context, { listener.onColor(it) }, { listener.onPipette() })
        row1.addView(colorStrip)
        row1.addView(divider())
        row1.addView(preview, LayoutParams(dp(56).toInt(), dp(30).toInt()))
        addView(scroll(row1))

        // ── Row 2 ──────────────────────────────────────────────────────────
        val row2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row2.addView(label("粗细"))
        widthSeek = SeekBar(context).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Color.parseColor("#0D84FF"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#0D84FF"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    if (!updating && fromUser) listener.onWidth(1f + value / 100f * 59f)
                }
                override fun onStartTrackingTouch(seek: SeekBar) = Unit
                override fun onStopTrackingTouch(seek: SeekBar) = Unit
            })
        }
        row2.addView(widthSeek, LayoutParams(dp(130).toInt(), dp(30).toInt()))
        widthValue = label("12")
        row2.addView(widthValue)

        pressureButton = iconButton(R.drawable.ic_pressure) {
            listener.onPressure(!pressureButton.selected)
        }
        row2.addView(divider())
        row2.addView(pressureButton)

        undoButton = iconButton(R.drawable.ic_undo) { listener.onUndo() }
        redoButton = iconButton(R.drawable.ic_redo) { listener.onRedo() }
        row2.addView(divider())
        row2.addView(undoButton)
        row2.addView(redoButton)
        row2.addView(iconButton(R.drawable.ic_trash) { listener.onClear() })

        modeButton = iconButton(R.drawable.ic_layers) { listener.onCycleMode() }
        row2.addView(divider())
        row2.addView(modeButton)
        row2.addView(iconButton(R.drawable.ic_image) { listener.onSavePng() })
        row2.addView(iconButton(R.drawable.ic_pdf) { listener.onSavePdf() })
        row2.addView(iconButton(R.drawable.ic_close) { listener.onQuit() })
        addView(scroll(row2))
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        updating = true
        brushButtons.forEach { (style, button) -> button.selected = style == brush.style }
        colorStrip.setColor(brush.color)
        preview.brush = brush
        widthSeek.progress = (((brush.widthDp - 1f) / 59f) * 100f).toInt().coerceIn(0, 100)
        widthValue.text = brush.widthDp.toInt().toString()
        pressureButton.selected = brush.pressureEnabled
        undoButton.enabledVisual = canUndo
        redoButton.enabledVisual = canRedo
        modeButton.selected = mode == OverlayMode.ROOT_SPLIT
        updating = false
    }

    private fun iconButton(icon: Int, tap: () -> Unit): IconButton =
        IconButton(context).apply {
            iconRes = icon
            onTap = tap
            contentDescription = resources.getResourceEntryName(icon)
            layoutParams = LayoutParams(dp(38).toInt(), dp(38).toInt())
        }

    private fun scroll(child: View): HorizontalScrollView =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(child, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Color.argb(28, 0, 0, 0))
        layoutParams = LayoutParams(dp(1).toInt(), dp(24).toInt()).apply {
            marginStart = dp(6).toInt()
            marginEnd = dp(6).toInt()
        }
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#5F6368"))
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(30).toInt()).apply {
            marginStart = dp(4).toInt()
            marginEnd = dp(4).toInt()
        }
    }

    private fun dp(value: Int): Float = value * density
}

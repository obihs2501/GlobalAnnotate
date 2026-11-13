package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.globalannotate.app.OverlayMode
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.IconButton
import com.globalannotate.app.widget.SwatchView
import kotlin.math.min

/**
 * Single-row bottom capsule modelled on Mi Canvas. There is deliberately NO
 * exit button here (the original uses a separate back arrow), which also keeps
 * the destructive action far away from the ⋮ menu and the export items.
 * Quick colours are a 2x2 grid so the bar never needs horizontal scrolling.
 */
class ToolbarView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onStyle(style: PenStyle)
        fun onColor(color: Int)
        fun onOpenColorPanel()
        fun onOpenBrushPanel()
        fun onOpenMoreMenu()
        fun onUndo()
        fun onRedo()
        fun onCollapse()
    }

    private val density = resources.displayMetrics.density
    private val glyphs = mutableMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val undoButton: IconButton
    private val redoButton: IconButton

    private val styles = listOf(
        PenStyle.PENCIL, PenStyle.BALLPOINT, PenStyle.INK_PEN,
        PenStyle.MARKER, PenStyle.HIGHLIGHTER, PenStyle.LASER, PenStyle.ERASER,
    )

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_toolbar)
        elevation = dp(8)
        setPadding(dp(14).toInt(), dp(6).toInt(), dp(14).toInt(), dp(10).toInt())

        val handle = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(90, 255, 255, 255))
                cornerRadius = dp(2)
            }
            setOnClickListener { listener.onCollapse() }
            layoutParams = LinearLayout.LayoutParams(dp(38).toInt(), dp(4).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6).toInt()
            }
        }
        addView(handle)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        undoButton = circle(R.drawable.ic_undo) { listener.onUndo() }
        redoButton = circle(R.drawable.ic_redo) { listener.onRedo() }
        row.addView(undoButton)
        row.addView(redoButton)
        row.addView(space(dp(8)))

        for (style in styles) {
            val glyph = BrushGlyph(context).apply {
                this.style = style
                translationY = dp(8)
                setOnClickListener {
                    if (checked) listener.onOpenBrushPanel() else listener.onStyle(style)
                }
                layoutParams = LinearLayout.LayoutParams(dp(46).toInt(), dp(84).toInt())
            }
            glyphs[style] = glyph
            row.addView(glyph)
        }

        row.addView(space(dp(10)))
        // 2x2 colour grid: no horizontal scrolling needed.
        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(76).toInt(), dp(76).toInt())
        }
        for (r in 0..1) {
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            }
            for (c in 0..1) {
                val index = r * 2 + c
                val cell = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
                if (index < BrushConfig.QUICK_COLORS.size) {
                    val color = BrushConfig.QUICK_COLORS[index]
                    val swatch = SwatchView(context, color).apply {
                        setOnClickListener { listener.onColor(color) }
                    }
                    quickSwatches.add(swatch)
                    line.addView(swatch, cell)
                } else {
                    line.addView(RainbowSwatch(context).apply {
                        onTap = { listener.onOpenColorPanel() }
                    }, cell)
                }
            }
            grid.addView(line)
        }
        row.addView(grid)
        row.addView(space(dp(8)))
        row.addView(circle(R.drawable.ic_more) { listener.onOpenMoreMenu() })
        addView(scroll(row))
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        glyphs.forEach { (style, glyph) ->
            glyph.color = brush.color
            glyph.checked = style == brush.style
            glyph.animate().translationY(if (glyph.checked) -dp(6) else dp(8)).setDuration(150).start()
        }
        quickSwatches.forEach { it.checked = it.color == brush.color }
        undoButton.enabledVisual = canUndo
        redoButton.enabledVisual = canRedo
    }

    private fun circle(icon: Int, tap: () -> Unit): IconButton =
        IconButton(context).apply {
            iconRes = icon
            onTap = tap
            contentDescription = resources.getResourceEntryName(icon)
            layoutParams = LinearLayout.LayoutParams(dp(40).toInt(), dp(40).toInt())
        }

    private fun scroll(child: View): HorizontalScrollView =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(child, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }

    private fun space(size: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(size.toInt(), 1)
    }

    private fun dp(value: Int): Float = value * density

    private class RainbowSwatch(context: Context) : View(context) {
        var onTap: (() -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = intArrayOf(
            Color.parseColor("#F44336"), Color.parseColor("#FFEB3B"),
            Color.parseColor("#4CAF50"), Color.parseColor("#00BCD4"),
            Color.parseColor("#2196F3"), Color.parseColor("#9C27B0"),
            Color.parseColor("#F44336"),
        )

        init {
            isClickable = true
            setOnClickListener { onTap?.invoke() }
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            paint.shader = SweepGradient(cx, cy, colors, null)
            canvas.drawCircle(cx, cy, min(width, height) * 0.36f, paint)
            paint.shader = null
        }
    }
}

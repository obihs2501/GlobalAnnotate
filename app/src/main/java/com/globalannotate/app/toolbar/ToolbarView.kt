package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
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
 * Single-row Mi Canvas-style bottom capsule. All settings live in separate
 * floating panels; the exit button stays permanently on this bar.
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
        fun onQuit()
        fun onCollapse()
    }

    private val density = resources.displayMetrics.density
    private val glyphs = mutableMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val undoButton: IconButton
    private val redoButton: IconButton

    private val styles = listOf(
        PenStyle.PENCIL, PenStyle.BALLPOINT, PenStyle.PEN, PenStyle.MARKER,
        PenStyle.HIGHLIGHTER, PenStyle.LASER, PenStyle.ERASER_PIXEL, PenStyle.ERASER_STROKE,
    )
    private val quickColors = listOf(
        Color.parseColor("#D93025"),
        Color.parseColor("#F9AB00"),
        Color.parseColor("#1A73E8"),
        Color.parseColor("#202124"),
    )

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_toolbar)
        elevation = dp(8)
        setPadding(dp(16).toInt(), dp(6).toInt(), dp(16).toInt(), dp(10).toInt())

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
        row.addView(space(dp(6)))

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

        row.addView(space(dp(8)))
        for (c in quickColors) {
            val swatch = SwatchView(context, c).apply {
                setOnClickListener { listener.onColor(c) }
                layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt()).apply {
                    marginStart = dp(4).toInt()
                    marginEnd = dp(4).toInt()
                }
            }
            quickSwatches.add(swatch)
            row.addView(swatch)
        }
        row.addView(RainbowSwatch(context).apply {
            onTap = { listener.onOpenColorPanel() }
            layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt())
        })
        row.addView(space(dp(6)))
        row.addView(circle(R.drawable.ic_more) { listener.onOpenMoreMenu() })
        row.addView(circle(R.drawable.ic_close) { listener.onQuit() })
        addView(scroll(row))
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        glyphs.forEach { (style, glyph) ->
            glyph.color = brush.color
            glyph.checked = style == brush.style
            glyph.animate().translationY(if (glyph.checked) -dp(6) else dp(8)).setDuration(160).start()
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

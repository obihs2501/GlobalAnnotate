package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.globalannotate.app.OverlayMode
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.IconButton
import com.globalannotate.app.widget.SwatchView
import kotlin.math.min

/**
 * Compact single-row capsule modelled on Mi Canvas: undo/redo, 7 pens,
 * a 2x2 quick-colour grid, the ⋮ menu — and nothing else. The exit action is a
 * separate back button, so destructive and export actions never sit adjacent.
 * Selection only lifts the glyph; no ring is drawn.
 */
class ToolbarView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onStyle(style: PenStyle, anchorX: Int)
        fun onColor(color: Int)
        fun onOpenColorPanel(anchorX: Int)
        fun onOpenMoreMenu(anchorX: Int)
        fun onUndo()
        fun onRedo()
        fun onCollapse()
    }

    private val density = resources.displayMetrics.density
    private val glyphs = mutableMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val undoButton: IconButton
    private val redoButton: IconButton
    private lateinit var moreAnchor: View

    private val styles = listOf(
        PenStyle.PENCIL, PenStyle.BALLPOINT, PenStyle.INK_PEN,
        PenStyle.MARKER, PenStyle.HIGHLIGHTER, PenStyle.LASER, PenStyle.ERASER,
    )

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_toolbar)
        elevation = dp(6)
        setPadding(dp(8).toInt(), dp(5).toInt(), dp(8).toInt(), dp(7).toInt())

        val handle = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider))
            setOnClickListener { listener.onCollapse() }
            layoutParams = LinearLayout.LayoutParams(dp(32).toInt(), dp(3).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(4).toInt()
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

        for (style in styles) {
            val glyph = BrushGlyph(context).apply {
                this.style = style
                translationY = dp(6)
                setOnClickListener {
                    listener.onStyle(style, centerOnScreen(this))
                }
                layoutParams = LinearLayout.LayoutParams(dp(40).toInt(), dp(72).toInt())
            }
            glyphs[style] = glyph
            row.addView(glyph)
        }

        // 2x2 quick colours, matching Mi Canvas (3 fixed + rainbow).
        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(62).toInt(), dp(62).toInt()).apply {
                marginStart = dp(6).toInt()
            }
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
                        onTap = { listener.onOpenColorPanel(centerOnScreen(this)) }
                    }, cell)
                }
            }
            grid.addView(line)
        }
        row.addView(grid)

        val moreButton = circle(R.drawable.ic_more) {
            listener.onOpenMoreMenu(centerOnScreen(moreAnchor))
        }
        moreButton.layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt()).apply {
            marginStart = dp(6).toInt()
        }
        moreAnchor = moreButton
        row.addView(moreButton)
        addView(scroll(row))
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        glyphs.forEach { (style, glyph) ->
            glyph.color = brush.color
            glyph.checked = style == brush.style
            glyph.animate().translationY(if (glyph.checked) -dp(4) else dp(6)).setDuration(140).start()
        }
        quickSwatches.forEach { it.checked = it.color == brush.color }
        undoButton.enabledVisual = canUndo
        redoButton.enabledVisual = canRedo
    }

    private fun centerOnScreen(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0] + view.width / 2
    }

    private fun circle(icon: Int, tap: () -> Unit): IconButton =
        IconButton(context).apply {
            iconRes = icon
            onTap = tap
            contentDescription = resources.getResourceEntryName(icon)
            layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt())
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

    private fun dp(value: Int): Float = value * density

    private class RainbowSwatch(context: Context) : View(context) {
        var onTap: (() -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val colors = intArrayOf(
            Color.parseColor("#E7E040"), Color.parseColor("#EEAA3C"),
            Color.parseColor("#E8403B"), Color.parseColor("#B33ED5"),
            Color.parseColor("#694AE8"), Color.parseColor("#3CCAE7"),
            Color.parseColor("#3CE885"), Color.parseColor("#89E743"),
            Color.parseColor("#E7E040"),
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

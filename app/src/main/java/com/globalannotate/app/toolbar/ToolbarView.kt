package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
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
import com.globalannotate.app.widget.Ripple
import com.globalannotate.app.widget.SwatchView
import kotlin.math.abs
import kotlin.math.min

/**
 * Compact single-row capsule modelled on Mi Canvas: undo/redo, 7 pens,
 * a 2x2 quick-colour grid, the ⋮ menu — and nothing else. The exit action is a
 * separate back button, so destructive and export actions never sit adjacent.
 * Selection only lifts the glyph; no ring is drawn.
 */
class ToolbarView(context: Context, private val listener: Listener) : LinearLayout(context) {

    /** Drag callbacks: the host moves the overlay window, not the view. */
    interface DragListener {
        fun onDragStart()
        fun onDrag(dx: Float, dy: Float)
        fun onDragEnd()
    }

    interface Listener {
        fun onStyle(style: PenStyle, anchorX: Int)
        fun onColor(color: Int)
        fun onOpenColorPanel(anchorX: Int)
        fun onOpenMoreMenu(anchorX: Int)
        fun onUndo()
        fun onRedo()
        fun onCollapse()
        fun onQuit()
    }

    private val density = resources.displayMetrics.density
    private val glyphs = mutableMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val undoButton: IconButton
    private val redoButton: IconButton
    private lateinit var moreAnchor: View

    /** The six tools Mi Canvas actually exposes: PenTypeValue 3/2/1/4/10/7. */
    private val styles = PenStyle.toolbarOrder

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_toolbar)
        elevation = dp(6)
        setPadding(dp(20).toInt(), dp(12).toInt(), dp(20).toInt(), dp(12).toInt())

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
                foreground = Ripple.rounded(context, dp(14))
                layoutParams = LinearLayout.LayoutParams(dp(48).toInt(), dp(64).toInt())
            }
            glyphs[style] = glyph
            row.addView(glyph)
        }

        // 2x2 quick colours, matching Mi Canvas (3 fixed + rainbow).
        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(76).toInt(), dp(76).toInt()).apply {
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
        moreButton.layoutParams = LinearLayout.LayoutParams(dp(40).toInt(), dp(40).toInt()).apply {
            marginStart = dp(6).toInt()
        }
        moreAnchor = moreButton
        row.addView(moreButton)

        // A divider keeps 退出 physically away from ⋮ (export lives in the menu).
        row.addView(View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.ga_divider))
            layoutParams = LinearLayout.LayoutParams(dp(1).toInt(), dp(26).toInt()).apply {
                marginStart = dp(12).toInt()
                marginEnd = dp(12).toInt()
            }
        })
        row.addView(circle(R.drawable.ic_close) { listener.onQuit() })
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

    var dragListener: DragListener? = null

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragEligible = true

    /**
     * The bar floats above other apps, so dragging has to move the window, not
     * the view. Blank bar area drags; buttons keep their own touches because
     * we only intercept when nothing clickable sits under the finger.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                // Do not hijack a gesture that started on a button or the scroller.
                dragEligible = !hitsInteractive(this, ev.x, ev.y)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) return true
                if (dragEligible && (abs(ev.rawX - downX) > dp(8) || abs(ev.rawY - downY) > dp(8))) {
                    dragging = true
                    dragListener?.onDragStart()
                    return true
                }
            }
        }
        return false
    }

    private fun hitsInteractive(group: android.view.ViewGroup, px: Float, py: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val x = px + group.scrollX - child.left
            val y = py + group.scrollY - child.top
            if (x < 0f || y < 0f || x > child.width || y > child.height) continue
            if (child is android.view.ViewGroup && hitsInteractive(child, x, y)) return true
            if (child.isClickable || child is HorizontalScrollView) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragListener?.onDrag(event.rawX - downX, event.rawY - downY)
                downX = event.rawX
                downY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) dragListener?.onDragEnd()
                dragging = false
                return true
            }
        }
        return dragging
    }

    private fun circle(icon: Int, tap: () -> Unit): IconButton =
        IconButton(context).apply {
            iconRes = icon
            onTap = tap
            contentDescription = resources.getResourceEntryName(icon)
            layoutParams = LinearLayout.LayoutParams(dp(40).toInt(), dp(40).toInt())
        }

    /** Natural width of the row, so the bar hugs its content instead of leaving gaps. */
    fun contentWidthPx(): Int {
        val child = (getChildAt(childCount - 1) as HorizontalScrollView).getChildAt(0)
        child.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val padding = paddingLeft + paddingRight
        return child.measuredWidth + padding + dp(8).toInt()
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


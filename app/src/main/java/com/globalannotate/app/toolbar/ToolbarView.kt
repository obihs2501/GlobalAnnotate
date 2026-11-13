package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
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
 * The floating pen bar, measured from Mi Canvas itself:
 *  - capsule 92dp tall, full-round, 16dp side padding
 *  - pen slots 48dp wide x 68dp tall (`pk_ink_tool_width`)
 *  - undo / redo / ⋮ / × are 38dp circles (`pk_other_tool_width` slots)
 *  - 2x2 quick colours: 34dp circles with 12dp gaps (80dp block)
 *  - the selected pen rises 12dp above the capsule, so the window keeps a
 *    104dp height and the capsule is inset by that lift.
 */
class ToolbarView(context: Context, private val listener: Listener) : FrameLayout(context) {

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

    companion object {
        /** Mi Canvas: cp_brush_height 124dp inside a ~96dp capsule. */
        const val BAR_HEIGHT_DP = 96
        const val BRUSH_HEIGHT_DP = 124
        const val WINDOW_HEIGHT_DP = BRUSH_HEIGHT_DP + 8
        private const val BRUSH_LARGE_DP = 68  // cp_brush_width_large
        private const val BRUSH_NORMAL_DP = 48 // cp_brush_width_normal
        private const val BUTTON_DP = 32       // cp_button_size
        private const val SWATCH_DP = 32
        private const val SWATCH_GAP_DP = 12
    }

    private val density = resources.displayMetrics.density
    private val glyphs = linkedMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val row = LinearLayout(context)
    private val scroll: HorizontalScrollView
    private val undoButton: IconButton
    private val redoButton: IconButton
    private lateinit var moreAnchor: View

    var dragListener: DragListener? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragEligible = true

    init {
        // Capsule layer, inset by the lift so a selected pen can stick out.
        // Capsule sits at the bottom; the 124dp brushes rise above it.
        addView(
            View(context).apply { setBackgroundResource(R.drawable.bg_toolbar) },
            LayoutParams(MATCH_PARENT, dp(BAR_HEIGHT_DP)).apply {
                topMargin = dp(WINDOW_HEIGHT_DP - BAR_HEIGHT_DP)
            },
        )

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(8), dp(16), 0)

        undoButton = circle(R.drawable.ic_undo) { listener.onUndo() }
        redoButton = circle(R.drawable.ic_redo) { listener.onRedo() }
        row.addView(undoButton)
        row.addView(redoButton, LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)).apply { marginStart = dp(8) })

        for (style in PenStyle.toolbarOrder) {
            val glyph = BrushGlyph(context).apply {
                this.style = style
                setOnClickListener { listener.onStyle(style, centerOnScreen(this)) }
                foreground = Ripple.rounded(context, dp(14).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(BRUSH_NORMAL_DP), dp(BRUSH_HEIGHT_DP)).apply {
                    if (style == PenStyle.toolbarOrder.first()) marginStart = dp(10)
                }
            }
            glyphs[style] = glyph
            row.addView(glyph)
        }

        // 2x2 quick colours + rainbow, 34dp circles with 12dp gaps.
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginStart = dp(10) }
        }
        for (r in 0..1) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            }
            for (c in 0..1) {
                val index = r * 2 + c
                val cell = LinearLayout.LayoutParams(dp(SWATCH_DP), dp(SWATCH_DP))
                if (index < BrushConfig.QUICK_COLORS.size) {
                    val color = BrushConfig.QUICK_COLORS[index]
                    val swatch = SwatchView(context, color).apply {
                        setOnClickListener { listener.onColor(color) }
                        layoutParams = cell
                    }
                    quickSwatches.add(swatch)
                    line.addView(swatch, cell.apply { if (c > 0) marginStart = dp(SWATCH_GAP_DP) })
                } else {
                    line.addView(RainbowSwatch(context).apply {
                        onTap = { listener.onOpenColorPanel(centerOnScreen(this)) }
                    }, cell.apply { if (c > 0) marginStart = dp(SWATCH_GAP_DP) })
                }
            }
            grid.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply {
                if (r > 0) topMargin = dp(SWATCH_GAP_DP)
            })
        }
        row.addView(grid)

        val moreButton = circle(R.drawable.ic_more) {
            listener.onOpenMoreMenu(centerOnScreen(moreAnchor))
        }
        moreButton.layoutParams = LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)).apply { marginStart = dp(10) }
        moreAnchor = moreButton
        row.addView(moreButton)

        row.addView(circle(R.drawable.ic_close) { listener.onQuit() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)).apply { marginStart = dp(16) }
        })

        scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = false
            addView(row, LayoutParams(WRAP_CONTENT, dp(BRUSH_HEIGHT_DP)))
            layoutParams = LayoutParams(MATCH_PARENT, dp(BRUSH_HEIGHT_DP)).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(0)
            }
        }
        addView(scroll)
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        glyphs.forEach { (style, glyph) ->
            glyph.color = brush.color
            val selected = style == brush.style
            glyph.checked = selected
            // Mi Canvas widens the active brush slot (68dp vs 48dp) and keeps
            // every brush at the same 124dp height, so the selected one rises
            // above the capsule naturally.
            val targetWidth = dp(if (selected) BRUSH_LARGE_DP else BRUSH_NORMAL_DP)
            if (glyph.layoutParams.width != targetWidth) {
                glyph.layoutParams = (glyph.layoutParams as LinearLayout.LayoutParams).apply {
                    width = targetWidth
                }
                glyph.requestLayout()
            }
            glyph.animate()
                .scaleX(if (selected) 1.12f else 1f)
                .scaleY(if (selected) 1.12f else 1f)
                .translationY(if (selected) -dp(6).toFloat() else 0f)
                .setDuration(150)
                .start()
        }
        quickSwatches.forEach { it.checked = it.color == brush.color }
        undoButton.enabledVisual = canUndo
        redoButton.enabledVisual = canRedo
    }

    /** Natural width so the window hugs the row (no trailing gap). */
    fun contentWidthPx(): Int {
        row.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(dp(BRUSH_HEIGHT_DP), MeasureSpec.EXACTLY),
        )
        return row.measuredWidth
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
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
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

    private fun dp(value: Int): Int = (value * density).toInt()

    /** White disc + rainbow ring + white centre, matching the original. */
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
            val r = min(width, height) * 0.5f
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, r, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.44f
            paint.shader = SweepGradient(cx, cy, colors, null)
            canvas.drawCircle(cx, cy, r * 0.74f, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL
        }
    }
}

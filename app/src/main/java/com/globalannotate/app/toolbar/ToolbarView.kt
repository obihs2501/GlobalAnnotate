package com.globalannotate.app.toolbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.globalannotate.app.R
import com.globalannotate.app.model.BrushConfig
import com.globalannotate.app.model.PenStyle
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.IconButton
import com.globalannotate.app.widget.Ripple
import kotlin.math.abs


/**
 * The floating pen bar, rebuilt against a pixel measurement of Mi Canvas 4.1.8
 * on this tablet (_work/shots/mc_bar_crop.png, uiautomator bounds):
 *
 *   capsule        101.5dp tall, full-round, 15dp side padding
 *   undo / redo    45.1dp circles, 30dp glyphs, 41.5dp pitch
 *   pens           45dp x 77dp, bottom-aligned; the active one grows to
 *                  64dp x 94dp (still inside the capsule)
 *   quick colours  41dp circles, 2x2, touching
 *   more           45dp circle
 *   handle         30dp x 3.6dp, centred, 4dp below the capsule top
 *
 * Nothing here is hand-waved: every number above was read off the reference.
 */
class ToolbarView(context: Context, private val listener: Listener) : FrameLayout(context) {

    interface Listener {
        fun onTool(style: PenStyle, anchorX: Int)
        fun onColor(color: Int)
        fun onOpenColorPanel(anchorX: Int)
        /** Fourth slot: open the colour editor; the picked colour replaces that slot. */
        fun onOpenCustomColor(anchorX: Int)
        fun onOpenMore(anchorX: Int)
        fun onUndo()
        fun onRedo()
    }

    interface DragListener {
        fun onDragStart()
        fun onDrag(dx: Float, dy: Float)
        fun onDragEnd()
    }

    var dragListener: DragListener? = null

    /** Colour of the fourth slot; 0 shows the rainbow disc (nothing picked yet). */
    var customColor: Int = 0
        set(value) { field = value; customSwatch?.color = value }

    private val density = resources.displayMetrics.density
    private val glyphs = linkedMapOf<PenStyle, BrushGlyph>()
    private val swatches = mutableListOf<Swatch>()
    private var customSwatch: CustomSwatch? = null
    private val undo: IconButton
    private val redo: IconButton
    private val more: IconButton
    private val row = LinearLayout(context)

    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        // The selected tool is allowed to overhang its slot.
        clipChildren = false
        clipToPadding = false
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        row.clipChildren = false
        row.clipToPadding = false
        row.setPadding(dp(15), 0, dp(15), 0)

        undo = circle(R.drawable.picker_undo) { listener.onUndo() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                bottomMargin = dp((CAPSULE_DP - 42) / 2)
            }
        }
        redo = circle(R.drawable.picker_redo) { listener.onRedo() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                bottomMargin = dp((CAPSULE_DP - 42) / 2)
            }
        }
        row.addView(undo)
        row.addView(redo)

        for (style in PenStyle.toolbarOrder) {
            val glyph = BrushGlyph(context).apply {
                this.style = style
                isClickable = true
                foreground = Ripple.rounded(context, dp(12).toFloat())
                setOnClickListener { listener.onTool(style, centerOnScreen(this)) }
                artLengthDp = PEN_HEIGHT_DP.toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    dp(if (style == PenStyle.ERASER) LARGE_WIDTH_DP else PEN_WIDTH_DP),
                    dp(WINDOW_HEIGHT_DP),
                ).apply {
                    if (style == PenStyle.toolbarOrder.first()) marginStart = dp(20)
                }
            }
            glyphs[style] = glyph
            row.addView(glyph)
        }

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(82)).apply {
                marginStart = dp(8)
                bottomMargin = dp((CAPSULE_DP - 82) / 2)
            }
        }
        for (r in 0..1) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            }
            for (c in 0..1) {
                val index = r * 2 + c
                val cell = LinearLayout.LayoutParams(dp(41), dp(41))
                if (index < BrushConfig.QUICK_COLORS.size) {
                    val color = BrushConfig.QUICK_COLORS[index]
                    val swatch = Swatch(color).apply {
                        setOnClickListener { listener.onColor(color) }
                    }
                    swatches.add(swatch)
                    line.addView(swatch, cell)
                } else {
                    val custom = CustomSwatch().apply {
                        onTap = { listener.onOpenCustomColor(centerOnScreen(this)) }
                    }
                    customSwatch = custom
                    line.addView(custom, cell)
                }
            }
            grid.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        row.addView(grid)

        more = circle(R.drawable.picker_more) { listener.onOpenMore(centerOnScreen(this)) }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(0)
                bottomMargin = dp((CAPSULE_DP - 42) / 2)
            }
        }
        row.addView(more)

        addView(row, LayoutParams(MATCH, dp(WINDOW_HEIGHT_DP)))
    }

    // ── drawing ────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Translucent overlay windows keep their previous frame unless the view
        // clears it, so a stale white capsule from the day theme (or from an
        // older bar position) used to peek out from behind the current one.
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        drawCapsule(canvas)
        super.dispatchDraw(canvas)
        drawHandle(canvas)
    }

    private fun drawCapsule(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val capsule = dp(CAPSULE_DP).toFloat()
        // The pens' resting position is the window top, so the capsule starts one
        // The capsule is the window: the tools' seats are bottom-aligned with it,
        // so a tool that slides in hides its tail below the window edge and the
        // selected one slides back up without ever looking shorter.
        val seat = 0f
        canvas.drawRoundRect(
            RectF(0f, seat, width.toFloat(), seat + capsule),
            capsule / 2f, capsule / 2f, paint,
        )
    }

    private fun drawHandle(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ContextCompat.getColor(context, R.color.ga_handle)
        val w = 30f * density
        val h = 3.6f * density
        val left = (width - w) / 2f
        val top = 4f * density
        canvas.drawRoundRect(RectF(left, top, left + w, top + h), h / 2f, h / 2f, paint)
    }

    // ── state ──────────────────────────────────────────────────────────────

    private var lastSelected: PenStyle? = null

    fun update(brush: BrushConfig, canUndo: Boolean, canRedo: Boolean) {
        // Slot geometry is fixed per tool, exactly like Mi Canvas: the eraser
        // (and ruler) use the large slot, every pen the normal one, and
        // selecting a brush never resizes or reflows the row.
        val selected = brush.style
        glyphs.forEach { (style, glyph) ->
            glyph.color = brush.color
            glyph.checked = style == selected
            if (style == selected && lastSelected != selected) {
                settle(glyph, selected = true)
            } else if (style != selected && lastSelected == style) {
                settle(glyph, selected = false)
            }
        }
        lastSelected = selected
        swatches.forEach { it.checked = it.color == brush.color }
        customSwatch?.checked = customColor != 0 && customColor == brush.color
        undo.enabledVisual = canUndo
        redo.enabledVisual = canRedo
    }

    /**
     * Mi Canvas' selection motion: only the chosen tool stretches, anchored at
     * the capsule floor. Neighbours never move and the row never re-lays out,
     * so the brush can only grow upwards ("only shrinks forward").
     */
    /**
     * Mi Canvas' actual selection motion (`II11I` in the pen bar): every tool
     * rests 18dp outside the capsule; the selected tool alone slides back to
     * its seat with a spring. No scaling, no reflow, so nothing moves
     * sideways — the chosen brush simply retracts inward ("only shrinks
     * forward") and the others stay put.
     */
    private fun settle(glyph: BrushGlyph, selected: Boolean) {
        val from = glyph.artOffsetDp
        // Offset 0 is the *out* position: the pen's tip reaches the capsule top and
        // its base sits on the capsule floor. Sliding to OUTWARD_DP seats it, which
        // pushes its tail OUTWARD_DP below the window edge, where it is hidden —
        // exactly the length the outward slide gives back, so the pen never looks
        // shortened.
        val to = if (selected) 0f else OUTWARD_DP.toFloat()
        val animator = android.animation.ValueAnimator.ofFloat(from, to)
        animator.duration = 300
        animator.interpolator = android.view.animation.OvershootInterpolator(0.35f)
        animator.addUpdateListener { glyph.artOffsetDp = it.animatedValue as Float }
        animator.start()
    }

    fun contentWidthPx(): Int {
        row.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(dp(CAPSULE_DP), MeasureSpec.EXACTLY),
        )
        return row.measuredWidth
    }

    private fun isNight(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
    }

    private fun centerOnScreen(view: View): Int {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return loc[0] + view.width / 2
    }

    /** Screen rect of the selected slot; the popup arrow anchors on it. */
    fun selectedToolBounds(): android.graphics.Rect? {
        val style = lastSelected ?: return null
        val glyph = glyphs[style] ?: return null
        val loc = IntArray(2)
        glyph.getLocationOnScreen(loc)
        return android.graphics.Rect(loc[0], loc[1], loc[0] + glyph.width, loc[1] + glyph.height)
    }

    private fun circle(icon: Int, tap: () -> Unit): IconButton =
        IconButton(context).apply {
            iconRes = icon
            onTap = tap
            contentDescription = resources.getResourceEntryName(icon)
        }

    // ── dragging ───────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) return true
                if (abs(ev.rawX - downX) > 8 * density || abs(ev.rawY - downY) > 8 * density) {
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
                if (!dragging) return false
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

    private fun dp(value: Int): Int = (value * density).toInt()

    // ── swatches ───────────────────────────────────────────────────────────

    private inner class Swatch(val color: Int) : View(context) {
        var checked = false
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val radius = 15.34f * density

        init {
            isClickable = true
            foreground = Ripple.circle(context)
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(cx, cy, radius, paint)
            if (checked) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.25f * density
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius * 0.76f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /** Fourth swatch. Mi Canvas keeps the colour you last picked here; otherwise it is rainbow. */
    private inner class CustomSwatch : View(context) {
        var onTap: (() -> Unit)? = null
        var color: Int = 0
            set(value) { field = value; invalidate() }
        var checked = false
            set(value) { field = value; invalidate() }
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
            foreground = Ripple.circle(context)
            setOnClickListener { onTap?.invoke() }
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = 15.34f * density
            if (color != 0) {
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.color = color
                canvas.drawCircle(cx, cy, radius, paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = radius * 0.60f
                paint.shader = SweepGradient(cx, cy, colors, null)
                canvas.drawCircle(cx, cy, radius * 0.68f, paint)
                paint.shader = null
                paint.style = Paint.Style.FILL
            }
            if (checked) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.25f * density
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius * 0.76f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    companion object {
        /**
         * Mi Canvas' tool slot is 124dp long and 48dp thick (68dp for the
         * eraser): the pen artwork is 48x123dp at xxhdpi, so a 124dp slot shows
         * it at native size. Using 77dp scaled the whole brush to 63 %.
         */
        const val PEN_WIDTH_DP = 48
        const val LARGE_WIDTH_DP = 68
        const val PEN_HEIGHT_DP = 124
        /** Every tool rests this far outside the capsule (Mi Canvas: 18dp). */
        const val OUTWARD_DP = 18
        /** The capsule is the visible window; it clips the seated tools' tails. */
        const val CAPSULE_DP = 124
        /**
         * Window = capsule. A seated tool is pushed OUTWARD_DP down, so its tail is
         * cut off at the window edge instead of visibly shortening the artwork.
         */
        const val WINDOW_HEIGHT_DP = CAPSULE_DP
        const val BAR_HEIGHT_DP = WINDOW_HEIGHT_DP
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}

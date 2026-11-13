package com.globalannotate.app.toolbar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
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
import com.globalannotate.app.widget.BrushGlyph
import com.globalannotate.app.widget.BrushPreview
import com.globalannotate.app.widget.ColorStrip
import com.globalannotate.app.widget.IconButton
import com.globalannotate.app.widget.SwatchView
import kotlin.math.min

/**
 * Mi Canvas-style bottom capsule: one row of circular history buttons,
 * painterly brush glyphs, four large colour dots and a rainbow wheel.
 * Width / pressure / full palette / export live in the "more" panel, hidden
 * by default so the main bar stays quiet and text-free.
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
        fun onCollapse()
    }

    private val density = resources.displayMetrics.density
    private val glyphs = mutableMapOf<PenStyle, BrushGlyph>()
    private val quickSwatches = mutableListOf<SwatchView>()
    private val colorStrip: ColorStrip
    private val preview = BrushPreview(context)
    private val widthSeek: SeekBar
    private val widthValue: TextView
    private val pressureButton: IconButton
    private val undoButton: IconButton
    private val redoButton: IconButton
    private val modeButton: IconButton
    private val morePanel: LinearLayout
    private var updating = false

    private val styles = listOf(
        PenStyle.PEN, PenStyle.PENCIL, PenStyle.MARKER, PenStyle.HIGHLIGHTER,
        PenStyle.LASER, PenStyle.ERASER_PIXEL, PenStyle.ERASER_STROKE,
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
            setOnClickListener { listener.onCollapse() }
            background = GradientDrawable().apply {
                setColor(Color.argb(90, 255, 255, 255))
                cornerRadius = dp(2)
            }
            layoutParams = LinearLayout.LayoutParams(dp(38).toInt(), dp(4).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(6).toInt()
            }
        }
        addView(handle)

        // ── main row ────────────────────────────────────────────────────────
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
                setOnClickListener { listener.onStyle(style) }
                layoutParams = LinearLayout.LayoutParams(dp(46).toInt(), dp(84).toInt())
                translationY = dp(8)
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
        val rainbow = RainbowSwatch(context).apply {
            onTap = { morePanel.visibility = if (morePanel.visibility == VISIBLE) GONE else VISIBLE }
            layoutParams = LinearLayout.LayoutParams(dp(36).toInt(), dp(36).toInt())
        }
        row.addView(rainbow)
        row.addView(space(dp(6)))

        val moreButton = circle(R.drawable.ic_more) {
            morePanel.visibility = if (morePanel.visibility == VISIBLE) GONE else VISIBLE
        }
        row.addView(moreButton)
        addView(scroll(row))

        // ── more panel ───────────────────────────────────────────────────────
        morePanel = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            setPadding(0, dp(10).toInt(), 0, 0)
        }

        val widthRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        widthRow.addView(label("粗细"))
        widthSeek = SeekBar(context).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Color.parseColor("#FF5B52"))
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, value: Int, fromUser: Boolean) {
                    if (!updating && fromUser) listener.onWidth(1f + value / 100f * 59f)
                }
                override fun onStartTrackingTouch(seek: SeekBar) = Unit
                override fun onStopTrackingTouch(seek: SeekBar) = Unit
            })
            layoutParams = LinearLayout.LayoutParams(dp(140).toInt(), dp(28).toInt())
        }
        widthRow.addView(widthSeek)
        widthValue = label("3")
        widthRow.addView(widthValue)
        pressureButton = circle(R.drawable.ic_pressure) {
            listener.onPressure(!pressureButton.checked)
        }
        widthRow.addView(pressureButton)
        widthRow.addView(preview, LinearLayout.LayoutParams(dp(60).toInt(), dp(28).toInt()))
        morePanel.addView(widthRow)

        colorStrip = ColorStrip(context, { listener.onColor(it) }, { listener.onPipette() })
        morePanel.addView(scroll(colorStrip))

        val actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeButton = circle(R.drawable.ic_layers) { listener.onCycleMode() }
        actionRow.addView(modeButton)
        actionRow.addView(circle(R.drawable.ic_image) { listener.onSavePng() })
        actionRow.addView(circle(R.drawable.ic_pdf) { listener.onSavePdf() })
        actionRow.addView(circle(R.drawable.ic_trash) { listener.onClear() })
        actionRow.addView(circle(R.drawable.ic_close) { listener.onQuit() })
        morePanel.addView(scroll(actionRow))

        addView(morePanel)
    }

    fun update(brush: BrushConfig, mode: OverlayMode, canUndo: Boolean, canRedo: Boolean) {
        updating = true
        glyphs.forEach { (style, glyph) ->
            glyph.style = style
            glyph.color = brush.color
            glyph.checked = style == brush.style
            glyph.animate().translationY(if (glyph.checked) -dp(6) else dp(8)).setDuration(160).start()
        }
        quickSwatches.forEach { it.checked = it.color == brush.color }
        colorStrip.setColor(brush.color)
        preview.brush = brush
        widthSeek.progress = (((brush.widthDp - 1f) / 59f) * 100f).toInt().coerceIn(0, 100)
        widthValue.text = brush.widthDp.toInt().toString()
        pressureButton.checked = brush.pressureEnabled
        undoButton.enabledVisual = canUndo
        redoButton.enabledVisual = canRedo
        modeButton.checked = mode == OverlayMode.ROOT_SPLIT
        updating = false
        refreshLabels()
    }

    private fun refreshLabels() {
        // Text colours are resolved here so label() can stay tiny.
        for (i in 0 until morePanel.childCount) {
            val child = morePanel.getChildAt(i)
            if (child is TextView) child.setTextColor(Color.parseColor("#C9CDD4"))
        }
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

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#C9CDD4"))
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(28).toInt(),
        ).apply { marginStart = dp(4).toInt(); marginEnd = dp(4).toInt() }
    }

    private fun dp(value: Int): Float = value * density

    /** Rainbow colour wheel entry, matching Mi Canvas's fourth colour dot. */
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
            val r = min(width, height) * 0.36f
            paint.shader = SweepGradient(cx, cy, colors, null)
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
        }
    }
}

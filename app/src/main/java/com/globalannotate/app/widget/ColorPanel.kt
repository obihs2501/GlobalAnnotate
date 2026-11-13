package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import com.globalannotate.app.R

/** 颜色 panel: tabs, 12x8 hue grid / hue strip, opacity checker slider, current colour. */
class ColorPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onColor(color: Int)
        fun onOpacity(alpha: Int)
        fun onPipette()
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val grid: HueGrid
    private val hueBar: HueBar
    private val opacityBar: TrackSlider
    private val chip: ColorChip
    private val gridTab: TextView
    private val sliderTab: TextView
    private val gridContainer: LinearLayout
    private val sliderContainer: LinearLayout
    private val opacityValue: TextView

    var color: Int = Color.parseColor("#D93025")
        set(value) {
            field = value
            grid.selectedColor = value
            chip.color = value
            opacityBar.gradientTo = value
            opacityBar.invalidate()
        }
    var opacity: Int = 255
        set(value) { field = value; opacityBar.progress = value / 255f; opacityValue.text = "${value * 100 / 255}%" }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        setPadding(dp(20).toInt(), dp(16).toInt(), dp(20).toInt(), dp(20).toInt())

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "颜色"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(40).toInt(), 1f)
        }
        titleRow.addView(title)
        titleRow.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_pipette
            onTap = { listener.onPipette() }
            layoutParams = LinearLayout.LayoutParams(dp(38).toInt(), dp(38).toInt())
        })
        titleRow.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_close
            onTap = { listener.onClose() }
            layoutParams = LinearLayout.LayoutParams(dp(38).toInt(), dp(38).toInt())
        })
        addView(titleRow)

        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = dp(18)
            }
            setPadding(dp(4).toInt(), dp(4).toInt(), dp(4).toInt(), dp(4).toInt())
        }
        gridTab = tab("网格取色", true) { selectTab(true) }
        sliderTab = tab("滑块取色", false) { selectTab(false) }
        tabRow.addView(gridTab)
        tabRow.addView(sliderTab)
        addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40).toInt()).apply { topMargin = dp(12).toInt() })

        addView(sectionLabel("色相"), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(14).toInt() })

        gridContainer = LinearLayout(context).apply { orientation = VERTICAL }
        grid = HueGrid(context) { picked ->
            color = picked
            listener.onColor(Color.argb(opacity, Color.red(picked), Color.green(picked), Color.blue(picked)))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(260).toInt())
        }
        gridContainer.addView(grid)
        addView(gridContainer)

        sliderContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
        }
        hueBar = HueBar(context) { picked ->
            color = picked
            listener.onColor(Color.argb(opacity, Color.red(picked), Color.green(picked), Color.blue(picked)))
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40).toInt()) }
        sliderContainer.addView(hueBar)
        sliderContainer.addView(HueGrid(context) { picked ->
            color = picked
            listener.onColor(Color.argb(opacity, Color.red(picked), Color.green(picked), Color.blue(picked)))
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(220).toInt()) })
        addView(sliderContainer)

        val opacityRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        opacityRow.addView(sectionLabel("不透明度"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        opacityValue = sectionLabel("100%")
        opacityRow.addView(opacityValue)
        addView(opacityRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14).toInt() })

        opacityBar = TrackSlider(context).apply {
            checkered = true
            knobFilled = true
            gradientFrom = Color.TRANSPARENT
            gradientTo = color
            progress = 1f
            onProgress = { p ->
                opacity = (p * 255f).toInt().coerceIn(0, 255)
                listener.onOpacity(opacity)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(30).toInt())
        }
        addView(opacityBar)

        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chip = ColorChip(context, color).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64).toInt(), dp(64).toInt())
        }
        bottom.addView(chip)
        val presets = listOf(color, Color.WHITE, Color.BLACK)
        for ((i, c) in presets.withIndex()) {
            val s = SwatchView(context, c).apply {
                checked = i == 0
                setOnClickListener {
                    this@ColorPanel.color = c
                    listener.onColor(Color.argb(opacity, Color.red(c), Color.green(c), Color.blue(c)))
                }
                layoutParams = LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt()).apply {
                    marginStart = dp(12).toInt()
                }
            }
            bottom.addView(s)
        }
        addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(18).toInt() })
    }

    private fun selectTab(gridSelected: Boolean) {
        gridTab.setTabSelected(gridSelected)
        sliderTab.setTabSelected(!gridSelected)
        gridContainer.visibility = if (gridSelected) VISIBLE else GONE
        sliderContainer.visibility = if (gridSelected) GONE else VISIBLE
    }

    private fun tab(text: String, selected: Boolean, click: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnClickListener { click() }
            setTabSelected(selected)
        }

    private fun TextView.setTabSelected(selected: Boolean) {
        background = GradientDrawable().apply {
            setColor(if (selected) Color.parseColor("#4A4A4E") else Color.TRANSPARENT)
            cornerRadius = dp(16)
        }
    }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#C9CDD4"))
    }

    private fun dp(value: Int): Float = value * density

    private class ColorChip(context: Context, var color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun onDraw(canvas: Canvas) {
            paint.color = color
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, width * 0.18f, width * 0.18f, paint)
        }
    }
}

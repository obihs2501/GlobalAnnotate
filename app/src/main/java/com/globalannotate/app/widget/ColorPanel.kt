package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Colour popup, structure copied from Mi Canvas
 * `penpicker_popup_color_picker.xml`: 20dp title row with a 44dp straw button,
 * a FilterSortView tab pair, 330x230 grid, then the alpha slider with a
 * checkerboard and a current-colour block plus quick swatches.
 * Popup size 360x571dp with 36dp corners, per penpicker dimens.
 */
class ColorPanel(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onColor(color: Int)
        fun onOpacity(alpha: Int)
        fun onPipette()
        fun onClose()
    }

    private val density = resources.displayMetrics.density
    private val grid: HueGrid
    private val opacityBar: TrackSlider
    private val chip: ColorChip
    private val gridTab: TextView
    private val sliderTab: TextView
    private val gridContainer: LinearLayout
    private val opacityValue: TextView
    private val quickSwatches = mutableListOf<SwatchView>()

    var color: Int = Color.parseColor("#D93025")
        set(value) {
            field = value
            grid.selectedColor = value
            chip.color = value
            opacityBar.gradientTo = value
            opacityBar.invalidate()
            quickSwatches.forEach { it.checked = it.color == value }
        }

    var opacity: Int = 255
        set(value) {
            field = value
            opacityBar.progress = value / 255f
            opacityValue.text = "${value * 100 / 255}%"
        }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_panel)
        elevation = dp(8)
        layoutParams = LayoutParams(dp(360).toInt(), dp(571).toInt())

        // Title row: 20dp top/bottom margin, straw button aligned to the end.
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20).toInt(), dp(20).toInt(), dp(20).toInt(), dp(12).toInt())
        }
        titleRow.addView(TextView(context).apply {
            text = "颜色"
            textSize = 20f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.ga_text_title))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        titleRow.addView(IconButton(context).apply {
            iconRes = R.drawable.ic_pipette
            onTap = { listener.onPipette() }
            layoutParams = LinearLayout.LayoutParams(dp(44).toInt(), dp(44).toInt())
        })
        addView(titleRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Tabs
        val tabRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.ga_tab_bg))
                cornerRadius = dp(20)
            }
            setPadding(dp(3).toInt(), dp(3).toInt(), dp(3).toInt(), dp(3).toInt())
        }
        gridTab = tab("网格取色", true) { selectTab(true) }
        sliderTab = tab("滑块取色", false) { selectTab(false) }
        tabRow.addView(gridTab)
        tabRow.addView(sliderTab)
        addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40).toInt()).apply {
            leftMargin = dp(20).toInt()
            rightMargin = dp(20).toInt()
        })

        addView(bodyText("色相").apply {
            setPadding(dp(25).toInt(), dp(14).toInt(), 0, 0)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        gridContainer = LinearLayout(context).apply { orientation = VERTICAL }
        grid = HueGrid(context) { picked ->
            color = picked
            listener.onColor(Color.argb(opacity, Color.red(picked), Color.green(picked), Color.blue(picked)))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(330).toInt(), dp(230).toInt()).apply {
                topMargin = dp(3).toInt()
            }
        }
        gridContainer.addView(grid, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dp(25).toInt()
        })
        addView(gridContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Opacity
        val opacityRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(25).toInt(), dp(16).toInt(), dp(25).toInt(), 0)
        }
        opacityRow.addView(bodyText("不透明度").apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        opacityValue = bodyText("100%")
        opacityRow.addView(opacityValue)
        addView(opacityRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

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
        }
        addView(opacityBar, LinearLayout.LayoutParams(MATCH_PARENT, dp(26).toInt()).apply {
            topMargin = dp(6).toInt()
            leftMargin = dp(25).toInt()
            rightMargin = dp(25).toInt()
        })

        // Current colour block + quick swatches
        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(25).toInt(), dp(16).toInt(), dp(25).toInt(), 0)
        }
        chip = ColorChip(context, color).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64).toInt(), dp(64).toInt())
        }
        bottom.addView(chip)
        for (c in listOf(color, Color.WHITE, Color.BLACK)) {
            val s = SwatchView(context, c).apply {
                checked = c == color
                setOnClickListener {
                    this@ColorPanel.color = c
                    listener.onColor(Color.argb(opacity, Color.red(c), Color.green(c), Color.blue(c)))
                }
                layoutParams = LinearLayout.LayoutParams(dp(32).toInt(), dp(32).toInt()).apply {
                    marginStart = dp(12).toInt()
                }
            }
            quickSwatches.add(s)
            bottom.addView(s)
        }
        addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(16).toInt()
        })
    }

    private fun selectTab(gridSelected: Boolean) {
        gridTab.setTabSelected(gridSelected)
        sliderTab.setTabSelected(!gridSelected)
        gridContainer.visibility = if (gridSelected) VISIBLE else GONE
    }

    private fun tab(text: String, selected: Boolean, click: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnClickListener { click() }
            setTabSelected(selected)
        }

    private fun TextView.setTabSelected(selected: Boolean) {
        background = GradientDrawable().apply {
            setColor(
                if (selected) ContextCompat.getColor(context, R.color.ga_tab_selected)
                else Color.TRANSPARENT,
            )
            cornerRadius = dp(17)
        }
        setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.ga_text_title else R.color.ga_text_primary,
            ),
        )
    }

    private fun bodyText(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        includeFontPadding = false
        setTextColor(ContextCompat.getColor(context, R.color.ga_text_primary))
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

package com.globalannotate.app.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Rounded track with a ring knob, matching Mi Canvas sliders. */
class TrackSlider(context: Context) : View(context) {

    var progress: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var onProgress: ((Float) -> Unit)? = null
    var knobFilled: Boolean = false
    var checkered: Boolean = false
    var gradientFrom: Int = Color.TRANSPARENT
    var gradientTo: Int = Color.parseColor("#F4511E")

    private val density = resources.displayMetrics.density
    private val rect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.parseColor("#3B87FF")
    }
    private val checkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val trackH = h * 0.52f
        val top = (h - trackH) / 2f
        rect.set(0f, top, w, top + trackH)

        if (checkered) {
            val cell = trackH / 2f
            var x = 0f
            while (x < w) {
                checkerPaint.color = if (((x / cell).toInt() % 2) == 0) {
                    Color.parseColor("#9A9A9A")
                } else Color.parseColor("#D0D0D0")
                canvas.drawRect(x, top, (x + cell).coerceAtMost(w), top + cell, checkerPaint)
                checkerPaint.color = if (((x / cell).toInt() % 2) == 0) {
                    Color.parseColor("#D0D0D0")
                } else Color.parseColor("#9A9A9A")
                canvas.drawRect(x, top + cell, (x + cell).coerceAtMost(w), top + trackH, checkerPaint)
                x += cell
            }
            trackPaint.shader = LinearGradient(
                0f, 0f, w, 0f, gradientFrom, gradientTo, Shader.TileMode.CLAMP,
            )
        } else {
            trackPaint.shader = null
            trackPaint.color = Color.parseColor("#4A4A4E")
        }
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint)

        val cx = w * progress
        val radius = trackH * 0.44f
        if (knobFilled) {
            knobPaint.color = Color.WHITE
            knobPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, h / 2f, radius, knobPaint)
        }
        canvas.drawCircle(cx, h / 2f, radius, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            progress = (event.x / width).coerceIn(0f, 1f)
            onProgress?.invoke(progress)
            invalidate()
        }
        return true
    }
}

/** 12 x 8 hue/value matrix used by the 网格取色 tab. */
class HueGrid(context: Context, private val onPick: (Int) -> Unit) : View(context) {

    var selectedColor: Int? = null
        set(value) { field = value; invalidate() }

    private val cols = 12
    private val rows = 8
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val cell = RectF()

    fun colorAt(col: Int, row: Int): Int {
        val hue = col * 360f / cols
        val t = row / (rows - 1f)
        val saturation = 0.10f + t * 0.86f
        val value = 1f - t * 0.52f
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = colorAt(c, r)
                paint.color = color
                paint.style = Paint.Style.FILL
                cell.set(c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)
                canvas.drawRect(cell, paint)
                if (selectedColor == color) canvas.drawRect(cell, outline)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            val c = (event.x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
            val r = (event.y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            val picked = colorAt(c, r)
            selectedColor = picked
            invalidate()
            onPick(picked)
        }
        return true
    }
}

/** Horizontal hue strip for the 滑块取色 tab. */
class HueBar(context: Context, private val onPick: (Int) -> Unit) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
    }
    var hue: Float = 0f
        set(value) { field = value.coerceIn(0f, 360f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        val colors = IntArray(13) { i -> Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)) }
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(
            RectF(0f, height * 0.25f, width.toFloat(), height * 0.75f),
            height * 0.25f, height * 0.25f, paint,
        )
        paint.shader = null
        val x = width * (hue / 360f)
        canvas.drawCircle(x, height / 2f, height * 0.35f, knob)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE
        ) {
            hue = (event.x / width) * 360f
            onPick(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        }
        return true
    }
}

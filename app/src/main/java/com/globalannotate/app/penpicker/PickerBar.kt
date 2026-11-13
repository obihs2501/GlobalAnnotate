package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Shared slider used by every picker page. Geometry comes from Mi Canvas:
 * 12dp rounded track, 28dp round thumb with a 3dp accent ring, touch height 28dp.
 */
open class PickerBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onProgress: ((Float) -> Unit)? = null

    /** Base colour the bar builds its gradient from (set by the panel). */
    var baseColor: Int = Color.parseColor("#3482FF")
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val rect = RectF()
    protected val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val checkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Override to paint the track; default is the flat property track. */
    open fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        trackPaint.shader = null
        trackPaint.color = if (isNight()) Color.parseColor("#333333") else Color.parseColor("#E6E6E6")
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }

    /** Override for a coloured thumb (alpha page uses the current colour). */
    open fun thumbColor(): Int = Color.WHITE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The Mi Canvas layouts give sliders wrap_content; without this they
        // measured to zero and the whole opacity row vanished.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (28 * resources.displayMetrics.density).toInt()
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            desired
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val trackH = h * 0.43f
        val top = (h - trackH) / 2f
        rect.set(0f, top, width.toFloat(), top + trackH)
        drawTrack(canvas, rect, trackH / 2f)

        val cx = width * progress
        val radius = trackH * 0.95f
        thumbPaint.color = thumbColor()
        canvas.drawCircle(cx, h / 2f, radius, thumbPaint)
        ringPaint.color = if (isNight() && thumbColor() == Color.WHITE) {
            ContextCompat.getColor(context, R.color.ga_accent)
        } else {
            ContextCompat.getColor(context, R.color.ga_accent)
        }
        canvas.drawCircle(cx, h / 2f, radius, ringPaint)
    }

    protected fun horizontalGradient(vararg colors: Int): Shader =
        LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)

    protected fun drawChecker(canvas: Canvas, track: RectF, cell: Float) {
        var x = track.left
        while (x < track.right) {
            val even = ((x - track.left) / cell).toInt() % 2 == 0
            checkerPaint.color = if (even) Color.parseColor("#9A9A9A") else Color.parseColor("#D0D0D0")
            canvas.drawRect(x, track.top, (x + cell).coerceAtMost(track.right), track.top + cell, checkerPaint)
            checkerPaint.color = if (even) Color.parseColor("#D0D0D0") else Color.parseColor("#9A9A9A")
            canvas.drawRect(x, track.top + cell, (x + cell).coerceAtMost(track.right), track.bottom, checkerPaint)
            x += cell
        }
    }

    protected fun isNight(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                progress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                onProgress?.invoke(progress)
                return true
            }
            MotionEvent.ACTION_UP -> {
                progress = (event.x / width.toFloat()).coerceIn(0f, 1f)
                onProgress?.invoke(progress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

package com.globalannotate.app.penpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.globalannotate.app.R

/**
 * Mi Canvas `SeekBarBaseView` (`I1llI1`): a 12dp rounded track, a 26dp thumb
 * drawn as a ring on top of it, and a value that follows the touch 1:1.
 *
 *  - property sliders use the blue `#3482FF` ring
 *  - colour sliders use the white ring, so the colour shows through
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

    /** Called continuously while dragging and once on release. */
    var onProgress: ((Float) -> Unit)? = null

    /** Called once the finger lifts: use it for one-shot actions. */
    var onCommit: ((Float) -> Unit)? = null

    var max: Int = 255
    var value: Int
        get() = (progress * max).toInt()
        set(v) { progress = if (max == 0) 0f else v.toFloat() / max }

    /** Base colour for gradients and the alpha checker. */
    var baseColor: Int = Color.parseColor("#E8464A")
        set(value) { field = value; invalidate() }

    protected val density = resources.displayMetrics.density
    protected val trackRect = RectF()
    protected val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val checkerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val trackHeight get() = 12f * density
    private val thumbRadius get() = 13f * density
    private val ringWidth get() = 2.5f * density

    /** Override to paint the track; the default is the neutral `pk_property_seekbar_bg`. */
    protected open fun drawTrack(canvas: Canvas, track: RectF, radius: Float) {
        trackPaint.shader = null
        trackPaint.color = ContextCompat.getColor(context, R.color.pk_property_seekbar_bg)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
    }

    /** Override for a coloured thumb centre (the alpha slider shows the colour). */
    protected open fun thumbCentreColor(): Int =
        if (isNight()) Color.parseColor("#434343") else Color.parseColor("#EDEDED")

    protected open fun ringColor(): Int = Color.parseColor("#3482FF")

    protected open fun isNight(): Boolean {
        val bg = ContextCompat.getColor(context, R.color.penpicker_background_color)
        val lum = 0.299f * Color.red(bg) + 0.587f * Color.green(bg) + 0.114f * Color.blue(bg)
        return lum < 128f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The Mi Canvas layouts ask for wrap_content and 26dp in practice; a
        // raw View would measure to zero and the row would vanish.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (thumbRadius * 2f).toInt()
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else desired
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val th = trackHeight
        val cy = h / 2f
        val r = thumbRadius
        trackRect.set(r, cy - th / 2f, width - r, cy + th / 2f)
        if (trackRect.right > trackRect.left) drawTrack(canvas, trackRect, th / 2f)

        val cx = r + progress * (width - 2f * r)
        thumbPaint.color = thumbCentreColor()
        canvas.drawCircle(cx, cy, r - ringWidth / 2f, thumbPaint)
        ringPaint.strokeWidth = ringWidth
        ringPaint.color = ringColor()
        canvas.drawCircle(cx, cy, r - ringWidth / 2f, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                update(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                update(event.x)
                onCommit?.invoke(progress)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun update(x: Float) {
        val r = thumbRadius
        progress = if (width <= 2 * r) 0f else (x - r) / (width - 2 * r)
        onProgress?.invoke(progress)
    }

    protected fun horizontalGradient(vararg colors: Int) =
        android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, android.graphics.Shader.TileMode.CLAMP)

    /** Checkerboard, the Miuix way of showing transparency. */
    protected fun drawChecker(canvas: Canvas, track: RectF, radius: Float) {
        val cell = track.height() / 2f
        val saved = canvas.save()
        val path = android.graphics.Path().apply {
            addRoundRect(track, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        var x = track.left
        var i = 0
        while (x < track.right) {
            checkerPaint.color = if (i % 2 == 0) Color.parseColor("#9A9A9A") else Color.parseColor("#D6D6D6")
            canvas.drawRect(x, track.top, (x + cell).coerceAtMost(track.right), track.top + cell, checkerPaint)
            checkerPaint.color = if (i % 2 == 0) Color.parseColor("#D6D6D6") else Color.parseColor("#9A9A9A")
            canvas.drawRect(x, track.top + cell, (x + cell).coerceAtMost(track.right), track.bottom, checkerPaint)
            x += cell
            i++
        }
        canvas.restoreToCount(saved)
    }

    protected fun clearTrack(canvas: Canvas, track: RectF, radius: Float) {
        val saved = canvas.save()
        canvas.clipPath(
            android.graphics.Path().apply {
                addRoundRect(track, radius, radius, android.graphics.Path.Direction.CW)
            },
        )
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.restoreToCount(saved)
    }

    /** Shared helper: detach from a scrolling parent while dragging. */
    protected fun lockParent(lock: Boolean) {
        var p: ViewGroup? = parent as? ViewGroup
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(lock)
            p = p.parent as? ViewGroup
        }
    }
}

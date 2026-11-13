package com.globalannotate.app.ink

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Zero-authorisation replacements for the SDK features a personal developer
 * cannot unlock (no App ID / enterprise account). These are plain geometry and
 * signal processing — no Xiaomi SDK, no native library, no auth call.
 *
 * They are deliberately conservative: recognition only fires on a closed,
 * single stroke that the user held still on; anything ambiguous is left as-is.
 */
object SmartInk {

    data class Point(val x: Float, val y: Float, val width: Float)

    sealed interface Shape {
        data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Shape
        data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) : Shape
        data class Oval(val l: Float, val t: Float, val r: Float, val b: Float) : Shape
        data class Arrow(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Shape
    }

    /** Bounding box + closedness + corner count, used for one-stroke shaping. */
    private data class Metrics(
        val l: Float, val t: Float, val r: Float, val b: Float,
        val firstX: Float, val firstY: Float,
        val lastX: Float, val lastY: Float,
        val pathLength: Float,
        val corners: Int,
    )

    /**
     * Recognise a rough hand-drawn shape. Returns null when the stroke is not a
     * clean closed-ish single gesture, so free writing is never rewritten.
     * Call only after the pen has paused (see README, "one-stroke shaping").
     */
    fun recognize(points: List<Point>): Shape? {
        if (points.size < 8) return null
        val m = metrics(points) ?: return null
        val w = m.r - m.l
        val h = m.b - m.t
        if (w < 24f || h < 24f) return null

        val straightness = hypot(m.lastX - m.firstX, m.lastY - m.firstY) / m.pathLength.coerceAtLeast(1f)
        if (straightness > 0.92f) {
            return Shape.Line(m.firstX, m.firstY, m.lastX, m.lastY)
        }

        val closed = hypot(m.lastX - m.firstX, m.lastY - m.firstY) < max(w, h) * 0.30f
        if (!closed) return null

        return when {
            m.corners in 3..5 && abs(w - h) / max(w, h) < 0.65f ->
                Shape.Rect(m.l, m.t, m.r, m.b)
            m.corners <= 2 ->
                Shape.Oval(m.l, m.t, m.r, m.b)
            else -> null
        }
    }

    /** Replace a recognised stroke with its idealised point list. */
    fun idealise(shape: Shape, width: Float): List<Point> = when (shape) {
        is Shape.Line -> listOf(
            Point(shape.x1, shape.y1, width),
            Point(shape.x2, shape.y2, width),
        )
        is Shape.Rect -> listOf(
            Point(shape.l, shape.t, width), Point(shape.r, shape.t, width),
            Point(shape.r, shape.b, width), Point(shape.l, shape.b, width),
            Point(shape.l, shape.t, width),
        )
        is Shape.Oval -> {
            val cx = (shape.l + shape.r) / 2f
            val cy = (shape.t + shape.b) / 2f
            val rx = (shape.r - shape.l) / 2f
            val ry = (shape.b - shape.t) / 2f
            (0..48).map { i ->
                val a = (i / 48f) * (2.0 * Math.PI)
                Point((cx + rx * kotlin.math.cos(a)).toFloat(), (cy + ry * kotlin.math.sin(a)).toFloat(), width)
            }
        }
        is Shape.Arrow -> listOf(
            Point(shape.x1, shape.y1, width),
            Point(shape.x2, shape.y2, width),
        )
    }

    /**
     * Lightweight stroke prediction: extrapolate the last few samples with a
     * damped velocity. Returns a short ghost segment, or null when the motion is
     * too noisy to be useful. This is NOT the SDK's estimate.hdb predictor.
     */
    fun predict(points: List<Point>, lookback: Int = 4, gain: Float = 0.45f): Point? {
        if (points.size < lookback + 1) return null
        val tail = points.takeLast(lookback + 1)
        var vx = 0f
        var vy = 0f
        for (i in 1 until tail.size) {
            vx += tail[i].x - tail[i - 1].x
            vy += tail[i].y - tail[i - 1].y
        }
        vx /= lookback
        vy /= lookback
        val speed = hypot(vx, vy)
        if (speed < 0.5f || speed > 60f) return null
        val last = tail.last()
        return Point(last.x + vx * gain, last.y + vy * gain, last.width)
    }

    private fun metrics(points: List<Point>): Metrics? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var path = 0f
        var prevX = points[0].x
        var prevY = points[0].y
        for (p in points) {
            l = min(l, p.x); r = max(r, p.x)
            t = min(t, p.y); b = max(b, p.y)
            path += hypot(p.x - prevX, p.y - prevY)
            prevX = p.x; prevY = p.y
        }
        if (path <= 0f) return null
        return Metrics(l, t, r, b, points.first().x, points.first().y,
            points.last().x, points.last().y, path, countCorners(points))
    }

    /** Counts significant direction changes above a noise threshold. */
    private fun countCorners(points: List<Point>): Int {
        var corners = 0
        var lastAngle: Float? = null
        var i = 0
        val step = max(1, points.size / 40)
        while (i + step < points.size) {
            val a = points[i]
            val b = points[i + step]
            val angle = Math.toDegrees(
                kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()),
            ).toFloat()
            lastAngle?.let { prev ->
                var delta = abs(angle - prev)
                if (delta > 180f) delta = 360f - delta
                if (delta > 55f) corners++
            }
            lastAngle = angle
            i += step
        }
        return corners
    }

    /** Reads a pixel from a root `screencap` bitmap — zero-auth cross-app picking. */
    fun pickColor(screen: android.graphics.Bitmap, x: Int, y: Int): Int =
        screen.getPixel(x.coerceIn(0, screen.width - 1), y.coerceIn(0, screen.height - 1))

    /** Standard AXIS_ORIENTATION substitute for Xiaomi's private posture API. */
    fun orientationDegrees(event: android.view.MotionEvent): Float =
        event.getAxisValue(android.view.MotionEvent.AXIS_ORIENTATION, 0)

    /** Standard tilt substitute, useful for brush-shape modulation. */
    fun tiltDegrees(event: android.view.MotionEvent): Float =
        Math.toDegrees(event.getAxisValue(android.view.MotionEvent.AXIS_TILT, 0).toDouble()).toFloat()

    /** Unused import guard kept explicit for readability. */
    private fun unusedGuard(): Float = sqrt(4f)
}

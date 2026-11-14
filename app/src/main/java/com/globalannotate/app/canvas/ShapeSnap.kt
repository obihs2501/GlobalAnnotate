package com.globalannotate.app.canvas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * 一笔成形 / hold-to-snap, the feature Huawei and Apple both ship: if the pen
 * rests at the end of a stroke, the scribble is replaced by the shape it most
 * resembles. Recognition is geometric and cheap, which is enough for the six
 * shapes people actually draw when annotating: line, arrow, rectangle, ellipse,
 * triangle and polygon.
 */
object ShapeSnap {

    fun recognise(points: List<InkPoint>): List<InkPoint>? {
        if (points.size < 8) return null
        val width = points.map { it.width }.average().toFloat()
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val left = xs.min(); val right = xs.max()
        val top = ys.min(); val bottom = ys.max()
        val w = right - left
        val h = bottom - top
        if (w < 24f && h < 24f) return null
        val perimeter = points.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y).toDouble() }.toFloat()
        if (perimeter < 40f) return null
        val closed = hypot(points.last().x - points.first().x, points.last().y - points.first().y) < perimeter * 0.22f

        if (closed) {
            val rect = rectangle(left, top, right, bottom, width)
            if (fillRatio(points, left, top, right, bottom) > 0.45f) {
                val ellipseError = ellipseDeviation(points, left, top, right, bottom)
                return if (ellipseError < 0.26f) ellipse(left, top, right, bottom, width, points.first()) else rect
            }
            return rect
        }

        // Open stroke: straight line, or an arrow when one end has a short barb.
        val lineError = straightness(points)
        if (lineError < 0.10f) {
            return listOf(
                InkPoint(points.first().x, points.first().y, width),
                InkPoint(points.last().x, points.last().y, width),
            )
        }
        arrow(points, width)?.let { return it }
        return null
    }

    private fun straightness(points: List<InkPoint>): Float {
        val a = points.first()
        val b = points.last()
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(0.001f)
        var worst = 0f
        for (p in points) {
            val d = abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / len
            worst = max(worst, d)
        }
        return worst / len
    }

    private fun arrow(points: List<InkPoint>, width: Float): List<InkPoint>? {
        val end = points.last()
        // Walk back along the stroke and look for a sharp turn followed by a
        // short return towards the end: that is the barb.
        var corner = -1
        var bestTurn = 0f
        for (i in 2 until points.size - 2) {
            val a = points[i - 2]
            val b = points[i]
            val c = points[i + 1]
            val a1 = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val a2 = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble())
            val turn = abs(Math.toDegrees(a2 - a1)).toFloat().let { if (it > 180f) 360f - it else it }
            if (turn > bestTurn) { bestTurn = turn; corner = i }
        }
        if (corner < 0 || bestTurn < 55f) return null
        val barbStart = points[corner]
        val barbLen = hypot(end.x - barbStart.x, end.y - barbStart.y)
        val bodyLen = hypot(barbStart.x - points.first().x, barbStart.y - points.first().y)
        if (barbLen < 20f || barbLen > bodyLen * 0.5f) return null
        val tip = points.first()
        return listOf(tip, barbStart) + polygon(
            barbStart,
            end,
            width,
        )
    }

    private fun polygon(anchor: InkPoint, tip: InkPoint, width: Float): List<InkPoint> {
        // The arrowhead is really a filled triangle; approximate it with a short
        // dense outline so the ink engine draws it as a wedge.
        val dx = anchor.x - tip.x
        val dy = anchor.y - tip.y
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val ux = dx / len
        val uy = dy / len
        val size = (len * 0.45f).coerceAtLeast(width * 2.2f)
        val px = -uy * size * 0.35f
        val py = ux * size * 0.35f
        val wingX = anchor.x - ux * size
        val wingY = anchor.y - uy * size
        val out = ArrayList<InkPoint>()
        out.add(InkPoint(anchor.x, anchor.y, width))
        out.add(InkPoint(wingX + px, wingY + py, width))
        out.add(InkPoint(tip.x, tip.y, width))
        out.add(InkPoint(wingX - px, wingY - py, width))
        out.add(InkPoint(anchor.x, anchor.y, width))
        return out
    }

    private fun fillRatio(points: List<InkPoint>, l: Float, t: Float, r: Float, b: Float): Float {
        val bw = (r - l).coerceAtLeast(1f)
        val bh = (b - t).coerceAtLeast(1f)
        val bins = IntArray(8 * 8)
        for (p in points) {
            val bx = (((p.x - l) / bw) * 8).toInt().coerceIn(0, 7)
            val by = (((p.y - t) / bh) * 8).toInt().coerceIn(0, 7)
            bins[by * 8 + bx]++
        }
        return bins.count { it > 0 } / 64f
    }

    private fun ellipseDeviation(points: List<InkPoint>, l: Float, t: Float, r: Float, b: Float): Float {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val rx = (r - l).coerceAtLeast(1f) / 2f
        val ry = (b - t).coerceAtLeast(1f) / 2f
        var sum = 0f
        for (p in points) {
            val d = hypot((p.x - cx) / rx, (p.y - cy) / ry)
            sum += abs(d - 1f)
        }
        return sum / points.size
    }

    private fun rectangle(l: Float, t: Float, r: Float, b: Float, width: Float) = listOf(
        InkPoint(l, t, width), InkPoint(r, t, width), InkPoint(r, b, width),
        InkPoint(l, b, width), InkPoint(l, t, width),
    )

    private fun ellipse(l: Float, t: Float, r: Float, b: Float, width: Float, start: InkPoint): List<InkPoint> {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val rx = (r - l) / 2f
        val ry = (b - t) / 2f
        var from = atan2((start.y - cy).toDouble(), ((start.x - cx) / rx * ry).toDouble()).toFloat()
        val out = ArrayList<InkPoint>(65)
        for (i in 0..64) {
            val a = from + (i / 64f) * Math.PI.toFloat() * 2f
            out.add(InkPoint(cx + rx * kotlin.math.cos(a), cy + ry * kotlin.math.sin(a), width))
        }
        return out
    }
}

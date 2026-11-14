package com.globalannotate.app.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.globalannotate.app.model.PenStyle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Paints [InkStroke]s. Widths come from the pen pressure and the brush settings;
 * each brush has its own texture so the six tools feel different, which was the
 * point of the exercise:
 *
 *  圆珠笔  hard thin line, width barely reacts to pressure
 *  铅笔    grainy graphite stamps, tilt widens the contact patch
 *  钢笔    smooth ribbon, width and taper follow pressure
 *  马克笔  flat angled nib, translucent, overlaps darken
 *  水彩笔  mottled wash with a soft bleed edge
 *  橡皮    CLEAR round stamps (pixel) or whole-stroke removal
 */
class InkEngine {

    private var grain: Bitmap? = null
    private var wash: Bitmap? = null

    private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    private fun grain(density: Float): Bitmap {
        grain?.let { return it }
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        val px = IntArray(n * n)
        for (i in px.indices) {
            val v = random.nextDouble()
            val a = when {
                v < 0.55 -> 0
                v < 0.95 -> ((v - 0.55) / 0.40 * 235).toInt()
                else -> 255
            }
            px[i] = Color.argb(a, 255, 255, 255)
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        grain = bmp
        return bmp
    }

    private fun wash(): Bitmap {
        wash?.let { return it }
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(11)
        val px = IntArray(n * n)
        // Blotchy alpha: a base wash plus darker pooled spots.
        val seeds = Array(26) { Triple(random.nextFloat() * n, random.nextFloat() * n, 6 + random.nextFloat() * 18) }
        for (y in 0 until n) for (x in 0 until n) {
            var a = 150 + random.nextInt(40)
            for (s in seeds) {
                val d = hypot(x - s.first, y - s.second)
                if (d < s.third) a += ((1f - d / s.third) * 70).toInt()
            }
            px[y * n + x] = Color.argb(a.coerceIn(0, 255), 255, 255, 255)
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        wash = bmp
        return bmp
    }

    // ── public ─────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        if (stroke.points.isEmpty()) return
        when (stroke.style) {
            PenStyle.PENCIL -> pencil(canvas, stroke, offsetY)
            PenStyle.WATER_COLOR -> waterColor(canvas, stroke, offsetY)
            PenStyle.BALLPOINT -> ballpoint(canvas, stroke, offsetY)
            PenStyle.MARKER, PenStyle.HIGHLIGHTER -> marker(canvas, stroke, offsetY)
            PenStyle.INK_PEN -> fountain(canvas, stroke, offsetY)
            PenStyle.ERASER -> eraser(canvas, stroke, offsetY)
        }
    }

    /** Fast incremental tail while the pen is still down. */
    fun drawTail(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        if (pts.size == 1) {
            stamp(canvas, stroke, pts[0], pts[0], offsetY, pts[0].width)
            return
        }
        stamp(canvas, stroke, pts[pts.size - 2], pts[pts.size - 1], offsetY, (pts[pts.size - 2].width + pts[pts.size - 1].width) / 2f)
    }

    // ── brushes ────────────────────────────────────────────────────────────

    private fun paintFor(stroke: InkStroke): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (stroke.isEraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.BLACK
        } else {
            color = stroke.color
        }
    }

    private fun eraser(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val paint = paintFor(stroke)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val pts = stroke.points
        if (pts.size == 1) {
            canvas.drawCircle(pts[0].x, pts[0].y - offsetY, pts[0].width / 2f, paint)
            return
        }
        for (i in 1 until pts.size) {
            paint.strokeWidth = (pts[i - 1].width + pts[i].width) / 2f
            canvas.drawLine(pts[i - 1].x, pts[i - 1].y - offsetY, pts[i].x, pts[i].y - offsetY, paint)
        }
    }

    private fun ballpoint(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val pts = stroke.points
        val paint = paintFor(stroke).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = pts.averageWidth()
        }
        canvas.drawPath(smoothPath(pts, offsetY), paint)
        // A hair of sheen down the middle, like wet ballpoint ink.
        paint.color = Color.argb(60, 255, 255, 255)
        paint.strokeWidth = max(paint.strokeWidth * 0.25f, 0.6f)
        canvas.drawPath(smoothPath(pts, offsetY), paint)
    }

    private fun fountain(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val paint = paintFor(stroke)
        val path = ribbon(stroke, offsetY, nibAngle = 0f, taper = true)
        canvas.drawPath(path, paint)
        val pts = stroke.points
        // Round the ends so a dot is a dot, not a wedge.
        canvas.drawCircle(pts.first().x, pts.first().y - offsetY, pts.first().width * 0.17f, paint)
        canvas.drawCircle(pts.last().x, pts.last().y - offsetY, pts.last().width * 0.22f, paint)
    }

    private fun marker(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val paint = paintFor(stroke)
        val tilt = stroke.points.averageTilt()
        canvas.drawPath(ribbon(stroke, offsetY, nibAngle = tilt * 1.2f, taper = false), paint)
    }

    private fun pencil(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val pts = stroke.points
        val bitmap = grain(canvas.densityHint)
        val colorFilter = tint(stroke.color)
        stampPaint.reset()
        stampPaint.isAntiAlias = true
        stampPaint.isFilterBitmap = true
        stampPaint.colorFilter = colorFilter
        var walked = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            var t = 0f
            while (t < d) {
                val f = t / d
                val x = a.x + (b.x - a.x) * f
                val y = a.y + (b.y - a.y) * f
                val w = a.width + (b.width - a.width) * f
                val size = w * (0.85f + 0.3f * noise(i + walked, 3))
                matrix.setScale(size / 96f, size / 96f)
                matrix.postRotate(noise(i + walked, 7) * 360f)
                matrix.postTranslate(x - size / 2f, y - offsetY - size / 2f)
                canvas.drawBitmap(bitmap, matrix, stampPaint)
                t += max(w * 0.22f, 1.1f)
                walked += 1f
            }
        }
        // A faint graphite core keeps the line readable at small sizes.
        val core = paintFor(stroke).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(pts.averageWidth() * 0.30f, 0.7f)
            alpha = (Color.alpha(stroke.color) * 0.5f).toInt()
        }
        canvas.drawPath(smoothPath(pts, offsetY), core)
    }

    private fun waterColor(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val pts = stroke.points
        val bitmap = wash()
        val paint = stampPaint
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.colorFilter = tint(stroke.color)
        val baseAlpha = Color.alpha(stroke.color) / 255f
        var walked = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            var t = 0f
            while (t < d) {
                val f = t / d
                val x = a.x + (b.x - a.x) * f
                val y = a.y + (b.y - a.y) * f
                val w = a.width + (b.width - a.width) * f
                val size = w * (1.25f + 0.25f * noise(i + walked, 5))
                paint.alpha = (255 * baseAlpha * 0.45f).toInt().coerceIn(8, 255)
                matrix.setScale(size / 128f, size / 128f)
                matrix.postRotate(noise(i + walked, 9) * 360f)
                matrix.postTranslate(x - size / 2f, y - offsetY - size / 2f)
                canvas.drawBitmap(bitmap, matrix, paint)
                t += max(w * 0.18f, 1.4f)
                walked += 1f
            }
        }
        // Crisp core so the brush reads like a wash with a wet edge.
        val core = paintFor(stroke).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(pts.averageWidth() * 0.55f, 1f)
            alpha = (Color.alpha(stroke.color) * 0.55f).toInt()
        }
        canvas.drawPath(smoothPath(pts, offsetY), core)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private var densityHint = 2.75f
    fun setDensity(density: Float) { densityHint = density }

    private fun stamp(canvas: Canvas, stroke: InkStroke, a: InkPoint, b: InkPoint, offsetY: Float, width: Float) {
        val paint = paintFor(stroke).apply {
            style = Paint.Style.STROKE
            strokeCap = if (stroke.style.isMarkerLike) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(width, 0.8f)
        }
        if (a === b) {
            canvas.drawCircle(a.x, a.y - offsetY, max(width / 2f, 0.8f), paint)
            return
        }
        canvas.drawLine(a.x, a.y - offsetY, b.x, b.y - offsetY, paint)
    }

    private fun tint(color: Int): ColorFilter {
        val matrix = ColorMatrix()
        matrix.setScale(
            Color.red(color) / 255f,
            Color.green(color) / 255f,
            Color.blue(color) / 255f,
            1f,
        )
        val alpha = Color.alpha(color) / 255f
        matrix.postScale(1f, 1f, 1f, alpha)
        return ColorMatrixColorFilter(matrix)
    }

    /** Catmull-Rom → cubic Bezier, so handwriting has no polyline corners. */
    private fun smoothPath(pts: List<InkPoint>, offsetY: Float): Path {
        val path = Path()
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].x, pts[0].y - offsetY)
        if (pts.size == 1) {
            path.lineTo(pts[0].x + 0.01f, pts[0].y - offsetY)
            return path
        }
        if (pts.size == 2) {
            path.lineTo(pts[1].x, pts[1].y - offsetY)
            return path
        }
        for (i in 0 until pts.size - 1) {
            val p0 = pts[(i - 1).coerceAtLeast(0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[(i + 2).coerceAtMost(pts.size - 1)]
            val c1x = p1.x + (p2.x - p0.x) / 6f
            val c1y = p1.y + (p2.y - p0.y) / 6f
            val c2x = p2.x - (p3.x - p1.x) / 6f
            val c2y = p2.y - (p3.y - p1.y) / 6f
            path.cubicTo(c1x, c1y - offsetY, c2x, c2y - offsetY, p2.x, p2.y - offsetY)
        }
        return path
    }

    /**
     * Variable-width polygon around the centre line. [nibAngle] rotates the
     * offset direction, which is what makes the marker look like a chisel.
     */
    private fun ribbon(stroke: InkStroke, offsetY: Float, nibAngle: Float, taper: Boolean): Path {
        val pts = stroke.points
        val path = Path()
        if (pts.isEmpty()) return path
        if (pts.size == 1) {
            path.addCircle(pts[0].x, pts[0].y - offsetY, pts[0].width / 2f, Path.Direction.CW)
            return path
        }
        val cosA = kotlin.math.cos(nibAngle)
        val sinA = kotlin.math.sin(nibAngle)
        val left = ArrayList<Float>(pts.size * 2)
        val right = ArrayList<Float>(pts.size * 2)
        for (i in pts.indices) {
            val prev = pts[(i - 1).coerceAtLeast(0)]
            val next = pts[(i + 1).coerceAtMost(pts.size - 1)]
            var dx = next.x - prev.x
            var dy = next.y - prev.y
            val len = hypot(dx, dy).coerceAtLeast(0.0001f)
            dx /= len
            dy /= len
            // Base normal, rotated toward the nib angle (chisel effect).
            var nx = -dy
            var ny = dx
            if (nibAngle != 0f) {
                val rx = nx * cosA - ny * sinA
                val ry = nx * sinA + ny * cosA
                nx = rx; ny = ry
            }
            var half = pts[i].width / 2f
            if (taper) {
                val t = i / (pts.size - 1f)
                half *= 0.32f + 0.68f * min(1f, min(t, 1f - t) * 3.2f)
            }
            left.add(pts[i].x + nx * half)
            left.add(pts[i].y + ny * half - offsetY)
            right.add(pts[i].x - nx * half)
            right.add(pts[i].y - ny * half - offsetY)
        }
        path.moveTo(left[0], left[1])
        for (i in 1 until pts.size) path.lineTo(left[i * 2], left[i * 2 + 1])
        for (i in pts.size - 1 downTo 0) path.lineTo(right[i * 2], right[i * 2 + 1])
        path.close()
        return path
    }

    private fun noise(seed: Float, salt: Int): Float {
        val v = kotlin.math.sin(seed * 12.9898 + salt * 78.233f) * 43758.5453f
        return abs(v - kotlin.math.floor(v))
    }

    private fun List<InkPoint>.averageWidth(): Float {
        if (isEmpty()) return 1f
        var sum = 0f
        for (p in this) sum += p.width
        return sum / size
    }

    private fun List<InkPoint>.averageTilt(): Float {
        if (isEmpty()) return 0f
        var sum = 0f
        for (p in this) sum += p.tilt
        return sum / size
    }
}

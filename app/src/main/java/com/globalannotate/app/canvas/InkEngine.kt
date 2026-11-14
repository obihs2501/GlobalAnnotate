package com.globalannotate.app.canvas

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.globalannotate.app.model.PenStyle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Paints [InkStroke]s.
 *
 * Every brush is a swept band along the smoothed centre line, so the shape only
 * ever moves forward with the pen — no lateral wobble. What differs per brush
 * is the band profile and the texture applied *through* it:
 *
 *  圆珠笔  hard thin band, constant width
 *  铅笔    soft band, graphite grain in the alpha channel, tilt widens it
 *  钢笔    width and taper follow pressure
 *  马克笔  chisel band, translucent, overlaps darken
 *  水彩笔  two wash passes with mottled alpha and a wet edge
 *  橡皮    CLEAR band
 */
class InkEngine {

    private var grain: Bitmap? = null
    private var wash: Bitmap? = null
    private val tinted = HashMap<Long, Bitmap>()
    private var density = 2.75f

    fun setDensity(value: Float) { density = value }

    // ── textures ───────────────────────────────────────────────────────────

    private fun grain(): Bitmap {
        grain?.let { return it }
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(7)
        val px = IntArray(n * n)
        for (i in px.indices) {
            val v = random.nextDouble()
            val a = when {
                v < 0.14 -> 0
                v < 0.9 -> 70 + ((v - 0.14) / 0.76 * 150).toInt()
                else -> 235
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
        val seeds = Array(18) { Triple(random.nextFloat() * n, random.nextFloat() * n, 10f + random.nextFloat() * 22f) }
        for (y in 0 until n) for (x in 0 until n) {
            var a = 186 + random.nextInt(30)
            for (s in seeds) {
                val d = hypot(x - s.first, y - s.second)
                if (d < s.third) a += ((1f - d / s.third) * 50).toInt()
            }
            px[y * n + x] = Color.argb(a.coerceIn(0, 255), 255, 255, 255)
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        wash = bmp
        return bmp
    }

    /** Texture recoloured to [color], cached per brush colour. */
    private fun texture(style: PenStyle, color: Int): Bitmap? {
        val key = (style.ordinal.toLong() shl 32) or (color.toLong() and 0xFFFFFFFFL)
        tinted[key]?.let { return it }
        val source = when (style) {
            PenStyle.PENCIL -> grain()
            PenStyle.WATER_COLOR -> wash()
            else -> return null
        }
        val tintedBmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val px = IntArray(source.width * source.height)
        source.getPixels(px, 0, source.width, 0, 0, source.width, source.height)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val baseAlpha = Color.alpha(color) / 255f
        for (i in px.indices) {
            val a = (Color.alpha(px[i]) * baseAlpha).toInt().coerceIn(0, 255)
            px[i] = Color.argb(a, r, g, b)
        }
        tintedBmp.setPixels(px, 0, source.width, 0, 0, source.width, source.height)
        tinted[key] = tintedBmp
        return tintedBmp
    }

    // ── public ─────────────────────────────────────────────────────────────

    fun draw(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        if (stroke.points.isEmpty()) return
        when (stroke.style) {
            PenStyle.PENCIL -> textured(canvas, stroke, offsetY, taper = false, extra = 1f)
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
            singlePoint(canvas, stroke, pts[0], offsetY)
            return
        }
        val from = max(0, pts.size - 3)
        val part = InkStroke(pts.subList(from, pts.size).toMutableList(), stroke.color, stroke.style)
        draw(canvas, part, offsetY)
    }

    // ── brushes ────────────────────────────────────────────────────────────

    private fun eraser(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawPath(ribbon(stroke, offsetY, nibAngle = 0f, taper = false), paint)
    }

    private fun ballpoint(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val pts = stroke.points
        val width = pts.averageWidth()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            strokeWidth = width
            shader = null
        }
        canvas.drawPath(smoothPath(pts, offsetY), paint)
        paint.color = Color.argb((Color.alpha(stroke.color) * 0.22f).toInt(), 255, 255, 255)
        paint.strokeWidth = max(width * 0.28f, 0.6f)
        canvas.drawPath(smoothPath(pts, offsetY), paint)
    }

    private fun fountain(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = stroke.color
        }
        canvas.drawPath(ribbon(stroke, offsetY, nibAngle = 0f, taper = true), paint)
        val pts = stroke.points
        canvas.drawCircle(pts.first().x, pts.first().y - offsetY, pts.first().width * 0.16f, paint)
        canvas.drawCircle(pts.last().x, pts.last().y - offsetY, pts.last().width * 0.2f, paint)
    }

    private fun marker(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        // Chisel: the band is offset slightly along the nib, never sideways
        // relative to the stroke direction.
        val tilt = stroke.points.averageTilt()
        val nib = (tilt * 0.9f).coerceIn(0f, 0.7f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = stroke.color
        }
        canvas.drawPath(ribbon(stroke, offsetY, nibAngle = nib, taper = false), paint)
    }

    /** Pencil / watercolour: a clean band filled with the brush texture. */
    private fun textured(
        canvas: Canvas,
        stroke: InkStroke,
        offsetY: Float,
        taper: Boolean,
        extra: Float,
    ) {
        val tex = texture(stroke.style, stroke.color) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            colorFilter = ColorMatrixColorFilter(ColorMatrix())
        }
        if (extra != 1f) {
            // Widen the band without moving its centre line.
            val pts = stroke.points.map { InkPoint(it.x, it.y, it.width * extra, it.tilt) }
            canvas.drawPath(ribbon(InkStroke(pts.toMutableList(), stroke.color, stroke.style), offsetY, 0f, taper), paint)
        } else {
            canvas.drawPath(ribbon(stroke, offsetY, nibAngle = 0f, taper = taper), paint)
        }
    }

    private fun waterColor(canvas: Canvas, stroke: InkStroke, offsetY: Float) {
        val tex = texture(stroke.style, stroke.color) ?: return
        // Bleed pass: a wider, fainter band with the same mottled alpha.
        val bleed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = 90
        }
        val pts = stroke.points.map { InkPoint(it.x, it.y, it.width * 1.28f, it.tilt) }
        canvas.drawPath(
            ribbon(InkStroke(pts.toMutableList(), stroke.color, stroke.style), offsetY, 0f, false),
            bleed,
        )
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = 170
        }
        canvas.drawPath(ribbon(stroke, offsetY, 0f, false), body)
    }

    private fun singlePoint(canvas: Canvas, stroke: InkStroke, p: InkPoint, offsetY: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke.isEraser) Color.BLACK else stroke.color
            if (stroke.isEraser) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawCircle(p.x, p.y - offsetY, max(p.width / 2f, 0.7f), paint)
    }

    // ── geometry ───────────────────────────────────────────────────────────

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
     * Variable-width band around the smoothed centre line. [nibAngle] only
     * rotates the offset direction slightly (a chisel nib); the band never
     * leaves the stroke's own direction.
     */
    private fun ribbon(stroke: InkStroke, offsetY: Float, nibAngle: Float, taper: Boolean): Path {
        val pts = stroke.points
        val path = Path()
        if (pts.isEmpty()) return path
        if (pts.size == 1) {
            path.addCircle(pts[0].x, pts[0].y - offsetY, pts[0].width / 2f, Path.Direction.CW)
            return path
        }
        // Resample so the outline follows the same spline as smoothPath.
        val dense = resample(pts)
        val cosA = kotlin.math.cos(nibAngle)
        val sinA = kotlin.math.sin(nibAngle)
        val left = ArrayList<Float>(dense.size * 2)
        val right = ArrayList<Float>(dense.size * 2)
        for (i in dense.indices) {
            val prev = dense[(i - 1).coerceAtLeast(0)]
            val next = dense[(i + 1).coerceAtMost(dense.size - 1)]
            var dx = next.x - prev.x
            var dy = next.y - prev.y
            val len = hypot(dx, dy).coerceAtLeast(0.0001f)
            dx /= len
            dy /= len
            var nx = -dy
            var ny = dx
            if (nibAngle != 0f) {
                val rx = nx * cosA - ny * sinA
                val ry = nx * sinA + ny * cosA
                nx = rx
                ny = ry
            }
            var half = dense[i].width / 2f
            if (taper) {
                val t = i / (dense.size - 1f)
                half *= 0.30f + 0.70f * min(1f, min(t, 1f - t) * 3.0f)
            }
            left.add(dense[i].x + nx * half)
            left.add(dense[i].y + ny * half - offsetY)
            right.add(dense[i].x - nx * half)
            right.add(dense[i].y - ny * half - offsetY)
        }
        path.moveTo(left[0], left[1])
        for (i in 1 until dense.size) path.lineTo(left[i * 2], left[i * 2 + 1])
        for (i in dense.size - 1 downTo 0) path.lineTo(right[i * 2], right[i * 2 + 1])
        path.close()
        return path
    }

    /** Subdivide long segments so the band edge curves with the stroke. */
    private fun resample(pts: List<InkPoint>): List<InkPoint> {
        if (pts.size < 2) return pts
        val out = ArrayList<InkPoint>(pts.size * 2)
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            out.add(a)
            val d = hypot(b.x - a.x, b.y - a.y)
            val steps = (d / max(2.2f, a.width * 0.22f)).toInt().coerceIn(0, 6)
            for (s in 1 until steps) {
                val t = s / steps.toFloat()
                out.add(
                    InkPoint(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        a.width + (b.width - a.width) * t,
                        a.tilt + (b.tilt - a.tilt) * t,
                    ),
                )
            }
        }
        out.add(pts.last())
        return out
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

    @Suppress("unused")
    private fun noise(seed: Float, salt: Int): Float {
        val v = kotlin.math.sin(seed * 12.9898 + salt * 78.233f) * 43758.5453f
        return (v - kotlin.math.floor(v)).toFloat()
    }
}

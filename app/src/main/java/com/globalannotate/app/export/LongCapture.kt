package com.globalannotate.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

/**
 * Stitches consecutive screenshots of a scrolling page into one tall image.
 *
 * Overlap is found from row signatures (a coarse luminance profile of each
 * row), then verified on real pixels; the verified offset wins only when the
 * mismatch is small, so an animated banner does not corrupt the seam.
 */
object LongCapture {

    private const val MAX_HEIGHT = 30000
    private const val SAMPLE_STEP = 24

    /** Row signature: mean luminance in a sparse horizontal sample. */
    private fun signatures(frame: Bitmap, from: Int, to: Int): LongArray {
        val w = frame.width
        val out = LongArray(to - from)
        val pixels = IntArray(w)
        var row = 0
        for (y in from until to) {
            frame.getPixels(pixels, 0, w, 0, y, w, 1)
            var sum = 0L
            var x = 0
            while (x < w) {
                val c = pixels[x]
                sum += ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
                x += SAMPLE_STEP
            }
            out[row++] = sum
        }
        return out
    }

    /**
     * @return number of rows of [accumulated] that also start [next], or -1.
     */
    fun findOverlap(accumulated: Bitmap, next: Bitmap): Int {
        val maxOverlap = minOf(accumulated.height, next.height) - 40
        if (maxOverlap < 40) return -1
        val tailFrom = accumulated.height - maxOverlap
        val acc = signatures(accumulated, tailFrom, accumulated.height)
        val head = signatures(next, 0, maxOverlap)
        var best = -1
        var bestScore = Long.MAX_VALUE
        var overlap = maxOverlap
        while (overlap >= 40) {
            val offset = maxOverlap - overlap
            var score = 0L
            var i = 0
            while (i < overlap) {
                score += abs(acc[offset + i] - head[i])
                i += 2
            }
            val normalised = score / (overlap / 2 + 1)
            if (normalised < bestScore) {
                bestScore = normalised
                best = overlap
            }
            overlap -= 2
        }
        if (best <= 0) return -1
        // A flat page matches almost anywhere; require a little texture.
        return if (bestScore < 26) best else -1
    }

    /**
     * Appends [next] to [accumulated] below the overlap, returning a new bitmap.
     * The caller keeps compositing ink into [next] before calling this.
     */
    fun append(accumulated: Bitmap?, next: Bitmap, overlap: Int): Bitmap {
        if (accumulated == null) return next.copy(Bitmap.Config.ARGB_8888, false)
        val add = (next.height - overlap).coerceAtLeast(1)
        val newHeight = minOf(accumulated.height + add, MAX_HEIGHT)
        if (newHeight <= accumulated.height) return accumulated
        val result = Bitmap.createBitmap(next.width, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawBitmap(accumulated, 0f, 0f, null)
        val srcTop = overlap.coerceAtLeast(0)
        canvas.drawBitmap(
            next,
            android.graphics.Rect(0, srcTop, next.width, next.height),
            android.graphics.Rect(0, accumulated.height, next.width, accumulated.height + add),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        accumulated.recycle()
        next.recycle()
        return result
    }
}

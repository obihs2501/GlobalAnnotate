package com.globalannotate.app.canvas

import com.globalannotate.app.model.PenStyle

/** One pressure sample. [y] is in content space (screen y + scroll offset). */
data class InkPoint(val x: Float, val y: Float, val width: Float, val tilt: Float = 0f)

/** One pen-down … pen-up sequence, stored in content coordinates. */
data class InkStroke(
    val points: MutableList<InkPoint> = mutableListOf(),
    val color: Int,
    val style: PenStyle,
) {
    @Volatile var snapped: String? = null
    val isEraser: Boolean get() = style.isEraser

    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (p in points) {
            val h = p.width * (if (style.isMarkerLike) 0.75f else 0.6f) + 4f
            l = minOf(l, p.x - h); r = maxOf(r, p.x + h)
            t = minOf(t, p.y - h); b = maxOf(b, p.y + h)
        }
        return floatArrayOf(l, t, r, b)
    }
}

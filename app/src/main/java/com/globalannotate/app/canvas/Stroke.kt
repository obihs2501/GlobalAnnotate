package com.globalannotate.app.canvas

import com.globalannotate.app.model.PenStyle

/** A single pressure sample. Width is already resolved from pressure + brush settings. */
data class InkPoint(val x: Float, val y: Float, val width: Float)

/** One pen-down … pen-up sequence. */
data class InkStroke(
    val points: MutableList<InkPoint> = mutableListOf(),
    val color: Int,
    val style: PenStyle,
    val baseWidth: Float,
) {
    val eraser: Boolean get() = style.isEraser
}

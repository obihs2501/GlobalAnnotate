package com.globalannotate.app.canvas

/** A single pressure sample. Width is already resolved from pressure + pen settings. */
data class InkPoint(val x: Float, val y: Float, val width: Float)

/** One pen-down … pen-up sequence. Eraser strokes clear pixels instead of drawing. */
data class InkStroke(
    val points: MutableList<InkPoint> = mutableListOf(),
    val color: Int,
    val eraser: Boolean = false,
)

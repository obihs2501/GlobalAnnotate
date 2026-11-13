package com.globalannotate.app.model

/**
 * Brush types with genuinely different stroke rendering.
 * pressureGain controls how strongly pen pressure changes the width.
 */
enum class PenStyle(
    val label: String,
    val defaultWidthDp: Float,
    val alpha: Int,
    val pressureGain: Float,
    val isEraser: Boolean = false,
    val fades: Boolean = false,
    val squareCap: Boolean = false,
    val grain: Boolean = false,
    val glow: Boolean = false,
    val inkHighlight: Boolean = false,
) {
    PENCIL("铅笔", 2.5f, 175, 0.60f, grain = true),
    BALLPOINT("圆珠笔", 2.0f, 235, 0.12f, inkHighlight = true),
    PEN("钢笔", 3.5f, 255, 1.00f),
    MARKER("马克笔", 12f, 165, 0.30f, squareCap = true),
    HIGHLIGHTER("荧光笔", 22f, 80, 0f, squareCap = true),
    LASER("激光笔", 5f, 235, 0f, fades = true, glow = true),
    ERASER_PIXEL("压感橡皮", 26f, 255, 0.80f, isEraser = true),
    ERASER_STROKE("笔画橡皮", 26f, 255, 0f, isEraser = true),
    ;

    val isPixelEraser: Boolean get() = this == ERASER_PIXEL
    val isStrokeEraser: Boolean get() = this == ERASER_STROKE
}

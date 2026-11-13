package com.globalannotate.app.model

/**
 * Brush types offered by the annotation layer.
 * Widths are in dp; the canvas resolves them against pressure at draw time.
 */
enum class PenStyle(
    val label: String,
    val defaultWidthDp: Float,
    val alpha: Int,
    val pressureSensitive: Boolean,
    val isEraser: Boolean,
    val fades: Boolean,
) {
    PEN("钢笔", 3.5f, 255, true, false, false),
    PENCIL("铅笔", 2.5f, 185, true, false, false),
    MARKER("马克笔", 12f, 165, true, false, false),
    HIGHLIGHTER("荧光笔", 22f, 80, false, false, false),
    LASER("激光笔", 5f, 235, false, false, true),
    ERASER_PIXEL("压感橡皮", 26f, 255, true, true, false),
    ERASER_STROKE("笔画橡皮", 26f, 255, false, true, false),
    ;

    val isPixelEraser: Boolean get() = this == ERASER_PIXEL
    val isStrokeEraser: Boolean get() = this == ERASER_STROKE
}

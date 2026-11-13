package com.globalannotate.app.model

/**
 * Brush model reverse-engineered from Mi Canvas (`PenTypeValue` / `PenBean`).
 *
 * Each pen type carries its own size range, exactly like the original:
 *   INK_PEN 1..30 (default 15), BALLPOINT 1..22 (6), PENCIL 1..6 (3),
 *   MARK / WATER_COLOR 5..65 (30), ERASER 4..32.
 * Sensitivity is exposed only for the fountain pen and pencil (with different
 * labels in the original), tip size only for the fountain pen.
 */
enum class PenStyle(
    val label: String,
    val minSize: Int,
    val maxSize: Int,
    val defaultSize: Int,
    val defaultAlpha: Int,
    val showSensitivity: Boolean = false,
    val sensitivityLabel: String = "压力灵敏度",
    val showTipSize: Boolean = false,
    val isEraser: Boolean = false,
    val fades: Boolean = false,
    val squareCap: Boolean = false,
    val grain: Boolean = false,
    val glow: Boolean = false,
    val inkHighlight: Boolean = false,
    val softEdge: Boolean = false,
) {
    INK_PEN("钢笔", 1, 30, 15, 255, showSensitivity = true, sensitivityLabel = "钢笔灵敏度", showTipSize = true),
    BALLPOINT("圆珠笔", 1, 22, 6, 255, inkHighlight = true),
    PENCIL("铅笔", 1, 6, 3, 200, showSensitivity = true, sensitivityLabel = "铅笔灵敏度", grain = true),
    MARKER("马克笔", 5, 65, 30, 165, squareCap = true),
    WATER_COLOR("水彩笔", 5, 65, 30, 120, softEdge = true),
    HIGHLIGHTER("荧光笔", 5, 65, 30, 80, squareCap = true),
    LASER("激光笔", 1, 30, 6, 235, fades = true, glow = true),
    ERASER("橡皮", 4, 32, 12, 255, isEraser = true),
    ;

    companion object {
        /**
         * Bar order reverse-engineered from Mi Canvas `PenTypeValue`:
         * PENCIL=3, BALLPOINT=2, INK_PEN=1, MARK=4, WATER_COLOR=10, ERASER=7.
         * HIGHLIGHTER and LASER are kept for rendering only — Mi Canvas has no
         * such tools, so they never appear in the bar.
         */
        val toolbarOrder: List<PenStyle> = listOf(
            PENCIL, BALLPOINT, INK_PEN, MARKER, WATER_COLOR, ERASER,
        )
    }

    /** Mi Canvas draws size in its own unit; this maps it to pixels. */
    fun toPixels(size: Float, density: Float): Float = size * density * 0.42f
}

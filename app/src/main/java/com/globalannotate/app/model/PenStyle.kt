package com.globalannotate.app.model

/**
 * Mi Canvas brush set (`PenTypeValue` in the APK) and its measured popup rows.
 *
 *  - 圆珠笔 BALLPOINT   stroke width only
 *  - 铅笔   PENCIL      stroke width, 倾斜角灵敏度, opacity
 *  - 钢笔   INK_PEN     stroke width, 压力灵敏度, 笔尖大小, opacity
 *  - 马克笔 MARKER      stroke width, opacity
 *  - 水彩笔 WATER_COLOR stroke width, opacity
 *  - 橡皮   ERASER      pixel / stroke tabs
 *
 * `size` is Mi Canvas' own unit. [toPixels] was calibrated against a Mi Canvas
 * capture at 440 dpi (see _work/shots/mc_brushes.png).
 */
enum class PenStyle(
    val label: String,
    val minSize: Int,
    val maxSize: Int,
    val defaultSize: Int,
    val defaultAlpha: Int,
    val pxPerUnit: Float,
    val titleSensitivity: String? = null,
    val showTip: Boolean = false,
    val isEraser: Boolean = false,
) {
    // Ranges and defaults are PenBean's constants from Mi Canvas.
    BALLPOINT("圆珠笔", 1, 22, 6, 255, 0.40f),
    PENCIL("铅笔", 1, 6, 3, 255, 0.45f, titleSensitivity = "倾斜角灵敏度"),
    INK_PEN("钢笔", 1, 30, 15, 255, 0.55f, titleSensitivity = "压力灵敏度", showTip = true),
    MARKER("马克笔", 5, 65, 30, 255, 0.42f),
    WATER_COLOR("水彩笔", 5, 65, 30, 255, 0.75f),
    HIGHLIGHTER("荧光笔", 5, 65, 30, 90, 0.55f),
    ERASER("橡皮", 1, 30, 6, 255, 0.55f, isEraser = true),
    ;

    /** Stroke width in pixels for a Mi Canvas size value. */
    fun toPixels(size: Float, density: Float): Float = size * density * pxPerUnit

    val isMarkerLike: Boolean get() = this == MARKER || this == HIGHLIGHTER
    val isTextured: Boolean get() = this == PENCIL || this == WATER_COLOR

    companion object {
        /** Left-to-right order of the bar, exactly as Mi Canvas shows it. */
        val toolbarOrder: List<PenStyle> = listOf(
            BALLPOINT, PENCIL, INK_PEN, MARKER, WATER_COLOR, ERASER,
        )
    }
}

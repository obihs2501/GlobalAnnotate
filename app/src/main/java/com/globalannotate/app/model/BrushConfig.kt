package com.globalannotate.app.model

import android.graphics.Color

/** Eraser modes, mirroring Mi Canvas' two tabs in the eraser popup. */
enum class EraserMode { PIXEL, STROKE }

/** Current brush state, persisted by AnnotationService. */
data class BrushConfig(
    val style: PenStyle = PenStyle.INK_PEN,
    val color: Int = Color.parseColor("#D93025"),
    val size: Float = PenStyle.INK_PEN.defaultSize.toFloat(),
    val alpha: Int = PenStyle.INK_PEN.defaultAlpha,
    val sensitivity: Float = 0.5f,
    val tipSize: Float = 1f,
    val eraserMode: EraserMode = EraserMode.PIXEL,
    val eraserSize: Float = PenStyle.ERASER.defaultSize.toFloat(),
) {
    /** Colour with the current alpha applied. */
    val effectiveColor: Int
        get() = Color.argb(
            alpha,
            Color.red(color), Color.green(color), Color.blue(color),
        )

    companion object {
        /** The four quick colours on the main bar, 2x2 like Mi Canvas. */
        val QUICK_COLORS = listOf(
            Color.parseColor("#E5464A"),
            Color.parseColor("#F4D153"),
            Color.parseColor("#3F7FEB"),
        )

        /** Full palette used by the colour panel. */
        val PALETTE = listOf(
            Color.parseColor("#202124"),
            Color.parseColor("#5F6368"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#D93025"),
            Color.parseColor("#F4511E"),
            Color.parseColor("#F9AB00"),
            Color.parseColor("#34A853"),
            Color.parseColor("#1A73E8"),
            Color.parseColor("#7B1FA2"),
            Color.parseColor("#A1887F"),
        )
    }
}

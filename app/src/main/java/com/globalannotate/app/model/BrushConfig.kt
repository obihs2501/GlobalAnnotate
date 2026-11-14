package com.globalannotate.app.model

import android.graphics.Color

/** Eraser modes, mirroring Mi Canvas' two tabs. */
enum class EraserMode { PIXEL, STROKE }

/** Current brush state, persisted by AnnotationService. */
data class BrushConfig(
    val style: PenStyle = PenStyle.INK_PEN,
    val color: Int = Color.parseColor("#E8464A"),
    val size: Float = PenStyle.INK_PEN.defaultSize.toFloat(),
    val alpha: Int = PenStyle.INK_PEN.defaultAlpha,
    val sensitivity: Float = 0.38f,
    val tipSize: Int = 2,
    val eraserMode: EraserMode = EraserMode.PIXEL,
    val eraserSize: Float = PenStyle.ERASER.defaultSize.toFloat(),
) {
    /** Colour with the current alpha applied. */
    val effectiveColor: Int
        get() = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        /** Quick colours on the bar, 2x2 like Mi Canvas (measured #E8464A / #F7D254 / #3F80EC). */
        val QUICK_COLORS = listOf(
            Color.parseColor("#E8464A"),
            Color.parseColor("#F7D254"),
            Color.parseColor("#3F80EC"),
        )

        /** Recent-colour row in the colour popup. */
        val RECENT_COLORS = listOf(
            Color.parseColor("#A8D4F5"),
            Color.parseColor("#E8464A"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#000000"),
        )
    }
}

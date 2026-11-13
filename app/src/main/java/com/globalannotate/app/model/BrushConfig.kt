package com.globalannotate.app.model

import android.graphics.Color

/** Current brush state. Persisted by AnnotationService. */
data class BrushConfig(
    val style: PenStyle = PenStyle.PEN,
    val color: Int = Color.parseColor("#D93025"),
    val widthDp: Float = PenStyle.PEN.defaultWidthDp,
    val pressureEnabled: Boolean = true,
    val pressureSensitivity: Float = 0.5f,
    val minWidthDp: Float = 1f,
    val opacity: Int = 255,
) {
    companion object {
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

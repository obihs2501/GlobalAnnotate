package com.globalannotate.app.model

import android.graphics.Color

/** Current brush state. Persisted by AnnotationService. */
data class BrushConfig(
    val style: PenStyle = PenStyle.PEN,
    val color: Int = Color.parseColor("#D93025"),
    val widthDp: Float = PenStyle.PEN.defaultWidthDp,
    val pressureEnabled: Boolean = true,
) {
    companion object {
        val PALETTE = listOf(
            Color.parseColor("#202124"), // black
            Color.parseColor("#5F6368"), // grey
            Color.parseColor("#FFFFFF"), // white
            Color.parseColor("#D93025"), // red
            Color.parseColor("#F4511E"), // orange
            Color.parseColor("#F9AB00"), // yellow
            Color.parseColor("#34A853"), // green
            Color.parseColor("#1A73E8"), // blue
            Color.parseColor("#7B1FA2"), // purple
            Color.parseColor("#A1887F"), // brown
        )
    }
}

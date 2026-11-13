package com.globalannotate.app

import com.globalannotate.app.canvas.AnnotationCanvasView

/** Same-process glue between the overlay service, the binder sink and receivers. */
object Session {
    @Volatile var service: AnnotationService? = null
    @Volatile var canvas: AnnotationCanvasView? = null

    /** True only in ROOT_SPLIT mode: stylus events are expected from system_server. */
    @Volatile var rootSplitActive: Boolean = false
    @Volatile var active: Boolean = false
}

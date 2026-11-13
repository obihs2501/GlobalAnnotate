package com.globalannotate.xposed

import android.os.IBinder

/** Lives in the `android` (system_server) process. */
object ModuleState {
    /** Master switch, driven by the main app's SET_MODULE_ENABLED broadcast. */
    @Volatile var enabled: Boolean = false

    /** True only when the app is in ROOT_SPLIT mode. */
    @Volatile var splitMode: Boolean = false

    /** Bound to com.globalannotate.app.AnnotationInputService. */
    @Volatile var sink: IBinder? = null
}

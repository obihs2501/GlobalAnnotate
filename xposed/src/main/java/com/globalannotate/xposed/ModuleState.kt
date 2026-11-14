package com.globalannotate.xposed

import android.os.IBinder

/** Lives in the `android` (system_server) process. */
object ModuleState {
    /**
     * The hooks are only useful while the annotation layer runs, but a missed
     * "enable" broadcast used to leave the whole module dead. Default to on and
     * let the app switch it off when it stops; every hook is fail-open anyway.
     */
    @Volatile var enabled: Boolean = true

    /** True only when the app is in ROOT_SPLIT mode. */
    @Volatile var splitMode: Boolean = false

    /** Bound to com.globalannotate.app.AnnotationInputService. */
    @Volatile var sink: IBinder? = null
}

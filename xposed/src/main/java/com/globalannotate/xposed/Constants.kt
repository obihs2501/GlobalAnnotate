package com.globalannotate.xposed

/** Kept byte-identical to the app-side constants; the two APKs are independent. */
object XActions {
    const val LAUNCH = "com.globalannotate.action.LAUNCH"
    const val TOGGLE = "com.globalannotate.action.TOGGLE"
    const val GESTURE = "com.globalannotate.action.GESTURE"
    const val SET_SPLIT_MODE = "com.globalannotate.action.SET_SPLIT_MODE"
    const val SET_MODULE_ENABLED = "com.globalannotate.action.SET_MODULE_ENABLED"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_ENABLED = "enabled"
    const val TOKEN = "ga-hyperos-2026"

    const val APP_PACKAGE = "com.globalannotate.app"
    const val RECEIVER_CLASS = "com.globalannotate.app.GestureLaunchReceiver"
}

object XIpc {
    const val DESCRIPTOR = "com.globalannotate.ipc.IAnnotationInput"
    const val TX_STYLUS_EVENT = 1
    const val TX_SET_ACTIVE = 2
    const val SERVICE_PACKAGE = "com.globalannotate.app"
    const val SERVICE_CLASS = "com.globalannotate.app.AnnotationInputService"
}

object XPenKeys {
    const val SQUEEZE = 194
    const val DOUBLE_TAP = 195
    const val SLIDE_UP = 196
    const val SLIDE_DOWN = 197
    const val SCANCODE_SQUEEZE_RAW = 189
}

package com.globalannotate.app

/** Broadcast actions shared with the LSPosed module (duplicated there on purpose:
 *  the two APKs must not depend on each other at build time). */
object Actions {
    const val LAUNCH = "com.globalannotate.action.LAUNCH"
    const val TOGGLE = "com.globalannotate.action.TOGGLE"
    const val GESTURE = "com.globalannotate.action.GESTURE"
    const val SET_SPLIT_MODE = "com.globalannotate.action.SET_SPLIT_MODE"
    const val SET_MODULE_ENABLED = "com.globalannotate.action.SET_MODULE_ENABLED"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_ENABLED = "enabled"
    const val TOKEN = "ga-hyperos-2026"
}

/** HyperOS delivers Focus Pen Pro barrel gestures as ordinary key codes. */
object GestureKeys {
    const val SQUEEZE = 194
    const val DOUBLE_TAP = 195
    const val SLIDE_UP = 196
    const val SLIDE_DOWN = 197
    const val SCANCODE_SQUEEZE_RAW = 189 // Linux KEY_F19
}

/** Binder protocol used by the LSPosed module to hand stylus MotionEvents over. */
object InputIpc {
    const val DESCRIPTOR = "com.globalannotate.ipc.IAnnotationInput"
    const val TX_STYLUS_EVENT = 1
    const val TX_SET_ACTIVE = 2
    const val SERVICE_PACKAGE = "com.globalannotate.app"
    const val SERVICE_CLASS = "com.globalannotate.app.AnnotationInputService"
}

enum class OverlayMode {
    /** No root: full-screen overlay intercepts everything, only stylus draws. */
    DRAW,
    /** No root: overlay is fully touch-transparent, underlying app is usable. */
    PASSTHROUGH,
    /** Root + LSPosed: overlay is touch-transparent; system_server reroutes
     *  stylus events into the app's binder sink and lets finger events pass. */
    ROOT_SPLIT,
}

enum class Gesture { SQUEEZE, DOUBLE_TAP, SLIDE_UP, SLIDE_DOWN }

/** Actions a pen-barrel gesture can be mapped to. */
enum class GestureAction {
    NONE,
    TOGGLE_OVERLAY,
    TOGGLE_ERASER,
    CYCLE_TOOL,
    UNDO,
    REDO,
    CLEAR,
    INCREASE_WIDTH,
    DECREASE_WIDTH,
    TOGGLE_TOOLBAR,
    OPEN_COLOR,
    CYCLE_COLOR,
}

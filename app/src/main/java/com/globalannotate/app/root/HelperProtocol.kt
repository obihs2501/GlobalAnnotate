package com.globalannotate.app.root

/**
 * Wire format between the app and the root helper.
 *
 * helper → app (stdout): `[type:1][length:4, big endian][payload]`
 * app → helper (stdin):  one text command per line
 *
 * Pipes are used instead of Binder on purpose: stdin/stdout of the `su` child
 * always work across SELinux domains, and the helper dies with the pipe.
 */
object HelperProtocol {
    const val FRAME_STYLUS = 1      // payload: marshalled MotionEvent parcel
    const val FRAME_FINGER = 2      // payload: marshalled MotionEvent parcel
    const val FRAME_PEN_KEY = 3     // payload: scanCode:int, value:int, uptimeMs:long
    const val FRAME_LOG = 4         // payload: utf-8 text
    const val FRAME_READY = 5       // payload: flags:int

    const val READY_INTERCEPT = 1
    const val READY_SPY = 2
    const val READY_KEYS = 4

    /** `REGION l,t,r,b;l,t,r,b` — screen rects where the stylus must NOT be captured. */
    const val CMD_REGION = "REGION"

    /** `INTERCEPT 0|1` — route the stylus into the annotation layer. */
    const val CMD_INTERCEPT = "INTERCEPT"

    /** `OPACITY 0|1` — lift / restore the obscuring-opacity limit for pass-through touches. */
    const val CMD_OPACITY = "OPACITY"
    const val CMD_QUIT = "QUIT"

    const val MAIN_CLASS = "com.globalannotate.app.root.HelperMain"
}

package com.globalannotate.xposed

/**
 * Hook points verified on Xiaomi Pad 8 Pro / HyperOS 3 `OS3.0.307.0.WOCCNXM`
 * and HyperOS 4 `OS4.0.4.0.XPYCNXM` by FocusPenProX, plus AOSP input classes.
 * Every lookup is best-effort and fail-open: a missing class simply logs.
 */
object CompatProfile {
    const val TOUCH_FILM_CLASS = "com.miui.server.input.stylus.MiuiStylusTouchFilmManager"
    const val SHORTCUT_MANAGER_CLASS = "com.miui.server.input.stylus.MiuiStylusShortcutManager"
    const val POLICY_CLASS = "com.android.server.policy.MiuiPhoneWindowManager"

    const val INTERCEPT_KEY_METHOD = "interceptKeyBeforeQueueing"
    const val FILTER_METHOD = "filterInputEvent"

    val KEY_HOOK_CLASSES = listOf(TOUCH_FILM_CLASS, POLICY_CLASS, SHORTCUT_MANAGER_CLASS)

    val INPUT_SERVICE_CLASSES = listOf(
        "com.android.server.input.InputManagerService",
        "com.android.server.input.InputManagerService\$LocalService",
        "com.miui.server.input.MiuiInputManagerService",
    )

    /** Observation zone for the pen-tip corner gesture. NOT a blocked area:
     *  events here are always forwarded to the app underneath. */
    const val CORNER_WIDTH_DP = 140
    const val CORNER_HEIGHT_DP = 140
    const val SWIPE_THRESHOLD_DP = 52
    const val CORNER_COOLDOWN_MS = 800L

    /** Minimum interval between forwarded MOVE events (ms). */
    const val FORWARD_INTERVAL_MS = 4L

    const val DOUBLE_TAP_WINDOW_MS = 320L
}

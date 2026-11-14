package com.globalannotate.app.sys

import android.util.Log
import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The few framework internals the app process needs. They are reached through
 * reflection after lifting the hidden-API filter for this process only.
 */
object Hidden {

    private const val TAG = "GA.Hidden"

    /** True once non-SDK members are reachable from this process. */
    val unlocked: Boolean by lazy {
        val ok = runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { Log.w(TAG, "hidden api bypass threw: $it") }
            .getOrDefault(false)
        Log.i(TAG, "hidden api unlocked=$ok")
        ok
    }

    private val getViewRootImpl by lazy { View::class.java.getMethod("getViewRootImpl") }
    private val inputFeaturesField by lazy {
        android.view.WindowManager.LayoutParams::class.java.getField("inputFeatures")
    }
    private val setSkipScreenshot by lazy {
        SurfaceControl.Transaction::class.java.getMethod(
            "setSkipScreenshot", SurfaceControl::class.java, Boolean::class.javaPrimitiveType,
        )
    }

    /**
     * `WindowManager.LayoutParams#inputFeatures` is hidden but drives
     * InputConfigAdapter: INPUT_FEATURE_INTERCEPTS_STYLUS (0x40000000) makes a
     * window the stylus target while fingers fall through, INPUT_FEATURE_SPY
     * (4) delivers a copy of touches. Both are read straight off the window
     * params by the window manager, with no extra permission check.
     */
    fun setInputFeatures(params: android.view.WindowManager.LayoutParams, features: Int): Boolean {
        if (!unlocked) return false
        return runCatching {
            inputFeaturesField.setInt(params, features)
            true
        }.onFailure { Log.w(TAG, "setInputFeatures failed: $it") }.getOrDefault(false)
    }

    fun surfaceControlOf(view: View): SurfaceControl? {
        if (!unlocked) return null
        return runCatching {
            val root = getViewRootImpl.invoke(view) ?: return null
            root.javaClass.getMethod("getSurfaceControl").invoke(root) as? SurfaceControl
        }.onFailure { Log.w(TAG, "surfaceControlOf failed: $it") }.getOrNull()
    }

    /**
     * Leaves a window out of screenshots, screen recordings and mirrored
     * displays while it stays visible on the panel. This is what lets the
     * page underneath be captured without first hiding the ink.
     */
    fun setSkipScreenshot(view: View, skip: Boolean): Boolean {
        val control = surfaceControlOf(view) ?: return false
        if (!control.isValid) return false
        return runCatching {
            val t = SurfaceControl.Transaction()
            setSkipScreenshot.invoke(t, control, skip)
            t.apply()
            true
        }.onFailure { Log.w(TAG, "setSkipScreenshot failed: $it") }.getOrDefault(false)
    }
}

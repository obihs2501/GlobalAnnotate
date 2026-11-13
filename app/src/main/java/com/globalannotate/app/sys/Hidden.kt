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
    private val setSkipScreenshot by lazy {
        SurfaceControl.Transaction::class.java.getMethod(
            "setSkipScreenshot", SurfaceControl::class.java, Boolean::class.javaPrimitiveType,
        )
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

package com.globalannotate.xposed

import android.util.Log

/** Routes module logs through libxposed's log() when available, else Logcat. */
object GaLog {
    private const val DEFAULT_TAG = "GlobalAnnotate"

    var sink: ((Int, String, String) -> Unit)? = null

    fun i(message: String, tag: String = DEFAULT_TAG) {
        val target = sink
        if (target != null) target(Log.INFO, tag, message) else Log.i(tag, message)
    }

    fun e(message: String, tag: String = DEFAULT_TAG) {
        val target = sink
        if (target != null) target(Log.ERROR, tag, message) else Log.e(tag, message)
    }
}

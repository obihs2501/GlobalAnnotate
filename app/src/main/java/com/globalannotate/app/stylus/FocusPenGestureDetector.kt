package com.globalannotate.app.stylus

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.globalannotate.app.Gesture
import com.globalannotate.app.GestureKeys

/**
 * Maps HyperOS Focus Pen Pro barrel key events to gestures.
 *
 * The system emits key 194 (squeeze, scan code 189 = KEY_F19), 195 (double tap),
 * 196 (slide up), 197 (slide down). On some builds a physical double tap arrives
 * as two 194 DOWN/UP pairs instead of a single 195, so we count squeezes too and
 * suppress the duplicate 195 for a short window.
 *
 * Both DOWN and UP are consumed: letting an UP leak back into
 * MiuiStylusTouchFilmManager would re-trigger Xiaomi's shortcut wheel.
 */
class FocusPenGestureDetector(private val onGesture: (Gesture) -> Unit) {

    var doubleTapWindowMs: Long = 320L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSingle: Runnable? = null
    private var lastSqueezeDownMs = 0L
    private var ignoreSystemDoubleUntilMs = 0L

    private fun gestureFor(keyCode: Int, scanCode: Int): Gesture? = when (keyCode) {
        GestureKeys.SQUEEZE -> Gesture.SQUEEZE
        GestureKeys.DOUBLE_TAP -> Gesture.DOUBLE_TAP
        GestureKeys.SLIDE_UP -> Gesture.SLIDE_UP
        GestureKeys.SLIDE_DOWN -> Gesture.SLIDE_DOWN
        else -> if (scanCode == GestureKeys.SCANCODE_SQUEEZE_RAW) Gesture.SQUEEZE else null
    }

    /** @return true when the event belongs to the pen and must be consumed. */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int, scanCode: Int): Boolean {
        val gesture = gestureFor(keyCode, scanCode) ?: return false
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            when (gesture) {
                Gesture.SQUEEZE -> onSqueeze()
                Gesture.DOUBLE_TAP -> {
                    cancelPendingSingle()
                    if (SystemClock.uptimeMillis() > ignoreSystemDoubleUntilMs) {
                        onGesture(Gesture.DOUBLE_TAP)
                    }
                }
                Gesture.SLIDE_UP, Gesture.SLIDE_DOWN -> onGesture(gesture)
            }
        }
        return true
    }

    private fun onSqueeze() {
        val now = SystemClock.uptimeMillis()
        cancelPendingSingle()
        if (now - lastSqueezeDownMs <= doubleTapWindowMs) {
            ignoreSystemDoubleUntilMs = now + doubleTapWindowMs
            onGesture(Gesture.DOUBLE_TAP)
        } else {
            val runnable = Runnable {
                pendingSingle = null
                onGesture(Gesture.SQUEEZE)
            }
            pendingSingle = runnable
            handler.postDelayed(runnable, doubleTapWindowMs)
        }
        lastSqueezeDownMs = now
    }

    private fun cancelPendingSingle() {
        pendingSingle?.let(handler::removeCallbacks)
        pendingSingle = null
    }

    fun reset() {
        cancelPendingSingle()
        lastSqueezeDownMs = 0L
        ignoreSystemDoubleUntilMs = 0L
    }
}

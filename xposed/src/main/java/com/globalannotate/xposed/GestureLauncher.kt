package com.globalannotate.xposed

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Global pen-gesture launch, hooked at the key-policy entry that
 * WondersReady/FocusPen documents:
 *
 *   BaseMiuiPhoneWindowManager.interceptKeyBeforeQueueingInternal
 *     -> MiuiStylusTouchFilmManager.interceptKeyBeforeQueueing
 *
 * Runs in system_server for every foreground app, so a squeeze / double tap
 * summons annotation from anywhere. With libxposed API 102, NOT calling
 * chain.proceed() consumes the event (equivalent to the old setResult(0)).
 * Both DOWN and UP are consumed so Xiaomi's shortcut wheel cannot re-trigger.
 */
object GestureLauncher {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSingle: Runnable? = null
    private var lastSqueezeDownMs = 0L

    fun install(api: XposedInterface, classLoader: ClassLoader, bridge: AppBridge) {
        var hooked = 0
        for (className in CompatProfile.KEY_HOOK_CLASSES) {
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: continue
            for (method in findMethods(clazz, CompatProfile.INTERCEPT_KEY_METHOD)) {
                val ok = runCatching {
                    method.isAccessible = true
                    api.hook(method).intercept { chain ->
                        try {
                            handle(chain, bridge)
                        } catch (error: Throwable) {
                            GaLog.e("gesture hook error: $error")
                            chain.proceed() // fail open
                        }
                    }
                    true
                }.onFailure { GaLog.e("hook $className failed: $it") }.getOrDefault(false)
                if (ok) hooked++
            }
        }
        if (hooked > 0) GaLog.i("key policy hooked on $hooked methods")
        else GaLog.e("no key-policy hook found; check CompatProfile against this ROM")
    }

    private fun handle(chain: XposedInterface.Chain, bridge: AppBridge): Any? {
        if (!ModuleState.enabled) return chain.proceed()
        val event = chain.args.getOrNull(0) as? KeyEvent ?: return chain.proceed()
        val keyCode = event.keyCode
        val isPenKey = keyCode in XPenKeys.SQUEEZE..XPenKeys.SLIDE_DOWN ||
            event.scanCode == XPenKeys.SCANCODE_SQUEEZE_RAW
        if (!isPenKey) return chain.proceed()

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (keyCode) {
                XPenKeys.DOUBLE_TAP -> {
                    cancelPending()
                    bridge.gesture("DOUBLE_TAP")
                }
                XPenKeys.SLIDE_UP -> bridge.gesture("SLIDE_UP")
                XPenKeys.SLIDE_DOWN -> bridge.gesture("SLIDE_DOWN")
                else -> onSqueeze(bridge)
            }
        }
        // Consume the whole DOWN/UP pair: do not call chain.proceed().
        return 0
    }

    private fun onSqueeze(bridge: AppBridge) {
        val now = SystemClock.uptimeMillis()
        cancelPending()
        if (now - lastSqueezeDownMs <= CompatProfile.DOUBLE_TAP_WINDOW_MS) {
            bridge.gesture("DOUBLE_TAP")
        } else {
            val runnable = Runnable {
                pendingSingle = null
                bridge.gesture("SQUEEZE")
            }
            pendingSingle = runnable
            handler.postDelayed(runnable, CompatProfile.DOUBLE_TAP_WINDOW_MS)
        }
        lastSqueezeDownMs = now
    }

    private fun cancelPending() {
        pendingSingle?.let(handler::removeCallbacks)
        pendingSingle = null
    }

    private fun findMethods(clazz: Class<*>, name: String): List<Method> =
        (clazz.declaredMethods.asList() + clazz.methods.asList())
            .filter { it.name == name }
            .distinct()
}

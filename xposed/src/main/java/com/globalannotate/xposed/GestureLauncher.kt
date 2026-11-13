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
 * chain.proceed() consumes the event; the replacement value must still match
 * the hooked method's return type or the framework throws ClassCastException,
 * so the value is derived from the real signature (int / boolean / void).
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
                val consumed = consumedValueFor(method)
                val ok = runCatching {
                    method.isAccessible = true
                    api.hook(method).intercept { chain ->
                        try {
                            handle(chain, bridge, consumed)
                        } catch (error: Throwable) {
                            GaLog.e("gesture hook error: $error")
                            chain.proceed() // fail open
                        }
                    }
                    true
                }.onFailure { GaLog.e("hook ${className}#${method.name} failed: $it") }.getOrDefault(false)
                if (ok) {
                    hooked++
                    GaLog.i("hooked ${className}#${method.name}(${method.parameterTypes.joinToString { it.simpleName }}): ${method.returnType.simpleName}")
                }
            }
        }
        if (hooked == 0) GaLog.e("no key-policy hook found; check CompatProfile against this ROM")
    }

    /** Consumed value matching the real return type of the hooked method. */
    private fun consumedValueFor(method: Method): Any? = when (method.returnType) {
        java.lang.Integer.TYPE, Integer::class.java -> 0
        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> true
        java.lang.Long.TYPE, java.lang.Long::class.java -> 0L
        else -> null // void
    }

    private fun handle(chain: XposedInterface.Chain, bridge: AppBridge, consumed: Any?): Any? {
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
        // Consume the whole DOWN/UP pair: skip chain.proceed().
        return consumed
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

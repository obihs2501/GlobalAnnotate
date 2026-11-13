package com.globalannotate.xposed

import android.os.SystemClock
import android.view.MotionEvent
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Two jobs in the system input filter (InputManagerService.filterInputEvent):
 *
 * 1. **Observe-only** corner gesture: a stylus that starts in the top-right
 *    region and moves inward summons annotation. The event is never consumed
 *    because of it, so no area of the screen becomes a dead zone.
 * 2. toolType split (ROOT_SPLIT): ONLY in that mode are stylus events copied to
 *    the app's Binder sink and dropped from normal dispatch. Finger events are
 *    always untouched.
 *
 * "Dropped" must match the hooked method's return type, so the value is derived
 * from the real signature (boolean false = drop, int 0 = handled, void = null).
 */
object InputRouter {

    private var armed = false
    private var fired = false
    private var startX = 0f
    private var startY = 0f
    private var lastLaunchMs = 0L

    fun install(api: XposedInterface, classLoader: ClassLoader, bridge: AppBridge) {
        var hooked = 0
        for (className in CompatProfile.INPUT_SERVICE_CLASSES) {
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull() ?: continue
            for (method in findMethods(clazz, CompatProfile.FILTER_METHOD)) {
                val dropped = droppedValueFor(method)
                val ok = runCatching {
                    method.isAccessible = true
                    api.hook(method).intercept { chain ->
                        try {
                            handle(chain, bridge, dropped)
                        } catch (error: Throwable) {
                            GaLog.e("input router error: $error")
                            chain.proceed()
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
        if (hooked == 0) GaLog.e("filterInputEvent not found; ROOT_SPLIT and corner swipe disabled")
    }

    /** Value that makes the framework drop the event, matching the signature. */
    private fun droppedValueFor(method: Method): Any? = when (method.returnType) {
        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> false
        java.lang.Integer.TYPE, Integer::class.java -> 0
        java.lang.Long.TYPE, java.lang.Long::class.java -> 0L
        else -> null
    }

    private fun handle(chain: XposedInterface.Chain, bridge: AppBridge, dropped: Any?): Any? {
        if (!ModuleState.enabled) return chain.proceed()
        val event = chain.args.getOrNull(0) as? MotionEvent ?: return chain.proceed()
        if (event.pointerCount == 0) return chain.proceed()
        val tool = event.getToolType(0)
        val isStylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

        // Observation only — never consumes because of the corner gesture.
        if (isStylus) observeCorner(event, bridge)

        if (ModuleState.splitMode && ModuleState.sink != null && isStylus) {
            bridge.forwardStylus(event)
            return dropped // pen is intentionally routed to the annotation layer
        }
        return chain.proceed()
    }

    private fun observeCorner(event: MotionEvent, bridge: AppBridge) {
        val (screenWidth, screenHeight) = bridge.displaySize()
        val cornerW = bridge.cornerWidthPx().coerceAtMost(screenWidth.toFloat())
        val cornerH = bridge.cornerHeightPx().coerceAtMost(screenHeight.toFloat())
        val threshold = bridge.swipeThresholdPx()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_HOVER_ENTER -> {
                armed = event.x >= screenWidth - cornerW && event.y <= cornerH
                fired = false
                startX = event.x
                startY = event.y
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (!armed || fired) return
                val inward = maxOf(startX - event.x, event.y - startY)
                if (inward <= threshold) return
                val now = SystemClock.uptimeMillis()
                if (now - lastLaunchMs < CompatProfile.CORNER_COOLDOWN_MS) return
                lastLaunchMs = now
                fired = true
                bridge.launch()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_EXIT -> {
                armed = false
                fired = false
            }
        }
    }

    private fun findMethods(clazz: Class<*>, name: String): List<Method> =
        (clazz.declaredMethods.asList() + clazz.methods.asList())
            .filter { it.name == name }
            .distinct()
}

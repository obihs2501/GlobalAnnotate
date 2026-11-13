package com.globalannotate.xposed

import android.os.SystemClock
import android.view.MotionEvent
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Two jobs in the system input filter (InputManagerService.filterInputEvent):
 *
 * 1. **Observe-only** corner gesture: a stylus that starts in the top-right
 *    region and moves inward summons annotation. Events are NEVER consumed —
 *    every DOWN/MOVE/UP and every finger event continues to the app below via
 *    chain.proceed(). Hover events (ACTION_HOVER_*) are preferred because they
 *    produce no ink or click at all.
 * 2. toolType split (ROOT_SPLIT): ONLY in that mode are stylus events copied to
 *    the app's Binder sink and dropped from normal dispatch. Finger events are
 *    always untouched.
 *
 * There is deliberately no "reserved" area that eats touches. The only input
 * consumed is the pen itself while ROOT_SPLIT is active, which is the intended
 * behaviour of that mode (switch to 穿透 to use the pen in the app below).
 *
 * Everything is fail-open: any error or dead binder falls through to
 * chain.proceed().
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
                val ok = runCatching {
                    method.isAccessible = true
                    api.hook(method).intercept { chain ->
                        try {
                            handle(chain, bridge)
                        } catch (error: Throwable) {
                            GaLog.e("input router error: $error")
                            chain.proceed()
                        }
                    }
                    true
                }.onFailure { GaLog.e("hook $className failed: $it") }.getOrDefault(false)
                if (ok) hooked++
            }
        }
        if (hooked > 0) GaLog.i("input filter hooked on $hooked methods (observe-only corner)")
        else GaLog.e("filterInputEvent not found; ROOT_SPLIT and corner swipe disabled")
    }

    private fun handle(chain: XposedInterface.Chain, bridge: AppBridge): Any? {
        if (!ModuleState.enabled) return chain.proceed()
        val event = chain.args.getOrNull(0) as? MotionEvent ?: return chain.proceed()
        if (event.pointerCount == 0) return chain.proceed()
        val tool = event.getToolType(0)
        val isStylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

        // Observation only. Never returns early / never consumes because of it.
        if (isStylus) observeCorner(event, bridge)

        if (ModuleState.splitMode && ModuleState.sink != null && isStylus) {
            bridge.forwardStylus(event)
            return false // pen is intentionally routed to the annotation layer
        }
        return chain.proceed() // fingers + all non-split input: always pass through
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

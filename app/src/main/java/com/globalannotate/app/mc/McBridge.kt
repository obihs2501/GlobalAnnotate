package com.globalannotate.app.mc

import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * Reflective bridge to Mi Canvas' obfuscated members.
 *
 * jadx renames Unicode identifiers (`m16888I1Iili`), so calling by the jadx
 * name fails at runtime. The dex keeps the original names, which contain a
 * Greek iota: the gradient setter of every colour slider is `I1Iιili(int)`.
 * Lookup tries the exact original name first and then falls back to a
 * signature scan, cached per class.
 */
object McBridge {

    private const val TAG = "GA.McBridge"
    private const val IOTA = "ι"
    private const val SETTER = "I1I${IOTA}ili"

    private val cache = HashMap<String, Method?>()
    private val coloursCache = HashMap<String, Method?>()

    fun setColour(view: View, colour: Int): Boolean {
        val method = cache.getOrPut(view.javaClass.name) { findSetter(view, Int::class.javaPrimitiveType!!) }
        return invoke(view, method, colour)
    }

    fun setColours(view: View, colours: IntArray): Boolean {
        val method = coloursCache.getOrPut(view.javaClass.name) { findSetter(view, IntArray::class.java) }
        return invoke(view, method, colours)
    }

    /** `Iiliill()` resets the gradient geometry after colours change. */
    fun refresh(view: View): Boolean = invoke(view, findByName(view, "Iiliill"), null)

    private fun findSetter(view: View, type: Class<*>): Method? {
        val exact = findByName(view, SETTER, type)
        if (exact != null) return exact
        // Fallback: any one-argument void method that is not a platform callback.
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != android.view.View::class.java) {
            for (m in cls.declaredMethods) {
                if (m.parameterCount != 1 || m.parameterTypes[0] != type) continue
                if (m.returnType != Void.TYPE) continue
                if (m.isSynthetic) continue
                if (m.name in EXCLUDED) continue
                Log.i(TAG, "setter by scan: ${cls.simpleName}.${m.name}")
                return m
            }
            cls = cls.superclass
        }
        return null
    }

    private fun findByName(view: View, name: String, type: Class<*>? = null): Method? {
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != android.view.View::class.java) {
            for (m in cls.declaredMethods) {
                if (m.name != name) continue
                if (type != null && (m.parameterCount != 1 || m.parameterTypes[0] != type)) continue
                if (type == null && m.parameterCount != 0) continue
                return m
            }
            cls = cls.superclass
        }
        return null
    }

    private fun invoke(view: View, method: Method?, vararg args: Any?): Boolean {
        if (method == null) return false
        return runCatching {
            method.isAccessible = true
            method.invoke(view, *args)
            true
        }.onFailure { Log.w(TAG, "invoke ${method.name} failed: $it") }.getOrDefault(false)
    }

    private val EXCLUDED = setOf(
        "onDraw", "onMeasure", "onTouchEvent", "onSizeChanged", "setProgress", "setMax",
        "setMin", "setOnSeekBarChangeListener", "setBackground", "setAlpha", "setEnabled",
        "onInitializeAccessibilityNodeInfo", "dispatchTouchEvent", "onLayout",
    )
}

package com.globalannotate.app.mc

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import dalvik.system.DexClassLoader

/**
 * Runs Mi Canvas' own UI classes inside this process.
 *
 * The pen-picker package (`com.xiaomi.penpicker`), the Miuix widgets it uses and
 * its resources all ship inside `com.miui.creation`; loading them from there is
 * the only way to get a literal copy of its popups instead of a look-alike.
 * The brush engine is native (`libmicurve.so`) and stays out of reach, so the
 * ink itself is still ours.
 */
object MiCanvasUi {

    private const val TAG = "GA.MiCanvas"
    const val PKG = "com.miui.creation"

    data class Host(
        val context: Context,
        val loader: ClassLoader,
        val resources: Resources,
        val packageName: String,
    )

    val available: Boolean get() = host != null
    private var hostOrNull: Host? = null
    private var initialised = false

    private val host: Host?
        get() {
            if (!initialised) {
                initialised = true
                hostOrNull = runCatching { build() }
                    .onFailure { Log.w(TAG, "Mi Canvas UI unavailable: $it") }
                    .getOrNull()
            }
            return hostOrNull
        }

    private fun build(): Host {
        val ctx = appContext ?: error("no app context")
        val info = ctx.packageManager.getApplicationInfo(PKG, PackageManager.GET_META_DATA)
        val apk = info.sourceDir
        val loader = DexClassLoader(apk, ctx.codeCacheDir.absolutePath, null, ctx.classLoader)
        val base = ctx.createPackageContext(PKG, 0)
        // Views resolve their resources through this wrapper; its class loader
        // must be the one that sees Mi Canvas' dex.
        val wrapped = object : ContextWrapper(base) {
            override fun getClassLoader(): ClassLoader = loader
            override fun getResources(): Resources = base.resources
            override fun getAssets() = base.assets
            override fun getPackageName(): String = PKG
            override fun getTheme() = base.theme
        }
        val theme = info.theme
        // ContextThemeWrapper keeps the base's class loader and resources, and
        // supplies the app's theme so ?attr lookups resolve as in Mi Canvas.
        val themed: Context = if (theme != 0) {
            android.view.ContextThemeWrapper(wrapped, theme)
        } else wrapped
        Log.i(TAG, "host ready apk=$apk theme=$theme")
        return Host(themed, loader, base.resources, PKG)
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context? get() = host?.context

    fun id(name: String): Int = host?.resources?.getIdentifier(name, "id", PKG) ?: 0

    fun layoutId(name: String): Int = host?.resources?.getIdentifier(name, "layout", PKG) ?: 0

    fun inflate(layoutName: String): View? {
        val h = host ?: return null
        val id = layoutId(layoutName)
        if (id == 0) return null
        return runCatching {
            LayoutInflater.from(h.context).inflate(id, null, false)
        }.onFailure { Log.w(TAG, "inflate $layoutName failed: $it") }.getOrNull()
    }

    fun createView(className: String, vararg args: Any?): View? {
        val h = host ?: return null
        return runCatching {
            val cls = Class.forName(className, true, h.loader)
            val ctor = cls.constructors.firstOrNull { it.parameterCount == args.size }
                ?: error("no constructor with ${args.size} args")
            ctor.newInstance(*args) as View
        }.onFailure { Log.w(TAG, "create $className failed: $it") }.getOrNull()
    }

    /** A Mi Canvas slider view, usable as the plain boot-class SeekBar. */
    fun createSeekBar(className: String): android.widget.SeekBar? {
        val h = host ?: return null
        return runCatching {
            val cls = Class.forName(className, true, h.loader)
            val ctor = cls.getConstructor(Context::class.java)
            ctor.newInstance(h.context) as android.widget.SeekBar
        }.onFailure { Log.w(TAG, "seekbar $className failed: $it") }.getOrNull()
    }

    /** Call an obfuscated setter; names are stable for one Mi Canvas build. */
    fun call(view: View, method: String, vararg args: Any?): Boolean {
        val types = args.map {
            when (it) {
                is Int -> Int::class.javaPrimitiveType
                is IntArray -> IntArray::class.java
                is Boolean -> Boolean::class.javaPrimitiveType
                else -> it?.javaClass
            }
        }.toTypedArray()
        return runCatching {
            view.javaClass.getMethod(method, *types).invoke(view, *args)
            true
        }.onFailure { Log.w(TAG, "call $method failed: $it") }.getOrDefault(false)
    }

    /**
     * Mi Canvas' gradient sliders keep their first colour stop as opaque white,
     * which would hide the checkerboard. The presenter normally clears it; we
     * do the same by finding the int field that still holds white.
     */
    fun clearWhiteStop(view: View) {
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != View::class.java) {
            runCatching {
                for (field in cls.declaredFields) {
                    if (field.type != Int::class.javaPrimitiveType) continue
                    field.isAccessible = true
                    if (field.getInt(view) == 0xFFFFFF) field.setInt(view, 0)
                }
            }
            cls = cls.superclass
        }
    }

    /** Debug aid: print the real (obfuscated) method table of a class. */
    fun dumpMethods(className: String) {
        val h = host ?: return
        runCatching {
            var cls: Class<*>? = Class.forName(className, true, h.loader)
            while (cls != null && cls != java.lang.Object::class.java) {
                for (m in cls.declaredMethods) {
                    if (m.parameterTypes.size <= 2) {
                        Log.i(TAG, "METHOD ${cls.simpleName}.${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }}) -> ${m.returnType.simpleName}")
                    }
                }
                cls = cls.superclass
            }
        }.onFailure { Log.w(TAG, "dump $className failed: $it") }
    }

    fun classFor(name: String): Class<*>? =
        runCatching { Class.forName(name, true, host!!.loader) }.getOrNull()
}

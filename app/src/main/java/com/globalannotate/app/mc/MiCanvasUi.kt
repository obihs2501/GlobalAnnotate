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

    /**
     * Gives Mi Canvas' resources a class loader that sees its own dex. Without it
     * `DrawableInflater` falls back to the boot loader, so any drawable that names
     * a class (`<drawable class="miuix...">`) throws InflateException — which is
     * what stopped the colour popup from being built at all.
     *
     * `Resources` keeps the loader in a private field and caches the inflater that
     * was built from it, so both have to be replaced.
     */
    private fun attachClassLoader(res: Resources, loader: ClassLoader): String {
        val cls = Resources::class.java
        val field = runCatching { cls.getDeclaredField("mClassLoader") }.getOrNull() ?: return "none"
        runCatching {
            field.isAccessible = true
            field.set(res, loader)
            runCatching {
                cls.getDeclaredField("mDrawableInflater").apply { isAccessible = true }.set(res, null)
            }
        }.onFailure { Log.w(TAG, "attach class loader failed: $it") }
        val attached = runCatching { cls.getMethod("getClassLoader").invoke(res) }.getOrNull()
        return if (attached === loader) "Resources.mClassLoader" else "none"
    }

    /** Debug aid: find the member that actually carries the resources' class loader. */
    private fun dumpClassLoaderHooks(res: Resources) {
        var cls: Class<*>? = res.javaClass
        while (cls != null && cls != Any::class.java) {
            Log.i(TAG, "res ${cls.name}")
            for (f in cls.declaredFields) Log.i(TAG, "  field ${f.name}: ${f.type.name}")
            for (m in cls.declaredMethods) {
                if (m.name.contains("lass", true)) {
                    Log.i(TAG, "  method ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
            }
            cls = cls.superclass
        }
        val impl = runCatching {
            Resources::class.java.getDeclaredField("mResourcesImpl")
                .apply { isAccessible = true }
                .get(res)
        }.getOrNull()
        Log.i(TAG, "impl=$impl")
        var c: Class<*>? = impl?.javaClass
        while (c != null && c != Any::class.java) {
            Log.i(TAG, "impl ${c.name}")
            for (f in c.declaredFields) Log.i(TAG, "  field ${f.name}: ${f.type.name}")
            for (m in c.declaredMethods) {
                if (m.name.contains("lass", true)) Log.i(TAG, "  method ${m.name}")
            }
            c = c.superclass
        }
    }

    private fun build(): Host {
        val ctx = appContext ?: error("no app context")
        val info = ctx.packageManager.getApplicationInfo(PKG, PackageManager.GET_META_DATA)
        val apk = info.sourceDir
        val loader = DexClassLoader(apk, ctx.codeCacheDir.absolutePath, null, ctx.classLoader)
        val base = ctx.createPackageContext(PKG, 0)
        // XML drawables inside Mi Canvas' resources name classes from its own dex
        // (`miuix.appcompat.app.AdaptRoundButtonDrawable`), and a package context
        // created from another app carries no class loader, so DrawableInflater
        // fell back to the boot loader and the popup failed to inflate.
        val attached = attachClassLoader(base.resources, loader)
        Log.i(TAG, "resources class loader: $attached")
        if (attached == "none") dumpClassLoaderHooks(base.resources)
        // Views resolve their resources through this wrapper; its class loader
        // must be the one that sees Mi Canvas' dex.
        val wrapped = object : ContextWrapper(base) {
            override fun getClassLoader(): ClassLoader = loader
            override fun getResources(): Resources = base.resources
            override fun getAssets() = base.assets
            override fun getPackageName(): String = PKG
            override fun getTheme() = base.theme
            // A package context from another app has no Application object, so
            // ContextWrapper.getApplicationContext() returns null and Mi Canvas'
            // picker manager NPEs on it. This wrapper is a complete context.
            override fun getApplicationContext(): Context = this
            // The system's own inflater belongs to the package context, whose
            // class loader has no dex (code from another app is not included), so
            // <view class="com.xiaomi..."> could not be resolved. Clone it onto a
            // context that carries Mi Canvas' loader and resources.
            private val inflater: LayoutInflater by lazy {
                LayoutInflater.from(base).cloneInContext(this)
            }

            override fun getSystemService(name: String): Any? =
                if (name == Context.LAYOUT_INFLATER_SERVICE) inflater else base.getSystemService(name)
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

    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context? get() = host?.context

    /**
     * Mi Canvas' own colour-picker popup. Its class name keeps the Greek iotas
     * that jadx hides behind `p249l1lII1Il.I11IIil`: the dex really calls it
     * `l1lII1ιιιιIl.I11IιIil`. Constructing it gives the real tab (`miuix
     * FilterSortView`), the real colour board, both rail pages and the recent
     * colour row — everything but the presenter that feeds it.
     */
    fun createColorPopup(): android.widget.PopupWindow? {
        val h = host ?: return null
        return runCatching {
            val cls = Class.forName(COLOR_POPUP_CLASS, true, h.loader)
            val ctor = cls.getConstructor(Context::class.java)
            ctor.newInstance(h.context) as android.widget.PopupWindow
        }.onFailure { Log.e(TAG, "colour popup failed", it) }.getOrNull()
    }

    /** `l1lII1` + 4x U+03B9 + `Il.I11I` + U+03B9 + `Iil`. */
    private const val COLOR_POPUP_CLASS = "l1lII1\u03b9\u03b9\u03b9\u03b9Il.I11I\u03b9Iil"

    fun id(name: String): Int = host?.resources?.getIdentifier(name, "id", PKG) ?: 0

    fun layoutId(name: String): Int = host?.resources?.getIdentifier(name, "layout", PKG) ?: 0

    fun dimension(name: String): Int {
        val h = host ?: return 0
        val id = h.resources.getIdentifier(name, "dimen", PKG)
        return if (id == 0) 0 else h.resources.getDimensionPixelSize(id)
    }

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

package com.globalannotate.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * libxposed API 102 entry point.
 *
 * Registered in META-INF/xposed/java_init.list; scope.list contains `system`,
 * so the framework injects us into system_server and calls
 * [onSystemServerStarting] with the system classloader.
 */
class GlobalAnnotateHook : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        GaLog.sink = { priority, tag, message -> log(priority, tag, message) }
        log(Log.INFO, TAG, "loaded: process=${param.processName}, system=${param.isSystemServer}, api=$apiVersion")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        log(Log.INFO, TAG, "system server starting; classloader=$classLoader")
        val context = systemContext()
        if (context == null) {
            log(Log.ERROR, TAG, "system Context unavailable; hooks not installed")
            return
        }
        val bridge = AppBridge(context)
        registerControlReceiver(context, bridge)
        GestureLauncher.install(this, classLoader, bridge)
        InputRouter.install(this, classLoader, bridge)
        log(Log.INFO, TAG, "installed; idle until SET_MODULE_ENABLED")
    }

    /**
     * system_server has no Application object, so `currentApplication()` is
     * usually null here. Try the system Context paths that actually work in
     * this process, newest first.
     */
    private fun systemContext(): Context? {
        val activityThread = runCatching {
            Class.forName("android.app.ActivityThread")
        }.getOrNull() ?: return null

        runCatching {
            val current = activityThread.getDeclaredMethod("currentActivityThread")
                .apply { isAccessible = true }
                .invoke(null)
            val systemContext = activityThread.getDeclaredMethod("getSystemContext")
                .apply { isAccessible = true }
                .invoke(current) as? Context
            if (systemContext != null) {
                log(Log.INFO, TAG, "context from ActivityThread.getSystemContext()")
                return systemContext
            }
        }.onFailure { log(Log.ERROR, TAG, "getSystemContext failed: $it") }

        runCatching {
            val systemMain = activityThread.getDeclaredMethod("systemMain")
                .apply { isAccessible = true }
                .invoke(null)
            val ctx = activityThread.getDeclaredMethod("getSystemContext")
                .apply { isAccessible = true }
                .invoke(systemMain) as? Context
            if (ctx != null) {
                log(Log.INFO, TAG, "context from ActivityThread.systemMain()")
                return ctx
            }
        }.onFailure { log(Log.ERROR, TAG, "systemMain failed: $it") }

        runCatching {
            val app = activityThread.getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
                .invoke(null) as? Context
            if (app != null) {
                log(Log.INFO, TAG, "context from ActivityThread.currentApplication()")
                return app
            }
        }

        return null
    }
    private fun registerControlReceiver(context: Context, bridge: AppBridge) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getStringExtra(XActions.EXTRA_TOKEN) != XActions.TOKEN) return
                val enabled = intent.getBooleanExtra(XActions.EXTRA_ENABLED, false)
                when (intent.action) {
                    XActions.SET_MODULE_ENABLED -> {
                        ModuleState.enabled = enabled
                        if (enabled) bridge.bindIfNeeded()
                        if (!enabled) {
                            ModuleState.splitMode = false
                            bridge.setSplitActive(false)
                        }
                        log(Log.INFO, TAG, "module enabled=$enabled")
                    }
                    XActions.SET_SPLIT_MODE -> {
                        ModuleState.splitMode = enabled
                        bridge.setSplitActive(enabled)
                        log(Log.INFO, TAG, "split mode=$enabled")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(XActions.SET_MODULE_ENABLED)
            addAction(XActions.SET_SPLIT_MODE)
        }
        runCatching {
            context.registerReceiver(receiver, filter, null, Handler(Looper.getMainLooper()))
        }.onFailure { log(Log.ERROR, TAG, "registerReceiver failed: $it") }
    }

    private companion object {
        const val TAG = "GlobalAnnotate"
    }
}


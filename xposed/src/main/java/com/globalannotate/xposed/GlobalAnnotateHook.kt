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

    private fun systemContext(): Context? = runCatching {
        val thread = Class.forName("android.app.ActivityThread")
        val method = thread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
        method.invoke(null) as? Context
    }.getOrNull()

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


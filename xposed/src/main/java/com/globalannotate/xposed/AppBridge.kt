package com.globalannotate.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.MotionEvent

/** Cross-process link from system_server to the main app. */
class AppBridge(private val context: Context) {

    private var bound = false
    private var lastForwardMs = 0L

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            ModuleState.sink = service
            setSplitActive(ModuleState.splitMode)
            GaLog.i("input sink connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            ModuleState.sink = null
            bound = false
            GaLog.i("input sink disconnected; fail-open")
        }
    }

    fun bindIfNeeded() {
        if (bound) return
        val intent = Intent().setComponent(
            ComponentName(XIpc.SERVICE_PACKAGE, XIpc.SERVICE_CLASS),
        )
        bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure { GaLog.e("bindService failed: $it") }.getOrDefault(false)
        if (!bound) GaLog.e("bindService returned false")
    }

    fun displaySize(): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    fun cornerWidthPx(): Float = CompatProfile.CORNER_WIDTH_DP * context.resources.displayMetrics.density
    fun cornerHeightPx(): Float = CompatProfile.CORNER_HEIGHT_DP * context.resources.displayMetrics.density
    fun swipeThresholdPx(): Float = CompatProfile.SWIPE_THRESHOLD_DP * context.resources.displayMetrics.density

    fun launch() = broadcast(XActions.LAUNCH) { }

    fun gesture(name: String) = broadcast(XActions.GESTURE) {
        putExtra(XActions.EXTRA_GESTURE, name)
    }

    private fun broadcast(action: String, fill: Intent.() -> Unit) {
        val intent = Intent(action)
            .setClassName(XIpc.SERVICE_PACKAGE, XActions.RECEIVER_CLASS)
            .putExtra(XActions.EXTRA_TOKEN, XActions.TOKEN)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        fill(intent)
        runCatching { context.sendBroadcast(intent) }
            .onFailure { GaLog.e("broadcast $action failed: $it") }
    }

    fun setSplitActive(active: Boolean) {
        val binder = ModuleState.sink ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(XIpc.DESCRIPTOR)
            data.writeInt(if (active) 1 else 0)
            binder.transact(XIpc.TX_SET_ACTIVE, data, reply, 0)
            reply.readException()
        } catch (error: Throwable) {
            GaLog.e("setSplitActive failed: $error")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * One-way Binder transaction: filterInputEvent runs on an input thread, so
     * this must never block waiting for the app. MotionEvent is copied first
     * because the framework recycles the original right after dispatch.
     */
    fun forwardStylus(event: MotionEvent) {
        val binder = ModuleState.sink ?: return
        val now = SystemClock.uptimeMillis()
        if (event.actionMasked == MotionEvent.ACTION_MOVE &&
            now - lastForwardMs < CompatProfile.FORWARD_INTERVAL_MS
        ) return
        lastForwardMs = now

        val copy = MotionEvent.obtain(event)
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(XIpc.DESCRIPTOR)
            copy.writeToParcel(data, 0)
            binder.transact(XIpc.TX_STYLUS_EVENT, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: Throwable) {
            GaLog.e("forwardStylus failed: $error")
            ModuleState.sink = null // fail open
        } finally {
            copy.recycle()
            data.recycle()
        }
    }
}

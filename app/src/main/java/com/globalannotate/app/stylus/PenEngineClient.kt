package com.globalannotate.app.stylus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * Talks to HyperOS's pencil-engine service directly over Binder.
 *
 * Why not use MiuiTouchFilmUtils.init()? The SDK only passes our package name to
 * registerListener when the system's engine-version string matches the SDK's
 * expectation, and the developer-center App ID is otherwise required. The
 * touch-film handshake itself is not behind that auth check, so we reproduce the
 * binder protocol from PenEngine 0.3.2 ourselves and always register with the
 * package name. This is what makes 194–197 reach our window without any
 * developer account.
 *
 * Protocol (verified by decompiling PenEngine-release_0.3.2.aar):
 *   IPencilEngine:
 *     1 registerListener(IPencilEngineCallback cb)
 *     2 unregisterListener()
 *     3 int setEnable(int feature, int enable)
 *     4 registerListener(IPencilEngineCallback cb, String packageName)
 *   feature 2 = touch film (barrel gestures)
 */
class PenEngineClient(private val context: Context) {

    companion object {
        private const val TAG = "PenEngineClient"
        private const val DESCRIPTOR = "com.xiaomi.touchservice.pencilengine.IPencilEngine"
        private const val CALLBACK_DESCRIPTOR = "com.xiaomi.touchservice.pencilengine.IPencilEngineCallback"
        private const val TX_REGISTER = 1
        private const val TX_UNREGISTER = 2
        private const val TX_SET_ENABLE = 3
        private const val TX_REGISTER_WITH_PACKAGE = 4
        private const val FEATURE_TOUCH_FILM = 2

        val SERVICE_COMPONENT = ComponentName(
            "com.xiaomi.touchservice",
            "com.xiaomi.touchservice.pencilengine.PencilEngineManagerService",
        )
    }

    var lastEnableResult: Int? = null
        private set
    var lastError: String? = null
        private set

    private val appContext = context.applicationContext
    private var remote: IBinder? = null
    private var bound = false

    private val callback = object : Binder() {
        init { attachInterface(null, CALLBACK_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            if (code == 1) {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                // onColorPicked(what, colorSpace, color) — not used, but must be drained.
                data.readInt(); data.readInt(); data.readInt()
                reply?.writeNoException()
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remote = service
            handshake(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remote = null
        }
    }

    /** @return true when bindService succeeded (handshake result is in [lastEnableResult]). */
    fun connect(): Boolean {
        if (bound) return true
        lastError = null
        lastEnableResult = null
        val intent = Intent().setComponent(SERVICE_COMPONENT)
        bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (error: Throwable) {
            lastError = "bindService threw: $error"
            false
        }
        if (!bound && lastError == null) lastError = "bindService returned false (is com.xiaomi.touchservice installed?)"
        return bound
    }

    fun disconnect() {
        remote?.let { binder ->
            transactSetEnable(binder, FEATURE_TOUCH_FILM, 0)
            transactVoid(binder, TX_UNREGISTER) { }
        }
        remote = null
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }

    private fun handshake(binder: IBinder) {
        val withPackage = transactVoid(binder, TX_REGISTER_WITH_PACKAGE) { data ->
            data.writeStrongBinder(callback)
            data.writeString(appContext.packageName)
        }
        if (!withPackage) {
            transactVoid(binder, TX_REGISTER) { data -> data.writeStrongBinder(callback) }
        }
        lastEnableResult = transactSetEnable(binder, FEATURE_TOUCH_FILM, 1)
        if (lastEnableResult == null) lastError = "setEnable(touchFilm, 1) failed"
    }

    private fun transactSetEnable(binder: IBinder, feature: Int, enable: Int): Int? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(feature)
            data.writeInt(enable)
            if (!binder.transact(TX_SET_ENABLE, data, reply, 0)) {
                null
            } else {
                reply.readException()
                reply.readInt()
            }
        } catch (error: RemoteException) {
            lastError = "setEnable: $error"
            null
        } catch (error: RuntimeException) {
            lastError = "setEnable: $error"
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactVoid(binder: IBinder, code: Int, write: (Parcel) -> Unit): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            val ok = binder.transact(code, data, reply, 0)
            if (ok) reply.readException()
            ok
        } catch (error: RemoteException) {
            lastError = "transaction $code: $error"
            false
        } catch (error: RuntimeException) {
            lastError = "transaction $code: $error"
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

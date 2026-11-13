package com.globalannotate.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.view.MotionEvent

/**
 * Receives stylus MotionEvents rerouted by the LSPosed module.
 *
 * In ROOT_SPLIT mode the overlay window is FLAG_NOT_TOUCHABLE so finger events
 * reach the app underneath. The module hooks the input policy in system_server,
 * consumes stylus events there, and hands them to this service over Binder.
 *
 * Only system_server / root / shell are allowed to bind.
 */
class AnnotationInputService : Service() {

    private val binder = object : Binder() {
        init { attachInterface(null, InputIpc.DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(InputIpc.DESCRIPTOR)
                return true
            }
            return when (code) {
                InputIpc.TX_SET_ACTIVE -> {
                    data.enforceInterface(InputIpc.DESCRIPTOR)
                    Session.rootSplitActive = data.readInt() == 1
                    reply?.writeNoException()
                    true
                }
                InputIpc.TX_STYLUS_EVENT -> {
                    data.enforceInterface(InputIpc.DESCRIPTOR)
                    val event = MotionEvent.CREATOR.createFromParcel(data)
                    val canvas = Session.canvas
                    if (canvas != null && Session.rootSplitActive) {
                        canvas.post {
                            canvas.injectStylusEvent(event)
                            event.recycle()
                        }
                    } else {
                        event.recycle()
                    }
                    reply?.writeNoException()
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        val uid = Binder.getCallingUid()
        return if (uid == Process.SYSTEM_UID || uid == 0 || uid == Process.SHELL_UID) {
            binder
        } else {
            null
        }
    }
}

package com.globalannotate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Explicit broadcasts sent by the LSPosed module (global pen gestures) and by
 * our own notification / QS tile. Delivers to the running service when present,
 * otherwise starts the foreground service as a fallback.
 */
class GestureLaunchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra(Actions.EXTRA_TOKEN) != Actions.TOKEN) return

        Session.service?.let { service ->
            service.handleRemoteAction(intent)
            return
        }

        val forward = Intent(context, AnnotationService::class.java).apply {
            action = intent.action
            putExtras(intent.extras ?: Bundle.EMPTY)
            putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN)
        }
        // Android 12+ restricts background FGS launches. Apps holding
        // SYSTEM_ALERT_WINDOW are exempt, which this app requires anyway.
        runCatching { context.startForegroundService(forward) }
    }
}

package com.globalannotate.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** One-shot permission gate, then starts the always-running overlay service. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            finish()
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        val intent = Intent(this, AnnotationService::class.java)
            .setAction(Actions.LAUNCH)
            .putExtra(Actions.EXTRA_TOKEN, Actions.TOKEN)
        startForegroundService(intent)
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}

package com.globalannotate.xposed

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Minimal launcher activity so the module APK is installable and inspectable. */
class ModuleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.module_status)
                textSize = 16f
                setPadding(48, 64, 48, 64)
            },
        )
    }
}

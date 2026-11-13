// Compile-only stubs for framework classes that are hidden from the SDK.
// Nothing here is packaged: at runtime the real classes come from the boot
// classpath. Only the root helper (started with app_process, where hidden-API
// enforcement does not apply) touches them.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.globalannotate.hiddenapi"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

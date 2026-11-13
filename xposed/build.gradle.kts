
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.globalannotate.xposed"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.globalannotate.xposed"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-api102"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // The libxposed API 102 framework reads java_init.list / module.prop /
    // scope.list from META-INF/xposed inside the APK. AGP must be told to keep
    // those Java resources (this mirrors the official libxposed/example config).
    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        checkReleaseBuilds = false
    }
}

// Modern Xposed API 102. compileOnly because the framework supplies it at
// runtime; no legacy XposedBridge jar is bundled or needed.
dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.androidx.core.ktx)
}

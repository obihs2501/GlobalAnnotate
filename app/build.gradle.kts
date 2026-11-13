
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.globalannotate.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.globalannotate.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    compileOnly(project(":hiddenapi"))
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

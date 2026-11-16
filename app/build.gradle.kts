plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase configuration is optional during the initial build.
// Add app/google-services.json from your Firebase project to enable it.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.samuel.remotemanager"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.samuel.remotemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

// Firebase libraries will be added after the Firebase configuration and
// Kotlin-compatible dependency versions are verified.

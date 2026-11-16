
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.samuel.remotemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samuel.remotemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

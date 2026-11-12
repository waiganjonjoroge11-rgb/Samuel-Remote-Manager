
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.samuel.remotemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samuel.remotemanager"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
}

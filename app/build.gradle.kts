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

    defaultConfig {
        applicationId = "com.samuel.remotemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
}

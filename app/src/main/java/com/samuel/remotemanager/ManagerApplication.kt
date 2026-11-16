package com.samuel.remotemanager

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class ManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyA-_uLuM51qj4CPExd0DWkFkQ6iGrYGfn0")
                .setApplicationId("1:674102252857:web:2b4a16e5103a37d959c4e8")
                .setProjectId("samuel-remote-manager")
                .setDatabaseUrl("https://samuel-remote-manager-default-rtdb.firebaseio.com")
                .setStorageBucket("samuel-remote-manager.firebasestorage.app")
                .setGcmSenderId("674102252857")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
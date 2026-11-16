package com.samuel.remotemanager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

class LocationShareService : Service() {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(71, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences("samuel_remote_manager", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        val deviceRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid).child("devices").child(deviceId)
        deviceRef.child("sharing").setValue(true)
        deviceRef.child("deviceName").setValue(Build.MANUFACTURER + " " + Build.MODEL)
        deviceRef.child("lastSeen").setValue(System.currentTimeMillis())

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000L)
            .setMinUpdateIntervalMillis(10000L)
            .build()
        // A service can receive repeated start commands. Remove the previous listener
        // before registering a new one so the device does not stream duplicate updates.
        callback?.let { fused.removeLocationUpdates(it) }
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                deviceRef.updateChildren(
                    mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracyMeters" to location.accuracy,
                        "lastSeen" to System.currentTimeMillis(),
                        "sharing" to true
                    )
                )
            }
        }
        try {
            fused.requestLocationUpdates(request, callback!!, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { fused.removeLocationUpdates(it) }
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val prefs = getSharedPreferences("samuel_remote_manager", Context.MODE_PRIVATE)
            prefs.getString("device_id", null)?.let { id ->
                FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
                    .child("devices").child(id).child("sharing").setValue(false)
            }
        }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("location_sharing", "Location sharing", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, "location_sharing")
            .setContentTitle("Samuel Remote Manager")
            .setContentText("Location sharing is active. Tap the app to stop sharing.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}

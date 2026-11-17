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
import kotlin.math.roundToInt

class LocationShareService : Service() {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null
    private var lastAccepted: android.location.Location? = null
    private var cumulativeDistanceMeters = 0.0
    private var trackSessionId: String? = null

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
        val existingSession = prefs.getString("active_track_session", null)
        val sessionId = existingSession ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("active_track_session", it).apply()
        }
        trackSessionId = sessionId
        cumulativeDistanceMeters = prefs.getFloat("track_distance_$sessionId", 0f).toDouble()
        val tracksRef = deviceRef.child("locationTracks").child(sessionId)
        if (existingSession == null) {
            tracksRef.child("startedAt").setValue(System.currentTimeMillis())
            tracksRef.child("status").setValue("recording")
        }
        deviceRef.updateChildren(mapOf(
            "sharing" to true,
            "deviceName" to (Build.MANUFACTURER + " " + Build.MODEL),
            "lastSeen" to System.currentTimeMillis(),
            "trackSessionId" to sessionId,
            "trackStartedAt" to (prefs.getLong("track_started_$sessionId", 0L).takeIf { it > 0L } ?: System.currentTimeMillis())
        ))
        if (!prefs.contains("track_started_$sessionId")) {
            prefs.edit().putLong("track_started_$sessionId", System.currentTimeMillis()).apply()
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        // Repeated start commands must not create duplicate GPS listeners.
        callback?.let { fused.removeLocationUpdates(it) }
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    if (location.accuracy > 50f) return@forEach
                    val previous = lastAccepted
                    val elapsedSeconds = if (previous != null) (location.time - previous.time) / 1000.0 else 0.0
                    val segmentMeters = if (previous != null && elapsedSeconds > 0.0) previous.distanceTo(location).toDouble() else 0.0
                    // Ignore implausible GPS jumps rather than inflating the trip distance.
                    val validSegment = segmentMeters in 0.0..250.0 && elapsedSeconds in 0.0..300.0
                    if (previous != null && validSegment) cumulativeDistanceMeters += segmentMeters
                    val speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble()
                        else if (validSegment && elapsedSeconds > 0.0) segmentMeters / elapsedSeconds else 0.0
                    val speedKmh = (speedMetersPerSecond * 3.6).coerceIn(0.0, 250.0)
                    val now = System.currentTimeMillis()
                    val point = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracyMeters" to location.accuracy,
                        "speedKmh" to speedKmh,
                        "speedMetersPerSecond" to speedMetersPerSecond,
                        "altitudeMeters" to if (location.hasAltitude()) location.altitude else null,
                        "bearingDegrees" to if (location.hasBearing()) location.bearing else null,
                        "recordedAt" to now
                    )
                    val session = trackSessionId ?: return@forEach
                    tracksRefPoint(user.uid, deviceId, session).push().setValue(point)
                    deviceRef.updateChildren(mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "accuracyMeters" to location.accuracy,
                        "speedKmh" to speedKmh,
                        "distanceMeters" to cumulativeDistanceMeters,
                        "lastSeen" to now,
                        "sharing" to true
                    ))
                    prefs.edit().putFloat("track_distance_$session", cumulativeDistanceMeters.toFloat()).apply()
                    lastAccepted = location
                    updateNotification(speedKmh.roundToInt(), (cumulativeDistanceMeters / 1000.0))
                }
            }
        }
        try {
            fused.requestLocationUpdates(request, callback!!, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun tracksRefPoint(uid: String, deviceId: String, sessionId: String) =
        FirebaseDatabase.getInstance().reference.child("users").child(uid).child("devices")
            .child(deviceId).child("locationTracks").child(sessionId).child("points")

    override fun onDestroy() {
        callback?.let { fused.removeLocationUpdates(it) }
        val user = FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("samuel_remote_manager", Context.MODE_PRIVATE)
        if (user != null) {
            prefs.getString("device_id", null)?.let { id ->
                val deviceRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
                    .child("devices").child(id)
                deviceRef.updateChildren(mapOf("sharing" to false, "speedKmh" to 0.0))
                trackSessionId?.let { session ->
                    deviceRef.child("locationTracks").child(session).updateChildren(
                        mapOf("status" to "stopped", "endedAt" to System.currentTimeMillis())
                    )
                    prefs.edit().remove("active_track_session").apply()
                }
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
            .setContentText("GPS movement tracking is active. Tap the app to stop tracking.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun updateNotification(speedKmh: Int, distanceKm: Double) {
        val updated = NotificationCompat.Builder(this, "location_sharing")
            .setContentTitle("Samuel Remote Manager · Tracking active")
            .setContentText("Speed: $speedKmh km/h · Distance: %.2f km".format(distanceKm))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(71, updated)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

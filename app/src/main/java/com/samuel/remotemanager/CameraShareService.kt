package com.samuel.remotemanager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraShareService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.samuel.remotemanager.CAMERA_START"
        const val ACTION_STOP = "com.samuel.remotemanager.CAMERA_STOP"
        const val ACTION_SWITCH_FRONT = "com.samuel.remotemanager.CAMERA_SWITCH_FRONT"
        const val ACTION_SWITCH_BACK = "com.samuel.remotemanager.CAMERA_SWITCH_BACK"
        const val LENS_FRONT = "front"
        const val LENS_BACK = "back"
        private const val CHANNEL_ID = "camera_sharing"
        private const val NOTIFICATION_ID = 4102
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var databasePath: String? = null
    private var running = false
    private var selectedLens = LENS_BACK

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSharing()
            ACTION_SWITCH_FRONT -> { selectedLens = LENS_FRONT; startSharing(startId) }
            ACTION_SWITCH_BACK -> { selectedLens = LENS_BACK; startSharing(startId) }
            ACTION_START, null -> startSharing(startId)
        }
        return START_NOT_STICKY
    }

    private fun startSharing(startId: Int) {
        if (running) { bindSelectedCamera(); updateNotification("Camera active · ${selectedLens} camera"); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId)
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            stopSelf(startId)
            return
        }
        val prefs = getSharedPreferences("samuel_remote_manager", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
        databasePath = "users/${user.uid}/devices/$deviceId/camera"

        createNotificationChannel()
        val notification = buildNotification("Camera sharing is active")
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            stopSelf(startId)
            return
        }

        running = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider
                bindSelectedCamera()
                captureNextFrame()
            } catch (_: Exception) {
                updateNotification("Camera could not start. Open the app to check permissions.")
                stopSharing()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindSelectedCamera() {
        val provider = cameraProvider ?: return
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(55)
            .build()
        imageCapture = capture
        provider.unbindAll()
        val selector = if (selectedLens == LENS_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        provider.bindToLifecycle(this, selector, capture)
    }

    private fun captureNextFrame() {
        if (!running) return
        val capture = imageCapture ?: return
        val file = File(cacheDir, "background-camera.jpg")
        val options = OutputFileOptions.Builder(file).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    val bytes = file.readBytes()
                    if (bytes.isEmpty()) throw IllegalStateException("Camera returned an empty image")
                    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val path = databasePath ?: throw IllegalStateException("Device archive path is unavailable")
                    val ref = FirebaseDatabase.getInstance().reference.child(path)
                    val capturedAt = System.currentTimeMillis()
                    val archive = ref.child("archive")
                    val archiveKey = archive.push().key
                        ?: throw IllegalStateException("Could not create a snapshot archive ID")

                    // Save the archive entry first. Only report a successful cloud snapshot
                    // after Realtime Database confirms the write.
                    archive.child(archiveKey).setValue(
                        mapOf(
                            "frame" to encoded,
                            "capturedAt" to capturedAt,
                            "contentType" to "image/jpeg",
                            "sizeBytes" to bytes.size
                        )
                    ).addOnSuccessListener {
                        if (!running) return@addOnSuccessListener
                        ref.child("frame").setValue(encoded)
                        ref.child("updatedAt").setValue(capturedAt)
                        ref.child("active").setValue(true)
                        updateNotification("Camera active · snapshot saved to cloud")

                        // Keep only the 30 newest snapshots for this device.
                        archive.orderByChild("capturedAt").limitToLast(30).get()
                            .addOnSuccessListener { recent ->
                                val keep = recent.children.mapNotNull { it.key }.toSet()
                                archive.get().addOnSuccessListener { all ->
                                    all.children.forEach { item ->
                                        if (item.key !in keep) item.ref.removeValue()
                                    }
                                }
                            }
                    }.addOnFailureListener {
                        if (running) updateNotification("Cloud snapshot save failed · check internet and Firebase rules")
                    }
                } catch (_: Exception) {
                    if (running) updateNotification("Snapshot could not be saved · check connection")
                } finally {
                    file.delete()
                }
                handler.postDelayed({ captureNextFrame() }, 5000)
            }

            override fun onError(error: ImageCaptureException) {
                if (running) updateNotification("Camera capture failed · sharing will retry")
                handler.postDelayed({ captureNextFrame() }, 7000)
            }
        })
    }

    private fun stopSharing() {
        if (!running) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        running = false
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        imageCapture = null
        databasePath?.let { path ->
            val ref = FirebaseDatabase.getInstance().reference.child(path)
            ref.child("active").setValue(false)
            ref.child("frame").removeValue()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Camera sharing", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 4103,
            Intent(this, CameraShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 4104, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Samuel Remote Manager · Camera ON")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop camera", stopIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        imageCapture = null
        // Clear the live indicator and the last frame whenever Android stops this service,
        // including when the owner uses the system/app stop control.
        databasePath?.let { path ->
            val ref = FirebaseDatabase.getInstance().reference.child(path)
            ref.child("active").setValue(false)
            ref.child("frame").removeValue()
        }
        executor.shutdown()
        super.onDestroy()
    }
}
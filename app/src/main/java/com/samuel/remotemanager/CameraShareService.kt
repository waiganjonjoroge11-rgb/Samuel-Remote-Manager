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
        private const val CHANNEL_ID = "camera_sharing"
        private const val NOTIFICATION_ID = 4102
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var databasePath: String? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSharing()
            ACTION_START, null -> startSharing(startId)
        }
        return START_NOT_STICKY
    }

    private fun startSharing(startId: Int) {
        if (running) return
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
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(55)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
                captureNextFrame()
            } catch (_: Exception) {
                updateNotification("Camera could not start. Open the app to check permissions.")
                stopSharing()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureNextFrame() {
        if (!running) return
        val capture = imageCapture ?: return
        val file = File(cacheDir, "background-camera.jpg")
        val options = OutputFileOptions.Builder(file).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    val ref = FirebaseDatabase.getInstance().reference.child(databasePath ?: return)
                    ref.child("frame").setValue(encoded)
                    ref.child("updatedAt").setValue(System.currentTimeMillis())
                    ref.child("active").setValue(true)
                } catch (_: Exception) {
                } finally {
                    file.delete()
                }
                handler.postDelayed({ captureNextFrame() }, 5000)
            }

            override fun onError(error: ImageCaptureException) {
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
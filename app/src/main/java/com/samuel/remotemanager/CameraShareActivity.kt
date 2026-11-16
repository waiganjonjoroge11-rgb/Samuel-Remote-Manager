package com.samuel.remotemanager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class CameraShareActivity : Activity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var capture: ImageCapture? = null
    private var active = false
    private var path: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 24, 16, 16) }
        root.addView(TextView(this).apply { text = "OWNER-CONTROLLED CAMERA SHARING"; textSize = 20f })
        status = TextView(this).apply { text = "Grant camera permission, then tap Start. Images are not sent before you start."; setPadding(0, 12, 0, 12) }
        root.addView(status)
        preview = PreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        toggle = Button(this).apply { text = "Start camera sharing"; isEnabled = false; setOnClickListener { if (active) stopSharing() else startSharing() } }
        root.addView(toggle)
        root.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        setContentView(root)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { status.text = "Sign in first."; return }
        val prefs = getSharedPreferences("samuel_remote_manager", Context.MODE_PRIVATE)
        val id = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
        path = "users/${user.uid}/devices/$id/camera"
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 811)
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val imageCaptureUseCase = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(50).build()
                capture = imageCaptureUseCase
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, imageCaptureUseCase)
                status.text = "Local preview ready. Tap Start to send periodic still images to your signed-in website."
                toggle.isEnabled = true
            } catch (e: Exception) { status.text = "Camera unavailable: ${e.localizedMessage}" }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 811 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) bindCamera()
        else status.text = "Camera permission denied. Sharing is disabled."
    }

    private fun startSharing() {
        active = true
        toggle.text = "Stop camera sharing"
        status.text = "Sharing is ON. Keep this screen open; the Android camera indicator remains visible."
        sendFrame()
    }

    private fun sendFrame() {
        if (!active) return
        val file = File(cacheDir, "shared-camera.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture?.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                try {
                    val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    runOnUiThread {
                        if (active) {
                            val ref = FirebaseDatabase.getInstance().reference.child(path!!)
                            ref.child("frame").setValue(encoded)
                            ref.child("updatedAt").setValue(System.currentTimeMillis())
                            ref.child("active").setValue(true)
                            status.text = "Camera snapshots sharing · " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        }
                    }
                } catch (_: Exception) { }
                file.delete()
                handler.postDelayed({ sendFrame() }, 3000)
            }
            override fun onError(error: ImageCaptureException) {
                runOnUiThread { status.text = "Capture failed: ${error.message}" }
                handler.postDelayed({ sendFrame() }, 4000)
            }
        })
    }

    private fun stopSharing() {
        active = false
        handler.removeCallbacksAndMessages(null)
        path?.let { FirebaseDatabase.getInstance().reference.child(it).child("active").setValue(false) }
        toggle.text = "Start camera sharing"
        status.text = "Camera sharing stopped."
    }

    override fun onDestroy() {
        stopSharing()
        executor.shutdown()
        super.onDestroy()
    }
}

package com.samuel.remotemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class CameraShareActivity : ComponentActivity() {
    companion object { const val EXTRA_REQUESTED_LENS = "requested_camera_lens" }
    private var requestedLens: String? = null
    private lateinit var status: TextView
    private lateinit var startButton: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) startBackgroundService()
        else status.text = "Camera permission is required. Sharing has not started."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedLens = intent.getStringExtra(EXTRA_REQUESTED_LENS)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "OWNER-CONTROLLED BACKGROUND CAMERA"
            textSize = 20f
        })
        status = TextView(this).apply {
            text = if (requestedLens == null) "Choose front or rear camera. Camera sharing requires your approval and remains visibly active." else "Your dashboard requested the ${requestedLens} camera. Tap Approve to switch. Camera use remains visible."
            textSize = 16f
            setPadding(0, 20, 0, 20)
        }
        root.addView(status)
        startButton = Button(this).apply {
            text = if (requestedLens == null) "Start rear camera sharing" else "Approve camera switch"
            setOnClickListener { requestAndStart() }
        }
        root.addView(startButton)
        root.addView(Button(this).apply { text = "Switch to front camera"; setOnClickListener { switchCamera(CameraShareService.LENS_FRONT) } })
        root.addView(Button(this).apply { text = "Switch to rear camera"; setOnClickListener { switchCamera(CameraShareService.LENS_BACK) } })
        root.addView(Button(this).apply {
            text = "Stop Camera Sharing"
            setOnClickListener {
                stopService(Intent(this@CameraShareActivity, CameraShareService::class.java))
                status.text = "Camera sharing stop requested."
            }
        })
        root.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun requestAndStart() {
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startBackgroundService()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun switchCamera(lens: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestedLens = lens
            requestAndStart()
            return
        }
        val intent = Intent(this, CameraShareService::class.java).apply {
            action = if (lens == CameraShareService.LENS_FRONT) CameraShareService.ACTION_SWITCH_FRONT else CameraShareService.ACTION_SWITCH_BACK
        }
        ContextCompat.startForegroundService(this, intent)
        status.text = "Switch request sent. Camera remains visibly active."
    }

    private fun startBackgroundService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission is not granted."
            return
        }
        val intent = Intent(this, CameraShareService::class.java).apply {
            action = when (requestedLens) { CameraShareService.LENS_FRONT -> CameraShareService.ACTION_SWITCH_FRONT; CameraShareService.LENS_BACK -> CameraShareService.ACTION_SWITCH_BACK; else -> CameraShareService.ACTION_START }
        }
        ContextCompat.startForegroundService(this, intent)
        status.text = "Background camera started. Keep the visible camera notification active; use it or this screen to stop sharing."
        startButton.isEnabled = false
    }
}
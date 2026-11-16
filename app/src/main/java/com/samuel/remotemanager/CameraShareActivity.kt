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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "OWNER-CONTROLLED BACKGROUND CAMERA"
            textSize = 20f
        })
        status = TextView(this).apply {
            text = "Tap Start to activate the camera. A persistent Android notification will remain visible while sharing."
            textSize = 16f
            setPadding(0, 20, 0, 20)
        }
        root.addView(status)
        startButton = Button(this).apply {
            text = "Start Background Camera"
            setOnClickListener { requestAndStart() }
        }
        root.addView(startButton)
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

    private fun startBackgroundService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission is not granted."
            return
        }
        val intent = Intent(this, CameraShareService::class.java).apply {
            action = CameraShareService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        status.text = "Background camera started. Keep the visible camera notification active; use it or this screen to stop sharing."
        startButton.isEnabled = false
    }
}
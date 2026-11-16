package com.samuel.remotemanager

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "SAMUEL REMOTE MANAGER"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Device manager is running"
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val cameraButton = Button(this).apply {
            text = "Enable Camera Permission"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            }
        }

        val microphoneButton = Button(this).apply {
            text = "Enable Microphone Permission"
            setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(cameraButton)
        layout.addView(microphoneButton)
        setContentView(layout)
    }

    companion object {
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_MICROPHONE = 101
    }
}

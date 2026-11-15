
package com.samuel.remotemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        val title = TextView(this).apply {
            text = "SAMUEL REMOTE MANAGER"
            textSize = 24f
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "Secure Android Device Manager"
            textSize = 16f
        }
        layout.addView(subtitle)

        addButton(layout, "SMS MANAGEMENT") {
            Toast.makeText(this, "SMS management setup next", Toast.LENGTH_SHORT).show()
        }

        addButton(layout, "TAKE PHOTO") {
            Toast.makeText(this, "Camera setup next", Toast.LENGTH_SHORT).show()
        }

        addButton(layout, "RECORD VIDEO") {
            Toast.makeText(this, "Video setup next", Toast.LENGTH_SHORT).show()
        }

        addButton(layout, "DEVICE STATUS") {
            Toast.makeText(this, "Device status selected", Toast.LENGTH_SHORT).show()
        }

        setContentView(layout)
    }

    private fun addButton(
        layout: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        layout.addView(button)
    }
}

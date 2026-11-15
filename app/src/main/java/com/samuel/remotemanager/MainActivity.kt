
package com.samuel.remotemanager

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {

    ove
package com.samuel.remotemanager

import android.app.Activity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(30, 30, 30, 30)

        val title = TextView(this)
        title.text = "SAMUEL REMOTE MANAGER"
        title.textSize = 24f

        val status = TextView(this)
        status.text = "Device manager is running"
        status.textSize = 18f

        val cameraButton = Button(this)
        cameraButton.text = "Enable Camera Permission"

        cameraButton.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA), 100
            )
        }

        val micButton = Button(this)
        micButton.text = "Enable Microphone Permission"

        micButton.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 101
            )
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(cameraButton)
        layout.addView(micButton)

        setContentView(layout)
    }
}

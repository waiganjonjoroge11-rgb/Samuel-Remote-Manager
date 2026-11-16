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
import com.google.firebase.auth.FirebaseAuth

class AudioRecordActivity : ComponentActivity() {
    companion object {
        const val EXTRA_APPROVED_REMOTE_START = "com.samuel.remotemanager.APPROVED_REMOTE_AUDIO_START"
    }
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val mic = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) startRecordingService() else status.text = "Microphone permission denied. Nothing was recorded."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24) }
        root.addView(TextView(this).apply { text="OWNER-APPROVED AUDIO RECORDING"; textSize=20f })
        status = TextView(this).apply {
            text="Recording starts only when you tap Start. A persistent Android notification and microphone indicator remain visible. Recordings upload to your signed-in cloud account when you stop."
            textSize=16f; setPadding(0,20,0,20)
        }
        root.addView(status)
        startButton = Button(this).apply { text="Start audio recording"; setOnClickListener { requestAndStart() } }
        root.addView(startButton)
        root.addView(Button(this).apply { text="Stop and upload recording"; setOnClickListener {
            startService(Intent(this@AudioRecordActivity, AudioRecordService::class.java).setAction(AudioRecordService.ACTION_STOP))
            status.text="Stopping and uploading the recording to your cloud archive…"
            startButton.isEnabled=true
        } })
        root.addView(Button(this).apply { text="Close"; setOnClickListener { finish() } })
        setContentView(root)
        // A remote request starts capture only after the owner approves the on-phone dialog.
        if (intent.getBooleanExtra(EXTRA_APPROVED_REMOTE_START, false)) requestAndStart()
    }
    private fun requestAndStart() {
        if (FirebaseAuth.getInstance().currentUser == null) { status.text="Sign in to Samuel Remote Manager before recording."; return }
        val needed=mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing=needed.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startRecordingService() else permissionLauncher.launch(missing.toTypedArray())
    }
    private fun startRecordingService() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            status.text="Microphone permission is not granted."; return
        }
        ContextCompat.startForegroundService(this,Intent(this,AudioRecordService::class.java).setAction(AudioRecordService.ACTION_START))
        status.text="RECORDING IS ON. The microphone notification remains visible. Tap Stop and upload when finished."
        startButton.isEnabled=false
    }
}

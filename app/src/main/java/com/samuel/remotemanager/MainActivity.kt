package com.samuel.remotemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID

class MainActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var status: TextView
    private lateinit var email: EditText
    private lateinit var password: EditText
    private var remoteCommandRef: DatabaseReference? = null
    private var remoteCommandListener: ValueEventListener? = null
    private var lastPromptedRequestId: String? = null
    private var audioCommandRef: DatabaseReference? = null
    private var audioCommandListener: ValueEventListener? = null
    private var lastPromptedAudioRequestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen()
        handleSharedText(intent)
    }
    override fun onResume() {
        super.onResume()
        listenForRemoteStartRequest()
        listenForRemoteAudioRequest()
    }
    override fun onPause() {
        stopRemoteCommandListener()
        stopRemoteAudioCommandListener()
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleSharedText(intent)
    }
    private fun listenForRemoteStartRequest() {
        stopRemoteCommandListener()
        val user = auth.currentUser ?: return
        val prefs = getSharedPreferences("samuel_remote_manager", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
        remoteCommandRef = FirebaseDatabase.getInstance().reference
            .child("users").child(user.uid).child("devices").child(deviceId)
            .child("remoteCommands").child("startCamera")
        remoteCommandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val command = snapshot.value as? Map<*, *> ?: return
                if (command["status"] != "pending") return
                val requestId = command["requestId"]?.toString() ?: return
                if (requestId == lastPromptedRequestId || isFinishing || isDestroyed) return
                lastPromptedRequestId = requestId
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Remote camera request")
                    .setMessage("Your dashboard is asking to open the camera-sharing screen. Nothing will be captured unless you approve and tap Start. Camera permission and a visible notification are required.")
                    .setNegativeButton("Decline") { _, _ ->
                        remoteCommandRef?.updateChildren(mapOf("status" to "declined", "respondedAt" to System.currentTimeMillis()))
                    }
                    .setPositiveButton("Review request") { _, _ ->
                        remoteCommandRef?.updateChildren(mapOf("status" to "accepted", "respondedAt" to System.currentTimeMillis()))
                        startActivity(Intent(this@MainActivity, CameraShareActivity::class.java))
                    }
                    .setOnCancelListener {
                        remoteCommandRef?.updateChildren(mapOf("status" to "declined", "respondedAt" to System.currentTimeMillis()))
                    }
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Remote request check failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        remoteCommandRef?.addValueEventListener(remoteCommandListener!!)
    }

    private fun listenForRemoteAudioRequest() {
        stopRemoteAudioCommandListener()
        val user = auth.currentUser ?: return
        val prefs = getSharedPreferences("samuel_remote_manager", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
        audioCommandRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
            .child("devices").child(deviceId).child("remoteCommands").child("startAudio")
        audioCommandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val command = snapshot.value as? Map<*, *> ?: return
                if (command["status"] != "pending") return
                val requestId = command["requestId"]?.toString() ?: return
                if (requestId == lastPromptedAudioRequestId || isFinishing || isDestroyed) return
                lastPromptedAudioRequestId = requestId
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Remote audio recording request")
                    .setMessage("Your dashboard requests an audio recording. Nothing starts unless you approve and tap Start. The microphone will be visibly active with an ongoing notification.")
                    .setNegativeButton("Decline") { _, _ -> audioCommandRef?.updateChildren(mapOf("status" to "declined", "respondedAt" to System.currentTimeMillis())) }
                    .setPositiveButton("Review on phone") { _, _ ->
                        audioCommandRef?.updateChildren(mapOf("status" to "accepted", "respondedAt" to System.currentTimeMillis()))
                        startActivity(Intent(this@MainActivity, AudioRecordActivity::class.java)
                            .putExtra(AudioRecordActivity.EXTRA_APPROVED_REMOTE_START, true))
                    }
                    .setOnCancelListener { audioCommandRef?.updateChildren(mapOf("status" to "declined", "respondedAt" to System.currentTimeMillis())) }
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Audio request check failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        audioCommandRef?.addValueEventListener(audioCommandListener!!)
    }
    private fun stopRemoteAudioCommandListener() {
        val ref = audioCommandRef
        val listener = audioCommandListener
        if (ref != null && listener != null) ref.removeEventListener(listener)
        audioCommandRef = null
        audioCommandListener = null
    }

    private fun stopRemoteCommandListener() {
        val ref = remoteCommandRef
        val listener = remoteCommandListener
        if (ref != null && listener != null) ref.removeEventListener(listener)
        remoteCommandRef = null
        remoteCommandListener = null
    }
    private fun handleSharedText(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND || source.type != "text/plain") return
        val shared = source.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isBlank()) return
        if (auth.currentUser == null) {
            Toast.makeText(this, "Sign in first, then share the selected message again.", Toast.LENGTH_LONG).show(); return
        }
        AlertDialog.Builder(this).setTitle("Share selected message")
            .setMessage("Send this text to your own dashboard?\\n\\n$shared")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share") { _, _ ->
                val uid = auth.currentUser?.uid ?: return@setPositiveButton
                val item = mapOf("text" to shared, "sharedAt" to System.currentTimeMillis(), "source" to "user_shared")
                FirebaseDatabase.getInstance().reference.child("users").child(uid).child("sharedMessages").push().setValue(item)
                    .addOnSuccessListener { Toast.makeText(this, "Selected text shared with your dashboard.", Toast.LENGTH_LONG).show() }
                    .addOnFailureListener { Toast.makeText(this, it.localizedMessage ?: "Could not share text.", Toast.LENGTH_LONG).show() }
            }.show()
    }
    private fun showScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(28,36,28,28) }
        root.addView(TextView(this).apply { text="SAMUEL REMOTE MANAGER"; textSize=24f; gravity=Gravity.CENTER })
        status = TextView(this).apply {
            text=if(auth.currentUser==null) "Sign in on this phone and on your web dashboard using the same account." else "Signed in: ${auth.currentUser?.email}. Keep this app open to receive dashboard requests."
            textSize=15f; gravity=Gravity.CENTER; setPadding(0,20,0,20)
        }
        root.addView(status)
        if(auth.currentUser==null) {
            email=EditText(this).apply { hint="Email address"; inputType=33 }; password=EditText(this).apply { hint="Password (at least 6 characters)"; inputType=129 }
            root.addView(email); root.addView(password)
            root.addView(Button(this).apply { text="Create account"; setOnClickListener { authenticate(true) } })
            root.addView(Button(this).apply { text="Sign in"; setOnClickListener { authenticate(false) } })
        } else {
            root.addView(Button(this).apply { text="Open SMS requests"; setOnClickListener { startActivity(Intent(this@MainActivity,SmsRequestsActivity::class.java)) } })
            root.addView(TextView(this).apply { text="To share a received message: open it in Messages, choose Share, select Samuel Remote Manager, review it, then confirm."; textSize=13f; setPadding(0,8,0,12) })
            root.addView(Button(this).apply { text="Start sharing my location"; setOnClickListener { requestLocationAndStart() } })
            root.addView(Button(this).apply { text="Open camera sharing"; setOnClickListener { startActivity(Intent(this@MainActivity,CameraShareActivity::class.java)) } })
            root.addView(Button(this).apply { text="Open audio recorder"; setOnClickListener { startActivity(Intent(this@MainActivity,AudioRecordActivity::class.java)) } })
            root.addView(Button(this).apply { text="Stop location sharing"; setOnClickListener { stopService(Intent(this@MainActivity,LocationShareService::class.java)); status.text="Location sharing stopped." } })
            root.addView(Button(this).apply { text="Sign out"; setOnClickListener {
                stopRemoteCommandListener()
                stopRemoteAudioCommandListener()
                stopService(Intent(this@MainActivity,AudioRecordService::class.java))
                stopService(Intent(this@MainActivity,LocationShareService::class.java)); stopService(Intent(this@MainActivity,CameraShareService::class.java)); auth.signOut(); showScreen()
            } })
        }
        setContentView(root)
    }
    private fun authenticate(create:Boolean) {
        val e=email.text.toString().trim(); val p=password.text.toString()
        if(e.isBlank()||p.length<6){Toast.makeText(this,"Enter a valid email and a password of at least 6 characters.",Toast.LENGTH_LONG).show();return}
        val task=if(create) auth.createUserWithEmailAndPassword(e,p) else auth.signInWithEmailAndPassword(e,p)
        task.addOnSuccessListener { Toast.makeText(this,"Signed in successfully.",Toast.LENGTH_SHORT).show();showScreen();listenForRemoteStartRequest();listenForRemoteAudioRequest() }
            .addOnFailureListener { Toast.makeText(this,it.localizedMessage?:"Authentication failed.",Toast.LENGTH_LONG).show() }
    }
    private fun requestLocationAndStart() {
        val permissions=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        val missing=permissions.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()){ActivityCompat.requestPermissions(this,missing.toTypedArray(),700);return}
        ContextCompat.startForegroundService(this,Intent(this,LocationShareService::class.java));status.text="Location sharing is ON. A permanent notification will remain visible."
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==700 && (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)) {
            ContextCompat.startForegroundService(this,Intent(this,LocationShareService::class.java));status.text="Location sharing is ON. A permanent notification will remain visible."
        } else if(requestCode==700) Toast.makeText(this,"Location permission is required to share location.",Toast.LENGTH_LONG).show()
    }
}

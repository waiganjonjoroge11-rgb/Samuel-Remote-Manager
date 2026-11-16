package com.samuel.remotemanager

import android.Manifest
import android.app.Activity
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
import com.google.firebase.database.FirebaseDatabase

class MainActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var status: TextView
    private lateinit var email: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen()
        handleSharedText(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleSharedText(intent)
    }
    private fun handleSharedText(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND || source.type != "text/plain") return
        val shared = source.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isBlank()) return
        if (auth.currentUser == null) {
            Toast.makeText(this, "Sign in first, then share the selected message again.", Toast.LENGTH_LONG).show(); return
        }
        android.app.AlertDialog.Builder(this).setTitle("Share selected message")
            .setMessage("Send this text to your own dashboard?\n\n$shared")
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
            text=if(auth.currentUser==null) "Sign in on this phone and on your web dashboard using the same account." else "Signed in: ${auth.currentUser?.email}"
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
            root.addView(Button(this).apply { text="Stop location sharing"; setOnClickListener { stopService(Intent(this@MainActivity,LocationShareService::class.java)); status.text="Location sharing stopped." } })
            root.addView(Button(this).apply { text="Sign out"; setOnClickListener {
                stopService(Intent(this@MainActivity,LocationShareService::class.java)); stopService(Intent(this@MainActivity,CameraShareService::class.java)); auth.signOut(); showScreen()
            } })
        }
        setContentView(root)
    }
    private fun authenticate(create:Boolean) {
        val e=email.text.toString().trim(); val p=password.text.toString()
        if(e.isBlank()||p.length<6){Toast.makeText(this,"Enter a valid email and a password of at least 6 characters.",Toast.LENGTH_LONG).show();return}
        val task=if(create) auth.createUserWithEmailAndPassword(e,p) else auth.signInWithEmailAndPassword(e,p)
        task.addOnSuccessListener { Toast.makeText(this,"Signed in successfully.",Toast.LENGTH_SHORT).show();showScreen() }
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
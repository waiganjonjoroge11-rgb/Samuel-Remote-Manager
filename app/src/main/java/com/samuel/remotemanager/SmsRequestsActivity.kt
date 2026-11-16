package com.samuel.remotemanager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SmsRequestsActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val requests by lazy { FirebaseDatabase.getInstance().reference.child("users").child(auth.currentUser?.uid ?: "missing").child("smsRequests") }
    private lateinit var list: LinearLayout
    private var listener: ChildEventListener? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(22,28,22,22) }
        root.addView(TextView(this).apply { text="SMS REQUESTS";textSize=22f })
        root.addView(TextView(this).apply { text="Review each request. Continue opens your SMS app with the recipient and message filled in; you must press Send there.";textSize=14f;setPadding(0,10,0,16) })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(list,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply { text="Close";setOnClickListener { finish() } })
        setContentView(root); listen()
    }
    private fun listen() {
        listener=object:ChildEventListener {
            override fun onChildAdded(s:DataSnapshot,p:String?)=showRequest(s)
            override fun onChildChanged(s:DataSnapshot,p:String?) {}
            override fun onChildRemoved(s:DataSnapshot) {}
            override fun onChildMoved(s:DataSnapshot,p:String?) {}
            override fun onCancelled(e:DatabaseError) { Toast.makeText(this@SmsRequestsActivity,e.message,Toast.LENGTH_LONG).show() }
        }
        requests.orderByChild("status").equalTo("pending").addChildEventListener(listener!!)
    }
    private fun showRequest(s:DataSnapshot) {
        val number=s.child("phoneNumber").getValue(String::class.java).orEmpty()
        val body=s.child("message").getValue(String::class.java).orEmpty()
        val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(14,14,14,14) }
        card.addView(TextView(this).apply { text="To: $number\n\n$body";textSize=16f })
        card.addView(Button(this).apply { text="Review in SMS app";setOnClickListener {
            if(number.isBlank()||body.isBlank()){Toast.makeText(this@SmsRequestsActivity,"Incomplete request.",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            val i=Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number))).putExtra("sms_body",body)
            try { startActivity(i);s.ref.child("status").setValue("opened_in_sms_app");s.ref.child("openedAt").setValue(System.currentTimeMillis()) }
            catch(_:Exception){Toast.makeText(this@SmsRequestsActivity,"No SMS app is available.",Toast.LENGTH_LONG).show()}
        } })
        card.addView(TextView(this).apply { text="You must review and press Send in the SMS app.";textSize=12f })
        list.addView(card)
    }
    override fun onDestroy(){listener?.let{requests.removeEventListener(it)};super.onDestroy()}
}
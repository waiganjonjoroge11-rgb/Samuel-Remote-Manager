package com.samuel.remotemanager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID

class AudioRecordService : LifecycleService() {
    companion object {
        const val ACTION_START="com.samuel.remotemanager.AUDIO_START"
        const val ACTION_STOP="com.samuel.remotemanager.AUDIO_STOP"
        private const val CHANNEL="audio_recording"
        private const val NOTIFICATION_ID=4201
    }
    private var recorder: MediaRecorder?=null
    private var output: File?=null
    private var startedAt=0L
    private var uid: String?=null
    private var deviceId: String?=null
    private var stopping=false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent,flags,startId)
        when(intent?.action) {
            ACTION_STOP -> stopAndUpload()
            ACTION_START, null -> startRecording(startId)
        }
        return START_NOT_STICKY
    }
    private fun startRecording(startId:Int) {
        if(recorder!=null) return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { stopSelf(startId); return }
        val user=FirebaseAuth.getInstance().currentUser
        if(user==null) { stopSelf(startId); return }
        uid=user.uid
        val prefs=getSharedPreferences("samuel_remote_manager",MODE_PRIVATE)
        deviceId=prefs.getString("device_id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id",it).apply() }
        createChannel()
        try {
            val notification=buildNotification()
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID,notification)
            output=File(cacheDir,"audio-${System.currentTimeMillis()}.m4a")
            val media=if(Build.VERSION.SDK_INT>=31) MediaRecorder(this) else MediaRecorder()
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioEncodingBitRate(96000)
            media.setAudioSamplingRate(44100)
            media.setOutputFile(output!!.absolutePath)
            media.prepare()
            media.start()
            recorder=media
            startedAt=System.currentTimeMillis()
            getDatabaseRef()?.child("recording")?.setValue(mapOf("active" to true,"startedAt" to startedAt))
        } catch (_:Exception) {
            try { recorder?.release() } catch (_:Exception) {}
            recorder=null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }
    private fun stopAndUpload() {
        if(stopping) return
        stopping=true
        val media=recorder
        recorder=null
        try { media?.stop() } catch (_:Exception) {}
        try { media?.reset(); media?.release() } catch (_:Exception) {}
        val file=output
        val start=startedAt
        val duration=(System.currentTimeMillis()-start).coerceAtLeast(0L)
        getDatabaseRef()?.child("recording")?.setValue(mapOf("active" to false,"stoppedAt" to System.currentTimeMillis()))
        if(file==null || !file.exists() || file.length()==0L || uid==null || deviceId==null) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val archiveId=UUID.randomUUID().toString()
        val storagePath="users/${uid}/devices/${deviceId}/audioArchive/${archiveId}.m4a"
        val storageRef=FirebaseStorage.getInstance().reference.child(storagePath)
        storageRef.putFile(android.net.Uri.fromFile(file))
            .continueWithTask { task ->
                if(!task.isSuccessful) throw task.exception ?: IllegalStateException("Upload failed")
                storageRef.metadata
            }.addOnSuccessListener { metadata ->
                val item=mapOf("storagePath" to storagePath,"capturedAt" to start,"uploadedAt" to System.currentTimeMillis(),"durationMs" to duration,"sizeBytes" to metadata.sizeBytes,"format" to "audio/mp4")
                getDatabaseRef()?.child("audioArchive")?.child(archiveId)?.setValue(item)
                    ?.addOnCompleteListener {
                        file.delete()
                        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    } ?: run { file.delete(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }.addOnFailureListener {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,buildNotification("Upload failed. Keep this app open and retry."))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
    }
    private fun getDatabaseRef() = if(uid!=null && deviceId!=null) FirebaseDatabase.getInstance().reference.child("users").child(uid!!).child("devices").child(deviceId!!) else null
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Audio recording",NotificationManager.IMPORTANCE_LOW))
    }
    private fun buildNotification(message:String="Microphone recording is active"): Notification {
        val stop=PendingIntent.getService(this,4202,Intent(this,AudioRecordService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,4203,Intent(this,AudioRecordActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Samuel Remote Manager · MICROPHONE ON").setContentText(message).setContentIntent(open)
            .setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Stop & upload",stop).build()
    }
    override fun onDestroy() {
        if(recorder!=null && !stopping) stopAndUpload()
        super.onDestroy()
    }
}

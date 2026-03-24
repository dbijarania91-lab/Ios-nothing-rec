package com.example.nothingrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class ScreenRecordService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    // CHANGED: Using our C++ NativeEncoder instead of VideoEncoder
    private lateinit var nativeEncoder: NativeEncoder
    private lateinit var audioEncoder: AudioEncoder
    private lateinit var muxerPipeline: MuxerPipeline
    private var outputFile: File? = null 

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "nothing_rec_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nothing Recorder")
            .setContentText("Hardcore recording active...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)

        setupPipeline()
        return START_NOT_STICKY
    }

    private fun setupPipeline() {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        outputFile = File(moviesDir, "NothingRecord_${System.currentTimeMillis()}.mp4")

        // 1. Initialize the C++ Native Engine
        nativeEncoder = NativeEncoder()
        val hardcoreSurface = nativeEncoder.createHardwareSurface()

        audioEncoder = AudioEncoder(mediaProjection!!).apply { prepare() }
        
        // 2. We pass 'null' for the video encoder because C++ is handling the video now!
        muxerPipeline = MuxerPipeline(this, null, audioEncoder, outputFile!!.absolutePath)

        // 3. Plug the Screen DIRECTLY into the C++ GPU Window (Zero-Copy)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder", 1080, 2400, 462,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            hardcoreSurface, 
            null, null
        )

        muxerPipeline.startLoop() 
    }

    override fun onDestroy() {
        mediaProjection?.unregisterCallback(projectionCallback)
        audioEncoder.release()
        
        // STOP the C++ encoder
        nativeEncoder.stopHardwareEncoder()
        
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}

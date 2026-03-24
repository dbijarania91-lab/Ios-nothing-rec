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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class ScreenRecordService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var videoEncoder: VideoEncoder
    private lateinit var audioEncoder: AudioEncoder
    private lateinit var muxerPipeline: MuxerPipeline
    
    // ADDED: Class-level variable so onDestroy can find the file
    private var outputFile: File? = null 
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    // MANDATORY FOR ANDROID 14: Listen for system stop signals
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Setup Notification Channel IMMEDIATELY
        val channelId = "nothing_rec_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        // 2. Build the "Hardcore" Notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nothing Recorder")
            .setContentText("Hardcore recording active...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        // 3. Start Foreground (Security Requirement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }

        // 4. Retrieve MediaProjection Data
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        // 5. REGISTER CALLBACK (Fixes the crash you found)
        mediaProjection?.registerCallback(projectionCallback, null)

        setupPipeline()
        return START_NOT_STICKY
    }

    private fun setupPipeline() {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        
        // UPDATED: Save to our new class-level variable
        outputFile = File(moviesDir, "NothingRecord_${System.currentTimeMillis()}.mp4")

        videoEncoder = VideoEncoder().apply { prepare() }
        audioEncoder = AudioEncoder(mediaProjection!!).apply { prepare() }
        
        // UPDATED: Passed "this" so MuxerPipeline has the Context for the Gallery fix
        muxerPipeline = MuxerPipeline(this, videoEncoder, audioEncoder, outputFile!!.absolutePath)

        // Map the VirtualDisplay DIRECTLY to the VideoEncoder Surface (Zero Copy)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder", 1080, 2400, 462,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            videoEncoder.inputSurface, null, null
        )

        serviceScope.launch { muxerPipeline.startLoop() }
    }

    override fun onDestroy() {
        // Cleanup resources
        mediaProjection?.unregisterCallback(projectionCallback)
        audioEncoder.release()
        videoEncoder.release()
        virtualDisplay?.release()
        mediaProjection?.stop()

        // NOTE: The Gallery Fix was moved to MuxerPipeline.kt so the default Gallery can play it!

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
        audioEncoder.release()
        videoEncoder.release()
        virtualDisplay?.release()
        mediaProjection?.stop()

        // --- THE GALLERY FIX IS HERE ---
        // This forces Android to instantly index the file we just closed
        outputFile?.let { file ->
            MediaScannerConnection.scanFile(
                this, 
                arrayOf(file.absolutePath), 
                arrayOf("video/mp4")
            ) { path, uri ->
                Log.d("NothingRecorder", "Gallery Sync Success! Saved to: $path")
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null
}

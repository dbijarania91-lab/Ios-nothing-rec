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
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        // 1. Create Channel FIRST
        val channelId = "nothing_rec_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        // 2. Build Notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nothing Recorder")
            .setContentText("Recording in progress...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        // 3. Start Foreground with Type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }

        // 4. Start Projection
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        setupPipeline()
        return START_NOT_STICKY
    }

    private fun setupPipeline() {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val outputFile = File(moviesDir, "NothingRecord_${System.currentTimeMillis()}.mp4")
        
        videoEncoder = VideoEncoder().apply { prepare() }
        audioEncoder = AudioEncoder(mediaProjection!!).apply { prepare() }
        muxerPipeline = MuxerPipeline(videoEncoder, audioEncoder, outputFile.absolutePath)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder", 1080, 2400, 402,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            videoEncoder.inputSurface, null, null
        )

        serviceScope.launch { muxerPipeline.startLoop() }
    }

    override fun onDestroy() {
        audioEncoder.release()
        videoEncoder.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

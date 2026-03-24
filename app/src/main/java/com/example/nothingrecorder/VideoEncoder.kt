package com.example.nothingrecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface

class VideoEncoder {
    lateinit var codec: MediaCodec
    lateinit var inputSurface: Surface

    fun prepare() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1080, 2400).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 20000000) 
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // 1. Tell VirtualDisplay to capture exactly 60
            setInteger(MediaFormat.KEY_CAPTURE_RATE, 60)

            // 2. High Profile (Like iOS) stops the encoder from dropping frames under heavy load
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)

            // 3. The CFR duplicate lock
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / 60)

            // Hardcore NDK System Flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 60f)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInteger(MediaFormat.KEY_PRIORITY, 0) 
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1) 
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setFloat(MediaFormat.KEY_OPERATING_RATE, 120f) 
            }
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        inputSurface = codec.createInputSurface() 

        // --- THE MAGIC BULLET FOR 60 FPS ---
        // This overrides your Nothing Phone's Variable Refresh Rate completely.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inputSurface.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }

        codec.start()
    }

    fun release() {
        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

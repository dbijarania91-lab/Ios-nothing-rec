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
        // --- THE HEVC (H.265) UPGRADE ---
        // This offloads the recording to a dedicated silicon chip so your game doesn't lag!
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 2400, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            
            // --- THE CONSTANT BITRATE (CBR) LOCK ---
            // This stops the micro-stutters and forces perfect frame pacing like iOS.
            setInteger(MediaFormat.KEY_BIT_RATE, 20000000) 
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // Force the hardware profile for HEVC
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)

            // 120Hz Bypass (Keeps your screen running fast)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 120f) 
            }

            // CFR duplicate lock
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / 60)

            // Hardcore NDK Flags
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

        // Initialize the new HEVC codec
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        inputSurface = codec.createInputSurface() 
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

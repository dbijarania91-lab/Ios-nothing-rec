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
        val format = MediaFormat.createVideoFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 2400, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 20000000) 
            
            // 1. Tell the final file to be exactly 60 FPS
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // --- THE 120HZ BYPASS ---
            // 2. Tell the encoder to accept 120 FPS from the VirtualDisplay.
            // This stops Android from forcing your physical screen down to 60Hz!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 120f) 
            }

            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)

            // 3. Keep the CFR duplicate lock so the file never drops below 60
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

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        inputSurface = codec.createInputSurface() 
        // NOTICE: I completely deleted the inputSurface.setFrameRate() lock!
        // Your screen is now free to breathe at 120Hz.
        
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

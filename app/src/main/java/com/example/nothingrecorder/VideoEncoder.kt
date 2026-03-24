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
            setInteger(MediaFormat.KEY_BIT_RATE, 20000000) // 20 Mbps for crystal clear gameplay
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // --- THE NDK HARDCORE FLAGS ---
            // Forces Android to treat this encoder with Real-Time iOS-level priority
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInteger(MediaFormat.KEY_PRIORITY, 0) 
            }
            // Stops the encoder from buffering frames, destroying touchscreen lag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1) 
            }
            // Forces the GPU/CPU to process this at max clock speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setFloat(MediaFormat.KEY_OPERATING_RATE, 120f) 
            }
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        // This generates the EXACT same zero-copy hardware surface as our C++ code!
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

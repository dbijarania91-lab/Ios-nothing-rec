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
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 2400, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            
            // --- THE APPLE WEIGHT CLASS ---
            setInteger(MediaFormat.KEY_BIT_RATE, 40000000) 
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // The Scanner
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, 60)
            }

            // --- 8-BIT STABILITY FOR SNAPDRAGON 778G+ ---
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51)

            // 120Hz Bypass
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, 120f) 
            }

            // Frame-Drop Recovery (The Safety Net)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / 60)

            // System Flags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInteger(MediaFormat.KEY_PRIORITY, 0) 
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1) 
            }
        }

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

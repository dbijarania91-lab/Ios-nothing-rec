package com.example.nothingrecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

class VideoEncoder(private val width: Int = 1080, private val height: Int = 2400) {
    lateinit var codec: MediaCodec
    var inputSurface: Surface? = null

    fun prepare() {
        // Using HEVC (H.265) for maximum quality and minimum file size
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 20000000) // 20 Mbps for crystal clear BGMI clips
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)     // Target 60 FPS
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe for better editing
            
            // THE 60 FPS LOCK: This forces the encoder to repeat frames if the game lags
            // This prevents audio/video sync issues in CapCut or Premiere
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / 60L)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    fun release() {
        try {
            codec.signalEndOfInputStream()
            codec.stop()
            codec.release()
            inputSurface?.release()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

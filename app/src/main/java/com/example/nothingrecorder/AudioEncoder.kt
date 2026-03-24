package com.example.nothingrecorder

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection

class AudioEncoder(private val mediaProjection: MediaProjection) {
    lateinit var codec: MediaCodec
    lateinit var audioRecord: AudioRecord
    private val sampleRate = 44100
    var isRecording = false

    @SuppressLint("MissingPermission")
    fun prepare() {
        // High-Fidelity AAC Audio (320kbps)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 320000) // 320kbps for crystal clear footsteps
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        // Capture Internal Game Audio ONLY
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        // Double buffer size to prevent audio crackling during heavy gunfights
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()
    }

    fun release() {
        isRecording = false
        try {
            audioRecord.stop()
            audioRecord.release()
            codec.stop()
            codec.release()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

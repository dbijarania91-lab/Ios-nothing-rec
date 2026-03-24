package com.example.nothingrecorder

import android.media.MediaCodec
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MuxerPipeline(
    private val videoEncoder: VideoEncoder,
    private val audioEncoder: AudioEncoder,
    private val outputPath: String
) {
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    suspend fun startLoop() = withContext(Dispatchers.IO) {
        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()

        audioEncoder.audioRecord.startRecording()
        audioEncoder.isRecording = true

        while (audioEncoder.isRecording) {
            drainEncoder(videoEncoder.codec, bufferInfo, true)
            drainEncoder(audioEncoder.codec, bufferInfo, false)
        }
        stopMuxer()
    }

    private fun drainEncoder(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean) {
        val index = codec.dequeueOutputBuffer(bufferInfo, 10000)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat = codec.outputFormat
            if (isVideo) videoTrackIndex = muxer!!.addTrack(newFormat) else audioTrackIndex = muxer!!.addTrack(newFormat)
            if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !isMuxerStarted) {
                muxer!!.start()
                isMuxerStarted = true
            }
        } else if (index >= 0) {
            val encodedData = codec.getOutputBuffer(index)!!
            if (isMuxerStarted && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                muxer!!.writeSampleData(if (isVideo) videoTrackIndex else audioTrackIndex, encodedData, bufferInfo)
            }
            codec.releaseOutputBuffer(index, false)
        }
    }

    private fun stopMuxer() {
        if (isMuxerStarted) {
            muxer?.stop()
            muxer?.release()
            isMuxerStarted = false
        }
    }
}

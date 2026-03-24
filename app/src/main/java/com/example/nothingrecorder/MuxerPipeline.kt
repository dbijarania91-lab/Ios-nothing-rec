package com.example.nothingrecorder

import android.content.Context
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MuxerPipeline(
    private val context: Context, // Added Context for the Gallery fix
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
            // THE AUDIO FIX: You MUST manually feed the audio bytes into the codec!
            feedAudio(audioEncoder.codec, audioEncoder.audioRecord)
            
            drainEncoder(videoEncoder.codec, bufferInfo, true)
            drainEncoder(audioEncoder.codec, bufferInfo, false)
        }
        
        stopMuxer()
    }

    // NEW FUNCTION: Scoops internal audio and feeds it to the file
    private fun feedAudio(codec: MediaCodec, audioRecord: AudioRecord) {
        val inputBufferIndex = codec.dequeueInputBuffer(0)
        if (inputBufferIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
            val readResult = audioRecord.read(inputBuffer, inputBuffer.capacity())
            if (readResult > 0) {
                val ptsUsec = System.nanoTime() / 1000
                codec.queueInputBuffer(inputBufferIndex, 0, readResult, ptsUsec, 0)
            }
        }
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
            
            if (isMuxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)) {
                muxer!!.writeSampleData(if (isVideo) videoTrackIndex else audioTrackIndex, encodedData, bufferInfo)
            }
            
            codec.releaseOutputBuffer(index, false)
        }
    }

    private fun stopMuxer() {
        try {
            if (isMuxerStarted) {
                muxer?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            muxer?.release()
            muxer = null
            isMuxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
            
            // THE GALLERY FIX: Triggers only when the file is 100% finished
            MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf("video/mp4")) { path, uri ->
                Log.d("NothingRecorder", "Perfect Sync! Ready for Gallery: $path")
            }
        }
    }
}

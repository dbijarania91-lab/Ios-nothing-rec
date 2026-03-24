package com.example.nothingrecorder

import android.content.Context
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.util.Log

class MuxerPipeline(
    private val context: Context,
    private val videoEncoder: VideoEncoder,
    private val audioEncoder: AudioEncoder,
    private val outputPath: String
) {
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    // REMOVED 'suspend'. We are using a raw, high-priority OS Thread now.
    fun startLoop() {
        Thread {
            // HARDCORE OPTIMIZATION: Forces the CPU to prioritize this over everything else
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            audioEncoder.audioRecord.startRecording()
            audioEncoder.isRecording = true

            while (audioEncoder.isRecording) {
                // Feeds internal audio (Reels/YT) to the file
                feedAudio(audioEncoder.codec, audioEncoder.audioRecord)
                
                drainEncoder(videoEncoder.codec, bufferInfo, true)
                drainEncoder(audioEncoder.codec, bufferInfo, false)
            }
            
            stopMuxer()
        }.start() // Starts the thread instantly
    }

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
            
            // Perfect Gallery Sync - triggers only when the file is fully closed
            MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf("video/mp4")) { path, uri ->
                Log.d("NothingRecorder", "Perfect Sync! Ready for Gallery: $path")
            }
        }
    }
}

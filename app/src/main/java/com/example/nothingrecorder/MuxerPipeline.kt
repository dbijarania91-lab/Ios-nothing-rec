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
    @Volatile private var videoTrackIndex = -1
    @Volatile private var audioTrackIndex = -1
    @Volatile private var isMuxerStarted = false
    private val muxerLock = Object()
    
    // --- THE TIMESTAMPS ---
    private var videoFrameCount = 0L 
    private var audioStartTimeUs = -1L // The new zero-anchor for perfect A/V sync

    fun startLoop() {
        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBufferInfo = MediaCodec.BufferInfo()

        audioEncoder.audioRecord.startRecording()
        audioEncoder.isRecording = true

        // Thread 1: Pure Video
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            while (audioEncoder.isRecording) {
                drainEncoder(videoEncoder.codec, videoBufferInfo, true)
            }
        }.start()

        // Thread 2: Pure Audio
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (audioEncoder.isRecording) {
                feedAudio(audioEncoder.codec, audioEncoder.audioRecord)
                drainEncoder(audioEncoder.codec, audioBufferInfo, false)
            }
            stopMuxer()
        }.start()
    }

    private fun feedAudio(codec: MediaCodec, audioRecord: AudioRecord) {
        val inputBufferIndex = codec.dequeueInputBuffer(0)
        if (inputBufferIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
            val readResult = audioRecord.read(inputBuffer, inputBuffer.capacity())
            if (readResult > 0) {
                
                // --- THE A/V SYNC ZERO ANCHOR ---
                val currentUs = System.nanoTime() / 1000
                if (audioStartTimeUs == -1L) {
                    audioStartTimeUs = currentUs // Lock in the start time
                }
                // Subtract the start time so audio starts at exactly 0, matching the video!
                val ptsUsec = currentUs - audioStartTimeUs 

                codec.queueInputBuffer(inputBufferIndex, 0, readResult, ptsUsec, 0)
            }
        }
    }

    private fun drainEncoder(codec: MediaCodec, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean) {
        val index = codec.dequeueOutputBuffer(bufferInfo, 10000)
        
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            synchronized(muxerLock) {
                val newFormat = codec.outputFormat
                if (isVideo) videoTrackIndex = muxer!!.addTrack(newFormat) else audioTrackIndex = muxer!!.addTrack(newFormat)
                
                if (videoTrackIndex >= 0 && audioTrackIndex >= 0 && !isMuxerStarted) {
                    muxer!!.start()
                    isMuxerStarted = true
                }
            }
        } else if (index >= 0) {
            val encodedData = codec.getOutputBuffer(index)!!
            
            synchronized(muxerLock) {
                if (isMuxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)) {
                    
                    if (isVideo) {
                        bufferInfo.presentationTimeUs = videoFrameCount * (1000000L / 60L)
                        videoFrameCount++
                    }

                    muxer!!.writeSampleData(if (isVideo) videoTrackIndex else audioTrackIndex, encodedData, bufferInfo)
                }
            }
            codec.releaseOutputBuffer(index, false)
        }
    }

    private fun stopMuxer() {
        synchronized(muxerLock) {
            try {
                if (isMuxerStarted) muxer?.stop()
            } catch (e: Exception) { e.printStackTrace() } 
            finally {
                muxer?.release()
                muxer = null
                isMuxerStarted = false
                videoTrackIndex = -1
                audioTrackIndex = -1
                
                // Reset everything for the next clip
                videoFrameCount = 0L 
                audioStartTimeUs = -1L 
                
                MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf("video/mp4")) { path, _ ->
                    Log.d("NothingRecorder", "Perfect Sync! Ready for Gallery: $path")
                }
            }
        }
    }
}

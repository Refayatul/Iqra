package com.refayatul.iqra

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class AudioRecorderHelper {

    companion object {
        private const val TAG = "AudioRecorderHelper"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun recordAudio(): Flow<FloatArray> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "IQRA_LOG: Invalid buffer size: $minBufferSize")
            return@flow
        }

        val bufferSize = minBufferSize * 2 
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "IQRA_LOG: AudioRecord initialization failed!")
            audioRecord.release()
            return@flow
        }

        val buffer = ShortArray(minBufferSize)
        
        try {
            Log.i(TAG, "IQRA_LOG: Starting recording...")
            audioRecord.startRecording()
            
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "IQRA_LOG: Failed to start recording. State: ${audioRecord.recordingState}")
                return@flow
            }

            while (currentCoroutineContext().isActive) {
                try {
                    val readCount = audioRecord.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        val floatBuffer = FloatArray(readCount)
                        for (i in 0 until readCount) {
                            floatBuffer[i] = buffer[i] / 32768f
                        }
                        emit(floatBuffer)
                    } else if (readCount < 0) {
                        Log.e(TAG, "IQRA_LOG: Error reading audio data: $readCount")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "IQRA_LOG: Exception during audio read", e)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IQRA_LOG: Exception starting/running recording", e)
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "IQRA_LOG: Error stopping AudioRecord", e)
            }
            audioRecord.release()
            Log.i(TAG, "IQRA_LOG: AudioRecord released")
        }
    }.flowOn(Dispatchers.IO)
}

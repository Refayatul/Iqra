package com.refayatul.iqra

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer

class TarteelInferenceEngine(private val modelPath: String) {

    private val TAG = "InferenceEngine"
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    /**
     * Initializes the ONNX session.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session == null) {
            val options = OrtSession.SessionOptions()
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(1)
            session = env.createSession(modelPath, options)
            
            session?.let { s ->
                Log.i(TAG, "IQRA_LOG: ONNX Session initialized.")
                Log.i(TAG, "IQRA_LOG: Inputs: ${s.inputNames}")
                Log.i(TAG, "IQRA_LOG: Outputs: ${s.outputNames}")
            }
        }
    }

    /**
     * Runs inference and returns the decoded Arabic transcript.
     */
    suspend fun runInference(
        features: FloatArray,
        timeFrames: Int,
        vocab: Map<Int, String>
    ): String = withContext(Dispatchers.Default) {
        val currentSession = session ?: throw IllegalStateException("Session not initialized")
        val blankId = vocab.keys.maxOrNull() ?: throw IllegalStateException("Vocab is empty")

        val featureBuffer = FloatBuffer.wrap(features)
        val featureShape = longArrayOf(1, 80, timeFrames.toLong())
        
        val lengthBuffer = LongBuffer.wrap(longArrayOf(timeFrames.toLong()))
        val lengthShape = longArrayOf(1)

        val featureTensor = OnnxTensor.createTensor(env, featureBuffer, featureShape)
        val lengthTensor = OnnxTensor.createTensor(env, lengthBuffer, lengthShape)

        try {
            // Robustly identify input names based on tensor rank (3 for audio, 1 for length)
            val inputInfo = currentSession.inputInfo
            val audioInputName = inputInfo.entries.find { 
                val info = it.value.info
                info is TensorInfo && info.shape.size == 3 
            }?.key ?: currentSession.inputNames.first()

            val lengthInputName = inputInfo.entries.find { 
                val info = it.value.info
                info is TensorInfo && info.shape.size == 1 
            }?.key ?: currentSession.inputNames.toList().getOrNull(1) ?: "length"

            val inputs = mapOf(
                audioInputName to featureTensor,
                lengthInputName to lengthTensor
            )

            Log.i(TAG, "IQRA_LOG: session.run() with mapped inputs: $inputs")
            currentSession.run(inputs).use { results ->
                val outputTensor = results[0] as OnnxTensor
                
                val logprobs = outputTensor.floatBuffer
                val outputShape = outputTensor.info.shape
                val tSteps = outputShape[1].toInt()
                val vSize = outputShape[2].toInt()

                val transcript = decode(logprobs, tSteps, vSize, vocab, blankId)
                Log.i(TAG, "IQRA_LOG: Decoded Transcript: $transcript")
                transcript
            }
        } catch (e: Exception) {
            Log.e(TAG, "IQRA_LOG: ONNX session.run() failed", e)
            ""
        } finally {
            featureTensor.close()
            lengthTensor.close()
        }
    }

    private fun decode(
        logprobs: FloatBuffer,
        tSteps: Int,
        vSize: Int,
        vocab: Map<Int, String>,
        blankId: Int
    ): String {
        val tokens = mutableListOf<String>()
        var prevId = -1

        for (t in 0 until tSteps) {
            var maxProb = -Float.MAX_VALUE
            var maxId = -1

            for (v in 0 until vSize) {
                val prob = logprobs.get(t * vSize + v)
                if (prob.isFinite() && prob > maxProb) {
                    maxProb = prob
                    maxId = v
                }
            }

            if (maxId != -1 && maxId != prevId && maxId != blankId) {
                vocab[maxId]?.let { tokens.add(it) }
            }
            prevId = maxId
        }

        return tokens.joinToString("")
            .replace("\u2581", " ")
            .trim()
    }

    fun close() {
        session?.close()
        env.close()
    }
}

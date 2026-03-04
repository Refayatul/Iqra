package com.refayatul.iqra

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer

class TarteelInferenceEngine(private val modelPath: String) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    /**
     * Initializes the ONNX session.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session == null) {
            val options = OrtSession.SessionOptions()
            // Optimization: Use 1 thread for mobile to avoid overhead
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(1)
            session = env.createSession(modelPath, options)
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

        // Input 1: [1, 80, timeFrames]
        val featureBuffer = FloatBuffer.wrap(features)
        val featureShape = longArrayOf(1, 80, timeFrames.toLong())
        
        // Input 2: [1] (timeFrames)
        val lengthBuffer = LongBuffer.wrap(longArrayOf(timeFrames.toLong()))
        val lengthShape = longArrayOf(1)

        // Create tensors. Env is a singleton, we don't close it here.
        val featureTensor = OnnxTensor.createTensor(env, featureBuffer, featureShape)
        val lengthTensor = OnnxTensor.createTensor(env, lengthBuffer, lengthShape)

        try {
            val inputs = mapOf(
                currentSession.inputNames.iterator().next() to featureTensor,
                currentSession.inputNames.toList()[1] to lengthTensor
            )

            // Run Session
            currentSession.run(inputs).use { results ->
                val outputTensor = results[0] as OnnxTensor
                
                // Output shape is [1, T, VocabSize]
                val logprobs = outputTensor.floatBuffer
                val outputShape = outputTensor.info.shape
                val tSteps = outputShape[1].toInt()
                val vSize = outputShape[2].toInt()

                // CTC Greedy Decode
                decode(logprobs, tSteps, vSize, vocab, blankId)
            }
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
            var maxProb = Float.NEGATIVE_INFINITY
            var maxId = -1

            // Argmax for current timestep
            for (v in 0 until vSize) {
                val prob = logprobs.get(t * vSize + v)
                if (prob > maxProb) {
                    maxProb = prob
                    maxId = v
                }
            }

            // Collapse repeats and remove blanks
            if (maxId != prevId && maxId != blankId) {
                vocab[maxId]?.let { tokens.add(it) }
            }
            prevId = maxId
        }

        // Join, replace NeMo's space token (\u2581), and trim
        return tokens.joinToString("")
            .replace("\u2581", " ")
            .trim()
    }

    fun close() {
        session?.close()
        // Env is a singleton, closing it usually happens at app exit, 
        // but we can close it if we're sure we're done.
        env.close()
    }
}

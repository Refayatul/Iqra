package com.refayatul.iqra

import android.util.Log
import kotlin.math.*

object AudioProcessor {
    private const val TAG = "AudioProcessor"
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 512
    private const val HOP_LENGTH = 160
    private const val WIN_LENGTH = 400
    private const val N_MELS = 80
    private const val PREEMPH = 0.97f
    private const val DITHER = 1e-5f
    private const val LOG_GUARD = 1e-5f

    private val hannWindow = FloatArray(WIN_LENGTH) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (WIN_LENGTH - 1)))).toFloat()
    }

    private val melFilters: Array<FloatArray> by lazy {
        createMelFilters(N_FFT / 2 + 1, N_MELS, 0f, 8000f, SAMPLE_RATE.toFloat())
    }

    fun computeMelSpectrogram(audio: FloatArray): Pair<FloatArray, Int> {
        if (audio.isEmpty()) return Pair(FloatArray(0), 0)

        // 1. Dither
        val dithered = FloatArray(audio.size) { i ->
            audio[i] + DITHER * ((Math.random() * 2.0).toFloat() - 1.0f)
        }

        // 2. Pre-emphasis
        val preemphasized = FloatArray(dithered.size)
        preemphasized[0] = dithered[0]
        for (i in 1 until dithered.size) {
            preemphasized[i] = dithered[i] - PREEMPH * dithered[i - 1]
        }

        // 3. Framing & STFT
        val numFrames = (preemphasized.size - WIN_LENGTH) / HOP_LENGTH + 1
        if (numFrames <= 0) return Pair(FloatArray(0), 0)

        val melSpectrogram = FloatArray(numFrames * N_MELS)
        val fftReal = FloatArray(N_FFT)
        val fftImag = FloatArray(N_FFT)
        val powerSpectrum = FloatArray(N_FFT / 2 + 1)

        for (t in 0 until numFrames) {
            val start = t * HOP_LENGTH
            // Windowing
            for (i in 0 until N_FFT) {
                if (i < WIN_LENGTH) {
                    fftReal[i] = preemphasized[start + i] * hannWindow[i]
                } else {
                    fftReal[i] = 0f
                }
                fftImag[i] = 0f
            }

            fft(fftReal, fftImag)

            // Power Spectrum
            for (i in 0..N_FFT / 2) {
                powerSpectrum[i] = fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]
            }

            // Mel Filters
            for (m in 0 until N_MELS) {
                var melValue = 0f
                for (i in 0..N_FFT / 2) {
                    melValue += powerSpectrum[i] * melFilters[m][i]
                }
                // Natural log with guard: ln(mel + 1e-5)
                melSpectrogram[m * numFrames + t] = ln(max(melValue, 0f) + LOG_GUARD)
            }
        }

        // 4. Per-feature Normalization (NeMo style)
        for (m in 0 until N_MELS) {
            var sum = 0.0
            for (t in 0 until numFrames) {
                sum += melSpectrogram[m * numFrames + t]
            }
            val mean = (sum / numFrames).toFloat()

            var sumSq = 0.0
            for (t in 0 until numFrames) {
                val diff = melSpectrogram[m * numFrames + t] - mean
                sumSq += diff * diff
            }
            // Add safety epsilon to avoid division by zero
            val std = (sqrt(sumSq / numFrames) + 1e-10).toFloat()

            for (t in 0 until numFrames) {
                val value = (melSpectrogram[m * numFrames + t] - mean) / std
                // Final safety check for NaN/Inf
                melSpectrogram[m * numFrames + t] = if (value.isFinite()) value else 0f
            }
        }

        return Pair(melSpectrogram, numFrames)
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k in 1..j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var m = 2
        while (m <= n) {
            val theta = -2.0 * PI / m
            val wr = cos(theta).toFloat()
            val wi = sin(theta).toFloat()
            for (i in 0 until n step m) {
                var w_real = 1f
                var w_imag = 0f
                for (k in 0 until m / 2) {
                    val idx1 = i + k
                    val idx2 = i + k + m / 2
                    val tr = w_real * real[idx2] - w_imag * imag[idx2]
                    val ti = w_real * imag[idx2] + w_imag * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] += tr
                    imag[idx1] += ti
                    val next_w_real = w_real * wr - w_imag * wi
                    w_imag = w_real * wi + w_imag * wr
                    w_real = next_w_real
                }
            }
            m *= 2
        }
    }

    private fun createMelFilters(
        numFreqBins: Int,
        numMelFilters: Int,
        minFreq: Float,
        maxFreq: Float,
        sampleRate: Float
    ): Array<FloatArray> {
        val melMin = freqToMel(minFreq)
        val melMax = freqToMel(maxFreq)

        val melPoints = FloatArray(numMelFilters + 2) { i ->
            melMin + i * (melMax - melMin) / (numMelFilters + 1)
        }
        val freqPoints = melPoints.map { melToFreq(it) }
        val binPoints = freqPoints.map { (it * N_FFT / sampleRate).toInt() }

        val filters = Array(numMelFilters) { FloatArray(numFreqBins) }
        for (m in 1..numMelFilters) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]

            if (center > left) {
                for (k in left until center) {
                    filters[m - 1][k] = (k - left).toFloat() / (center - left)
                }
            }
            if (right > center) {
                for (k in center until right) {
                    if (k < numFreqBins) {
                        filters[m - 1][k] = (right - k).toFloat() / (right - center)
                    }
                }
            }
        }

        // Slaney normalization
        for (m in 0 until numMelFilters) {
            val freqDiff = freqPoints[m + 2] - freqPoints[m]
            if (freqDiff > 0) {
                val enorm = 2.0f / freqDiff
                for (i in 0 until numFreqBins) {
                    filters[m][i] *= enorm
                }
            }
        }

        return filters
    }

    private fun freqToMel(freq: Float): Float = 2595.0f * log10(1.0f + freq / 700.0f)
    private fun melToFreq(mel: Float): Float = 700.0f * (10.0f.pow(mel / 2595.0f) - 1.0f)
}

package com.refayatul.iqra

import kotlin.math.*

object AudioProcessor {
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 512
    private const val HOP_LENGTH = 160
    private const val WIN_LENGTH = 400
    private const val N_MELS = 80
    private const val PREEMPH = 0.97f
    private const val DITHER = 1e-5f
    private const val LOG_GUARD = 1e-5f

    private val hannWindow = FloatArray(WIN_LENGTH) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / WIN_LENGTH))).toFloat()
    }

    private val melFilters: Array<FloatArray> by lazy {
        createMelFilters(N_FFT / 2 + 1, N_MELS, 0f, 8000f, SAMPLE_RATE.toFloat())
    }

    /**
     * Translates raw audio into an 80-bin Mel Spectrogram.
     * Follows NeMo / FastConformer preprocessing.
     */
    fun computeMelSpectrogram(audio: FloatArray): Pair<FloatArray, Int> {
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
            // Apply Window
            for (i in 0 until N_FFT) {
                if (i < WIN_LENGTH) {
                    fftReal[i] = preemphasized[start + i] * hannWindow[i]
                } else {
                    fftReal[i] = 0f
                }
                fftImag[i] = 0f
            }

            // Compute FFT
            fft(fftReal, fftImag)

            // Compute Power Spectrum
            for (i in 0..N_FFT / 2) {
                powerSpectrum[i] = fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]
            }

            // Apply Mel Filters
            for (m in 0 until N_MELS) {
                var melValue = 0f
                for (i in 0..N_FFT / 2) {
                    melValue += powerSpectrum[i] * melFilters[m][i]
                }
                // Log scaling: ln(mel + guard)
                melSpectrogram[m * numFrames + t] = ln(max(melValue, 0f) + LOG_GUARD)
            }
        }

        // 4. Per-feature Normalization (Mean/Std per mel bin)
        for (m in 0 until N_MELS) {
            var sum = 0f
            for (t in 0 until numFrames) {
                sum += melSpectrogram[m * numFrames + t]
            }
            val mean = sum / numFrames

            var sumSq = 0f
            for (t in 0 until numFrames) {
                val diff = melSpectrogram[m * numFrames + t] - mean
                sumSq += diff * diff
            }
            val std = sqrt(sumSq / numFrames) + 1e-10f

            for (t in 0 until numFrames) {
                melSpectrogram[m * numFrames + t] = (melSpectrogram[m * numFrames + t] - mean) / std
            }
        }

        return Pair(melSpectrogram, numFrames)
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal
                val tempImag = imag[i]
                imag[i] = imag[j]
                imag[j] = tempImag
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var m = 2
        while (m <= n) {
            val theta = -2.0 * PI / m
            val wReal = cos(theta).toFloat()
            val wImag = sin(theta).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                for (k in 0 until m / 2) {
                    val r = real[i + k + m / 2]
                    val im = imag[i + k + m / 2]
                    val tr = wr * r - wi * im
                    val ti = wr * im + wi * r
                    real[i + k + m / 2] = real[i + k] - tr
                    imag[i + k + m / 2] = imag[i + k] - ti
                    real[i + k] += tr
                    imag[i + k] += ti
                    val nextWr = wr * wReal - wi * wImag
                    wi = wr * wImag + wi * wReal
                    wr = nextWr
                }
                i += m
            }
            m = m shl 1
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
        val binPoints = freqPoints.map { (it * (N_FFT + 1) / sampleRate).toInt() }

        val filters = Array(numMelFilters) { FloatArray(numFreqBins) }
        for (m in 1..numMelFilters) {
            for (k in binPoints[m - 1]..binPoints[m]) {
                filters[m - 1][k] = (k - binPoints[m - 1]).toFloat() / (binPoints[m] - binPoints[m - 1])
            }
            for (k in binPoints[m]..binPoints[m + 1]) {
                if (k < numFreqBins) {
                    filters[m - 1][k] = (binPoints[m + 1] - k).toFloat() / (binPoints[m + 1] - binPoints[m])
                }
            }
        }
        
        // Slaney normalization
        for (m in 0 until numMelFilters) {
            val enorm = 2.0f / (freqPoints[m + 2] - freqPoints[m])
            for (i in 0 until numFreqBins) {
                filters[m][i] *= enorm
            }
        }

        return filters
    }

    private fun freqToMel(freq: Float): Float = 2595.0f * log10(1.0f + freq / 700.0f)
    private fun melToFreq(mel: Float): Float = 700.0f * (10.0f.pow(mel / 2595.0f) - 1.0f)
}

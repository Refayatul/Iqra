package com.refayatul.iqra.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refayatul.iqra.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IqraUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isModelLoaded: Boolean = false,
    val matchedSurahName: String? = null,
    val matchedSurahNameEn: String? = null,
    val matchedAyahNumber: Int? = null,
    val arabicText: String? = null,
    val transcript: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val _uiState = MutableStateFlow(IqraUiState())
    val uiState = _uiState.asStateFlow()

    private val assetManager = QuranAssetManager(application)
    private val recorderHelper = AudioRecorderHelper()
    private var inferenceEngine: TarteelInferenceEngine? = null
    
    private var vocab: Map<Int, String>? = null
    private var quran: List<Ayah>? = null
    
    private var recordingJob: Job? = null
    private val audioBuffer = mutableListOf<FloatArray>()

    init {
        // Load AI model and data in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                
                Log.i(TAG, "IQRA_LOG: Step 1: Loading Vocab and Quran data...")
                vocab = assetManager.loadVocab()
                quran = assetManager.loadQuran()
                
                Log.i(TAG, "IQRA_LOG: Step 2: Copying/Checking ONNX model...")
                val modelPath = assetManager.provideModelPath()
                
                Log.i(TAG, "IQRA_LOG: Step 3: Initializing ONNX engine...")
                inferenceEngine = TarteelInferenceEngine(modelPath).apply {
                    initialize()
                }
                
                Log.i(TAG, "IQRA_LOG: Initialization successful.")
                _uiState.update { it.copy(isProcessing = false, isModelLoaded = true) }
            } catch (e: Exception) {
                Log.e(TAG, "IQRA_LOG: Initialization failed with exception", e)
                _uiState.update { 
                    it.copy(
                        isProcessing = false, 
                        isModelLoaded = false,
                        errorMessage = "Model Load Failed: ${e.localizedMessage}"
                    ) 
                }
            }
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        // Prevent recording if model isn't ready
        if (!_uiState.value.isModelLoaded) {
            Log.i(TAG, "IQRA_LOG: startRecording ignored: Model not loaded yet")
            return
        }
        
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            Log.i(TAG, "IQRA_LOG: startRecording requested")
            
            // Ensure previous recording job is fully stopped
            recordingJob?.cancel()
            recordingJob?.join()
            
            // Small safety delay to let hardware release
            delay(200)

            audioBuffer.clear()
            _uiState.update { it.copy(isRecording = true, errorMessage = null) }
            
            recordingJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    recorderHelper.recordAudio().collect { chunk ->
                        audioBuffer.add(chunk)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "IQRA_LOG: Recording flow error", e)
                    _uiState.update { it.copy(isRecording = false, errorMessage = "Recording failed") }
                }
            }
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "IQRA_LOG: stopRecording called")
        _uiState.update { it.copy(isRecording = false, isProcessing = true) }
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Wait for the recording flow to stop
                recordingJob?.cancel()
                recordingJob?.join()
                
                // 1. Flatten audio
                val totalSize = audioBuffer.sumOf { it.size }
                Log.i(TAG, "IQRA_LOG: Captured audio size: $totalSize floats")
                
                if (totalSize == 0) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "No audio captured") }
                    return@launch
                }

                val fullAudio = FloatArray(totalSize)
                var offset = 0
                for (chunk in audioBuffer) {
                    System.arraycopy(chunk, 0, fullAudio, offset, chunk.size)
                    offset += chunk.size
                }

                // 2. Process to Mel Spectrogram
                Log.i(TAG, "IQRA_LOG: AudioProcessor start")
                val (features, timeFrames) = AudioProcessor.computeMelSpectrogram(fullAudio)
                Log.i(TAG, "IQRA_LOG: AudioProcessor finish. TimeFrames: $timeFrames")
                
                // Check for NaN/Inf in features
                if (features.any { it.isNaN() || it.isInfinite() }) {
                    Log.e(TAG, "IQRA_LOG: CRITICAL: Mel Spectrogram contains NaN or Infinite values!")
                }

                if (timeFrames == 0) {
                    Log.i(TAG, "IQRA_LOG: Audio too short")
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Audio too short") }
                    return@launch
                }

                // 3. Inference
                val currentVocab = vocab ?: throw IllegalStateException("Vocab not loaded")
                
                Log.i(TAG, "IQRA_LOG: Running ONNX Inference...")
                val resultTranscript = try {
                    inferenceEngine?.runInference(features, timeFrames, currentVocab) ?: ""
                } catch (e: Exception) {
                    // Catching all exceptions including OrtException to log stack trace
                    Log.e(TAG, "IQRA_LOG: ONNX Inference CRASHED", e)
                    throw e
                }
                Log.i(TAG, "IQRA_LOG: Decoded Transcript: $resultTranscript")

                // 4. Match
                val currentQuran = quran ?: throw IllegalStateException("Quran data not loaded")
                Log.i(TAG, "IQRA_LOG: QuranMatcher start")
                val match = QuranMatcher.matchVerse(resultTranscript, currentQuran)
                Log.i(TAG, "IQRA_LOG: QuranMatcher finish. Match Result: $match")

                _uiState.update { state ->
                    if (match != null) {
                        state.copy(
                            isProcessing = false,
                            matchedSurahName = match.surahName,
                            matchedSurahNameEn = match.surahNameEn,
                            matchedAyahNumber = match.ayah,
                            arabicText = match.textUthmani,
                            transcript = resultTranscript
                        )
                    } else {
                        state.copy(
                            isProcessing = false,
                            transcript = resultTranscript,
                            errorMessage = if (resultTranscript.isBlank()) "No speech detected" else "No verse matched"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "IQRA_LOG: Processing Pipeline Failed", e)
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Processing error: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine?.close()
    }
}

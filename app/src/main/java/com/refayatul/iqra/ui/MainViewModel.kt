package com.refayatul.iqra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refayatul.iqra.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IqraUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val matchedSurahName: String? = null,
    val matchedSurahNameEn: String? = null,
    val matchedAyahNumber: Int? = null,
    val arabicText: String? = null,
    val transcript: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

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
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                
                // 1. Load Data
                vocab = assetManager.loadVocab()
                quran = assetManager.loadQuran()
                
                // 2. Initialize Engine
                val modelPath = assetManager.copyModelToCache()
                inferenceEngine = TarteelInferenceEngine(modelPath).apply {
                    initialize()
                }
                
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Initialization failed: ${e.message}") }
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
        audioBuffer.clear()
        _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            recorderHelper.recordAudio().collect { chunk ->
                audioBuffer.add(chunk)
            }
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(isRecording = false, isProcessing = true) }
        recordingJob?.cancel()
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Flatten audio
                val totalSize = audioBuffer.sumOf { it.size }
                val fullAudio = FloatArray(totalSize)
                var offset = 0
                for (chunk in audioBuffer) {
                    System.arraycopy(chunk, 0, fullAudio, offset, chunk.size)
                    offset += chunk.size
                }

                // 2. Process to Mel Spectrogram
                val (features, timeFrames) = AudioProcessor.computeMelSpectrogram(fullAudio)
                
                if (timeFrames == 0) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Audio too short") }
                    return@launch
                }

                // 3. Inference
                val currentVocab = vocab ?: return@launch
                val resultTranscript = inferenceEngine?.runInference(features, timeFrames, currentVocab) ?: ""

                // 4. Match
                val currentQuran = quran ?: return@launch
                val match = QuranMatcher.matchVerse(resultTranscript, currentQuran)

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
                            errorMessage = "No verse matched"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Processing error: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine?.close()
    }
}

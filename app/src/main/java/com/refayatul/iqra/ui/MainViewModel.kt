package com.refayatul.iqra.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.refayatul.iqra.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import kotlin.math.sqrt

enum class WordState { CORRECT, MISSING, WRONG }
data class WordCorrection(val word: String, val state: WordState)

enum class TafsirSource(val id: String, val displayName: String, val path: String, val language: String) {
    // Bengali
    FATHUL_MAJID("381", "Tafsir Fathul Majid", "bn-tafisr-fathul-majid", "Bengali"),
    IBN_KATHIR_BN("164", "Tafseer ibn Kathir", "bn-tafseer-ibn-e-kaseer", "Bengali"),
    AHSANUL_BAYAAN("165", "Tafsir Ahsanul Bayaan", "bn-tafsir-ahsanul-bayaan", "Bengali"),
    ABU_BAKR_ZAKARIA("166", "Tafsir Abu Bakr Zakaria", "bn-tafsir-abu-bakr-zakaria", "Bengali"),
    
    // English
    IBN_KATHIR_EN("169", "Tafsir Ibn Kathir (abridged)", "en-tafisr-ibn-kathir", "English"),
    TAZKIRUL_QURAN_EN("817", "Tazkirul Quran", "en-tazkirul-quran", "English"),
    MAARIF_UL_QURAN_EN("168", "Maarif-ul-Quran", "en-tafsir-maarif-ul-quran", "English"),
    AL_JALALAYN_EN("74", "Al-Jalalayn", "en-al-jalalayn", "English"),
    IBN_ABBAS_EN("73", "Tanwîr al-Miqbâs", "en-tafsir-ibn-abbas", "English"),
    ASBAB_AL_NUZUL_EN("86", "Asbab Al-Nuzul", "en-asbab-al-nuzul-by-al-wahidi", "English"),
    TUSTARI_EN("93", "Tafsir al-Tustari", "en-tafsir-al-tustari", "English"),
    KASHANI_EN("107", "Kashani Tafsir", "en-kashani-tafsir", "English"),
    AL_QUSHAIRI_EN("108", "Al Qushairi Tafsir", "en-al-qushairi-tafsir", "English"),
    KASHF_AL_ASRAR_EN("109", "Kashf Al-Asrar Tafsir", "en-kashf-al-asrar-tafsir", "English"),

    // Arabic
    SADDI_AR("91", "Tafseer Al Saddi", "ar-tafseer-al-saddi", "Arabic"),
    IBN_KATHIR_AR("14", "Tafsir Ibn Kathir", "ar-tafsir-ibn-kathir", "Arabic"),
    BAGHAWY_AR("94", "Tafseer Al-Baghawi", "ar-tafsir-al-baghawi", "Arabic"),
    TANWEER_AR("92", "Tafseer Tanwir al-Miqbas", "ar-tafseer-tanwir-al-miqbas", "Arabic"),
    WASEET_AR("93", "Tafsir Al Wasit", "ar-tafsir-al-wasit", "Arabic"),
    TABARI_AR("15", "Tafsir al-Tabari", "ar-tafsir-al-tabari", "Arabic"),
    MUYASSAR_AR("16", "Tafsir Muyassar", "ar-tafsir-muyassar", "Arabic"),
    QURTUBI_AR("90", "Tafseer Al Qurtubi", "ar-tafseer-al-qurtubi", "Arabic")
}

data class IqraUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isModelLoaded: Boolean = false,
    val matchedSurahId: Int? = null,
    val matchedSurahName: String? = null,
    val matchedSurahNameEn: String? = null,
    val matchedAyahNumber: Int? = null,
    val arabicText: String? = null,
    val translationEn: String? = null,
    val translationBn: String? = null,
    val transcript: String? = null,
    val errorMessage: String? = null,
    val isNoMatch: Boolean = false,
    val isDarkMode: Boolean = true,
    val isContinuousModeEnabled: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Ayah> = emptyList(),
    val surahIndex: List<SurahInfo> = emptyList(),
    val currentScreen: AppScreen = AppScreen.Home,
    val fullSurahAyahs: List<Ayah> = emptyList(),
    val currentAmplitude: List<Float> = List(30) { 0f },
    val verseCorrection: List<WordCorrection> = emptyList(),
    val isPlaying: Boolean = false,
    val isOnline: Boolean = true,
    val currentTafsir: String? = null,
    val isTafsirLoading: Boolean = false,
    val selectedTafsirSource: TafsirSource = TafsirSource.ABU_BAKR_ZAKARIA
)

data class SurahInfo(
    val id: Int,
    val name: String,
    val nameEn: String
)

sealed class AppScreen {
    object Home : AppScreen()
    object Search : AppScreen()
    data class SurahDetail(val surahId: Int, val ayahId: Int) : AppScreen()
}

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
    private var continuousProcessingJob: Job? = null
    private var searchJob: Job? = null
    
    private val SLIDING_WINDOW_SIZE = 16000 * 7
    private val audioBuffer = mutableListOf<FloatArray>()

    private var exoPlayer: ExoPlayer? = null

    init {
        checkNetworkStatus()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isProcessing = true) }
                vocab = assetManager.loadVocab()
                quran = assetManager.loadQuran()
                val surahs = quran?.groupBy { it.surah }?.map { (id, ayahs) ->
                    val first = ayahs.first()
                    SurahInfo(id, first.surah_name, first.surah_name_en)
                } ?: emptyList()
                
                val modelPath = assetManager.provideModelPath()
                inferenceEngine = TarteelInferenceEngine(modelPath).apply {
                    initialize()
                }
                _uiState.update { 
                    it.copy(
                        isProcessing = false, 
                        isModelLoaded = true,
                        surahIndex = surahs
                    ) 
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
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

    private fun checkNetworkStatus() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val isOnline = capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    fun fetchTafsir(surah: Int, ayah: Int) {
        checkNetworkStatus()
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(errorMessage = "Tafsir requires internet connection") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTafsirLoading = true, currentTafsir = null) }
            try {
                val source = _uiState.value.selectedTafsirSource
                val url = "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/${source.path}/$surah/$ayah.json"
                
                val result = withContext(Dispatchers.IO) {
                    val jsonString = URL(url).readText()
                    val jsonObject = Json.decodeFromString<JsonObject>(jsonString)
                    jsonObject["text"]?.jsonPrimitive?.content ?: "Tafsir content not found."
                }
                
                _uiState.update { it.copy(isTafsirLoading = false, currentTafsir = result) }
            } catch (e: Exception) {
                Log.e(TAG, "Tafsir fetch failed", e)
                _uiState.update { it.copy(isTafsirLoading = false, errorMessage = "Failed to load Tafsir") }
            }
        }
    }

    fun selectTafsirSource(source: TafsirSource) {
        _uiState.update { it.copy(selectedTafsirSource = source) }
        val state = _uiState.value
        if (state.matchedSurahId != null && state.matchedAyahNumber != null && state.currentTafsir != null) {
            fetchTafsir(state.matchedSurahId, state.matchedAyahNumber)
        }
    }

    @OptIn(UnstableApi::class)
    fun playRecitation(surah: Int, ayah: Int) {
        checkNetworkStatus()
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(errorMessage = "Playback requires internet connection") }
            return
        }
        stopRecitation()
        try {
            val surahStr = surah.toString().padStart(3, '0')
            val ayahStr = ayah.toString().padStart(3, '0')
            val url = "https://everyayah.com/data/Mishary_Rashid_Alafasy_64kbps/$surahStr$ayahStr.mp3"
            
            viewModelScope.launch(Dispatchers.Main) {
                exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                    val mediaItem = MediaItem.fromUri(url)
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                stopRecitation()
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ExoPlayer error", e)
            _uiState.update { it.copy(errorMessage = "Audio playback failed") }
        }
    }

    fun stopRecitation() {
        viewModelScope.launch(Dispatchers.Main) {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    fun toggleDarkMode() {
        _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun toggleContinuousMode() {
        _uiState.update { it.copy(isContinuousModeEnabled = !it.isContinuousModeEnabled) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // 300ms debounce
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        if (query.length < 3) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val results = quran?.filter { ayah ->
                ayah.translationEn.contains(query, ignoreCase = true) ||
                ayah.translationBn.contains(query, ignoreCase = true) ||
                ayah.surah_name_en.contains(query, ignoreCase = true)
            } ?: emptyList()
            _uiState.update { it.copy(searchResults = results.take(50)) }
        }
    }

    fun navigateToSearch() {
        _uiState.update { it.copy(currentScreen = AppScreen.Search, searchQuery = "", searchResults = emptyList()) }
    }

    fun navigateToSurah(surahId: Int, ayahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val ayahs = assetManager.getFullSurah(surahId)
            _uiState.update { 
                it.copy(
                    currentScreen = AppScreen.SurahDetail(surahId, ayahId),
                    fullSurahAyahs = ayahs
                )
            }
        }
    }

    fun navigateBack() {
        _uiState.update { it.copy(currentScreen = AppScreen.Home) }
    }

    fun resetState() {
        stopRecitation()
        _uiState.update { 
            it.copy(
                matchedSurahId = null,
                matchedSurahName = null,
                matchedSurahNameEn = null,
                matchedAyahNumber = null,
                arabicText = null,
                translationEn = null,
                translationBn = null,
                transcript = null,
                errorMessage = null,
                isNoMatch = false,
                isProcessing = false,
                isRecording = false,
                currentAmplitude = List(30) { 0f },
                verseCorrection = emptyList(),
                currentTafsir = null
            )
        }
        audioBuffer.clear()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (!_uiState.value.isModelLoaded || _uiState.value.isProcessing) return
        stopRecitation()
        viewModelScope.launch {
            recordingJob?.cancel()
            recordingJob?.join()
            continuousProcessingJob?.cancel()
            delay(200)
            audioBuffer.clear()
            _uiState.update { 
                it.copy(
                    isRecording = true, 
                    errorMessage = null,
                    isNoMatch = false,
                    matchedSurahName = null,
                    currentAmplitude = List(30) { 0f },
                    verseCorrection = emptyList(),
                    currentTafsir = null
                ) 
            }
            recordingJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    recorderHelper.recordAudio().collect { chunk -> 
                        audioBuffer.add(chunk)
                        var currentSize = audioBuffer.sumOf { it.size }
                        while (currentSize > SLIDING_WINDOW_SIZE && audioBuffer.isNotEmpty()) {
                            val removed = audioBuffer.removeAt(0)
                            currentSize -= removed.size
                        }
                        updateAmplitude(chunk)
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isRecording = false, errorMessage = "Recording failed") }
                }
            }
            if (_uiState.value.isContinuousModeEnabled) {
                startContinuousProcessing()
            }
        }
    }

    private fun startContinuousProcessing() {
        continuousProcessingJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(2500)
                if (!_uiState.value.isRecording) break
                val windowData = audioBuffer.toList()
                if (windowData.isNotEmpty()) {
                    processAudioChunk(windowData)
                }
            }
        }
    }

    private suspend fun processAudioChunk(buffer: List<FloatArray>) {
        try {
            val totalSize = buffer.sumOf { it.size }
            val fullAudio = FloatArray(totalSize)
            var offset = 0
            for (chunk in buffer) {
                System.arraycopy(chunk, 0, fullAudio, offset, chunk.size)
                offset += chunk.size
            }
            val (features, timeFrames) = AudioProcessor.computeMelSpectrogram(fullAudio)
            if (timeFrames > 0) {
                val resultTranscript = inferenceEngine?.runInference(features, timeFrames, vocab!!) ?: ""
                val match = QuranMatcher.matchVerse(
                    transcript = resultTranscript, 
                    quran = quran!!,
                    threshold = 0.85, 
                    currentSurah = _uiState.value.matchedSurahId,
                    currentAyah = _uiState.value.matchedAyahNumber
                )
                if (match != null) {
                    if (match.surah != _uiState.value.matchedSurahId || match.ayah != _uiState.value.matchedAyahNumber) {
                        val correction = computeCorrection(match.textUthmani, resultTranscript)
                        _uiState.update { state ->
                            state.copy(
                                matchedSurahId = match.surah,
                                matchedSurahName = match.surahName,
                                matchedSurahNameEn = match.surahNameEn,
                                matchedAyahNumber = match.ayah,
                                arabicText = match.textUthmani,
                                translationEn = match.translationEn,
                                translationBn = match.translationBn,
                                transcript = resultTranscript,
                                isNoMatch = false,
                                verseCorrection = correction,
                                currentTafsir = null // Clear tafsir for new verse
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Continuous processing error", e)
        }
    }

    private fun computeCorrection(verse: String, transcript: String): List<WordCorrection> {
        return try {
            val verseWords = verse.split("\\s+".toRegex()).filter { it.isNotBlank() }
            val normalizedTranscript = ArabicNormalizer.normalize(transcript)
            val transcriptWords = normalizedTranscript.split("\\s+".toRegex()).filter { it.isNotBlank() }
            var lastFoundIndex = -1
            verseWords.map { word ->
                val normWord = ArabicNormalizer.normalize(word)
                var foundAt = -1
                for (i in (lastFoundIndex + 1) until transcriptWords.size) {
                    if (transcriptWords[i] == normWord || transcriptWords[i].contains(normWord) || normWord.contains(transcriptWords[i])) {
                        foundAt = i
                        break
                    }
                }
                if (foundAt != -1) {
                    lastFoundIndex = foundAt
                    WordCorrection(word, WordState.CORRECT)
                } else {
                    val existsElsewhere = transcriptWords.any { it == normWord }
                    if (existsElsewhere) WordCorrection(word, WordState.WRONG) else WordCorrection(word, WordState.MISSING)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Correction alignment failed", e)
            emptyList()
        }
    }

    private fun updateAmplitude(chunk: FloatArray) {
        var sum = 0f
        for (sample in chunk) {
            sum += sample * sample
        }
        val rms = if (chunk.isNotEmpty()) sqrt(sum / chunk.size) else 0f
        val normalized = (rms * 5f).coerceIn(0f, 1f)
        _uiState.update { state ->
            val newAmplitudes = state.currentAmplitude.toMutableList().apply {
                removeAt(0)
                add(normalized)
            }
            state.copy(currentAmplitude = newAmplitudes)
        }
    }

    private fun stopRecording() {
        _uiState.update { it.copy(isRecording = false, isProcessing = !_uiState.value.isContinuousModeEnabled) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                recordingJob?.cancel()
                recordingJob?.join()
                continuousProcessingJob?.cancel()
                if (_uiState.value.isContinuousModeEnabled) {
                    _uiState.update { it.copy(isProcessing = false) }
                    return@launch
                }
                val totalSize = audioBuffer.sumOf { it.size }
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
                val (features, timeFrames) = AudioProcessor.computeMelSpectrogram(fullAudio)
                if (timeFrames == 0) {
                    _uiState.update { it.copy(isProcessing = false, errorMessage = "Audio too short") }
                    return@launch
                }
                val resultTranscript = inferenceEngine?.runInference(features, timeFrames, vocab!!) ?: ""
                val match = QuranMatcher.matchVerse(resultTranscript, quran!!)
                _uiState.update { state ->
                    if (match != null) {
                        val correction = computeCorrection(match.textUthmani, resultTranscript)
                        state.copy(
                            isProcessing = false,
                            isNoMatch = false,
                            matchedSurahId = match.surah,
                            matchedSurahName = match.surahName,
                            matchedSurahNameEn = match.surahNameEn,
                            matchedAyahNumber = match.ayah,
                            arabicText = match.textUthmani,
                            translationEn = match.translationEn,
                            translationBn = match.translationBn,
                            transcript = resultTranscript,
                            currentAmplitude = List(30) { 0f },
                            verseCorrection = correction,
                            currentTafsir = null
                        )
                    } else {
                        state.copy(
                            isProcessing = false,
                            isNoMatch = true,
                            transcript = resultTranscript,
                            errorMessage = if (resultTranscript.isBlank()) "No speech detected" else null,
                            currentAmplitude = List(30) { 0f },
                            verseCorrection = emptyList(),
                            currentTafsir = null
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
        stopRecitation()
        inferenceEngine?.close()
    }
}

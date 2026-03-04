package com.refayatul.iqra

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuranAssetManager(private val context: Context) {

    private val TAG = "QuranAssetManager"
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var englishMap: Map<String, String> = emptyMap()
    private var banglaMap: Map<String, String> = emptyMap()
    private var quranCache: List<Ayah>? = null

    /**
     * Loads vocab.json and returns a map of token ID to character.
     */
    suspend fun loadVocab(): Map<Int, String> = withContext(Dispatchers.IO) {
        var jsonString: String? = context.assets.open("data/vocab.json").bufferedReader().use { it.readText() }
        val jsonObject = json.decodeFromString<JsonObject>(jsonString!!)
        jsonString = null // Help GC
        
        jsonObject.mapNotNull { (key, value) ->
            val id = key.toIntOrNull()
            val char = value.jsonPrimitive.contentOrNull
            if (id != null && char != null) id to char else null
        }.toMap()
    }

    /**
     * Loads translations and populates englishMap and banglaMap.
     */
    suspend fun loadTranslations() = withContext(Dispatchers.IO) {
        try {
            var enString: String? = context.assets.open("data/quran_en.json").bufferedReader().use { it.readText() }
            var bnString: String? = context.assets.open("data/quran_bn.json").bufferedReader().use { it.readText() }

            val enSurahs = json.decodeFromString<List<TranslationSurah>>(enString!!)
            enString = null // Help GC
            
            val bnSurahs = json.decodeFromString<List<TranslationSurah>>(bnString!!)
            bnString = null // Help GC

            englishMap = enSurahs.flatMap { surah ->
                surah.verses.map { ayah -> "${surah.id}:${ayah.id}" to ayah.translation }
            }.toMap()

            banglaMap = bnSurahs.flatMap { surah ->
                surah.verses.map { ayah -> "${surah.id}:${ayah.id}" to ayah.translation }
            }.toMap()
            
            Log.d(TAG, "Translations loaded: EN=${englishMap.size}, BN=${banglaMap.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading translations", e)
        }
    }

    /**
     * Loads quran.json as a list of Ayah objects and attaches translations.
     */
    suspend fun loadQuran(): List<Ayah> = withContext(Dispatchers.IO) {
        quranCache?.let { return@withContext it }

        if (englishMap.isEmpty() || banglaMap.isEmpty()) {
            loadTranslations()
        }
        
        var jsonString: String? = context.assets.open("data/quran.json").bufferedReader().use { it.readText() }
        val ayahs = json.decodeFromString<List<Ayah>>(jsonString!!)
        jsonString = null // Help GC
        
        val loadedAyahs = ayahs.map { ayah ->
            val key = "${ayah.surah}:${ayah.ayah}"
            ayah.copy(
                translationEn = englishMap[key] ?: "Translation not found",
                translationBn = banglaMap[key] ?: "অনুবাদ পাওয়া যায়নি"
            )
        }
        quranCache = loadedAyahs
        loadedAyahs
    }

    /**
     * Searches for ayahs matching the query in English or Bangla.
     */
    suspend fun searchQuran(query: String): List<Ayah> = withContext(Dispatchers.Default) {
        if (query.length < 3) return@withContext emptyList<Ayah>()
        
        val allAyahs = loadQuran()
        val lowerQuery = query.lowercase()
        
        allAyahs.filter { ayah ->
            ayah.translationEn.lowercase().contains(lowerQuery) ||
            ayah.translationBn.contains(query) // Bangla doesn't have lowercase/uppercase
        }.take(50)
    }

    /**
     * Returns all ayahs for a specific surah.
     */
    suspend fun getFullSurah(surahId: Int): List<Ayah> = withContext(Dispatchers.IO) {
        val allAyahs = loadQuran()
        allAyahs.filter { it.surah == surahId }
    }

    /**
     * Copies the ONNX model from assets to the internal files directory.
     * Returns the absolute path to the copied file.
     */
    suspend fun provideModelPath(): String = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "fastconformer_ar_ctc_q8.onnx")
        if (!modelFile.exists()) {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            Log.i(TAG, "IQRA_LOG: Copying started at ${sdf.format(Date())}")
            
            context.assets.open("fastconformer_ar_ctc_q8.onnx").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "IQRA_LOG: Copying finished at ${sdf.format(Date())}")
        } else {
            Log.i(TAG, "IQRA_LOG: Model already exists at ${modelFile.absolutePath}")
        }
        modelFile.absolutePath
    }
}

@Serializable
data class Ayah(
    val surah: Int,
    val ayah: Int,
    val text_uthmani: String,
    val text_clean: String,
    val surah_name: String,
    val surah_name_en: String,
    val translationEn: String = "",
    val translationBn: String = ""
)

@Serializable
data class TranslationSurah(
    val id: Int,
    val name: String,
    val verses: List<TranslationAyah>
)

@Serializable
data class TranslationAyah(
    val id: Int,
    val text: String,
    val translation: String
)

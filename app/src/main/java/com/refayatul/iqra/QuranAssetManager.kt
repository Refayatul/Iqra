package com.refayatul.iqra

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Loads vocab.json and returns a map of token ID to character.
     */
    suspend fun loadVocab(): Map<Int, String> = withContext(Dispatchers.IO) {
        val jsonString = context.assets.open("data/vocab.json").bufferedReader().use { it.readText() }
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)
        jsonObject.mapNotNull { (key, value) ->
            val id = key.toIntOrNull()
            val char = value.jsonPrimitive.contentOrNull
            if (id != null && char != null) id to char else null
        }.toMap()
    }

    /**
     * Loads quran.json as a list of Ayah objects.
     */
    suspend fun loadQuran(): List<Ayah> = withContext(Dispatchers.IO) {
        val jsonString = context.assets.open("data/quran.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<Ayah>>(jsonString)
    }

    /**
     * Copies the ONNX model from assets to the internal files directory.
     * Returns the absolute path to the copied file.
     */
    suspend fun provideModelPath(): String = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, "fastconformer_ar_ctc_q8.onnx")
        if (!modelFile.exists()) {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            Log.d(TAG, "Copying started at ${sdf.format(Date())}")
            
            context.assets.open("fastconformer_ar_ctc_q8.onnx").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Copying finished at ${sdf.format(Date())}")
        } else {
            Log.d(TAG, "Model already exists at ${modelFile.absolutePath}")
        }
        modelFile.absolutePath
    }
}

@kotlinx.serialization.Serializable
data class Ayah(
    val surah: Int,
    val ayah: Int,
    val text_uthmani: String,
    val text_clean: String,
    val surah_name: String,
    val surah_name_en: String
)

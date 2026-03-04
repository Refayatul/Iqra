package com.refayatul.iqra

import kotlin.math.min

object QuranMatcher {

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var strA = a
        var strB = b
        if (strA.length > strB.length) {
            val temp = strA
            strA = strB
            strB = temp
        }

        val m = strA.length
        val n = strB.length
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (j in 1..n) {
            curr[0] = j
            for (i in 1..m) {
                val cost = if (strA[i - 1] == strB[j - 1]) 0 else 1
                curr[i] = min(
                    min(prev[i] + 1, curr[i - 1] + 1),
                    prev[i - 1] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[m]
    }

    fun ratio(a: String, b: String): Double {
        val lenSum = a.length + b.length
        if (lenSum == 0) return 1.0
        return (lenSum - distance(a, b)).toDouble() / lenSum
    }

    private val BSM_CLEAN = ArabicNormalizer.normalize("بسم الله الرحمن الرحيم")

    fun matchVerse(
        transcript: String,
        quran: List<Ayah>,
        threshold: Double = 0.3,
        currentSurah: Int? = null,
        currentAyah: Int? = null
    ): MatchResult? {
        val normalizedTranscript = ArabicNormalizer.normalize(transcript)
        if (normalizedTranscript.isBlank()) return null

        var bestScore = -1.0
        var bestAyah: Ayah? = null

        for (v in quran) {
            val vClean = ArabicNormalizer.normalize(v.text_clean)
            var score = ratio(normalizedTranscript, vClean)

            // Sequence Bias: Bonus for the next verse
            if (currentSurah != null && currentAyah != null) {
                if (v.surah == currentSurah && v.ayah == currentAyah + 1) {
                    score += 0.1 // 10% bonus for the immediate next verse
                }
            }

            // Try without Bismillah for verse 1s
            if (v.ayah == 1 && v.surah != 1 && v.surah != 9) {
                if (vClean.startsWith(BSM_CLEAN)) {
                    val stripped = vClean.removePrefix(BSM_CLEAN).trim()
                    if (stripped.isNotEmpty()) {
                        score = maxOf(score, ratio(normalizedTranscript, stripped))
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestAyah = v
            }
        }

        return if (bestScore >= threshold && bestAyah != null) {
            MatchResult(
                surah = bestAyah.surah,
                ayah = bestAyah.ayah,
                ayahEnd = null,
                surahName = bestAyah.surah_name,
                surahNameEn = bestAyah.surah_name_en,
                textUthmani = bestAyah.text_uthmani,
                translationEn = bestAyah.translationEn,
                translationBn = bestAyah.translationBn,
                score = bestScore
            )
        } else null
    }

    data class MatchResult(
        val surah: Int,
        val ayah: Int,
        val ayahEnd: Int?,
        val surahName: String,
        val surahNameEn: String,
        val textUthmani: String,
        val translationEn: String,
        val translationBn: String,
        val score: Double
    )
}

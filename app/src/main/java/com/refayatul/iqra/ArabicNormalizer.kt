package com.refayatul.iqra

object ArabicNormalizer {
    private val DIACRITICS_RE = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED\u0640]")
    
    private val NORM_MAP = mapOf(
        '\u0623' to '\u0627', // أ -> ا
        '\u0625' to '\u0627', // إ -> ا
        '\u0622' to '\u0627', // آ -> ا
        '\u0671' to '\u0627', // ٱ -> ا
        '\u0629' to '\u0647', // ة -> ه
        '\u0649' to '\u064A'  // ى -> ي
    )

    fun normalize(text: String): String {
        // 1. Strip diacritics
        var normalized = text.replace(DIACRITICS_RE, "")
        
        // 2. Normalize characters
        val builder = StringBuilder()
        for (char in normalized) {
            builder.append(NORM_MAP[char] ?: char)
        }
        normalized = builder.toString()
        
        // 3. Normalize whitespace
        return normalized.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
}

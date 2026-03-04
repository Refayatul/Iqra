# Iqra (اقরأ) - Premium Offline Quran Recognition & Learning

**Iqra** is a state-of-the-art native Android application designed for high-accuracy, 100% offline Quranic voice recognition. It combines on-device AI with a premium manuscript-inspired design to create a powerful tool for recitation improvement, memorization, and discovery.

## 🚀 Key Features

- **100% Offline AI**: All inference happens on-device using a quantized NVIDIA FastConformer model. No internet is required for recognition.
- **Continuous Recitation (Auto-Mode)**: Hands-free experience with a 7-second sliding window buffer that identifies verses in real-time as you recite.
- **Correction Mode**: Visual feedback on your recitation. Correct words are highlighted in theme colors, while missed or incorrect words are marked in red with underlines.
- **Identify & Play**: Listen to professional recitation by Mishary Rashid Alafasy for any identified verse to verify your Tajweed (requires internet).
- **Deep Study with Tafsir**: Access 22+ high-quality Tafsir sources in **Bengali, English, and Arabic** (requires internet). Includes classical works like Ibn Kathir, Al-Jalalayn, and modern interpretations.
- **Smart Topic Search**: Quickly find verses by keywords in English or Bangla. Features 300ms debouncing and background filtering for zero lag.
- **Premium Quranic Typography**: Features the professional **KFGQPC Uthman Taha** font with optimized 1.6em line spacing for clear diacritics.
- **Share as Image**: Generate and share high-resolution verse cards with Arabic text, translations, and "Iqra" branding.
- **Dual Premium Themes**:
    - **Deep Forest (Dark)**: Gold-on-charcoal luxury aesthetic.
    - **Classic Manuscript (Light)**: Traditional ink-on-cream feel.

## 🏗 Architecture & Tech Stack

- **UI**: Jetpack Compose (Material 3) with `FlowRow` for word-by-word alignment and `AnimatedContent` for screen transitions.
- **AI Inference**: [ONNX Runtime Mobile](https://onnxruntime.ai/).
- **Audio Engine**: Native `AudioRecord` at 16kHz mono with real-time RMS visualizer.
- **Media**: Media3 ExoPlayer for high-quality audio streaming.
- **Matching Engine**: Enhanced Levenshtein fuzzy matching with sequence bias (+10% bonus for sequential verses).
- **Memory Optimization**: Explicit GC-friendly JSON parsing and caching of processed datasets.

## 📦 Asset Requirements

Ensure these files are in `app/src/main/assets/data/`:
- `vocab.json`: CTC vocabulary mapping.
- `quran.json`: Flat Arabic dataset.
- `quran_en.json` & `quran_bn.json`: Nested translations.
- `fastconformer_ar_ctc_q8.onnx`: Root of `assets/`.
- `quran_font.ttf`: Professional font in `app/src/main/res/font/`.

## 📜 License

This project is licensed under the MIT License.

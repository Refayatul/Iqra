# Iqra (اقرأ) - Offline Quran Verse Recognition

**Iqra** is a native Android application that performs high-accuracy, completely offline Quranic voice recognition. By combining modern Android development practices with on-device AI, Iqra identifies Surahs and Ayahs as you recite them—no internet connection required.

This project is a native Android port of the [Offline Tarteel](https://github.com/yazinsai/offline-tarteel) repository.

## 🚀 Features

- **100% Offline**: All AI inference and database matching happen on-device. Your voice never leaves your phone.
- **Fast Recognition**: Powered by a quantized NVIDIA FastConformer model (~0.3s - 1.0s latency).
- **Fuzzy Matching**: Uses Levenshtein distance algorithms to handle minor recitation mistakes or background noise.
- **Modern UI**: Built with Jetpack Compose and Material 3, featuring a clean, minimalist aesthetic and pulsing recording animations.
- **Uthmani Script**: Displays results in beautiful, readable Arabic script with RTL support.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel, StateFlow)
- **AI Inference**: [ONNX Runtime Mobile](https://onnxruntime.ai/)
- **Audio Processing**: Native `AudioRecord` (16kHz, Mono, PCM-16 to Float32)
- **Concurrency**: Kotlin Coroutines (Inference and math operations off-loaded to `Dispatchers.Default`)
- **Data Parsing**: `kotlinx.serialization` for vocabulary and Quranic datasets.

## 🏗 Architecture

The app follows a robust 4-step pipeline:

1.  **Audio Capture**: Native recording at 16kHz mono.
2.  **Mel Spectrogram**: Custom Kotlin implementation of NeMo-compatible 80-bin Mel features (Dithering, Pre-emphasis, Slaney normalization).
3.  **ONNX Inference**: Runs the quantized FastConformer model to produce CTC logprobs.
4.  **Decode & Match**: CTC Greedy decoding generates a transcript, which is then fuzzy-matched against all 6,236 verses using normalized Arabic text.

## 📦 Installation & Setup

### Prerequisites
- Android Studio Ladybug or newer.
- A physical Android device (API 24+). *Note: Emulators are not recommended for microphone-based AI testing.*

### Asset Setup
Before building, ensure the following files are in `app/src/main/assets/data/`:
- `vocab.json`: The CTC vocabulary mapping.
- `quran.json`: The dataset of all 6,236 verses.
- `fastconformer_ar_ctc_q8.onnx`: The quantized model file (Place in `assets/` root).

### Building
1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle.
4. Click **Run**.

## 🤝 Acknowledgments

- **NVIDIA**: For the FastConformer ASR architecture.
- **Tarteel.ai**: For inspiring the mission of offline Quranic tools.
- **Yazin (yazinsai)**: For the [Offline Tarteel](https://github.com/yazinsai/offline-tarteel) logic and model quantization that made this port possible.

## 📜 License

This project is licensed under the MIT License. The underlying NVIDIA model is licensed under CC-BY-4.0.

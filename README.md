# Voice Mobile

**Voice Mobile** is a native Android **Qwen3-TTS** speech synthesis, voice cloning, and voice design studio — the mobile counterpart to [voicecli](https://github.com/ultrapg/voicecli) — built for **Android 15 through Android 17** (`minSdk = 35`, `targetSdk = 37`, 16 KB ELF page-size aligned) with a minimalistic **Material Design 3** interface and pure multi-core **ARM64 SIMD CPU** inference.

All synthesis, speaker encoding, 12Hz neural vocoding, streaming playback, and audio DSP run **100% locally on-device** with zero cloud APIs, zero telemetry, and no command line required. Network access is used solely when downloading GGUF model weights from Hugging Face.

---

## Installation

Download **`VoiceMobile_release.apk`** from the [Latest GitHub Release](https://github.com/ultrapg/voicemobile/releases/latest) and install it on any `arm64-v8a` device running **Android 15, 16, or 17**.

Models can be fetched on demand inside the app (automatically on first synthesis or from the **Models** tab) or imported offline from local `.gguf` files.

---

## Features

### 1. Studio (`Speak` · `Clone` · `Design`)
- **Speak (Built-In Studio Speakers)**:
  - Synthesize speech with built-in Qwen3-TTS CustomVoice speakers (`Vivian`, `Serena`, `Ryan`, `Aiden`, `Uncle Fu`, `Ono Anna`, `Sohee`, `Eric`, `Dylan`, or custom speaker tables embedded in imported GGUFs).
  - Supports optional natural-language **Emotion / Delivery Instructions** when using `1.7B` CustomVoice models.
- **Clone (Zero-Shot & Full-ICL Voice Cloning)**:
  - Record a `24 kHz` mono PCM16 reference sample directly from the microphone, import any common audio file (`WAV`, `MP3`, `M4A`, `AAC`, `FLAC`, `OGG` — decoded and resampled on-device via `MediaExtractor` / `MediaCodec`), or pick a saved Voice Profile.
  - Supports **Zero-Shot x-vector** cloning (audio only) and **Full In-Context Learning (`Full-ICL`)** cloning (audio + reference transcript), with an option to save newly cloned voices directly as reusable profiles.
- **Design (Natural-Language Voice Design)**:
  - Generate custom voices from acoustic text descriptions (gender, timbre, warmth, pacing, accent) powered by Qwen3-TTS `1.7B` VoiceDesign models, with 6 one-tap studio style presets (*Warm Narrator*, *Crisp Host*, *Deep Cinematic*, *Gentle Calm*, *Upbeat Guide*, *News Anchor*).
- **Synthesis, Streaming & DSP Controls**:
  - **Languages**: `Auto Detect`, `English`, `German`, `Spanish`, `Chinese`, `Japanese`, `French`, `Korean`, `Russian`, `Italian`, and `Portuguese`.
  - **Live 24 kHz Streaming**: Stateful streaming 12Hz vocoder over `AudioTrack` (`ENCODING_PCM_FLOAT`) with configurable chunk windows (`0.25s–2.50s`), full hardware playback draining, and live **TTFA** / **RTF** metrics.
  - **Pitch-Preserving Speed (`0.25x–3.00x`)**: Native C++ O(1) prefix-sum **SOLA** time-stretching for both batch and live streaming output.
  - **Audio Post-Processing**: Two-pass DC offset removal, `-0.5 dBFS` peak normalization, soft-knee limiting, and `-45 dBFS` inter-chunk silence trimming with Hann crossfades.
  - **Markdown Cleaner & Linguistic Chunker**: Optional stripping of Markdown/HTML/URLs/code blocks and sentence/clause chunking (`Auto >800 chars`, `Always Chunk`, or `Single Pass`, `200–1500` chars) preserving abbreviations, decimals, ellipses, and CJK punctuation.
  - **Autoregressive Sampling**: Configurable Temperature (`0.00–1.40`), Top-P (`0.10–1.00`), Top-K (`0–100`), Repetition Penalty (`1.00–1.35`), and Max Codec Frames (`512–8192`).

### 2. Voices (`Profiles` · `Audio Clips`)
- **Voice Profile Lab**: Create standalone **Full-ICL (`.json`)** profiles (ECAPA-TDNN embedding + 12Hz reference codec codes + transcript) or **Fast Embedding (`.bin`)** profiles (binary float32 speaker embedding) directly compatible with desktop `voicecli` (`voicecli profile`), with SAF `.json` / `.bin` import, export, rename, and sharing.
- **Generated Clips Library**: Browse synthesized `24 kHz` WAV clips with interactive waveform scrubbing, mode/RTF metadata, 1-tap device save, and Android Share integration.

### 3. Models & CPU (`0.6B` & `1.7B` GGUF Hub + ARM64 Runtime)
- **All 24 Official Qwen3-TTS GGUF Models**: Switch between **Qwen3-TTS `0.6B`** and **Qwen3-TTS `1.7B`** in one tap from either Studio or the **Models** tab across all four quantizations (`Q4_K_M`, `Q8_0`, `BF16`, `F32`), select individual per-role or codec GGUFs, or import local `.gguf` weights via SAF:

| Model Family | Role | `Q4_K_M` | `Q8_0` | `BF16` | `F32` |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `qwen-talker-0.6b-customvoice` | Speak (Built-In Speakers) | 577 MB | 924 MB | 1,733 MB | 3,461 MB |
| `qwen-talker-0.6b-base` | Clone & Voice Profiles | 600 MB | 947 MB | 1,756 MB | 3,495 MB |
| `qwen-talker-1.7b-customvoice` | Speak + Emotion Steering | 1,128 MB | 1,948 MB | 3,662 MB | 7,317 MB |
| `qwen-talker-1.7b-base` | Clone & Voice Profiles | 1,163 MB | 1,983 MB | 3,697 MB | 7,363 MB |
| `qwen-talker-1.7b-voicedesign` | Voice Design *(1.7B only upstream)* | 1,128 MB | 1,948 MB | 3,662 MB | 7,317 MB |
| `qwen-tokenizer-12hz` | Shared 12Hz Audio Codec / Vocoder | 243 MB | 278 MB | 342 MB | 617 MB |

> **Note**: Upstream Qwen3-TTS provides VoiceDesign exclusively in `1.7B`. In `0.6B` presets, Speak and Clone use `0.6B` weights while Voice Design uses the matching `1.7B` VoiceDesign quantization.

- **Resumable Hugging Face Downloader**: HTTP `Range` resume support for [`Serveurperso/Qwen3-TTS-GGUF`](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) with GGUF magic header and file-size validation.
- **ARM64 SIMD CPU Engine (`libggml-cpu.so`)**: Pure CPU execution utilizing ARM64 SIMD extensions (`NEON`, `FP16`, `DotProd`, `I8MM`, `BF16`, `SVE`) — no NPU or GPU drivers required. Includes live SoC/ISA detection, configurable CPU thread count (`Auto` or manual slider), and **Low-Memory Mode** (unloads talker weights before 12Hz vocoding to reduce peak RAM).

### 4. Android System Integration
- **Share & Text Selection Targets**: Send text directly into Voice Mobile from any browser, reader, or notes app via the Android Share sheet (`ACTION_SEND`) or text-selection toolbar (`ACTION_PROCESS_TEXT`).
- **Foreground Execution**: Runs long-form synthesis, live streaming playback, and model downloads inside a foreground service so active jobs continue cleanly while switching apps.

---

## System Requirements

- **Android Version**: Android 15, 16, or 17 (`API 35–37`)
- **CPU ABI**: `arm64-v8a` (16 KB ELF page-size aligned)
- **Memory**: 6 GB RAM minimum (enable *Low-Memory Mode* with `0.6B Q4_K_M`); 8 GB+ RAM recommended for `1.7B` models

---

## Building from Source

### Prerequisites
- JDK 17+
- Android SDK Platform 37 (`compileSdk = 37`), Android NDK `27.2.12479018`, and CMake `3.22.1`

### Build & Unit Tests
```bash
./gradlew testReleaseUnitTest assembleRelease
```

The release APK is generated at `app/build/outputs/apk/release/app-release.apk` (optionally signed via `RELEASE_KEYSTORE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`).

---

## Credits & License

- **Upstream CLI**: [ultrapg/voicecli](https://github.com/ultrapg/voicecli)
- **Qwen3-TTS C++ Engine & GGUF Weights**: [Serveurperso/Qwen3-TTS-GGUF](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) / [qwen3-tts.cpp](https://github.com/Serveurperso/qwen3-tts.cpp)
- **GGML ARM64 CPU Tensor Runtime**: [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp)
- **License**: [GNU General Public License v3.0](LICENSE)

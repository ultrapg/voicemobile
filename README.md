# Voice Mobile

**Voice Mobile** is the fully native Android mobile counterpart to [voicecli](https://github.com/ultrapg/voicecli) — an offline, on-device **Qwen3-TTS** neural speech synthesis studio built for **Android 15 through Android 17** (`minSdk = 35`, `targetSdk = 37`, 16 KB page-size aligned) with a minimalistic **Material Design 3** interface and multi-core **ARM64 SIMD CPU** execution.

Zero cloud APIs, zero telemetry, zero command line — every synthesis mode, `0.6B` & `1.7B` Qwen3-TTS model variant, voice profile workflow, streaming vocoder pipeline, linguistic chunker, and DSP post-processor from `voicecli` runs natively on your phone.

---

## Highlights

### Pure ARM64 SIMD CPU Runtime (`libggml-cpu.so`)
- **Multi-Core ARM64 Execution**: Runs 100% locally on ARMv8.2+ CPUs using NEON, FP16, DotProd, I8MM, BF16, and SVE SIMD kernels for deterministic, artifact-free autoregressive talker generation, Conv1D/ConvNeXt 12Hz audio vocoding, and ECAPA-TDNN speaker encoding across every Android 15–17 ARM64 device.
- **Configurable CPU Threads & Low-Memory Mode**: Tune active compute thread count (`Auto` or manual `1–16` threads) and enable sequential Low-Memory Mode to unload talker weights before vocoding on lower-RAM devices.

### Complete Qwen3-TTS `0.6B` & `1.7B` Model Selection (All 24 Official GGUFs)
- **One-Tap `0.6B` vs `1.7B` Switching**: Pick directly between **Qwen3-TTS `0.6B`** and **Qwen3-TTS `1.7B`** in both Studio and the Models tab, across all four quantizations (`Q4_K_M`, `Q8_0`, `BF16`, and `F32`).
- **Full 24-Model Hugging Face Catalog (`Serveurperso/Qwen3-TTS-GGUF`)**:
  - **`0.6B` CustomVoice (`Speak`)**: `Q4_K_M` (577 MB), `Q8_0` (924 MB), `BF16` (1,733 MB), `F32` (3,461 MB)
  - **`0.6B` Base (`Clone & Profiles`)**: `Q4_K_M` (600 MB), `Q8_0` (947 MB), `BF16` (1,756 MB), `F32` (3,495 MB)
  - **`1.7B` CustomVoice (`Speak + Emotion`)**: `Q4_K_M` (1,128 MB), `Q8_0` (1,948 MB), `BF16` (3,662 MB), `F32` (7,317 MB)
  - **`1.7B` Base (`Clone & Profiles`)**: `Q4_K_M` (1,163 MB), `Q8_0` (1,983 MB), `BF16` (3,697 MB), `F32` (7,363 MB)
  - **`1.7B` VoiceDesign (`Design`)**: `Q4_K_M` (1,128 MB), `Q8_0` (1,948 MB), `BF16` (3,662 MB), `F32` (7,317 MB)
  - **`12Hz` Audio Codec / Vocoder**: `Q4_K_M` (243 MB), `Q8_0` (278 MB), `BF16` (342 MB), `F32` (617 MB)
- **Resumable Downloader & Local `.gguf` Import**: Built-in resumable HTTP `Range` downloader with GGUF magic/size verification plus SAF local `.gguf` import and per-role overrides.

### Complete `voicecli` Feature Parity — Minimalistic Material Design 3 UI
- **Speak (Built-In Studio Voices)**: Synthesize with built-in Qwen3-TTS speakers (`Vivian`, `Serena`, `Ryan`, `Aiden`, `Uncle Fu`, `Ono Anna`, `Sohee`, `Eric`, `Dylan`, or custom GGUF speaker tables) plus optional natural-language emotion & delivery steering on `1.7B` models.
- **Clone (Zero-Shot & Full-ICL Voice Cloning)**:
  - Record directly at **24 kHz mono PCM16** from the device microphone or import any audio file (`WAV`, `MP3`, `M4A`, `AAC`, `FLAC`, `OGG` — automatically decoded and resampled on-device via `MediaExtractor`/`MediaCodec`).
  - Supports both **Zero-Shot x-vector** cloning (audio only) and **Full In-Context Learning (Full-ICL)** cloning (audio + reference transcript).
  - Inline or standalone **Voice Profile Lab** compatible with desktop `voicecli` profiles:
    - **Full-ICL (`.json`)**: Stores ECAPA-TDNN speaker embedding + 12Hz reference codec tokens + transcript.
    - **Fast Embedding (`.bin`)**: Compact binary speaker embedding (`~4–8 KB`) for instant transcript-free cloning.
- **Design (Natural-Language Voice Design)**: Create brand-new voices from acoustic text prompts (gender, timbre, warmth, pacing, accent) with one-tap studio presets.
- **Live 24 kHz Streaming & Native Audio DSP**:
  - Asynchronous, underrun-free `AudioTrack` (`ENCODING_PCM_FLOAT`) stateful streaming vocoder pipeline with configurable chunk window (`0.25s–2.50s`), full hardware buffer draining, and live **TTFA** / **RTF** metrics.
  - Native C++ **O(1) prefix-sum SOLA** pitch-preserving time-stretcher (`0.25x–3.0x`) for both batch and live streaming, smooth soft-knee peak limiting, two-pass DC offset removal + `-0.5 dBFS` peak normalization, and `-45 dBFS` inter-chunk silence trimming with Hann crossfades.
- **Document & System Integration**:
  - Import `.txt` / `.md` files, strip Markdown/HTML/code blocks automatically, and split long articles with an abbreviation-, decimal-, ellipsis-, and CJK-aware linguistic sentence/clause chunker.
  - System-wide Android **Share** (`ACTION_SEND`) and **Text Selection** (`ACTION_PROCESS_TEXT`) targets so you can send text from any browser or reader straight into Voice Mobile.

---

## System Requirements

- **OS**: Android 15, 16, or 17 (`API 35–37`)
- **Architecture**: `arm64-v8a` (16 KB ELF page-size aligned)
- **RAM**: 6 GB minimum (with *Low-Memory Mode* enabled for `0.6B Q4_K_M`); 8 GB–16 GB recommended for `1.7B` models

---

## Building from Source

### Prerequisites
- JDK 17 or 21
- Android SDK Platform 37 (`platforms;android-37.0`), Build-Tools `37.0.0`, and Android NDK `27.2.12479018`

### Build Commands
```bash
./gradlew testReleaseUnitTest assembleRelease
```

The signed release APK is written to `app/build/outputs/apk/release/app-release.apk`.

---

## Credits & License

- **Upstream CLI**: [ultrapg/voicecli](https://github.com/ultrapg/voicecli)
- **Native Qwen3-TTS C++ Engine & GGUF Weights**: [Serveurperso/Qwen3-TTS-GGUF](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) / [qwen3-tts.cpp](https://github.com/Serveurperso/qwen3-tts.cpp)
- **GGML ARM64 CPU Tensor Runtime**: [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp)
- **License**: [GNU General Public License v3.0](LICENSE)

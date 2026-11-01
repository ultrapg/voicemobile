# Voice Mobile

**Voice Mobile** is the fully native Android mobile counterpart to [voicecli](https://github.com/ultrapg/voicecli) — an offline, on-device **Qwen3-TTS** neural speech synthesis studio built for **Android 15 through Android 17** (`minSdk = 35`, `targetSdk = 37`, 16 KB page-size aligned) with a minimalistic **Material Design 3** interface and heterogeneous **Qualcomm Hexagon NPU**, **OpenCL GPU**, and **ARM64 CPU** acceleration.

Zero cloud APIs, zero telemetry, zero command line — every synthesis mode, voice profile workflow, streaming vocoder pipeline, linguistic chunker, and DSP post-processor from `voicecli` runs natively on your phone.

---

## Highlights

### Heterogeneous On-Device Compute (`NPU` · `GPU` · `CPU` · `Auto`)
- **Qualcomm Snapdragon Hexagon NPU (`libggml-hexagon.so`)**: Direct FastRPC CDSP offload (`libcdsprpc.so`) targeting Hexagon Tensor Processor **HVX** vector units and **HMX** tiled matrix extensions across Snapdragon architectures (`v73`, `v75`, `v79`, `v81`, `v85` — Snapdragon 8 Gen 2, 8 Gen 3, 8s Gen 3/4, 8 Elite, and 8 Elite Gen 5), with FP32 flash-attention accumulation, contiguous 2D KV-cache layout, and configurable DCVS power profiles (*Sustained High*, *Burst*, *Balanced*, *Power Saver*).
- **Mobile OpenCL GPU (`libggml-opencl.so`)**: Tiled Adreno & mobile OpenCL tensor kernels for autoregressive talker & code-predictor generation.
- **ARM64 SIMD CPU (`libggml-cpu.so`)**: Multi-core ARMv8.2+ NEON, FP16, and DotProd execution for full-CPU synthesis as well as dedicated Conv1D/ConvNeXt 12Hz audio vocoding and ECAPA-TDNN speaker encoding alongside NPU/GPU talker offload.
- **Smart `Auto` Cascade & Safe Fallback**: Automatically probes `Hexagon NPU → OpenCL GPU → ARM64 CPU` via runtime `dlopen` so the app runs cleanly across Qualcomm Snapdragon flagships and non-Qualcomm ARM64 devices alike.

### Complete `voicecli` Feature Parity — Minimalistic Material Design 3 UI
- **Speak (Built-In Studio Voices)**: Synthesize with built-in Qwen3-TTS speakers (`Vivian`, `Serena`, `Ryan`, `Aiden`, `Uncle Fu`, `Ono Anna`, `Sohee`, `Eric`, `Dylan`, or custom GGUF speaker tables) plus optional natural-language emotion & delivery steering.
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
- **Resumable Hugging Face GGUF Hub & Local `.gguf` Import**:
  - Built-in resumable HTTP `Range` downloader for [`Serveurperso/Qwen3-TTS-GGUF`](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) across all 6 presets (`0.6b-q4`, `0.6b`, `q4`, `q8`, `0.6b-bf16`, `bf16`), GGUF magic header verification, Low-Memory sequential mode, and SAF local `.gguf` import.

---

## System Requirements

- **OS**: Android 15, 16, or 17 (`API 35–37`)
- **Architecture**: `arm64-v8a` (16 KB ELF page-size aligned)
- **RAM**: 6 GB minimum (with *Low-Memory Mode* enabled for `0.6B Q4`); 8 GB–16 GB recommended for `1.7B` models

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
- **GGML Tensor Runtime (Hexagon HTP, OpenCL, ARM64 CPU)**: [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp)
- **License**: [GNU General Public License v3.0](LICENSE)

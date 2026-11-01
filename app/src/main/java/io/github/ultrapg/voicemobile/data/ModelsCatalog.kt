package io.github.ultrapg.voicemobile.data

import io.github.ultrapg.voicemobile.engine.SynthesisMode
import java.io.File
import java.io.RandomAccessFile

enum class ModelPreset(
    val id: String,
    val title: String,
    val badge: String,
    val summary: String,
    val styleModelFile: String,
    val cloneModelFile: String,
    val speakModelFile: String,
    val codecModelFile: String
) {
    FAST_06B_Q8(
        id = "0.6b",
        title = "Balanced Fast (0.6B Q8)",
        badge = "Recommended",
        summary = "Crisp 8-bit 0.6B Q8_0 for Clone & Speak (~1.2 GB to start), 1.7B Q8_0 for Design",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-Q8_0.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-Q8_0.gguf",
        codecModelFile = "qwen-tokenizer-12hz-Q8_0.gguf"
    ),
    TINY_06B_Q4(
        id = "0.6b-q4",
        title = "Ultra Compact (0.6B Q4)",
        badge = "0.6B Q4",
        summary = "Smallest footprint (~850 MB to start) with 4-bit quantization",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-Q4_K_M.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-Q4_K_M.gguf",
        codecModelFile = "qwen-tokenizer-12hz-Q8_0.gguf"
    ),
    FULL_17B_Q4(
        id = "q4",
        title = "Full 1.7B Compact (Q4_K_M)",
        badge = "1.7B Q4",
        summary = "Full 1.7B expressiveness across all modes in 4-bit quantization",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-Q4_K_M.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-Q4_K_M.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FULL_17B_Q8(
        id = "q8",
        title = "Studio 1.7B (Q8_0)",
        badge = "1.7B Q8",
        summary = "High-fidelity 8-bit 1.7B models for flagship 12 GB+ RAM devices",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-Q8_0.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-Q8_0.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FAST_06B_BF16(
        id = "0.6b-bf16",
        title = "Unquantized 0.6B (BF16)",
        badge = "0.6B BF16",
        summary = "Full BFloat16 precision 0.6B models for Clone & Speak",
        styleModelFile = "qwen-talker-1.7b-voicedesign-BF16.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-BF16.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-BF16.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FULL_17B_BF16(
        id = "bf16",
        title = "Reference 1.7B (BF16)",
        badge = "1.7B BF16",
        summary = "Uncompressed BFloat16 desktop reference weights (requires 16 GB RAM)",
        styleModelFile = "qwen-talker-1.7b-voicedesign-BF16.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-BF16.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-BF16.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    );

    fun talkerFileForMode(mode: SynthesisMode): String = when (mode) {
        SynthesisMode.DESIGN -> styleModelFile
        SynthesisMode.CLONE -> cloneModelFile
        SynthesisMode.SPEAK -> speakModelFile
    }

    fun allRequiredFiles(): List<String> = listOf(
        codecModelFile,
        styleModelFile,
        cloneModelFile,
        speakModelFile
    ).distinct()

    companion object {
        fun fromId(id: String): ModelPreset =
            entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) ||
                    (id.equals("tiny", ignoreCase = true) && it == TINY_06B_Q4) ||
                    (id.equals("fast", ignoreCase = true) && it == FAST_06B_Q8)
            } ?: FAST_06B_Q8
    }
}

data class ModelFileSpec(
    val fileName: String,
    val displayTitle: String,
    val roleLabel: String,
    val approxSizeMb: Int
) {
    val downloadUrl: String
        get() = "${ModelsCatalog.HF_BASE_URL}/$fileName"
}

object ModelsCatalog {

    const val HF_REPO = "Serveurperso/Qwen3-TTS-GGUF"
    const val HF_BASE_URL = "https://huggingface.co/$HF_REPO/resolve/main"

    val DEFAULT_SPEAKERS = listOf(
        "vivian", "serena", "ryan", "aiden", "uncle_fu", "ono_anna", "sohee", "eric", "dylan"
    )

    val STYLE_PRESETS = listOf(
        "Warm Narrator" to "A warm, calm studio narrator voice with clear articulation, natural pacing, and rich midrange resonance.",
        "Crisp Host" to "A bright, articulate podcast host speaking with steady energy, friendly warmth, and crisp diction.",
        "Deep Cinematic" to "A deep, resonant baritone voice speaking slowly with measured authority and subtle dramatic presence.",
        "Gentle Calm" to "A soft, soothing voice with gentle breathiness, relaxed tempo, and reassuring warmth.",
        "Upbeat Guide" to "An enthusiastic, upbeat voice with lively intonation, brisk pacing, and approachable clarity.",
        "News Anchor" to "A neutral, composed broadcast anchor voice with balanced pitch, crisp consonants, and steady cadence."
    )

    private val KNOWN_SPECS: Map<String, ModelFileSpec> = listOf(
        // Shared 12Hz Audio Codec / Vocoder
        ModelFileSpec("qwen-tokenizer-12hz-Q4_K_M.gguf", "12Hz Audio Codec (Q4_K_M)", "Shared Vocoder & Tokenizer", 243),
        ModelFileSpec("qwen-tokenizer-12hz-Q8_0.gguf", "12Hz Audio Codec (Q8_0)", "Shared Vocoder & Tokenizer", 277),
        ModelFileSpec("qwen-tokenizer-12hz-BF16.gguf", "12Hz Audio Codec (BF16)", "Shared Vocoder & Tokenizer", 342),

        // 0.6B Talkers
        ModelFileSpec("qwen-talker-0.6b-base-Q4_K_M.gguf", "Qwen3-TTS 0.6B Base (Q4_K_M)", "Voice Cloning & Profiles", 599),
        ModelFileSpec("qwen-talker-0.6b-base-Q8_0.gguf", "Qwen3-TTS 0.6B Base (Q8_0)", "Voice Cloning & Profiles", 946),
        ModelFileSpec("qwen-talker-0.6b-base-BF16.gguf", "Qwen3-TTS 0.6B Base (BF16)", "Voice Cloning & Profiles", 1756),
        ModelFileSpec("qwen-talker-0.6b-customvoice-Q4_K_M.gguf", "Qwen3-TTS 0.6B CustomVoice (Q4_K_M)", "Built-In Speakers", 576),
        ModelFileSpec("qwen-talker-0.6b-customvoice-Q8_0.gguf", "Qwen3-TTS 0.6B CustomVoice (Q8_0)", "Built-In Speakers", 923),
        ModelFileSpec("qwen-talker-0.6b-customvoice-BF16.gguf", "Qwen3-TTS 0.6B CustomVoice (BF16)", "Built-In Speakers", 1733),

        // 1.7B Talkers
        ModelFileSpec("qwen-talker-1.7b-voicedesign-Q4_K_M.gguf", "Qwen3-TTS 1.7B VoiceDesign (Q4_K_M)", "Voice Design", 1127),
        ModelFileSpec("qwen-talker-1.7b-voicedesign-Q8_0.gguf", "Qwen3-TTS 1.7B VoiceDesign (Q8_0)", "Voice Design", 1948),
        ModelFileSpec("qwen-talker-1.7b-voicedesign-BF16.gguf", "Qwen3-TTS 1.7B VoiceDesign (BF16)", "Voice Design", 3661),
        ModelFileSpec("qwen-talker-1.7b-base-Q4_K_M.gguf", "Qwen3-TTS 1.7B Base (Q4_K_M)", "Voice Cloning & Profiles", 1162),
        ModelFileSpec("qwen-talker-1.7b-base-Q8_0.gguf", "Qwen3-TTS 1.7B Base (Q8_0)", "Voice Cloning & Profiles", 1983),
        ModelFileSpec("qwen-talker-1.7b-base-BF16.gguf", "Qwen3-TTS 1.7B Base (BF16)", "Voice Cloning & Profiles", 3696),
        ModelFileSpec("qwen-talker-1.7b-customvoice-Q4_K_M.gguf", "Qwen3-TTS 1.7B CustomVoice (Q4_K_M)", "Built-In Speakers", 1127),
        ModelFileSpec("qwen-talker-1.7b-customvoice-Q8_0.gguf", "Qwen3-TTS 1.7B CustomVoice (Q8_0)", "Built-In Speakers", 1948),
        ModelFileSpec("qwen-talker-1.7b-customvoice-BF16.gguf", "Qwen3-TTS 1.7B CustomVoice (BF16)", "Built-In Speakers", 3661)
    ).associateBy { it.fileName }

    fun specFor(fileName: String): ModelFileSpec =
        KNOWN_SPECS[fileName] ?: ModelFileSpec(
            fileName = fileName,
            displayTitle = fileName.removeSuffix(".gguf"),
            roleLabel = if (fileName.contains("tokenizer", ignoreCase = true)) "Audio Codec" else "Custom GGUF",
            approxSizeMb = 0
        )

    fun isValidGgufFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 64L) return false
        val spec = KNOWN_SPECS[file.name]
        if (spec != null && spec.approxSizeMb > 0) {
            val minPlausibleBytes = spec.approxSizeMb.toLong() * 750_000L
            if (file.length() < minPlausibleBytes) return false
        }
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                b0 == 'G'.code && b1 == 'G'.code && b2 == 'U'.code && b3 == 'F'.code
            }
        }.getOrDefault(false)
    }

    /**
     * Resolves an already-installed codec file in [modelDir] if present (e.g. if BF16 codec is
     * already downloaded, it can be shared with 0.6B presets without re-downloading Q8_0).
     */
    fun resolveInstalledOrPreferredCodec(modelDir: File, preferredCodecFile: String): File {
        val exact = File(modelDir, preferredCodecFile)
        if (isValidGgufFile(exact)) return exact

        val fallbacks = listOf(
            "qwen-tokenizer-12hz-BF16.gguf",
            "qwen-tokenizer-12hz-Q8_0.gguf",
            "qwen-tokenizer-12hz-Q4_K_M.gguf",
            "qwen-tokenizer-12hz-F32.gguf"
        )
        for (name in fallbacks) {
            val f = File(modelDir, name)
            if (isValidGgufFile(f)) return f
        }
        return exact
    }

    /**
     * Finds any installed Base (clone/encoder) GGUF in [modelDir] for Voice Profile extraction,
     * falling back to the active preset's clone model filename if none is downloaded yet.
     */
    fun resolveBestBaseModelForProfiles(modelDir: File, preset: ModelPreset): File {
        val preferred = File(modelDir, preset.cloneModelFile)
        if (isValidGgufFile(preferred)) return preferred

        val candidates = modelDir.listFiles().orEmpty().filter {
            it.extension.equals("gguf", ignoreCase = true) &&
                it.name.contains("base", ignoreCase = true) &&
                isValidGgufFile(it)
        }
        return candidates.firstOrNull() ?: preferred
    }
}

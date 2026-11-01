package io.github.ultrapg.voicemobile.data

import io.github.ultrapg.voicemobile.engine.SynthesisMode
import java.io.File
import java.io.RandomAccessFile

enum class ModelSizeTier(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String
) {
    SIZE_06B(
        id = "0.6b",
        label = "Qwen3-TTS 0.6B",
        shortLabel = "0.6B",
        description = "Fast, lightweight mobile talker for Speak & Clone (uses 1.7B for Voice Design)"
    ),
    SIZE_17B(
        id = "1.7b",
        label = "Qwen3-TTS 1.7B",
        shortLabel = "1.7B",
        description = "Full flagship talker across Speak (with emotion instructions), Clone & Voice Design"
    )
}

enum class ModelQuantization(
    val id: String,
    val label: String,
    val shortTag: String
) {
    Q4_K_M("Q4_K_M", "Q4_K_M (4-bit)", "Q4_K_M"),
    Q8_0("Q8_0", "Q8_0 (8-bit)", "Q8_0"),
    BF16("BF16", "BF16 (16-bit)", "BF16"),
    F32("F32", "F32 (32-bit)", "F32")
}

enum class ModelRole(
    val id: String,
    val label: String
) {
    SPEAK("speak", "Speak (CustomVoice)"),
    CLONE("clone", "Clone & Profiles (Base)"),
    DESIGN("style", "Voice Design"),
    CODEC("codec", "12Hz Audio Codec")
}

enum class ModelPreset(
    val id: String,
    val sizeTier: ModelSizeTier,
    val quantization: ModelQuantization,
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
        sizeTier = ModelSizeTier.SIZE_06B,
        quantization = ModelQuantization.Q8_0,
        title = "Qwen3-TTS 0.6B · Q8_0",
        badge = "0.6B · Q8_0",
        summary = "Recommended balanced 8-bit 0.6B Q8_0 for Speak & Clone (~1.2 GB to start), 1.7B Q8_0 for Design",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-Q8_0.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-Q8_0.gguf",
        codecModelFile = "qwen-tokenizer-12hz-Q8_0.gguf"
    ),
    TINY_06B_Q4(
        id = "0.6b-q4",
        sizeTier = ModelSizeTier.SIZE_06B,
        quantization = ModelQuantization.Q4_K_M,
        title = "Qwen3-TTS 0.6B · Q4_K_M",
        badge = "0.6B · Q4_K_M",
        summary = "Smallest footprint (~850 MB to start) with 4-bit 0.6B Q4_K_M quantization",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-Q4_K_M.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-Q4_K_M.gguf",
        codecModelFile = "qwen-tokenizer-12hz-Q8_0.gguf"
    ),
    FAST_06B_BF16(
        id = "0.6b-bf16",
        sizeTier = ModelSizeTier.SIZE_06B,
        quantization = ModelQuantization.BF16,
        title = "Qwen3-TTS 0.6B · BF16",
        badge = "0.6B · BF16",
        summary = "Unquantized BFloat16 0.6B models for Speak & Clone, 1.7B BF16 for Design",
        styleModelFile = "qwen-talker-1.7b-voicedesign-BF16.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-BF16.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-BF16.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FAST_06B_F32(
        id = "0.6b-f32",
        sizeTier = ModelSizeTier.SIZE_06B,
        quantization = ModelQuantization.F32,
        title = "Qwen3-TTS 0.6B · F32",
        badge = "0.6B · F32",
        summary = "Full 32-bit float 0.6B models for Speak & Clone, 1.7B F32 for Design",
        styleModelFile = "qwen-talker-1.7b-voicedesign-F32.gguf",
        cloneModelFile = "qwen-talker-0.6b-base-F32.gguf",
        speakModelFile = "qwen-talker-0.6b-customvoice-F32.gguf",
        codecModelFile = "qwen-tokenizer-12hz-F32.gguf"
    ),
    FULL_17B_Q4(
        id = "q4",
        sizeTier = ModelSizeTier.SIZE_17B,
        quantization = ModelQuantization.Q4_K_M,
        title = "Qwen3-TTS 1.7B · Q4_K_M",
        badge = "1.7B · Q4_K_M",
        summary = "Full 1.7B expressiveness & emotion steering across all modes in compact 4-bit quantization",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q4_K_M.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-Q4_K_M.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-Q4_K_M.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FULL_17B_Q8(
        id = "q8",
        sizeTier = ModelSizeTier.SIZE_17B,
        quantization = ModelQuantization.Q8_0,
        title = "Qwen3-TTS 1.7B · Q8_0",
        badge = "1.7B · Q8_0",
        summary = "High-fidelity 8-bit 1.7B models across Speak, Clone & Voice Design",
        styleModelFile = "qwen-talker-1.7b-voicedesign-Q8_0.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-Q8_0.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-Q8_0.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FULL_17B_BF16(
        id = "bf16",
        sizeTier = ModelSizeTier.SIZE_17B,
        quantization = ModelQuantization.BF16,
        title = "Qwen3-TTS 1.7B · BF16",
        badge = "1.7B · BF16",
        summary = "Uncompressed BFloat16 1.7B reference weights across all modes",
        styleModelFile = "qwen-talker-1.7b-voicedesign-BF16.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-BF16.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-BF16.gguf",
        codecModelFile = "qwen-tokenizer-12hz-BF16.gguf"
    ),
    FULL_17B_F32(
        id = "f32",
        sizeTier = ModelSizeTier.SIZE_17B,
        quantization = ModelQuantization.F32,
        title = "Qwen3-TTS 1.7B · F32",
        badge = "1.7B · F32",
        summary = "Full 32-bit float 1.7B desktop reference weights across all modes",
        styleModelFile = "qwen-talker-1.7b-voicedesign-F32.gguf",
        cloneModelFile = "qwen-talker-1.7b-base-F32.gguf",
        speakModelFile = "qwen-talker-1.7b-customvoice-F32.gguf",
        codecModelFile = "qwen-tokenizer-12hz-F32.gguf"
    );

    fun talkerFileForMode(mode: SynthesisMode): String = when (mode) {
        SynthesisMode.DESIGN -> styleModelFile
        SynthesisMode.CLONE -> cloneModelFile
        SynthesisMode.SPEAK -> speakModelFile
    }

    fun allRequiredFiles(): List<String> = listOf(
        codecModelFile,
        speakModelFile,
        cloneModelFile,
        styleModelFile
    ).distinct()

    companion object {
        fun fromId(id: String): ModelPreset =
            entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) ||
                    (id.equals("tiny", ignoreCase = true) && it == TINY_06B_Q4) ||
                    (id.equals("fast", ignoreCase = true) && it == FAST_06B_Q8)
            } ?: FAST_06B_Q8

        fun forSizeAndQuant(sizeTier: ModelSizeTier, quant: ModelQuantization): ModelPreset =
            entries.firstOrNull { it.sizeTier == sizeTier && it.quantization == quant }
                ?: if (sizeTier == ModelSizeTier.SIZE_17B) FULL_17B_Q4 else FAST_06B_Q8
    }
}

data class ModelFileSpec(
    val fileName: String,
    val displayTitle: String,
    val role: ModelRole,
    val roleLabel: String,
    val sizeTier: ModelSizeTier?,
    val quantization: ModelQuantization?,
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

    /**
     * Complete catalog of all 24 official Qwen3-TTS GGUF models in Serveurperso/Qwen3-TTS-GGUF.
     */
    val ALL_MODELS: List<ModelFileSpec> = listOf(
        // 1. 0.6B CustomVoice (Speak)
        ModelFileSpec("qwen-talker-0.6b-customvoice-Q4_K_M.gguf", "Qwen3-TTS 0.6B CustomVoice (Q4_K_M)", ModelRole.SPEAK, "0.6B Built-In Speakers", ModelSizeTier.SIZE_06B, ModelQuantization.Q4_K_M, 577),
        ModelFileSpec("qwen-talker-0.6b-customvoice-Q8_0.gguf", "Qwen3-TTS 0.6B CustomVoice (Q8_0)", ModelRole.SPEAK, "0.6B Built-In Speakers", ModelSizeTier.SIZE_06B, ModelQuantization.Q8_0, 924),
        ModelFileSpec("qwen-talker-0.6b-customvoice-BF16.gguf", "Qwen3-TTS 0.6B CustomVoice (BF16)", ModelRole.SPEAK, "0.6B Built-In Speakers", ModelSizeTier.SIZE_06B, ModelQuantization.BF16, 1733),
        ModelFileSpec("qwen-talker-0.6b-customvoice-F32.gguf", "Qwen3-TTS 0.6B CustomVoice (F32)", ModelRole.SPEAK, "0.6B Built-In Speakers", ModelSizeTier.SIZE_06B, ModelQuantization.F32, 3461),

        // 2. 0.6B Base (Clone & Voice Profiles)
        ModelFileSpec("qwen-talker-0.6b-base-Q4_K_M.gguf", "Qwen3-TTS 0.6B Base (Q4_K_M)", ModelRole.CLONE, "0.6B Voice Cloning & Profiles", ModelSizeTier.SIZE_06B, ModelQuantization.Q4_K_M, 600),
        ModelFileSpec("qwen-talker-0.6b-base-Q8_0.gguf", "Qwen3-TTS 0.6B Base (Q8_0)", ModelRole.CLONE, "0.6B Voice Cloning & Profiles", ModelSizeTier.SIZE_06B, ModelQuantization.Q8_0, 947),
        ModelFileSpec("qwen-talker-0.6b-base-BF16.gguf", "Qwen3-TTS 0.6B Base (BF16)", ModelRole.CLONE, "0.6B Voice Cloning & Profiles", ModelSizeTier.SIZE_06B, ModelQuantization.BF16, 1756),
        ModelFileSpec("qwen-talker-0.6b-base-F32.gguf", "Qwen3-TTS 0.6B Base (F32)", ModelRole.CLONE, "0.6B Voice Cloning & Profiles", ModelSizeTier.SIZE_06B, ModelQuantization.F32, 3495),

        // 3. 1.7B CustomVoice (Speak + Emotion Instructions)
        ModelFileSpec("qwen-talker-1.7b-customvoice-Q4_K_M.gguf", "Qwen3-TTS 1.7B CustomVoice (Q4_K_M)", ModelRole.SPEAK, "1.7B Speakers + Emotion", ModelSizeTier.SIZE_17B, ModelQuantization.Q4_K_M, 1128),
        ModelFileSpec("qwen-talker-1.7b-customvoice-Q8_0.gguf", "Qwen3-TTS 1.7B CustomVoice (Q8_0)", ModelRole.SPEAK, "1.7B Speakers + Emotion", ModelSizeTier.SIZE_17B, ModelQuantization.Q8_0, 1948),
        ModelFileSpec("qwen-talker-1.7b-customvoice-BF16.gguf", "Qwen3-TTS 1.7B CustomVoice (BF16)", ModelRole.SPEAK, "1.7B Speakers + Emotion", ModelSizeTier.SIZE_17B, ModelQuantization.BF16, 3662),
        ModelFileSpec("qwen-talker-1.7b-customvoice-F32.gguf", "Qwen3-TTS 1.7B CustomVoice (F32)", ModelRole.SPEAK, "1.7B Speakers + Emotion", ModelSizeTier.SIZE_17B, ModelQuantization.F32, 7317),

        // 4. 1.7B Base (Clone & Voice Profiles)
        ModelFileSpec("qwen-talker-1.7b-base-Q4_K_M.gguf", "Qwen3-TTS 1.7B Base (Q4_K_M)", ModelRole.CLONE, "1.7B Voice Cloning & Profiles", ModelSizeTier.SIZE_17B, ModelQuantization.Q4_K_M, 1163),
        ModelFileSpec("qwen-talker-1.7b-base-Q8_0.gguf", "Qwen3-TTS 1.7B Base (Q8_0)", ModelRole.CLONE, "1.7B Voice Cloning & Profiles", ModelSizeTier.SIZE_17B, ModelQuantization.Q8_0, 1983),
        ModelFileSpec("qwen-talker-1.7b-base-BF16.gguf", "Qwen3-TTS 1.7B Base (BF16)", ModelRole.CLONE, "1.7B Voice Cloning & Profiles", ModelSizeTier.SIZE_17B, ModelQuantization.BF16, 3697),
        ModelFileSpec("qwen-talker-1.7b-base-F32.gguf", "Qwen3-TTS 1.7B Base (F32)", ModelRole.CLONE, "1.7B Voice Cloning & Profiles", ModelSizeTier.SIZE_17B, ModelQuantization.F32, 7363),

        // 5. 1.7B VoiceDesign (Voice Design)
        ModelFileSpec("qwen-talker-1.7b-voicedesign-Q4_K_M.gguf", "Qwen3-TTS 1.7B VoiceDesign (Q4_K_M)", ModelRole.DESIGN, "1.7B Voice Design", ModelSizeTier.SIZE_17B, ModelQuantization.Q4_K_M, 1128),
        ModelFileSpec("qwen-talker-1.7b-voicedesign-Q8_0.gguf", "Qwen3-TTS 1.7B VoiceDesign (Q8_0)", ModelRole.DESIGN, "1.7B Voice Design", ModelSizeTier.SIZE_17B, ModelQuantization.Q8_0, 1948),
        ModelFileSpec("qwen-talker-1.7b-voicedesign-BF16.gguf", "Qwen3-TTS 1.7B VoiceDesign (BF16)", ModelRole.DESIGN, "1.7B Voice Design", ModelSizeTier.SIZE_17B, ModelQuantization.BF16, 3662),
        ModelFileSpec("qwen-talker-1.7b-voicedesign-F32.gguf", "Qwen3-TTS 1.7B VoiceDesign (F32)", ModelRole.DESIGN, "1.7B Voice Design", ModelSizeTier.SIZE_17B, ModelQuantization.F32, 7317),

        // 6. Shared 12Hz Audio Codec / Vocoder
        ModelFileSpec("qwen-tokenizer-12hz-Q4_K_M.gguf", "Qwen3-TTS 12Hz Codec (Q4_K_M)", ModelRole.CODEC, "Shared 12Hz Vocoder & Tokenizer", null, ModelQuantization.Q4_K_M, 243),
        ModelFileSpec("qwen-tokenizer-12hz-Q8_0.gguf", "Qwen3-TTS 12Hz Codec (Q8_0)", ModelRole.CODEC, "Shared 12Hz Vocoder & Tokenizer", null, ModelQuantization.Q8_0, 278),
        ModelFileSpec("qwen-tokenizer-12hz-BF16.gguf", "Qwen3-TTS 12Hz Codec (BF16)", ModelRole.CODEC, "Shared 12Hz Vocoder & Tokenizer", null, ModelQuantization.BF16, 342),
        ModelFileSpec("qwen-tokenizer-12hz-F32.gguf", "Qwen3-TTS 12Hz Codec (F32)", ModelRole.CODEC, "Shared 12Hz Vocoder & Tokenizer", null, ModelQuantization.F32, 617)
    )

    private val KNOWN_SPECS: Map<String, ModelFileSpec> = ALL_MODELS.associateBy { it.fileName }

    fun modelsForMode(mode: SynthesisMode): List<ModelFileSpec> {
        val targetRole = when (mode) {
            SynthesisMode.SPEAK -> ModelRole.SPEAK
            SynthesisMode.CLONE -> ModelRole.CLONE
            SynthesisMode.DESIGN -> ModelRole.DESIGN
        }
        return ALL_MODELS.filter { it.role == targetRole }
    }

    fun codecModels(): List<ModelFileSpec> =
        ALL_MODELS.filter { it.role == ModelRole.CODEC }

    fun inferRoleForFile(fileName: String): ModelRole =
        KNOWN_SPECS[fileName]?.role ?: when {
            fileName.contains("tokenizer", ignoreCase = true) -> ModelRole.CODEC
            fileName.contains("voicedesign", ignoreCase = true) -> ModelRole.DESIGN
            fileName.contains("base", ignoreCase = true) -> ModelRole.CLONE
            else -> ModelRole.SPEAK
        }

    fun specFor(fileName: String): ModelFileSpec =
        KNOWN_SPECS[fileName] ?: ModelFileSpec(
            fileName = fileName,
            displayTitle = fileName.removeSuffix(".gguf"),
            role = inferRoleForFile(fileName),
            roleLabel = if (fileName.contains("tokenizer", ignoreCase = true)) "Custom 12Hz Codec" else "Custom Qwen3-TTS GGUF",
            sizeTier = when {
                fileName.contains("0.6b", ignoreCase = true) -> ModelSizeTier.SIZE_06B
                fileName.contains("1.7b", ignoreCase = true) -> ModelSizeTier.SIZE_17B
                else -> null
            },
            quantization = null,
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
     * Resolves [preferredCodecFile] if installed, or falls back to another installed 12Hz codec
     * only when [allowInstalledFallback] is true (i.e. when no explicit custom codec override is set).
     */
    fun resolveInstalledOrPreferredCodec(
        modelDir: File,
        preferredCodecFile: String,
        allowInstalledFallback: Boolean = true
    ): File {
        val exact = File(modelDir, preferredCodecFile)
        if (isValidGgufFile(exact) || !allowInstalledFallback) return exact

        val fallbacks = listOf(
            "qwen-tokenizer-12hz-F32.gguf",
            "qwen-tokenizer-12hz-BF16.gguf",
            "qwen-tokenizer-12hz-Q8_0.gguf",
            "qwen-tokenizer-12hz-Q4_K_M.gguf"
        )
        for (name in fallbacks) {
            val f = File(modelDir, name)
            if (isValidGgufFile(f)) return f
        }
        return exact
    }

    /**
     * Finds the preferred Base (clone/encoder) GGUF in [modelDir] for Voice Profile extraction,
     * falling back to any installed Base GGUF if the preferred one isn't downloaded yet.
     */
    fun resolveBestBaseModelForProfiles(modelDir: File, preferredCloneFile: String): File {
        val preferred = File(modelDir, preferredCloneFile)
        if (isValidGgufFile(preferred)) return preferred

        val candidates = modelDir.listFiles().orEmpty().filter {
            it.extension.equals("gguf", ignoreCase = true) &&
                it.name.contains("base", ignoreCase = true) &&
                isValidGgufFile(it)
        }
        return candidates.firstOrNull() ?: preferred
    }
}

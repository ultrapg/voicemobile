package io.github.ultrapg.voicemobile.engine

enum class SynthesisMode(
    val id: String,
    val label: String,
    val description: String
) {
    DESIGN(
        id = "style",
        label = "Design",
        description = "Design a custom voice from natural-language acoustic traits"
    ),
    CLONE(
        id = "clone",
        label = "Clone",
        description = "Zero-shot or Full-ICL voice cloning from audio, mic, or profile"
    ),
    SPEAK(
        id = "speak",
        label = "Speak",
        description = "Built-in studio speakers with optional emotion steering"
    );

    companion object {
        fun fromId(id: String): SynthesisMode =
            entries.firstOrNull { it.id == id } ?: SPEAK
    }
}

enum class TtsLanguage(
    val code: String,
    val displayName: String,
    val languageId: Int
) {
    AUTO("auto", "Auto Detect", -1),
    ENGLISH("en", "English", 2050),
    GERMAN("de", "German", 2053),
    SPANISH("es", "Spanish", 2054),
    CHINESE("zh", "Chinese", 2055),
    JAPANESE("ja", "Japanese", 2058),
    FRENCH("fr", "French", 2061),
    KOREAN("ko", "Korean", 2064),
    RUSSIAN("ru", "Russian", 2069),
    ITALIAN("it", "Italian", 2070),
    PORTUGUESE("pt", "Portuguese", 2071);

    companion object {
        fun fromCode(code: String): TtsLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

enum class ChunkMode(val id: String, val label: String) {
    AUTO("auto", "Auto (>800 chars)"),
    FORCE_ON("on", "Always Chunk"),
    FORCE_OFF("off", "Single Pass");

    companion object {
        fun fromId(id: String): ChunkMode =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

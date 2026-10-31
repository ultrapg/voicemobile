package io.github.ultrapg.voicemobile.engine

enum class AcceleratorBackend(
    val nativeId: Int,
    val title: String,
    val badge: String,
    val subtitle: String
) {
    AUTO(
        nativeId = 0,
        title = "Auto",
        badge = "Smart Cascade",
        subtitle = "Hexagon HTP NPU → Adreno OpenCL GPU → ARM64 CPU"
    ),
    NPU(
        nativeId = 3,
        title = "Qualcomm NPU",
        badge = "Hexagon HTP",
        subtitle = "Snapdragon Hexagon Tensor Processor (HVX + HMX)"
    ),
    GPU(
        nativeId = 2,
        title = "GPU",
        badge = "Adreno OpenCL",
        subtitle = "Qualcomm Adreno & mobile OpenCL tiled tensor kernels"
    ),
    CPU(
        nativeId = 1,
        title = "CPU",
        badge = "ARM64 SIMD",
        subtitle = "Multi-core ARMv8.2+ NEON, FP16 & DotProd"
    );

    companion object {
        fun fromId(id: Int): AcceleratorBackend =
            entries.firstOrNull { it.nativeId == id } ?: AUTO
    }
}

enum class HexagonPowerMode(
    val envValue: String,
    val label: String,
    val description: String
) {
    SUSTAINED_HIGH(
        envValue = "sustained_high_performance",
        label = "Sustained High",
        description = "Consistent high NPU clocks without thermal throttling"
    ),
    BURST(
        envValue = "burst",
        label = "Burst",
        description = "Peak Hexagon HTP & HMX clock frequency"
    ),
    BALANCED(
        envValue = "balanced",
        label = "Balanced",
        description = "Optimal performance per watt on battery"
    ),
    POWER_SAVER(
        envValue = "power_saver",
        label = "Power Saver",
        description = "Low-power DSP voltage rail for cool operation"
    );

    companion object {
        fun fromEnv(value: String): HexagonPowerMode =
            entries.firstOrNull { it.envValue == value } ?: SUSTAINED_HIGH
    }
}

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
            entries.firstOrNull { it.id == id } ?: DESIGN
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

package io.github.ultrapg.voicemobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ultrapg.voicemobile.engine.ChunkMode
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import io.github.ultrapg.voicemobile.engine.TtsLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voiceDataStore by preferencesDataStore(name = "voicemobile_settings")

/**
 * Mobile equivalent of voicecli's settings.json, persisted reactively via Jetpack DataStore.
 */
data class AppSettings(
    val activeMode: SynthesisMode = SynthesisMode.SPEAK,
    val preset: ModelPreset = ModelPreset.FAST_06B_Q8,
    val customStyleModel: String = "",
    val customCloneModel: String = "",
    val customSpeakModel: String = "",
    val customCodecModel: String = "",
    // ARM64 CPU Runtime
    val cpuThreads: Int = 0, // 0 = Auto optimal threads
    val lowMemMode: Boolean = false,
    // Generation & Post-Processing (matching voicecli CommonSynthArgs + settings.json)
    val language: TtsLanguage = TtsLanguage.AUTO,
    val speed: Float = 1.0f,
    val normalizePeak: Boolean = true,
    val cleanMarkdown: Boolean = true,
    val chunkMode: ChunkMode = ChunkMode.AUTO,
    val chunkSizeChars: Int = 600,
    val streamingMode: Boolean = false,
    val streamChunkSec: Float = 0.40f,
    val autoPlayOutput: Boolean = true,
    val temperature: Float = 0.9f,
    val topP: Float = 1.0f,
    val topK: Int = 50,
    val repetitionPenalty: Float = 1.05f,
    val maxTokens: Int = 4096,
    val selectedSpeaker: String = "vivian"
) {
    fun effectiveTalkerFileName(mode: SynthesisMode): String {
        val override = when (mode) {
            SynthesisMode.DESIGN -> customStyleModel
            SynthesisMode.CLONE -> customCloneModel
            SynthesisMode.SPEAK -> customSpeakModel
        }
        return override.ifBlank { preset.talkerFileForMode(mode) }
    }

    fun effectiveCodecFileName(): String =
        customCodecModel.ifBlank { preset.codecModelFile }

    val hasCustomModelOverrides: Boolean
        get() = customSpeakModel.isNotBlank() ||
            customCloneModel.isNotBlank() ||
            customStyleModel.isNotBlank() ||
            customCodecModel.isNotBlank()

    val supportsSpeakInstruction: Boolean
        get() = !effectiveTalkerFileName(SynthesisMode.SPEAK).contains("0.6b", ignoreCase = true)

    fun activeModelSummaryChip(): String {
        val talkerName = effectiveTalkerFileName(activeMode)
        val spec = ModelsCatalog.specFor(talkerName)
        if (spec.sizeTier != null && spec.quantization != null) {
            return "${spec.sizeTier.shortLabel} · ${spec.quantization.shortTag}"
        }
        return spec.displayTitle.take(24)
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val PRESET = stringPreferencesKey("model_preset")
        val CUSTOM_STYLE = stringPreferencesKey("custom_style_model")
        val CUSTOM_CLONE = stringPreferencesKey("custom_clone_model")
        val CUSTOM_SPEAK = stringPreferencesKey("custom_speak_model")
        val CUSTOM_CODEC = stringPreferencesKey("custom_codec_model")

        val CPU_THREADS = intPreferencesKey("cpu_threads")
        val LOW_MEM = booleanPreferencesKey("low_mem_mode")

        val LANGUAGE = stringPreferencesKey("language_code")
        val SPEED = floatPreferencesKey("speed")
        val NORMALIZE = booleanPreferencesKey("normalize_peak")
        val CLEAN_MD = booleanPreferencesKey("clean_markdown")
        val CHUNK_MODE = stringPreferencesKey("chunk_mode")
        val CHUNK_SIZE = intPreferencesKey("chunk_size_chars")
        val STREAMING = booleanPreferencesKey("streaming_mode")
        val STREAM_CHUNK_SEC = floatPreferencesKey("stream_chunk_sec")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_output")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val REP_PENALTY = floatPreferencesKey("repetition_penalty")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val SELECTED_SPEAKER = stringPreferencesKey("selected_speaker")
    }

    val settingsFlow: Flow<AppSettings> = context.voiceDataStore.data.map { p ->
        AppSettings(
            activeMode = SynthesisMode.fromId(p[Keys.ACTIVE_MODE] ?: SynthesisMode.SPEAK.id),
            preset = ModelPreset.fromId(p[Keys.PRESET] ?: ModelPreset.FAST_06B_Q8.id),
            customStyleModel = p[Keys.CUSTOM_STYLE].orEmpty(),
            customCloneModel = p[Keys.CUSTOM_CLONE].orEmpty(),
            customSpeakModel = p[Keys.CUSTOM_SPEAK].orEmpty(),
            customCodecModel = p[Keys.CUSTOM_CODEC].orEmpty(),
            cpuThreads = (p[Keys.CPU_THREADS] ?: 0).coerceIn(0, 16),
            lowMemMode = p[Keys.LOW_MEM] ?: false,
            language = TtsLanguage.fromCode(p[Keys.LANGUAGE] ?: TtsLanguage.AUTO.code),
            speed = (p[Keys.SPEED] ?: 1.0f).coerceIn(0.25f, 3.0f),
            normalizePeak = p[Keys.NORMALIZE] ?: true,
            cleanMarkdown = p[Keys.CLEAN_MD] ?: true,
            chunkMode = ChunkMode.fromId(p[Keys.CHUNK_MODE] ?: ChunkMode.AUTO.id),
            chunkSizeChars = (p[Keys.CHUNK_SIZE] ?: 600).coerceIn(150, 1800),
            streamingMode = p[Keys.STREAMING] ?: false,
            streamChunkSec = (p[Keys.STREAM_CHUNK_SEC] ?: 0.40f).coerceIn(0.25f, 2.5f),
            autoPlayOutput = p[Keys.AUTO_PLAY] ?: true,
            temperature = (p[Keys.TEMPERATURE] ?: 0.9f).coerceIn(0.0f, 1.5f),
            topP = (p[Keys.TOP_P] ?: 1.0f).coerceIn(0.1f, 1.0f),
            topK = (p[Keys.TOP_K] ?: 50).coerceIn(0, 100),
            repetitionPenalty = (p[Keys.REP_PENALTY] ?: 1.05f).coerceIn(1.0f, 1.5f),
            maxTokens = (p[Keys.MAX_TOKENS] ?: 4096).coerceIn(256, 8192),
            selectedSpeaker = p[Keys.SELECTED_SPEAKER] ?: "vivian"
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.voiceDataStore.edit { prefs ->
            val current = AppSettings(
                activeMode = SynthesisMode.fromId(prefs[Keys.ACTIVE_MODE] ?: SynthesisMode.SPEAK.id),
                preset = ModelPreset.fromId(prefs[Keys.PRESET] ?: ModelPreset.FAST_06B_Q8.id),
                customStyleModel = prefs[Keys.CUSTOM_STYLE].orEmpty(),
                customCloneModel = prefs[Keys.CUSTOM_CLONE].orEmpty(),
                customSpeakModel = prefs[Keys.CUSTOM_SPEAK].orEmpty(),
                customCodecModel = prefs[Keys.CUSTOM_CODEC].orEmpty(),
                cpuThreads = prefs[Keys.CPU_THREADS] ?: 0,
                lowMemMode = prefs[Keys.LOW_MEM] ?: false,
                language = TtsLanguage.fromCode(prefs[Keys.LANGUAGE] ?: TtsLanguage.AUTO.code),
                speed = prefs[Keys.SPEED] ?: 1.0f,
                normalizePeak = prefs[Keys.NORMALIZE] ?: true,
                cleanMarkdown = prefs[Keys.CLEAN_MD] ?: true,
                chunkMode = ChunkMode.fromId(prefs[Keys.CHUNK_MODE] ?: ChunkMode.AUTO.id),
                chunkSizeChars = prefs[Keys.CHUNK_SIZE] ?: 600,
                streamingMode = prefs[Keys.STREAMING] ?: false,
                streamChunkSec = prefs[Keys.STREAM_CHUNK_SEC] ?: 0.40f,
                autoPlayOutput = prefs[Keys.AUTO_PLAY] ?: true,
                temperature = prefs[Keys.TEMPERATURE] ?: 0.9f,
                topP = prefs[Keys.TOP_P] ?: 1.0f,
                topK = prefs[Keys.TOP_K] ?: 50,
                repetitionPenalty = prefs[Keys.REP_PENALTY] ?: 1.05f,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: 4096,
                selectedSpeaker = prefs[Keys.SELECTED_SPEAKER] ?: "vivian"
            )
            val next = transform(current)
            prefs[Keys.ACTIVE_MODE] = next.activeMode.id
            prefs[Keys.PRESET] = next.preset.id
            prefs[Keys.CUSTOM_STYLE] = next.customStyleModel
            prefs[Keys.CUSTOM_CLONE] = next.customCloneModel
            prefs[Keys.CUSTOM_SPEAK] = next.customSpeakModel
            prefs[Keys.CUSTOM_CODEC] = next.customCodecModel
            prefs[Keys.CPU_THREADS] = next.cpuThreads
            prefs[Keys.LOW_MEM] = next.lowMemMode
            prefs[Keys.LANGUAGE] = next.language.code
            prefs[Keys.SPEED] = next.speed
            prefs[Keys.NORMALIZE] = next.normalizePeak
            prefs[Keys.CLEAN_MD] = next.cleanMarkdown
            prefs[Keys.CHUNK_MODE] = next.chunkMode.id
            prefs[Keys.CHUNK_SIZE] = next.chunkSizeChars
            prefs[Keys.STREAMING] = next.streamingMode
            prefs[Keys.STREAM_CHUNK_SEC] = next.streamChunkSec
            prefs[Keys.AUTO_PLAY] = next.autoPlayOutput
            prefs[Keys.TEMPERATURE] = next.temperature
            prefs[Keys.TOP_P] = next.topP
            prefs[Keys.TOP_K] = next.topK
            prefs[Keys.REP_PENALTY] = next.repetitionPenalty
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.SELECTED_SPEAKER] = next.selectedSpeaker
        }
    }
}

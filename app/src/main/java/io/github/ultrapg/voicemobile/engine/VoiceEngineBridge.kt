package io.github.ultrapg.voicemobile.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

interface NativeSynthCallback {
    fun onProgress(tokensGenerated: Int, maxTokens: Int)
    fun onAudioChunk(pcmSamples: FloatArray, sampleRate: Int): Boolean
}

data class EngineRuntimeConfig(
    val backend: AcceleratorBackend = AcceleratorBackend.AUTO,
    val cpuThreads: Int = 0, // 0 = auto optimal threads
    val powerMode: HexagonPowerMode = HexagonPowerMode.SUSTAINED_HIGH,
    val enableHmx: Boolean = true,
    val lowMemMode: Boolean = false
)

data class SynthesisJobRequest(
    val mode: SynthesisMode,
    val chunks: List<String>,
    val modelDir: String,
    val talkerModelPath: String,
    val tokenizerModelPath: String,
    val runtimeConfig: EngineRuntimeConfig,
    val outputWavPath: String,
    // Mode-specific fields:
    val stylePrompt: String = "",
    val speakerName: String = "vivian",
    val cloneRefWavPath: String? = null,
    val cloneRefTranscript: String = "",
    val cloneProfilePath: String? = null,
    val saveExtractedProfilePath: String? = null,
    // Tuning & DSP:
    val language: TtsLanguage = TtsLanguage.AUTO,
    val speed: Float = 1.0f,
    val normalizePeak: Boolean = true,
    val streaming: Boolean = false,
    val streamChunkSec: Float = 0.40f,
    val temperature: Float = 0.9f,
    val topP: Float = 1.0f,
    val topK: Int = 50,
    val repetitionPenalty: Float = 1.05f,
    val maxTokens: Int = 4096
)

data class SynthesisJobResult(
    val outputWavPath: String,
    val durationSec: Float,
    val totalElapsedMs: Long,
    val ttfaMs: Long,
    val rtf: Float,
    val activeBackend: String,
    val waveformBars: List<Float>,
    val availableSpeakers: List<String>
)

object VoiceEngineBridge {

    private val json = Json { ignoreUnknownKeys = true }
    private val engineMutex = Mutex()

    @Volatile
    private var nativeLoaded = false

    private var activeCtxPtr: Long = 0L
    private var loadedTalkerPath: String = ""
    private var loadedTokenizerPath: String = ""
    private var loadedConfig: EngineRuntimeConfig? = null

    fun ensureInitialized(context: Context, config: EngineRuntimeConfig) {
        if (!nativeLoaded) {
            synchronized(this) {
                if (!nativeLoaded) {
                    System.loadLibrary("ggml-base")
                    System.loadLibrary("ggml-cpu")
                    System.loadLibrary("voicemobile_jni")
                    nativeLoaded = true
                }
            }
        }
        nativeInitRuntime(
            nativeLibDir = context.applicationInfo.nativeLibraryDir,
            powerMode = config.powerMode.envValue,
            enableHmx = config.enableHmx,
            lowMem = config.lowMemMode,
            cpuThreads = config.cpuThreads
        )
    }

    fun getHardwareStatus(): NativeHardwareStatus {
        if (!nativeLoaded) return NativeHardwareStatus()
        val raw = runCatching { nativeGetHardwareStatusJson() }.getOrDefault("")
        if (raw.isBlank()) return NativeHardwareStatus()
        return runCatching { json.decodeFromString<NativeHardwareStatus>(raw) }
            .getOrDefault(NativeHardwareStatus())
    }

    fun cancelActiveSynthesis() {
        if (nativeLoaded) {
            nativeCancelSynthesis()
        }
    }

    suspend fun releaseLoadedModel() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            destroyActiveContextLocked()
        }
    }

    suspend fun querySpeakersForModel(
        context: Context,
        modelDir: String,
        talkerModelPath: String,
        tokenizerModelPath: String,
        config: EngineRuntimeConfig
    ): List<String> = withContext(Dispatchers.IO) {
        ensureInitialized(context, config)
        engineMutex.withLock {
            ensureModelLoadedLocked(modelDir, talkerModelPath, tokenizerModelPath, config)
            nativeGetAvailableSpeakers(activeCtxPtr).map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    suspend fun extractVoiceProfile(
        context: Context,
        modelDir: String,
        baseTalkerModelPath: String,
        tokenizerModelPath: String,
        refWavPath: String,
        refTranscript: String?,
        outputProfilePath: String,
        config: EngineRuntimeConfig
    ): Result<File> = withContext(Dispatchers.IO) {
        ensureInitialized(context, config)
        engineMutex.withLock {
            runCatching {
                // Unload main pipeline first if backend config changed so encoder uses requested accelerator
                if (loadedConfig != null && loadedConfig != config) {
                    destroyActiveContextLocked()
                }
                nativeConfigureBackend(
                    preference = config.backend.nativeId,
                    cpuThreads = config.cpuThreads,
                    powerMode = config.powerMode.envValue,
                    enableHmx = config.enableHmx,
                    lowMem = config.lowMemMode
                )

                val tempCtx = nativeCreateContext()
                if (tempCtx == 0L) error("Failed to allocate native profile extraction context")
                try {
                    val isFullIcl = !refTranscript.isNullOrBlank() && outputProfilePath.endsWith(".json", ignoreCase = true)
                    if (isFullIcl) {
                        val loaded = nativeLoadIclEncoder(
                            tempCtx,
                            modelDir,
                            baseTalkerModelPath,
                            tokenizerModelPath
                        )
                        if (!loaded) {
                            error(nativeGetLastError(tempCtx).ifBlank { "Failed to load ICL encoder" })
                        }
                        val ok = nativeExtractIclPrompt(
                            tempCtx,
                            refWavPath,
                            refTranscript!!.trim(),
                            outputProfilePath
                        )
                        if (!ok) {
                            error(nativeGetLastError(tempCtx).ifBlank { "Failed to extract Full-ICL voice profile" })
                        }
                    } else {
                        val loaded = nativeLoadSpeakerEncoderOnly(
                            tempCtx,
                            modelDir,
                            baseTalkerModelPath
                        )
                        if (!loaded) {
                            error(nativeGetLastError(tempCtx).ifBlank { "Failed to load ECAPA-TDNN speaker encoder" })
                        }
                        val ok = nativeExtractSpeakerEmbedding(tempCtx, refWavPath, outputProfilePath)
                        if (!ok) {
                            error(nativeGetLastError(tempCtx).ifBlank { "Failed to extract speaker embedding" })
                        }
                    }
                    File(outputProfilePath)
                } finally {
                    nativeDestroyContext(tempCtx)
                }
            }
        }
    }

    suspend fun runSynthesis(
        context: Context,
        request: SynthesisJobRequest,
        onStatus: (String) -> Unit,
        onProgress: (chunkIndex: Int, totalChunks: Int, tokensGenerated: Int, maxTokens: Int) -> Unit,
        onLiveStreamPcm: ((FloatArray, Int) -> Boolean)? = null
    ): Result<SynthesisJobResult> = withContext(Dispatchers.IO) {
        ensureInitialized(context, request.runtimeConfig)
        engineMutex.withLock {
            runCatching {
                require(request.chunks.isNotEmpty()) { "Input text is empty" }
                val tStartAll = System.currentTimeMillis()

                onStatus("Initializing ${request.runtimeConfig.backend.title} engine…")
                ensureModelLoadedLocked(
                    modelDir = request.modelDir,
                    talkerPath = request.talkerModelPath,
                    tokenizerPath = request.tokenizerModelPath,
                    config = request.runtimeConfig
                )

                val availableSpeakers = if (request.mode == SynthesisMode.SPEAK) {
                    nativeGetAvailableSpeakers(activeCtxPtr)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }

                // Optionally save reusable voice profile if requested during Clone from Audio
                var effectiveProfilePath = request.cloneProfilePath
                if (request.mode == SynthesisMode.CLONE &&
                    effectiveProfilePath.isNullOrBlank() &&
                    !request.cloneRefWavPath.isNullOrBlank() &&
                    !request.saveExtractedProfilePath.isNullOrBlank()
                ) {
                    val outProf = request.saveExtractedProfilePath
                    onStatus("Saving reusable voice profile…")
                    val savedOk = if (request.cloneRefTranscript.isNotBlank() && outProf.endsWith(".json", ignoreCase = true)) {
                        nativeExtractIclPrompt(
                            activeCtxPtr,
                            request.cloneRefWavPath,
                            request.cloneRefTranscript.trim(),
                            outProf
                        )
                    } else {
                        nativeExtractSpeakerEmbedding(
                            activeCtxPtr,
                            request.cloneRefWavPath,
                            outProf
                        )
                    }
                    if (savedOk) {
                        effectiveProfilePath = outProf
                    }
                }

                val (synthModeInt, refPathArg, refTextArg) = when (request.mode) {
                    SynthesisMode.DESIGN -> Triple(0, "", "")
                    SynthesisMode.SPEAK -> Triple(0, "", "")
                    SynthesisMode.CLONE -> {
                        when {
                            !effectiveProfilePath.isNullOrBlank() -> {
                                val modeCode = if (effectiveProfilePath.endsWith(".json", ignoreCase = true)) 3 else 2
                                Triple(modeCode, effectiveProfilePath, "")
                            }
                            !request.cloneRefWavPath.isNullOrBlank() -> {
                                Triple(1, request.cloneRefWavPath, request.cloneRefTranscript.trim())
                            }
                            else -> error("Select or record reference audio (or pick a saved Voice Profile) for cloning")
                        }
                    }
                }

                val speakerArg = if (request.mode == SynthesisMode.SPEAK) {
                    request.speakerName.lowercase().trim()
                } else {
                    ""
                }
                val instructionArg = when (request.mode) {
                    SynthesisMode.DESIGN -> request.stylePrompt.trim()
                    SynthesisMode.SPEAK -> request.stylePrompt.trim()
                    SynthesisMode.CLONE -> ""
                }

                val canStream = request.streaming && onLiveStreamPcm != null
                val rawChunks = ArrayList<FloatArray>(request.chunks.size)
                var sampleRate = 24000
                var firstTtfaMs = -1L

                for ((index, chunkText) in request.chunks.withIndex()) {
                    val chunkNum = index + 1
                    onStatus(
                        if (request.chunks.size > 1) {
                            "Synthesizing chunk $chunkNum / ${request.chunks.size}…"
                        } else if (canStream) {
                            "Streaming live speech…"
                        } else {
                            "Synthesizing speech…"
                        }
                    )

                    if (canStream && index > 0) {
                        val pauseSamples = (sampleRate * 150) / 1000
                        if (pauseSamples > 0) {
                            val keepGoing = onLiveStreamPcm?.invoke(FloatArray(pauseSamples), sampleRate) ?: true
                            if (!keepGoing) {
                                error("Synthesis cancelled")
                            }
                        }
                    }

                    val statsOut = LongArray(4)
                    val cb = object : NativeSynthCallback {
                        override fun onProgress(tokensGenerated: Int, maxTokens: Int) {
                            onProgress(index, request.chunks.size, tokensGenerated, maxTokens)
                        }

                        override fun onAudioChunk(pcmSamples: FloatArray, sampleRate: Int): Boolean {
                            return onLiveStreamPcm?.invoke(pcmSamples, sampleRate) ?: true
                        }
                    }

                    val audioChunk = nativeSynthesizeChunk(
                        ctxPtr = activeCtxPtr,
                        synthMode = synthModeInt,
                        text = chunkText,
                        refPath = refPathArg,
                        refText = refTextArg,
                        speaker = speakerArg,
                        instruction = instructionArg,
                        languageId = request.language.languageId,
                        temperature = request.temperature,
                        topP = request.topP,
                        topK = request.topK,
                        repetitionPenalty = request.repetitionPenalty,
                        maxTokens = request.maxTokens,
                        streaming = canStream,
                        streamChunkSec = request.streamChunkSec,
                        streamSpeed = request.speed,
                        callback = cb,
                        statsOut = statsOut
                    )

                    if (audioChunk == null || audioChunk.isEmpty()) {
                        val err = nativeGetLastError(activeCtxPtr)
                        error(err.ifBlank { "Synthesis failed on chunk $chunkNum" })
                    }

                    if (statsOut[0] > 0L) sampleRate = statsOut[0].toInt()
                    if (firstTtfaMs < 0L && statsOut[2] >= 0L) firstTtfaMs = statsOut[2]
                    rawChunks.add(audioChunk)
                }

                onStatus("Applying audio mastering & writing WAV…")
                File(request.outputWavPath).parentFile?.mkdirs()
                val summary = nativePostProcessAndSaveWav(
                    chunks = rawChunks.toTypedArray(),
                    sampleRate = sampleRate,
                    speed = request.speed,
                    normalize = request.normalizePeak,
                    multiChunkStitch = request.chunks.size > 1,
                    outputWavPath = request.outputWavPath
                ) ?: error("Failed to master and save WAV output")

                val durationSec = summary.firstOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
                val bars = if (summary.size > 1) {
                    summary.drop(1)
                } else {
                    List(64) { 0.3f }
                }

                val elapsedMs = (System.currentTimeMillis() - tStartAll).coerceAtLeast(1L)
                val rtf = (elapsedMs.toFloat() / 1000f) / durationSec
                val activeBackendLabel = nativeGetActiveBackendName().ifBlank {
                    request.runtimeConfig.backend.title
                }

                SynthesisJobResult(
                    outputWavPath = request.outputWavPath,
                    durationSec = durationSec,
                    totalElapsedMs = elapsedMs,
                    ttfaMs = firstTtfaMs,
                    rtf = rtf,
                    activeBackend = activeBackendLabel,
                    waveformBars = bars,
                    availableSpeakers = availableSpeakers
                )
            }
        }
    }

    private fun ensureModelLoadedLocked(
        modelDir: String,
        talkerPath: String,
        tokenizerPath: String,
        config: EngineRuntimeConfig
    ) {
        require(File(talkerPath).exists()) {
            "Talker GGUF model not found. Open the Engine tab to download or import it."
        }
        require(File(tokenizerPath).exists()) {
            "12Hz Audio Codec GGUF not found. Open the Engine tab to download it."
        }

        val sameModel = activeCtxPtr != 0L &&
            loadedTalkerPath == talkerPath &&
            loadedTokenizerPath == tokenizerPath &&
            loadedConfig == config

        if (sameModel) {
            return
        }

        // Destroy existing context first so shared GGML backend refcount drops to 0 before switching backend
        destroyActiveContextLocked()

        nativeConfigureBackend(
            preference = config.backend.nativeId,
            cpuThreads = config.cpuThreads,
            powerMode = config.powerMode.envValue,
            enableHmx = config.enableHmx,
            lowMem = config.lowMemMode
        )

        val ctx = nativeCreateContext()
        if (ctx == 0L) {
            error("Failed to create native Qwen3-TTS context")
        }

        val ok = nativeLoadModel(ctx, modelDir, talkerPath, tokenizerPath)
        if (!ok) {
            val err = nativeGetLastError(ctx)
            nativeDestroyContext(ctx)
            error(err.ifBlank { "Failed to load GGUF models" })
        }

        activeCtxPtr = ctx
        loadedTalkerPath = talkerPath
        loadedTokenizerPath = tokenizerPath
        loadedConfig = config
    }

    private fun destroyActiveContextLocked() {
        if (activeCtxPtr != 0L) {
            nativeDestroyContext(activeCtxPtr)
            activeCtxPtr = 0L
            loadedTalkerPath = ""
            loadedTokenizerPath = ""
            loadedConfig = null
        }
    }

    // --- JNI Bindings ---
    private external fun nativeInitRuntime(
        nativeLibDir: String,
        powerMode: String,
        enableHmx: Boolean,
        lowMem: Boolean,
        cpuThreads: Int
    )

    private external fun nativeGetHardwareStatusJson(): String

    private external fun nativeConfigureBackend(
        preference: Int,
        cpuThreads: Int,
        powerMode: String,
        enableHmx: Boolean,
        lowMem: Boolean
    ): Boolean

    private external fun nativeCreateContext(): Long
    private external fun nativeDestroyContext(ctxPtr: Long)

    private external fun nativeLoadModel(
        ctxPtr: Long,
        modelDir: String,
        talkerPath: String,
        tokenizerPath: String
    ): Boolean

    private external fun nativeLoadSpeakerEncoderOnly(
        ctxPtr: Long,
        modelDir: String,
        talkerPath: String
    ): Boolean

    private external fun nativeLoadIclEncoder(
        ctxPtr: Long,
        modelDir: String,
        talkerPath: String,
        tokenizerPath: String
    ): Boolean

    private external fun nativeGetAvailableSpeakers(ctxPtr: Long): Array<String>
    private external fun nativeGetLastError(ctxPtr: Long): String
    private external fun nativeGetActiveBackendName(): String
    private external fun nativeCancelSynthesis()

    private external fun nativeExtractSpeakerEmbedding(
        ctxPtr: Long,
        refWavPath: String,
        outBinPath: String
    ): Boolean

    private external fun nativeExtractIclPrompt(
        ctxPtr: Long,
        refWavPath: String,
        refText: String,
        outJsonPath: String
    ): Boolean

    private external fun nativeSynthesizeChunk(
        ctxPtr: Long,
        synthMode: Int,
        text: String,
        refPath: String,
        refText: String,
        speaker: String,
        instruction: String,
        languageId: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        maxTokens: Int,
        streaming: Boolean,
        streamChunkSec: Float,
        streamSpeed: Float,
        callback: NativeSynthCallback?,
        statsOut: LongArray
    ): FloatArray?

    private external fun nativePostProcessAndSaveWav(
        chunks: Array<FloatArray>,
        sampleRate: Int,
        speed: Float,
        normalize: Boolean,
        multiChunkStitch: Boolean,
        outputWavPath: String
    ): FloatArray?
}

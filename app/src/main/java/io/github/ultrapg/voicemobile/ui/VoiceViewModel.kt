package io.github.ultrapg.voicemobile.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.voicemobile.audio.AudioConverter
import io.github.ultrapg.voicemobile.audio.AudioPlayerManager
import io.github.ultrapg.voicemobile.audio.MicRecorder
import io.github.ultrapg.voicemobile.audio.MicRecordingState
import io.github.ultrapg.voicemobile.audio.PlaybackState
import io.github.ultrapg.voicemobile.audio.PreparedReferenceAudio
import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.DownloadProgress
import io.github.ultrapg.voicemobile.data.GeneratedClipItem
import io.github.ultrapg.voicemobile.data.ModelDownloadManager
import io.github.ultrapg.voicemobile.data.ModelPreset
import io.github.ultrapg.voicemobile.data.ModelQuantization
import io.github.ultrapg.voicemobile.data.ModelSizeTier
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.data.SettingsRepository
import io.github.ultrapg.voicemobile.data.VoiceProfileItem
import io.github.ultrapg.voicemobile.data.VoiceRepository
import io.github.ultrapg.voicemobile.engine.ChunkMode
import io.github.ultrapg.voicemobile.engine.DeviceHardwareProfile
import io.github.ultrapg.voicemobile.engine.EngineRuntimeConfig
import io.github.ultrapg.voicemobile.engine.HardwareProbe
import io.github.ultrapg.voicemobile.engine.NativeHardwareStatus
import io.github.ultrapg.voicemobile.engine.SynthesisJobRequest
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import io.github.ultrapg.voicemobile.engine.SynthesisService
import io.github.ultrapg.voicemobile.engine.TextProcessor
import io.github.ultrapg.voicemobile.engine.TtsLanguage
import io.github.ultrapg.voicemobile.engine.VoiceEngineBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppTab {
    STUDIO,
    VOICES,
    ENGINE
}

enum class CloneSourceKind {
    AUDIO_OR_MIC,
    SAVED_PROFILE
}

data class ActiveSynthesisState(
    val isRunning: Boolean = false,
    val statusText: String = "",
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val tokensGenerated: Int = 0,
    val maxTokens: Int = 4096
)

data class StudioDraftState(
    val inputText: String = "Welcome to Voice Mobile. Everything runs completely offline on your phone with native ARM64 CPU synthesis.",
    val stylePrompt: String = ModelsCatalog.STYLE_PRESETS.first().second,
    val speakInstruction: String = "",
    val cloneSourceKind: CloneSourceKind = CloneSourceKind.AUDIO_OR_MIC,
    val cloneRefAudio: PreparedReferenceAudio? = null,
    val cloneRefTranscript: String = "",
    val selectedProfileId: String? = null,
    val saveCloneAsProfile: Boolean = false,
    val saveCloneProfileName: String = ""
)

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val settingsRepo = SettingsRepository(appContext)
    val downloadManager = ModelDownloadManager(appContext)
    private val voiceRepo = VoiceRepository(appContext)
    val tempRefDir: File get() = voiceRepo.tempRefDir
    val playerManager = AudioPlayerManager(viewModelScope)
    val micRecorder = MicRecorder(viewModelScope)

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings()
    )

    private val _currentTab = MutableStateFlow(AppTab.STUDIO)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _draft = MutableStateFlow(StudioDraftState())
    val draft: StateFlow<StudioDraftState> = _draft.asStateFlow()

    private val _synthState = MutableStateFlow(ActiveSynthesisState())
    val synthState: StateFlow<ActiveSynthesisState> = _synthState.asStateFlow()

    private val _installedModels = MutableStateFlow<List<File>>(emptyList())
    val installedModels: StateFlow<List<File>> = _installedModels.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _profiles = MutableStateFlow<List<VoiceProfileItem>>(emptyList())
    val profiles: StateFlow<List<VoiceProfileItem>> = _profiles.asStateFlow()

    private val _clips = MutableStateFlow<List<GeneratedClipItem>>(emptyList())
    val clips: StateFlow<List<GeneratedClipItem>> = _clips.asStateFlow()

    private val _availableSpeakers = MutableStateFlow(ModelsCatalog.DEFAULT_SPEAKERS)
    val availableSpeakers: StateFlow<List<String>> = _availableSpeakers.asStateFlow()

    private val _hardwareProfile = MutableStateFlow(
        HardwareProbe.inspect(appContext, NativeHardwareStatus())
    )
    val hardwareProfile: StateFlow<DeviceHardwareProfile> = _hardwareProfile.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isExtractingProfile = MutableStateFlow(false)
    val isExtractingProfile: StateFlow<Boolean> = _isExtractingProfile.asStateFlow()

    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = downloadManager.activeDownloads
    val playbackState: StateFlow<PlaybackState> = playerManager.state
    val micState: StateFlow<MicRecordingState> = micRecorder.state

    private var activeSynthJob: Job? = null
    private var batchDownloadJob: Job? = null
    private val downloadJobs = mutableMapOf<String, Job>()

    val latestClip: StateFlow<GeneratedClipItem?> = combine(_clips, playbackState) { list, pb ->
        list.firstOrNull { it.id == pb.activeClipId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            refreshModelsInventory()
            _profiles.value = voiceRepo.loadProfiles()
            _clips.value = voiceRepo.loadClips()

            val initial = settingsRepo.settingsFlow.first()
            withContext(Dispatchers.IO) {
                runCatching {
                    VoiceEngineBridge.ensureInitialized(appContext, initial.toRuntimeConfig())
                    val nativeSt = VoiceEngineBridge.getHardwareStatus()
                    _hardwareProfile.value = HardwareProbe.inspect(appContext, nativeSt)
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun dismissError() {
        _lastErrorMessage.value = null
    }

    fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    private fun reportError(msg: String) {
        val clean = msg.ifBlank { "Operation failed" }
        _lastErrorMessage.value = clean
        _snackbarMessage.value = clean
    }

    fun receiveSharedText(sharedText: String) {
        if (sharedText.isBlank()) return
        _draft.update { it.copy(inputText = sharedText.trim()) }
        _currentTab.value = AppTab.STUDIO
        postMessage("Imported shared text into Studio")
    }

    // --- Draft Updates ---
    fun updateInputText(text: String) {
        _draft.update { it.copy(inputText = text) }
    }

    fun updateStylePrompt(prompt: String) {
        _draft.update { it.copy(stylePrompt = prompt) }
    }

    fun updateSpeakInstruction(instruction: String) {
        _draft.update { it.copy(speakInstruction = instruction) }
    }

    fun setCloneSourceKind(kind: CloneSourceKind) {
        _draft.update { it.copy(cloneSourceKind = kind) }
    }

    fun updateCloneTranscript(text: String) {
        _draft.update { it.copy(cloneRefTranscript = text) }
    }

    fun selectCloneProfile(profileId: String?) {
        _draft.update { it.copy(selectedProfileId = profileId, cloneSourceKind = CloneSourceKind.SAVED_PROFILE) }
    }

    fun toggleSaveCloneAsProfile(enabled: Boolean) {
        _draft.update { it.copy(saveCloneAsProfile = enabled) }
    }

    fun updateSaveCloneProfileName(name: String) {
        _draft.update { it.copy(saveCloneProfileName = name) }
    }

    fun useProfileInCloneStudio(profile: VoiceProfileItem) {
        _draft.update {
            it.copy(
                cloneSourceKind = CloneSourceKind.SAVED_PROFILE,
                selectedProfileId = profile.id
            )
        }
        setMode(SynthesisMode.CLONE)
        _currentTab.value = AppTab.STUDIO
    }

    // --- Settings Updates ---
    fun setMode(mode: SynthesisMode) {
        viewModelScope.launch { settingsRepo.update { it.copy(activeMode = mode) } }
    }

    fun setSpeaker(speaker: String) {
        viewModelScope.launch { settingsRepo.update { it.copy(selectedSpeaker = speaker) } }
    }

    fun setCpuThreads(threads: Int) {
        viewModelScope.launch { settingsRepo.update { it.copy(cpuThreads = threads.coerceIn(0, 16)) } }
    }

    fun setLowMemMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(lowMemMode = enabled) } }
    }

    fun setModelSizeTier(sizeTier: ModelSizeTier) {
        viewModelScope.launch {
            settingsRepo.update { cur ->
                val nextPreset = ModelPreset.forSizeAndQuant(sizeTier, cur.preset.quantization)
                cur.copy(
                    preset = nextPreset,
                    customStyleModel = "",
                    customCloneModel = "",
                    customSpeakModel = "",
                    customCodecModel = ""
                )
            }
        }
    }

    fun setModelQuantization(quantization: ModelQuantization) {
        viewModelScope.launch {
            settingsRepo.update { cur ->
                val nextPreset = ModelPreset.forSizeAndQuant(cur.preset.sizeTier, quantization)
                cur.copy(
                    preset = nextPreset,
                    customStyleModel = "",
                    customCloneModel = "",
                    customSpeakModel = "",
                    customCodecModel = ""
                )
            }
        }
    }

    fun setPreset(preset: ModelPreset) {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    preset = preset,
                    customStyleModel = "",
                    customCloneModel = "",
                    customSpeakModel = "",
                    customCodecModel = ""
                )
            }
        }
    }

    fun clearCustomModelOverrides() {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    customStyleModel = "",
                    customCloneModel = "",
                    customSpeakModel = "",
                    customCodecModel = ""
                )
            }
            postMessage("Using preset models (${settings.value.preset.badge})")
        }
    }

    fun assignCustomModel(role: String, fileName: String) {
        viewModelScope.launch {
            settingsRepo.update { cur ->
                when (role) {
                    "style" -> cur.copy(customStyleModel = fileName)
                    "clone" -> cur.copy(customCloneModel = fileName)
                    "speak" -> cur.copy(customSpeakModel = fileName)
                    "codec" -> cur.copy(customCodecModel = fileName)
                    else -> cur
                }
            }
            if (fileName.isNotBlank()) {
                val roleName = when (role) {
                    "style" -> "Voice Design"
                    "clone" -> "Clone / Base"
                    "speak" -> "Speak"
                    "codec" -> "12Hz Codec"
                    else -> role
                }
                postMessage("Selected $fileName for $roleName")
            }
        }
    }

    fun updateTuning(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    // --- File & Audio Imports ---
    fun importTextDocument(uri: Uri, asCloneTranscript: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot read selected text file")
            }.onSuccess { content ->
                if (asCloneTranscript) {
                    _draft.update { it.copy(cloneRefTranscript = content.trim()) }
                    postMessage("Imported reference transcript (${content.length} chars)")
                } else {
                    _draft.update { it.copy(inputText = content) }
                    postMessage("Imported text (${content.length} chars)")
                }
            }.onFailure { err ->
                postMessage(err.message ?: "Failed to read text file")
            }
        }
    }

    fun importCloneReferenceAudio(uri: Uri) {
        prepareReferenceAudioUri(uri) { prepared ->
            _draft.update {
                it.copy(
                    cloneRefAudio = prepared,
                    cloneSourceKind = CloneSourceKind.AUDIO_OR_MIC
                )
            }
            postMessage("Prepared ${prepared.displayName} (${String.format("%.1fs", prepared.durationSec)} · 24kHz WAV)")
        }
    }

    fun prepareReferenceAudioUri(uri: Uri, onReady: (PreparedReferenceAudio) -> Unit) {
        viewModelScope.launch {
            AudioConverter.importAndConvertTo24kMonoWav(appContext, uri, voiceRepo.tempRefDir)
                .onSuccess(onReady)
                .onFailure { err ->
                    postMessage(err.message ?: "Failed to decode reference audio")
                }
        }
    }

    fun applyMicRecordingToClone(prepared: PreparedReferenceAudio) {
        _draft.update {
            it.copy(
                cloneRefAudio = prepared,
                cloneSourceKind = CloneSourceKind.AUDIO_OR_MIC
            )
        }
    }

    // --- Models Download & Management ---
    fun refreshModelsInventory() {
        _installedModels.value = downloadManager.listInstalledModels()
        _storageBytes.value = downloadManager.totalStorageBytes()
    }

    fun isModelReadyForActiveMode(appSettings: AppSettings, installed: List<File>): Boolean {
        val names = installed.map { it.name }.toSet()
        val talkerReady = appSettings.effectiveTalkerFileName(appSettings.activeMode) in names
        val codecFile = ModelsCatalog.resolveInstalledOrPreferredCodec(
            downloadManager.modelsDir,
            appSettings.effectiveCodecFileName(),
            allowInstalledFallback = appSettings.customCodecModel.isBlank()
        )
        return talkerReady && codecFile.name in names
    }

    fun missingFilesForActiveMode(appSettings: AppSettings, installed: List<File>): List<String> {
        val names = installed.map { it.name }.toSet()
        val missing = mutableListOf<String>()
        val codecFile = ModelsCatalog.resolveInstalledOrPreferredCodec(
            downloadManager.modelsDir,
            appSettings.effectiveCodecFileName(),
            allowInstalledFallback = appSettings.customCodecModel.isBlank()
        )
        if (codecFile.name !in names) {
            missing += appSettings.effectiveCodecFileName()
        }
        val talkerName = appSettings.effectiveTalkerFileName(appSettings.activeMode)
        if (talkerName !in names) {
            missing += talkerName
        }
        return missing.distinct()
    }

    fun downloadSingleModel(fileName: String) {
        if (downloadJobs[fileName]?.isActive == true) return
        _lastErrorMessage.value = null
        val job = viewModelScope.launch {
            SynthesisService.startOrUpdate(appContext, "Downloading Model", fileName)
            try {
                downloadManager.downloadModel(fileName) { detail ->
                    SynthesisService.startOrUpdate(appContext, "Downloading Qwen3-TTS", detail)
                }.onSuccess {
                    refreshModelsInventory()
                    postMessage("Downloaded $fileName")
                }.onFailure { err ->
                    reportError(err.message ?: "Download failed: $fileName")
                }
            } finally {
                downloadJobs.remove(fileName)
                if (downloadJobs.isEmpty() && batchDownloadJob?.isActive != true && !_synthState.value.isRunning) {
                    SynthesisService.stop(appContext)
                }
            }
        }
        downloadJobs[fileName] = job
    }

    fun downloadFilesSequential(files: List<String>, onAllReady: (() -> Unit)? = null) {
        if (files.isEmpty()) {
            onAllReady?.invoke()
            return
        }
        if (batchDownloadJob?.isActive == true) return
        _lastErrorMessage.value = null
        val job = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            SynthesisService.startOrUpdate(appContext, "Downloading Models", files.first())
            try {
                for (name in files.distinct()) {
                    if (currentJob != null) {
                        downloadJobs[name] = currentJob
                    }
                    val res = try {
                        downloadManager.downloadModel(name) { detail ->
                            SynthesisService.startOrUpdate(appContext, "Downloading Qwen3-TTS", detail)
                        }
                    } finally {
                        downloadJobs.remove(name)
                    }
                    refreshModelsInventory()
                    if (res.isFailure) {
                        reportError(res.exceptionOrNull()?.message ?: "Download failed: $name")
                        return@launch
                    }
                }
                refreshModelsInventory()
                if (onAllReady != null) {
                    onAllReady()
                } else {
                    postMessage("All requested models are ready")
                }
            } finally {
                batchDownloadJob = null
                if (downloadJobs.isEmpty() && !_synthState.value.isRunning) {
                    SynthesisService.stop(appContext)
                }
            }
        }
        batchDownloadJob = job
    }

    fun cancelDownload(fileName: String) {
        val job = downloadJobs.remove(fileName)
        job?.cancel()
        if (batchDownloadJob === job) {
            batchDownloadJob = null
        }
        if (downloadJobs.isEmpty() && batchDownloadJob?.isActive != true && !_synthState.value.isRunning) {
            SynthesisService.stop(appContext)
        }
    }

    fun cancelAllDownloads() {
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        val jobs = downloadJobs.values.toList()
        downloadJobs.clear()
        jobs.forEach { it.cancel() }
        if (!_synthState.value.isRunning) {
            SynthesisService.stop(appContext)
        }
    }

    fun deleteModel(fileName: String) {
        viewModelScope.launch {
            VoiceEngineBridge.releaseLoadedModel()
            downloadManager.deleteModel(fileName)
            refreshModelsInventory()
            postMessage("Removed $fileName")
        }
    }

    fun importLocalGgufUri(uri: Uri) {
        viewModelScope.launch {
            downloadManager.importLocalGguf(uri)
                .onSuccess { file ->
                    refreshModelsInventory()
                    postMessage("Imported ${file.name}")
                }
                .onFailure { err ->
                    postMessage(err.message ?: "Failed to import GGUF file")
                }
        }
    }

    // --- Synthesis Execution ---
    fun synthesizeNow() {
        if (_synthState.value.isRunning) return

        val curSettings = settings.value
        val curDraft = _draft.value
        val chunks = TextProcessor.prepareChunks(
            rawText = curDraft.inputText,
            cleanMarkdownEnabled = curSettings.cleanMarkdown,
            chunkMode = curSettings.chunkMode,
            maxChunkChars = curSettings.chunkSizeChars
        )
        if (chunks.isEmpty()) {
            postMessage("Enter some text to synthesize")
            return
        }

        // Validate mode-specific inputs before starting
        if (curSettings.activeMode == SynthesisMode.DESIGN && curDraft.stylePrompt.isBlank()) {
            postMessage("Describe the target voice style first")
            return
        }
        val selectedProfile = _profiles.value.firstOrNull { it.id == curDraft.selectedProfileId }
        if (curSettings.activeMode == SynthesisMode.CLONE) {
            if (curDraft.cloneSourceKind == CloneSourceKind.SAVED_PROFILE && selectedProfile == null) {
                postMessage("Select a saved Voice Profile or switch to Audio / Mic")
                return
            }
            if (curDraft.cloneSourceKind == CloneSourceKind.AUDIO_OR_MIC && curDraft.cloneRefAudio == null) {
                postMessage("Record or import a 3–10s reference voice sample first")
                return
            }
        }

        val missing = missingFilesForActiveMode(curSettings, _installedModels.value)
        if (missing.isNotEmpty()) {
            // Auto-download missing model files (matching voicecli's seamless auto-fetch!) then run synthesis
            postMessage("Downloading required model (${missing.joinToString(", ")})…")
            downloadFilesSequential(missing) {
                executeLoadedSynthesis(curSettings, curDraft, chunks, selectedProfile)
            }
            return
        }

        executeLoadedSynthesis(curSettings, curDraft, chunks, selectedProfile)
    }

    private fun executeLoadedSynthesis(
        curSettings: AppSettings,
        curDraft: StudioDraftState,
        chunks: List<String>,
        selectedProfile: VoiceProfileItem?
    ) {
        _lastErrorMessage.value = null
        activeSynthJob = viewModelScope.launch {
            val clipId = "clip_${System.currentTimeMillis()}"
            val outWavFile = File(voiceRepo.audioDir, "$clipId.wav")
            val talkerFile = File(downloadManager.modelsDir, curSettings.effectiveTalkerFileName(curSettings.activeMode))
            val codecFile = ModelsCatalog.resolveInstalledOrPreferredCodec(
                downloadManager.modelsDir,
                curSettings.effectiveCodecFileName(),
                allowInstalledFallback = curSettings.customCodecModel.isBlank()
            )

            // Optional inline profile save during Clone
            var inlineSaveProfPath: String? = null
            var inlineProfItem: VoiceProfileItem? = null
            if (curSettings.activeMode == SynthesisMode.CLONE &&
                curDraft.cloneSourceKind == CloneSourceKind.AUDIO_OR_MIC &&
                curDraft.saveCloneAsProfile &&
                curDraft.cloneRefAudio != null
            ) {
                val profId = "prof_${System.currentTimeMillis()}"
                val isFullIcl = curDraft.cloneRefTranscript.isNotBlank()
                val ext = if (isFullIcl) "json" else "bin"
                val profFile = File(voiceRepo.profilesDir, "$profId.$ext")
                inlineSaveProfPath = profFile.absolutePath
                inlineProfItem = VoiceProfileItem(
                    id = profId,
                    name = curDraft.saveCloneProfileName.trim().ifBlank {
                        curDraft.cloneRefAudio.displayName.substringBeforeLast('.')
                    },
                    filePath = profFile.absolutePath,
                    isFullIcl = isFullIcl,
                    referenceTranscript = curDraft.cloneRefTranscript.trim(),
                    createdAtMs = System.currentTimeMillis(),
                    sizeBytes = 0L
                )
            }

            _synthState.value = ActiveSynthesisState(
                isRunning = true,
                statusText = "Preparing ARM64 CPU pipeline…",
                chunkIndex = 0,
                totalChunks = chunks.size,
                tokensGenerated = 0,
                maxTokens = curSettings.maxTokens
            )
            SynthesisService.startOrUpdate(
                appContext,
                "Voice Mobile Synthesizing",
                "Running Qwen3-TTS (${curSettings.activeModelSummaryChip()}) on ARM64 CPU",
                isMedia = curSettings.streamingMode
            )

            val useLiveStream = curSettings.streamingMode
            if (useLiveStream) {
                playerManager.startLiveStream(24000)
            } else {
                playerManager.stopAll()
            }

            try {
                val req = SynthesisJobRequest(
                    mode = curSettings.activeMode,
                    chunks = chunks,
                    modelDir = downloadManager.modelsDir.absolutePath,
                    talkerModelPath = talkerFile.absolutePath,
                    tokenizerModelPath = codecFile.absolutePath,
                    runtimeConfig = curSettings.toRuntimeConfig(),
                    outputWavPath = outWavFile.absolutePath,
                    stylePrompt = when (curSettings.activeMode) {
                        SynthesisMode.DESIGN -> curDraft.stylePrompt
                        SynthesisMode.SPEAK -> if (curSettings.supportsSpeakInstruction) curDraft.speakInstruction else ""
                        SynthesisMode.CLONE -> ""
                    },
                    speakerName = curSettings.selectedSpeaker,
                    cloneRefWavPath = if (curDraft.cloneSourceKind == CloneSourceKind.AUDIO_OR_MIC) {
                        curDraft.cloneRefAudio?.wavFile?.absolutePath
                    } else null,
                    cloneRefTranscript = curDraft.cloneRefTranscript,
                    cloneProfilePath = if (curDraft.cloneSourceKind == CloneSourceKind.SAVED_PROFILE) {
                        selectedProfile?.filePath
                    } else null,
                    saveExtractedProfilePath = inlineSaveProfPath,
                    language = curSettings.language,
                    speed = curSettings.speed,
                    normalizePeak = curSettings.normalizePeak,
                    streaming = useLiveStream,
                    streamChunkSec = curSettings.streamChunkSec,
                    temperature = curSettings.temperature,
                    topP = curSettings.topP,
                    topK = curSettings.topK,
                    repetitionPenalty = curSettings.repetitionPenalty,
                    maxTokens = curSettings.maxTokens
                )

                val result = VoiceEngineBridge.runSynthesis(
                    context = appContext,
                    request = req,
                    onStatus = { status ->
                        _synthState.update { it.copy(statusText = status) }
                        SynthesisService.startOrUpdate(
                            appContext,
                            "Voice Mobile",
                            status,
                            isMedia = useLiveStream
                        )
                    },
                    onProgress = { cIdx, totalC, genTok, maxTok ->
                        _synthState.update {
                            it.copy(
                                chunkIndex = cIdx,
                                totalChunks = totalC,
                                tokensGenerated = genTok,
                                maxTokens = maxTok
                            )
                        }
                    },
                    onLiveStreamPcm = if (useLiveStream) {
                        { pcm, sr -> playerManager.pushLiveStreamChunk(pcm, sr) }
                    } else null
                )

                result.onSuccess { res ->
                    if (useLiveStream) {
                        _synthState.update { it.copy(statusText = "Finishing live stream playback…") }
                        playerManager.completeLiveStreamAndDrain()
                    }

                    if (res.availableSpeakers.isNotEmpty()) {
                        _availableSpeakers.value = res.availableSpeakers
                    }
                    refreshHardwareStatus()

                    // Register inline saved profile if created
                    if (inlineProfItem != null && File(inlineProfItem.filePath).exists()) {
                        _profiles.value = voiceRepo.saveProfileEntry(
                            inlineProfItem.copy(sizeBytes = File(inlineProfItem.filePath).length())
                        )
                        _draft.update { it.copy(saveCloneAsProfile = false, saveCloneProfileName = "") }
                    }

                    val preview = chunks.first().take(90)
                    val modeTag = when (curSettings.activeMode) {
                        SynthesisMode.SPEAK -> "Speak · ${curSettings.selectedSpeaker.replaceFirstChar { it.uppercase() }}"
                        SynthesisMode.CLONE -> "Clone"
                        SynthesisMode.DESIGN -> "Voice Design"
                    }
                    val clip = GeneratedClipItem(
                        id = clipId,
                        title = preview.take(42).let { if (preview.length > 42) "$it…" else it },
                        textPreview = curDraft.inputText.trim().take(240),
                        modeLabel = modeTag,
                        backendUsed = res.activeBackend,
                        filePath = res.outputWavPath,
                        durationSec = res.durationSec,
                        rtf = res.rtf,
                        ttfaMs = res.ttfaMs,
                        createdAtMs = System.currentTimeMillis(),
                        waveformBars = res.waveformBars
                    )
                    _clips.value = voiceRepo.addClip(clip)

                    if (curSettings.autoPlayOutput && !useLiveStream) {
                        playerManager.playClip(clip.id, clip.title, clip.filePath)
                    }
                }.onFailure { err ->
                    if (useLiveStream) {
                        playerManager.abortLiveStream()
                    }
                    outWavFile.delete()
                    val msg = err.message.orEmpty()
                    if (!msg.contains("cancelled", ignoreCase = true)) {
                        reportError(msg.ifBlank { "Synthesis failed" })
                    }
                }
            } catch (_: CancellationException) {
                if (useLiveStream) {
                    playerManager.abortLiveStream()
                }
                outWavFile.delete()
            } finally {
                if (useLiveStream) {
                    playerManager.abortLiveStream()
                }
                _synthState.value = ActiveSynthesisState(isRunning = false)
                if (downloadJobs.isEmpty() && batchDownloadJob?.isActive != true) {
                    SynthesisService.stop(appContext)
                }
            }
        }
    }

    fun cancelSynthesis() {
        VoiceEngineBridge.cancelActiveSynthesis()
        playerManager.abortLiveStream()
        activeSynthJob?.cancel()
    }

    // --- Voice Profiles Lab (voicecli profile subcommand) ---
    fun extractStandaloneVoiceProfile(
        name: String,
        refAudio: PreparedReferenceAudio,
        refTranscript: String,
        fullIcl: Boolean,
        onComplete: () -> Unit
    ) {
        if (_isExtractingProfile.value) return
        viewModelScope.launch {
            val curSettings = settings.value
            val baseModelFile = ModelsCatalog.resolveBestBaseModelForProfiles(
                downloadManager.modelsDir,
                curSettings.effectiveTalkerFileName(SynthesisMode.CLONE)
            )
            val codecFile = ModelsCatalog.resolveInstalledOrPreferredCodec(
                downloadManager.modelsDir,
                curSettings.effectiveCodecFileName(),
                allowInstalledFallback = curSettings.customCodecModel.isBlank()
            )

            val neededDownloads = mutableListOf<String>()
            if (!ModelsCatalog.isValidGgufFile(baseModelFile)) {
                neededDownloads += baseModelFile.name
            }
            if (fullIcl && !ModelsCatalog.isValidGgufFile(codecFile)) {
                neededDownloads += curSettings.effectiveCodecFileName()
            }

            if (neededDownloads.isNotEmpty()) {
                postMessage("Downloading Base encoder model for profile extraction…")
                downloadFilesSequential(neededDownloads) {
                    runProfileExtractionInternal(name, refAudio, refTranscript, fullIcl, onComplete)
                }
            } else {
                runProfileExtractionInternal(name, refAudio, refTranscript, fullIcl, onComplete)
            }
        }
    }

    private fun runProfileExtractionInternal(
        name: String,
        refAudio: PreparedReferenceAudio,
        refTranscript: String,
        fullIcl: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isExtractingProfile.value = true
            try {
                val curSettings = settings.value
                val baseModelFile = ModelsCatalog.resolveBestBaseModelForProfiles(
                    downloadManager.modelsDir,
                    curSettings.effectiveTalkerFileName(SynthesisMode.CLONE)
                )
                val codecFile = ModelsCatalog.resolveInstalledOrPreferredCodec(
                    downloadManager.modelsDir,
                    curSettings.effectiveCodecFileName(),
                    allowInstalledFallback = curSettings.customCodecModel.isBlank()
                )
                val id = "prof_${System.currentTimeMillis()}"
                val ext = if (fullIcl) "json" else "bin"
                val outFile = File(voiceRepo.profilesDir, "$id.$ext")

                VoiceEngineBridge.extractVoiceProfile(
                    context = appContext,
                    modelDir = downloadManager.modelsDir.absolutePath,
                    baseTalkerModelPath = baseModelFile.absolutePath,
                    tokenizerModelPath = codecFile.absolutePath,
                    refWavPath = refAudio.wavFile.absolutePath,
                    refTranscript = if (fullIcl) refTranscript else null,
                    outputProfilePath = outFile.absolutePath,
                    config = curSettings.toRuntimeConfig()
                ).onSuccess { savedFile ->
                    val item = VoiceProfileItem(
                        id = id,
                        name = name.trim().ifBlank { "Voice Profile" },
                        filePath = savedFile.absolutePath,
                        isFullIcl = fullIcl,
                        referenceTranscript = if (fullIcl) refTranscript.trim() else "",
                        createdAtMs = System.currentTimeMillis(),
                        sizeBytes = savedFile.length()
                    )
                    _profiles.value = voiceRepo.saveProfileEntry(item)
                    postMessage("Extracted ${if (fullIcl) "Full-ICL (.json)" else "Speaker Embedding (.bin)"} profile")
                    onComplete()
                }.onFailure { err ->
                    reportError(err.message ?: "Failed to extract voice profile")
                }
            } finally {
                _isExtractingProfile.value = false
            }
        }
    }

    fun importVoiceProfile(uri: Uri) {
        viewModelScope.launch {
            voiceRepo.importProfileUri(uri)
                .onSuccess { item ->
                    _profiles.value = voiceRepo.loadProfiles()
                    postMessage("Imported profile '${item.name}'")
                }
                .onFailure { err ->
                    postMessage(err.message ?: "Failed to import profile")
                }
        }
    }

    fun renameVoiceProfile(id: String, newName: String) {
        viewModelScope.launch {
            _profiles.value = voiceRepo.renameProfile(id, newName)
        }
    }

    fun deleteVoiceProfile(id: String) {
        viewModelScope.launch {
            _profiles.value = voiceRepo.deleteProfile(id)
            if (_draft.value.selectedProfileId == id) {
                _draft.update { it.copy(selectedProfileId = null) }
            }
        }
    }

    fun deleteClip(id: String) {
        viewModelScope.launch {
            if (playbackState.value.activeClipId == id) {
                playerManager.stopAll()
            }
            _clips.value = voiceRepo.deleteClip(id)
        }
    }

    fun exportFile(sourcePath: String, destUri: Uri, label: String) {
        viewModelScope.launch {
            voiceRepo.exportFileToUri(File(sourcePath), destUri)
                .onSuccess { postMessage("Saved $label") }
                .onFailure { err -> postMessage(err.message ?: "Export failed") }
        }
    }

    fun createShareIntent(filePath: String, mimeType: String, title: String): Intent? = runCatching {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }.getOrNull()

    fun refreshHardwareStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val st = VoiceEngineBridge.getHardwareStatus()
            _hardwareProfile.value = HardwareProbe.inspect(appContext, st)
        }
    }

    override fun onCleared() {
        playerManager.stopAll()
        micRecorder.cancel()
        super.onCleared()
    }

    private fun AppSettings.toRuntimeConfig(): EngineRuntimeConfig = EngineRuntimeConfig(
        cpuThreads = cpuThreads,
        lowMemMode = lowMemMode
    )
}

package io.github.ultrapg.voicemobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ultrapg.voicemobile.ui.AppTab
import io.github.ultrapg.voicemobile.ui.VoiceViewModel
import io.github.ultrapg.voicemobile.ui.screens.EngineScreen
import io.github.ultrapg.voicemobile.ui.screens.SynthesizeScreen
import io.github.ultrapg.voicemobile.ui.screens.VoicesScreen
import io.github.ultrapg.voicemobile.ui.theme.VoiceMobileTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeTextIntent(intent)

        setContent {
            VoiceMobileTheme {
                VoiceMobileRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeTextIntent(intent)
    }

    private fun consumeTextIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                        viewModel.receiveSharedText(shared)
                    }
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let { selected ->
                    viewModel.receiveSharedText(selected)
                }
            }
        }
    }
}

private data class PendingExportRequest(
    val sourcePath: String,
    val label: String,
    val mimeType: String
)

@Composable
private fun VoiceMobileRoot(viewModel: VoiceViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val synthState by viewModel.synthState.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val availableSpeakers by viewModel.availableSpeakers.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val latestClip by viewModel.latestClip.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val micState by viewModel.micState.collectAsStateWithLifecycle()
    val isExtractingProfile by viewModel.isExtractingProfile.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var pendingExport by remember { mutableStateOf<PendingExportRequest?>(null) }

    val saveWavLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { destUri ->
        val req = pendingExport
        pendingExport = null
        if (destUri != null && req != null) {
            viewModel.exportFile(req.sourcePath, destUri, req.label)
        }
    }

    val saveProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        val req = pendingExport
        pendingExport = null
        if (destUri != null && req != null) {
            viewModel.exportFile(req.sourcePath, destUri, req.label)
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Foreground synthesis still executes cleanly even if notifications are declined */ }

    var promptedNotifications by remember { mutableStateOf(false) }

    fun ensureForegroundNotifReady() {
        if (!promptedNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            promptedNotifications = true
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    val missingModels = remember(settings, installedModels) {
        viewModel.missingFilesForActiveMode(settings, installedModels)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.STUDIO,
                    onClick = { viewModel.selectTab(AppTab.STUDIO) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.STUDIO) {
                                Icons.Rounded.AutoAwesome
                            } else {
                                Icons.Outlined.AutoAwesome
                            },
                            contentDescription = "Studio"
                        )
                    },
                    label = { Text("Studio") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.VOICES,
                    onClick = { viewModel.selectTab(AppTab.VOICES) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.VOICES) {
                                Icons.Rounded.GraphicEq
                            } else {
                                Icons.Outlined.GraphicEq
                            },
                            contentDescription = "Voices"
                        )
                    },
                    label = { Text("Voices") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.ENGINE,
                    onClick = { viewModel.selectTab(AppTab.ENGINE) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppTab.ENGINE) {
                                Icons.Rounded.Memory
                            } else {
                                Icons.Outlined.Memory
                            },
                            contentDescription = "Engine"
                        )
                    },
                    label = { Text("Engine") }
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier.padding(innerPadding),
            label = "main_tab_crossfade"
        ) { tab ->
            when (tab) {
                AppTab.STUDIO -> SynthesizeScreen(
                    settings = settings,
                    draft = draft,
                    synthState = synthState,
                    hardwareProfile = hardwareProfile,
                    availableSpeakers = availableSpeakers,
                    profiles = profiles,
                    missingModels = missingModels,
                    activeDownloads = activeDownloads,
                    latestClip = latestClip,
                    playbackState = playbackState,
                    micRecorder = viewModel.micRecorder,
                    micState = micState,
                    tempRefDir = viewModel.tempRefDir,
                    onSelectMode = viewModel::setMode,
                    onUpdateInputText = viewModel::updateInputText,
                    onImportTextDoc = viewModel::importTextDocument,
                    onSelectSpeaker = viewModel::setSpeaker,
                    onUpdateSpeakInstruction = viewModel::updateSpeakInstruction,
                    onUpdateStylePrompt = viewModel::updateStylePrompt,
                    onSetCloneSourceKind = viewModel::setCloneSourceKind,
                    onImportCloneAudio = viewModel::importCloneReferenceAudio,
                    onApplyMicToClone = viewModel::applyMicRecordingToClone,
                    onUpdateCloneTranscript = viewModel::updateCloneTranscript,
                    onSelectCloneProfile = viewModel::selectCloneProfile,
                    onToggleSaveCloneProfile = viewModel::toggleSaveCloneAsProfile,
                    onUpdateSaveCloneProfileName = viewModel::updateSaveCloneProfileName,
                    onUpdateTuning = viewModel::updateTuning,
                    onPreDownloadMissing = { list ->
                        ensureForegroundNotifReady()
                        viewModel.downloadFilesSequential(list)
                    },
                    onSynthesize = {
                        ensureForegroundNotifReady()
                        viewModel.synthesizeNow()
                    },
                    onCancelSynthesis = viewModel::cancelSynthesis,
                    onTogglePlayClip = { clip ->
                        viewModel.playerManager.playClip(clip.id, clip.title, clip.filePath)
                    },
                    onPreviewRefAudio = { ref ->
                        viewModel.playerManager.playClip(
                            "ref_preview_${ref.wavFile.name}",
                            ref.displayName,
                            ref.wavFile.absolutePath
                        )
                    },
                    onSeekFraction = viewModel.playerManager::seekToFraction,
                    onShareClip = { clip ->
                        viewModel.createShareIntent(clip.filePath, "audio/wav", clip.title)?.let {
                            context.startActivity(it)
                        }
                    },
                    onSaveClip = { clip ->
                        val safeName = clip.title
                            .replace(Regex("[^A-Za-z0-9_-]"), "_")
                            .take(32)
                            .ifBlank { "voicemobile" }
                        pendingExport = PendingExportRequest(clip.filePath, "$safeName.wav", "audio/wav")
                        saveWavLauncher.launch("${safeName}.wav")
                    },
                    onNavigateToEngine = { viewModel.selectTab(AppTab.ENGINE) },
                    onNavigateToVoices = { viewModel.selectTab(AppTab.VOICES) },
                    onError = viewModel::postMessage
                )

                AppTab.VOICES -> VoicesScreen(
                    profiles = profiles,
                    clips = clips,
                    playbackState = playbackState,
                    isExtractingProfile = isExtractingProfile,
                    micRecorder = viewModel.micRecorder,
                    micState = micState,
                    tempRefDir = viewModel.tempRefDir,
                    onDecodeAudioUri = viewModel::prepareReferenceAudioUri,
                    onExtractProfile = { name, ref, transcript, fullIcl, done ->
                        ensureForegroundNotifReady()
                        viewModel.extractStandaloneVoiceProfile(name, ref, transcript, fullIcl, done)
                    },
                    onImportProfileUri = viewModel::importVoiceProfile,
                    onUseProfileInClone = viewModel::useProfileInCloneStudio,
                    onRenameProfile = { prof, newName ->
                        viewModel.renameVoiceProfile(prof.id, newName)
                    },
                    onDeleteProfile = { prof ->
                        viewModel.deleteVoiceProfile(prof.id)
                    },
                    onExportProfile = { prof ->
                        val ext = if (prof.isFullIcl) "json" else "bin"
                        val safeName = prof.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "profile" }
                        pendingExport = PendingExportRequest(prof.filePath, "$safeName.$ext", "application/octet-stream")
                        saveProfileLauncher.launch("$safeName.$ext")
                    },
                    onShareProfile = { prof ->
                        val mime = if (prof.isFullIcl) "application/json" else "application/octet-stream"
                        viewModel.createShareIntent(prof.filePath, mime, prof.name)?.let {
                            context.startActivity(it)
                        }
                    },
                    onTogglePlayClip = { clip ->
                        viewModel.playerManager.playClip(clip.id, clip.title, clip.filePath)
                    },
                    onSeekFraction = viewModel.playerManager::seekToFraction,
                    onSaveClip = { clip ->
                        val safeName = clip.title
                            .replace(Regex("[^A-Za-z0-9_-]"), "_")
                            .take(32)
                            .ifBlank { "voicemobile" }
                        pendingExport = PendingExportRequest(clip.filePath, "$safeName.wav", "audio/wav")
                        saveWavLauncher.launch("$safeName.wav")
                    },
                    onShareClip = { clip ->
                        viewModel.createShareIntent(clip.filePath, "audio/wav", clip.title)?.let {
                            context.startActivity(it)
                        }
                    },
                    onDeleteClip = { clip ->
                        viewModel.deleteClip(clip.id)
                    },
                    onError = viewModel::postMessage
                )

                AppTab.ENGINE -> EngineScreen(
                    settings = settings,
                    hardwareProfile = hardwareProfile,
                    installedModels = installedModels,
                    storageBytes = storageBytes,
                    activeDownloads = activeDownloads,
                    onSelectBackend = viewModel::setBackend,
                    onSelectHexagonPower = viewModel::setHexagonPowerMode,
                    onToggleHmx = viewModel::setEnableHmx,
                    onSetCpuThreads = viewModel::setCpuThreads,
                    onToggleLowMem = viewModel::setLowMemMode,
                    onSelectPreset = viewModel::setPreset,
                    onDownloadFile = { name ->
                        ensureForegroundNotifReady()
                        viewModel.downloadSingleModel(name)
                    },
                    onDownloadBatch = { list ->
                        ensureForegroundNotifReady()
                        viewModel.downloadFilesSequential(list)
                    },
                    onCancelDownload = viewModel::cancelDownload,
                    onDeleteModel = viewModel::deleteModel,
                    onImportLocalGguf = viewModel::importLocalGgufUri,
                    onAssignCustomModel = viewModel::assignCustomModel,
                    onRefreshHardware = viewModel::refreshHardwareStatus
                )
            }
        }
    }
}

package io.github.ultrapg.voicemobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.audio.MicRecorder
import io.github.ultrapg.voicemobile.audio.MicRecordingState
import io.github.ultrapg.voicemobile.audio.PlaybackState
import io.github.ultrapg.voicemobile.audio.PreparedReferenceAudio
import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.DownloadProgress
import io.github.ultrapg.voicemobile.data.GeneratedClipItem
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.data.VoiceProfileItem
import io.github.ultrapg.voicemobile.engine.AcceleratorBackend
import io.github.ultrapg.voicemobile.engine.DeviceHardwareProfile
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import io.github.ultrapg.voicemobile.engine.TextProcessor
import io.github.ultrapg.voicemobile.ui.ActiveSynthesisState
import io.github.ultrapg.voicemobile.ui.CloneSourceKind
import io.github.ultrapg.voicemobile.ui.StudioDraftState
import io.github.ultrapg.voicemobile.ui.components.MicRecordSheet
import io.github.ultrapg.voicemobile.ui.components.TuningBottomSheet
import io.github.ultrapg.voicemobile.ui.components.WaveformPlayerBar
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SynthesizeScreen(
    settings: AppSettings,
    draft: StudioDraftState,
    synthState: ActiveSynthesisState,
    hardwareProfile: DeviceHardwareProfile,
    availableSpeakers: List<String>,
    profiles: List<VoiceProfileItem>,
    missingModels: List<String>,
    activeDownloads: Map<String, DownloadProgress>,
    lastErrorMessage: String?,
    latestClip: GeneratedClipItem?,
    playbackState: PlaybackState,
    micRecorder: MicRecorder,
    micState: MicRecordingState,
    tempRefDir: File,
    onSelectMode: (SynthesisMode) -> Unit,
    onUpdateInputText: (String) -> Unit,
    onImportTextDoc: (Uri, Boolean) -> Unit,
    onSelectSpeaker: (String) -> Unit,
    onUpdateSpeakInstruction: (String) -> Unit,
    onUpdateStylePrompt: (String) -> Unit,
    onSetCloneSourceKind: (CloneSourceKind) -> Unit,
    onImportCloneAudio: (Uri) -> Unit,
    onApplyMicToClone: (PreparedReferenceAudio) -> Unit,
    onUpdateCloneTranscript: (String) -> Unit,
    onSelectCloneProfile: (String?) -> Unit,
    onToggleSaveCloneProfile: (Boolean) -> Unit,
    onUpdateSaveCloneProfileName: (String) -> Unit,
    onUpdateTuning: ((AppSettings) -> AppSettings) -> Unit,
    onPreDownloadMissing: (List<String>) -> Unit,
    onCancelDownloads: () -> Unit,
    onDismissError: () -> Unit,
    onSynthesize: () -> Unit,
    onCancelSynthesis: () -> Unit,
    onTogglePlayClip: (GeneratedClipItem) -> Unit,
    onPreviewRefAudio: (PreparedReferenceAudio) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onShareClip: (GeneratedClipItem) -> Unit,
    onSaveClip: (GeneratedClipItem) -> Unit,
    onNavigateToEngine: () -> Unit,
    onNavigateToVoices: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showTuningSheet by remember { mutableStateOf(false) }
    var showMicSheet by remember { mutableStateOf(false) }

    val textFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportTextDoc(uri, false)
    }

    val transcriptFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportTextDoc(uri, true)
    }

    val cloneAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportCloneAudio(uri)
    }

    val estimatedChunks = remember(
        draft.inputText,
        settings.cleanMarkdown,
        settings.chunkMode,
        settings.chunkSizeChars
    ) {
        TextProcessor.prepareChunks(
            rawText = draft.inputText,
            cleanMarkdownEnabled = settings.cleanMarkdown,
            chunkMode = settings.chunkMode,
            maxChunkChars = settings.chunkSizeChars
        ).size
    }

    val studioModes = listOf(SynthesisMode.SPEAK, SynthesisMode.CLONE, SynthesisMode.DESIGN)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Minimalist Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Voice Mobile",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "On-device Qwen3-TTS Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ComputeStatusPill(
                    backend = settings.backend,
                    hardwareProfile = hardwareProfile,
                    onClick = onNavigateToEngine
                )
                IconButton(onClick = { showTuningSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Tuning & Parameters"
                    )
                }
            }
        }

        // 3-Mode Segmented Control: Speak | Clone | Design
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            studioModes.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = settings.activeMode == mode,
                    onClick = { onSelectMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(idx, studioModes.size),
                    icon = {
                        val icon = when (mode) {
                            SynthesisMode.SPEAK -> Icons.Rounded.RecordVoiceOver
                            SynthesisMode.CLONE -> Icons.Rounded.Person
                            SynthesisMode.DESIGN -> Icons.Rounded.AutoAwesome
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                ) {
                    Text(mode.label)
                }
            }
        }

        // Clean Text Input Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = draft.inputText,
                    onValueChange = onUpdateInputText,
                    placeholder = {
                        Text(
                            "Type, paste, or import Markdown / text to synthesize offline…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    },
                    minLines = 4,
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (draft.inputText.isBlank()) {
                            "0 chars"
                        } else {
                            "${draft.inputText.length} chars · $estimatedChunks ${if (estimatedChunks == 1) "pass" else "chunks"}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { textFilePicker.launch(arrayOf("text/*", "application/octet-stream")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = {
                                val clipText = clipboard.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    onUpdateInputText(clipText)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste", style = MaterialTheme.typography.labelMedium)
                        }
                        if (draft.inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { onUpdateInputText("") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear text",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode-Specific Voice Configuration Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (settings.activeMode) {
                    SynthesisMode.SPEAK -> SpeakModeSection(
                        selectedSpeaker = settings.selectedSpeaker,
                        availableSpeakers = availableSpeakers,
                        speakInstruction = draft.speakInstruction,
                        supportsInstruction = settings.supportsSpeakInstruction,
                        onSelectSpeaker = onSelectSpeaker,
                        onUpdateInstruction = onUpdateSpeakInstruction
                    )

                    SynthesisMode.CLONE -> CloneModeSection(
                        draft = draft,
                        profiles = profiles,
                        playbackState = playbackState,
                        onSetSourceKind = onSetCloneSourceKind,
                        onOpenMic = { showMicSheet = true },
                        onPickAudio = { cloneAudioPicker.launch(arrayOf("audio/*")) },
                        onPickTranscriptFile = { transcriptFilePicker.launch(arrayOf("text/*")) },
                        onPreviewRefAudio = onPreviewRefAudio,
                        onUpdateTranscript = onUpdateCloneTranscript,
                        onSelectProfile = onSelectCloneProfile,
                        onToggleSaveProfile = onToggleSaveCloneProfile,
                        onUpdateSaveProfileName = onUpdateSaveCloneProfileName,
                        onNavigateToVoices = onNavigateToVoices
                    )

                    SynthesisMode.DESIGN -> DesignModeSection(
                        stylePrompt = draft.stylePrompt,
                        onUpdatePrompt = onUpdateStylePrompt
                    )
                }
            }
        }

        // Minimalist Quick Tuning Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { showTuningSheet = true },
                leadingIcon = {
                    Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(settings.language.displayName) }
            )
            AssistChip(
                onClick = { showTuningSheet = true },
                leadingIcon = {
                    Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(String.format(Locale.US, "%.2fx", settings.speed)) }
            )
            FilterChip(
                selected = settings.streamingMode,
                onClick = { onUpdateTuning { it.copy(streamingMode = !it.streamingMode) } },
                leadingIcon = {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Live Stream") }
            )
            AssistChip(
                onClick = onNavigateToEngine,
                label = { Text(settings.preset.badge) }
            )
        }

        // Persistent Dismissible Error Banner (if last synthesis / download failed)
        if (!lastErrorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lastErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissError,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Download / Missing Model Banner (only shown if needed or actively downloading)
        if (activeDownloads.isNotEmpty() || missingModels.isNotEmpty()) {
            ModelReadinessCard(
                missingModels = missingModels,
                activeDownloads = activeDownloads,
                onDownloadNow = { onPreDownloadMissing(missingModels) },
                onCancelDownloads = onCancelDownloads
            )
        }

        // Primary Synthesize Action / Active Progress Card
        if (synthState.isRunning) {
            ActiveSynthesisCard(
                synthState = synthState,
                onCancel = onCancelSynthesis
            )
        } else {
            Button(
                onClick = onSynthesize,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = if (settings.streamingMode) Icons.Rounded.GraphicEq else Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        missingModels.isNotEmpty() -> "Download & Synthesize"
                        settings.streamingMode -> "Stream Voice Live"
                        else -> "Synthesize Audio"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Latest Output Interactive Waveform Player
        WaveformPlayerBar(
            clip = latestClip,
            playbackState = playbackState,
            onTogglePlay = onTogglePlayClip,
            onSeekFraction = onSeekFraction,
            onShare = onShareClip,
            onSaveToDevice = onSaveClip
        )

        Spacer(Modifier.height(12.dp))
    }

    if (showTuningSheet) {
        TuningBottomSheet(
            settings = settings,
            onUpdate = onUpdateTuning,
            onDismiss = { showTuningSheet = false }
        )
    }

    if (showMicSheet) {
        MicRecordSheet(
            micRecorder = micRecorder,
            micState = micState,
            destDir = tempRefDir,
            onRecorded = onApplyMicToClone,
            onError = onError,
            onDismiss = { showMicSheet = false }
        )
    }
}

@Composable
private fun ComputeStatusPill(
    backend: AcceleratorBackend,
    hardwareProfile: DeviceHardwareProfile,
    onClick: () -> Unit
) {
    val activeTag = when (backend) {
        AcceleratorBackend.AUTO -> when {
            hardwareProfile.nativeStatus.npuAvailable -> "Auto · NPU"
            hardwareProfile.nativeStatus.gpuAvailable -> "Auto · GPU"
            else -> "Auto · CPU"
        }
        AcceleratorBackend.NPU -> "Hexagon NPU"
        AcceleratorBackend.GPU -> "OpenCL GPU"
        AcceleratorBackend.CPU -> "ARM64 CPU"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (backend == AcceleratorBackend.NPU || hardwareProfile.nativeStatus.npuAvailable) {
                    Icons.Rounded.Bolt
                } else {
                    Icons.Rounded.Memory
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = activeTag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeakModeSection(
    selectedSpeaker: String,
    availableSpeakers: List<String>,
    speakInstruction: String,
    supportsInstruction: Boolean,
    onSelectSpeaker: (String) -> Unit,
    onUpdateInstruction: (String) -> Unit
) {
    Text(
        text = "Studio Speaker",
        style = MaterialTheme.typography.titleSmall
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        availableSpeakers.forEach { spk ->
            val niceLabel = spk.split('_').joinToString(" ") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
            FilterChip(
                selected = selectedSpeaker.equals(spk, ignoreCase = true),
                onClick = { onSelectSpeaker(spk) },
                label = { Text(niceLabel) }
            )
        }
    }

    OutlinedTextField(
        value = if (supportsInstruction) speakInstruction else "",
        onValueChange = onUpdateInstruction,
        enabled = supportsInstruction,
        label = { Text("Emotion / Delivery Instruction (Optional)") },
        placeholder = { Text("e.g. Speak softly with calm, thoughtful pacing") },
        supportingText = if (!supportsInstruction) {
            { Text("Emotion instructions require a 1.7B preset (0.6B uses pure speaker voice)") }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CloneModeSection(
    draft: StudioDraftState,
    profiles: List<VoiceProfileItem>,
    playbackState: PlaybackState,
    onSetSourceKind: (CloneSourceKind) -> Unit,
    onOpenMic: () -> Unit,
    onPickAudio: () -> Unit,
    onPickTranscriptFile: () -> Unit,
    onPreviewRefAudio: (PreparedReferenceAudio) -> Unit,
    onUpdateTranscript: (String) -> Unit,
    onSelectProfile: (String?) -> Unit,
    onToggleSaveProfile: (Boolean) -> Unit,
    onUpdateSaveProfileName: (String) -> Unit,
    onNavigateToVoices: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Voice Cloning Source", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = draft.cloneSourceKind == CloneSourceKind.AUDIO_OR_MIC,
                onClick = { onSetSourceKind(CloneSourceKind.AUDIO_OR_MIC) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text("Audio / Mic", style = MaterialTheme.typography.labelMedium)
            }
            SegmentedButton(
                selected = draft.cloneSourceKind == CloneSourceKind.SAVED_PROFILE,
                onClick = { onSetSourceKind(CloneSourceKind.SAVED_PROFILE) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text("Profile", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (draft.cloneSourceKind == CloneSourceKind.AUDIO_OR_MIC) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenMic,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Record Voice")
            }
            OutlinedButton(
                onClick = onPickAudio,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Audio File")
            }
        }

        draft.cloneRefAudio?.let { ref ->
            val refClipId = "ref_preview_${ref.wavFile.name}"
            val isPlayingRef = playbackState.activeClipId == refClipId && playbackState.isPlaying
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onPreviewRefAudio(ref) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingRef) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Preview reference audio"
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ref.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fs · 24 kHz mono WAV ready", ref.durationSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft.cloneRefTranscript,
            onValueChange = onUpdateTranscript,
            label = { Text("Reference Transcript (Optional · Full-ICL)") },
            placeholder = { Text("Leave blank for zero-shot x-vector, or enter transcript for Full-ICL…") },
            trailingIcon = {
                IconButton(onClick = onPickTranscriptFile) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = "Import transcript file",
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Save as Voice Profile", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Reuse this voice anytime without re-encoding",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = draft.saveCloneAsProfile,
                onCheckedChange = onToggleSaveProfile
            )
        }

        AnimatedVisibility(visible = draft.saveCloneAsProfile) {
            OutlinedTextField(
                value = draft.saveCloneProfileName,
                onValueChange = onUpdateSaveProfileName,
                label = { Text("New Profile Name") },
                placeholder = { Text("e.g. My Cloned Voice") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        if (profiles.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "No saved profiles yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onNavigateToVoices) {
                        Text("Create or Import")
                    }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profiles.forEach { prof ->
                    val badge = if (prof.isFullIcl) "ICL" else "EMB"
                    FilterChip(
                        selected = draft.selectedProfileId == prof.id,
                        onClick = { onSelectProfile(prof.id) },
                        label = { Text("${prof.name} ($badge)") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignModeSection(
    stylePrompt: String,
    onUpdatePrompt: (String) -> Unit
) {
    Text(
        text = "Acoustic Voice Description",
        style = MaterialTheme.typography.titleSmall
    )
    OutlinedTextField(
        value = stylePrompt,
        onValueChange = onUpdatePrompt,
        placeholder = { Text("Describe gender, timbre, warmth, cadence, and tone…") },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModelsCatalog.STYLE_PRESETS.forEach { (label, prompt) ->
            SuggestionChip(
                onClick = { onUpdatePrompt(prompt) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ModelReadinessCard(
    missingModels: List<String>,
    activeDownloads: Map<String, DownloadProgress>,
    onDownloadNow: () -> Unit,
    onCancelDownloads: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeDownloads.isNotEmpty()) {
                val dl = activeDownloads.values.first()
                val speedMb = dl.speedBytesPerSec.toFloat() / (1024f * 1024f)
                val doneMb = dl.downloadedBytes / (1024L * 1024L)
                val totalMb = (dl.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Downloading ${dl.fileName}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(Locale.US, "%d/%d MB · %.1f MB/s", doneMb, totalMb, speedMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onCancelDownloads,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
                LinearProgressIndicator(
                    progress = { dl.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val totalApproxMb = missingModels.sumOf { ModelsCatalog.specFor(it).approxSizeMb }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model ready to download (~$totalApproxMb MB)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = missingModels.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onDownloadNow,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fetch")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSynthesisCard(
    synthState: ActiveSynthesisState,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = synthState.statusText.ifBlank { "Synthesizing speech…" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val audioSecEst = synthState.tokensGenerated / 12.5f
                    val sub = buildString {
                        if (synthState.totalChunks > 1) {
                            append("Chunk ${synthState.chunkIndex + 1}/${synthState.totalChunks} · ")
                        }
                        if (synthState.tokensGenerated > 0) {
                            append(
                                String.format(
                                    Locale.US,
                                    "%d codec frames (~%.1fs audio)",
                                    synthState.tokensGenerated,
                                    audioSecEst
                                )
                            )
                        } else {
                            append("Running on-device neural graph")
                        }
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }

            if (synthState.totalChunks > 1 && synthState.tokensGenerated > 0) {
                val frac = ((synthState.chunkIndex.toFloat()) / synthState.totalChunks.toFloat())
                    .coerceIn(0.05f, 0.98f)
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

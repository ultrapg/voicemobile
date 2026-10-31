package io.github.ultrapg.voicemobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.audio.MicRecorder
import io.github.ultrapg.voicemobile.audio.MicRecordingState
import io.github.ultrapg.voicemobile.audio.PlaybackState
import io.github.ultrapg.voicemobile.audio.PreparedReferenceAudio
import io.github.ultrapg.voicemobile.data.GeneratedClipItem
import io.github.ultrapg.voicemobile.data.VoiceProfileItem
import io.github.ultrapg.voicemobile.ui.components.ExtractProfileSheet
import io.github.ultrapg.voicemobile.ui.components.WaveformScrubberCanvas
import io.github.ultrapg.voicemobile.ui.components.formatSeconds
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    profiles: List<VoiceProfileItem>,
    clips: List<GeneratedClipItem>,
    playbackState: PlaybackState,
    isExtractingProfile: Boolean,
    micRecorder: MicRecorder,
    micState: MicRecordingState,
    tempRefDir: File,
    onDecodeAudioUri: (Uri, (PreparedReferenceAudio) -> Unit) -> Unit,
    onExtractProfile: (String, PreparedReferenceAudio, String, Boolean, () -> Unit) -> Unit,
    onImportProfileUri: (Uri) -> Unit,
    onUseProfileInClone: (VoiceProfileItem) -> Unit,
    onRenameProfile: (VoiceProfileItem, String) -> Unit,
    onDeleteProfile: (VoiceProfileItem) -> Unit,
    onExportProfile: (VoiceProfileItem) -> Unit,
    onShareProfile: (VoiceProfileItem) -> Unit,
    onTogglePlayClip: (GeneratedClipItem) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSaveClip: (GeneratedClipItem) -> Unit,
    onShareClip: (GeneratedClipItem) -> Unit,
    onDeleteClip: (GeneratedClipItem) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Profiles, 1 = Clips
    var showCreateProfileSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VoiceProfileItem?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val profilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportProfileUri(uri)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Voices & Library",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Reusable Voice Profiles (.json / .bin) & synthesized WAV clips",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("Profiles (${profiles.size})")
                }
                SegmentedButton(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("Audio Clips (${clips.size})")
                }
            }
        }

        if (subTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showCreateProfileSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Profile")
                    }
                    OutlinedButton(
                        onClick = {
                            profilePicker.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .json/.bin")
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    EmptyCard(
                        title = "No Voice Profiles Yet",
                        subtitle = "Create a Full-ICL (.json) or Fast Speaker Embedding (.bin) profile from any 4–10s audio clip, or import an existing profile from desktop voicecli."
                    )
                }
            } else {
                items(profiles, key = { it.id }) { prof ->
                    VoiceProfileCard(
                        profile = prof,
                        onUse = { onUseProfileInClone(prof) },
                        onRename = {
                            renameTarget = prof
                            renameDraft = prof.name
                        },
                        onExport = { onExportProfile(prof) },
                        onShare = { onShareProfile(prof) },
                        onDelete = { onDeleteProfile(prof) }
                    )
                }
            }
        } else {
            if (clips.isEmpty()) {
                item {
                    EmptyCard(
                        title = "No Synthesized Clips Yet",
                        subtitle = "Every WAV clip generated in Studio appears here with full waveform scrubbing, RTF benchmarks, and 1-tap export."
                    )
                }
            } else {
                items(clips, key = { it.id }) { clip ->
                    GeneratedClipCard(
                        clip = clip,
                        playbackState = playbackState,
                        onTogglePlay = { onTogglePlayClip(clip) },
                        onSeekFraction = onSeekFraction,
                        onSave = { onSaveClip(clip) },
                        onShare = { onShareClip(clip) },
                        onDelete = { onDeleteClip(clip) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showCreateProfileSheet) {
        ExtractProfileSheet(
            isExtracting = isExtractingProfile,
            micRecorder = micRecorder,
            micState = micState,
            tempRefDir = tempRefDir,
            onDecodeAudioUri = onDecodeAudioUri,
            onExtract = { name, ref, transcript, fullIcl ->
                onExtractProfile(name, ref, transcript, fullIcl) {
                    showCreateProfileSheet = false
                }
            },
            onError = onError,
            onDismiss = { showCreateProfileSheet = false }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Profile") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameProfile(target, renameDraft)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VoiceProfileCard(
    profile: VoiceProfileItem,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val kb = (profile.sizeBytes / 1024L).coerceAtLeast(1L)
    val badge = if (profile.isFullIcl) "Full-ICL · .json" else "Fast Embed · .bin"

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$badge · $kb KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FilledTonalButton(
                    onClick = onUse,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Use Voice")
                }
            }

            if (profile.referenceTranscript.isNotBlank()) {
                Text(
                    text = "“${profile.referenceTranscript}”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Download, contentDescription = "Save file", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share profile", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete profile",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedClipCard(
    clip: GeneratedClipItem,
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isCurrent = playbackState.activeClipId == clip.id
    val isPlaying = isCurrent && playbackState.isPlaying
    val progress = if (isCurrent) playbackState.progress else 0f

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildList {
                        add(formatSeconds(clip.durationSec))
                        add(clip.modeLabel)
                        if (clip.backendUsed.isNotBlank()) add(clip.backendUsed)
                        if (clip.rtf > 0f) add(String.format(Locale.US, "RTF %.2fx", clip.rtf))
                    }
                    Text(
                        text = meta.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Download, contentDescription = "Save WAV", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share WAV", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete clip",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            WaveformScrubberCanvas(
                bars = clip.waveformBars,
                progress = progress,
                enabled = isCurrent,
                onScrub = { frac ->
                    if (!isCurrent) onTogglePlay()
                    onSeekFraction(frac)
                },
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
            )
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

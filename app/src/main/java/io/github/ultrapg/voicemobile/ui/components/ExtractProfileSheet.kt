package io.github.ultrapg.voicemobile.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.audio.MicRecorder
import io.github.ultrapg.voicemobile.audio.MicRecordingState
import io.github.ultrapg.voicemobile.audio.PreparedReferenceAudio
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractProfileSheet(
    isExtracting: Boolean,
    micRecorder: MicRecorder,
    micState: MicRecordingState,
    tempRefDir: File,
    onDecodeAudioUri: (Uri, (PreparedReferenceAudio) -> Unit) -> Unit,
    onExtract: (name: String, refAudio: PreparedReferenceAudio, transcript: String, fullIcl: Boolean) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var profileName by remember { mutableStateOf("") }
    var fullIcl by remember { mutableStateOf(true) }
    var transcript by remember { mutableStateOf("") }
    var preparedRef by remember { mutableStateOf<PreparedReferenceAudio?>(null) }
    var showMicModal by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onDecodeAudioUri(uri) { prepared ->
                preparedRef = prepared
                if (profileName.isBlank()) {
                    profileName = prepared.displayName.substringBeforeLast('.')
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isExtracting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create Voice Profile",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Extract a reusable voice profile compatible with voicecli (.json Full-ICL or .bin Speaker Embedding).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                label = { Text("Profile Name") },
                placeholder = { Text("e.g. Studio Narrator") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Reference Audio Picker / Mic Recorder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showMicModal = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Record Mic")
                }
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Audio File")
                }
            }

            preparedRef?.let { ref ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${ref.displayName} · ${String.format(Locale.US, "%.1fs", ref.durationSec)} (24 kHz mono)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Profile Format: Full-ICL (.json) vs Fast Embedding (.bin)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Profile Architecture", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = fullIcl,
                        onClick = { fullIcl = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) {
                        Text("Full-ICL (.json)")
                    }
                    SegmentedButton(
                        selected = !fullIcl,
                        onClick = { fullIcl = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) {
                        Text("Fast Embed (.bin)")
                    }
                }
                Text(
                    text = if (fullIcl) {
                        "Stores ECAPA speaker embedding + 12Hz speech codes + transcript for highest acoustic fidelity."
                    } else {
                        "Stores compact ECAPA speaker embedding (~4–8 KB) for ultra-fast zero-transcript cloning."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = fullIcl) {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text("Reference Audio Transcript") },
                    placeholder = { Text("Exact words spoken in the reference audio clip…") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val ref = preparedRef
                    if (ref == null) {
                        onError("Record or import a reference voice sample first")
                        return@Button
                    }
                    if (fullIcl && transcript.isBlank()) {
                        onError("Enter the reference transcript for Full-ICL (.json), or switch to Fast Embed (.bin)")
                        return@Button
                    }
                    onExtract(profileName, ref, transcript, fullIcl)
                },
                enabled = !isExtracting,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Extracting Voice Profile…")
                } else {
                    Text("Extract & Save Profile")
                }
            }
        }
    }

    if (showMicModal) {
        MicRecordSheet(
            micRecorder = micRecorder,
            micState = micState,
            destDir = tempRefDir,
            onRecorded = { rec ->
                preparedRef = rec
                if (profileName.isBlank()) {
                    profileName = "Recorded Voice"
                }
            },
            onError = onError,
            onDismiss = { showMicModal = false }
        )
    }
}

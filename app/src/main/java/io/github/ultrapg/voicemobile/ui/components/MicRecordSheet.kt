package io.github.ultrapg.voicemobile.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ultrapg.voicemobile.audio.MicRecorder
import io.github.ultrapg.voicemobile.audio.MicRecordingState
import io.github.ultrapg.voicemobile.audio.PreparedReferenceAudio
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicRecordSheet(
    micRecorder: MicRecorder,
    micState: MicRecordingState,
    destDir: File,
    onRecorded: (PreparedReferenceAudio) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            if (!micRecorder.startRecording()) {
                onError("Unable to initialize 24 kHz microphone input")
            }
        } else {
            onError("Microphone permission is required to record reference voice samples")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (micState.isRecording) {
                micRecorder.cancel()
            }
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (micState.isRecording) 1f + (micState.level * 0.42f) else 1f,
        label = "mic_pulse"
    )

    ModalBottomSheet(
        onDismissRequest = {
            micRecorder.cancel()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Record Reference Voice",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Speak clearly for 4–10 seconds in a quiet space. Captured natively at 24 kHz mono PCM16.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier.size(128.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .background(
                            color = if (micState.isRecording) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            },
                            shape = CircleShape
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = if (micState.isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (micState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (micState.isRecording) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (micState.isRecording) {
                    String.format(Locale.US, "Recording · %.1fs / 30.0s", micState.elapsedSec)
                } else {
                    "Ready to record"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (micState.isRecording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        micRecorder.cancel()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }

                if (!micState.isRecording) {
                    Button(
                        onClick = {
                            if (hasMicPermission) {
                                if (!micRecorder.startRecording()) {
                                    onError("Unable to open microphone")
                                }
                            } else {
                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                micRecorder.stopAndSaveWav(destDir)
                                    .onSuccess { prepared ->
                                        onRecorded(prepared)
                                        onDismiss()
                                    }
                                    .onFailure { err ->
                                        onError(err.message ?: "Recording failed")
                                    }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Use Sample")
                    }
                }
            }
        }
    }
}

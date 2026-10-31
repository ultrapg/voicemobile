package io.github.ultrapg.voicemobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.audio.PlaybackState
import io.github.ultrapg.voicemobile.data.GeneratedClipItem
import java.util.Locale

@Composable
fun WaveformPlayerBar(
    clip: GeneratedClipItem?,
    playbackState: PlaybackState,
    onTogglePlay: (GeneratedClipItem) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onShare: (GeneratedClipItem) -> Unit,
    onSaveToDevice: (GeneratedClipItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playbackState.isLiveStreaming) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live 24 kHz Streaming Output",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatDurationMs(playbackState.positionMs) + " streamed directly from vocoder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        return
    }

    if (clip == null) return

    val isCurrentClip = playbackState.activeClipId == clip.id
    val isPlaying = isCurrentClip && playbackState.isPlaying
    val progress = if (isCurrentClip) playbackState.progress else 0f
    val posLabel = if (isCurrentClip) {
        formatDurationMs(playbackState.positionMs)
    } else {
        "0:00"
    }
    val totalLabel = formatSeconds(clip.durationSec)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledIconButton(
                    onClick = { onTogglePlay(clip) },
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
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
                    val metaParts = buildList {
                        add(clip.modeLabel)
                        if (clip.backendUsed.isNotBlank()) add(clip.backendUsed)
                        if (clip.rtf > 0f) add(String.format(Locale.US, "RTF %.2fx", clip.rtf))
                        if (clip.ttfaMs > 0L) add("TTFA ${clip.ttfaMs}ms")
                    }
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onSaveToDevice(clip) }) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Save WAV",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onShare(clip) }) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share WAV",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            WaveformScrubberCanvas(
                bars = clip.waveformBars,
                progress = progress,
                enabled = isCurrentClip,
                onScrub = { frac ->
                    if (!isCurrentClip) {
                        onTogglePlay(clip)
                    }
                    onSeekFraction(frac)
                },
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = posLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WaveformScrubberCanvas(
    bars: List<Float>,
    progress: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val displayBars = if (bars.isNotEmpty()) bars else List(64) { idx ->
        0.22f + 0.45f * kotlin.math.abs(kotlin.math.sin(idx * 0.28f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onScrub((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(enabled) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onScrub((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val count = displayBars.size.coerceAtLeast(1)
        val totalWidth = size.width
        val step = totalWidth / count.toFloat()
        val barWidth = (step * 0.58f).coerceAtLeast(2.dp.toPx())
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until count) {
            val amp = displayBars[i].coerceIn(0.08f, 1f)
            val barHeight = (size.height * amp).coerceAtLeast(4.dp.toPx())
            val x = i * step + (step - barWidth) / 2f
            val y = (size.height - barHeight) / 2f
            val frac = (i.toFloat() + 0.5f) / count.toFloat()
            drawRoundRect(
                color = if (frac <= progress) activeColor else inactiveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

fun formatDurationMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatSeconds(sec: Float): String {
    val totalSec = sec.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

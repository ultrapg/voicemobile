package io.github.ultrapg.voicemobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.engine.ChunkMode
import io.github.ultrapg.voicemobile.engine.TtsLanguage
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TuningBottomSheet(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSamplingAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Synthesis & Audio Tuning",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = {
                        onUpdate {
                            it.copy(
                                language = TtsLanguage.AUTO,
                                speed = 1.0f,
                                normalizePeak = true,
                                cleanMarkdown = true,
                                chunkMode = ChunkMode.AUTO,
                                chunkSizeChars = 600,
                                streamingMode = false,
                                streamChunkSec = 1.0f,
                                autoPlayOutput = true,
                                temperature = 0.9f,
                                topP = 1.0f,
                                topK = 50,
                                repetitionPenalty = 1.05f,
                                maxTokens = 4096
                            )
                        }
                    }
                ) {
                    Text("Reset")
                }
            }

            // 1. Language Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Language",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TtsLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdate { it.copy(language = lang) } },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 2. SOLA Pitch-Preserving Speed (0.25x - 3.0x)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Speaking Speed (SOLA)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Pitch-preserving time-stretch DSP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.2fx", settings.speed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.speed,
                    onValueChange = { v ->
                        val snapped = (v * 20f).roundToInt() / 20f
                        onUpdate { it.copy(speed = snapped.coerceIn(0.25f, 3.0f)) }
                    },
                    valueRange = 0.25f..3.0f
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1.0f, 1.15f, 1.25f, 1.5f, 2.0f).forEach { presetSpeed ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.speed - presetSpeed) < 0.02f,
                            onClick = { onUpdate { it.copy(speed = presetSpeed) } },
                            label = { Text("${presetSpeed}x") }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 3. Streaming & Audio Post-Processing Toggles
            SettingToggleRow(
                title = "Live Streaming Vocoder",
                subtitle = "Play 24 kHz audio immediately as codec frames decode",
                checked = settings.streamingMode,
                onCheckedChange = { enabled -> onUpdate { it.copy(streamingMode = enabled) } }
            )

            AnimatedVisibility(visible = settings.streamingMode) {
                LabeledSliderRow(
                    title = "Streaming Chunk Window",
                    valueText = String.format(Locale.US, "%.1fs", settings.streamChunkSec),
                    value = settings.streamChunkSec,
                    range = 0.4f..2.4f,
                    onValueChange = { v ->
                        val rounded = (v * 10f).roundToInt() / 10f
                        onUpdate { it.copy(streamChunkSec = rounded) }
                    }
                )
            }

            SettingToggleRow(
                title = "Normalize Peak (-0.5 dBFS)",
                subtitle = "Two-pass DC offset removal and headroom-safe loudness normalization",
                checked = settings.normalizePeak,
                onCheckedChange = { enabled -> onUpdate { it.copy(normalizePeak = enabled) } }
            )

            SettingToggleRow(
                title = "Clean Markdown & Links",
                subtitle = "Strip code blocks, URLs, HTML tags, and formatting symbols",
                checked = settings.cleanMarkdown,
                onCheckedChange = { enabled -> onUpdate { it.copy(cleanMarkdown = enabled) } }
            )

            SettingToggleRow(
                title = "Auto-Play Finished Audio",
                subtitle = "Start playback automatically when batch synthesis finishes",
                checked = settings.autoPlayOutput,
                onCheckedChange = { enabled -> onUpdate { it.copy(autoPlayOutput = enabled) } }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 4. Long-Form Linguistic Chunking
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Long-Text Chunking", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ChunkMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = settings.chunkMode == mode,
                            onClick = { onUpdate { it.copy(chunkMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(idx, ChunkMode.entries.size)
                        ) {
                            Text(mode.label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                AnimatedVisibility(visible = settings.chunkMode != ChunkMode.FORCE_OFF) {
                    LabeledSliderRow(
                        title = "Target Chunk Length",
                        valueText = "${settings.chunkSizeChars} chars",
                        value = settings.chunkSizeChars.toFloat(),
                        range = 200f..1500f,
                        onValueChange = { v ->
                            val snapped = ((v / 50f).roundToInt() * 50).coerceIn(200, 1500)
                            onUpdate { it.copy(chunkSizeChars = snapped) }
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 5. Collapsible Autoregressive Sampling Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Autoregressive Sampling", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Temperature, Top-P, Top-K, Repetition Penalty, Max Tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showSamplingAdvanced = !showSamplingAdvanced }) {
                    Text(if (showSamplingAdvanced) "Hide" else "Configure")
                }
            }

            AnimatedVisibility(visible = showSamplingAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledSliderRow(
                        title = "Temperature",
                        valueText = String.format(Locale.US, "%.2f", settings.temperature),
                        value = settings.temperature,
                        range = 0.0f..1.4f,
                        onValueChange = { v ->
                            onUpdate { it.copy(temperature = (v * 20f).roundToInt() / 20f) }
                        }
                    )
                    LabeledSliderRow(
                        title = "Top-P (Nucleus)",
                        valueText = String.format(Locale.US, "%.2f", settings.topP),
                        value = settings.topP,
                        range = 0.1f..1.0f,
                        onValueChange = { v ->
                            onUpdate { it.copy(topP = (v * 20f).roundToInt() / 20f) }
                        }
                    )
                    LabeledSliderRow(
                        title = "Top-K (0 = disabled)",
                        valueText = "${settings.topK}",
                        value = settings.topK.toFloat(),
                        range = 0f..100f,
                        onValueChange = { v ->
                            onUpdate { it.copy(topK = v.roundToInt()) }
                        }
                    )
                    LabeledSliderRow(
                        title = "Repetition Penalty",
                        valueText = String.format(Locale.US, "%.2f", settings.repetitionPenalty),
                        value = settings.repetitionPenalty,
                        range = 1.0f..1.35f,
                        onValueChange = { v ->
                            onUpdate { it.copy(repetitionPenalty = (v * 100f).roundToInt() / 100f) }
                        }
                    )
                    LabeledSliderRow(
                        title = "Max Codec Frames",
                        valueText = "${settings.maxTokens}",
                        value = settings.maxTokens.toFloat(),
                        range = 512f..8192f,
                        onValueChange = { v ->
                            val snapped = ((v / 256f).roundToInt() * 256).coerceIn(512, 8192)
                            onUpdate { it.copy(maxTokens = snapped) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

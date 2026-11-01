package io.github.ultrapg.voicemobile.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.ModelFileSpec
import io.github.ultrapg.voicemobile.data.ModelQuantization
import io.github.ultrapg.voicemobile.data.ModelRole
import io.github.ultrapg.voicemobile.data.ModelSizeTier
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelSelectorSheet(
    settings: AppSettings,
    installedModels: List<File>,
    onSelectSizeTier: (ModelSizeTier) -> Unit,
    onSelectQuantization: (ModelQuantization) -> Unit,
    onAssignCustomModel: (role: String, fileName: String) -> Unit,
    onClearModelOverrides: () -> Unit,
    onOpenFullCatalog: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val installedNames = remember(installedModels) { installedModels.map { it.name }.toSet() }

    val activeRole = when (settings.activeMode) {
        SynthesisMode.SPEAK -> ModelRole.SPEAK
        SynthesisMode.CLONE -> ModelRole.CLONE
        SynthesisMode.DESIGN -> ModelRole.DESIGN
    }
    val effectiveTalker = settings.effectiveTalkerFileName(settings.activeMode)
    val effectiveTalkerSpec = remember(effectiveTalker) { ModelsCatalog.specFor(effectiveTalker) }
    val activeSizeTier = effectiveTalkerSpec.sizeTier ?: settings.preset.sizeTier
    val activeQuant = effectiveTalkerSpec.quantization ?: settings.preset.quantization

    val modeCatalogModels = remember(settings.activeMode, installedModels) {
        val official = ModelsCatalog.modelsForMode(settings.activeMode)
        val officialNames = official.map { it.fileName }.toSet()
        val allOfficialNames = ModelsCatalog.ALL_MODELS.map { it.fileName }.toSet()
        val customImported = installedModels
            .filter { it.name !in allOfficialNames && ModelsCatalog.inferRoleForFile(it.name) != ModelRole.CODEC }
            .map { file ->
                ModelsCatalog.specFor(file.name).copy(
                    approxSizeMb = (file.length() / (1024L * 1024L)).toInt().coerceAtLeast(1)
                )
            }
            .filter { it.fileName !in officialNames }
        official + customImported
    }

    val codecModels = remember { ModelsCatalog.codecModels() }
    val effectiveCodec = settings.effectiveCodecFileName()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Qwen3-TTS Models",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Choose 0.6B or 1.7B & quantization for ARM64 CPU",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenFullCatalog()
                    }
                ) {
                    Text("All 24 Models")
                }
            }

            // 1. Model Size Tier: 0.6B vs 1.7B
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Model Parameter Size",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ModelSizeTier.entries.forEachIndexed { idx, tier ->
                        val isSelected = if (settings.activeMode == SynthesisMode.DESIGN && !settings.hasCustomModelOverrides) {
                            settings.preset.sizeTier == tier
                        } else {
                            activeSizeTier == tier
                        }
                        SegmentedButton(
                            selected = isSelected,
                            onClick = { onSelectSizeTier(tier) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ModelSizeTier.entries.size)
                        ) {
                            Text(tier.label)
                        }
                    }
                }
                Text(
                    text = if (settings.activeMode == SynthesisMode.DESIGN) {
                        "Note: Qwen3-TTS Voice Design is exclusively a 1.7B architecture; 0.6B presets use 0.6B for Speak & Clone and 1.7B for Voice Design."
                    } else {
                        settings.preset.sizeTier.description
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. Quantization Precision
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quantization Precision",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.hasCustomModelOverrides) {
                        TextButton(onClick = onClearModelOverrides) {
                            Text("Reset Overrides")
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModelQuantization.entries.forEach { quant ->
                        FilterChip(
                            selected = activeQuant == quant && !settings.hasCustomModelOverrides,
                            onClick = { onSelectQuantization(quant) },
                            label = { Text(quant.label) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 3. Individual Talker GGUFs for Active Mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${activeRole.label} GGUF (${modeCatalogModels.size} available)",
                    style = MaterialTheme.typography.titleSmall
                )
                modeCatalogModels.forEach { spec ->
                    SelectableModelRow(
                        spec = spec,
                        selected = effectiveTalker == spec.fileName,
                        installed = spec.fileName in installedNames,
                        onClick = {
                            onAssignCustomModel(activeRole.id, spec.fileName)
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 4. Shared 12Hz Audio Codec GGUFs
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "12Hz Audio Codec / Vocoder",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    codecModels.forEach { codecSpec ->
                        val shortName = codecSpec.quantization?.shortTag ?: codecSpec.displayTitle
                        val isInstalled = codecSpec.fileName in installedNames
                        FilterChip(
                            selected = effectiveCodec == codecSpec.fileName,
                            onClick = { onAssignCustomModel(ModelRole.CODEC.id, codecSpec.fileName) },
                            leadingIcon = if (isInstalled) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Installed",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            label = { Text("$shortName (${codecSpec.approxSizeMb} MB)") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableModelRow(
    spec: ModelFileSpec,
    selected: Boolean,
    installed: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (selected) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeInfo = if (spec.approxSizeMb > 0) "~${spec.approxSizeMb} MB" else "Custom GGUF"
                val statusInfo = if (installed) "Installed on device" else "Auto-downloads on use"
                Text(
                    text = "$sizeInfo · $statusInfo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (installed) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

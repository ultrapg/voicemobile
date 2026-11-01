package io.github.ultrapg.voicemobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ultrapg.voicemobile.data.AppSettings
import io.github.ultrapg.voicemobile.data.DownloadProgress
import io.github.ultrapg.voicemobile.data.ModelFileSpec
import io.github.ultrapg.voicemobile.data.ModelPreset
import io.github.ultrapg.voicemobile.data.ModelQuantization
import io.github.ultrapg.voicemobile.data.ModelRole
import io.github.ultrapg.voicemobile.data.ModelSizeTier
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.engine.DeviceHardwareProfile
import io.github.ultrapg.voicemobile.engine.SynthesisMode
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private enum class CatalogFilter(val label: String) {
    ACTIVE("Active Set"),
    SIZE_06B("0.6B (8)"),
    SIZE_17B("1.7B (12)"),
    CODEC("12Hz Codec (4)"),
    ALL("All Models")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(
    settings: AppSettings,
    hardwareProfile: DeviceHardwareProfile,
    installedModels: List<File>,
    storageBytes: Long,
    activeDownloads: Map<String, DownloadProgress>,
    onSetCpuThreads: (Int) -> Unit,
    onToggleLowMem: (Boolean) -> Unit,
    onSelectSizeTier: (ModelSizeTier) -> Unit,
    onSelectQuantization: (ModelQuantization) -> Unit,
    onSelectPreset: (ModelPreset) -> Unit,
    onClearModelOverrides: () -> Unit,
    onDownloadFile: (String) -> Unit,
    onDownloadBatch: (List<String>) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onImportLocalGguf: (Uri) -> Unit,
    onAssignCustomModel: (role: String, fileName: String) -> Unit,
    onRefreshHardware: () -> Unit,
    modifier: Modifier = Modifier
) {
    var catalogFilter by remember { mutableStateOf(CatalogFilter.ALL) }

    val ggufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalGguf(uri)
    }

    val installedNames = remember(installedModels) { installedModels.map { it.name }.toSet() }
    val officialNames = remember { ModelsCatalog.ALL_MODELS.map { it.fileName }.toSet() }

    val activeSpeakFile = settings.effectiveTalkerFileName(SynthesisMode.SPEAK)
    val activeCloneFile = settings.effectiveTalkerFileName(SynthesisMode.CLONE)
    val activeDesignFile = settings.effectiveTalkerFileName(SynthesisMode.DESIGN)
    val activeCodecFile = settings.effectiveCodecFileName()

    val activeRoleFiles = remember(activeSpeakFile, activeCloneFile, activeDesignFile, activeCodecFile) {
        listOf(activeCodecFile, activeSpeakFile, activeCloneFile, activeDesignFile).distinct()
    }
    val missingActiveFiles = remember(activeRoleFiles, installedNames) {
        activeRoleFiles.filterNot { it in installedNames }
    }

    val allCatalogAndCustomSpecs = remember(installedModels) {
        val customImported = installedModels
            .filter { it.name !in officialNames }
            .map { file ->
                ModelsCatalog.specFor(file.name).copy(
                    approxSizeMb = (file.length() / (1024L * 1024L)).toInt().coerceAtLeast(1)
                )
            }
        ModelsCatalog.ALL_MODELS + customImported
    }

    val visibleSpecs = remember(catalogFilter, allCatalogAndCustomSpecs, activeRoleFiles) {
        when (catalogFilter) {
            CatalogFilter.ACTIVE -> activeRoleFiles.map { name ->
                allCatalogAndCustomSpecs.firstOrNull { it.fileName == name } ?: ModelsCatalog.specFor(name)
            }
            CatalogFilter.SIZE_06B -> allCatalogAndCustomSpecs.filter { it.sizeTier == ModelSizeTier.SIZE_06B }
            CatalogFilter.SIZE_17B -> allCatalogAndCustomSpecs.filter { it.sizeTier == ModelSizeTier.SIZE_17B }
            CatalogFilter.CODEC -> allCatalogAndCustomSpecs.filter { it.role == ModelRole.CODEC }
            CatalogFilter.ALL -> allCatalogAndCustomSpecs
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Models & CPU",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Qwen3-TTS 0.6B & 1.7B GGUF Hub · ARM64 SIMD CPU",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefreshHardware) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh CPU Status")
            }
        }

        // 1. Qwen3-TTS 0.6B vs 1.7B Selector & Active Preset Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Qwen3-TTS Model Selection", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Pick 0.6B or 1.7B and quantization across all synthesis modes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (settings.hasCustomModelOverrides) {
                        TextButton(onClick = onClearModelOverrides) {
                            Text("Reset")
                        }
                    }
                }

                // 0.6B | 1.7B Segmented Control
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ModelSizeTier.entries.forEachIndexed { idx, tier ->
                        SegmentedButton(
                            selected = settings.preset.sizeTier == tier && !settings.hasCustomModelOverrides,
                            onClick = { onSelectSizeTier(tier) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ModelSizeTier.entries.size)
                        ) {
                            Text(tier.label)
                        }
                    }
                }

                // Quantization Chips: Q4_K_M | Q8_0 | BF16 | F32
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModelQuantization.entries.forEach { quant ->
                        val matchingPreset = ModelPreset.forSizeAndQuant(settings.preset.sizeTier, quant)
                        FilterChip(
                            selected = settings.preset == matchingPreset && !settings.hasCustomModelOverrides,
                            onClick = { onSelectQuantization(quant) },
                            label = { Text(quant.label) }
                        )
                    }
                }

                Text(
                    text = settings.preset.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Active Role Assignments Summary
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ActiveRoleLine("Speak", activeSpeakFile, activeSpeakFile in installedNames)
                        ActiveRoleLine("Clone / Base", activeCloneFile, activeCloneFile in installedNames)
                        ActiveRoleLine("Voice Design", activeDesignFile, activeDesignFile in installedNames)
                        ActiveRoleLine("12Hz Codec", activeCodecFile, activeCodecFile in installedNames)
                    }
                }

                if (missingActiveFiles.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { onDownloadBatch(missingActiveFiles) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download Active Models (${missingActiveFiles.size} missing)")
                    }
                }
            }
        }

        // 2. Complete Qwen3-TTS GGUF Catalog (All 24 Official Models + Custom Imports)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All Qwen3-TTS GGUF Models", style = MaterialTheme.typography.titleMedium)
                        val gbUsed = storageBytes.toFloat() / (1024f * 1024f * 1024f)
                        Text(
                            text = String.format(
                                Locale.US,
                                "%d of %d installed · %.2f GB on device",
                                installedModels.size,
                                allCatalogAndCustomSpecs.size,
                                gbUsed
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { ggufPicker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .gguf")
                    }
                }

                // Category Filter Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CatalogFilter.entries.forEach { filter ->
                        val label = if (filter == CatalogFilter.ALL) {
                            "All (${allCatalogAndCustomSpecs.size})"
                        } else {
                            filter.label
                        }
                        FilterChip(
                            selected = catalogFilter == filter,
                            onClick = { catalogFilter = filter },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                visibleSpecs.forEach { spec ->
                    val isSelectedForRole = when (spec.role) {
                        ModelRole.SPEAK -> activeSpeakFile == spec.fileName
                        ModelRole.CLONE -> activeCloneFile == spec.fileName
                        ModelRole.DESIGN -> activeDesignFile == spec.fileName
                        ModelRole.CODEC -> activeCodecFile == spec.fileName
                    }
                    val isOfficial = spec.fileName in officialNames

                    CatalogModelRow(
                        spec = spec,
                        isInstalled = spec.fileName in installedNames,
                        isSelectedForRole = isSelectedForRole,
                        isOfficialCatalogFile = isOfficial,
                        downloadProgress = activeDownloads[spec.fileName],
                        onSelectForRole = { roleId -> onAssignCustomModel(roleId, spec.fileName) },
                        onDownload = { onDownloadFile(spec.fileName) },
                        onCancel = { onCancelDownload(spec.fileName) },
                        onDelete = { onDeleteModel(spec.fileName) }
                    )
                }
            }
        }

        // 3. ARM64 SIMD CPU Hardware & Runtime Tuning Card
        CpuRuntimeCard(
            settings = settings,
            hardwareProfile = hardwareProfile,
            onSetCpuThreads = onSetCpuThreads,
            onToggleLowMem = onToggleLowMem
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActiveRoleLine(
    roleName: String,
    fileName: String,
    installed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = roleName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (installed) "$fileName ✓" else fileName,
            style = MaterialTheme.typography.labelSmall,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CatalogModelRow(
    spec: ModelFileSpec,
    isInstalled: Boolean,
    isSelectedForRole: Boolean,
    isOfficialCatalogFile: Boolean,
    downloadProgress: DownloadProgress?,
    onSelectForRole: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelectedForRole) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isSelectedForRole) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        } else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = spec.displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSelectedForRole) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val sizeStr = if (spec.approxSizeMb > 0) " · ~${spec.approxSizeMb} MB" else ""
                    Text(
                        text = "${spec.roleLabel}$sizeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isSelectedForRole) {
                        if (isOfficialCatalogFile) {
                            TextButton(
                                onClick = { onSelectForRole(spec.role.id) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Select")
                            }
                        } else {
                            TextButton(
                                onClick = { roleMenuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Use As")
                            }
                            DropdownMenu(
                                expanded = roleMenuExpanded,
                                onDismissRequest = { roleMenuExpanded = false }
                            ) {
                                ModelRole.entries.forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.label) },
                                        onClick = {
                                            roleMenuExpanded = false
                                            onSelectForRole(r.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    when {
                        downloadProgress != null -> {
                            IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel download",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        isInstalled -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Installed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        else -> {
                            AssistChip(
                                onClick = onDownload,
                                leadingIcon = {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text("Get") }
                            )
                        }
                    }
                }
            }

            if (downloadProgress != null) {
                val doneMb = downloadProgress.downloadedBytes / (1024L * 1024L)
                val totalMb = (downloadProgress.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                val speedMb = downloadProgress.speedBytesPerSec.toFloat() / (1024f * 1024f)
                LinearProgressIndicator(
                    progress = { downloadProgress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = String.format(Locale.US, "%d / %d MB (%.1f MB/s)", doneMb, totalMb, speedMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CpuRuntimeCard(
    settings: AppSettings,
    hardwareProfile: DeviceHardwareProfile,
    onSetCpuThreads: (Int) -> Unit,
    onToggleLowMem: (Boolean) -> Unit
) {
    val st = hardwareProfile.nativeStatus
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ARM64 CPU Engine", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = String.format(
                        Locale.US,
                        "%s · %s · %.1f GB RAM · %d CPU cores",
                        hardwareProfile.deviceModel,
                        hardwareProfile.summaryLine,
                        hardwareProfile.totalRamGb,
                        hardwareProfile.cpuCores
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (st.cpuAvailable) "ARM64 SIMD CPU ✓" else "ARM64 CPU",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                hardwareProfile.armIsaFeatures.forEach { isa ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = isa,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (st.activeBackend.isNotBlank()) {
                Text(
                    text = "Active Runtime: ${st.activeBackend}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // CPU Thread Count Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CPU Compute Threads", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (settings.cpuThreads == 0) {
                            "Auto (${st.cpuThreads})"
                        } else {
                            "${settings.cpuThreads} threads"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.cpuThreads.toFloat(),
                    onValueChange = { onSetCpuThreads(it.roundToInt()) },
                    valueRange = 0f..hardwareProfile.cpuCores.coerceIn(4, 16).toFloat()
                )
            }

            // Low-Memory Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Low-Memory Mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (hardwareProfile.recommendLowMem) {
                            "Recommended for this device's RAM — unloads talker weights before vocoding"
                        } else {
                            "Unload talker GGUF before 12Hz audio decoding to minimize peak RAM"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.lowMemMode, onCheckedChange = onToggleLowMem)
            }
        }
    }
}

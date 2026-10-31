package io.github.ultrapg.voicemobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import io.github.ultrapg.voicemobile.data.ModelPreset
import io.github.ultrapg.voicemobile.data.ModelsCatalog
import io.github.ultrapg.voicemobile.engine.AcceleratorBackend
import io.github.ultrapg.voicemobile.engine.DeviceHardwareProfile
import io.github.ultrapg.voicemobile.engine.HexagonPowerMode
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EngineScreen(
    settings: AppSettings,
    hardwareProfile: DeviceHardwareProfile,
    installedModels: List<File>,
    storageBytes: Long,
    activeDownloads: Map<String, DownloadProgress>,
    onSelectBackend: (AcceleratorBackend) -> Unit,
    onSelectHexagonPower: (HexagonPowerMode) -> Unit,
    onToggleHmx: (Boolean) -> Unit,
    onSetCpuThreads: (Int) -> Unit,
    onToggleLowMem: (Boolean) -> Unit,
    onSelectPreset: (ModelPreset) -> Unit,
    onDownloadFile: (String) -> Unit,
    onDownloadBatch: (List<String>) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onImportLocalGguf: (Uri) -> Unit,
    onAssignCustomModel: (role: String, fileName: String) -> Unit,
    onRefreshHardware: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomModels by remember { mutableStateOf(false) }

    val ggufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalGguf(uri)
    }

    val installedNames = remember(installedModels) { installedModels.map { it.name }.toSet() }
    val presetFiles = remember(settings.preset) { settings.preset.allRequiredFiles() }
    val missingPresetFiles = remember(presetFiles, installedNames) {
        presetFiles.filterNot { it in installedNames }
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
                    text = "Compute & Models",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Qualcomm Hexagon NPU, OpenCL GPU & GGUF Hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefreshHardware) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Hardware Status")
            }
        }

        // 1. Live Silicon & Accelerator Telemetry Card
        HardwareOverviewCard(hardwareProfile = hardwareProfile)

        // 2. Compute Backend Selector (Auto | Qualcomm NPU | GPU | CPU)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Hardware Accelerator",
                style = MaterialTheme.typography.titleMedium
            )
            AcceleratorBackend.entries.forEach { candidate ->
                val availabilityNote = when (candidate) {
                    AcceleratorBackend.AUTO -> "Recommended"
                    AcceleratorBackend.NPU -> if (hardwareProfile.nativeStatus.npuAvailable) {
                        hardwareProfile.hexagonBadge ?: "Hexagon Ready"
                    } else if (hardwareProfile.isQualcommSoc) {
                        "FastRPC CDSP"
                    } else {
                        "Auto-fallback"
                    }
                    AcceleratorBackend.GPU -> if (hardwareProfile.nativeStatus.gpuAvailable) {
                        "OpenCL Ready"
                    } else {
                        "Auto-fallback"
                    }
                    AcceleratorBackend.CPU -> "${hardwareProfile.cpuCores} Cores"
                }

                BackendOptionCard(
                    backend = candidate,
                    selected = settings.backend == candidate,
                    statusPill = availabilityNote,
                    onClick = { onSelectBackend(candidate) }
                )
            }
        }

        // 3. Qualcomm Hexagon NPU & Runtime Tuning Card
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
                Text("Accelerator Tuning", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Qualcomm Hexagon HMX", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Enable HTP Tensor Matrix Extensions alongside HVX vectors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.enableHmx, onCheckedChange = onToggleHmx)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hexagon NPU Power Profile (DCVS)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HexagonPowerMode.entries.forEach { pm ->
                            FilterChip(
                                selected = settings.hexagonPowerMode == pm,
                                onClick = { onSelectHexagonPower(pm) },
                                label = { Text(pm.label) }
                            )
                        }
                    }
                    Text(
                        text = settings.hexagonPowerMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // CPU Thread Count
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ARM64 CPU Threads", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (settings.cpuThreads == 0) {
                                "Auto (${hardwareProfile.nativeStatus.cpuThreads})"
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

        // 4. Qwen3-TTS GGUF Model Presets & Hugging Face Manager
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
                    Column {
                        Text("Qwen3-TTS Models", style = MaterialTheme.typography.titleMedium)
                        val gbUsed = storageBytes.toFloat() / (1024f * 1024f * 1024f)
                        Text(
                            text = String.format(Locale.US, "%d installed · %.2f GB used", installedModels.size, gbUsed),
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

                // Preset Selector Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModelPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.preset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.title) }
                        )
                    }
                }

                Text(
                    text = settings.preset.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (missingPresetFiles.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { onDownloadBatch(missingPresetFiles) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download All for Preset (${missingPresetFiles.size} files)")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Required Files for Active Preset
                presetFiles.forEach { fileName ->
                    ModelFileRow(
                        fileName = fileName,
                        isInstalled = fileName in installedNames,
                        downloadProgress = activeDownloads[fileName],
                        onDownload = { onDownloadFile(fileName) },
                        onCancel = { onCancelDownload(fileName) },
                        onDelete = { onDeleteModel(fileName) }
                    )
                }

                // Custom / All Installed GGUF Assignment Section
                if (installedModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Installed & Custom GGUF Overrides",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showCustomModels = !showCustomModels }) {
                            Text(if (showCustomModels) "Hide" else "Manage (${installedModels.size})")
                        }
                    }

                    AnimatedVisibility(visible = showCustomModels) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            installedModels.forEach { file ->
                                InstalledGgufOverrideRow(
                                    file = file,
                                    onAssignRole = { role -> onAssignCustomModel(role, file.name) },
                                    onDelete = { onDeleteModel(file.name) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HardwareOverviewCard(hardwareProfile: DeviceHardwareProfile) {
    val st = hardwareProfile.nativeStatus
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hardwareProfile.summaryLine,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s · %.1f GB RAM · %d CPU cores",
                            hardwareProfile.deviceModel,
                            hardwareProfile.totalRamGb,
                            hardwareProfile.cpuCores
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                hardwareProfile.hexagonBadge?.let { badge ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadgePill("NPU", st.npuAvailable)
                StatusBadgePill("OpenCL GPU", st.gpuAvailable)
                StatusBadgePill("ARM64 CPU", st.cpuAvailable)
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
                    text = if (st.fallbackActive) {
                        "Active Engine: ${st.activeBackend} (safe fallback active)"
                    } else {
                        "Active Engine: ${st.activeBackend}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusBadgePill(name: String, ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (ready) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Text(
            text = if (ready) "$name ✓" else "$name —",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (ready) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun BackendOptionCard(
    backend: AcceleratorBackend,
    selected: Boolean,
    statusPill: String,
    onClick: () -> Unit
) {
    val icon = when (backend) {
        AcceleratorBackend.AUTO -> Icons.Rounded.AutoAwesome
        AcceleratorBackend.NPU -> Icons.Rounded.Bolt
        AcceleratorBackend.GPU -> Icons.Rounded.Speed
        AcceleratorBackend.CPU -> Icons.Rounded.Memory
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = backend.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = backend.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = backend.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusPill,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelFileRow(
    fileName: String,
    isInstalled: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val spec = ModelsCatalog.specFor(fileName)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = spec.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeStr = if (spec.approxSizeMb > 0) " · ~${spec.approxSizeMb} MB" else ""
                Text(
                    text = "${spec.roleLabel}$sizeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                downloadProgress != null -> {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel download",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                isInstalled -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> {
                    AssistChip(
                        onClick = onDownload,
                        leadingIcon = {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Get") }
                    )
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

@Composable
private fun InstalledGgufOverrideRow(
    file: File,
    onAssignRole: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sizeMb = file.length() / (1024L * 1024L)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sizeMb MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { menuExpanded = true }) {
                    Text("Use As")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Speak Model") },
                        onClick = {
                            menuExpanded = false
                            onAssignRole("speak")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Clone / Base Model") },
                        onClick = {
                            menuExpanded = false
                            onAssignRole("clone")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Voice Design Model") },
                        onClick = {
                            menuExpanded = false
                            onAssignRole("style")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("12Hz Audio Codec") },
                        onClick = {
                            menuExpanded = false
                            onAssignRole("codec")
                        }
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

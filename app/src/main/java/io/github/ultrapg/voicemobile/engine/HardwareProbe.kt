package io.github.ultrapg.voicemobile.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class NativeHardwareStatus(
    val cpuAvailable: Boolean = true,
    val gpuAvailable: Boolean = false,
    val npuAvailable: Boolean = false,
    val cpuDesc: String = "",
    val gpuDesc: String = "",
    val npuDesc: String = "",
    val hexagonArch: Int = 0,
    val activeBackend: String = "",
    val fallbackActive: Boolean = false,
    val cpuThreads: Int = 4
)

data class DeviceHardwareProfile(
    val deviceModel: String,
    val socManufacturer: String,
    val socModel: String,
    val snapdragonMarketingName: String?,
    val isQualcommSoc: Boolean,
    val cpuCores: Int,
    val armIsaFeatures: List<String>,
    val totalRamGb: Float,
    val recommendLowMem: Boolean,
    val nativeStatus: NativeHardwareStatus
) {
    val summaryLine: String
        get() = snapdragonMarketingName
            ?: listOf(socManufacturer, socModel)
                .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                .joinToString(" ")
                .ifBlank { deviceModel }

    val hexagonBadge: String?
        get() = when {
            nativeStatus.hexagonArch > 0 -> "Hexagon HTP v${nativeStatus.hexagonArch}"
            nativeStatus.npuAvailable && nativeStatus.npuDesc.isNotBlank() -> nativeStatus.npuDesc
            isQualcommSoc -> "Qualcomm Snapdragon SoC"
            else -> null
        }
}

object HardwareProbe {

    fun inspect(context: Context, nativeStatus: NativeHardwareStatus): DeviceHardwareProfile {
        val socMfg = Build.SOC_MANUFACTURER.orEmpty().trim()
        val socModel = Build.SOC_MODEL.orEmpty().trim()
        val hw = Build.HARDWARE.orEmpty().trim()
        val isQualcomm = socMfg.contains("Qualcomm", ignoreCase = true) ||
            socModel.startsWith("SM", ignoreCase = true) ||
            socModel.startsWith("QCS", ignoreCase = true) ||
            socModel.startsWith("SDM", ignoreCase = true) ||
            hw.contains("qcom", ignoreCase = true)

        val marketing = resolveSnapdragonName(socModel)
        val isa = readArmIsaFeatures()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamGb = if (memInfo.totalMem > 0L) {
            memInfo.totalMem.toFloat() / (1024f * 1024f * 1024f)
        } else {
            8f
        }

        return DeviceHardwareProfile(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim(),
            socManufacturer = socMfg,
            socModel = socModel,
            snapdragonMarketingName = marketing,
            isQualcommSoc = isQualcomm,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            armIsaFeatures = isa,
            totalRamGb = totalRamGb,
            recommendLowMem = totalRamGb <= 6.5f,
            nativeStatus = nativeStatus
        )
    }

    private fun resolveSnapdragonName(socModel: String): String? {
        val code = socModel.uppercase()
        return when {
            code.startsWith("SM8850") -> "Snapdragon 8 Elite Gen 5"
            code.startsWith("SM8750") -> "Snapdragon 8 Elite"
            code.startsWith("SM8735") -> "Snapdragon 8s Gen 4"
            code.startsWith("SM8650") -> "Snapdragon 8 Gen 3"
            code.startsWith("SM8635") -> "Snapdragon 8s Gen 3"
            code.startsWith("SM8550") -> "Snapdragon 8 Gen 2"
            code.startsWith("SM8475") -> "Snapdragon 8+ Gen 1"
            code.startsWith("SM8450") -> "Snapdragon 8 Gen 1"
            code.startsWith("SM7675") -> "Snapdragon 7+ Gen 3"
            code.startsWith("SM7550") -> "Snapdragon 7 Gen 3"
            code.startsWith("SM7475") -> "Snapdragon 7+ Gen 2"
            code.startsWith("QCS8550") -> "Qualcomm QCS8550"
            else -> null
        }
    }

    private fun readArmIsaFeatures(): List<String> {
        val raw = runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull {
                    it.startsWith("Features", ignoreCase = true)
                }?.substringAfter(":")?.trim().orEmpty()
            }
        }.getOrDefault("")

        if (raw.isBlank()) return listOf("NEON")
        val tokens = raw.split(Regex("\\s+")).map { it.lowercase() }.toSet()
        val badges = mutableListOf<String>()
        if ("asimd" in tokens || "neon" in tokens) badges += "NEON"
        if ("fphp" in tokens || "asimdhp" in tokens) badges += "FP16"
        if ("asimddp" in tokens) badges += "DotProd"
        if ("i8mm" in tokens) badges += "I8MM"
        if ("bf16" in tokens) badges += "BF16"
        if ("sve" in tokens || "sve2" in tokens) badges += "SVE"
        return badges.ifEmpty { listOf("ARM64") }
    }
}

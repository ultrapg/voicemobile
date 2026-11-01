package io.github.ultrapg.voicemobile.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class NativeHardwareStatus(
    val cpuAvailable: Boolean = true,
    val cpuDesc: String = "",
    val activeBackend: String = "",
    val cpuThreads: Int = 4
)

data class DeviceHardwareProfile(
    val deviceModel: String,
    val socManufacturer: String,
    val socModel: String,
    val cpuCores: Int,
    val armIsaFeatures: List<String>,
    val totalRamGb: Float,
    val recommendLowMem: Boolean,
    val nativeStatus: NativeHardwareStatus
) {
    val summaryLine: String
        get() = listOf(socManufacturer, socModel)
            .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            .joinToString(" ")
            .ifBlank { deviceModel }
}

object HardwareProbe {

    fun inspect(context: Context, nativeStatus: NativeHardwareStatus): DeviceHardwareProfile {
        val socMfg = Build.SOC_MANUFACTURER.orEmpty().trim()
        val socModel = Build.SOC_MODEL.orEmpty().trim()
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
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            armIsaFeatures = isa,
            totalRamGb = totalRamGb,
            recommendLowMem = totalRamGb <= 6.5f,
            nativeStatus = nativeStatus
        )
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

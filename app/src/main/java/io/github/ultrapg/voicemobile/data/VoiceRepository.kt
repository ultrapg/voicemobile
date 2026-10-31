package io.github.ultrapg.voicemobile.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class VoiceProfileItem(
    val id: String,
    val name: String,
    val filePath: String,
    val isFullIcl: Boolean, // true = .json (ECAPA + 12Hz speech codes), false = .bin (ECAPA speaker embedding)
    val referenceTranscript: String = "",
    val createdAtMs: Long,
    val sizeBytes: Long
)

@Serializable
data class GeneratedClipItem(
    val id: String,
    val title: String,
    val textPreview: String,
    val modeLabel: String,
    val backendUsed: String,
    val filePath: String,
    val durationSec: Float,
    val rtf: Float,
    val ttfaMs: Long = -1L,
    val createdAtMs: Long,
    val waveformBars: List<Float> = emptyList()
)

class VoiceRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val profilesDir: File = File(context.filesDir, "profiles").apply { mkdirs() }
    val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }
    val tempRefDir: File = File(context.cacheDir, "ref_audio").apply { mkdirs() }

    private val profilesMetaFile = File(profilesDir, "_profiles_index.json")
    private val clipsMetaFile = File(audioDir, "_clips_index.json")

    suspend fun loadProfiles(): List<VoiceProfileItem> = withContext(Dispatchers.IO) {
        val indexed = runCatching {
            if (profilesMetaFile.exists()) {
                json.decodeFromString<List<VoiceProfileItem>>(profilesMetaFile.readText())
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val valid = indexed.filter { File(it.filePath).exists() }.toMutableList()
        val knownPaths = valid.map { it.filePath }.toSet()

        // Discover any manually placed .json / .bin profile files
        profilesDir.listFiles().orEmpty()
            .filter {
                it.isFile &&
                    !it.name.startsWith("_") &&
                    (it.extension.equals("json", ignoreCase = true) || it.extension.equals("bin", ignoreCase = true)) &&
                    it.absolutePath !in knownPaths
            }
            .forEach { file ->
                valid += VoiceProfileItem(
                    id = file.nameWithoutExtension,
                    name = file.nameWithoutExtension.replace('_', ' '),
                    filePath = file.absolutePath,
                    isFullIcl = file.extension.equals("json", ignoreCase = true),
                    createdAtMs = file.lastModified(),
                    sizeBytes = file.length()
                )
            }

        valid.sortedByDescending { it.createdAtMs }
    }

    suspend fun saveProfileEntry(item: VoiceProfileItem): List<VoiceProfileItem> = withContext(Dispatchers.IO) {
        val updated = (loadProfiles().filterNot { it.id == item.id || it.filePath == item.filePath } + item)
            .sortedByDescending { it.createdAtMs }
        profilesMetaFile.writeText(json.encodeToString(updated))
        updated
    }

    suspend fun renameProfile(id: String, newName: String): List<VoiceProfileItem> = withContext(Dispatchers.IO) {
        val trimmed = newName.trim().ifBlank { return@withContext loadProfiles() }
        val updated = loadProfiles().map { if (it.id == id) it.copy(name = trimmed) else it }
        profilesMetaFile.writeText(json.encodeToString(updated))
        updated
    }

    suspend fun deleteProfile(id: String): List<VoiceProfileItem> = withContext(Dispatchers.IO) {
        val current = loadProfiles()
        current.firstOrNull { it.id == id }?.let { File(it.filePath).delete() }
        val remaining = current.filterNot { it.id == id }
        profilesMetaFile.writeText(json.encodeToString(remaining))
        remaining
    }

    suspend fun importProfileUri(uri: Uri): Result<VoiceProfileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "voice_profile.json"
            val isJson = rawName.endsWith(".json", ignoreCase = true)
            val isBin = rawName.endsWith(".bin", ignoreCase = true)
            require(isJson || isBin) { "Voice profile must be a .json (Full-ICL) or .bin (Embedding) file" }

            val id = "prof_${System.currentTimeMillis()}"
            val ext = if (isJson) "json" else "bin"
            val dest = File(profilesDir, "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out ->
                    input.copyTo(out)
                }
            } ?: error("Cannot read selected profile file")

            require(dest.length() >= 64L) { "Selected profile file is empty or invalid" }

            val item = VoiceProfileItem(
                id = id,
                name = rawName.substringBeforeLast('.').replace('_', ' ').ifBlank { "Imported Voice" },
                filePath = dest.absolutePath,
                isFullIcl = isJson,
                createdAtMs = System.currentTimeMillis(),
                sizeBytes = dest.length()
            )
            saveProfileEntry(item)
            item
        }
    }

    suspend fun loadClips(): List<GeneratedClipItem> = withContext(Dispatchers.IO) {
        val indexed = runCatching {
            if (clipsMetaFile.exists()) {
                json.decodeFromString<List<GeneratedClipItem>>(clipsMetaFile.readText())
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
        indexed.filter { File(it.filePath).exists() }.sortedByDescending { it.createdAtMs }
    }

    suspend fun addClip(item: GeneratedClipItem): List<GeneratedClipItem> = withContext(Dispatchers.IO) {
        val updated = (listOf(item) + loadClips().filterNot { it.id == item.id })
            .take(100)
        clipsMetaFile.writeText(json.encodeToString(updated))
        updated
    }

    suspend fun deleteClip(id: String): List<GeneratedClipItem> = withContext(Dispatchers.IO) {
        val current = loadClips()
        current.firstOrNull { it.id == id }?.let { File(it.filePath).delete() }
        val remaining = current.filterNot { it.id == id }
        clipsMetaFile.writeText(json.encodeToString(remaining))
        remaining
    }

    suspend fun exportFileToUri(sourceFile: File, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.exists()) { "Source file no longer exists" }
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 128 * 1024)
                }
            } ?: error("Unable to open destination URI for writing")
            Unit
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

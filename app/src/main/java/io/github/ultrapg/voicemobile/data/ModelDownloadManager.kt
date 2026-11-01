package io.github.ultrapg.voicemobile.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

class ModelDownloadManager(private val context: Context) {

    val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    fun listInstalledModels(): List<File> =
        modelsDir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) && ModelsCatalog.isValidGgufFile(it) }
            .sortedBy { it.name }

    fun totalStorageBytes(): Long =
        modelsDir.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    fun deleteModel(fileName: String): Boolean {
        File(modelsDir, "$fileName.part").delete()
        return File(modelsDir, fileName).delete()
    }

    /**
     * Atomic, resumable HTTP Range (.part) GGUF downloader with GGUF magic verification.
     */
    suspend fun downloadModel(
        fileName: String,
        onProgressText: ((String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, fileName)
        if (ModelsCatalog.isValidGgufFile(targetFile)) {
            return@withContext Result.success(targetFile)
        }

        val partFile = File(modelsDir, "$fileName.part")
        val spec = ModelsCatalog.specFor(fileName)

        try {
            val existingLen = if (partFile.exists()) partFile.length() else 0L
            val conn = openRedirectingConnection(spec.downloadUrl, existingLen)
            try {
                val code = conn.responseCode
                if (code == 416 && existingLen > 0L) {
                    partFile.delete()
                    error("Partial download invalidated (HTTP 416) — tap Fetch again")
                }
                val isResume = (code == HttpURLConnection.HTTP_PARTIAL && existingLen > 0L)
                if (code !in 200..299) {
                    error("HTTP $code downloading $fileName")
                }

                val contentLen = conn.contentLengthLong
                val expectedExactBytes = when {
                    isResume && contentLen > 0L -> existingLen + contentLen
                    contentLen > 0L -> contentLen
                    else -> -1L
                }
                val totalBytes = when {
                    expectedExactBytes > 0L -> expectedExactBytes
                    spec.approxSizeMb > 0 -> spec.approxSizeMb.toLong() * 1024L * 1024L
                    else -> -1L
                }

                var written = if (isResume) existingLen else 0L
                var lastTickMs = System.currentTimeMillis()
                var lastTickBytes = written
                var smoothedSpeed = 0L

                updateProgress(fileName, written, totalBytes, 0L)

                conn.inputStream.use { input ->
                    FileOutputStream(partFile, isResume).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (n == 0) continue
                            output.write(buffer, 0, n)
                            written += n

                            val now = System.currentTimeMillis()
                            val dt = now - lastTickMs
                            if (dt >= 250L) {
                                val instSpeed = ((written - lastTickBytes) * 1000L) / dt
                                smoothedSpeed = if (smoothedSpeed == 0L) instSpeed else (smoothedSpeed * 3 + instSpeed) / 4
                                lastTickMs = now
                                lastTickBytes = written
                                updateProgress(fileName, written, totalBytes, smoothedSpeed)
                                val pct = if (totalBytes > 0L) "${(written * 100L / totalBytes)}%" else "${written / (1024 * 1024)} MB"
                                onProgressText?.invoke("Downloading $fileName ($pct)")
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (expectedExactBytes > 0L && partFile.length() != expectedExactBytes) {
                    val gotMb = partFile.length() / (1024L * 1024L)
                    val wantMb = expectedExactBytes / (1024L * 1024L)
                    if (partFile.length() > expectedExactBytes) {
                        partFile.delete()
                    }
                    error("Download incomplete ($gotMb / $wantMb MB) — tap Fetch to resume")
                }

                if (!ModelsCatalog.isValidGgufFile(partFile)) {
                    partFile.delete()
                    error("Downloaded file failed GGUF verification")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
                Result.success(targetFile)
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            _activeDownloads.update { it - fileName }
        }
    }

    /**
     * Imports a user-selected local .gguf file via SAF ContentResolver without external storage permissions.
     */
    suspend fun importLocalGguf(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.gguf"
            val cleanName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").let {
                if (it.endsWith(".gguf", ignoreCase = true)) it else "$it.gguf"
            }
            val tempFile = File(modelsDir, "$cleanName.import_tmp")
            val destFile = File(modelsDir, cleanName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                    output.fd.sync()
                }
            } ?: error("Unable to read selected file")

            if (!ModelsCatalog.isValidGgufFile(tempFile)) {
                tempFile.delete()
                error("Selected file is not a valid GGUF model")
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            destFile
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun updateProgress(fileName: String, downloaded: Long, total: Long, speed: Long) {
        _activeDownloads.update { map ->
            map + (fileName to DownloadProgress(fileName, downloaded, total, speed))
        }
    }

    private fun openRedirectingConnection(urlStr: String, resumeFromBytes: Long): HttpURLConnection {
        var currentUrl = urlStr
        repeat(6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "VoiceMobile/1.0 (Android; Qwen3-TTS)")
                if (resumeFromBytes > 0L) {
                    setRequestProperty("Range", "bytes=$resumeFromBytes-")
                }
            }
            conn.connect()
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("Redirect missing Location header")
                currentUrl = URL(URL(currentUrl), loc).toString()
            } else {
                return conn
            }
        }
        error("Too many redirects while connecting to Hugging Face")
    }
}

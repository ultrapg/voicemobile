package io.github.ultrapg.voicemobile.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

data class PreparedReferenceAudio(
    val wavFile: File,
    val displayName: String,
    val durationSec: Float
)

/**
 * Pure Android replacement for desktop voicecli's ffmpeg helper:
 * Decodes WAV, MP3, M4A/AAC, FLAC, OGG/Opus via MediaExtractor + MediaCodec,
 * downmixes to mono, resamples to 24,000 Hz, and writes a clean 16-bit PCM WAV.
 */
object AudioConverter {

    const val TARGET_SAMPLE_RATE = 24_000
    private const val MAX_REF_SECONDS = 30 // Cap reference audio to 30s to keep ICL fast and within context

    suspend fun importAndConvertTo24kMonoWav(
        context: Context,
        uri: Uri,
        destDir: File
    ): Result<PreparedReferenceAudio> = withContext(Dispatchers.IO) {
        runCatching {
            destDir.mkdirs()
            val displayName = queryDisplayName(context, uri) ?: "reference_audio"
            val tempInput = File(destDir, "raw_import_${System.currentTimeMillis()}.tmp")
            val outWav = File(destDir, "ref_${System.currentTimeMillis()}.wav")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempInput).use { output ->
                        input.copyTo(output, bufferSize = 128 * 1024)
                    }
                } ?: error("Cannot read selected audio file")

                // Try direct RIFF WAV parser first, then fallback to MediaExtractor + MediaCodec
                val decoded = tryDecodeRiffWav(tempInput) ?: decodeWithMediaCodec(tempInput)
                require(decoded.samples.isNotEmpty()) { "Decoded audio contains no samples" }

                val resampled = if (decoded.sampleRate == TARGET_SAMPLE_RATE) {
                    decoded.samples
                } else {
                    resampleLinear(decoded.samples, decoded.sampleRate, TARGET_SAMPLE_RATE)
                }

                val maxSamples = TARGET_SAMPLE_RATE * MAX_REF_SECONDS
                val capped = if (resampled.size > maxSamples) {
                    resampled.copyOfRange(0, maxSamples)
                } else {
                    resampled
                }

                writeMono16BitWav(outWav, capped, TARGET_SAMPLE_RATE)
                val durationSec = capped.size.toFloat() / TARGET_SAMPLE_RATE.toFloat()
                PreparedReferenceAudio(
                    wavFile = outWav,
                    displayName = displayName,
                    durationSec = durationSec
                )
            } finally {
                tempInput.delete()
            }
        }
    }

    private class DecodedMonoFloat(val samples: FloatArray, val sampleRate: Int)

    private fun tryDecodeRiffWav(file: File): DecodedMonoFloat? = runCatching {
        if (file.length() < 44L) return null
        val bytes = if (file.length() <= 64L * 1024L * 1024L) file.readBytes() else return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null

        var audioFormat = 0
        var channels = 1
        var sampleRate = 24000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(pos + 4)
            val bodyStart = pos + 8
            if (chunkSize < 0 || bodyStart > bytes.size) break

            if (id == "fmt " && chunkSize >= 16 && bodyStart + 16 <= bytes.size) {
                audioFormat = bb.getShort(bodyStart).toInt() and 0xFFFF
                channels = (bb.getShort(bodyStart + 2).toInt() and 0xFFFF).coerceAtLeast(1)
                sampleRate = bb.getInt(bodyStart + 4)
                bitsPerSample = bb.getShort(bodyStart + 14).toInt() and 0xFFFF
            } else if (id == "data") {
                dataOffset = bodyStart
                dataSize = chunkSize.coerceAtMost(bytes.size - bodyStart)
                break
            }
            pos = bodyStart + chunkSize + (chunkSize and 1)
        }

        if (dataOffset < 0 || dataSize <= 0 || sampleRate <= 0) return null
        // Support PCM (1) 16/24/32-bit and IEEE Float (3) 32-bit; let MediaCodec handle exotic compressed WAVs
        if (audioFormat != 1 && audioFormat != 3) return null

        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * channels
        val frames = dataSize / frameSize
        if (frames <= 0) return null

        val mono = FloatArray(frames)
        var offset = dataOffset
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val sample = when {
                    audioFormat == 1 && bitsPerSample == 16 ->
                        bb.getShort(offset).toFloat() / 32768f
                    audioFormat == 1 && bitsPerSample == 24 -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt() // sign-extended
                        val v = b0 or (b1 shl 8) or (b2 shl 16)
                        v.toFloat() / 8_388_608f
                    }
                    audioFormat == 1 && bitsPerSample == 32 ->
                        bb.getInt(offset).toFloat() / 2_147_483_648f
                    audioFormat == 3 && bitsPerSample == 32 ->
                        bb.getFloat(offset)
                    else -> return null
                }
                sum += sample
                offset += bytesPerSample
            }
            mono[f] = (sum / channels.toFloat()).coerceIn(-1f, 1f)
        }
        DecodedMonoFloat(mono, sampleRate)
    }.getOrNull()

    private fun decodeWithMediaCodec(file: File): DecodedMonoFloat {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            require(audioTrackIndex >= 0 && trackFormat != null) {
                "Unsupported or non-audio file format"
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(trackFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                val maxOutSamples = sampleRate * (MAX_REF_SECONDS + 5)
                var outBuffer = FloatArray(sampleRate * 10)
                var outCount = 0

                fun appendMono(v: Float) {
                    if (outCount >= outBuffer.size) {
                        outBuffer = outBuffer.copyOf(outBuffer.size * 2)
                    }
                    outBuffer[outCount++] = v.coerceIn(-1f, 1f)
                }

                while (!outputDone && outCount < maxOutSamples) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val read = extractor.readSampleData(inBuf, 0)
                            if (read < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, read, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFmt = codec.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                pcmEncoding = newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                outBuf.order(ByteOrder.LITTLE_ENDIAN)

                                if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    val fb = outBuf.asFloatBuffer()
                                    val frames = fb.remaining() / channelCount
                                    for (f in 0 until frames) {
                                        var sum = 0f
                                        for (c in 0 until channelCount) sum += fb.get()
                                        appendMono(sum / channelCount)
                                    }
                                } else {
                                    val sb = outBuf.asShortBuffer()
                                    val frames = sb.remaining() / channelCount
                                    for (f in 0 until frames) {
                                        var sum = 0f
                                        for (c in 0 until channelCount) {
                                            sum += sb.get().toFloat() / 32768f
                                        }
                                        appendMono(sum / channelCount)
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }

                return DecodedMonoFloat(outBuffer.copyOf(outCount), sampleRate)
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty() || srcRate <= 0 || dstRate <= 0 || srcRate == dstRate) return input
        val outLen = ((input.size.toLong() * dstRate.toLong()) / srcRate.toLong()).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val lastIdx = input.size - 1
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, lastIdx)
            val i1 = (i0 + 1).coerceAtMost(lastIdx)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }

    fun writeMono16BitWav(dest: File, samples: FloatArray, sampleRate: Int) {
        // Normalize lightly if clip is extremely quiet so ECAPA-TDNN gets clean SNR
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        val gain = if (peak in 0.005f..0.35f) (0.85f / peak) else 1f

        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()

        BufferedOutputStream(FileOutputStream(dest), 65536).use { out ->
            out.write(header)
            val chunk = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
            var idx = 0
            while (idx < samples.size) {
                chunk.clear()
                val end = (idx + 8192).coerceAtMost(samples.size)
                while (idx < end) {
                    val v = (samples[idx++] * gain).coerceIn(-1f, 1f)
                    val pcm = (v * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
                    chunk.putShort(pcm)
                }
                out.write(chunk.array(), 0, chunk.position())
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

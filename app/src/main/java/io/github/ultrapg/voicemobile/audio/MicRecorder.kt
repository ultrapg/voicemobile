package io.github.ultrapg.voicemobile.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class MicRecordingState(
    val isRecording: Boolean = false,
    val elapsedSec: Float = 0f,
    val level: Float = 0f
)

class MicRecorder(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(MicRecordingState())
    val state: StateFlow<MicRecordingState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private val pcmStream = ByteArrayOutputStream()

    @Volatile
    private var stopRequested = false

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_state.value.isRecording) return true

        val sr = AudioConverter.TARGET_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                (minBuf * 2).coerceAtLeast(sr)
            )
        }.getOrNull() ?: return false

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        pcmStream.reset()
        stopRequested = false
        recorder.startRecording()
        _state.value = MicRecordingState(isRecording = true, elapsedSec = 0f, level = 0f)

        recordJob = scope.launch(Dispatchers.IO) {
            val shortBuf = ShortArray(2048)
            val byteChunk = ByteBuffer.allocate(shortBuf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            var totalSamples = 0L
            val maxSamples = sr * 30L // 30s auto-stop cap

            try {
                while (isActive && !stopRequested && totalSamples < maxSamples) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        byteChunk.clear()
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = shortBuf[i]
                            byteChunk.putShort(s)
                            val f = s.toDouble() / 32768.0
                            sumSq += f * f
                        }
                        synchronized(pcmStream) {
                            pcmStream.write(byteChunk.array(), 0, read * 2)
                        }
                        totalSamples += read
                        val rms = sqrt(sumSq / read.toDouble()).toFloat()
                        val uiLevel = (rms * 3.5f).coerceIn(0.02f, 1f)
                        _state.value = MicRecordingState(
                            isRecording = true,
                            elapsedSec = totalSamples.toFloat() / sr.toFloat(),
                            level = uiLevel
                        )
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                _state.value = MicRecordingState(isRecording = false)
            }
        }
        return true
    }

    suspend fun stopAndSaveWav(destDir: File): Result<PreparedReferenceAudio> = withContext(Dispatchers.IO) {
        stopRequested = true
        recordJob?.join()
        recordJob = null

        runCatching {
            val rawBytes = synchronized(pcmStream) { pcmStream.toByteArray() }
            require(rawBytes.size >= AudioConverter.TARGET_SAMPLE_RATE * 2) {
                "Recording is too short — please speak for at least 2–3 seconds"
            }

            val samples = FloatArray(rawBytes.size / 2)
            val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in samples.indices) {
                samples[i] = bb.getShort().toFloat() / 32768f
            }

            destDir.mkdirs()
            val outFile = File(destDir, "mic_ref_${System.currentTimeMillis()}.wav")
            AudioConverter.writeMono16BitWav(outFile, samples, AudioConverter.TARGET_SAMPLE_RATE)
            val duration = samples.size.toFloat() / AudioConverter.TARGET_SAMPLE_RATE.toFloat()
            PreparedReferenceAudio(
                wavFile = outFile,
                displayName = "Mic Recording (${String.format("%.1fs", duration)})",
                durationSec = duration
            )
        }
    }

    fun cancel() {
        stopRequested = true
        recordJob?.cancel()
        recordJob = null
        _state.value = MicRecordingState(isRecording = false)
    }
}

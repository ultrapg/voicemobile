package io.github.ultrapg.voicemobile.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val activeClipId: String? = null,
    val activeTitle: String = "",
    val isPlaying: Boolean = false,
    val isLiveStreaming: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
) {
    val progress: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

class AudioPlayerManager(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var pollJob: Job? = null

    private val streamLock = Any()
    private var liveAudioTrack: AudioTrack? = null
    private var streamedSamplesCount: Long = 0L

    fun playClip(clipId: String, title: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        // Toggle play/pause if tapping the already-loaded clip
        val current = _state.value
        if (current.activeClipId == clipId && mediaPlayer != null && !current.isLiveStreaming) {
            togglePlayPause()
            return
        }

        stopAll()
        val mp = MediaPlayer()
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            val dur = mp.duration.coerceAtLeast(1)
            mp.setOnCompletionListener {
                pollJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false, positionMs = dur)
            }
            mp.start()
            mediaPlayer = mp
            _state.value = PlaybackState(
                activeClipId = clipId,
                activeTitle = title,
                isPlaying = true,
                isLiveStreaming = false,
                positionMs = 0,
                durationMs = dur
            )
            startPolling()
        }.onFailure {
            runCatching { mp.release() }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                pollJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false, positionMs = mp.currentPosition)
            } else {
                if (mp.currentPosition >= mp.duration - 60) {
                    mp.seekTo(0)
                }
                mp.start()
                _state.value = _state.value.copy(isPlaying = true)
                startPolling()
            }
        }
    }

    fun seekToFraction(fraction: Float) {
        val mp = mediaPlayer ?: return
        runCatching {
            val targetMs = (mp.duration * fraction.coerceIn(0f, 1f)).toInt()
            mp.seekTo(targetMs)
            _state.value = _state.value.copy(positionMs = targetMs)
        }
    }

    /**
     * Starts low-latency PCM Float AudioTrack for live Qwen3-TTS streaming vocoder playback.
     */
    fun startLiveStream(sampleRate: Int = 24000) {
        stopAll()
        synchronized(streamLock) {
            val sr = sampleRate.coerceAtLeast(8000)
            val minBuf = AudioTrack.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(sr * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            liveAudioTrack = track
            streamedSamplesCount = 0L
            _state.value = PlaybackState(
                activeClipId = "__live_stream__",
                activeTitle = "Live Streaming Vocoder",
                isPlaying = true,
                isLiveStreaming = true
            )
        }
    }

    fun pushLiveStreamChunk(samples: FloatArray, sampleRate: Int): Boolean {
        synchronized(streamLock) {
            val track = liveAudioTrack ?: return false
            var offset = 0
            while (offset < samples.size) {
                val written = track.write(
                    samples,
                    offset,
                    samples.size - offset,
                    AudioTrack.WRITE_BLOCKING
                )
                if (written <= 0) return false
                offset += written
            }
            streamedSamplesCount += samples.size
            val streamedMs = ((streamedSamplesCount * 1000L) / sampleRate.coerceAtLeast(1)).toInt()
            _state.value = _state.value.copy(positionMs = streamedMs, durationMs = streamedMs)
            return true
        }
    }

    fun finishLiveStream() {
        synchronized(streamLock) {
            runCatching {
                liveAudioTrack?.stop()
                liveAudioTrack?.release()
            }
            liveAudioTrack = null
            if (_state.value.isLiveStreaming) {
                _state.value = PlaybackState()
            }
        }
    }

    fun stopAll() {
        pollJob?.cancel()
        pollJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        finishLiveStream()
        _state.value = PlaybackState()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mp = mediaPlayer ?: break
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                val dur = runCatching { mp.duration }.getOrDefault(0)
                _state.value = _state.value.copy(
                    isPlaying = playing,
                    positionMs = pos,
                    durationMs = dur
                )
                if (!playing) break
                delay(60L)
            }
        }
    }
}

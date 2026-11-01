package io.github.ultrapg.voicemobile.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
    private var liveChunkChannel: Channel<Pair<FloatArray, Int>>? = null
    private var liveWriterJob: Job? = null
    private val liveAborted = AtomicBoolean(false)

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
     * Starts an asynchronous, underrun-free PCM Float AudioTrack pipeline for live Qwen3-TTS
     * streaming vocoder playback. AudioTrack playback starts when the first PCM chunk arrives,
     * and drains completely at the end of synthesis before releasing.
     */
    fun startLiveStream(sampleRate: Int = 24000) {
        stopAll()
        val channel = Channel<Pair<FloatArray, Int>>(Channel.UNLIMITED)
        liveAborted.set(false)
        synchronized(streamLock) {
            liveChunkChannel = channel
        }
        _state.value = PlaybackState(
            activeClipId = "__live_stream__",
            activeTitle = "Live Streaming Vocoder",
            isPlaying = true,
            isLiveStreaming = true,
            positionMs = 0,
            durationMs = 0
        )

        liveWriterJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            var trackSr = sampleRate.coerceAtLeast(8000)
            var totalWrittenFrames = 0L
            var startedPlaying = false

            try {
                for ((samples, chunkSr) in channel) {
                    if (!isActive || liveAborted.get()) break
                    if (samples.isEmpty()) continue

                    val desiredSr = if (chunkSr > 0) chunkSr else trackSr
                    if (track == null) {
                        trackSr = desiredSr.coerceAtLeast(8000)
                        track = createFloatStreamTrack(trackSr)
                        synchronized(streamLock) {
                            if (liveAborted.get()) {
                                runCatching { track.release() }
                                return@launch
                            }
                            liveAudioTrack = track
                        }
                    }

                    val activeTrack = track
                    var offset = 0
                    while (offset < samples.size && isActive && !liveAborted.get()) {
                        if (!startedPlaying) {
                            // Prime initial buffer non-blockingly then start playback immediately
                            val written = activeTrack.write(
                                samples,
                                offset,
                                samples.size - offset,
                                AudioTrack.WRITE_NON_BLOCKING
                            )
                            activeTrack.play()
                            startedPlaying = true
                            if (written > 0) {
                                offset += written
                                totalWrittenFrames += written
                                continue
                            }
                        }
                        val written = activeTrack.write(
                            samples,
                            offset,
                            samples.size - offset,
                            AudioTrack.WRITE_BLOCKING
                        )
                        if (written <= 0) break
                        offset += written
                        totalWrittenFrames += written
                    }

                    val headFrames = (activeTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
                        .coerceAtMost(totalWrittenFrames)
                    val playedMs = ((headFrames * 1000L) / trackSr).toInt()
                    val bufferedMs = ((totalWrittenFrames * 1000L) / trackSr).toInt()
                    _state.value = PlaybackState(
                        activeClipId = "__live_stream__",
                        activeTitle = "Live Streaming Vocoder",
                        isPlaying = true,
                        isLiveStreaming = true,
                        positionMs = playedMs,
                        durationMs = bufferedMs
                    )
                }

                // Synthesis finished pushing chunks: drain remaining hardware AudioTrack buffer cleanly
                val drainTrack = track
                if (drainTrack != null && startedPlaying && !liveAborted.get() && totalWrittenFrames > 0L) {
                    val bufferedMs = ((totalWrittenFrames * 1000L) / trackSr)
                    var lastHead = -1L
                    var stagnantMs = 0L
                    var waitedMs = 0L
                    val maxWaitMs = bufferedMs + 1500L

                    while (isActive && !liveAborted.get() && waitedMs < maxWaitMs) {
                        val head = drainTrack.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                        val playedMs = ((head.coerceAtMost(totalWrittenFrames) * 1000L) / trackSr).toInt()
                        _state.value = PlaybackState(
                            activeClipId = "__live_stream__",
                            activeTitle = "Live Streaming Vocoder",
                            isPlaying = true,
                            isLiveStreaming = true,
                            positionMs = playedMs,
                            durationMs = bufferedMs.toInt()
                        )
                        if (head >= totalWrittenFrames) {
                            break
                        }
                        if (head == lastHead) {
                            stagnantMs += 30L
                            if (stagnantMs >= 650L) break
                        } else {
                            lastHead = head
                            stagnantMs = 0L
                        }
                        delay(30L)
                        waitedMs += 30L
                    }
                }
            } catch (_: CancellationException) {
                // Aborted by user
            } catch (_: Throwable) {
                // Audio device error; clean up safely below
            } finally {
                synchronized(streamLock) {
                    runCatching {
                        track?.pause()
                        track?.flush()
                        track?.stop()
                        track?.release()
                    }
                    if (liveAudioTrack === track) {
                        liveAudioTrack = null
                    }
                    if (liveChunkChannel === channel) {
                        liveChunkChannel = null
                    }
                }
                if (_state.value.isLiveStreaming) {
                    _state.value = PlaybackState()
                }
            }
        }
    }

    /**
     * Enqueues a live PCM chunk without blocking the native C++ synthesis thread.
     */
    fun pushLiveStreamChunk(samples: FloatArray, sampleRate: Int): Boolean {
        if (liveAborted.get()) return false
        if (samples.isEmpty()) return true
        val ch = synchronized(streamLock) { liveChunkChannel } ?: return false
        return ch.trySend(samples.copyOf() to sampleRate).isSuccess
    }

    /**
     * Signals that no more chunks will be produced and suspends until all queued audio finishes playing.
     */
    suspend fun completeLiveStreamAndDrain() {
        val job: Job?
        synchronized(streamLock) {
            liveChunkChannel?.close()
            job = liveWriterJob
        }
        job?.join()
    }

    /**
     * Immediately stops and discards any active or buffered live stream audio.
     */
    fun abortLiveStream() {
        liveAborted.set(true)
        val jobToCancel: Job?
        synchronized(streamLock) {
            liveChunkChannel?.close()
            liveChunkChannel = null
            jobToCancel = liveWriterJob
            liveWriterJob = null
            runCatching {
                liveAudioTrack?.pause()
                liveAudioTrack?.flush()
                liveAudioTrack?.stop()
                liveAudioTrack?.release()
            }
            liveAudioTrack = null
        }
        jobToCancel?.cancel()
        if (_state.value.isLiveStreaming) {
            _state.value = PlaybackState()
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
        abortLiveStream()
        _state.value = PlaybackState()
    }

    private fun createFloatStreamTrack(sr: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sr * 4)
        // ~1.5s float ring buffer prevents underruns between neural vocoder chunks
        val targetBytes = maxOf(minBuf * 3, sr * 4 * 3 / 2)

        return AudioTrack.Builder()
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
            .setBufferSizeInBytes(targetBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
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

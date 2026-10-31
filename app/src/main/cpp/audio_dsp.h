#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace voicemobile {

// Pitch-preserving Synchronized Overlap-Add (SOLA) time-stretching (0.25x .. 3.0x)
// Uses O(1) prefix-sum energy normalization + cross-correlation + Hann crossfade.
std::vector<float> sola_time_stretch(
    const float * input,
    size_t n_samples,
    int sample_rate,
    float speed
);

// Remove DC offset and normalize peak amplitude to target_peak (~0.944 = -0.5 dBFS).
void normalize_audio_peak(
    std::vector<float> & samples,
    float target_peak = 0.944f
);

// Trim leading/trailing silence (-45 dBFS with 25ms safety margin) and apply
// 15ms Hann fade-in + 20ms Hann fade-out for seamless multi-chunk stitching.
void trim_and_fade_chunk(
    std::vector<float> & samples,
    int sample_rate
);

// Write mono 16-bit PCM RIFF WAV file using a 64 KB I/O buffer.
bool write_wav_pcm16(
    const std::string & path,
    const float * samples,
    size_t n_samples,
    int sample_rate
);

} // namespace voicemobile

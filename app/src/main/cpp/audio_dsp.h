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

// Stateful streaming time-stretcher & soft peak limiter for live audio chunks.
// At speed == 1.0x, passes samples through with zero phase latency and soft-clip protection.
// At speed != 1.0x, maintains continuous overlap-add state across chunk boundaries.
class StreamingSpeedStretcher {
public:
    StreamingSpeedStretcher(int sample_rate = 24000, float speed = 1.0f);

    void reset(int sample_rate, float speed);
    void ensure_sample_rate(int sample_rate);
    void process(const float * samples, size_t n_samples, std::vector<float> & out);
    void flush(std::vector<float> & out);

private:
    int sample_rate_ = 24000;
    float speed_ = 1.0f;
    int synth_hop_ = 480;
    int win_len_ = 960;
    int overlap_ = 480;
    int search_len_ = 360;
    double ana_hop_ = 480.0;
    double ana_pos_ = 0.0;
    bool started_ = false;
    std::vector<float> fade_in_;
    std::vector<float> fade_out_;
    std::vector<float> input_buf_;
    std::vector<float> tail_overlap_;
};

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

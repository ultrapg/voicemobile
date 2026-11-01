#include "audio_dsp.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace voicemobile {

namespace {
constexpr float kPi = 3.14159265358979323846f;

inline float sanitize_sample(float s) {
    return std::isfinite(s) ? s : 0.0f;
}

inline float soft_limit_sample(float s) {
    if (!std::isfinite(s)) {
        return 0.0f;
    }
    const float ax = std::fabs(s);
    if (ax <= 0.90f) {
        return s;
    }
    // Smooth knee above 0.90 to ceiling ~0.995 without harsh digital clipping
    const float excess = (ax - 0.90f) / 0.095f;
    const float limited = 0.90f + 0.095f * std::tanh(excess);
    return s >= 0.0f ? limited : -limited;
}
}

StreamingSpeedStretcher::StreamingSpeedStretcher(int sample_rate, float speed) {
    reset(sample_rate, speed);
}

void StreamingSpeedStretcher::reset(int sample_rate, float speed) {
    sample_rate_ = sample_rate > 0 ? sample_rate : 24000;
    speed_ = std::clamp(speed, 0.25f, 3.0f);
    synth_hop_ = std::max(32, (sample_rate_ * 20) / 1000); // 20ms hop
    win_len_ = synth_hop_ * 2;                             // 40ms window (exact 50% overlap)
    overlap_ = synth_hop_;
    search_len_ = std::max(16, (sample_rate_ * 15) / 1000); // 15ms correlation search
    ana_hop_ = (double) synth_hop_ * (double) speed_;
    ana_pos_ = 0.0;
    started_ = false;
    input_buf_.clear();
    tail_overlap_.clear();

    fade_in_.resize((size_t) overlap_);
    fade_out_.resize((size_t) overlap_);
    for (int i = 0; i < overlap_; ++i) {
        const float t = (float) i / (float) overlap_;
        fade_in_[(size_t) i] = 0.5f * (1.0f - std::cos(kPi * t));
        fade_out_[(size_t) i] = 1.0f - fade_in_[(size_t) i];
    }
}

void StreamingSpeedStretcher::ensure_sample_rate(int sample_rate) {
    const int sr = sample_rate > 0 ? sample_rate : 24000;
    if (sr != sample_rate_ && !started_ && input_buf_.empty()) {
        reset(sr, speed_);
    }
}

void StreamingSpeedStretcher::process(const float * samples, size_t n_samples, std::vector<float> & out) {
    out.clear();
    if (!samples || n_samples == 0) {
        return;
    }

    if (std::fabs(speed_ - 1.0f) < 0.005f) {
        out.resize(n_samples);
        for (size_t i = 0; i < n_samples; ++i) {
            out[i] = soft_limit_sample(samples[i]);
        }
        return;
    }

    const size_t old_in = input_buf_.size();
    input_buf_.resize(old_in + n_samples);
    for (size_t i = 0; i < n_samples; ++i) {
        input_buf_[old_in + i] = sanitize_sample(samples[i]);
    }

    if (!started_) {
        if ((int) input_buf_.size() < win_len_) {
            return;
        }
        out.insert(out.end(), input_buf_.begin(), input_buf_.begin() + synth_hop_);
        tail_overlap_.assign(input_buf_.begin() + synth_hop_, input_buf_.begin() + win_len_);
        ana_pos_ = ana_hop_;
        started_ = true;
    }

    while (true) {
        const int center = (int) std::llround(ana_pos_);
        if (center + win_len_ + search_len_ > (int) input_buf_.size()) {
            break;
        }

        const int min_k = std::max(-search_len_, -center);
        const int max_k = std::min(search_len_, (int) input_buf_.size() - win_len_ - center);

        double tail_energy = 0.0;
        for (int i = 0; i < overlap_; ++i) {
            const double tv = (double) tail_overlap_[(size_t) i];
            tail_energy += tv * tv;
        }

        int best_k = 0;
        if (tail_energy > 1e-9) {
            double best_score = -1e30;
            for (int k = min_k; k <= max_k; ++k) {
                const float * cptr = input_buf_.data() + (center + k);
                double cand_energy = 0.0;
                double dot = 0.0;
                for (int i = 0; i < overlap_; ++i) {
                    const double cv = (double) cptr[i];
                    cand_energy += cv * cv;
                    dot += (double) tail_overlap_[(size_t) i] * cv;
                }
                if (cand_energy <= 1e-12) {
                    continue;
                }
                const double score = dot / (std::sqrt(tail_energy * cand_energy) + 1e-12);
                if (score > best_score) {
                    best_score = score;
                    best_k = k;
                }
            }
        }

        const float * chosen = input_buf_.data() + (center + best_k);
        const size_t out_base = out.size();
        out.resize(out_base + (size_t) overlap_);
        for (int i = 0; i < overlap_; ++i) {
            const float mixed =
                tail_overlap_[(size_t) i] * fade_out_[(size_t) i] +
                chosen[i] * fade_in_[(size_t) i];
            out[out_base + (size_t) i] = soft_limit_sample(mixed);
        }
        std::memcpy(
            tail_overlap_.data(),
            chosen + overlap_,
            (size_t) overlap_ * sizeof(float)
        );

        ana_pos_ += ana_hop_;
    }

    const int safe_drop = std::max(0, (int) std::floor(ana_pos_) - search_len_ - 4);
    if (safe_drop > 0 && safe_drop <= (int) input_buf_.size()) {
        input_buf_.erase(input_buf_.begin(), input_buf_.begin() + safe_drop);
        ana_pos_ -= (double) safe_drop;
    }

    for (float & s : out) {
        s = soft_limit_sample(s);
    }
}

void StreamingSpeedStretcher::flush(std::vector<float> & out) {
    out.clear();
    if (std::fabs(speed_ - 1.0f) < 0.005f) {
        return;
    }
    if (!started_) {
        out.resize(input_buf_.size());
        for (size_t i = 0; i < input_buf_.size(); ++i) {
            out[i] = soft_limit_sample(input_buf_[i]);
        }
        input_buf_.clear();
        return;
    }

    while (true) {
        const int center = (int) std::llround(ana_pos_);
        if (center < 0 || center + win_len_ > (int) input_buf_.size()) {
            break;
        }
        const int min_k = std::max(-search_len_, -center);
        const int max_k = std::min(search_len_, (int) input_buf_.size() - win_len_ - center);

        double tail_energy = 0.0;
        for (int i = 0; i < overlap_; ++i) {
            const double tv = (double) tail_overlap_[(size_t) i];
            tail_energy += tv * tv;
        }

        int best_k = 0;
        if (tail_energy > 1e-9) {
            double best_score = -1e30;
            for (int k = min_k; k <= max_k; ++k) {
                const float * cptr = input_buf_.data() + (center + k);
                double cand_energy = 0.0;
                double dot = 0.0;
                for (int i = 0; i < overlap_; ++i) {
                    const double cv = (double) cptr[i];
                    cand_energy += cv * cv;
                    dot += (double) tail_overlap_[(size_t) i] * cv;
                }
                if (cand_energy <= 1e-12) {
                    continue;
                }
                const double score = dot / (std::sqrt(tail_energy * cand_energy) + 1e-12);
                if (score > best_score) {
                    best_score = score;
                    best_k = k;
                }
            }
        }

        const float * chosen = input_buf_.data() + (center + best_k);
        const size_t out_base = out.size();
        out.resize(out_base + (size_t) overlap_);
        for (int i = 0; i < overlap_; ++i) {
            const float mixed =
                tail_overlap_[(size_t) i] * fade_out_[(size_t) i] +
                chosen[i] * fade_in_[(size_t) i];
            out[out_base + (size_t) i] = soft_limit_sample(mixed);
        }
        std::memcpy(
            tail_overlap_.data(),
            chosen + overlap_,
            (size_t) overlap_ * sizeof(float)
        );

        ana_pos_ += ana_hop_;
    }

    for (float s : tail_overlap_) {
        out.push_back(soft_limit_sample(s));
    }

    input_buf_.clear();
    tail_overlap_.clear();
    ana_pos_ = 0.0;
    started_ = false;
}

std::vector<float> sola_time_stretch(
    const float * input,
    size_t n_samples,
    int sample_rate,
    float speed
) {
    if (!input || n_samples == 0) {
        return {};
    }
    if (sample_rate <= 0) {
        sample_rate = 24000;
    }
    if (speed < 0.25f) speed = 0.25f;
    if (speed > 3.0f)  speed = 3.0f;

    if (std::fabs(speed - 1.0f) < 0.005f || n_samples < (size_t) (sample_rate / 10)) {
        return std::vector<float>(input, input + n_samples);
    }

    const int win_len    = std::max(64, (sample_rate * 40) / 1000); // 40ms window
    const int synth_hop  = win_len / 2;                             // 20ms synthesis hop
    const int overlap    = win_len - synth_hop;                     // 20ms overlap
    const int search_len = std::max(16, (sample_rate * 15) / 1000); // 15ms search range
    const double ana_hop = (double) synth_hop * (double) speed;

    if ((size_t) (win_len + search_len) >= n_samples) {
        return std::vector<float>(input, input + n_samples);
    }

    const size_t est_out = (size_t) ((double) n_samples / (double) speed) + (size_t) win_len * 2;
    std::vector<float> output(est_out, 0.0f);

    // Precompute O(1) prefix-sum squared energy array over input
    std::vector<double> prefix_sq(n_samples + 1, 0.0);
    for (size_t i = 0; i < n_samples; ++i) {
        const double v = (double) input[i];
        prefix_sq[i + 1] = prefix_sq[i] + v * v;
    }

    // Precompute Hann crossfade weights for overlap region
    std::vector<float> fade_in((size_t) overlap);
    std::vector<float> fade_out((size_t) overlap);
    for (int i = 0; i < overlap; ++i) {
        const float t = (float) i / (float) overlap;
        fade_in[(size_t) i] = 0.5f * (1.0f - std::cos(kPi * t));
        fade_out[(size_t) i] = 1.0f - fade_in[(size_t) i];
    }

    // Copy initial window directly
    std::memcpy(output.data(), input, (size_t) win_len * sizeof(float));
    size_t out_pos = (size_t) synth_hop;
    double ana_pos = ana_hop;

    while (true) {
        const int center = (int) std::llround(ana_pos);
        if (center < 0 || (size_t) (center + win_len) >= n_samples) {
            break;
        }
        if (out_pos + (size_t) win_len >= output.size()) {
            output.resize(output.size() + (size_t) win_len * 4, 0.0f);
        }

        const int min_k = std::max(-search_len, -center);
        const int max_k = std::min(search_len, (int) n_samples - win_len - center);

        // Energy of current synthesis tail overlap
        double tail_energy = 0.0;
        const float * tail = output.data() + out_pos;
        for (int i = 0; i < overlap; ++i) {
            const double tv = (double) tail[i];
            tail_energy += tv * tv;
        }

        int best_k = 0;
        if (tail_energy > 1e-9) {
            double best_score = -1e30;
            for (int k = min_k; k <= max_k; ++k) {
                const int cand = center + k;
                const double cand_energy = prefix_sq[(size_t) (cand + overlap)] - prefix_sq[(size_t) cand];
                if (cand_energy <= 1e-12) {
                    continue;
                }
                const float * cptr = input + cand;
                double dot = 0.0;
                for (int i = 0; i < overlap; ++i) {
                    dot += (double) tail[i] * (double) cptr[i];
                }
                const double norm = std::sqrt(tail_energy * cand_energy) + 1e-12;
                const double score = dot / norm;
                if (score > best_score) {
                    best_score = score;
                    best_k = k;
                }
            }
        }

        const float * chosen = input + (center + best_k);
        // Hann crossfade overlap region
        for (int i = 0; i < overlap; ++i) {
            output[out_pos + (size_t) i] =
                output[out_pos + (size_t) i] * fade_out[(size_t) i] +
                chosen[i] * fade_in[(size_t) i];
        }
        // Copy remainder of window
        std::memcpy(
            output.data() + out_pos + (size_t) overlap,
            chosen + overlap,
            (size_t) (win_len - overlap) * sizeof(float)
        );

        out_pos += (size_t) synth_hop;
        ana_pos += ana_hop;
    }

    const size_t final_len = std::min(out_pos + (size_t) overlap, output.size());
    output.resize(final_len);
    return output;
}

void normalize_audio_peak(std::vector<float> & samples, float target_peak) {
    if (samples.empty()) {
        return;
    }

    // Pass 1: Sanitize non-finite values & compute DC offset
    double sum = 0.0;
    for (float & s : samples) {
        s = sanitize_sample(s);
        sum += (double) s;
    }
    const float dc = (float) (sum / (double) samples.size());

    // Remove DC and measure peak
    float peak = 0.0f;
    for (float & s : samples) {
        s -= dc;
        const float a = std::fabs(s);
        if (a > peak) peak = a;
    }

    if (peak < 1e-6f) {
        return;
    }

    // Pass 2: Scale to target peak (-0.5 dBFS = 0.944f) and clamp safely
    const float gain = target_peak / peak;
    for (float & s : samples) {
        float v = s * gain;
        if (v > 0.999f) v = 0.999f;
        if (v < -0.999f) v = -0.999f;
        s = v;
    }
}

void trim_and_fade_chunk(std::vector<float> & samples, int sample_rate) {
    if (samples.empty()) {
        return;
    }
    if (sample_rate <= 0) {
        sample_rate = 24000;
    }

    constexpr float kThreshold = 0.0056f; // -45 dBFS
    const size_t margin = (size_t) ((sample_rate * 25) / 1000); // 25ms safety margin
    const size_t n = samples.size();

    size_t first_active = 0;
    while (first_active < n && std::fabs(samples[first_active]) < kThreshold) {
        first_active++;
    }
    if (first_active == n) {
        // Entire chunk is silence; keep brief 50ms pad
        samples.resize(std::min(n, (size_t) (sample_rate / 20)));
        return;
    }

    size_t last_active = n - 1;
    while (last_active > first_active && std::fabs(samples[last_active]) < kThreshold) {
        last_active--;
    }

    const size_t start = (first_active > margin) ? (first_active - margin) : 0;
    const size_t end = std::min(n, last_active + 1 + margin);
    if (start > 0 || end < n) {
        std::vector<float> trimmed(samples.begin() + (ptrdiff_t) start,
                                   samples.begin() + (ptrdiff_t) end);
        samples.swap(trimmed);
    }

    const size_t len = samples.size();
    const size_t fade_in_len  = std::min(len / 2, (size_t) ((sample_rate * 15) / 1000)); // 15ms
    const size_t fade_out_len = std::min(len / 2, (size_t) ((sample_rate * 20) / 1000)); // 20ms

    for (size_t i = 0; i < fade_in_len; ++i) {
        const float w = 0.5f * (1.0f - std::cos(kPi * (float) i / (float) fade_in_len));
        samples[i] *= w;
    }
    for (size_t i = 0; i < fade_out_len; ++i) {
        const float w = 0.5f * (1.0f - std::cos(kPi * (float) i / (float) fade_out_len));
        samples[len - 1 - i] *= w;
    }
}

bool write_wav_pcm16(
    const std::string & path,
    const float * samples,
    size_t n_samples,
    int sample_rate
) {
    FILE * f = std::fopen(path.c_str(), "wb");
    if (!f) {
        return false;
    }

    // 64 KB stdio buffer matching voicecli/src/model.c
    std::vector<char> io_buf(65536);
    std::setvbuf(f, io_buf.data(), _IOFBF, io_buf.size());

    const uint16_t num_channels = 1;
    const uint16_t bits_per_sample = 16;
    const uint32_t sr = (uint32_t) (sample_rate > 0 ? sample_rate : 24000);
    const uint32_t byte_rate = sr * num_channels * (bits_per_sample / 8);
    const uint16_t block_align = num_channels * (bits_per_sample / 8);
    const uint32_t data_bytes = (uint32_t) (n_samples * sizeof(int16_t));
    const uint32_t riff_size = 36u + data_bytes;

    uint8_t header[44];
    std::memcpy(header + 0,  "RIFF", 4);
    std::memcpy(header + 4,  &riff_size, 4);
    std::memcpy(header + 8,  "WAVE", 4);
    std::memcpy(header + 12, "fmt ", 4);
    const uint32_t fmt_size = 16;
    const uint16_t audio_format = 1; // PCM
    std::memcpy(header + 16, &fmt_size, 4);
    std::memcpy(header + 20, &audio_format, 2);
    std::memcpy(header + 22, &num_channels, 2);
    std::memcpy(header + 24, &sr, 4);
    std::memcpy(header + 28, &byte_rate, 4);
    std::memcpy(header + 32, &block_align, 2);
    std::memcpy(header + 34, &bits_per_sample, 2);
    std::memcpy(header + 36, "data", 4);
    std::memcpy(header + 40, &data_bytes, 4);

    if (std::fwrite(header, 1, 44, f) != 44) {
        std::fclose(f);
        return false;
    }

    constexpr size_t kBlockSamples = 8192;
    int16_t pcm_block[kBlockSamples];
    size_t idx = 0;
    while (idx < n_samples) {
        const size_t count = std::min(kBlockSamples, n_samples - idx);
        for (size_t i = 0; i < count; ++i) {
            float s = samples ? sanitize_sample(samples[idx + i]) : 0.0f;
            if (s > 1.0f) s = 1.0f;
            if (s < -1.0f) s = -1.0f;
            const int32_t q = (int32_t) std::lrintf(s * 32767.0f);
            pcm_block[i] = (int16_t) std::clamp(q, -32768, 32767);
        }
        if (std::fwrite(pcm_block, sizeof(int16_t), count, f) != count) {
            std::fclose(f);
            return false;
        }
        idx += count;
    }

    const int rc = std::fclose(f);
    return rc == 0;
}

} // namespace voicemobile

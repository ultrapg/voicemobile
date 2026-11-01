#include <jni.h>

#include "audio_dsp.h"
#include "qwen3_tts/gguf_loader.h"
#include "qwen3_tts/qwen3_tts.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <sstream>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#define JNI_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VoiceMobileJNI", __VA_ARGS__)
#define JNI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VoiceMobileJNI", __VA_ARGS__)
#else
#define JNI_LOGI(...)
#define JNI_LOGE(...)
#endif

namespace {

struct EngineWrapper {
    qwen3_tts::Qwen3TTS tts;
    std::string last_error;
};

std::string jstring_to_utf8(JNIEnv * env, jstring jstr) {
    if (!jstr) return "";
    const char * chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

std::string json_escape(const std::string & s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (unsigned char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c >= 0x20) out.push_back((char) c);
                break;
        }
    }
    return out;
}

// Query Qualcomm FastRPC CDSP architecture version (e.g. 73, 75, 79, 81, 85) if libcdsprpc.so is present
int probe_qualcomm_hexagon_arch() {
    static int cached_arch = -2;
    if (cached_arch != -2) {
        return cached_arch;
    }

    void * handle = dlopen("libcdsprpc.so", RTLD_LAZY | RTLD_LOCAL);
    if (!handle) {
        cached_arch = 0;
        return 0;
    }

    using remote_handle_control_fn = int (*)(uint32_t req, void * data, uint32_t datalen);
    auto fn = reinterpret_cast<remote_handle_control_fn>(dlsym(handle, "remote_handle_control"));
    if (!fn) {
        dlclose(handle);
        cached_arch = 0;
        return 0;
    }

    struct remote_dsp_capability {
        uint32_t domain;
        uint32_t attribute_ID;
        uint32_t capability;
    };

    // DSPRPC_GET_DSP_INFO = 5, ARCH_VER = 2 (or 0x102 / standard FastRPC attribute 2, CDSP_DOMAIN_ID = 3)
    constexpr uint32_t kDsprpcGetDspInfo = 5;
    constexpr uint32_t kCdspDomain = 3;
    constexpr uint32_t kArchVer = 2;

    remote_dsp_capability cap{ kCdspDomain, kArchVer, 0 };
    int rc = fn(kDsprpcGetDspInfo, &cap, sizeof(cap));
    dlclose(handle);

    if (rc == 0 && (cap.capability & 0xff) != 0) {
        uint32_t v = cap.capability & 0xff;
        cached_arch = (int) ((v >> 4) * 10 + (v & 0x0f));
    } else {
        cached_arch = -1; // libcdsprpc.so present, arch auto-detected inside ggml-hexagon
    }
    return cached_arch;
}

void apply_runtime_env(
    const std::string & power_mode,
    bool enable_hmx,
    bool low_mem
) {
    if (!power_mode.empty()) {
        setenv("GGML_HEXAGON_POWER_MODE", power_mode.c_str(), 1);
        setenv("QNN_HTP_PERF_MODE", power_mode.c_str(), 1);
    }
    setenv("GGML_HEXAGON_NHMX", enable_hmx ? "1" : "0", 1);
    setenv("QWEN3_TTS_LOW_MEM", low_mem ? "1" : "0", 1);
    setenv("QNN_HTP_ENABLE_PDR", "1", 1);
    setenv("FASTRPC_PERF_KERNEL", "1", 1);
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeInitRuntime(
    JNIEnv * env,
    jobject /* thiz */,
    jstring j_native_lib_dir,
    jstring j_power_mode,
    jboolean enable_hmx,
    jboolean low_mem,
    jint cpu_threads
) {
    const std::string native_lib_dir = jstring_to_utf8(env, j_native_lib_dir);
    const std::string power_mode = jstring_to_utf8(env, j_power_mode);

    apply_runtime_env(power_mode, enable_hmx == JNI_TRUE, low_mem == JNI_TRUE);
    qwen3_tts::set_cpu_thread_count((int32_t) cpu_threads, cpu_threads > 0);
    qwen3_tts::ensure_backends_registered(native_lib_dir);
}

JNIEXPORT jstring JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeGetHardwareStatusJson(
    JNIEnv * env,
    jobject /* thiz */
) {
    const auto st = qwen3_tts::query_hardware_backend_status();
    const int hex_arch = probe_qualcomm_hexagon_arch();

    std::ostringstream oss;
    oss << "{"
        << "\"cpuAvailable\":" << (st.cpu_available ? "true" : "false") << ","
        << "\"gpuAvailable\":" << (st.gpu_available ? "true" : "false") << ","
        << "\"npuAvailable\":" << (st.npu_available ? "true" : "false") << ","
        << "\"cpuDesc\":\"" << json_escape(st.cpu_description) << "\","
        << "\"gpuDesc\":\"" << json_escape(st.gpu_description) << "\","
        << "\"npuDesc\":\"" << json_escape(st.npu_description) << "\","
        << "\"hexagonArch\":" << hex_arch << ","
        << "\"activeBackend\":\"" << json_escape(qwen3_tts::get_active_backend_name()) << "\","
        << "\"fallbackActive\":" << (st.fallback_active ? "true" : "false") << ","
        << "\"cpuThreads\":" << qwen3_tts::get_cpu_thread_count()
        << "}";

    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeConfigureBackend(
    JNIEnv * env,
    jobject /* thiz */,
    jint preference,
    jint cpu_threads,
    jstring j_power_mode,
    jboolean enable_hmx,
    jboolean low_mem
) {
    const std::string power_mode = jstring_to_utf8(env, j_power_mode);
    apply_runtime_env(power_mode, enable_hmx == JNI_TRUE, low_mem == JNI_TRUE);
    qwen3_tts::set_cpu_thread_count((int32_t) cpu_threads, cpu_threads > 0);

    qwen3_tts::backend_preference pref = qwen3_tts::backend_preference::auto_select;
    switch (preference) {
        case 1: pref = qwen3_tts::backend_preference::cpu; break;
        case 2: pref = qwen3_tts::backend_preference::cuda; break; // GPU (Adreno OpenCL / Vulkan)
        case 3: pref = qwen3_tts::backend_preference::npu; break;  // Qualcomm Hexagon HTP NPU
        case 0:
        default:
            pref = qwen3_tts::backend_preference::auto_select;
            break;
    }
    return qwen3_tts::set_backend_preference(pref) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeCreateContext(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    auto * wrapper = new EngineWrapper();
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeDestroyContext(
    JNIEnv * /* env */,
    jobject /* thiz */,
    jlong ctx_ptr
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    delete wrapper;
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeLoadModel(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jstring j_model_dir,
    jstring j_talker_path,
    jstring j_tokenizer_path
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return JNI_FALSE;

    const std::string model_dir = jstring_to_utf8(env, j_model_dir);
    const std::string talker_path = jstring_to_utf8(env, j_talker_path);
    const std::string tokenizer_path = jstring_to_utf8(env, j_tokenizer_path);

    if (!wrapper->tts.load_models(model_dir, talker_path, tokenizer_path)) {
        wrapper->last_error = wrapper->tts.get_error();
        JNI_LOGE("load_models failed: %s", wrapper->last_error.c_str());
        return JNI_FALSE;
    }
    wrapper->last_error.clear();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeLoadSpeakerEncoderOnly(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jstring j_model_dir,
    jstring j_talker_path
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return JNI_FALSE;

    const std::string model_dir = jstring_to_utf8(env, j_model_dir);
    const std::string talker_path = jstring_to_utf8(env, j_talker_path);

    if (!wrapper->tts.load_speaker_encoder_only(model_dir, talker_path)) {
        wrapper->last_error = wrapper->tts.get_error();
        return JNI_FALSE;
    }
    wrapper->last_error.clear();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeLoadIclEncoder(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jstring j_model_dir,
    jstring j_talker_path,
    jstring j_tokenizer_path
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return JNI_FALSE;

    const std::string model_dir = jstring_to_utf8(env, j_model_dir);
    const std::string talker_path = jstring_to_utf8(env, j_talker_path);
    const std::string tokenizer_path = jstring_to_utf8(env, j_tokenizer_path);

    if (!wrapper->tts.load_icl_prompt_encoder_only(model_dir, talker_path, tokenizer_path)) {
        wrapper->last_error = wrapper->tts.get_error();
        return JNI_FALSE;
    }
    wrapper->last_error.clear();
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeGetAvailableSpeakers(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    jclass str_cls = env->FindClass("java/lang/String");
    if (!wrapper || !str_cls) {
        return env->NewObjectArray(0, str_cls, nullptr);
    }

    std::vector<std::string> speakers = wrapper->tts.get_available_speakers();
    jobjectArray arr = env->NewObjectArray((jsize) speakers.size(), str_cls, nullptr);
    for (size_t i = 0; i < speakers.size(); ++i) {
        jstring js = env->NewStringUTF(speakers[i].c_str());
        env->SetObjectArrayElement(arr, (jsize) i, js);
        env->DeleteLocalRef(js);
    }
    return arr;
}

JNIEXPORT jstring JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeGetLastError(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return env->NewStringUTF("Engine context is null");
    const std::string err = wrapper->last_error.empty() ? wrapper->tts.get_error() : wrapper->last_error;
    return env->NewStringUTF(err.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeGetActiveBackendName(
    JNIEnv * env,
    jobject /* thiz */
) {
    const std::string name = qwen3_tts::get_active_backend_name();
    return env->NewStringUTF(name.c_str());
}

JNIEXPORT void JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeCancelSynthesis(
    JNIEnv * /* env */,
    jobject /* thiz */
) {
    qwen3_tts::set_synthesis_cancelled(true);
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeExtractSpeakerEmbedding(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jstring j_ref_wav_path,
    jstring j_out_bin_path
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return JNI_FALSE;

    const std::string ref_wav = jstring_to_utf8(env, j_ref_wav_path);
    const std::string out_bin = jstring_to_utf8(env, j_out_bin_path);

    std::vector<float> embedding;
    int64_t encode_ms = 0;
    if (!wrapper->tts.extract_speaker_embedding(ref_wav, embedding, &encode_ms)) {
        wrapper->last_error = wrapper->tts.get_error();
        return JNI_FALSE;
    }
    if (!qwen3_tts::save_speaker_embedding_file(out_bin, embedding)) {
        wrapper->last_error = "Failed to save speaker embedding file: " + out_bin;
        return JNI_FALSE;
    }
    wrapper->last_error.clear();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeExtractIclPrompt(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jstring j_ref_wav_path,
    jstring j_ref_text,
    jstring j_out_json_path
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return JNI_FALSE;

    const std::string ref_wav = jstring_to_utf8(env, j_ref_wav_path);
    const std::string ref_text = jstring_to_utf8(env, j_ref_text);
    const std::string out_json = jstring_to_utf8(env, j_out_json_path);

    qwen3_tts::icl_prompt prompt;
    int64_t encode_ms = 0;
    if (!wrapper->tts.extract_icl_prompt(ref_wav, ref_text, prompt, &encode_ms)) {
        wrapper->last_error = wrapper->tts.get_error();
        return JNI_FALSE;
    }
    if (!qwen3_tts::save_icl_prompt_file(out_json, prompt)) {
        wrapper->last_error = "Failed to save Full-ICL profile file: " + out_json;
        return JNI_FALSE;
    }
    wrapper->last_error.clear();
    return JNI_TRUE;
}

JNIEXPORT jfloatArray JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativeSynthesizeChunk(
    JNIEnv * env,
    jobject /* thiz */,
    jlong ctx_ptr,
    jint synth_mode,
    jstring j_text,
    jstring j_ref_path,
    jstring j_ref_text,
    jstring j_speaker,
    jstring j_instruction,
    jint language_id,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repetition_penalty,
    jint max_tokens,
    jboolean streaming,
    jfloat stream_chunk_sec,
    jfloat stream_speed,
    jobject callback_obj,
    jlongArray stats_out
) {
    auto * wrapper = reinterpret_cast<EngineWrapper *>(ctx_ptr);
    if (!wrapper) return nullptr;

    qwen3_tts::set_synthesis_cancelled(false);

    const std::string text = jstring_to_utf8(env, j_text);
    const std::string ref_path = jstring_to_utf8(env, j_ref_path);
    const std::string ref_text = jstring_to_utf8(env, j_ref_text);
    const std::string speaker = jstring_to_utf8(env, j_speaker);
    const std::string instruction = jstring_to_utf8(env, j_instruction);

    qwen3_tts::tts_params params;
    params.max_audio_tokens = (int32_t) max_tokens;
    params.temperature = (float) temperature;
    params.top_p = (float) top_p;
    params.top_k = (int32_t) top_k;
    params.n_threads = qwen3_tts::get_cpu_thread_count();
    params.print_progress = false;
    params.print_timing = false;
    params.repetition_penalty = (float) repetition_penalty;
    params.language_id = (int32_t) language_id;
    params.instruction = instruction;
    params.speaker = speaker;
    if (!ref_text.empty()) {
        params.reference_text = ref_text;
    }

    jmethodID mid_on_progress = nullptr;
    jmethodID mid_on_chunk = nullptr;
    if (callback_obj != nullptr) {
        jclass cb_cls = env->GetObjectClass(callback_obj);
        if (cb_cls) {
            mid_on_progress = env->GetMethodID(cb_cls, "onProgress", "(II)V");
            mid_on_chunk = env->GetMethodID(cb_cls, "onAudioChunk", "([FI)Z");
            env->DeleteLocalRef(cb_cls);
        }
    }

    if (mid_on_progress) {
        wrapper->tts.set_progress_callback([env, callback_obj, mid_on_progress](int gen, int max_tok) {
            if ((gen == 1 || gen % 3 == 0) && !env->ExceptionCheck()) {
                env->CallVoidMethod(callback_obj, mid_on_progress, (jint) gen, (jint) max_tok);
            }
        });
    } else {
        wrapper->tts.set_progress_callback(nullptr);
    }

    qwen3_tts::tts_result result;
    const bool use_streaming = (streaming == JNI_TRUE && mid_on_chunk != nullptr);

    qwen3_tts::tts_streaming_params stream_params;
    stream_params.generation = params;
    stream_params.chunk_sec = std::clamp((float) stream_chunk_sec, 0.25f, 3.0f);
    stream_params.left_context_sec = 2.0f;
    stream_params.collect_audio = true;

    voicemobile::StreamingSpeedStretcher stream_stretcher(24000, (float) stream_speed);
    int last_stream_sr = 24000;

    auto emit_pcm_to_java = [&](const float * pcm_ptr, size_t pcm_len, int sr) -> bool {
        if (!pcm_ptr || pcm_len == 0) {
            return true;
        }
        jfloatArray jpcm = env->NewFloatArray((jsize) pcm_len);
        if (!jpcm) return false;
        env->SetFloatArrayRegion(jpcm, 0, (jsize) pcm_len, pcm_ptr);
        jboolean keep_going = env->CallBooleanMethod(
            callback_obj,
            mid_on_chunk,
            jpcm,
            (jint) sr
        );
        env->DeleteLocalRef(jpcm);
        if (env->ExceptionCheck()) {
            return false;
        }
        return keep_going == JNI_TRUE && !qwen3_tts::is_synthesis_cancelled();
    };

    auto on_stream_chunk = [&](const qwen3_tts::tts_audio_chunk & chunk) -> bool {
        if (qwen3_tts::is_synthesis_cancelled() || env->ExceptionCheck()) {
            return false;
        }
        if (!chunk.samples || chunk.n_samples <= 0) {
            return true;
        }

        const int sr = chunk.sample_rate > 0 ? chunk.sample_rate : 24000;
        last_stream_sr = sr;
        stream_stretcher.ensure_sample_rate(sr);

        std::vector<float> processed;
        stream_stretcher.process(chunk.samples, (size_t) chunk.n_samples, processed);
        if (processed.empty()) {
            return true;
        }
        return emit_pcm_to_java(processed.data(), processed.size(), sr);
    };

    // synth_mode:
    //   0 = Plain (Voice Design / CustomVoice Speakers)
    //   1 = Reference WAV Audio (Fast Zero-Shot or Auto Full-ICL when ref_text is provided)
    //   2 = Cached Speaker Embedding (.bin)
    //   3 = Cached Full-ICL Voice Profile (.json)
    if (synth_mode == 0) {
        result = use_streaming
            ? wrapper->tts.synthesize_streaming(text, on_stream_chunk, stream_params)
            : wrapper->tts.synthesize(text, params);
    } else if (synth_mode == 1) {
        result = use_streaming
            ? wrapper->tts.synthesize_with_voice_streaming(text, ref_path, on_stream_chunk, stream_params)
            : wrapper->tts.synthesize_with_voice(text, ref_path, params);
    } else if (synth_mode == 2) {
        std::vector<float> embedding;
        if (!qwen3_tts::load_speaker_embedding_file(ref_path, embedding)) {
            wrapper->last_error = "Failed to load speaker embedding profile: " + ref_path;
            wrapper->tts.set_progress_callback(nullptr);
            return nullptr;
        }
        result = use_streaming
            ? wrapper->tts.synthesize_with_speaker_embedding_streaming(text, embedding, on_stream_chunk, stream_params)
            : wrapper->tts.synthesize_with_speaker_embedding(text, embedding, params);
    } else if (synth_mode == 3) {
        qwen3_tts::icl_prompt prompt;
        if (qwen3_tts::load_icl_prompt_file(ref_path, prompt)) {
            params.reference_text = prompt.reference_text;
            params.reference_token_ids = prompt.reference_token_ids;
            params.reference_codes = prompt.reference_codes;
            stream_params.generation = params;
            result = use_streaming
                ? wrapper->tts.synthesize_with_speaker_embedding_streaming(
                      text, prompt.speaker_embedding, on_stream_chunk, stream_params)
                : wrapper->tts.synthesize_with_speaker_embedding(
                      text, prompt.speaker_embedding, params);
        } else {
            // Also support speaker-embedding-only .json profiles exported by desktop voicecli
            std::vector<float> embedding;
            if (!qwen3_tts::load_speaker_embedding_file(ref_path, embedding)) {
                wrapper->last_error = "Failed to load voice profile: " + ref_path;
                wrapper->tts.set_progress_callback(nullptr);
                return nullptr;
            }
            result = use_streaming
                ? wrapper->tts.synthesize_with_speaker_embedding_streaming(
                      text, embedding, on_stream_chunk, stream_params)
                : wrapper->tts.synthesize_with_speaker_embedding(
                      text, embedding, params);
        }
    } else {
        wrapper->last_error = "Unsupported synthesis mode";
        wrapper->tts.set_progress_callback(nullptr);
        return nullptr;
    }

    wrapper->tts.set_progress_callback(nullptr);

    if (use_streaming && result.success && !qwen3_tts::is_synthesis_cancelled() && !env->ExceptionCheck()) {
        std::vector<float> tail_pcm;
        stream_stretcher.flush(tail_pcm);
        if (!tail_pcm.empty()) {
            emit_pcm_to_java(tail_pcm.data(), tail_pcm.size(), last_stream_sr);
        }
    }

    if (stats_out && env->GetArrayLength(stats_out) >= 4) {
        jlong buf[4] = {
            (jlong) (result.sample_rate > 0 ? result.sample_rate : 24000),
            (jlong) result.t_total_ms,
            (jlong) result.t_ttfa_ms,
            (jlong) (result.success ? 1 : 0)
        };
        env->SetLongArrayRegion(stats_out, 0, 4, buf);
    }

    if (!result.success || result.audio.empty()) {
        wrapper->last_error = result.error_msg.empty() ? wrapper->tts.get_error() : result.error_msg;
        return nullptr;
    }

    wrapper->last_error.clear();
    jfloatArray out_arr = env->NewFloatArray((jsize) result.audio.size());
    if (!out_arr) {
        wrapper->last_error = "Out of memory allocating audio array";
        return nullptr;
    }
    env->SetFloatArrayRegion(out_arr, 0, (jsize) result.audio.size(), result.audio.data());
    return out_arr;
}

JNIEXPORT jfloatArray JNICALL
Java_io_github_ultrapg_voicemobile_engine_VoiceEngineBridge_nativePostProcessAndSaveWav(
    JNIEnv * env,
    jobject /* thiz */,
    jobjectArray j_chunks,
    jint sample_rate,
    jfloat speed,
    jboolean normalize,
    jboolean multi_chunk_stitch,
    jstring j_output_wav_path
) {
    if (!j_chunks) return nullptr;
    const jsize num_chunks = env->GetArrayLength(j_chunks);
    if (num_chunks <= 0) return nullptr;

    const int sr = sample_rate > 0 ? (int) sample_rate : 24000;
    const size_t pause_samples = (size_t) ((sr * 150) / 1000); // 150ms natural breathing pause
    std::vector<float> master;

    for (jsize idx = 0; idx < num_chunks; ++idx) {
        auto j_chunk = reinterpret_cast<jfloatArray>(env->GetObjectArrayElement(j_chunks, idx));
        if (!j_chunk) continue;
        const jsize len = env->GetArrayLength(j_chunk);
        if (len <= 0) {
            env->DeleteLocalRef(j_chunk);
            continue;
        }
        std::vector<float> buf((size_t) len);
        env->GetFloatArrayRegion(j_chunk, 0, len, buf.data());
        env->DeleteLocalRef(j_chunk);

        if (num_chunks > 1 && multi_chunk_stitch == JNI_TRUE) {
            voicemobile::trim_and_fade_chunk(buf, sr);
            if (!master.empty() && !buf.empty()) {
                master.insert(master.end(), pause_samples, 0.0f);
            }
        }
        master.insert(master.end(), buf.begin(), buf.end());
    }

    if (master.empty()) {
        return nullptr;
    }

    // Apply pitch-preserving SOLA time-stretching if speed != 1.0x
    if (std::fabs((float) speed - 1.0f) >= 0.005f) {
        master = voicemobile::sola_time_stretch(master.data(), master.size(), sr, (float) speed);
    }

    // Apply DC removal + -0.5 dBFS peak normalization if enabled
    if (normalize == JNI_TRUE) {
        voicemobile::normalize_audio_peak(master, 0.944f);
    }

    const std::string out_path = jstring_to_utf8(env, j_output_wav_path);
    if (!voicemobile::write_wav_pcm16(out_path, master.data(), master.size(), sr)) {
        return nullptr;
    }

    // Compute [durationSeconds, bar0..bar95] envelope for minimalist Material 3 waveform player
    constexpr int kBars = 96;
    std::vector<float> summary(1 + kBars, 0.0f);
    summary[0] = (float) ((double) master.size() / (double) sr);

    const size_t total = master.size();
    float max_bar = 1e-4f;
    for (int b = 0; b < kBars; ++b) {
        const size_t s0 = (total * (size_t) b) / (size_t) kBars;
        const size_t s1 = std::max(s0 + 1, (total * (size_t) (b + 1)) / (size_t) kBars);
        double sq = 0.0;
        size_t count = 0;
        for (size_t i = s0; i < s1 && i < total; ++i) {
            const double v = (double) master[i];
            sq += v * v;
            count++;
        }
        const float rms = count > 0 ? (float) std::sqrt(sq / (double) count) : 0.0f;
        summary[1 + b] = rms;
        if (rms > max_bar) max_bar = rms;
    }
    for (int b = 0; b < kBars; ++b) {
        summary[1 + b] = std::clamp(summary[1 + b] / max_bar, 0.06f, 1.0f);
    }

    jfloatArray out_summary = env->NewFloatArray((jsize) summary.size());
    if (!out_summary) return nullptr;
    env->SetFloatArrayRegion(out_summary, 0, (jsize) summary.size(), summary.data());
    return out_summary;
}

} // extern "C"

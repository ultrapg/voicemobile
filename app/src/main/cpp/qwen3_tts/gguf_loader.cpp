#include "gguf_loader.h"
#include "ggml-cpu.h"

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <mutex>
#include <thread>

#ifdef __ANDROID__
#include <android/log.h>
#define VM_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VoiceMobileNative", __VA_ARGS__)
#define VM_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "VoiceMobileNative", __VA_ARGS__)
#else
#define VM_LOGI(...) fprintf(stderr, __VA_ARGS__)
#define VM_LOGW(...) fprintf(stderr, __VA_ARGS__)
#endif

namespace qwen3_tts {

namespace {

std::atomic<bool> g_synthesis_cancelled{false};

struct shared_backend_state {
    std::mutex mutex;
    bool backends_probed = false;
    ggml_backend_reg_t cpu_reg = nullptr;
    std::string cpu_desc = "ARM64 NEON / FP16 / DotProd CPU";

    ggml_backend_t backend = nullptr;
    int32_t ref_count = 0;
    backend_preference preference = backend_preference::cpu;
    int32_t cpu_n_threads = 0;
    bool cpu_n_threads_explicit = false;
    std::string active_backend_name;
    std::string active_backend_desc;
};

shared_backend_state & get_shared_backend_state() {
    static shared_backend_state state;
    return state;
}

int32_t sanitize_thread_count(int32_t n_threads) {
    return n_threads > 0 ? n_threads : default_cpu_thread_count();
}

void configure_cpu_backend_threads(ggml_backend_t backend) {
    if (!backend) {
        return;
    }

    ggml_backend_dev_t device = ggml_backend_get_device(backend);
    if (!device || ggml_backend_dev_type(device) != GGML_BACKEND_DEVICE_TYPE_CPU) {
        return;
    }

    ggml_backend_reg_t reg = ggml_backend_dev_backend_reg(device);
    if (!reg) {
        return;
    }

    auto set_fn =
        (ggml_backend_set_n_threads_t) ggml_backend_reg_get_proc_address(reg, "ggml_backend_set_n_threads");
    if (set_fn) {
        set_fn(backend, get_cpu_thread_count());
    }
}

std::string describe_reg_first_device(ggml_backend_reg_t reg) {
    if (!reg || ggml_backend_reg_dev_count(reg) == 0) {
        return "";
    }
    ggml_backend_dev_t dev = ggml_backend_reg_dev_get(reg, 0);
    if (!dev) {
        return "";
    }
    const char * name = ggml_backend_dev_name(dev);
    const char * desc = ggml_backend_dev_description(dev);
    if (name && desc && strlen(desc) > 0 && strcmp(name, desc) != 0) {
        return std::string(name) + " (" + desc + ")";
    }
    return name ? name : (desc ? desc : "");
}

ggml_backend_t init_backend_from_reg(ggml_backend_reg_t reg) {
    if (!reg) {
        return nullptr;
    }
    const size_t count = ggml_backend_reg_dev_count(reg);
    for (size_t i = 0; i < count; ++i) {
        ggml_backend_dev_t dev = ggml_backend_reg_dev_get(reg, i);
        if (!dev) continue;
        ggml_backend_t backend = ggml_backend_dev_init(dev, nullptr);
        if (backend) {
            if (ggml_backend_dev_type(dev) == GGML_BACKEND_DEVICE_TYPE_CPU) {
                configure_cpu_backend_threads(backend);
            }
            return backend;
        }
    }
    return nullptr;
}

std::string format_backend_label(ggml_backend_t backend) {
    if (!backend) {
        return "";
    }
    return "ARM64 CPU (" + std::to_string(get_cpu_thread_count()) + " threads)";
}

} // namespace

void set_synthesis_cancelled(bool cancelled) {
    g_synthesis_cancelled.store(cancelled, std::memory_order_relaxed);
}

bool is_synthesis_cancelled() {
    return g_synthesis_cancelled.load(std::memory_order_relaxed);
}

void ensure_backends_registered() {
    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);

    if (shared.backends_probed) {
        return;
    }

    shared.cpu_reg = ggml_backend_cpu_reg();
    if (shared.cpu_reg) {
        std::string d = describe_reg_first_device(shared.cpu_reg);
        if (!d.empty()) shared.cpu_desc = d;
    }

    shared.backends_probed = true;
}

hardware_backend_status query_hardware_backend_status() {
    ensure_backends_registered();
    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);

    hardware_backend_status st;
    st.cpu_available = true;
    st.cpu_description = shared.cpu_desc;
    st.active_backend_name = shared.active_backend_name;
    st.active_backend_description = shared.active_backend_desc;
    return st;
}

GGUFLoader::GGUFLoader() = default;

GGUFLoader::~GGUFLoader() {
    close();
}

ggml_backend_t init_preferred_backend(const char * component_name, std::string * error_msg) {
    if (error_msg) error_msg->clear();
    ensure_backends_registered();

    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);
    if (shared.backend) {
        shared.ref_count++;
        return shared.backend;
    }

    ggml_backend_t backend = init_backend_from_reg(shared.cpu_reg);

    if (!backend && error_msg) {
        const char * name = component_name ? component_name : "component";
        *error_msg = "Failed to initialize ARM64 CPU backend for " + std::string(name);
    }

    if (backend) {
        shared.backend = backend;
        shared.ref_count = 1;
        shared.active_backend_name = "CPU";
        shared.active_backend_desc = format_backend_label(backend);
        VM_LOGI("Active VoiceMobile backend [%s]: %s",
                component_name ? component_name : "engine",
                shared.active_backend_desc.c_str());
    }

    return backend;
}

ggml_backend_t init_dedicated_preferred_backend(const char * component_name, std::string * error_msg) {
    return init_cpu_backend(component_name, error_msg);
}

ggml_backend_t init_cpu_backend(const char * component_name, std::string * error_msg) {
    if (error_msg) error_msg->clear();
    ensure_backends_registered();

    auto & shared = get_shared_backend_state();
    ggml_backend_reg_t cpu_reg = nullptr;
    {
        std::lock_guard<std::mutex> lock(shared.mutex);
        cpu_reg = shared.cpu_reg;
    }
    ggml_backend_t backend = init_backend_from_reg(cpu_reg);
    if (!backend && error_msg) {
        const char * name = component_name ? component_name : "component";
        *error_msg = "Failed to initialize CPU backend for " + std::string(name);
    }
    return backend;
}

void release_preferred_backend(ggml_backend_t backend) {
    if (!backend) {
        return;
    }

    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);
    if (shared.backend == backend) {
        shared.ref_count--;
        if (shared.ref_count <= 0) {
            ggml_backend_free(shared.backend);
            shared.backend = nullptr;
            shared.ref_count = 0;
            shared.active_backend_name.clear();
            shared.active_backend_desc.clear();
        }
        return;
    }

    ggml_backend_free(backend);
}

bool set_backend_preference(backend_preference preference) {
    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);
    shared.preference = (preference == backend_preference::auto_select)
        ? backend_preference::cpu
        : preference;
    return true;
}

int32_t default_cpu_thread_count() {
    int32_t hw = (int32_t) std::thread::hardware_concurrency();
    if (hw <= 2) return hw > 0 ? hw : 1;
    // On modern Android ARM64 octa-core SoCs, 4-6 performance threads outperform
    // saturating efficiency cores with barrier contention.
    int32_t target = (hw >= 8) ? 6 : (hw / 2 + 1);
    return target > 0 ? target : 1;
}

int32_t default_parallel_thread_count() {
    int32_t n_threads = (int32_t) std::thread::hardware_concurrency();
    return n_threads > 0 ? n_threads : default_cpu_thread_count();
}

bool set_cpu_thread_count(int32_t n_threads, bool explicit_value) {
    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);
    shared.cpu_n_threads = sanitize_thread_count(n_threads);
    shared.cpu_n_threads_explicit = explicit_value;
    if (shared.backend) {
        configure_cpu_backend_threads(shared.backend);
        shared.active_backend_desc = format_backend_label(shared.backend);
    }
    return true;
}

int32_t get_cpu_thread_count() {
    auto & shared = get_shared_backend_state();
    if (shared.cpu_n_threads <= 0) {
        shared.cpu_n_threads = default_cpu_thread_count();
    }
    return shared.cpu_n_threads;
}

bool cpu_thread_count_is_explicit() {
    return get_shared_backend_state().cpu_n_threads_explicit;
}

backend_preference get_backend_preference() {
    return backend_preference::cpu;
}

enum ggml_backend_dev_type get_preferred_backend_type() {
    return GGML_BACKEND_DEVICE_TYPE_CPU;
}

int32_t get_compiled_backend_mask() {
    return 1; // ARM64 CPU only
}

std::string get_active_backend_name() {
    auto & shared = get_shared_backend_state();
    std::lock_guard<std::mutex> lock(shared.mutex);
    if (!shared.active_backend_desc.empty()) {
        return shared.active_backend_desc;
    }
    return "ARM64 CPU (" + std::to_string(get_cpu_thread_count()) + " threads)";
}

bool GGUFLoader::open(const std::string & path) {
    close();
    file_path_ = path;

    struct gguf_init_params params = {
        /*.no_alloc =*/ true,
        /*.ctx      =*/ &meta_ctx_,
    };

    ctx_ = gguf_init_from_file(path.c_str(), params);
    if (!ctx_) {
        error_msg_ = "Failed to open GGUF file: " + path;
        return false;
    }
    return true;
}

void GGUFLoader::close() {
    if (ctx_) {
        gguf_free(ctx_);
        ctx_ = nullptr;
    }
    if (meta_ctx_) {
        ggml_free(meta_ctx_);
        meta_ctx_ = nullptr;
    }
    file_path_.clear();
}

int64_t GGUFLoader::get_n_tensors() const {
    if (!ctx_) return 0;
    return gguf_get_n_tensors(ctx_);
}

const char * GGUFLoader::get_tensor_name(int64_t idx) const {
    if (!ctx_) return nullptr;
    return gguf_get_tensor_name(ctx_, idx);
}

enum ggml_type GGUFLoader::get_tensor_type(int64_t idx) const {
    if (!ctx_) return GGML_TYPE_F32;
    return gguf_get_tensor_type(ctx_, idx);
}

size_t GGUFLoader::get_tensor_offset(int64_t idx) const {
    if (!ctx_) return 0;
    return gguf_get_tensor_offset(ctx_, idx);
}

size_t GGUFLoader::get_tensor_size(int64_t idx) const {
    if (!ctx_) return 0;
    return gguf_get_tensor_size(ctx_, idx);
}

int32_t GGUFLoader::get_u32(const char * key, int32_t default_val) const {
    if (!ctx_) return default_val;
    int64_t idx = gguf_find_key(ctx_, key);
    if (idx < 0) return default_val;
    return (int32_t) gguf_get_val_u32(ctx_, idx);
}

float GGUFLoader::get_f32(const char * key, float default_val) const {
    if (!ctx_) return default_val;
    int64_t idx = gguf_find_key(ctx_, key);
    if (idx < 0) return default_val;
    return gguf_get_val_f32(ctx_, idx);
}

size_t GGUFLoader::get_data_offset() const {
    if (!ctx_) return 0;
    return gguf_get_data_offset(ctx_);
}

bool load_tensor_data_from_file(
    const std::string & path,
    struct gguf_context * ctx,
    struct ggml_context * model_ctx,
    const std::map<std::string, struct ggml_tensor *> & tensors,
    ggml_backend_buffer_t & buffer,
    std::string & error_msg,
    enum ggml_backend_dev_type preferred_backend_type
) {
    const bool use_shared_preferred = (preferred_backend_type != GGML_BACKEND_DEVICE_TYPE_CPU);
    ggml_backend_t backend = use_shared_preferred
        ? init_preferred_backend("GGUFTensorLoader", &error_msg)
        : init_cpu_backend("GGUFTensorLoaderCPU", &error_msg);

    if (!backend) {
        if (error_msg.empty()) {
            error_msg = "Failed to initialize backend for GGUF tensor loader";
        }
        return false;
    }

    auto release_loader_backend = [&]() {
        if (use_shared_preferred) {
            release_preferred_backend(backend);
        } else {
            ggml_backend_free(backend);
        }
    };

    buffer = ggml_backend_alloc_ctx_tensors(model_ctx, backend);
    if (!buffer) {
        error_msg = "Failed to allocate CPU tensor buffer";
        release_loader_backend();
        return false;
    }

    ggml_backend_buffer_set_usage(buffer, GGML_BACKEND_BUFFER_USAGE_WEIGHTS);

    FILE * f = fopen(path.c_str(), "rb");
    if (!f) {
        error_msg = "Failed to open file for reading: " + path;
        if (backend) release_loader_backend();
        return false;
    }

    const size_t data_offset = gguf_get_data_offset(ctx);
    const int64_t n_tensors = gguf_get_n_tensors(ctx);
    std::vector<uint8_t> read_buf;

    for (int64_t i = 0; i < n_tensors; ++i) {
        const char * name = gguf_get_tensor_name(ctx, i);
        size_t offset = gguf_get_tensor_offset(ctx, i);

        auto it = tensors.find(name);
        if (it == tensors.end()) {
            continue;
        }

        struct ggml_tensor * tensor = it->second;
        const enum ggml_type source_type = gguf_get_tensor_type(ctx, i);
        const size_t source_nbytes = gguf_get_tensor_size(ctx, i);
        const size_t target_nbytes = ggml_nbytes(tensor);

        read_buf.resize(source_nbytes);

        if (fseek(f, data_offset + offset, SEEK_SET) != 0) {
            error_msg = "Failed to seek to tensor data: " + std::string(name);
            fclose(f);
            if (backend) release_loader_backend();
            return false;
        }

        if (fread(read_buf.data(), 1, source_nbytes, f) != source_nbytes) {
            error_msg = "Failed to read tensor data: " + std::string(name);
            fclose(f);
            if (backend) release_loader_backend();
            return false;
        }

        if (source_type == tensor->type) {
            if (source_nbytes != target_nbytes) {
                error_msg = "Tensor byte size mismatch: " + std::string(name);
                fclose(f);
                if (backend) release_loader_backend();
                return false;
            }
            ggml_backend_tensor_set(tensor, read_buf.data(), 0, target_nbytes);
        } else if (tensor->type == GGML_TYPE_F16 &&
                   (source_type == GGML_TYPE_F32 || source_type == GGML_TYPE_BF16)) {
            const int64_t n = ggml_nelements(tensor);
            std::vector<ggml_fp16_t> converted((size_t) n);
            if (source_type == GGML_TYPE_F32) {
                ggml_fp32_to_fp16_row(reinterpret_cast<const float *>(read_buf.data()),
                                      converted.data(), n);
            } else {
                const ggml_bf16_t * source = reinterpret_cast<const ggml_bf16_t *>(read_buf.data());
                for (int64_t element = 0; element < n; ++element) {
                    converted[(size_t) element] = ggml_fp32_to_fp16(ggml_bf16_to_fp32(source[element]));
                }
            }
            if (converted.size() * sizeof(ggml_fp16_t) != target_nbytes) {
                error_msg = "Converted tensor byte size mismatch: " + std::string(name);
                fclose(f);
                if (backend) release_loader_backend();
                return false;
            }
            ggml_backend_tensor_set(tensor, converted.data(), 0, target_nbytes);
        } else {
            error_msg = "Unsupported tensor conversion for " + std::string(name) + ": " +
                        ggml_type_name(source_type) + " -> " + ggml_type_name(tensor->type);
            fclose(f);
            if (backend) release_loader_backend();
            return false;
        }
    }

    fclose(f);
    if (backend) {
        release_loader_backend();
    }

    return true;
}

void free_ggml_resources(struct ggml_context * ctx, ggml_backend_buffer_t buffer) {
    if (buffer) {
        ggml_backend_buffer_free(buffer);
    }
    if (ctx) {
        ggml_free(ctx);
    }
}

} // namespace qwen3_tts

cmake_minimum_required(VERSION 3.22.1)
project(voicemobile_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(GGML_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION "${GGML_JNI_DIR}/libggml-base.so")

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION "${GGML_JNI_DIR}/libggml-cpu.so")

set(QWEN3_TTS_SOURCES
    qwen3_tts/audio_tokenizer_encoder.cpp
    qwen3_tts/common/audio_io.cpp
    qwen3_tts/common/icl_prompt_io.cpp
    qwen3_tts/common/speaker_embedding_io.cpp
    qwen3_tts/coreml_code_predictor_stub.cpp
    qwen3_tts/decoder/decoder_cache.cpp
    qwen3_tts/decoder/decoder_graph.cpp
    qwen3_tts/decoder/decoder_layers.cpp
    qwen3_tts/decoder/decoder_loader.cpp
    qwen3_tts/decoder/decoder_runtime.cpp
    qwen3_tts/encoder/encoder_frontend.cpp
    qwen3_tts/encoder/encoder_graph.cpp
    qwen3_tts/encoder/encoder_loader.cpp
    qwen3_tts/encoder/encoder_runtime.cpp
    qwen3_tts/gguf_loader.cpp
    qwen3_tts/pipeline/pipeline_models.cpp
    qwen3_tts/pipeline/pipeline_runtime.cpp
    qwen3_tts/pipeline/pipeline_synthesize.cpp
    qwen3_tts/qwen3_tts_c.cpp
    qwen3_tts/qwen3_tts.cpp
    qwen3_tts/speech_tokenizer_encoder.cpp
    qwen3_tts/text_tokenizer.cpp
    qwen3_tts/tokenizer_unicode.cpp
    qwen3_tts/tokenizer_unicode_data.cpp
    qwen3_tts/transformer/transformer_cache.cpp
    qwen3_tts/transformer/transformer_debug.cpp
    qwen3_tts/transformer/transformer_embeddings.cpp
    qwen3_tts/transformer/transformer_generate.cpp
    qwen3_tts/transformer/transformer_graph_code_pred.cpp
    qwen3_tts/transformer/transformer_graph_talker.cpp
    qwen3_tts/transformer/transformer_loader.cpp
    qwen3_tts/transformer/transformer_loader_metadata.cpp
    qwen3_tts/transformer/transformer_loader_tensors.cpp
    qwen3_tts/transformer/transformer_runtime_code_pred.cpp
    qwen3_tts/transformer/transformer_runtime.cpp
    qwen3_tts/tts_transformer.cpp
)

add_library(voicemobile_jni SHARED
    ${QWEN3_TTS_SOURCES}
    audio_dsp.cpp
    voicemobile_jni.cpp
)

target_include_directories(voicemobile_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/qwen3_tts
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml_include
)

target_compile_options(voicemobile_jni PRIVATE
    -O3
    -DNDEBUG
    -Wno-unused-function
    -Wno-unused-variable
)

if (ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(voicemobile_jni PRIVATE -march=armv8.2-a+fp16+dotprod)
endif()

# Enforce 16 KB ELF page-size alignment for Android 15-17 (API 35-37)
target_link_options(voicemobile_jni PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

find_library(ANDROID_LOG_LIB log)

target_link_libraries(voicemobile_jni PRIVATE
    ggml-base
    ggml-cpu
    ${ANDROID_LOG_LIB}
    dl
    m
)

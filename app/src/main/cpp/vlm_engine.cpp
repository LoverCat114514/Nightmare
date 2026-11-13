// vlm_engine.cpp — on-device VLM (Qwen3-VL) inference engine for Nightmare's
// reverse-prompt module. JNI bridge over llama.cpp (libllama + mtmd).
// Built by tools/build_vlm.sh: system clang (aarch64 host) + NDK sysroot +
// GNU ld — no cmake required (cmake is unavailable in the proot env).
//
// Memory contract: exactly one model resident; inferImage() frees the KV cache
// when done; unloadModel() frees everything. Kotlin registers trim() into
// PictureModels.releaseAll() so the DiT render pipeline can reclaim RAM.

#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "VlmEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Engine {
    llama_model * model = nullptr;
    const llama_vocab * vocab = nullptr;
    llama_context * lctx = nullptr;
    mtmd_context * mctx = nullptr;
    int n_ctx = 0;
    int n_threads = 4;
};

Engine g_eng;
std::mutex g_mtx;
bool g_loaded = false;

std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * p = env->GetStringUTFChars(s, nullptr);
    std::string out = p ? p : "";
    if (p) env->ReleaseStringUTFChars(s, p);
    return out;
}

std::string trim_ws(const std::string & s) {
    const size_t a = s.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return "";
    const size_t b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

} // namespace

extern "C" {

// 0 = ok; -1 main missing; -2 model load; -3 context; -4 mmproj
JNIEXPORT jint JNICALL
Java_com_abrah_nightmare_reverse_VlmEngine_nativeLoadModel(
        JNIEnv * env, jobject, jstring mainPath, jstring mmprojPath,
        jint nThreads, jint nCtx) {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (g_loaded) return 0;

    const std::string main = jstr(env, mainPath);
    const std::string mm   = jstr(env, mmprojPath);
    LOGI("load: %s | %s | threads=%d ctx=%d", main.c_str(), mm.c_str(), (int)nThreads, (int)nCtx);

    FILE * f = fopen(main.c_str(), "rb");
    if (!f) { LOGE("main model not readable"); return -1; }
    fclose(f);

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU only — the DiT engine owns the NPU
    g_eng.model = llama_model_load_from_file(main.c_str(), mp);
    if (!g_eng.model) { llama_backend_free(); LOGE("model load failed"); return -2; }
    g_eng.vocab = llama_model_get_vocab(g_eng.model);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx     = (uint32_t) nCtx;
    cp.n_batch   = 256;
    cp.n_ubatch  = 256;
    cp.n_threads = (uint32_t) nThreads;
    cp.n_threads_batch = (uint32_t) nThreads;
    cp.type_k = GGML_TYPE_Q8_0; // KV quantized: halves KV RAM (~144KB/token → ~72KB)
    cp.type_v = GGML_TYPE_Q8_0;
    cp.no_perf = true;
    g_eng.lctx = llama_init_from_model(g_eng.model, cp);
    if (!g_eng.lctx) {
        llama_model_free(g_eng.model); g_eng.model = nullptr;
        llama_backend_free();
        LOGE("context init failed");
        return -3;
    }

    mtmd_context_params vp = mtmd_context_params_default();
    vp.use_gpu = false;
    vp.print_timings = false;
    vp.n_threads = (int) nThreads;
    vp.warmup = false;
    g_eng.mctx = mtmd_init_from_file(mm.c_str(), g_eng.model, vp);
    if (!g_eng.mctx) {
        llama_free(g_eng.lctx); g_eng.lctx = nullptr;
        llama_model_free(g_eng.model); g_eng.model = nullptr;
        llama_backend_free();
        LOGE("mmproj load failed");
        return -4;
    }

    g_eng.n_ctx = nCtx;
    g_eng.n_threads = nThreads;
    g_loaded = true;
    LOGI("engine loaded");
    return 0;
}

// rgb = packed RGB888 (3 bytes/px), w*h. Returns generated text or "ERR:...".
JNIEXPORT jstring JNICALL
Java_com_abrah_nightmare_reverse_VlmEngine_nativeInferImage(
        JNIEnv * env, jobject,
        jbyteArray rgb, jint width, jint height,
        jstring systemPrompt, jstring userPrompt, jint maxTokens) {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (!g_loaded || !g_eng.lctx || !g_eng.mctx) {
        return env->NewStringUTF("ERR:引擎未加载");
    }
    if (width <= 0 || height <= 0) {
        return env->NewStringUTF("ERR:图像尺寸无效");
    }
    const jsize pxlen = env->GetArrayLength(rgb);
    if ((size_t) pxlen < (size_t) width * height * 3) {
        return env->NewStringUTF("ERR:像素数据不足");
    }

    auto * px = env->GetByteArrayElements(rgb, nullptr);
    mtmd_bitmap * bmp = mtmd_bitmap_init((uint32_t) width, (uint32_t) height,
                                         reinterpret_cast<const unsigned char *>(px));
    env->ReleaseByteArrayElements(rgb, px, JNI_ABORT);
    if (!bmp) return env->NewStringUTF("ERR:bitmap init failed");

    const std::string sys = jstr(env, systemPrompt);
    const std::string usr = jstr(env, userPrompt);
    const std::string marker = mtmd_default_marker();
    // Qwen chat template; media marker sits at the head of the user turn and
    // mtmd_tokenize() replaces it with the image embedding tokens.
    const std::string prompt =
        "<|im_start|>system\n" + sys + "<|im_end|>\n"
        "<|im_start|>user\n" + marker + usr + "<|im_end|>\n"
        "<|im_start|>assistant\n"
        // DeepSeek-V4-Pro-Qwen3.5 chat template, enable_thinking=false branch:
        // prefill an EMPTY think block so the model answers directly. Without
        // it this reasoning model burns the whole token budget on <think>…
        // (CPU speed makes that unusable here). Qwen3-VL-4B tolerates it as
        // ordinary whitespace tokens.
        "<think>\n\n</think>\n\n";

    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    mtmd_input_text text { prompt.c_str(), prompt.size(), /*add_special*/false, /*parse_special*/true };
    const mtmd_bitmap * bitmaps[1] = { bmp };
    const int32_t tres = mtmd_tokenize(g_eng.mctx, chunks, &text, bitmaps, 1);
    mtmd_bitmap_free(bmp);
    if (tres != 0) {
        mtmd_input_chunks_free(chunks);
        LOGE("tokenize failed: %d", (int) tres);
        return env->NewStringUTF("ERR:视觉分词失败");
    }

    llama_memory_clear(llama_get_memory(g_eng.lctx), 0);
    // Eval via llama.cpp's own helper (same path as llama-mtmd-cli): it handles
    // text sub-batching, image encode + embd decode, and logits_last placement.
    // A hand-rolled decode loop failed on-device ("文本解码失败") — defer to the
    // maintained implementation instead of second-guessing batch internals.
    llama_pos n_past = 0;
    const int32_t eres = mtmd_helper_eval_chunks(
        g_eng.mctx, g_eng.lctx, chunks,
        /*n_past*/      0,
        /*seq_id*/      0,
        /*n_batch*/     256,
        /*logits_last*/ true,
        &n_past);
    mtmd_input_chunks_free(chunks);
    if (eres != 0) {
        LOGE("helper eval failed: %d", (int) eres);
        const std::string emsg =
            "ERR:推理失败（eval 阶段，代码 " + std::to_string(eres) + "）";
        return env->NewStringUTF(emsg.c_str());
    }
    // ---- generation: temp 0.7 / top-k 20 / top-p 0.8 (Qwen recommended) ----
    // Every early-exit path carries its reason into the returned error — the
    // app surfaces it verbatim; silent breaks cost a whole debug round-trip.
    std::string out;
    std::string halt; // non-empty => abnormal stop, reported via ERR:
    const int max_new = maxTokens > 0 ? maxTokens : 512;
    const llama_token eos = llama_vocab_eos(g_eng.vocab);
    const llama_token eot = llama_vocab_eot(g_eng.vocab);
    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    llama_token cur = llama_sampler_sample(chain, g_eng.lctx, -1);
    for (int g = 0; g < max_new; ++g) {
        // is_eog covers the model's full end-of-generation set (im_end,
        // endoftext, ...); eos/eot alone can miss members of that set.
        if (llama_vocab_is_eog(g_eng.vocab, cur) || cur == eos || (eot >= 0 && cur == eot)) {
            LOGI("gen: eog at step %d (token %d)", g, (int) cur);
            break;
        }
        char piece[256];
        const int n = llama_token_to_piece(g_eng.vocab, cur, piece, (int32_t) sizeof(piece) - 1, 0, true);
        if (n <= 0) {
            halt = "生成中断：token 无法解码（token " + std::to_string((int) cur) + "，步 " + std::to_string(g) + "）";
            break;
        }
        out.append(piece, (size_t) n);
        if (out.size() > 16384) break;
        llama_batch b = llama_batch_init(1, 0, 1);
        // CRITICAL: llama_batch_init only pre-allocates; n_tokens stays 0.
        // Decoding an EMPTY batch fails outright (llama-batch.cpp rejects
        // n_tokens==0) — this was the root cause of the "one token then stop"
        // bug: the first sampled token rendered fine, then every subsequent
        // decode returned -1 and the loop broke silently.
        b.n_tokens    = 1;
        b.token[0]     = cur;
        b.pos[0]       = n_past++;
        b.n_seq_id[0]  = 1;
        b.seq_id[0][0] = 0;
        b.logits[0]    = 1;
        const int dres = llama_decode(g_eng.lctx, b);
        llama_batch_free(b);
        if (dres != 0) {
            LOGE("gen: decode failed at step %d (token %d, pos %d): %d", g, (int) cur, (int) n_past, dres);
            halt = "生成中断：解码失败（步 " + std::to_string(g) +
                   "，位置 " + std::to_string((int) n_past) + "/" + std::to_string(g_eng.n_ctx) +
                   "，代码 " + std::to_string(dres) + "）——上下文溢出或内存不足";
            break;
        }
        cur = llama_sampler_sample(chain, g_eng.lctx, -1);
    }
    llama_sampler_free(chain);
    llama_memory_clear(llama_get_memory(g_eng.lctx), 0); // KV freed immediately
    LOGI("infer done: %zu bytes", out.size());
    if (!halt.empty()) {
        // partial output preserved for diagnostics (Kotlin shows it verbatim)
        const std::string emsg = "ERR:" + halt + "\n已生成片段：" + trim_ws(out).substr(0, 200);
        return env->NewStringUTF(emsg.c_str());
    }
    return env->NewStringUTF(trim_ws(out).c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_abrah_nightmare_reverse_VlmEngine_nativeIsLoaded(JNIEnv *, jobject) {
    return g_loaded ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_abrah_nightmare_reverse_VlmEngine_nativeUnloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lk(g_mtx);
    if (g_eng.mctx)  { mtmd_free(g_eng.mctx); g_eng.mctx = nullptr; }
    if (g_eng.lctx)  { llama_free(g_eng.lctx); g_eng.lctx = nullptr; }
    if (g_eng.model) { llama_model_free(g_eng.model); g_eng.model = nullptr; }
    llama_backend_free();
    g_loaded = false;
    LOGI("engine unloaded");
}

} // extern "C"
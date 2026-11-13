package com.abrah.nightmare.reverse

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ⭐⭐⭐ Module 2 — JNI bridge to the on-device VLM engine (libvlm_engine.so:
 * llama.cpp libllama + mtmd + our vlm_engine.cpp bridge, built by
 * tools/build_vlm.sh).
 *
 * Memory contract:
 *   - at most ONE model resident (the C side enforces it with a mutex);
 *   - [inferImage] releases the KV cache before returning;
 *   - [trim] (== unload) is registered in PictureModels.releaseAll() so the
 *     DiT render pipeline can reclaim the ~4 GB of model RAM before loading
 *     its own weights.
 */
object VlmEngine {
    private const val TAG = "VlmEngine"

    /** Set once in [init]; every entry guards on it (no native calls if false). */
    @Volatile
    private var libLoaded = false

    init {
        libLoaded = try {
            System.loadLibrary("vlm_engine")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "System.loadLibrary(vlm_engine) failed", t)
            false
        }
    }

    /** Context window: covers prompt + image tokens + generation headroom. */
    private const val N_CTX = 7168
    private const val N_THREADS = 8
    /** Generation cap: layout JSON / natural prompts fit easily. */
    private const val MAX_TOKENS = 1024

    /**
     * ⭐ Multi-model era: absolute path of the resident main GGUF ("" when
     * nothing is loaded). runLocalReverse compares it against the chosen
     * definition to decide whether an unload is needed before loading.
     */
    @Volatile
    var loadedMainPath: String = ""
        private set

    private val loadMutex = Mutex()

    /**
     * Deferred completed with the last SUCCESSFUL load's code (always 0).
     * Kept so [ensureLoaded] can short-circuit when the model is already
     * resident; nulled by [trim] so the next load re-arms.
     */
    @Volatile
    private var lastLoad: Deferred<Int>? = null

    val isLoaded: Boolean
        get() = libLoaded && try {
            nativeIsLoaded()
        } catch (t: UnsatisfiedLinkError) {
            false
        }

    /**
     * Loads main model + mmproj if not already loaded. Concurrent callers
     * share one load via the mutex (first caller runs the native load, the
     * rest queue up and observe its result). Returns the native load code
     * (0 = ok, negative = specific failure). Calls made while the model is
     * already resident short-circuit inside the native loader (g_loaded).
     */
    suspend fun ensureLoaded(mainPath: String, mmprojPath: String): Int {
        if (!libLoaded) return -100
        // Fast path: a previous load succeeded and the model is still resident.
        lastLoad?.let { prev ->
            if (runCatching { prev.await() }.getOrDefault(-1) == 0 && nativeIsLoaded()) return 0
        }
        // Serialize actual loads (5.6 GB mmap + ctx alloc must never overlap).
        loadMutex.withLock {
            // Re-check under the lock: another caller may have just succeeded.
            lastLoad?.let { prev ->
                if (runCatching { prev.await() }.getOrDefault(-1) == 0 && nativeIsLoaded()) return 0
            }
            val code = withContext(Dispatchers.Default) {
                try {
                    nativeLoadModel(mainPath, mmprojPath, N_THREADS, N_CTX)
                } catch (t: UnsatisfiedLinkError) {
                    Log.e(TAG, "libvlm_engine.so missing", t); -100
                } catch (t: Throwable) {
                    Log.e(TAG, "native load crashed", t); -101
                }
            }
            if (code == 0) {
                lastLoad = CompletableDeferred(0)
                loadedMainPath = mainPath
            }
            return code
        }
    }

    /**
     * Runs one VLM inference. [rgb] is packed RGB888 (3 bytes/pixel, w*h) —
     * the native side validates size == w*h*3 and feeds mtmd_bitmap_init
     * directly. Returns generated text, or an "ERR:..." string on failure
     * (single-channel text contract, no streaming).
     */
    suspend fun inferImage(
        rgb: ByteArray,
        width: Int,
        height: Int,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.Default) {
        if (!libLoaded) return@withContext "ERR:原生引擎库缺失"
        try {
            nativeInferImage(rgb, width, height, systemPrompt, userPrompt, MAX_TOKENS)
        } catch (t: Throwable) {
            Log.e(TAG, "native infer crashed", t)
            "ERR:推理崩溃：${t.message ?: t.javaClass.simpleName}"
        }
    }

    /** Frees the model completely. Safe to call when nothing is loaded. */
    fun trim() {
        if (!libLoaded) return
        try {
            nativeUnloadModel()
        } catch (t: Throwable) {
            Log.w(TAG, "trim failed", t)
        } finally {
            // Invalidate the cached load result so a later ensureLoaded() re-arms.
            lastLoad = null
            loadedMainPath = ""
        }
    }

    private external fun nativeLoadModel(mainPath: String, mmprojPath: String, nThreads: Int, nCtx: Int): Int
    private external fun nativeInferImage(rgb: ByteArray, width: Int, height: Int, systemPrompt: String, userPrompt: String, maxTokens: Int): String
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeUnloadModel()
}
package com.abrah.nightmare.reverse

import android.content.Context
import android.util.Log
import com.abrah.nightmare.ModelInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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

    /** Set once [ensureEngine] succeeds; every entry guards on it (no native calls if false). */
    @Volatile
    private var libLoaded = false

    /**
     * ⭐⭐⭐ Since 1.6.373 the engine `.so`s are NOT in the APK (`packaging.jniLibs.excludes`
     * — 20 MB of APK for a feature most users never open). First use downloads and
     * unpacks them via [VlmInstaller], then `System.load`s each in dependency order
     * (ggml's backends are separate `.so`s: base→cpu→hexagon→ggml→llama→mtmd→vlm_engine;
     * an already-loaded soname satisfies the loader, the rest link from the absolute
     * path we hand it).
     *
     * ⚠ Blocking (network on a cold phone) — suspend so callers stay off the main
     * thread. Concurrent callers share one install+load via the object monitor;
     * [libLoaded] is `@Volatile` for the fast-path reads. Returns false (and logs)
     * on any failure — [ensureLoaded]/[inferImage] report their own error codes.
     */
    suspend fun ensureEngine(
        context: Context,
        onProgress: (suspend (ModelInstaller.Progress) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (libLoaded) return@withContext true
        synchronized(this@VlmEngine) {
            if (libLoaded) return@synchronized true
            try {
                // install() reports through a plain callback; bridge it into the
                // caller's suspend callback (UI emits) via runBlocking — the
                // callback bodies are fire-and-forget state writes, never a
                // handshake, so the adapter cannot deadlock.
                val cb = onProgress
                VlmInstaller.install(context, { p ->
                    if (cb != null) runBlocking { cb(p) }
                }, { false })
            } catch (t: Throwable) {
                Log.e(TAG, "VLM engine install failed", t)
                return@synchronized false
            }
            val d = VlmInstaller.dir(context)
            try {
                for (lib in VlmInstaller.LIBS) System.load(File(d, lib).absolutePath)
                libLoaded = true
                Log.i(TAG, "VLM engine loaded from ${d.absolutePath}")
            } catch (t: Throwable) {
                Log.e(TAG, "System.load(vlm engine) failed", t)
                return@synchronized false
            }
            true
        }
    }

    /** Context window: covers prompt + image tokens + generation headroom. */
    private const val N_CTX = 7168
    private const val N_THREADS = 8
    /** Generation cap: a complete layout tag from a busy screenshot runs
     * 1800–2100 tokens (25 items measured on a real sample) — 3072 gives ~50%
     * headroom over that. EOG normally stops earlier; N_CTX 7168 fits
     * prompt + image + 3072 with room, and KV RAM is fixed at load regardless
     * of max_new, so the only cost is time when the cap is actually reached. */
    private const val MAX_TOKENS = 3072
    /**
     * Hexagon batch-completion polling knob (GGML_HEXAGON_OPPOLL; nonzero =
     * poll instead of DSP interrupt wait). Only read by the CI hexagon build;
     * the CPU-only proot build ignores it. Upstream uses it as a boolean-style
     * value, so "1" = poll.
     */
    const val OP_POLL = "1"

    /**
     * Sets native runtime env (ADSP_LIBRARY_PATH → [adspPath],
     * GGML_HEXAGON_OPPOLL). Call once after [ensureEngine] before
     * [ensureLoaded]; idempotent and a no-op for the CPU-only build.
     *
     * ⭐ Since the engine libs moved out of the APK, [adspPath] must be a
     * colon list with the unpack dir FIRST (`VlmInstaller.dir(context)`),
     * so FastRPC finds `libggml-htp-v79.so` there, and the (now missing from
     * it) `nativeLibraryDir` LAST for anything else.
     */
    fun configureRuntime(adspPath: String) {
        if (!libLoaded) return
        try {
            nativeConfigureRuntime(adspPath, OP_POLL)
        } catch (t: Throwable) {
            Log.w(TAG, "configureRuntime failed", t)
        }
    }

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

    private external fun nativeConfigureRuntime(nativeLibDir: String, opPoll: String)
    private external fun nativeLoadModel(mainPath: String, mmprojPath: String, nThreads: Int, nCtx: Int): Int
    private external fun nativeInferImage(rgb: ByteArray, width: Int, height: Int, systemPrompt: String, userPrompt: String, maxTokens: Int): String
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeUnloadModel()
}
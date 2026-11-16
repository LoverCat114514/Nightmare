package com.abrah.nightmare.reverse

import android.content.Context
import android.util.Log
import com.abrah.nightmare.LoraInstall
import com.abrah.nightmare.ModelInstaller
import com.abrah.nightmare.UpscalerCatalog
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * ⭐⭐⭐ The on-device VLM engine, downloaded on first use instead of packed in the APK.
 *
 * The reverse-prompt engine is 8 native libs — ~80 MB raw, ~20 MB of APK — that only
 * the 端侧反推 (on-device captioning) user ever needs. Since 1.6.373 they are excluded
 * from the APK (`packaging.jniLibs.excludes` in `app/build.gradle.kts`) and fetched
 * here on demand, the same way [com.abrah.nightmare.DitEngine] fetches the DiT engine:
 * GitHub release asset → sha256 → `.part`+rename unpack → URL stamp.
 *
 * The zip is flat (no directories) and carries the eight libs of CI run 43588630436's
 * lineage (llama.cpp master 2026-11-14, hexagon v79 skel, GGML_OPENCL=OFF):
 *
 *   libggml-base.so  libggml-cpu.so  libggml-hexagon.so  libggml.so
 *   libllama.so      libmtmd.so      libvlm_engine.so    libggml-htp-v79.so
 *
 * ⚠ The seven loader libs are `System.load`ed in [LIBS]' order — that IS the
 * dependency order (`libggml.so` links the cpu and hexagon backends as separate
 * `.so`s; `libllama` links all of them; `libvlm_engine` links llama and mtmd).
 * The skel ([SKEL]) is NOT linked: FastRPC's DSP loader finds it by bare name on
 * `ADSP_LIBRARY_PATH`, which [VlmEngine.configureRuntime] points at this directory
 * beside `nativeLibraryDir`.
 *
 * The asset lives on the user's own rolling release (`vlm-engine`); an engine bump
 * re-uploads the asset under a NEW name? No — same name, same URL: [URL] is the
 * stamp, so an update means a new asset pair only when the bytes change, and the
 * phone re-fetches exactly when [SHA256]/[BYTES]/[URL] move in this file. A
 * partially-downloaded or partially-unpacked state never matches [isInstalled] and
 * is repaired by the next [install].
 */
object VlmInstaller {
    private const val TAG = "VlmInstaller"

    /** ⭐ The rolling release the user uploads the zip to (asset name fixed). */
    const val URL: String =
        "https://github.com/LoverCat114514/Nightmare/releases/download/vlm-engine/vlm-engine.zip"

    /** ⚠ The ARCHIVE's size, measured off the uploaded asset (19,887,890 bytes). */
    const val BYTES: Long = 19_887_890L

    /**
     * ⚠ Hash of the uploaded asset, exactly as [com.abrah.nightmare.DitEngine] does
     * it: native code the linker will mmap, so a length check alone cannot tell a
     * truncated-then-resumed download from a correct one. Hashing 20 MB is ~100 ms once.
     */
    const val SHA256: String = "834f37d479949acf7a59e4d349a2bcf7d85b6be1066c80c1d72720b2f6f75a8f"

    /**
     * ⭐ GitHub release assets are unreachable from some networks — the very
     * reason [com.abrah.nightmare.Prefs.apply] exists for HF URLs. Each
     * fallback here prefixes the FULL primary URL (the common gh-proxy
     * convention). A mirror that serves wrong bytes fails the [SHA256] check
     * and the next candidate is tried, so no mirror can inject code; a dead
     * one just costs a connection error.
     */
    private val CANDIDATES: List<String> = listOf(
        URL,
        "https://ghfast.top/$URL",
        "https://ghproxy.net/$URL",
        "https://gh-proxy.com/$URL",
    )

    /**
     * ⭐ The seven libs `[VlmEngine.ensureEngine]` `System.load`s, in dependency
     * order. Linker-declared deps: ggml→(base,cpu,hexagon), llama→ggml*, mtmd→(llama,ggml*),
     * vlm_engine→(llama,mtmd,ggml*). Already-loaded sonames satisfy the loader, so
     * loading them one by one from [dir] links the whole set.
     */
    val LIBS: List<String> = listOf(
        "libggml-base.so",
        "libggml-cpu.so",
        "libggml-hexagon.so",
        "libggml.so",
        "libllama.so",
        "libmtmd.so",
        "libvlm_engine.so",
    )

    /** The hexagon v79 DSP skel: ships in the zip, loaded by FastRPC, never by the linker. */
    const val SKEL: String = "libggml-htp-v79.so"

    private val ALL: List<String> = LIBS + SKEL

    /** Where the engine unpacks — its own directory, NOT the GGUF dir it serves. */
    fun dir(context: Context): File = File(context.filesDir, "vlm-engine")

    /** Holds the [URL] the current contents came from (the install stamp). */
    private fun stamp(context: Context) = File(dir(context), ".vlm-engine")

    /** Every lib present and non-empty, and stamped from THIS build's [URL]. */
    fun isInstalled(context: Context): Boolean {
        if (stamp(context).readTextOrNull() != URL) return false
        val d = dir(context)
        return ALL.all { f -> File(d, f).isFile && f.let { File(d, it).length() > 0L } }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

    /**
     * Fetch and unpack. ⚠ Blocking — off the main thread; [VlmEngine.ensureEngine]
     * and the model-download flow both call it on Dispatchers.IO.
     *
     * ⚠ One at a time (shared with [VlmEngine.ensureEngine]'s load): two installs
     * writing the same `*.part` would race exactly like the DiT engine's did.
     */
    fun install(
        context: Context,
        onProgress: (ModelInstaller.Progress) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): Unit = synchronized(this) {
        if (isInstalled(context)) return@synchronized
        val d = dir(context).apply { mkdirs() }
        val zip = File(d, "vlm-engine.zip.part")
        var lastError: Exception? = null
        for (candidate in CANDIDATES) {
            // A partial from the previous candidate must never resume-mix
            // into this one's bytes — start every attempt from zero.
            zip.delete()
            try {
                UpscalerCatalog.download(candidate, zip, BYTES, onProgress, isCancelled)
                if (zip.length() != BYTES) {
                    val got = zip.length()
                    throw IOException("downloaded $got bytes, expected $BYTES")
                }
                onProgress(ModelInstaller.Progress("verifying", 0, 0))
                val got = LoraInstall.sha256(zip) { isCancelled() }
                if (got != SHA256) {
                    throw IOException("archive sha256 is $got, expected $SHA256. The download is corrupt")
                }
                unpack(context, zip, onProgress, isCancelled)
                zip.delete()
                // ⭐ The install stamp — without this isInstalled() stayed
                // false forever and every app restart re-downloaded 20 MB.
                stamp(context).writeText(URL)
                Log.i(TAG, "installed the VLM engine in ${d.absolutePath}")
                return@synchronized
            } catch (e: ModelInstaller.Cancelled) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "engine fetch failed from " +
                    candidate.removePrefix("https://").substringBefore('/') + ": " + e.message)
                lastError = e
            }
        }
        zip.delete()
        throw lastError ?: IOException("no download candidate succeeded")
    }

    /**
     * ⚠ `.part` then RENAME, never a write in place — the linker may hold any of
     * these mapped ([com.abrah.nightmare.DitEngine.unpack] carries the full why:
     * truncating a mapped file zaps its COW pages and SIGSEGVs the holder).
     */
    private fun unpack(
        context: Context,
        zip: File,
        onProgress: (ModelInstaller.Progress) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        onProgress(ModelInstaller.Progress("extracting", 0, 0))
        val d = dir(context)
        val wanted = ALL.toSet()
        val seen = mutableSetOf<String>()
        ZipInputStream(zip.inputStream().buffered(1 shl 16)).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (entry.isDirectory || name !in wanted) {
                    z.closeEntry()
                    continue
                }
                if (isCancelled()) throw ModelInstaller.Cancelled()
                val tmp = File(d, "$name.part")
                tmp.outputStream().use { out -> z.copyTo(out, 1 shl 16) }
                if (tmp.length() == 0L) {
                    tmp.delete()
                    throw IOException("$name unpacked empty")
                }
                tmp.setReadable(true, false)
                tmp.setExecutable(true, false)
                // Same directory, same filesystem: rename(2), atomic.
                if (!tmp.renameTo(File(d, name))) throw IOException("cannot replace ${File(d, name)}")
                seen += name
                z.closeEntry()
            }
        }
        val missing = wanted - seen
        if (missing.isNotEmpty()) throw IOException("the archive had no ${missing.joinToString()}")
    }

    /** Wipes the whole engine directory (the stamp rides in it). */
    fun delete(context: Context) {
        dir(context).deleteRecursively()
    }
}

package com.abrah.nightmare.reverse

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.abrah.nightmare.BackendProcess
import com.abrah.nightmare.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * ⭐⭐⭐ Module 1 — on-device VLM GGUF storage, download & model-chooser management.
 *
 * Models live in `<modelsDir>/vlm/` (a sibling of every other model family,
 * same [BackendProcess.modelsDir] root). Downloads use the same `.part` +
 * resume + rename discipline as Upscalers/DitEngine, and go through
 * [Prefs.apply] so the user's HF mirror setting covers them too.
 *
 * ⭐ Multi-model era (2026-11-13, user request): the chooser offers every
 * [VlmModelManager.ModelDef]; the picked id is persisted via
 * [Prefs.setVlmSelectedModel] and every status/download/infer call targets it.
 * The previous single-flagship design (DeepSeek 9B only) became just the
 * first entry of that table.
 *
 * mmproj stays f16 — the vision tower is quantization-sensitive and the user
 * explicitly rejected q8_0 for it (hallucination risk).
 */
object VlmModelManager {
    private const val TAG = "VlmModelManager"

    /**
     * One downloadable/loadable on-device VLM. [mainBytes]/[mmprojBytes] are
     * the exact HF file sizes — the downloader verifies them byte-for-byte
     * before the atomic rename.
     */
    data class ModelDef(
        val id: String,
        val displayName: String,
        val shortName: String,
        val badge: String,
        val hfBase: String,
        val mainFile: String,
        val mmprojFile: String,
        val mainBytes: Long,
        val mmprojBytes: Long,
    )

    /** Flagship since 2026-11-13: Sakura721's DeepSeek-V4-Pro-Qwen3.5-9B (qwen35
     *  hybrid linear-attention arch, qwen3vl_merger projector — verified
     *  end-to-end on-device with the standalone harness). */
    val DEEPSEEK_9B = ModelDef(
        id = "deepseek-v4-pro-qwen35-9b",
        displayName = "DeepSeek-V4-Pro-Qwen3.5-9B",
        shortName = "9B",
        badge = "旗舰 12GB+",
        hfBase = "https://huggingface.co/Sakura721/DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED-heretic-GGUF/resolve/main/",
        mainFile = "DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED-heretic-Q4_K_M.gguf",
        mmprojFile = "mmproj-DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED-heretic-f16.gguf",
        mainBytes = 5_629_111_968L,
        mmprojBytes = 918_167_360L,
    )

    /** The original flagship (prithivMLmods Qwen3-VL-4B-Instruct-Unredacted-MAX),
     *  kept as a second downloadable option per the user's multi-select request.
     *  Sizes are the exact byte counts the app itself downloaded back then. */
    val QWEN_4B = ModelDef(
        id = "qwen3-vl-4b-unredacted",
        displayName = "Qwen3-VL-4B-Instruct",
        shortName = "4B",
        badge = "旗舰 8GB+",
        hfBase = "https://huggingface.co/prithivMLmods/Qwen3-VL-4B-Instruct-Unredacted-MAX-GGUF/resolve/main/",
        mainFile = "Qwen3-VL-4B-Instruct-Unredacted-MAX.Q8_0.gguf",
        mmprojFile = "mmproj-Qwen3-VL-4B-Instruct-Unredacted-MAX-f16.gguf",
        mainBytes = 4_280_406_240L,
        mmprojBytes = 839_326_048L,
    )

    val MODELS: List<ModelDef> = listOf(DEEPSEEK_9B, QWEN_4B)

    fun modelDef(context: Context, modelId: String): ModelDef =
        MODELS.firstOrNull { it.id == modelId } ?: selectedDef(context)

    /** The persisted selection; unknown/blank ids fall back to the flagship 9B. */
    fun selectedDef(context: Context): ModelDef {
        val id = Prefs.vlmSelectedModel(context)
        return MODELS.firstOrNull { it.id == id } ?: DEEPSEEK_9B
    }

    /** Set by [cancelDownload]; every download chunk checks it. */
    @Volatile
    private var cancelled = false

    fun vlmDir(context: Context): File = File(BackendProcess.modelsDir(context), "vlm")

    // ------------------------------------------------------------------ status

    /** Per-definition installed flags for the chooser chips. */
    private fun optionFor(context: Context, def: ModelDef): VlmModelOption {
        val dir = vlmDir(context)
        val names = dir.listFiles()?.mapTo(HashSet()) { it.name } ?: emptySet()
        return VlmModelOption(
            id = def.id,
            displayName = def.displayName,
            shortName = def.shortName,
            badge = def.badge,
            mainInstalled = names.contains(def.mainFile),
            mmprojInstalled = names.contains(def.mmprojFile),
        )
    }

    /**
     * Scans the vlm dir for the given model id (or the persisted selection).
     * The definition's canonical names win; otherwise the newest matching
     * `*.gguf` counts (imported files keep their original names — status
     * reports what would actually load, so the UI never lies).
     * `.part` leftovers are ignored.
     */
    fun status(
        context: Context,
        modelId: String? = null,
        downloading: Boolean = false,
        progress: Float = 0f,
        detail: String = "",
    ): LocalModelStatus {
        val def = modelDef(context, modelId ?: Prefs.vlmSelectedModel(context))
        val dir = vlmDir(context)
        val ggufs = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        val main = ggufs.firstOrNull { it.name == def.mainFile }
            ?: ggufs.firstOrNull { !it.name.startsWith("mmproj") }
        val mmproj = ggufs.firstOrNull { it.name == def.mmprojFile }
            ?: ggufs.firstOrNull { it.name.startsWith("mmproj") }

        return LocalModelStatus(
            modelReady = main != null && mmproj != null,
            mainModelName = main?.name,
            mainModelBytes = main?.length() ?: 0L,
            mmprojName = mmproj?.name,
            mmprojBytes = mmproj?.length() ?: 0L,
            isDownloading = downloading,
            downloadProgress = progress,
            downloadDetail = detail,
            selectedModelId = def.id,
            // Canonical file installed → clean display name; an imported GGUF
            // would load instead → show that file's name, the card never lies.
            selectedModelName = when {
                main == null -> def.displayName
                main.name == def.mainFile -> def.displayName
                else -> main.name.removeSuffix(".gguf")
            },
            selectedModelShort = def.shortName,
            selectedModelBadge = def.badge,
            models = MODELS.map { optionFor(context, it) },
        )
    }

    // ---------------------------------------------------------------- download

    fun downloadModel(context: Context, modelId: String): Flow<LocalModelStatus> = flow {
        cancelled = false
        val def = modelDef(context, modelId)
        val dir = vlmDir(context)
        dir.mkdirs()
        Log.i(TAG, "download start [${def.id}] → ${dir.absolutePath}")

        suspend fun step(file: File, url: String, expected: Long, label: String) {
            if (file.exists() && file.length() == expected) {
                emit(status(context, def.id, downloading = true, progress = 1f, detail = "$label 已存在，跳过"))
                return
            }
            downloadFile(context, url, file, expected) { frac, extra ->
                emit(
                    status(
                        context,
                        def.id,
                        downloading = true,
                        progress = frac,
                        detail = "$label ${(frac * 100).toInt()}%$extra",
                    )
                )
            }
        }

        try {
            step(File(dir, def.mainFile), def.hfBase + def.mainFile, def.mainBytes, "主模型")
            if (cancelled) throw DownloadCancelled()
            step(File(dir, def.mmprojFile), def.hfBase + def.mmprojFile, def.mmprojBytes, "视觉投影")
            if (cancelled) throw DownloadCancelled()
            emit(status(context, def.id).copy(downloadDetail = "下载完成"))
        } catch (e: DownloadCancelled) {
            Log.i(TAG, "download cancelled [${def.id}]")
            // .part is kept on purpose — the next attempt resumes from it.
            emit(status(context, def.id).copy(downloadDetail = "已取消（进度已保留，可续传）"))
        } catch (t: Throwable) {
            Log.w(TAG, "download failed [${def.id}]", t)
            emit(status(context, def.id).copy(downloadDetail = "下载失败：${t.message ?: t.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)

    fun cancelDownload() {
        cancelled = true
    }

    private class DownloadCancelled : Exception()

    /**
     * `.part` download with byte-range resume, adapted from Upscalers' proven
     * discipline: partial file `<name>.part`, resume via Range header, size
     * verification, atomic rename, GGUF magic check at the end.
     */
    private suspend fun downloadFile(
        context: Context,
        url: String,
        dest: File,
        expected: Long,
        onProgress: suspend (Float, String) -> Unit,
    ) {
        val part = File(dest.parentFile, dest.name + ".part")
        var from = if (part.exists()) part.length() else 0L
        if (from > expected) { // a longer "partial" is junk, not a partial
            part.delete()
            from = 0L
        }

        onProgress(if (expected > 0) from.toFloat() / expected else 0f, if (from > 0) "（续传）" else "")

        val conn = (URL(Prefs.apply(url)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            if (from > 0) setRequestProperty("Range", "bytes=$from-")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            val append = from > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (!append) from = 0L

            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(1 shl 16)
                    var written = from
                    var lastEmit = 0L
                    while (true) {
                        if (cancelled) throw DownloadCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        // throttle progress to ~16 MB steps; it crosses to the UI
                        if (written - lastEmit >= (1 shl 24)) {
                            lastEmit = written
                            onProgress(
                                (written.toFloat() / expected).coerceIn(0f, 0.999f),
                                if (append) "（续传）" else "",
                            )
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (part.length() != expected) {
            throw IOException("文件不完整：${part.length()}/${expected} 字节")
        }
        checkGgufMagic(part)
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) throw IOException("无法完成写入 ${dest.name}")
        Log.i(TAG, "downloaded ${dest.name} ($expected bytes)")
    }

    /** GGUF files start with the magic bytes "GGUF" (0x47 0x47 0x55 0x46). */
    private fun checkGgufMagic(file: File) {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (!(magic[0] == 'G'.code.toByte() && magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() && magic[3] == 'F'.code.toByte())
            ) {
                throw IOException("${file.name} 不是有效的 GGUF 文件（魔数校验失败）")
            }
        }
    }

    // ------------------------------------------------------------------ import

    /** Imports a user-picked .gguf from SAF, validating the GGUF magic. */
    fun importLocalModelFile(context: Context, uri: Uri, isMmproj: Boolean): LocalModelStatus {
        val resolver = context.contentResolver
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { displayName = it }
            }
        }
        // sanitize: keep a filesystem-safe name, guarantee the .gguf suffix
        displayName = displayName.substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (displayName.isEmpty()) displayName = "model.gguf"
        if (!displayName.endsWith(".gguf", ignoreCase = true)) displayName += ".gguf"

        val dir = vlmDir(context)
        dir.mkdirs()
        val dest = File(dir, displayName)
        val tmp = File(dir, displayName + ".import.part")

        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, 1 shl 16)
                }
            } ?: throw IOException("无法打开所选文件")
            checkGgufMagic(tmp)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("无法完成导入 ${dest.name}")
            Log.i(TAG, "imported ${dest.name} (${dest.length()} bytes, mmproj=$isMmproj)")
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        return status(context)
    }

    // ------------------------------------------------------------------ delete

    /** Deletes the whole vlm dir — GGUFs and `.part` leftovers alike. */
    fun deleteLocalModel(context: Context): LocalModelStatus {
        val dir = vlmDir(context)
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
        Log.i(TAG, "vlm models deleted (${dir.absolutePath})")
        return status(context)
    }
}
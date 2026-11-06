package com.abrah.nightmare.newui.core

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.abrah.nightmare.Family
import com.abrah.nightmare.HarnessOps
import com.abrah.nightmare.ModelCatalog
import com.abrah.nightmare.R
import com.abrah.nightmare.SelectedModel
import com.abrah.nightmare.SdSampler
import com.abrah.nightmare.api.ApiCore
import com.abrah.nightmare.api.ApiError
import com.abrah.nightmare.api.RunRequest
import com.abrah.nightmare.detail.Detailer
import java.io.File

/**
 * ⭐⭐ fix34 (2026-11-06) — the bridge between the workbench and the render engine.
 *
 * ## What this replaces
 *
 * The old code had three things tangled into one 1998-line file:
 *
 * 1. `private fun core(ctx)` + a `private object CoreHolder` — the engine
 *    instance, held in a **process-wide singleton**.
 * 2. `buildRequest(s, prompt, negative, src)` — pure translation from UI state
 *    to a [RunRequest].
 * 3. `onTapGenerate(…)` — the run itself, writing progress into three
 *    `AtomicReference`s that the UI then polled at 100 ms.
 *
 * Here the three are separated by concern: this file owns the engine and the
 * request translation; [RunController] owns the run's lifecycle and publishes
 * its phase. Nothing polls.
 *
 * ## What is honestly NOT fixed
 *
 * `ApiCore` has no `dispose`/`shutdown` — it owns a `CoroutineScope` and an
 * `ImageStore` with no teardown API, so the engine instance still lives as long
 * as the process. That is an upstream limitation, not something a refactor of
 * the workbench can fix without touching the engine. What IS fixed: the engine
 * is now an ordinary object owned by the screen's `remember`, not an `object`
 * singleton, and **all of the run's live UI state** (phase, cancel flag,
 * history) lives in [RunController]/[WbParams] with a real `dispose()`. The
 * leak that mattered — UI state surviving its screen — is gone.
 *
 * ⚠ `holder = "newui"` is kept verbatim: it is what scopes `RunLock`, so the
 * workbench still refuses to render while the canvas or the API is rendering
 * (409), exactly as before.
 */
class Engine(ctx: Context) {

    private val app = ctx.applicationContext

    /**
     * The engine, built once.
     *
     * ⚠ `@Volatile` + double-checked init: the first touch may come from the
     * engine thread, not the main thread.
     */
    @Volatile private var core: ApiCore? = null

    /**
     * The sink's forwarding targets, swapped per run.
     *
     * ⚠ This is the one piece of mutable plumbing in the file, and it exists
     * because [ApiCore]'s `HarnessOps.Sink` is fixed at construction while the
     * receiving callbacks are per-run. `@Volatile` because the engine thread
     * reads them and the caller's thread writes them.
     */
    @Volatile private var onProgress: ((Pair<Int, Int>?) -> Unit)? = null
    @Volatile private var onLog: ((String) -> Unit)? = null

    private fun core(): ApiCore = core ?: synchronized(this) {
        core ?: ApiCore(
            app,
            HarnessOps(app, object : HarnessOps.Sink {
                override fun say(text: String, bad: Boolean) {
                    // ⭐ `bad` is kept as a prefix rather than dropped: the old
                    // sink did the same, and a warning the person cannot tell
                    // apart from a normal line is a warning that does nothing.
                    onLog?.invoke(if (bad) "⚠ $text" else text)
                }

                override fun progress(stepOfTotal: Pair<Int, Int>?) {
                    onProgress?.invoke(stepOfTotal)
                }
            }),
            holder = "newui",
        ).also { core = it }
    }

    /**
     * Render [req] on the calling thread (which [RunController] has already
     * moved to its own single-threaded lane, because `core.run` BLOCKS).
     *
     * [onStep] and [onLog] receive the engine's events; [isCancelled] is polled
     * inside the engine's own progress callback — the engine offers no other
     * cancellation hook, and polling there is free because the callback fires
     * once per step rather than once per frame.
     */
    fun run(
        req: RunRequest,
        onStep: (step: Int, total: Int) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean,
    ): RunController.Result {
        val core = core()
        // ⚠ Cleared in a `finally`: a sink left pointing at a finished run's
        // callbacks would write into a dead scope on the NEXT run's first event.
        this.onProgress = { pair ->
            if (pair != null) onStep(pair.first, pair.second)
        }
        this.onLog = onLog
        try {
            val prepared = core.prepare(req)
            val t0 = System.currentTimeMillis()
            val res = core.run(prepared) { _, js ->
                val total = js.optString("total", "-1").toInt()
                val step = js.optString("step", "-1").toInt()
                if (total > 0 && step >= 0) {
                    onStep(step, total)
                    // ⚠ Cancellation is checked HERE, on the engine's own thread,
                    // so `core.cancel()` is called from the thread that owns the
                    // run — not from the UI.
                    if (isCancelled()) core.cancel()
                }
            }
            val js = res.answer
            val err = js.optString("error")
            if (err.isNotEmpty()) return RunController.Result.Failure(err)

            val first = res.images.firstOrNull()
                ?: return RunController.Result.Empty("no picture came out of this run")
            val url = js.optJSONArray("results")?.optJSONObject(0)?.optString("url").orEmpty()
            val file = core.resultFileOf(url)
            return RunController.Result.Success(file, System.currentTimeMillis() - t0)
        } catch (e: ApiError) {
            // ⚠ The engine's own refusals (409 while something else renders, a
            // missing model) carry a message written for a person. Keep it.
            return RunController.Result.Failure(e.message ?: "the engine refused the run")
        }
    }

    /** Ask the engine to stop. Called from the engine thread via [run]'s poll. */
    fun cancel() {
        core?.let { runCatching { it.cancel() } }
    }
}

/**
 * Build the [RunRequest] from the workbench's state.
 *
 * ⚠ Every rule below is the OLD code's, kept deliberately — these are engine
 * contracts, not style, and "refactoring" one of them silently changes what the
 * engine renders:
 *
 * - `scheduler` IS the sampler+schedule wire value (`dpm3_sde_karras`).
 * - `loras` is `name.safetensors@weight`. The picker's names already carry
 *   `.safetensors`, so appending another one produced
 *   `x.safetensors.safetensors@w`, which the engine refuses as "not installed"
 *   and the run died. The guard below keeps old bare-name states working.
 * - `fix_faces` / `fix_hands` are FAMILY-GATED: a sampler without the detail
 *   widget would 400 the whole render.
 * - MOSS layout rides `params["generate"]["moss_layout"]`.
 * - img2img wires the source into the `image` node as a base64 data URL.
 */
fun buildRequest(
    s: WbParams,
    prompt: String,
    negative: String,
    srcDataUrl: String?,
): RunRequest {
    val model = ModelCatalog.byId(s.modelId) ?: throw ApiError(400, "no model selected")
    val img2img = s.mode == WbParams.MODE_IMG2IMG
    val knobs = mutableMapOf<String, String>()
    if (s.scheduler.isNotBlank()) knobs["scheduler"] = s.scheduler
    if (s.w > 0) knobs["width"] = s.w.toString()
    if (s.h > 0) knobs["height"] = s.h.toString()
    if (s.steps > 0) knobs["steps"] = s.steps.toString()
    if (s.cfg > 0f) knobs["cfg"] = s.cfg.toString()
    if (img2img) knobs["denoise"] = s.denoise.toString()
    // ⚠ fixedSeed with an EMPTY seed field (0) falls through to a random seed by
    // design — the field starts blank and the person may not have typed yet.
    // Blocking it would make "lock the seed" refuse the very first tap.
    if (s.fixedSeed && s.seed > 0L) knobs["seed"] = s.seed.toString()

    if (model.family !in DETAIL_EXCLUDED) {
        if (s.pFace) knobs[Detailer.FIX_FACES] = "true"
        if (s.pHands) knobs[Detailer.FIX_HANDS] = "true"
    }

    if (s.loras.isNotEmpty()) {
        knobs["loras"] = s.loras.joinToString(", ") { pick ->
            val fn = if (pick.name.endsWith(".safetensors", ignoreCase = true)) {
                pick.name
            } else {
                pick.name + ".safetensors"
            }
            "$fn@${LoraPick.formatWeight(pick.weight)}"
        }
    }

    val params = if (s.mossOn && model.family == Family.MOSS) {
        mapOf("generate" to mapOf(SdSampler.MOSS_LAYOUT to "true"))
    } else {
        emptyMap()
    }

    val images = if (img2img) {
        val b64 = srcDataUrl
            ?: throw ApiError(400, "img2img needs a source picture — load one first")
        mapOf("image" to b64)
    } else {
        emptyMap()
    }

    return RunRequest(
        flow = if (img2img) "img2img" else "txt2img",
        model = model.id,
        prompt = prompt,
        negative = negative,
        knobs = knobs,
        params = params,
        images = images,
    )
}

/**
 * The families with NO fix widgets on their sampler — [Detailer]'s own
 * exclusion list. Sending the knobs to those would 400 the whole render.
 */
private val DETAIL_EXCLUDED = setOf(
    Family.IRIS,
    Family.WAN,
    Family.IDEOGRAM,
    Family.FLUX3,
    Family.FIBO2,
)

/**
 * ⭐ The checkpoint label, read from the SAME source the request uses.
 *
 * ⚠ [SelectedModel] is `@Volatile` with no state signal, so this is called
 * during composition of the result card's run strip — and the card is keyed on
 * the model id, which IS state. Reading the global here without a key would
 * freeze the label across picks (a real user report from the old tree).
 */
fun modelLabel(modelId: String): String =
    ModelCatalog.byId(modelId)?.label ?: SelectedModel.spec.label

/** Decode a picked content uri into a data URL (the img2img source travels as base64). */
fun uriToDataUrl(ctx: Context, uri: Uri): String? = try {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    if (bytes == null) null else {
        val mime = ctx.contentResolver.getType(uri) ?: "image/png"
        "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
} catch (_: Exception) {
    null
}

/**
 * ⭐ Utilities to embed and extract Nightmare parameters into/from PNG tEXt chunks.
 * Mimics ComfyUI / Stable Diffusion image metadata embedding.
 */
object PngMetadata {
    private const val KEYWORD = "nightmare"

    fun injectMetadata(srcFile: File, metaJson: String, outStream: java.io.OutputStream) {
        val bytes = srcFile.readBytes()
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
            && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
            val kwBytes = KEYWORD.toByteArray(Charsets.ISO_8859_1)
            val textBytes = metaJson.toByteArray(Charsets.UTF_8)
            val chunkData = ByteArray(kwBytes.size + 1 + textBytes.size)
            System.arraycopy(kwBytes, 0, chunkData, 0, kwBytes.size)
            chunkData[kwBytes.size] = 0 // null separator
            System.arraycopy(textBytes, 0, chunkData, kwBytes.size + 1, textBytes.size)

            val typeBytes = "tEXt".toByteArray(Charsets.US_ASCII)
            val crc = java.util.zip.CRC32()
            crc.update(typeBytes)
            crc.update(chunkData)
            val crcVal = crc.value.toInt()

            val len = chunkData.size
            val chunkBytes = java.nio.ByteBuffer.allocate(12 + len)
                .putInt(len)
                .put(typeBytes)
                .put(chunkData)
                .putInt(crcVal)
                .array()

            // Insert chunk right before IEND
            val iendBytes = "IEND".toByteArray(Charsets.US_ASCII)
            var iendPos = -1
            for (i in bytes.size - 12 downTo 8) {
                if (bytes[i] == iendBytes[0] && bytes[i + 1] == iendBytes[1] &&
                    bytes[i + 2] == iendBytes[2] && bytes[i + 3] == iendBytes[3]) {
                    iendPos = i
                    break
                }
            }
            if (iendPos >= 4) {
                val insertPos = iendPos - 4
                outStream.write(bytes, 0, insertPos)
                outStream.write(chunkBytes)
                outStream.write(bytes, insertPos, bytes.size - insertPos)
                return
            }
        }
        // Fallback: copy bytes directly
        outStream.write(bytes)
    }

    fun extractMetadata(inStream: java.io.InputStream): String? {
        val bytes = inStream.readBytes()
        if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 'P'.code.toByte()
            || bytes[2] != 'N'.code.toByte() || bytes[3] != 'G'.code.toByte()) {
            return null
        }
        var idx = 8
        val buf = java.nio.ByteBuffer.wrap(bytes)
        while (idx + 8 <= bytes.size) {
            val len = buf.getInt(idx)
            val type = String(bytes, idx + 4, 4, Charsets.US_ASCII)
            val dataStart = idx + 8
            if (dataStart + len > bytes.size) break
            if (type == "tEXt" || type == "iTXt") {
                var nullIdx = -1
                for (i in 0 until len) {
                    if (bytes[dataStart + i] == 0.toByte()) {
                        nullIdx = i
                        break
                    }
                }
                if (nullIdx > 0) {
                    val kw = String(bytes, dataStart, nullIdx, Charsets.ISO_8859_1)
                    if (kw == KEYWORD || kw == "parameters" || kw == "workflow") {
                        val textStart = dataStart + nullIdx + 1
                        val textLen = len - (nullIdx + 1)
                        if (textLen > 0) {
                            return String(bytes, textStart, textLen, Charsets.UTF_8)
                        }
                    }
                }
            }
            idx += 8 + len + 4
        }
        return null
    }
}

/** Finished PNG → Pictures/Nightmare via MediaStore; with embedded ComfyUI-like parameter metadata. */
fun saveShot(ctx: Context, shot: Shot) {
    try {
        val name = "nm-" + System.currentTimeMillis() + ".png"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nightmare")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            if (shot.meta != null) {
                PngMetadata.injectMetadata(shot.file, shot.meta, out)
            } else {
                java.io.FileInputStream(shot.file).use { it.copyTo(out) }
            }
        }
        Toast.makeText(
            ctx,
            ctx.getString(R.string.newui_saved, "Pictures/Nightmare/$name"),
            Toast.LENGTH_SHORT,
        ).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "save: " + e.message, Toast.LENGTH_SHORT).show()
    }
}

/** The display name of a picked document, or null when it cannot be read. */
fun queryDisplayName(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
} catch (_: Exception) {
    null
}
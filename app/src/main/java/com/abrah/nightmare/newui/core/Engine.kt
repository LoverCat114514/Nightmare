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

            val url = js.optJSONArray("results")?.optJSONObject(0)?.optString("url").orEmpty()
            if (url.isEmpty()) return RunController.Result.Empty("no result came out of this run")
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
    val mode = s.mode
    val takesImage = mode in setOf(
        WbParams.MODE_IMG2IMG,
        WbParams.MODE_INPAINT,
        WbParams.MODE_UPSCALE,
        "face_edit",
        "animate_portrait",
        "swap_i2i",
        "flux_edit",
        "i2v",
        "motion",
    )
    val images = mutableMapOf<String, String>()
    if (takesImage) {
        val b64 = srcDataUrl
            ?: throw ApiError(400, "当前流程需要先加载一张源图片")
        images["image"] = b64
    }

    val videos = mutableMapOf<String, String>()
    if (mode == "animate_portrait" || mode == "motion") {
        val vpath = s.driverVideoPath
            ?: throw ApiError(400, "当前流程需要先选择一段驱动视频 (MP4)")
        val f = java.io.File(vpath)
        if (!f.exists()) throw ApiError(400, "驱动视频文件不存在")
        val b64 = android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)
        videos["clip"] = b64
    }

    when (mode) {
        "upscale" -> {
            return RunRequest(
                flow = "upscale",
                model = null,
                prompt = null,
                negative = null,
                knobs = emptyMap(),
                params = emptyMap(),
                images = images,
            )
        }
        "face_edit" -> {
            val faceParams = mapOf(
                "mouth_smile" to s.faceSmile.toString(),
                "eye_open_ratio" to s.faceEyes.toString(),
                "lip_open_ratio" to s.faceLipOpen.toString(),
                "mouth_pout" to s.faceMouthPout.toString(),
                "head_pitch" to s.facePitch.toString(),
                "head_yaw" to s.faceYaw.toString(),
                "head_roll" to s.faceRoll.toString(),
            )
            return RunRequest(
                flow = "face_edit",
                model = null,
                prompt = null,
                negative = null,
                knobs = emptyMap(),
                params = mapOf("face" to faceParams),
                images = images,
            )
        }
        "animate_portrait" -> {
            return RunRequest(
                flow = "animate_portrait",
                model = null,
                prompt = null,
                negative = null,
                knobs = emptyMap(),
                params = mapOf("animate" to mapOf("seconds" to s.videoSeconds.toString())),
                images = images,
                videos = videos,
            )
        }
        "motion" -> {
            val motionParams = mutableMapOf(
                com.abrah.nightmare.npu.WanFun.START to "0.0",
                com.abrah.nightmare.npu.WanFun.END to s.videoSeconds.toString(),
            )
            if (s.fixedSeed && s.seed > 0L) motionParams["seed"] = s.seed.toString()
            return RunRequest(
                flow = "motion",
                model = null,
                prompt = null,
                negative = null,
                knobs = emptyMap(),
                params = mapOf("motion" to motionParams),
                images = images,
                videos = videos,
            )
        }
        "t2v" -> {
            val videoParams = mutableMapOf("seconds" to s.videoSeconds.toString())
            if (s.fixedSeed && s.seed > 0L) videoParams["seed"] = s.seed.toString()
            if (s.videoModel == "wan22_14b") {
                return RunRequest(
                    flow = "t2v14",
                    model = null,
                    prompt = prompt,
                    negative = negative,
                    knobs = emptyMap(),
                    params = mapOf("video" to videoParams),
                    images = emptyMap(),
                )
            }
            if (s.videoModel == "nd.sample") {
                videoParams["model"] = "nd.sample"
            } else if (s.videoModel == "minimax_h3") {
                videoParams["model"] = com.abrah.nightmare.npu.H3Node.name
            } else if (s.videoModel == "wan21_nsfw13") {
                videoParams["model"] = com.abrah.nightmare.npu.WanNsfw13Node.name
            }
            return RunRequest(
                flow = "t2v",
                model = null,
                prompt = prompt,
                negative = negative,
                knobs = emptyMap(),
                params = mapOf("video" to videoParams),
                images = emptyMap(),
            )
        }
        "i2v" -> {
            val videoParams = mutableMapOf("seconds" to s.videoSeconds.toString())
            if (s.fixedSeed && s.seed > 0L) videoParams["seed"] = s.seed.toString()
            if (s.videoModel == "nd.sample") {
                videoParams["model"] = "nd.sample"
            } else if (s.videoModel == "minimax_h3") {
                videoParams["model"] = com.abrah.nightmare.npu.H3Node.name
            } else if (s.videoModel == "wan21_nsfw13") {
                videoParams["model"] = com.abrah.nightmare.npu.WanNsfw13Node.name
            }
            return RunRequest(
                flow = "i2v",
                model = null,
                prompt = prompt,
                negative = negative,
                knobs = emptyMap(),
                params = mapOf("video" to videoParams),
                images = images,
            )
        }
        "t2v14" -> {
            val model = ModelCatalog.byId(s.modelId) ?: throw ApiError(400, "14B顶级视频需要先选择第一帧生成模型")
            val videoParams = mutableMapOf("seconds" to s.videoSeconds.toString())
            if (s.fixedSeed && s.seed > 0L) videoParams["seed"] = s.seed.toString()
            return RunRequest(
                flow = "t2v14",
                model = model.id,
                prompt = prompt,
                negative = negative,
                knobs = emptyMap(),
                params = mapOf("video" to videoParams),
                images = emptyMap(),
            )
        }
        else -> {
            val model = ModelCatalog.byId(s.modelId) ?: throw ApiError(400, "no model selected")
            val knobs = mutableMapOf<String, String>()
            if (s.scheduler.isNotBlank()) knobs["scheduler"] = s.scheduler
            if (s.w > 0) knobs["width"] = s.w.toString()
            if (s.h > 0) knobs["height"] = s.h.toString()
            if (s.steps > 0) knobs["steps"] = s.steps.toString()
            if (s.cfg > 0f) knobs["cfg"] = s.cfg.toString()
            if (mode == WbParams.MODE_IMG2IMG || mode == WbParams.MODE_INPAINT || mode == "swap_i2i") {
                knobs["denoise"] = s.denoise.toString()
            }
            if (s.fixedSeed && s.seed > 0L) knobs["seed"] = s.seed.toString()
            // ⭐ fix35g: the detail widgets only exist on the sampler when it is NOT the
            // inpaint variant (SdSampler.offersDetail excludes inpaint). Sending
            // fix_faces/fix_hands for an inpaint flow used to 400 with
            // "no sampler in this flow takes \"fix_faces\"" — the switch looked
            // dead. The widgets' own visibility matches this condition.
            if (mode != WbParams.MODE_INPAINT && model.family !in DETAIL_EXCLUDED) {
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
            val samplerNodeId = if (mode == WbParams.MODE_INPAINT) "inpaint" else if (mode == "flux_edit") "edit" else "generate"
            val params = if (s.mossOn && model.family == Family.MOSS) {
                mapOf(samplerNodeId to mapOf(SdSampler.MOSS_LAYOUT to "true"))
            } else {
                emptyMap()
            }
            return RunRequest(
                flow = mode,
                model = model.id,
                prompt = prompt,
                negative = negative,
                knobs = knobs,
                params = params,
                images = images,
            )
        }
    }
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
            // ⭐ fix35p: the scan starts at size-8, not size-12. A PNG ends with
            // the 12-byte IEND chunk (len4+type4+crc4), so the "IEND" TYPE field
            // sits at size-8 — a scan from size-12 never reached it, every inject
            // fell through to the plain-copy fallback, and NO metadata was ever
            // written (the import-params bug, reported 2026-11-09; verified on a
            // real gallery PNG: IEND at n-8, old range [n-12..8] missed it).
            var iendPos = -1
            for (i in bytes.size - 8 downTo 8) {
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

    private fun decompressZlib(bytes: ByteArray, offset: Int, length: Int): String? = try {
        val inflater = java.util.zip.Inflater()
        inflater.setInput(bytes, offset, length)
        val bos = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count <= 0 && inflater.needsInput()) break
            bos.write(buffer, 0, count)
        }
        inflater.end()
        bos.toString("UTF-8")
    } catch (_: Throwable) {
        null
    }

    fun extractMetadata(inStream: java.io.InputStream): String? {
        val bytes = inStream.readBytes()
        if (bytes.size < 8) return null

        var rawResult: String? = null

        // 1. Check PNG signature
        if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
            && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
            var idx = 8
            val buf = java.nio.ByteBuffer.wrap(bytes)
            while (idx + 8 <= bytes.size) {
                val len = buf.getInt(idx)
                if (len < 0) break
                val type = String(bytes, idx + 4, 4, Charsets.US_ASCII)
                val dataStart = idx + 8
                if (dataStart + len > bytes.size) break

                if (type == "tEXt") {
                    var nullIdx = -1
                    for (i in 0 until len) {
                        if (bytes[dataStart + i] == 0.toByte()) {
                            nullIdx = i
                            break
                        }
                    }
                    if (nullIdx > 0) {
                        val kw = String(bytes, dataStart, nullIdx, Charsets.ISO_8859_1)
                        if (kw.equals(KEYWORD, ignoreCase = true) || kw.equals("parameters", ignoreCase = true)
                            || kw.equals("workflow", ignoreCase = true) || kw.equals("prompt", ignoreCase = true)
                            || kw.equals("comment", ignoreCase = true) || kw.equals("description", ignoreCase = true)) {
                            val textStart = dataStart + nullIdx + 1
                            val textLen = len - (nullIdx + 1)
                            if (textLen > 0) {
                                rawResult = String(bytes, textStart, textLen, Charsets.UTF_8)
                                break
                            }
                        }
                    }
                } else if (type == "zTXt") {
                    var nullIdx = -1
                    for (i in 0 until len) {
                        if (bytes[dataStart + i] == 0.toByte()) {
                            nullIdx = i
                            break
                        }
                    }
                    if (nullIdx > 0 && nullIdx + 2 < len) {
                        val kw = String(bytes, dataStart, nullIdx, Charsets.ISO_8859_1)
                        if (kw.equals(KEYWORD, ignoreCase = true) || kw.equals("parameters", ignoreCase = true)
                            || kw.equals("workflow", ignoreCase = true) || kw.equals("prompt", ignoreCase = true)) {
                            val decomp = decompressZlib(bytes, dataStart + nullIdx + 2, len - (nullIdx + 2))
                            if (!decomp.isNullOrBlank()) {
                                rawResult = decomp
                                break
                            }
                        }
                    }
                } else if (type == "iTXt") {
                    var nullIdx = -1
                    for (i in 0 until len) {
                        if (bytes[dataStart + i] == 0.toByte()) {
                            nullIdx = i
                            break
                        }
                    }
                    if (nullIdx > 0 && nullIdx + 3 < len) {
                        val kw = String(bytes, dataStart, nullIdx, Charsets.ISO_8859_1)
                        if (kw.equals(KEYWORD, ignoreCase = true) || kw.equals("parameters", ignoreCase = true)
                            || kw.equals("workflow", ignoreCase = true) || kw.equals("prompt", ignoreCase = true)) {
                            val compFlag = bytes[dataStart + nullIdx + 1].toInt()
                            // Skip language tag and translated keyword (both null-terminated)
                            var p = dataStart + nullIdx + 3
                            var nullCount = 0
                            while (p < dataStart + len && nullCount < 2) {
                                if (bytes[p] == 0.toByte()) nullCount++
                                p++
                            }
                            val textLen = (dataStart + len) - p
                            if (textLen > 0) {
                                val text = if (compFlag == 1) decompressZlib(bytes, p, textLen)
                                else String(bytes, p, textLen, Charsets.UTF_8)
                                if (!text.isNullOrBlank()) {
                                    rawResult = text
                                    break
                                }
                            }
                        }
                    }
                }
                idx += 8 + len + 4
            }
        }

        // 2. Check JPEG (0xFF, 0xD8)
        if (rawResult.isNullOrBlank() && bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            var p = 2
            while (p + 4 <= bytes.size) {
                if (bytes[p] == 0xFF.toByte()) {
                    val marker = bytes[p + 1].toInt() and 0xFF
                    val segLen = ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
                    if (marker == 0xFE && segLen > 2 && p + 2 + segLen <= bytes.size) { // COM (Comment)
                        val comment = String(bytes, p + 4, segLen - 2, Charsets.UTF_8).trim()
                        if (comment.isNotBlank()) {
                            rawResult = comment
                            break
                        }
                    }
                    if (marker == 0xE1 && segLen > 6 && p + 2 + segLen <= bytes.size) { // APP1 (Exif / XMP)
                        val segContent = String(bytes, p + 4, segLen - 2, Charsets.ISO_8859_1)
                        if ("\"app\":\"Nightmare\"" in segContent || "Steps:" in segContent) {
                            rawResult = segContent
                            break
                        }
                    }
                    p += 2 + segLen
                } else {
                    p++
                }
            }
        }

        // 3. Fallback: scanning raw binary for JSON signature or A1111 Steps
        if (rawResult.isNullOrBlank()) {
            val rawStr = String(bytes, Charsets.ISO_8859_1)
            val nmIdx = rawStr.indexOf("{\"app\":\"Nightmare\"")
            if (nmIdx >= 0) {
                val endIdx = rawStr.indexOf("}", nmIdx)
                if (endIdx > nmIdx) {
                    rawResult = String(bytes.sliceArray(nmIdx..endIdx), Charsets.UTF_8)
                }
            }
        }

        return rawResult
    }

    /**
     * Normalizes metadata extracted from Nightmare, WebUI (A1111), ComfyUI, etc. into a standard Nightmare JSON.
     */
    fun normalizeMetadata(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val parsedObj = runCatching { org.json.JSONObject(trimmed) }.getOrNull()
            if (parsedObj != null) {
                if (parsedObj.has("positive") || parsedObj.has("prompt") || parsedObj.has("modelId")) {
                    if (!parsedObj.has("positive") && parsedObj.has("prompt")) {
                        parsedObj.put("positive", parsedObj.getString("prompt"))
                    }
                    return parsedObj.toString()
                }
            }
        }
        // Try parsing A1111 / WebUI format
        if (trimmed.contains("Steps:") && (trimmed.contains("Sampler:") || trimmed.contains("Size:"))) {
            val a1111 = parseA1111(trimmed)
            if (a1111 != null) return a1111.toString()
        }
        // Fallback: put string directly into positive prompt
        return org.json.JSONObject().put("positive", trimmed).toString()
    }

    private fun parseA1111(text: String): org.json.JSONObject? = runCatching {
        val obj = org.json.JSONObject()
        val lines = text.trim().lines()
        val posLines = mutableListOf<String>()
        val negLines = mutableListOf<String>()
        var inNeg = false
        var paramLine = ""
        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("Negative prompt:")) {
                inNeg = true
                negLines.add(l.removePrefix("Negative prompt:").trim())
            } else if (l.contains("Steps:") && (l.contains("Sampler:") || l.contains("CFG scale:"))) {
                paramLine = l
                break
            } else if (inNeg) {
                negLines.add(line)
            } else {
                posLines.add(line)
            }
        }
        val pos = posLines.joinToString("\n").trim()
        val neg = negLines.joinToString("\n").trim()
        if (pos.isNotBlank()) obj.put("positive", pos)
        if (neg.isNotBlank()) obj.put("negative", neg)

        Regex("""Steps:\s*(\d+)""").find(paramLine)?.groupValues?.get(1)?.toIntOrNull()?.let { obj.put("steps", it) }
        Regex("""CFG scale:\s*([\d.]+)""").find(paramLine)?.groupValues?.get(1)?.toDoubleOrNull()?.let { obj.put("cfg", it) }
        Regex("""Seed:\s*(\d+)""").find(paramLine)?.groupValues?.get(1)?.toLongOrNull()?.let { obj.put("seed", it) }
        Regex("""Size:\s*(\d+)x(\d+)""").find(paramLine)?.let {
            obj.put("width", it.groupValues[1].toInt())
            obj.put("height", it.groupValues[2].toInt())
        }
        Regex("""Sampler:\s*([^,\n]+)""").find(paramLine)?.groupValues?.get(1)?.trim()?.let { obj.put("scheduler", it) }
        Regex("""Model:\s*([^,\n]+)""").find(paramLine)?.groupValues?.get(1)?.trim()?.let { obj.put("modelId", it) }
        obj
    }.getOrNull()
}

/** Finished PNG → Pictures/Nightmare via MediaStore; with embedded ComfyUI-like parameter metadata. */
fun saveShot(
    ctx: Context,
    shot: Shot,
    /**
     * ⭐ fix35q — save the ORIGINAL picture instead of the processed one: the
     * upscale SOURCE ([Shot.sourceFile]) when set, else [Shot.file] as-is.
     * Metadata is still spliced in; the post chain is skipped. Driven by the
     * workbench pager sitting on the 原图 page when 保存 was tapped.
     * (Sits BEFORE [post] so a trailing lambda still binds to [post].)
     */
    original: Boolean = false,
    post: ((android.graphics.Bitmap) -> android.graphics.Bitmap)? = null,
) {
    // ⚠ fix35f: this runs on RunController's sideWork lane — a plain background
    // thread with NO Looper. Every Toast here used to be constructed on that
    // thread and died with "Can't toast on a thread that has not called
    // Looper.prepare()" the moment the save finished (crash on save, reported
    // 2026-11-07). Toast must be created and shown on a Looper thread: hop to
    // the main thread for the toast, keep the file I/O where it already is.
    val toast: (String) -> Unit = { msg ->
        android.os.Handler(ctx.mainLooper).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
    try {
        val isVideo = shot.file.name.endsWith(".mp4", ignoreCase = true)
        if (isVideo) {
            val name = "nm-" + System.currentTimeMillis() + ".mp4"
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Nightmare")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("MediaStore insert failed")
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(shot.file).use { it.copyTo(out) }
            }
            toast("视频已保存至相册: Movies/Nightmare/$name")
        } else {
            val name = "nm-" + System.currentTimeMillis() + ".png"
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nightmare")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("MediaStore insert failed")
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                if (original) {
                    // ⭐ fix35q: the 原图 page was showing when 保存 was tapped —
                    // stream the original file byte-wise, metadata spliced in
                    // exactly like the plain path below. No post chain, no
                    // re-encode. The upscale run's original is [sourceFile]
                    // (the run's input), NOT [file] (the engine's ×N output).
                    val src = shot.sourceFile ?: shot.file
                    if (shot.meta != null) {
                        PngMetadata.injectMetadata(src, shot.meta, out)
                    } else {
                        java.io.FileInputStream(src).use { it.copyTo(out) }
                    }
                } else if (post != null) {
                    // ⭐ fix35g: the shot on screen is POST-PROCESSED (colour /
                    // adjust / upscale) but [file] is the raw render. Re-apply
                    // the chain so the gallery copy matches what was on screen.
                    // ⭐ fix35h: compress() re-encodes the PNG from pixels and
                    // DROPS the tEXt parameter chunk — the gallery copy then
                    // reads back with no parameters (the import-params
                    // regression, reported 2026-11-08). Encode to a cache file
                    // first, then splice the metadata in byte-wise exactly like
                    // the plain path below.
                    val raw = android.graphics.BitmapFactory.decodeFile(shot.file.absolutePath)
                        ?: throw IllegalStateException("the render file could not be decoded")
                    val processed = post(raw)
                    val tmp = java.io.File(ctx.cacheDir, "nm-save-" + System.currentTimeMillis() + ".png")
                    try {
                        java.io.FileOutputStream(tmp).use { fo ->
                            processed.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fo)
                        }
                        if (shot.meta != null) {
                            PngMetadata.injectMetadata(tmp, shot.meta, out)
                        } else {
                            tmp.inputStream().use { it.copyTo(out) }
                        }
                    } finally {
                        tmp.delete()
                    }
                } else if (shot.meta != null) {
                    PngMetadata.injectMetadata(shot.file, shot.meta, out)
                } else {
                    java.io.FileInputStream(shot.file).use { it.copyTo(out) }
                }
            }
            toast(ctx.getString(R.string.newui_saved, "Pictures/Nightmare/$name"))
        }
    } catch (e: Exception) {
        toast("save: " + e.message)
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
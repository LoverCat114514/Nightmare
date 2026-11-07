package com.abrah.nightmare.newui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.LoraSources
import com.abrah.nightmare.ModelCatalog
import com.abrah.nightmare.R
import com.abrah.nightmare.SelectedModel
import com.abrah.nightmare.newui.card.ModeCard
import com.abrah.nightmare.newui.card.ModelCard
import com.abrah.nightmare.newui.card.NmCard
import com.abrah.nightmare.newui.card.ParamsCard
import com.abrah.nightmare.newui.card.ResultCard
import com.abrah.nightmare.newui.card.CardTitle
import com.abrah.nightmare.newui.core.Engine
import com.abrah.nightmare.newui.core.RunController
import com.abrah.nightmare.newui.core.Shot
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.core.buildRequest
import com.abrah.nightmare.newui.core.modelLabel
import com.abrah.nightmare.newui.core.queryDisplayName
import com.abrah.nightmare.newui.core.saveShot
import com.abrah.nightmare.newui.core.uriToDataUrl
import com.abrah.nightmare.newui.theme.Nm
import com.abrah.nightmare.ui.BOTTOM_BAR_RESERVE
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ⭐⭐ fix34 (2026-11-06) — the workbench, rebuilt.
 *
 * ## The contract this refactor is held to
 *
 * The person's requirement, verbatim: **keep the layout and the look, change
 * only the machinery underneath** ("保留目前ui的布局，风格，或者说全部保存，但
 * 前提是要重写底层逻辑"), and **no piling up motion code that costs frames**
 * ("动效就按照目前的动效想法来，但前提是不要堆砌无用代码造成卡顿").
 *
 * So this screen draws the same five cards in the same order with the same
 * padding, the same colours ([com.abrah.nightmare.newui.theme.Nm] is
 * byte-identical to the old palette) and the same strings. What changed is
 * everything a person cannot see:
 *
 * | Old | New |
 * |---|---|
 * | six file-level singletons (`RUN_PROGRESS`, `RUN_LOG`, `RUN_CANCEL`, `LAST_SHOT`(+VERSION), `SESSION`(+VERSION), `NewUiRun.busy`) | [RunController] owns the phase + cancel; the screen owns the shot and the history as ordinary state |
 * | progress polled from atomics every 100 ms | [RunController.phase] is a `StateFlow` the screen collects — no loop, no 100 ms floor |
 * | `loras` a plain list + hand-bumped `modelVer` in six places | every field of [WbParams] is `mutableStateOf`; nothing to bump |
 * | a 1998-line file | five cards, one screen, four core files, one motion table |
 * | `remember { mutableStateOf(s.x) }` mirrors of state | reads go straight to state |
 *
 * ## What is deliberately unchanged
 *
 * - **The signature.** `NewUiScreen(onBrowseLoras, onDeleteLora)` is called from
 *   `MainActivity.kt` and that call is not being touched.
 * - **The scroll container is a `LazyColumn` with keys per card** — that was a
 *   fix31 improvement (viewport-only composition) and it stays.
 * - **The page insets go on `contentPadding`, not `Modifier.padding`** — padding
 *   on a lazy list would clip the scrolled content's edge.
 */
@Composable
fun NewUiScreen(
    onBrowseLoras: ((LoraSources.Target) -> Unit)? = null,
    onDeleteLora: ((String) -> Unit)? = null,
    onSaveWorkflow: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current

    // ── The state, owned by this screen ────────────────────────────────────
    //
    // ⭐ The workbench's own pick wins on cold start: the Default UI's init
    // re-selects ITS default into SelectedModel, and blindly copying that here
    // clobbered the saved model (a restart ended on MiaoMiao while the
    // checkpoint showed MOSS). Follow the global only when newui has none.
    val s = remember {
        WbParams.load(ctx).apply {
            if (modelId.isBlank() || ModelCatalog.byId(modelId) == null) {
                modelId = SelectedModel.spec.id
            }
        }
    }

    var positive by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf("") }
    var srcDataUrl by remember { mutableStateOf<String?>(null) }
    var srcName by remember { mutableStateOf("") }

    // The one finished shot on show, and this session's strip. ⭐ Ordinary
    // state, not an `AtomicReference` plus a version counter: a write is a
    // recomposition, and there is no way to forget a bump.
    var shot by remember { mutableStateOf<Shot?>(null) }
    var history by remember { mutableStateOf<List<Shot>>(emptyList()) }

    // The engine and the run's lifecycle. ⚠ `remember` + `DisposableEffect`:
    // [RunController.dispose] cancels the scope, which is the teardown the old
    // `object` singletons could not perform.
    val engine = remember { Engine(ctx) }
    val run = remember { RunController(ctx) }
    DisposableEffect(run) {
        onDispose { run.dispose() }
    }
    val phase by run.phase.collectAsState()

    // ── The saved parameters ──────────────────────────────────────────────
    //
    // ⭐ Debounced, and keyed on the states themselves. The old version ran
    // `saveState` with NO delay on a `modelVer` key that the width/height
    // sliders bumped every frame — a whole prefs transaction per drag frame on
    // the main thread, which saturated it and made taps land late or never.
    // Every field is state now, so one effect keyed on them all saves 600 ms
    // after the LAST change: one save per burst, off the drag's path.
    LaunchedEffect(
        s.modelId, s.scheduler, s.loras, s.w, s.h, s.steps, s.cfg, s.seed,
        s.fixedSeed, s.denoise, s.mossOn, s.mode, s.autosave, s.pUps, s.pUps2,
        s.pFace, s.pColor, s.adjBr, s.adjCo, s.adjSa, s.warm, s.adjOn, s.pHands,
    ) {
        delay(600)
        WbParams.save(ctx, s)
    }

    // The global follows the workbench's restored pick (symmetric with the
    // picker's double write), so the engine and the Default UI agree.
    // Also warm up installed catalog cache in the background IO thread.
    LaunchedEffect(Unit) {
        if (SelectedModel.spec.id != s.modelId) {
            runCatching { SelectedModel.set(ctx, s.modelId) }
        }
        withContext(Dispatchers.IO) {
            runCatching { ModelCatalog.refreshInstalled(ctx) }
        }
    }

    LaunchedEffect(WbParams.lastPickedMode) {
        val picked = WbParams.lastPickedMode
        if (picked != null) {
            s.mode = picked
            WbParams.lastPickedMode = null
        }
    }

    // ── LoRA import ───────────────────────────────────────────────────────
    var loraEpoch by remember { mutableIntStateOf(0) }
    val loraImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri)
                ?: "lora-" + System.currentTimeMillis() + ".safetensors"
            // ⚠ The copy runs on the SIDE lane, not the render lane: an import
            // tapped mid-generation must not wait for the picture. And [loraEpoch]
            // is bumped AFTER the copy, so the sheet's re-scan sees the file —
            // bumping it on the UI thread first made the picker scan before the
            // write landed and the fresh import stayed invisible.
            run.runSideWork {
                try {
                    val dst = File(
                        com.abrah.nightmare.BackendProcess.lorasDir(ctx),
                        name,
                    )
                    ctx.contentResolver.openInputStream(uri)?.use { inp ->
                        dst.outputStream().use { inp.copyTo(it) }
                    }
                } catch (_: Exception) {
                } finally {
                    loraEpoch++
                }
            }
        }
    }

    // ── The generate tap ──────────────────────────────────────────────────
    //
    // ⭐ Cancellation and starting are the SAME handler, switched on
    // `phase.running` — one button, one handler, so there is no second control
    // that can disagree with the first.
    val onGenerate: () -> Unit = generate@{
        if (phase.running) {
            run.cancel()
            return@generate
        }
        // The saved picker may be blank (first launch): the family's first entry.
        if (ModelCatalog.byId(s.modelId) == null) s.modelId = SelectedModel.spec.id
        if (s.scheduler.isBlank()) {
            s.scheduler = ModelCatalog.schedulersFor(SelectedModel.spec.family)
                .firstOrNull().orEmpty()
        }

        val req = try {
            buildRequest(s, positive, negative, srcDataUrl)
        } catch (e: Exception) {
            run.note(e.message ?: "the request is incomplete")
            return@generate
        }

        // ⚠ Persist BEFORE the run, synchronously: the debounced save above
        // would otherwise fire during the render, and a person who leaves the
        // screen mid-run should still come back to what they asked for.
        WbParams.save(ctx, s)

        run.start { onStep, isCancelled ->
            // The engine blocks, and [RunController.start] has already moved
            // this to its own single-threaded lane.
            val result = engine.run(
                req = req,
                onStep = onStep,
                onLog = { line -> run.note(line) },
                isCancelled = isCancelled,
            )
            if (result is RunController.Result.Success) {
                val f = result.file
                val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                if (bmp == null) {
                    RunController.Result.Failure("the result could not be decoded")
                } else {
                    var out = bmp
                    if (s.pColor) out = autoColour(out)
                    if (s.adjOn) out = applyAdjust(out, s)
                    // ⭐ The upscale switches: the main row's x4 and the
                    // More-options x2. Both may be on — they stack; each hint
                    // says what it does.
                    if (s.pUps) {
                        run.note("post-processing…")
                        out = android.graphics.Bitmap.createScaledBitmap(
                            out, out.width * 4, out.height * 4, true,
                        )
                    }
                    if (s.pUps2) {
                        run.note("post-processing…")
                        out = android.graphics.Bitmap.createScaledBitmap(
                            out, out.width * 2, out.height * 2, true,
                        )
                    }
                    val metaJson = buildMetaJson(s, positive, negative, s.seed)
                    val made = Shot(out.asImageBitmap(), System.currentTimeMillis(), s.seed, f, meta = metaJson)
                    shot = made
                    if (s.autosave) {
                        // ⭐ A NEW list, capped at 12 like the app's own result
                        // pages. Never an in-place mutation of a state list.
                        history = (listOf(made) + history).take(12)
                    }
                    result
                }
            } else {
                result
            }
        }
    }

    // ── The apply-now post tools ──────────────────────────────────────────
    //
    // ⭐ On the SIDE lane, not the render lane: an "apply now" tapped during a
    // generation used to queue behind the blocking `core.run` and read as a dead
    // button. The tool re-renders from the ORIGINAL file (no compounding).
    val applyPost: (Int) -> Unit = { tool ->
        val cur = shot
        if (cur != null) {
            run.runSideWork {
                val src = android.graphics.BitmapFactory.decodeFile(cur.file.absolutePath)
                if (src != null) {
                    val out = when (tool) {
                        // ⭐ x4 — the Default UI's apply-now upscale is x4.
                        0 -> android.graphics.Bitmap.createScaledBitmap(
                            src, src.width * 4, src.height * 4, true,
                        )
                        1 -> autoColour(src)
                        2 -> applyAdjust(src, s)
                        // The More-options quick enlarge: a lighter bilinear x2.
                        else -> android.graphics.Bitmap.createScaledBitmap(
                            src, src.width * 2, src.height * 2, true,
                        )
                    }
                    shot = Shot(out.asImageBitmap(), cur.ms, cur.seed, cur.file, meta = cur.meta)
                }
            }
        }
    }

    val onImportImageParamsCallback = remember(ctx) {
        { uri: android.net.Uri ->
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val meta = com.abrah.nightmare.newui.core.PngMetadata.extractMetadata(stream)
                    if (!meta.isNullOrBlank()) {
                        val obj = org.json.JSONObject(meta)
                        if (obj.has("modelId")) s.modelId = obj.getString("modelId")
                        if (obj.has("scheduler")) s.scheduler = obj.getString("scheduler")
                        if (obj.has("positive")) positive = obj.getString("positive")
                        if (obj.has("negative")) negative = obj.getString("negative")
                        if (obj.has("steps")) s.steps = obj.getInt("steps")
                        if (obj.has("cfg")) s.cfg = obj.getDouble("cfg").toFloat()
                        if (obj.has("seed")) {
                            s.seed = obj.getLong("seed")
                            s.fixedSeed = true
                        }
                        if (obj.has("width")) s.w = obj.getInt("width")
                        if (obj.has("height")) s.h = obj.getInt("height")
                        if (obj.has("denoise")) s.denoise = obj.getDouble("denoise").toFloat()
                        if (obj.has("mode")) s.mode = obj.getString("mode")
                        if (obj.has("loras")) {
                            val loraStr = obj.getString("loras")
                            if (loraStr.isNotBlank()) {
                                s.loras = com.abrah.nightmare.LoraSpec.parse(
                                    loraStr.split(",").joinToString(" ") { "<lora:$it>" }
                                ).map { com.abrah.nightmare.newui.core.LoraPick(it.name, it.strength.toFloat()) }
                            }
                        }
                        if (obj.has("pUps")) s.pUps = obj.getBoolean("pUps")
                        if (obj.has("pUps2")) s.pUps2 = obj.getBoolean("pUps2")
                        if (obj.has("pFace")) s.pFace = obj.getBoolean("pFace")
                        if (obj.has("pHands")) s.pHands = obj.getBoolean("pHands")
                        if (obj.has("pColor")) s.pColor = obj.getBoolean("pColor")
                        if (obj.has("adjOn")) s.adjOn = obj.getBoolean("adjOn")
                        if (obj.has("adjBr")) s.adjBr = obj.getInt("adjBr")
                        if (obj.has("adjCo")) s.adjCo = obj.getInt("adjCo")
                        if (obj.has("adjSa")) s.adjSa = obj.getInt("adjSa")
                        if (obj.has("warm")) s.warm = obj.getInt("warm")
                        android.widget.Toast.makeText(ctx, "已成功从图片中解析并恢复全部参数！", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(ctx, "未在该图片中检测到 Nightmare 参数元数据", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                android.widget.Toast.makeText(ctx, "解析图片参数失败: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
            Unit
        }
    }

    val listState = rememberLazyListState()
    val family = ModelCatalog.byId(s.modelId)?.family ?: SelectedModel.spec.family

    // ⭐ Stable callbacks: memoized with remember so child cards skip recomposition
    // during scrolling or unrelated state changes.
    val onPickSrcCallback = remember(ctx) {
        { uri: android.net.Uri?, name: String ->
            srcDataUrl = uri?.let { uriToDataUrl(ctx, it) }
            srcName = name
        }
    }
    val onSaveCallback = remember(ctx) {
        {
            shot?.let { saveShot(ctx, it) }
            Unit
        }
    }
    val onCancelCallback = remember(run) {
        { run.cancel() }
    }
    val onDraftCallback = remember {
        { text: String -> positive = text }
    }
    val onDraftSizeCallback = remember(s) {
        { w: Int, h: Int -> s.w = w; s.h = h }
    }
    val onPickedModelCallback = remember(s) {
        { id: String -> s.modelId = id }
    }
    val onImportLoraCallback = remember(loraImport) {
        { loraImport.launch(arrayOf("application/octet-stream", "*/*")) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        // ⚠ `contentPadding`, not `Modifier.padding`: a lazy list must apply the
        // page insets to the VIEWPORT, or the scrolled content's edge clips.
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = BOTTOM_BAR_RESERVE + 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Card 1: the result ────────────────────────────────────────────
        item(key = "result") {
            ResultCard(
                ctx = ctx,
                phase = phase,
                shot = shot,
                history = history,
                srcName = srcName,
                mode = s.mode,
                statusText = when {
                    phase.error != null -> phase.error!!
                    phase.running && phase.total > 0 -> "生成中 ${phase.step}/${phase.total} (${(phase.fraction!! * 100).toInt()}%)"
                    phase.running -> phase.message.ifBlank { "正在生成…" }
                    phase.message.isNotBlank() -> phase.message
                    else -> stringResource(R.string.newui_ready)
                },
                modelLabel = modelLabel(s.modelId),
                onPickSrc = onPickSrcCallback,
                onSave = onSaveCallback,
                onSaveWorkflow = onSaveWorkflow,
                onImportImageParams = onImportImageParamsCallback,
                onRun = onGenerate,
                onCancel = onCancelCallback,
            )
        }

        // ── Card 2: the mode ──────────────────────────────────────────────
        item(key = "mode") {
            ModeCard(
                s = s,
                family = family,
                onDraft = onDraftCallback,
                onDraftSize = onDraftSizeCallback,
            )
        }

        // ── Card 3: the prompts ───────────────────────────────────────────
        item(key = "prompts") {
            PromptsCard(
                positive = positive,
                negative = negative,
                onPositiveChange = { positive = it },
                onNegativeChange = { negative = it },
            )
        }

        // ── Card 4: the model ─────────────────────────────────────────────
        item(key = "model") {
            ModelCard(
                ctx = ctx,
                s = s,
                modelId = s.modelId,
                family = family,
                loraEpoch = loraEpoch,
                onPicked = onPickedModelCallback,
                onImportLora = onImportLoraCallback,
                onBrowseLoras = onBrowseLoras,
                onDeleteLora = onDeleteLora,
            )
        }

        // ── Card 5: the parameters ────────────────────────────────────────
        item(key = "params") {
            ParamsCard(
                ctx = ctx,
                s = s,
                family = family,
                onPicked = {},
                onApply = applyPost,
            )
        }
    }
}

/**
 * ⭐ The prompts card: positive and negative prompt text fields, isolated in its
 * own composable to keep text-input styling consistent and clean with the Nm theme.
 */
@Composable
fun PromptsCard(
    positive: String,
    negative: String,
    onPositiveChange: (String) -> Unit,
    onNegativeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NmCard(modifier = modifier) {
        CardTitle(stringResource(R.string.newui_prompt))
        OutlinedTextField(
            value = positive,
            onValueChange = onPositiveChange,
            label = { Text(stringResource(R.string.newui_positive)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            placeholder = { Text("1girl, solo, ...") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri,
                unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri,
                unfocusedLabelColor = Nm.sub,
                cursorColor = Nm.pri,
                focusedTextColor = Nm.ink,
                unfocusedTextColor = Nm.ink,
            ),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = negative,
            onValueChange = onNegativeChange,
            label = { Text(stringResource(R.string.newui_negative)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri,
                unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri,
                unfocusedLabelColor = Nm.sub,
                cursorColor = Nm.pri,
                focusedTextColor = Nm.ink,
                unfocusedTextColor = Nm.ink,
            ),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

// ── The post-processing maths ──────────────────────────────────────────────
//
// ⭐ Moved out of the panel file but NOT rewritten: these are image operations
// whose exact constants a person tuned against the engine's output. A refactor
// that "improves" a colour matrix changes what a picture looks like.

/**
 * The Default UI's auto colour correction: a per-channel percentile stretch —
 * the darks go darker, the brights brighter, a flat cast washes out — with the
 * gain capped so a near-flat picture is not amplified into noise.
 */
internal fun autoColour(src: android.graphics.Bitmap): android.graphics.Bitmap {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val step = maxOf(1, px.size / 80000)
    val rh = IntArray(256); val gh = IntArray(256); val bh = IntArray(256)
    var n = 0
    for (i in px.indices step step) {
        val p = px[i]
        rh[(p shr 16) and 0xFF]++
        gh[(p shr 8) and 0xFF]++
        bh[p and 0xFF]++
        n++
    }
    fun bounds(hist: IntArray): Pair<Int, Int> {
        val loN = (n * 0.004).toInt()
        val hiN = (n * 0.996).toInt()
        var acc = 0
        var lo = 0
        var hi = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= loN) { lo = v; break } }
        acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc >= hiN) { hi = v; break } }
        return lo to hi
    }
    val (rLo, rHi) = bounds(rh)
    val (gLo, gHi) = bounds(gh)
    val (bLo, bHi) = bounds(bh)
    fun gain(lo: Int, hi: Int): Float = (255f / (hi - lo).coerceAtLeast(8)).coerceIn(1f, 2.0f)
    val rg = gain(rLo, rHi); val gg = gain(gLo, gHi); val bg = gain(bLo, bHi)
    val m = android.graphics.ColorMatrix(
        floatArrayOf(
            rg, 0f, 0f, 0f, -rLo * rg,
            0f, gg, 0f, 0f, -gLo * gg,
            0f, 0f, bg, 0f, -bLo * bg,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(
        src, 0f, 0f,
        android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(m) },
    )
    return out
}

/**
 * The manual sliders: brightness → contrast → saturation → warmth, the ±60
 * maths the page used. Warmth: below 0 cooler (toward blue), above 0 warmer.
 */
internal fun applyAdjust(src: android.graphics.Bitmap, s: WbParams): android.graphics.Bitmap {
    if (s.adjBr == 0 && s.adjCo == 0 && s.adjSa == 0 && s.warm == 0) return src
    val br = android.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, s.adjBr * 2.55f,
            0f, 1f, 0f, 0f, s.adjBr * 2.55f,
            0f, 0f, 1f, 0f, s.adjBr * 2.55f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val c = 1f + s.adjCo / 100f
    val off = 127.5f * (1f - c)
    val co = android.graphics.ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, off,
            0f, c, 0f, 0f, off,
            0f, 0f, c, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val sa = android.graphics.ColorMatrix().apply { setSaturation(1f + s.adjSa / 100f) }
    val wf = s.warm / 100f
    val wm = android.graphics.ColorMatrix(
        floatArrayOf(
            1f + wf * 0.10f, 0f, 0f, 0f, wf * 6f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - wf * 0.10f, 0f, -wf * 6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val m = android.graphics.ColorMatrix().apply {
        setConcat(br, co); setConcat(this, sa); setConcat(this, wm)
    }
    val out = android.graphics.Bitmap.createBitmap(
        src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888,
    )
    android.graphics.Canvas(out).drawBitmap(
        src, 0f, 0f,
        android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(m) },
    )
    return out
}
/**
 * ⭐ Builds JSON metadata of all current generation parameters to embed into PNG tEXt chunks.
 * ComfyUI-style parameter embedding.
 */
private fun buildMetaJson(s: WbParams, positive: String, negative: String, seed: Long): String {
    val obj = org.json.JSONObject().apply {
        put("app", "Nightmare")
        put("version", 1)
        put("modelId", s.modelId)
        put("scheduler", s.scheduler)
        put("positive", positive)
        put("negative", negative)
        put("steps", s.steps)
        put("cfg", s.cfg.toDouble())
        put("seed", seed)
        put("width", s.w)
        put("height", s.h)
        put("denoise", s.denoise.toDouble())
        put("mode", s.mode)
        put("loras", s.loras.joinToString(",") { "${it.name}:${it.weight}" })
        put("pUps", s.pUps)
        put("pUps2", s.pUps2)
        put("pFace", s.pFace)
        put("pHands", s.pHands)
        put("pColor", s.pColor)
        put("adjOn", s.adjOn)
        put("adjBr", s.adjBr)
        put("adjCo", s.adjCo)
        put("adjSa", s.adjSa)
        put("warm", s.warm)
    }
    return obj.toString()
}

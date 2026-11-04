/*
 * [LOCAL-NEWUI 2026-11-05] The fourth view (the web workbench), REWRITTEN as native Compose.
 * A user-added local addition, not upstream: it rides the canvas slot — the top-left toggle,
 * Settings → Interface mode, the view memory — like AGENT/NODES/GRAPH do.
 *
 * ⭐⭐ NO HTTP, NO WEBVIEW, NO BRIDGE. The first cut ported the user's browser workbench
 * (nm-workbench.html) behind a JS bridge; the user pulled the plug: same shape, but written
 * the way the app's own three views are — direct engine calls, Material 3, the system theme.
 * (The HTML asset stays in the APK untouched, only unused.)
 *
 * Shape (the workbench's, kept): prompt card · model card (checkpoint + ONE scheduler
 * dropdown + LoRA chips + size/steps/cfg/seed) · txt2img/img2img mode + source + denoise ·
 * MOSS layout row · results strip + save · the run button pinned above the bottom bar.
 *
 * Engine contract (verified in source):
 *   ApiCore(ctx, HarnessOps(ctx, sink), holder="newui") — run() blocks → executor; RunLock
 *     keeps it exclusive with canvas/api (409). RunRequest knobs are STRING maps; steps/cfg
 *     stay OUT on purpose (the model supplies them — pinning burns distilled checkpoints);
 *     scheduler IS sampler+schedule (dpm3_sde_karras); loras = "name.safetensors@0.8, …".
 *   params["generate"]["moss_layout"]="true" — SdSampler.MOSS_LAYOUT; family from
 *     SelectedModel.spec.family (the node editor's own visibility source).
 *   RunResult.answer.results[].url = /results/<run>/<file> → core.resultFileOf → decode.
 */
package com.abrah.nightmare.newui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.HarnessOps
import com.abrah.nightmare.ModelCatalog
import com.abrah.nightmare.R
import com.abrah.nightmare.SelectedModel
import com.abrah.nightmare.SdSampler
import com.abrah.nightmare.api.ApiCore
import com.abrah.nightmare.api.ApiError
import com.abrah.nightmare.api.RunRequest
import com.abrah.nightmare.canvas.MossCanvasActivity
import com.abrah.nightmare.ui.BOTTOM_BAR_RESERVE
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import com.abrah.nightmare.BackendProcess
import com.abrah.nightmare.ModelSpec
import com.abrah.nightmare.Res


/** Live progress of the executor's run, if any (step, total). */
private val RUN_PROGRESS = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)

/** The engine's log line while a run is on, if any. */
private val RUN_LOG = java.util.concurrent.atomic.AtomicReference<String?>(null)

/** Set when the person asks to cancel; the run thread calls [ApiCore.cancel] on it. */
@Volatile private var RUN_CANCEL = false

/** The [ApiCore] for the NEWUI view. Built once; holder "newui" so RunLock scopes it. */
private fun core(ctx: Context): ApiCore = CoreHolder.get(ctx)

/** Holds the engine so the screen's recompositions never rebuild it. */
private object CoreHolder {
    @Volatile private var core: ApiCore? = null
    fun get(ctx: Context): ApiCore =
        core ?: synchronized(this) {
            core ?: ApiCore(
                ctx.applicationContext,
                HarnessOps(ctx.applicationContext, object : HarnessOps.Sink {
                    override fun say(text: String, bad: Boolean) {
                        RUN_LOG.set(if (bad) "⚠ $text" else text)
                    }

                    override fun progress(stepOfTotal: Pair<Int, Int>?) {
                        RUN_PROGRESS.set(stepOfTotal)
                    }
                }),
                holder = "newui",
            ).also { core = it }
        }
}

/** The workbench's saved choices (the HTML page's localStorage, as Prefs-shaped app data). */
private class WbState {
    var modelId: String = ""
    var scheduler: String = ""
    var loras: MutableList<Pair<String, Float>> = mutableListOf()
    var w: Int = 832
    var h: Int = 1216
    var steps: Int = 29
    var cfg: Float = 5.0f
    var seed: Long = 0L
    var fixedSeed: Boolean = false
    var denoise: Float = 0.40f
    var mossOn: Boolean = false
    var mode: String = "txt2img"
    // The post-processing block, the HTML page's S.pUps/pFace/pColor/adj*.
    var autosave: Boolean = true
    var pUps: Boolean = false
    var pFace: Boolean = false // placeholder: the engine has no face endpoint
    var pColor: Boolean = false
    var adjBr: Int = 0
    var adjCo: Int = 0
    var adjSa: Int = 0
    var warm: Int = 0 // the warmth slider (±60), with the colour adjust
    var adjOn: Boolean = false // the adjust-colour row's per-run switch
    var pHands: Boolean = false // placeholder, like pFace
}

/** The single background thread [ApiCore.run] lives on (it blocks). */
private val RUN_EX = Executors.newSingleThreadExecutor()

/** The workbench's prefs file — same store the app's own screens use. */
private const val WB_PREFS = "newui_wb"

/** Load the saved choices once per composition; [remember] keeps the instance. */
private fun loadState(ctx: Context): WbState {
    val p = ctx.getSharedPreferences(WB_PREFS, Context.MODE_PRIVATE)
    return WbState().apply {
        modelId = p.getString("model", "") ?: ""
        scheduler = p.getString("scheduler", "") ?: ""
        loras = p.getString("loras", "")?.split("|")?.filter { it.isNotBlank() }?.map {
            val parts = it.split("@")
            (parts.getOrNull(0) ?: it) to (parts.getOrNull(1)?.toFloatOrNull() ?: 0.8f)
        }?.toMutableList() ?: mutableListOf()
        w = p.getInt("w", 832)
        h = p.getInt("h", 1216)
        steps = p.getInt("steps", 29)
        cfg = p.getFloat("cfg", 5.0f)
        seed = p.getLong("seed", 0L)
        fixedSeed = p.getBoolean("fixedSeed", false)
        denoise = p.getFloat("denoise", 0.40f)
        mossOn = p.getBoolean("mossOn", false)
        mode = p.getString("mode", "txt2img") ?: "txt2img"
        autosave = p.getBoolean("autosave", true)
        pUps = p.getBoolean("pUps", false)
        pFace = p.getBoolean("pFace", false)
        pColor = p.getBoolean("pColor", false)
        adjBr = p.getInt("adjBr", 0)
        adjCo = p.getInt("adjCo", 0)
        adjSa = p.getInt("adjSa", 0)
        warm = p.getInt("warm", 0)
        adjOn = p.getBoolean("adjOn", false)
        pHands = p.getBoolean("pHands", false)
    }
}

/** Persist the choices; the person's sliders and dropdowns survive the view switch. */
private fun saveState(ctx: Context, s: WbState) {
    ctx.getSharedPreferences(WB_PREFS, Context.MODE_PRIVATE).edit()
        .putString("model", s.modelId)
        .putString("scheduler", s.scheduler)
        .putString("loras", s.loras.joinToString("|") { (n, w) -> "$n@$w" })
        .putInt("w", s.w)
        .putInt("h", s.h)
        .putInt("steps", s.steps)
        .putFloat("cfg", s.cfg)
        .putLong("seed", s.seed)
        .putBoolean("fixedSeed", s.fixedSeed)
        .putFloat("denoise", s.denoise)
        .putBoolean("mossOn", s.mossOn)
        .putString("mode", s.mode)
        .putBoolean("autosave", s.autosave)
        .putBoolean("pUps", s.pUps)
        .putBoolean("pFace", s.pFace)
        .putBoolean("pColor", s.pColor)
        .putInt("adjBr", s.adjBr)
        .putInt("adjCo", s.adjCo)
        .putInt("adjSa", s.adjSa)
        .putInt("warm", s.warm)
        .putBoolean("adjOn", s.adjOn)
        .putBoolean("pHands", s.pHands)
        .apply()
}

/** One finished picture of this session: the decoded bitmap plus the meta the strip shows. */
private class Shot(val bitmap: ImageBitmap, val ms: Long, val seed: Long, val file: File)

/** The one shot the strip currently shows (a @Volatile ref the polling loop reads). */
private val LAST_SHOT = java.util.concurrent.atomic.AtomicReference<Shot?>(null)

/** This session's shots, newest first (the strip; capped like the app's own result pages). */
private val SESSION = java.util.concurrent.ConcurrentLinkedDeque<Shot>()

/**
 * Build the [RunRequest] from the screen's state. The rules the app's own runner follows:
 * steps/cfg stay OUT (the model's defaults apply — a recipe that pins them burns distilled
 * checkpoints); scheduler IS the sampler+schedule wire value; loras the AgentTools format;
 * MOSS layout rides `params["generate"]["moss_layout"]`; img2img wires the source into the
 * `image` node as a base64 data URL (ApiCore's upload takes it — `substringAfter("base64,")`).
 */
private fun buildRequest(
    s: WbState,
    prompt: String,
    negative: String,
    srcB64: String?,
): RunRequest {
    val model = ModelCatalog.byId(s.modelId) ?: throw ApiError(400, "no model selected")
    val img2img = s.mode == "img2img"
    val knobs = mutableMapOf<String, String>()
    if (s.scheduler.isNotBlank()) knobs["scheduler"] = s.scheduler
    if (s.w > 0) knobs["width"] = s.w.toString()
    if (s.h > 0) knobs["height"] = s.h.toString()
    if (s.steps > 0) knobs["steps"] = s.steps.toString()
    if (s.cfg > 0f) knobs["cfg"] = s.cfg.toString()
    if (img2img) knobs["denoise"] = s.denoise.toString()
    if (s.fixedSeed && s.seed > 0L) knobs["seed"] = s.seed.toString()
    if (s.loras.isNotEmpty()) {
        knobs["loras"] = s.loras.joinToString(", ") { (n, w) -> "$n.safetensors@$w" }
    }
    val params = if (s.mossOn && model.family == com.abrah.nightmare.Family.MOSS) {
        mapOf("generate" to mapOf(SdSampler.MOSS_LAYOUT to "true"))
    } else emptyMap()
    val images = if (img2img) {
        val b64 = srcB64 ?: throw ApiError(400, "img2img needs a source picture — load one first")
        mapOf("image" to b64)
    } else emptyMap()
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

/** Decode a picked content uri into a data URL (the img2img source travels as base64). */
private fun uriToDataUrl(ctx: Context, uri: Uri): String? = try {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mime = ctx.contentResolver.getType(uri) ?: "image/png"
    "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
} catch (e: Exception) {
    null
}

/** Finished PNG → Pictures/Nightmare via MediaStore; the app's own saver's destination. */
private fun saveShot(context: Context, shot: Shot) {
    try {
        val name = "nm-" + System.currentTimeMillis() + ".png"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nightmare")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("MediaStore insert failed")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            java.io.FileInputStream(shot.file).use { it.copyTo(out) }
        }
        Toast.makeText(context, context.getString(R.string.newui_saved, "Pictures/Nightmare/$name"), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "save: " + e.message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * ⭐⭐⭐ The fourth view's body — the workbench, as NATIVE Compose (2026-11-05).
 *
 * ⭐ THE SHAPE IS THE HTML WORKBENCH'S, card for card, in its order — the user
 * pulled the WebView but kept the page as the standard: the header, the result
 * card (frame · progress+cancel · load/save · the source row · the history),
 * the mode card (txt2img/img2img · denoise · the MOSS block), the prompt card,
 * the model card (checkpoint · LoRA), the parameters card (width/height ·
 * sampler · Steps/CFG sliders · the seed segment · auto-save · the three
 * post-processing rows with the colour sliders), and the footer line — the
 * elapsed time, the model, the GENERATE button and the status line above the
 * app's bottom bar. The implementation is all native: direct engine calls,
 * Material 3, the system theme — no WebView, no bridge, no HTTP.
 *
 * The engine ([ApiCore]) is NOT touched on the UI thread: [RUN_EX] owns it, the
 * screen flips [RUN_BUSY] and polls [RUN_PROGRESS]/[RUN_LOG].
 */
@Composable
fun NewUiScreen() {
    val ctx = LocalContext.current
    // The checkpoint display and the request read ONE source: the modelId state
    // (SelectedModel is @Volatile but no state signal — a plain read here would
    // freeze the label across picks, the user's report).
    // ⭐ (2026-11-05, fifth round) The workbench's OWN pick wins on cold start: the
    // Default UI's init re-selects its default into SelectedModel, and blindly
    // copying it here clobbered the saved model (a restart ended on MiaoMiao but
    // the checkpoint showed MOSS). Follow the global only when newui has none.
    val s = remember {
        loadState(ctx).apply {
            if (modelId.isBlank() || ModelCatalog.byId(modelId) == null) {
                modelId = SelectedModel.spec.id
            }
        }
    }
    var positive by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf("") }
    val busy = remember { mutableStateOf(false) }
    val progTxt = remember { mutableStateOf("") }
    val progFrac = remember { mutableStateOf<Float?>(null) }
    val statusLine = remember { mutableStateOf("") }
    val msLine = remember { mutableStateOf("—") }
    var srcDataUrl by remember { mutableStateOf<String?>(null) }
    var srcName by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf(s.modelId) }
    // The global follows the workbench's restored pick (symmetric with the
    // picker's double write), so the engine and the Default UI agree.
    LaunchedEffect(Unit) {
        if (SelectedModel.spec.id != s.modelId) {
            try { SelectedModel.set(ctx, s.modelId) } catch (_: Exception) {}
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    var modelVer by remember { mutableIntStateOf(0) }
    // ⭐ (2026-11-05, sixth round) Persist on every parameter change: saveState used
    // to run only on Generate, so a pick followed by leaving the view lost it (the
    // model reset itself across a process kill). onPicked() fires from the model
    // card, the size fields and (now) both sampler dropdowns.
    LaunchedEffect(modelVer) { saveState(ctx, s) }
    // The Default UI's APPLY NOW: the tool re-renders the displayed shot from its
    // ORIGINAL file (no compounding), on [RUN_EX]; postVer redraws the frame.
    val postVer = remember { mutableIntStateOf(0) }
    val applyPost: (Int) -> Unit = { tool ->
        val cur = LAST_SHOT.get()
        if (cur != null) {
            RUN_EX.execute {
                val src = BitmapFactory.decodeFile(cur.file.absolutePath) ?: return@execute
                val out = when (tool) {
                    // ⭐ (2026-11-05, fourth round) x4 — the Default UI's apply-now
                    // upscale is x4 (Executor.upscaleTo, NATIVE_SCALE=4); there is no
                    // ESRGAN endpoint from here, so it is a bilinear x4.
                    0 -> android.graphics.Bitmap.createScaledBitmap(src, src.width * 4, src.height * 4, true)
                    1 -> autoColour(src)
                    2 -> applyAdjust(src, s)
                    // The More-options quick enlarge: a lighter bilinear x2.
                    else -> android.graphics.Bitmap.createScaledBitmap(src, src.width * 2, src.height * 2, true)
                }
                LAST_SHOT.set(Shot(out.asImageBitmap(), cur.ms, cur.seed, cur.file))
                postVer.intValue++
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = BOTTOM_BAR_RESERVE + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Card 1: the result ────────────────────────────────────────────
        ResultCard(
            ctx, busy.value, progTxt.value, progFrac.value, srcName, postVer.intValue,
            onPickSrc = { uri, name ->
                srcDataUrl = uri?.let { uriToDataUrl(ctx, it) }
                srcName = name
            },
        )

        // ── Card 2: the mode ──────────────────────────────────────────────
        ModeCard(
            s,
            onDraft = { positive = it },
            onDraftSize = { w, h -> s.w = w; s.h = h; modelVer++ },
        )

        // ── Card 3: the prompts ───────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.newui_prompt), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = positive, onValueChange = { positive = it },
                    label = { Text(stringResource(R.string.newui_positive)) },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    placeholder = { Text("1girl, solo, ...") },
                )
                OutlinedTextField(
                    value = negative, onValueChange = { negative = it },
                    label = { Text(stringResource(R.string.newui_negative)) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            }
        }

        // ── Card 4: the model ─────────────────────────────────────────────
        ModelCard(ctx, s, modelId, onPicked = { id -> modelId = id; s.modelId = id; modelVer++ })

        // ── Card 5: the parameters ────────────────────────────────────────
        ParamsCard(
            ctx, s,
            family = ModelCatalog.byId(modelId)?.family ?: SelectedModel.spec.family,
            onPicked = { modelVer++ },
            onApply = { applyPost(it) },
        )

        // ── The footer line: time · model, then the GENERATE button, then the
        // status — the HTML footer's three rows, padded above the nav pill.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "⏱ ${msLine.value}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.newui_model, modelLabel()),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        val runLabel = when {
            busy.value -> stringResource(R.string.cancel_run)
            else -> stringResource(R.string.newui_generate)
        }
        Button(
            onClick = { onTapGenerate(ctx, s, positive, negative, srcDataUrl, busy, progTxt, progFrac, statusLine, msLine) },
            enabled = true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(runLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
        Text(
            statusLine.value.ifBlank { stringResource(R.string.newui_ready) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // The progress + result poll while the engine is on (the run thread cannot
    // touch state): step text, the fraction for the bar, the log as the status.
    LaunchedEffect(busy.value) {
        while (busy.value) {
            RUN_PROGRESS.get()?.let { (p, t) ->
                progTxt.value = "$p / $t"
                progFrac.value = p.toFloat() / t
            }
            RUN_LOG.get()?.let { statusLine.value = it }
            tick++ // the history strip redraws when a shot lands
            kotlinx.coroutines.delay(100)
        }
    }
}

/** The current checkpoint's label for the footer. */
private fun modelLabel(): String =
    SelectedModel.spec.label

/**
 * Card 1 — the result: the square frame (the finished shot or the empty hint),
 * the progress bar + cancel while the engine runs, the load/save row, the
 * img2img source line, and this session's history strip.
 */
@Composable
private fun ResultCard(
    ctx: Context,
    busy: Boolean,
    progTxt: String,
    progFrac: Float?,
    srcName: String,
    ver: Int,
    onPickSrc: (Uri?, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val shot = LAST_SHOT.get()
                if (shot != null) {
                    Image(
                        bitmap = shot.bitmap, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        stringResource(R.string.newui_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(progTxt.ifBlank { "…" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { RUN_CANCEL = true }) {
                        Text(stringResource(R.string.cancel_run))
                    }
                }
                LinearProgressIndicator(
                    progress = { progFrac ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val pickSrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) onPickSrc(uri, queryDisplayName(ctx, uri) ?: "image")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickSrc.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.newui_load))
                }
                Spacer(Modifier.weight(1f))
                val have = LAST_SHOT.get() != null
                OutlinedButton(onClick = { LAST_SHOT.get()?.let { saveShot(ctx, it) } }, enabled = have) {
                    Text(stringResource(R.string.save))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.newui_src) + ": ",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    srcName.ifBlank { stringResource(R.string.newui_src_empty) },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (srcName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (srcName.isNotBlank()) {
                    TextButton(onClick = { onPickSrc(null, "") }) { Text("✕") }
                }
            }
            val hist = SESSION.toList()
            if (hist.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hist) { sh ->
                        Image(
                            bitmap = sh.bitmap, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card 2 — the mode: the txt2img/img2img segment, the denoise slider (img2img
 * only) and the MOSS block (the switch, the open-canvas button when on).
 */
@Composable
private fun ModeCard(s: WbState, onDraft: (String) -> Unit, onDraftSize: (Int, Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var mode by remember { mutableStateOf(s.mode) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode != "img2img",
                    onClick = { mode = "txt2img"; s.mode = "txt2img" },
                    label = { Text("✦ " + stringResource(R.string.newui_txt2img)) },
                )
                FilterChip(
                    selected = mode == "img2img",
                    onClick = { mode = "img2img"; s.mode = "img2img" },
                    label = { Text("🖼 " + stringResource(R.string.newui_img2img)) },
                )
            }
            if (mode == "img2img") {
                PostSlider(
                    stringResource(R.string.newui_denoise),
                    s.denoise, 0.05f..0.95f, fmt = { String.format("%.2f", it) },
                ) { s.denoise = it }
            }
            var mossOn by remember { mutableStateOf(s.mossOn) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.newui_moss_layout), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.hint_moss_layout),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = mossOn, onCheckedChange = { mossOn = it; s.mossOn = it })
            }
            if (mossOn) {
                val ctx = LocalContext.current
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                    val draft = r.data?.getStringExtra(MossCanvasActivity.EXTRA_DRAFT)
                    val w = r.data?.getIntExtra(MossCanvasActivity.EXTRA_WIDTH, -1) ?: -1
                    val h = r.data?.getIntExtra(MossCanvasActivity.EXTRA_HEIGHT, -1) ?: -1
                    if (r.resultCode == android.app.Activity.RESULT_OK && !draft.isNullOrBlank()) {
                        onDraft(draft)
                        if (w > 0 && h > 0) onDraftSize(w, h)
                    }
                }
                OutlinedButton(
                    onClick = {
                        launcher.launch(
                            Intent(ctx, MossCanvasActivity::class.java)
                                .putExtra(MossCanvasActivity.EXTRA_WIDTH, s.w.toString())
                                .putExtra(MossCanvasActivity.EXTRA_HEIGHT, s.h.toString()),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.moss_open_canvas)) }
            }
        }
    }
}

/**
 * Card 4 — the model: the checkpoint (the app's own INSTALLED-only picker) and
 * the LoRA block (the header + hint, the chips, the picker dialog).
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
private fun ModelCard(ctx: Context, s: WbState, modelId: String, onPicked: (String) -> Unit) {
    val installed = remember { ModelCatalog.installed(ctx) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val picked = ModelCatalog.byId(modelId)
            CheckpointPicker(
                ctx, installed,
                picked?.label ?: SelectedModel.spec.label,
                picked?.family?.label ?: "",
                onPicked = onPicked,
            )
            var picking by remember { mutableStateOf(false) }
            var ver by remember { mutableIntStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.loras_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.newui_loras_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { picking = true }) { Text(stringResource(R.string.newui_choose)) }
            }
            if (ver >= 0 && s.loras.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.loras.forEachIndexed { i, (name, w) ->
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { s.loras.removeAt(i); ver++ }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                name.removeSuffix(".safetensors"),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                String.format("%.2f", w),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            if (picking) {
                AlertDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        TextButton(onClick = { picking = false }) { Text(stringResource(R.string.ok)) }
                    },
                    title = { Text(stringResource(R.string.loras_title)) },
                    text = {
                        Column {
                            val files = BackendProcess.lorasDir(ctx)
                                .listFiles { f -> f.name.endsWith(".safetensors") }
                                ?.map { it.name }?.sorted().orEmpty()
                            if (files.isEmpty()) {
                                Text(stringResource(R.string.newui_pick_unavailable), style = MaterialTheme.typography.bodyMedium)
                            }
                            for (name in files) {
                                val on = s.loras.any { it.first == name }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (on) s.loras.removeAll { it.first == name }
                                        else s.loras.add(name to 0.8f)
                                        ver++
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        name.removeSuffix(".safetensors"),
                                        Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (on) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (on) {
                                        Text(
                                            String.format("%.2f", s.loras.first { it.first == name }.second),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The checkpoint row: read-only field + the same INSTALLED-only picker the app uses. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CheckpointPicker(ctx: Context, installed: List<ModelSpec>, label: String, familyLabel: String, onPicked: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    if (familyLabel.isBlank()) stringResource(R.string.inspector_checkpoint)
                    else stringResource(R.string.inspector_checkpoint) + " · " + familyLabel
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in installed) {
                DropdownMenuItem(
                    text = { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        try { SelectedModel.set(ctx, m.id); onPicked(m.id) } catch (_: Exception) {}
                    },
                )
            }
        }
    }
}

/**
 * Card 5 — the parameters: the width×height grid (two number fields + the 64
 * snap hint), ONE sampler dropdown (the wire value IS sampler+schedule — the
 * HTML page's sampler+schedule pair was one control), the Steps/CFG sliders,
 * the seed segment (random / fixed + the field), auto-save, and the three
 * post-processing rows with the colour slider panel.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ParamsCard(
    ctx: Context,
    s: WbState,
    family: com.abrah.nightmare.Family,
    onPicked: () -> Unit,
    onApply: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.newui_params), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(stringResource(R.string.knob_width), s.w, onCommit = {
                    s.w = snap64(it); onPicked()
                }, Modifier.weight(1f))
                NumField(stringResource(R.string.knob_height), s.h, onCommit = {
                    s.h = snap64(it); onPicked()
                }, Modifier.weight(1f))
            }
            // ⭐ (2026-11-05, fourth round) Sliders under the size fields, like
            // Steps/CFG (the user's request 3); the fields stay the precise entry.
            PostSlider(
                stringResource(R.string.knob_width),
                s.w.toFloat(), 256f..2048f,
                fmt = { String.format("%.0f", it) },
            ) { s.w = snap64(it.toInt()); onPicked() }
            PostSlider(
                stringResource(R.string.knob_height),
                s.h.toFloat(), 256f..2048f,
                fmt = { String.format("%.0f", it) },
            ) { s.h = snap64(it.toInt()); onPicked() }
            Text(
                stringResource(R.string.newui_size_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var schedVer by remember { mutableIntStateOf(0) }
            // ⭐ (2026-11-05, second round) The Default UI's split: SAMPLER and SCHEDULE
            // are two pickers over ONE wire value ([splitScheduler]/[joinScheduler]) —
            // the user's image 1. State mirrors for recomposition (a plain write into
            // WbState recomposes nothing); every pick re-splits through schedVer.
            var wire by remember { mutableStateOf(s.scheduler) }
            val (base, sched) = remember(schedVer) {
                ModelCatalog.splitScheduler(if (wire.isBlank()) ModelCatalog.DEFAULT_SCHEDULER else wire)
            }
            LaunchedEffect(family, schedVer) {
                val choices = ModelCatalog.schedulersFor(family)
                if (wire.isBlank() || wire !in choices) {
                    wire = choices.firstOrNull().orEmpty()
                    s.scheduler = wire
                    schedVer++
                }
            }
            // ⭐ (2026-11-05, fourth round) Follow the model's family: picking a model
            // re-runs schedulersFor here — a MOSS pick restores the full
            // sampler+schedule wire, a turbo pick drops to its bare sampler (it used
            // to keep the old family's value until a manual tap — the user's report).
            LaunchedEffect(family) {
                val choices = ModelCatalog.schedulersFor(family)
                if (wire !in choices) {
                    wire = choices.firstOrNull().orEmpty()
                    s.scheduler = wire
                    schedVer++
                }
            }
            DropdownWire(
                label = stringResource(R.string.knob_scheduler),
                current = base,
                choices = remember(family) {
                    ModelCatalog.schedulersFor(family).map { ModelCatalog.splitScheduler(it).first }.distinct()
                },
                textOf = { ModelCatalog.schedulerLabel(it) },
                onPick = { b -> wire = ModelCatalog.joinScheduler(b, sched); s.scheduler = wire; schedVer++; onPicked() },
            )
            // The schedule row: only where the family's sampler actually takes one
            // (Anima's flow-match has none; LCM keeps its own).
            val schedules = remember(family, base) {
                ModelCatalog.schedulersFor(family)
                    .map { ModelCatalog.splitScheduler(it) }
                    .filter { it.first == base }
                    .map { it.second }
                    .distinct()
            }
            if (schedules.size > 1 && ModelCatalog.schedulesSupported(base)) {
                // stringResource is @Composable: build the names in composition,
                // not inside a remember calculation.
                val schedNames = mapOf(
                    "" to stringResource(R.string.schedule_normal),
                    "_karras" to stringResource(R.string.schedule_karras),
                    "_exponential" to stringResource(R.string.schedule_exponential),
                    "_sgm_uniform" to stringResource(R.string.schedule_sgm_uniform),
                    "_bong_tangent" to stringResource(R.string.schedule_bong_tangent),
                    "_simple" to stringResource(R.string.schedule_simple),
                    "_beta" to stringResource(R.string.schedule_beta),
                )
                DropdownWire(
                    label = stringResource(R.string.sampler_schedule),
                    current = sched,
                    choices = schedules,
                    textOf = { schedNames[it] ?: it },
                    onPick = { sfx -> wire = ModelCatalog.joinScheduler(base, sfx); s.scheduler = wire; schedVer++; onPicked() },
                )
            }
            PostSlider(
                stringResource(R.string.knob_steps),
                s.steps.toFloat(), 4f..60f,
                fmt = { String.format("%.0f", it) },
            ) { s.steps = it.toInt() }
            PostSlider(
                stringResource(R.string.knob_cfg),
                s.cfg, 1f..14f,
                fmt = { String.format("%.1f", it) },
            ) { s.cfg = it }

            // The seed segment: random / fixed, the field only when fixed.
            var fixedSeed by remember { mutableStateOf(s.fixedSeed) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !fixedSeed,
                    onClick = { fixedSeed = false; s.fixedSeed = false },
                    label = { Text("✓ " + stringResource(R.string.newui_rand_seed)) },
                )
                FilterChip(
                    selected = fixedSeed,
                    onClick = { fixedSeed = true; s.fixedSeed = true },
                    label = { Text("🔒 " + stringResource(R.string.newui_fixed_seed)) },
                )
            }
            if (fixedSeed) {
                var seedTxt by remember { mutableStateOf(if (s.seed > 0) s.seed.toString() else "") }
                OutlinedTextField(
                    value = seedTxt,
                    onValueChange = { t ->
                        seedTxt = t
                        s.seed = t.filter { it.isDigit() }.take(18).toLongOrNull() ?: 0L
                    },
                    label = { Text(stringResource(R.string.knob_seed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Auto save: the run's shot stays in the history strip.
            var autosave by remember { mutableStateOf(s.autosave) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.newui_autosave), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.newui_autosave_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autosave, onCheckedChange = { autosave = it; s.autosave = it })
            }

            // ── Post-processing: the Default UI's three tools, each with its per-run
            // switch and an APPLY NOW (the user's image 2) — folded like the app's own.
            var postOpen by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.post_processing),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { postOpen = !postOpen }) {
                    Text(if (postOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
                }
            }
            AnimatedVisibility(postOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PostTool(
                        stringResource(R.string.post_upscale_title),
                        stringResource(R.string.newui_p_ups_hint),
                        on = s.pUps,
                        onFlag = { s.pUps = it },
                        actionLabel = stringResource(R.string.post_upscale_now),
                    ) { onApply(0) }
                    PostTool(
                        stringResource(R.string.post_colour_title),
                        stringResource(R.string.post_colour_desc),
                        on = s.pColor,
                        onFlag = { s.pColor = it },
                        actionLabel = stringResource(R.string.post_colour_now),
                    ) { onApply(1) }
                    PostTool(
                        stringResource(R.string.post_adjust_title),
                        stringResource(R.string.post_adjust_desc),
                        on = s.adjOn,
                        onFlag = { s.adjOn = it },
                        actionLabel = stringResource(R.string.post_adjust_now),
                    ) { onApply(2) }
                    AnimatedVisibility(s.adjOn) {
                        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PostSlider(stringResource(R.string.knob_brightness), s.adjBr.toFloat(), -60f..60f, fmt = { String.format("%.0f", it) }) {
                                s.adjBr = it.toInt()
                            }
                            PostSlider(stringResource(R.string.knob_contrast), s.adjCo.toFloat(), -60f..60f, fmt = { String.format("%.0f", it) }) {
                                s.adjCo = it.toInt()
                            }
                            PostSlider(stringResource(R.string.knob_saturation), s.adjSa.toFloat(), -60f..60f, fmt = { String.format("%.0f", it) }) {
                                s.adjSa = it.toInt()
                            }
                            PostSlider(stringResource(R.string.knob_warmth), s.warm.toFloat(), -60f..60f, fmt = { String.format("%.0f", it) }) {
                                s.warm = it.toInt()
                            }
                        }
                    }
                }
            }
            // ── More options: the ×2 upscale, the face/hand fixes, the reset — the
            // Default UI's fold, folded shut by default (the user's image 3).
            var moreOpen by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.more_options),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { moreOpen = !moreOpen }) {
                    Text(if (moreOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
                }
            }
            AnimatedVisibility(moreOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PostTool(
                        stringResource(R.string.knob_enlarge),
                        stringResource(R.string.newui_m_ups_hint),
                        on = false,
                        onFlag = { },
                        withSwitch = false,
                        actionLabel = stringResource(R.string.post_upscale_now),
                    ) { onApply(3) }
                    PostTool(
                        stringResource(R.string.knob_fix_faces),
                        stringResource(R.string.newui_p_face_hint),
                        on = false,
                        onFlag = { },
                        withSwitch = false,
                        actionLabel = stringResource(R.string.newui_todo),
                    ) { }
                    PostTool(
                        stringResource(R.string.knob_fix_hands),
                        stringResource(R.string.newui_p_hands_hint),
                        on = false,
                        onFlag = { },
                        withSwitch = false,
                        actionLabel = stringResource(R.string.newui_todo),
                    ) { }
                    TextButton(onClick = {
                        s.steps = 29; s.cfg = 5.0f
                        s.adjBr = 0; s.adjCo = 0; s.adjSa = 0; s.warm = 0
                        s.pUps = false; s.pColor = false; s.adjOn = false
                        s.pFace = false; s.pHands = false
                        ModelCatalog.schedulersFor(family).firstOrNull()?.let {
                            wire = it; s.scheduler = it; schedVer++
                        }
                        onPicked()
                    }) {
                        Text(stringResource(R.string.detect_reset))
                    }
                }
            }
        }
    }
}

/**
 * One post tool: the title+desc on top, then the per-run switch and the APPLY
 * NOW button — the Default UI's row shape (the user's image 2). The switch
 * mirrors [on] through a keyed remember so a reset recomposes it.
 */
@Composable
private fun PostTool(
    title: String,
    desc: String,
    on: Boolean,
    onFlag: (Boolean) -> Unit,
    actionLabel: String,
    withSwitch: Boolean = true,
    onAction: () -> Unit,
) {
    var onNow by remember(on) { mutableStateOf(on) }
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // ⭐ (2026-11-05, fourth round) withSwitch=false: the More-options entries
        // are one-shot actions — no per-run flag (the user's request 2).
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (withSwitch) {
                Text(stringResource(R.string.post_every_run), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(checked = onNow, onCheckedChange = { onNow = it; onFlag(it) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** One labelled slider row (Steps/CFG and the colour adjust). */
@Composable
private fun PostSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fmt: (Float) -> String,
    onSet: (Float) -> Unit,
) {
    var v by remember(value) { mutableStateOf(value) }
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(fmt(v), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value = v, onValueChange = { v = it; onSet(it) }, valueRange = range)
    }
}

/** A number field (width/height): it keeps its own text, the value lands on commit. */
@Composable
private fun NumField(label: String, value: Int, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    var txt by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = txt,
        onValueChange = { t ->
            txt = t
            t.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

/** One labelled dropdown over wire values (the sampler). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DropdownWire(
    label: String,
    current: String,
    choices: List<String>,
    textOf: (String) -> String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            // ⭐ (2026-11-05, fifth round) An empty wire suffix is a REAL value for
            // the schedule field (= normal, schedNames[""]) — show its name, not a dash.
            value = textOf(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in choices) {
                DropdownMenuItem(
                    text = { Text(textOf(c), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onPick(c) },
                )
            }
        }
    }
}

/** Content uri → its display name, for the img2img source line. */
private fun queryDisplayName(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
} catch (_: Exception) {
    null
}

/** Snap to the nearest multiple of 64 (the workbench's blur rule). */
private fun snap64(v: Int): Int = (v / 64).coerceAtLeast(1) * 64


/**
 * The generate/cancel tap. While the engine runs this only raises [RUN_CANCEL]
 * — the run loop polls it and calls [ApiCore.cancel] on the engine's own thread.
 * The finished picture runs through the workbench's post-process (colour, then
 * the ×2 upscale), lands in the frame and — with auto-save on — the history.
 */
private fun onTapGenerate(
    ctx: Context,
    s: WbState,
    positive: String,
    negative: String,
    srcDataUrl: String?,
    busy: MutableState<Boolean>,
    progTxt: MutableState<String>,
    progFrac: MutableState<Float?>,
    statusLine: MutableState<String>,
    msLine: MutableState<String>,
) {
    if (busy.value) {
        RUN_CANCEL = true
        return
    }
    statusLine.value = ""
    if (ModelCatalog.byId(s.modelId) == null) s.modelId = SelectedModel.spec.id
    // The saved picker may be blank (first launch): the family's first entry.
    if (s.scheduler.isBlank()) {
        s.scheduler = ModelCatalog.schedulersFor(SelectedModel.spec.family).firstOrNull().orEmpty()
    }
    saveState(ctx, s)
    val req = try {
        buildRequest(s, positive, negative, srcDataUrl)
    } catch (e: Exception) {
        statusLine.value = e.message ?: "the request is incomplete"
        return
    }
    busy.value = true
    RUN_CANCEL = false
    RUN_PROGRESS.set(null)
    RUN_EX.execute {
        try {
            val core = core(ctx)
            val t0 = System.currentTimeMillis()
            val res = core.run(core.prepare(req)) { _, js ->
                val t = js.optString("total", "-1").toInt()
                val p = js.optString("step", "-1").toInt()
                if (t > 0 && p >= 0) {
                    RUN_PROGRESS.set(p to t)
                    if (RUN_CANCEL) core.cancel()
                }
            }
            val js = res.answer
            val err = js.optString("error")
            if (err.isNotEmpty()) {
                statusLine.value = err
                return@execute
            }
            val first = res.images.firstOrNull()
            if (first == null) {
                statusLine.value = "no picture came out of this run"
                return@execute
            }
            val url = js.optJSONArray("results")?.optJSONObject(0)?.optString("url").orEmpty()
            val f = core.resultFileOf(url)
            val ms = System.currentTimeMillis() - t0
            msLine.value = String.format("%.1fs", ms / 1000.0)
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: run {
                statusLine.value = "the result could not be decoded"
                return@execute
            }
            var out = bmp
            if (s.pColor) out = autoColour(out)
            if (s.adjOn) out = applyAdjust(out, s)
            if (s.pUps) {
                statusLine.value = "post-processing…"
                out = android.graphics.Bitmap.createScaledBitmap(out, out.width * 4, out.height * 4, true)
            }
            val shot = Shot(out.asImageBitmap(), System.currentTimeMillis(), 0L, f)
            LAST_SHOT.set(shot)
            if (s.autosave) {
                SESSION.addFirst(shot)
                if (SESSION.size > 12) SESSION.pollLast()
            }
            statusLine.value = "$ms ms"
        } catch (e: Exception) {
            statusLine.value = e.message ?: e.javaClass.simpleName
        } finally {
            RUN_PROGRESS.set(null)
            busy.value = false
        }
    }
}

/**
 * The Default UI's auto colour correction: a per-channel percentile stretch —
 * the darks go darker, the brights brighter, a flat cast washes out — with the
 * gain capped so a near-flat picture is not amplified into noise.
 */
private fun autoColour(src: android.graphics.Bitmap): android.graphics.Bitmap {
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
private fun applyAdjust(src: android.graphics.Bitmap, s: WbState): android.graphics.Bitmap {
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
    val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(
        src, 0f, 0f,
        android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(m) },
    )
    return out
}


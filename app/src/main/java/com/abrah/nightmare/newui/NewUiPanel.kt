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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
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
import com.abrah.nightmare.canvas.rememberOffMain
import com.abrah.nightmare.LoraSpec
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign


/** Live progress of the executor's run, if any (step, total). */
private val RUN_PROGRESS = java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)

/** The engine's log line while a run is on, if any. */
private val RUN_LOG = java.util.concurrent.atomic.AtomicReference<String?>(null)

/** Set when the person asks to cancel; the run thread calls [ApiCore.cancel] on it. */
@Volatile private var RUN_CANCEL = false

/**
 * ⭐ (2026-11-05, ninth round, revised) The top-bar Run bridge — the Run button
 * lives in CanvasScreen's TopBar now, but the workbench's run action needs the
 * screen's local states (busy, prompts, source...). [NewUiScreen] publishes the
 * tap here on every recomposition and hands the bridge's own busy state to its
 * run loop, so one flag flips the button in both places.
 */
object NewUiRun {
    /** Shared busy flag — the same instance the screen's run loop flips. */
    val busy: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false)
    /** The screen's generate/cancel tap; null before the first NEWUI composition. */
    var onTap: (() -> Unit)? = null
}

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
    // ⭐⭐ (2026-11-06) Every scalar is COMPOSE STATE now. This class used to be
    // a plain bag of fields — writes recomposed nothing — and the whole file
    // fought that with local mirrors + the modelVer counter. The stateless
    // PostSlider (the Default UI's SliderRow shape) needs the wire to BE
    // state: without it the thumb fought M3's internal drag state and the
    // value froze until an unrelated recomposition (the user: sliders and
    // buttons needed a refresh / several taps). Same architecture as
    // CanvasState in the Default UI. [loras] stays a plain list: it mutates
    // behind onLorasChanged -> modelVer++.
    var modelId: String by mutableStateOf("")
    var scheduler: String by mutableStateOf("")
    var loras: MutableList<Pair<String, Float>> = mutableListOf()
    var w: Int by mutableStateOf(832)
    var h: Int by mutableStateOf(1216)
    var steps: Int by mutableStateOf(29)
    var cfg: Float by mutableStateOf(5.0f)
    var seed: Long by mutableStateOf(0L)
    var fixedSeed: Boolean by mutableStateOf(false)
    var denoise: Float by mutableStateOf(0.40f)
    var mossOn: Boolean by mutableStateOf(false)
    var mode: String by mutableStateOf("txt2img")
    // The post-processing block, the HTML page's S.pUps/pFace/pColor/adj*.
    var autosave: Boolean by mutableStateOf(true)
    var pUps: Boolean by mutableStateOf(false)
    var pUps2: Boolean by mutableStateOf(false) // the More-options x2, per run
    // ⭐ (2026-11-05, eighth round) Wired for real: the flags ride the request as
    // fix_faces/fix_hands knobs — the engine re-draws each found part in-render.
    var pFace: Boolean by mutableStateOf(false)
    var pColor: Boolean by mutableStateOf(false)
    var adjBr: Int by mutableStateOf(0)
    var adjCo: Int by mutableStateOf(0)
    var adjSa: Int by mutableStateOf(0)
    var warm: Int by mutableStateOf(0) // the warmth slider (±60), with the colour adjust
    var adjOn: Boolean by mutableStateOf(false) // the adjust-colour row's per-run switch
    var pHands: Boolean by mutableStateOf(false)
}

/** The single background thread [ApiCore.run] lives on (it blocks). */
private val RUN_EX = Executors.newSingleThreadExecutor()

/**
 * ⭐ fix28 (2026-11-06, external review): a SECOND single thread for the
 * non-render work — "apply now" post tools and LoRA import. They used to
 * share [RUN_EX] with generation, and `core.run` blocks that thread for the
 * whole render: an "apply now" tapped mid-generation queued behind it and
 * only executed after the picture finished (the user: buttons dead during a
 * run). Independent thread = these stay live while the engine is busy.
 */
private val POST_EX = Executors.newSingleThreadExecutor()

/**
 * The families with NO fix widgets on their sampler — [com.abrah.nightmare.detail.Detailer]'s
 * own exclusion list (offersDetail). Sending the knobs to those anyway would 400 the
 * whole render in [applyOverrides]'s checked().
 */
private val DETAIL_EXCLUDED = setOf(
    com.abrah.nightmare.Family.IRIS,
    com.abrah.nightmare.Family.WAN,
    com.abrah.nightmare.Family.IDEOGRAM,
    com.abrah.nightmare.Family.FLUX3,
    com.abrah.nightmare.Family.FIBO2,
)

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
        pUps2 = p.getBoolean("pUps2", false)
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
        .putBoolean("pUps2", s.pUps2)
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
    // ⭐ (2026-11-05, eighth round) Fix faces / hands ride the SAME knobs channel as
    // steps/cfg: every family with a masked render declares the fix widgets on its
    // sampler (Fused.detailWidgets) and re-draws each found part in the render
    // itself — no local second pipeline. ⚠ Family-gated: a sampler without the
    // widget would 400 the whole render in checked() (IRIS/WAN/IDEOGRAM/FLUX3/FIBO2).
    if (model.family !in DETAIL_EXCLUDED) {
        if (s.pFace) knobs[com.abrah.nightmare.detail.Detailer.FIX_FACES] = "true"
        if (s.pHands) knobs[com.abrah.nightmare.detail.Detailer.FIX_HANDS] = "true"
    }
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
fun NewUiScreen(
    // ⭐ (2026-11-05, eleventh round) The LoRA sheet's ⋮ (delete) and browse
    // entries, threaded from MainActivity's view model.
    onBrowseLoras: ((com.abrah.nightmare.LoraSources.Target) -> Unit)? = null,
    onDeleteLora: ((String) -> Unit)? = null,
) {
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
    // ⭐ (2026-11-05, ninth round, revised) The busy flag is the bridge's own
    // state: the top bar's Run reads the same instance, one render flips both.
    val busy = NewUiRun.busy
    val progTxt = remember { mutableStateOf("") }
    val progFrac = remember { mutableStateOf<Float?>(null) }
    val statusLine = remember { mutableStateOf("") }
    val msLine = remember { mutableStateOf("—") }
    var srcDataUrl by remember { mutableStateOf<String?>(null) }
    var srcName by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf(s.modelId) }
    // ⭐ (2026-11-05, eighth round) The mode is STATE here, not a plain read of
    // [s.mode]: WbState has no recompose signal, so the ResultCard froze on the
    // composition-time value (the user's report: img2img picked, no Load button).
    // Same shape as [modelId] above; the ModeCard writes BOTH, and buildRequest
    // keeps reading [s.mode].
    var modeState by remember { mutableStateOf(s.mode) }
    // ⭐ (2026-11-05, ninth round, revised) Publish the run action to the bridge
    // on every recomposition — the prompt/source states captured here are the
    // fresh ones; leaving the view unpublishes so a stale tap can't fire.
    androidx.compose.runtime.SideEffect {
        NewUiRun.onTap = { onTapGenerate(ctx, s, positive, negative, srcDataUrl, busy, progTxt, progFrac, statusLine, msLine) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { NewUiRun.onTap = null }
    }
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
    // model reset itself across a process kill).
    // ⭐⭐ (2026-11-06) DEBOUNCED, and keyed on the states themselves. It used to
    // be LaunchedEffect(modelVer) with NO delay — and the width/height sliders
    // bumped modelVer every frame, so a drag ran saveState (the whole prefs map
    // plus a SharedPreferences apply) on the MAIN thread per frame: the thread
    // saturated and taps landed late or never (the user: buttons needed several
    // tries). Every scalar is compose state now, so this one effect keys on them
    // all and saves 600 ms after the LAST change — one save per burst, off the
    // drag's critical path. modelVer stays a key for the loras list (plain,
    // non-state mutations behind onLorasChanged).
    LaunchedEffect(
        modelVer, s.modelId, s.scheduler, s.w, s.h, s.steps, s.cfg, s.seed,
        s.fixedSeed, s.denoise, s.mossOn, s.mode, s.autosave, s.pUps, s.pUps2,
        s.pFace, s.pColor, s.adjBr, s.adjCo, s.adjSa, s.warm, s.adjOn, s.pHands,
    ) {
        kotlinx.coroutines.delay(600)
        saveState(ctx, s)
    }
    // The Default UI's APPLY NOW: the tool re-renders the displayed shot from its
    // ORIGINAL file (no compounding), on [RUN_EX]; postVer redraws the frame.
    val postVer = remember { mutableIntStateOf(0) }
    // ⭐ (2026-11-05, ninth round) LoRA import from the workbench's own picker —
    // the same _loras folder the Default UI's picker fills, copied off the main
    // thread. [loraEpoch] re-keys the picker's file scan, so the file a person
    // just picked shows up without leaving the card.
    var loraEpoch by remember { mutableIntStateOf(0) }
    val loraImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri) ?: "lora-" + System.currentTimeMillis() + ".safetensors"
            POST_EX.execute {
                try {
                    val dst = java.io.File(BackendProcess.lorasDir(ctx), name)
                    ctx.contentResolver.openInputStream(uri)?.use { inp ->
                        dst.outputStream().use { inp.copyTo(it) }
                    }
                } catch (_: Exception) {} finally {
                    // ⭐ fix28 (external review): re-key the scan AFTER the copy —
                    // the old code bumped [loraEpoch] on the UI thread immediately,
                    // so the picker scanned before the file was written and the
                    // fresh import was invisible until something else re-scanned.
                    loraEpoch++
                }
            }
        }
    }
    val applyPost: (Int) -> Unit = { tool ->
        val cur = LAST_SHOT.get()
        if (cur != null) {
            // ⭐ fix28 (external review): POST_EX, not RUN_EX — "apply now" must
            // not queue behind a blocking `core.run` (see POST_EX's doc).
            POST_EX.execute {
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

    val workbenchScroll = rememberScrollState()
    // ⭐ TouchProbe layer 3 (2026-11-06): observe the scroll OFF the recomposition
    // path (snapshotFlow emits on the applier thread) — a frozen SCROLL stream
    // while DISPATCH lines keep flowing is the ownership-conflict signature.
    LaunchedEffect(Unit) {
        snapshotFlow { workbenchScroll.value }.collect { TouchProbe.markScroll(it.toFloat()) }
    }
    // ⭐ TouchProbe layer 3b (2026-11-06, probe v3): the scroll RANGE, logged on
    // change only. maxValue == 0 while the screen shows content = a collapsed
    // measure: every drag is a no-op and the value glues at 0 — the dead-swipe
    // signature the 14:00 freeze showed.
    LaunchedEffect(Unit) {
        snapshotFlow { workbenchScroll.maxValue }.collect { TouchProbe.markScrollRange(it) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(workbenchScroll)
            // ⭐ TouchProbe layer 4 (2026-11-06, probe v3): INSIDE the scroll
            // modifier on purpose. The Main pass travels leaf→root, so this
            // observer reads `isConsumed` BEFORE the scrollable above it has its
            // say: consumed=true here can only come from a child widget stealing
            // the drag; all-unconsumed here with a frozen SCROLL means the
            // scrollable itself refused (measurement collapse — see SCROLLRANGE).
            .pointerInput(Unit) { passObserver("wb") }
            // ⭐ (2026-11-05, ninth round, re-re-marking) The top bar is gone:
            // the host leaves the very top to this page, so the page keeps one
            // module gap (12dp) at its head — the spacing every card keeps.
            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = BOTTOM_BAR_RESERVE + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Card 1: the result ────────────────────────────────────────────
        // ⭐ (2026-11-05, ninth round, revised) The run head is in the top bar
        // now (the brand's old spot); the card keeps status + save icon.
        ResultCard(
            ctx, busy.value, progTxt.value, progFrac.value, srcName, postVer.intValue,
            mode = modeState,
            statusTxt = statusLine.value.ifBlank { stringResource(R.string.newui_ready) },
            onPickSrc = { uri, name ->
                srcDataUrl = uri?.let { uriToDataUrl(ctx, it) }
                srcName = name
            },
            onSave = { LAST_SHOT.get()?.let { saveShot(ctx, it) } },
            // ⭐ (2026-11-05, ninth round, third re-marking) The run block's own
            // inputs: the elapsed time and model, and the tap that starts a render.
            msLine = msLine.value,
            modelLbl = modelLabel(),
            runLabel = if (busy.value) stringResource(R.string.cancel_run)
                       else stringResource(R.string.newui_generate),
            onRun = { onTapGenerate(ctx, s, positive, negative, srcDataUrl, busy, progTxt, progFrac, statusLine, msLine) },
        )

        // ── Card 2: the mode ──────────────────────────────────────────────
        ModeCard(
            s,
            family = ModelCatalog.byId(modelId)?.family ?: SelectedModel.spec.family,
            onMode = { modeState = it },
            onDraft = { positive = it },
            onDraftSize = { w, h -> s.w = w; s.h = h; modelVer++ },
        )

        // ── Card 3: the prompts ───────────────────────────────────────────
        Card(Modifier.fillMaxWidth().border(1.dp, Nm.line, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Nm.card), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.newui_prompt), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Nm.ink)
                OutlinedTextField(
                    value = positive, onValueChange = { positive = it },
                    label = { Text(stringResource(R.string.newui_positive)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    placeholder = { Text("1girl, solo, ...") },
                )
                OutlinedTextField(
                    value = negative, onValueChange = { negative = it },
                    label = { Text(stringResource(R.string.newui_negative)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
            }
        }

        // ── Card 4: the model ─────────────────────────────────────────────
        ModelCard(
            ctx, s,
            modelId = modelId,
            family = ModelCatalog.byId(modelId)?.family ?: SelectedModel.spec.family,
            loraEpoch = loraEpoch,
            onPicked = { id -> modelId = id; s.modelId = id; modelVer++ },
            onLorasChanged = { modelVer++ },
            onImportLora = { loraImport.launch(arrayOf("application/octet-stream", "*/*")) },
            onBrowseLoras = onBrowseLoras,
            onDeleteLora = onDeleteLora,
        )

        // ── Card 5: the parameters ────────────────────────────────────────
        ParamsCard(
            ctx, s,
            family = ModelCatalog.byId(modelId)?.family ?: SelectedModel.spec.family,
            onPicked = { modelVer++ },
            onApply = { applyPost(it) },
        )

        // ⭐ (2026-11-05, ninth round) The footer (time · model · GENERATE ·
        // status) moved INTO the result card's head — the user's items 5-6.
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
// ⭐⭐ (2026-11-05, seventh round) THE WORKBENCH LOOK — nm-workbench.html's
// palette and shapes as local tokens (the user: the Compose port read nothing
// like the HTML). Light-only, exactly like the HTML (it pins color-scheme too).
// The app theme is untouched: these live inside the newui tree only.
/**
 * ⭐⭐⭐ (2026-11-05, twelfth round) The workbench's colours AS a Material scheme,
 * so the Default UI's own sheet can be drawn in the HTML's palette without
 * touching a line of it (the user: "主要是风格，功能不要变").
 *
 * Every colour the canvas [com.abrah.nightmare.canvas.LoraPicker] reads comes
 * from a role below — the violet accent on the checkbox, the slider and the
 * Done button (`--pri`), the white card (`--card`), the hairline (`--line`),
 * the field stroke (`--fld`), the ink text (`--txt`) and the grey hints
 * (`--sub`). Overriding the roles is the whole of the restyle.
 *
 * ⚠ `surfaceContainerHigh` is what an [androidx.compose.material3.AlertDialog]
 * paints its own card with — left unset it stays the app's near-black in dark
 * mode, and the sheet would be dark in a light workbench.
 */
internal fun nmScheme(dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) androidx.compose.material3.darkColorScheme()
               else androidx.compose.material3.lightColorScheme()
    return base.copy(
        primary = Nm.pri,
        onPrimary = Color.White,
        primaryContainer = Nm.priSoft,
        onPrimaryContainer = Nm.priD,
        secondary = Nm.priD,
        onSecondary = Color.White,
        secondaryContainer = Nm.priSoft,
        onSecondaryContainer = Nm.priD,
        tertiary = Nm.pri,
        tertiaryContainer = Nm.priSoft,
        onTertiaryContainer = Nm.priD,
        background = Nm.bg,
        onBackground = Nm.ink,
        surface = Nm.card,
        onSurface = Nm.ink,
        surfaceVariant = Nm.soft2,
        onSurfaceVariant = Nm.sub,
        surfaceContainerHigh = Nm.card,
        surfaceContainerHighest = Nm.soft2,
        outline = Nm.fld,
        outlineVariant = Nm.line,
    )
}

/** The sheet's own corner: the HTML's 20px on the card, 10 on a field. */
internal val NmSheetShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal object Nm {
    val bg = Color(0xFFF2F0F7)       // --bg, the lavender-grey page
    val card = Color(0xFFFFFFFF)     // --card
    val line = Color(0xFFE5E1EF)     // --line, the hairline stroke
    val ink = Color(0xFF17151F)      // --txt
    val sub = Color(0xFF6E6A7A)      // --sub, secondary text
    val pri = Color(0xFF6D4AE0)      // --pri, the violet
    val priD = Color(0xFF5B39CE)     // --pri-d
    val priSoft = Color(0xFFEFEAFB)  // --pri-soft, selected chips
    val soft2 = Color(0xFFF7F4FE)    // --pri-soft2, tinted panels
    val canvas = Color(0xFFFBFAFE)   // #canvasBox
    val fld = Color(0xFFD9D4E8)      // the field stroke
    val priLine = Color(0xFFD8CCF5)  // a SELECTED chip's edge (.chip.on border)
    val bad = Color(0xFFD64545)      // --bad, a missing file
}

/** The HTML .seg: a pill container, the selected half in the soft violet. */
@Composable
private fun SegmentedRow(
    selLeft: Boolean,
    left: String,
    right: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Nm.card, RoundedCornerShape(999.dp))
            .border(1.dp, Nm.fld, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selLeft) Nm.priSoft else Color.Transparent)
                .clickable { onLeft() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(left, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selLeft) Nm.priD else Nm.sub)
        }
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(if (!selLeft) Nm.priSoft else Color.Transparent)
                .clickable { onRight() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(right, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (!selLeft) Nm.priD else Nm.sub)
        }
    }
}

@Composable
private fun ResultCard(
    ctx: Context,
    busy: Boolean,
    progTxt: String,
    progFrac: Float?,
    srcName: String,
    ver: Int,
    mode: String,
    // ⭐ (2026-11-05, ninth round, revised) The status line stays — it answers
    // the top-bar Run, which took the brand's spot; the run head itself left
    // the card (the user's re-marking). Save is now the top-bar icon, so the
    // card takes [onSave].
    statusTxt: String,
    onPickSrc: (Uri?, String) -> Unit,
    onSave: () -> Unit,
    // ⚠ (2026-11-05, fourteenth round) No Results entry here any more: the
    // bottom nav's own tab is the one door (the user's image-1 arrow).
    // ⭐⭐ (2026-11-05, ninth round, third re-marking) The run block is back at
    // the card's foot (the user's image-2 arrow): the time/model strip above,
    // the full-width Generate below — the shipped workbench's own shape.
    msLine: String,
    modelLbl: String,
    runLabel: String,
    onRun: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().border(1.dp, Nm.line, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Nm.card), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ⭐ (2026-11-05, ninth round, revised) The run head is gone from the
            // card — the Run lives in the top bar now (the brand's old spot; the
            // user's re-marking). The status stays at the head to answer it.
            Text(
                statusTxt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Nm.canvas)
                    .border(1.dp, Nm.line, RoundedCornerShape(14.dp)),
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
                // ⭐ (2026-11-05, seventh round) The source picker only exists for
                // img2img — txt2img has no use for one (the user).
                if (mode == "img2img") {
                    OutlinedButton(onClick = { pickSrc.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.newui_load))
                    }
                }
                Spacer(Modifier.weight(1f))
                // ⭐ (2026-11-05, ninth round, revised) The top bar's save icon
                // lives here now — the row's right end, where the Save button
                // was — at icon-button size to fit the row (the user's re-marking).
                val have = LAST_SHOT.get() != null
                IconButton(onClick = onSave, enabled = have) {
                    Icon(
                        com.abrah.nightmare.ui.SaveIcon,
                        contentDescription = stringResource(R.string.save),
                        tint = if (have) Nm.sub else Nm.sub.copy(alpha = 0.4f),
                    )
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
                // ⭐⭐ (2026-11-05, fourteenth round) The Results icon is GONE
                // from here: the bottom nav already carries it, and two doors to
                // one room is one door too many (the user's image-1 arrow).
            }
            val hist = SESSION.toList()
            if (hist.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hist) { sh ->
                        Image(
                            bitmap = sh.bitmap, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Nm.pri, RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
            // ⭐⭐ (2026-11-05, ninth round, third re-marking) The run block, at
            // the card's foot (the user's image-2 arrow) and in the shipped
            // workbench's shape (their image 1): the tinted time/model strip on
            // top, the full-width Generate under it. Busy swaps the label for
            // Cancel — the tap runs through [onTapGenerate]'s own guard.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Nm.soft2)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⏱ $msLine",
                    style = MaterialTheme.typography.labelMedium,
                    color = Nm.sub,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.newui_model, modelLbl),
                    style = MaterialTheme.typography.labelMedium,
                    color = Nm.sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onRun,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nm.pri, contentColor = Color.White),
            ) {
                Text(
                    if (busy) runLabel else "▶  " + runLabel,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/**
 * Card 2 — the mode: the txt2img/img2img segment, the denoise slider (img2img
 * only) and the MOSS block (the switch, the open-canvas button when on).
 */
@Composable
private fun ModeCard(
    s: WbState,
    family: com.abrah.nightmare.Family,
    onDraft: (String) -> Unit,
    onDraftSize: (Int, Int) -> Unit,
    // ⭐ (2026-11-05, eighth round) The screen's mode STATE follows the pick: the
    // ResultCard reads that state (a plain write into [s] has no recompose signal).
    onMode: (String) -> Unit = {},
) {
    Card(Modifier.fillMaxWidth().border(1.dp, Nm.line, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Nm.card), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var mode by remember { mutableStateOf(s.mode) }
            // ⭐ (2026-11-05, seventh round) The HTML .seg: one pill, two halves.
            SegmentedRow(
                selLeft = mode != "img2img",
                left = "✦ " + stringResource(R.string.newui_txt2img),
                right = "🖼 " + stringResource(R.string.newui_img2img),
                onLeft = { mode = "txt2img"; s.mode = "txt2img"; onMode("txt2img") },
                onRight = { mode = "img2img"; s.mode = "img2img"; onMode("img2img") },
            )
            if (mode == "img2img") {
                PostSlider(
                    stringResource(R.string.newui_denoise),
                    s.denoise, 0.05f..0.95f, fmt = { String.format("%.2f", it) },
                ) { s.denoise = it }
            }
            var mossOn by remember { mutableStateOf(s.mossOn) }
            // ⭐ (2026-11-05, seventh round) The MOSS draft is a MOSS-only feature:
            // the block only exists when the picked model is Family.MOSS (the user).
            if (family == com.abrah.nightmare.Family.MOSS) {
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
private fun ModelCard(
    ctx: Context,
    s: WbState,
    modelId: String,
    family: com.abrah.nightmare.Family,
    loraEpoch: Int,
    onPicked: (String) -> Unit,
    onLorasChanged: () -> Unit,
    onImportLora: (() -> Unit)?,
    // ⭐⭐ (2026-11-05, eleventh round) The sheet's ⋮ and browse entries — the
    // same two the Default UI's node inspector hands its picker.
    onBrowseLoras: ((com.abrah.nightmare.LoraSources.Target) -> Unit)? = null,
    onDeleteLora: ((String) -> Unit)? = null,
) {
    val installed = remember { ModelCatalog.installed(ctx) }
    Card(Modifier.fillMaxWidth().border(1.dp, Nm.line, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Nm.card), elevation = CardDefaults.cardElevation(0.dp)) {
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
            // ⭐⭐⭐ (2026-11-05, thirteenth round) The card's own sheet
            // ([NewUiLoraPicker]): the workbench's 选择 LoRA look, carrying the
            // four things the user kept from the Default UI's — grouping, search,
            // import and the ⋮ menu (the user: "算了，lora选择的ui重写吧").
            if (picking) {
                // ⚠ Off the main thread, re-keyed on [loraEpoch]: a file imported
                // from the sheet shows up without leaving the card. Null until the
                // scan lands — an empty list would mark every pick missing.
                val installed = rememberOffMain("newui-loras", "lora files", loraEpoch) {
                    BackendProcess.lorasDir(ctx).listFiles { f ->
                        f.isFile && f.extension.equals("safetensors", ignoreCase = true)
                    }.orEmpty().map { it.name to it.length() }.sortedBy { it.first.lowercase() }
                }
                // ⭐⭐ (2026-11-05, eleventh round) Each file's SIZE, DATE and BASE
                // ([LoraLibrary]) — this is what draws the "Anima / 132 MB" line
                // under every row of the Default UI's sheet (the user's image), and
                // what feeds its base grouping. Headers only, cached, off the main
                // thread — the same read the node inspector makes.
                val library by androidx.compose.runtime.produceState<
                    List<com.abrah.nightmare.LoraLibrary.Item>?>(null, loraEpoch) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.abrah.nightmare.LoraLibrary.list(
                            BackendProcess.lorasDir(ctx),
                            com.abrah.nightmare.LoraLibrary.baseOverrides(ctx),
                        )
                    }
                }
                // ⭐ The ⋮ per file ([LoraNotes]): read beside the files, written
                // straight back on save. ⚠ Without these the ⋮ is not drawn at all.
                val loraDir = BackendProcess.lorasDir(ctx)
                val notesRead = rememberOffMain("newui-loras", "lora notes", loraEpoch) {
                    com.abrah.nightmare.LoraNotes.read(loraDir)
                }
                var savedNotes by remember(loraEpoch) { mutableStateOf<Map<String, String>?>(null) }
                // ⚠ Nothing until both land: a picker drawn on an EMPTY list marks
                // every chosen LoRA missing (the inspector's own rule).
                if (installed != null && notesRead != null) {
                    val loraNotes = savedNotes ?: notesRead
                    // ⭐⭐⭐ (2026-11-05, thirteenth round) The workbench's OWN
                    // sheet ([NewUiLoraPicker]), drawn to the HTML's 选择 LoRA
                    // rather than inheriting the inspector's Material one. Same
                    // data, same callbacks — the four functions the user kept
                    // (grouping / search / import / the ⋮ menu) all live inside it.
                    NewUiLoraPicker(
                        installed = installed,
                        library = library,
                        spec = s.loras.joinToString(", ") { (n, w) ->
                            if (w == 1.0f) n else "$n@" + LoraSpec.number(w.toDouble())
                        },
                        onSet = { txt ->
                            s.loras = LoraSpec.parse(txt).map { it.name to it.strength.toFloat() }.toMutableList()
                            ver++
                            onLorasChanged()
                        },
                        onDismiss = { picking = false },
                        onImport = onImportLora,
                        // ⭐ Only where this family's LoRAs can be searched.
                        onBrowse = com.abrah.nightmare.LoraSources.Target.of(family)
                            ?.let { t -> onBrowseLoras?.let { cb -> { cb(t) } } },
                        notes = loraNotes,
                        onSaveNote = { name, note ->
                            savedNotes = com.abrah.nightmare.LoraNotes.write(loraDir, name, note)
                        },
                        onDelete = onDeleteLora,
                        nodeBase = com.abrah.nightmare.LoraLibrary.Base.of(family),
                        experimental = com.abrah.nightmare.LoraSources.Target.experimental(family),
                        // ⭐ Below 0 where the engine takes it (DiT families, GitHub #51).
                        allowNegative = family.dit,
                    )
                }
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
    Card(Modifier.fillMaxWidth().border(1.dp, Nm.line, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Nm.card), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.newui_params), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Nm.ink)
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
            ) { s.w = snap64(it.toInt()) } // ⭐ state write only: onPicked() here ran modelVer++ EVERY FRAME, saturating the main thread (taps swallowed)
            PostSlider(
                stringResource(R.string.knob_height),
                s.h.toFloat(), 256f..2048f,
                fmt = { String.format("%.0f", it) },
            ) { s.h = snap64(it.toInt()) }
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
                    label = { Text(stringResource(R.string.knob_seed), color = Nm.sub) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri, unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri, unfocusedLabelColor = Nm.sub,
                cursorColor = Nm.pri, focusedTextColor = Nm.ink, unfocusedTextColor = Nm.ink,
            ),
                    shape = RoundedCornerShape(12.dp),
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
                    fontWeight = FontWeight.Bold,
                    color = Nm.ink,
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
                    fontWeight = FontWeight.Bold,
                    color = Nm.ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { moreOpen = !moreOpen }) {
                    Text(if (moreOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
                }
            }
            AnimatedVisibility(moreOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ⭐ (2026-11-05, ninth round) A per-run switch like the face/
                    // hand fixes, not a one-shot button (the user's item 1).
                    // ⭐ fix28 (external review): the flags are compose state already — the
                    // debounced-save LaunchedEffect keys on them directly, and
                    // `modelVer` has no consumer that needs a bump here. The old
                    // onPicked() re-invalidated the whole NewUiScreen on every flip.
                    SwitchRow(
                        stringResource(R.string.knob_enlarge),
                        stringResource(R.string.newui_m_ups_hint),
                        on = s.pUps2,
                        onFlag = { s.pUps2 = it },
                    )
                    SwitchRow(
                        stringResource(R.string.knob_fix_faces),
                        stringResource(R.string.newui_p_face_hint),
                        on = s.pFace,
                        onFlag = { s.pFace = it },
                    )
                    SwitchRow(
                        stringResource(R.string.knob_fix_hands),
                        stringResource(R.string.newui_p_hands_hint),
                        on = s.pHands,
                        onFlag = { s.pHands = it },
                    )
                    TextButton(onClick = {
                        s.steps = 29; s.cfg = 5.0f
                        s.adjBr = 0; s.adjCo = 0; s.adjSa = 0; s.warm = 0
                        s.pUps = false; s.pUps2 = false; s.pColor = false; s.adjOn = false
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
    // ⭐ (2026-11-05, ninth round) NOT keyed on [on]: a keyed remember re-runs
    // only when the OUTSIDE value changes, so a flip here updated [on]'s source
    // but kept the OLD local — the switch looked dead until the card was folded
    // away and back (the user's item 2). Stateless: the flag IS the state.
    // ⭐ fix28 (2026-11-06, external review): bind the switch to the WIRE
    // directly, exactly like [SwitchRow] — no local state. The old
    // `remember { mutableStateOf(on) }` took a copy that never followed [on]
    // afterwards: a reset (or any outside change) cleared the wire but left
    // the switch drawn "on", and the NEXT tap wrote the stale value back,
    // looking dead. (The ninth-round note feared a keyed remember's trap;
    // the fix27 answer — PostSlider's — is no local state at all.)
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Nm.ink)
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
                Switch(checked = on, onCheckedChange = onFlag)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * One labelled switch row — the ModeCard's MOSS-layout block shape, extracted so
 * the More-options render-time flags (fix faces / fix hands) read the same. The
 * flags ride the NEXT request as fix_faces/fix_hands knobs; there is no per-run
 * "apply now" on them (the HTML page's S.pFace / S.pHands are plain checkboxes).
 */
@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    on: Boolean,
    onFlag: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Nm.ink)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onFlag)
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
    // ⭐⭐ (2026-11-06) STATELESS — the Default UI's SliderRow shape
    // (NodeInspector.kt): the thumb IS the wire value. The old
    // remember(value) re-seeded only when the value CHANGED, so a 64-snap
    // that kept the wire the same (960 -> 1370 -> 960) left the thumb parked
    // on the raw drag value: the label read 1370 while the field said 960
    // (the user's screenshot). The snap is written every frame, so the wire,
    // the thumb and the label agree by construction — a drag shows the
    // snapped value directly, exactly like the Default UI's step knobs.
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Nm.sub, modifier = Modifier.weight(1f))
            Text(fmt(value), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Nm.ink)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onSet,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Nm.pri, activeTrackColor = Nm.pri, inactiveTrackColor = Nm.line,
            ),
        )
    }
}

/**
 * A number field (width/height). ⭐⭐ (2026-11-06) The text FOLLOWS the wire
 * value whenever the wire moved from OUTSIDE this field — the slider's drag,
 * the MOSS canvas write-back — and on leaving the field the snap displays.
 * While focused the typist owns the text (never interrupted mid-number); the
 * old `remember { }` froze the digits forever, so the field read 960 while
 * the slider and the canvas had agreed on 1344 (the user's screenshot).
 * Every keystroke still commits (the snap writes as you type), so a Generate
 * without ever leaving the field still uses what was typed.
 */
@Composable
private fun NumField(label: String, value: Int, onCommit: (Int) -> Unit, modifier: Modifier = Modifier) {
    var txt by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    // Not focused -> mirror the wire (a drag or a canvas write-back shows up
    // as you do it). Focused -> untouched.
    if (!focused && txt != value.toString()) txt = value.toString()
    OutlinedTextField(
        value = txt,
        onValueChange = { t ->
            txt = t
            t.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label, color = Nm.sub) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri, unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri, unfocusedLabelColor = Nm.sub,
                cursorColor = Nm.pri, focusedTextColor = Nm.ink, unfocusedTextColor = Nm.ink,
            ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.onFocusChanged { f ->
            focused = f.isFocused
            // Leaving the field: display the WIRE (the snap of what was
            // typed), so the digits and the slider can never disagree at rest.
            if (!f.isFocused) txt = value.toString()
        },
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
            label = { Text(label, color = Nm.sub) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri, unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri, unfocusedLabelColor = Nm.sub,
                cursorColor = Nm.pri, focusedTextColor = Nm.ink, unfocusedTextColor = Nm.ink,
            ),
            shape = RoundedCornerShape(12.dp),
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
            // ⭐ (2026-11-05, ninth round) The upscale switches: the main row's
            // x4 and the More-options x2 (now a per-run switch, the user). Both
            // may be on — they stack; each hint says what it does.
            if (s.pUps) {
                statusLine.value = "post-processing…"
                out = android.graphics.Bitmap.createScaledBitmap(out, out.width * 4, out.height * 4, true)
            }
            if (s.pUps2) {
                statusLine.value = "post-processing…"
                out = android.graphics.Bitmap.createScaledBitmap(out, out.width * 2, out.height * 2, true)
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

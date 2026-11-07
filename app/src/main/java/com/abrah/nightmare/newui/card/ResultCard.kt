package com.abrah.nightmare.newui.card

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.R
import com.abrah.nightmare.newui.core.RunController
import com.abrah.nightmare.newui.core.Shot
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.theme.Nm
import com.abrah.nightmare.ui.SaveIcon

/**
 * ⭐ Sleek vector play/generate icon for the primary action button.
 */
val GeneratePlayIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GeneratePlay",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).run {
        addPath(
            pathData = PathParser().parsePathString("M8 5v14l11-7z").toNodes(),
            fill = SolidColor(Color.White),
        )
        build()
    }
}

/**
 * ⭐⭐ fix34 (2026-11-06) — Card 1: the result.
 *
 * The square frame (the finished shot or the empty hint), the progress bar and
 * cancel while the engine runs, the load/save row, the img2img source line, and
 * this session's history strip.
 */
@Composable
fun ResultCard(
    ctx: Context,
    phase: RunController.Phase,
    shot: Shot?,
    history: List<Shot>,
    srcName: String,
    mode: String,
    statusText: String,
    modelLabel: String,
    onPickSrc: (Uri?, String) -> Unit,
    onSave: () -> Unit,
    onSaveWorkflow: (() -> Unit)? = null,
    onImportImageParams: ((Uri) -> Unit)? = null,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    NmCard {
        // ── The status line (isolated recomposition) ──────────────────────
        GenerationStatusHeader(phase = phase, statusText = statusText)

        // ── The square frame ───────────────────────────────────────────────
        // Fixed 1:1 ratio box with smooth crossfade on new render arrival.
        // Causes zero layout re-measurement because container dimensions are strictly invariant.
        // ⭐ fix35h: full-screen viewer state — tap the picture to open it big.
        var viewerOpen by remember { mutableStateOf(false) }

        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Nm.canvas)
                .border(1.dp, Nm.line, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = shot,
                animationSpec = tween(240),
                label = "resultCrossfade",
            ) { curShot ->
                if (curShot == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.newui_results),
                            color = Nm.sub,
                        )
                    }
                } else if (curShot.file.name.endsWith(".mp4", ignoreCase = true)) {
                    com.abrah.nightmare.ui.ClipPlayer(
                        path = curShot.file.absolutePath,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (curShot.hasPost) {
                    // ⭐ fix35h: two pages — the RAW render ⇄ the post-processed
                    // picture — swiped exactly like the canvas fullscreen viewer
                    // (CanvasScreen's Received ⇄ Made pager). The raw bitmap is
                    // decoded lazily on the first swipe, never up front.
                    val pager = rememberPagerState(pageCount = { 2 })
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        Image(
                            bitmap = if (page == 0) curShot.rawBitmap else curShot.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { viewerOpen = true },
                        )
                    }
                    // ⭐ Page dots: with two nearly identical pictures the only
                    // tell is the position, so the dots carry it.
                    // ⭐ fix35i: page LABELS above the dots — with an x8 upscale
                    // the two pages look IDENTICAL inside a Fit frame (same
                    // picture, one is just smoother), so each page names itself.
                    // The multiplier is only computed while the processed page
                    // is shown, where the raw bitmap is already on screen —
                    // asking on page 0 would decode the raw render and defeat
                    // the lazy load.
                    val mult = if (pager.currentPage == 1) {
                        val rw = curShot.rawBitmap.asAndroidBitmap().width.coerceAtLeast(1)
                        val pw = curShot.bitmap.asAndroidBitmap().width
                        val m = (pw.toFloat() / rw * 10).toInt() / 10.0
                        m.takeIf { it > 1.0 }
                    } else null
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PageLabel("原图", pager.currentPage == 0)
                        PageLabel(
                            mult?.let { "放大 ×" + if (it == it.toInt().toDouble()) "${it.toInt()}" else "$it" }
                                ?: "成品",
                            pager.currentPage == 1,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(2) { i ->
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (pager.currentPage == i) Nm.pri
                                        else Nm.sub.copy(alpha = 0.35f),
                                    ),
                            )
                        }
                    }
                } else {
                    Image(
                        bitmap = curShot.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewerOpen = true },
                    )
                }
            }
        }

        // ⭐ fix35h: the full-screen viewer. Same two-page pager inside, so the
        // swipe continues in fullscreen; tap anywhere to close.
        if (viewerOpen && shot != null && !shot.file.name.endsWith(".mp4", ignoreCase = true)) {
            val fsShot = shot
            Dialog(
                onDismissRequest = { viewerOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewerOpen = false },
                ) {
                    if (fsShot.hasPost) {
                        val fsPager = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = fsPager, modifier = Modifier.fillMaxSize()) { page ->
                            Image(
                                bitmap = if (page == 0) fsShot.rawBitmap else fsShot.bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PageLabel("原图", fsPager.currentPage == 0)
                            val m = if (fsPager.currentPage == 1) {
                                val rw = fsShot.rawBitmap.asAndroidBitmap().width.coerceAtLeast(1)
                                val pw = fsShot.bitmap.asAndroidBitmap().width
                                (pw.toFloat() / rw * 10).toInt() / 10.0
                            } else null
                            PageLabel(
                                m?.takeIf { it > 1.0 }
                                    ?.let { "放大 ×" + if (it == it.toInt().toDouble()) "${it.toInt()}" else "$it" }
                                    ?: "成品",
                                fsPager.currentPage == 1,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(2) { i ->
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (fsPager.currentPage == i) Nm.pri
                                            else Color.White.copy(alpha = 0.4f),
                                        ),
                                )
                            }
                        }
                    } else {
                        Image(
                            bitmap = fsShot.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // ── Progress + cancel (isolated recomposition) ────────────────────
        GenerationProgressSection(phase = phase, onCancel = onCancel)

        // ── Load source (img2img only) & Save rendered image ───────────────
        // ⭐ fix35h: the stray `onImportImageParams?.invoke(uri)` that used to
        // sit here was the package author's slip — loading an img2img source
        // ALSO fired the import-params flow, so "加载图片" and "导入图参" were
        // wired together. Loading a source loads the source, nothing else.
        val pickSrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onPickSrc(uri, queryDisplayName(ctx, uri) ?: "image")
            }
        }
        val pickParamImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onImportImageParams?.invoke(uri)
        }
        val needsSource = mode in setOf(
            WbParams.MODE_IMG2IMG, WbParams.MODE_INPAINT, WbParams.MODE_UPSCALE,
            "face_edit", "animate_portrait", "swap_i2i", "flux_edit", "i2v", "motion",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (needsSource) {
                OutlinedButton(onClick = { pickSrc.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.newui_load))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave, enabled = shot != null) {
                Icon(
                    SaveIcon,
                    contentDescription = stringResource(R.string.save),
                    tint = if (shot != null) Nm.sub else Nm.sub.copy(alpha = 0.4f),
                )
            }
        }

        // ── ⭐ Dedicated Independent Action Buttons: 保存工作流 & 导入图参 ──────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onSaveWorkflow != null) {
                OutlinedButton(
                    onClick = onSaveWorkflow,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Nm.priLine),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Nm.priSoft.copy(alpha = 0.45f),
                        contentColor = Nm.pri,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(SaveIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Nm.pri)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.save_workflow),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (onImportImageParams != null) {
                OutlinedButton(
                    onClick = { pickParamImage.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Nm.fld),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Nm.card,
                        contentColor = Nm.ink,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(com.abrah.nightmare.ui.FlowsIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Nm.ink)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "导入图参",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ── The source line ────────────────────────────────────────────────
        val sourceLabel = when (mode) {
            WbParams.MODE_INPAINT -> "重绘源图"
            WbParams.MODE_UPSCALE -> "放大原图"
            "face_edit" -> "人脸底图"
            "animate_portrait" -> "人像底图"
            "i2v" -> "视频首帧图"
            "motion" -> "迁移人物图"
            "flux_edit" -> "待编辑图"
            "swap_i2i" -> "图生图源"
            else -> stringResource(R.string.newui_src)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$sourceLabel: ",
                style = MaterialTheme.typography.labelMedium,
                color = Nm.sub,
            )
            Text(
                srcName.ifBlank { stringResource(R.string.newui_src_empty) },
                style = MaterialTheme.typography.labelMedium,
                color = if (srcName.isBlank()) Nm.sub else Nm.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (srcName.isNotBlank()) {
                TextButton(onClick = { onPickSrc(null, "") }) { Text("✕") }
            }
        }

        // ── The history strip ──────────────────────────────────────────────
        if (history.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.key }) { sh ->
                    Image(
                        bitmap = sh.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Nm.pri, RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        // ── The run strip: time · model ────────────────────────────────────
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Nm.soft2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⏱ " + (phase.message.takeIf { !phase.running && phase.error == null } ?: "—"),
                style = MaterialTheme.typography.labelMedium,
                color = Nm.sub,
                modifier = Modifier.weight(1f),
            )
            val modelDesc = when {
                mode == WbParams.MODE_UPSCALE -> "超分模型: 4x 独立模型"
                mode in setOf("face_edit", "animate_portrait") -> "人像模型: LivePortrait"
                mode in setOf("t2v", "i2v", "motion") -> "视频模型: Wan 2.2 Turbo"
                mode == "t2v14" -> "视频模型: Wan 2.2 14B"
                else -> stringResource(R.string.newui_model, modelLabel)
            }
            Text(
                modelDesc,
                style = MaterialTheme.typography.labelMedium,
                color = Nm.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── Generate / Cancel ──────────────────────────────────────────────
        Button(
            onClick = if (phase.running) onCancel else onRun,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Nm.pri,
                contentColor = Color.White,
            ),
        ) {
            if (phase.running) {
                Text(
                    stringResource(R.string.cancel_run),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = GeneratePlayIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    val actionLabel = when (mode) {
                        WbParams.MODE_UPSCALE -> "开始超分放大"
                        WbParams.MODE_INPAINT -> "开始局部重绘"
                        "face_edit" -> "开始人脸微调"
                        "animate_portrait" -> "开始动态人像"
                        "flux_edit" -> "开始智能编辑"
                        "t2v", "i2v", "t2v14", "motion" -> "开始生成视频"
                        else -> stringResource(R.string.newui_generate)
                    }
                    Text(
                        actionLabel,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** The display name of a picked document, or null when it cannot be read. */
private fun queryDisplayName(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
    }
} catch (_: Exception) {
    null
}

/**
 * ⭐ fix35i — a pill naming which page of the raw/processed pager is showing.
 * An upscale keeps the PICTURE identical and only the pixels finer, so inside
 * a Fit frame the two pages are indistinguishable without a name.
 */
@Composable
private fun PageLabel(text: String, active: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) Nm.pri.copy(alpha = 0.85f)
                else Color.Black.copy(alpha = 0.45f),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Color.White else Color.White.copy(alpha = 0.75f),
        )
    }
}
/**
 * ⭐ Isolated status header: only this leaf recomposes on step updates during generation,
 * preventing expensive recomposition of the preview box and entire card hierarchy.
 */
@Composable
private fun GenerationStatusHeader(
    phase: RunController.Phase,
    statusText: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (phase.running) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = Nm.pri,
            )
            val progressText = when {
                phase.total > 0 -> "生成中 ${phase.step}/${phase.total} (${(phase.fraction!! * 100).toInt()}%)"
                else -> phase.message.ifBlank { "正在生成…" }
            }
            Text(
                progressText,
                style = MaterialTheme.typography.labelSmall,
                color = Nm.pri,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = Nm.sub,
            )
        }
    }
}

/**
 * ⭐ Isolated progress bar section: steps and fraction stay within this leaf composable.
 */
@Composable
private fun GenerationProgressSection(
    phase: RunController.Phase,
    onCancel: () -> Unit,
) {
    if (phase.running) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    phase.message.ifBlank {
                        if (phase.total > 0) "${phase.step} / ${phase.total}" else "…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Nm.ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel_run))
                }
            }
            LinearProgressIndicator(
                progress = { phase.fraction ?: 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Nm.pri,
                trackColor = Nm.line,
            )
        }
    }
}

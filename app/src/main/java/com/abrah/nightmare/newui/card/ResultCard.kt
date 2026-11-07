package com.abrah.nightmare.newui.card

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
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
        // ── The status line ────────────────────────────────────────────────
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

        // ── The square frame ───────────────────────────────────────────────
        // Fixed 1:1 ratio box with smooth crossfade on new render arrival.
        // Causes zero layout re-measurement because container dimensions are strictly invariant.
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
                if (curShot != null) {
                    if (curShot.file.name.endsWith(".mp4", ignoreCase = true)) {
                        com.abrah.nightmare.ui.ClipPlayer(
                            path = curShot.file.absolutePath,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(
                            bitmap = curShot.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.newui_results),
                            color = Nm.sub,
                        )
                    }
                }
            }
        }

        // ── Progress + cancel, only while a run is on ──────────────────────
        if (phase.running) {
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

        // ── Load source (img2img only) & Save rendered image ───────────────
        val pickSrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onPickSrc(uri, queryDisplayName(ctx, uri) ?: "image")
                onImportImageParams?.invoke(uri)
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
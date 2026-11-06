package com.abrah.nightmare.newui.card

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    NmCard {
        // ── The status line ────────────────────────────────────────────────
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = Nm.sub,
        )

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
                    Image(
                        bitmap = curShot.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
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

        // ── Load source (img2img only) + save ─────────────────────────────
        val pickSrc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPickSrc(uri, queryDisplayName(ctx, uri) ?: "image")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == WbParams.MODE_IMG2IMG) {
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

        // ── The source line ────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.newui_src) + ": ",
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
            Text(
                stringResource(R.string.newui_model, modelLabel),
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
                    Text(
                        stringResource(R.string.newui_generate),
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
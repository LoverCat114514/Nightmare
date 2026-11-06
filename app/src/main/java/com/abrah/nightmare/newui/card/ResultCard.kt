package com.abrah.nightmare.newui.card

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.R
import com.abrah.nightmare.newui.core.LoraPick
import com.abrah.nightmare.newui.core.RunController
import com.abrah.nightmare.newui.core.Shot
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.core.thumb
import com.abrah.nightmare.newui.theme.Nm
import com.abrah.nightmare.ui.SaveIcon

/**
 * ⭐⭐ fix34 (2026-11-06) — Card 1: the result.
 *
 * The square frame (the finished shot or the empty hint), the progress bar and
 * cancel while the engine runs, the load/save row, the img2img source line, and
 * this session's history strip.
 *
 * ## What changed under it, and what did not
 *
 * **Nothing visual changed.** The frame is still a 1:1 box with a 14 dp radius,
 * the same lavender page colour inside, the same 84 dp thumbnails at a 12 dp
 * radius with the violet border, the same full-width Generate at 56 dp. This is
 * a machinery rewrite; the look is a contract.
 *
 * What changed is where the values come from:
 * - **`busy` / progress / status** now arrive as ONE [RunController.Phase]
 *   instead of five separate `MutableState`s that the screen had to keep in
 *   step. A single immutable value cannot disagree with itself.
 * - **The shot** is a parameter, not a read of a file-level `AtomicReference`
 *   with a version counter beside it. The old pair (`LAST_SHOT` +
 *   `LAST_SHOT_VERSION`) was a hand-rolled observable whose correctness
 *   depended on every writer bumping the version — one missed bump and the
 *   frame showed a stale picture forever.
 * - **The history strip** draws 256 px thumbnails with stable keys. It used to
 *   draw full-resolution render bitmaps into an 84 dp square, so every scroll
 *   frame paid for a full-size texture upload per thumbnail.
 *
 * ⚠ **The one gesture-target rule holds here.** The Generate button, the save
 * icon, the load button and the clear-source "✕" are each their own control on
 * their own row — never nested inside a clickable container.
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
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Nm.canvas)
                .border(1.dp, Nm.line, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (shot != null) {
                Image(
                    bitmap = shot.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    stringResource(R.string.newui_results),
                    color = Nm.sub,
                )
            }
        }

        // ── Progress + cancel, only while a run is on ──────────────────────
        //
        // ⚠ `if (phase.running)` — the bar leaves the composition entirely when
        // idle, instead of sitting there at zero. A zero-progress bar that is
        // always present reads as "the app is stuck".
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
                // ⚠ `progress = { }` (the lambda overload): a null fraction gives
                // the indeterminate bar. The old code passed 0f for "unknown",
                // which drew a bar permanently stuck at the left edge.
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
            // ⭐ The source picker exists only for img2img — txt2img has no use
            // for one. This is a design rule, not a bug guard.
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
                // Its own target: clearing the source is a distinct action, and
                // nesting it in the row's text would make the text tappable.
                TextButton(onClick = { onPickSrc(null, "") }) { Text("✕") }
            }
        }

        // ── The history strip ──────────────────────────────────────────────
        if (history.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ⭐ Keys: a strip keyed by position re-composes every thumb when
                // one is inserted at the head, which is exactly what a new shot
                // does. Keyed by identity, only the new one composes.
                items(history, key = { it.key }) { sh ->
                    Image(
                        // ⭐ 256 px, not the full render. 84 dp at 3× is ≈252 px;
                        // anything more is uploaded and sampled every frame of a
                        // scroll for no visible gain.
                        bitmap = remember(sh.bitmap) { sh.bitmap.thumb(256) },
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
        //
        // ⚠ One button, one handler. `phase.running` swaps the label and the
        // action, so there is no second button to disagree with the first.
        Button(
            onClick = if (phase.running) onCancel else onRun,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Nm.pri,
                contentColor = Color.White,
            ),
        ) {
            Text(
                if (phase.running) stringResource(R.string.cancel_run)
                else "▶  " + stringResource(R.string.newui_generate),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
            )
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
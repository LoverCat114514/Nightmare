package com.abrah.nightmare.newui.card

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.Family
import com.abrah.nightmare.R
import com.abrah.nightmare.canvas.MossCanvasActivity
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐ fix34 (2026-11-06) — Card 2: the mode.
 *
 * The txt2img/img2img pill, the denoise slider (img2img only), and the MOSS
 * layout block with its canvas launcher.
 *
 * ## What changed under it, and what did not
 *
 * **Nothing visual changed.** Same pill, same order, same MOSS block, same
 * button. The only edits are the ones a refactor is allowed to make:
 *
 * - The card frame comes from [NmCard] instead of being re-typed here. Three
 *   cards used to repeat the identical `Card(...)` incantation — white fill,
 *   16 dp radius, 1 dp hairline, `elevation = 0`. Three copies means three
 *   places to drift.
 * - **No `remember { mutableStateOf(s.mode) }` mirror.** The old version had
 *   one in the eighth round and removed it in fix30; this file simply never
 *   reintroduces it. `s.mode` is compose state, so reading it here subscribes
 *   this card directly.
 * - `s.mode` writes are plain writes. There is no `modelVer++` beside them —
 *   [WbParams] has no version counter to bump.
 *
 * ⚠ **One gesture target per row.** The MOSS switch is the only handler on its
 * row; the title column carries none.
 */
@Composable
fun ModeCard(
    s: WbParams,
    family: Family,
    onDraft: (String) -> Unit,
    onDraftSize: (Int, Int) -> Unit,
) {
    NmCard {
        SegmentedRow(
            selLeft = s.mode != WbParams.MODE_IMG2IMG,
            left = "✦ " + stringResource(R.string.newui_txt2img),
            right = "🖼 " + stringResource(R.string.newui_img2img),
            onLeft = { s.mode = WbParams.MODE_TXT2IMG },
            onRight = { s.mode = WbParams.MODE_IMG2IMG },
        )

        // ⭐ img2img only: txt2img has no denoise to speak of. The slider is the
        // stateless one from [Controls], so the thumb IS the wire value.
        if (s.mode == WbParams.MODE_IMG2IMG) {
            PostSlider(
                label = stringResource(R.string.newui_denoise),
                value = s.denoise,
                range = 0.05f..0.95f,
                fmt = { String.format(java.util.Locale.US, "%.2f", it) },
                onSet = { s.denoise = it },
            )
        }

        // ⭐ The MOSS layout block exists only for the MOSS family — it is a
        // layout instruction the MOSS sampler reads, and any other family would
        // ignore the flag entirely.
        if (family == Family.MOSS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.newui_moss_layout),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.hint_moss_layout),
                        style = MaterialTheme.typography.bodySmall,
                        color = Nm.sub,
                    )
                }
                // ⚠ The switch owns the row's only handler. No clickable wrapper.
                Switch(checked = s.mossOn, onCheckedChange = { s.mossOn = it })
            }

            if (s.mossOn) {
                val ctx = LocalContext.current
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { r ->
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

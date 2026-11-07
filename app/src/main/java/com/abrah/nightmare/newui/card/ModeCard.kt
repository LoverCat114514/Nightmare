package com.abrah.nightmare.newui.card

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
 * Full multi-flow support (txt2img, img2img, inpaint, upscale) directly within NewUI,
 * smooth sliding indicator mode picker, denoise slider, and animated MOSS layout block.
 */
@Composable
fun ModeCard(
    s: WbParams,
    family: Family,
    onDraft: (String) -> Unit,
    onDraftSize: (Int, Int) -> Unit,
) {
    NmCard {
        // ⭐ 4 modes supported smoothly: 文生图, 图生图, 局部重绘, 超分放大
        ModeSegmentedRow(
            selectedMode = s.mode,
            onSelect = { s.mode = it },
        )

        // ⭐ img2img / inpaint: Denoise slider
        if (s.mode == WbParams.MODE_IMG2IMG || s.mode == WbParams.MODE_INPAINT) {
            PostSlider(
                label = if (s.mode == WbParams.MODE_INPAINT) "重绘重度 (Denoise)" else stringResource(R.string.newui_denoise),
                value = s.denoise,
                range = if (s.mode == WbParams.MODE_INPAINT) 0.05f..1.00f else 0.05f..0.95f,
                fmt = { String.format(java.util.Locale.US, "%.2f", it) },
                onSet = { s.denoise = it },
            )
        }

        // ⭐ Mode hints
        if (s.mode == WbParams.MODE_INPAINT) {
            Text(
                "🎨 局部重绘模式：请在上方加载待重绘的源图片，在提示词输入需要重绘生成的画面内容。",
                style = MaterialTheme.typography.bodySmall,
                color = Nm.sub,
            )
        } else if (s.mode == WbParams.MODE_UPSCALE) {
            Text(
                "🔍 超分放大模式：利用内置 4x 独立超分模型将图片无损放大 4 倍（无需加载主模型 Checkpoint）。",
                style = MaterialTheme.typography.bodySmall,
                color = Nm.sub,
            )
        }

        // ⭐ The MOSS layout block exists only for the MOSS family
        if (family == Family.MOSS && s.mode != WbParams.MODE_UPSCALE) {
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
                Switch(checked = s.mossOn, onCheckedChange = { s.mossOn = it })
            }

            // ⭐ AnimatedVisibility with 280ms cubic bezier curve for smooth expand/shrink
            AnimatedVisibility(
                visible = s.mossOn,
                enter = expandVertically(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                    ),
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
                    ),
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 150),
                ),
            ) {
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
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(stringResource(R.string.moss_open_canvas)) }
            }
        }
    }
}

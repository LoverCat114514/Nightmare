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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.Family
import com.abrah.nightmare.R
import com.abrah.nightmare.canvas.MossCanvasActivity
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐ Complete All-Flow Support for NewUI
 *
 * Fully covers all 5 built-in categories:
 * - 图片 (Pictures): txt2img, img2img, inpaint, upscale
 * - 人脸 (Faces): face_edit, animate_portrait
 * - 高级 (Advanced): swap_t2i, swap_i2i
 * - 编辑 (Edit): flux_edit
 * - 视频 (Video): t2v, i2v, t2v14, motion
 */
private val CATEGORIES = listOf(
    "pictures" to "🖼️ 图片",
    "faces" to "👤 人脸",
    "advanced" to "⚡ 高级",
    "edit" to "✨ 编辑",
    "video" to "🎬 视频",
)

private val CATEGORY_MODES = mapOf(
    "pictures" to listOf(
        "txt2img" to "✦ 文生图",
        "img2img" to "🖼 图生图",
        "inpaint" to "🎨 局部重绘",
        "upscale" to "🔍 超分放大",
    ),
    "faces" to listOf(
        "face_edit" to "😊 人脸微调",
        "animate_portrait" to "🎭 动态人像",
    ),
    "advanced" to listOf(
        "swap_t2i" to "✦ 高级文生图",
        "swap_i2i" to "🖼 高级图生图",
    ),
    "edit" to listOf(
        "flux_edit" to "✨ 智能重绘编辑",
    ),
    "video" to listOf(
        "t2v" to "✦ 文生视频",
        "i2v" to "🎬 图生视频",
        "t2v14" to "🌟 14B顶级视频",
        "motion" to "🕺 动作迁移",
    ),
)

fun categoryForMode(mode: String): String = when (mode) {
    "face_edit", "animate_portrait" -> "faces"
    "swap_t2i", "swap_i2i" -> "advanced"
    "flux_edit" -> "edit"
    "t2v", "i2v", "t2v14", "motion" -> "video"
    else -> "pictures"
}

@Composable
fun ModeCard(
    s: WbParams,
    family: Family,
    onDraft: (String) -> Unit,
    onDraftSize: (Int, Int) -> Unit,
) {
    // Current category automatically follows s.mode
    var activeCategory by remember(s.mode) { mutableStateOf(categoryForMode(s.mode)) }

    NmCard {
        CardTitle("流程模式")

        // ── 1. Top Category Pills ──────────────────────────────────────────
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(CATEGORIES.size) { idx ->
                val (catId, catLabel) = CATEGORIES[idx]
                val isSel = catId == activeCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSel) Nm.priSoft else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSel) Nm.priLine else Nm.fld,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable {
                            activeCategory = catId
                            val defaultMode = CATEGORY_MODES[catId]?.firstOrNull()?.first ?: "txt2img"
                            s.mode = defaultMode
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        catLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) Nm.pri else Nm.sub,
                        maxLines = 1,
                    )
                }
            }
        }

        // ── 2. Sub-mode Segmented Row (smooth sliding translation animation) ─
        val modesInCat = CATEGORY_MODES[activeCategory] ?: emptyList()
        if (modesInCat.isNotEmpty()) {
            PillSegmentedRow(
                selectedId = s.mode,
                items = modesInCat,
                onSelect = { s.mode = it },
            )
        }

        // ── 3. Mode Knobs (Denoise slider for img2img / inpaint / swap_i2i) ─
        if (s.mode in setOf(WbParams.MODE_IMG2IMG, WbParams.MODE_INPAINT, "swap_i2i")) {
            val denoiseLabel = when (s.mode) {
                WbParams.MODE_INPAINT -> "重绘重度 (Denoise)"
                "swap_i2i" -> "高级图生图重绘度 (Denoise)"
                else -> stringResource(R.string.newui_denoise)
            }
            PostSlider(
                label = denoiseLabel,
                value = s.denoise,
                range = if (s.mode == WbParams.MODE_INPAINT) 0.05f..1.00f else 0.05f..0.95f,
                fmt = { String.format(java.util.Locale.US, "%.2f", it) },
                onSet = { s.denoise = it },
            )
        }

        // ── 4. Informative hint for the current active flow ─────────────────
        val modeHint = when (s.mode) {
            "txt2img" -> "✦ 文生图模式：输入正向及负向提示词，直接生成全新画面。"
            "img2img" -> "🖼 图生图模式：在上方加载参考原图，按设定的重绘幅度重新演绎。"
            "inpaint" -> "🎨 局部重绘模式：在上方加载待重绘的源图片，在提示词输入需要重绘生成的画面内容。"
            "upscale" -> "🔍 超分放大模式：利用内置 4x 独立超分模型将图片无损放大 4 倍（无需加载主模型）。"
            "face_edit" -> "😊 人脸微调模式：加载带有人脸的照片，通过 LivePortrait 调整面部表情与动作（无需主模型）。"
            "animate_portrait" -> "🎭 动态人像模式：加载人像图片，让静态人像根据驱动视频生动流畅地运动起来（无需主模型）。"
            "swap_t2i" -> "✦ 高级文生图：支持 LoRA、ControlNet 及 IP-Adapter 多重高级条件组合注入。"
            "swap_i2i" -> "🖼 高级图生图：基于原图进行 LoRA、ControlNet 及 IP-Adapter 深入渲染。"
            "flux_edit" -> "✨ 智能重绘编辑：FLUX.2/Qwen 深度图片编辑，按提示词重构图像。"
            "t2v" -> "✦ 文生视频模式：由提示词驱动生成 Wan 2.2 Turbo 高清动态短片。"
            "i2v" -> "🎬 图生视频模式：加载首帧源图，使静止画面流畅运动成视频短片。"
            "t2v14" -> "🌟 14B顶级视频：由主模型生成首帧，Wan 2.2 14B 大模型完成超高质量视频渲染。"
            "motion" -> "🕺 动作迁移模式：将指定视频中的姿势与动作精准迁移复制到源图片人物中。"
            else -> "已就绪"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Nm.priSoft.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modeHint,
                style = MaterialTheme.typography.bodySmall,
                color = Nm.ink,
            )
        }

        // ── 5. MOSS layout block (MOSS family only) ─────────────────────────
        if (family == Family.MOSS && s.mode in setOf(WbParams.MODE_TXT2IMG, WbParams.MODE_IMG2IMG)) {
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

package com.abrah.nightmare.newui.card

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.core.queryDisplayName
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐ LivePortrait 人脸微调卡片
 */
@Composable
fun FaceEditCard(s: WbParams) {
    NmCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle("😊 人脸微调参数")
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    s.faceSmile = 0.0f
                    s.faceEyes = 0.0f
                    s.faceLipOpen = 0.0f
                    s.faceMouthPout = 0.0f
                    s.facePitch = 0.0f
                    s.faceYaw = 0.0f
                    s.faceRoll = 0.0f
                },
            ) {
                Text("重置归零", style = MaterialTheme.typography.labelMedium, color = Nm.pri)
            }
        }

        // 快捷表情按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { s.faceSmile = 0.75f; s.faceEyes = 0.15f },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Nm.priLine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Nm.priSoft.copy(alpha = 0.5f)),
            ) {
                Text("😊 阳光微笑", style = MaterialTheme.typography.labelSmall, color = Nm.pri)
            }
            OutlinedButton(
                onClick = { s.faceSmile = 0.2f; s.faceEyes = -0.6f },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Nm.fld),
            ) {
                Text("😉 眨眼表情", style = MaterialTheme.typography.labelSmall, color = Nm.ink)
            }
            OutlinedButton(
                onClick = { s.faceRoll = 0.35f; s.faceYaw = 0.2f },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Nm.fld),
            ) {
                Text("🙃 俏皮歪头", style = MaterialTheme.typography.labelSmall, color = Nm.ink)
            }
        }

        // 微调滑块
        PostSlider(
            label = "嘴角微笑 (Smile)",
            value = s.faceSmile,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceSmile = it },
        )
        PostSlider(
            label = "眼睛开合 (Eye Open)",
            value = s.faceEyes,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceEyes = it },
        )
        PostSlider(
            label = "嘴唇张合 (Lip Open)",
            value = s.faceLipOpen,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceLipOpen = it },
        )
        PostSlider(
            label = "嘴角撇嘴 (Mouth Pout)",
            value = s.faceMouthPout,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceMouthPout = it },
        )
        PostSlider(
            label = "头部俯仰 (Head Pitch)",
            value = s.facePitch,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.facePitch = it },
        )
        PostSlider(
            label = "头部左右 (Head Yaw)",
            value = s.faceYaw,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceYaw = it },
        )
        PostSlider(
            label = "头部倾斜 (Head Roll)",
            value = s.faceRoll,
            range = -1.0f..1.0f,
            fmt = { String.format(java.util.Locale.US, "%+.2f", it) },
            onSet = { s.faceRoll = it },
        )
    }
}

/**
 * ⭐ 动作驱动视频选择卡片 (动态人像 / 动作迁移)
 */
@Composable
fun DriverVideoCard(
    s: WbParams,
    onPickVideo: (Uri?, String) -> Unit,
) {
    val ctx = LocalContext.current
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri) ?: "video.mp4"
            onPickVideo(uri, name)
        }
    }

    NmCard {
        CardTitle("🎬 动作驱动视频")
        Text(
            "选择一段包含面部动作或身体姿态的 MP4 视频，模型将提取其中的动态并迁移到源人像上。",
            style = MaterialTheme.typography.bodySmall,
            color = Nm.sub,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { pickVideo.launch(arrayOf("video/*")) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Nm.priLine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Nm.priSoft.copy(alpha = 0.45f)),
            ) {
                Text(
                    if (s.driverVideoName.isBlank()) "选择驱动视频 (MP4)" else "更换驱动视频",
                    color = Nm.pri,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (s.driverVideoName.isNotBlank()) {
                Text(
                    s.driverVideoName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Nm.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPickVideo(null, "") }) {
                    Text("✕", color = Nm.sub)
                }
            }
        }

        PostSlider(
            label = "动画时长 (Seconds)",
            value = s.videoSeconds.toFloat(),
            range = 1f..30f,
            fmt = { "${it.toInt()} 秒" },
            onSet = { s.videoSeconds = it.toInt() },
        )
    }
}

/**
 * ⭐ 视频生成配置卡片
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VideoParamsCard(s: WbParams) {
    NmCard {
        CardTitle("🎬 视频生成设置")

        // ── 视频模型选择（仅保留应用内置真实存在的视频模型） ───────
        val videoModels = listOf(
            "wan22_turbo" to "Wan 2.2 Turbo",
            "wan22_14b" to "Wan 2.2 14B",
            "wan21_nsfw13" to "Wan 2.1 1.3B",
            "minimax_h3" to "MiniMax H3",
        )
        DropdownWire(
            label = "视频模型",
            current = s.videoModel,
            choices = videoModels.map { it.first },
            textOf = { id -> videoModels.firstOrNull { it.first == id }?.second ?: id },
            onPick = { s.videoModel = it },
        )

        // 视频时长选择
        Text("生成片段时长", style = MaterialTheme.typography.labelMedium, color = Nm.sub)
        val durations = listOf(2 to "2 秒 (极速预览)", 3 to "3 秒 (标准)", 5 to "5 秒 (完整动作)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            durations.forEach { (sec, label) ->
                val isSel = s.videoSeconds == sec
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Nm.priSoft else Color.Transparent)
                        .border(1.dp, if (isSel) Nm.priLine else Nm.fld, RoundedCornerShape(12.dp))
                        .clickable { s.videoSeconds = sec }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) Nm.pri else Nm.sub,
                        maxLines = 1,
                    )
                }
            }
        }

        // 随机种子控制
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("固定随机种子 (Seed)", style = MaterialTheme.typography.titleSmall, color = Nm.ink)
                Text(
                    if (s.fixedSeed) "当前锁定为: ${s.seed}" else "随机生成全新种子",
                    style = MaterialTheme.typography.bodySmall,
                    color = Nm.sub,
                )
            }
            Switch(
                checked = s.fixedSeed,
                onCheckedChange = {
                    s.fixedSeed = it
                    if (it && s.seed == 0L) s.seed = (1L..999999999L).random()
                },
            )
        }
    }
}

/**
 * ⭐ 高级控制卡片 (ControlNet & IP-Adapter)
 */
@Composable
fun AdvancedControlCard(
    s: WbParams,
    onPickControlNet: (Uri?, String) -> Unit,
    onPickIpAdapter: (Uri?, String) -> Unit,
) {
    val ctx = LocalContext.current
    val pickCn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri) ?: "cn_ref.png"
            onPickControlNet(uri, name)
        }
    }
    val pickIp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri) ?: "ip_ref.png"
            onPickIpAdapter(uri, name)
        }
    }

    NmCard {
        CardTitle("⚡ 高级条件控制 (Swap)")

        // ── ControlNet ───────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ControlNet 姿态/边缘控制", style = MaterialTheme.typography.titleSmall, color = Nm.ink)
                Text("引导画面构图、骨骼姿态或线稿轮廓", style = MaterialTheme.typography.bodySmall, color = Nm.sub)
            }
            Switch(checked = s.controlNetOn, onCheckedChange = { s.controlNetOn = it })
        }
        if (s.controlNetOn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pickCn.launch(arrayOf("image/*")) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Nm.priLine),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Nm.priSoft.copy(alpha = 0.4f)),
                ) {
                    Text(
                        if (s.controlNetImageName.isBlank()) "选择 ControlNet 参考图" else "更换参考图",
                        color = Nm.pri,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (s.controlNetImageName.isNotBlank()) {
                    Text(
                        s.controlNetImageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Nm.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPickControlNet(null, "") }) { Text("✕", color = Nm.sub) }
                }
            }
            PostSlider(
                label = "ControlNet 控制强度",
                value = s.controlNetStrength,
                range = 0.1f..2.0f,
                fmt = { String.format(java.util.Locale.US, "%.2f", it) },
                onSet = { s.controlNetStrength = it },
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── IP-Adapter ───────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("IP-Adapter 垫图风格参考", style = MaterialTheme.typography.titleSmall, color = Nm.ink)
                Text("迁移参考图的角色特征与美术色彩风格", style = MaterialTheme.typography.bodySmall, color = Nm.sub)
            }
            Switch(checked = s.ipAdapterOn, onCheckedChange = { s.ipAdapterOn = it })
        }
        if (s.ipAdapterOn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pickIp.launch(arrayOf("image/*")) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Nm.priLine),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Nm.priSoft.copy(alpha = 0.4f)),
                ) {
                    Text(
                        if (s.ipAdapterImageName.isBlank()) "选择 IP-Adapter 垫图" else "更换垫图",
                        color = Nm.pri,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (s.ipAdapterImageName.isNotBlank()) {
                    Text(
                        s.ipAdapterImageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Nm.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPickIpAdapter(null, "") }) { Text("✕", color = Nm.sub) }
                }
            }
            PostSlider(
                label = "IP-Adapter 迁移权重",
                value = s.ipAdapterWeight,
                range = 0.1f..1.5f,
                fmt = { String.format(java.util.Locale.US, "%.2f", it) },
                onSet = { s.ipAdapterWeight = it },
            )
        }
    }
}

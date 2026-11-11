package com.abrah.nightmare.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abrah.nightmare.R
import com.abrah.nightmare.newui.theme.Nm

/**
 * Transparent backdrop that closes the More card on tap outside.
 */
@Composable
internal fun MoreMenuScrim(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (open) {
        Box(
            modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

/**
 * ⭐⭐⭐ The More Menu card — elegant floating card over the bottom bar's 'More' slot
 * with pure NewUI aesthetics and fluid spring animation (reverted to single clean card).
 */
@Composable
internal fun MoreMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onOpenGallery: () -> Unit,
    onSettings: (() -> Unit)? = null,
    onResults: (() -> Unit)? = null,
    onToast: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(open) }
    val p = remember { Animatable(0f) }

    LaunchedEffect(open) {
        if (open) {
            shown = true
            p.animateTo(1f, tween(300, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)))
        } else if (shown) {
            p.animateTo(0f, tween(180, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f)))
            shown = false
        }
    }

    if (!shown) return
    BackHandler { onDismiss() }

    Box(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // Sits right above the floating bottom bar
            .padding(bottom = BOTTOM_BAR_RESERVE + 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.98f),
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    alpha = p.value.coerceIn(0f, 1f)
                    scaleX = 0.90f + 0.10f * p.value
                    scaleY = 0.90f + 0.10f * p.value
                    translationY = (1f - p.value) * 22.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // ── Header Row (Clean: ✨ 更多功能) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✨", fontSize = 15.sp)
                    }
                    Text(
                        text = "更多功能",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Nm.ink,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── Online Gallery Card ──
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF9F8FD),
                    border = BorderStroke(1.dp, Color(0xFFE9E4F5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenGallery),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Icon Box with gradient
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                    )
                                )
                                .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x407C3AED)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_gallery),
                                contentDescription = "在线画廊",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "在线画廊",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Nm.ink,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "AI 绘画咒语图库 · 灵感检索与提示词应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = Nm.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Chevron
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDE9FE).copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "›",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                            )
                        }
                    }
                }
            }
        }
    }
}

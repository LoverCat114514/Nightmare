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
 * ⭐ The transparent touch detector covering the screen behind the More card:
 * tap anywhere outside closes the card.
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
 * ⭐⭐⭐ The More Menu card — an elegant, responsive floating card expanding out of the
 * bottom bar's 'More' slot (user ask 2026-11-11: "在线画廊的入口就放在底导航栏的更多里面吧，
 * 点击更多展开卡片，然后在里面放着在线画廊").
 */
@Composable
internal fun MoreMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(open) }
    val p = remember { Animatable(0f) }

    LaunchedEffect(open) {
        if (open) {
            shown = true
            p.animateTo(1f, tween(320, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)))
        } else if (shown) {
            p.animateTo(0f, tween(200, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f)))
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
            .padding(bottom = BOTTOM_BAR_RESERVE + 12.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.97f),
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    alpha = p.value.coerceIn(0f, 1f)
                    scaleX = 0.88f + 0.12f * p.value
                    scaleY = 0.88f + 0.12f * p.value
                    translationY = (1f - p.value) * 26.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // ── Header Row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEDE9FE)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✨", fontSize = 14.sp)
                        }
                        Text(
                            text = "更多功能",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Nm.ink,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFF3E8FF),
                    ) {
                        Text(
                            text = "AI 创作社区",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "在线画廊",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Nm.ink,
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFEDE9FE),
                                ) {
                                    Text(
                                        text = "aitag.win",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6D28D9),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "AI TAG 咒语图库 · 灵感检索与一键应用",
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

                Spacer(Modifier.height(14.dp))

                // ── Footer Hint ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "💡 浏览海量生图作品，在画廊中快捷提取提示词填入画布",
                        style = MaterialTheme.typography.labelSmall,
                        color = Nm.sub.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

package com.abrah.nightmare.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Extras carrying the tapped card's screen coordinates (in pixels) so the target
 * Activity pops up directly from that card and shrinks back into it on close.
 */
object PopOrigin {
    const val EXTRA_X = "pop_origin_x"
    const val EXTRA_Y = "pop_origin_y"
    const val EXTRA_W = "pop_origin_w"
    const val EXTRA_H = "pop_origin_h"

    fun Intent.putOrigin(rect: Rect?): Intent {
        if (rect != null && rect.width > 0f && rect.height > 0f) {
            putExtra(EXTRA_X, rect.center.x)
            putExtra(EXTRA_Y, rect.center.y)
            putExtra(EXTRA_W, rect.width)
            putExtra(EXTRA_H, rect.height)
        }
        return this
    }

    fun Activity.disableWindowTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

/**
 * ⭐⭐⭐ Shared-Origin Pop-Up Container:
 * Animates a fullscreen Activity from the exact tapped card's bounds (small → full)
 * on enter, and shrinks smoothly back into that card on exit.
 */
@Composable
fun PopExpandContainer(
    intent: Intent,
    onFinish: () -> Unit,
    content: @Composable (requestClose: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }

    val originX = intent.getFloatExtra(PopOrigin.EXTRA_X, -1f)
    val originY = intent.getFloatExtra(PopOrigin.EXTRA_Y, -1f)
    val originW = intent.getFloatExtra(PopOrigin.EXTRA_W, -1f)
    val originH = intent.getFloatExtra(PopOrigin.EXTRA_H, -1f)

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 360,
                easing = CubicBezierEasing(0.18f, 0.95f, 0.25f, 1.0f),
            ),
        )
    }

    val requestClose: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f),
                    ),
                )
                onFinish()
            }
        }
    }

    BackHandler {
        requestClose()
    }

    val p = progress.value.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f * p)),
    ) {
        val density = LocalDensity.current
        val screenW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val screenH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val hasOrigin = originX > 0f && originY > 0f && originW > 0f && originH > 0f
        val targetPivotX = if (hasOrigin) originX else screenW * 0.5f
        val targetPivotY = if (hasOrigin) originY else screenH * 0.82f

        val startScaleX = if (hasOrigin) (originW / screenW).coerceIn(0.28f, 0.9f) else 0.78f
        val startScaleY = if (hasOrigin) (originH / screenH).coerceIn(0.10f, 0.9f) else 0.78f

        val curScaleX = startScaleX + (1f - startScaleX) * p
        val curScaleY = startScaleY + (1f - startScaleY) * p

        val deltaX = (targetPivotX - screenW * 0.5f) * (1f - p)
        val deltaY = (targetPivotY - screenH * 0.5f) * (1f - p)

        val cornerDp = (24f * (1f - p)).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (p * 1.65f).coerceIn(0f, 1f)
                    scaleX = curScaleX
                    scaleY = curScaleY
                    translationX = deltaX
                    translationY = deltaY
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .clip(RoundedCornerShape(cornerDp)),
        ) {
            content(requestClose)
        }
    }
}

package com.abrah.nightmare.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * ⭐⭐⭐ A destination card (Models / Flows / Results / full Settings) INSIDE the
 * main window, driven by a `visible` flag — v2 (2026-11-06, the user's report):
 *
 * ⭐ **82 % of the height** (was 62 %): most of the screen, but the top edge
 * stays — "至少要将上半边也占居但不要顶头".
 *
 * ⭐ **It rises from the BOTTOM edge** (translateY 64 dp → 0, origin at the
 * card's bottom centre) — "不应该是从底部弹出吗", not out of the side.
 *
 * ⭐ **It animates OUT.** The card watches `visible` itself: when the flag
 * drops (any closer — scrim, Back, the bar's Canvas slot) it plays the exit
 * FIRST and only then stops composing, so a card can never vanish mid-air
 * ("关闭时凭空消失"). The caller keeps it composed unconditionally; `shown`
 * here is the only thing that gates the layout. Re-opening while the exit is
 * still running re-enters cleanly (the effect just animates back up).
 *
 * Exit 220 ms ≈ 69 % of the 320 ms entrance — the skill's 65–75 % rule.
 */
@Composable
internal fun DestCardSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(visible) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            p.animateTo(1f, tween(320, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        } else if (shown) {
            p.animateTo(0f, tween(220, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)))
            shown = false
        }
    }
    if (!shown) return
    BackHandler { onDismiss() }
    // ⭐ fix32: the layers below are hit-testable the whole time `shown` is up.
    // The scrim's tap-closer and the card's own swallow ride `graphicsLayer
    // { alpha }` — which only affects DRAWING — so during the entrance/exit
    // window (alpha ≈ 0, the layer invisible) they still ate EVERY tap on
    // screen: the invisible-scrim signature (the same bug class the closed
    // SettingsMenu capsules had, fixed 2026-11-05 by making them inert).
    // Gate the GESTURE layers on real visibility: alpha 0 = no interception.
    val interactive = p.value > 0.02f
    Box(Modifier.fillMaxSize()) {
        // Ambient: the dim rides the same progress, so it deepens with the
        // rise and lifts with the exit.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.value }
                .background(Color.Black.copy(alpha = 0.34f))
                .then(
                    if (interactive) Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    else Modifier,
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_BAR_RESERVE + 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    // ⚠ Swallows taps so a tap on the BODY never reaches the scrim.
                    // ⭐ fix32: only while actually visible — same gate as the scrim.
                    .then(
                        if (interactive) Modifier.pointerInput(Unit) { detectTapGestures { } }
                        else Modifier,
                    )
                    .graphicsLayer {
                        val v = p.value
                        alpha = v
                        // ⭐ The rise: out of the BOTTOM edge — Y leads (64 dp),
                        // the scale does the growth, the origin is the card's own
                        // bottom centre. No side origin any more (2026-11-06).
                        translationY = (1f - v) * 64.dp.toPx()
                        scaleX = 0.96f + 0.04f * v
                        scaleY = 0.96f + 0.04f * v
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            ) { content() }
        }
    }
}

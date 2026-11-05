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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * ⭐⭐⭐ A destination card (Models / Flows / Results / full Settings) INSIDE the
 * main window — not a [PullDownSheet] (the user, 2026-11-06: *"这个下沉问题不如换个
 * 方式解决…点击底导航栏模型、流程、结果的时候往上弹窗卡片，这样让底导航栏不用下沉
 * 也可以打开这三个"*).
 *
 * Why not a sheet: a sheet is its own window, and in those windows the insets
 * never reach Compose AND the decor listener behind [bottomInset] never fired
 * (measured on the phone: the sheet's bar sat 50 px lower than the main
 * window's — the user's "下沉"). Here there is NO second window: the bar stays
 * where the activity put it, so it cannot sink, and it stays LIVE — another
 * slot switches the card in place.
 *
 * Same shape as [SettingsCardSheet]: bottom-centred, 62 % of the height, clear
 * of the bar ([BOTTOM_BAR_RESERVE]), unfolding out of the tapped bar slot
 * (280 ms, `cubic-bezier(0.2, 0, 0, 1)`; exit 190 ms ≈ 68 % — the skill's
 * exit-is-65–75 %-of-entrance rule).
 */
@Composable
internal fun DestCardSheet(
    onDismiss: () -> Unit,
    /** ⭐ The tapped bar slot's centre, as a fraction of the screen height. */
    anchorY: Float = 0.9f,
    content: @Composable () -> Unit,
) {
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(280, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
    }
    val closing = remember { mutableStateOf(false) }
    fun close() {
        if (closing.value) return
        closing.value = true
        scope.launch {
            p.animateTo(0f, tween(190, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)))
            onDismiss()
        }
    }
    BackHandler { close() }
    Box(Modifier.fillMaxSize()) {
        // Ambient: the dim deepens as the card grows, lightens as it leaves.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * p.value))
                .pointerInput(Unit) { detectTapGestures { close() } },
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
                    .fillMaxHeight(0.62f)
                    // ⚠ Swallows taps so a tap on the BODY never reaches the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .graphicsLayer {
                        val v = p.value
                        alpha = v
                        // The slot's height within THIS card (the card is the bottom
                        // 62 % of the area the anchor is a fraction of).
                        val oy = ((anchorY - 0.38f) / 0.62f).coerceIn(0.15f, 0.9f)
                        // ⭐ The unfold: LEFT out of the slot — X leads (24 dp), Y
                        // follows lightly (10 dp); the scale does the growth.
                        translationX = (1f - v) * 24.dp.toPx()
                        translationY = (1f - v) * 10.dp.toPx()
                        scaleX = 0.88f + 0.12f * v
                        scaleY = 0.94f + 0.06f * v
                        transformOrigin = TransformOrigin(1f, oy)
                    },
            ) { content() }
        }
    }
}

package com.abrah.nightmare.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.asComposeRenderEffect

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.abrah.nightmare.R

/**
 * ⭐⭐⭐ The Settings menu — SIX SEPARATE CAPSULES that grow up out of the bottom
 * bar's Settings slot (the user, 2026-11-05: *"图一展开的这样子不好看，要那种胶囊型
 * 分开的"*).
 *
 * ⭐  Six pills, right-aligned and clear of the bar, the first five being Settings'
 * own pages and the last one the whole screen: 外观 / 模型 / 性能 / 高级 / 提示词 /
 * 设置. No card behind them — the capsules ARE the menu.
 *
 * ⚠⚠ The ORDER is the user's, not [SettingsScreen]'s page order — hence the
 * explicit index pair on every row rather than a list of page enums. A row's
 * `Int` is the index into Settings' FIXED page order (`Appearance, Prompts,
 * Models, Performance, Advanced`).
 *
 * ⭐  Motion (`motion-design` skill, Corporate personality): the capsules pop up
 * one at a time from their own bottom (a Y scale about `TransformOrigin(0.5f, 1f)`,
 * 220 ms `cubic-bezier(0.2, 0, 0, 1)`, 30 ms apart from 60 ms in — the skill's own
 * Dropdown recipe). 430 ms total, inside its < 500 ms budget. The exit fades all
 * six together at 150 ms `ease-in`, the skill's 65–75 % of the entrance.
 */
@Composable
internal fun SettingsMenu(
    open: Boolean,
    /** A row tapped: the index into Settings' fixed page order (see the note above). */
    onPick: (Int) -> Unit,
    /** The last row — the whole Settings screen, pills and all. */
    onFull: () -> Unit,
    /** ⭐ A capsule was tapped: its center as a fraction of the screen height —
     * the anchor the card grows LEFT out of (the user, 2026-11-05). */
    onAnchor: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ⚠ Resolved HERE, in composition: a lambda calling `stringResource` is a
    // composable call from a non-composable context and does not compile.
    val rows = listOf(
        0 to stringResource(R.string.settings_appearance),
        2 to stringResource(R.string.settings_models),
        3 to stringResource(R.string.settings_performance),
        4 to stringResource(R.string.settings_advanced),
        1 to stringResource(R.string.settings_prompts),
    )
    val fullLabel = stringResource(R.string.settings)
    var active by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open) active = true
    }
    val menuAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (open) 120 else 180),
        finishedListener = { if (it == 0f) active = false },
        label = "settingsMenuActive",
    )
    if (!active && !open) return

    val rootH = remember { mutableStateOf(1f) }
    val onGeometry: (LayoutCoordinates) -> Unit = { coords ->
        val h = rootH.value
        if (h > 0f) {
            val center = coords.positionInRoot().y + coords.size.height / 2f
            onAnchor((center / h).coerceIn(0f, 1f))
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // ⭐ Right-aligned and clear of the bar: the bar is 64 dp tall with a
            // 12 dp hover gap, which is what [BOTTOM_BAR_RESERVE] reserves.
            .padding(end = 16.dp, bottom = BOTTOM_BAR_RESERVE + 8.dp)
            .onGloballyPositioned { rootH.value = it.size.height.toFloat() },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEachIndexed { i, row ->
                MenuCapsule(
                    label = row.second,
                    open = open,
                    index = i,
                    onTap = { c ->
                        onGeometry(c)
                        onPick(row.first)
                    },
                )
            }
            MenuCapsule(
                label = fullLabel,
                open = open,
                index = rows.size,
                onTap = { c ->
                    onGeometry(c)
                    onFull()
                },
            )
        }
    }
}

/**
 * ⭐ One capsule: a full-round pill on `surfaceContainer` with a faint outline —
 * the bar's own glass. Fades in [index] × 30 ms after the bar opens the menu, and
 * rises into place from its own bottom (the Secondary layer; the capsule's scale is
 * the Primary one).
 */
@Composable
private fun MenuCapsule(
    label: String,
    open: Boolean,
    index: Int,
    /** ⭐ The tap: report this capsule's geometry, then pick. */
    onTap: (LayoutCoordinates) -> Unit,
) {
    val p by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (open) {
            tween(
                durationMillis = 150,
                delayMillis = 20 + index * 14,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
            )
        } else {
            tween(220, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f))
        },
        label = "settingsMenuCapsule$index",
    )
    val interaction = remember { MutableInteractionSource() }
    val coords = remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Dynamic horizontal motion blur radius on retraction (0 -> 16px)
    val blurRadius = (1f - p) * 16f

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.White.copy(alpha = 0.90f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.85f),
        ),
        modifier = Modifier
            .onGloballyPositioned { coords.value = it }
            .then(
                if (open) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { coords.value?.let(onTap) },
                ) else Modifier,
            )
            .graphicsLayer {
                alpha = p
                // ⭐ Cascade retraction: capsules gracefully waterfall down-right into the bottom bar
                translationX = (1f - p) * (20.dp.toPx() + index * 6.dp.toPx())
                translationY = (1f - p) * (24.dp.toPx() + index * 12.dp.toPx())
                scaleY = 0.75f + 0.25f * p
                scaleX = 0.85f + 0.15f * p
                transformOrigin = TransformOrigin(1f, 1f)

                // ⭐ Dynamic Motion Blur on text and capsule during retraction!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !open && blurRadius > 0.5f) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadius,
                        0.1f,
                        android.graphics.Shader.TileMode.CLAMP,
                    ).asComposeRenderEffect()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = null
                }
            },
    ) {
        Box(
            Modifier
                .widthIn(min = 120.dp)
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * ⭐ The scrim the menu opens over: the canvas dims, a tap anywhere closes the menu.
 *
 * ⚠⚠ Composed BEFORE the bar (the caller's `Box` order is scrim → bar → menu), so
 * the bar stays bright and stays tappable: a tap on another slot closes this menu
 * and does that slot's job, which is the rule every destination follows.
 */
@Composable
internal fun SettingsMenuScrim(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Transparent tap-dismiss backdrop (no black shadow mask)
    if (open) {
        Box(
            modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

/**
 * ⭐⭐⭐ One Settings page as a FLOATING CARD — what a row of [SettingsMenu] opens
 * (the user, 2026-11-05: *"点击对应的，然后按照箭头指着的方向展开那个页面，做一个
 * 悬浮的方卡"*).
 *
 * ⭐  IN THE MAIN WINDOW (2026-11-06): no dialog any more — the caller composes
 * the bar around this card, at the real window edge. Why: in a dialog window the
 * insets never reach Compose AND the decor listener behind [bottomInset] never
 * fired (measured: a bar drawn by the card's overlay sat 50 px lower than the
 * main window's — the user's "下沉").
 *
 * ⚠⚠ **A card, not a sheet.** It is clear of the bar, grows LEFT out of the tapped
 * capsule (280 ms, `cubic-bezier(0.2, 0, 0, 1)`) and takes a SHARE of the screen
 * rather than the whole pull-down panel — the canvas stays visible around it,
 * which is what makes the growth read as growth. The content scrolls inside and
 * the card itself never resizes (the reason `SwipeTabs` takes `fillHeight`).
 *
 * ⭐  What the card draws above the page — the quick switch the user asked for
 * (*"图二展开后将其余选项胶囊放到上方提供快捷切换页面"*) — is the page's own pill
 * row ([SettingsScreen]'s `onlyPage`), so the page opened is the page shown and
 * the rest are one tap away.
 */
@Composable
internal fun SettingsCardSheet(
    visible: Boolean,
    anchorY: Float = 0.8f,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(visible) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            // ⭐ Fluid water-droplet split: 460ms with smooth, elegant fluid curve (pacing matches other sheets)
            p.animateTo(1f, tween(460, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)))
        } else if (shown) {
            p.animateTo(0f, tween(260, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f)))
            shown = false
        }
    }
    if (!shown) return
    BackHandler { onDismiss() }

    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { p.value > 0.02f }.collect { interactive = it }
    }
    Box(Modifier.fillMaxSize()) {
        // Transparent tap-dismiss backdrop (no dark black shadow!)
        if (interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        BoxWithConstraints(
            Modifier.fillMaxSize(),
        ) {
            val totalW = maxWidth
            val totalH = maxHeight
            val v = p.value

            // Capsule is on the right side: left end is at totalW - 136.dp
            val capsuleLeftX = totalW - 136.dp
            val capsuleCenterY = totalH * anchorY.coerceIn(0.2f, 0.9f)

            // Target card bounds: 16dp margins, bottom reserve
            val targetLeft = 16.dp
            val targetRight = totalW - 16.dp
            val targetBottom = totalH - BOTTOM_BAR_RESERVE - 8.dp
            val targetHeight = totalH * 0.62f
            val targetTop = targetBottom - targetHeight

            // ⭐ Water-droplet split to the left:
            // At v = 0: a compact round droplet sitting at (capsuleLeftX - 24.dp, capsuleCenterY - 18.dp)
            // As v -> 1: expands leftward across the screen to targetLeft (16.dp), blooming into the card!
            val currentLeft = androidx.compose.ui.unit.lerp(capsuleLeftX - 24.dp, targetLeft, v)
            val currentRight = androidx.compose.ui.unit.lerp(capsuleLeftX + 16.dp, targetRight, v)
            val currentTop = androidx.compose.ui.unit.lerp(capsuleCenterY - 18.dp, targetTop, v)
            val currentBottom = androidx.compose.ui.unit.lerp(capsuleCenterY + 18.dp, targetBottom, v)

            val currentWidth = (currentRight - currentLeft).coerceAtLeast(36.dp)
            val currentHeight = (currentBottom - currentTop).coerceAtLeast(36.dp)

            // Corner radius morphs from a droplet circle (28dp) to the floating card (20dp)
            val cornerRadius = androidx.compose.ui.unit.lerp(28.dp, 20.dp, v)

            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = Color.White.copy(alpha = 0.95f),
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(
                    1.2.dp,
                    Color.White.copy(alpha = 0.90f),
                ),
                modifier = Modifier
                    .offset(x = currentLeft, y = currentTop)
                    .width(currentWidth)
                    .height(currentHeight)
                    .then(
                        if (interactive) Modifier.pointerInput(Unit) { detectTapGestures { } }
                        else Modifier,
                    )
                    .graphicsLayer {
                        alpha = v.coerceIn(0f, 1f)
                    },
            ) {
                // Content smoothly blooms in as the droplet card reaches full size
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((v - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        },
                ) {
                    content()
                }
            }
        }
    }
}

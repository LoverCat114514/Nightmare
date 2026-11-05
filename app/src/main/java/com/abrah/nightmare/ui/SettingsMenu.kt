package com.abrah.nightmare.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.abrah.nightmare.R

/**
 * ⭐⭐⭐ The Settings menu — the vertical list that grows UP out of the bottom bar's
 * Settings slot (the user, 2026-11-05: *"点击设置后按照箭头1的方向向上展开设置里面的
 * 功能，依次竖向排开，整体右靠"*).
 *
 * ⭐  Six rows, right-aligned, five of them Settings' own pages and the last one
 * the whole screen: 外观 / 模型 / 性能 / 高级 / 提示词 / 设置.
 *
 * ⚠⚠ The ORDER is the user's, not [SettingsScreen]'s page order — hence the
 * explicit index pair on every row rather than a list of page enums. A row's
 * `Int` is the index into Settings' FIXED page order (`Appearance, Prompts,
 * Models, Performance, Advanced`), which is what `SettingsScreen`'s `onlyPage`
 * takes.
 *
 * ⭐  Motion (`motion-design` skill, Corporate personality): the card grows from
 * its BOTTOM-RIGHT corner upward (a Y scale about `TransformOrigin(1f, 1f)`,
 * 220 ms `cubic-bezier(0.2, 0, 0, 1)`), and the rows fade in 30 ms apart starting
 * 70 ms in — a Standard stagger, 420 ms total, inside the skill's < 500 ms budget.
 * The exit is `ease-in` at 150 ms, the skill's 65–75 % of the entrance.
 */
@Composable
internal fun SettingsMenu(
    open: Boolean,
    /** A row tapped: the index into Settings' fixed page order (see the note above). */
    onPick: (Int) -> Unit,
    /** The last row — the whole Settings screen, pills and all. */
    onFull: () -> Unit,
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
    Box(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // ⭐ Right-aligned and clear of the bar: the bar is 64 dp tall with a
            // 12 dp hover gap, which is what [BOTTOM_BAR_RESERVE] reserves.
            .padding(end = 16.dp, bottom = BOTTOM_BAR_RESERVE + 8.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // ⭐ The card's own growth: bottom-right origin, so it unfolds UPWARD.
        val grow by animateFloatAsState(
            targetValue = if (open) 1f else 0f,
            animationSpec = if (open) {
                tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
            } else {
                tween(150, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            },
            label = "settingsMenuGrow",
        )
        if (grow > 0f) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .width(168.dp)
                    .graphicsLayer {
                        alpha = grow
                        // ⚠ 0.72 rather than 0: a full collapse reads as a flicker
                        // on the first frame, and the skill's 3 % overshoot budget
                        // is about the settled state, not the start.
                        scaleY = 0.72f + 0.28f * grow
                        scaleX = 0.96f + 0.04f * grow
                        transformOrigin = TransformOrigin(1f, 1f)
                    },
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    rows.forEachIndexed { i, row ->
                        MenuRow(
                            label = row.second,
                            open = open,
                            index = i,
                            onClick = { onPick(row.first) },
                        )
                    }
                    // ⭐ The rule before the last row: the five pages are one kind
                    // of thing, "Settings" the whole of it.
                    Box(
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    MenuRow(
                        label = fullLabel,
                        open = open,
                        index = rows.size,
                        onClick = onFull,
                    )
                }
            }
        }
    }
}

/**
 * ⭐ One row: right-aligned text, fading in [index] × 30 ms after the card starts
 * growing, and rising 10 dp into place — the skill's Secondary layer (the card's
 * scale is the Primary one).
 */
@Composable
private fun MenuRow(
    label: String,
    open: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    val p by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(
            durationMillis = 170,
            // ⚠ Stagger on the way IN only: an exit that walked the rows back out
            // would take 6 × 30 ms longer than the card and read as a stutter.
            delayMillis = if (open) 70 + index * 30 else 0,
            easing = LinearOutSlowInEasing,
        ),
        label = "settingsMenuRow$index",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer {
                alpha = p
                translationY = (1f - p) * 10.dp.toPx()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
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
    val dim by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (open) 200 else 150),
        label = "settingsMenuScrim",
    )
    if (dim > 0f) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f * dim))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
}

/**
 * ⭐⭐⭐ One Settings page as a FLOATING CARD — what a row of [SettingsMenu] opens
 * (the user, 2026-11-05: *"点击对应的，然后按照箭头指着的方向展开那个页面，做一个
 * 悬浮的方卡"*).
 *
 * ⭐  Its own window, like every sheet over the canvas ([PullDownSheet]'s
 * reasoning): the bar rides inside it via [overlay], so the app's navigation stays
 * live while a page is open.
 *
 * ⚠⚠ **A card, not a sheet.** It is clear of the bar, grows UP from its bottom-right
 * corner (260 ms, `cubic-bezier(0.2, 0, 0, 1)`) and takes a SHARE of the screen
 * rather than the whole pull-down panel — the canvas stays visible around it,
 * which is what makes the growth read as growth. The content scrolls inside and
 * the card itself never resizes (the reason `SwipeTabs` takes `fillHeight`).
 */
@Composable
internal fun SettingsCardSheet(
    onDismiss: () -> Unit,
    overlay: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // ⭐ Unfolds and lifts at once: the 16 dp rise is the Secondary layer, the Y
    // scale the Primary one.
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // ⚠⚠ The window, not the dialog: `decorFitsSystemWindows = false` alone
        // left it fitted (the measurement that shaped [PullDownSheet], 1.6.084),
        // and this card's scrim has to reach the screen's edges.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.let {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)
                it.attributes = it.attributes.apply { fitInsetsTypes = 0 }
                it.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            // ⭐ A tap beside the card closes it — back to the canvas.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.34f * p.value))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_BAR_RESERVE + 8.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // ⚠ A SHARE of the screen: the canvas has to stay visible
                        // around the card for the growth to read as growth.
                        .fillMaxHeight(0.74f)
                        .graphicsLayer {
                            alpha = p.value
                            translationY = (1f - p.value) * 16.dp.toPx()
                            scaleY = 0.92f + 0.08f * p.value
                            transformOrigin = TransformOrigin(1f, 1f)
                        },
                ) { content() }
            }
            // ⭐ The bar rides INSIDE the card's window, so navigation stays live.
            Box(Modifier.fillMaxSize()) { overlay() }
        }
    }
}
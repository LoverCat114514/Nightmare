package com.abrah.nightmare.newui.motion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐ fix34 (2026-11-06) — the workbench's motion, rebuilt on one decision table.
 *
 * ## The decisions, made once here instead of re-litigated in every card
 *
 * Every animation in this app answers three questions, in this order, and this
 * file is where the answers live. The rule: a card never invents its own curve
 * or its own duration — it names the *kind* of motion and takes what [Motion]
 * gives it.
 *
 * ### 1. Should this animate at all?
 *
 * | Frequency | Decision |
 * |---|---|
 * | 100+/day — scrolling, toggling a switch, tapping a chip | **No animation.** The state change is instant. |
 * | Tens/day — press feedback, row selection | Near-imperceptible: ≤150 ms, or nothing |
 * | Occasional — sheets, modals | Standard animation |
 * | Rare — a finished render, a saved shot | The delight budget lives here |
 *
 * ⚠ **This is where the old code broke.** It animated the FOLD of the
 * post-processing and More-options blocks with `AnimatedVisibility`'s default
 * `expandVertically`/`shrinkVertically` — a HEIGHT animation. Both folds live
 * inside a `LazyColumn` item, so every frame of that animation re-measured and
 * re-laid-out the list around it. The user's report — "上下滑卡顿，甚至划不动"
 * — is the predictable result. A height animation is the one thing this table
 * forbids outright.
 *
 * **The fold is now instant.** Not a faster animation: instant. See [FoldBody].
 *
 * ### 2. What is the purpose?
 *
 * Every animation below names one, and anything that cannot name one is absent:
 * - **feedback** — [pressScale]: "the press registered".
 * - **preventing a jarring change** — [sheetEnter]: a panel appearing from
 *   nothing reads as a glitch.
 * - **delight** — the rare tier only: a finished render.
 *
 * ### 3. Which properties?
 *
 * **Draw-time only.** In Compose that means what `graphicsLayer` can do:
 * `scale`, `alpha`, `translation`. Anything that triggers measurement —
 * `size`, `height`, `padding`, `Arrangement` — is layout-time and banned here.
 */
object Motion {

    // ── Curves ────────────────────────────────────────────────────────────

    /** Entering or exiting: fast at the start, settles slowly. The default. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Moving or morphing while already on screen. */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** A sheet rising from the bottom edge. */
    val EaseSheet: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // ── Durations ─────────────────────────────────────────────────────────

    /** Press feedback. Under 150 ms, or it reads as lag rather than response. */
    const val PRESS_MS = 120

    /** A chip, a toggle, a small state change. */
    const val TOGGLE_MS = 160

    /** A sheet or a panel entering. */
    const val SHEET_MS = 260

    /** A scrim fading behind a sheet. Faster than the sheet — it leads, not trails. */
    const val SCRIM_MS = 180

    /** Press scale. ⚠ Never 0 — nothing in the real world appears from nothing. */
    const val PRESS_SCALE = 0.97f

    /** The spring every "settle" uses. No overshoot. */
    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A spring that may overshoot — only for motion a finger released. */
    fun <T> carry() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A plain timed curve for anything with no finger on it. */
    fun <T> timed(ms: Int, easing: Easing = EaseOut) = tween<T>(durationMillis = ms, easing = easing)
}

/**
 * ⭐ Press feedback on a pressable, with exactly one gesture target.
 *
 * Uses `graphicsLayer { scaleX = ...; scaleY = ... }` instead of `Modifier.scale()`,
 * keeping the animation entirely in the GPU Draw phase without invalidating layout measurement.
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) Motion.PRESS_SCALE else 1f,
        animationSpec = Motion.timed(Motion.PRESS_MS, Motion.EaseOut),
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * ⭐ Modern rounded disclosure chevron icon for fold headers.
 * Sized 20x20 with smooth round stroke geometry, replacing the raw Unicode '▸' text glyph.
 */
val FoldChevronIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FoldChevron",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 20f,
        viewportHeight = 20f,
    ).run {
        addPath(
            pathData = PathParser().parsePathString("M7.5 4.5L13 10L7.5 15.5").toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        build()
    }
}

/**
 * ⭐ The header row of a fold – the title, an optional trailing slot, and the
 * disclosure arrow, as ONE gesture target.
 *
 * Polished with a modern rounded chevron icon inside a subtle badge, smoothly
 * animating rotation and theme accent color via draw-time states.
 */
@Composable
fun FoldHeader(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (open) 90f else 0f,
        animationSpec = Motion.timed(Motion.TOGGLE_MS, Motion.EaseOut),
        label = "foldArrowRotation",
    )
    val badgeBg by animateColorAsState(
        targetValue = if (open) Nm.priSoft else Nm.fld.copy(alpha = 0.5f),
        animationSpec = Motion.timed(Motion.TOGGLE_MS, Motion.EaseOut),
        label = "foldBadgeBg",
    )
    val chevronTint by animateColorAsState(
        targetValue = if (open) Nm.pri else Nm.sub,
        animationSpec = Motion.timed(Motion.TOGGLE_MS, Motion.EaseOut),
        label = "foldChevronTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FoldChevronIcon,
                contentDescription = null,
                tint = chevronTint,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }
    }
}

/**
 * ⭐ The fold's body — instant visibility inside LazyColumn.
 *
 * ⚠ Banned: `expandVertically` / `shrinkVertically` (height animations).
 * Because this fold lives inside a LazyColumn item, any frame-by-frame height
 * animation forces the entire LazyColumn to re-measure and re-layout all items
 * above and below it on every single frame, causing severe scrolling stutters.
 * Following the table's rule: visibility toggle is instant and layout-stable.
 */
@Composable
fun FoldBody(
    open: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (open) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * ⭐ A scrim that fades — and **leaves the composition** when it has nothing to do.
 */
@Composable
fun Scrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.timed(Motion.SCRIM_MS, Motion.EaseOut),
        label = "scrimAlpha",
    )
    if (alpha > 0.01f) {
        Box(
            modifier
                .fillMaxSize()
                .background(color.copy(alpha = color.alpha * alpha))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        onDismiss()
                    }
                },
        )
    }
}

/**
 * ⭐ A sheet's entrance: `alpha` + a small `translationY`, both draw-time.
 */
@Composable
fun sheetEnter(visible: Boolean): SheetMotion {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.timed(Motion.SHEET_MS, Motion.EaseSheet),
        label = "sheetEnter",
    )
    return remember(progress) { SheetMotion(progress) }
}

/** The two values a sheet entrance hands its content. */
class SheetMotion(val progress: Float) {
    val alpha: Float get() = progress
    val translateYDp: Float get() = (1f - progress) * 12f
}
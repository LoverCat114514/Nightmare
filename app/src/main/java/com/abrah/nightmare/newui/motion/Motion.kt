package com.abrah.nightmare.newui.motion

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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
 * Compose makes this subtler than React Native: `animateContentSize` and
 * `AnimatedVisibility(expandVertically)` look convenient and are pure layout
 * animations, banned for the same reason RN bans animating `height`.
 */
object Motion {

    // ── Curves ────────────────────────────────────────────────────────────
    //
    // ⚠ Never `EaseIn` on a UI element. It starts slow, which delays exactly the
    // moment the person is watching. These are the numbers the animation skill
    // ships.

    /** Entering or exiting: fast at the start, settles slowly. The default. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Moving or morphing while already on screen. */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** A sheet rising from the bottom edge. */
    val EaseSheet: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    // ── Durations ─────────────────────────────────────────────────────────
    //
    // Mobile motion stays under 300 ms. A sheet is the slowest thing here and
    // it is still 260.

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
 * **Frequency tier: tens of times a day** → near-imperceptible: 120 ms, scale
 * only. Not a colour flash, not a shadow, not a ripple: `scale` takes the label
 * and the icon with it, which is what makes a press read as physical.
 *
 * ⚠ The scale rides `collectIsPressedAsState` + `animateFloatAsState`, so it
 * costs one animation, not a `MutableState` write per frame of the press.
 *
 * ⚠ **One gesture target.** This modifier owns the click. Never wrap it around
 * a child that also has an `onClick` — the old tree's `Row.clickable` + inner
 * `TextButton` ran BOTH handlers on one tap and flipped a flag twice (net
 * zero), which is why fold rows read as dead buttons.
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
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            // ⚠ No ripple: this is a custom-designed surface, not Material. The
            // same scale on every platform is more coherent than a ripple on one.
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * ⭐ The header row of a fold — the title, an optional trailing slot, and the
 * disclosure arrow, as ONE gesture target.
 *
 * ⚠ Exists because of a real bug: `Row.clickable` + inner `TextButton(onClick)`
 * fired both handlers on one tap, flipping the flag twice — net zero, so the
 * row read as "buttons don't open" (user report 2026-11-06, item 2). A toggle
 * row owns its gesture; its children never carry one.
 *
 * The arrow is a glyph, not a button. It must never get its own `onClick`.
 */
@Composable
fun FoldHeader(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().pressScale(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠ `RowScope` receiver, not a bare lambda: the caller's title needs
        // `Modifier.weight(1f)` to push the arrow to the end, and `weight` only
        // exists inside a RowScope. Without it every fold header would have its
        // arrow hugging the title.
        content()
        Text(
            text = if (open) "▾" else "▸",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * ⭐ The body of a fold: **instant, no height animation.**
 *
 * ## Why this is not animated, and why that is the correct call
 *
 * This is the single most important performance decision in the tree.
 *
 * `AnimatedVisibility(visible, enter = expandVertically(), exit =
 * shrinkVertically())` animates HEIGHT. Both folds live inside a `LazyColumn`
 * item, so every frame of that animation invalidates measurement for the item
 * and forces the lazy list to re-measure what fits in the viewport. On a
 * throttled device the whole scroll stalls while a fold opens. That is not a
 * theory — it is the user's "上下滑卡顿，甚至划不动", read straight out of the code.
 *
 * **The motion table says no animation here anyway.** Opening a disclosure is a
 * 100+/day action; the tier says no animation. The content appearing IS the
 * feedback — someone who taps "More options" wants to see the options, not to
 * watch them slide. An instant reveal is both faster and *correct*, and free.
 *
 * ⚠ If a future design genuinely wants motion here, it must animate `alpha`
 * and a `translationY` on a `graphicsLayer` — draw-time properties that do not
 * re-measure the list. Never height. That is the one rule this tree will not
 * bend, because bending it is what caused the bug being fixed.
 */
@Composable
fun FoldBody(open: Boolean, content: @Composable () -> Unit) {
    if (open) content()
}

/**
 * ⭐ A scrim that fades — and **leaves the composition** when it has nothing to
 * do.
 *
 * ⚠ This answers the second real bug. A `Box(Modifier.fillMaxSize()
 * .pointerInput { detectTapGestures { onDismiss() } })` with `alpha = 0` still
 * participates in hit testing: invisible, full-screen, and it eats every tap.
 * The old tree had exactly this shape in two places, and it is why buttons
 * "didn't work" while a sheet was mid-animation.
 *
 * The rule: **a scrim is only in the tree while it is at least 1% opaque.**
 * `alpha 0` means not composed, not "composed but transparent".
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
    // ⚠ THE GATE: below the floor the scrim leaves the composition entirely, so
    // its pointerInput is not in the hit-test tree at all. This is the fix for
    // "an invisible layer eats taps".
    if (alpha > 0.01f) {
        Box(
            modifier
                .fillMaxSize()
                .background(color.copy(alpha = color.alpha * alpha))
                .pointerInput(Unit) {
                    // A tap anywhere dismisses. awaitFirstDown is enough — a full
                    // detectTapGestures also waits for the up, which delays the
                    // dismissal by however long the person held the screen.
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
 *
 * **Frequency tier: occasional** → standard animation; purpose is
 * **preventing a jarring change** — a panel that materialises from nothing
 * reads as a glitch. 260 ms, ease-out, 12 dp of travel: enough to say "this
 * came from the bottom", not enough to make anyone wait for it.
 *
 * ⚠ The travel is `translationY` on a `graphicsLayer` — NOT a height change and
 * NOT a padding change. Nothing re-measures.
 *
 * ⚠ No `scale(0)` entrance anywhere: 0.96 + alpha 0 is the floor, because
 * nothing in the real world appears from nothing.
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
    /** dp of travel, from below. */
    val translateYDp: Float get() = (1f - progress) * 12f
}
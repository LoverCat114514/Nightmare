package com.abrah.nightmare.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abrah.nightmare.R

/**
 * ⭐⭐⭐ The floating pill bottom bar — the destinations, one tap away (the user's
 * mock, 2026-11-04: exteraGram's floating capsule navigation).
 *
 * ⭐  **Replaces the drawer as the way to move between destinations** — five slots:
 * Canvas | Models | Flows | Results | Settings. The canvas is the HOME slot (its
 * AGENT/NODES/GRAPH views stay the canvas's own view switcher — three views of one
 * flow, not three destinations). Tapping a non-canvas slot opens that sheet over
 * the canvas; tapping Canvas — or the current slot again — returns to the canvas.
 *
 * ⭐  Drawn by the same `sidebar` lambda that used to draw only [ShellDrawer]:
 * the canvas composes it directly, and every sheet composes it as its `overlay`,
 * because a sheet is its own window and nothing the activity draws can cover it —
 * the bar therefore floats over EVERY page, always. That is why the bar lives in
 * MainActivity's lambda rather than inside any one screen.
 *
 * ⭐  Visual language: a full-round capsule on `surfaceContainer` with a faint
 * outline (the glass feel), the selected slot carrying a pill highlight that
 * SLIDES between slots, icons and labels always visible — not Material's
 * label-hides-when-selected. Tokens only ([Theme.kt]); no hard-coded colours.
 *
 * ⭐  Height is reserved by [BOTTOM_BAR_RESERVE]: the canvas RunBar pads itself up
 * by that much so the bar never covers Run. If the bar's metrics change, that
 * constant changes with them.
 */

/** The five slots, in bar order. Derived from state, not stored — one source of truth. */
enum class BottomDest { CANVAS, MODELS, FLOWS, RESULTS, SETTINGS }

/**
 * ⭐  What one full bar occupies above the system navigation area: the capsule
 * (64.dp) plus its 12.dp hover gap, plus 8.dp of breathing room for whatever
 * sits above it (the canvas RunBar). CanvasScreen pads its RunBar up by this.
 */
val BOTTOM_BAR_RESERVE: Dp = 84.dp

/**
 * Icons for the two slots [SaveIcon.kt] had no glyph for — the same hand-built
 * rule: `material-icons-extended` is deliberately absent from this module (~55 MB
 * of dex whatever it is used for), so a missing glyph is drawn here at a few
 * hundred bytes, not imported. Canvas — a picture inside a frame; Settings —
 * Material's own `settings` gear outline.
 */
val CanvasIcon: ImageVector by lazy {
    glyph(
        "Canvas",
        "M3 3h18v18H3V3zm2 2v14h14V5H5zm2.5 9.5l2.5-3 1.83 2.2L14.5 10l4 4.5h-13l1-1z",
    )
}
val SettingsIcon: ImageVector by lazy {
    glyph(
        "SettingsGear",
        "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    )
}

/**
 * The bar itself. All navigation is passed in as lambdas — the bar owns NO state
 * and knows nothing about [com.abrah.nightmare.HarnessViewModel]; `selected` is
 * derived by the caller from the same view state the sheets read, so the bar can
 * never disagree with what is on screen.
 *
 * @param modelsBusy a download/import is running — a badge on the Models slot,
 *   so a download started in a sheet keeps being visible from the canvas.
 */
@Composable
fun NightmareBottomBar(
    selected: BottomDest,
    onCanvas: () -> Unit,
    onModels: () -> Unit,
    onFlows: () -> Unit,
    onResults: () -> Unit,
    onSettings: () -> Unit,
    /** ⭐ Where a slot was tapped: its centre as a fraction of the screen height —
     * a destination card unfolds out of this point (2026-11-06). */
    onAnchor: (Float) -> Unit = {},
    modelsBusy: Boolean,
    hideFlows: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val allSlots = listOf(
        Triple(BottomDest.CANVAS, CanvasIcon, stringResource(R.string.nav_canvas)),
        Triple(BottomDest.MODELS, ModelsIcon, stringResource(R.string.nav_models)),
        Triple(BottomDest.FLOWS, FlowsIcon, stringResource(R.string.nav_flows)),
        Triple(BottomDest.RESULTS, ResultsIcon, stringResource(R.string.nav_results)),
        Triple(BottomDest.SETTINGS, SettingsIcon, stringResource(R.string.nav_settings)),
    )
    val slots = if (hideFlows) allSlots.filterNot { it.first == BottomDest.FLOWS } else allSlots
    // ⭐⭐⭐ fillMaxSize + BottomCenter: the bar is composed as a plain child of a
    // Box (the canvas's root, or a sheet's full-window overlay), and a Box child
    // without an alignment modifier sits TOP-START — which is exactly where the
    // first install drew it (reported from the phone 2026-11-04, the bar over
    // the top bar). The alignment lives HERE so every caller gets the bottom
    // for free and cannot get it wrong.
    Box(
        modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color.White.copy(alpha = 0.48f),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // ⭐ The sliding pill: one highlight BEHIND the slots, offset to the
                // selected slot's fraction of the width. Animated, so a tap GLIDES the
                // highlight to its slot rather than teleporting (the mock's motion).
                val slotWidth = maxWidth / slots.size
                val pillW = slotWidth - 8.dp
                val pillX by animateDpAsState(
                    targetValue = slotWidth * slots.indexOfFirst { it.first == selected } + 4.dp,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
                    label = "bottomBarPill",
                )
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .offset(x = pillX)
                        .padding(vertical = 4.dp)
                        .width(pillW)
                        .fillMaxHeight(),
                ) {}
                Row(Modifier.fillMaxSize()) {
                    slots.forEach { (dest, icon, label) ->
                        val isSel = dest == selected
                        val slotCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { slotCoords.value = it }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    // ⭐ Where the tap landed: the slot's centre as a fraction
                                    // of the screen height — the destination card unfolds
                                    // out of this point (2026-11-06).
                                    slotCoords.value?.let { c ->
                                        val root = c.findRootCoordinates()
                                        onAnchor(
                                            ((c.positionInRoot().y + c.size.height / 2f) /
                                                root.size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                        )
                                    }
                                    // ⭐ Tapping the slot you are already on is "go home":
                                    // from a sheet that means back to the canvas; on the
                                    // canvas it re-asserts the canvas. The Canvas slot is
                                    // always home, wherever you are.
                                    when (dest) {
                                        BottomDest.CANVAS -> onCanvas()
                                        BottomDest.MODELS -> if (isSel) onCanvas() else onModels()
                                        BottomDest.FLOWS -> if (isSel) onCanvas() else onFlows()
                                        BottomDest.RESULTS -> if (isSel) onCanvas() else onResults()
                                        BottomDest.SETTINGS -> if (isSel) onCanvas() else onSettings()
                                    }
                                }
                                .semantics { contentDescription = label },
                        ) {
                            // ⭐ The Models badge: a download in flight is one of the few
                            // states the canvas cannot show, so the bar carries it.
                            Box {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                                if (dest == BottomDest.MODELS && modelsBusy) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.alpha(if (isSel) 1f else 0.62f),
                            )
                        }
                    }
                }
            }
        }
    }
}
}
/**
 * ⭐⭐ MOVED HERE (2026-11-04) from the retired `ShellDrawer.kt`: the shell RAM pill is the
 * bottom bar's neighbour now — the Run bar carries it, the shell it reported to is gone.
 */
/** ⭐ The Model card's badge: what the checkpoint the flow needs is doing. */
enum class LoadState { IDLE, LOADING, LOADED, NONE }

/**
 * ⭐ What the shell RAM pill's Model card and the Run bar's RAM pill show — built in `MainActivity` from
 * the view model's `CanvasLoad` ([model] is the name, never with "(idle)" glued on any more).
 */
data class ShellLoad(
    val model: String,
    val state: LoadState,
    val ramFree: String,
    val ramTotal: String,
    /** ⭐ The battery temperature, whole °C, or null ([com.abrah.nightmare.HarnessViewModel.CanvasLoad.batteryTempC]). */
    val tempC: String? = null,
)

/** ⭐ THE RAM line, drawer and run bar alike: free / total, and the temperature when the phone gives one. */
@Composable
internal fun ramLine(load: ShellLoad): String =
    load.tempC?.let { stringResource(R.string.shell_ram_temp, load.ramFree, load.ramTotal, it) }
        ?: stringResource(R.string.shell_ram, load.ramFree, load.ramTotal)


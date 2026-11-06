package com.abrah.nightmare.newui.card

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.R
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐ fix34 (2026-11-06) — the workbench's shared controls, extracted.
 *
 * These were `private` functions inside `NewUiPanel.kt`, so every card had to
 * live in that one file to use them. They are the vocabulary the cards are
 * written in: a slider row, a number field, a dropdown, a switch row, a
 * segmented pill, the fold header, and the card shell itself.
 *
 * ## The rule every control here follows
 *
 * **Stateless.** A control takes a value and an `onSet` callback; it holds no
 * copy of the value. The old tree had a specific, documented bug from breaking
 * this: `PostSlider` used `remember(value)` to seed a local, so a value
 * written from outside (a reset, a preset) left the thumb parked on its own
 * local copy until an unrelated recomposition. `PostTool`'s switch had the same
 * shape and the same bug. No local state means no drift — the wire IS the
 * thumb.
 *
 * ⚠ The one exception is a TEXT FIELD mid-edit: it must hold the raw string
 * while the person types, because parsing per keystroke would eat the cursor.
 * [NumField] and the seed field carry that state explicitly and sync from the
 * wire only while UNFOCUSED. That is the documented, bounded exception.
 */

// ── The card shell ─────────────────────────────────────────────────────────

/**
 * ⭐ The workbench's card: white, 16 dp radius, a 1 dp hairline, NO elevation.
 *
 * ⚠ `elevation = 0` on purpose. On Android, `Card`'s elevation draws a real
 * shadow, and a shadow on a white card over a lavender page reads as a
 * Material app rather than as the HTML's flat design. The HTML used a hairline
 * stroke and no shadow, and that is what this reproduces.
 */
@Composable
fun NmCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Nm.line, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Nm.card),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

/** A card's section title — one style, so a new card cannot invent another. */
@Composable
fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = Nm.ink,
    )
}

// ── The segmented pill (the HTML's .seg) ────────────────────────────────────

/**
 * ⭐ Two halves in one pill; the selected half in the soft violet.
 * Smooth color transition between active and inactive states.
 */
@Composable
fun SegmentedRow(
    selLeft: Boolean,
    left: String,
    right: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Nm.card, RoundedCornerShape(999.dp))
            .border(1.dp, Nm.fld, RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentHalf(left, selected = selLeft, onClick = onLeft, modifier = Modifier.weight(1f))
        SegmentHalf(right, selected = !selLeft, onClick = onRight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SegmentHalf(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Nm.priSoft else Color.Transparent,
        animationSpec = tween(150),
        label = "segBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Nm.priLine else Color.Transparent,
        animationSpec = tween(150),
        label = "segBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Nm.pri else Nm.sub,
        animationSpec = tween(150),
        label = "segText",
    )

    Box(
        modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── The slider row ─────────────────────────────────────────────────────────

/**
 * ⭐ A labelled slider whose thumb IS the wire value.
 *
 * ⚠ **Stateless, deliberately.** The old version seeded a local with
 * `remember(value)`, which re-seeded only when the value CHANGED — so a
 * 64-snap that returned the wire to its previous number (960 → 1370 → 960)
 * left the thumb parked on the raw drag value: the label read 1370 while the
 * field said 960. With no local state the snap is written every frame and the
 * wire, the thumb and the label agree by construction.
 *
 * **Frequency tier: continuous drag** → no animation on the value. A slider
 * that animated toward the finger would feel laggy by definition; the drag IS
 * the animation.
 */
@Composable
fun PostSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fmt: (Float) -> String,
    onSet: (Float) -> Unit,
) {
    Column {
        Row {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Nm.sub,
                modifier = Modifier.weight(1f),
            )
            Text(
                fmt(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Nm.ink,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onSet,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Nm.pri,
                activeTrackColor = Nm.pri,
                inactiveTrackColor = Nm.line,
            ),
        )
    }
}

// ── The number field ───────────────────────────────────────────────────────

/**
 * ⭐ A numeric entry field that commits on blur or Enter.
 *
 * ⚠ Holds its own text WHILE FOCUSED — the bounded exception to the
 * stateless rule. Parsing per keystroke would fight the cursor, and syncing
 * from the wire mid-edit would overwrite what the person is typing.
 *
 * ⚠ The sync is keyed on `(value, focused)` and guarded by `!focused`, so a
 * write from outside lands the moment the field loses focus — never during
 * typing. This is the shape the old `NumField` had and it was correct; it is
 * kept verbatim because it is the one place local state is right.
 */
@Composable
fun NumField(
    label: String,
    value: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    // Sync from the wire, but never while the person is typing.
    LaunchedEffect(value, focused) {
        if (!focused) text = value.toString()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            // Digits only, bounded — an invalid keystroke is dropped, not shown
            // and then rejected.
            val digits = t.filter { it.isDigit() }.take(6)
            text = digits
        },
        label = { Text(label, color = Nm.sub) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Nm.pri,
            unfocusedBorderColor = Nm.fld,
            focusedLabelColor = Nm.pri,
            unfocusedLabelColor = Nm.sub,
            cursorColor = Nm.pri,
            focusedTextColor = Nm.ink,
            unfocusedTextColor = Nm.ink,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.onFocusChanged { f ->
            val wasFocused = focused
            focused = f.isFocused
            // Commit on blur, once: the person typed a number and moved on.
            if (wasFocused && !f.isFocused) {
                text.toIntOrNull()?.let(onCommit)
            }
        },
    )
}

// ── The dropdown ───────────────────────────────────────────────────────────

/**
 * ⭐ A read-only field that opens a list — the app's own picker shape.
 *
 * ⚠ `MenuAnchorType.PrimaryNotEditable` on the text field: without it the
 * anchor does not open the menu on a click, and the row reads as dead. This
 * was the exact shape the checkpoint picker needed and the reason the two
 * pickers here look the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownWire(
    label: String,
    current: String,
    choices: List<String>,
    textOf: (String) -> String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = textOf(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = Nm.sub) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Nm.pri,
                unfocusedBorderColor = Nm.fld,
                focusedLabelColor = Nm.pri,
                unfocusedLabelColor = Nm.sub,
                focusedTextColor = Nm.ink,
                unfocusedTextColor = Nm.ink,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in choices) {
                DropdownMenuItem(
                    text = { Text(textOf(c), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onPick(c)
                    },
                )
            }
        }
    }
}

// ── The switch row ─────────────────────────────────────────────────────────

/**
 * ⭐ A titled row with a switch at its end.
 *
 * ⚠ The switch is bound to the WIRE, not to a local copy. No local state, no stale copy.
 * ⚠ **One gesture target:** the switch owns the tap, the title does not.
 */
@Composable
fun SwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Nm.ink,
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Nm.sub,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Nm.pri,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Nm.line,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

// ── The post-processing tool block ─────────────────────────────────────────

@Composable
fun PostTool(
    title: String,
    desc: String,
    on: Boolean,
    onFlag: (Boolean) -> Unit,
    actionLabel: String,
    withSwitch: Boolean = true,
    onAction: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Nm.ink,
        )
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = Nm.sub,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (withSwitch) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFlag(!on) }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.post_every_run),
                        style = MaterialTheme.typography.labelMedium,
                        color = Nm.sub,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = on,
                        onCheckedChange = onFlag,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Nm.pri,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Nm.line,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
package com.abrah.nightmare.newui.card

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
 * copy of the value.
 */

// ── The card shell ─────────────────────────────────────────────────────────

/**
 * ⭐ The workbench's card: white, 16 dp radius, a 1 dp hairline, NO elevation.
 */
@Composable
fun NmCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (com.abrah.nightmare.newui.theme.Nm.customBgActive) Color.White.copy(alpha = 0.70f) else Nm.line, RoundedCornerShape(16.dp)),
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
 * ⭐ Two halves in one pill; with a smooth sliding indicator pill (translation animation
 * matching the bottom status bar).
 */
@Composable
fun SegmentedRow(
    selLeft: Boolean,
    left: String,
    right: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Nm.card, RoundedCornerShape(999.dp))
            .border(1.dp, Nm.fld, RoundedCornerShape(999.dp))
            .padding(4.dp),
    ) {
        val halfW = (maxWidth - 4.dp) / 2
        val pillX by animateDpAsState(
            targetValue = if (selLeft) 0.dp else halfW + 4.dp,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = 500f,
            ),
            label = "segmentPillX",
        )

        // ⭐ The sliding indicator pill (smooth gliding translation matching BottomBar)
        Box(
            modifier = Modifier
                .offset(x = pillX)
                .width(halfW)
                .fillMaxHeight()
                .background(Nm.priSoft, RoundedCornerShape(999.dp))
                .border(1.dp, Nm.priLine, RoundedCornerShape(999.dp)),
        )

        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leftTextColor by animateColorAsState(
                targetValue = if (selLeft) Nm.pri else Nm.sub,
                animationSpec = tween(150),
                label = "leftTextCol",
            )
            val rightTextColor by animateColorAsState(
                targetValue = if (!selLeft) Nm.pri else Nm.sub,
                animationSpec = tween(150),
                label = "rightTextCol",
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onLeft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    left,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selLeft) FontWeight.Bold else FontWeight.Medium,
                    color = leftTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onRight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    right,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (!selLeft) FontWeight.Bold else FontWeight.Medium,
                    color = rightTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


/**
 * ⭐ Four-mode segmented pill (txt2img, img2img, inpaint, upscale) with smooth sliding indicator pill.
 */
@Composable
fun ModeSegmentedRow(
    selectedMode: String,
    onSelect: (String) -> Unit,
) {
    val items = listOf(
        com.abrah.nightmare.newui.core.WbParams.MODE_TXT2IMG to "✦ 文生图",
        com.abrah.nightmare.newui.core.WbParams.MODE_IMG2IMG to "🖼 图生图",
        com.abrah.nightmare.newui.core.WbParams.MODE_INPAINT to "🎨 重绘",
        com.abrah.nightmare.newui.core.WbParams.MODE_UPSCALE to "🔍 超分",
    )
    val selIndex = items.indexOfFirst { it.first == selectedMode }.let { if (it >= 0) it else 0 }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Nm.card, RoundedCornerShape(999.dp))
            .border(1.dp, Nm.fld, RoundedCornerShape(999.dp))
            .padding(4.dp),
    ) {
        val count = items.size
        val totalSpacing = 2.dp * (count - 1)
        val itemW = (maxWidth - totalSpacing) / count
        val pillX by animateDpAsState(
            targetValue = (itemW + 2.dp) * selIndex,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = 500f,
            ),
            label = "modePillX",
        )

        // ⭐ The sliding indicator pill (smooth gliding translation matching BottomBar)
        Box(
            modifier = Modifier
                .offset(x = pillX)
                .width(itemW)
                .fillMaxHeight()
                .background(Nm.priSoft, RoundedCornerShape(999.dp))
                .border(1.dp, Nm.priLine, RoundedCornerShape(999.dp)),
        )

        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { idx, item ->
                val isSel = idx == selIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSel) Nm.pri else Nm.sub,
                    animationSpec = tween(150),
                    label = "modeTextCol_$idx",
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = { onSelect(item.first) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.second,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── The slider row ─────────────────────────────────────────────────────────

/**
 * ⭐ A labelled slider whose thumb IS the wire value.
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
                inactiveTrackColor = if (Nm.customBgActive) Color.White.copy(alpha = 0.35f) else Nm.pri.copy(alpha = 0.16f),
            ),
        )
    }
}

// ── The number field ───────────────────────────────────────────────────────

/**
 * ⭐ A numeric entry field that commits on blur or Enter.
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

    LaunchedEffect(value, focused) {
        if (!focused) text = value.toString()
    }

    OutlinedTextField(
        value = text,
        onValueChange = { t ->
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
            if (wasFocused && !f.isFocused) {
                text.toIntOrNull()?.let(onCommit)
            }
        },
    )
}

// ── The dropdown ───────────────────────────────────────────────────────────

/**
 * ⭐ A read-only field that opens a list — the app's own picker shape.
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
package com.abrah.nightmare.newui.card

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abrah.nightmare.Family
import com.abrah.nightmare.ModelCatalog
import com.abrah.nightmare.R
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.motion.FoldBody
import com.abrah.nightmare.newui.motion.FoldHeader
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐ fix34 (2026-11-06) — Card 5: the parameters.
 *
 * The size grid + sliders, the sampler dropdown and its schedule row, Steps and
 * CFG, the seed segment, auto-save, the post-processing fold, and the More
 * options fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamsCard(
    ctx: Context,
    s: WbParams,
    family: Family,
    onPicked: () -> Unit,
    onApply: (Int) -> Unit,
) {
    NmCard {
        CardTitle(stringResource(R.string.newui_params))

        // ── Size: two precise fields, then two sliders ─────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(
                label = stringResource(R.string.knob_width),
                value = s.w,
                onCommit = { s.w = snap64(it); onPicked() },
                modifier = Modifier.weight(1f),
            )
            NumField(
                label = stringResource(R.string.knob_height),
                value = s.h,
                onCommit = { s.h = snap64(it); onPicked() },
                modifier = Modifier.weight(1f),
            )
        }
        PostSlider(
            label = stringResource(R.string.knob_width),
            value = s.w.toFloat(),
            range = 256f..2048f,
            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
            onSet = { s.w = snap64(it.toInt()) },
        )
        PostSlider(
            label = stringResource(R.string.knob_height),
            value = s.h.toFloat(),
            range = 256f..2048f,
            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
            onSet = { s.h = snap64(it.toInt()) },
        )
        Text(
            stringResource(R.string.newui_size_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Nm.sub,
        )

        // ── The sampler, and its schedule ──────────────────────────────────
        val wire = s.scheduler
        val (base, sched) = ModelCatalog.splitScheduler(
            if (wire.isBlank()) ModelCatalog.DEFAULT_SCHEDULER else wire,
        )

        LaunchedEffect(family) {
            val choices = ModelCatalog.schedulersFor(family)
            if (s.scheduler.isBlank() || s.scheduler !in choices) {
                s.scheduler = choices.firstOrNull().orEmpty()
            }
        }

        DropdownWire(
            label = stringResource(R.string.knob_scheduler),
            current = base,
            choices = remember(family) {
                ModelCatalog.schedulersFor(family).map { ModelCatalog.splitScheduler(it).first }.distinct()
            },
            textOf = { ModelCatalog.schedulerLabel(it) },
            onPick = { b -> s.scheduler = ModelCatalog.joinScheduler(b, sched); onPicked() },
        )

        val schedules = remember(family, base) {
            ModelCatalog.schedulersFor(family)
                .map { ModelCatalog.splitScheduler(it) }
                .filter { it.first == base }
                .map { it.second }
                .distinct()
        }
        if (schedules.size > 1 && ModelCatalog.schedulesSupported(base)) {
            val normalStr = stringResource(R.string.schedule_normal)
            val karrasStr = stringResource(R.string.schedule_karras)
            val expStr = stringResource(R.string.schedule_exponential)
            val sgmStr = stringResource(R.string.schedule_sgm_uniform)
            val bongStr = stringResource(R.string.schedule_bong_tangent)
            val simpleStr = stringResource(R.string.schedule_simple)
            val betaStr = stringResource(R.string.schedule_beta)
            val schedNames = remember(normalStr, karrasStr, expStr, sgmStr, bongStr, simpleStr, betaStr) {
                mapOf(
                    "" to normalStr,
                    "_karras" to karrasStr,
                    "_exponential" to expStr,
                    "_sgm_uniform" to sgmStr,
                    "_bong_tangent" to bongStr,
                    "_simple" to simpleStr,
                    "_beta" to betaStr,
                )
            }
            DropdownWire(
                label = stringResource(R.string.sampler_schedule),
                current = sched,
                choices = schedules,
                textOf = { schedNames[it] ?: it },
                onPick = { sfx ->
                    s.scheduler = ModelCatalog.joinScheduler(base, sfx)
                    onPicked()
                },
            )
        }

        // ── Steps and CFG ──────────────────────────────────────────────────
        PostSlider(
            label = stringResource(R.string.knob_steps),
            value = s.steps.toFloat(),
            range = 4f..60f,
            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
            onSet = { s.steps = it.toInt() },
        )
        PostSlider(
            label = stringResource(R.string.knob_cfg),
            value = s.cfg,
            range = 1f..14f,
            fmt = { String.format(java.util.Locale.US, "%.1f", it) },
            onSet = { s.cfg = it },
        )

        // ── The seed segment: random / fixed, the field only when fixed ─────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !s.fixedSeed,
                onClick = { s.fixedSeed = false },
                label = { Text("✓ " + stringResource(R.string.newui_rand_seed)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Nm.priSoft,
                    selectedLabelColor = Nm.pri,
                ),
            )
            FilterChip(
                selected = s.fixedSeed,
                onClick = { s.fixedSeed = true },
                label = { Text("🔒 " + stringResource(R.string.newui_fixed_seed)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Nm.priSoft,
                    selectedLabelColor = Nm.pri,
                ),
            )
        }
        if (s.fixedSeed) {
            var seedTxt by remember { mutableStateOf(if (s.seed > 0) s.seed.toString() else "") }
            var focused by remember { mutableStateOf(false) }
            LaunchedEffect(s.seed, focused) {
                if (!focused) seedTxt = if (s.seed > 0) s.seed.toString() else ""
            }
            OutlinedTextField(
                value = seedTxt,
                onValueChange = { t ->
                    seedTxt = t
                    s.seed = t.filter { it.isDigit() }.take(18).toLongOrNull() ?: 0L
                },
                label = { Text(stringResource(R.string.knob_seed), color = Nm.sub) },
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().onFocusChanged { f -> focused = f.isFocused },
            )
        }

        // ── Auto save ──────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.newui_autosave),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.newui_autosave_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Nm.sub,
                )
            }
            Switch(
                checked = s.autosave,
                onCheckedChange = { s.autosave = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Nm.pri,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Nm.line,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        // ── Post-processing: the three tools, folded open by default ────────
        var postOpen by rememberSaveable { mutableStateOf(true) }
        FoldHeader(open = postOpen, onToggle = { postOpen = !postOpen }) {
            Text(
                stringResource(R.string.post_processing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Nm.ink,
                modifier = Modifier.weight(1f),
            )
        }
        FoldBody(postOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PostTool(
                    title = stringResource(R.string.post_upscale_title),
                    desc = stringResource(R.string.newui_p_ups_hint),
                    on = s.pUps,
                    onFlag = { s.pUps = it },
                    actionLabel = stringResource(R.string.post_upscale_now),
                    onAction = { onApply(0) },
                )
                PostTool(
                    title = stringResource(R.string.post_colour_title),
                    desc = stringResource(R.string.post_colour_desc),
                    on = s.pColor,
                    onFlag = { s.pColor = it },
                    actionLabel = stringResource(R.string.post_colour_now),
                    onAction = { onApply(1) },
                )
                PostTool(
                    title = stringResource(R.string.post_adjust_title),
                    desc = stringResource(R.string.post_adjust_desc),
                    on = s.adjOn,
                    onFlag = { s.adjOn = it },
                    actionLabel = stringResource(R.string.post_adjust_now),
                    onAction = { onApply(2) },
                )
                if (s.adjOn) {
                    Column(
                        Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PostSlider(
                            stringResource(R.string.knob_brightness),
                            s.adjBr.toFloat(), -60f..60f,
                            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
                        ) { s.adjBr = it.toInt() }
                        PostSlider(
                            stringResource(R.string.knob_contrast),
                            s.adjCo.toFloat(), -60f..60f,
                            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
                        ) { s.adjCo = it.toInt() }
                        PostSlider(
                            stringResource(R.string.knob_saturation),
                            s.adjSa.toFloat(), -60f..60f,
                            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
                        ) { s.adjSa = it.toInt() }
                        PostSlider(
                            stringResource(R.string.knob_warmth),
                            s.warm.toFloat(), -60f..60f,
                            fmt = { String.format(java.util.Locale.US, "%.0f", it) },
                        ) { s.warm = it.toInt() }
                    }
                }
            }
        }

        // ── More options: the ×2 upscale, the fixes, the reset ──────────────
        var moreOpen by rememberSaveable { mutableStateOf(false) }
        FoldHeader(open = moreOpen, onToggle = { moreOpen = !moreOpen }) {
            Text(
                stringResource(R.string.more_options),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Nm.ink,
                modifier = Modifier.weight(1f),
            )
        }
        FoldBody(moreOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow(
                    title = stringResource(R.string.knob_enlarge),
                    hint = stringResource(R.string.newui_m_ups_hint),
                    checked = s.pUps2,
                    onCheckedChange = { s.pUps2 = it },
                )
                SwitchRow(
                    title = stringResource(R.string.knob_fix_faces),
                    hint = stringResource(R.string.newui_p_face_hint),
                    checked = s.pFace,
                    onCheckedChange = { s.pFace = it },
                )
                SwitchRow(
                    title = stringResource(R.string.knob_fix_hands),
                    hint = stringResource(R.string.newui_p_hands_hint),
                    checked = s.pHands,
                    onCheckedChange = { s.pHands = it },
                )
                TextButton(
                    onClick = {
                        s.steps = 29
                        s.cfg = 5.0f
                        s.adjBr = 0
                        s.adjCo = 0
                        s.adjSa = 0
                        s.warm = 0
                        s.pUps = false
                        s.pUps2 = false
                        s.pColor = false
                        s.adjOn = false
                        s.pFace = false
                        s.pHands = false
                        ModelCatalog.schedulersFor(family).firstOrNull()?.let { s.scheduler = it }
                        onPicked()
                    },
                ) {
                    Text(stringResource(R.string.detect_reset))
                }
            }
        }
    }
}

/** Snap to the nearest multiple of 64 (the workbench's blur rule). */
internal fun snap64(v: Int): Int = (v / 64).coerceAtLeast(1) * 64
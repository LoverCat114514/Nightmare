package com.abrah.nightmare.newui.card

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abrah.nightmare.BackendProcess
import com.abrah.nightmare.Family
import com.abrah.nightmare.LoraLibrary
import com.abrah.nightmare.LoraNotes
import com.abrah.nightmare.LoraSources
import com.abrah.nightmare.LoraSpec
import com.abrah.nightmare.ModelCatalog
import com.abrah.nightmare.ModelSpec
import com.abrah.nightmare.R
import com.abrah.nightmare.SelectedModel
import com.abrah.nightmare.canvas.rememberOffMain
import com.abrah.nightmare.newui.NewUiLoraPicker
import com.abrah.nightmare.newui.core.LoraPick
import com.abrah.nightmare.newui.core.WbParams
import com.abrah.nightmare.newui.theme.Nm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ⭐⭐ fix34 (2026-11-06) — Card 4: the model.
 *
 * The checkpoint picker and the LoRA block (header, hint, the sheet).
 *
 * ## What changed under it, and what did not
 *
 * **Nothing visual changed.** Same checkpoint field, same LoRA row, same
 * `NewUiLoraPicker` sheet, same loading dialog that fix33 added.
 *
 * What changed is the machinery:
 *
 * - **`loras` is real state.** [WbParams.loras] is a `mutableStateOf` holding an
 *   immutable `List<LoraPick>`, so writing it is the ONLY thing needed to make
 *   the UI agree.
 * - **`installed` models loaded off the main thread**: [ModelCatalog.installed]
 *   is no longer evaluated synchronously during LazyColumn scrolling, avoiding
 *   hundreds of File.exists() calls that caused frame drops mid-scroll.
 *
 * ⚠ **One gesture target per row.** The LoRA "choose" `TextButton` is the only
 * handler on its row — the title column carries none. This is the same rule
 * that fix33 enforced for the fold headers.
 */
@Composable
fun ModelCard(
    ctx: Context,
    s: WbParams,
    modelId: String,
    family: Family,
    loraEpoch: Int,
    onPicked: (String) -> Unit,
    onImportLora: (() -> Unit)?,
    onBrowseLoras: ((LoraSources.Target) -> Unit)? = null,
    onDeleteLora: ((String) -> Unit)? = null,
) {
    NmCard {
        val picked = ModelCatalog.byId(modelId)
        CheckpointPicker(
            ctx = ctx,
            label = picked?.label ?: SelectedModel.spec.label,
            familyLabel = picked?.family?.label ?: "",
            onPicked = onPicked,
        )

        var picking by remember { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.loras_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.newui_loras_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Nm.sub,
                )
            }
            TextButton(onClick = { picking = true }) {
                Text(stringResource(R.string.newui_choose))
            }
        }

        if (picking) {
            // ⚠ Off the main thread, re-keyed on [loraEpoch]: a file imported
            // from inside the sheet shows up without leaving the card. Null
            // until the scan lands — an empty list would mark every pick missing.
            val installedFiles = rememberOffMain("newui-loras", "lora files", loraEpoch) {
                BackendProcess.lorasDir(ctx).listFiles { f ->
                    f.isFile && f.extension.equals("safetensors", ignoreCase = true)
                }.orEmpty().map { it.name to it.length() }.sortedBy { it.first.lowercase() }
            }
            // ⭐ Each file's SIZE, DATE and BASE ([LoraLibrary]) — what draws the
            // "Anima · 132 MB" line under every row and what feeds the base
            // grouping. Headers only, cached, off the main thread.
            val library by produceState<List<LoraLibrary.Item>?>(null, loraEpoch) {
                value = withContext(Dispatchers.IO) {
                    LoraLibrary.list(BackendProcess.lorasDir(ctx), LoraLibrary.baseOverrides(ctx))
                }
            }
            // ⭐ The per-file ⋮ notes ([LoraNotes]): read beside the files, written
            // straight back on save. Without these the ⋮ is not drawn at all.
            val loraDir = BackendProcess.lorasDir(ctx)
            val notesRead = rememberOffMain("newui-loras", "lora notes", loraEpoch) {
                LoraNotes.read(loraDir)
            }
            var savedNotes by remember(loraEpoch) { mutableStateOf<Map<String, String>?>(null) }

            // ⚠ Nothing usable until BOTH scans land: a picker drawn on an EMPTY
            // list marks every chosen LoRA missing.
            // ⭐ fix33 (user 2026-11-06 item 1: "点好几次打不开要过一会才行"):
            // the gate stays, but the wait now SHOWS. Before, tapping 选择 composed
            // nothing at all until both disk scans landed — no feedback, so the
            // tap read as dead and the person tapped again.
            if (installedFiles == null || notesRead == null) {
                Dialog(onDismissRequest = { picking = false }) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Nm.card, tonalElevation = 3.dp) {
                        Column(
                            Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Nm.pri,
                                strokeWidth = 2.5.dp,
                            )
                            Text(
                                stringResource(R.string.newui_loras_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = Nm.sub,
                            )
                        }
                    }
                }
            }

            if (installedFiles != null && notesRead != null) {
                val loraNotes = savedNotes ?: notesRead
                // ⚠ Re-derived on every list identity change: [LoraPick.toSpec]
                // is the one place that knows the wire form, so there is no second
                // formatter to drift from it.
                val loraSpecStr = remember(s.loras) {
                    s.loras.joinToString(", ") { it.toSpec() }
                }
                NewUiLoraPicker(
                    installed = installedFiles,
                    library = library,
                    spec = loraSpecStr,
                    onSet = { txt ->
                        // ⭐ A NEW list, never a mutation: `mutableStateOf` compares
                        // by equality, and an in-place `add` would look unchanged
                        // to Compose — the exact trap the old `MutableList` fell
                        // into, which is why it needed a hand-bumped counter.
                        s.loras = LoraSpec.parse(txt).map {
                            LoraPick(it.name, it.strength.toFloat())
                        }
                    },
                    onDismiss = { picking = false },
                    onImport = onImportLora,
                    // ⭐ Only where this family's LoRAs can be searched.
                    onBrowse = LoraSources.Target.of(family)?.let { t ->
                        onBrowseLoras?.let { cb -> { cb(t) } }
                    },
                    notes = loraNotes,
                    onSaveNote = { name, note ->
                        savedNotes = LoraNotes.write(loraDir, name, note)
                    },
                    onDelete = onDeleteLora,
                    nodeBase = LoraLibrary.Base.of(family),
                    experimental = LoraSources.Target.experimental(family),
                    // ⭐ Below 0 where the engine takes it (DiT families, GitHub #51).
                    allowNegative = family.dit,
                )
            }
        }
    }
}

/** The checkpoint row: a read-only field + the same INSTALLED-only picker the app uses. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CheckpointPicker(
    ctx: Context,
    label: String,
    familyLabel: String,
    onPicked: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<List<ModelSpec>>(emptyList()) }

    // ⭐ Load installed models asynchronously off the main thread when dropdown is opened.
    // Never run ModelCatalog.installed(ctx) synchronously during LazyColumn scrolling!
    LaunchedEffect(open) {
        if (open) {
            installed = withContext(Dispatchers.IO) {
                ModelCatalog.installed(ctx)
            }
        }
    }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    if (familyLabel.isBlank()) stringResource(R.string.inspector_checkpoint)
                    else stringResource(R.string.inspector_checkpoint) + " · " + familyLabel,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            // ⚠ `PrimaryNotEditable`: without it the anchor does not open the
            // menu on a tap and the row reads as dead.
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in installed) {
                DropdownMenuItem(
                    text = { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        try {
                            SelectedModel.set(ctx, m.id)
                            onPicked(m.id)
                        } catch (_: Exception) {}
                    },
                )
            }
        }
    }
}
package com.abrah.nightmare.newui

import androidx.compose.foundation.background
import com.abrah.nightmare.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * ⭐⭐⭐ (2026-11-05, thirteenth round) **The workbench's own LoRA sheet**, drawn
 * to the HTML workbench (`assets/newui/nm-workbench.html`, the `选择 LoRA`
 * dialog) instead of inheriting the canvas inspector's Material sheet.
 *
 * The user's call: *"算了，重写lora选择的ui吧，目前这个ui不符合我的心意，但还是有不少
 * 可取之处。比如分组的功能，搜索的功能，导入，以及三个点的更多那个功能"* — a
 * REWRITE of the look, keeping those four functions:
 *
 *  - **分组** — the family each file is filed under (the HTML's per-row base
 *    label), the family chips that filter by it, and the folders the ⋮ makes;
 *  - **搜索** — the HTML's `过滤…` field;
 *  - **导入** — the same `.safetensors` import the Default UI's sheet offers;
 *  - **⋮ 更多** — one LoRA's note, base, folder, delete ([LoraNoteDialog] and
 *    [ConfirmDeleteLora], reused rather than redrawn: they are the app's one
 *    note editor and one delete confirm).
 *
 * ⚠⚠ The HTML's own row is `名字 | 开关 | 滑条 | 数值` on ONE line. At a phone's
 * width that leaves the name about 60dp once the ⋮ is added, so the weight
 * moved to a second line under the name — the palette, the radii, the 42×24
 * switch and the violet figure are the HTML's; the wrap is the phone's.
 */
@Composable
internal fun NewUiLoraPicker(
    installed: List<Pair<String, Long>>,
    library: List<com.abrah.nightmare.LoraLibrary.Item>?,
    spec: String,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
    notes: Map<String, String> = emptyMap(),
    onSaveNote: ((name: String, note: String) -> Unit)? = null,
    onImport: (() -> Unit)? = null,
    onBrowse: (() -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    nodeBase: com.abrah.nightmare.LoraLibrary.Base? = null,
    experimental: Boolean = false,
    allowNegative: Boolean = false,
) {
    // ⚠ Seeded from the param and re-seeded when it changes, so the sheet shows
    // what the node actually carries rather than a copy that drifted.
    var chosen by remember(spec) { mutableStateOf(com.abrah.nightmare.LoraSpec.parse(spec)) }
    fun commit(next: List<com.abrah.nightmare.LoraSpec.Entry>) {
        chosen = next
        onSet(com.abrah.nightmare.LoraSpec.format(next))
    }
    val ctx = LocalContext.current
    // ⭐ The bases set by hand apply at once, over what the caller read.
    var overrides by remember { mutableStateOf(com.abrah.nightmare.LoraLibrary.baseOverrides(ctx)) }
    // ⭐ The folders the ⋮ makes, and which are open. Closed by default: folders
    // are for a long list, and a long list is what they tidy away.
    var folders by remember { mutableStateOf(com.abrah.nightmare.LoraLibrary.folders(ctx)) }
    var openFolders by remember { mutableStateOf(emptySet<String>()) }
    val items = (library ?: installed.map {
        com.abrah.nightmare.LoraLibrary.Item(
            it.first, it.second, 0L, com.abrah.nightmare.LoraLibrary.Base.OTHER,
        )
    }).map { it.copy(base = overrides[it.name] ?: it.detected) }

    var noting by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf<String?>(null) }

    // ── ⭐ 更多: the note editor, the typed weight, the delete confirm. Wrapped
    // in the workbench palette so the three inherit it too (they are the app's
    // own dialogs, not redrawn here).
    typing?.let { name ->
        val entry = chosen.firstOrNull { it.name == name }
        if (entry == null) typing = null
        else MaterialTheme(
            colorScheme = nmScheme(androidx.compose.foundation.isSystemInDarkTheme()),
            shapes = NmSheetShapes,
        ) {
            com.abrah.nightmare.canvas.LoraWeightDialog(
                entry.strength, allowNegative, onDismiss = { typing = null },
            ) { w ->
                commit(chosen.map { if (it.name == name) it.copy(strength = w) else it })
                typing = null
            }
        }
    }
    noting?.let { name ->
        val item = items.firstOrNull { it.name == name }
        MaterialTheme(
            colorScheme = nmScheme(androidx.compose.foundation.isSystemInDarkTheme()),
            shapes = NmSheetShapes,
        ) {
            com.abrah.nightmare.canvas.LoraNoteDialog(
                name = name,
                note = notes[name].orEmpty(),
                base = item?.base,
                detected = item?.detected,
                onSetBase = { b ->
                    com.abrah.nightmare.LoraLibrary.setBase(ctx, name, b)
                    overrides = com.abrah.nightmare.LoraLibrary.baseOverrides(ctx)
                },
                folder = folders[name],
                folderChoices = com.abrah.nightmare.LoraLibrary.foldersFor(items, folders, item?.base),
                onSetFolder = { fo ->
                    com.abrah.nightmare.LoraLibrary.setFolder(ctx, name, fo)
                    folders = com.abrah.nightmare.LoraLibrary.folders(ctx)
                    fo?.let { openFolders = openFolders + it.trim() }
                },
                onSave = { onSaveNote?.invoke(name, it) },
                // ⚠ No prompt node on this screen: null keeps the two prompt
                // buttons DIMMED and saying why, rather than removed.
                onAddToPrompt = null,
                onReplacePrompt = null,
                onDelete = onDelete?.let { { deleting = name } },
                onDismiss = { noting = null },
            )
        }
    }
    deleting?.let { name ->
        MaterialTheme(
            colorScheme = nmScheme(androidx.compose.foundation.isSystemInDarkTheme()),
            shapes = NmSheetShapes,
        ) {
            com.abrah.nightmare.canvas.ConfirmDeleteLora(
                name,
                onConfirm = {
                    noting = null
                    // ⚠ Unticked on THIS screen too: deleting a LoRA that is on
                    // means "not this one", and leaving it named would only make
                    // the next Run refuse.
                    if (chosen.any { it.name == name }) commit(chosen.filterNot { it.name == name })
                    onDelete?.invoke(name)
                },
                onDismiss = { deleting = null },
            )
        }
    }

    var sort by remember { mutableStateOf(com.abrah.nightmare.LoraLibrary.Sort.NAME) }
    var base by remember { mutableStateOf<com.abrah.nightmare.LoraLibrary.Base?>(null) }
    var query by remember { mutableStateOf("") }
    var favourites by remember { mutableStateOf(com.abrah.nightmare.LoraLibrary.favourites(ctx)) }
    val uses = remember { com.abrah.nightmare.LoraLibrary.uses(ctx) }
    val bases = items.map { it.base }.distinct().sortedBy { if (it == nodeBase) -1 else it.ordinal }
    val arranged = com.abrah.nightmare.LoraLibrary.arrange(items, sort, base, query, favourites, uses)
    val installedNames = items.map { it.name }.toSet()
    val sizes = items.associate { it.name to it.bytes }
    val baseOf = items.associate { it.name to it.base }
    // ⚠⚠ Chosen ones FIRST and in their own order, then everything else: the
    // order in the param is the order the engine applies them in, so re-sorting
    // the list would silently re-sort the stack.
    val rows = chosen.map { it.name }.distinct() +
        arranged.map { it.name }.filter { n -> chosen.none { it.name == n } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth(0.92f).wrapContentHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Nm.card)
                .border(1.dp, Nm.line, RoundedCornerShape(14.dp))
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.newui_lora_sheet_title),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
            )
            Text(
                stringResource(R.string.newui_lora_pick_hint),
                style = TextStyle(fontSize = 12.5.sp, color = Nm.sub),
                modifier = Modifier.padding(top = 4.dp),
            )
            // ── ⭐ 搜索: the HTML's `过滤…` field.
            Box(
                Modifier.fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, Nm.fld, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.newui_lora_filter),
                        style = TextStyle(fontSize = 14.sp, color = Nm.sub),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Nm.ink),
                    cursorBrush = SolidColor(Nm.pri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // ── ⭐ 分组: the family chips, the sort, the folders.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var sortOpen by remember { mutableStateOf(false) }
                Box {
                    NmChip(stringResource(sortLabelRes(sort)), false) { sortOpen = true }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        for (s in com.abrah.nightmare.LoraLibrary.Sort.entries) {
                            DropdownMenuItem(
                                text = { Text(stringResource(sortLabelRes(s))) },
                                onClick = { sort = s; sortOpen = false },
                            )
                        }
                    }
                }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NmChip(stringResource(R.string.lora_base_all), base == null) { base = null }
                    for (b in bases) NmChip(b.label, base == b) { base = if (base == b) null else b }
                }
            }
            // ── The list. ⚠ heightIn(max) so the sheet stops growing at the
            // phone's height and scrolls instead.
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (experimental) {
                    com.abrah.nightmare.canvas.ExperimentalLoraNote(Modifier.fillMaxWidth())
                }
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.newui_lora_empty),
                        style = TextStyle(fontSize = 13.sp, color = Nm.sub),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    )
                }
                // ⭐⭐ The chosen ones first, then the folders, then the rest.
                // ⚠ Flat while filtering: a match inside a closed folder must show.
                val chosenRows = rows.filter { n -> chosen.any { it.name == n } }
                val rest = rows.filter { n -> chosen.none { it.name == n } }
                val (groups, loose) = if (query.isBlank())
                    com.abrah.nightmare.LoraLibrary.grouped(rest, folders)
                else emptyList<Pair<String, List<String>>>() to rest

                val draw: @Composable (String) -> Unit = { name ->
                    val entry = chosen.firstOrNull { it.name == name }
                    val missing = name !in installedNames
                    NmRow(
                        name = name,
                        entry = entry,
                        base = baseOf[name],
                        bytes = sizes[name] ?: 0L,
                        missing = missing,
                        hasNote = !notes[name].isNullOrBlank(),
                        allowNegative = allowNegative,
                        onToggle = { on ->
                            if (on) com.abrah.nightmare.LoraLibrary.markUsed(ctx, name)
                            commit(
                                if (on) chosen + com.abrah.nightmare.LoraSpec.Entry(name, com.abrah.nightmare.LoraSpec.FULL)
                                else chosen.filterNot { it.name == name },
                            )
                        },
                        onWeight = { v ->
                            // ⚠ Snapped before it is stored: `0.8500001` is not a
                            // strength anyone typed.
                            val snapped = com.abrah.nightmare.LoraSpec.snapped(v, allowNegative)
                            commit(chosen.map { if (it.name == name) it.copy(strength = snapped) else it })
                        },
                        onType = { typing = name },
                        onMore = if (onSaveNote != null && !missing) {
                            { noting = name }
                        } else null,
                    )
                }
                for (name in chosenRows) draw(name)
                for ((folder, names) in groups) {
                    val isOpen = folder in openFolders
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                openFolders = if (isOpen) openFolders - folder else openFolders + folder
                            }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowDown
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.lora_cd_folder, folder),
                            tint = Nm.sub,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.lora_folder_count, folder, names.size),
                            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (isOpen) for (name in names) draw(name)
                }
                for (name in loose) draw(name)
            }
            // ── ⭐ 导入 and 完成, as the HTML's footer.
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onImport?.let { act ->
                    NmChip(stringResource(R.string.newui_lora_import), false, act)
                }
                onBrowse?.let { act ->
                    NmChip(stringResource(R.string.lb_open), false, act)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.crop_done),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Nm.pri)
                        .clickable { onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** ⭐ The HTML `.chip`: white with a hairline, the soft violet when it is the state. */
@Composable
private fun NmChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Nm.pri else Nm.ink,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Nm.priSoft else Color.White)
            .border(1.dp, if (on) Nm.priLine else Nm.line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** ⭐ The HTML `.sw`: a 42×24 pill, a white knob, the violet when it is on. */
@Composable
private fun NmSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) Nm.pri else Nm.fld)
            .clickable { onToggle(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 3.dp).size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * ⭐⭐ One LoRA, as the HTML draws it: the name (and, under it, the family and
 * the size — the two things the picker carries that the HTML's flat list has no
 * room for), the switch, the ⋮, then the weight on its own line: slider + the
 * violet figure, which is also where a typed weight starts.
 */
@Composable
private fun NmRow(
    name: String,
    entry: com.abrah.nightmare.LoraSpec.Entry?,
    base: com.abrah.nightmare.LoraLibrary.Base?,
    bytes: Long,
    missing: Boolean,
    hasNote: Boolean,
    allowNegative: Boolean,
    onToggle: (Boolean) -> Unit,
    onWeight: (Double) -> Unit,
    onType: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        fontWeight = if (entry != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = Nm.ink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⭐ 分组: the family it is filed under (the HTML's `Anima` label).
                    if (base != null && base != com.abrah.nightmare.LoraLibrary.Base.OTHER) {
                        Text(
                            base.label,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                        )
                        Text(" · ", style = TextStyle(fontSize = 11.sp, color = Nm.sub))
                    }
                    Text(
                        // ⚠ The MB is why the size is carried at all: an adapter
                        // is tens of megabytes and a merged checkpoint is
                        // gigabytes, and the wrong file shows here, not at Run.
                        if (missing) stringResource(R.string.lora_missing)
                        else stringResource(R.string.device_mb, bytes shr 20),
                        style = TextStyle(fontSize = 11.sp, color = if (missing) Nm.bad else Nm.sub),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            onMore?.let { act ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { act() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.lora_cd_note, name),
                        tint = Nm.sub,
                        modifier = Modifier.size(18.dp),
                    )
                    // ⭐ A dot says a note exists, so the LoRAs with a recipe
                    // written down are findable at a glance.
                    if (hasNote) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp)
                                .size(5.dp).clip(CircleShape).background(Nm.pri),
                        )
                    }
                }
            }
            NmSwitch(entry != null, onToggle)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = entry?.strength?.toFloat() ?: com.abrah.nightmare.LoraSpec.FULL.toFloat(),
                onValueChange = { onWeight(it.toDouble()) },
                valueRange = com.abrah.nightmare.LoraSpec.range(allowNegative).let {
                    it.start.toFloat()..it.endInclusive.toFloat()
                },
                // ⚠⚠ No `steps`: passing it makes Material draw a mark per stop —
                // forty across a phone-width slider, which reads as a barcode.
                // The quantising happens above, on the VALUE.
                enabled = entry != null,
                colors = SliderDefaults.colors(
                    thumbColor = Nm.pri,
                    activeTrackColor = Nm.pri,
                    inactiveTrackColor = Nm.line,
                    disabledThumbColor = Nm.fld,
                    disabledActiveTrackColor = Nm.line,
                    disabledInactiveTrackColor = Nm.line,
                ),
                modifier = Modifier.weight(1f).height(34.dp),
            )
            Text(
                if (entry == null) "—" else com.abrah.nightmare.LoraSpec.number(entry.strength),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (entry != null) Nm.pri else Nm.sub,
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp).clickable(enabled = entry != null) { onType() },
            )
        }
    }
}

/** ⭐ The sort menu's label — the canvas picker's own four ([com.abrah.nightmare.LoraLibrary.Sort]). */
private fun sortLabelRes(s: com.abrah.nightmare.LoraLibrary.Sort): Int = when (s) {
    com.abrah.nightmare.LoraLibrary.Sort.NAME -> R.string.lora_sort_name
    com.abrah.nightmare.LoraLibrary.Sort.NEWEST -> R.string.lora_sort_newest
    com.abrah.nightmare.LoraLibrary.Sort.MOST_USED -> R.string.lora_sort_used
    com.abrah.nightmare.LoraLibrary.Sort.SIZE -> R.string.lora_sort_size
}

package com.abrah.nightmare.newui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import com.abrah.nightmare.R

/**
 * ⭐⭐⭐ (2026-11-05, fourteenth round) **The workbench's LoRA sheet**, to the
 * user's own mock-up. A rewrite of the thirteenth round's sheet, whose rows
 * crowded a switch, a slider and a ⋮ into one line and whose weight column read
 * as a minus sign.
 *
 * What the mock-up asked for, and what is drawn here:
 *
 *  - **顶部** — the how-to line folded behind a `?` (it is reference, not
 *    instruction); a **X** at the top right, because "only Done or the
 *    backdrop closes it" is not a way out.
 *  - **每一条 a CARD** — a faint ground and a corner, air between the rows, the
 *    file name as the title and `MOSS · 141 MB` as the grey subtitle.
 *  - **开关与滑块联动** — a row that is OFF has NO slider at all (the old
 *    disabled bar plus its `—` was the "减号" that confused the user) and reads
 *    grey throughout; a row that is ON shows the slider with its figure to the
 *    right of it.
 *  - **筛选** — the selected chip is FILLED with the accent and set in white,
 *    not a pale tint; the sort button says `↑↓ 名称` rather than looking like
 *    one more filter.
 *  - **搜索** — a magnifier and `搜索 LoRA…`, not a bare `过滤…`.
 *  - **底部** — `+ 导入` as a light text button (a secondary act), `完成` as the
 *    one filled button.
 *
 * ⚠⚠ The four functions the user kept are all still here: grouping (family
 * chips, the per-row family line, the ⋮'s folders), search, import, and the ⋮
 * menu (note, family, folder, delete).
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
        // ⭐ probe v4 (review report 1 §6): log every selection write with its
        // outcome — distinguishes "callback ran, list unchanged" (the dead-tap
        // signature) from a real flip, and shows the count the UI should draw.
        // The name is the DIFF against the old list: the added entry on a
        // select, the removed one on a deselect (`next.lastOrNull()` alone
        // logged "?" for the last-item removal).
        val removed = next.size < chosen.size
        val name = if (removed) chosen.firstOrNull { e -> next.none { it.name == e.name } }?.name ?: "?"
                   else next.lastOrNull()?.name ?: "?"
        TouchProbe.loraChange(name, !removed, next.size)
        chosen = next
        onSet(com.abrah.nightmare.LoraSpec.format(next))
    }
    val ctx = LocalContext.current
    var overrides by remember { mutableStateOf(com.abrah.nightmare.LoraLibrary.baseOverrides(ctx)) }
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
    // ⭐ The how-to line starts folded: a first-time reader needs the range, a
    // regular one needs the list.
    var showHint by remember { mutableStateOf(false) }

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
            // ── ⭐ The title row: the name, then `?` and `X`. Both are ways out
            // of a line that used to take three lines of dialog to read.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.newui_lora_sheet_title),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable { showHint = !showHint },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "?",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showHint) Nm.pri else Nm.sub,
                        ),
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Nm.sub,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // ⭐ The how-to, folded: reference, not instruction.
            if (showHint) {
                Text(
                    stringResource(R.string.newui_lora_pick_hint),
                    style = TextStyle(fontSize = 11.5.sp, color = Nm.sub),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )
            }
            // ── ⭐ 搜索: a magnifier and a name, not a bare `过滤…`.
            Row(
                Modifier.fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, Nm.fld, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = Nm.sub,
                    modifier = Modifier.size(17.dp),
                )
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.newui_lora_search),
                            style = TextStyle(fontSize = 13.5.sp, color = Nm.sub),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, color = Nm.ink),
                        cursorBrush = SolidColor(Nm.pri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Nm.sub,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            // ── ⭐ 筛选: the sort says what it is, the chosen chip is FILLED.
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var sortOpen by remember { mutableStateOf(false) }
                Box {
                    // ⚠ The arrows are text, not a vector: `SwapVert` is NOT in
                    // material-icons-core, and the extended set is 55 MB of dex
                    // this build deliberately does not carry.
                    Text(
                        "↑↓ " + stringResource(sortLabelRes(sort)),
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, Nm.line, RoundedCornerShape(10.dp))
                            .clickable { sortOpen = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
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
                Modifier.fillMaxWidth().padding(top = 10.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    NmCard(
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
                            .padding(vertical = 4.dp, horizontal = 2.dp),
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
            // ── ⭐ 底部: the two ways to get a LoRA in are LIGHT (secondary),
            // `完成` is the one filled button (primary).
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                onImport?.let { act ->
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { act() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Nm.pri,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            stringResource(R.string.newui_lora_import),
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
                onBrowse?.let { act ->
                    Text(
                        stringResource(R.string.lb_open),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { act() }
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.crop_done),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Nm.pri)
                        .clickable { onDismiss() }
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * ⭐ The filter chip. **FILLED with the accent when it is the state** — the user:
 * *"选中的标签应使用主色调填充背景+白色文字"*; the pale tint was too easy to miss.
 */
@Composable
private fun NmChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Color.White else Nm.ink,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Nm.pri else Color.White)
            .border(1.dp, if (on) Nm.pri else Nm.line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

/** ⭐ The switch: a 42×24 pill, a white knob, the violet when it is on. */
@Composable
private fun NmSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    // ⭐ probe v4 (review report 2 §4): the tappable area used to be exactly
    // the 42×24 pill — a thumb landing a couple of px outside it silently did
    // nothing ("LoRA 选择偶尔点击无反应"). A 48×48 tap box (the Material 3
    // minimum) wraps the pill; the pill's drawing is unchanged.
    Box(
        Modifier.size(48.dp)
            .clickable { TouchProbe.uiToggle("lora_switch", on, !on); onToggle(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 42.dp, height = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (on) Nm.pri else Nm.fld),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier.padding(horizontal = 3.dp).size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * ⭐⭐ One LoRA as a CARD (the user's mock-up): the file name as the title, `MOSS ·
 * 141 MB` as the grey subtitle, the ⋮ and the switch at the right, and the
 * weight BELOW them — drawn only while the row is on.
 *
 * ⚠⚠ **A row that is off has no slider at all.** The old sheet drew a disabled
 * bar and a `—` where the figure goes; the user read that `—` as a minus sign
 * ("去掉冗余的 — (减号)"). Hiding the whole line removes the ambiguity and half
 * the noise with it.
 */
@Composable
private fun NmCard(
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
    val on = entry != null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // ⭐ A faint ground: the pale violet once it is on, the neutral
            // canvas once it is off — the state reads before the switch does.
            .background(if (on) Nm.priSoft else Nm.canvas)
            .then(if (on) Modifier.border(1.dp, Nm.priLine, RoundedCornerShape(12.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = TextStyle(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        // ⭐ Off reads grey throughout, so a glance finds the
                        // ones that are actually doing something.
                        color = if (on) Nm.ink else Nm.sub,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⭐ 分组: the family it is filed under, then the size.
                    if (base != null && base != com.abrah.nightmare.LoraLibrary.Base.OTHER) {
                        Text(
                            base.label,
                            style = TextStyle(fontSize = 11.5.sp, color = Nm.sub),
                            maxLines = 1,
                        )
                        Text(" · ", style = TextStyle(fontSize = 11.5.sp, color = Nm.sub))
                    }
                    Text(
                        // ⚠ The MB is why the size is carried at all: an adapter
                        // is tens of megabytes and a merged checkpoint is
                        // gigabytes, and the wrong file shows here, not at Run.
                        if (missing) stringResource(R.string.lora_missing)
                        else stringResource(R.string.device_mb, bytes shr 20),
                        style = TextStyle(fontSize = 11.5.sp, color = if (missing) Nm.bad else Nm.sub),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // ⚠ The ⋮ moved off the switch's edge and the target shrank to 28dp:
            // a 36dp hit-box touching a 42dp switch was the mis-tap the user
            // reported. The switch keeps the far right, as the mock-up draws it.
            onMore?.let { act ->
                Box(
                    Modifier.padding(end = 10.dp).size(28.dp)
                        .clip(CircleShape).clickable { act() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.lora_cd_note, name),
                        tint = Nm.sub,
                        modifier = Modifier.size(17.dp),
                    )
                    // ⭐ A dot says a note exists, so the LoRAs with a recipe
                    // written down are findable at a glance.
                    if (hasNote) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp)
                                .size(5.dp).clip(CircleShape).background(Nm.pri),
                        )
                    }
                }
            }
            NmSwitch(on, onToggle)
        }
        // ⚠⚠ Only while ON. No bar, no `—`, no row.
        if (entry != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = entry.strength.toFloat(),
                    onValueChange = { onWeight(it.toDouble()) },
                    valueRange = com.abrah.nightmare.LoraSpec.range(allowNegative).let {
                        it.start.toFloat()..it.endInclusive.toFloat()
                    },
                    // ⚠⚠ No `steps`: passing it makes Material draw a mark per
                    // stop — forty across a phone-width slider, which reads as a
                    // barcode. The quantising happens above, on the VALUE.
                    colors = SliderDefaults.colors(
                        thumbColor = Nm.pri,
                        activeTrackColor = Nm.pri,
                        inactiveTrackColor = Nm.line,
                    ),
                    modifier = Modifier.weight(1f).height(32.dp),
                )
                // ⭐ The figure sits beside the slider while it is on (the user:
                // "滑块滑到最右侧显示具体的数值（如 1.0）"); tapping it types an
                // exact weight, which a slider cannot land on.
                Text(
                    com.abrah.nightmare.LoraSpec.number(entry.strength),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Nm.pri,
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onType() }
                        .padding(vertical = 4.dp),
                )
            }
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

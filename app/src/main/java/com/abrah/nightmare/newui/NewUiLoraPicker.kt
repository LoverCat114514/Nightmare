package com.abrah.nightmare.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.abrah.nightmare.newui.theme.Nm
import com.abrah.nightmare.newui.theme.NmSheetShapes
import com.abrah.nightmare.newui.theme.nmScheme
import kotlinx.coroutines.launch

/**
 * ⭐⭐⭐ (2026-11-07) **The workbench's LoRA picker** — redesigned as a spacious,
 * modern floating card with fluid physics animations, zero clipping, smooth switch,
 * and 100% preserved feature set.
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
    var chosen by remember(spec) { mutableStateOf(com.abrah.nightmare.LoraSpec.parse(spec)) }
    fun commit(next: List<com.abrah.nightmare.LoraSpec.Entry>) {
        val removed = next.size < chosen.size
        val name = if (removed) chosen.firstOrNull { e -> next.none { it.name == e.name } }?.name ?: "?"
                   else next.lastOrNull()?.name ?: "?"
        TouchProbe.loraChange(name, !removed, next.size)
        chosen = next
        onSet(com.abrah.nightmare.LoraSpec.format(next))
    }
    val ctx = LocalContext.current
    var overrides by remember(library) { mutableStateOf(com.abrah.nightmare.LoraLibrary.baseOverrides(ctx)) }
    var folders by remember(library) { mutableStateOf(com.abrah.nightmare.LoraLibrary.folders(ctx)) }
    var openFolders by remember { mutableStateOf(emptySet<String>()) }
    val items = (library ?: installed.map {
        com.abrah.nightmare.LoraLibrary.Item(
            it.first, it.second, 0L, com.abrah.nightmare.LoraLibrary.Base.OTHER,
        )
    }).map { it.copy(base = overrides[it.name] ?: it.detected) }

    var noting by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(false) }

    // Sub-dialogs (Weight, Note, Delete)
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
    var favourites by remember(library) { mutableStateOf(com.abrah.nightmare.LoraLibrary.favourites(ctx)) }
    val uses = remember(library) { com.abrah.nightmare.LoraLibrary.uses(ctx) }
    val bases = remember(items) {
        items.map { it.base }.distinct().sortedBy { if (it == nodeBase) -1 else it.ordinal }
    }
    val arranged = remember(items, sort, base, query, favourites, uses) {
        com.abrah.nightmare.LoraLibrary.arrange(items, sort, base, query, favourites, uses)
    }
    val installedNames = remember(items) { items.map { it.name }.toSet() }
    val sizes = remember(items) { items.associate { it.name to it.bytes } }
    val baseOf = remember(items) { items.associate { it.name to it.base } }
    val rows = remember(chosen, arranged) {
        chosen.map { it.name }.distinct() +
        arranged.map { it.name }.filter { n -> chosen.none { it.name == n } }
    }

    // ⭐ Fluid physical entrance and exit animation
    val animProgress = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val animateDismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                animProgress.animateTo(
                    0f,
                    tween(240, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f)),
                )
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            1f,
            tween(340, easing = CubicBezierEasing(0.18f, 0.90f, 0.25f, 1.0f)),
        )
    }

    Dialog(
        onDismissRequest = { animateDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val p = animProgress.value
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { animateDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            // Ambient Backdrop Dim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = p }
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            // ⭐ Modern, Spacious Floating Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (Nm.customBgActive) Color.White.copy(alpha = (Nm.cardOpacity + 0.12f).coerceAtMost(0.98f)) else Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(
                    1.2.dp,
                    if (Nm.customBgActive) Color.White.copy(alpha = 0.85f) else Color(0xFFE5E2EC),
                ),
                modifier = Modifier
                    .fillMaxWidth(0.93f)
                    .fillMaxHeight(0.78f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.90f + 0.10f * p
                        scaleY = 0.90f + 0.10f * p
                        translationY = (1f - p) * 48.dp.toPx()
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    // ── Header ─────────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.newui_lora_sheet_title),
                            style = TextStyle(fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold, color = Nm.ink),
                        )
                        if (chosen.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Nm.priSoft)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "已加载 ${chosen.size} 项",
                                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Help button
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (showHint) Nm.priSoft else Color.Black.copy(alpha = 0.04f))
                                .clickable { showHint = !showHint },
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

                        Spacer(Modifier.width(8.dp))

                        // Close button
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.05f))
                                .clickable { animateDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Nm.ink.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Collapsible Hint
                    AnimatedVisibility(
                        visible = showHint,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Nm.priSoft.copy(alpha = 0.5f))
                                .padding(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.newui_lora_pick_hint),
                                style = TextStyle(fontSize = 11.5.sp, color = Nm.sub, lineHeight = 16.sp),
                            )
                        }
                    }

                    // ── Search Bar ──────────────────────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.65f) else Color(0xFFF6F5FA))
                            .border(1.dp, Nm.line, RoundedCornerShape(12.dp))
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
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { query = "" },
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

                    // ── Filter & Sort Chips ────────────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        var sortOpen by remember { mutableStateOf(false) }
                        Box {
                            Text(
                                "↑↓ " + stringResource(sortLabelRes(sort)),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.7f) else Color(0xFFF6F5FA))
                                    .border(1.dp, Nm.line, RoundedCornerShape(10.dp))
                                    .clickable { sortOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
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

                    // ── LoRA List ───────────────────────────────────────────
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (experimental) {
                            item(key = "note") {
                                com.abrah.nightmare.canvas.ExperimentalLoraNote(Modifier.fillMaxWidth())
                            }
                        }
                        if (rows.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    stringResource(R.string.newui_lora_empty),
                                    style = TextStyle(fontSize = 13.sp, color = Nm.sub),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                )
                            }
                        }
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
                                    val snapped = com.abrah.nightmare.LoraSpec.snapped(v, allowNegative)
                                    commit(chosen.map { if (it.name == name) it.copy(strength = snapped) else it })
                                },
                                onType = { typing = name },
                                onMore = if (onSaveNote != null && !missing) {
                                    { noting = name }
                                } else null,
                            )
                        }
                        items(chosenRows, key = { "lora:$it" }) { name -> draw(name) }
                        for ((folder, names) in groups) {
                            val isOpen = folder in openFolders
                            item(key = "hdr:$folder") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            openFolders = if (isOpen) openFolders - folder else openFolders + folder
                                        }
                                        .padding(vertical = 5.dp, horizontal = 2.dp),
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
                            }
                            if (isOpen) items(names, key = { "lora:$it" }) { name -> draw(name) }
                        }
                        items(loose, key = { "lora:$it" }) { name -> draw(name) }
                    }

                    // ── Bottom Action Bar (Guaranteed on-screen!) ────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        onImport?.let { act ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Nm.priSoft)
                                    .clickable { act() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                        onBrowse?.let { act ->
                            Text(
                                stringResource(R.string.lb_open),
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF2F0F7))
                                    .clickable { act() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (chosen.isEmpty()) stringResource(R.string.crop_done) else "完成 (${chosen.size})",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Nm.pri)
                                .clickable { animateDismiss() }
                                .padding(horizontal = 24.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

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
            .background(if (on) Nm.pri else if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF6F5FA))
            .border(1.dp, if (on) Nm.pri else Nm.line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** ⭐ Smooth Animated Switch: tactile spring knob and animated color track */
@Composable
private fun NmSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    val knobOffset by animateDpAsState(
        targetValue = if (on) 18.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "switchKnob",
    )
    val trackColor by animateColorAsState(
        targetValue = if (on) Nm.pri else Color(0xFFDDD9E6),
        animationSpec = tween(180),
        label = "switchTrack",
    )

    Box(
        Modifier
            .size(width = 44.dp, height = 36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    TouchProbe.uiToggle("lora_switch", on, !on)
                    onToggle(!on)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = knobOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * ⭐⭐ One LoRA Card:
 * - Clean layout: Name on left, ⋮ menu and smooth switch on right
 * - Animated visibility for slider: rolls in/out smoothly without snapping
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
    val cardBg by animateColorAsState(
        targetValue = if (on) Nm.priSoft.copy(alpha = if (Nm.customBgActive) 0.80f else 0.88f)
                      else if (Nm.customBgActive) Color.White.copy(alpha = 0.65f)
                      else Color(0xFFF9F9FC),
        animationSpec = tween(200),
        label = "loraCardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (on) Nm.pri.copy(alpha = 0.55f) else Color(0xFFEDEBF2),
        animationSpec = tween(200),
        label = "loraBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Nm.ink else Nm.sub,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (base != null && base != com.abrah.nightmare.LoraLibrary.Base.OTHER) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) Nm.pri.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                base.label,
                                style = TextStyle(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) Nm.pri else Nm.sub,
                                ),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (missing) stringResource(R.string.lora_missing)
                        else stringResource(R.string.device_mb, bytes shr 20),
                        style = TextStyle(fontSize = 11.5.sp, color = if (missing) Nm.bad else Nm.sub),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            onMore?.let { act ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { act() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.lora_cd_note, name),
                        tint = Nm.sub,
                        modifier = Modifier.size(17.dp),
                    )
                    if (hasNote) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Nm.pri),
                        )
                    }
                }
            }
            NmSwitch(on, onToggle)
        }

        // ⭐ Animated expansion for slider row: smooth roll out/in without layout snap!
        AnimatedVisibility(
            visible = on,
            enter = expandVertically(tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))) + fadeOut(tween(140)),
        ) {
            if (entry != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Slider(
                        value = entry.strength.toFloat(),
                        onValueChange = { onWeight(it.toDouble()) },
                        valueRange = com.abrah.nightmare.LoraSpec.range(allowNegative).let {
                            it.start.toFloat()..it.endInclusive.toFloat()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Nm.pri,
                            activeTrackColor = Nm.pri,
                            inactiveTrackColor = Color(0xFFDDD9E6),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Nm.priSoft)
                            .border(1.dp, Nm.pri.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .clickable { onType() }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            com.abrah.nightmare.LoraSpec.number(entry.strength),
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Nm.pri,
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun sortLabelRes(s: com.abrah.nightmare.LoraLibrary.Sort): Int = when (s) {
    com.abrah.nightmare.LoraLibrary.Sort.NAME -> R.string.lora_sort_name
    com.abrah.nightmare.LoraLibrary.Sort.NEWEST -> R.string.lora_sort_newest
    com.abrah.nightmare.LoraLibrary.Sort.MOST_USED -> R.string.lora_sort_used
    com.abrah.nightmare.LoraLibrary.Sort.SIZE -> R.string.lora_sort_size
}

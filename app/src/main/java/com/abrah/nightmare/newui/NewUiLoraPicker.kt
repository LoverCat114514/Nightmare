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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
 * modern bottom sheet drawer with fluid physics animations, active model chips,
 * and quick-snap weight adjustment.
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

    // Sub-dialogs
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

    // ⭐ Fluid bottom sheet entrance and exit animation
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
            tween(360, easing = CubicBezierEasing(0.18f, 0.90f, 0.25f, 1.0f)),
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
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Ambient Backdrop Dim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = p }
                    .background(Color.Black.copy(alpha = 0.45f))
            )

            // ⭐ Spacious Modern Bottom Sheet Drawer
            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                color = if (Nm.customBgActive) Color.White.copy(alpha = (Nm.cardOpacity + 0.12f).coerceAtMost(0.98f)) else Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = BorderStroke(
                    1.2.dp,
                    if (Nm.customBgActive) Color.White.copy(alpha = 0.85f) else Color(0xFFE8E6EE),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * 200.dp.toPx()
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    // Drag handle pill at top
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(42.dp)
                                .height(4.5.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(alpha = 0.25f))
                        )
                    }

                    // ── Header ─────────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "LoRA 模型库",
                                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Nm.ink),
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
                            }
                            Text(
                                if (chosen.isEmpty()) "勾选需要加载的 LoRA 特效并自由调节权重"
                                else "权重已实时同步至渲染流水线，点击数值可精准微调",
                                style = TextStyle(fontSize = 12.sp, color = Nm.sub),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        // Help button
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (showHint) Nm.priSoft else Color.Black.copy(alpha = 0.04f))
                                .clickable { showHint = !showHint },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "?",
                                style = TextStyle(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showHint) Nm.pri else Nm.sub,
                                ),
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Close button
                        Box(
                            Modifier
                                .size(32.dp)
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
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Nm.priSoft.copy(alpha = 0.6f))
                                .padding(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.newui_lora_pick_hint),
                                style = TextStyle(fontSize = 12.sp, color = Nm.sub, lineHeight = 17.sp),
                            )
                        }
                    }

                    // ⭐ Active Selected LoRAs Quick Carousel
                    if (chosen.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (entry in chosen) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Nm.priSoft)
                                        .border(1.dp, Nm.pri.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        entry.name,
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        com.abrah.nightmare.LoraSpec.number(entry.strength),
                                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Nm.pri),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Nm.pri.copy(alpha = 0.15f))
                                            .clickable { commit(chosen.filterNot { it.name == entry.name }) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = Nm.pri,
                                            modifier = Modifier.size(11.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Search Bar ──────────────────────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.65f) else Color(0xFFF6F5FA))
                            .border(1.dp, Nm.line, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Nm.sub,
                            modifier = Modifier.size(18.dp),
                        )
                        Box(Modifier.weight(1f).padding(start = 10.dp)) {
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
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
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
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                    style = TextStyle(fontSize = 13.5.sp, color = Nm.sub),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            openFolders = if (isOpen) openFolders - folder else openFolders + folder
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (isOpen) Icons.Filled.KeyboardArrowDown
                                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.lora_cd_folder, folder),
                                        tint = Nm.sub,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        stringResource(R.string.lora_folder_count, folder, names.size),
                                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                            if (isOpen) items(names, key = { "lora:$it" }) { name -> draw(name) }
                        }
                        items(loose, key = { "lora:$it" }) { name -> draw(name) }
                    }

                    // ── Sticky Bottom Action Bar ────────────────────────────
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            onImport?.let { act ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Nm.priSoft)
                                        .clickable { act() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = Nm.pri,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.newui_lora_import),
                                        style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            onBrowse?.let { act ->
                                Text(
                                    stringResource(R.string.lb_open),
                                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF2F0F7))
                                        .clickable { act() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (chosen.isEmpty()) "完成" else "应用 (${chosen.size})",
                                style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Nm.pri)
                                    .clickable { animateDismiss() }
                                    .padding(horizontal = 28.dp, vertical = 11.dp),
                            )
                        }
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/**
 * ⭐⭐ One LoRA Card:
 * - Round checkbox toggle button on the left with tactile response
 * - Animated weight slider with quick adjustment chips ([-0.1], [0.8], [1.0], [+0.1])
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
        targetValue = if (on) Nm.priSoft.copy(alpha = if (Nm.customBgActive) 0.82f else 0.92f)
                      else if (Nm.customBgActive) Color.White.copy(alpha = 0.65f)
                      else Color(0xFFFAF9FD),
        animationSpec = tween(220),
        label = "loraCardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (on) Nm.pri.copy(alpha = 0.55f) else Color(0xFFECEAF0),
        animationSpec = tween(220),
        label = "loraBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Touch friendly Checkbox Indicator
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (on) Nm.pri else Color.White)
                    .border(1.5.dp, if (on) Nm.pri else Nm.fld, CircleShape)
                    .clickable { onToggle(!on) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                Modifier
                    .weight(1f)
                    .clickable { onToggle(!on) },
            ) {
                Text(
                    name,
                    style = TextStyle(
                        fontSize = 14.5.sp,
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

            // Quick weight badge when ON
            if (on && entry != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Nm.pri.copy(alpha = 0.12f))
                        .border(1.dp, Nm.pri.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onType() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        com.abrah.nightmare.LoraSpec.number(entry.strength),
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Nm.pri,
                        ),
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            onMore?.let { act ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { act() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.lora_cd_note, name),
                        tint = Nm.sub,
                        modifier = Modifier.size(18.dp),
                    )
                    if (hasNote) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 3.dp, end = 3.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Nm.pri),
                        )
                    }
                }
            }
        }

        // ⭐ Animated expansion for slider row with quick snap chips
        AnimatedVisibility(
            visible = on,
            enter = expandVertically(tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))) + fadeOut(tween(160)),
        ) {
            if (entry != null) {
                Column(Modifier.padding(top = 8.dp)) {
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
                            .fillMaxWidth()
                            .height(30.dp),
                    )

                    // Quick Snap Preset Chips (e.g. -0.1, 0.6, 0.8, 1.0, +0.1)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val currentW = entry.strength
                        val quickChips = listOf(
                            "-0.1" to (currentW - 0.1),
                            "0.6" to 0.6,
                            "0.8" to 0.8,
                            "1.0" to 1.0,
                            "+0.1" to (currentW + 0.1),
                        )
                        for ((label, targetVal) in quickChips) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.8f))
                                    .border(0.8.dp, Color(0xFFE2E0E8), RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clamped = targetVal.coerceIn(if (allowNegative) -2.0 else 0.0, 2.0)
                                        onWeight(clamped)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    label,
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Nm.sub),
                                )
                            }
                        }
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

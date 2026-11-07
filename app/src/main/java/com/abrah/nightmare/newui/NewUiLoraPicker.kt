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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Brush
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
 * ⭐⭐⭐ (2026-11-07) **LoRA Studio Pro (LoRA 特效工坊)** — Ground-up reimagination
 * of the LoRA picker for NewUI with luxury frosted aesthetics, interactive active stack HUD,
 * smart architecture avatars, quick-snap weight mixing chips, and 100% complete feature preservation.
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
        NewUiLoraNoteDialog(
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
            onDelete = onDelete?.let { { deleting = name } },
            onDismiss = { noting = null },
        )
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

    // Navigation sub-tab: 0 = 全部, 1 = 已启用, 2 = 收藏
    var viewTab by remember { mutableStateOf(0) }

    val rows = remember(chosen, arranged, viewTab, favourites) {
        val baseList = when (viewTab) {
            1 -> chosen.map { it.name }
            2 -> arranged.map { it.name }.filter { it in favourites }
            else -> chosen.map { it.name }.distinct() + arranged.map { it.name }.filter { n -> chosen.none { it.name == n } }
        }
        baseList
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
                    .background(Color.Black.copy(alpha = 0.50f))
            )

            // ⭐ Studio Floating Glass Container
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (Nm.customBgActive) Color.White.copy(alpha = (Nm.cardOpacity + 0.12f).coerceAtMost(0.98f)) else Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = BorderStroke(
                    1.2.dp,
                    if (Nm.customBgActive) Color.White.copy(alpha = 0.85f) else Color(0xFFE5E2EC),
                ),
                modifier = Modifier
                    .fillMaxWidth(0.93f)
                    .fillMaxHeight(0.80f)
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
                        .padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 14.dp),
                ) {
                    // ── Hero Header ─────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Title with glowing active dot
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (chosen.isNotEmpty()) Nm.pri else Color(0xFFC4C0CE))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "LoRA 列表",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Nm.ink,
                                    letterSpacing = 0.2.sp,
                                ),
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Help toggle button
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

                    // Collapsible Hint Accordion
                    AnimatedVisibility(
                        visible = showHint,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
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

                    // ── Top Navigation Segments (全部 / 已启用 / 收藏) ────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF3F1F7))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val tabs = listOf(
                            0 to "全部模型 (${items.size})",
                            1 to "已加载 (${chosen.size})",
                            2 to "我的收藏 (${favourites.size})",
                        )
                        for ((id, title) in tabs) {
                            val isSel = viewTab == id
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSel) Color.White else Color.Transparent)
                                    .then(if (isSel) Modifier.border(1.dp, Color(0xFFE4E1EB), RoundedCornerShape(9.dp)) else Modifier)
                                    .clickable { viewTab = id },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    title,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Nm.ink else Nm.sub,
                                    ),
                                )
                            }
                        }
                    }

                    // ── Active LoRA HUD Stack (When active) ──────────────────
                    if (chosen.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("当前堆叠:", style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub))
                            for (entry in chosen) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Nm.priSoft)
                                        .border(1.dp, Nm.pri.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                        .padding(start = 9.dp, top = 3.dp, bottom = 3.dp, end = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        entry.name,
                                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        com.abrah.nightmare.LoraSpec.number(entry.strength),
                                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Nm.pri),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Nm.pri.copy(alpha = 0.15f))
                                            .clickable { commit(chosen.filterNot { it.name == entry.name }) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = Nm.pri,
                                            modifier = Modifier.size(10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Search & Filter Controls ────────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Search bar
                        Row(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.65f) else Color(0xFFF6F5FA))
                                .border(1.dp, Nm.line, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = Nm.sub,
                                modifier = Modifier.size(16.dp),
                            )
                            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                                if (query.isEmpty()) {
                                    Text(
                                        stringResource(R.string.newui_lora_search),
                                        style = TextStyle(fontSize = 13.sp, color = Nm.sub),
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = Nm.ink),
                                    cursorBrush = SolidColor(Nm.pri),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (query.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable { query = "" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = Nm.sub,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        // Sort Menu Dropdown
                        var sortOpen by remember { mutableStateOf(false) }
                        Box {
                            Row(
                                Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.7f) else Color(0xFFF6F5FA))
                                    .border(1.dp, Nm.line, RoundedCornerShape(12.dp))
                                    .clickable { sortOpen = true }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "↑↓ " + stringResource(sortLabelRes(sort)),
                                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                                )
                            }
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                for (s in com.abrah.nightmare.LoraLibrary.Sort.entries) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(sortLabelRes(s))) },
                                        onClick = { sort = s; sortOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    // ── Architecture Filter Chips ───────────────────────────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NmChip(stringResource(R.string.lora_base_all), base == null) { base = null }
                        for (b in bases) NmChip(b.label, base == b) { base = if (base == b) null else b }
                    }

                    // ── Main Model Grid / List ──────────────────────────────
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (experimental) {
                            item(key = "note") {
                                com.abrah.nightmare.canvas.ExperimentalLoraNote(Modifier.fillMaxWidth())
                            }
                        }
                        if (rows.isEmpty()) {
                            item(key = "empty") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        stringResource(R.string.newui_lora_empty),
                                        style = TextStyle(fontSize = 14.sp, color = Nm.sub),
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        "可点击下方 '+ 导入本地' 载入 .safetensors 特效模型",
                                        style = TextStyle(fontSize = 11.5.sp, color = Nm.sub.copy(alpha = 0.7f)),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        val chosenRows = rows.filter { n -> chosen.any { it.name == n } }
                        val rest = rows.filter { n -> chosen.none { it.name == n } }
                        val (groups, loose) = if (query.isBlank() && viewTab == 0)
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
                                isFav = name in favourites,
                                onToggleFav = {
                                    val nextFav = if (name in favourites) favourites - name else favourites + name
                                    com.abrah.nightmare.LoraLibrary.setFavourite(ctx, name, name !in favourites)
                                    favourites = nextFav
                                },
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
                                        .background(Color.Black.copy(alpha = 0.03f))
                                        .clickable {
                                            openFolders = if (isOpen) openFolders - folder else openFolders + folder
                                        }
                                        .padding(vertical = 6.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (isOpen) Icons.Filled.KeyboardArrowDown
                                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.lora_cd_folder, folder),
                                        tint = Nm.pri,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.lora_folder_count, folder, names.size),
                                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Nm.ink),
                                    )
                                }
                            }
                            if (isOpen) items(names, key = { "lora:$it" }) { name -> draw(name) }
                        }
                        items(loose, key = { "lora:$it" }) { name -> draw(name) }
                    }

                    // ── Bottom Fixed Elevated Control Dock ──────────────────
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            onImport?.let { act ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Nm.priSoft)
                                        .border(1.dp, Nm.pri.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .clickable { act() }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Nm.pri),
                                        modifier = Modifier.padding(start = 3.dp),
                                    )
                                }
                            }
                            onBrowse?.let { act ->
                                Text(
                                    stringResource(R.string.lb_open),
                                    style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF2F0F7))
                                        .border(1.dp, Color(0xFFE2E0E8), RoundedCornerShape(12.dp))
                                        .clickable { act() }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (chosen.isEmpty()) "完成" else "应用 (${chosen.size})",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Nm.pri)
                                    .clickable { animateDismiss() }
                                    .padding(horizontal = 26.dp, vertical = 10.dp),
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) Color.White else Nm.ink,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) Nm.pri else if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF6F5FA))
            .border(1.dp, if (on) Nm.pri else Nm.line, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** ⭐ Power Switch: smooth spring tactile pill toggle */
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
            .size(width = 44.dp, height = 34.dp)
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
 * ⭐⭐ Studio LoRA Module Card:
 * - Left: Artistic module avatar with gradient base badge
 * - Center: Title, architecture chip, file size, note indicator
 * - Right: Favorite star, More menu (⋮), Smooth tactile power switch
 * - Bottom (When Active): Smoothly unrolls Mixing Bay with slider, digital readout, and rapid snap chips!
 */
@Composable
private fun NmCard(
    name: String,
    entry: com.abrah.nightmare.LoraSpec.Entry?,
    base: com.abrah.nightmare.LoraLibrary.Base?,
    bytes: Long,
    missing: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    hasNote: Boolean,
    allowNegative: Boolean,
    onToggle: (Boolean) -> Unit,
    onWeight: (Double) -> Unit,
    onType: () -> Unit,
    onMore: (() -> Unit)?,
) {
    val on = entry != null
    val cardBg by animateColorAsState(
        targetValue = if (on) Nm.priSoft.copy(alpha = if (Nm.customBgActive) 0.85f else 0.92f)
                      else if (Nm.customBgActive) Color.White.copy(alpha = 0.65f)
                      else Color(0xFFFAF9FD),
        animationSpec = tween(200),
        label = "loraCardBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (on) Nm.pri.copy(alpha = 0.60f) else Color(0xFFEAE8F0),
        animationSpec = tween(200),
        label = "loraBorder",
    )

    // Base avatar gradient
    val avatarBrush = remember(base) {
        when (base) {
            com.abrah.nightmare.LoraLibrary.Base.MOSS -> Brush.linearGradient(listOf(Color(0xFF8B4DEE), Color(0xFFAC7AFF)))
            com.abrah.nightmare.LoraLibrary.Base.ANIMA -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
            com.abrah.nightmare.LoraLibrary.Base.KREA2 -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
            com.abrah.nightmare.LoraLibrary.Base.SD15 -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
            com.abrah.nightmare.LoraLibrary.Base.SDXL -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B4DEE)))
            com.abrah.nightmare.LoraLibrary.Base.FLUX -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
            else -> Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
        }
    }

    val baseShortTag = remember(base) {
        when (base) {
            com.abrah.nightmare.LoraLibrary.Base.MOSS -> "MOSS"
            com.abrah.nightmare.LoraLibrary.Base.ANIMA -> "ANIMA"
            com.abrah.nightmare.LoraLibrary.Base.KREA2 -> "KREA"
            com.abrah.nightmare.LoraLibrary.Base.SD15 -> "SD1.5"
            com.abrah.nightmare.LoraLibrary.Base.SDXL -> "SDXL"
            com.abrah.nightmare.LoraLibrary.Base.FLUX -> "FLUX"
            else -> "LoRA"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Visual Module Avatar
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(avatarBrush)
                    .clickable { onToggle(!on) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (on) "✦" else "✨",
                        style = TextStyle(fontSize = 12.sp, color = Color.White),
                    )
                    Text(
                        baseShortTag.take(4),
                        style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title and Details
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onToggle(!on) },
            ) {
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
                                    fontWeight = FontWeight.Bold,
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

            // Favorite star toggle
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onToggleFav() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isFav) "★" else "☆",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = if (isFav) Color(0xFFF59E0B) else Nm.sub.copy(alpha = 0.4f),
                    ),
                )
            }

            // Note/More options menu (⋮)
            onMore?.let { act ->
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
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

            // Tactile Power Switch
            NmSwitch(on, onToggle)
        }

        // ⭐ Animated Mixing Bay Console (when ON)
        AnimatedVisibility(
            visible = on,
            enter = expandVertically(tween(240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))) + fadeOut(tween(140)),
        ) {
            if (entry != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.70f))
                        .border(0.8.dp, Color(0xFFE5E2EC), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
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
                                .height(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        // Digital Readout Chip (Tap to type)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Nm.priSoft)
                                .border(1.dp, Nm.pri.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable { onType() }
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                com.abrah.nightmare.LoraSpec.number(entry.strength),
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Nm.pri,
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Quick Snap Weight Preset Chips
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val currentW = entry.strength
                        val snapChips = listOf(
                            "-0.1" to (currentW - 0.1),
                            "0.4" to 0.4,
                            "0.6" to 0.6,
                            "0.8" to 0.8,
                            "1.0" to 1.0,
                            "+0.1" to (currentW + 0.1),
                        )
                        for ((chipLabel, targetWeight) in snapChips) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(0.7.dp, Color(0xFFDDD8E6), RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clamped = targetWeight.coerceIn(if (allowNegative) -2.0 else 0.0, 2.0)
                                        onWeight(clamped)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                            ) {
                                Text(
                                    chipLabel,
                                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
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

/**
 * ⭐⭐⭐ (2026-11-07) **NewUI专属 LoRA 笔记与分类弹窗 (重构图二)**
 * 完全保持图一的设计语言：22dp 大圆角、悬浮亚克力磨砂质感、平滑贝塞尔物理进出场，
 * 同时 100% 完整保留原有笔记编辑、Base 架构切换、文件夹管理、复制与删除功能。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NewUiLoraNoteDialog(
    name: String,
    note: String,
    base: com.abrah.nightmare.LoraLibrary.Base?,
    detected: com.abrah.nightmare.LoraLibrary.Base?,
    onSetBase: ((com.abrah.nightmare.LoraLibrary.Base?) -> Unit)?,
    folder: String?,
    folderChoices: List<String>,
    onSetFolder: ((String?) -> Unit)?,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember(name) { mutableStateOf(note) }
    var newFolder by remember(name) { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val animProgress = remember { Animatable(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val animateDismiss: (Boolean) -> Unit = { save ->
        if (!dismissing) {
            dismissing = true
            if (save && text != note) {
                onSave(text)
            }
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
            tween(320, easing = CubicBezierEasing(0.18f, 0.90f, 0.25f, 1.0f)),
        )
    }

    Dialog(
        onDismissRequest = { animateDismiss(true) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val p = animProgress.value
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { animateDismiss(true) } },
            contentAlignment = Alignment.Center,
        ) {
            // Ambient Backdrop Dim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = p }
                    .background(Color.Black.copy(alpha = 0.50f))
            )

            // Modern Floating Modal Surface
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (Nm.customBgActive) Color.White.copy(alpha = (Nm.cardOpacity + 0.12f).coerceAtMost(0.98f)) else Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp,
                border = BorderStroke(
                    1.2.dp,
                    if (Nm.customBgActive) Color.White.copy(alpha = 0.85f) else Color(0xFFE5E2EC),
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.90f + 0.10f * p
                        scaleY = 0.90f + 0.10f * p
                        translationY = (1f - p) * 44.dp.toPx()
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Header: Title & Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Nm.ink,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "编辑触发词笔记与分类属性",
                                style = TextStyle(fontSize = 11.5.sp, color = Nm.sub),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.05f))
                                .clickable { animateDismiss(true) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Nm.ink.copy(alpha = 0.75f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    // Text Field for Note / Trigger Words
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.65f) else Color(0xFFF6F5FA))
                            .border(1.dp, Nm.line, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.lora_note_hint),
                                style = TextStyle(fontSize = 13.5.sp, color = Nm.sub.copy(alpha = 0.7f), lineHeight = 19.sp),
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                            textStyle = TextStyle(fontSize = 14.sp, color = Nm.ink, lineHeight = 20.sp),
                            cursorBrush = SolidColor(Nm.pri),
                        )
                    }

                    // Model Architecture Section (模型架构)
                    if (onSetBase != null && detected != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.lora_model_family),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                val manual = base != null && base != detected
                                val autoSelected = !manual
                                PillBadge(
                                    label = stringResource(R.string.lora_base_auto, detected.label),
                                    selected = autoSelected,
                                    onClick = { onSetBase(null) },
                                )
                                for (b in com.abrah.nightmare.LoraLibrary.Base.entries) {
                                    if (b == com.abrah.nightmare.LoraLibrary.Base.OTHER) continue
                                    val isSelected = manual && base == b
                                    PillBadge(
                                        label = b.label,
                                        selected = isSelected,
                                        onClick = { onSetBase(b) },
                                    )
                                }
                            }
                        }
                    }

                    // Folder Section (文件夹归类)
                    if (onSetFolder != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.lora_folder),
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.sub),
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                PillBadge(
                                    label = stringResource(R.string.lora_folder_none),
                                    selected = folder == null,
                                    onClick = { onSetFolder(null) },
                                )
                                for (fo in (folderChoices + listOfNotNull(folder)).distinct()) {
                                    PillBadge(
                                        label = fo,
                                        selected = folder == fo,
                                        onClick = { onSetFolder(fo) },
                                    )
                                }
                                PillBadge(
                                    label = "+ " + stringResource(R.string.lora_folder_new),
                                    selected = newFolder != null,
                                    onClick = { newFolder = if (newFolder != null) null else "" },
                                )
                            }
                            newFolder?.let { typed ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.7f) else Color(0xFFF6F5FA))
                                            .border(1.dp, Nm.line, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicTextField(
                                            value = typed,
                                            onValueChange = { newFolder = it },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 13.sp, color = Nm.ink),
                                            cursorBrush = SolidColor(Nm.pri),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (typed.isNotBlank()) Nm.pri else Color(0xFFDDD9E6))
                                            .clickable(enabled = typed.isNotBlank()) {
                                                onSetFolder(typed.trim())
                                                newFolder = null
                                            }
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            stringResource(R.string.lora_folder_add),
                                            style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Copy action button
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (Nm.customBgActive) Color.White.copy(alpha = 0.6f) else Color(0xFFF2F0F7))
                                .border(1.dp, Color(0xFFE2E0E8), RoundedCornerShape(10.dp))
                                .clickable(enabled = text.isNotBlank()) {
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text.trim()))
                                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.lora_note_copied), android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "📋 " + stringResource(R.string.err_copy),
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (text.isNotBlank()) Nm.ink else Nm.sub.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }

                    // Footer Action Buttons
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (onDelete != null) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFDE8E8))
                                    .border(1.dp, Color(0xFFF8B4B4), RoundedCornerShape(12.dp))
                                    .clickable { onDelete() }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.delete),
                                    style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE02424)),
                                )
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        Text(
                            stringResource(R.string.crop_done),
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Nm.pri)
                                .clickable { animateDismiss(true) }
                                .padding(horizontal = 26.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillBadge(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Nm.ink,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Nm.pri else if (Nm.customBgActive) Color.White.copy(alpha = 0.65f) else Color(0xFFF3F1F7))
            .border(1.dp, if (selected) Nm.pri else Color(0xFFE4E1EB), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

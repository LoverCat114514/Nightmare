package com.abrah.nightmare.reverse

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.abrah.nightmare.newui.theme.Nm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clean vector helper matching Nightmare's zero-bloat icon pattern.
 */
private fun glyph(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.White),
        )
    }.build()

private val IconChip: ImageVector by lazy {
    glyph("Memory", "M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z")
}

private val IconCloud: ImageVector by lazy {
    glyph("Cloud", "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z")
}

private val IconDownload: ImageVector by lazy {
    glyph("Download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
}

private val IconFolder: ImageVector by lazy {
    glyph("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
}

private val IconSparkle: ImageVector by lazy {
    glyph("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z")
}

private val IconImage: ImageVector by lazy {
    glyph("Image", "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z")
}

private val IconPaste: ImageVector by lazy {
    glyph("ContentPaste", "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z")
}

/**
 * API Provider Presets for quick setup.
 */
private data class ApiPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
)

private val API_PRESETS = listOf(
    ApiPreset("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-vl-plus"),
    ApiPreset("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen3-VL-30B-A3B-Instruct"),
    ApiPreset("OpenRouter", "https://openrouter.ai/api/v1", "qwen/qwen3-vl-30b-a3b-instruct"),
    ApiPreset("自定义", "", ""),
)


/**
 * ⭐⭐⭐ Reverse Prompt Activity (反推) — Dual-mode UI supporting Flagship On-Device
 * Qwen3-VL-4B (Online Download / Local Import) and Online VLM APIs.
 */
class ReversePromptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROMPT = "reverse_prompt"
        const val PREFS_NAME = "reverse_prompt_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val backend: ReversePromptBackend = ReversePromptBackendImpl(applicationContext)

        setContent {
            ReversePromptScreen(
                backend = backend,
                onClose = { finish() },
                onApplyPrompt = { prompt ->
                    val intent = Intent().apply {
                        putExtra(EXTRA_PROMPT, prompt)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                },
            )
        }
    }
}

@Composable
private fun ReversePromptScreen(
    backend: ReversePromptBackend,
    onClose: () -> Unit,
    onApplyPrompt: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(ReversePromptActivity.PREFS_NAME, Context.MODE_PRIVATE) }

    var engineMode by remember { mutableStateOf(ReverseEngineMode.LOCAL) }
    var outputStyle by remember { mutableStateOf(ReverseOutputStyle.MOSS_LAYOUT) }
    var localStatus by remember { mutableStateOf(LocalModelStatus()) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // API Config persisted in SharedPreferences
    var apiConfig by remember {
        mutableStateOf(
            ApiEndpointConfig(
                providerName = prefs.getString("provider", "阿里云百炼") ?: "阿里云百炼",
                baseUrl = prefs.getString("base_url", "https://dashscope.aliyuncs.com/compatible-mode/v1") ?: "",
                apiKey = prefs.getString("api_key", "") ?: "",
                modelId = prefs.getString("model_id", "qwen3-vl-plus") ?: "qwen3-vl-plus",
            ),
        )
    }
    var showApiKey by remember { mutableStateOf(false) }
    var apiExpanded by remember { mutableStateOf(true) }
    var isTestingApi by remember { mutableStateOf(false) }

    // Image & Inference State
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf("") }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        localStatus = backend.getLocalModelStatus()
    }

    fun saveApiConfig(cfg: ApiEndpointConfig) {
        apiConfig = cfg
        prefs.edit()
            .putString("provider", cfg.providerName)
            .putString("base_url", cfg.baseUrl)
            .putString("api_key", cfg.apiKey)
            .putString("model_id", cfg.modelId)
            .apply()
    }

    // Pickers
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    selectedBitmap = bmp
                } else {
                    Toast.makeText(context, "无法读取所选图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val mainModelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                localStatus = try {
                    backend.importLocalModelFile(uri, isMmproj = false)
                } catch (t: Throwable) {
                    Toast.makeText(context, "导入失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(context, "已导入主模型：${localStatus.mainModelName}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val mmprojPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                localStatus = try {
                    backend.importLocalModelFile(uri, isMmproj = true)
                } catch (t: Throwable) {
                    Toast.makeText(context, "导入失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(context, "已导入视觉投影：${localStatus.mmprojName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pasteImageFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val uri = clip.getItemAt(0).uri
            if (uri != null) {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bmp != null) {
                        selectedBitmap = bmp
                        Toast.makeText(context, "已从剪贴板粘贴图片", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "无法解析剪贴板图片", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
        Toast.makeText(context, "剪贴板中没有图片", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0F7)),
    ) {
        // ── 1. Top Navigation Bar ──
        Surface(
            color = Color.White.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, Color(0xFFE9E4F5)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF2F0F7),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onClose() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Nm.ink)
                        }
                    }
                    Column {
                        Text(
                            text = "反推",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Nm.ink,
                        )
                        Text(
                            text = "Qwen3-VL 端侧旗舰 · 在线 API 双引擎",
                            fontSize = 11.5.sp,
                            color = Nm.sub,
                        )
                    }
                }

                // Mode Badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (engineMode == ReverseEngineMode.LOCAL) Color(0xFFEDE9FE) else Color(0xFFDBEAFE),
                ) {
                    Text(
                        text = if (engineMode == ReverseEngineMode.LOCAL) "端侧 4B" else "在线 API",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (engineMode == ReverseEngineMode.LOCAL) Color(0xFF6D28D9) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── 2. Scrollable Workspace ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Segmented Mode Switcher ──
            item {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE9E4F5)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            ReverseEngineMode.LOCAL to "端侧本地反推",
                            ReverseEngineMode.API to "在线 API 反推",
                        ).forEach { (mode, label) ->
                            val selected = engineMode == mode
                            val pillColor by animateColorAsState(
                                targetValue = if (selected) Color(0xFF6D28D9) else Color.Transparent,
                                animationSpec = tween(220),
                                label = "modePillBg",
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (selected) Color.White else Nm.sub,
                                animationSpec = tween(220),
                                label = "modePillText",
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = pillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { engineMode = mode },
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Mode-Specific Panel with Smooth Transition ──
            item {
                AnimatedContent(
                    targetState = engineMode,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(280, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)),
                            initialOffsetY = { it / 12 },
                        ) + fadeIn(tween(220))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(180),
                                targetOffsetY = { -it / 12 },
                            ) + fadeOut(tween(160)),
                        )
                    },
                    label = "reverseModePanel",
                ) { currentMode ->
                    if (currentMode == ReverseEngineMode.LOCAL) {
                        LocalModelPanel(
                            status = localStatus,
                            onDownload = {
                                downloadJob?.cancel()
                                downloadJob = scope.launch {
                                    backend.downloadFlagshipModel().collect { st ->
                                        localStatus = st
                                    }
                                }
                            },
                            onCancelDownload = {
                                downloadJob?.cancel()
                                backend.cancelDownload()
                                localStatus = localStatus.copy(isDownloading = false, downloadProgress = 0f)
                            },
                            onImportMain = { mainModelPicker.launch(arrayOf("*/*")) },
                            onImportMmproj = { mmprojPicker.launch(arrayOf("*/*")) },
                            onDelete = {
                                scope.launch {
                                    localStatus = backend.deleteLocalModel()
                                    Toast.makeText(context, "已清理本地反推模型", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else {
                        ApiConfigPanel(
                            config = apiConfig,
                            expanded = apiExpanded,
                            showKey = showApiKey,
                            isTesting = isTestingApi,
                            onToggleExpand = { apiExpanded = !apiExpanded },
                            onToggleShowKey = { showApiKey = !showApiKey },
                            onConfigChange = ::saveApiConfig,
                            onTestConnection = {
                                scope.launch {
                                    isTestingApi = true
                                    val res = backend.testApiConnection(apiConfig)
                                    isTestingApi = false
                                    res.onSuccess { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "连接失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // ── Image Input Card ──
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "待反推图片",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Nm.ink,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Paste button
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFF3E8FF),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { pasteImageFromClipboard() },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = IconPaste,
                                            contentDescription = "粘贴",
                                            tint = Color(0xFF6D28D9),
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "粘贴图片",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6D28D9),
                                        )
                                    }
                                }

                                if (selectedBitmap != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color(0xFFFEE2E2),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { selectedBitmap = null },
                                    ) {
                                        Text(
                                            text = "移除",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (selectedBitmap == null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF9F8FD),
                                border = BorderStroke(1.2.dp, Color(0xFFDDD6FE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { imagePicker.launch("image/*") },
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFEDE9FE)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = IconImage,
                                            contentDescription = "选择图片",
                                            tint = Color(0xFF6D28D9),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "点击从相册选择图片",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Nm.ink,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "或点击右上角从剪贴板快速粘贴",
                                        fontSize = 11.5.sp,
                                        color = Nm.sub,
                                    )
                                }
                            }
                        } else {
                            val bmp = selectedBitmap!!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF121018))
                                    .clickable { imagePicker.launch("image/*") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "待反推图片",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = "${bmp.width} × ${bmp.height}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Output Style Chips
                        Text(
                            text = "反推输出格式",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Nm.sub,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                ReverseOutputStyle.MOSS_LAYOUT to "MOSS 结构化布局 (JSON)",
                                ReverseOutputStyle.NATURAL to "常规自然语言描述",
                            ).forEach { (style, title) ->
                                val selected = outputStyle == style
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) Color(0xFFEDE9FE) else Color(0xFFF7F6FA),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) Color(0xFF8B5CF6) else Color(0xFFE5E1EF),
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { outputStyle = style },
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color(0xFF6D28D9) else Nm.sub,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        // ── Persistent Error Card (full text, never truncated) ──
                        errorBanner?.let { err ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⚠ 反推失败",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C),
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "关闭",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFDC2626),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { errorBanner = null }
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = err,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF7F1D1D),
                                        lineHeight = 18.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        // Start Reverse Prompting Button
                        val canStart = selectedBitmap != null && !isRunning
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (canStart) {
                                        Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFFD1D5DB), Color(0xFF9CA3AF)))
                                    },
                                )
                                .clickable(enabled = canStart) {
                                    val bmp = selectedBitmap ?: return@clickable
                                    scope.launch {
                                        isRunning = true
                                        statusBanner = "正在初始化推理..."
                                        backend.runReversePrompt(
                                            bitmap = bmp,
                                            mode = engineMode,
                                            style = outputStyle,
                                            apiConfig = apiConfig,
                                        ).collect { progress ->
                                            when (progress) {
                                                is ReverseProgress.Loading -> statusBanner = progress.message
                                                is ReverseProgress.Streaming -> resultText = progress.partialText
                                                is ReverseProgress.Success -> {
                                                    resultText = progress.resultText
                                                    statusBanner = null
                                                    isRunning = false
                                                }
                                                is ReverseProgress.Error -> {
                                                    statusBanner = null
                                                    isRunning = false
                                                    // Full error text in a persistent card —
                                                    // Toasts truncate long messages, which made
                                                    // errors look like partial/no feedback.
                                                    errorBanner = progress.message
                                                }
                                            }
                                        }
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (isRunning) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = statusBanner ?: "正在反推...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                } else {
                                    Icon(
                                        imageVector = IconSparkle,
                                        contentDescription = "开始反推",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "开始反推",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Result Output Card ──
            item {
                AnimatedVisibility(
                    visible = resultText.isNotBlank() || isRunning,
                    enter = expandVertically(tween(280)) + fadeIn(tween(240)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "反推结果",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Nm.ink,
                                )
                                if (resultText.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color(0xFFF2F0F7),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("reverse_prompt", resultText))
                                                Toast.makeText(context, "已复制反推结果", Toast.LENGTH_SHORT).show()
                                            },
                                    ) {
                                        Text(
                                            text = "复制结果",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Nm.ink,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = resultText,
                                onValueChange = { resultText = it },
                                placeholder = { Text("反推生成的提示词或 MOSS 布局 JSON 将显示在这里...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF7C3AED),
                                    unfocusedBorderColor = Color(0xFFE5E1EF),
                                    focusedContainerColor = Color(0xFFFBFAFE),
                                    unfocusedContainerColor = Color(0xFFFBFAFE),
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (resultText.isNotBlank()) Color(0xFFEDE9FE) else Color(0xFFF3F4F6),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = resultText.isNotBlank()) {
                                        onApplyPrompt(resultText.trim())
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "应用到画布提示词",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (resultText.isNotBlank()) Color(0xFF6D28D9) else Color(0xFF9CA3AF),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalModelPanel(
    status: LocalModelStatus,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onImportMain: () -> Unit,
    onImportMmproj: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = IconChip,
                            contentDescription = "旗舰模型",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Qwen3-VL-4B-Instruct",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Nm.ink,
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFEDE9FE),
                            ) {
                                Text(
                                    text = "旗舰 12GB+",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D28D9),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = if (status.modelReady) "模型已就绪，可离线运行" else "需配置主模型 (.gguf) 与视觉投影 (mmproj)",
                            fontSize = 11.5.sp,
                            color = if (status.modelReady) Color(0xFF059669) else Nm.sub,
                        )
                    }
                }

                if (status.mainModelName != null || status.mmprojName != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onDelete() },
                    ) {
                        Text(
                            text = "清理",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // File status pills
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF9F8FD),
                border = BorderStroke(1.dp, Color(0xFFE9E4F5)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelFileRow(
                        label = "语言主模型 (Q4_K_M / Q8_0)",
                        fileName = status.mainModelName,
                        bytes = status.mainModelBytes,
                    )
                    ModelFileRow(
                        label = "视觉投影 (mmproj)",
                        fileName = status.mmprojName,
                        bytes = status.mmprojBytes,
                    )
                }
            }

            // Download progress bar if active
            AnimatedVisibility(visible = status.isDownloading) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = status.downloadDetail.ifBlank { "正在下载旗舰模型..." },
                            fontSize = 11.5.sp,
                            color = Color(0xFF6D28D9),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${(status.downloadProgress * 100).toInt()}%",
                            fontSize = 11.5.sp,
                            color = Color(0xFF6D28D9),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val animatedProg by animateFloatAsState(
                        targetValue = status.downloadProgress.coerceIn(0f, 1f),
                        label = "vlmDlProgress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProg },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = Color(0xFF7C3AED),
                        trackColor = Color(0xFFEDE9FE),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Two Import Ways: Online Download & Local Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1. Online Download
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (status.isDownloading) Color(0xFFFEE2E2) else Color(0xFF6D28D9),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (status.isDownloading) onCancelDownload() else onDownload()
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (!status.isDownloading) {
                            Icon(
                                imageVector = IconDownload,
                                contentDescription = "在线下载",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = if (status.isDownloading) "取消下载" else "在线下载",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status.isDownloading) Color(0xFFDC2626) else Color.White,
                        )
                    }
                }

                // 2. Local Import (Main GGUF)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF3E8FF),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onImportMain() },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = IconFolder,
                            contentDescription = "导入主模型",
                            tint = Color(0xFF6D28D9),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "导入主模型",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                        )
                    }
                }

                // 3. Local Import (mmproj)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF3E8FF),
                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onImportMmproj() },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = IconFolder,
                            contentDescription = "导入视觉",
                            tint = Color(0xFF6D28D9),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "导入 mmproj",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelFileRow(
    label: String,
    fileName: String?,
    bytes: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Nm.ink,
            )
            Text(
                text = fileName ?: "未配置",
                fontSize = 11.sp,
                color = if (fileName != null) Color(0xFF6D28D9) else Nm.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (fileName != null) Color(0xFFDCFCE7) else Color(0xFFE5E7EB),
        ) {
            val sizeText = if (fileName != null && bytes > 0L) {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                if (gb >= 1.0) String.format("%.2f GB", gb)
                else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
            } else if (fileName != null) {
                "已就绪"
            } else {
                "缺失"
            }
            Text(
                text = sizeText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (fileName != null) Color(0xFF15803D) else Color(0xFF6B7280),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ApiConfigPanel(
    config: ApiEndpointConfig,
    expanded: Boolean,
    showKey: Boolean,
    isTesting: Boolean,
    onToggleExpand: () -> Unit,
    onToggleShowKey: () -> Unit,
    onConfigChange: (ApiEndpointConfig) -> Unit,
    onTestConnection: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = IconCloud,
                            contentDescription = "在线 API",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "在线视觉模型 API",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Nm.ink,
                        )
                        Text(
                            text = "${config.providerName} · ${config.modelId.ifBlank { "未填写模型" }}",
                            fontSize = 11.5.sp,
                            color = Nm.sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (expanded) "收起" else "展开",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2563EB),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Presets
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(API_PRESETS) { preset ->
                            val selected = config.providerName == preset.name
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) Color(0xFFDBEAFE) else Color(0xFFF3F4F6),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF3B82F6) else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        onConfigChange(
                                            config.copy(
                                                providerName = preset.name,
                                                baseUrl = if (preset.baseUrl.isNotEmpty()) preset.baseUrl else config.baseUrl,
                                                modelId = if (preset.defaultModel.isNotEmpty()) preset.defaultModel else config.modelId,
                                            ),
                                        )
                                    },
                            ) {
                                Text(
                                    text = preset.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color(0xFF1D4ED8) else Nm.sub,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = config.baseUrl,
                        onValueChange = { onConfigChange(config.copy(baseUrl = it)) },
                        label = { Text("Base URL (OpenAI 兼容)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { onConfigChange(config.copy(apiKey = it)) },
                        label = { Text("API Key", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showKey) "隐藏" else "显示",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clickable { onToggleShowKey() },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.modelId,
                        onValueChange = { onConfigChange(config.copy(modelId = it)) },
                        label = { Text("视觉模型名称 (Model ID)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isTesting) { onTestConnection() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    color = Color(0xFF2563EB),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    text = "测试 API 连接",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

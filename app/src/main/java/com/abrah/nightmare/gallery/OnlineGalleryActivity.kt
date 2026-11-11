package com.abrah.nightmare.gallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐ Gallery Site Model
 */
data class GallerySite(
    val id: String,
    val name: String,
    val domain: String,
    val url: String,
    val description: String,
    val iconGlyph: String,
    val badge: String? = null,
)

val GALLERY_SITES = listOf(
    GallerySite(
        id = "aitag",
        name = "AI TAG",
        domain = "aitag.win",
        url = "https://aitag.win/",
        description = "AI 绘画咒语图库",
        iconGlyph = "🏷️",
        badge = "推荐",
    ),
    GallerySite(
        id = "civitai",
        name = "Civitai",
        domain = "civitai.com",
        url = "https://civitai.com/",
        description = "开源模型与作品社区",
        iconGlyph = "🌐",
    ),
    GallerySite(
        id = "danbooru",
        name = "Danbooru",
        domain = "danbooru.donmai.us",
        url = "https://danbooru.donmai.us/",
        description = "二次元标签与画作",
        iconGlyph = "🎨",
    ),
    GallerySite(
        id = "safebooru",
        name = "Safebooru",
        domain = "safebooru.org",
        url = "https://safebooru.org/",
        description = "全年龄二次元图库",
        iconGlyph = "🛡️",
    ),
    GallerySite(
        id = "liblib",
        name = "LiblibAI",
        domain = "liblib.art",
        url = "https://www.liblib.art/",
        description = "国内原创模型社区",
        iconGlyph = "📚",
    ),
    GallerySite(
        id = "openart",
        name = "OpenArt",
        domain = "openart.ai",
        url = "https://openart.ai/",
        description = "AI 艺术作品与灵感搜索",
        iconGlyph = "🧭",
    ),
    GallerySite(
        id = "lexica",
        name = "Lexica",
        domain = "lexica.art",
        url = "https://lexica.art/",
        description = "SD 提示词搜索引擎",
        iconGlyph = "🔍",
    ),
    GallerySite(
        id = "yande",
        name = "Yande.re",
        domain = "yande.re",
        url = "https://yande.re/post",
        description = "高质量插画壁纸",
        iconGlyph = "🖼️",
    ),
    GallerySite(
        id = "custom",
        name = "自定义",
        domain = "输入网址",
        url = "",
        description = "输入任意画廊网址",
        iconGlyph = "➕",
    ),
)

/**
 * ⭐⭐⭐ Online Gallery Activity — Fullscreen, multi-site AI art gallery hub and
 * docked browser with instant prompt extraction and application to Nightmare's canvas.
 */
class OnlineGalleryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROMPT = "gallery_prompt"
        const val EXTRA_NEGATIVE = "gallery_negative"
    }

    private var webView: WebView? = null
    private var onBackPressAction: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GalleryContainer(
                onClose = { finish() },
                onApplyPrompt = { prompt ->
                    val intent = Intent().apply {
                        putExtra(EXTRA_PROMPT, prompt)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                },
                onRegisterBackHandler = { onBackPressAction = it },
                onRegisterWebView = { webView = it },
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (onBackPressAction?.invoke() == true) {
            return
        }
        super.onBackPressed()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GalleryContainer(
    onClose: () -> Unit,
    onApplyPrompt: (String) -> Unit,
    onRegisterBackHandler: (() -> Boolean) -> Unit,
    onRegisterWebView: (WebView) -> Unit,
) {
    val context = LocalContext.current
    var activeSite by remember { mutableStateOf<GallerySite?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf("") }
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractedPromptText by remember { mutableStateOf("") }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("https://") }

    // Intercept hardware Back
    val handleBack: () -> Boolean = {
        if (activeSite != null) {
            if (webViewRef?.canGoBack() == true) {
                webViewRef?.goBack()
                true
            } else {
                activeSite = null
                true
            }
        } else {
            false
        }
    }

    LaunchedEffect(Unit) {
        onRegisterBackHandler(handleBack)
    }

    BackHandler {
        if (!handleBack()) {
            onClose()
        }
    }

    if (activeSite == null) {
        // ── View A: Gallery Site Selection Hub (Figure 1 style 3-column squircle grid) ──
        GalleryHubView(
            sites = GALLERY_SITES,
            onClose = onClose,
            onPickSite = { site ->
                if (site.id == "custom") {
                    showCustomUrlDialog = true
                } else {
                    activeSite = site
                }
            },
        )
    } else {
        // ── View B: Gallery Web Browser (Docked layout so web controls are NEVER blocked) ──
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
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left: Return to Hub
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF2F0F7),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { activeSite = null },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("←", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Nm.ink)
                            }
                        }

                        // Center: Title and badge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = activeSite?.name ?: "在线画廊",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Nm.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFEDE9FE),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981)),
                                        )
                                        Text(
                                            text = activeSite?.domain ?: "",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6D28D9),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = currentTitle.ifBlank { activeSite?.description ?: "" },
                                fontSize = 11.sp,
                                color = Nm.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Right: Switch site, Reload, External Browser
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // ⊞ Switch Site button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF2F0F7),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { activeSite = null },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⊞", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Nm.ink)
                                }
                            }

                            // 🔄 Refresh button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF2F0F7),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { webViewRef?.reload() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🔄", fontSize = 14.sp)
                                }
                            }

                            // 🌐 External browser button
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF2F0F7),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val url = webViewRef?.url ?: activeSite?.url ?: ""
                                        if (url.isNotBlank()) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🌐", fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Loading Progress Indicator
                    val animatedProgress by animateFloatAsState(
                        targetValue = loadingProgress / 100f,
                        label = "webLoadingProgress",
                    )
                    if (loadingProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = Color(0xFF7C3AED),
                            trackColor = Color(0xFFEDE9FE),
                        )
                    }
                }
            }

            // ── 2. WebView Content (weight(1f) strictly ends above the bottom bar, NEVER covers web controls) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = userAgentString.replace("; wv", "")
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadingProgress = newProgress
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        currentTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loadingProgress = 15
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loadingProgress = 100
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val u = request?.url?.toString() ?: return false
                                    return if (u.startsWith("http://") || u.startsWith("https://")) {
                                        false
                                    } else {
                                        try {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                                            true
                                        } catch (e: Exception) {
                                            true
                                        }
                                    }
                                }
                            }

                            loadUrl(activeSite?.url ?: "")
                            webViewRef = this
                            onRegisterWebView(this)
                        }
                    },
                    update = { view ->
                        val url = activeSite?.url ?: ""
                        if (url.isNotBlank() && view.url != url) {
                            view.loadUrl(url)
                        }
                    },
                )
            }

            // ── 3. Docked Bottom Bar (Cleanly docked below WebView, NEVER overlaps any web buttons!) ──
            Surface(
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFE9E4F5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Navigation controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (canGoBack) Color(0xFFF3E8FF) else Color(0xFFF3F4F6),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canGoBack) { webViewRef?.goBack() },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "‹",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canGoBack) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (canGoForward) Color(0xFFF3E8FF) else Color(0xFFF3F4F6),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canGoForward) { webViewRef?.goForward() },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "›",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canGoForward) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
                                )
                            }
                        }

                        // Switch gallery button
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF2F0F7),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { activeSite = null },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("⊞", fontSize = 13.sp, color = Nm.ink)
                                Text("换站点", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.ink)
                            }
                        }
                    }

                    // Primary Action: Apply Prompt to Canvas
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                                )
                            )
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = cm?.primaryClip?.let { clip ->
                                    if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString() else null
                                } ?: ""
                                extractedPromptText = clipText.trim()
                                showExtractDialog = true
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("✨", fontSize = 13.sp)
                            Text(
                                text = "提取并填入画布",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Custom URL Input Dialog ──
    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = {
                Text("输入画廊网址", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Nm.ink)
            },
            text = {
                Column {
                    Text("输入想要浏览的图库或画廊 URL：", fontSize = 12.5.sp, color = Nm.sub)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customUrlInput.trim()
                        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            showCustomUrlDialog = false
                            val domain = Uri.parse(trimmed).host ?: "自定义"
                            activeSite = GallerySite(
                                id = "custom_url",
                                name = "自定义画廊",
                                domain = domain,
                                url = trimmed,
                                description = domain,
                                iconGlyph = "🌐",
                            )
                        } else {
                            Toast.makeText(context, "请输入以 http:// 或 https:// 开头的合法网址", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9)),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("打开", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("取消", color = Nm.sub)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
        )
    }

    // ── Prompt Application Confirmation Dialog ──
    if (showExtractDialog) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("✨", fontSize = 18.sp)
                    Text(
                        "应用提示词到画布",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = Nm.ink,
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "已从剪贴板读取你在画廊中复制的提示词：",
                        fontSize = 12.5.sp,
                        color = Nm.sub,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = extractedPromptText,
                        onValueChange = { extractedPromptText = it },
                        placeholder = { Text("若未自动复制，可在此直接粘贴画廊中的提示词...") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "💡 确认后将直接填入主画布正面提示词，返回即可一键生成！",
                        fontSize = 11.5.sp,
                        color = Color(0xFF7C3AED),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (extractedPromptText.isNotBlank()) {
                            showExtractDialog = false
                            onApplyPrompt(extractedPromptText.trim())
                        } else {
                            Toast.makeText(context, "提示词不能为空，请先在画廊复制或输入提示词", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9)),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("立即填入画布", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) {
                    Text("取消", color = Nm.sub)
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
        )
    }
}

/**
 * ⭐⭐⭐ View A: Gallery Site Selection Hub (Figure 1 style clean 3-column squircle grid)
 */
@Composable
private fun GalleryHubView(
    sites: List<GallerySite>,
    onClose: () -> Unit,
    onPickSite: (GallerySite) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0F7)),
    ) {
        // ── Top Header ──
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
                            text = "在线画廊",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Nm.ink,
                        )
                        Text(
                            text = "选择要浏览的 AI 咒语与图库站点",
                            fontSize = 11.5.sp,
                            color = Nm.sub,
                        )
                    }
                }
            }
        }

        // ── 3-Column Squircle Cards Grid (Figure 1 Style) ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            items(sites) { site ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFEFEAFB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPickSite(site) },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Card Content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = site.iconGlyph,
                                fontSize = 28.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = site.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Nm.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }

                        // Badge (e.g. 推荐)
                        if (site.badge != null) {
                            Surface(
                                shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 16.dp),
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Text(
                                    text = site.badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

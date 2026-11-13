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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.abrah.nightmare.newui.theme.Nm

/**
 * Clean vector helper.
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

private val IconTag: ImageVector by lazy {
    glyph("Tag", "M21.41 11.58l-9-9C12.05 2.22 11.55 2 11 2H4c-1.1 0-2 .9-2 2v7c0 .55.22 1.05.59 1.42l9 9c.36.36.86.58 1.41.58.55 0 1.05-.22 1.41-.59l7-7c.37-.36.59-.86.59-1.41 0-.55-.23-1.06-.59-1.42zM5.5 7C4.67 7 4 6.33 4 5.5S4.67 4 5.5 4 7 4.67 7 5.5 6.33 7 5.5 7z")
}

private val IconArt: ImageVector by lazy {
    glyph("Palette", "M12 3c-4.97 0-9 4.03-9 9 0 2.12.74 4.07 1.97 5.61L4.35 19c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0l1.9-1.9C9.22 19.43 10.57 20 12 20c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-4.5 9c-.83 0-1.5-.67-1.5-1.5S6.67 9 7.5 9s1.5.67 1.5 1.5S8.33 12 7.5 12zm3-4C9.67 8 9 7.33 9 6.5S9.67 5 10.5 5s1.5.67 1.5 1.5S11.33 8 10.5 8zm3 0c-.83 0-1.5-.67-1.5-1.5S12.67 5 13.5 5s1.5.67 1.5 1.5S14.33 8 13.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z")
}

private val IconPlus: ImageVector by lazy {
    glyph("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
}

/**
 * ⭐ Gallery Site Model
 */
data class GallerySite(
    val id: String,
    val name: String,
    val domain: String,
    val url: String,
    val description: String,
    val iconVector: ImageVector,
    val gradientColors: List<Color>,
    val badge: String? = null,
)

/**
 * Curated list: Only aitag, danbooru, and custom as explicitly requested.
 */
val GALLERY_SITES = listOf(
    GallerySite(
        id = "aitag",
        name = "AI TAG",
        domain = "aitag.win",
        url = "https://aitag.win/",
        description = "AI 绘画咒语图库 (SD / ComfyUI / NAI)",
        iconVector = IconTag,
        gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        badge = "推荐",
    ),
    GallerySite(
        id = "danbooru",
        name = "Danbooru",
        domain = "danbooru.donmai.us",
        url = "https://danbooru.donmai.us/",
        description = "二次元高清动漫作品与精准 Tag 索引",
        iconVector = IconArt,
        gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        badge = "二次元",
    ),
    GallerySite(
        id = "custom",
        name = "自定义网址",
        domain = "输入任意站点",
        url = "",
        description = "支持加载任意第三方 AI 画廊或图库站点",
        iconVector = IconPlus,
        gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
        badge = "扩展",
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
        with(com.abrah.nightmare.ui.PopOrigin) { disableWindowTransitions() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            com.abrah.nightmare.ui.PopExpandContainer(
                intent = intent,
                onFinish = {
                    finish()
                    with(com.abrah.nightmare.ui.PopOrigin) { disableWindowTransitions() }
                },
            ) { requestClose ->
                GalleryContainer(
                    onClose = requestClose,
                    onApplyPrompt = { prompt ->
                        val resIntent = Intent().apply {
                            putExtra(EXTRA_PROMPT, prompt)
                        }
                        setResult(Activity.RESULT_OK, resIntent)
                        requestClose()
                    },
                    onRegisterBackHandler = { onBackPressAction = it },
                    onRegisterWebView = { webView = it },
                )
            }
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

    // ⭐ Fluid AnimatedContent transition between Hub and Browser: eliminates jarring hard cuts!
    AnimatedContent(
        targetState = activeSite,
        transitionSpec = {
            if (targetState != null) {
                // Opening Web Browser from Hub: slides up with spring and fades in
                (slideInVertically(
                    animationSpec = tween(340, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)),
                    initialOffsetY = { (it * 0.12f).toInt() },
                ) + fadeIn(
                    animationSpec = tween(280),
                )).togetherWith(
                    fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.96f, animationSpec = tween(240)),
                )
            } else {
                // Returning to Hub from Web Browser: scales & fades back in, browser slides down
                (fadeIn(
                    animationSpec = tween(260),
                ) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(280, easing = CubicBezierEasing(0.20f, 0.95f, 0.28f, 1f)),
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(240, easing = CubicBezierEasing(0.35f, 0f, 0.75f, 0.15f)),
                        targetOffsetY = { (it * 0.12f).toInt() },
                    ) + fadeOut(animationSpec = tween(200)),
                )
            }
        },
        label = "galleryTransition",
        modifier = Modifier.fillMaxSize(),
    ) { currentSite ->
        if (currentSite == null) {
            // ── View A: Gallery Site Selection Hub (Clean, beautiful cards) ──
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
                                        text = currentSite.name,
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
                                                text = currentSite.domain,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6D28D9),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = currentTitle.ifBlank { currentSite.description },
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
                                            val url = webViewRef?.url ?: currentSite.url
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
                                // Prevent black flash during initialization
                                setBackgroundColor(android.graphics.Color.parseColor("#F2F0F7"))
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

                                loadUrl(currentSite.url)
                                webViewRef = this
                                onRegisterWebView(this)
                            }
                        },
                        update = { view ->
                            val url = currentSite.url
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
                                iconVector = IconPlus,
                                gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
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
 * ⭐⭐⭐ View A: Gallery Site Selection Hub — Clean, spacious, aesthetic cards
 * featuring ONLY the three essential destinations: AI TAG, Danbooru, and Custom URL.
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
                            text = "精选 AI 咒语与高质量作品图库",
                            fontSize = 11.5.sp,
                            color = Nm.sub,
                        )
                    }
                }
            }
        }

        // ── 3 Clean Full-Width Modern Cards ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            sites.forEach { site ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPickSite(site) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Vector icon box
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(site.gradientColors))
                                .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = site.gradientColors.first().copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = site.iconVector,
                                contentDescription = site.name,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = site.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Nm.ink,
                                )
                                if (site.badge != null) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = site.gradientColors.first().copy(alpha = 0.12f),
                                    ) {
                                        Text(
                                            text = site.badge,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = site.gradientColors.first(),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = site.description,
                                fontSize = 12.5.sp,
                                color = Nm.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDE9FE).copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "›",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Bottom tip card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFEDE9FE).copy(alpha = 0.45f),
                border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("💡", fontSize = 16.sp)
                    Text(
                        text = "在任意画廊中看到喜欢的图片，复制提示词并点击底部「提取并填入画布」，即可直接带回主画布一键生图！",
                        fontSize = 12.sp,
                        color = Color(0xFF5B21B6),
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

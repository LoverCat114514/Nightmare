package com.abrah.nightmare.gallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.abrah.nightmare.newui.theme.Nm

/**
 * ⭐⭐⭐ Online Gallery Activity — Fullscreen, native web browser experience for AI TAG
 * (https://aitag.win/) with instant prompt extraction and application to Nightmare's canvas.
 */
class OnlineGalleryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROMPT = "gallery_prompt"
        const val EXTRA_NEGATIVE = "gallery_negative"
        const val TARGET_URL = "https://aitag.win/"
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GalleryScreen(
                initialUrl = TARGET_URL,
                onClose = { finish() },
                onApplyPrompt = { prompt ->
                    val intent = Intent().apply {
                        putExtra(EXTRA_PROMPT, prompt)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                },
                onRegisterWebView = { webView = it },
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GalleryScreen(
    initialUrl: String,
    onClose: () -> Unit,
    onApplyPrompt: (String) -> Unit,
    onRegisterWebView: (WebView) -> Unit,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf("在线画廊") }
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractedPromptText by remember { mutableStateOf("") }

    // Intercept hardware Back
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0F7)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 1. Top Navigation Bar ──
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
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
                        // Left: Back/Close button
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
                                    text = "在线画廊",
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
                                            text = "aitag.win",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6D28D9),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = currentTitle.ifBlank { "AI 绘画咒语图库" },
                                fontSize = 11.sp,
                                color = Nm.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Right: Refresh & External browser buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF2F0F7),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val url = webViewRef?.url ?: initialUrl
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

            // ── 2. WebView Content ──
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                // Set mobile User Agent
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

                            loadUrl(initialUrl)
                            webViewRef = this
                            onRegisterWebView(this)
                        }
                    },
                )
            }
        }

        // ── 3. Bottom Floating Action Pill ──
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                // Back button
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

                Spacer(Modifier.width(6.dp))

                // Forward button
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

                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(Color(0xFFE5E7EB)),
                )
                Spacer(Modifier.width(10.dp))

                // Main Action: Apply Prompt to Canvas
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
                            // Check device clipboard
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("✨", fontSize = 13.sp)
                        Text(
                            text = "提取并应用到画布",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // ── 4. Prompt Application Confirmation Dialog ──
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
}

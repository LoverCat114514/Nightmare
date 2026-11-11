package com.abrah.nightmare.scramble

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.abrah.nightmare.newui.theme.Nm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ScrambleState { ORIGINAL, SCRAMBLED, DESCRAMBLED }
enum class AutoAction { NONE, ENCRYPT, DECRYPT }

data class ScrambleImageItem(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    var currentBitmap: Bitmap,
    var state: ScrambleState = ScrambleState.ORIGINAL,
    var isProcessing: Boolean = false,
)

/**
 * ⭐⭐⭐ Image Scramble Activity — Fully reconstructed, high-aesthetic, fluid
 * image scrambler and descrambler based on discrete Hilbert space-filling curve permutation.
 */
class ImageScrambleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ScrambleScreen(onClose = { finish() })
        }
    }
}

@Composable
private fun ScrambleScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoAction by remember { mutableStateOf(AutoAction.NONE) }
    val imageItems = remember { mutableStateListOf<ScrambleImageItem>() }
    // Function to process a single item
    suspend fun processItem(item: ScrambleImageItem, toScramble: Boolean) {
        val index = imageItems.indexOfFirst { it.id == item.id }
        if (index == -1) return
        imageItems[index] = item.copy(isProcessing = true)

        try {
            val result = if (toScramble) {
                ImageScrambleEngine.scramble(item.currentBitmap)
            } else {
                ImageScrambleEngine.descramble(item.currentBitmap)
            }
            val newState = if (toScramble) ScrambleState.SCRAMBLED else ScrambleState.DESCRAMBLED
            val curIndex = imageItems.indexOfFirst { it.id == item.id }
            if (curIndex != -1) {
                imageItems[curIndex] = item.copy(
                    currentBitmap = result,
                    state = newState,
                    isProcessing = false,
                )
            }
        } catch (e: Exception) {
            val curIndex = imageItems.indexOfFirst { it.id == item.id }
            if (curIndex != -1) {
                imageItems[curIndex] = item.copy(isProcessing = false)
            }
        }
    }

    var isBatchProcessing by remember { mutableStateOf(false) }

    // Multi-Image Picker
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val loadedList = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                for (bitmap in loadedList) {
                    val item = ScrambleImageItem(
                        originalBitmap = bitmap,
                        currentBitmap = bitmap,
                        state = ScrambleState.ORIGINAL,
                    )
                    imageItems.add(item)
                    // Apply auto action if set
                    if (autoAction == AutoAction.ENCRYPT) {
                        launch { processItem(item, toScramble = true) }
                    } else if (autoAction == AutoAction.DECRYPT) {
                        launch { processItem(item, toScramble = false) }
                    }
                }
            }
        }
    }


    // Function to restore a single item
    fun restoreItem(item: ScrambleImageItem) {
        val index = imageItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            imageItems[index] = item.copy(
                currentBitmap = item.originalBitmap,
                state = ScrambleState.ORIGINAL,
                isProcessing = false,
            )
        }
    }

    // Paste from clipboard
    fun pasteImage() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val uri = clip.getItemAt(0).uri
            if (uri != null) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        val item = ScrambleImageItem(
                            originalBitmap = bitmap,
                            currentBitmap = bitmap,
                            state = ScrambleState.ORIGINAL,
                        )
                        imageItems.add(item)
                        if (autoAction == AutoAction.ENCRYPT) {
                            launch { processItem(item, toScramble = true) }
                        } else if (autoAction == AutoAction.DECRYPT) {
                            launch { processItem(item, toScramble = false) }
                        }
                        Toast.makeText(context, "已粘贴图片", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未能从剪贴板解析图片", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
        Toast.makeText(context, "剪贴板中没有图片", Toast.LENGTH_SHORT).show()
    }

    // Save image to gallery
    fun saveImage(bitmap: Bitmap) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val fileName = "scramble_${System.currentTimeMillis()}"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nightmare")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                Toast.makeText(context, "已成功保存到相册 (Pictures/Nightmare)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0F7)),
    ) {
        // ── 1. Top Bar ──
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
                            text = "图片混淆",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Nm.ink,
                        )
                        Text(
                            text = "无损像素置换 · 安全加密与还原",
                            fontSize = 11.5.sp,
                            color = Nm.sub,
                        )
                    }
                }

                if (imageItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { imageItems.clear() },
                    ) {
                        Text(
                            text = "清空全部",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 2. Auto Action Selector Panel ──
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("⚙️", fontSize = 13.sp)
                            Text(
                                text = "自动处理设置",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Nm.ink,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Segmented 3-Way Pill Switcher
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF2F0F7),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(
                                    AutoAction.NONE to "不处理",
                                    AutoAction.ENCRYPT to "🔒 自动混淆",
                                    AutoAction.DECRYPT to "🔓 自动解混淆",
                                ).forEach { (action, label) ->
                                    val isSelected = autoAction == action
                                    val pillBg by animateColorAsState(
                                        targetValue = if (isSelected) Color.White else Color.Transparent,
                                        animationSpec = tween(200),
                                        label = "autoPillBg",
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) Color(0xFF6D28D9) else Nm.sub,
                                        animationSpec = tween(200),
                                        label = "autoPillText",
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = pillBg,
                                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable { autoAction = action },
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = textColor,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Primary Actions Bar ──
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Top row: Choose & Paste
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Choose Images
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { imagePicker.launch("image/*") },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text("📁", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "选择图片",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                    )
                                }
                            }

                            // Paste from Clipboard
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF3E8FF),
                                border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { pasteImage() },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text("📋", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "粘贴图片",
                                        color = Color(0xFF6D28D9),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                    )
                                }
                            }
                        }

                        // Bottom row: Batch Actions (Active only when items exist)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val hasImages = imageItems.isNotEmpty()

                            // 全部混淆
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasImages) Color(0xFFFFF1F2) else Color(0xFFF9FAFB),
                                border = BorderStroke(1.dp, if (hasImages) Color(0xFFFECDD3) else Color(0xFFE5E7EB)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = hasImages && !isBatchProcessing) {
                                        scope.launch {
                                            isBatchProcessing = true
                                            imageItems.forEach { item ->
                                                processItem(item, toScramble = true)
                                            }
                                            isBatchProcessing = false
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🔒 全部混淆",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasImages) Color(0xFFE11D48) else Color(0xFF9CA3AF),
                                    )
                                }
                            }

                            // 全部解混淆
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasImages) Color(0xFFECFDF5) else Color(0xFFF9FAFB),
                                border = BorderStroke(1.dp, if (hasImages) Color(0xFFA7F3D0) else Color(0xFFE5E7EB)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = hasImages && !isBatchProcessing) {
                                        scope.launch {
                                            isBatchProcessing = true
                                            imageItems.forEach { item ->
                                                processItem(item, toScramble = false)
                                            }
                                            isBatchProcessing = false
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🔓 全部解混淆",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasImages) Color(0xFF059669) else Color(0xFF9CA3AF),
                                    )
                                }
                            }

                            // 全部还原
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasImages) Color(0xFFF3F4F6) else Color(0xFFF9FAFB),
                                border = BorderStroke(1.dp, if (hasImages) Color(0xFFE5E7EB) else Color(0xFFF3F4F6)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = hasImages && !isBatchProcessing) {
                                        imageItems.forEach { item -> restoreItem(item) }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "↺ 全部还原",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasImages) Nm.ink else Color(0xFF9CA3AF),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. Image Cards / Empty State ──
            if (imageItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        border = BorderStroke(1.5.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                            .clickable { imagePicker.launch("image/*") },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 42.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEDE9FE)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🖼️", fontSize = 24.sp)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "点击选择图片 或 剪贴板粘贴",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Nm.ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "支持多张图片批量选择，基于空间填充曲线无损置换",
                                fontSize = 12.sp,
                                color = Nm.sub,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = imageItems,
                    key = { _, item -> item.id },
                ) { index, item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.2.dp, Color(0xFFEDE9FE)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header of each image card
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEDE9FE),
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF6D28D9),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }

                                    // State Badge
                                    val (badgeBg, badgeText, badgeColor) = when (item.state) {
                                        ScrambleState.ORIGINAL -> Triple(Color(0xFFF3F4F6), "原图", Color(0xFF4B5563))
                                        ScrambleState.SCRAMBLED -> Triple(Color(0xFFFEE2E2), "🔒 已混淆", Color(0xFFDC2626))
                                        ScrambleState.DESCRAMBLED -> Triple(Color(0xFFDCFCE7), "🔓 已解混淆", Color(0xFF16A34A))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = badgeBg,
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }

                                    Text(
                                        text = "${item.currentBitmap.width} × ${item.currentBitmap.height}",
                                        fontSize = 11.sp,
                                        color = Nm.sub,
                                    )
                                }

                                // Delete button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF3F4F6),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { imageItems.remove(item) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("✕", fontSize = 12.sp, color = Nm.sub)
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Image Preview Container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF121018)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = item.currentBitmap.asImageBitmap(),
                                    contentDescription = "图片预览",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                )

                                if (item.isProcessing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Individual Action Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 混淆
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFF1F2),
                                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !item.isProcessing) {
                                            scope.launch { processItem(item, toScramble = true) }
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("混淆", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                                    }
                                }

                                // 解混淆
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFECFDF5),
                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !item.isProcessing) {
                                            scope.launch { processItem(item, toScramble = false) }
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("解混淆", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                    }
                                }

                                // 还原
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF3F4F6),
                                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !item.isProcessing) {
                                            restoreItem(item)
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("还原", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Nm.ink)
                                    }
                                }

                                // 保存到相册
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEDE9FE),
                                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { saveImage(item.currentBitmap) },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("保存相册", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6D28D9))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

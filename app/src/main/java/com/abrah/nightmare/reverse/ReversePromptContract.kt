package com.abrah.nightmare.reverse

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * ⭐⭐⭐ Clean UI ↔ Backend Contract for Reverse Prompting (反推).
 *
 * The UI layer delegates all heavy lifting (model downloading, local GGUF inference,
 * and online VLM API calls) to this interface. Replace [StubReversePromptBackend] with
 * your real implementation when the backend is ready.
 */
enum class ReverseEngineMode { LOCAL, API }
enum class ReverseOutputStyle { MOSS_LAYOUT, NATURAL }

data class LocalModelStatus(
    val modelReady: Boolean = false,
    val mainModelName: String? = null,
    val mainModelBytes: Long = 0L,
    val mmprojName: String? = null,
    val mmprojBytes: Long = 0L,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadDetail: String = "",
)

data class ApiEndpointConfig(
    val providerName: String = "阿里云百炼",
    val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val apiKey: String = "",
    val modelId: String = "qwen3-vl-plus",
)

sealed class ReverseProgress {
    data class Loading(val message: String) : ReverseProgress()
    data class Streaming(val partialText: String) : ReverseProgress()
    data class Success(val resultText: String) : ReverseProgress()
    data class Error(val message: String) : ReverseProgress()
}

interface ReversePromptBackend {
    /** Inspect whether flagship Qwen3-VL-4B main model + mmproj are installed on disk. */
    suspend fun getLocalModelStatus(): LocalModelStatus

    /** Start online download for flagship Qwen3-VL-4B (GGUF + mmproj). */
    fun downloadFlagshipModel(): Flow<LocalModelStatus>

    /** Cancel an active download. */
    fun cancelDownload()

    /** Import a local main .gguf file or mmproj-*.gguf file from SAF Uri. */
    suspend fun importLocalModelFile(uri: Uri, isMmproj: Boolean): LocalModelStatus

    /** Delete local VLM model files to free storage. */
    suspend fun deleteLocalModel(): LocalModelStatus

    /** Test online VLM API connectivity. */
    suspend fun testApiConnection(config: ApiEndpointConfig): Result<String>

    /** Run reverse-prompting inference on [bitmap]. */
    fun runReversePrompt(
        bitmap: Bitmap,
        mode: ReverseEngineMode,
        style: ReverseOutputStyle,
        apiConfig: ApiEndpointConfig,
    ): Flow<ReverseProgress>
}

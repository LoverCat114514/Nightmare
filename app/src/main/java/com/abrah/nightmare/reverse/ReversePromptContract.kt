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

/**
 * One entry of the on-device model chooser. [mainInstalled]/[mmprojInstalled]
 * reflect the canonical GGUF files of that definition on disk, so a chip can
 * show ready / partial / missing before the user commits to it.
 */
data class VlmModelOption(
    val id: String,
    val displayName: String,
    val shortName: String,
    val badge: String,
    val mainInstalled: Boolean = false,
    val mmprojInstalled: Boolean = false,
)

data class LocalModelStatus(
    val modelReady: Boolean = false,
    val mainModelName: String? = null,
    val mainModelBytes: Long = 0L,
    val mmprojName: String? = null,
    val mmprojBytes: Long = 0L,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadDetail: String = "",
    // ⭐ Multi-model era (2026-11-13): which definition is selected and what the
    // chooser can offer. [selectedModelName] is what the model card + header show;
    // when the selected definition's canonical file is missing but an imported
    // GGUF would be loaded instead, it carries that file's name so the UI never
    // lies about what will actually run.
    val selectedModelId: String = "",
    val selectedModelName: String = "",
    val selectedModelShort: String = "",
    val selectedModelBadge: String = "",
    val models: List<VlmModelOption> = emptyList(),
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
    /** Inspect whether the selected on-device VLM (main model + mmproj) is installed. */
    suspend fun getLocalModelStatus(modelId: String): LocalModelStatus

    /** Start online download for the given VLM definition (GGUF + mmproj). */
    fun downloadVlmModel(modelId: String): Flow<LocalModelStatus>

    /** Cancel an active download. */
    fun cancelDownload()

    /** Import a local main .gguf file or mmproj-*.gguf file from SAF Uri. */
    suspend fun importLocalModelFile(uri: Uri, isMmproj: Boolean): LocalModelStatus

    /** Delete local VLM model files to free storage. */
    suspend fun deleteLocalModel(): LocalModelStatus

    /** Test online VLM API connectivity. */
    suspend fun testApiConnection(config: ApiEndpointConfig): Result<String>

    /** Run reverse-prompting inference on [bitmap]. [selectedModelId] picks the LOCAL engine's GGUF. */
    fun runReversePrompt(
        bitmap: Bitmap,
        mode: ReverseEngineMode,
        style: ReverseOutputStyle,
        apiConfig: ApiEndpointConfig,
        selectedModelId: String,
    ): Flow<ReverseProgress>
}
package com.abrah.nightmare.reverse

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * ⭐⭐⭐ The real [ReversePromptBackend] — wires Modules 1/3/4 together.
 *
 *   Module 1  local GGUF storage/download  → [VlmModelManager]
 *   Module 3  online VLM API reverse       → this file (OpenAI-compatible
 *             chat/completions, SSE streaming, image as base64 data URL)
 *   Module 4  prompt templates + JSON sanitizer → [ReversePromptTemplates] /
 *             [MossJsonSanitizer]
 *
 * Module 2 (on-device libvlm_engine.so over llama.cpp) lands separately; the
 * LOCAL mode reports a clear "engine not yet wired" error until then.
 */
class ReversePromptBackendImpl(private val context: Context) : ReversePromptBackend {

    private val TAG = "ReverseBackendImpl"

    // ------------------------------------------------ Module 1: local models

    override suspend fun getLocalModelStatus(): LocalModelStatus =
        withContext(Dispatchers.IO) { VlmModelManager.status(context) }

    override fun downloadFlagshipModel(): Flow<LocalModelStatus> =
        VlmModelManager.downloadFlagshipModel(context)

    override fun cancelDownload() = VlmModelManager.cancelDownload()

    override suspend fun importLocalModelFile(uri: Uri, isMmproj: Boolean): LocalModelStatus =
        withContext(Dispatchers.IO) { VlmModelManager.importLocalModelFile(context, uri, isMmproj) }

    override suspend fun deleteLocalModel(): LocalModelStatus =
        withContext(Dispatchers.IO) { VlmModelManager.deleteLocalModel(context) }

    // ------------------------------------------------ Module 3: online API

    override suspend fun testApiConnection(config: ApiEndpointConfig): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val invalid = validateConfig(config)
                if (invalid != null) return@withContext Result.failure(IllegalArgumentException(invalid))

                val url = joinUrl(config.baseUrl, "/models")
                val conn = openConnection(url, config.apiKey, method = "GET")
                val code: Int
                val body: String
                try {
                    code = conn.responseCode
                    body = readBody(conn, code)
                } finally {
                    conn.disconnect()
                }
                if (code !in 200..299) {
                    return@withContext Result.failure(IOException("HTTP $code：${body.take(200)}"))
                }
                val listed = try {
                    val arr = JSONObject(body).optJSONArray("data")
                    arr != null && (0 until arr.length()).any {
                        arr.optJSONObject(it)?.optString("id") == config.modelId
                    }
                } catch (t: Throwable) {
                    Log.i(TAG, "/models response not a JSON model list — skipping id check")
                    false
                }
                if (listed) Result.success("连接成功，模型 ${config.modelId} 可用")
                else Result.success("连接成功（HTTP $code）。/models 列表中未确认 ${config.modelId}，仍可直接尝试反推")
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    override fun runReversePrompt(
        bitmap: Bitmap,
        mode: ReverseEngineMode,
        style: ReverseOutputStyle,
        apiConfig: ApiEndpointConfig,
    ): Flow<ReverseProgress> = flow {
        when (mode) {
            ReverseEngineMode.API -> runApiReverse(bitmap, style, apiConfig) { emit(it) }

            ReverseEngineMode.LOCAL -> {
                // Module 2 (libvlm_engine.so / llama.cpp JNI) is not wired yet.
                val st = VlmModelManager.status(context)
                val missing = buildList {
                    if (st.mainModelName == null) add("主模型")
                    if (st.mmprojName == null) add("mmproj 视觉投影")
                }
                val msg = when {
                    missing.isNotEmpty() ->
                        "端侧推理引擎尚未接入，且缺少：${missing.joinToString("、")}。" +
                            "请先下载或导入模型文件（引擎就绪前请使用「在线 API」模式）"
                    else ->
                        "模型文件已就绪（${st.mainModelName}），但端侧推理引擎（libvlm_engine.so）尚未接入。" +
                            "请使用「在线 API」模式"
                }
                emit(ReverseProgress.Error(msg))
            }
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------ API internals

    private suspend fun runApiReverse(
        bitmap: Bitmap,
        style: ReverseOutputStyle,
        config: ApiEndpointConfig,
        onProgress: suspend (ReverseProgress) -> Unit,
    ) {
        val invalid = validateConfig(config)
        if (invalid != null) {
            onProgress(ReverseProgress.Error(invalid))
            return
        }

        onProgress(ReverseProgress.Loading("正在压缩图片..."))
        val dataUrl = try {
            encodeBitmapDataUrl(bitmap, maxSide = 1280, quality = 88)
        } catch (t: Throwable) {
            Log.w(TAG, "bitmap encode failed", t)
            onProgress(ReverseProgress.Error("图片处理失败：${t.message ?: t.javaClass.simpleName}"))
            return
        }

        val system = if (style == ReverseOutputStyle.MOSS_LAYOUT) {
            ReversePromptTemplates.MOSS_LAYOUT
        } else {
            ReversePromptTemplates.NATURAL
        }
        val userText = if (style == ReverseOutputStyle.MOSS_LAYOUT) {
            "Analyse this image and output the layout JSON now."
        } else {
            "Reverse-engineer the full drawing prompt for this image now."
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(
                JSONObject().put("role", "user").put(
                    "content",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", dataUrl))
                        )
                        .put(JSONObject().put("type", "text").put("text", userText))
                )
            )

        val payload = JSONObject()
            .put("model", config.modelId)
            .put("messages", messages)
            .put("stream", true)
            .put("max_tokens", 2048)

        onProgress(ReverseProgress.Loading("正在请求 ${config.providerName}（${config.modelId}）..."))
        val full = StringBuilder()
        try {
            streamChatCompletion(config, payload) { delta ->
                if (delta.isNotEmpty()) {
                    full.append(delta)
                    onProgress(ReverseProgress.Streaming(full.toString()))
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "chat/completions failed", t)
            onProgress(ReverseProgress.Error("API 请求失败：${t.message ?: t.javaClass.simpleName}"))
            return
        }

        val raw = full.toString().trim()
        if (raw.isEmpty()) {
            onProgress(ReverseProgress.Error("API 返回为空（模型可能拒绝了请求，请检查 Key 额度/模型权限）"))
            return
        }

        if (style == ReverseOutputStyle.MOSS_LAYOUT) {
            onProgress(ReverseProgress.Loading("正在校验/修复布局 JSON..."))
            val cleaned = MossJsonSanitizer.stripThinking(raw)
            val sanitized = MossJsonSanitizer.sanitize(cleaned)
            if (sanitized == null) {
                onProgress(
                    ReverseProgress.Error(
                        "模型未返回可解析的布局 JSON（已尝试自动纠偏）。原始输出前 300 字：\n${cleaned.take(300)}"
                    )
                )
                return
            }
            onProgress(ReverseProgress.Success(sanitized))
        } else {
            onProgress(ReverseProgress.Success(MossJsonSanitizer.stripThinking(raw)))
        }
    }

    /**
     * POSTs {baseUrl}/chat/completions with stream=true and parses the SSE
     * `data:` lines, forwarding every delta.content. Falls back to parsing the
     * whole body as a normal JSON completion when the gateway ignores
     * stream=true and replies with application/json.
     */
    private suspend fun streamChatCompletion(
        config: ApiEndpointConfig,
        payload: JSONObject,
        onDelta: suspend (String) -> Unit,
    ) {
        val url = joinUrl(config.baseUrl, "/chat/completions")
        val conn = openConnection(url, config.apiKey, method = "POST")
        try {
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = readBody(conn, code)
                throw IOException("HTTP $code：${err.take(300)}")
            }

            val contentType = (conn.contentType ?: "").lowercase()
            if (contentType.contains("application/json")) {
                // Gateway ignored stream=true — parse as a plain completion.
                val body = readBody(conn, code)
                val content = JSONObject(body)
                    .optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")
                    ?: throw IOException("无法解析响应：${body.take(200)}")
                onDelta(content)
                return
            }

            conn.inputStream.use { input ->
                BufferedReader(input.reader(Charsets.UTF_8)).useLines { lines ->
                    for (line in lines) {
                        val t = line.trim()
                        if (t.isEmpty() || t.startsWith(":")) continue
                        if (!t.startsWith("data:")) continue
                        val data = t.removePrefix("data:").trim()
                        if (data == "[DONE]") return
                        val delta = try {
                            val obj = JSONObject(data)
                            val err = obj.optJSONObject("error")
                            if (err != null) {
                                throw IOException("流式响应错误：${err.optString("message", data.take(200))}")
                            }
                            obj.optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("delta")?.optString("content")
                        } catch (e: IOException) {
                            throw e
                        } catch (t: Throwable) {
                            Log.i(TAG, "unparseable SSE chunk: $data")
                            null
                        } ?: continue
                        if (delta.isNotEmpty()) onDelta(delta)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // ------------------------------------------------ helpers

    private fun validateConfig(config: ApiEndpointConfig): String? = when {
        config.baseUrl.isBlank() -> "请先填写 Base URL"
        config.apiKey.isBlank() -> "请先填写 API Key"
        config.modelId.isBlank() -> "请先填写模型名称"
        else -> null
    }

    private fun joinUrl(base: String, path: String): String =
        base.trimEnd('/') + path

    private fun openConnection(url: String, apiKey: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 300_000 // VLM generations are slow; SSE keeps it alive
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
        }

    private fun readBody(conn: HttpURLConnection, code: Int): String {
        val input: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
        return input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }

    /** Scales long edge to <= [maxSide] and JPEG-encodes into a base64 data URL. */
    private fun encodeBitmapDataUrl(src: Bitmap, maxSide: Int, quality: Int): String {
        var bmp = src
        var recycled = false
        val longest = maxOf(src.width, src.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            bmp = Bitmap.createScaledBitmap(src, w, h, true)
            recycled = bmp !== src
        }
        try {
            val baos = ByteArrayOutputStream()
            Bitmap.CompressFormat.JPEG.let { bmp.compress(it, quality, baos) }
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            Log.i(TAG, "image encoded: ${bmp.width}x${bmp.height}, ${b64.length} b64 chars")
            return "data:image/jpeg;base64,$b64"
        } finally {
            if (recycled) bmp.recycle()
        }
    }
}
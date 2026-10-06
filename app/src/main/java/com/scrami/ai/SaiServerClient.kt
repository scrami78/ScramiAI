package com.scrami.ai

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64

data class SaiChatSource(val title: String, val url: String)
data class SaiChatResult(val text: String, val model: String, val device: String, val sources: List<SaiChatSource> = emptyList())
data class SaiImageResult(val base64: String, val model: String, val device: String)
data class SaiSearchItem(val title: String, val url: String, val snippet: String)

object SaiServerClient {
    private val client = OkHttpClient.Builder().build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun chat(baseUrl: String, message: String, history: String, memory: String, mode: String): SaiChatResult {
        val payload = JSONObject()
            .put("message", message)
            .put("history", history)
            .put("memory", memory)
            .put("mode", mode)
            .put("use_web", true)
            .put("max_tokens", if (mode == "FAST") 450 else 1200)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(JSONObject(body).optString("detail", "S.AI Core HTTP " + response.code))
            val json = JSONObject(body)
            return SaiChatResult(
                json.optString("text", ""),
                json.optString("model", "S.AI Core"),
                json.optString("device", "local"),
                (0 until (json.optJSONArray("sources")?.length() ?: 0)).map { i ->
                    val s = json.getJSONArray("sources").getJSONObject(i)
                    SaiChatSource(s.optString("title"), s.optString("url"))
                }
            )
        }
    }


    fun search(baseUrl: String, query: String): List<SaiSearchItem> {
        val payload = JSONObject().put("query", query)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/search")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Search HTTP " + response.code)
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SaiSearchItem(o.optString("title"), o.optString("url"), o.optString("snippet"))
            }
        }
    }

    fun image(baseUrl: String, prompt: String): SaiImageResult {
        val payload = JSONObject()
            .put("prompt", prompt)
            .put("width", 768)
            .put("height", 768)
            .put("steps", 25)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/image")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(JSONObject(body).optString("detail", "Image generation failed"))
            val json = JSONObject(body)
            return SaiImageResult(
                json.optString("image_base64", ""),
                json.optString("model", "S.AI Image"),
                json.optString("device", "local")
            )
        }
    }

    fun savePng(context: Context, base64: String): Uri {
        val bytes = Base64.getDecoder().decode(base64)
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SAI_" + System.currentTimeMillis() + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/S.AI")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create image file")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Cannot write image")
        return uri
    }
}

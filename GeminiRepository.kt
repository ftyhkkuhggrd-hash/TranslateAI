package com.translateai.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GroundingLink(val title: String, val url: String)
data class GeminiReply(val answer: String, val links: List<GroundingLink>)

class GeminiRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    suspend fun ask(\n        apiKey: String,\n        question: String,\n        useGoogleSearch: Boolean,\n        history: List<Pair<String, String>> = emptyList()\n    ): GeminiReply {
        require(apiKey.isNotBlank()) { "ไม่พบ Gemini API key กรุณาเปิดตั้งค่า" }
        require(question.isNotBlank()) { "กรุณาพิมพ์คำถามก่อนส่ง" }

        val model = "gemini-2.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            .toHttpUrl().newBuilder()
            .addQueryParameter("key", apiKey)
            .build()

        val contents = JSONArray()
        history.takeLast(12).forEach { (oldQuestion, oldAnswer) ->
            if (oldQuestion.isNotBlank() && oldAnswer.isNotBlank()) {
                contents.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", oldQuestion)))
                )
                contents.put(
                    JSONObject()
                        .put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", oldAnswer)))
                )
            }
        }
        contents.put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", question)))
        )

        val body = JSONObject().put("contents", contents)
        body.put(
            "generationConfig",
            JSONObject().put("temperature", 0.6).put("maxOutputTokens", 2048)
        )
        if (useGoogleSearch) {
            body.put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBodyCompat())
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                } catch (_: Exception) { null }
                throw IOException(
                    when (response.code) {
                        400 -> "คำขอไม่ถูกต้อง: ${message ?: "ตรวจสอบ API key หรือรุ่นโมเดล"}"
                        403 -> "API key ไม่มีสิทธิ์ใช้งาน หรือเปิด Gemini API ไม่ครบ"
                        429 -> "เรียกใช้งานเกินโควตาชั่วคราว กรุณารอสักครู่"
                        else -> "เชื่อมต่อ Gemini ไม่สำเร็จ (HTTP ${response.code}): ${message ?: "ลองใหม่อีกครั้ง"}"
                    }
                )
            }

            val json = JSONObject(responseText)
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw IOException("AI ไม่ส่งผลลัพธ์กลับมา")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val answer = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }.ifBlank { "AI ส่งคำตอบว่างกลับมา กรุณาลองถามอีกครั้ง" }

            val links = mutableListOf<GroundingLink>()
            val chunks = candidate.optJSONObject("groundingMetadata")
                ?.optJSONArray("groundingChunks") ?: JSONArray()
            val seen = mutableSetOf<String>()
            for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val uri = web.optString("uri").trim()
                val title = web.optString("title").trim()
                if (isValidWebUrl(uri) && seen.add(uri)) {
                    links += GroundingLink(title.ifBlank { uri }, uri)
                }
            }
            return GeminiReply(answer, links.take(10))
        }
    }

    private fun isValidWebUrl(value: String): Boolean {
        return try {
            val uri = java.net.URI(value)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !uri.host.isNullOrBlank()
        } catch (_: Exception) { false }
    }
}

private fun String.toRequestBodyCompat(): okhttp3.RequestBody =
    toByteArray(Charsets.UTF_8).toRequestBody(
        okhttp3.MediaType.Companion.toMediaType("application/json; charset=utf-8")
    )

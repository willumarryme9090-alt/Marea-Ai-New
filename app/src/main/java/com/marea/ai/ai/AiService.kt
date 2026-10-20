package com.marea.ai.ai

import com.marea.ai.data.AppSettings
import com.marea.ai.data.ChatMessage
import com.marea.ai.data.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** The UI only knows this interface, never a specific provider. */
interface AiService {
    suspend fun reply(history: List<ChatMessage>): Result<String>
}

class AiException(message: String) : Exception(message)

/** Used when nothing is configured: fails honestly instead of faking a reply. */
class UnconfiguredAiService : AiService {
    override suspend fun reply(history: List<ChatMessage>): Result<String> =
        Result.failure(
            AiException(
                "No AI provider is configured. Open Settings → AI connection and enter " +
                    "an HTTPS base URL, model name and (if required) API key."
            )
        )
}

/**
 * Talks to any server exposing the widely used "chat completions" style API:
 *   POST {baseUrl}/chat/completions
 *   Authorization: Bearer <key>   (only sent if a key is entered)
 *   { "model": "...", "messages": [{ "role": "...", "content": "..." }] }
 *   -> { "choices": [ { "message": { "content": "..." } } ] }
 * The user supplies base URL, model and key; nothing is hard-coded. Check your
 * provider's own documentation for the exact values.
 */
class ChatCompletionsService(private val settings: AppSettings) : AiService {

    override suspend fun reply(history: List<ChatMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            val base = settings.baseUrl.trim().trimEnd('/')
            if (!base.startsWith("https://")) {
                return@withContext Result.failure(AiException("The base URL must start with https://"))
            }
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (settings.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
                    }
                }
                conn.outputStream.use { it.write(buildRequestBody(settings, history).toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val hint = when (code) {
                        401, 403 -> "Authentication failed. Check your API key."
                        404 -> "Endpoint not found. Check the base URL and model."
                        429 -> "Rate limit reached. Try again later."
                        else -> "Server returned HTTP $code."
                    }
                    Result.failure(AiException(hint))
                } else {
                    parseReply(text)
                }
            } catch (e: SocketTimeoutException) {
                Result.failure(AiException("The request timed out."))
            } catch (e: UnknownHostException) {
                Result.failure(AiException("Cannot reach the server. Check your internet connection."))
            } catch (e: java.io.IOException) {
                Result.failure(AiException("Network error: ${e.message ?: "unknown"}"))
            } finally {
                conn?.disconnect()
            }
        }

    companion object {
        private const val MAX_CONTEXT = 20

        fun buildRequestBody(settings: AppSettings, history: List<ChatMessage>): String {
            val msgs = JSONArray()
            if (settings.systemPrompt.isNotBlank()) {
                msgs.put(JSONObject().put("role", "system").put("content", settings.systemPrompt))
            }
            history.takeLast(MAX_CONTEXT).forEach {
                msgs.put(
                    JSONObject()
                        .put("role", if (it.role == Role.USER) "user" else "assistant")
                        .put("content", it.text)
                )
            }
            return JSONObject().put("model", settings.model).put("messages", msgs).toString()
        }

        fun parseReply(body: String): Result<String> = try {
            val content = JSONObject(body)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (content.isEmpty()) Result.failure(AiException("The AI returned an empty reply."))
            else Result.success(content)
        } catch (e: Exception) {
            Result.failure(AiException("The server's response was not in the expected format."))
        }
    }
}

fun createAiService(settings: AppSettings): AiService =
    if (settings.aiConfigured) ChatCompletionsService(settings) else UnconfiguredAiService()

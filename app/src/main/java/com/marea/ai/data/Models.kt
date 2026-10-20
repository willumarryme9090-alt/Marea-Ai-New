package com.marea.ai.data

enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Accent { BURGUNDY, PURPLE }

/** All user-configurable settings. Nothing here has a built-in provider or key. */
data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val systemPrompt: String = "You are Marea, a friendly and concise voice assistant.",
    val speakReplies: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val blinkEnabled: Boolean = true,
    val mouthAnimEnabled: Boolean = true,
    val accent: Accent = Accent.BURGUNDY,
    val saveHistory: Boolean = true
) {
    val aiConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()
}

/** Returns trimmed text, or null when the message is empty. */
fun normalizeInput(raw: String): String? = raw.trim().ifEmpty { null }

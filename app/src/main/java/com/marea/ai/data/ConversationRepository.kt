package com.marea.ai.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Plain local conversation history stored as one JSON file in app-private storage.
 * This is ordinary chat history, not "AI memory".
 */
class ConversationRepository(private val file: File, private val maxMessages: Int = 200) {

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    role = Role.valueOf(o.getString("role")),
                    text = o.getString("text"),
                    timestamp = o.optLong("ts", 0L)
                )
            }
        } catch (e: Exception) {
            // Corrupted file: start clean instead of crashing.
            file.delete()
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(maxMessages).forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("role", it.role.name)
                    .put("text", it.text)
                    .put("ts", it.timestamp)
            )
        }
        // Write to a temp file first so a crash cannot corrupt existing history.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }
}

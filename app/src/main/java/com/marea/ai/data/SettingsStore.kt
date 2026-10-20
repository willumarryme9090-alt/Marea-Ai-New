package com.marea.ai.data

import android.content.Context

/**
 * Settings in app-private SharedPreferences. The API key is stored here on the device
 * (not in source code or the APK). Backups are disabled in the manifest.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("marea_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            baseUrl = prefs.getString("baseUrl", d.baseUrl) ?: d.baseUrl,
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            model = prefs.getString("model", d.model) ?: d.model,
            systemPrompt = prefs.getString("systemPrompt", d.systemPrompt) ?: d.systemPrompt,
            speakReplies = prefs.getBoolean("speakReplies", d.speakReplies),
            speechRate = prefs.getFloat("speechRate", d.speechRate),
            speechPitch = prefs.getFloat("speechPitch", d.speechPitch),
            blinkEnabled = prefs.getBoolean("blinkEnabled", d.blinkEnabled),
            mouthAnimEnabled = prefs.getBoolean("mouthAnimEnabled", d.mouthAnimEnabled),
            accent = runCatching { Accent.valueOf(prefs.getString("accent", d.accent.name)!!) }
                .getOrDefault(d.accent),
            saveHistory = prefs.getBoolean("saveHistory", d.saveHistory)
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("baseUrl", s.baseUrl)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("systemPrompt", s.systemPrompt)
            .putBoolean("speakReplies", s.speakReplies)
            .putFloat("speechRate", s.speechRate)
            .putFloat("speechPitch", s.speechPitch)
            .putBoolean("blinkEnabled", s.blinkEnabled)
            .putBoolean("mouthAnimEnabled", s.mouthAnimEnabled)
            .putString("accent", s.accent.name)
            .putBoolean("saveHistory", s.saveHistory)
            .apply()
    }
}

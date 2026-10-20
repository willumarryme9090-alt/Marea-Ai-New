package com.marea.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marea.ai.ai.createAiService
import com.marea.ai.avatar.AvatarState
import com.marea.ai.avatar.resolveAvatarState
import com.marea.ai.data.AppSettings
import com.marea.ai.data.ChatMessage
import com.marea.ai.data.ConversationRepository
import com.marea.ai.data.Role
import com.marea.ai.data.SettingsStore
import com.marea.ai.data.normalizeInput
import com.marea.ai.voice.AndroidTtsOutput
import com.marea.ai.voice.SpeechInput
import com.marea.ai.voice.SpeechOutput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val listening: Boolean = false,
    val thinking: Boolean = false,
    val speaking: Boolean = false,
    val error: String? = null,
    val foreground: Boolean = true
) {
    val avatarState: AvatarState
        get() = resolveAvatarState(listening, thinking, speaking, error != null)
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val repo = ConversationRepository(File(app.filesDir, "history.json"))

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var requestJob: Job? = null
    private var requestId = 0L
    private var nextMessageId = 1L

    private val speechOut: SpeechOutput = AndroidTtsOutput(app, object : SpeechOutput.Callback {
        override fun onSpeechStart() = _ui.update { it.copy(speaking = true) }
        override fun onSpeechDone() = _ui.update { it.copy(speaking = false) }
        override fun onSpeechError(message: String) =
            _ui.update { it.copy(speaking = false, error = message) }
    })

    private val speechIn = SpeechInput(app, object : SpeechInput.Callback {
        override fun onListening() = _ui.update { it.copy(listening = true, error = null) }
        override fun onText(text: String) = send(text)
        override fun onError(message: String) = _ui.update { it.copy(listening = false, error = message) }
        override fun onStopped() = _ui.update { it.copy(listening = false) }
    })

    init {
        if (_settings.value.saveHistory) {
            val loaded = repo.load()
            nextMessageId = (loaded.maxOfOrNull { it.id } ?: 0L) + 1
            _ui.update { it.copy(messages = loaded) }
        }
    }

    // ---- Chat ------------------------------------------------------------

    fun send(raw: String) {
        val text = normalizeInput(raw) ?: return          // empty message
        if (_ui.value.thinking) return                    // duplicate submission
        speechOut.stop()
        speechIn.stop()

        val userMsg = ChatMessage(nextMessageId++, Role.USER, text)
        val history = _ui.value.messages + userMsg
        _ui.update { it.copy(messages = history, thinking = true, speaking = false, error = null) }
        persist(history)

        val myId = ++requestId
        val service = createAiService(_settings.value)
        requestJob = viewModelScope.launch {
            val result = service.reply(history)
            if (myId != requestId) return@launch        // stale response: ignore
            result.fold(
                onSuccess = { reply ->
                    val msgs = _ui.value.messages + ChatMessage(nextMessageId++, Role.ASSISTANT, reply)
                    _ui.update { it.copy(messages = msgs, thinking = false) }
                    persist(msgs)
                    if (_settings.value.speakReplies) {
                        val s = _settings.value
                        speechOut.speak(reply, s.speechRate, s.speechPitch)
                    }
                },
                onFailure = { e ->
                    _ui.update { it.copy(thinking = false, error = e.message ?: "Something went wrong.") }
                }
            )
        }
    }

    /** Cancels a pending request and any speech, returning the avatar to neutral. */
    fun stopAll() {
        requestId++ // invalidate in-flight request
        requestJob?.cancel()
        speechOut.stop()
        speechIn.stop()
        _ui.update { it.copy(thinking = false, speaking = false, listening = false) }
    }

    // ---- Voice -----------------------------------------------------------

    /** Call only after RECORD_AUDIO has been granted. */
    fun startListening() {
        if (_ui.value.thinking) return
        speechOut.stop()
        _ui.update { it.copy(speaking = false, error = null) }
        speechIn.start()
    }

    fun stopListening() = speechIn.stop()

    /** Lets the user preview the current voice settings. */
    fun speakSample() {
        if (_ui.value.thinking) return
        val s = _settings.value
        speechOut.speak("Hello, I'm Marea. This is how I sound.", s.speechRate, s.speechPitch)
    }

    fun reportError(message: String) = _ui.update { it.copy(error = message) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    // ---- Lifecycle -------------------------------------------------------

    fun onForeground() = _ui.update { it.copy(foreground = true) }

    /** App no longer visible: stop mic and speech, pause animations. */
    fun onBackground() {
        speechIn.stop()
        speechOut.stop()
        _ui.update { it.copy(foreground = false, listening = false, speaking = false) }
    }

    // ---- Settings / history ---------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        settingsStore.save(new)
        if (old.saveHistory && !new.saveHistory) repo.clear()
        if (!old.saveHistory && new.saveHistory) persist(_ui.value.messages)
    }

    fun clearHistory() {
        stopAll()
        repo.clear()
        _ui.update { it.copy(messages = emptyList(), error = null) }
    }

    private fun persist(messages: List<ChatMessage>) {
        if (_settings.value.saveHistory) runCatching { repo.save(messages) }
    }

    override fun onCleared() {
        speechIn.release()
        speechOut.release()
        super.onCleared()
    }
}

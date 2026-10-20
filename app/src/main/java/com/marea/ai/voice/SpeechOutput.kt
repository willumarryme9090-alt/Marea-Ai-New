package com.marea.ai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Interface so an optional cloud TTS provider can be added later without touching the UI. */
interface SpeechOutput {
    interface Callback {
        fun onSpeechStart()
        fun onSpeechDone()
        fun onSpeechError(message: String)
    }

    fun speak(text: String, rate: Float, pitch: Float)
    fun stop()
    fun release()
}

/** Android TextToSpeech implementation. Callbacks are always delivered on the main thread. */
class AndroidTtsOutput(context: Context, private val cb: SpeechOutput.Callback) : SpeechOutput {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initError: String? = null
    private var pending: Triple<String, Float, Float>? = null
    private var currentId = 0
    private var released = false

    init {
        tts = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }
    }

    private fun onInit(status: Int) {
        if (released) return
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            initError = "Text-to-speech could not start on this device."
        } else {
            val r = engine.setLanguage(Locale.getDefault())
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                initError = "No text-to-speech voice is installed for ${Locale.getDefault().displayLanguage}. " +
                    "Install one in your phone's TTS settings."
            } else {
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post { if (utteranceId == idString(currentId)) cb.onSpeechStart() }
                    }

                    override fun onDone(utteranceId: String?) {
                        main.post { if (utteranceId == idString(currentId)) cb.onSpeechDone() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post {
                            if (utteranceId == idString(currentId)) cb.onSpeechError("Speech playback failed.")
                        }
                    }
                })
                ready = true
            }
        }
        val p = pending
        pending = null
        if (p != null) speak(p.first, p.second, p.third)
    }

    private fun idString(i: Int) = "marea-$i"

    override fun speak(text: String, rate: Float, pitch: Float) {
        initError?.let { cb.onSpeechError(it); return }
        val engine = tts
        if (!ready || engine == null) {
            pending = Triple(text, rate, pitch) // still initializing
            return
        }
        currentId++ // invalidates callbacks of any previous utterance -> no overlap
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        val safe = text.take(TextToSpeech.getMaxSpeechInputLength() - 1)
        val result = engine.speak(safe, TextToSpeech.QUEUE_FLUSH, null, idString(currentId))
        if (result != TextToSpeech.SUCCESS) cb.onSpeechError("Speech playback could not start.")
    }

    override fun stop() {
        pending = null
        currentId++ // ignore late callbacks from the cancelled utterance
        runCatching { tts?.stop() }
    }

    override fun release() {
        released = true
        pending = null
        currentId++
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }
}

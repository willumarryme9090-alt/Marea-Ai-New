package com.marea.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wrapper around Android SpeechRecognizer. Listens ONLY between start() and the
 * result/error/stop(). Must be used from the main thread.
 */
class SpeechInput(private val context: Context, private val cb: Callback) {

    interface Callback {
        fun onListening()
        fun onText(text: String)
        fun onError(message: String)
        fun onStopped()
    }

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) {
            cb.onError("Speech recognition is not available on this device. You can type instead.")
            return
        }
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = cb.onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                release()
                cb.onStopped()
                if (text.isEmpty()) cb.onError("I didn't catch that. Please try again.") else cb.onText(text)
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_CLIENT) return // caused by our own cancel()
                release()
                cb.onStopped()
                cb.onError(
                    when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
                        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection right now."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
                        else -> "Speech recognition failed (code $error)."
                    }
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    /** Stops listening and frees the recognizer. Safe to call anytime. */
    fun stop() {
        val had = recognizer != null
        release()
        if (had) cb.onStopped()
    }

    fun release() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }
}

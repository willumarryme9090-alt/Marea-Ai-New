package com.marea.ai.avatar

/**
 * Avatar state model. States come ONLY from real app events:
 *  - LISTENING : SpeechRecognizer reported it is ready for speech
 *  - THINKING  : an AI request is in flight
 *  - SPEAKING  : TextToSpeech reported utterance start (until done/stop/error)
 *  - ERROR     : the last operation failed and nothing else is active
 *  - IDLE      : none of the above (neutral reset)
 */
enum class AvatarState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

/** Priority when several flags overlap: listening > speaking > thinking > error > idle. */
fun resolveAvatarState(
    listening: Boolean,
    thinking: Boolean,
    speaking: Boolean,
    error: Boolean
): AvatarState = when {
    listening -> AvatarState.LISTENING
    speaking -> AvatarState.SPEAKING
    thinking -> AvatarState.THINKING
    error -> AvatarState.ERROR
    else -> AvatarState.IDLE
}

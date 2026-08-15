package com.preappointment1.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

data class VoiceSessionState(
    val state: VoiceState = VoiceState.IDLE,
    val text: String = "",
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null
)

class VoiceManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _sessionState = MutableStateFlow(VoiceSessionState())
    val sessionState: StateFlow<VoiceSessionState> = _sessionState.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.LISTENING,
                errorMessage = null
            )
        }

        override fun onBeginningOfSpeech() {
            _sessionState.value = _sessionState.value.copy(state = VoiceState.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize RMS level for visual animation (typically -2dB to 10dB)
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _sessionState.value = _sessionState.value.copy(rmsLevel = normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.PROCESSING,
                rmsLevel = 0f
            )
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out. Tap mic to speak again."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_NETWORK -> "Network issue for speech recognition."
                else -> "Voice recognition error (code $error)"
            }
            Log.w("VoiceManager", "Speech recognition error: $msg")
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.ERROR,
                errorMessage = msg,
                rmsLevel = 0f
            )
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: _sessionState.value.text
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.SUCCESS,
                text = recognizedText,
                rmsLevel = 0f
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _sessionState.value = _sessionState.value.copy(
                    text = partial,
                    state = VoiceState.LISTENING
                )
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.ERROR,
                errorMessage = "Speech recognition is not available on this device."
            )
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            _sessionState.value = VoiceSessionState(state = VoiceState.LISTENING, text = "")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to start speech recognizer", e)
            _sessionState.value = _sessionState.value.copy(
                state = VoiceState.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to start voice recognition"
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error stopping speech recognizer", e)
        }
    }

    fun reset() {
        stopListening()
        _sessionState.value = VoiceSessionState(state = VoiceState.IDLE)
    }

    fun setManualText(text: String) {
        _sessionState.value = _sessionState.value.copy(
            text = text,
            state = if (text.isNotBlank()) VoiceState.SUCCESS else VoiceState.IDLE
        )
    }
}

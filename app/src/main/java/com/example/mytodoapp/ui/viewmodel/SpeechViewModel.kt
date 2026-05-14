package com.example.mytodoapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytodoapp.utils.AiHelper
import com.example.mytodoapp.utils.RewriteMode
import com.example.mytodoapp.utils.SpeechRecognitionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpeechUiState(
    val isListening: Boolean = false,
    val isPaused: Boolean = false,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val error: String? = null,
    val isProcessingAi: Boolean = false,
    val rmsdB: Float = 0f
)

class SpeechViewModel(
    private val speechManager: SpeechRecognitionManager
) : ViewModel(), SpeechRecognitionManager.SpeechRecognitionListener {

    private val _uiState = MutableStateFlow(SpeechUiState())
    val uiState: StateFlow<SpeechUiState> = _uiState.asStateFlow()

    init {
        speechManager.setListener(this)
    }

    fun startListening() {
        // Always reset for a fresh start or "Record Again"
        _uiState.value = SpeechUiState(isListening = true)
        speechManager.startListening()
    }

    fun stopListening() {
        speechManager.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    fun pauseListening() {
        speechManager.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false, isPaused = true)
    }

    fun resumeListening() {
        _uiState.value = _uiState.value.copy(isListening = true, isPaused = false, error = null)
        speechManager.startListening()
    }

    fun reset() {
        _uiState.value = SpeechUiState()
        speechManager.cancelListening()
    }

    fun rewriteWithAi(text: String, mode: RewriteMode = RewriteMode.DEFAULT) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingAi = true, error = null)
            val result = AiHelper.rewriteText(text, mode)
            if (result.startsWith("Error:")) {
                _uiState.value = _uiState.value.copy(
                    isProcessingAi = false,
                    error = result.removePrefix("Error:").trim()
                )
                // Auto-clear AI error after 2 seconds to reshow original text
                kotlinx.coroutines.delay(2000)
                _uiState.value = _uiState.value.copy(error = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessingAi = false,
                    finalTranscript = result
                )
            }
        }
    }

    override fun onReadyForSpeech() {
        _uiState.value = _uiState.value.copy(isListening = true, error = null)
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {
        _uiState.value = _uiState.value.copy(rmsdB = rmsdB)
    }

    override fun onPartialResults(partialText: String) {
        _uiState.value = _uiState.value.copy(partialTranscript = partialText)
    }

    override fun onResults(finalText: String) {
        val currentFinal = _uiState.value.finalTranscript
        val newText = if (currentFinal.isEmpty()) finalText else "$currentFinal $finalText".trim()

        _uiState.value = _uiState.value.copy(
            isListening = false,
            partialTranscript = "",
            finalTranscript = newText
        )
    }

    override fun onError(error: String) {
        _uiState.value = _uiState.value.copy(isListening = false, error = error)
    }

    override fun onEndOfSpeech() {
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}

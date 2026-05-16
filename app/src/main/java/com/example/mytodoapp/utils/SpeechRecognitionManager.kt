package com.example.mytodoapp.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Manager class to handle Android SpeechRecognizer logic.
 */
class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private var listener: SpeechRecognitionListener? = null

    interface SpeechRecognitionListener {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onRmsChanged(rmsdB: Float)
        fun onPartialResults(partialText: String)
        fun onResults(finalText: String)
        fun onError(error: String)
        fun onEndOfSpeech()
    }

    fun setListener(listener: SpeechRecognitionListener) {
        this.listener = listener
    }

    fun startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Some devices need this to work better
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            listener?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            listener?.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            listener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                10 -> "Too many requests"
                11 -> "Server disconnected"
                12 -> "Language not supported"
                13 -> "Offline language model unavailable. Please download it in Google settings."
                14 -> "Cannot check offline support"
                else -> "Unknown error: $error"
            }
            listener?.onError(errorMessage)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                listener?.onResults(matches[0])
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                listener?.onPartialResults(matches[0])
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
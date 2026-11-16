
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidCommandRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val languages = listOf("hi-IN", "en-IN", "or-IN")
    private var languageIndex = 0
    private var multilingual = false
    private var finished = false

    fun start(language: String = "hi-IN") {
        multilingual = false
        languageIndex = 0
        finished = false
        startInternal(language)
    }

    fun startMultilingual() {
        multilingual = true
        languageIndex = 0
        finished = false
        startInternal(languages[languageIndex])
    }

    private fun startInternal(language: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { startInternal(language) }
            return
        }

        if (finished) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finishWithError("Speech recognition service उपलब्ध नहीं है।")
            return
        }

        destroyInternal()

        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onError(error: Int) {
                if (finished) return

                val retryable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (multilingual && retryable && languageIndex < languages.lastIndex) {
                    languageIndex++
                    handler.postDelayed({
                        if (!finished) {
                            startInternal(languages[languageIndex])
                        }
                    }, 250)
                } else {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "आवाज़ समझ नहीं आई।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "आपकी आवाज़ सुनाई नहीं दी।"
                        SpeechRecognizer.ERROR_NETWORK ->
                            "Voice recognition network error."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Voice recognition timeout."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission नहीं मिली।"
                        else ->
                            "Voice recognition error: $error"
                    }
                    finishWithError(message)
                }
            }

            override fun onResults(results: Bundle?) {
                if (finished) return

                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val text = matches?.firstOrNull()?.trim().orEmpty()

                if (text.isNotBlank()) {
                    finished = true
                    onResult(text)
                } else {
                    onError(SpeechRecognizer.ERROR_NO_MATCH)
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        try {
            speech.startListening(intent)
        } catch (e: Exception) {
            finishWithError(e.message ?: "Voice recognition शुरू नहीं हुआ।")
        }
    }

    private fun finishWithError(message: String) {
        if (finished) return
        finished = true
        destroyInternal()
        onError(message)
    }

    fun destroy() {
        finished = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyInternal()
        } else {
            handler.post { destroyInternal() }
        }
    }

    private fun destroyInternal() {
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }
}

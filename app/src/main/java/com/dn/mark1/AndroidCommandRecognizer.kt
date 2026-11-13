
package com.dn.mark1

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    fun start(language: String = "hi-IN") {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            android.os.Handler(Looper.getMainLooper()).post {
                start(language)
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service available nahi hai.")
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

            override fun onError(error: Int) {
                onError("Voice recognition error: $error")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val text = matches?.firstOrNull()?.trim().orEmpty()

                if (text.isNotEmpty()) {
                    onResult(text)
                } else {
                    onError("Aapki awaaz samajh nahi aayi.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
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

        speech.startListening(intent)
    }

    fun destroy() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyInternal()
        } else {
            android.os.Handler(Looper.getMainLooper()).post {
                destroyInternal()
            }
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


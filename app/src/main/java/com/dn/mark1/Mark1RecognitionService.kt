
package com.dn.mark1

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class Mark1RecognitionService : RecognitionService() {

    private var recognizer: SpeechRecognizer? = null

    override fun onStartListening(
        recognizerIntent: Intent?,
        listener: Callback?
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    listener?.results(results ?: Bundle())
                    recognizer?.destroy()
                    recognizer = null
                }

                override fun onError(error: Int) {
                    listener?.error(error)
                    recognizer?.destroy()
                    recognizer = null
                }
            }
        )

        recognizer?.startListening(recognizerIntent)
    }

    override fun onStopListening(listener: Callback?) {
        recognizer?.stopListening()
    }

    override fun onCancel(listener: Callback?) {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}


package com.dn.mark1

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class OfflineVoiceEngine(
    context: Context,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    fun load(onReady: () -> Unit) {
        Thread {
            try {
                val modelDir = File(
                    appContext.filesDir,
                    "models/vosk-model-small-hi-0.22"
                )

                if (!File(modelDir, "am/final.mdl").exists()) {
                    throw Exception("Hindi model not found")
                }

                val loadedModel = Model(modelDir.absolutePath)

                model = loadedModel

                mainHandler.post {
                    onReady()
                }

            } catch (e: Exception) {
                mainHandler.post {
                    onError(e.message ?: "Model loading failed")
                }
            }
        }.start()
    }

    fun startListening() {
        try {
            val loadedModel = model
                ?: throw Exception("Load Hindi model first")

            val rec = Recognizer(loadedModel, 16000.0f)
            recognizer = rec

            val service = SpeechService(rec, 16000.0f)
            speechService = service

            service.startListening(this)

        } catch (e: Exception) {
            onError(e.message ?: "Voice engine failed")
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            recognizer?.close()

            speechService = null
            recognizer = null

        } catch (e: Exception) {
            onError(e.message ?: "Stop failed")
        }
    }

    private fun readText(json: String): String {
        return try {
            JSONObject(json).optString("text")
        } catch (e: Exception) {
            ""
        }
    }

    override fun onResult(hypothesis: String) {}

    override fun onFinalResult(hypothesis: String) {
        val result = readText(hypothesis)
        if (result.isNotBlank()) onText(result)
    }

    override fun onPartialResult(hypothesis: String) {}

    override fun onError(e: Exception) {
        onError(e.message ?: "Recognition error")
    }

    override fun onTimeout() {}
}

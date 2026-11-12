
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
    private val onFailure: (String) -> Unit,
    private val onTimeout: () -> Unit = {}
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    @Volatile
    private var listening = false

    fun load(onReady: () -> Unit) {
        load(onReady, onFailure)
    }

    fun load(
        onReady: () -> Unit,
        onLoadError: (String) -> Unit
    ) {
        Thread {
            try {
                val modelDir = File(
                    appContext.filesDir,
                    "models/vosk-model-small-hi-0.22"
                )

                if (!File(modelDir, "am/final.mdl").exists()) {
                    throw Exception("Hindi model not found")
                }

                model = Model(modelDir.absolutePath)
                mainHandler.post { onReady() }

            } catch (e: Exception) {
                mainHandler.post {
                    onLoadError(e.message ?: "Model loading failed")
                }
            }
        }.start()
    }

    @Synchronized
    fun startListening() {
        stopListening()

        try {
            val loadedModel = model
                ?: throw Exception("Load Hindi model first")

            val rec = Recognizer(loadedModel, 16000.0f)
            val service = SpeechService(rec, 16000.0f)

            recognizer = rec
            speechService = service
            listening = true

            service.startListening(this)

        } catch (e: Exception) {
            listening = false
            stopListening()
            onFailure(e.message ?: "Voice engine failed")
        }
    }

    @Synchronized
    fun stopListening() {
        listening = false

        val service = speechService
        speechService = null

        val rec = recognizer
        recognizer = null

        try {
            service?.stop()
            service?.shutdown()
        } catch (_: Exception) {
        }

        try {
            rec?.close()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopListening()

        try {
            model?.close()
        } catch (_: Exception) {
        }

        model = null
    }

    private fun readText(json: String): String {
        return try {
            JSONObject(json).optString("text")
        } catch (_: Exception) {
            ""
        }
    }

    override fun onResult(hypothesis: String) {
        val result = readText(hypothesis)
        if (listening && result.isNotBlank()) {
            onText(result)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        val result = readText(hypothesis)
        if (listening && result.isNotBlank()) {
            onText(result)
        }
    }

    override fun onPartialResult(hypothesis: String) {
    }

    override fun onError(e: Exception) {
        if (listening) {
            onFailure(e.message ?: "Recognition error")
        }
    }

    override fun onTimeout() {
        if (listening) {
            listening = false
            onTimeout.invoke()
        }
    }
}

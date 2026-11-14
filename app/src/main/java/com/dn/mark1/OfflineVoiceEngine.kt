
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
    private val onTimeout: () -> Unit = {},
    private val onWakeWord: () -> Unit = {}
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
    if (!listening) return

    try {
        val partial = JSONObject(hypothesis)
            .optString("partial")
            .lowercase()
            .trim()

                val wakeWords = setOf(
            "jarvis", "jervis", "jarvish",
            "जार्विस", "जार्वीस", "जर्विस", "जर्वीस",
            "जारविस", "जारवीस"
        )

        val spokenWords = partial
            .split(Regex("\\s+"))
            .map { it.trim().trim(',', '.', '?', '!', '।', '॥', ':', ';') }
            .filter { it.isNotBlank() }

        val wakeDetected = spokenWords.any { spoken ->
            spoken in wakeWords ||
                (spoken.all { it in 'a'..'z' } &&
                    spoken.length >= 5 &&
                    wakeWords.any { target ->
                        target.all { it in 'a'..'z' } &&
                            differsByAtMostOneCharacter(spoken, target)
                    })
        }

        if (wakeDetected) {
            listening = false
            onWakeWord()
        }
        private fun differsByAtMostOneCharacter(
    first: String,
    second: String
): Boolean {
    if (kotlin.math.abs(first.length - second.length) > 1) return false

    var i = 0
    var j = 0
    var edits = 0

    while (i < first.length && j < second.length) {
        if (first[i] == second[j]) {
            i++
            j++
        } else {
            edits++
            if (edits > 1) return false

            if (first.length > second.length) {
                i++
            } else if (second.length > first.length) {
                j++
            } else {
                i++
                j++
            }
        }
    }

    if (i < first.length || j < second.length) edits++
    return edits <= 1
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

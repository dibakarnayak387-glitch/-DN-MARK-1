
package com.dn.mark1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.File

class Mark1RecognitionService : RecognitionService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var model: Model? = null

    override fun onStartListening(
        recognizerIntent: Intent?,
        listener: Callback?
    ) {
        if (listener == null) return

        stopEngine()
        cancelled = false

        Thread {
            try {
                val modelDir = File(
                    filesDir,
                    "models/vosk-model-small-hi-0.22"
                )

                if (!File(modelDir, "am/final.mdl").exists()) {
                    throw Exception("Hindi voice model not installed")
                }

                val loadedModel = Model(modelDir.absolutePath)
                val rec = Recognizer(loadedModel, 16000.0f)
                val service = SpeechService(rec, 16000.0f)

                if (cancelled) {
                    service.shutdown()
                    rec.close()
                    loadedModel.close()
                    return@Thread
                }

                model = loadedModel
                recognizer = rec
                speechService = service

                mainHandler.post {
                    if (!cancelled) {
                        listener.readyForSpeech(Bundle())
                    }
                }

                service.startListening(
                    object : org.vosk.android.RecognitionListener {

                        override fun onPartialResult(hypothesis: String) {
                            val partial = try {
                                JSONObject(hypothesis)
                                    .optString("partial")
                            } catch (_: Exception) {
                                ""
                            }

                            if (partial.isNotBlank() && !cancelled) {
                                val bundle = Bundle().apply {
                                    putStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION,
                                        arrayListOf(partial)
                                    )
                                }

                                mainHandler.post {
                                    if (!cancelled) {
                                        listener.partialResults(bundle)
                                    }
                                }
                            }
                        }

                        override fun onResult(hypothesis: String) {}

                        override fun onFinalResult(hypothesis: String) {
                            val result = try {
                                JSONObject(hypothesis).optString("text").trim()
                            } catch (_: Exception) {
                                ""
                            }

                            mainHandler.post {
                                if (!cancelled) {
                                    if (result.isNotBlank()) {
                                        val bundle = Bundle().apply {
                                            putStringArrayList(
                                                SpeechRecognizer.RESULTS_RECOGNITION,
                                                arrayListOf(result)
                                            )
                                        }
                                        listener.results(bundle)
                                    } else {
                                        listener.error(
                                            SpeechRecognizer.ERROR_NO_MATCH
                                        )
                                    }
                                    stopEngine()
                                }
                            }
                        }

                        override fun onError(exception: Exception) {
                            mainHandler.post {
                                if (!cancelled) {
                                    listener.error(
                                        SpeechRecognizer.ERROR_SERVER
                                    )
                                    stopEngine()
                                }
                            }
                        }

                        override fun onTimeout() {
                            mainHandler.post {
                                if (!cancelled) {
                                    listener.error(
                                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                    )
                                    stopEngine()
                                }
                            }
                        }
                    }
                )

            } catch (_: Exception) {
                mainHandler.post {
                    if (!cancelled) {
                        listener.error(SpeechRecognizer.ERROR_CLIENT)
                        stopEngine()
                    }
                }
            }
        }.start()
    }

    override fun onStopListening(listener: Callback?) {
        speechService?.stop()
    }

    override fun onCancel(listener: Callback?) {
        cancelled = true
        stopEngine()
    }

    private fun stopEngine() {
        try {
            speechService?.shutdown()
        } catch (_: Exception) {}

        speechService = null

        try {
            recognizer?.close()
        } catch (_: Exception) {}

        recognizer = null

        try {
            model?.close()
        } catch (_: Exception) {}

        model = null
    }

    override fun onDestroy() {
        cancelled = true
        stopEngine()
        super.onDestroy()
    }
}

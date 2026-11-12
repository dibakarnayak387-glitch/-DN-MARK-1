
package com.dn.mark1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.dn.mark1.ACTION_START_JARVIS"
        const val ACTION_STOP = "com.dn.mark1.ACTION_STOP_JARVIS"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "dn_jarvis_voice"
    }

    private val handler = Handler(Looper.getMainLooper())

    private var voiceEngine: OfflineVoiceEngine? = null
    private var tts: TextToSpeech? = null

    private var ttsReady = false
    private var serviceStopped = false
    private var waitingForCommand = false
    private var processing = false
    private var pendingSpeech: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        tts = TextToSpeech(this, this)

        updateNotification("Jarvis voice service starting")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopJarvis()
            return START_NOT_STICKY
        }

        serviceStopped = false

        val notification = buildNotification(
            "Jarvis voice service starting"
        )

        startForeground(NOTIFICATION_ID, notification)

        if (voiceEngine == null) {
            loadVoiceEngine()
        }

        return START_NOT_STICKY
    }

    private fun loadVoiceEngine() {
        updateNotification("Loading Hindi voice model...")

        val engine = OfflineVoiceEngine(
            applicationContext,
            onText = { text ->
                handler.post {
                    handleRecognizedText(text)
                }
            },
            onError = { error ->
                handler.post {
                    if (!serviceStopped && !processing) {
                        updateNotification("Voice error: $error")
                        scheduleListeningRestart()
                    }
                }
            }
        )

        voiceEngine = engine

        engine.load {
            if (!serviceStopped) {
                updateNotification("Listening for Jarvis...")
                startWakeListening()
            }
        }
    }

    private fun startWakeListening() {
        if (serviceStopped || processing) return

        try {
            voiceEngine?.startListening()
            updateNotification("Listening for Jarvis...")
        } catch (e: Exception) {
            updateNotification("Voice start failed")
            scheduleListeningRestart()
        }
    }

    private fun handleRecognizedText(rawText: String) {
        if (serviceStopped || processing) return

        val text = rawText.trim()
        if (text.isBlank()) return

        val lower = text.lowercase(Locale.ROOT)

        val wakeWords = listOf(
            "jarvis",
            "जार्विस",
            "जार्वीस",
            "जर्विस"
        )

        val wakeFound = wakeWords.any { lower.contains(it) }

        if (!waitingForCommand && !wakeFound) {
            return
        }

        processing = true

        try {
            voiceEngine?.stopListening()
        } catch (_: Exception) {
        }

        if (!waitingForCommand && wakeFound) {
            val command = wakeWords.fold(lower) { result, word ->
                result.replace(word, "").trim()
            }.trim(' ', ',', '.', '?', '!')

            if (command.isBlank()) {
                waitingForCommand = true
                speakAndResume("जी, मैं सुन रहा हूँ।")
            } else {
                waitingForCommand = true
                askGemini(command)
            }
        } else {
            waitingForCommand = false
            askGemini(text)
        }
    }

    private fun askGemini(question: String) {
        updateNotification("Gemini is thinking...")

        GeminiClient.send(
            listOf(
                GeminiMessage(
                    "user",
                    question
                )
            )
        ) { reply, error ->

            handler.post {
                if (serviceStopped) return@post

                val answer = reply
                    ?: "माफ कीजिए, जवाब नहीं मिल पाया। ${error.orEmpty()}"

                waitingForCommand = false
                speakAndResume(answer)
            }
        }
    }

    private fun speakAndResume(message: String) {
        if (serviceStopped) return

        if (!ttsReady) {
            pendingSpeech = message
            return
        }

        val utteranceId = if (waitingForCommand) {
    "jarvis_ack_${System.currentTimeMillis()}"
} else {
    "jarvis_reply_${System.currentTimeMillis()}"
}

        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale("hi", "IN")

            pendingSpeech?.let { message ->
                pendingSpeech = null
                speakAndResume(message)
            }
        } else {
            updateNotification("Speech engine unavailable")
        }

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        if (!serviceStopped) {
    if (utteranceId?.startsWith("jarvis_ack_") != true) {
        waitingForCommand = false
    }

    processing = false
    scheduleListeningRestart()
}
                    
                                

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post {
                        processing = false
                        scheduleListeningRestart()
                    }
                }
            }
        )
    }

    private fun scheduleListeningRestart() {
        if (serviceStopped) return

        handler.postDelayed({
            if (!serviceStopped) {
                processing = false
                startWakeListening()
            }
        }, 700)
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, JarvisVoiceService::class.java)
            .setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("D.N. MARK 1")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Jarvis",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(message: String) {
        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(message)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun stopJarvis() {
        serviceStopped = true

        handler.removeCallbacksAndMessages(null)

        try {
            voiceEngine?.stopListening()
        } catch (_: Exception) {
        }

        voiceEngine = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceStopped = true
        handler.removeCallbacksAndMessages(null)

        try {
            voiceEngine?.stopListening()
        } catch (_: Exception) {
        }

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

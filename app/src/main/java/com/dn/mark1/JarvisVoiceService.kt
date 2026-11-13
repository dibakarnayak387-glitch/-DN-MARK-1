
package com.dn.mark1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    private var ttsReady = false
    private var serviceStopped = false
    private var waitingForCommand = false
    private var processing = false

    // Confirmation memory
    private var awaitingConfirmation = false
    private var pendingAction: AgentStep? = null

    private var pendingSpeech: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DNMark1:JarvisWakeLock"
        )

        tts = TextToSpeech(this, this)
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

        val notification = buildNotification("Jarvis voice service starting")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            if (wakeLock?.isHeld != true) wakeLock?.acquire()
        } catch (_: Exception) {
        }

        if (voiceEngine == null) loadVoiceEngine()

        return START_STICKY
    }

    private fun loadVoiceEngine() {
        updateNotification("Loading Hindi voice model...")

                val engine = OfflineVoiceEngine(
            applicationContext,
            onText = { text ->
                handler.post { handleRecognizedText(text) }
            },
            onFailure = { error ->
                handler.post {
                    if (!serviceStopped && !processing) {
                        updateNotification("Voice error: $error")
                        scheduleListeningRestart()
                    }
                }
            },
            onTimeout = {
                handler.post {
                    if (!serviceStopped && !processing) {
                        updateNotification("Restarting voice listening...")
                        scheduleListeningRestart()
                    }
                }
            },
            onWakeWord = {
                handler.post {
                    if (
                        !serviceStopped &&
                        !processing &&
                        !awaitingConfirmation &&
                        !waitingForCommand
                    ) {
                        processing = true
                        voiceEngine?.stopListening()
                        waitingForCommand = true
                        speakAndResume("जी, मैं सुन रहा हूँ।")
                    }
                }
            }
        }

        voiceEngine = engine

        engine.load(
            onReady = {
                if (!serviceStopped) {
                    updateNotification("Listening for Jarvis...")
                    startWakeListening()
                }
            },
            onLoadError = { error ->
                if (!serviceStopped) {
                    updateNotification("Voice model error: $error")
                    processing = false
                }
            }
        )
    }

    private fun startWakeListening() {
        if (serviceStopped || processing) return

        try {
            voiceEngine?.startListening()
            updateNotification(
                if (awaitingConfirmation)
                    "Listening for confirmation..."
                else
                    "Listening for Jarvis..."
            )
        } catch (e: Exception) {
            updateNotification("Voice start failed: ${e.message}")
            scheduleListeningRestart()
        }
    }

    
private fun handleRecognizedText(rawText: String) {
    if (serviceStopped || processing) return

    val text = rawText.trim()
    if (text.isBlank()) return

    processing = true
    voiceEngine?.stopListening()

    if (awaitingConfirmation) {
        handleConfirmation(text)
        return
    }

    val lower = text.lowercase(Locale.ROOT)

    val wakeWords = listOf(
        "jarvis", "जार्विस", "जार्वीस", "जर्विस"
    )

    val wakeFound = wakeWords.any { lower.contains(it) }

    if (!waitingForCommand && !wakeFound) {
        processing = false
        scheduleListeningRestart()
        return
    }

    if (!waitingForCommand && wakeFound) {
        val command = wakeWords.fold(lower) { result, word ->
            result.replace(word, "").trim()
        }.trim(' ', ',', '.', '?', '!')

        waitingForCommand = true

        if (command.isBlank()) {
            speakAndResume("जी, मैं सुन रहा हूँ।")
        } else {
            waitingForCommand = false
            askGemini(command)
        }
    } else {
        waitingForCommand = false
        askGemini(text)
    }
}

private fun askGemini(question: String) {
    updateNotification("AI is thinking...")

    GeminiClient.sendAgent(
        listOf(GeminiMessage("user", question))
    ) { result, error ->

        handler.post {
            if (serviceStopped) return@post

            if (result == null) {
                speakAndResume(
                    "माफ कीजिए, जवाब नहीं मिल पाया। ${error.orEmpty()}"
                )
                return@post
            }

            val plan = result.agentPlan
            val action = plan.steps.firstOrNull {
                it.action == "open_app" ||
                it.action == "draft_message"
            }

            if (plan.requiresConfirmation && action != null) {
                pendingAction = action
                awaitingConfirmation = true

                speakAndResume(
                    if (result.reply.isNotBlank()) result.reply
                    else "क्या आप इस काम की पुष्टि करते हैं?"
                )
                return@post
            }

            if (action != null && action.action == "open_app") {
                executeAction(action)
                return@post
            }

            speakAndResume(result.reply)
        }
    }
}

private fun handleConfirmation(rawText: String) {
    val normalized = rawText.lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("[\\s.,!?;:।॥]+"), " ")
        .trim()

    val yesPhrases = setOf(
        "हाँ", "हां", "जी हाँ", "जी हां",
        "yes", "yeah", "yep",
        "ହଁ", "ହଁ ହଁ"
    )

    val noPhrases = setOf(
        "नहीं", "नही", "जी नहीं", "जी नही",
        "no", "nope",
        "ନା", "ନାହିଁ", "ରଦ୍ଦ"
    )

    when {
        normalized in yesPhrases -> {
            val action = pendingAction
            awaitingConfirmation = false
            pendingAction = null

            if (action != null) {
                executeAction(action)
            } else {
                speakAndResume("पुष्टि मिली, लेकिन काम उपलब्ध नहीं है।")
            }
        }

        normalized in noPhrases -> {
            awaitingConfirmation = false
            pendingAction = null
            speakAndResume("ठीक है, मैंने काम रद्द कर दिया।")
        }

        else -> {
            speakAndResume("कृपया हाँ या नहीं में जवाब दीजिए।")
        }
    }
}

    private fun executeAction(action: AgentStep) {
        when (action.action) {
            "open_app" -> openApprovedApp(action.target)

            "draft_message" -> {
                speakAndResume(
                    "मैं संदेश तैयार कर सकता हूँ, लेकिन उसे अपने-आप नहीं भेजूँगा।"
                )
            }

            else -> {
                speakAndResume("यह काम अभी उपलब्ध नहीं है।")
            }
        }
    }

    
private fun openApprovedApp(target: String) {
    val requested = target.trim()

    if (requested.isBlank()) {
        speakAndResume("कौन सा ऐप खोलना है, कृपया उसका नाम बताइए।")
        return
    }

    try {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val installedApps = packageManager
            .queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolve ->
                val info = resolve.activityInfo ?: return@mapNotNull null
                val label = info.applicationInfo
                    .loadLabel(packageManager).toString()

                label to info.packageName
            }
            .distinctBy { it.second }

        fun normalize(value: String): String {
            return value.lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N}]"), "")
        }

        val wanted = normalize(requested)

        val exactMatches = installedApps.filter {
            normalize(it.first) == wanted
        }

        val matches = if (exactMatches.isNotEmpty()) {
            exactMatches
        } else {
            installedApps.filter {
                val label = normalize(it.first)
                label.isNotBlank() &&
                    (label.contains(wanted) || wanted.contains(label))
            }
        }

        if (matches.isEmpty()) {
            speakAndResume("माफ कीजिए, यह ऐप फोन में नहीं मिला।")
            return
        }

        if (matches.size > 1) {
            speakAndResume(
                "इस नाम के एक से ज्यादा ऐप मिले हैं। कृपया पूरा नाम बताइए।"
            )
            return
        }

        val selected = matches.first()
        val launchIntent =
            packageManager.getLaunchIntentForPackage(selected.second)

        if (launchIntent == null) {
            speakAndResume("इस ऐप को खोलने का विकल्प उपलब्ध नहीं है।")
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)

        speakAndResume("${selected.first} खोलने की कोशिश कर रहा हूँ।")

    } catch (e: Exception) {
        speakAndResume("ऐप नहीं खुल पाया। कृपया फिर कोशिश करें।")
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

            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            if (!serviceStopped) {
                                if (awaitingConfirmation) {
                                    processing = false
                                    scheduleListeningRestart()
                                    return@post
                                }

                                if (
                                    utteranceId?.startsWith("jarvis_ack_") != true
                                ) {
                                    waitingForCommand = false
                                }

                                processing = false
                                scheduleListeningRestart()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post {
                            if (!serviceStopped) {
                                processing = false
                                scheduleListeningRestart()
                            }
                        }
                    }
                }
            )

            pendingSpeech?.let {
                pendingSpeech = null
                speakAndResume(it)
            }
        } else {
            updateNotification("Speech engine unavailable")
        }
    }

    private fun scheduleListeningRestart() {
        if (serviceStopped) return

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            if (!serviceStopped) {
                processing = false
                startWakeListening()
            }
        }, 200)
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, JarvisVoiceService::class.java)
            .setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
        getSystemService(NotificationManager::class.java).notify(
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
        awaitingConfirmation = false
        pendingAction = null
        handler.removeCallbacksAndMessages(null)

        voiceEngine?.release()
        voiceEngine = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceStopped = true
        awaitingConfirmation = false
        pendingAction = null
        handler.removeCallbacksAndMessages(null)

        voiceEngine?.release()
        voiceEngine = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

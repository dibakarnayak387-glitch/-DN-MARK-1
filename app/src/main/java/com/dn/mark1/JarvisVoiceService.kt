
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
    private var commandRecognizer: AndroidCommandRecognizer? = null
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var ttsReady = false
    private var serviceStopped = false
    private var waitingForCommand = false
    private var processing = false

    // Confirmation memory
    private var awaitingConfirmation = false
    private var pendingActions: List<AgentStep> = emptyList()
    private var confirmingMemoryForget = false

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
        )

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
private fun startCommandListening() {
    if (serviceStopped) return

    voiceEngine?.stopListening()
    updateNotification(
    if (awaitingConfirmation)
        "Listening for confirmation..."
    else
        "Listening for your command..."
)
    commandRecognizer?.destroy()

    commandRecognizer = AndroidCommandRecognizer(
        applicationContext,
        onResult = { recognizedText ->
            handler.post {
                if (serviceStopped) return@post

                commandRecognizer?.destroy()
                commandRecognizer = null

                if (awaitingConfirmation) {
                    handleConfirmation(recognizedText)
                } else {
                    waitingForCommand = false
                    askGemini(recognizedText)
                }
            }
        },
        onError = { error ->
            handler.post {
                if (serviceStopped) return@post

                commandRecognizer?.destroy()
                commandRecognizer = null

                if (!awaitingConfirmation) {
                    waitingForCommand = false
                }

                speakAndResume(
                    if (awaitingConfirmation)
                        "कृपया फिर से हाँ या नहीं बोलिए।"
                    else
                        "आवाज़ समझ नहीं आई। कृपया फिर बोलिए।"
                )
            }
        }
    )

    commandRecognizer?.start("hi-IN")
}
    
private fun startWakeListening() {
    if (serviceStopped || processing || awaitingConfirmation) return

    val engine = voiceEngine

    if (engine == null) {
        updateNotification("Voice engine unavailable. Reloading...")
        loadVoiceEngine()
        return
    }

    try {
        engine.startListening()

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


private fun extractOpenAppTarget(raw: String): String? {
    val text = raw.lowercase(Locale.ROOT)

    val openWords = listOf(
        "open", "kholo", "khol do", "khol", "chalao",
        "launch", "start", "ओपन", "खोलो", "खोल दो",
        "खोल", "चालू करो", "चलाओ"
    )

    if (openWords.none { text.contains(it) }) return null

    val aliases = listOf(
        "whatsapp" to listOf("whatsapp", "व्हाट्सएप", "व्हाट्स अप", "वाट्सएप"),
        "youtube" to listOf("youtube", "यूट्यूब"),
        "chrome" to listOf("chrome", "क्रोम"),
        "instagram" to listOf("instagram", "इंस्टाग्राम"),
        "facebook" to listOf("facebook", "फेसबुक"),
        "camera" to listOf("camera", "कैमरा"),
        "settings" to listOf("settings", "सेटिंग", "सेटिंग्स"),
        "phone" to listOf("phone app", "फोन ऐप"),
        "maps" to listOf("google maps", "maps", "मैप्स")
    )

    for ((target, names) in aliases) {
        if (names.any { text.contains(it) }) return target
    }

    val cleaned = text
        .replace(Regex("open|kholo|khol do|khol|chalao|launch|start"), " ")
        .replace(Regex("ओपन|खोलो|खोल दो|खोल|चालू करो|चलाओ"), " ")
        .trim()

    return cleaned.takeIf { it.isNotBlank() }
}

private fun isCallRequest(raw: String): Boolean {
    val text = raw.lowercase(Locale.ROOT)
    return listOf(
        "call", "कॉल", "কল", "କଲ୍",
        "phone karna", "फोन करना", "फोन लगाना",
        "फोन लगाओ"
    ).any { text.contains(it) }
}

private fun isMultiStepRequest(raw: String): Boolean {
    val text = raw.lowercase(Locale.ROOT)

    return Regex(
        "\\b(aur|and|then|phir|fir|uske baad|after that|also)\\b|और|फिर|उसके बाद|ଏବଂ|ତାପରେ"
    ).containsMatchIn(text)
}
private fun extractRequestedApps(raw: String): List<String> {
    val text = raw.lowercase(Locale.ROOT)

    val aliases = listOf(
        "youtube" to listOf("youtube", "यूट्यूब"),
        "instagram" to listOf("instagram", "इंस्टाग्राम"),
        "whatsapp" to listOf("whatsapp", "व्हाट्सएप"),
        "facebook" to listOf("facebook", "फेसबुक"),
        "chrome" to listOf("chrome", "क्रोम")
    )

    return aliases.mapNotNull { (app, names) ->
        val positions = names.map { text.indexOf(it) }
            .filter { it >= 0 }

        if (positions.isEmpty()) null
        else app to positions.minOrNull()!!
    }.sortedBy { it.second }.map { it.first }
}
private fun askGemini(question: String) {
    val appTarget = extractOpenAppTarget(question)
    val lowerQuestion = question.lowercase(Locale.ROOT)
    val multiStepRequest = isMultiStepRequest(question)
    // DIRECT BATTERY RESPONSE
if (
    lowerQuestion.contains("battery") ||
    lowerQuestion.contains("बैटरी") ||
    lowerQuestion.contains("charge kitna") ||
    lowerQuestion.contains("charge kitni") ||
    lowerQuestion.contains("चार्ज कितना")
) {
    speakAndResume(
        AndroidActionExecutor.getBatteryStatus(this)
    )
    return
}

// CONFIRMED MULTI-APP SEQUENCE
val requestedApps = extractRequestedApps(question)

val hasOpenCommand = listOf(
    "open", "kholo", "khol", "खोलो", "खोल"
).any { lowerQuestion.contains(it) }

if (
    multiStepRequest &&
    hasOpenCommand &&
    requestedApps.size >= 2
) {
    pendingActions = requestedApps.map {
        AgentStep(
            action = "open_app",
            target = it,
            description = "$it खोलना"
        )
    }

    awaitingConfirmation = true

    val sequence = requestedApps.joinToString(" फिर ")

    speakAndResume(
        "मैं पहले $sequence खोलूँगा। " +
        "क्या मैं शुरू करूँ? हाँ या नहीं बोलिए।"
    )
    return
}
        // SMART MEMORY: Save a preference or fact
    val rememberPrefixes = listOf(
        "yaad rakhna", "remember that", "remember",
        "याद रखना", "याद रखो", "ମନେ ରଖ"
    )

    val rememberPrefix = rememberPrefixes.firstOrNull {
        lowerQuestion.startsWith(it)
    }

    if (rememberPrefix != null) {
        val memoryText = question
            .substring(rememberPrefix.length)
            .trim()
            .removePrefix("कि")
            .trim()

        val saved = JarvisMemory.save(this, memoryText)

        speakAndResume(
            if (saved) "ठीक है, मैंने यह बात याद रख ली।"
            else "यह जानकारी सेव नहीं हो पाई। कृपया दोबारा बताइए।"
        )
        return
    }

    // SMART MEMORY: Recall saved memories
    
// SMART MEMORY: Recall saved memories
val recallPhrases = listOf(
    "what do you remember",
    "what do you remember about me",
    "what are my preferences",
    "what do you know about me",
    "tumhe kya yaad hai",
    "tumhe meri kya preference yaad hai",
    "meri kya preference yaad hai",
    "meri pasand kya hai",
    "mujhe kya pasand hai",
    "meri pasand yaad hai",
    "memory dikhao",
    "meri memory batao",
    "मेरे बारे में क्या याद",
    "मेरी पसंद क्या है",
    "मुझे क्या पसंद है",
    "मेरी पसंद बताओ",
    "ମୋ ବିଷୟରେ କଣ ମନେ",
    "ମୋତେ କଣ ଭଲ ଲାଗେ"
)

if (recallPhrases.any { lowerQuestion.contains(it) }) {
    val memories = JarvisMemory.getAll(this)

    speakAndResume(
        if (memories.isEmpty()) "अभी मेरी मेमोरी खाली है।"
        else "मुझे यह बातें याद हैं: " + memories.joinToString("। ")
    )
    return
}

    // SMART MEMORY: Ask before deleting all memories
    if (
        lowerQuestion.contains("forget all memory") ||
        lowerQuestion.contains("delete all memory") ||
        lowerQuestion.contains("sab memory delete") ||
        lowerQuestion.contains("सारी मेमोरी मिटा") ||
        lowerQuestion.contains("सभी मेमोरी हटाओ")
    ) {
        confirmingMemoryForget = true
        awaitingConfirmation = true
        pendingActions = emptyList()
        speakAndResume("क्या आप अपनी सभी सेव की गई मेमोरी हटाना चाहते हैं? पुष्टि के लिए हाँ या रद्द करने के लिए नहीं बोलिए।")
        return
    }

    // Direct YouTube search
    val isYouTube = lowerQuestion.contains("youtube") ||
            lowerQuestion.contains("यूट्यूब")

    val hasSearchCommand = listOf(
        "search", "find", "dhundo", "खोजो",
        "ढूंढो", "खोज", "सर्च", "ढूंढ"
    ).any { lowerQuestion.contains(it) }

    if (isYouTube && hasSearchCommand && !multiStepRequest) {
        val query = question
            .replace(Regex("(?i)youtube|यूट्यूब"), " ")
            .replace(
                Regex("(?i)search\\s*karo|search|find|dhundo"),
                " "
            )
            .replace(
                Regex("(?i)khol\\s*do|kholo|khol|open\\s*karo"),
                " "
            )
            .replace(
                Regex("(?i)\\baur\\b|\\bkarke\\b|\\bpar\\b"),
                " "
            )
            .replace(
                Regex("खोलो|खोल दो|खोल|खोजो|ढूंढो|ढूंढ|खोज|सर्च"),
                " "
            )
            .trim()

        if (query.isBlank()) {
            speakAndResume("क्या खोज करना है, कृपया बताइए।")
            return
        }

        val opened = AndroidActionExecutor.searchYouTube(this, query)

        speakAndResume(
            if (opened) "YouTube पर खोज रहा हूँ।"
            else "YouTube नहीं खुल पाया।"
        )
        return
    }

    // Open installed apps
    if (appTarget != null && !multiStepRequest) {
    val opened = openApprovedApp(appTarget)

    if (opened) {
        updateNotification("App opened. Restarting voice listening...")
        scheduleListeningRestart()
    } else {
        speakAndResume("यह ऐप नहीं खुल पाया।")
    }

    return
}
    // Call request
    if (isCallRequest(question)) {
        speakAndResume("किसे कॉल करना है? उसका नाम या नंबर बताइए।")
        return
    }

    // AI agent request
    updateNotification("AI is thinking...")

    GeminiClient.sendAgent(
    listOf(GeminiMessage("user", question)),
    JarvisMemory.getAll(this)
) { result, error ->

        handler.post {
            if (serviceStopped) return@post

            val agentResult = result

            if (agentResult == null) {
                speakAndResume(
                    "माफ कीजिए, जवाब नहीं मिल पाया। ${error.orEmpty()}"
                )
                return@post
            }

            val plan = agentResult.agentPlan

            val actions = plan.steps.filter {
            it.action == "open_app" ||
            it.action == "youtube_search" ||
            it.action == "share_text" ||
            it.action == "draft_message" ||
            it.action == "battery_status"
        }
            if (actions.isEmpty()) {
                speakAndResume(agentResult.reply)
                return@post
            }

            if (plan.requiresConfirmation) {
                pendingActions = actions
                awaitingConfirmation = true

                speakAndResume(
                    if (agentResult.reply.isNotBlank()) {
                        agentResult.reply
                    } else {
                        "क्या आप इस काम की पुष्टि करते हैं?"
                    }
                )
                return@post
            }

            executePlan(actions)
        }
    }
}



private fun handleConfirmation(rawText: String) {
    val normalized = rawText.lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("[\\s.,!?;:।॥]+"), " ")

    val words = normalized.split(" ").filter { it.isNotBlank() }

    val yesWords = setOf(
    "हाँ", "हां", "जी", "yes", "yeah", "yep",
    "haan", "han", "ha", "haanji", "hanji",
    "okay", "ok", "bilkul", "confirm", "kardo",
    "ହଁ"
)

    val noWords = setOf(
        "नहीं", "नही", "no", "nope", "nahi", "nahin",
        "ନା", "ନାହିଁ", "ରଦ୍ଦ"
    )

    val isYes = words.any { it in yesWords }
    val isNo = words.any { it in noWords }

    when {
        isYes && !isNo -> {
            if (confirmingMemoryForget) {
                confirmingMemoryForget = false
                awaitingConfirmation = false
                pendingActions = emptyList()

                val deleted = JarvisMemory.forgetAll(this)

                speakAndResume(
                    if (deleted) "आपकी सभी सेव की गई मेमोरी हटा दी गई है।"
                    else "मेमोरी हटाई नहीं जा सकी।"
                )
                return
            }

            val actions = pendingActions
            pendingActions = emptyList()
            awaitingConfirmation = false
            confirmingMemoryForget = false

            if (actions.isNotEmpty()) {
                executePlan(actions)
            } else {
                speakAndResume("कोई काम बाकी नहीं है।")
            }
        }

        isNo && !isYes -> {
            pendingActions = emptyList()
            awaitingConfirmation = false
            confirmingMemoryForget = false
            speakAndResume("ठीक है, मैंने काम रद्द कर दिया।")
        }

        else -> {
            speakAndResume("कृपया हाँ या नहीं में जवाब दीजिए।")
        }
    }
}
    private fun executePlan(actions: List<AgentStep>) {
    if (actions.isEmpty()) {
        speakAndResume("करने के लिए कोई काम नहीं मिला।")
        return
    }

    val completed = mutableListOf<String>()

    for (action in actions) {
        when (action.action) {

            "battery_status" -> {
                completed.add(
                    AndroidActionExecutor.getBatteryStatus(this)
                )
            }

            "open_app" -> {
                if (openApprovedApp(action.target)) {
                    completed.add("${action.target} खोला")
                } else {
                    speakAndResume("ऐप नहीं खुल पाया। बाकी काम रोक दिया गया।")
                    return
                }
            }

            "youtube_search" -> {
                val success = AndroidActionExecutor.searchYouTube(
                    this,
                    action.target
                )

                if (success) {
                    completed.add("YouTube पर ${action.target} खोजा")
                } else {
                    speakAndResume("YouTube पर खोज नहीं हो पाई।")
                    return
                }
            }

            "share_text" -> {
                val success = AndroidActionExecutor.shareText(
                    this,
                    action.target
                )

                speakAndResume(
                    if (success) {
                        "शेयर करने का विकल्प खुल गया है। अब ऐप और प्राप्तकर्ता चुनें।"
                    } else {
                        "शेयर नहीं हो पाया।"
                    }
                )
                return
            }

            "draft_message" -> {
                speakAndResume(
                    "मैं संदेश तैयार करने में मदद कर सकता हूँ, लेकिन उसे अपने-आप नहीं भेजूँगा।"
                )
                return
            }

            else -> {
                speakAndResume("यह काम अभी उपलब्ध नहीं है।")
                return
            }
        }
    }

    speakAndResume(
        if (completed.isNotEmpty()) {
            completed.joinToString("। ")
        } else {
            "कोई काम पूरा नहीं हुआ।"
        }
    )
}
    private fun openApprovedApp(target: String): Boolean {
    val requested = target.trim()

    if (requested.isBlank()) {
        return false
    }

    return try {
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

        if (matches.size != 1) {
            false
        } else {
            val selected = matches.first()
            val launchIntent =
                packageManager.getLaunchIntentForPackage(selected.second)

            if (launchIntent == null) {
                false
            } else {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                true
            }
        }
    } catch (e: Exception) {
        false
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

                            if (
                                utteranceId?.startsWith("jarvis_ack_") == true
                            ) {
                                startCommandListening()
                                return@post
                            }

                            if (awaitingConfirmation) {
                                startCommandListening()
                                return@post
                            }

                            waitingForCommand = false
                            processing = false
                            scheduleListeningRestart()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    handler.post {
                        if (!serviceStopped) {

                            if (
                                utteranceId?.startsWith("jarvis_ack_") == true ||
                                awaitingConfirmation
                            ) {
                                startCommandListening()
                                return@post
                            }

                            waitingForCommand = false
                            processing = false
                            scheduleListeningRestart()
                        }
                    }
                }
            }
        )

        pendingSpeech?.let { message ->
            pendingSpeech = null
            speakAndResume(message)
        }
    } else {
        ttsReady = false
        updateNotification("Voice output initialization failed")
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
        pendingActions = emptyList()
        handler.removeCallbacksAndMessages(null)
        commandRecognizer?.destroy()
        commandRecognizer = null
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
        pendingActions = emptyList()
        handler.removeCallbacksAndMessages(null)
        commandRecognizer?.destroy()
commandRecognizer = null
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

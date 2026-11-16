
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
        const val ACTION_CANCEL_TASK =
    "com.dn.mark1.ACTION_CANCEL_TASK"
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
// Retry system: stores only failed supported actions.
private var retryActions: List<AgentStep> = emptyList()
    private var pendingSpeech: String? = null
    // Advanced conversation context
private val conversationHistory = mutableListOf<GeminiMessage>()

    override fun onCreate() {
        super.onCreate()
        if (conversationHistory.isEmpty()) {
    conversationHistory.addAll(ConversationStore.load(this))
        }
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
        if (intent?.action == ACTION_CANCEL_TASK) {
    val cancelled = AgentTaskManager.cancelCurrent(
    "यूज़र ने notification से task cancel किया।"
)

if (cancelled) {
    AgentTaskManager.snapshot()?.let { cancelledTask ->
        TaskHistoryStore.save(this, cancelledTask)
    }
}

    if (cancelled) {
        handler.post {
            awaitingConfirmation = false
            pendingActions = emptyList()
            processing = false
            speakAndResume("ठीक है, चल रहा काम रद्द कर दिया गया।")
        }
    }

    return START_NOT_STICKY
        }

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

    val confirmationMode = awaitingConfirmation

    waitingForCommand = !confirmationMode

    updateNotification(
        if (confirmationMode)
            "Listening for YES or NO..."
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
        onError = {
            handler.post {
                if (serviceStopped) return@post

                commandRecognizer?.destroy()
                commandRecognizer = null

                if (awaitingConfirmation) {
                    waitingForCommand = false
                    speakAndResume(
                        "कृपया केवल हाँ या नहीं बोलिए।"
                    )
                } else {
                    waitingForCommand = false
                    speakAndResume(
                        "आवाज़ समझ नहीं आई। कृपया फिर बोलिए।"
                    )
                }
            }
        }
    )

   commandRecognizer?.startMultilingual()
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
        // RETRY LAST FAILED TASK
    val retryWords = listOf(
        "retry", "try again", "repeat failed",
        "dobara karo", "phir se karo",
        "फिर से करो", "दोबारा करो",
        "पुनः प्रयास", "ପୁଣିଥରେ କର"
    )

    if (retryWords.any { lowerQuestionForRetry(question).contains(it) }) {
        val actionsToRetry = retryActions.toList()

        if (actionsToRetry.isEmpty()) {
            speakAndResume("दोबारा चलाने के लिए कोई असफल काम उपलब्ध नहीं है।")
        } else {
            retryActions = emptyList()
            speakAndResume("असफल काम दोबारा शुरू कर रहा हूँ।")
            handler.postDelayed({
                if (!serviceStopped) executePlan(actionsToRetry)
            }, 700)
        }
        return
    }
    val appTarget = extractOpenAppTarget(question)
    val lowerQuestion = question.lowercase(Locale.ROOT)
    val multiStepRequest = isMultiStepRequest(question)
    
// TASK STATUS COMMAND
val taskStatusWords = listOf(
    "task status",
    "current task",
    "kaam ka status",
    "mera task batao",
    "काम का स्टेटस",
    "टास्क की स्थिति",
    "अभी क्या काम चल रहा है",
    "କାମର ସ୍ଥିତି"
)

if (taskStatusWords.any { lowerQuestion.contains(it) }) {
    val task = AgentTaskManager.snapshot()

    val message = if (task == null) {
        "अभी कोई टास्क रिकॉर्ड नहीं है।"
    } else {
        "टास्क ${task.status} है। " +
        "${task.completedSteps} में से ${task.totalSteps} काम पूरे हुए। " +
        "अभी का काम: ${task.currentStep}। " +
        task.result
    }

    speakAndResume(message)
    return
}

    // TASK HISTORY COMMAND
    val historyWords = listOf(
        "task history",
        "show history",
        "previous tasks",
        "my tasks",
        "purane task",
        "pichle kaam",
        "task record",
        "टास्क हिस्ट्री",
        "पुराने काम",
        "पिछले काम बताओ",
        "मेरे पुराने टास्क",
        "କାମର ଇତିହାସ",
        "ପୁରୁଣା କାମ"
    )

    if (historyWords.any { lowerQuestion.contains(it) }) {
        val history = TaskHistoryStore.getAll(this)

        if (history.isEmpty()) {
            speakAndResume("अभी तक कोई पुराना टास्क रिकॉर्ड नहीं है।")
        } else {
            val report = history.take(5).mapIndexed { index, item ->
                "${index + 1}. ${item.goal}. " +
                "स्थिति ${item.status}. " +
                "${item.completedSteps} में से ${item.totalSteps} steps पूरे हुए."
            }.joinToString("। ")

            speakAndResume("आपके पिछले टास्क ये हैं। $report")
        }
        return
    }
    
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
    // Delete one matching saved memory
val forgetOnePrefixes = listOf(
    "forget memory",
    "delete memory",
    "remove memory",
    "ek memory hatao",
    "ek baat bhool jao",
    "एक मेमोरी हटाओ",
    "एक बात भूल जाओ",
    "ମନେ ରଖିଥିବା କଥା ହଟାଅ"
)

val forgetPrefix = forgetOnePrefixes.firstOrNull {
    lowerQuestion.startsWith(it)
}

if (forgetPrefix != null) {
    val query = question
        .substring(forgetPrefix.length)
        .trim()
        .removePrefix("कि")
        .trim()

    if (query.isBlank()) {
        speakAndResume("कौन सी याद की हुई बात हटानी है?")
    } else {
        val removed = JarvisMemory.forgetMatching(this, query)

        speakAndResume(
            if (removed) "वह याद की हुई बात हटा दी गई।"
            else "उससे मिलती हुई कोई मेमोरी नहीं मिली।"
        )
    }
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
// CONVERSATION HISTORY CLEAR WITH CONFIRMATION
val clearConversationWords = listOf(
    "clear conversation history",
    "clear chat history",
    "delete conversation history",
    "delete chat history",
    "purani chat delete",
    "purani conversation hatao",
    "बातचीत मिटाओ",
    "पुरानी बातचीत हटाओ",
    "चैट हिस्ट्री मिटाओ",
    "ସମସ୍ତ କଥାବାର୍ତ୍ତା ହଟାଅ",
    "ପୁରୁଣା ଚାଟ୍ ହଟାଅ"
)

if (clearConversationWords.any { lowerQuestion.contains(it) }) {
    confirmingConversationClear = true
    awaitingConfirmation = true
    pendingActions = emptyList()

    speakAndResume(
        "क्या आप पुरानी बातचीत की हिस्ट्री मिटाना चाहते हैं? " +
        "हाँ बोलकर पुष्टि करें या नहीं बोलकर रद्द करें।"
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

    conversationHistory.add(GeminiMessage("user", question))
    ConversationStore.save(this, conversationHistory)

while (conversationHistory.size > 12) {
    conversationHistory.removeAt(0)
}

GeminiClient.sendAgent(
    conversationHistory.toList(),
    JarvisMemory.getAll(this)
) { result, error ->
        handler.post {
            if (serviceStopped) return@post

            val agentResult = result

            if (agentResult == null) {
    if (conversationHistory.lastOrNull()?.role == "user") {
        conversationHistory.removeAt(conversationHistory.lastIndex)
    }

    speakAndResume(
        "माफ कीजिए, जवाब नहीं मिल पाया। ${error.orEmpty()}"
    )
    return@post
}

conversationHistory.add(
    GeminiMessage("assistant", agentResult.reply)
)
ConversationStore.save(this, conversationHistory)
while (conversationHistory.size > 12) {
    conversationHistory.removeAt(0)
}

            val plan = agentResult.agentPlan

           val actions = plan.steps.filter {
    it.action == "open_app" ||
    it.action == "youtube_search" ||
    it.action == "web_search" ||
    it.action == "share_text" ||
    it.action == "draft_message" ||
    it.action == "battery_status" ||
    it.action == "weather"
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
    if (serviceStopped || !awaitingConfirmation) return

    val normalized = rawText.lowercase(Locale.ROOT)
        .trim()
        .replace(Regex("[\\s.,!?;:।॥]+"), " ")

    val words = normalized.split(" ")
        .filter { it.isNotBlank() }

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
            if (confirmingConversationClear) {
    confirmingConversationClear = false
    awaitingConfirmation = false
    pendingActions = emptyList()

    val cleared = ConversationStore.clear(this)

    if (cleared) {
        conversationHistory.clear()
        speakAndResume("पुरानी बातचीत की हिस्ट्री मिटा दी गई है।")
    } else {
        speakAndResume("बातचीत की हिस्ट्री नहीं मिट पाई।")
    }
    return
            }
            if (confirmingMemoryForget) {
                confirmingMemoryForget = false
                confirmingConversationClear = false
                awaitingConfirmation = false
                pendingActions = emptyList()

                val deleted = JarvisMemory.forgetAll(this)

                speakAndResume(
                    if (deleted)
                        "आपकी सेव की गई मेमोरी हटा दी गई है।"
                    else
                        "मेमोरी हटाई नहीं जा सकी।"
                )
                return
            }

            val actions = pendingActions.toList()

            pendingActions = emptyList()
            awaitingConfirmation = false
            confirmingMemoryForget = false
            confirmingConversationClear = false

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
            // Confirmation mode चालू रहेगा।
            // कोई दूसरा command execute नहीं होगा।
            awaitingConfirmation = true

            speakAndResume(
                "मुझे केवल हाँ या नहीं बताइए।"
            )
        }
    }
}
    


private fun executePlan(actions: List<AgentStep>) {
    if (actions.isEmpty()) {
        speakAndResume("करने के लिए कोई काम नहीं मिला।")
        return
    }

    val results = mutableListOf<String>()
    val failedActions = mutableListOf<AgentStep>()

    val taskId = AgentTaskManager.start(
        "Jarvis multi-step task",
        actions.size
    )

    fun finishPlan() {
                if (AgentTaskManager.isCancelled(taskId)) {
            AgentTaskManager.snapshot()?.let { cancelledTask ->
                if (cancelledTask.id == taskId) {
                    TaskHistoryStore.save(this@JarvisVoiceService, cancelledTask)
                }
            }
            updateNotification("Task cancelled")
            return
        }
        retryActions = failedActions.toList()

        val summary = buildString {
            append(
                if (results.isEmpty()) "कोई काम पूरा नहीं हुआ।"
                else results.joinToString("। ")
            )

            if (failedActions.isNotEmpty()) {
                append("। ${failedActions.size} काम असफल हुए।")
                append(" आप Retry या दोबारा करो बोल सकते हैं।")
            } else {
                append("। सभी काम पूरे हुए।")
            }
        }

        val completed = AgentTaskManager.complete(taskId, summary)
                if (completed) {
            AgentTaskManager.snapshot()?.let { finishedTask ->
                TaskHistoryStore.save(this@JarvisVoiceService, finishedTask)
            }
                }

        if (!completed) {
            updateNotification("Task cancelled")
            return
        }

        conversationHistory.add(
            GeminiMessage("assistant", "टास्क रिपोर्ट: $summary")
        )

        while (conversationHistory.size > 12) {
            conversationHistory.removeAt(0)
        }

        speakAndResume(summary)
    }

    fun runStep(index: Int) {
        if (serviceStopped) return

        if (AgentTaskManager.isCancelled(taskId)) {
            updateNotification("Task cancelled")
            return
        }

        if (index >= actions.size) {
            finishPlan()
            return
        }

        val step = actions[index]

        AgentTaskManager.update(
    taskId,
    index,
    step.description.ifBlank { step.action }
)

updateNotification(
    "Task progress: ${index + 1}/${actions.size} — " +
    step.description.ifBlank { step.action }
)

        when (step.action) {
            "answer_only" -> {
                results.add(step.description)
                runStep(index + 1)
            }

            "battery_status" -> {
                results.add(
                    AndroidActionExecutor.getBatteryStatus(this)
                )
                runStep(index + 1)
            }

            "weather" -> {
                AndroidActionExecutor.getWeather(step.target) { report ->
                    handler.post {
                        if (!serviceStopped &&
                            !AgentTaskManager.isCancelled(taskId)
                        ) {
                            results.add(report)
                            runStep(index + 1)
                        }
                    }
                }
            }

            "open_app" -> {
                val success = openApprovedApp(step.target)

                if (success) {
                    results.add("${step.target} ऐप खुल गया।")
                } else {
                    results.add("${step.target} ऐप नहीं खुल पाया।")
                    failedActions.add(step)
                }

                handler.postDelayed(
                    { runStep(index + 1) },
                    1200
                )
            }

            "youtube_search" -> {
                val success = AndroidActionExecutor.searchYouTube(
                    this, step.target
                )

                if (success) {
                    results.add("YouTube खोज खुल गई।")
                } else {
                    results.add("YouTube खोज नहीं खुल पाई।")
                    failedActions.add(step)
                }

                handler.postDelayed(
                    { runStep(index + 1) },
                    1200
                )
            }

            "web_search" -> {
                val success = AndroidActionExecutor.searchWeb(
                    this, step.target
                )

                if (success) {
                    results.add("वेब खोज खुल गई।")
                } else {
                    results.add("वेब खोज नहीं खुल पाई।")
                    failedActions.add(step)
                }

                handler.postDelayed(
                    { runStep(index + 1) },
                    1200
                )
            }

            "share_text", "draft_message" -> {
                val success = AndroidActionExecutor.shareText(
                    this, step.target
                )

                if (success) {
                    results.add(
                        "Share विकल्प खुल गया। संदेश जाँचकर खुद भेजें।"
                    )
                } else {
                    results.add("Share विकल्प नहीं खुल पाया।")
                    failedActions.add(step)
                }

                finishPlan()
            }

            else -> {
                results.add(
                    "यह काम अभी समर्थित नहीं है: ${step.description}"
                )
                runStep(index + 1)
            }
        }
    }

    runStep(0)
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

    val odiaCount = message.count { it.code in 0x0B00..0x0B7F }
    val hindiCount = message.count { it.code in 0x0900..0x097F }
    val englishCount = message.count { it in 'A'..'Z' || it in 'a'..'z' }

    val preferredLocale = when {
        odiaCount > 0 -> Locale("or", "IN")
        hindiCount > 0 -> Locale("hi", "IN")
        englishCount > 0 -> Locale("en", "IN")
        else -> Locale("hi", "IN")
    }

    try {
        val result = tts?.setLanguage(preferredLocale)

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts?.setLanguage(Locale("hi", "IN"))
        }
    } catch (_: Exception) {
        tts?.setLanguage(Locale.getDefault())
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

    val cancelIntent = Intent(this, JarvisVoiceService::class.java)
        .setAction(ACTION_CANCEL_TASK)

    val cancelPendingIntent = PendingIntent.getService(
        this,
        103,
        cancelIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("D.N. MARK 1")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel Task",
            cancelPendingIntent
        )
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

private fun saveInterruptedTask(reason: String) {
    val cancelled = AgentTaskManager.cancelCurrent(reason)

    if (cancelled) {
        AgentTaskManager.snapshot()?.let { task ->
            TaskHistoryStore.save(this, task)
        }
    }
}

    private fun stopJarvis() {
        saveInterruptedTask(
    "Jarvis service बंद होने के कारण task बीच में रुक गया।"
)
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
        saveInterruptedTask(
    "Jarvis service बंद होने के कारण task बीच में रुक गया।"
)
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
private fun lowerQuestionForRetry(value: String): String {
    return value.lowercase(Locale.ROOT)
}

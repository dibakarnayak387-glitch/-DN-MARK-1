
package com.dn.mark1

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private val cyan = Color.rgb(0, 220, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(145, 180, 205)

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var statusView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(4, 12, 28)
        window.navigationBarColor = Color.rgb(4, 12, 28)

        tts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(4, 12, 30),
                    Color.rgb(8, 25, 52),
                    Color.rgb(3, 10, 24)
                )
            )
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(text("D.N. MARK 1", 27f, cyan, true))
        root.addView(text("PERSONAL AI ASSISTANT", 11f, muted, true))
        root.addView(space(18))

        val status = TextView(this).apply {
            text = "● SYSTEM ONLINE | VERSION 1.1"
            textSize = 11f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = panel()
        }
        statusView = status
        root.addView(status)
        root.addView(space(24))

        root.addView(text("YOUR AI CORE", 13f, muted, true))

        val core = CoreView()
        root.addView(core, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(230)
        ))

        core.setOnClickListener {
            startListening()
        }

        root.addView(text("TAP TO SPEAK", 14f, cyan, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { startListening() }
        })

        root.addView(space(25))
        root.addView(text("COMMAND CENTER", 17f, white, true))
        root.addView(space(12))

        val features = listOf(
            "VOICE ASSISTANT" to "Tap the central AI Core",
            "AI CHAT" to "Smart conversation",
            "WEATHER" to "Weather information",
            "BATTERY" to "Phone battery status",
            "WHATSAPP" to "Open WhatsApp by voice",
            "AI MEMORY" to "Assistant memory",
            "LANGUAGE" to "Hindi • Odia • English",
            "SETTINGS" to "System preferences"
        )

        for (i in features.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            row.addView(
                featureCard(features[i].first, features[i].second),
                LinearLayout.LayoutParams(0, dp(105), 1f)
            )

            row.addView(spaceWidth(10))

            if (i + 1 < features.size) {
                row.addView(
                    featureCard(features[i + 1].first, features[i + 1].second),
                    LinearLayout.LayoutParams(0, dp(105), 1f)
                )
            }

            root.addView(row)
            root.addView(space(10))
        }

        root.addView(space(12))
        root.addView(text(
            "D.N. MARK 1 • VOICE SYSTEM",
            11f, muted, true
        ).apply {
            gravity = Gravity.CENTER
        })

        setContentView(scroll)
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatus("Speech recognition unavailable")
            speak("Voice recognition is not available")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 101
            )
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        updateStatus("LISTENING...")
                    }

                    override fun onBeginningOfSpeech() {
                        updateStatus("VOICE DETECTED")
                    }

                    override fun onEndOfSpeech() {
                        updateStatus("PROCESSING...")
                    }

                    override fun onResults(results: Bundle?) {
                        val words = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        val command = words?.firstOrNull()

                        if (command != null) {
                            updateStatus("YOU: $command")
                            handleCommand(command)
                        } else {
                            updateStatus("NO VOICE DETECTED")
                        }
                    }

                    override fun onError(error: Int) {
                        updateStatus("VOICE ERROR - TAP TO RETRY")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer?.startListening(intent)
            updateStatus("STARTING MICROPHONE...")

        } catch (e: Exception) {
            updateStatus("VOICE SYSTEM ERROR")
        }
    }

    private fun handleCommand(command: String) {
        val input = command.lowercase(Locale.ROOT)

        when {
            input.contains("whatsapp") ||
            input.contains("व्हाट्सएप") -> {
                val launch = packageManager
                    .getLaunchIntentForPackage("com.whatsapp")

                if (launch != null) {
                    speak("Opening WhatsApp")
                    startActivity(launch)
                    updateStatus("WHATSAPP OPENED")
                } else {
                    speak("WhatsApp is not installed")
                    updateStatus("WHATSAPP NOT FOUND")
                }
            }

            input.contains("hello") ||
            input.contains("नमस्ते") ||
            input.contains("हेलो") -> {
                speak("नमस्ते! मैं D N MARK 1 हूँ।")
                updateStatus("COMMAND COMPLETED")
            }

            else -> {
                speak("मैंने आपकी बात सुनी। यह कमांड अभी उपलब्ध नहीं है।")
                updateStatus("COMMAND RECEIVED")
            }
        }
    }

    private fun speak(message: String) {
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "dn_reply"
        )
    }

    private fun updateStatus(message: String) {
        statusView?.text = "● $message"
    }

    private fun featureCard(title: String, subtitle: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            background = panel()
        }

        card.addView(text(title, 13f, cyan, true))
        card.addView(space(7))
        card.addView(text(subtitle, 11f, muted, false))
        return card
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD
                       else Typeface.DEFAULT
        }
    }

    private fun panel(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.rgb(10, 31, 57))
            setStroke(dp(1), Color.rgb(0, 130, 180))
            cornerRadius = dp(13).toFloat()
        }
    }

    private fun space(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }

    private fun spaceWidth(width: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(width), 1)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else if (requestCode == 101) {
            updateStatus("MICROPHONE PERMISSION DENIED")
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    inner class CoreView : View(this@MainActivity) {

        private val paint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)

            val x = width / 2f
            val y = height / 2f
            val radius = dp(76).toFloat()

            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = cyan
            canvas.drawCircle(x, y, radius, paint)

            paint.strokeWidth = dp(1).toFloat()
            paint.alpha = 130
            canvas.drawCircle(x, y, radius + dp(10), paint)
            canvas.drawCircle(x, y, radius - dp(10), paint)

            paint.style = android.graphics.Paint.Style.FILL
            paint.alpha = 255
            paint.color = Color.rgb(10, 75, 115)
            canvas.drawCircle(x, y, radius - dp(18), paint)

            paint.color = cyan
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = dp(24).toFloat()
            canvas.drawText("MARK 1", x, y + dp(8), paint)

            paint.textSize = dp(10).toFloat()
            canvas.drawText("AI CORE", x, y + dp(28), paint)
        }
    }
}

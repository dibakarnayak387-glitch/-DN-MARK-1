
package com.dn.mark1

import android.app.role.RoleManager
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
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

    private var statusView: TextView? = null
    private var startButton: TextView? = null
    private var tts: TextToSpeech? = null

    companion object {
        private const val REQUEST_MICROPHONE = 103
        private const val REQUEST_NOTIFICATIONS = 104
        private const val REQUEST_ASSISTANT_ROLE = 105
    }

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
        root.addView(space(12))

        val startJarvisButton = TextView(this).apply {
            text = "START JARVIS"
            textSize = 14f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = panel()

            setOnClickListener {
                requestMicrophoneAndStart()
            }
        }

        startButton = startJarvisButton
        root.addView(startJarvisButton)
        root.addView(space(10))

val assistantButton = TextView(this).apply {
    text = "SET AS DEFAULT ASSISTANT"
    textSize = 14f
    setTextColor(white)
    gravity = Gravity.CENTER
    setPadding(dp(12), dp(16), dp(12), dp(16))
    background = panel()

    setOnClickListener {
        requestDefaultAssistantRole()
    }
}

root.addView(assistantButton)

        root.addView(space(10))
        root.addView(text(
            "Start once, then say Jarvis while the voice service is active.",
            11f, muted, false
        ))

        root.addView(space(24))
        root.addView(text("YOUR AI CORE", 13f, muted, true))

        root.addView(
            CoreView(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230)
            )
        )

        root.addView(text(
            "BACKGROUND VOICE READY • USE START JARVIS",
            11f, muted, true
        ).apply {
            gravity = Gravity.CENTER
        })

        root.addView(space(20))

        val downloadButton = TextView(this).apply {
            text = "DOWNLOAD HINDI VOICE MODEL"
            textSize = 14f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
            background = panel()

            setOnClickListener {
                isEnabled = false
                text = "DOWNLOADING... 0%"

                ModelDownloader.download(
                    this@MainActivity,
                    { progress ->
                        runOnUiThread {
                            text = "DOWNLOADING... $progress%"
                        }
                    },
                    { result ->
                        runOnUiThread {
                            isEnabled = true

                            if (result.isSuccess) {
                                text = "HINDI MODEL READY"
                                updateStatus("HINDI MODEL INSTALLED")

                                tts?.speak(
                                    "Hindi voice model ready",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "model_ready"
                                )
                            } else {
                                text = "DOWNLOAD FAILED - RETRY"
                                updateStatus("MODEL DOWNLOAD FAILED")
                            }
                        }
                    }
                )
            }
        }

        root.addView(downloadButton)
        root.addView(space(25))
        root.addView(text("COMMAND CENTER", 17f, white, true))
        root.addView(space(12))

        val features = listOf(
            "VOICE ASSISTANT" to "Say Jarvis to activate",
            "AI CHAT" to "Smart conversation via Gemini",
            "WEATHER" to "Weather information",
            "BATTERY" to "Phone battery status",
            "WHATSAPP" to "Open WhatsApp by voice",
            "AI MEMORY" to "Assistant memory",
            "LANGUAGE" to "Hindi • Odia • English*",
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

        root.addView(text(
            "*Current offline voice recognition uses the installed Hindi Vosk model.",
            10f, muted, false
        ))

        root.addView(space(12))
        root.addView(text(
            "D.N. MARK 1 • VOICE SYSTEM",
            11f, muted, true
        ).apply {
            gravity = Gravity.CENTER
        })

        setContentView(scroll)
    }
private fun requestDefaultAssistantRole() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        updateStatus("ANDROID 10 OR NEWER REQUIRED")
        return
    }

    val roleManager = getSystemService(RoleManager::class.java)

    if (!roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        updateStatus("ASSISTANT ROLE NOT AVAILABLE")
        return
    }

    if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
        updateStatus("JARVIS IS ALREADY DEFAULT ASSISTANT")
        return
    }

    startActivityForResult(
        roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
        REQUEST_ASSISTANT_ROLE
    )
}

override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQUEST_ASSISTANT_ROLE) {
        val manager = getSystemService(RoleManager::class.java)

        updateStatus(
            if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT))
                "D.N. MARK 1 IS DEFAULT ASSISTANT"
            else
                "DEFAULT ASSISTANT NOT SELECTED"
        )
    }
}
    private fun requestMicrophoneAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_MICROPHONE
            )
            return
        }

        requestNotificationPermissionOrStart()
    }

    private fun requestNotificationPermissionOrStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
            return
        }

        startJarvisService()
    }

    private fun startJarvisService() {
        try {
            val intent = Intent(
                this,
                JarvisVoiceService::class.java
            ).apply {
                action = JarvisVoiceService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            updateStatus("JARVIS BACKGROUND VOICE SERVICE STARTED")
            startButton?.text = "JARVIS SERVICE STARTED"

        } catch (e: Exception) {
            updateStatus(
                "COULD NOT START JARVIS: ${e.message ?: "CHECK PERMISSIONS"}"
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        when (requestCode) {
            REQUEST_MICROPHONE -> {
                if (
                    grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionOrStart()
                } else {
                    updateStatus("MICROPHONE PERMISSION REQUIRED")
                }
            }

            REQUEST_NOTIFICATIONS -> {
                startJarvisService()
            }
        }
    }

    private fun updateStatus(message: String) {
        statusView?.text = "● $message"
    }

    private fun featureCard(
        title: String,
        subtitle: String
    ): View {
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
            typeface = if (bold) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
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

    override fun onDestroy() {
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
            paint.alpha = 255
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

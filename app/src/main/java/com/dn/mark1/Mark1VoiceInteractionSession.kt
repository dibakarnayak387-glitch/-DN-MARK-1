package com.dn.mark1

import android.content.Context
import android.graphics.Color
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.widget.TextView

class Mark1VoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        return TextView(context).apply {
            text = "D.N. MARK 1\nVoice Assistant Ready"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(4, 18, 38))
            setPadding(32, 32, 32, 32)
        }
    }
}

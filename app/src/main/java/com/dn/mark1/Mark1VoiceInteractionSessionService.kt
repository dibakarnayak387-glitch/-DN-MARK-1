package com.dn.mark1

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class Mark1VoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession {
        return Mark1VoiceInteractionSession(this)
    }
}

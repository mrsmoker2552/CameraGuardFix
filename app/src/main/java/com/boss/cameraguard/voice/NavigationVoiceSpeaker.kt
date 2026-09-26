package com.boss.cameraguard.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Owns route TTS separately from the safety-camera warning audio engine. */
class NavigationVoiceSpeaker(context: Context, private val onReady: (Boolean) -> Unit) {
    private var engine: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (status == TextToSpeech.SUCCESS && tts != null) {
                val code = tts.setLanguage(Locale.ENGLISH)
                ready = code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED
            } else ready = false
            main.post { onReady(ready) }
        }
    }

    fun speak(instruction: String) {
        if (ready && instruction.isNotBlank()) {
            engine?.speak(instruction, TextToSpeech.QUEUE_ADD, null, "route-guidance")
        }
    }

    fun stop() { engine?.stop() }
    fun shutdown() { ready = false; engine?.stop(); engine?.shutdown(); engine = null }
}

package com.boss.cameraguard.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceWarningManager(context: Context) : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var ready = false

    // Three selectable voice presets. Android TTS voice availability
    // (tts.voices) varies wildly by device/engine/installed language packs,
    // so named/distinct "voices" are not guaranteed to exist on every
    // device. To make "3 selectable voices" actually work everywhere, each
    // preset instead applies a distinct, deterministic pitch/rate profile
    // on top of whatever single TTS voice the device provides. This is an
    // honest trade-off: it's a real, always-working difference in how
    // warnings sound, not a claim that three separate named voice actors
    // are being used.
    @Volatile
    private var preset: Int = 0

    init { textToSpeech = TextToSpeech(context.applicationContext, this) }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.ENGLISH)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            applyPreset()
        }
    }

    fun setVoicePreset(newPreset: Int) {
        preset = newPreset.coerceIn(0, 2)
        applyPreset()
    }

    private fun applyPreset() {
        when (preset) {
            0 -> { textToSpeech?.setPitch(1.0f); textToSpeech?.setSpeechRate(1.0f) }
            1 -> { textToSpeech?.setPitch(0.78f); textToSpeech?.setSpeechRate(0.95f) }
            else -> { textToSpeech?.setPitch(1.25f); textToSpeech?.setSpeechRate(1.08f) }
        }
    }

    fun speakSpeedCamera(speedLimit: Int?) = speak(
        if (speedLimit != null) "Boss, speed camera ahead. Speed limit $speedLimit kilometers per hour."
        else "Boss, speed camera ahead."
    )

    fun speakRedLightCamera(speedLimit: Int?) = speak(
        if (speedLimit != null) "Boss, red light camera ahead. Speed limit $speedLimit kilometers per hour. Drive carefully."
        else "Boss, red light camera ahead. Drive carefully."
    )

    fun speakBusLaneCamera() = speak("Boss, reserved or bus lane camera ahead. Check lane restrictions.")
    fun speakNoEntryCamera() = speak("Boss, no entry enforcement ahead. Do not enter if access is restricted.")
    fun speakZtlCamera() = speak("Boss, ZTL restricted access camera ahead. Check that you are authorised to enter.")
    fun speakAverageSpeedCamera(speedLimit: Int?) = speak(
        if (speedLimit != null) "Boss, average speed control ahead. Maintain an average speed within $speedLimit kilometers per hour."
        else "Boss, average speed section control ahead. Watch your average speed."
    )
    fun speakMobilePhoneCamera() = speak("Boss, mobile phone and seatbelt enforcement camera ahead. Keep your phone away and seatbelt fastened.")
    fun speakOtherEnforcementCamera() = speak("Boss, traffic enforcement camera ahead. Drive carefully and follow road restrictions.")

    private fun speak(message: String) {
        if (!ready) return
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "CameraGuardWarning")
    }

    fun stop() { textToSpeech?.stop() }
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ready = false
    }
}

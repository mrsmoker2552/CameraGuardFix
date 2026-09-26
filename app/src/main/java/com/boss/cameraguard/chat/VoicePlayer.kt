package com.boss.cameraguard.chat

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays back one voice note's remote URL at a time. Deliberately a single shared instance
 * per screen (see rememberVoicePlayer) rather than one MediaPlayer per bubble, so starting a
 * second playback always stops the first - incoming voice notes never auto-play (the caller
 * only ever invokes [play] from an explicit tap).
 */
class VoicePlayer(private val scope: CoroutineScope) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var progressFraction by mutableStateOf(0f)
        private set
    var failedMessageId by mutableStateOf<String?>(null)
        private set

    fun play(messageId: String, url: String) {
        if (playingMessageId == messageId) {
            pause()
            return
        }
        stop()
        failedMessageId = null
        val mp = MediaPlayer()
        mediaPlayer = mp
        playingMessageId = messageId
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                it.start()
                trackProgress()
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                failedMessageId = messageId
                stop()
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            failedMessageId = messageId
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        mediaPlayer?.let { runCatching { it.stop() }; it.release() }
        mediaPlayer = null
        playingMessageId = null
        progressFraction = 0f
    }

    private fun trackProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val mp = mediaPlayer ?: break
                val duration = mp.duration.coerceAtLeast(1)
                progressFraction = (mp.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                delay(120)
            }
        }
    }
}

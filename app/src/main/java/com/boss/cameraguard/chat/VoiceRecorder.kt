package com.boss.cameraguard.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

/**
 * Records a single AAC/M4A voice note to a temp file in the app's cache directory. One
 * recorder instance = one recording; call [cancel] or [stopAndFinish] exactly once, then
 * discard the instance. Never starts recording on construction - only [start] does, which
 * is only ever called right after the user taps "record" and RECORD_AUDIO is granted (see
 * ChatThreadScreen), so there is no background/hidden recording.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set
    private var startedAtMs: Long = 0L

    val isRecording: Boolean get() = recorder != null

    @Throws(IOException::class)
    fun start() {
        check(recorder == null) { "Already recording." }
        val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.setMaxDuration(120_000) // 2 minute cap, matches ChatRepository's storage size cap.
        r.prepare()
        r.start()
        recorder = r
        outputFile = file
        startedAtMs = System.currentTimeMillis()
    }

    fun elapsedMs(): Long = if (recorder != null) System.currentTimeMillis() - startedAtMs else 0L

    /** Returns the finished file (caller uploads it, then MUST call [deleteOutputFile] once
     *  done - see ChatThreadScreen), or null if nothing was recorded. */
    fun stopAndFinish(): File? {
        val r = recorder ?: return null
        return try {
            r.stop()
            outputFile
        } catch (_: RuntimeException) {
            // stop() throws if called too soon after start() with no audio captured.
            outputFile?.delete()
            null
        } finally {
            r.release()
            recorder = null
        }
    }

    fun cancel() {
        val r = recorder
        recorder = null
        try {
            r?.stop()
        } catch (_: RuntimeException) {
            // no audio captured - nothing to clean up beyond the file below.
        } finally {
            r?.release()
        }
        deleteOutputFile()
    }

    fun deleteOutputFile() {
        outputFile?.delete()
        outputFile = null
    }
}

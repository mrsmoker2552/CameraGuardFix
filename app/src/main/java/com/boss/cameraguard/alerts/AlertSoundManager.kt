package com.boss.cameraguard.alerts

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class AlertSoundManager {

    private var toneGenerator: ToneGenerator? =
        ToneGenerator(
            AudioManager.STREAM_MUSIC,
            95
        )

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var warningInProgress =
        false

    private val scheduledTasks =
        mutableListOf<Runnable>()

    fun playCameraBeep() {

        if (
            warningInProgress
        ) {
            return
        }

        warningInProgress =
            true

        /*
         * Premium 3-second warning pattern:
         *
         * 0.00s   ─ BEEP
         * 0.55s   ─ BEEP
         * 1.10s   ─ stronger alert
         * 1.70s   ─ BEEP
         * 2.20s   ─ final longer alert
         *
         * Total warning window ≈ 3 seconds.
         */
        scheduleTone(
            delayMillis = 0L,
            tone =
                ToneGenerator.TONE_PROP_BEEP2,
            durationMillis = 400
        )

        scheduleTone(
            delayMillis = 550L,
            tone =
                ToneGenerator.TONE_PROP_BEEP2,
            durationMillis = 400
        )

        scheduleTone(
            delayMillis = 1100L,
            tone =
                ToneGenerator.TONE_SUP_ERROR,
            durationMillis = 400
        )

        scheduleTone(
            delayMillis = 1650L,
            tone =
                ToneGenerator.TONE_PROP_BEEP2,
            durationMillis = 400
        )

        scheduleTone(
            delayMillis = 2200L,
            tone =
                ToneGenerator.TONE_SUP_ERROR,
            durationMillis = 800
        )

        val finishTask =
            Runnable {

                warningInProgress =
                    false

                scheduledTasks.clear()
            }

        scheduledTasks.add(
            finishTask
        )

        handler.postDelayed(
            finishTask,
            3050L
        )
    }

    private fun scheduleTone(
        delayMillis: Long,
        tone: Int,
        durationMillis: Int
    ) {

        val task =
            Runnable {

                toneGenerator
                    ?.startTone(
                        tone,
                        durationMillis
                    )
            }

        scheduledTasks.add(
            task
        )

        handler.postDelayed(
            task,
            delayMillis
        )
    }

    fun release() {

        scheduledTasks.forEach {

            handler.removeCallbacks(
                it
            )
        }

        scheduledTasks.clear()

        handler.removeCallbacksAndMessages(
            null
        )

        warningInProgress =
            false

        toneGenerator
            ?.stopTone()

        toneGenerator
            ?.release()

        toneGenerator =
            null
    }
}
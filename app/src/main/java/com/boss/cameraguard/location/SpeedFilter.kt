package com.boss.cameraguard.location

import java.util.ArrayDeque

class SpeedFilter {

    private val samples = ArrayDeque<Float>()

    private val maxSamples = 4

    fun update(rawSpeedKmh: Float): Float {

        val cleanedSpeed =
            when {
                rawSpeedKmh < 2.0f -> 0f
                rawSpeedKmh > 220f -> return current()
                else -> rawSpeedKmh
            }

        samples.addLast(cleanedSpeed)

        while (samples.size > maxSamples) {
            samples.removeFirst()
        }

        return current()
    }

    fun current(): Float {

        if (samples.isEmpty()) {
            return 0f
        }

        return samples.average().toFloat()
    }

    fun reset() {
        samples.clear()
    }
}
package com.boss.cameraguard.location

/**
 * Smooths a GPS course-over-ground reading for VISUAL use only (the HUD's 3D road/mini-map
 * rotation and heading readout). This is intentionally separate from any value used by the
 * camera-warning engine, direction-gating, or navigation logic elsewhere in the app - those
 * keep reading the raw/filtered Location bearing exactly as before. Nothing here changes what
 * the app treats as the "real" heading, only what a display animates towards.
 *
 * Two problems this fixes, both purely cosmetic but with real safety-adjacent side effects
 * (a HUD that looks broken is a HUD a rider stops trusting/looking at):
 *  - GPS course-over-ground is inherently noisy below a few km/h (a device sitting still or
 *    creeping in traffic can report a bearing that swings by dozens of degrees between
 *    updates, even though the rider isn't actually turning). Below [MIN_RELIABLE_SPEED_KMH]
 *    the last trusted heading is held instead of jumping to a fresh, unreliable reading.
 *  - Even above that speed, consecutive GPS fixes can still disagree by a few degrees; a
 *    light exponential smoothing pass removes the resulting jitter without adding perceptible
 *    lag at riding speeds.
 */
class HeadingFilter {

    private var smoothedDegrees: Float? = null

    /**
     * @param rawBearingDegrees the device's current GPS bearing (0-360, meaningless if
     *   [hasBearing] is false)
     * @param hasBearing whether the location fix actually reports a bearing
     * @param speedKmh current (already speed-filtered) speed, used only to decide whether this
     *   update's bearing is trustworthy enough to move the smoothed value at all
     * @return a smoothed heading in degrees [0, 360); 0 until a first trustworthy reading ever
     *   arrives
     */
    fun update(rawBearingDegrees: Float, hasBearing: Boolean, speedKmh: Float): Float {
        val current = smoothedDegrees
        if (!hasBearing || speedKmh < MIN_RELIABLE_SPEED_KMH) {
            // Not a reliable course-over-ground reading right now - hold the last trusted
            // value rather than rotating the HUD to GPS noise while slow/stopped.
            return current ?: 0f
        }
        val raw = ((rawBearingDegrees % 360f) + 360f) % 360f
        if (current == null) {
            smoothedDegrees = raw
            return raw
        }
        // Shortest signed angular distance, so smoothing crosses the 0/360 wrap correctly
        // instead of spinning the long way around.
        val delta = ((raw - current + 540f) % 360f) - 180f
        val next = (current + delta * SMOOTHING_FACTOR + 360f) % 360f
        smoothedDegrees = next
        return next
    }

    fun reset() {
        smoothedDegrees = null
    }

    companion object {
        private const val MIN_RELIABLE_SPEED_KMH = 8f
        private const val SMOOTHING_FACTOR = 0.35f
    }
}

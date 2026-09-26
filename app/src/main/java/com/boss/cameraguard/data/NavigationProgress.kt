package com.boss.cameraguard.data

import android.location.Location
import org.maplibre.android.geometry.LatLng
import kotlin.math.*

/** Distance follows the route geometry, never straight-line distance to destination. */
data class NavigationProgress(
    val remainingMeters: Double,
    val remainingSeconds: Double,
    val nextInstruction: String,
    val nextTurnMeters: Double,
    val offRouteMeters: Double,
    val travelledMeters: Double
)

class NavigationProgressTracker(private val route: NavigationRoute) {
    private val points = route.points
    private val cumulative = DoubleArray(points.size)
    private var lastTravelled = 0.0
    init {
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + distance(points[i-1], points[i])
    }
    private val stepOffsets = route.steps.map { step ->
        val i = points.indices.minByOrNull { distance(points[it], step.point) } ?: 0
        cumulative.getOrElse(i) { 0.0 }
    }
    fun update(location: Location): NavigationProgress {
        val total = cumulative.lastOrNull() ?: 0.0
        val here = LatLng(location.latitude, location.longitude)
        val scale = cos(Math.toRadians(location.latitude))
        var bestDistance = Double.POSITIVE_INFINITY
        var travelled = 0.0
        for (i in 0 until points.size - 1) {
            fun x(p: LatLng) = (p.longitude - here.longitude) * 111320.0 * scale
            fun y(p: LatLng) = (p.latitude - here.latitude) * 111320.0
            val ax = x(points[i]); val ay = y(points[i])
            val dx = x(points[i+1]) - ax; val dy = y(points[i+1]) - ay
            val denominator = dx*dx + dy*dy
            val fraction = if (denominator > 0) (-(ax*dx + ay*dy)/denominator).coerceIn(0.0, 1.0) else 0.0
            val gap = hypot(ax + fraction*dx, ay + fraction*dy)
            val along = cumulative[i] + fraction*(cumulative[i+1] - cumulative[i])
            if (gap < bestDistance - 2.0 || (abs(gap-bestDistance) <= 2.0 && abs(along-lastTravelled) < abs(travelled-lastTravelled))) {
                bestDistance = gap; travelled = along
            }
        }
        lastTravelled = travelled
        val remaining = (total - travelled).coerceAtLeast(0.0)
        val next = stepOffsets.indices.firstOrNull { stepOffsets[it] > travelled + 8.0 }
        return NavigationProgress(remaining,
            if (total > 0) route.durationSeconds * remaining / total else 0.0,
            next?.let { route.steps[it].instruction } ?: "Continue to destination",
            next?.let { (stepOffsets[it]-travelled).coerceAtLeast(0.0) } ?: remaining,
            bestDistance, travelled)
    }
    private fun distance(a: LatLng, b: LatLng): Double {
        val out = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
        return out[0].toDouble()
    }
}

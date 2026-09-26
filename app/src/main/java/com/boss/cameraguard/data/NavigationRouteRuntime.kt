package com.boss.cameraguard.data

import org.maplibre.android.geometry.LatLng

/** Shared in-memory navigation state. Map tab owns route selection; HUD/mini-map only render it. */
data class NavigationRoute(
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val active: Boolean = false,
    val steps: List<NavigationStep> = emptyList()
)

data class NavigationStep(val instruction: String, val point: LatLng)

object NavigationRouteRuntime {
    @Volatile var route: NavigationRoute? = null
    @Volatile var revision: Int = 0
    fun updateRoute(value: NavigationRoute?) { route = value; revision++ }
    fun start() { route = route?.copy(active = true); revision++ }
    fun clear() { route = null; revision++ }
}

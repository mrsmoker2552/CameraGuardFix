package com.boss.cameraguard.data

import android.content.Context

data class RoutePreferences(
    val avoidAutostrada: Boolean = false,
    val avoidTangenziale: Boolean = false,
    val avoidTollRoads: Boolean = false
)

class RoutePreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_guard_route_preferences", Context.MODE_PRIVATE)
    fun load() = RoutePreferences(
        avoidAutostrada = prefs.getBoolean("avoid_autostrada", false),
        avoidTangenziale = prefs.getBoolean("avoid_tangenziale", false),
        avoidTollRoads = prefs.getBoolean("avoid_tolls", false)
    )
    fun save(value: RoutePreferences) {
        prefs.edit()
            .putBoolean("avoid_autostrada", value.avoidAutostrada)
            .putBoolean("avoid_tangenziale", value.avoidTangenziale)
            .putBoolean("avoid_tolls", value.avoidTollRoads)
            .apply()
    }
}

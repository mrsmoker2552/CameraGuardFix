package com.boss.cameraguard.data

import android.content.Context

/** Persistent display-only filter for camera markers on the Map screen. */
class CameraMapFilterStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_guard_map_camera_filters", Context.MODE_PRIVATE)

    fun load(): Set<RealCameraType> {
        val saved = prefs.getStringSet(KEY_TYPES, null) ?: return RealCameraType.entries.toSet()
        return saved.mapNotNull { runCatching { RealCameraType.valueOf(it) }.getOrNull() }.toSet()
    }

    fun save(types: Set<RealCameraType>) {
        prefs.edit().putStringSet(KEY_TYPES, types.map { it.name }.toSet()).apply()
    }

    private companion object { const val KEY_TYPES = "visible_camera_types" }
}

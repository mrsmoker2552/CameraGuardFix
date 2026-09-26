package com.boss.cameraguard.data

import android.content.Context

class CameraOverrideStore(
    context: Context
) {
    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun suppress(camera: RealCamera) {
        if (camera.source == RealCameraSource.MANUAL) return
        val current = hiddenKeys().toMutableSet()
        current.add(camera.stableSourceKey())
        save(current)
    }

    fun suppressKey(key: String) {
        val current = hiddenKeys().toMutableSet()
        current.add(key)
        save(current)
    }

    fun restoreKey(key: String) {
        val current = hiddenKeys().toMutableSet()
        current.remove(key)
        save(current)
    }

    fun restoreAll() {
        preferences.edit().remove(KEY_HIDDEN).apply()
    }

    fun isSuppressed(camera: RealCamera): Boolean {
        return camera.source != RealCameraSource.MANUAL &&
            hiddenKeys().contains(camera.stableSourceKey())
    }

    fun filterVisible(cameras: List<RealCamera>): List<RealCamera> {
        val hidden = hiddenKeys()
        if (hidden.isEmpty()) return cameras
        return cameras.filterNot {
            it.source != RealCameraSource.MANUAL && hidden.contains(it.stableSourceKey())
        }
    }

    fun count(): Int = hiddenKeys().size

    private fun hiddenKeys(): Set<String> {
        return preferences.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()
    }

    private fun save(keys: Set<String>) {
        preferences.edit().putStringSet(KEY_HIDDEN, keys).apply()
    }

    companion object {
        private const val PREFS_NAME = "camera_guard_overrides"
        private const val KEY_HIDDEN = "hidden_osm_camera_keys"
    }
}

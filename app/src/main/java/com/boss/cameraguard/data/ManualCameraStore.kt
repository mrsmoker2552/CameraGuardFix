package com.boss.cameraguard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ManualCameraStore(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun load(): List<RealCamera> {
        val json =
            preferences.getString(
                KEY_CAMERAS,
                null
            ) ?: return emptyList()

        return parseJson(json)
    }

    fun add(
        latitude: Double,
        longitude: Double,
        type: RealCameraType,
        speedLimit: Int?,
        monitoredBearing: Float? = null,
        userNote: String? = null,
        verifiedByUser: Boolean = true,
        replacesSourceKey: String? = null
    ): RealCamera {
        val current = load().toMutableList()

        val camera =
            RealCamera(
                id = -System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                type = type,
                speedLimit = speedLimit,
                direction = null,
                source = RealCameraSource.MANUAL,
                monitoredBearing = monitoredBearing,
                relationId = null,
                userNote = userNote?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMillis = System.currentTimeMillis(),
                verifiedByUser = verifiedByUser,
                replacesSourceKey = replacesSourceKey
            )

        current.add(camera)
        saveAll(current)
        return camera
    }

    fun update(
        cameraId: Long,
        type: RealCameraType,
        speedLimit: Int?,
        userNote: String?,
        monitoredBearing: Float? = null,
        // The direction picker is now a real editable field, so a null
        // monitoredBearing can mean either "the caller didn't touch
        // direction, keep whatever was saved" (clearMonitoredBearing =
        // false, old default) or "the user explicitly cleared/left it
        // unset" (clearMonitoredBearing = true). Without this flag a
        // deliberate clear could never be persisted, since null and
        // "untouched" were indistinguishable.
        clearMonitoredBearing: Boolean = false
    ): Boolean {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == cameraId }
        if (index < 0) return false

        val old = current[index]
        current[index] = old.copy(
            type = type,
            speedLimit = speedLimit,
            userNote = userNote?.trim()?.takeIf { it.isNotEmpty() },
            monitoredBearing =
                when {
                    monitoredBearing != null -> monitoredBearing
                    clearMonitoredBearing -> null
                    else -> old.monitoredBearing
                },
            verifiedByUser = true
        )
        saveAll(current)
        return true
    }

    fun delete(cameraId: Long): RealCamera? {
        val current = load()
        val removed = current.firstOrNull { it.id == cameraId }
        if (removed != null) {
            saveAll(current.filterNot { it.id == cameraId })
        }
        return removed
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "CameraGuardManualCameras")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("cameras", camerasToJson(load()))
        return root.toString(2)
    }

    fun importJson(json: String): Int {
        val imported = try {
            val root = JSONObject(json)
            val array = root.optJSONArray("cameras") ?: JSONArray()
            parseArray(array)
        } catch (_: Exception) {
            // Backward compatibility with older raw-array backups.
            try {
                parseArray(JSONArray(json))
            } catch (_: Exception) {
                emptyList()
            }
        }

        if (imported.isEmpty()) return 0

        val merged = LinkedHashMap<Long, RealCamera>()
        load().forEach { merged[it.id] = it }
        imported.forEach { camera ->
            val normalized = camera.copy(
                source = RealCameraSource.MANUAL,
                verifiedByUser = true,
                createdAtMillis = camera.createdAtMillis ?: System.currentTimeMillis()
            )
            merged[normalized.id] = normalized
        }

        saveAll(merged.values.toList())
        return imported.size
    }

    private fun saveAll(cameras: List<RealCamera>) {
        preferences
            .edit()
            .putString(
                KEY_CAMERAS,
                camerasToJson(cameras).toString()
            )
            .apply()
    }

    private fun parseJson(json: String): List<RealCamera> {
        return try {
            parseArray(JSONArray(json))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseArray(array: JSONArray): List<RealCamera> {
        val cameras = mutableListOf<RealCamera>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            val type =
                runCatching {
                    RealCameraType.valueOf(item.getString("type"))
                }.getOrNull() ?: continue

            val lat = item.optDouble("latitude", Double.NaN)
            val lon = item.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val speedLimit =
                if (item.has("speedLimit") && !item.isNull("speedLimit")) {
                    item.optInt("speedLimit")
                } else null

            val monitoredBearing =
                if (item.has("monitoredBearing") && !item.isNull("monitoredBearing")) {
                    item.optDouble("monitoredBearing").toFloat()
                } else null

            cameras.add(
                RealCamera(
                    id = item.optLong("id", -System.currentTimeMillis() - index),
                    latitude = lat,
                    longitude = lon,
                    type = type,
                    speedLimit = speedLimit,
                    direction = null,
                    source = RealCameraSource.MANUAL,
                    monitoredBearing = monitoredBearing,
                    relationId = null,
                    userNote = item.optString("userNote").takeIf { it.isNotBlank() },
                    createdAtMillis =
                        if (item.has("createdAtMillis")) item.optLong("createdAtMillis") else null,
                    verifiedByUser = item.optBoolean("verifiedByUser", true),
                    replacesSourceKey =
                        item.optString("replacesSourceKey").takeIf { it.isNotBlank() }
                )
            )
        }

        return cameras
    }

    private fun camerasToJson(cameras: List<RealCamera>): JSONArray {
        val array = JSONArray()

        cameras.forEach { camera ->
            val item = JSONObject()
                .put("id", camera.id)
                .put("latitude", camera.latitude)
                .put("longitude", camera.longitude)
                .put("type", camera.type.name)
                .put("verifiedByUser", camera.verifiedByUser)

            camera.speedLimit?.let { item.put("speedLimit", it) }
            camera.monitoredBearing?.let { item.put("monitoredBearing", it) }
            camera.userNote?.let { item.put("userNote", it) }
            camera.createdAtMillis?.let { item.put("createdAtMillis", it) }
            camera.replacesSourceKey?.let { item.put("replacesSourceKey", it) }

            array.put(item)
        }

        return array
    }

    companion object {
        private const val PREFS_NAME = "camera_guard_manual_cameras"
        private const val KEY_CAMERAS = "manual_cameras"
    }
}

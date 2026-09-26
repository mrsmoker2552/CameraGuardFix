package com.boss.cameraguard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CachedCameraData(
    val cameras: List<RealCamera>,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val savedAtMillis: Long
)

class CameraCache(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "camera_guard_camera_cache",
            Context.MODE_PRIVATE
        )

    fun save(
        cameras: List<RealCamera>,
        centerLatitude: Double,
        centerLongitude: Double
    ) {

        if (cameras.isEmpty()) {
            return
        }

        val array =
            JSONArray()

        cameras.forEach { camera ->

            val objectJson =
                JSONObject()

            objectJson.put(
                "id",
                camera.id
            )

            objectJson.put(
                "latitude",
                camera.latitude
            )

            objectJson.put(
                "longitude",
                camera.longitude
            )

            objectJson.put(
                "type",
                camera.type.name
            )

            if (camera.speedLimit != null) {

                objectJson.put(
                    "speedLimit",
                    camera.speedLimit
                )
            }

            if (camera.direction != null) {

                objectJson.put(
                    "direction",
                    camera.direction
                )
            }

            objectJson.put(
                "source",
                camera.source.name
            )

            if (camera.monitoredBearing != null) {

                objectJson.put(
                    "monitoredBearing",
                    camera.monitoredBearing
                )
            }

            if (camera.relationId != null) {

                objectJson.put(
                    "relationId",
                    camera.relationId
                )
            }

            if (camera.approachPath.isNotEmpty()) {

                val pathJson =
                    JSONArray()

                camera.approachPath.forEach { point ->

                    pathJson.put(
                        JSONObject()
                            .put("latitude", point.latitude)
                            .put("longitude", point.longitude)
                    )
                }

                objectJson.put(
                    "approachPath",
                    pathJson
                )
            }

            array.put(
                objectJson
            )
        }

        preferences
            .edit()
            .putInt(
                KEY_SCHEMA_VERSION,
                CURRENT_SCHEMA_VERSION
            )
            .putString(
                KEY_CAMERAS,
                array.toString()
            )
            .putLong(
                KEY_SAVED_AT,
                System.currentTimeMillis()
            )
            .putString(
                KEY_CENTER_LATITUDE,
                centerLatitude.toString()
            )
            .putString(
                KEY_CENTER_LONGITUDE,
                centerLongitude.toString()
            )
            .apply()
    }

    fun load(): CachedCameraData? {

        val schemaVersion =
            preferences.getInt(
                KEY_SCHEMA_VERSION,
                0
            )

        /*
         * v3 also stores local OSM approach-road geometry. Old cache data
         * is intentionally ignored once so the updated APK fetches the
         * improved same-road dataset.
         */
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return null
        }

        val json =
            preferences.getString(
                KEY_CAMERAS,
                null
            )
                ?: return null

        val centerLatitude =
            preferences.getString(
                KEY_CENTER_LATITUDE,
                null
            )
                ?.toDoubleOrNull()
                ?: return null

        val centerLongitude =
            preferences.getString(
                KEY_CENTER_LONGITUDE,
                null
            )
                ?.toDoubleOrNull()
                ?: return null

        val savedAt =
            preferences.getLong(
                KEY_SAVED_AT,
                0L
            )

        return try {

            val array =
                JSONArray(json)

            val cameras =
                mutableListOf<RealCamera>()

            for (index in 0 until array.length()) {

                val item =
                    array.getJSONObject(index)

                val type =
                    runCatching {

                        RealCameraType.valueOf(
                            item.getString("type")
                        )
                    }
                        .getOrNull()
                        ?: continue

                val source =
                    runCatching {

                        RealCameraSource.valueOf(
                            item.getString("source")
                        )
                    }
                        .getOrNull()
                        ?: continue

                val speedLimit =
                    if (item.has("speedLimit")) {
                        item.optInt("speedLimit")
                    } else {
                        null
                    }

                val direction =
                    if (item.has("direction")) {

                        item.optString("direction")
                            .takeIf {
                                it.isNotBlank()
                            }

                    } else {
                        null
                    }

                val monitoredBearing =
                    if (item.has("monitoredBearing")) {

                        item.optDouble("monitoredBearing")
                            .toFloat()

                    } else {
                        null
                    }

                val relationId =
                    if (item.has("relationId")) {
                        item.optLong("relationId")
                    } else {
                        null
                    }

                val approachPath =
                    mutableListOf<RoadPoint>()

                val pathJson =
                    item.optJSONArray("approachPath")

                if (pathJson != null) {

                    for (pathIndex in 0 until pathJson.length()) {

                        val point =
                            pathJson.optJSONObject(pathIndex)
                                ?: continue

                        val latitude =
                            point.optDouble("latitude", Double.NaN)

                        val longitude =
                            point.optDouble("longitude", Double.NaN)

                        if (!latitude.isNaN() && !longitude.isNaN()) {

                            approachPath.add(
                                RoadPoint(
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            )
                        }
                    }
                }

                cameras.add(
                    RealCamera(
                        id = item.getLong("id"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        type = type,
                        speedLimit = speedLimit,
                        direction = direction,
                        source = source,
                        monitoredBearing = monitoredBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (cameras.isEmpty()) {
                null
            } else {

                CachedCameraData(
                    cameras = cameras,
                    centerLatitude = centerLatitude,
                    centerLongitude = centerLongitude,
                    savedAtMillis = savedAt
                )
            }

        } catch (exception: Exception) {

            exception.printStackTrace()
            null
        }
    }

    fun clear() {

        preferences
            .edit()
            .clear()
            .apply()
    }

    companion object {

        private const val CURRENT_SCHEMA_VERSION =
            4

        private const val KEY_SCHEMA_VERSION =
            "schema_version"

        private const val KEY_CAMERAS =
            "cameras"

        private const val KEY_SAVED_AT =
            "saved_at"

        private const val KEY_CENTER_LATITUDE =
            "center_latitude"

        private const val KEY_CENTER_LONGITUDE =
            "center_longitude"
    }
}

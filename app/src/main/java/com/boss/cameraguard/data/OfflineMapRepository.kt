package com.boss.cameraguard.data

import android.content.Context
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Free offline map area downloads, backed by MapLibre's built-in OfflineManager against the
 * same free OpenFreeMap "liberty" vector style RealMapView already renders online (see
 * RealMapView.kt's Style.Builder().fromUri(...)) - no separate tile provider, account or key.
 * Purely a local map-tile cache: it never feeds camera-warning, routing or map-matching logic,
 * all of which keep using their existing live data sources exactly as before.
 */
object OfflineMapRepository {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    data class OfflineArea(
        val regionId: Long,
        val name: String,
        val centerLat: Double,
        val centerLon: Double,
        val radiusKm: Double,
        val downloading: Boolean,
        val complete: Boolean,
        val completedTileCount: Long,
        val requiredTileCount: Long,
        val completedSizeBytes: Long
    )

    private fun manager(context: Context): OfflineManager = OfflineManager.getInstance(context.applicationContext)

    private fun boundsFor(center: LatLng, radiusKm: Double): LatLngBounds {
        val latDelta = radiusKm / 110.574
        val lonDelta = radiusKm / (111.320 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
        return LatLngBounds.from(
            center.latitude + latDelta, center.longitude + lonDelta,
            center.latitude - latDelta, center.longitude - lonDelta
        )
    }

    /** Wider areas download far more tiles at the same max zoom, so cap detail as radius grows
     * to keep a single download from ballooning into gigabytes on a rider's phone. */
    private fun maxZoomFor(radiusKm: Double): Double = when {
        radiusKm <= 6.0 -> 16.0
        radiusKm <= 12.0 -> 15.0
        else -> 14.0
    }

    fun download(
        context: Context,
        name: String,
        center: LatLng,
        radiusKm: Double,
        pixelRatio: Float,
        onProgress: (percent: Int, completedTiles: Long, sizeBytes: Long) -> Unit,
        onComplete: (OfflineArea) -> Unit,
        onError: (String) -> Unit
    ) {
        val definition = OfflineTilePyramidRegionDefinition(STYLE_URL, boundsFor(center, radiusKm), 11.0, maxZoomFor(radiusKm), pixelRatio)
        val metadata = JSONObject().apply {
            put("name", name); put("radiusKm", radiusKm); put("lat", center.latitude); put("lon", center.longitude)
        }.toString().toByteArray(Charsets.UTF_8)
        manager(context).createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val percent = if (status.requiredResourceCount > 0)
                            (100.0 * status.completedResourceCount / status.requiredResourceCount).roundToInt().coerceIn(0, 100) else 0
                        onProgress(percent, status.completedResourceCount, status.completedResourceSize)
                        if (status.isComplete) {
                            onComplete(
                                OfflineArea(offlineRegion.id, name, center.latitude, center.longitude, radiusKm,
                                    downloading = false, complete = true,
                                    completedTileCount = status.completedResourceCount,
                                    requiredTileCount = status.requiredResourceCount,
                                    completedSizeBytes = status.completedResourceSize)
                            )
                        }
                    }
                    override fun onError(error: OfflineRegionError) { onError(error.message ?: "Offline download failed") }
                    override fun mapboxTileCountLimitExceeded(limit: Long) { onError("Area too large for one offline download; choose a smaller radius.") }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) { onError(error) }
        })
    }

    fun list(context: Context, callback: (List<OfflineArea>) -> Unit) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>) {
                if (offlineRegions.isEmpty()) { callback(emptyList()); return }
                val results = mutableListOf<OfflineArea>()
                var remaining = offlineRegions.size
                offlineRegions.forEach { region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus) {
                            val meta = runCatching { JSONObject(String(region.metadata, Charsets.UTF_8)) }.getOrNull()
                            results += OfflineArea(
                                region.id, meta?.optString("name")?.takeIf { it.isNotBlank() } ?: "Saved area",
                                meta?.optDouble("lat") ?: 0.0, meta?.optDouble("lon") ?: 0.0,
                                meta?.optDouble("radiusKm") ?: 0.0,
                                downloading = !status.isComplete,
                                complete = status.isComplete,
                                completedTileCount = status.completedResourceCount,
                                requiredTileCount = status.requiredResourceCount,
                                completedSizeBytes = status.completedResourceSize
                            )
                            remaining--
                            if (remaining == 0) callback(results.sortedBy { it.name })
                        }
                        override fun onError(error: String) {
                            remaining--
                            if (remaining == 0) callback(results.sortedBy { it.name })
                        }
                    })
                }
            }
            override fun onError(error: String) { callback(emptyList()) }
        })
    }

    fun delete(context: Context, regionId: Long, callback: (Boolean) -> Unit) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>) {
                val match = offlineRegions.firstOrNull { it.id == regionId } ?: return callback(false)
                match.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { callback(true) }
                    override fun onError(error: String) { callback(false) }
                })
            }
            override fun onError(error: String) { callback(false) }
        })
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

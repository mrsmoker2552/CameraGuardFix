package com.boss.cameraguard.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTracker(
    context: Context,
    private val onLocationUpdate: (Location) -> Unit
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest =
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1f)
            .setWaitForAccurateLocation(true)
            .build()

    private var lastAccepted: Location? = null

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {
                // Process the batch in timestamp order. Ignore stale/out-of-order fixes
                // and obvious horizontal-accuracy regressions when a recent better fix exists.
                result.locations.sortedBy { it.elapsedRealtimeNanos }.forEach { location ->
                    val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
                    if (ageMillis !in 0L..5_000L || !location.hasAccuracy()) return@forEach
                    val previous = lastAccepted
                    if (previous != null && location.elapsedRealtimeNanos <= previous.elapsedRealtimeNanos) return@forEach
                    if (previous != null &&
                        (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) < 3_000_000_000L &&
                        previous.accuracy <= 20f && location.accuracy > 60f) return@forEach
                    lastAccepted = location
                    onLocationUpdate(location)
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun start() {

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stop() {
        lastAccepted = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
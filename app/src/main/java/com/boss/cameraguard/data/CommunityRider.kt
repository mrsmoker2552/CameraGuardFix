package com.boss.cameraguard.data

/**
 * A participating CameraGuard rider read back from the shared
 * `presence/<uid>` Firebase node, plus fields the client derives locally
 * (distance from the current user, geographic bucket for activity
 * counting). This is display-only data - it never includes anything
 * beyond what the rider explicitly chose to publish (display name,
 * position, heading, speed) while community sharing was enabled.
 */
data class CommunityRider(
    val uid: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Double,
    val speedKmh: Double,
    val lastSeenMillis: Long,
    val cell: String,
    val distanceMeters: Float,
    val roadName: String? = null,
    val moving: Boolean = false,
    val sosActive: Boolean = false
)

package com.boss.cameraguard.data

/**
 * A participating CameraGuard rider read back from the shared
 * `presence/<uid>` Firebase node, plus fields the client derives locally
 * (distance from the current user, geographic bucket for activity
 * counting). This is display-only data - it never includes anything
 * beyond what the rider explicitly chose to publish (display name,
 * position, heading, speed) while community sharing was enabled.
 *
 * [photoUrl], when present, is always a Google-hosted account avatar URL
 * (lh3.googleusercontent.com/...) copied from the rider's own Google
 * sign-in profile - never an uploaded file. This keeps rider photos
 * free: CameraGuard never stores or serves the image bytes itself, it
 * only republishes a URL Google is already hosting for free. Null means
 * the rider isn't Google-linked (or hasn't set a Google avatar), and the
 * UI falls back to initials - see RiderAvatar.
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
    val sosActive: Boolean = false,
    val photoUrl: String? = null
)

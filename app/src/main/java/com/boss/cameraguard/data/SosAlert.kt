package com.boss.cameraguard.data

/**
 * Emergency/SOS alert intentionally publishes the rider's exact current location for a
 * short period so other opted-in Community riders can find them. It is separate from
 * normal /presence, which stays privacy-rounded.
 */
data class SosAlert(
    val uid: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val active: Boolean = true
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        active && createdAtMillis > 0L && expiresAtMillis > nowMillis
}

package com.boss.cameraguard.data

/**
 * Single source of truth for the CameraGuard community Firebase Realtime
 * Database URL. Previously this was a private constant inside MainActivity;
 * reuse the SAME real backend/database instead of inventing a second one.
 */
object CommunityConfig {
    const val DATABASE_URL =
        "https://cameraguard-b1c5a-default-rtdb.europe-west1.firebasedatabase.app"
}

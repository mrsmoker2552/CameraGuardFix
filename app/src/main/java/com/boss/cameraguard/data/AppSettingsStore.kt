package com.boss.cameraguard.data

import android.content.Context

enum class VehicleMode { CAR, SCOOTER }
enum class AppThemeMode { DARK, LIGHT }

data class AppSettings(
    val drivingModeEnabled: Boolean,
    val voiceAlertsEnabled: Boolean,
    val beepAlertsEnabled: Boolean,
    val onboardingAccepted: Boolean,
    val communityModeEnabled: Boolean,
    val communityConsentAccepted: Boolean,
    val communityDisplayName: String,
    val voicePreset: Int = 0,
    val vehicleMode: VehicleMode = VehicleMode.SCOOTER,
    val vehicleSelectionDone: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val speedCameraWarningDistanceMeters: Int = 300,
    val redLightWarningDistanceMeters: Int = 150,
    val busLaneWarningDistanceMeters: Int = 200,
    val noEntryWarningDistanceMeters: Int = 200,
    val ztlWarningDistanceMeters: Int = 300,
    val averageSpeedWarningDistanceMeters: Int = 500,
    val mobilePhoneWarningDistanceMeters: Int = 300,
    val otherEnforcementWarningDistanceMeters: Int = 200
) {
    fun warningDistanceFor(type: RealCameraType): Int =
        when (type) {
            RealCameraType.SPEED -> speedCameraWarningDistanceMeters
            RealCameraType.RED_LIGHT -> redLightWarningDistanceMeters
            RealCameraType.BUS_LANE -> busLaneWarningDistanceMeters
            RealCameraType.NO_ENTRY -> noEntryWarningDistanceMeters
            RealCameraType.ZTL -> ztlWarningDistanceMeters
            RealCameraType.AVERAGE_SPEED -> averageSpeedWarningDistanceMeters
            RealCameraType.MOBILE_PHONE -> mobilePhoneWarningDistanceMeters
            RealCameraType.OTHER_ENFORCEMENT -> otherEnforcementWarningDistanceMeters
        }

    fun warningDistances(): Map<RealCameraType, Int> = mapOf(
        RealCameraType.SPEED to speedCameraWarningDistanceMeters,
        RealCameraType.RED_LIGHT to redLightWarningDistanceMeters,
        RealCameraType.BUS_LANE to busLaneWarningDistanceMeters,
        RealCameraType.NO_ENTRY to noEntryWarningDistanceMeters,
        RealCameraType.ZTL to ztlWarningDistanceMeters,
        RealCameraType.AVERAGE_SPEED to averageSpeedWarningDistanceMeters,
        RealCameraType.MOBILE_PHONE to mobilePhoneWarningDistanceMeters,
        RealCameraType.OTHER_ENFORCEMENT to otherEnforcementWarningDistanceMeters
    )
}

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        drivingModeEnabled = preferences.getBoolean(KEY_DRIVING_MODE, true),
        voiceAlertsEnabled = preferences.getBoolean(KEY_VOICE_ALERTS, true),
        beepAlertsEnabled = preferences.getBoolean(KEY_BEEP_ALERTS, true),
        onboardingAccepted = preferences.getBoolean(KEY_ONBOARDING_ACCEPTED, false),
        communityModeEnabled = preferences.getBoolean(KEY_COMMUNITY_MODE, false),
        communityConsentAccepted = preferences.getBoolean(KEY_COMMUNITY_CONSENT, false),
        communityDisplayName = preferences.getString(KEY_COMMUNITY_DISPLAY_NAME, "") ?: "",
        voicePreset = preferences.getInt(KEY_VOICE_PRESET, 0).coerceIn(0, 2),
        vehicleMode = runCatching { VehicleMode.valueOf(preferences.getString(KEY_VEHICLE_MODE, VehicleMode.SCOOTER.name) ?: VehicleMode.SCOOTER.name) }.getOrDefault(VehicleMode.SCOOTER),
        vehicleSelectionDone = preferences.getBoolean(KEY_VEHICLE_SELECTION_DONE, false),
        themeMode = runCatching { AppThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name) }.getOrDefault(AppThemeMode.DARK),
        // Safety-critical locked CameraGuard distances. Never restore stale user-edited values.
        speedCameraWarningDistanceMeters = DEFAULT_SPEED_CAMERA_WARNING_DISTANCE_METERS,
        redLightWarningDistanceMeters = DEFAULT_RED_LIGHT_WARNING_DISTANCE_METERS,
        busLaneWarningDistanceMeters = getDistance(KEY_BUS_LANE_WARNING_DISTANCE, DEFAULT_BUS_LANE_WARNING_DISTANCE_METERS),
        noEntryWarningDistanceMeters = getDistance(KEY_NO_ENTRY_WARNING_DISTANCE, DEFAULT_NO_ENTRY_WARNING_DISTANCE_METERS),
        ztlWarningDistanceMeters = getDistance(KEY_ZTL_WARNING_DISTANCE, DEFAULT_ZTL_WARNING_DISTANCE_METERS),
        averageSpeedWarningDistanceMeters = getDistance(KEY_AVERAGE_SPEED_WARNING_DISTANCE, DEFAULT_AVERAGE_SPEED_WARNING_DISTANCE_METERS),
        mobilePhoneWarningDistanceMeters = getDistance(KEY_MOBILE_PHONE_WARNING_DISTANCE, DEFAULT_MOBILE_PHONE_WARNING_DISTANCE_METERS),
        otherEnforcementWarningDistanceMeters = getDistance(KEY_OTHER_ENFORCEMENT_WARNING_DISTANCE, DEFAULT_OTHER_ENFORCEMENT_WARNING_DISTANCE_METERS)
    )

    private fun getDistance(key: String, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue).coerceIn(MIN_WARNING_DISTANCE_METERS, MAX_WARNING_DISTANCE_METERS)

    fun setDrivingModeEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_DRIVING_MODE, enabled).apply() }
    fun setVoiceAlertsEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_VOICE_ALERTS, enabled).apply() }
    fun setBeepAlertsEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_BEEP_ALERTS, enabled).apply() }
    fun setOnboardingAccepted(accepted: Boolean) { preferences.edit().putBoolean(KEY_ONBOARDING_ACCEPTED, accepted).apply() }
    fun setCommunityModeEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_COMMUNITY_MODE, enabled).apply() }
    fun setCommunityConsentAccepted(accepted: Boolean) { preferences.edit().putBoolean(KEY_COMMUNITY_CONSENT, accepted).apply() }
    fun setCommunityDisplayName(displayName: String) { preferences.edit().putString(KEY_COMMUNITY_DISPLAY_NAME, displayName.trim()).apply() }
    fun setVoicePreset(preset: Int) { preferences.edit().putInt(KEY_VOICE_PRESET, preset.coerceIn(0, 2)).apply() }
    fun setVehicleMode(mode: VehicleMode) { preferences.edit().putString(KEY_VEHICLE_MODE, mode.name).putBoolean(KEY_VEHICLE_SELECTION_DONE, true).apply() }
    fun setThemeMode(mode: AppThemeMode) { preferences.edit().putString(KEY_THEME_MODE, mode.name).apply() }

    fun setWarningDistances(distances: Map<RealCameraType, Int>) {
        val editor = preferences.edit()
        distances.forEach { (type, meters) ->
            if (type == RealCameraType.SPEED || type == RealCameraType.RED_LIGHT) return@forEach
            val safe = meters.coerceIn(MIN_WARNING_DISTANCE_METERS, MAX_WARNING_DISTANCE_METERS)
            val key = when (type) {
                RealCameraType.SPEED -> KEY_SPEED_CAMERA_WARNING_DISTANCE
                RealCameraType.RED_LIGHT -> KEY_RED_LIGHT_WARNING_DISTANCE
                RealCameraType.BUS_LANE -> KEY_BUS_LANE_WARNING_DISTANCE
                RealCameraType.NO_ENTRY -> KEY_NO_ENTRY_WARNING_DISTANCE
                RealCameraType.ZTL -> KEY_ZTL_WARNING_DISTANCE
                RealCameraType.AVERAGE_SPEED -> KEY_AVERAGE_SPEED_WARNING_DISTANCE
                RealCameraType.MOBILE_PHONE -> KEY_MOBILE_PHONE_WARNING_DISTANCE
                RealCameraType.OTHER_ENFORCEMENT -> KEY_OTHER_ENFORCEMENT_WARNING_DISTANCE
            }
            editor.putInt(key, safe)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "camera_guard_settings"
        private const val KEY_DRIVING_MODE = "driving_mode_enabled"
        private const val KEY_VOICE_ALERTS = "voice_alerts_enabled"
        private const val KEY_BEEP_ALERTS = "beep_alerts_enabled"
        private const val KEY_ONBOARDING_ACCEPTED = "onboarding_accepted"
        private const val KEY_COMMUNITY_MODE = "community_mode_enabled"
        private const val KEY_COMMUNITY_CONSENT = "community_consent_accepted"
        private const val KEY_COMMUNITY_DISPLAY_NAME = "community_display_name"
        private const val KEY_VOICE_PRESET = "voice_preset"
        private const val KEY_VEHICLE_MODE = "vehicle_mode"
        private const val KEY_VEHICLE_SELECTION_DONE = "vehicle_selection_done"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SPEED_CAMERA_WARNING_DISTANCE = "speed_camera_warning_distance_meters"
        private const val KEY_RED_LIGHT_WARNING_DISTANCE = "red_light_warning_distance_meters"
        private const val KEY_BUS_LANE_WARNING_DISTANCE = "bus_lane_warning_distance_meters"
        private const val KEY_NO_ENTRY_WARNING_DISTANCE = "no_entry_warning_distance_meters"
        private const val KEY_ZTL_WARNING_DISTANCE = "ztl_warning_distance_meters"
        private const val KEY_AVERAGE_SPEED_WARNING_DISTANCE = "average_speed_warning_distance_meters"
        private const val KEY_MOBILE_PHONE_WARNING_DISTANCE = "mobile_phone_warning_distance_meters"
        private const val KEY_OTHER_ENFORCEMENT_WARNING_DISTANCE = "other_enforcement_warning_distance_meters"

        const val DEFAULT_SPEED_CAMERA_WARNING_DISTANCE_METERS = 300
        const val DEFAULT_RED_LIGHT_WARNING_DISTANCE_METERS = 150
        const val DEFAULT_BUS_LANE_WARNING_DISTANCE_METERS = 200
        const val DEFAULT_NO_ENTRY_WARNING_DISTANCE_METERS = 200
        const val DEFAULT_ZTL_WARNING_DISTANCE_METERS = 300
        const val DEFAULT_AVERAGE_SPEED_WARNING_DISTANCE_METERS = 500
        const val DEFAULT_MOBILE_PHONE_WARNING_DISTANCE_METERS = 300
        const val DEFAULT_OTHER_ENFORCEMENT_WARNING_DISTANCE_METERS = 200
        const val MIN_WARNING_DISTANCE_METERS = 50
        const val MAX_WARNING_DISTANCE_METERS = 5000
    }
}

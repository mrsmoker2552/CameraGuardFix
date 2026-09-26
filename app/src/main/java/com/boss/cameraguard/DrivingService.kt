package com.boss.cameraguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.Geocoder
import java.util.Locale
import android.os.Build
import android.os.IBinder
import com.boss.cameraguard.alerts.AlertSoundManager
import com.boss.cameraguard.alerts.CameraWarningRuntime
import com.boss.cameraguard.alerts.RealCameraWarningEngine
import com.boss.cameraguard.data.AppSettingsStore
import com.boss.cameraguard.data.CameraCache
import com.boss.cameraguard.data.CameraOverrideStore
import com.boss.cameraguard.data.CameraRepository
import com.boss.cameraguard.data.DriveDiagnosticStore
import com.boss.cameraguard.data.ManualCameraStore
import com.boss.cameraguard.data.RealCamera
import com.boss.cameraguard.data.RealCameraType
import com.boss.cameraguard.data.displayName
import com.boss.cameraguard.data.stableSourceKey
import com.boss.cameraguard.location.LocationTracker
import com.boss.cameraguard.voice.VoiceWarningManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrivingService : Service() {

    private var locationTracker:
            LocationTracker? =
        null

    private lateinit var voiceWarningManager:
            VoiceWarningManager

    private lateinit var alertSoundManager:
            AlertSoundManager

    private lateinit var cameraCache:
            CameraCache

    private lateinit var manualCameraStore:
            ManualCameraStore

    private lateinit var settingsStore:
            AppSettingsStore

    private lateinit var overrideStore:
            CameraOverrideStore

    private val cameraRepository =
        CameraRepository()

    private val warningEngine =
        RealCameraWarningEngine()

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )

    @Volatile
    private var osmCameras:
            List<RealCamera> =
        emptyList()

    @Volatile
    private var masterCameras:
            List<RealCamera> =
        emptyList()

    @Volatile
    private var manualCameras:
            List<RealCamera> =
        emptyList()

    @Volatile
    private var cameraLoadInProgress =
        false

    @Volatile
    private var cameraAreaLatitude: Double? = null

    @Volatile
    private var cameraAreaLongitude: Double? = null

    @Volatile
    private var cameraAreaLoadedAt: Long = 0L

    @Volatile
    private var masterCameraRefreshInProgress = false

    @Volatile
    private var lastMasterCameraRefreshAt: Long = 0L

    @Volatile
    private var lastKnownLocation: Location? = null

    private var lastEmptyCameraStatusLogMs: Long = 0L


    override fun onCreate() {

        super.onCreate()

        DriveDiagnosticStore.log(
            "SERVICE_LIFECYCLE",
            "onCreate pid=${android.os.Process.myPid()}"
        )

        voiceWarningManager =
            VoiceWarningManager(this)

        alertSoundManager =
            AlertSoundManager()

        cameraCache =
            CameraCache(this)

        manualCameraStore =
            ManualCameraStore(this)

        settingsStore =
            AppSettingsStore(this)

        overrideStore =
            CameraOverrideStore(this)

        /*
         * If the user previously switched Driving Mode OFF,
         * do not continue running the service.
         */
        if (
            !settingsStore
                .load()
                .drivingModeEnabled
        ) {

            stopSelf()
            return
        }

        manualCameras =
            manualCameraStore.load()

        createNotificationChannel()

        startAsForegroundService()

        loadCachedCameras()

        startLocationTracking()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        DriveDiagnosticStore.log(
            "SERVICE_LIFECYCLE",
            "onStartCommand action=${intent?.action ?: "NONE"} startId=$startId flags=$flags"
        )

        if (
            intent?.action ==
            ACTION_STOP
        ) {

            settingsStore
                .setDrivingModeEnabled(
                    false
                )

            stopDrivingMode()

            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun allCameras():
            List<RealCamera> {

        return overrideStore.filterVisible(osmCameras.filter {
            it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
        }) + masterCameras + manualCameras
    }

    private fun startAsForegroundService() {

        val notification =
            buildNotification()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_LOCATION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun buildNotification():
            Notification {

        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                100,
                Intent(
                    this,
                    MainActivity::class.java
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent =
            Intent(
                this,
                DrivingService::class.java
            ).apply {

                action =
                    ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                101,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    this,
                    CHANNEL_ID
                )

            } else {

                Notification.Builder(this)
            }

        return builder
            .setSmallIcon(
                android.R.drawable
                    .ic_menu_mylocation
            )
            .setContentTitle(
                "CameraGuard Driving Mode"
            )
            .setContentText(
                "Monitoring speed and red-light cameras"
            )
            .setContentIntent(
                openAppPendingIntent
            )
            .setOngoing(
                true
            )
            .setCategory(
                Notification.CATEGORY_SERVICE
            )
            .addAction(
                android.R.drawable
                    .ic_menu_close_clear_cancel,
                "STOP",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "CameraGuard Driving Mode",
                NotificationManager
                    .IMPORTANCE_LOW
            )

        getSystemService(
            NotificationManager::class.java
        )
            .createNotificationChannel(
                channel
            )
    }

    private fun loadCachedCameras() {

        serviceScope.launch {

            val cache =
                withContext(
                    Dispatchers.IO
                ) {

                    cameraCache.load()
                }

            if (
                cache != null
            ) {

                osmCameras =
                    cache.cameras

                cameraAreaLatitude = cache.centerLatitude
                cameraAreaLongitude = cache.centerLongitude
                cameraAreaLoadedAt = cache.savedAtMillis
            }

            manualCameras =
                withContext(
                    Dispatchers.IO
                ) {

                    manualCameraStore.load()
                }
        }
    }

    private fun startLocationTracking() {

        if (
            locationTracker != null
        ) {
            return
        }

        DriveDiagnosticStore.log(
            "SERVICE_LIFECYCLE",
            "startLocationTracking requested"
        )

        locationTracker =
            LocationTracker(
                this
            ) { location ->

                handleLocation(
                    location
                )
            }

        try {

            locationTracker?.start()

            DriveDiagnosticStore.log(
                "SERVICE_LIFECYCLE",
                "locationTracker_started"
            )

        } catch (
            exception: SecurityException
        ) {

            DriveDiagnosticStore.log(
                "SERVICE_ERROR",
                "location_permission_exception=${exception.message}"
            )

            exception.printStackTrace()

            stopDrivingMode()
        }
    }

    private fun handleLocation(
        location: Location
    ) {

        lastKnownLocation = location

        DriveDiagnosticStore.log(
            "GPS",
            "lat=${location.latitude} lon=${location.longitude} accuracy=${location.accuracy} speedMps=${location.speed} bearing=${if (location.hasBearing()) location.bearing else -1f} cameras=${allCameras().size}"
        )

        maybeRefreshCameraArea(location)
        maybeRefreshMasterCameras(location)

        val cameras =
            allCameras()

        if (
            cameras.isEmpty()
        ) {

            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastEmptyCameraStatusLogMs >= 10_000L) {
                lastEmptyCameraStatusLogMs = now
                DriveDiagnosticStore.log(
                    "CAMERA_STATUS",
                    "no_cameras_loaded; osm=${osmCameras.size} master=${masterCameras.size} manual=${manualCameras.size} loading=$cameraLoadInProgress"
                )
            }
            CameraWarningRuntime.publish(null)

            loadCameraDataIfNeeded(
                location
            )

            return
        }

        val target =
            warningEngine
                .findRelevantCamera(
                    location =
                        location,

                    cameras =
                        cameras
                )

        CameraWarningRuntime.publish(target)

        if (target == null) {
            // TARGET diagnostics from the engine show the nearest camera's
            // distance/heading/lateral offset. Do not trigger opposite-road
            // cameras by bypassing direction or approach-path gating.
            return
        }

        DriveDiagnosticStore.log(
            "SERVICE_TARGET",
            "key=${target.camera.source}:${target.camera.type}:${target.camera.id} dist=${target.distanceMeters.toInt()} headingDiff=${target.headingDifference.toInt()}"
        )

        val settings =
            settingsStore.load()

        if (
            !warningEngine
                .shouldWarn(
                    target = target,
                    settings = settings
                )
        ) {
            return
        }

        DriveDiagnosticStore.log(
            "SERVICE_WARN",
            "audio_trigger key=${target.camera.source}:${target.camera.type}:${target.camera.id} dist=${target.distanceMeters.toInt()} warningZone=${settings.warningDistanceFor(target.camera.type)}"
        )

        if (
            settings.beepAlertsEnabled
        ) {

            alertSoundManager
                .playCameraBeep()
        }

        if (
            !settings.voiceAlertsEnabled
        ) {
            return
        }

        // Always sync the preset immediately before speaking so a
        // mid-drive voice-option change (made in Settings) takes effect on
        // the very next warning, without a second polling loop.
        voiceWarningManager.setVoicePreset(settings.voicePreset)

        when (target.camera.type) {
            RealCameraType.SPEED -> voiceWarningManager.speakSpeedCamera(target.camera.speedLimit)
            RealCameraType.RED_LIGHT -> voiceWarningManager.speakRedLightCamera(target.camera.speedLimit)
            RealCameraType.BUS_LANE -> voiceWarningManager.speakBusLaneCamera()
            RealCameraType.NO_ENTRY -> voiceWarningManager.speakNoEntryCamera()
            RealCameraType.ZTL -> voiceWarningManager.speakZtlCamera()
            RealCameraType.AVERAGE_SPEED -> voiceWarningManager.speakAverageSpeedCamera(target.camera.speedLimit)
            RealCameraType.MOBILE_PHONE -> voiceWarningManager.speakMobilePhoneCamera()
            RealCameraType.OTHER_ENFORCEMENT -> voiceWarningManager.speakOtherEnforcementCamera()
        }
    }

    private fun maybeRefreshMasterCameras(location: Location) {
        val now = System.currentTimeMillis()
        if (masterCameraRefreshInProgress) return
        if (now - lastMasterCameraRefreshAt < MASTER_CAMERA_REFRESH_INTERVAL_MILLIS) return

        masterCameraRefreshInProgress = true
        lastMasterCameraRefreshAt = now

        serviceScope.launch {
            try {
                val freshMasters = withContext(Dispatchers.IO) {
                    cameraRepository.loadFirebaseMasterCameras(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusMeters = 25000
                    )
                }
                masterCameras = freshMasters
                DriveDiagnosticStore.log(
                    "MASTER_SYNC",
                    "service refresh count=${freshMasters.size}"
                )
            } catch (exception: Exception) {
                DriveDiagnosticStore.log(
                    "MASTER_SYNC",
                    "service refresh failed ${exception.message}"
                )
            } finally {
                masterCameraRefreshInProgress = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveCurrentRoadName(location: Location): String {
        return try {
            val address = Geocoder(this, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            address?.thoroughfare
                ?: address?.subLocality
                ?: address?.locality
                ?: "Unknown road"
        } catch (_: Exception) {
            "Unknown road"
        }
    }

    private fun maybeRefreshCameraArea(
        location: Location
    ) {
        if (cameraLoadInProgress) return

        val lat = cameraAreaLatitude
        val lon = cameraAreaLongitude

        val movedFar =
            if (lat == null || lon == null) {
                true
            } else {
                val result = FloatArray(1)
                Location.distanceBetween(
                    lat,
                    lon,
                    location.latitude,
                    location.longitude,
                    result
                )
                result[0] >= CAMERA_AREA_REFRESH_DISTANCE_METERS
            }

        val stale =
            System.currentTimeMillis() - cameraAreaLoadedAt >= CAMERA_AREA_REFRESH_INTERVAL_MILLIS

        if (movedFar || stale) {
            loadCameraDataIfNeeded(location)
        }
    }

    private fun loadCameraDataIfNeeded(
        location: Location
    ) {

        if (
            cameraLoadInProgress
        ) {
            return
        }

        cameraLoadInProgress =
            true

        serviceScope.launch {

            try {

                val fresh =
                    withContext(
                        Dispatchers.IO
                    ) {

                        cameraRepository
                            .loadNearbyCameras(
                                latitude =
                                    location.latitude,

                                longitude =
                                    location.longitude,

                                radiusMeters =
                                    25000
                            )
                    }

                if (
                    fresh.isNotEmpty()
                ) {

                    val freshMasters = fresh.filter {
                        it.source == com.boss.cameraguard.data.RealCameraSource.MASTER
                    }
                    if (freshMasters.isNotEmpty()) {
                        masterCameras = freshMasters
                        lastMasterCameraRefreshAt = System.currentTimeMillis()
                    }

                    osmCameras =
                        mergeAndTrimCoverage(
                            existing = osmCameras.filter {
                                it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                            },
                            fresh = fresh.filter {
                                it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                            },
                            location = location
                        )

                    cameraAreaLatitude = location.latitude
                    cameraAreaLongitude = location.longitude
                    cameraAreaLoadedAt = System.currentTimeMillis()

                    withContext(
                        Dispatchers.IO
                    ) {

                        cameraCache.save(
                            cameras =
                                osmCameras.filter {
                                    it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                                },

                            centerLatitude =
                                location.latitude,

                            centerLongitude =
                                location.longitude
                        )
                    }
                }

            } catch (
                exception: Exception
            ) {

                DriveDiagnosticStore.log("CAMERA_LOAD_ERROR", exception.message ?: exception.javaClass.simpleName)
                exception.printStackTrace()

            } finally {

                cameraLoadInProgress =
                    false
            }
        }
    }

    private fun mergeAndTrimCoverage(
        existing: List<RealCamera>,
        fresh: List<RealCamera>,
        location: Location
    ): List<RealCamera> {

        return (existing + fresh)
            .distinctBy { it.stableSourceKey() }
            .filter { camera ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    camera.latitude,
                    camera.longitude,
                    results
                )
                results[0] <= CAMERA_COVERAGE_KEEP_RADIUS_METERS
            }
    }

    private fun stopDrivingMode() {

        locationTracker?.stop()

        locationTracker =
            null

        warningEngine.resetAll()
        CameraWarningRuntime.clear()

        if (
            ::voiceWarningManager
                .isInitialized
        ) {

            voiceWarningManager.shutdown()
        }

        if (
            ::alertSoundManager
                .isInitialized
        ) {

            alertSoundManager.release()
        }

        serviceScope.cancel()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        DriveDiagnosticStore.log(
            "SERVICE_LIFECYCLE",
            "onDestroy"
        )

        locationTracker?.stop()
        warningEngine.resetAll()
        CameraWarningRuntime.clear()

        if (
            ::voiceWarningManager
                .isInitialized
        ) {

            voiceWarningManager.shutdown()
        }

        if (
            ::alertSoundManager
                .isInitialized
        ) {

            alertSoundManager.release()
        }

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    companion object {

        const val ACTION_STOP =
            "com.boss.cameraguard.ACTION_STOP_DRIVING"

        private const val CHANNEL_ID =
            "camera_guard_driving"

        private const val NOTIFICATION_ID =
            7001

        private const val CAMERA_AREA_REFRESH_DISTANCE_METERS =
            8000f

        private const val CAMERA_COVERAGE_KEEP_RADIUS_METERS =
            35000f

        private const val CAMERA_AREA_REFRESH_INTERVAL_MILLIS =
            6L * 60L * 60L * 1000L

        private const val MASTER_CAMERA_REFRESH_INTERVAL_MILLIS =
            60L * 1000L
    }
}
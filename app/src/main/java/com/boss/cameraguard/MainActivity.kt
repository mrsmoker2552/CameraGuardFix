package com.boss.cameraguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.boss.cameraguard.alerts.AlertSoundManager
import com.boss.cameraguard.alerts.RealCameraTarget
import com.boss.cameraguard.alerts.CameraWarningRuntime
import com.boss.cameraguard.data.AppSettings
import com.boss.cameraguard.data.AppSettingsStore
import com.boss.cameraguard.data.CurrentRoad
import com.boss.cameraguard.data.CurrentRoadRepository
import com.boss.cameraguard.data.CachedCameraData
import com.boss.cameraguard.data.CameraCache
import com.boss.cameraguard.data.CameraOverrideStore
import com.boss.cameraguard.data.CameraRepository
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.data.DriveDiagnosticStore
import com.boss.cameraguard.data.ManualCameraStore
import com.boss.cameraguard.data.RealCamera
import com.boss.cameraguard.data.RealCameraType
import com.boss.cameraguard.data.SosAlert
import com.boss.cameraguard.data.stableSourceKey
import com.boss.cameraguard.location.LocationTracker
import com.boss.cameraguard.location.SpeedFilter
import com.boss.cameraguard.ui.CameraGuardApp
import com.boss.cameraguard.ui.CameraGuardOnboarding
import com.boss.cameraguard.ui.VehicleSelectionScreen
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import com.boss.cameraguard.ui.theme.CameraGuardTheme
import com.boss.cameraguard.voice.VoiceWarningManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var locationTracker:
            LocationTracker? = null

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

    private val speedFilter =
        SpeedFilter()

    private val cameraRepository =
        CameraRepository()

    private val currentRoadRepository = CurrentRoadRepository()
    private var currentRoad by mutableStateOf<CurrentRoad?>(null)
    private var lastRoadMatchAt = 0L

    // HUD mini-map real road network (see fetchNearbyRoadNetwork doc comment for why this is
    // a separate, slower-cadence fetch from the current-road match above).
    private var nearbyRoadNetwork by mutableStateOf<List<List<com.boss.cameraguard.data.RoadPoint>>>(emptyList())
    private var lastRoadNetworkAt = 0L
    private var lastRoadNetworkLocation: Location? = null
    // v1.4.6: vehicle is deliberately chosen on EVERY app launch.
    // This is session-only; the chosen mode is still persisted so Settings share it.
    private var vehicleChosenThisLaunch by mutableStateOf(false)


    private val communityDatabase by lazy {
        FirebaseDatabase.getInstance(com.boss.cameraguard.data.CommunityConfig.DATABASE_URL)
    }

    private var lastCommunityPresenceWriteAt = 0L

    // SOS is intentionally separate from normal privacy-rounded community presence.
    // Exact coordinates are shared only while an explicit short-lived SOS is active.
    private var sosAlerts by mutableStateOf<List<SosAlert>>(emptyList())
    private var sosListener: ValueEventListener? = null
    private val seenSosEvents = mutableSetOf<String>()
    private var pendingSosFocus by mutableStateOf<SosAlert?>(null)
    private var sosFocusRequest by mutableIntStateOf(0)

    // Tap-to-open target for a rider-message notification (Task: Community message
    // notifications). Mirrors the pendingSosFocus/sosFocusRequest pattern immediately above:
    // a plain counter bump so CameraGuardApp's LaunchedEffect(chatOpenRequest) fires even if
    // the same conversation id is tapped twice in a row.
    private var pendingChatOpenConversationId by mutableStateOf<String?>(null)
    private var chatOpenRequest by mutableIntStateOf(0)

    /*
     * Nearby-rider read side of the community feature. Presence publishing
     * (writing our own location) already existed; this adds the missing
     * read side - subscribing to *other* riders' presence so they can
     * actually be shown, scoped to a small grid neighbourhood around the
     * current user rather than the whole database.
     */
    private var communityRiders by
    mutableStateOf<List<CommunityRider>>(emptyList())

    private val communityCellListeners =
        mutableMapOf<String, ValueEventListener>()

    private val communityCellQueries =
        mutableMapOf<String, Query>()

    private val communityRidersByCell =
        mutableMapOf<String, Map<String, CommunityRider>>()

    private var communitySubscribedCenterCell: String? = null

    private var lastCommunityRiderRefreshAt = 0L


    private var currentLocation by
    mutableStateOf<Location?>(null)

    private var filteredSpeedKmh by
    mutableFloatStateOf(0f)

    private var osmCameras by
    mutableStateOf<List<RealCamera>>(
        emptyList()
    )

    private var masterCameras by
    mutableStateOf<List<RealCamera>>(
        emptyList()
    )

    private var manualCameras by
    mutableStateOf<List<RealCamera>>(
        emptyList()
    )

    private var cameraDataLoading by
    mutableStateOf(false)

    private var cameraDataError by
    mutableStateOf<String?>(null)

    private var activeCameraTarget by
    mutableStateOf<RealCameraTarget?>(null)

    private val warningTargetListener: (RealCameraTarget?) -> Unit = { target ->
        runOnUiThread {
            activeCameraTarget = target
            target?.let {
                DriveDiagnosticStore.log(
                    "UI_TARGET",
                    "authoritative key=${it.camera.source}:${it.camera.type}:${it.camera.id} dist=${it.distanceMeters.toInt()} headingDiff=${it.headingDifference.toInt()}"
                )
            }
        }
    }

    private var appSettings by
    mutableStateOf(
        AppSettings(
            drivingModeEnabled = true,
            voiceAlertsEnabled = true,
            beepAlertsEnabled = true,
            onboardingAccepted = false,
            communityModeEnabled = false,
            communityConsentAccepted = false,
            communityDisplayName = "",
            speedCameraWarningDistanceMeters = 300,
            redLightWarningDistanceMeters = 150,
            busLaneWarningDistanceMeters = 200,
            noEntryWarningDistanceMeters = 200,
            ztlWarningDistanceMeters = 300,
            averageSpeedWarningDistanceMeters = 500,
            mobilePhoneWarningDistanceMeters = 300,
            otherEnforcementWarningDistanceMeters = 200
        )
    )

    private var cameraLoadInProgress = false

    private var lastCameraAreaLatitude: Double? = null
    private var lastCameraAreaLongitude: Double? = null
    private var lastCameraAreaLoadedAt: Long = 0L
    private var lastMasterCameraRefreshAt: Long = 0L
    private var masterCameraRefreshInProgress = false

    private var drivingServiceStarted =
        false

    // Ask permissions sequentially: Android cannot display two runtime permission
    // dialogs at once. Location is requested first, notifications afterwards.
    private var startupPermissionFlowPending = false

    private fun requestStartupPermissions() {
        if (startupPermissionFlowPending) return
        startupPermissionFlowPending = true
        if (hasLocationPermission()) {
            checkLocationPermission()
            finishStartupPermissionFlow()
        } else {
            externalFlow = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun finishStartupPermissionFlow() {
        if (!startupPermissionFlowPending) return
        startupPermissionFlowPending = false
        requestNotificationPermission()
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val coarseGranted =
                permissions[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (
                fineGranted ||
                coarseGranted
            ) {

                startLocationTracking()

                if (
                    appSettings.drivingModeEnabled
                ) {

                    startDrivingService()
                }
            }
            finishStartupPermissionFlow()
        }


    private val exportDriveDiagnosticsLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            if (uri == null) return@registerForActivityResult

            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(DriveDiagnosticStore.exportText())
                }
            }.onFailure { it.printStackTrace() }
        }

    private val exportManualCamerasLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null || !::manualCameraStore.isInitialized) return@registerForActivityResult

            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(manualCameraStore.exportJson())
                }
            }.onFailure { it.printStackTrace() }
        }

    private val importManualCamerasLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null || !::manualCameraStore.isInitialized) return@registerForActivityResult

            runCatching {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@runCatching
                manualCameraStore.importJson(json)
                manualCameras = manualCameraStore.load()
                currentLocation?.let { updateRealCameraTarget(it) }
                if (appSettings.drivingModeEnabled) restartDrivingService()
            }.onFailure { it.printStackTrace() }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()

        voiceWarningManager =
            VoiceWarningManager(
                this
            )

        alertSoundManager =
            AlertSoundManager()

        cameraCache =
            CameraCache(
                this
            )

        manualCameraStore =
            ManualCameraStore(
                this
            )

        settingsStore =
            AppSettingsStore(
                this
            )

        overrideStore =
            CameraOverrideStore(
                this
            )

        appSettings =
            settingsStore.load()

        ensureFirebaseSession()
        handleSosNotificationIntent(intent)
        handleChatNotificationIntent(intent)

        manualCameras =
            manualCameraStore.load()

        // Startup loading animation and swipe-through guide/onboarding pager
        // have been removed (v1.3.9): the app now opens directly into either
        // the one-time safety/consent screen (if not yet accepted) or the
        // main interface, with no artificial delay.
        if (appSettings.onboardingAccepted) {
            requestStartupPermissions()
        }

        setContent {

            CameraGuardTheme(themeMode = appSettings.themeMode) {

                if (!appSettings.onboardingAccepted) {
                    CameraGuardOnboarding(
                        onAccept = { vehicleMode ->
                            settingsStore.setVehicleMode(vehicleMode)
                            vehicleChosenThisLaunch = true
                            settingsStore.setOnboardingAccepted(true)
                            appSettings = settingsStore.load()
                            requestStartupPermissions()
                        }
                    )
                    return@CameraGuardTheme
                }

                val visibleOsmCameras =
                    overrideStore.filterVisible(
                        osmCameras.filter {
                            it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                        }
                    )

                // Firebase Admin Map cameras are kept in masterCameras, not in
                // osmCameras. Include them explicitly in the UI dataset so the
                // Navigate map and Cameras tab show the same Master records that
                // the warning engine already uses. Master records intentionally
                // bypass local OSM hide/override rules.
                val allCameras =
                    visibleOsmCameras + masterCameras + manualCameras

                var hudModeActive by remember { mutableStateOf(false) }

                CameraGuardApp(
                    liveLocation =
                        currentLocation,

                    filteredSpeedKmh =
                        filteredSpeedKmh,

                    realCameras =
                        allCameras,

                    cameraDataLoading =
                        cameraDataLoading,

                    cameraDataError =
                        cameraDataError,

                    activeCameraTarget =
                        activeCameraTarget,

                    currentRoad = currentRoad,
                    nearbyRoadNetwork = nearbyRoadNetwork,

                    appSettings =
                        appSettings,

                    onTestRedLightWarning = {

                        alertSoundManager
                            .playCameraBeep()

                        voiceWarningManager
                            .speakRedLightCamera(
                                50
                            )
                    },

                    onTestSpeedCameraWarning = {

                        alertSoundManager
                            .playCameraBeep()

                        voiceWarningManager
                            .speakSpeedCamera(
                                70
                            )
                    },

                    onAddManualCamera = {
                            type,
                            speedLimit,
                            monitoredBearing ->

                        addManualCamera(
                            type =
                                type,

                            speedLimit =
                                speedLimit,

                            monitoredBearing =
                                monitoredBearing
                        )
                    },

                    onDeleteManualCamera = {
                            cameraId ->

                        deleteManualCamera(
                            cameraId
                        )
                    },

                    onEditManualCamera = { cameraId, type, speedLimit, note, monitoredBearing ->
                        editManualCamera(cameraId, type, speedLimit, note, monitoredBearing)
                    },

                    onFixOsmCamera = { camera, type, speedLimit, note, monitoredBearing ->
                        fixOsmCamera(camera, type, speedLimit, note, monitoredBearing)
                    },

                    onExportManualCameras = {
                        run { externalFlow = true; exportManualCamerasLauncher }.launch("CameraGuard-manual-cameras.json")
                    },

                    onImportManualCameras = {
                        run { externalFlow = true; importManualCamerasLauncher }.launch(arrayOf("application/json", "text/plain"))
                    },

                    onRestoreOsmOverrides = {
                        overrideStore.restoreAll()
                        currentLocation?.let { updateRealCameraTarget(it) }
                        if (appSettings.drivingModeEnabled) restartDrivingService()
                    },

                    hiddenOsmCameraCount = overrideStore.count(),

                    onStartDriveDiagnostics = {
                        DriveDiagnosticStore.clear()
                    },

                    onExportDriveDiagnostics = {
                        run { externalFlow = true; exportDriveDiagnosticsLauncher }.launch("CameraGuard-drive-diagnostic.log")
                    },

                    onDrivingModeChanged = {
                            enabled ->

                        setDrivingModeEnabled(
                            enabled
                        )
                    },

                    onVoiceAlertsChanged = {
                            enabled ->

                        settingsStore
                            .setVoiceAlertsEnabled(
                                enabled
                            )

                        appSettings =
                            appSettings.copy(
                                voiceAlertsEnabled =
                                    enabled
                            )
                    },

                    onBeepAlertsChanged = {
                            enabled ->

                        settingsStore
                            .setBeepAlertsEnabled(
                                enabled
                            )

                        appSettings =
                            appSettings.copy(
                                beepAlertsEnabled =
                                    enabled
                            )
                    },

                    onVehicleModeChanged = { mode ->
                        settingsStore.setVehicleMode(mode)
                        appSettings = settingsStore.load()
                        DriveDiagnosticStore.log("SETTINGS", "vehicle_mode=${mode.name}")
                    },

                    onThemeModeChanged = { mode ->
                        settingsStore.setThemeMode(mode)
                        appSettings = appSettings.copy(themeMode = mode)
                        DriveDiagnosticStore.log("SETTINGS", "theme_mode=${mode.name}")
                    },

                    onWarningDistancesChanged = { distances ->
                        settingsStore.setWarningDistances(distances)
                        appSettings = settingsStore.load()
                        DriveDiagnosticStore.log(
                            "SETTINGS",
                            "warning_distances ${appSettings.warningDistances()}"
                        )
                    },

                    onCommunityModeChanged = { enabled, displayName ->
                        setCommunityMode(
                            enabled = enabled,
                            displayName = displayName
                        )
                    },

                    onCommunityDisplayNameChanged = { displayName ->
                        updateCommunityDisplayName(displayName)
                    },

                    onRoadReport = { reportType ->
                        publishRoadReport(reportType)
                    },

                    communityRiders =
                        communityRiders,

                    sosAlerts = sosAlerts,
                    sosFocusAlert = pendingSosFocus,
                    sosFocusRequest = sosFocusRequest,
                    onSosPressed = { publishSosAlert() },
                    onSosCancelled = { cancelOwnSosAlert() },

                    chatOpenConversationId = pendingChatOpenConversationId,
                    chatOpenRequest = chatOpenRequest,

                    onVoicePresetChanged = { preset ->
                        settingsStore.setVoicePreset(preset)
                        appSettings = appSettings.copy(voicePreset = preset)
                    },

                    hudModeActive =
                        hudModeActive,

                    onHudModeChanged = {
                        hudModeActive = it
                    }
                )
            }
        }
    }

    private var externalFlow = false

    override fun onStart() {
        super.onStart()
        CameraWarningRuntime.addListener(
            warningTargetListener
        )
    }

    override fun onStop() {
        CameraWarningRuntime.removeListener(
            warningTargetListener
        )
        super.onStop()
    }

    override fun onResume() {

        super.onResume()
        externalFlow = false

        if (
            ::settingsStore.isInitialized
        ) {

            appSettings =
                settingsStore.load()

            /*
             * Notification STOP button can stop the service
             * while MainActivity is in background.
             *
             * Reloading here keeps Settings screen in sync.
             */
            if (
                !appSettings.drivingModeEnabled
            ) {

                drivingServiceStarted =
                    false
            }
        }
    }

    private fun setDrivingModeEnabled(
        enabled: Boolean
    ) {

        settingsStore
            .setDrivingModeEnabled(
                enabled
            )

        appSettings =
            appSettings.copy(
                drivingModeEnabled =
                    enabled
            )

        if (
            enabled
        ) {

            if (
                hasLocationPermission()
            ) {

                startLocationTracking()

                startDrivingService()

            } else {

                run { externalFlow = true; locationPermissionLauncher }.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

        } else {

            stopDrivingService()
        }
    }

    private fun stopDrivingService() {

        try {

            stopService(
                Intent(
                    this,
                    DrivingService::class.java
                )
            )

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }

        drivingServiceStarted =
            false
    }

    private fun addManualCamera(
        type: RealCameraType,
        speedLimit: Int?,
        monitoredBearing: Float?
    ) {

        val location =
            currentLocation
                ?: return

        // The camera direction is now an explicit choice made by the user in
        // the direction-arrow picker (see CameraDirectionPicker), not
        // silently derived from whatever the rider's live GPS bearing
        // happened to be at the moment "Add camera" was tapped. That old
        // fallback was unreliable (null whenever the rider was stationary or
        // slow) and was the root cause of manually added cameras warning
        // from both travel directions instead of only the one the user
        // actually meant.
        manualCameraStore.add(
            latitude =
                location.latitude,

            longitude =
                location.longitude,

            type =
                type,

            speedLimit =
                speedLimit,

            monitoredBearing =
                monitoredBearing
        )

        manualCameras =
            manualCameraStore.load()

        updateRealCameraTarget(
            location
        )

        /*
         * Restart background service so it immediately
         * reloads the manual-camera database.
         */
        if (
            appSettings.drivingModeEnabled
        ) {

            restartDrivingService()
        }
    }

    private fun editManualCamera(
        cameraId: Long,
        type: RealCameraType,
        speedLimit: Int?,
        note: String?,
        monitoredBearing: Float?
    ) {
        // monitoredBearing now always reflects the direction picker's
        // current selection (including an explicit "cleared" null), so it
        // must be written through even when null - ManualCameraStore.update
        // is called with a sentinel-aware path below rather than silently
        // keeping the old bearing.
        manualCameraStore.update(
            cameraId = cameraId,
            type = type,
            speedLimit = speedLimit,
            userNote = note,
            monitoredBearing = monitoredBearing,
            clearMonitoredBearing = monitoredBearing == null
        )
        manualCameras = manualCameraStore.load()
        currentLocation?.let { updateRealCameraTarget(it) }
        if (appSettings.drivingModeEnabled) restartDrivingService()
    }

    private fun fixOsmCamera(
        camera: RealCamera,
        type: RealCameraType,
        speedLimit: Int?,
        note: String?,
        monitoredBearing: Float?
    ) {
        if (camera.source == com.boss.cameraguard.data.RealCameraSource.MANUAL ||
            camera.source == com.boss.cameraguard.data.RealCameraSource.MASTER
        ) return

        overrideStore.suppress(camera)

        manualCameraStore.add(
            latitude = camera.latitude,
            longitude = camera.longitude,
            type = type,
            speedLimit = speedLimit,
            // Prefer the direction the user explicitly confirmed/edited in
            // the picker; fall back to the original OSM camera's bearing
            // only when the user left the picker untouched (edit dialog
            // pre-fills it from camera.monitoredBearing, so this is only
            // reached if that pre-fill is what came back).
            monitoredBearing = monitoredBearing,
            userNote = note,
            verifiedByUser = true,
            replacesSourceKey = camera.stableSourceKey()
        )

        manualCameras = manualCameraStore.load()
        currentLocation?.let { updateRealCameraTarget(it) }
        if (appSettings.drivingModeEnabled) restartDrivingService()
    }

    private fun deleteManualCamera(
        cameraId: Long
    ) {

        val removed =
            manualCameraStore.delete(
                cameraId
            )

        removed?.replacesSourceKey?.let { key ->
            overrideStore.restoreKey(key)
        }

        manualCameras =
            manualCameraStore.load()

        currentLocation
            ?.let {

                updateRealCameraTarget(
                    it
                )
            }

        if (
            appSettings.drivingModeEnabled
        ) {

            restartDrivingService()
        }
    }

    private fun restartDrivingService() {

        stopDrivingService()

        startDrivingService()
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        externalFlow = true
        notificationPermissionLauncher
            .launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
    }

    private fun hasLocationPermission():
            Boolean {

        val finePermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        val coarsePermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        return (
                finePermission ==
                        PackageManager.PERMISSION_GRANTED ||
                        coarsePermission ==
                        PackageManager.PERMISSION_GRANTED
                )
    }

    private fun checkLocationPermission() {

        if (
            hasLocationPermission()
        ) {

            startLocationTracking()

            if (
                appSettings.drivingModeEnabled
            ) {

                startDrivingService()
            }

        } else {

            run { externalFlow = true; locationPermissionLauncher }.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startDrivingService() {

        if (
            drivingServiceStarted ||
            !appSettings.drivingModeEnabled
        ) {
            return
        }

        try {

            ContextCompat
                .startForegroundService(
                    this,
                    Intent(
                        this,
                        DrivingService::class.java
                    )
                )

            drivingServiceStarted =
                true

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }
    }

    private fun maybeMatchCurrentRoad(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastRoadMatchAt < 8_000L) return
        lastRoadMatchAt = now
        lifecycleScope.launch {
            val matched = withContext(Dispatchers.IO) { currentRoadRepository.match(location) }
            if (matched != null) currentRoad = matched
        }
    }

    /**
     * Real road-network geometry for the HUD mini-map (replaces its old decorative grid).
     * Deliberately on a much slower cadence than [maybeMatchCurrentRoad] above and gated on
     * real movement, not just elapsed time - it is cosmetic background for the mini-map, not
     * something the rider is depending on for a safety decision, so it should never compete
     * with the current-road match for network priority or add meaningfully to data usage.
     */
    private fun maybeRefreshRoadNetwork(location: Location) {
        val now = System.currentTimeMillis()
        val last = lastRoadNetworkLocation
        val moved = last == null || location.distanceTo(last) >= 1100f
        if (!moved && now - lastRoadNetworkAt < 180_000L) return
        lastRoadNetworkAt = now
        lastRoadNetworkLocation = Location(location)
        lifecycleScope.launch {
            val network = withContext(Dispatchers.IO) { currentRoadRepository.fetchNearbyRoadNetwork(location) }
            if (network.isNotEmpty()) {
                nearbyRoadNetwork = network
            } else {
                // Do not leave the HUD mini-map blank for the full normal 3-minute
                // refresh window when one Overpass endpoint times out. Allow a gentle
                // retry after ~30 seconds without hammering the network on every GPS fix.
                lastRoadNetworkAt = System.currentTimeMillis() - 150_000L
            }
        }
    }

    private fun startLocationTracking() {

        if (
            locationTracker != null
        ) {
            return
        }

        locationTracker =
            LocationTracker(
                this
            ) { location ->

                DriveDiagnosticStore.log(
                    "ACTIVITY_GPS",
                    "lat=${location.latitude} lon=${location.longitude} accuracy=${location.accuracy} speedMps=${location.speed} bearing=${if (location.hasBearing()) location.bearing else -1f}"
                )

                currentLocation =
                    location

                publishCommunityPresenceIfEnabled(location)

                val rawSpeedKmh =
                    if (
                        location.hasSpeed()
                    ) {

                        location.speed *
                                3.6f

                    } else {

                        0f
                    }

                filteredSpeedKmh =
                    speedFilter.update(
                        rawSpeedKmh
                    )

                maybeLoadCameraData(
                    location
                )
                maybeMatchCurrentRoad(location)
                maybeRefreshRoadNetwork(location)

                updateRealCameraTarget(
                    location
                )
            }

        locationTracker?.start()
    }

    private fun ensureFirebaseSession() {
        CameraGuardAuthManager.ensureGuestSession(
            onSuccess = {
                Log.d("CameraGuardCommunity", "Firebase session ready")
                DriveDiagnosticStore.log("COMMUNITY_AUTH", "session established anonymous=${it.isAnonymous}")
                syncCommunityStateToFirebase()
                if (appSettings.communityModeEnabled) startSosListener()
            },
            onFailure = { exception ->
                Log.e("CameraGuardCommunity", "Firebase sign-in failed", exception)
                DriveDiagnosticStore.log("COMMUNITY_AUTH", "sign-in failed ${exception.message}")
            }
        )
    }

    private fun startSosListener() {
        if (sosListener != null || !appSettings.communityModeEnabled) return
        val selfUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val reference = communityDatabase.reference.child("sosAlerts")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val fresh = mutableListOf<SosAlert>()
                // Free-tier SOS delivery: there is no FCM/Cloud-Function backend in this
                // project (would require a billing account even to stay within free
                // usage), so this remains an RTDB listener - it only fires while this
                // device's app process is alive with a live connection (foreground or
                // simply backgrounded-but-not-killed; torn down only in onDestroy, see
                // stopSosListener() call sites). A fully killed app or a device that
                // hasn't opened CameraGuard recently cannot be woken by this, and that
                // is a genuine, documented limitation of staying free-only.
                //
                // What IS newly enforced here, for free, entirely client-side: the
                // 10 km eligibility radius from the spec. Every community-enabled
                // client used to be notified of every SOS worldwide (no distance check
                // existed at all); each recipient now computes its own distance to the
                // alert using its own last-known location and only notifies itself
                // when within range and that location is recent enough to trust.
                val myLocation = currentLocation
                val myLocationFresh = myLocation != null &&
                    (now - myLocation.time) in 0L..SOS_LOCATION_MAX_AGE_MS
                // Spec requires SOS recipients to be Google-authenticated specifically
                // (basic presence/SOS otherwise also works for an anonymous guest
                // session - unchanged for sending/being visible - this only narrows
                // who gets notified).
                val selfUser = FirebaseAuth.getInstance().currentUser
                val selfGoogleLinked = selfUser != null && !selfUser.isAnonymous &&
                    selfUser.providerData.any { it.providerId == "google.com" }
                snapshot.children.forEach { child ->
                    val uid = child.key ?: return@forEach
                    val name = child.child("displayName").getValue(String::class.java) ?: "Rider"
                    val lat = child.child("latitude").getValue(Double::class.java) ?: return@forEach
                    val lon = child.child("longitude").getValue(Double::class.java) ?: return@forEach
                    val createdAt = child.child("createdAt").getValue(Long::class.java) ?: return@forEach
                    val expiresAt = child.child("expiresAt").getValue(Long::class.java) ?: return@forEach
                    val active = child.child("active").getValue(Boolean::class.java) ?: false
                    val alert = SosAlert(uid, name, lat, lon, createdAt, expiresAt, active)
                    if (!alert.isFresh(now)) return@forEach
                    fresh += alert
                    if (uid != selfUid) {
                        val withinRadius = selfGoogleLinked && myLocationFresh && communityDistanceMeters(
                            myLocation!!.latitude, myLocation.longitude, lat, lon
                        ) <= SOS_NOTIFY_RADIUS_METERS
                        if (withinRadius) {
                            val eventKey = "$uid:$createdAt"
                            if (seenSosEvents.add(eventKey)) SosNotifier.post(this@MainActivity, alert)
                        }
                    }
                }
                sosAlerts = fresh.sortedByDescending { it.createdAtMillis }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CameraGuardSOS", "SOS listener cancelled", error.toException())
            }
        }
        reference.addValueEventListener(listener)
        sosListener = listener
    }

    private fun stopSosListener() {
        sosListener?.let { communityDatabase.reference.child("sosAlerts").removeEventListener(it) }
        sosListener = null
        sosAlerts = emptyList()
    }

    private fun publishSosAlert() {
        if (!appSettings.communityModeEnabled || !appSettings.communityConsentAccepted) {
            DriveDiagnosticStore.log("SOS", "blocked: Community Mode disabled")
            return
        }
        val location = currentLocation ?: run {
            DriveDiagnosticStore.log("SOS", "blocked: no GPS fix")
            return
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val displayName = appSettings.communityDisplayName.trim().take(40)
            .ifBlank { FirebaseAuth.getInstance().currentUser?.displayName?.trim()?.take(40).orEmpty() }
            .ifBlank { "Rider" }
        val now = System.currentTimeMillis()
        val expires = now + SOS_ALERT_DURATION_MILLIS
        val payload = mapOf<String, Any>(
            "displayName" to displayName,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "createdAt" to now,
            "expiresAt" to expires,
            "active" to true
        )
        communityDatabase.reference.child("sosAlerts").child(uid).setValue(payload)
            .addOnSuccessListener {
                startSosListener()
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(SOS_ALERT_DURATION_MILLIS)
                    val ref = communityDatabase.reference.child("sosAlerts").child(uid)
                    ref.get().addOnSuccessListener { snapshot ->
                        val storedCreatedAt = snapshot.child("createdAt").getValue(Long::class.java)
                        if (storedCreatedAt == now) ref.removeValue()
                    }
                }
                DriveDiagnosticStore.log("SOS", "published exact emergency location")
            }
            .addOnFailureListener { e -> Log.e("CameraGuardSOS", "SOS publish failed", e) }
    }

    private fun cancelOwnSosAlert() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        communityDatabase.reference.child("sosAlerts").child(uid).removeValue()
            .addOnFailureListener { e -> Log.e("CameraGuardSOS", "SOS cancel failed", e) }
    }

    private fun handleSosNotificationIntent(source: Intent?) {
        source ?: return
        if (!source.hasExtra(EXTRA_SOS_LAT) || !source.hasExtra(EXTRA_SOS_LON)) return
        val lat = source.getDoubleExtra(EXTRA_SOS_LAT, Double.NaN)
        val lon = source.getDoubleExtra(EXTRA_SOS_LON, Double.NaN)
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        val uid = source.getStringExtra(EXTRA_SOS_UID) ?: "sos"
        val name = source.getStringExtra(EXTRA_SOS_NAME)?.take(40) ?: "Rider"
        val createdAt = source.getLongExtra(EXTRA_SOS_CREATED_AT, System.currentTimeMillis())
        pendingSosFocus = SosAlert(uid, name, lat, lon, createdAt, createdAt + SOS_ALERT_DURATION_MILLIS, true)
        sosFocusRequest++
    }

    /** Tap target for a rider-message notification (see ChatNotifier.postChatNotification) -
     *  jumps straight to the tapped conversation instead of just bringing the app to the
     *  foreground. Same shape as handleSosNotificationIntent above. */
    private fun handleChatNotificationIntent(source: Intent?) {
        source ?: return
        val conversationId = source.getStringExtra(EXTRA_CHAT_CONVERSATION_ID) ?: return
        pendingChatOpenConversationId = conversationId
        chatOpenRequest++
    }

    @Deprecated("Compatibility bridge for the legacy Google Sign-In fallback used when Credential Manager is unavailable")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (CameraGuardAuthManager.handleActivityResult(requestCode, resultCode, data)) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSosNotificationIntent(intent)
        handleChatNotificationIntent(intent)
    }

    private fun publishRoadReport(reportType: String) {
        val location = currentLocation ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val report = hashMapOf<String, Any>(
            "type" to reportType,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "roadName" to (currentRoad?.name ?: "Unknown road"),
            "reporterUid" to uid,
            "createdAt" to ServerValue.TIMESTAMP
        )
        communityDatabase.reference.child("roadReports").push().setValue(report)
            .addOnSuccessListener { DriveDiagnosticStore.log("ROAD_REPORT", reportType) }
            .addOnFailureListener { e -> Log.e("CameraGuardCommunity", "Road report failed", e) }
    }

    private fun setCommunityMode(
        enabled: Boolean,
        displayName: String
    ) {
        val cleanName = displayName.trim().take(40)

        if (enabled) {
            if (cleanName.isBlank()) return

            settingsStore.setCommunityDisplayName(cleanName)
            settingsStore.setCommunityConsentAccepted(true)
            settingsStore.setCommunityModeEnabled(true)
            appSettings = settingsStore.load()

            ensureFirebaseSession()
            syncCommunityStateToFirebase()
            currentLocation?.let { publishCommunityPresenceIfEnabled(it, force = true) }
        } else {
            settingsStore.setCommunityModeEnabled(false)
            appSettings = settingsStore.load()
            cancelOwnSosAlert()
            removeCommunityPresence()
            writeCommunityProfile(enabled = false)
            stopCommunityRiderSubscriptions()
            stopSosListener()
            DriveDiagnosticStore.log("COMMUNITY_PRESENCE_REMOVE", "community mode disabled by user")
        }
    }

    private fun updateCommunityDisplayName(displayName: String) {
        val cleanName = displayName.trim().take(40)
        if (cleanName.isBlank()) return

        settingsStore.setCommunityDisplayName(cleanName)
        appSettings = settingsStore.load()

        if (appSettings.communityModeEnabled) {
            writeCommunityProfile(enabled = true)
            currentLocation?.let { publishCommunityPresenceIfEnabled(it, force = true) }
        }
    }

    private fun syncCommunityStateToFirebase() {
        if (appSettings.communityModeEnabled &&
            appSettings.communityConsentAccepted &&
            appSettings.communityDisplayName.isNotBlank()
        ) {
            writeCommunityProfile(enabled = true)
            configureCommunityDisconnectCleanup()
            currentLocation?.let { publishCommunityPresenceIfEnabled(it, force = true) }
        } else {
            removeCommunityPresence()
            if (appSettings.communityDisplayName.isNotBlank()) {
                writeCommunityProfile(enabled = false)
            }
        }
    }

    private fun writeCommunityProfile(enabled: Boolean) {
        val selfUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = selfUser.uid
        val name = appSettings.communityDisplayName.trim().take(40)
        if (name.isBlank()) return

        // Free-only avatar: only ever the URL Google already hosts for this account's
        // photo, never anything CameraGuard uploads or stores itself. See the doc
        // comment on CommunityRider.photoUrl for why this is free.
        val googlePhotoUrl =
            if (!selfUser.isAnonymous && selfUser.providerData.any { it.providerId == "google.com" }) {
                selfUser.photoUrl?.toString()
            } else {
                null
            }

        val profile = mutableMapOf<String, Any>(
            "displayName" to name,
            "communityEnabled" to enabled
        )
        if (googlePhotoUrl != null) profile["photoUrl"] = googlePhotoUrl

        communityDatabase.reference
            .child("users")
            .child(uid)
            .setValue(profile)
            .addOnSuccessListener { if (enabled) startSosListener() }
            .addOnFailureListener { exception ->
                Log.e("CameraGuardCommunity", "Profile write failed", exception)
            }
    }

    private fun configureCommunityDisconnectCleanup() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listOf("presence", "privatePresence").forEach { node ->
            communityDatabase.reference.child(node).child(uid)
                .onDisconnect().removeValue()
        }
    }

    private fun publishCommunityPresenceIfEnabled(
        location: Location,
        force: Boolean = false
    ) {
        if (!appSettings.communityModeEnabled ||
            !appSettings.communityConsentAccepted ||
            appSettings.communityDisplayName.isBlank()
        ) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        if (!force && now - lastCommunityPresenceWriteAt < COMMUNITY_PRESENCE_UPDATE_INTERVAL_MILLIS) {
            return
        }
        lastCommunityPresenceWriteAt = now

        configureCommunityDisconnectCleanup()

        val heading =
            if (location.hasBearing()) {
                (((location.bearing % 360f) + 360f) % 360f).toDouble()
            } else {
                0.0
            }

        val speedKmh =
            if (location.hasSpeed()) {
                (location.speed * 3.6f).coerceIn(0f, 300f).toDouble()
            } else {
                0.0
            }

        val cell =
            communityGridCell(
                latitude = location.latitude,
                longitude = location.longitude
            )

        // Public coordinates are rounded, never the exact GPS fix - but rounded to a much
        // finer grid than the ~20km COMMUNITY_CELL_SIZE_DEGREES bucket used just above for
        // "cell" (that field only scopes which Firebase listeners get subscribed to; it was
        // previously reused here too, which is why a published rider marker could be off by
        // up to ~14km even standing still next to another phone - see the change report).
        // COMMUNITY_DISPLAY_PRECISION_DEGREES rounds to a much smaller ~100m grid instead:
        // still not the raw GPS fix, but close enough for another rider's marker to be useful.
        val coarseLat = (floor(location.latitude / COMMUNITY_DISPLAY_PRECISION_DEGREES) + 0.5) * COMMUNITY_DISPLAY_PRECISION_DEGREES
        val coarseLon = (floor(location.longitude / COMMUNITY_DISPLAY_PRECISION_DEGREES) + 0.5) * COMMUNITY_DISPLAY_PRECISION_DEGREES
        // Free-only avatar: republishes the Google-hosted account photo URL only - see
        // CommunityRider.photoUrl doc comment and writeCommunityProfile() above.
        val selfUserForPhoto = FirebaseAuth.getInstance().currentUser
        val presenceGooglePhotoUrl =
            if (selfUserForPhoto != null && !selfUserForPhoto.isAnonymous &&
                selfUserForPhoto.providerData.any { it.providerId == "google.com" }
            ) {
                selfUserForPhoto.photoUrl?.toString()
            } else {
                null
            }
        val presence = hashMapOf<String, Any>(
            "displayName" to appSettings.communityDisplayName.trim().take(40),
            "latitude" to coarseLat,
            "longitude" to coarseLon,
            "heading" to (if (speedKmh >= 5.0 && location.hasBearing()) (kotlin.math.floor(heading / 45.0) * 45.0) else 0.0),
            "moving" to (speedKmh >= 5.0 && location.hasBearing()),
            // Previously hardcoded to 0.0 - a leftover workaround for the old RTDB rule that
            // required speedKmh==0 on every presence write (see change report). That rule no
            // longer exists, and other riders' "X km/h" / "Moving"/"stopped" displays read this
            // field directly (CameraGuardApp.kt), so publishing 0.0 always made every online
            // rider appear stationary regardless of their real speed. Publish the real,
            // already-rounded/clamped speed computed above instead.
            "speedKmh" to speedKmh,
            "cell" to cell,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        if (presenceGooglePhotoUrl != null) presence["photoUrl"] = presenceGooglePhotoUrl
        // Never publish exact coordinates to /presence.
        val privatePresence = mapOf<String, Any>(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        communityDatabase.reference.child("privatePresence").child(uid)
            .updateChildren(privatePresence)
        communityDatabase.reference.child("presence").child(uid)
            .setValue(presence)
            .addOnFailureListener { exception ->
                Log.e("CameraGuardCommunity", "Approximate presence write failed", exception)
            }

        updateCommunityRiderSubscriptions(location)
    }

    /*
     * Coarse geographic bucket (~5km at this latitude) used purely to scope
     * community reads/writes. This is NOT a precise geolocation key on its
     * own beyond what latitude/longitude already are - it just lets the
     * client subscribe to "riders near me" instead of "every rider using
     * CameraGuard anywhere".
     */
    private fun communityGridCell(
        latitude: Double,
        longitude: Double
    ): String {
        val latBucket =
            floor(latitude / COMMUNITY_CELL_SIZE_DEGREES).toInt()
        val lonBucket =
            floor(longitude / COMMUNITY_CELL_SIZE_DEGREES).toInt()
        return "$latBucket:$lonBucket"
    }

    private fun communityNeighborCells(
        centerCell: String
    ): Set<String> {
        val parts = centerCell.split(":")
        val latBucket = parts.getOrNull(0)?.toIntOrNull() ?: return setOf(centerCell)
        val lonBucket = parts.getOrNull(1)?.toIntOrNull() ?: return setOf(centerCell)
        val cells = mutableSetOf<String>()
        // +/- COMMUNITY_CELL_RADIUS_CELLS cells around the rider's own cell,
        // sized so the queried grid window comfortably covers the
        // COMMUNITY_RIDER_RADIUS_METERS discovery radius (see the cell-size
        // comment below). The hard 50 km cutoff itself is enforced with an
        // actual distance check once rider positions are read back (see
        // updateCommunityRiderSubscriptions), so this grid only controls
        // which Firebase listeners we subscribe to - it never widens the
        // effective radius on its own.
        for (dLat in -COMMUNITY_CELL_RADIUS_CELLS..COMMUNITY_CELL_RADIUS_CELLS) {
            for (dLon in -COMMUNITY_CELL_RADIUS_CELLS..COMMUNITY_CELL_RADIUS_CELLS) {
                cells.add("${latBucket + dLat}:${lonBucket + dLon}")
            }
        }
        return cells
    }

    private fun communityDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toFloat()
    }

    /**
     * Keeps a live subscription to the 3x3 grid-cell neighbourhood around
     * the rider's current location. Only re-subscribes when the rider has
     * moved into a new centre cell or when a refresh interval has passed,
     * so this does not re-attach listeners on every single GPS tick.
     */
    private fun updateCommunityRiderSubscriptions(
        location: Location
    ) {
        if (!appSettings.communityModeEnabled ||
            !appSettings.communityConsentAccepted
        ) {
            stopCommunityRiderSubscriptions()
            return
        }

        val now = System.currentTimeMillis()
        val centerCell =
            communityGridCell(location.latitude, location.longitude)

        val centerUnchanged =
            centerCell == communitySubscribedCenterCell

        if (centerUnchanged &&
            now - lastCommunityRiderRefreshAt < COMMUNITY_RIDER_REFRESH_INTERVAL_MILLIS
        ) {
            return
        }

        lastCommunityRiderRefreshAt = now
        communitySubscribedCenterCell = centerCell

        val wantedCells =
            communityNeighborCells(centerCell)

        val staleCells =
            communityCellListeners.keys.filter { it !in wantedCells }

        staleCells.forEach { cell ->
            communityCellQueries[cell]?.let { query ->
                communityCellListeners[cell]?.let { listener ->
                    query.removeEventListener(listener)
                }
            }
            communityCellQueries.remove(cell)
            communityCellListeners.remove(cell)
            communityRidersByCell.remove(cell)
        }

        val newCells =
            wantedCells.filter { it !in communityCellListeners }

        val selfUid = FirebaseAuth.getInstance().currentUser?.uid

        newCells.forEach { cell ->
            val query =
                communityDatabase.reference
                    .child("presence")
                    .orderByChild("cell")
                    .equalTo(cell)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val here = currentLocation
                    val riders = mutableMapOf<String, CommunityRider>()

                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        if (uid == selfUid) continue

                        val lat = child.child("latitude").getValue(Double::class.java) ?: continue
                        val lon = child.child("longitude").getValue(Double::class.java) ?: continue
                        val name = child.child("displayName").getValue(String::class.java) ?: continue
                        val heading = child.child("heading").getValue(Double::class.java) ?: 0.0
                        val speed = child.child("speedKmh").getValue(Double::class.java) ?: 0.0
                        val moving = child.child("moving").getValue(Boolean::class.java) ?: false
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        val riderCell = child.child("cell").getValue(String::class.java) ?: cell
                        val roadName = child.child("roadName").getValue(String::class.java)
                        val photoUrl = child.child("photoUrl").getValue(String::class.java)
                        val isFresh =
                            System.currentTimeMillis() - lastSeen <=
                                COMMUNITY_PRESENCE_STALE_MILLIS

                        if (!isFresh) continue

                        // The 3x3(+)-cell subscription grid is a coarse
                        // pre-filter for which Firebase listeners we open; it
                        // is not itself the radius. Enforce the actual 50 km
                        // discovery radius here against real rider distance
                        // so the functional cutoff is exact, not just a
                        // label. If our own position isn't known yet we
                        // cannot verify the rider is in range, so skip them
                        // rather than showing unverified/worldwide riders.
                        val here = here ?: continue

                        val distance =
                            communityDistanceMeters(
                                lat1 = here.latitude,
                                lon1 = here.longitude,
                                lat2 = lat,
                                lon2 = lon
                            )

                        if (distance > COMMUNITY_RIDER_RADIUS_METERS) continue

                        riders[uid] =
                            CommunityRider(
                                uid = uid,
                                displayName = name,
                                latitude = lat,
                                longitude = lon,
                                headingDegrees = heading,
                                speedKmh = speed,
                                moving = moving,
                                lastSeenMillis = lastSeen,
                                cell = riderCell,
                                distanceMeters = distance,
                                roadName = roadName,
                                photoUrl = photoUrl
                            )
                    }

                    communityRidersByCell[cell] = riders
                    val flattened =
                        communityRidersByCell.values
                            .flatMap { it.values }
                            .sortedBy { it.distanceMeters }

                    if (flattened.size != communityRiders.size) {
                        DriveDiagnosticStore.log(
                            "COMMUNITY_RIDER_COUNT",
                            "nearby=${flattened.size}"
                        )
                    }

                    communityRiders = flattened
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("CameraGuardCommunity", "Rider read cancelled for cell $cell", error.toException())
                }
            }

            query.addValueEventListener(listener)
            communityCellQueries[cell] = query
            communityCellListeners[cell] = listener
            DriveDiagnosticStore.log("COMMUNITY_LISTENER", "subscribed cell=$cell")
        }
    }

    private fun stopCommunityRiderSubscriptions() {
        communityCellQueries.forEach { (cell, query) ->
            communityCellListeners[cell]?.let { listener ->
                query.removeEventListener(listener)
            }
        }
        if (communityCellQueries.isNotEmpty()) {
            DriveDiagnosticStore.log(
                "COMMUNITY_LISTENER",
                "unsubscribed ${communityCellQueries.size} cell(s)"
            )
        }
        communityCellQueries.clear()
        communityCellListeners.clear()
        communityRidersByCell.clear()
        communitySubscribedCenterCell = null
        communityRiders = emptyList()
    }

    private fun removeCommunityPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listOf("presence", "privatePresence").forEach { node ->
            communityDatabase.reference.child(node).child(uid).removeValue()
        }
    }

    private fun allCameras():
            List<RealCamera> {

        return overrideStore.filterVisible(osmCameras.filter {
            it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
        }) + masterCameras + manualCameras
    }

    private fun updateRealCameraTarget(
        location: Location
    ) {
        /*
         * The DrivingService is now the ONLY owner of RealCameraWarningEngine.
         * The UI mirrors its published target instead of running a second
         * independent state machine.
         */
        activeCameraTarget =
            CameraWarningRuntime.latestTarget
    }

    private fun maybeLoadCameraData(
        location: Location
    ) {
        maybeRefreshMasterCameras(location)
        if (cameraLoadInProgress) return

        val previousLat = lastCameraAreaLatitude
        val previousLon = lastCameraAreaLongitude

        val movedFarEnough =
            if (previousLat == null || previousLon == null) {
                true
            } else {
                val result = FloatArray(1)
                Location.distanceBetween(
                    previousLat,
                    previousLon,
                    location.latitude,
                    location.longitude,
                    result
                )
                result[0] >= CAMERA_AREA_REFRESH_DISTANCE_METERS
            }

        val stale =
            System.currentTimeMillis() - lastCameraAreaLoadedAt >=
                CACHE_REFRESH_INTERVAL_MILLIS

        if (!movedFarEnough && !stale) return

        loadCameraData(
            location = location,
            forceNetworkRefresh = movedFarEnough && previousLat != null
        )
    }

    private fun loadCameraData(
        location: Location,
        forceNetworkRefresh: Boolean
    ) {
        if (cameraLoadInProgress) return

        cameraLoadInProgress = true
        lastCameraAreaLatitude = location.latitude
        lastCameraAreaLongitude = location.longitude
        lastCameraAreaLoadedAt = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val cachedData =
                    withContext(Dispatchers.IO) { cameraCache.load() }

                val usableCache =
                    cachedData?.takeIf {
                        isCacheNearCurrentArea(
                            cache = it,
                            location = location
                        )
                    }

                if (usableCache != null) {
                    osmCameras = usableCache.cameras
                    cameraDataError = null
                    updateRealCameraTarget(location)
                }

                val shouldRefresh =
                    forceNetworkRefresh ||
                        shouldRefreshCameraData(usableCache) ||
                        usableCache == null

                if (!shouldRefresh) {
                    cameraDataLoading = false
                    return@launch
                }

                cameraDataLoading = usableCache == null

                val fresh =
                    withContext(Dispatchers.IO) {
                        cameraRepository.loadNearbyCameras(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            radiusMeters = 25000
                        )
                    }

                val freshMasters = fresh.filter {
                    it.source == com.boss.cameraguard.data.RealCameraSource.MASTER
                }
                if (freshMasters.isNotEmpty()) {
                    masterCameras = freshMasters
                    lastMasterCameraRefreshAt = System.currentTimeMillis()
                }

                val merged =
                    trimCameraCoverage(
                        cameras =
                            mergeFreshWithCache(
                                cached = osmCameras.filter {
                                    it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                                },
                                fresh = fresh.filter {
                                    it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                                }
                            ),
                        location = location
                    )

                if (merged.isNotEmpty()) {
                    osmCameras = merged
                    cameraDataError = null

                    withContext(Dispatchers.IO) {
                        cameraCache.save(
                            cameras = merged.filter {
                                it.source != com.boss.cameraguard.data.RealCameraSource.MASTER
                            },
                            centerLatitude = location.latitude,
                            centerLongitude = location.longitude
                        )
                    }

                    updateRealCameraTarget(location)
                } else if (usableCache == null && osmCameras.isEmpty()) {
                    cameraDataError = "No camera data available"
                }

            } catch (exception: Exception) {
                // Offline-safe: never discard already loaded/cached cameras.
                if (osmCameras.isEmpty()) {
                    cameraDataError =
                        exception.message ?: "Camera data load failed"
                }
            } finally {
                cameraDataLoading = false
                cameraLoadInProgress = false
            }
        }
    }


    private fun maybeRefreshMasterCameras(location: Location) {
        val now = System.currentTimeMillis()
        if (masterCameraRefreshInProgress) return
        if (now - lastMasterCameraRefreshAt < MASTER_CAMERA_REFRESH_INTERVAL_MILLIS) return

        masterCameraRefreshInProgress = true
        lastMasterCameraRefreshAt = now

        lifecycleScope.launch {
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
                    "ui refresh count=${freshMasters.size}"
                )
                updateRealCameraTarget(location)
            } catch (exception: Exception) {
                DriveDiagnosticStore.log(
                    "MASTER_SYNC",
                    "ui refresh failed ${exception.message}"
                )
            } finally {
                masterCameraRefreshInProgress = false
            }
        }
    }

    private fun mergeFreshWithCache(
        cached: List<RealCamera>,
        fresh: List<RealCamera>
    ): List<RealCamera> {
        if (cached.isEmpty()) return fresh
        if (fresh.isEmpty()) return cached

        val merged = RealCameraType.entries.flatMap { type ->
            val freshType = fresh.filter { it.type == type }
            if (freshType.isNotEmpty()) freshType else cached.filter { it.type == type }
        }

        return merged.distinctBy { camera ->
            if (camera.relationId != null) {
                "REL:${camera.type}:${camera.relationId}"
            } else {
                "CAM:${camera.type}:${camera.source}:${camera.id}"
            }
        }
    }

    private fun trimCameraCoverage(
        cameras: List<RealCamera>,
        location: Location
    ): List<RealCamera> {

        return cameras.filter { camera ->
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

    private fun isCacheNearCurrentArea(
        cache: CachedCameraData,
        location: Location
    ): Boolean {

        val results =
            FloatArray(1)

        Location.distanceBetween(
            cache.centerLatitude,
            cache.centerLongitude,
            location.latitude,
            location.longitude,
            results
        )

        return results[0] <=
                CACHE_AREA_DISTANCE_METERS
    }

    private fun shouldRefreshCameraData(
        cache: CachedCameraData?
    ): Boolean {

        if (
            cache == null
        ) {
            return true
        }

        return (
                System.currentTimeMillis() -
                        cache.savedAtMillis
                ) >=
                CACHE_REFRESH_INTERVAL_MILLIS
    }

    override fun onDestroy() {

        locationTracker?.stop()

        speedFilter.reset()

        voiceWarningManager.shutdown()

        alertSoundManager.release()

        stopCommunityRiderSubscriptions()
        stopSosListener()

        super.onDestroy()
    }

    companion object {
        const val EXTRA_SOS_UID = "camera_guard_sos_uid"
        const val EXTRA_SOS_NAME = "camera_guard_sos_name"
        const val EXTRA_SOS_LAT = "camera_guard_sos_lat"
        const val EXTRA_SOS_LON = "camera_guard_sos_lon"
        const val EXTRA_SOS_CREATED_AT = "camera_guard_sos_created_at"
        const val EXTRA_CHAT_CONVERSATION_ID = "camera_guard_chat_conversation_id"
        private const val SOS_ALERT_DURATION_MILLIS = 10 * 60 * 1000L


        private const val COMMUNITY_PRESENCE_UPDATE_INTERVAL_MILLIS =
            5000L

        /* ~20km grid bucket at Italian latitudes; used only to scope which
         * Firebase listeners this device subscribes to (see
         * updateCommunityRiderSubscriptions/communityNeighborCells). Combined
         * with COMMUNITY_CELL_RADIUS_CELLS below this covers comfortably more
         * than COMMUNITY_RIDER_RADIUS_METERS in every direction so the actual
         * 50 km cutoff (enforced separately against real rider distance) is
         * never starved of data by the grid. */
        private const val COMMUNITY_CELL_SIZE_DEGREES =
            0.18

        /* ~100m rounding grid used ONLY for the actual published presence latitude/longitude
         * (the coordinate other riders' markers are drawn at). Deliberately separate from
         * COMMUNITY_CELL_SIZE_DEGREES above, which is ~20km and exists purely to scope which
         * Firebase listeners this device subscribes to - it was previously (incorrectly) reused
         * for the displayed coordinate too, making every rider marker jump to the centre of a
         * ~20km square. This constant keeps the same "never publish the raw GPS fix" privacy
         * property while making the published position actually useful on a map. */
        private const val COMMUNITY_DISPLAY_PRECISION_DEGREES =
            0.001

        /* +/- this many cells around the rider's own cell are subscribed to,
         * i.e. a (2*N+1) x (2*N+1) grid. With a ~20km cell this yields a
         * subscribed window of roughly 100km x 100km, safely covering the
         * 50 km radius in every direction without subscribing to unbounded
         * worldwide data. */
        private const val COMMUNITY_CELL_RADIUS_CELLS =
            2

        /* Community Rider discovery radius. Real functional cutoff, applied
         * against actual rider distance in updateCommunityRiderSubscriptions
         * - not just a label. */
        private const val COMMUNITY_RIDER_RADIUS_METERS =
            50_000f

        /* SOS notification eligibility radius, per spec: an SOS alert should reach
         * every eligible community-enabled user within 10 km, not the whole
         * (previously unfiltered) global sosAlerts feed. */
        private const val SOS_NOTIFY_RADIUS_METERS =
            10_000f

        /* A recipient's own last-known location must be at least this fresh to be
         * trusted for the 10 km radius check ("sufficiently recent known location
         * data" in the spec). Deliberately more lenient than live-navigation
         * freshness checks elsewhere - this only needs to be roughly where the
         * rider still is, not a live GPS-tick-accurate fix. */
        private const val SOS_LOCATION_MAX_AGE_MS =
            15 * 60 * 1000L

        private const val COMMUNITY_RIDER_REFRESH_INTERVAL_MILLIS =
            20_000L

        /* A rider not heard from in 3 minutes is treated as stale/offline
         * even if their onDisconnect cleanup did not fire (e.g. crash). */
        private const val COMMUNITY_PRESENCE_STALE_MILLIS =
            3L * 60L * 1000L

        private const val
                CACHE_REFRESH_INTERVAL_MILLIS =
            6L *
                    60L *
                    60L *
                    1000L

        private const val
                CACHE_AREA_DISTANCE_METERS =
            12000f

        private const val
                CAMERA_AREA_REFRESH_DISTANCE_METERS =
            8000f

        private const val
                CAMERA_COVERAGE_KEEP_RADIUS_METERS =
            35000f

        private const val MASTER_CAMERA_REFRESH_INTERVAL_MILLIS =
            60L * 1000L
    }
}
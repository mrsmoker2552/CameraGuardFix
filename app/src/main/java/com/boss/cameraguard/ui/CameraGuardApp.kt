package com.boss.cameraguard.ui

import android.location.Location
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.util.Log
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import com.boss.cameraguard.data.NavigationProgressTracker
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.boss.cameraguard.alerts.RealCameraTarget
import com.boss.cameraguard.CameraGuardAuthManager
import com.boss.cameraguard.data.AppSettings
import com.boss.cameraguard.data.AppThemeMode
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.data.CurrentRoad
import com.boss.cameraguard.data.RealCamera
import com.boss.cameraguard.data.RealCameraSource
import com.boss.cameraguard.data.RealCameraType
import com.boss.cameraguard.data.SosAlert
import com.boss.cameraguard.data.VehicleMode
import com.boss.cameraguard.data.NavigationRouteRuntime
import com.boss.cameraguard.data.RoutePlannerRepository
import com.boss.cameraguard.data.RoutePreferences
import com.boss.cameraguard.data.RoutePreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.boss.cameraguard.data.displayName
import com.boss.cameraguard.data.usesSpeedWarningDistance
import com.boss.cameraguard.ui.theme.CameraGuardPalette
import com.boss.cameraguard.ui.theme.neumorphicRaised
import com.boss.cameraguard.ui.theme.neumorphicInset
import com.boss.cameraguard.map.RealMapView
import kotlin.math.roundToInt
import com.boss.cameraguard.chat.ChatRepository
import com.boss.cameraguard.ui.chat.ChatHostScreen
import com.boss.cameraguard.ui.community.CommunityHostScreen
import com.boss.cameraguard.location.HeadingFilter
import com.boss.cameraguard.voice.NavigationVoiceSpeaker
import com.google.firebase.auth.FirebaseAuth

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val PremiumAccent: Color get() = CameraGuardPalette.Accent
private val PremiumLine: Color get() = CameraGuardPalette.Border
private val AppBackground: Color get() = CameraGuardPalette.Background
private val SurfaceDark: Color get() = CameraGuardPalette.Surface
private val SurfaceSoft: Color get() = CameraGuardPalette.Raised
private val ElectricBlue: Color get() = CameraGuardPalette.RouteBlue
private val CyanGlow: Color get() = CameraGuardPalette.Accent
private val TextPrimary: Color get() = CameraGuardPalette.Text
private val TextSecondary: Color get() = CameraGuardPalette.Muted
private val WarningAmber: Color get() = CameraGuardPalette.Warning
private val DangerRed: Color get() = CameraGuardPalette.Danger
private val SuccessGreen: Color get() = CameraGuardPalette.Success


@Composable
fun VehicleSelectionScreen(onSelected: (VehicleMode) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<VehicleMode?>(null) }
    Column(Modifier.fillMaxSize().background(AppBackground).padding(26.dp), verticalArrangement = Arrangement.Center) {
        Text("CAMERAGUARD", color=CyanGlow, fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp)
        Spacer(Modifier.height(10.dp))
        Text("Choose your vehicle", color=TextPrimary, fontSize=30.sp, fontWeight=FontWeight.ExtraBold)
        Text("This tunes the driving HUD for your ride.", color=TextSecondary, fontSize=14.sp, lineHeight=20.sp)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf(VehicleMode.SCOOTER to Icons.Default.TwoWheeler, VehicleMode.CAR to Icons.Default.DirectionsCar).forEach { (mode, icon) ->
                val active=selected==mode
                Surface(onClick={selected=mode}, modifier=Modifier.weight(1f).height(150.dp).neumorphicRaised(RoundedCornerShape(30.dp), if(active) lerp(SurfaceDark,CyanGlow,.10f) else SurfaceDark, if(active) 12.dp else 9.dp), shape=RoundedCornerShape(30.dp), color=Color.Transparent, border=BorderStroke(if(active) 1.5.dp else 1.dp, if(active) CyanGlow else PremiumLine.copy(alpha=.45f))) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                        Icon(icon,null,Modifier.size(48.dp),tint=if(active) CyanGlow else TextSecondary)
                        Spacer(Modifier.height(12.dp)); Text(mode.name,color=if(active) TextPrimary else TextSecondary,fontWeight=FontWeight.ExtraBold,fontSize=16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick={selected?.let(onSelected)}, enabled=selected!=null, modifier=Modifier.fillMaxWidth().height(56.dp).shadow(10.dp,RoundedCornerShape(20.dp),clip=false), shape=RoundedCornerShape(20.dp)) { Text("CONTINUE",fontWeight=FontWeight.Bold) }
    }
}

@Composable
fun CameraGuardOnboarding(
    onAccept: (VehicleMode) -> Unit
) {
    var selectedVehicle by rememberSaveable { mutableStateOf(VehicleMode.SCOOTER) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 34.dp),
        verticalArrangement =
            Arrangement.spacedBy(18.dp)
    ) {

        Surface(
            shape = CircleShape,
            color = ElectricBlue.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f))
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = CyanGlow,
                modifier = Modifier.padding(16.dp).size(34.dp)
            )
        }

        Text(
            text = "Welcome to CameraGuard",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Text(
            text = "CameraGuard uses your location and mapped enforcement data to help warn about speed and red-light cameras while you travel.",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )

        OnboardingInfoCard(
            icon = Icons.Default.Route,
            title = "Road-aware warnings",
            text = "Default warning zones are 150 m for red-light cameras and 300 m for speed cameras. You can adjust both distances in Settings."
        )

        OnboardingInfoCard(
            icon = Icons.Default.Public,
            title = "Works wherever data is available",
            text = "Nearby camera coverage follows your GPS position and refreshes as you travel into new areas. Cached data remains available during temporary network loss."
        )

        OnboardingInfoCard(
            icon = Icons.Default.MyLocation,
            title = "Location access",
            text = "Location is required for map position, travel direction and camera warnings. Driving Mode can continue monitoring in the background when enabled."
        )

        OnboardingInfoCard(
            icon = Icons.Default.WarningAmber,
            title = "Important safety information",
            text = "Camera data can be incomplete, delayed or incorrect. Always follow road signs, traffic signals and local law. Do not interact with the app while driving."
        )

        Text("WHAT ARE YOU USING?", color = PremiumAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(VehicleMode.SCOOTER to Icons.Default.TwoWheeler, VehicleMode.CAR to Icons.Default.DirectionsCar).forEach { (mode, icon) ->
                val selected = selectedVehicle == mode
                Surface(onClick = { selectedVehicle = mode }, modifier = Modifier.weight(1f).neumorphicRaised(RoundedCornerShape(20.dp), if (selected) lerp(SurfaceSoft,CyanGlow,.10f) else SurfaceSoft, if(selected) 10.dp else 7.dp), shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) CyanGlow else Color.White.copy(alpha=.08f))) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(30.dp), tint = if (selected) CyanGlow else TextSecondary)
                        Spacer(Modifier.height(7.dp)); Text(mode.name, color = if (selected) TextPrimary else TextSecondary, fontWeight=FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = "By continuing, you confirm that you understand how CameraGuard works and that camera alerts are an assistance feature, not a substitute for safe driving.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Button(
            onClick = { onAccept(selectedVehicle) },
            modifier = Modifier.fillMaxWidth().height(56.dp).shadow(10.dp, RoundedCornerShape(20.dp), clip=false),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "I UNDERSTAND & CONTINUE",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OnboardingInfoCard(
    icon: ImageVector,
    title: String,
    text: String
) {

    Surface(
        color = SurfaceSoft,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(22.dp), SurfaceSoft, 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricBlue,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = text,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
fun CameraGuardApp(
    liveLocation: Location?,
    filteredSpeedKmh: Float,
    realCameras: List<RealCamera>,
    cameraDataLoading: Boolean,
    cameraDataError: String?,
    activeCameraTarget: RealCameraTarget?,
    currentRoad: CurrentRoad?,
    nearbyRoadNetwork: List<List<com.boss.cameraguard.data.RoadPoint>> = emptyList(),
    appSettings: AppSettings,
    onTestRedLightWarning: () -> Unit,
    onTestSpeedCameraWarning: () -> Unit,
    onAddManualCamera: (RealCameraType, Int?, Float?) -> Unit,
    onDeleteManualCamera: (Long) -> Unit,
    onEditManualCamera: (Long, RealCameraType, Int?, String?, Float?) -> Unit,
    onFixOsmCamera: (RealCamera, RealCameraType, Int?, String?, Float?) -> Unit,
    onExportManualCameras: () -> Unit,
    onImportManualCameras: () -> Unit,
    onRestoreOsmOverrides: () -> Unit,
    hiddenOsmCameraCount: Int,
    onStartDriveDiagnostics: () -> Unit,
    onExportDriveDiagnostics: () -> Unit,
    onDrivingModeChanged: (Boolean) -> Unit,
    onVoiceAlertsChanged: (Boolean) -> Unit,
    onBeepAlertsChanged: (Boolean) -> Unit,
    onVehicleModeChanged: (VehicleMode) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onWarningDistancesChanged: (Map<RealCameraType, Int>) -> Unit,
    onCommunityModeChanged: (Boolean, String) -> Unit,
    onCommunityDisplayNameChanged: (String) -> Unit,
    onRoadReport: (String) -> Unit = {},
    communityRiders: List<CommunityRider> = emptyList(),
    sosAlerts: List<SosAlert> = emptyList(),
    sosFocusAlert: SosAlert? = null,
    sosFocusRequest: Int = 0,
    onSosPressed: () -> Unit = {},
    onSosCancelled: () -> Unit = {},
    onVoicePresetChanged: (Int) -> Unit = {},
    hudModeActive: Boolean = false,
    onHudModeChanged: (Boolean) -> Unit = {}
) {

    var activeTab by rememberSaveable {
        mutableIntStateOf(0)
    }
    var hudMirrorMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sosFocusRequest) {
        if (sosFocusRequest > 0) activeTab = 0
    }
    val appContext = LocalContext.current
    val cameraMapFilterStore = remember(appContext) { com.boss.cameraguard.data.CameraMapFilterStore(appContext) }
    var visibleCameraTypes by remember { mutableStateOf(cameraMapFilterStore.load()) }
    fun updateVisibleCameraTypes(types: Set<RealCameraType>) {
        visibleCameraTypes = types
        cameraMapFilterStore.save(types)
    }

    // Single authoritative background rerouting for the whole app. This used to live inside
    // NavigationScreen and only ran while a fullMap instance of it happened to be composed -
    // i.e. only while the Route tab's own full-screen navigation view was actually on screen -
    // so switching to the Map tab or the HUD while navigating silently stopped automatic
    // rerouting entirely (the rider would have had to switch back to Route to get a reroute at
    // all). Hoisting it here means it keeps running for as long as a route is active, no matter
    // which tab is currently visible, and both the Route tab and the HUD - which each read
    // NavigationRouteRuntime.route/​revision directly - pick up the result the moment it lands.
    val routePreferencesStore = remember(appContext) { RoutePreferencesStore(appContext) }
    val latestNavLocation by rememberUpdatedState(liveLocation)
    LaunchedEffect(Unit) {
        var offRouteSamples = 0
        var lastRerouteAttempt = 0L
        var trackedRoute: com.boss.cameraguard.data.NavigationRoute? = null
        while (true) {
            kotlinx.coroutines.delay(700)
            val current = NavigationRouteRuntime.route?.takeIf { it.active }
            if (current !== trackedRoute) {
                // A different route object (freshly started, or just rerouted) - reset the
                // consecutive-sample counter and the cooldown so a stale count from the
                // previous route can never carry over and trigger an immediate re-reroute.
                trackedRoute = current; offRouteSamples = 0
            }
            if (current == null) continue
            val loc = latestNavLocation ?: continue
            val fresh = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) in 0L..15_000_000_000L
            // Distinguishing genuine deviation from GPS noise/lane-level offset: requires a
            // fresh, reasonably accurate, MOVING fix more than 55m from the route line (a
            // parked/poor-GPS reading sitting far from the line is noise, not "took another
            // road"), confirmed on 3 consecutive 700ms samples (~2.1s) before reacting at all.
            val genuinelyOffRoute = fresh && loc.hasAccuracy() && loc.accuracy <= 35f &&
                loc.hasSpeed() && loc.speed >= 1.0f && distanceFromRouteMeters(loc, current.points) > 55f
            offRouteSamples = if (genuinelyOffRoute) offRouteSamples + 1 else 0
            if (offRouteSamples < 3) continue
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastRerouteAttempt < 2500L) continue
            lastRerouteAttempt = now
            try {
                // Always recalculates FROM the rider's live position TO the same destination -
                // never back toward the old route - so a genuinely shorter/faster road the rider
                // took is accepted rather than fought.
                val recalculated = withContext(Dispatchers.IO) {
                    RoutePlannerRepository.route(loc,
                        RoutePlannerRepository.Place(current.destinationName, current.destinationLat, current.destinationLon),
                        routePreferencesStore.load())
                }
                // Ignore a stale result if the authoritative route already moved on (a newer
                // reroute landed first, the destination changed, navigation was stopped, etc).
                if (NavigationRouteRuntime.route === current) {
                    NavigationRouteRuntime.updateRoute(recalculated.copy(active = true))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep current route; retry after the next off-route confirmation. */ }
            offRouteSamples = 0
        }
    }

    // Rider Community Chat - identity comes from the existing auth session (guest or linked
    // account); chat is simply unavailable (tab omitted) if somehow no session exists yet.
    // Read reactively via an AuthStateListener, not a one-time remember{}: on a fresh
    // install the anonymous guest sign-in (ensureFirebaseSession in MainActivity) completes
    // asynchronously, sometimes after this composable's first pass - a one-time read would
    // have permanently frozen myChatUid at null and hidden the Chat tab for that session.
    var chatUser by remember { mutableStateOf(CameraGuardAuthManager.currentUser()) }
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { chatUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val myChatUid = chatUser?.uid
    val myChatDisplayName = remember(chatUser) {
        chatUser?.displayName?.takeIf { it.isNotBlank() } ?: "Rider"
    }
    val myUidForSos = FirebaseAuth.getInstance().currentUser?.uid
    val ownSosActive = myUidForSos != null && sosAlerts.any { it.uid == myUidForSos && it.isFresh() }
    var pendingDirectTargetUid by remember { mutableStateOf<String?>(null) }
    var chatUnreadCount by remember { mutableIntStateOf(0) }

    if (myChatUid != null) {
        // Posts local in-app notifications for new messages regardless of which tab is
        // active; safe to keep running for the app's whole lifetime (see ChatNotifier.kt).
        com.boss.cameraguard.chat.ChatNewMessageWatcher()

        LaunchedEffect(myChatUid) {
            ChatRepository.observeMyConversations().collect { conversations ->
                var total = 0
                for (conversation in conversations) total += ChatRepository.getUnreadCount(conversation.id)
                chatUnreadCount = total
            }
        }
    }

    Box(Modifier.fillMaxSize()) {

    Scaffold(
        containerColor =
            AppBackground,

        bottomBar = {

            // HUD Mode hides all normal chrome so the mirrored screen only
            // shows essential driving information (see requirement: remove
            // bottom navigation / settings / cameras / logs controls while
            // the windshield-reflection mode is active).
            if (!(activeTab == 4 && hudMirrorMode)) {
                Box {
                    PremiumBottomNavigation(
                        selectedTab = activeTab,
                        onTabSelected = { activeTab = it },
                        showChatTab = myChatUid != null,
                        chatUnreadCount = chatUnreadCount
                    )
                }
            }
        }
    ) { padding ->

        when (
            activeTab
        ) {

            0 -> {

                NavigationScreen(
                    modifier =
                        Modifier.padding(
                            padding
                        ),

                    liveLocation =
                        liveLocation,

                    filteredSpeedKmh =
                        filteredSpeedKmh,

                    realCameras =
                        realCameras,

                    communityRiders =
                        communityRiders,
                    sosAlerts = sosAlerts,
                    sosFocusAlert = sosFocusAlert,
                    sosFocusRequest = sosFocusRequest,

                    activeCameraTarget =
                        activeCameraTarget,

                    appSettings =
                        appSettings,
                    visibleCameraTypes = visibleCameraTypes,
                    ownSosActive = ownSosActive,
                    onSosPressed = onSosPressed,
                    onSosCancelled = onSosCancelled,
                    onMessageRider = { uid ->
                        pendingDirectTargetUid = uid
                        activeTab = 5
                    }
                )
            }

            5 -> {
                CommunityHostScreen(
                    modifier = Modifier.padding(padding),
                    myUid = myChatUid,
                    myDisplayName = appSettings.communityDisplayName.ifBlank { myChatDisplayName },
                    communityEnabled = appSettings.communityModeEnabled,
                    communityRiders = communityRiders,
                    pendingDirectTargetUid = pendingDirectTargetUid,
                    onPendingDirectTargetHandled = { pendingDirectTargetUid = null },
                    onCommunityJoined = { name -> onCommunityModeChanged(true, name) }
                )
            }

            4 -> LiveHudScreen(
                modifier = Modifier.padding(padding),
                liveLocation = liveLocation,
                filteredSpeedKmh = filteredSpeedKmh,
                realCameras = realCameras,
                activeCameraTarget = activeCameraTarget,
                currentRoad = currentRoad,
                nearbyRoadNetwork = nearbyRoadNetwork,
                appSettings = appSettings,
                sosAlerts = sosAlerts,
                ownSosActive = ownSosActive,
                onSosPressed = onSosPressed,
                onSosCancelled = onSosCancelled,
                hudModeActive = hudMirrorMode,
                onHudModeChanged = { hudMirrorMode = it }
            )

            3 -> RouteExploreScreen(Modifier.padding(padding), liveLocation, filteredSpeedKmh, activeCameraTarget, appSettings, sosAlerts,
                ownSosActive = ownSosActive, onSosPressed = onSosPressed, onSosCancelled = onSosCancelled)

            1 -> {

                CameraListScreen(
                    modifier =
                        Modifier.padding(
                            padding
                        ),

                    liveLocation =
                        liveLocation,

                    cameras =
                        realCameras,

                    loading =
                        cameraDataLoading,

                    error =
                        cameraDataError,

                    onAddManualCamera =
                        onAddManualCamera,

                    onDeleteManualCamera =
                        onDeleteManualCamera,

                    onEditManualCamera =
                        onEditManualCamera,

                    onFixOsmCamera =
                        onFixOsmCamera
                )
            }

            2 -> {

                SettingsScreen(
                    modifier =
                        Modifier.padding(
                            padding
                        ),

                    settings =
                        appSettings,

                    onTestRedLightWarning = onTestRedLightWarning,
                    onTestSpeedCameraWarning = onTestSpeedCameraWarning,

                    liveLocation =
                        liveLocation,

                    realCameras =
                        realCameras,

                    activeCameraTarget =
                        activeCameraTarget,

                    cameraDataLoading =
                        cameraDataLoading,

                    cameraDataError =
                        cameraDataError,

                    hiddenOsmCameraCount =
                        hiddenOsmCameraCount,

                    onExportManualCameras =
                        onExportManualCameras,

                    onImportManualCameras =
                        onImportManualCameras,

                    onRestoreOsmOverrides =
                        onRestoreOsmOverrides,

                    onStartDriveDiagnostics =
                        onStartDriveDiagnostics,

                    onExportDriveDiagnostics =
                        onExportDriveDiagnostics,

                    onDrivingModeChanged =
                        onDrivingModeChanged,

                    onVoiceAlertsChanged =
                        onVoiceAlertsChanged,

                    onBeepAlertsChanged =
                        onBeepAlertsChanged,

                    onVehicleModeChanged =
                        onVehicleModeChanged,

                    onThemeModeChanged = onThemeModeChanged,

                    mapCameraTypes = visibleCameraTypes,
                    onMapCameraTypesChanged = ::updateVisibleCameraTypes,

                    onWarningDistancesChanged =
                        onWarningDistancesChanged,

                    onCommunityModeChanged =
                        onCommunityModeChanged,

                    onCommunityDisplayNameChanged =
                        onCommunityDisplayNameChanged,

                    communityRiders =
                        communityRiders,

                    onVoicePresetChanged =
                        onVoicePresetChanged
                )
            }
        }
    }

}
}

@Composable
private fun SosFloatingButton(active: Boolean, enabled: Boolean, onClick: () -> Unit, buttonSize: Dp = 44.dp, modifier: Modifier = Modifier) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) { pulse = false; return@LaunchedEffect }
        while (true) { pulse = !pulse; delay(550) }
    }
    val scale by animateFloatAsState(if (active && pulse) 1.10f else 1f, tween(350), label = "sosPulse")
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(buttonSize).scale(scale).shadow(10.dp, CircleShape, clip = false),
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 1.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp),
        containerColor = if (active) DangerRed else DangerRed.copy(alpha = if (enabled) 0.96f else 0.38f),
        contentColor = Color.White
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Warning, if (active) "Cancel SOS" else "Send SOS", Modifier.size(if (buttonSize <= 38.dp) 17.dp else 19.dp))
            Text(if (active) "ON" else "SOS", fontSize = if (buttonSize <= 38.dp) 6.sp else 7.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PremiumBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showChatTab: Boolean = false,
    chatUnreadCount: Int = 0
) {
    val tabs = listOf("Map" to Icons.Default.Map,
        "Cameras" to Icons.Default.PhotoCamera, "Settings" to Icons.Default.Tune, "Route" to Icons.Default.Route,
        "HUD" to Icons.Default.Speed, "Community" to Icons.Default.Groups)
    val order = if (showChatTab) listOf(0, 3, 4, 1, 5, 2) else listOf(0, 3, 4, 1, 2)
    Surface(modifier = Modifier.navigationBarsPadding(), color = AppBackground, tonalElevation = 0.dp) {
        NavigationBar(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp).height(62.dp)
                .neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, elevation = 12.dp)
                .clip(RoundedCornerShape(24.dp)),
            containerColor = Color.Transparent, tonalElevation = 0.dp
        ) {
            order.forEach { index ->
                val tab = tabs[index]
                val isSelected = selectedTab == index
                NavigationBarItem(selected = isSelected, onClick = { onTabSelected(index) },
                    icon = {
                        // Neumorphic nav item: unselected tabs sit flush (no chip at all), the
                        // selected tab gets a small INSET (pressed-in) pill behind its icon
                        // instead of Material's flat filled indicator, so "active" reads as
                        // depth rather than just a color change.
                        val iconBox = Modifier
                            .size(34.dp)
                            .let { if (isSelected) it.neumorphicInset(CircleShape, CameraGuardPalette.Raised) else it }
                        Box(iconBox, contentAlignment = Alignment.Center) {
                            if (index == 5 && chatUnreadCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = CyanGlow, contentColor = Color.Black) {
                                        Text(if (chatUnreadCount > 99) "99+" else chatUnreadCount.toString())
                                    }
                                }) { Icon(tab.second, null, Modifier.size(18.dp)) }
                            } else {
                                Icon(tab.second, null, Modifier.size(18.dp))
                            }
                        }
                    },
                    label = { Text(tab.first, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanGlow, selectedTextColor = CyanGlow,
                        unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
                        indicatorColor = Color.Transparent))
            }
        }
    }
}

@Composable
private fun NavigationScreen(modifier: Modifier, liveLocation: Location?, filteredSpeedKmh: Float,
    realCameras: List<RealCamera>, communityRiders: List<CommunityRider>,
    sosAlerts: List<SosAlert> = emptyList(), sosFocusAlert: SosAlert? = null, sosFocusRequest: Int = 0,
    activeCameraTarget: RealCameraTarget?, appSettings: AppSettings,
    visibleCameraTypes: Set<RealCameraType>, fullMap: Boolean = false, onNavigationFinished: (() -> Unit)? = null,
    onMessageRider: (String) -> Unit = {},
    ownSosActive: Boolean = false,
    onSosPressed: () -> Unit = {},
    onSosCancelled: () -> Unit = {},
    initialRouteLayers: com.boss.cameraguard.map.RouteMapLayers = com.boss.cameraguard.map.RouteMapLayers()) {
    var followMode by rememberSaveable { mutableStateOf(true) }
    var recenterRequest by remember { mutableIntStateOf(0) }
    var activeRouteLayers by remember { mutableStateOf(initialRouteLayers) }
    var activeRouteLayersOpen by rememberSaveable { mutableStateOf(false) }
    var destinationQuery by rememberSaveable { mutableStateOf("") }
    var activeRouteAlertMenu by remember { mutableStateOf(false) }
    var activeRouteAlertMessage by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<RoutePlannerRepository.Place>>(emptyList()) }
    var routeRevision by remember { mutableIntStateOf(NavigationRouteRuntime.revision) }
    val route = remember(routeRevision) { NavigationRouteRuntime.route }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var voiceEnabled by rememberSaveable { mutableStateOf(true) }
    var voiceReady by remember { mutableStateOf(false) }
    val navigationSpeaker = remember(context) {
        NavigationVoiceSpeaker(context.applicationContext) { voiceReady = it }
    }
    DisposableEffect(navigationSpeaker) {
        onDispose { navigationSpeaker.shutdown() }
    }
    val spokenRouteKey = remember { mutableStateOf("") }
    val spokenMilestones = remember { mutableSetOf<String>() }
    val routePrefsStore = remember { RoutePreferencesStore(context) }
    var routePreferences by remember { mutableStateOf(routePrefsStore.load()) }
    var showMapSettings by rememberSaveable { mutableStateOf(false) }
    var showRouteEditor by rememberSaveable { mutableStateOf(false) }
    var onlineRidersExpanded by rememberSaveable { mutableStateOf(false) }
    var reportMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var comingSoonMessage by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var selectedRiderUid by rememberSaveable { mutableStateOf<String?>(null) }
    var riderFocusUid by remember { mutableStateOf<String?>(null) }
    var riderFocusRequest by remember { mutableIntStateOf(0) }
    var nearbyPlaces by remember { mutableStateOf<List<RoutePlannerRepository.Place>>(emptyList()) }
    var nearbyCategory by remember { mutableStateOf<String?>(null) }
    var nearbyLoading by remember { mutableStateOf(false) }
    fun loadNearby(category: String) {
        val origin = liveLocation ?: run { errorText = "Waiting for GPS"; return }
        nearbyCategory = category; nearbyLoading = true; errorText = null
        scope.launch {
            try { nearbyPlaces = withContext(Dispatchers.IO) { RoutePlannerRepository.nearbyPlaces(category, origin) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { nearbyPlaces = emptyList(); errorText = "Nearby places unavailable. Try again." }
            finally { nearbyLoading = false }
        }
    }
    var routingBusy by remember { mutableStateOf(false) }
    var arrivedDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var tripRating by rememberSaveable { mutableIntStateOf(0) }
    var arrivalFixes by remember(route) { mutableIntStateOf(0) }
    var lastArrivalFix by remember(route) { mutableStateOf(-1L) }
    val progressTracker = remember(route) { route?.let { NavigationProgressTracker(it) } }
    val progress = remember(route, liveLocation) { liveLocation?.let { progressTracker?.update(it) } }
    // Navigation guidance uses actual route maneuvers and validated, fresh GPS fixes.
    // Milestones are keyed to the route revision so reroutes announce their new instructions.
    LaunchedEffect(fullMap, routeRevision, route?.active, progress, voiceEnabled, voiceReady, liveLocation) {
        if (!fullMap || route?.active != true || !voiceEnabled || !voiceReady) return@LaunchedEffect
        val loc = liveLocation ?: return@LaunchedEffect
        val fresh = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) in 0L..15_000_000_000L
        if (!fresh || !loc.hasAccuracy() || loc.accuracy > 35f) return@LaunchedEffect
        val current = progress ?: return@LaunchedEffect
        if (current.offRouteMeters > 55.0) return@LaunchedEffect
        val routeKey = routeRevision.toString()
        if (spokenRouteKey.value != routeKey) {
            spokenMilestones.clear()
            spokenRouteKey.value = routeKey
        }
        val instruction = current.nextInstruction.trim()
        if (instruction.isBlank() || instruction == "Continue to destination") return@LaunchedEffect
        val nextMeters = current.nextTurnMeters
        val band = when {
            nextMeters <= 35.0 -> "now"
            nextMeters <= 120.0 -> "near"
            nextMeters <= 450.0 -> "approach"
            else -> null
        } ?: return@LaunchedEffect
        val key = "$instruction:$band"
        if (spokenMilestones.add(key)) {
            val intro = when (band) {
                "now" -> "Now, "
                "near" -> "In ${nextMeters.roundToInt()} meters, "
                else -> "In about ${((nextMeters / 50.0).roundToInt() * 50).coerceAtLeast(50)} meters, "
            }
            navigationSpeaker.speak(intro + instruction)
        }
    }
    LaunchedEffect(route?.active, voiceEnabled) {
        if (route?.active != true || !voiceEnabled) navigationSpeaker.stop()
    }


    fun startDestination(place: RoutePlannerRepository.Place? = null) {
        if (routingBusy) return
        val origin = liveLocation
        if (origin == null) { errorText = "Waiting for GPS. Please try again."; return }
        val query = destinationQuery.trim()
        if (place == null && query.isBlank()) return
        routingBusy = true; errorText = null
        keyboard?.hide(); focusManager.clearFocus()
        scope.launch {
            try {
                val selected = withContext(Dispatchers.IO) {
                    val destination = place ?: runCatching { RoutePlannerRepository.suggestPlaces(query).firstOrNull() }.getOrNull()
                        ?: RoutePlannerRepository.searchPlace(query).firstOrNull()
                        ?: error("No destination found. Enter a full address.")
                    RoutePlannerRepository.route(origin, destination, routePreferences)
                }
                destinationQuery = selected.destinationName
                results = emptyList(); showRouteEditor = false
                NavigationRouteRuntime.updateRoute(selected.copy(active = true))
                routeRevision = NavigationRouteRuntime.revision
                arrivedDestination = null; arrivalFixes = 0; tripRating = 0
                followMode = true; recenterRequest++
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorText = "Route unavailable: ${error.message ?: "network error"}"
                showRouteEditor = true
            } finally { routingBusy = false }
        }
    }

    LaunchedEffect(liveLocation, route) {
        if (!fullMap) return@LaunchedEffect
        val current = liveLocation ?: return@LaunchedEffect
        val active = route?.takeIf { it.active } ?: return@LaunchedEffect
        if (current.elapsedRealtimeNanos == lastArrivalFix) return@LaunchedEffect
        lastArrivalFix = current.elapsedRealtimeNanos
        val distance = FloatArray(1)
        Location.distanceBetween(current.latitude, current.longitude, active.destinationLat, active.destinationLon, distance)
        val fresh = (android.os.SystemClock.elapsedRealtimeNanos() - current.elapsedRealtimeNanos) in 0L..15_000_000_000L
        val near = fresh && current.hasAccuracy() && current.accuracy <= 30f && distance[0] <= 35f &&
            (progress?.remainingMeters ?: Double.MAX_VALUE) <= 60.0 &&
            current.hasSpeed() && current.speed <= 3f
        arrivalFixes = if (near) arrivalFixes + 1 else 0
        if (arrivalFixes >= 3) {
            arrivedDestination = active.destinationName; tripRating = 0
            NavigationRouteRuntime.clear(); routeRevision = NavigationRouteRuntime.revision
            destinationQuery = ""; arrivalFixes = 0
        }
    }

    fun applyRoutePreferences(updated: RoutePreferences) {
        routePreferences = updated
        routePrefsStore.save(updated)
        val loc = liveLocation
        val existing = NavigationRouteRuntime.route
        if (loc != null && existing != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    RoutePlannerRepository.route(loc, RoutePlannerRepository.Place(existing.destinationName, existing.destinationLat, existing.destinationLon), updated)
                } }.onSuccess { recalculated ->
                    if (NavigationRouteRuntime.route === existing) {
                        NavigationRouteRuntime.updateRoute(recalculated.copy(active = existing.active))
                        routeRevision = NavigationRouteRuntime.revision
                    }
                }
            }
        }
    }

    // Debounced live destination suggestions while typing.
    LaunchedEffect(destinationQuery) {
        val q = destinationQuery.trim()
        if (q.length < 2 || route?.destinationName == destinationQuery) {
            if (q.length < 2) results = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(450)
        searching = true
        try {
            results = withContext(Dispatchers.IO) { RoutePlannerRepository.suggestPlaces(q, liveLocation) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { results = emptyList() }
        finally { searching = false }
    }

    // GPS updates must not continually cancel the reroute debounce.
    val latestLocation by rememberUpdatedState(liveLocation)
    // Automatic off-route detection/rerouting now runs once, app-wide, in CameraGuardApp -
    // see the comment there for why (it used to live here, gated behind fullMap, and silently
    // stopped the moment the rider switched away from this screen). This screen only needs to
    // notice when that shared reroute has landed; see the routeRevision poller just below.
    // Catches a reroute (or any other route/clear) made from OUTSIDE this screen - e.g. the
    // app-wide automatic-reroute watcher in CameraGuardApp - promptly, instead of only on the
    // next self-triggered write to the local routeRevision. Matches the existing
    // SpeakRouteGuidance polling idiom elsewhere in this file (NavigationRouteRuntime.revision
    // is a plain volatile counter, not Compose state, so it has to be polled to react to writes
    // made by a different composable).
    LaunchedEffect(Unit) {
        while (true) {
            if (routeRevision != NavigationRouteRuntime.revision) routeRevision = NavigationRouteRuntime.revision
            kotlinx.coroutines.delay(400)
        }
    }

    val alertsByUid = (sosAlerts + listOfNotNull(sosFocusAlert)).associateBy { it.uid }
    val mapCommunityRiders = communityRiders.filterNot { alertsByUid.containsKey(it.uid) } +
        alertsByUid.values.filter { it.isFresh() }.map { alert ->
            CommunityRider(
                uid = "sos:${alert.uid}",
                displayName = alert.displayName,
                latitude = alert.latitude,
                longitude = alert.longitude,
                headingDegrees = 0.0,
                speedKmh = 0.0,
                lastSeenMillis = alert.createdAtMillis,
                cell = "SOS",
                distanceMeters = 0f,
                roadName = "SOS · accident alert",
                moving = false,
                sosActive = true
            )
        }

    BoxWithConstraints(modifier.fillMaxSize().background(AppBackground)) {
        val compactMap = maxWidth < 380.dp
        val shortMap = maxHeight < 650.dp
        val cockpitGauge = if (compactMap) 68.dp else 94.dp
        val cockpitCompass = if (compactMap) 64.dp else 88.dp
        val cockpitHeight = if (compactMap) 88.dp else 112.dp
        val warningBottom = if (shortMap) 104.dp else 160.dp
        val warningEnd = if (compactMap) 58.dp else 80.dp
        RealMapView(
            Modifier.fillMaxSize(), liveLocation = liveLocation, realCameras = realCameras.filter { it.type in visibleCameraTypes },
            communityRiders = mapCommunityRiders, followMode = followMode, recenterRequest = recenterRequest,
            nearbyPlaces = if (fullMap && nearbyCategory != null) nearbyPlaces.map { org.maplibre.android.geometry.LatLng(it.lat, it.lon) to it.name } else emptyList(),
            onManualMapMove = { followMode = false }, routePoints = if(fullMap) route?.points ?: emptyList() else emptyList(), roadsOnly = !fullMap,
            destinationPoint = if(fullMap) route?.points?.lastOrNull() ?: route?.let { org.maplibre.android.geometry.LatLng(it.destinationLat, it.destinationLon) } else null,
            onRiderTap = { if (!it.sosActive) selectedRiderUid = it.uid },
            focusRiderUid = if (sosFocusRequest > 0 && sosFocusAlert != null) "sos:${sosFocusAlert.uid}" else riderFocusUid,
            focusRiderRequest = if (sosFocusRequest > 0) sosFocusRequest else riderFocusRequest,
            routeMapLayers = activeRouteLayers,
            darkTheme = appSettings.themeMode == AppThemeMode.DARK
        )

        val currentWeather by rememberCurrentWeather(liveLocation)
        WeatherChip(currentWeather, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp))

        if (fullMap) {
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 237.dp),
                horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { activeRouteLayersOpen = !activeRouteLayersOpen },
                    modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp), containerColor = SurfaceDark, contentColor = CyanGlow, elevation = FloatingActionButtonDefaults.elevation(0.dp)) {
                    Icon(Icons.Default.Layers, "Map layers", Modifier.size(20.dp))
                }
                FreeRouteLayersMenu(
                    expanded = activeRouteLayersOpen,
                    onDismiss = { activeRouteLayersOpen = false },
                    layers = activeRouteLayers,
                    onLayersChanged = { activeRouteLayers = it }
                )
            }
        }

        if (fullMap && route?.active != true && !showRouteEditor) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 104.dp, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // Exactly five equal-width categories fill the available screen width without horizontal scrolling.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Petrol", "Food", "Shops", "Hotels", "Parks").forEach { category ->
                        OutlinedButton(onClick = { loadNearby(category) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceDark, contentColor = if (nearbyCategory == category) CyanGlow else TextPrimary),
                            border = BorderStroke(1.dp, if (nearbyCategory == category) CyanGlow else PremiumLine),
                            shape = RoundedCornerShape(12.dp)) { Text(category, fontSize = 10.sp, maxLines = 1) }
                    }
                }
                if (nearbyLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = CyanGlow)
                if (nearbyCategory != null && !nearbyLoading) {
                    Surface(Modifier.fillMaxWidth().padding(top = 5.dp).neumorphicRaised(RoundedCornerShape(14.dp), SurfaceDark, 8.dp),
                        color = Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Nearby ${nearbyCategory}", Modifier.weight(1f), color = CyanGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { nearbyCategory = null; nearbyPlaces = emptyList() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Close nearby places", tint = TextSecondary, modifier = Modifier.size(15.dp))
                                }
                            }
                            if (nearbyPlaces.isEmpty()) Text("No nearby results", color = TextSecondary, fontSize = 11.sp)
                            nearbyPlaces.take(5).forEach { place ->
                                Text(place.name, modifier = Modifier.fillMaxWidth().clickable {
                                    nearbyCategory = null; nearbyPlaces = emptyList(); startDestination(place)
                                }.padding(vertical = 6.dp), color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (fullMap) Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(AppBackground, AppBackground.copy(alpha=.94f), Color.Transparent)))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = CyanGlow, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("CameraGuard", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("ROUTE", color = CyanGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (route?.active != true) Button(
                onClick = { showRouteEditor = !showRouteEditor; showMapSettings = false; onlineRidersExpanded = false; reportMenuExpanded = false },
                modifier = Modifier.widthIn(min = 118.dp, max = 154.dp).height(42.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CameraGuardPalette.Accent, contentColor = AppBackground),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Route, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (showRouteEditor) "CLOSE" else "SET ROUTE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(showRouteEditor) {
                Column {
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceDark) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(CameraGuardPalette.RouteBlue, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(if (liveLocation != null) "Your current location" else "Waiting for GPS…", color=TextPrimary, fontSize=13.sp, modifier=Modifier.weight(1f))
                    Icon(Icons.Default.MyLocation, null, tint=TextSecondary, modifier=Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = destinationQuery, onValueChange = { destinationQuery = it; errorText=null },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !routingBusy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { startDestination() }, onDone = { startDestination() }),
                placeholder = { Text("Destination", color=TextSecondary) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint=DangerRed) },
                trailingIcon = {
                    IconButton(onClick = { startDestination() }, enabled = !routingBusy) {
                        if (searching || routingBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth=2.dp)
                        else Icon(Icons.Default.Navigation, "Start navigation", tint=CyanGlow)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor=CyanGlow, unfocusedBorderColor=PremiumLine, focusedTextColor=TextPrimary, unfocusedTextColor=TextPrimary),
                shape = RoundedCornerShape(14.dp)
            )
            if (results.isNotEmpty()) {
                Surface(color=SurfaceDark, shape=RoundedCornerShape(12.dp)) {
                    Column {
                        results.take(6).forEach { place ->
                            Row(Modifier.fillMaxWidth().clickable {
                                startDestination(place)
                            }.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, tint=DangerRed, modifier=Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                                Text(place.name, color=TextPrimary, fontSize=12.sp, maxLines=2, overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            errorText?.let { Text(it, color=DangerRed, fontSize=11.sp, modifier=Modifier.padding(top=5.dp)) }
        }

                }
            }
        if (fullMap && !showRouteEditor) route?.let { r ->
            val remaining = progress?.remainingMeters ?: r.distanceMeters
            val seconds = progress?.remainingSeconds ?: r.durationSeconds
            val eta = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(System.currentTimeMillis() + (seconds * 1000).toLong()))
            Surface(
                Modifier.align(Alignment.TopCenter).padding(start=6.dp, end=6.dp, top=52.dp).fillMaxWidth(),
                color=SurfaceDark, shape=RoundedCornerShape(15.dp), border=BorderStroke(1.dp, CyanGlow.copy(alpha=.45f))
            ) {
                Column(Modifier.padding(horizontal=12.dp, vertical=7.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, null, tint=CyanGlow, modifier=Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            val turnDistance = progress?.nextTurnMeters ?: 0.0
                            Text(if (progress == null) "Waiting for GPS" else if ((progress?.offRouteMeters ?: 0.0) > 55.0) "Recalculating route…" else
                                "In ${if (turnDistance < 1000) "${turnDistance.roundToInt()} m" else "%.1f km".format(turnDistance / 1000)}",
                                color=CyanGlow, fontSize=12.sp, fontWeight=FontWeight.Bold)
                            Text(progress?.nextInstruction ?: "Follow the route", color=TextPrimary, fontSize=17.sp, fontWeight=FontWeight.Bold, maxLines=2, overflow=TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { voiceEnabled = !voiceEnabled; if (!voiceEnabled) navigationSpeaker.stop() }) {
                            Icon(if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                if (voiceEnabled) "Mute route guidance" else "Unmute route guidance", tint = CyanGlow)
                        }
                        IconButton(onClick = {
                            showRouteEditor = true
                            destinationQuery = ""
                            results = emptyList()
                        }) { Icon(Icons.Default.EditLocationAlt, "Change destination", tint = CyanGlow) }
                        IconButton(onClick={ NavigationRouteRuntime.clear(); routeRevision=NavigationRouteRuntime.revision; destinationQuery=""; arrivalFixes=0; onNavigationFinished?.invoke() }) {
                            Icon(Icons.Default.Close, "End navigation", tint=TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                        Column { Text(eta, color=TextPrimary, fontSize=21.sp, fontWeight=FontWeight.Bold); Text("Arrival", color=TextSecondary, fontSize=10.sp) }
                        Column { Text("${kotlin.math.ceil(seconds/60).toInt().coerceAtLeast(1)} min", color=CyanGlow, fontSize=21.sp, fontWeight=FontWeight.Bold); Text("Remaining · est.", color=TextSecondary, fontSize=10.sp) }
                        Column { Text("%.1f km".format(remaining/1000), color=TextPrimary, fontSize=21.sp, fontWeight=FontWeight.Bold); Text("Distance left", color=TextSecondary, fontSize=10.sp) }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(r.destinationName, color=TextSecondary, fontSize=11.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                }
            }
        }

        if (fullMap) Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start=12.dp, bottom=12.dp),
            color = SurfaceDark, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyanGlow.copy(alpha=.35f))) {
            Text("Smoker’s Map", modifier = Modifier.padding(horizontal=10.dp, vertical=5.dp),
                color=CyanGlow, fontSize=11.sp, fontWeight=FontWeight.Bold)
        }

        selectedRiderUid?.let { uid ->
            val rider = communityRiders.firstOrNull { it.uid == uid }
            AlertDialog(
                onDismissRequest = { selectedRiderUid = null },
                icon = { Icon(Icons.Default.PersonPinCircle, null, tint=CyanGlow) },
                title = { Text(rider?.displayName?.ifBlank { "Rider" } ?: "Rider offline") },
                text = { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(rider?.roadName?.takeIf { it.isNotBlank() } ?: "Current road unavailable")
                    Text(rider?.let { "${it.speedKmh.coerceAtLeast(0.0).roundToInt()} km/h · GPS speed" } ?: "This rider is no longer online.",
                        color=CyanGlow, fontSize=20.sp, fontWeight=FontWeight.Bold)
                } },
                confirmButton = { TextButton(onClick={ selectedRiderUid=null }) { Text("Close") } },
                dismissButton = {
                    TextButton(onClick = { onMessageRider(uid); selectedRiderUid = null }) {
                        Icon(Icons.Default.Chat, null, Modifier.size(16.dp), tint = CyanGlow)
                        Spacer(Modifier.width(6.dp))
                        Text("Message", color = CyanGlow)
                    }
                },
                containerColor = SurfaceDark, titleContentColor=TextPrimary, textContentColor=TextSecondary
            )
        }

        arrivedDestination?.let { destination ->
            AlertDialog(
                onDismissRequest = { arrivedDestination = null; onNavigationFinished?.invoke() },
                icon = { Icon(Icons.Default.CheckCircle, null, tint=CyanGlow) },
                title = { Text("You've arrived!") },
                text = {
                    Column {
                        Text("You have reached your destination.\n$destination")
                        Spacer(Modifier.height(16.dp))
                        Text("How was your navigation experience?", fontWeight=FontWeight.Bold)
                        Text("1 = Poor · 10 = Excellent", fontSize=12.sp)
                        Spacer(Modifier.height(8.dp))
                        for (row in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            for (number in (row*5+1)..(row*5+5)) {
                                OutlinedButton(onClick={ tripRating=number }, modifier=Modifier.weight(1f), contentPadding=PaddingValues(0.dp),
                                    colors=ButtonDefaults.outlinedButtonColors(containerColor=if(tripRating==number) CyanGlow else Color.Transparent,
                                        contentColor=if(tripRating==number) Color.Black else TextPrimary)) { Text("$number") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(enabled=tripRating in 1..10, onClick={
                    context.getSharedPreferences("navigation_feedback", android.content.Context.MODE_PRIVATE).edit()
                        .putInt("last_rating", tripRating).putLong("rated_at", System.currentTimeMillis()).apply()
                    arrivedDestination=null
                    onNavigationFinished?.invoke()
                    android.widget.Toast.makeText(context, "Thank you for your feedback!", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Submit rating") } },
                dismissButton = { TextButton(onClick={arrivedDestination=null; onNavigationFinished?.invoke()}) { Text("Skip") } }
            )
        }

        // Shared live target: the warning engine remains the sole authority for camera
        // eligibility, direction and passage. Never retain a stale target in the UI.
        activeCameraTarget?.takeIf {
            it.distanceMeters >= 0f && it.distanceMeters <= appSettings.warningDistanceFor(it.camera.type)
        }?.let { target ->
            val accent = target.camera.type.premiumAccent()
            val heartbeat = rememberInfiniteTransition(label = "mapCameraHeartbeat")
            val glow by heartbeat.animateFloat(
                initialValue = 0.40f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Reverse),
                label = "cameraPulse"
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                color = SurfaceDark, shape = RoundedCornerShape(17.dp),
                border = BorderStroke(2.dp, accent.copy(alpha = glow)),
                shadowElevation = 16.dp
            ) {
                Row(Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(accent.copy(alpha = .17f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(target.camera.type.premiumIcon(), null, tint = accent, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CAMERA WARNING", color = accent, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(target.camera.type.displayName(), color = Color.White,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        target.camera.speedLimit?.takeIf { it > 0 }?.let { limit ->
                            Text("Limit $limit km/h", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Text("${target.distanceMeters.roundToInt().coerceAtLeast(0)} m",
                        color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Connected cockpit controls: rider menu and recenter share one compact bottom row.
        if (!fullMap) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 42.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { onlineRidersExpanded = !onlineRidersExpanded; showMapSettings = false; reportMenuExpanded = false },
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceDark, contentColor = CyanGlow),
                        border = BorderStroke(1.dp, PremiumLine)
                    ) {
                        Icon(Icons.Default.Groups, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                        Text("Online Riders (${communityRiders.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = onlineRidersExpanded,
                        onDismissRequest = { onlineRidersExpanded = false },
                        modifier = Modifier.widthIn(min = 290.dp, max = 350.dp)
                            .background(SurfaceDark)
                    ) {
                        if (communityRiders.isEmpty()) {
                            DropdownMenuItem(text = { Text("No riders online nearby") }, enabled = false, onClick = {})
                        } else {
                            communityRiders.sortedBy { it.distanceMeters }.take(12).forEachIndexed { index, rider ->
                                val road = rider.roadName?.trim()?.takeIf { it.isNotEmpty() } ?: "Road unavailable"
                                val speed = if (rider.speedKmh.isFinite()) rider.speedKmh.coerceAtLeast(0.0).roundToInt() else 0
                                val distance = if (!rider.distanceMeters.isFinite() || rider.distanceMeters < 0f) "—"
                                    else if (rider.distanceMeters < 1000f) "${rider.distanceMeters.roundToInt()} m"
                                    else "${"%.1f".format(java.util.Locale.US, rider.distanceMeters / 1000f)} km"
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("${index + 1}. ${rider.displayName.ifBlank { "Rider" }}",
                                                color = CyanGlow, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(road, color = TextPrimary, fontSize = 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("$speed km/h  •  $distance away", color = TextSecondary, fontSize = 11.sp)
                                        }
                                    },
                                    onClick = {
                                        riderFocusUid = rider.uid
                                        followMode = false
                                        riderFocusRequest++
                                        onlineRidersExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    SosFloatingButton(
                        active = ownSosActive,
                        enabled = appSettings.communityModeEnabled && liveLocation != null,
                        onClick = { if (ownSosActive) onSosCancelled() else onSosPressed() },
                        buttonSize = 38.dp
                    )
                    FloatingActionButton(
                        onClick = { followMode = true; recenterRequest++; showMapSettings=false; reportMenuExpanded=false; onlineRidersExpanded=false },
                        modifier = Modifier.size(38.dp).shadow(7.dp, RoundedCornerShape(14.dp), clip = false), shape = RoundedCornerShape(14.dp),
                        containerColor = SurfaceDark, contentColor = CyanGlow,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
                    ) { Icon(Icons.Default.MyLocation, "Centre map", Modifier.size(18.dp)) }
                }
            }
        } else {
            // Warning card takes precedence over right-side controls.
            if (activeCameraTarget?.let { it.distanceMeters >= 0f && it.distanceMeters <= appSettings.warningDistanceFor(it.camera.type) } != true) Column(
                Modifier.align(Alignment.BottomEnd).padding(end=14.dp, bottom=78.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalAlignment = Alignment.End
            ) {
                Box {
                    FloatingActionButton(
                        onClick = { activeRouteLayersOpen = !activeRouteLayersOpen; showMapSettings=false; activeRouteAlertMenu=false },
                        modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp),
                        containerColor = SurfaceDark, contentColor = CyanGlow, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Layers, "Map layers", Modifier.size(19.dp)) }
                    FreeRouteLayersMenu(expanded = activeRouteLayersOpen, onDismiss = { activeRouteLayersOpen = false },
                        layers = activeRouteLayers, onLayersChanged = { activeRouteLayers = it })
                }
                FloatingActionButton(
                    onClick = { followMode = true; recenterRequest++; showMapSettings=false; activeRouteAlertMenu=false },
                    modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp),
                    containerColor = SurfaceDark, contentColor = CyanGlow, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) { Icon(Icons.Default.MyLocation, "Centre map", Modifier.size(19.dp)) }
                SosFloatingButton(
                    active = ownSosActive,
                    enabled = appSettings.communityModeEnabled && liveLocation != null,
                    onClick = { if (ownSosActive) onSosCancelled() else onSosPressed() },
                    buttonSize = 44.dp
                )
                Box {
                    FloatingActionButton(
                        onClick = { showMapSettings = !showMapSettings; activeRouteAlertMenu=false },
                        modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp), containerColor = SurfaceDark, contentColor = CyanGlow, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Tune, "Route options", Modifier.size(19.dp)) }
                    DropdownMenu(expanded = showMapSettings, onDismissRequest = { showMapSettings = false }) {
                        Text("ROUTE OPTIONS", color=PremiumAccent, fontSize=10.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(horizontal=14.dp, vertical=8.dp))
                        MapRoutePreferenceMenuItem("Avoid autostrada", routePreferences.avoidAutostrada) { applyRoutePreferences(routePreferences.copy(avoidAutostrada=it)) }
                        MapRoutePreferenceMenuItem("Avoid tangenziale", routePreferences.avoidTangenziale) { applyRoutePreferences(routePreferences.copy(avoidTangenziale=it)) }
                        MapRoutePreferenceMenuItem("Avoid toll roads", routePreferences.avoidTollRoads) { applyRoutePreferences(routePreferences.copy(avoidTollRoads=it)) }
                    }
                }
                Box {
                    FloatingActionButton(
                        onClick = { activeRouteAlertMenu = !activeRouteAlertMenu; showMapSettings=false },
                        modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp), containerColor = DangerRed, contentColor = Color.White, elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) { Icon(Icons.Default.Report, "Road alert", Modifier.size(19.dp)) }
                    DropdownMenu(expanded=activeRouteAlertMenu,onDismissRequest={activeRouteAlertMenu=false}) {
                        listOf("Road closure","Congestion","Police","Road works","Lane closure","Object on road").forEach { label ->
                            DropdownMenuItem(text={Text(label)},onClick={activeRouteAlertMenu=false;activeRouteAlertMessage=label})
                        }
                    }
                }
            }
            activeRouteAlertMessage?.let { label -> AlertDialog(
                onDismissRequest={activeRouteAlertMessage=null}, title={Text(label)},
                text={Text("This function is under construction. Coming soon.")},
                confirmButton={TextButton(onClick={activeRouteAlertMessage=null}){Text("OK")}}
            ) }
        }

        if (!fullMap) {
            // v1.6.4: one connected cockpit HUD instead of three unrelated floating widgets.
            val speedValue = if (liveLocation != null && filteredSpeedKmh > 15f) filteredSpeedKmh.roundToInt() else 0
            val moving = liveLocation?.let { it.hasBearing() && it.hasSpeed() && it.speed >= .8f } == true
            val degrees = if (moving) liveLocation?.bearing?.let { ((it % 360f) + 360f) % 360f } else null
            val compass = degrees?.let { listOf("N","NE","E","SE","S","SW","W","NW")[((it+22.5f)/45f).toInt()%8] }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp),
                color = SurfaceDark.copy(alpha=.97f), shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.dp, CyanGlow.copy(alpha=.28f)), shadowElevation = 12.dp
            ) {
                Row(Modifier.fillMaxWidth().height(cockpitHeight).padding(horizontal=if (compactMap) 6.dp else 10.dp, vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(cockpitGauge), contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.Black.copy(alpha=.18f))
                            drawArc(PremiumLine.copy(alpha=.72f), 135f, 270f, false, style=Stroke(5.dp.toPx(), cap=StrokeCap.Round))
                            drawArc(CyanGlow.copy(alpha=.22f), 135f, 270f, false, style=Stroke(9.dp.toPx(), cap=StrokeCap.Round))
                            if (liveLocation != null) drawArc(CyanGlow, 135f, 270f*(speedValue/140f).coerceIn(.02f,1f), false,
                                style=Stroke(5.dp.toPx(), cap=StrokeCap.Round))
                        }
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(if(liveLocation != null) speedValue.toString() else "—", color=Color.White, fontSize=if (compactMap) 23.sp else 31.sp, fontWeight=FontWeight.Black)
                            Text("km/h", color=TextSecondary, fontSize=8.sp, fontWeight=FontWeight.SemiBold)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal=if (compactMap) 2.dp else 10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, null, tint=CyanGlow, modifier=Modifier.size(17.dp))
                            Spacer(Modifier.width(if (compactMap) 2.dp else 6.dp))
                            Text("Camera", color=Color.White, fontSize=if (compactMap) 11.sp else 15.sp, fontWeight=FontWeight.ExtraBold)
                            Text("Guard", color=CyanGlow, fontSize=if (compactMap) 11.sp else 15.sp, fontWeight=FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(if (compactMap) "RIDE SAFE" else "RIDE SAFE  •  RIDE SMART", color=TextSecondary, fontSize=7.sp, letterSpacing=1.1.sp)
                        Spacer(Modifier.height(10.dp))
                        Surface(shape=RoundedCornerShape(50), color=CyanGlow.copy(alpha=.10f), border=BorderStroke(1.dp,CyanGlow.copy(alpha=.22f))) {
                            Text(if(liveLocation != null) "GPS LOCKED" else "ACQUIRING GPS", color=if(liveLocation!=null) CyanGlow else WarningAmber,
                                fontSize=8.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(horizontal=10.dp, vertical=5.dp))
                        }
                    }
                    // Fixed-center-arrow / rotating-ring compass, like a car dashboard heading dial:
                    // the arrow never moves or rotates - it is the stable "forward" indicator - and
                    // the tick ring turns underneath it to reflect the rider's real heading. A
                    // continuously-unwrapped angle (rather than the raw 0-360 value) plus
                    // shortest-path deltas avoid the ring whipping backwards through 360 degrees
                    // when heading crosses north, and the last known angle is held - never reset to
                    // 0 - whenever heading data is briefly unavailable.
                    var ringContinuousDeg by remember { mutableFloatStateOf(0f) }
                    var lastRawHeadingDeg by remember { mutableStateOf<Float?>(null) }
                    LaunchedEffect(degrees) {
                        val raw = degrees ?: return@LaunchedEffect
                        val previous = lastRawHeadingDeg
                        ringContinuousDeg += if (previous == null) 0f else (((raw - previous + 540f) % 360f) - 180f)
                        if (previous == null) ringContinuousDeg = raw
                        lastRawHeadingDeg = raw
                    }
                    val animatedRingDeg by animateFloatAsState(
                        targetValue = ringContinuousDeg, animationSpec = tween(320, easing = LinearEasing), label = "compassRing"
                    )
                    Box(Modifier.size(cockpitCompass), contentAlignment=Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color.Black.copy(alpha=.18f))
                            drawCircle(PremiumLine.copy(alpha=.72f), style=Stroke(2.dp.toPx()))
                            drawCircle(CyanGlow.copy(alpha=.10f), radius=size.minDimension*.38f, style=Stroke(1.dp.toPx()))
                            // The ring (tick marks) rotates opposite the heading so the bright
                            // "north" tick sweeps to wherever true north currently sits relative to
                            // the fixed forward arrow - the arrow itself is drawn outside this
                            // rotated scope further below, so it never moves or spins with it.
                            rotate(-animatedRingDeg, pivot = center) {
                                for(i in 0 until 12) {
                                    val a=Math.toRadians((i*30-90).toDouble()); val c=center
                                    val outer=size.minDimension*.47f; val inner=outer-if(i%3==0) 7.dp.toPx() else 4.dp.toPx()
                                    drawLine(if(i%3==0) CyanGlow.copy(alpha=.65f) else PremiumLine,
                                        Offset(c.x+cos(a).toFloat()*inner,c.y+sin(a).toFloat()*inner),
                                        Offset(c.x+cos(a).toFloat()*outer,c.y+sin(a).toFloat()*outer),1.dp.toPx())
                                }
                            }
                        }
                        // Pixel-perfect centered, permanently fixed forward arrow - no .rotate(),
                        // no sibling composables sharing this alignment slot, so it can never drift
                        // off-center the way a taller stacked Column (icon+labels) previously did.
                        Icon(Icons.Default.Navigation, null, tint = if (moving) CyanGlow else TextSecondary,
                            modifier = Modifier.size(23.dp).align(Alignment.Center))
                        Text(
                            if (moving) "${compass ?: "—"} · ${degrees!!.roundToInt()%360}°" else "Stationary",
                            color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (compactMap) 3.dp else 6.dp)
                        )
                    }
                }
            }

        // Locked bottom-left wordmark. The MapLibre attribution button is positioned
        // immediately after this mark by RealMapView, so both read as one compact footer.
        SmokerMapWordmark(Modifier.align(Alignment.BottomStart).padding(start=10.dp, bottom=7.dp))

        comingSoonMessage?.let { feature ->
            AlertDialog(
                onDismissRequest = { comingSoonMessage = null },
                confirmButton = { TextButton(onClick={comingSoonMessage=null}) { Text("OK") } },
                icon = { Icon(Icons.Default.Construction, null, tint=WarningAmber) },
                title = { Text(feature) },
                text = { Text("This function is under construction. Coming soon.") }
            )
        }
    }
}

}

@Composable
private fun MapRoutePreferenceMenuItem(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Switch(checked=checked, onCheckedChange=onChanged, modifier=Modifier.scale(.75f)) },
        onClick = { onChanged(!checked) }
    )
}

@Composable
private fun MapRoutePreferenceRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=38.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(label, color=TextPrimary, fontSize=12.sp, modifier=Modifier.weight(1f))
        Switch(checked=checked, onCheckedChange=onChanged, modifier=Modifier.scale(.82f))
    }
}

@Composable
private fun OnlineRidersPlainOverlay(riders: List<CommunityRider>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        riders.sortedBy { it.distanceMeters }.take(12).forEach { rider ->
            Text(
                "${rider.displayName}  ${rider.speedKmh.roundToInt()} km/h",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = .46f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Compact glass-style "Online Riders" card for the Map tab. Replaces any
 * large permanent rider list with one small, scrollable, translucent card.
 * Every row is real data already fetched by MainActivity's Firebase
 * cell-subscription listeners (see CommunityRider) - no synthetic riders.
 */
@Composable
private fun CompactOnlineRidersCard(
    riders: List<CommunityRider>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(20.dp),
        color = CameraGuardPalette.Surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, CyanGlow.copy(alpha = .35f)),
        shadowElevation = 12.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, null, Modifier.size(14.dp), tint = CyanGlow)
                Spacer(Modifier.width(6.dp))
                Text(
                    "ONLINE RIDERS",
                    color = CyanGlow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("${riders.size}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                riders.sortedBy { it.distanceMeters }.take(12).forEach { rider ->
                    val moving = rider.speedKmh >= 3.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (moving) {
                                Icon(
                                    Icons.Default.Navigation,
                                    null,
                                    Modifier
                                        .size(16.dp)
                                        .rotate(rider.headingDegrees.toFloat()),
                                    tint = CyanGlow
                                )
                            } else {
                                Box(Modifier.size(9.dp).background(TextSecondary, CircleShape))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rider.displayName,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (moving) "${rider.speedKmh.roundToInt()} km/h" else "stopped",
                            color = if (moving) CyanGlow else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Purely decorative "you are here" pulse drawn around the rider's real
 * on-screen marker position (screen-centre, only shown while the map is
 * centred/following - see NavigationScreen). This is a self-contained local
 * animation clock: it does not read or write GPS, heading, warning state,
 * camera data, or any timing used by DrivingService / RealCameraWarningEngine.
 * Removing this composable changes nothing about driving/warning behaviour.
 */
@Composable
private fun LocationPulseOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "locationPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "locationPulseProgress"
    )
    Canvas(modifier.size(90.dp)) {
        val maxRadius = size.minDimension / 2f
        val radius = maxRadius * progress
        val alpha = (1f - progress).coerceIn(0f, 1f)
        drawCircle(
            color = CyanGlow.copy(alpha = alpha * 0.35f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = CyanGlow.copy(alpha = alpha * 0.8f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun LiveRidersScreen(
    modifier: Modifier,
    liveLocation: Location?,
    communityRiders: List<CommunityRider>,
    communityModeEnabled: Boolean,
    darkTheme: Boolean = true
) {
    // Dedicated Community Rider map (separate from the driving dashboard).
    // Only ever plots REAL Firebase presence data already fetched by
    // MainActivity's cell-subscription listeners - never demo/fake riders,
    // never hard-coded coordinates. If community sharing/consent is off,
    // this screen explains that rather than showing an empty map that looks
    // broken, and it never turns sharing on for the user.
    var followMode by rememberSaveable { mutableStateOf(true) }
    var recenterRequest by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize().background(AppBackground)) {

        Column(Modifier.padding(20.dp)) {
            PremiumSectionHeader(
                "LIVE COMMUNITY",
                "Rider Map",
                if (communityModeEnabled)
                    "${communityRiders.size} online within 50 km"
                else
                    "Community sharing is off"
            )
        }

        if (!communityModeEnabled) {

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.People,
                    null,
                    Modifier.size(46.dp),
                    tint = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Turn on Community Mode in Settings to see nearby online riders and to be seen by them.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }

        } else {

            Box(Modifier.weight(1f).fillMaxWidth()) {

                RealMapView(
                    Modifier.fillMaxSize(),
                    liveLocation = liveLocation,
                    realCameras = emptyList(),
                    communityRiders = communityRiders,
                    followMode = followMode,
                    recenterRequest = recenterRequest,
                    onManualMapMove = { followMode = false },
                    darkTheme = darkTheme
                )

                FloatingActionButton(
                    onClick = { followMode = true; recenterRequest++ },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp).shadow(9.dp, RoundedCornerShape(17.dp), clip=false),
                    shape = RoundedCornerShape(17.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    containerColor = SurfaceDark,
                    contentColor = if (followMode) PremiumAccent else CyanGlow
                ) {
                    Icon(Icons.Default.MyLocation, "Centre map")
                }

                if (communityRiders.isEmpty()) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                            .neumorphicRaised(RoundedCornerShape(50), SurfaceDark.copy(alpha = .92f), 8.dp),
                        color = Color.Transparent,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "No other riders online nearby right now",
                            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PremiumDrivingDashboard(location: Location?, filteredSpeedKmh: Float) {
    val speed = if (location != null && filteredSpeedKmh > 15f) filteredSpeedKmh.roundToInt() else 0
    Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(26.dp), SurfaceDark, 11.dp),
        shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(PremiumLine, 140f, 260f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(PremiumAccent, 140f, 260f * (speed / 160f).coerceIn(.025f, 1f), false,
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
                Icon(Icons.Default.Navigation, null, Modifier.size(26.dp), tint = PremiumAccent)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("CAMERAGUARD", color = PremiumAccent, fontSize = 9.sp, letterSpacing = 1.4.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (location == null) "—" else "$speed", color = TextPrimary, fontSize = 39.sp, fontWeight = FontWeight.Bold)
                    Text(" km/h", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (location != null) "GPS FIX" else "GPS WAIT", color = if (location != null) CyanGlow else WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(if (location?.hasBearing() == true) "${location.bearing.roundToInt()}°" else "—", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("HEADING", color = TextSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun CinematicRoadScanner() {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val pulse by transition.animateFloat(.15f, .9f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "echo")
    Box(Modifier.size(90.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val r = size.minDimension / 2
            for (ring in 1..3) drawCircle(CyanGlow.copy(alpha = .12f + ring * .035f), r * ring / 3, style = Stroke(1.dp.toPx()))
            for (tick in 0..23) {
                val a = Math.toRadians(tick * 15.0)
                val v = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(if (tick % 6 == 0) PremiumAccent else CyanGlow.copy(alpha = .35f), center + v * (r * .92f), center + v * r, 1.dp.toPx())
            }
            drawLine(CyanGlow.copy(alpha = .10f), Offset(center.x-r, center.y), Offset(center.x+r, center.y))
            drawLine(CyanGlow.copy(alpha = .10f), Offset(center.x, center.y-r), Offset(center.x, center.y+r))
            // Layered sweep trail; these are decorative arcs, not invented camera blips.
            for (i in 0..17) {
                drawArc(CyanGlow.copy(alpha = .012f + i * .009f), sweep - 72 + i * 4, 4f, true,
                    Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width-4.dp.toPx(), size.height-4.dp.toPx()))
            }
            val a = Math.toRadians(sweep.toDouble())
            drawLine(CyanGlow, center, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r * .94f), 1.5.dp.toPx(), StrokeCap.Round)
            drawCircle(CyanGlow.copy(alpha = (1-pulse)*.25f), r*pulse, style = Stroke(1.dp.toPx()))
            drawCircle(PremiumAccent, 3.dp.toPx())
        }
    }
}

@Composable
private fun PremiumCameraWarningCard(target: RealCameraTarget?, appSettings: AppSettings) {
    if (target == null) {
        Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(26.dp), SurfaceDark, 10.dp),
            shape = RoundedCornerShape(26.dp), color = Color.Transparent) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CinematicRoadScanner()
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("ROAD RADAR", color = PremiumAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Watching ahead", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("No relevant camera target right now", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        return
    }
    val type = target.camera.type
    val accent = type.premiumAccent()
    val zone = appSettings.warningDistanceFor(type).toFloat().coerceAtLeast(1f)
    val inZone = target.distanceMeters <= zone
    // Both endpoints are fully opaque: the map must never show through a warning.
    val background by animateColorAsState(if (inZone) lerp(SurfaceDark, accent, .19f) else SurfaceDark, label = "warningBackground")
    val progress by animateFloatAsState((1f - target.distanceMeters / (zone * 2f)).coerceIn(0f, 1f), label = "approach")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = background,
        border = BorderStroke(if (inZone) 2.dp else 1.dp, if (inZone) accent else PremiumLine), shadowElevation = 14.dp) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(lerp(background, accent, .20f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(type.premiumIcon(), null, tint = accent)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (inZone) "WARNING ZONE" else "CAMERA AHEAD", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(type.displayName(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDistance(target.distanceMeters), color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(if (inZone) "Stay aware • follow road signs" else "On your current approach", color = if (inZone) accent else TextSecondary, fontSize = 11.sp)
                }
                if (type.usesSpeedWarningDistance() && target.camera.speedLimit != null) PremiumSpeedBadge(target.camera.speedLimit)
            }
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                drawLine(PremiumLine, Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Round)
                drawLine(accent, Offset(0f, center.y), Offset(size.width * progress, center.y), size.height, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PremiumSpeedBadge(
    limit: Int
) {

    Box(
        modifier =
            Modifier
                .size(
                    58.dp
                )
                .clip(
                    CircleShape
                )
                .background(
                    Color(
                        0xFFF4F5F7
                    )
                )
                .border(
                    width =
                        4.dp,

                    color =
                        DangerRed,

                    shape =
                        CircleShape
                ),

        contentAlignment =
            Alignment.Center
    ) {

        Text(
            limit.toString(),

            color =
                Color(
                    0xFF101820
                ),

            fontSize =
                19.sp,

            fontWeight =
                FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PremiumGpsStatus(location: Location?, filteredSpeedKmh: Float) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SurfaceDark) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MyLocation, null, Modifier.size(15.dp), tint = if (location != null) CyanGlow else WarningAmber)
            Spacer(Modifier.width(8.dp))
            Text(if (location != null) "GPS accuracy ±${location.accuracy.roundToInt()} m" else "Waiting for location", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("LIVE POSITION", color = PremiumAccent, fontSize = 9.sp, letterSpacing = .6.sp)
        }
    }
}

/**
 * One diagnostic event captured either from the native WebView client (load/HTTP errors)
 * or reported by the HUD page itself via the CameraGuardDiag JS bridge (JS exceptions,
 * a caught Three.js/WebGL init failure, or a "ready" signal once the page has finished
 * initializing). Shown in-app via [HudDiagnosticsOverlay] so a signed APK tester can see
 * what went wrong without a live Logcat session attached.
 */
private data class HudDiagEvent(val stage: String, val message: String)

/**
 * JS-to-Android bridge used only for HUD diagnostics. It never drives app behavior or the
 * camera-warning engine — it only reports what happened inside the WebView page so it can be
 * surfaced in [HudDiagnosticsOverlay]. Methods run on a WebView-owned background thread, so
 * callers must hop back to the main thread before touching Compose state.
 */
private class HudDiagnosticsBridge(
    private val onReport: (String, String) -> Unit,
    private val onExportGpsDebug: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(stage: String, message: String) {
        mainHandler.post { onReport(stage, message) }
    }

    // Called only after the rider taps an explicit export control on the HUD debug panel.
    // No GPS samples are automatically uploaded, logged, or copied.
    @JavascriptInterface
    fun copyGpsDebug(report: String) {
        if (report.length > 500_000) {
            mainHandler.post { onReport("gps-export", "Report too large; stop and retry the test") }
            return
        }
        mainHandler.post { onExportGpsDebug(report) }
    }
}

@Composable
private fun LiveHudScreen(
    modifier: Modifier,
    liveLocation: Location?,
    filteredSpeedKmh: Float,
    realCameras: List<RealCamera>,
    activeCameraTarget: RealCameraTarget?,
    currentRoad: CurrentRoad?,
    nearbyRoadNetwork: List<List<com.boss.cameraguard.data.RoadPoint>> = emptyList(),
    appSettings: AppSettings,
    sosAlerts: List<SosAlert> = emptyList(),
    ownSosActive: Boolean = false,
    onSosPressed: () -> Unit = {},
    onSosCancelled: () -> Unit = {},
    hudModeActive: Boolean,
    onHudModeChanged: (Boolean) -> Unit
) {
    // Raw GPS bearing is noisy at low/no speed and drives the whole 3D road/mini-map
    // rotation - unfiltered, that noise made the road appear to spin, flip, or momentarily
    // point away from the camera (reported as the road "disappearing"). headingFilter only
    // feeds this HUD's visuals; camera-warning/direction/navigation logic elsewhere is
    // untouched and keeps using the raw Location bearing directly.
    val headingFilter = remember { HeadingFilter() }
    val heading = headingFilter.update(
        rawBearingDegrees = if (liveLocation?.hasBearing() == true) liveLocation.bearing else 0f,
        hasBearing = liveLocation?.hasBearing() == true,
        speedKmh = filteredSpeedKmh
    )
    val warningInZone = activeCameraTarget?.let {
        it.distanceMeters <= appSettings.warningDistanceFor(it.camera.type).toFloat()
    } == true
    val selectedRoute = NavigationRouteRuntime.route
    // HUD previously had no spoken turn-by-turn guidance at all (only the Map tab did).
    // Reads the same shared route the Map tab writes to; see SpeakRouteGuidance above.
    val hudVoice = rememberVoiceGuidance()
    SpeakRouteGuidance(hudVoice, active = true, liveLocation = liveLocation)

    val context = LocalContext.current

    // Diagnostics: populated from WebView load errors (native side) and from the HUD page's
    // own JS via the CameraGuardDiag bridge. hudReady flips true once the page confirms its
    // core 2D readout is live; the overlay hides the instant that happens.
    val diagEvents = remember { mutableStateListOf<HudDiagEvent>() }
    var hudReady by remember { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var pageSnapshot by remember { mutableStateOf("Not checked") }

    fun recordDiag(stage: String, message: String) {
        diagEvents.add(HudDiagEvent(stage, message))
        if (diagEvents.size > 20) diagEvents.removeAt(0)
        if (stage == "core-ready" || stage == "ready" || stage == "dom-ready") hudReady = true
    }

    val latestHudPayloadState = remember { mutableStateOf("{}") }
    LaunchedEffect(Unit) { recordDiag("hud-tab", "HUD tab opened; diagnostics available via button") }
    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(9, 18, 28))
            // Keep WebGL composited in the WebView's own hardware layer. The page's
            // DOM can be ready while the WebView layer is not yet painted.
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            // Serve both the HTML and the ES module from the same secure origin.
            // file:// ES module imports are blocked by Android WebView's CORS policy.
            // Keep HUD document visible if WebGL fails; capture real WebView errors both in
            // Logcat and in the in-app diagnostics overlay (see CameraGuardDiag below).
            addJavascriptInterface(
                HudDiagnosticsBridge(
                    onReport = { stage, message -> recordDiag(stage, message) },
                    onExportGpsDebug = { report ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CameraGuard GPS debug", report))
                        recordDiag("gps-export", "Report copied by user action (${report.length} characters)")
                    }
                ),
                "CameraGuardDiag"
            )
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.e("CameraGuardHUD", "JS ${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        recordDiag("console-error", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    recordDiag("page-finished", url ?: "unknown")
                    super.onPageFinished(view, url)
                    if (url == "https://appassets.androidplatform.net/hud/index.html") {
                        view?.evaluateJavascript("window.CameraGuardUpdate && window.CameraGuardUpdate(" + latestHudPayloadState.value + ");", null)
                    }
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    val description = "${error?.description} (${request?.url})"
                    Log.e("CameraGuardHUD", "Load error ${request?.url}: ${error?.description}")
                    if (request?.isForMainFrame == true) {
                        recordDiag("webview-load-error", description)
                        view?.post { view.loadDataWithBaseURL(null,
                            "<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='background:#101d2a;color:#d5edff;font:16px sans-serif;padding:28px'>HUD could not load. See diagnostics panel for details.</body></html>",
                            "text/html", "UTF-8", null) }
                    } else {
                        recordDiag("webview-resource-error", description)
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    Log.e("CameraGuardHUD", "HTTP ${errorResponse?.statusCode} ${request?.url}")
                    recordDiag("webview-http-error", "HTTP ${errorResponse?.statusCode} ${request?.url}")
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    return !(uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.path?.startsWith("/hud/") == true)
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    val path = uri.path ?: ""
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                        (path == "/hud/index.html" || path == "/hud/three.module.js")) {
                        val file = if (path.endsWith(".js")) "hud/three.module.js" else "hud/index.html"
                        val mime = if (path.endsWith(".js")) "text/javascript" else "text/html"
                        return try {
                            WebResourceResponse(mime, "UTF-8", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "https://appassets.androidplatform.net",
                                      "Cache-Control" to "no-cache"), context.assets.open(file))
                        } catch (error: java.io.IOException) {
                            Log.e("CameraGuardHUD", "Missing bundled asset $file", error)
                            recordDiag("missing-asset", "Missing bundled HUD asset: $file")
                            WebResourceResponse("text/plain", "UTF-8", 404, "Missing asset", emptyMap(),
                                java.io.ByteArrayInputStream("Missing HUD asset: $file".toByteArray()))
                        }
                    }
                    // Browser-generated resources (favicon, etc.) must not break the document.
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net") {
                        return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(),
                            java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(),
                        java.io.ByteArrayInputStream(ByteArray(0)))
                }
            }
            // Loading begins only after AndroidView attaches the WebView to its window.
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.loadUrl("about:blank"); webView.destroy() }
    }

    // Safety net: if the page never reports core-ready or an error within a few seconds
    // (e.g. it hung before either path ran), surface that instead of staying silently blank.
    LaunchedEffect(webView) {
        delay(6000)
        if (!hudReady) {
            recordDiag("timeout", "HUD page did not confirm it was ready within 6s.")
        }
    }

    val routeRevisionForHud = NavigationRouteRuntime.revision
    val payload = JSONObject().apply {
        put("gps", liveLocation != null)
        put("themeMode", appSettings.themeMode.name)
        put("speed", if (liveLocation != null && liveLocation.time > 0L &&
            System.currentTimeMillis() - liveLocation.time in 0L..8000L && filteredSpeedKmh >= 12f)
            filteredSpeedKmh.toDouble() else 0.0)
        put("heading", heading.toDouble())
        put("hasBearing", liveLocation?.hasBearing() == true)
        put("road", currentRoad?.name ?: "")
        // Free, no-key speed limit from OSM's maxspeed tag (CurrentRoadRepository.parseMaxspeed).
        // Left absent when OSM has no numeric maxspeed for this way rather than guessing.
        put("speedLimitKmh", currentRoad?.speedLimitKmh ?: JSONObject.NULL)
        put("latitude", liveLocation?.latitude ?: JSONObject.NULL)
        put("longitude", liveLocation?.longitude ?: JSONObject.NULL)
        // HUD-only diagnostic metadata. The warning engine, location filtering and
        // map-matching inputs/outputs are unchanged.
        put("gpsAccuracyM", liveLocation?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble() ?: JSONObject.NULL)
        put("gpsBearingRawDeg", liveLocation?.takeIf { it.hasBearing() }?.bearing?.toDouble() ?: JSONObject.NULL)
        put("gpsSpeedRawMps", liveLocation?.takeIf { it.hasSpeed() }?.speed?.toDouble() ?: JSONObject.NULL)
        put("gpsFixTimeMs", liveLocation?.time ?: JSONObject.NULL)
        put("roadWayId", currentRoad?.osmWayId ?: JSONObject.NULL)
        put("roadPoints", org.json.JSONArray().apply {
            currentRoad?.points?.forEach { point -> put(org.json.JSONArray().put(point.latitude).put(point.longitude)) }
        })
        // Real nearby OSM roads for the mini-map background (replaces its old decorative
        // grid) - see CurrentRoadRepository.fetchNearbyRoadNetwork. Purely cosmetic context,
        // independent of roadPoints/routePoints above which drive the actual 3D road ahead.
        put("roadNetwork", org.json.JSONArray().apply {
            nearbyRoadNetwork.forEach { way ->
                put(org.json.JSONArray().apply {
                    way.forEach { point -> put(org.json.JSONArray().put(point.latitude).put(point.longitude)) }
                })
            }
        })
        put("routePoints", org.json.JSONArray().apply {
            selectedRoute?.takeIf { it.active }?.points?.forEach { point -> put(org.json.JSONArray().put(point.latitude).put(point.longitude)) }
        })
        put("sosAlerts", org.json.JSONArray().apply {
            sosAlerts.filter { it.isFresh() }.forEach { alert ->
                put(JSONObject().apply {
                    put("uid", alert.uid)
                    put("name", alert.displayName)
                    put("latitude", alert.latitude)
                    put("longitude", alert.longitude)
                })
            }
        })
        // Visual roadside camera is derived from the same engine-approved target.
        // This payload never creates/changes warning eligibility or camera audio.
        put("cameraVisual", activeCameraTarget?.takeIf { liveLocation != null &&
            it.distanceMeters >= 0f && it.distanceMeters <= 320f }?.let { target ->
            JSONObject().apply {
                put("latitude", target.camera.latitude)
                put("longitude", target.camera.longitude)
                put("type", target.camera.type.displayName().uppercase())
            }
        } ?: JSONObject.NULL)
        // Native Compose draws the real warning above the WebView canvas.
        put("nativeWarningOverlay", true)
        put("warning", if (warningInZone && activeCameraTarget != null) JSONObject().apply {
            put("type", activeCameraTarget.camera.type.displayName().uppercase())
            put("distance", activeCameraTarget.distanceMeters.toDouble())
        } else JSONObject.NULL)
    }.toString()
    latestHudPayloadState.value = payload
    LaunchedEffect(webView, payload) {
        webView.evaluateJavascript("window.CameraGuardUpdate && window.CameraGuardUpdate($payload);", null)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(AppBackground)) {
        AndroidView(
            factory = { _ -> webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Explicitly keep the embedded native view visible and request a
                // redraw after Compose measures/reattaches the HUD tab.
                view.visibility = android.view.View.VISIBLE
                view.alpha = 1f
                view.post { view.invalidate() }
                if (view.url == null) {
                    view.post {
                        if (view.url == null) {
                            view.loadUrl("https://appassets.androidplatform.net/hud/index.html")
                        }
                    }
                }
            }
        )

        // Native MapLibre mini-map: uses the same proven vector-map pipeline as Main Map,
        // so it remains visible even if Overpass/current-road matching is temporarily unavailable.
        var hudMiniRouteEnabled by rememberSaveable { mutableStateOf(true) }
        val hudMiniRoutePoints = remember(routeRevisionForHud, hudMiniRouteEnabled) {
            if (!hudMiniRouteEnabled) emptyList() else NavigationRouteRuntime.route?.takeIf { it.active }?.points
                ?.map { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) } ?: emptyList()
        }
        Box(
            Modifier.align(Alignment.TopStart)
                .offset(x = maxWidth * 0.03f, y = maxHeight * 0.105f)
                .width(maxWidth * 0.59f)
                .height(maxHeight * 0.23f)
                .clip(RoundedCornerShape(18.dp))
        ) {
            RealMapView(
                modifier = Modifier.fillMaxSize(),
                liveLocation = liveLocation,
                realCameras = emptyList(),
                communityRiders = emptyList(),
                followMode = true,
                recenterRequest = 0,
                onManualMapMove = {},
                routePoints = hudMiniRoutePoints,
                compactUserMarker = true,
                roadsOnly = true,
                miniMapMode = true,
                darkTheme = appSettings.themeMode == AppThemeMode.DARK
            )
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to CyanGlow.copy(alpha = 0.16f),
                            0.34f to CyanGlow.copy(alpha = 0.08f),
                            0.52f to Color.Transparent,
                            0.78f to AppBackground.copy(alpha = 0.46f),
                            1.0f to AppBackground.copy(alpha = 0.96f)
                        ),
                        center = center,
                        radius = size.minDimension * 0.72f
                    )
                )
            }
            TextButton(
                onClick = { hudMiniRouteEnabled = !hudMiniRouteEnabled },
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = SurfaceDark.copy(alpha = .92f), contentColor = CyanGlow),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)
            ) { Text(if (hudMiniRouteEnabled) "ROUTE ON" else "ROUTE OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }

        // The HUD control replaces the temporary diagnostics button. It toggles
        // the existing native HUD mode; diagnostics remain available in source logs.
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoiceMuteButton(
                hudVoice,
                modifier = Modifier.size(34.dp),
                containerColor = CameraGuardPalette.Surface.copy(alpha = 0.92f),
                iconSize = 16.dp
            )
            TextButton(
                onClick = { onHudModeChanged(!hudModeActive) },
                colors = ButtonDefaults.textButtonColors(containerColor = CameraGuardPalette.Surface.copy(alpha = 0.92f), contentColor = CameraGuardPalette.Accent)
            ) { Text(if (hudModeActive) "EXIT HUD" else "HUD", fontSize = 12.sp) }
        }

        SosFloatingButton(
            active = ownSosActive,
            enabled = appSettings.communityModeEnabled && liveLocation != null,
            onClick = { if (ownSosActive) onSosCancelled() else onSosPressed() },
            buttonSize = 44.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp)
        )

        // The service is the sole authority for camera eligibility/direction.
        // Render an opaque native warning OVER the WebView so an HTML/WebGL
        // failure or canvas compositing issue cannot hide a real active target.
        // This works identically with an active route and without one.
        activeCameraTarget?.takeIf { target ->
            target.distanceMeters >= 0f &&
                target.distanceMeters <= appSettings.warningDistanceFor(target.camera.type).toFloat()
        }?.let { target ->
            val accent = target.camera.type.premiumAccent()
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                color = SurfaceDark,
                shape = RoundedCornerShape(17.dp),
                border = BorderStroke(2.dp, accent),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = target.camera.type.premiumIcon(),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(33.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CAMERA WARNING", color = accent, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold)
                        Text(target.camera.type.displayName(), color = Color.White,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${target.distanceMeters.roundToInt()} m", color = Color.White,
                        fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

    }
}

/**
 * Temporary, non-production diagnostic panel. Only ever shown when the HUD page has not
 * confirmed it is ready (see [HudDiagEvent]/hudReady in [LiveHudScreen]) — it disappears the
 * instant the page reports core-ready, so it never lingers over a working HUD. Lets a phone
 * tester copy the details to the clipboard without an attached Logcat session.
 */
@Composable
private fun HudDiagnosticsOverlay(events: List<HudDiagEvent>, snapshot: String, pageUrl: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(14.dp), CameraGuardPalette.Surface.copy(alpha = 0.94f), 8.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text("HUD diagnostics", color = CameraGuardPalette.Highlight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Signed APK diagnostic report. Copy and send this report to identify the blank HUD issue.",
                color = TextPrimary, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Page: $pageUrl", color = TextPrimary, fontSize = 10.sp)
            Text("Page state: $snapshot", color = TextPrimary, fontSize = 10.sp)
            events.takeLast(20).forEach { event ->
                Text("[${event.stage}] ${event.message}", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                clipboard.setText(AnnotatedString("CameraGuard HUD diagnostics\nPage: $pageUrl\nPage state: $snapshot\n" + events.joinToString("\n") { "[${it.stage}] ${it.message}" }))
            }) {
                Text("Copy diagnostics", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HudHeaderBar(
    location: Location?,
    heading: Float,
    hudModeActive: Boolean,
    onHudModeChanged: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!hudModeActive) {
            Icon(Icons.Default.Shield, null, Modifier.size(26.dp), tint = CyanGlow)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) { append("Camera") }
                        withStyle(SpanStyle(color = CyanGlow, fontWeight = FontWeight.Bold)) { append("Guard") }
                    },
                    fontSize = 17.sp
                )
                Text("DRIVE SAFE · STAY ALERT", color = TextSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (location != null) Color(0xFF34D399) else WarningAmber, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(if (location != null) "GPS Live" else "GPS…", color = TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = { onHudModeChanged(!hudModeActive) },
                modifier = Modifier.neumorphicRaised(RoundedCornerShape(14.dp), if (hudModeActive) DangerRed.copy(alpha = .18f) else SurfaceDark, 7.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (hudModeActive) DangerRed else CyanGlow)
            ) {
                Text(if (hudModeActive) "EXIT HUD" else "HUD", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (hudModeActive) DangerRed else CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (location?.hasBearing() == true) "${heading.roundToInt()}°" else "—", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("HEADING", color = TextSecondary, fontSize = 7.sp, letterSpacing = .8.sp)
        }
    }
}

/**
 * Central navigation arrow + route visualisation.
 *
 * There is no destination-based turn-by-turn routing engine anywhere in
 * CameraGuard (confirmed by inspecting CameraRepository/DrivingService) - so
 * this deliberately does NOT pretend to draw a real point-to-point route.
 * Instead, when a real camera target with real OSM approach-road geometry
 * (RealCamera.approachPath) is available, that REAL road geometry is
 * projected into rider-relative screen space (rotated so travel direction
 * points up) and drawn as the route line - an honest use of real map data
 * for the stretch of road actually ahead of the rider. With no target/path,
 * a plain straight line along the current heading is drawn as a clearly
 * generic fallback, never a fabricated "route."
 */
@Composable
private fun HudArrowRoute(
    heading: Float,
    target: RealCameraTarget?,
    currentRoad: CurrentRoad?,
    liveLocation: Location?,
    vehicleMode: VehicleMode,
    navigationRoute: List<org.maplibre.android.geometry.LatLng>,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val originX = size.width / 2f
            // Rider anchor is the arrow's real current-position anchor -
            // placed centrally (not pinned to the bottom edge) so the arrow
            // reads as "you are here" with the route/road extending both
            // ahead (up) and slightly behind (down), matching the approved
            // reference design's central-arrow layout.
            val riderY = size.height * 0.62f
            val topY = size.height * 0.06f
            val bottomY = size.height * 0.96f
            val visibleRangeMeters = 320f // ~ current road + 300m ahead
            val behindToleranceMeters = 40f // small real "just behind" context, never fabricated
            val headingRad = Math.toRadians(heading.toDouble())
            val metersPerScreenUnitAhead = (riderY - topY) / visibleRangeMeters
            val metersPerScreenUnitBehind = (bottomY - riderY) / behindToleranceMeters

            // Project a real-world (forward, right) offset - metres relative
            // to the rider, in the rider's own heading-up frame - to screen
            // space. forward=0 is the rider's real anchor position;
            // positive forward goes up (ahead), a small negative range goes
            // down (just behind), matching real geometry rather than
            // bunching every behind-point onto one pixel.
            fun projectForwardRight(forwardMeters: Float, rightMeters: Float): Offset {
                val y = if (forwardMeters >= 0f) {
                    riderY - forwardMeters.coerceAtMost(visibleRangeMeters) * metersPerScreenUnitAhead
                } else {
                    riderY + (-forwardMeters).coerceAtMost(behindToleranceMeters) * metersPerScreenUnitBehind
                }
                val x = originX + rightMeters * 2.2f
                return Offset(x, y)
            }

            // Converts a real lat/lng into the rider's (forward, right)
            // frame using a standard local equirectangular approximation
            // (accurate over the few-hundred-metre distances relevant here),
            // then rotates by the rider's real heading so "forward" is up.
            fun realPointToForwardRight(lat: Double, lon: Double, riderLat: Double, riderLon: Double): Offset {
                val metersPerDegLat = 110_540.0
                val metersPerDegLon = 111_320.0 * cos(Math.toRadians(riderLat))
                val east = (lon - riderLon) * metersPerDegLon
                val north = (lat - riderLat) * metersPerDegLat
                val forward = east * sin(headingRad) + north * cos(headingRad)
                val right = east * cos(headingRad) - north * sin(headingRad)
                return Offset(forward.toFloat(), right.toFloat())
            }

            val path = Path()
            // v1.4.6: when navigation is active the HUD MUST follow the real selected route,
            // not a decorative/current-road fallback. Slice the route around the GPS-nearest
            // vertex so only the rider's local/ahead corridor is projected. When navigation is
            // inactive we retain the live OSM map-matched current road, then camera approachPath.
            val routeCorridor = if (liveLocation != null && navigationRoute.size >= 2) {
                fun d2(p: org.maplibre.android.geometry.LatLng): Double {
                    val dy = (p.latitude - liveLocation.latitude) * 110540.0
                    val dx = (p.longitude - liveLocation.longitude) * 111320.0 * cos(Math.toRadians(liveLocation.latitude))
                    return dx*dx + dy*dy
                }
                val nearest = navigationRoute.indices.minByOrNull { d2(navigationRoute[it]) } ?: 0
                val from = (nearest - 2).coerceAtLeast(0)
                val to = (nearest + 35).coerceAtMost(navigationRoute.lastIndex)
                navigationRoute.subList(from, to + 1)
            } else emptyList()
            // Normalize route LatLng and OSM RoadPoint into one coordinate type.  Keeping
            // the Elvis chain as mixed List<LatLng>/List<RoadPoint> makes Kotlin infer
            // List<Any>, which is why latitude/longitude failed to compile in v1.4.6.1.
            val approachCoordinates: List<Pair<Double, Double>> = when {
                routeCorridor.size >= 2 -> routeCorridor.map { it.latitude to it.longitude }
                currentRoad?.points?.size ?: 0 >= 2 -> currentRoad!!.points.map { it.latitude to it.longitude }
                (target?.camera?.approachPath?.size ?: 0) >= 2 -> target!!.camera.approachPath.map { it.latitude to it.longitude }
                else -> emptyList()
            }
            var drewRealGeometry = false
            var renderedRoadPoints: List<Offset> = emptyList()

            if (liveLocation != null && approachCoordinates.size >= 2) {
                // Real OSM road-segment geometry near the upcoming camera
                // (RealCamera.approachPath), reprojected into rider-relative
                // screen space. This is genuine map data, not a decorative
                // curve - if the real road bends, this line bends with it.
                // Points clearly behind the rider (beyond the small real
                // "just behind" tolerance) are dropped entirely rather than
                // clamped onto the rider's own position, so the line never
                // bunches multiple real points into a single pixel.
                val forwardRight = approachCoordinates.map { (latitude, longitude) ->
                    realPointToForwardRight(latitude, longitude, liveLocation.latitude, liveLocation.longitude)
                }
                val relevant = forwardRight.filter { it.x >= -behindToleranceMeters && it.x <= visibleRangeMeters + behindToleranceMeters }

                if (relevant.size >= 2) {
                    val screenPoints = relevant.map { projectForwardRight(it.x, it.y) }
                    renderedRoadPoints = screenPoints
                    path.moveTo(screenPoints.first().x, screenPoints.first().y)
                    screenPoints.drop(1).forEach { path.lineTo(it.x, it.y) }
                    drewRealGeometry = true
                }
            }

            if (!drewRealGeometry) {
                // No real approach-road geometry available for the current
                // target (or no target at all) - draw a plain straight
                // heading-aligned line rather than fabricating a curve.
                path.moveTo(originX, bottomY)
                path.lineTo(originX, topY)
            }

            // Perspective current-road surface. Near the vehicle it is wide; toward the
            // horizon it narrows. Only the matched current road contributes geometry.
            if (renderedRoadPoints.size >= 2) {
                renderedRoadPoints.zipWithNext().forEach { (a, b) ->
                    fun halfWidth(y: Float): Float {
                        val depth = ((riderY - y) / (riderY - topY)).coerceIn(0f, 1f)
                        return 46.dp.toPx() * (1f - depth) + 8.dp.toPx() * depth
                    }
                    val wa = halfWidth(a.y); val wb = halfWidth(b.y)
                    val road = Path().apply { moveTo(a.x-wa,a.y); lineTo(a.x+wa,a.y); lineTo(b.x+wb,b.y); lineTo(b.x-wb,b.y); close() }
                    // Locked HUD look: asphalt stays dark; blue is the actual navigation/current-road guidance, not the whole road surface.
                    drawPath(road, color = CameraGuardPalette.Background)
                    drawLine(CameraGuardPalette.Border.copy(alpha=.72f), Offset(a.x-wa,a.y), Offset(b.x-wb,b.y), 2.dp.toPx())
                    drawLine(CameraGuardPalette.Border.copy(alpha=.72f), Offset(a.x+wa,a.y), Offset(b.x+wb,b.y), 2.dp.toPx())
                    val routeWidthA = (wa * .34f).coerceAtLeast(4.dp.toPx())
                    val routeWidthB = (wb * .34f).coerceAtLeast(3.dp.toPx())
                    drawLine(CameraGuardPalette.RouteBlue, a, b, (routeWidthA + routeWidthB) / 2f, StrokeCap.Round)
                }
            } else {
                drawPath(path, color = CameraGuardPalette.RouteBlue, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Camera marker at its REAL geographic position, projected
            // through the exact same rider-relative heading-up transform as
            // the route line above (same coordinate system for both) - not
            // a decorative centre-line marker. Forward distance is capped
            // to the visible window purely for on-screen placement when the
            // real target is further away than the visualised range; the
            // real lateral (right/left) offset from the rider's actual
            // heading is never zeroed out or invented.
            if (target != null && liveLocation != null) {
                val real = realPointToForwardRight(
                    target.camera.latitude, target.camera.longitude,
                    liveLocation.latitude, liveLocation.longitude
                )
                val markerPos = projectForwardRight(real.x, real.y)
                val accent = target.camera.type.premiumAccent()
                drawCircle(accent, radius = 15.dp.toPx(), center = markerPos)
                drawCircle(Color.Black.copy(alpha = .35f), radius = 15.dp.toPx(), center = markerPos, style = Stroke(2.dp.toPx()))
            } else if (target != null) {
                // No live location yet (rare - GPS not fixed) - fall back to
                // the old centre-line placement using only real distance,
                // since a lateral offset cannot be computed without a
                // rider origin to project from.
                val markerPos = projectForwardRight(target.distanceMeters, 0f)
                val accent = target.camera.type.premiumAccent()
                drawCircle(accent, radius = 15.dp.toPx(), center = markerPos)
                drawCircle(Color.Black.copy(alpha = .35f), radius = 15.dp.toPx(), center = markerPos, style = Stroke(2.dp.toPx()))
            }

            // 3D-styled rider arrow, fixed at the rider's real current
            // position anchor (riderY, centrally placed - see above). The
            // whole scene is projected into the heading-up frame, so the
            // arrow itself is drawn pointing straight up = the rider's real
            // current travel direction.
            val arrowCenter = Offset(originX, riderY)
            if (vehicleMode == VehicleMode.CAR) {
                // Original, lightweight top-down car glyph: clearly a car, not the old navigation arrow.
                val bodyW = 34.dp.toPx(); val bodyH = 64.dp.toPx()
                drawRoundRect(CameraGuardPalette.Accent, Offset(arrowCenter.x-bodyW/2, arrowCenter.y-bodyH/2),
                    androidx.compose.ui.geometry.Size(bodyW, bodyH), CornerRadius(10.dp.toPx(),10.dp.toPx()))
                drawRoundRect(CameraGuardPalette.AccentContainer, Offset(arrowCenter.x-bodyW*.34f, arrowCenter.y-bodyH*.20f),
                    androidx.compose.ui.geometry.Size(bodyW*.68f, bodyH*.25f), CornerRadius(5.dp.toPx(),5.dp.toPx()))
                drawLine(Color.White.copy(alpha=.75f), Offset(arrowCenter.x-bodyW*.28f,arrowCenter.y-bodyH*.34f), Offset(arrowCenter.x+bodyW*.28f,arrowCenter.y-bodyH*.34f), 1.5.dp.toPx())
            } else {
                // Original top-down scooter/rider glyph. Compact enough not to cover road geometry.
                drawCircle(CameraGuardPalette.Text, 8.dp.toPx(), Offset(arrowCenter.x, arrowCenter.y-20.dp.toPx()))
                drawCircle(CameraGuardPalette.Accent, 10.dp.toPx(), Offset(arrowCenter.x, arrowCenter.y-5.dp.toPx()))
                drawRoundRect(CameraGuardPalette.Accent, Offset(arrowCenter.x-7.dp.toPx(),arrowCenter.y),
                    androidx.compose.ui.geometry.Size(14.dp.toPx(),30.dp.toPx()), CornerRadius(6.dp.toPx(),6.dp.toPx()))
                drawCircle(CameraGuardPalette.Background, 6.dp.toPx(), Offset(arrowCenter.x, arrowCenter.y+28.dp.toPx()))
                drawLine(Color.White.copy(alpha=.7f), Offset(arrowCenter.x-13.dp.toPx(),arrowCenter.y-1.dp.toPx()), Offset(arrowCenter.x+13.dp.toPx(),arrowCenter.y-1.dp.toPx()), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun RoadRadarWidget(nearby: List<Triple<RealCamera, Float, Float>>, heading: Float) {
    Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(20.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RadioButtonChecked, null, Modifier.size(14.dp), tint = CyanGlow)
                Spacer(Modifier.width(6.dp))
                Text("Road Radar", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val c = center
                val maxR = size.minDimension / 2f - 6.dp.toPx()
                for (ring in 1..3) drawCircle(PremiumLine, maxR * ring / 3f, c, style = Stroke(1.dp.toPx()))
                drawLine(PremiumLine, Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 1.dp.toPx())
                drawLine(PremiumLine, Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 1.dp.toPx())
                nearby.forEach { (camera, distance, bearing) ->
                    val relative = ((bearing - heading) + 360f) % 360f
                    val rad = Math.toRadians(relative.toDouble())
                    val r = (distance / 3000f).coerceIn(.08f, 1f) * maxR
                    val x = c.x + r * sin(rad).toFloat()
                    val y = c.y - r * cos(rad).toFloat()
                    drawCircle(camera.type.premiumAccent(), 6.dp.toPx(), Offset(x, y))
                }
                drawCircle(CyanGlow, 5.dp.toPx(), c)
            }
            Spacer(Modifier.height(8.dp))
            if (nearby.isEmpty()) {
                Text("No cameras within 3 km", color = TextSecondary, fontSize = 11.sp)
            } else {
                nearby.take(3).forEach { (camera, distance, _) ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(camera.type.premiumAccent(), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(camera.type.shortLabel(), color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text(formatDistance(distance), color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HudNextCameraCard(target: RealCameraTarget?) {
    Surface(Modifier.fillMaxWidth().fillMaxHeight().neumorphicRaised(RoundedCornerShape(20.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
        Column(Modifier.padding(14.dp)) {
            Text("NEXT CAMERA", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (target == null) {
                Text("None nearby", color = TextSecondary, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(target.camera.type.premiumIcon(), null, Modifier.size(20.dp), tint = target.camera.type.premiumAccent())
                    Spacer(Modifier.width(8.dp))
                    Text(formatDistance(target.distanceMeters), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(target.camera.type.displayName(), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private fun bearingToCamera(currentLocation: Location?, camera: RealCamera): Float? {
    if (currentLocation == null) return null
    val results = FloatArray(2)
    Location.distanceBetween(
        currentLocation.latitude, currentLocation.longitude,
        camera.latitude, camera.longitude, results
    )
    return (results[1] + 360f) % 360f
}


@Composable
private fun CameraListScreen(
    modifier: Modifier,
    liveLocation: Location?,
    cameras: List<RealCamera>,
    loading: Boolean,
    error: String?,
    onAddManualCamera: (RealCameraType, Int?, Float?) -> Unit,
    onDeleteManualCamera: (Long) -> Unit,
    onEditManualCamera: (Long, RealCameraType, Int?, String?, Float?) -> Unit,
    onFixOsmCamera: (RealCamera, RealCameraType, Int?, String?, Float?) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var cameraToEdit by remember { mutableStateOf<RealCamera?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var listExpanded by rememberSaveable { mutableStateOf(true) }
    val sorted = remember(cameras, liveLocation, search, selectedType) {
        cameras.filter { camera ->
            (selectedType == null || camera.type.name == selectedType) &&
                (search.isBlank() || camera.type.displayName().contains(search, true) ||
                    camera.userNote.orEmpty().contains(search, true) || camera.source.name.contains(search, true))
        }.map { it to distanceToCamera(liveLocation, it) }.sortedBy { it.second ?: Float.MAX_VALUE }
    }
    val groupedByType = remember(sorted) {
        RealCameraType.entries.mapNotNull { type ->
            val group = sorted.filter { it.first.type == type }
            if (group.isEmpty()) null else type to group
        }
    }
    LazyColumn(modifier.fillMaxSize().background(AppBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = SurfaceDark,
                border = BorderStroke(1.dp, PremiumLine), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(CyanGlow.copy(alpha = .12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoCamera, null, tint = CyanGlow, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CAMERA ATLAS", color = CyanGlow, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                            Text("Road cameras", color = TextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = CyanGlow.copy(alpha = .12f)) {
                            Text("${cameras.size} LIVE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = CyanGlow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Browse nearby cameras, review details and manage your own locations.",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showAddDialog = true }, enabled = liveLocation != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumAccent, contentColor = AppBackground)) {
                        Icon(Icons.Default.AddLocationAlt, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Add camera at my location", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (liveLocation == null) Text("Waiting for GPS before you can add a camera.",
                        color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumMetricCard(Modifier.weight(1f), Icons.Default.Speed, "Speed", cameras.count { it.type == RealCameraType.SPEED }.toString(), WarningAmber)
                PremiumMetricCard(Modifier.weight(1f), Icons.Default.Traffic, "Red light", cameras.count { it.type == RealCameraType.RED_LIGHT }.toString(), DangerRed)
                PremiumMetricCard(Modifier.weight(1f), Icons.Default.Shield, "Other", cameras.count { it.type != RealCameraType.RED_LIGHT && it.type != RealCameraType.SPEED }.toString(), CyanGlow)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceDark,
                border = BorderStroke(1.dp, PremiumLine), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, null, tint = CyanGlow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explore cameras", color = TextPrimary, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${sorted.size} found", color = TextSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = search, onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search cameras, source or notes", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = CyanGlow) },
                        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "Clear search") } },
                        singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(selectedType == null, { selectedType = null }, label = { Text("All") })
                        RealCameraType.entries.forEach { type ->
                            FilterChip(selectedType == type.name, { selectedType = type.name },
                                label = { Text(type.shortLabel()) },
                                leadingIcon = { Icon(type.premiumIcon(), null, Modifier.size(15.dp), tint = type.premiumAccent()) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().clickable { listExpanded = !listExpanded }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${if (listExpanded) "Hide" else "Show"} camera list", color = CyanGlow,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (listExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = CyanGlow, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        if (loading) item { Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = CyanGlow, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp)); Text("Updating camera data…", color = TextSecondary, fontSize = 12.sp)
        } }
        if (error != null) item { PremiumMessageCard(error, DangerRed) }
        if (listExpanded) {
            if (sorted.isEmpty() && !loading) item {
                PremiumMessageCard(if (cameras.isEmpty()) "No cameras loaded yet. Check location access and your connection."
                    else "No matches. Try another camera type or search.", CyanGlow)
            }
            groupedByType.forEach { (type, group) ->
                item {
                    Row(Modifier.padding(top = 5.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).background(type.premiumAccent().copy(alpha = .12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(type.premiumIcon(), null, Modifier.size(17.dp), tint = type.premiumAccent())
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(type.displayName(), color = TextPrimary, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${group.size}", color = type.premiumAccent(), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                items(group) { pair -> PremiumCameraRow(pair.first, pair.second, onDeleteManualCamera,
                    onEditCamera = { cameraToEdit = it }, onFixOsmCamera = { cameraToEdit = it }) }
            }
        }
    }
    if (
        showAddDialog
    ) {

        AddCameraDialog(
            liveLocation = liveLocation,

            onDismiss = {

                showAddDialog =
                    false
            },

            onSave = {
                    type,
                    speedLimit,
                    monitoredBearing ->

                onAddManualCamera(
                    type,
                    speedLimit,
                    monitoredBearing
                )

                showAddDialog =
                    false
            }
        )
    }

    cameraToEdit?.let { camera ->
        CameraEditDialog(
            camera = camera,
            liveLocation = liveLocation,
            onDismiss = { cameraToEdit = null },
            onSave = { type, speedLimit, note, monitoredBearing ->
                if (camera.source == RealCameraSource.MANUAL) {
                    onEditManualCamera(camera.id, type, speedLimit, note, monitoredBearing)
                } else {
                    onFixOsmCamera(camera, type, speedLimit, note, monitoredBearing)
                }
                cameraToEdit = null
            }
        )
    }
}

@Composable
private fun PremiumMetricCard(modifier: Modifier, icon: ImageVector, title: String, value: String, accent: Color) {
    Surface(modifier.neumorphicRaised(RoundedCornerShape(22.dp), SurfaceSoft, 8.dp), shape = RoundedCornerShape(22.dp), color = Color.Transparent) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(title, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PremiumCameraRow(camera: RealCamera, distanceMeters: Float?, onDeleteManualCamera: (Long) -> Unit,
    onEditCamera: (RealCamera) -> Unit, onFixOsmCamera: (RealCamera) -> Unit) {
    val accent = camera.type.premiumAccent()
    val manual = camera.source == RealCameraSource.MANUAL
    val master = camera.source == RealCameraSource.MASTER
    Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(lerp(SurfaceDark, accent, .13f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(camera.type.premiumIcon(), null, tint = accent, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(camera.type.displayName(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(if (master) "ADMIN CAMERA" else if (manual) "SAVED BY YOU" else "OPENSTREETMAP", color = TextSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDistance(distanceMeters), color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(if (distanceMeters == null) "Location needed for distance" else "From your location", color = TextSecondary, fontSize = 10.sp)
                }
                if (camera.type.usesSpeedWarningDistance() && camera.speedLimit != null) PremiumSpeedBadge(camera.speedLimit)
            }
            camera.userNote?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (manual || !master) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = PremiumLine)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (manual) {
                        TextButton(onClick = { onEditCamera(camera) }) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = CyanGlow)
                            Spacer(Modifier.width(6.dp)); Text("Edit", color = CyanGlow)
                        }
                        TextButton(onClick = { onDeleteManualCamera(camera.id) }) {
                            Icon(Icons.Default.DeleteOutline, null, Modifier.size(16.dp), tint = DangerRed)
                            Spacer(Modifier.width(6.dp)); Text("Delete", color = DangerRed)
                        }
                    } else {
                        TextButton(onClick = { onFixOsmCamera(camera) }) {
                            Icon(Icons.Default.EditLocationAlt, null, Modifier.size(17.dp), tint = CyanGlow)
                            Spacer(Modifier.width(6.dp)); Text("Correct camera", color = CyanGlow)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Alerts-tab replacement for the reused warning card. Same real
 * activeCameraTarget/appSettings data as everywhere else in the app (no
 * duplicate warning logic) - just without any "Road Radar" branding, which
 * is now exclusive to the LIVE HUD tab per the Alerts-tab redesign.
 */
@Composable
private fun AlertsCurrentApproachCard(target: RealCameraTarget?, appSettings: AppSettings) {
    if (target == null) {
        Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 8.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
            Column(Modifier.padding(18.dp)) {
                Text("No relevant camera on your current approach", color = TextSecondary, fontSize = 13.sp)
            }
        }
        return
    }
    val accent = target.camera.type.premiumAccent()
    val zone = appSettings.warningDistanceFor(target.camera.type).toFloat().coerceAtLeast(1f)
    val inZone = target.distanceMeters <= zone
    Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(24.dp), if(inZone) lerp(SurfaceDark,accent,.08f) else SurfaceDark, 9.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        border = BorderStroke(if (inZone) 2.dp else 1.dp, if (inZone) accent else PremiumLine.copy(alpha=.45f))) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(target.camera.type.premiumIcon(), null, Modifier.size(26.dp), tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(target.camera.type.displayName(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (inZone) "In warning zone" else "Approaching", color = if (inZone) accent else TextSecondary, fontSize = 11.sp)
            }
            Text(formatDistance(target.distanceMeters), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun distanceFromRouteMeters(location: Location, points: List<org.maplibre.android.geometry.LatLng>): Float {
    if (points.isEmpty()) return Float.MAX_VALUE
    var best = Float.MAX_VALUE
    val out = FloatArray(1)
    points.forEach { p ->
        Location.distanceBetween(location.latitude, location.longitude, p.latitude, p.longitude, out)
        if (out[0] < best) best = out[0]
    }
    return best
}

@Composable
private fun CommunityRoadReportButton(onReport: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FloatingActionButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp).shadow(9.dp, RoundedCornerShape(17.dp), clip=false), shape = RoundedCornerShape(17.dp),
            elevation = FloatingActionButtonDefaults.elevation(0.dp), containerColor = DangerRed, contentColor = Color.White) { Icon(Icons.Default.Report, "Report road alert") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val reports = listOf(
                Triple("Road closure", Icons.Default.Block, "ROAD_CLOSURE"),
                Triple("Congestion", Icons.Default.Traffic, "CONGESTION"),
                Triple("Police", Icons.Default.LocalPolice, "POLICE"),
                Triple("Road works", Icons.Default.Construction, "ROAD_WORKS"),
                Triple("Lane closure", Icons.Default.DoNotDisturbOn, "LANE_CLOSURE"),
                Triple("Object on road", Icons.Default.Warning, "OBJECT_ON_ROAD")
            )
            reports.forEach { (label, icon, code) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { expanded = false; onReport(code) })
            }
        }
    }
}

@Composable
private fun AlertsScreen(modifier: Modifier, activeCameraTarget: RealCameraTarget?, appSettings: AppSettings,
    onTestRedLightWarning: () -> Unit, onTestSpeedCameraWarning: () -> Unit) {
    var tryExpanded by rememberSaveable { mutableStateOf(false) }
    var zonesExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PremiumSectionHeader("AWARENESS CENTRE", "Your alerts", "Camera warnings, tuned for your journey")
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(22.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(22.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = if (appSettings.drivingModeEnabled) CyanGlow else TextSecondary)
                    Spacer(Modifier.width(10.dp)); Text(if (appSettings.drivingModeEnabled) "Driving Mode enabled" else "Driving Mode off", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp)); PremiumStatusLine("Voice alerts", appSettings.voiceAlertsEnabled); PremiumStatusLine("Warning beep", appSettings.beepAlertsEnabled)
            }
        }
        DropdownSectionHeader("TRY YOUR ALERTS", tryExpanded) { tryExpanded = !tryExpanded }
        AnimatedVisibility(tryExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertPreviewCard("Red-light warning", "Preview your traffic-signal alert", Icons.Default.Traffic, DangerRed, onTestRedLightWarning)
                AlertPreviewCard("Speed-camera warning", "Preview your speed-camera alert", Icons.Default.Speed, WarningAmber, onTestSpeedCameraWarning)
            }
        }
        DropdownSectionHeader("YOUR WARNING ZONES", zonesExpanded) { zonesExpanded = !zonesExpanded }
        AnimatedVisibility(zonesExpanded) {
            Surface(shape = RoundedCornerShape(22.dp), color = SurfaceDark) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RealCameraType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.premiumIcon(), null, Modifier.size(18.dp), tint = type.premiumAccent()); Spacer(Modifier.width(10.dp))
                            Text(type.shortLabel(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${appSettings.warningDistanceFor(type)} m", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Text("Change alert options in Settings. Set up and test while stationary.", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun DropdownSectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(18.dp), SurfaceDark, 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
        }
    }
}

@Composable
private fun CompactSettingToggle(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    accent: Color,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onChanged(!enabled) }, modifier = modifier.height(66.dp).neumorphicRaised(RoundedCornerShape(18.dp), if (enabled) lerp(SurfaceDark, accent, .10f) else SurfaceDark, 7.dp),
        shape = RoundedCornerShape(18.dp), color = Color.Transparent,
        border = null
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (enabled) accent else TextSecondary)
            Spacer(Modifier.height(3.dp))
            Text(label, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (enabled) "ON" else "OFF", color = if (enabled) accent else TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CommunityRidersSection(communityRiders: List<CommunityRider>) {
    Surface(Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(26.dp), SurfaceDark, 10.dp), color = Color.Transparent, shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = CameraGuardPalette.Accent)
                Spacer(Modifier.width(10.dp))
                Text("Riders nearby", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${communityRiders.size}", color = PremiumAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text("Participating CameraGuard riders in your area", color = TextSecondary, fontSize = 11.sp)
            if (communityRiders.isEmpty()) Text("No other riders are sharing nearby right now.", color = TextSecondary, fontSize = 12.sp)
            val busiest = communityRiders.groupingBy { it.cell }.eachCount().maxByOrNull { it.value }
            if (busiest != null && busiest.value >= 3) Text("Most active nearby zone: ${busiest.value} riders", color = CyanGlow, fontSize = 12.sp)
            communityRiders.take(8).forEach { rider ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(CameraGuardPalette.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(rider.displayName.take(1).uppercase(), color = CameraGuardPalette.Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(rider.displayName, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatDistance(rider.distanceMeters), color = CyanGlow, fontSize = 12.sp)
                }
            }
            if (communityRiders.size > 8) Text("+${communityRiders.size-8} more nearby", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * Free offline map area downloads (com.boss.cameraguard.data.OfflineMapRepository, MapLibre's
 * built-in OfflineManager against the same free OpenFreeMap style already used online - no new
 * tile provider, account or key). Purely a local cache the rider manages here; navigation,
 * camera warnings and the HUD keep using their existing live data untouched.
 */
@Composable
private fun OfflineMapsSection(liveLocation: Location?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var areas by remember { mutableStateOf<List<com.boss.cameraguard.data.OfflineMapRepository.OfflineArea>>(emptyList()) }
    var loadingList by remember { mutableStateOf(true) }
    var radiusKm by remember { mutableStateOf(5.0) }
    var areaName by remember { mutableStateOf("") }
    var downloadPercent by remember { mutableStateOf<Int?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        loadingList = true
        com.boss.cameraguard.data.OfflineMapRepository.list(context) { areas = it; loadingList = false }
    }
    LaunchedEffect(Unit) { refresh() }
    Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 10.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("OFFLINE MAPS", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("Download map areas for use without a signal", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Saves the street map (not live cameras/traffic) around a chosen point so it still renders offline.", color = TextSecondary, fontSize = 11.sp)
            OutlinedTextField(
                value = areaName, onValueChange = { areaName = it.take(30) },
                label = { Text("Area name (e.g. Home city)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5.0 to "5 km", 10.0 to "10 km", 20.0 to "20 km").forEach { (km, label) ->
                    val chosen = radiusKm == km
                    Surface(
                        onClick = { radiusKm = km }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        color = if (chosen) CyanGlow.copy(alpha = .16f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (chosen) CyanGlow else PremiumLine)
                    ) { Text(label, Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, color = if (chosen) CyanGlow else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            downloadPercent?.let { percent ->
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(), color = CyanGlow)
                Text("Downloading… $percent%", color = TextSecondary, fontSize = 11.sp)
            }
            downloadError?.let { Text(it, color = DangerRed, fontSize = 11.sp) }
            Button(
                onClick = {
                    val origin = liveLocation ?: run { downloadError = "Waiting for GPS to find your area."; return@Button }
                    val name = areaName.trim().ifBlank { "Area ${areas.size + 1}" }
                    downloadError = null; downloadPercent = 0
                    com.boss.cameraguard.data.OfflineMapRepository.download(
                        context, name, org.maplibre.android.geometry.LatLng(origin.latitude, origin.longitude), radiusKm,
                        context.resources.displayMetrics.density,
                        onProgress = { percent, _, _ -> downloadPercent = percent },
                        onComplete = { downloadPercent = null; areaName = ""; refresh() },
                        onError = { message -> downloadPercent = null; downloadError = message }
                    )
                },
                enabled = downloadPercent == null, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download area around me", fontWeight = FontWeight.Bold)
            }
            if (loadingList) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CyanGlow)
            else if (areas.isEmpty()) Text("No offline areas saved yet.", color = TextSecondary, fontSize = 12.sp)
            else areas.forEach { area ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(area.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${area.radiusKm.roundToInt()} km · " + (if (area.complete) com.boss.cameraguard.data.OfflineMapRepository.formatSize(area.completedSizeBytes) else "downloading…"),
                            color = TextSecondary, fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { com.boss.cameraguard.data.OfflineMapRepository.delete(context, area.regionId) { refresh() } }
                    }) { Icon(Icons.Default.Delete, "Delete offline area", tint = TextSecondary) }
                }
            }
        }
    }
}

/** Manage Home/Work/saved places and clear recent-trip history (SavedPlacesRepository, on-device). */
@Composable
private fun SavedPlacesSection() {
    val context = LocalContext.current
    var home by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.home(context)) }
    var work by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.work(context)) }
    var saved by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.savedPlaces(context)) }
    var tripCount by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.trips(context).size) }
    Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 10.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SAVED PLACES & TRIPS", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("Home, Work and saved places", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (home == null && work == null && saved.isEmpty()) {
                Text("Nothing saved yet. On the Route tab, pick a place and tap Home, Work or Save.", color = TextSecondary, fontSize = 12.sp)
            } else {
                home?.let { PlaceRow(Icons.Default.Home, "Home", it.name) }
                work?.let { PlaceRow(Icons.Default.Work, "Work", it.name) }
                saved.forEach { place ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, null, tint = CyanGlow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(place.name, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = {
                            com.boss.cameraguard.data.SavedPlacesRepository.removeSavedPlace(context, place.name, place.lat, place.lon)
                            saved = com.boss.cameraguard.data.SavedPlacesRepository.savedPlaces(context)
                        }) { Icon(Icons.Default.Delete, "Remove saved place", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            if (tripCount > 0) {
                Text("$tripCount recent trip${if (tripCount == 1) "" else "s"} recorded (Route tab).", color = TextSecondary, fontSize = 11.sp)
                TextButton(onClick = {
                    com.boss.cameraguard.data.SavedPlacesRepository.clearTrips(context)
                    tripCount = 0
                }) { Text("Clear trip history", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun PlaceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, name: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CyanGlow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextSecondary, fontSize = 10.sp)
            Text(name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    liveLocation: Location?,
    realCameras: List<RealCamera>,
    activeCameraTarget: RealCameraTarget?,
    onTestRedLightWarning: () -> Unit,
    onTestSpeedCameraWarning: () -> Unit,
    cameraDataLoading: Boolean,
    cameraDataError: String?,
    hiddenOsmCameraCount: Int,
    onExportManualCameras: () -> Unit,
    onImportManualCameras: () -> Unit,
    onRestoreOsmOverrides: () -> Unit,
    onStartDriveDiagnostics: () -> Unit,
    onExportDriveDiagnostics: () -> Unit,
    onDrivingModeChanged: (Boolean) -> Unit,
    onVoiceAlertsChanged: (Boolean) -> Unit,
    onBeepAlertsChanged: (Boolean) -> Unit,
    onVehicleModeChanged: (VehicleMode) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    mapCameraTypes: Set<RealCameraType>,
    onMapCameraTypesChanged: (Set<RealCameraType>) -> Unit,
    onWarningDistancesChanged: (Map<RealCameraType, Int>) -> Unit,
    onCommunityModeChanged: (Boolean, String) -> Unit,
    onCommunityDisplayNameChanged: (String) -> Unit,
    communityRiders: List<CommunityRider>,
    onVoicePresetChanged: (Int) -> Unit
) {
    var showCommunityConsent by remember { mutableStateOf(false) }
    var showCommunityNameEditor by remember { mutableStateOf(false) }
    var zonesExpanded by rememberSaveable { mutableStateOf(false) }
    var savedZone by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val authScope = rememberCoroutineScope()
    var accountUser by remember { mutableStateOf(CameraGuardAuthManager.currentUser()) }
    // Keep Settings in sync with auth changes initiated elsewhere in the app.
    DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            accountUser = firebaseAuth.currentUser
        }
        val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var showEmailAuth by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var showPhoneAuth by remember { mutableStateOf(false) }
    if (showProfileEditor) {
        var editedName by remember { mutableStateOf(accountUser?.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { if (!accountBusy) showProfileEditor = false },
            title = { Text("Edit account display name") },
            text = { OutlinedTextField(editedName, { editedName = it.take(40) },
                label = { Text("Display name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = !accountBusy && editedName.trim().isNotEmpty(), onClick = {
                accountBusy = true
                authScope.launch {
                    try {
                        accountUser = CameraGuardAuthManager.updateDisplayName(editedName)
                        accountMessage = "Account display name updated."
                        showProfileEditor = false
                    } catch (e: Exception) { accountMessage = e.message ?: "Profile update failed." }
                    finally { accountBusy = false }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(enabled = !accountBusy, onClick = { showProfileEditor = false }) { Text("Cancel") } }
        )
    }
    if (showSignOutConfirmation) AlertDialog(
        onDismissRequest = { showSignOutConfirmation = false },
        title = { Text("Sign out of CameraGuard?") },
        text = { Text("Your current account will be disconnected on this device. CameraGuard will create a new guest session for basic features. Your old account data will not be deleted.") },
        confirmButton = { TextButton(onClick = {
            showSignOutConfirmation = false
            if (settings.communityModeEnabled) {
                accountMessage = "Turn off Riders and wait for location sharing to stop before signing out."
            } else {
                CameraGuardAuthManager.signOut()
                accountUser = null
                accountMessage = "Signed out. Guest mode is available."
                CameraGuardAuthManager.ensureGuestSession({ accountUser = it },
                    { accountMessage = it.message ?: "Could not start guest session." })
            }
        }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { showSignOutConfirmation = false }) { Text("Cancel") } }
    )
    if (showPhoneAuth) PhoneAuthDialog(
        onDismiss = { showPhoneAuth = false },
        onAccountChanged = { user, message ->
            accountUser = user
            accountMessage = message
            showPhoneAuth = false
        }
    )
    if (showEmailAuth) EmailAuthDialog(
        onDismiss = { showEmailAuth = false },
        onAccountChanged = { user, message ->
            accountUser = user
            accountMessage = message
            showEmailAuth = false
        }
    )
    if (showCommunityConsent) CommunityConsentDialog(settings.communityDisplayName,
        onDismiss = { showCommunityConsent = false }, onAgree = { name ->
            showCommunityConsent = false
            onCommunityModeChanged(true, name)
        })
    if (showCommunityNameEditor) CommunityNameDialog(settings.communityDisplayName,
        onDismiss = { showCommunityNameEditor = false }, onSave = { name ->
            showCommunityNameEditor = false
            onCommunityDisplayNameChanged(name)
        })
    Column(modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PremiumSectionHeader("PERSONALISE YOUR DRIVE", "Settings", "Your controls. Your preferred journey.")
        Text("APPEARANCE", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(20.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App theme", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Changes the complete CameraGuard interface while keeping text readable.", color = TextSecondary, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(AppThemeMode.DARK to "Dark mode", AppThemeMode.LIGHT to "Light mode").forEach { (mode, label) ->
                        val selected = settings.themeMode == mode
                        Surface(
                            onClick = { onThemeModeChanged(mode) }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) CyanGlow.copy(alpha = .16f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) CyanGlow else PremiumLine)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(if (mode == AppThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = if (selected) CyanGlow else TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(label, color = if (selected) CyanGlow else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Text("DRIVING CONTROLS", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactSettingToggle("Drive", Icons.Default.DirectionsCar, settings.drivingModeEnabled, CyanGlow, onDrivingModeChanged, Modifier.weight(1f))
            CompactSettingToggle("Voice", Icons.Default.RecordVoiceOver, settings.voiceAlertsEnabled, PremiumAccent, onVoiceAlertsChanged, Modifier.weight(1f))
            CompactSettingToggle("Beep", Icons.Default.VolumeUp, settings.beepAlertsEnabled, WarningAmber, onBeepAlertsChanged, Modifier.weight(1f))
            CompactSettingToggle("Riders", Icons.Default.Groups, settings.communityModeEnabled, CameraGuardPalette.Accent, { enabled ->
                if (enabled) showCommunityConsent = true else onCommunityModeChanged(false, settings.communityDisplayName)
            }, Modifier.weight(1f))
        }
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(20.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VEHICLE PROFILE", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.3.sp)
                Text("Used by driving behaviour", color = TextSecondary, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(VehicleMode.SCOOTER to "Scooter", VehicleMode.CAR to "Car").forEach { (mode, label) ->
                        val selected = settings.vehicleMode == mode
                        Surface(
                            onClick = { onVehicleModeChanged(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) CyanGlow.copy(alpha = .16f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) CyanGlow else PremiumLine)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(if (mode == VehicleMode.SCOOTER) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar, null, tint = if (selected) CyanGlow else TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp)); Text(label, color = if (selected) CyanGlow else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (settings.communityDisplayName.isNotBlank()) {
            OutlinedButton(onClick = { showCommunityNameEditor = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Edit, null, Modifier.size(17.dp)); Spacer(Modifier.width(9.dp))
                Text("Edit rider name: ${settings.communityDisplayName}", fontSize = 12.sp)
            }
        }
        if (settings.communityModeEnabled) CommunityRidersSection(communityRiders)
        var mapCamerasExpanded by rememberSaveable { mutableStateOf(false) }
        DropdownSectionHeader("CAMERAS ON MAP  ·  ${mapCameraTypes.size}/${RealCameraType.entries.size}", mapCamerasExpanded) { mapCamerasExpanded = !mapCamerasExpanded }
        AnimatedVisibility(mapCamerasExpanded) {
            Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(20.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RealCameraType.entries.forEach { type ->
                        Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                            Row(Modifier.fillMaxWidth().heightIn(min=42.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(type.premiumIcon(), null, tint=type.premiumAccent(), modifier=Modifier.size(19.dp)); Spacer(Modifier.width(10.dp))
                                Text(type.displayName(), color=TextPrimary, fontSize=12.sp, modifier=Modifier.weight(1f))
                                Switch(checked = type in mapCameraTypes, onCheckedChange = { enabled -> onMapCameraTypesChanged(if (enabled) mapCameraTypes + type else mapCameraTypes - type) }, modifier=Modifier.scale(.82f))
                            }
                            WarningDistanceEditor(type.premiumIcon(), "${type.shortLabel()} warning", settings.warningDistanceFor(type).toString(), type.premiumAccent()) { value ->
                                val distance=value.toIntOrNull()
                                if(distance != null && distance in 50..5000) {
                                    onWarningDistancesChanged(RealCameraType.entries.associateWith { if(it==type) distance else settings.warningDistanceFor(it) })
                                }
                            }
                            HorizontalDivider(color=PremiumLine.copy(alpha=.55f))
                        }
                    }
                    Text("Camera visibility and its warning distance are managed together. Warning range: 50–5000 m.", color=TextSecondary, fontSize=10.sp)
                }
            }
        }
        var tryAlertsExpanded by rememberSaveable { mutableStateOf(false) }
        DropdownSectionHeader("TRY YOUR ALERTS", tryAlertsExpanded) { tryAlertsExpanded = !tryAlertsExpanded }
        AnimatedVisibility(tryAlertsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertPreviewCard("Red-light warning", "Preview your traffic-signal alert", Icons.Default.Traffic, DangerRed, onTestRedLightWarning)
                AlertPreviewCard("Speed-camera warning", "Preview your speed-camera alert", Icons.Default.Speed, WarningAmber, onTestSpeedCameraWarning)
            }
        }
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 10.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SPOKEN WARNINGS", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Warning voice", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Used for every spoken camera warning (speed, red light, and all others).",
                    color = TextSecondary, fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0 to "Voice 1", 1 to "Voice 2", 2 to "Voice 3").forEach { (index, label) ->
                        val selected = settings.voicePreset == index
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onVoicePresetChanged(index) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) CyanGlow.copy(alpha = .18f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) CyanGlow else PremiumLine)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                color = if (selected) CyanGlow else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 10.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CAMERA BACKUP", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Keep your saved cameras close", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExportManualCameras, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Export", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onImportManualCameras, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Import", fontSize = 12.sp)
                    }
                }
                if (hiddenOsmCameraCount > 0) TextButton(onClick = onRestoreOsmOverrides) {
                    Text("Restore $hiddenOsmCameraCount hidden OSM cameras", fontSize = 12.sp)
                }
            }
        }
        OfflineMapsSection(liveLocation)
        SavedPlacesSection()
        Surface(modifier = Modifier.neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 10.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DIAGNOSTICS", color = PremiumAccent, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text("Troubleshooting log", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Records driving/GPS activity plus Community Google sign-in attempts (no tokens or passwords). " +
                        "Use \"Clear log\" right before reproducing an issue, then \"Export log\" to save a text file you can share.",
                    color = TextSecondary, fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onStartDriveDiagnostics, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Clear log", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onExportDriveDiagnostics, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Export log", fontSize = 12.sp)
                    }
                }
            }
        }
        Text("CAMERAGUARD  /  DRIVE AWARE", color = TextSecondary, fontSize = 9.sp, letterSpacing = 1.6.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp))
    }
}

@Composable
private fun PhoneAuthDialog(
    onDismiss: () -> Unit,
    onAccountChanged: (com.google.firebase.auth.FirebaseUser, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var phone by rememberSaveable { mutableStateOf("+39") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun requestCode(forceResend: Boolean) {
        if (activity == null) { message = "Phone verification could not open from this screen."; return }
        busy = true; message = null
        try {
            CameraGuardAuthManager.startPhoneVerification(
                activity = activity, phoneNumber = phone, resendToken = if (forceResend) resendToken else null,
                listener = object : CameraGuardAuthManager.PhoneVerificationListener {
                    override fun onCodeSent(id: String, token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken) {
                        verificationId = id; resendToken = token; busy = false
                        message = "SMS code sent. Enter the 6-digit code."
                    }
                    override fun onVerificationCompleted(result: CameraGuardAuthManager.PhoneAuthResult) {
                        busy = false
                        onAccountChanged(result.user, if (result.linkedExistingGuest)
                            "Mobile number verified. Your existing CameraGuard guest UID and data were preserved."
                            else "Mobile number verified and signed in.")
                    }
                    override fun onVerificationFailed(exception: Exception) {
                        busy = false; message = exception.message ?: "Phone verification failed."
                    }
                }
            )
        } catch (e: Exception) { busy = false; message = e.message ?: "Could not send SMS code." }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Continue with mobile number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Use the international country code, for example +39 for Italy or +92 for Pakistan.", color = TextSecondary, fontSize = 11.sp)
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("Mobile number") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = verificationId.isBlank() && !busy, modifier = Modifier.fillMaxWidth()
                )
                if (verificationId.isNotBlank()) {
                    OutlinedTextField(
                        value = smsCode, onValueChange = { if (it.length <= 6) smsCode = it.filter(Char::isDigit) },
                        label = { Text("6-digit SMS code") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(enabled = !busy && resendToken != null, onClick = { requestCode(true) }) { Text("Resend SMS code") }
                    TextButton(enabled = !busy, onClick = { verificationId = ""; smsCode = ""; message = null }) { Text("Change mobile number") }
                }
                message?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                Text("CameraGuard never stores the SMS verification code. Verification is handled by Firebase Authentication.", color = TextSecondary, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                if (verificationId.isBlank()) requestCode(false) else {
                    busy = true; message = null
                    scope.launch {
                        try {
                            val result = CameraGuardAuthManager.verifyPhoneCode(verificationId, smsCode)
                            onAccountChanged(result.user, if (result.linkedExistingGuest)
                                "Mobile number verified. Your existing CameraGuard guest UID and data were preserved."
                                else "Mobile number verified and signed in.")
                        } catch (e: Exception) { message = e.message ?: "Invalid or expired SMS code." }
                        finally { busy = false }
                    }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (verificationId.isBlank()) "Send SMS code" else "Verify code")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmailAuthDialog(
    onDismiss: () -> Unit,
    onAccountChanged: (com.google.firebase.auth.FirebaseUser, String) -> Unit
) {
    val connectingExisting = CameraGuardAuthManager.currentUser()?.isAnonymous == false
    var createMode by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (connectingExisting) "Connect email to your account" else if (createMode) "Create CameraGuard account" else "Sign in with email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!connectingExisting) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(createMode, { createMode = true; message = null }, { Text("Create account") }, Modifier.weight(1f))
                    FilterChip(!createMode, { createMode = false; message = null }, { Text("Sign in") }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (createMode || connectingExisting) OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it },
                    label = { Text("Confirm password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!createMode && !connectingExisting) TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true; message = null
                        scope.launch {
                            try {
                                CameraGuardAuthManager.sendPasswordReset(email)
                                message = "Password reset email sent."
                            } catch (e: Exception) { message = e.message ?: "Could not send password reset email." }
                            finally { busy = false }
                        }
                    }
                ) { Text("Forgot password?") }
                message?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                if (createMode || connectingExisting) Text(
                    "The email will be linked to your current CameraGuard UID. A verification email will be sent.",
                    color = TextSecondary, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                if ((createMode || connectingExisting) && password != confirmPassword) {
                    message = "Passwords do not match."
                    return@Button
                }
                busy = true; message = null
                scope.launch {
                    try {
                        val result = if (createMode || connectingExisting)
                            CameraGuardAuthManager.createOrLinkEmailAccount(email, password)
                        else CameraGuardAuthManager.signInWithEmail(email, password)
                        val status = if (createMode || connectingExisting)
                            "Email account connected. Check your inbox and verify your email address."
                        else if (result.user.isEmailVerified) "Email account signed in."
                        else "Signed in. Please verify your email address."
                        onAccountChanged(result.user, status)
                    } catch (e: Exception) { message = e.message ?: "Email authentication failed." }
                    finally { busy = false }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (connectingExisting) "Connect email" else if (createMode) "Create account" else "Sign in")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommunityConsentDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onAgree: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val cleanName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Groups, contentDescription = null, tint = ElectricBlue) },
        title = { Text("Join CameraGuard Community") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "If you enable Community Riders, other CameraGuard community members can see your chosen display name and your current/near-live location on the community map."
                )
                Text(
                    "Community Mode is optional. You can switch it off at any time; CameraGuard will then stop publishing your live presence.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Display name") },
                    placeholder = { Text("e.g. Abdul") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "By tapping I AGREE & ENABLE, you explicitly agree to share this display name and your live location with other Community Mode users while the feature is enabled.",
                    color = WarningAmber,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                enabled = cleanName.isNotBlank(),
                onClick = { onAgree(cleanName.take(40)) }
            ) { Text("I AGREE & ENABLE") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
private fun CommunityNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val cleanName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community display name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = cleanName.isNotBlank(),
                onClick = { onSave(cleanName.take(40)) }
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun PremiumSettingCard(icon: ImageVector, title: String, description: String,
    checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    val background by animateColorAsState(if (checked) lerp(SurfaceDark, accent, .15f) else CameraGuardPalette.Surface, label = "controlBackground")
    val border by animateColorAsState(if (checked) accent.copy(alpha = .75f) else PremiumLine, label = "controlBorder")
    Surface(Modifier.fillMaxWidth().heightIn(min = 186.dp)
        .neumorphicRaised(RoundedCornerShape(24.dp), background, if (checked) 11.dp else 8.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        shape = RoundedCornerShape(24.dp), color = Color.Transparent, border = BorderStroke(if (checked) 1.5.dp else 1.dp, border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, tint = if (checked) accent else TextSecondary, modifier = Modifier.size(28.dp))
                Switch(checked = checked, onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedThumbColor = AppBackground, checkedTrackColor = accent,
                        uncheckedThumbColor = TextSecondary, uncheckedTrackColor = CameraGuardPalette.Raised, uncheckedBorderColor = PremiumLine))
            }
            Text(title, color = if (checked) TextPrimary else TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            Text(if (checked) "ON" else "OFF", color = if (checked) accent else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun WarningDistanceEditor(icon: ImageVector, title: String, value: String, accent: Color, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var customValue by remember(value) { mutableStateOf(value) }
    val choices = (listOf(50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 5000) + listOfNotNull(value.toIntOrNull())).distinct().sorted()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(9.dp))
            Text(title, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(15.dp), SurfaceDark, 6.dp), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, PremiumLine)) {
                Text("$value metres", color = accent, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, "Choose distance for $title", tint = TextSecondary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { distance ->
                    DropdownMenuItem(text = { Text("$distance m${if (value == distance.toString()) "  ✓" else ""}") },
                        onClick = { expanded = false; onValueChange(distance.toString()) })
                }
                DropdownMenuItem(text = { Text("Custom distance…") }, onClick = { expanded = false; customValue = value; custom = true })
            }
        }
    }
    if (custom) {
        val valid = customValue.toIntOrNull()?.let { it in 50..5000 } == true
        AlertDialog(onDismissRequest = { custom = false }, title = { Text(title) },
            text = { OutlinedTextField(value = customValue, onValueChange = { customValue = it.filter(Char::isDigit).take(4) },
                label = { Text("Distance in metres") }, supportingText = { Text("50–5000 m") }, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = { TextButton(enabled = valid, onClick = { onValueChange(customValue); custom = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
    }
}

@Composable
private fun PremiumStatusLine(
    label: String,
    enabled: Boolean
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        5.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(
                        7.dp
                    )
                    .clip(
                        CircleShape
                    )
                    .background(
                        if (
                            enabled
                        ) {
                            SuccessGreen
                        } else {
                            DangerRed
                        }
                    )
        )

        Spacer(
            Modifier.width(
                9.dp
            )
        )

        Text(
            label,

            modifier =
                Modifier.weight(
                    1f
                ),

            color =
                TextSecondary,

            fontSize =
                11.sp
        )

        Text(
            if (
                enabled
            ) {
                "ACTIVE"
            } else {
                "OFF"
            },

            color =
                if (
                    enabled
                ) {
                    SuccessGreen
                } else {
                    DangerRed
                },

            fontSize =
                9.sp,

            fontWeight =
                FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PremiumSectionHeader(eyebrow: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = PremiumAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(7.dp))
            Text(title, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Box(Modifier.padding(start = 12.dp).size(40.dp).background(CameraGuardPalette.Raised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Shield, null, tint = PremiumAccent, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun PremiumMessageCard(
    text: String,
    color: Color
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        color =
            color.copy(
                alpha =
                    0.10f
            ),

        shape =
            RoundedCornerShape(
                18.dp
            ),

        border =
            BorderStroke(
                1.dp,
                color.copy(
                    alpha =
                        0.25f
                )
            )
    ) {

        Text(
            text,

            modifier =
                Modifier.padding(
                    14.dp
                ),

            color =
                color,

            fontSize =
                11.sp
        )
    }
}

/**
 * Shared direction-arrow control used by both AddCameraDialog and
 * CameraEditDialog. The selected bearing becomes the camera's real
 * monitoredBearing / direction data (see ManualCameraStore + the warning
 * engine) - it is not just UI decoration. Tap anywhere on the dial, or pick
 * a named compass point, to set the direction the camera is facing /
 * monitoring. Leaving it unset is allowed but the camera then falls back to
 * the engine's conservative weak-metadata handling, which may warn from
 * either travel direction, so the control makes that trade-off explicit.
 */
@Composable
private fun CameraDirectionPicker(
    selectedBearing: Float?,
    onBearingSelected: (Float?) -> Unit,
    liveHeadingDegrees: Float?
) {
    Column(Modifier.fillMaxWidth()) {

        Text(
            "CAMERA DIRECTION",
            color = CyanGlow,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Which way does this camera face / monitor? This decides which travel direction gets warned. Drag the arrow, tap the dial, or choose a compass point below.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(Modifier.height(14.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(150.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { offset ->
                            val dx = offset.x - size.width / 2f
                            val dy = offset.y - size.height / 2f
                            onBearingSelected((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() + 360f) % 360f)
                        }) { change, _ ->
                            change.consume()
                            val dx = change.position.x - size.width / 2f
                            val dy = change.position.y - size.height / 2f
                            onBearingSelected((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() + 360f) % 360f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val angle =
                                (Math.toDegrees(
                                    atan2(dx.toDouble(), -dy.toDouble())
                                ).toFloat() + 360f) % 360f
                            onBearingSelected(angle)
                        }
                    }
            ) {
                val radius = size.minDimension / 2f - 10.dp.toPx()
                val c = center

                drawCircle(CyanGlow.copy(alpha = .05f), radius, c)
                drawCircle(PremiumLine, radius, c, style = Stroke(1.5.dp.toPx()))

                // Cardinal tick marks (N/E/S/W).
                for (deg in listOf(0f, 90f, 180f, 270f)) {
                    val rad = Math.toRadians(deg.toDouble())
                    val innerR = radius - 10.dp.toPx()
                    val x1 = c.x + innerR * sin(rad).toFloat()
                    val y1 = c.y - innerR * cos(rad).toFloat()
                    val x2 = c.x + radius * sin(rad).toFloat()
                    val y2 = c.y - radius * cos(rad).toFloat()
                    drawLine(TextSecondary, Offset(x1, y1), Offset(x2, y2), 2.dp.toPx())
                }

                if (selectedBearing != null) {
                    val rad = Math.toRadians(selectedBearing.toDouble())
                    val tipX = c.x + radius * .84f * sin(rad).toFloat()
                    val tipY = c.y - radius * .84f * cos(rad).toFloat()
                    drawLine(
                        PremiumAccent,
                        c,
                        Offset(tipX, tipY),
                        4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(PremiumAccent, 7.dp.toPx(), Offset(tipX, tipY))
                }

                drawCircle(CyanGlow, 4.dp.toPx(), c)
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            selectedBearing?.let {
                "Selected: ${it.roundToInt()}° ${compassLabel(it)}"
            } ?: "No direction set — this camera may warn from either travel direction",
            color = if (selectedBearing != null) TextPrimary else WarningAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
                180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
            ).forEach { (deg, label) ->
                val isSelected =
                    selectedBearing != null &&
                        angularDistanceDegrees(selectedBearing, deg) < 12f
                FilterChip(
                    selected = isSelected,
                    onClick = { onBearingSelected(deg) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (liveHeadingDegrees != null) {
                TextButton(onClick = { onBearingSelected(liveHeadingDegrees) }) {
                    Icon(Icons.Default.NearMe, null, Modifier.size(16.dp), tint = CyanGlow)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Use my heading (${liveHeadingDegrees.roundToInt()}°)",
                        color = CyanGlow,
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = { onBearingSelected(null) }) {
                Text("Unknown / clear", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun angularDistanceDegrees(first: Float, second: Float): Float {
    val diff = kotlin.math.abs((first % 360f) - (second % 360f))
    return if (diff > 180f) 360f - diff else diff
}

private fun compassLabel(bearing: Float): String {
    val normalized = ((bearing % 360f) + 360f) % 360f
    val labels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = ((normalized + 22.5f) / 45f).toInt() % 8
    return labels[index]
}

@Composable
private fun AddCameraDialog(
    liveLocation: Location?,
    onDismiss: () -> Unit,
    onSave: (RealCameraType, Int?, Float?) -> Unit
) {

    var selectedType by remember {
        mutableStateOf(
            RealCameraType.SPEED
        )
    }

    var speedLimitText by remember {
        mutableStateOf("")
    }

    var selectedBearing by remember {
        mutableStateOf<Float?>(null)
    }

    val liveHeading =
        if (liveLocation != null && liveLocation.hasBearing() && liveLocation.speed >= 0.8f) {
            liveLocation.bearing
        } else {
            null
        }

    AlertDialog(
        onDismissRequest =
            onDismiss,

        containerColor =
            SurfaceDark,

        shape =
            RoundedCornerShape(
                26.dp
            ),

        title = {

            Column {

                Text(
                    "ADD CAMERA",

                    color =
                        CyanGlow,

                    fontSize =
                        10.sp,

                    fontWeight =
                        FontWeight.Bold,

                    letterSpacing =
                        1.3.sp
                )

                Text(
                    "Current Location",

                    color =
                        TextPrimary,

                    fontSize =
                        23.sp,

                    fontWeight =
                        FontWeight.ExtraBold
                )
            }
        },

        text = {

            Column(Modifier.verticalScroll(rememberScrollState())) {

                Text(
                    "CameraGuard will save the camera at your current GPS position.",

                    color =
                        TextSecondary,

                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(
                        17.dp
                    )
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RealCameraType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName()) }
                        )
                    }
                }

                if (
                    selectedType.usesSpeedWarningDistance()
                ) {

                    Spacer(
                        Modifier.height(
                            15.dp
                        )
                    )

                    OutlinedTextField(
                        value =
                            speedLimitText,

                        onValueChange = {

                            speedLimitText =
                                it.filter {
                                        character ->

                                    character
                                        .isDigit()
                                }
                        },

                        label = {

                            Text(
                                "Speed limit"
                            )
                        },

                        suffix = {

                            Text(
                                "km/h"
                            )
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number
                            ),

                        singleLine =
                            true,

                        shape =
                            RoundedCornerShape(
                                14.dp
                            )
                    )
                }

                Spacer(Modifier.height(20.dp))

                CameraDirectionPicker(
                    selectedBearing = selectedBearing,
                    onBearingSelected = { selectedBearing = it },
                    liveHeadingDegrees = liveHeading
                )
            }
        },

        confirmButton = {

            Button(
                onClick = {

                    onSave(
                        selectedType,

                        if (selectedType.usesSpeedWarningDistance()) {

                            speedLimitText
                                .toIntOrNull()

                        } else {

                            null
                        },

                        selectedBearing
                    )
                },

                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                ElectricBlue
                        )
            ) {

                Text(
                    "SAVE CAMERA",

                    fontWeight =
                        FontWeight.Bold
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    "CANCEL",

                    color =
                        TextSecondary
                )
            }
        }
    )
}

@Composable
private fun CameraEditDialog(
    camera: RealCamera,
    liveLocation: Location?,
    onDismiss: () -> Unit,
    onSave: (RealCameraType, Int?, String?, Float?) -> Unit
) {
    var selectedType by remember(camera.id) { mutableStateOf(camera.type) }
    var speedLimitText by remember(camera.id) { mutableStateOf(camera.speedLimit?.toString() ?: "") }
    var note by remember(camera.id) { mutableStateOf(camera.userNote ?: "") }
    var selectedBearing by remember(camera.id) { mutableStateOf(camera.monitoredBearing) }

    val liveHeading =
        if (liveLocation != null && liveLocation.hasBearing() && liveLocation.speed >= 0.8f) {
            liveLocation.bearing
        } else {
            null
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                if (camera.source == RealCameraSource.MANUAL) "EDIT CAMERA" else "FIX OSM CAMERA",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (camera.source == RealCameraSource.MANUAL)
                        "Changes are permanently stored on this phone."
                    else
                        "The OSM record will be hidden locally and replaced by your verified copy.",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RealCameraType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName()) }
                        )
                    }
                }
                if (selectedType.usesSpeedWarningDistance()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = speedLimitText,
                        onValueChange = { speedLimitText = it.filter(Char::isDigit) },
                        label = { Text("Speed limit") },
                        suffix = { Text("km/h") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text("Note / road (optional)") },
                    maxLines = 2
                )
                Spacer(Modifier.height(20.dp))
                CameraDirectionPicker(
                    selectedBearing = selectedBearing,
                    onBearingSelected = { selectedBearing = it },
                    liveHeadingDegrees = liveHeading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedType,
                        if (selectedType.usesSpeedWarningDistance()) speedLimitText.toIntOrNull() else null,
                        note.trim().takeIf { it.isNotEmpty() },
                        selectedBearing
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text(if (camera.source == RealCameraSource.MANUAL) "SAVE" else "SAVE VERIFIED FIX")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary) }
        }
    )
}

private fun distanceToCamera(
    currentLocation: Location?,
    camera: RealCamera
): Float? {

    if (
        currentLocation ==
        null
    ) {
        return null
    }

    val results =
        FloatArray(
            1
        )

    Location.distanceBetween(
        currentLocation.latitude,
        currentLocation.longitude,
        camera.latitude,
        camera.longitude,
        results
    )

    return results[
        0
    ]
}

private fun formatDistance(
    distanceMeters: Float?
): String {

    if (
        distanceMeters ==
        null
    ) {
        return "--"
    }

    return if (
        distanceMeters <
        1000f
    ) {

        "${distanceMeters.roundToInt()} m"

    } else {

        "%.1f km"
            .format(
                distanceMeters /
                        1000f
            )
    }
}

private fun RealCameraType.premiumAccent(): Color = when (this) {
    RealCameraType.SPEED -> WarningAmber
    RealCameraType.RED_LIGHT -> DangerRed
    RealCameraType.BUS_LANE -> CyanGlow
    RealCameraType.NO_ENTRY -> Color(0xFFFF7278)
    RealCameraType.ZTL -> Color(0xFFF3AE72)
    RealCameraType.AVERAGE_SPEED -> CameraGuardPalette.Accent
    RealCameraType.MOBILE_PHONE -> Color(0xFFF39ADA)
    RealCameraType.OTHER_ENFORCEMENT -> Color(0xFFAABCCD)
}

private fun RealCameraType.premiumIcon(): ImageVector = when (this) {
    RealCameraType.SPEED -> Icons.Default.Speed
    RealCameraType.RED_LIGHT -> Icons.Default.Traffic
    RealCameraType.BUS_LANE -> Icons.Default.DirectionsBus
    RealCameraType.NO_ENTRY -> Icons.Default.DoNotDisturbOn
    RealCameraType.ZTL -> Icons.Default.Lock
    RealCameraType.AVERAGE_SPEED -> Icons.Default.AvTimer
    RealCameraType.MOBILE_PHONE -> Icons.Default.PhoneAndroid
    RealCameraType.OTHER_ENFORCEMENT -> Icons.Default.Shield
}

private fun RealCameraType.shortLabel(): String = when (this) {
    RealCameraType.SPEED -> "Speed camera"
    RealCameraType.RED_LIGHT -> "Red light"
    RealCameraType.BUS_LANE -> "Bus / reserved lane"
    RealCameraType.NO_ENTRY -> "No entry"
    RealCameraType.ZTL -> "ZTL / restricted access"
    RealCameraType.AVERAGE_SPEED -> "Average speed"
    RealCameraType.MOBILE_PHONE -> "Phone / seatbelt"
    RealCameraType.OTHER_ENFORCEMENT -> "Other enforcement"
}

@Composable
private fun AlertPreviewCard(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(24.dp), SurfaceDark, 9.dp), shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        border = BorderStroke(1.dp, lerp(PremiumLine, accent, .4f))) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(lerp(SurfaceDark, accent, .15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Default.PlayCircle, "Test $title", Modifier.padding(start = 10.dp).size(28.dp), tint = accent)
        }
    }
}

// ---- Shared spoken turn-by-turn voice guidance -----------------------------------------
// The Map tab (NavigationScreen, above) has its own long-standing voice/mute implementation
// and is left untouched. Route and HUD previously had no voice guidance and no mute control
// at all - this adds the same "speak upcoming turns, mutable, off by default never silently
// resumes" behaviour to both, reading the same shared NavigationRouteRuntime the Map tab
// already writes to. Each screen owns its own TextToSpeech instance (only one of Map/Route/HUD
// is composed at a time, so there is no overlap) and its own mute state, matching how the Map
// tab already does it; muting on one tab does not (yet) carry over to another.
private class VoiceGuidance(
    val speaker: NavigationVoiceSpeaker,
    val enabled: androidx.compose.runtime.MutableState<Boolean>,
    val ready: androidx.compose.runtime.MutableState<Boolean>
)

@Composable
private fun rememberVoiceGuidance(): VoiceGuidance {
    val context = LocalContext.current
    val ready = remember { mutableStateOf(false) }
    val enabled = rememberSaveable { mutableStateOf(true) }
    val speaker = remember(context) { NavigationVoiceSpeaker(context.applicationContext) { ready.value = it } }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return remember(speaker) { VoiceGuidance(speaker, enabled, ready) }
}

/** Polls the shared route runtime (a plain volatile, not Compose state) and speaks upcoming turns. */
@Composable
private fun SpeakRouteGuidance(voice: VoiceGuidance, active: Boolean, liveLocation: Location?) {
    var route by remember { mutableStateOf(NavigationRouteRuntime.route) }
    var routeRevision by remember { mutableIntStateOf(NavigationRouteRuntime.revision) }
    LaunchedEffect(active) {
        while (active) {
            if (routeRevision != NavigationRouteRuntime.revision) {
                routeRevision = NavigationRouteRuntime.revision
                route = NavigationRouteRuntime.route
            }
            kotlinx.coroutines.delay(700)
        }
    }
    val spokenRouteKey = remember { mutableStateOf("") }
    val spokenMilestones = remember { mutableSetOf<String>() }
    val progressTracker = remember(route) { route?.let { NavigationProgressTracker(it) } }
    val progress = remember(route, liveLocation) { liveLocation?.let { progressTracker?.update(it) } }
    LaunchedEffect(active, routeRevision, route?.active, progress, voice.enabled.value, voice.ready.value, liveLocation) {
        if (!active || route?.active != true || !voice.enabled.value || !voice.ready.value) return@LaunchedEffect
        val loc = liveLocation ?: return@LaunchedEffect
        val fresh = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) in 0L..15_000_000_000L
        if (!fresh || !loc.hasAccuracy() || loc.accuracy > 35f) return@LaunchedEffect
        val current = progress ?: return@LaunchedEffect
        if (current.offRouteMeters > 55.0) return@LaunchedEffect
        val routeKey = routeRevision.toString()
        if (spokenRouteKey.value != routeKey) {
            spokenMilestones.clear()
            spokenRouteKey.value = routeKey
        }
        val instruction = current.nextInstruction.trim()
        if (instruction.isBlank() || instruction == "Continue to destination") return@LaunchedEffect
        val nextMeters = current.nextTurnMeters
        val band = when {
            nextMeters <= 35.0 -> "now"
            nextMeters <= 120.0 -> "near"
            nextMeters <= 450.0 -> "approach"
            else -> null
        } ?: return@LaunchedEffect
        val key = "$instruction:$band"
        if (spokenMilestones.add(key)) {
            val intro = when (band) {
                "now" -> "Now, "
                "near" -> "In ${nextMeters.roundToInt()} meters, "
                else -> "In about ${((nextMeters / 50.0).roundToInt() * 50).coerceAtLeast(50)} meters, "
            }
            voice.speaker.speak(intro + instruction)
        }
    }
    LaunchedEffect(route?.active, voice.enabled.value) {
        if (route?.active != true || !voice.enabled.value) voice.speaker.stop()
    }
}

@Composable
private fun VoiceMuteButton(voice: VoiceGuidance, modifier: Modifier = Modifier.size(44.dp), containerColor: Color = SurfaceDark, tint: Color = CyanGlow, iconSize: androidx.compose.ui.unit.Dp = 20.dp) {
    FloatingActionButton(
        onClick = { voice.enabled.value = !voice.enabled.value; if (!voice.enabled.value) voice.speaker.stop() },
        modifier = modifier.shadow(8.dp, RoundedCornerShape(16.dp), clip = false),
        shape = RoundedCornerShape(16.dp), containerColor = containerColor, contentColor = tint,
        elevation = FloatingActionButtonDefaults.elevation(0.dp)
    ) {
        Icon(if (voice.enabled.value) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            if (voice.enabled.value) "Mute voice guidance" else "Unmute voice guidance", Modifier.size(iconSize))
    }
}

/**
 * Free, no-key live weather (com.boss.cameraguard.data.WeatherRepository / Open-Meteo) for the
 * rider's current GPS position. Polls every few minutes rather than on every location update -
 * weather does not change fast enough to justify it, and it keeps this an informational
 * add-on, never a dependency other logic waits on.
 */
@Composable
private fun rememberCurrentWeather(location: Location?): State<com.boss.cameraguard.data.CurrentWeather?> {
    val weather = remember { mutableStateOf<com.boss.cameraguard.data.CurrentWeather?>(null) }
    val latestLocation by rememberUpdatedState(location)
    LaunchedEffect(location != null) {
        while (true) {
            latestLocation?.let { loc ->
                weather.value = withContext(Dispatchers.IO) { com.boss.cameraguard.data.WeatherRepository.current(loc) } ?: weather.value
            }
            kotlinx.coroutines.delay(10 * 60 * 1000L)
        }
    }
    return weather
}

@Composable
private fun WeatherChip(weather: com.boss.cameraguard.data.CurrentWeather?, modifier: Modifier = Modifier) {
    if (weather == null) return
    Surface(
        modifier = modifier.neumorphicRaised(RoundedCornerShape(14.dp), SurfaceDark, 6.dp),
        color = Color.Transparent, shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(weather.glyph, fontSize = 15.sp)
            Column {
                Text("${weather.temperatureC.roundToInt()}°C", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(weather.summary, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

/** Full-map exploration -> selected place -> Directions preview -> Start navigation. */
@Composable
private fun RouteExploreScreen(modifier: Modifier, location: Location?, speed: Float,
    warning: RealCameraTarget?, settings: AppSettings, sosAlerts: List<SosAlert> = emptyList(),
    ownSosActive: Boolean = false, onSosPressed: () -> Unit = {}, onSosCancelled: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var navigating by rememberSaveable { mutableStateOf(NavigationRouteRuntime.route?.active == true) }
    var selected by remember { mutableStateOf<com.boss.cameraguard.data.PlaceDetailsRepository.Details?>(null) }
    var preview by remember { mutableStateOf<com.boss.cameraguard.data.NavigationRoute?>(null) }
    // Free alternate-route options (Valhalla `alternates`, see RoutePlannerRepository).
    // Index 0 is always the primary/fastest route Valhalla returned.
    var alternateRoutes by remember { mutableStateOf<List<com.boss.cameraguard.data.NavigationRoute>>(emptyList()) }
    var selectedRouteOption by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<RoutePlannerRepository.Place>>(emptyList()) }
    var suggestionsLoading by remember { mutableStateOf(false) }
    var suggestionsError by remember { mutableStateOf<String?>(null) }
    var suggestionsDismissedFor by remember { mutableStateOf<String?>(null) }
    var places by remember { mutableStateOf<List<com.boss.cameraguard.data.PlaceDetailsRepository.Details>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }
    var recenter by remember { mutableIntStateOf(0) }
    var mapLayersOpen by rememberSaveable { mutableStateOf(false) }
    var routeLayers by remember { mutableStateOf(com.boss.cameraguard.map.RouteMapLayers()) }
    val routeVoice = rememberVoiceGuidance()
    SpeakRouteGuidance(routeVoice, active = navigating, liveLocation = location)

    var request by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val preferences = remember { RoutePreferencesStore(context) }
    // Free, on-device Home/Work/saved places and recent-trip history (SavedPlacesRepository).
    var savedHome by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.home(context)) }
    var savedWork by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.work(context)) }
    var savedPlaces by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.savedPlaces(context)) }
    var recentTrips by remember { mutableStateOf(com.boss.cameraguard.data.SavedPlacesRepository.trips(context)) }
    fun select(details: com.boss.cameraguard.data.PlaceDetailsRepository.Details) {
        request++; job?.cancel(); busy=false
        selected=details; preview=null; alternateRoutes=emptyList(); selectedRouteOption=0; places=emptyList(); message=null; follow=false
        query=details.name; searchSuggestions=emptyList(); suggestionsDismissedFor=query; suggestionsError=null
        keyboard?.hide(); focus.clearFocus()
    }
    fun lookup(point: org.maplibre.android.geometry.LatLng, label: String?) {
        select(com.boss.cameraguard.data.PlaceDetailsRepository.Details(label ?: "Dropped pin", point.latitude, point.longitude))
        val token=request; busy=true
        job=scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { com.boss.cameraguard.data.PlaceDetailsRepository.reverse(point.latitude,point.longitude,label) }
                if(token==request) selected=result
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(_:Exception) { if(token==request) message="Details unavailable. You can still route to this pin." }
            finally { if(token==request) busy=false }
        }
    }
    fun search(category:String? = null) {
        if(category==null && query.isBlank()) return
        if(category!=null && location==null) { message="Waiting for GPS to find nearby places."; return }
        request++; val token=request; job?.cancel(); busy=true; message=null; selected=null; preview=null; alternateRoutes=emptyList(); selectedRouteOption=0
        searchSuggestions=emptyList(); suggestionsDismissedFor=query
        keyboard?.hide(); focus.clearFocus()
        job=scope.launch {
            try {
                val found=withContext(Dispatchers.IO) {
                    if(category!=null && location!=null) com.boss.cameraguard.data.PlaceDetailsRepository.nearby(location.latitude,location.longitude,category)
                    else RoutePlannerRepository.searchPlace(query).map { com.boss.cameraguard.data.PlaceDetailsRepository.Details(it.name,it.lat,it.lon) }
                }
                if(token==request) { places=found; if(found.isEmpty()) message="No places found in available map data." }
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(_:Exception) { if(token==request) message="Place search unavailable. Try again." }
            finally { if(token==request) busy=false }
        }
    }
    // Route-tab autocomplete runs separately from route/place requests. Delaying requests
    // avoids restarting the remote geocoder on each keystroke, and cancellation prevents
    // stale responses appearing after the user selects a result or changes the query.
    LaunchedEffect(query, suggestionsDismissedFor) {
        val typed = query.trim()
        if (typed.length < 3 || suggestionsDismissedFor == query || navigating) {
            searchSuggestions = emptyList(); suggestionsLoading = false; suggestionsError = null
            return@LaunchedEffect
        }
        searchSuggestions = emptyList(); suggestionsError = null
        suggestionsLoading = true
        try {
            delay(450)
            searchSuggestions = withContext(Dispatchers.IO) {
                RoutePlannerRepository.suggestPlaces(typed, location)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchSuggestions = emptyList()
            suggestionsError = "Address suggestions unavailable. Try Search or check internet."
        } finally {
            suggestionsLoading = false
        }
    }
    fun selectSuggestion(place: RoutePlannerRepository.Place) {
        select(com.boss.cameraguard.data.PlaceDetailsRepository.Details(place.name, place.lat, place.lon))
    }
    fun directions() {
        val destination=selected ?: return
        val origin=location ?: run { message="Waiting for GPS to calculate directions."; return }
        request++; val token=request; job?.cancel(); busy=true; message=null
        job=scope.launch {
            try {
                val options=withContext(Dispatchers.IO) { RoutePlannerRepository.routeWithAlternates(origin,destination.place(),preferences.load()) }
                if(token==request) { alternateRoutes=options; selectedRouteOption=0; preview=options.firstOrNull(); follow=false }
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(error:Exception) { if(token==request) message="Directions unavailable: ${error.message ?: "network error"}" }
            finally { if(token==request) busy=false }
        }
    }
    var routeOptionsOpen by remember { mutableStateOf(false) }
    var roadAlertsOpen by remember { mutableStateOf(false) }
    var routeOptions by remember { mutableStateOf(preferences.load()) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    fun saveOptions(updated: RoutePreferences) {
        routeOptions=updated; preferences.save(updated)
        // An old preview must never remain startable with newly changed options.
        request++; job?.cancel(); busy=false; preview=null
        if(selected!=null) directions()
    }
    if(navigating) {
        NavigationScreen(modifier, location, speed, emptyList(), emptyList(),
            sosAlerts = sosAlerts, activeCameraTarget = warning, appSettings = settings, visibleCameraTypes = emptySet(),
            fullMap=true, onNavigationFinished={
                (NavigationRouteRuntime.route ?: preview)?.let { finished ->
                    com.boss.cameraguard.data.SavedPlacesRepository.recordTrip(context, finished.destinationName, finished.distanceMeters, finished.durationSeconds)
                    recentTrips = com.boss.cameraguard.data.SavedPlacesRepository.trips(context)
                }
                navigating=false; preview=null; selected=null
            },
            ownSosActive = ownSosActive, onSosPressed = onSosPressed, onSosCancelled = onSosCancelled,
            initialRouteLayers=routeLayers)
        return
    }
    val routeSosRiders = sosAlerts.filter { it.isFresh() }.map { alert ->
        CommunityRider("sos:${alert.uid}", alert.displayName, alert.latitude, alert.longitude, 0.0, 0.0,
            alert.createdAtMillis, "SOS", 0f, "SOS · accident alert", false, true)
    }
    Box(modifier.fillMaxSize().background(AppBackground)) {
        RealMapView(Modifier.fillMaxSize(),location,emptyList(),routeSosRiders,follow,recenter,{follow=false},
            routePoints=preview?.points ?: emptyList(), roadsOnly=false,
            destinationPoint=selected?.let { org.maplibre.android.geometry.LatLng(it.latitude,it.longitude) },
            onPlaceTap={point,name->lookup(point,name)}, overviewPoints=preview?.points ?: selected?.let { listOf(org.maplibre.android.geometry.LatLng(it.latitude,it.longitude)) } ?: emptyList(), routeMapLayers=routeLayers,
            darkTheme=settings.themeMode == AppThemeMode.DARK)
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            Surface(modifier=Modifier.fillMaxWidth().neumorphicRaised(RoundedCornerShape(26.dp), SurfaceDark, 10.dp),color=Color.Transparent,shape=RoundedCornerShape(26.dp)) {
                OutlinedTextField(value=query,onValueChange={query=it; suggestionsDismissedFor=null; message=null},modifier=Modifier.fillMaxWidth(),singleLine=true,
                    placeholder={Text("Search places or address")},leadingIcon={Icon(Icons.Default.Search,null,tint=CyanGlow)},
                    trailingIcon={IconButton(onClick={search()}){Icon(Icons.Default.ArrowForward,"Search",tint=CyanGlow)}},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}),
                    colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TextPrimary,unfocusedTextColor=TextPrimary),shape=RoundedCornerShape(24.dp))
            }
            if (suggestionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color=CyanGlow)
            if (searchSuggestions.isNotEmpty()) {
                Surface(Modifier.fillMaxWidth().heightIn(max=260.dp), color=SurfaceDark,
                    shape=RoundedCornerShape(14.dp), shadowElevation=8.dp) {
                    LazyColumn {
                        items(searchSuggestions) { suggestion ->
                            Row(Modifier.fillMaxWidth().clickable { selectSuggestion(suggestion) }
                                .padding(horizontal=12.dp, vertical=10.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, tint=CyanGlow,
                                    modifier=Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(suggestion.name, color=TextPrimary, fontSize=12.sp,
                                    maxLines=2, overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else if (!suggestionsLoading && query.trim().length >= 3 &&
                suggestionsDismissedFor != query) {
                Text(suggestionsError ?: "No live suggestions. Tap Search to search the full address.",
                    color=TextSecondary, fontSize=11.sp,
                    modifier=Modifier.padding(horizontal=8.dp, vertical=5.dp))
            }
            // Five equally sized category buttons share the full width beneath the search field.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Petrol","Food","Shops","Hotels","Parks").forEach { category ->
                    OutlinedButton(onClick = { search(category) },
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
                        border = BorderStroke(1.dp, PremiumLine),
                        shape = RoundedCornerShape(12.dp)) { Text(category, fontSize = 10.sp, maxLines = 1) }
                }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=CyanGlow)
            message?.let { Surface(color=SurfaceDark,shape=RoundedCornerShape(10.dp)){Text(it,color=TextPrimary,modifier=Modifier.padding(10.dp),fontSize=12.sp)} }
            // Google-Maps-style Home/Work/saved shortcuts and recent-trip history
            // (SavedPlacesRepository, free/on-device). Only shown before a destination is picked.
            if (selected == null && query.isBlank()) {
                if (savedHome != null || savedWork != null || savedPlaces.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedHome?.let { home -> AssistChip(onClick = { select(com.boss.cameraguard.data.PlaceDetailsRepository.Details(home.name, home.lat, home.lon)); directions() },
                            label = { Text("Home", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Home, null, Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark, labelColor = TextPrimary, leadingIconContentColor = CyanGlow)) }
                        savedWork?.let { work -> AssistChip(onClick = { select(com.boss.cameraguard.data.PlaceDetailsRepository.Details(work.name, work.lat, work.lon)); directions() },
                            label = { Text("Work", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Work, null, Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark, labelColor = TextPrimary, leadingIconContentColor = CyanGlow)) }
                        savedPlaces.forEach { place -> AssistChip(onClick = { select(com.boss.cameraguard.data.PlaceDetailsRepository.Details(place.name, place.lat, place.lon)); directions() },
                            label = { Text(place.label.ifBlank { place.name }.take(16), fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(14.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceDark, labelColor = TextPrimary, leadingIconContentColor = CyanGlow)) }
                    }
                }
                if (recentTrips.isNotEmpty()) {
                    Text("RECENT TRIPS", color = PremiumAccent, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    Surface(Modifier.fillMaxWidth().heightIn(max = 220.dp), color = SurfaceDark, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp) {
                        LazyColumn {
                            items(recentTrips.take(6)) { trip ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    // Recent trips only store the destination name/stats, not coordinates
                                    // (kept minimal and privacy-light), so re-search by name to get a
                                    // routable point rather than guessing/persisting one.
                                    query = trip.destinationName; suggestionsDismissedFor = null; search()
                                }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(trip.destinationName, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${"%.1f".format(trip.distanceMeters/1000)} km · ${kotlin.math.ceil(trip.durationSeconds/60).toInt().coerceAtLeast(1)} min", color = TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalAlignment=Alignment.End) {
            Box {
                FloatingActionButton(onClick = { mapLayersOpen = !mapLayersOpen; routeOptionsOpen=false; roadAlertsOpen=false },
                    modifier = Modifier.size(44.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false), shape = RoundedCornerShape(16.dp), containerColor = SurfaceDark, contentColor = CyanGlow, elevation = FloatingActionButtonDefaults.elevation(0.dp)) {
                    Icon(Icons.Default.Layers, "Map layers", Modifier.size(20.dp))
                }
                FreeRouteLayersMenu(expanded = mapLayersOpen, onDismiss = { mapLayersOpen = false }, layers = routeLayers, onLayersChanged = { routeLayers = it })
            }
            FloatingActionButton(onClick={follow=true;recenter++;routeOptionsOpen=false;roadAlertsOpen=false;mapLayersOpen=false},modifier=Modifier.size(44.dp).shadow(8.dp,RoundedCornerShape(16.dp),clip=false),shape=RoundedCornerShape(16.dp),containerColor=SurfaceDark,contentColor=CyanGlow,elevation=FloatingActionButtonDefaults.elevation(0.dp)) {
                Icon(Icons.Default.MyLocation,"My location",Modifier.size(20.dp))
            }
            VoiceMuteButton(routeVoice)
            SosFloatingButton(
                active = ownSosActive,
                enabled = settings.communityModeEnabled && location != null,
                onClick = { if (ownSosActive) onSosCancelled() else onSosPressed() },
                buttonSize = 44.dp
            )
            Box {
                FloatingActionButton(onClick={routeOptionsOpen=!routeOptionsOpen;roadAlertsOpen=false;mapLayersOpen=false},modifier=Modifier.size(44.dp).shadow(8.dp,RoundedCornerShape(16.dp),clip=false),shape=RoundedCornerShape(16.dp),containerColor=SurfaceDark,contentColor=CyanGlow,elevation=FloatingActionButtonDefaults.elevation(0.dp)) { Icon(Icons.Default.Tune,"Route options",Modifier.size(20.dp)) }
                DropdownMenu(expanded=routeOptionsOpen,onDismissRequest={routeOptionsOpen=false}) {
                    MapRoutePreferenceMenuItem("Avoid autostrada",routeOptions.avoidAutostrada){saveOptions(routeOptions.copy(avoidAutostrada=it))}
                    MapRoutePreferenceMenuItem("Avoid tangenziale",routeOptions.avoidTangenziale){saveOptions(routeOptions.copy(avoidTangenziale=it))}
                    MapRoutePreferenceMenuItem("Avoid toll roads",routeOptions.avoidTollRoads){saveOptions(routeOptions.copy(avoidTollRoads=it))}
                }
            }
            Box {
                FloatingActionButton(onClick={roadAlertsOpen=!roadAlertsOpen;routeOptionsOpen=false;mapLayersOpen=false},modifier=Modifier.size(44.dp).shadow(8.dp,RoundedCornerShape(16.dp),clip=false),shape=RoundedCornerShape(16.dp),containerColor=DangerRed,contentColor=Color.White,elevation=FloatingActionButtonDefaults.elevation(0.dp)) { Icon(Icons.Default.Report,"Road alerts",Modifier.size(20.dp)) }
                DropdownMenu(expanded=roadAlertsOpen,onDismissRequest={roadAlertsOpen=false}) {
                    listOf("Road closure","Congestion","Police","Road works","Lane closure","Object on road").forEach { label ->
                        DropdownMenuItem(text={Text(label)},onClick={roadAlertsOpen=false;alertMessage=label})
                    }
                }
            }
        }
        alertMessage?.let { label -> AlertDialog(onDismissRequest={alertMessage=null},title={Text(label)},
            text={Text("This function is under construction. Coming soon.")},
            confirmButton={TextButton(onClick={alertMessage=null}){Text("OK")}}) }
        if(places.isNotEmpty()) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=300.dp),color=SurfaceDark,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),shadowElevation=14.dp) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("Places",color=CyanGlow,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold); IconButton(onClick={places=emptyList()}){Icon(Icons.Default.Close,"Close results",tint=TextPrimary)} }
                LazyColumn { items(places) { item ->
                    Column(Modifier.fillMaxWidth().clickable { select(item) }.padding(12.dp)) {
                        Text(item.name,color=TextPrimary,fontWeight=FontWeight.Bold)
                        Text(item.address.ifBlank { item.category },color=TextSecondary,fontSize=12.sp,maxLines=2)
                    }
                } }
            }
        }
        selected?.let { place ->
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=330.dp),color=SurfaceDark,shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),shadowElevation=16.dp) {
                Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(place.name,color=TextPrimary,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick={request++;job?.cancel();busy=false;selected=null;preview=null;alternateRoutes=emptyList();selectedRouteOption=0}){Icon(Icons.Default.Close,"Close place",tint=TextSecondary)}
                    }
                    Text(place.category,color=CyanGlow,fontSize=12.sp)
                    if(place.address.isNotBlank()) Text(place.address,color=TextSecondary,fontSize=12.sp)
                    place.hours?.let { Text("Hours: $it",color=TextSecondary,fontSize=12.sp) }
                    place.phone?.let { Text("Phone: $it",color=TextSecondary,fontSize=12.sp) }
                    place.website?.let { Text(it,color=TextSecondary,fontSize=12.sp,maxLines=2) }
                    Text("%.5f, %.5f".format(place.latitude,place.longitude),color=TextSecondary,fontSize=11.sp)
                    // Free, on-device Home/Work/bookmark saving (SavedPlacesRepository).
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            com.boss.cameraguard.data.SavedPlacesRepository.setHome(context, place.name, place.latitude, place.longitude)
                            savedHome = com.boss.cameraguard.data.SavedPlacesRepository.home(context)
                            message = "Saved as Home"
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp), shape = RoundedCornerShape(11.dp)) {
                            Icon(Icons.Default.Home, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Home", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            com.boss.cameraguard.data.SavedPlacesRepository.setWork(context, place.name, place.latitude, place.longitude)
                            savedWork = com.boss.cameraguard.data.SavedPlacesRepository.work(context)
                            message = "Saved as Work"
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp), shape = RoundedCornerShape(11.dp)) {
                            Icon(Icons.Default.Work, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Work", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            com.boss.cameraguard.data.SavedPlacesRepository.addSavedPlace(context, place.name, place.latitude, place.longitude)
                            savedPlaces = com.boss.cameraguard.data.SavedPlacesRepository.savedPlaces(context)
                            message = "Place saved"
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp), shape = RoundedCornerShape(11.dp)) {
                            Icon(Icons.Default.Bookmark, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Save", fontSize = 11.sp)
                        }
                    }
                    preview?.let { route ->
                        Text("Your location → ${place.name}",color=TextSecondary,fontSize=12.sp,maxLines=2)
                        Text("${kotlin.math.ceil(route.durationSeconds/60).toInt().coerceAtLeast(1)} min · ${"%.1f".format(route.distanceMeters/1000)} km",color=CyanGlow,fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text("Estimated driving time",color=TextSecondary,fontSize=11.sp)
                        // Free alternate road options (Valhalla `alternates`, no extra service/key).
                        // Only shown when the routing backend actually returned more than one.
                        if (alternateRoutes.size > 1) {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                alternateRoutes.forEachIndexed { index, option ->
                                    val chosen = index == selectedRouteOption
                                    OutlinedButton(
                                        onClick = { selectedRouteOption = index; preview = option },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (chosen) CyanGlow.copy(alpha = .16f) else SurfaceDark, contentColor = if (chosen) CyanGlow else TextSecondary),
                                        border = BorderStroke(1.dp, if (chosen) CyanGlow else PremiumLine),
                                        shape = RoundedCornerShape(11.dp)
                                    ) {
                                        Text(
                                            (if (index == 0) "Fastest" else "Alt ${index + 1}") + " · ${kotlin.math.ceil(option.durationSeconds/60).toInt().coerceAtLeast(1)}m",
                                            fontSize = 11.sp, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick={
                        val route=preview
                        if(route==null) directions() else {
                            NavigationRouteRuntime.updateRoute(route.copy(active=true)); navigating=true
                            keyboard?.hide();focus.clearFocus()
                        }
                    },enabled=!busy,modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=CyanGlow,contentColor=Color.Black)) {
                        Icon(if(preview==null) Icons.Default.Directions else Icons.Default.Navigation,null)
                        Spacer(Modifier.width(8.dp));Text(if(preview==null) "Directions" else "Start",fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        // Identical 82dp warning banner dimensions on Main Map, Route and native HUD.
        warning?.takeIf { target ->
            target.distanceMeters >= 0f &&
                target.distanceMeters <= settings.warningDistanceFor(target.camera.type).toFloat()
        }?.let { target ->
            val accent = target.camera.type.premiumAccent()
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                color = SurfaceDark,
                shape = RoundedCornerShape(17.dp), border = BorderStroke(2.dp, accent),
                shadowElevation = 16.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(target.camera.type.premiumIcon(), null, Modifier.size(33.dp), tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CAMERA WARNING", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(target.camera.type.displayName(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${target.distanceMeters.roundToInt().coerceAtLeast(0)} m", color = Color.White,
                        fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        SmokerMapWordmark(Modifier.align(Alignment.BottomStart).padding(start=10.dp, bottom=7.dp))
    }
}

@Composable
private fun SmokerMapWordmark(modifier: Modifier = Modifier) {
    // A short twist once per five-second cycle; no map camera or recomposition-driven timer.
    val twist = rememberInfiniteTransition(label = "smokers-brand-twist")
    val twistDegrees by twist.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 5000
                0f at 0
                0f at 4200
                72f at 4430
                -12f at 4680
                0f at 4900
                0f at 5000
            },
            repeatMode = RepeatMode.Restart
        ), label = "brand-logo-rotation"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(25.dp).graphicsLayer { rotationY = twistDegrees },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = CyanGlow, modifier = Modifier.fillMaxSize())
            Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFF08111D), modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text("Smoker's", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = TextPrimary, lineHeight = 13.sp)
            Text("MAP", fontSize = 7.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp, color = CyanGlow, lineHeight = 8.sp)
        }
    }
}

/** Free OpenFreeMap vector-layer options; available layers depend on tile coverage/zoom.
 * No fake satellite, live traffic or Street View switches are shown.
 */
@Composable
private fun FreeRouteLayersMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    layers: com.boss.cameraguard.map.RouteMapLayers,
    onLayersChanged: (com.boss.cameraguard.map.RouteMapLayers) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text("FREE ROUTE MAP LAYERS", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = CyanGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        RouteLayerToggle("Buildings", layers.buildings) {
            onLayersChanged(layers.copy(buildings = it))
        }
        RouteLayerToggle("3D buildings (where available)", layers.threeDimensionalBuildings) {
            onLayersChanged(layers.copy(threeDimensionalBuildings = it))
        }
        RouteLayerToggle("Places & POIs", layers.pointsOfInterest) {
            onLayersChanged(layers.copy(pointsOfInterest = it))
        }
        RouteLayerToggle("Transit & railway", layers.transit) {
            onLayersChanged(layers.copy(transit = it))
        }
        RouteLayerToggle("Parks & water", layers.parksAndWater) {
            onLayersChanged(layers.copy(parksAndWater = it))
        }
        Text("Free vector-map data only. Satellite imagery, live traffic and Street View are not available in this mode.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 245.dp),
            color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun RouteLayerToggle(label: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { onChanged(!enabled) },
        trailingIcon = { Checkbox(checked = enabled, onCheckedChange = onChanged) })
}

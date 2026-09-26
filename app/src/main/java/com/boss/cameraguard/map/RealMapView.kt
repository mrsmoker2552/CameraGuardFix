package com.boss.cameraguard.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.boss.cameraguard.data.CommunityRider
import com.boss.cameraguard.data.RealCamera
import com.boss.cameraguard.data.RealCameraType
import com.boss.cameraguard.data.displayName
import com.boss.cameraguard.data.usesSpeedWarningDistance
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory

/** Route-map layer switches use only features present in the currently loaded vector style.
 * They never alter the roads-only Main Map, map annotations or navigation overlays.
 */
data class RouteMapLayers(
    val buildings: Boolean = true,
    val pointsOfInterest: Boolean = true,
    val transit: Boolean = true,
    val parksAndWater: Boolean = true,
    val threeDimensionalBuildings: Boolean = true
)

@Composable
fun RealMapView(
    modifier: Modifier = Modifier,
    liveLocation: Location?,
    realCameras: List<RealCamera>,
    communityRiders: List<CommunityRider> = emptyList(),
    followMode: Boolean,
    recenterRequest: Int,
    onManualMapMove: () -> Unit,
    routePoints: List<LatLng> = emptyList(),
    compactUserMarker: Boolean = false,
    roadsOnly: Boolean = false,
    destinationPoint: LatLng? = null,
    onRiderTap: ((CommunityRider) -> Unit)? = null,
    onPlaceTap: ((LatLng, String?) -> Unit)? = null,
    overviewPoints: List<LatLng> = emptyList(),
    focusRiderUid: String? = null,
    focusRiderRequest: Int = 0,
    nearbyPlaces: List<Pair<LatLng, String>> = emptyList(),
    routeMapLayers: RouteMapLayers = RouteMapLayers(),
    miniMapMode: Boolean = false,
    darkTheme: Boolean = true
) {

    val context =
        LocalContext.current
    latestMapContext = context

    val routeLineHolder = remember { arrayOfNulls<Polyline>(1) }
    val routeBorderHolder = remember { arrayOfNulls<Polyline>(1) }
    val destinationMarkerHolder = remember { arrayOfNulls<Marker>(1) }
    val nearbyMarkers = remember { mutableListOf<Marker>() }
    val destinationIcon = remember { createDestinationPinIcon(context) }

    val cameraMarkers =
        remember {
            mutableListOf<Marker>()
        }

    val riderMarkers =
        remember {
            mutableListOf<Marker>()
        }

    val riderMarkerIds = remember { mutableMapOf<Long, String>() }
    val riderMarkerAnimators = remember { mutableMapOf<String, ValueAnimator>() }
    val latestRiders by rememberUpdatedState(communityRiders)
    val latestUserLocation by rememberUpdatedState(liveLocation)
    val latestRiderTap by rememberUpdatedState(onRiderTap)
    val latestPlaceTap by rememberUpdatedState(onPlaceTap)

    val riderSignatureHolder =
        remember {
            longArrayOf(Long.MIN_VALUE)
        }

    val riderIcon =
        remember {
            createCommunityRiderIcon(context)
        }

    val sosRiderIconCache = remember { mutableMapOf<String, Pair<Icon, Icon>>() }
    var sosBlinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(600)
            sosBlinkOn = !sosBlinkOn
        }
    }

    val riderDirectionalIcons =
        remember {
            createCommunityRiderDirectionalIcons(context)
        }

    val cameraIcons =
        remember {
            RealCameraType.values().associateWith { type ->
                createCameraTypeIcon(context, type)
            }
        }

    val userMarkerHolder =
        remember {
            arrayOfNulls<Marker>(1)
        }

    val userMarkerAnimatorHolder =
        remember {
            arrayOfNulls<ValueAnimator>(1)
        }

    val cameraSignatureHolder =
        remember {
            longArrayOf(Long.MIN_VALUE)
        }

    val userStationaryIcon = remember { createBlueUserDotIcon(context, if (compactUserMarker) 24f else 34f) }
    val userMovingIcons = remember {
        (0 until 24).associateWith { bucket ->
            createBlueUserArrowIcon(context, bucket * 15f, if (compactUserMarker) 32f else 48f)
        }
    }

    val mapView =
        remember {

            MapView(context).apply {
                onCreate(null)
            }
        }

    DisposableEffect(mapView) {

        mapView.onStart()
        mapView.onResume()

        onDispose {

            userMarkerAnimatorHolder[0]?.cancel()
            userMarkerAnimatorHolder[0] = null
            riderMarkerAnimators.values.forEach { it.cancel() }
            riderMarkerAnimators.clear()

            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Ordinary holders: rendering bookkeeping must not trigger Compose updates.
    val lastRenderedLocation = remember { arrayOfNulls<Location>(1) }
    val lastFollowLocation = remember { arrayOfNulls<Location>(1) }
    val lastFollowUpdateMillis = remember { longArrayOf(0L) }
    val wasFollowing = remember { booleanArrayOf(false) }
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // Apply layer visibility in-place; never reset map style on a toggle (prevents blinking).
    LaunchedEffect(readyMap, routeMapLayers, roadsOnly) {
        val map = readyMap ?: return@LaunchedEffect
        if (!roadsOnly) map.style?.let { applyRouteLayerVisibility(it, routeMapLayers) }
    }
    // Theme changes repaint the already-loaded vector style in place. No setStyle() call,
    // so switching Dark/Light does not blank or blink the map.
    LaunchedEffect(readyMap, darkTheme, roadsOnly) {
        val map = readyMap ?: return@LaunchedEffect
        map.style?.let { style ->
            if (roadsOnly) applyRoadsOnlyStyle(style, darkTheme) else {
                applyFullMapTheme(style, darkTheme)
                applyRouteLayerVisibility(style, routeMapLayers)
            }
        }
    }
    val latestManualMove by rememberUpdatedState(onManualMapMove)
    // Async callbacks must render the latest Compose inputs, not factory-time data.
    val latestRender by rememberUpdatedState<(MapLibreMap, Boolean) -> Unit>({ map, force ->
        updateCameraMarkersIfNeeded(map, realCameras, cameraMarkers, cameraIcons, cameraSignatureHolder, force)
        updateRiderMarkersIfNeeded(map, communityRiders, riderDirectionalIcons, riderIcon,
            sosRiderIconCache, sosBlinkOn,
            riderMarkers, riderSignatureHolder, force, riderMarkerIds, riderMarkerAnimators)
        val location = liveLocation
        val previous = lastRenderedLocation[0]
        if (location != null && (force || previous == null ||
            location.latitude != previous.latitude || location.longitude != previous.longitude ||
            location.bearing != previous.bearing || location.speed != previous.speed ||
            location.hasBearing() != previous.hasBearing() || location.hasSpeed() != previous.hasSpeed())) {
            updateUserLocationMarker(map, location, userMarkerHolder, userMarkerAnimatorHolder,
                userStationaryIcon, userMovingIcons, animated = !force, routePoints = routePoints)
            lastRenderedLocation[0] = Location(location)
        }
        if (location != null && followMode) {
            val last = lastFollowLocation[0]
            val firstFix = force || !wasFollowing[0] || last == null
            // Stationary GPS jitter must not repeatedly recenter the map, and a new
            // GPS sample must not restart an unfinished camera animation.
            val moving = location.hasSpeed() && location.speed >= 1.5f
            val headingChanged = last != null && moving && location.hasBearing() &&
                kotlin.math.abs(((location.bearing - last.bearing + 540f) % 360f) - 180f) >= 8f
            val minimumMovementMeters = maxOf(5f,
                if (location.hasAccuracy()) location.accuracy * 1.5f else 5f)
            val moved = last != null && moving && location.distanceTo(last) >= minimumMovementMeters
            val now = android.os.SystemClock.uptimeMillis()
            if (firstFix || ((moved || headingChanged) && now - lastFollowUpdateMillis[0] >= 850L)) {
                // Initial framing sets default zoom; all later follow updates retain
                // the zoom and tilt chosen by the user instead of jumping to 18.2.
                moveToCurrentLocation(map, location, animated = !firstFix,
                    resetFraming = firstFix, miniMapMode = miniMapMode)
                lastFollowLocation[0] = Location(location)
                lastFollowUpdateMillis[0] = now
            }
        }
        wasFollowing[0] = followMode
    })

    // An explicit rider selection moves the camera once; ordinary GPS updates must
    // not immediately pull the map back to the current user. Recenter restores follow.
    LaunchedEffect(readyMap, focusRiderRequest) {
        val map = readyMap ?: return@LaunchedEffect
        if (focusRiderRequest <= 0) return@LaunchedEffect
        val rider = communityRiders.firstOrNull { it.uid == focusRiderUid }
            ?: return@LaunchedEffect // Rider went offline before selection was handled.
        if (!rider.latitude.isFinite() || !rider.longitude.isFinite() ||
            rider.latitude !in -90.0..90.0 || rider.longitude !in -180.0..180.0) return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(rider.latitude, rider.longitude),
                map.cameraPosition.zoom.coerceAtLeast(16.0)), 700
        )
    }

    LaunchedEffect(readyMap, recenterRequest) {
        val map = readyMap ?: return@LaunchedEffect
        if (recenterRequest > 0 && liveLocation != null) {
            moveToCurrentLocation(map, liveLocation, animated = true,
                resetFraming = true, miniMapMode = miniMapMode)
            lastFollowLocation[0] = Location(liveLocation)
            lastFollowUpdateMillis[0] = android.os.SystemClock.uptimeMillis()
            wasFollowing[0] = true
        }
    }

    LaunchedEffect(readyMap, routePoints, destinationPoint) {
        val map = readyMap ?: return@LaunchedEffect
        if (routePoints.size >= 2) {
            // Update both polylines in place to avoid a blank frame between old/new routes.
            val border = routeBorderHolder[0]
            val line = routeLineHolder[0]
            if (border != null && line != null) {
                border.points = routePoints
                line.points = routePoints
                map.updatePolyline(border)
                map.updatePolyline(line)
            } else {
                routeBorderHolder[0] = map.addPolyline(PolylineOptions().addAll(routePoints).color(Color.WHITE).width(if (compactUserMarker) 8f else 11f))
                routeLineHolder[0] = map.addPolyline(PolylineOptions().addAll(routePoints).color(Color.rgb(0, 153, 255))
                    .width(if (compactUserMarker) 6f else 9f))
            }
        } else {
            routeLineHolder[0]?.let { map.removePolyline(it) }
            routeBorderHolder[0]?.let { map.removePolyline(it) }
            routeLineHolder[0] = null
            routeBorderHolder[0] = null
        }
        updateDestinationMarker(map, destinationPoint, destinationMarkerHolder, destinationIcon)
    }

    LaunchedEffect(readyMap, nearbyPlaces) {
        val map = readyMap ?: return@LaunchedEffect
        nearbyMarkers.forEach { map.removeMarker(it) }
        nearbyMarkers.clear()
        nearbyPlaces.forEach { (point, name) ->
            nearbyMarkers += map.addMarker(MarkerOptions().position(point).title(name).snippet("Nearby place"))
        }
    }

    LaunchedEffect(readyMap, overviewPoints) {
        val map = readyMap ?: return@LaunchedEffect
        if (overviewPoints.size == 1) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(overviewPoints.first(), 16.0))
        } else if (overviewPoints.size >= 2) {
            val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().includes(overviewPoints).build()
            val density = context.resources.displayMetrics.density
            val top = minOf((150*density).toInt(), mapView.height / 4)
            val bottom = minOf((260*density).toInt(), mapView.height / 3)
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0,
                (32*density).toInt(), top, (32*density).toInt(), bottom))
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_MOVE -> latestManualMove()
                    }
                    false
                }
                getMapAsync { map ->
                    map.addOnCameraMoveListener {
                        val location = latestUserLocation
                        val marker = userMarkerHolder[0]
                        if (location != null && marker != null && location.hasSpeed() && location.speed >= .8f) {
                            val angle = (if(location.hasBearing()) location.bearing else 0f) - map.cameraPosition.bearing.toFloat()
                            val bucket = (((angle % 360f + 360f) % 360f)/15f).roundToInt()%24
                            val icon = userMovingIcons[bucket] ?: userStationaryIcon
                            if(marker.icon != icon) marker.icon=icon
                        }
                    }
                    map.addOnMapLongClickListener { point ->
                        latestPlaceTap?.invoke(point, null)
                        latestPlaceTap != null
                    }
                    map.addOnMapClickListener { point ->
                        val callback = latestPlaceTap
                        if (callback == null) false else {
                            val pixel = map.projection.toScreenLocation(point)
                            val features = map.queryRenderedFeatures(android.graphics.RectF(pixel.x-18f, pixel.y-18f, pixel.x+18f, pixel.y+18f))
                            val named = features.firstOrNull { it.hasProperty("name") || it.hasProperty("name:latin") }
                            val name = named?.let { feature ->
                                runCatching { if (feature.hasProperty("name")) feature.getStringProperty("name") else feature.getStringProperty("name:latin") }.getOrNull()
                            }
                            if (name != null) {
                                val featurePoint = runCatching {
                                    val geometry = org.json.JSONObject(named?.geometry()?.toJson() ?: "{}")
                                    if (geometry.optString("type") == "Point") {
                                        val coordinates = geometry.getJSONArray("coordinates")
                                        LatLng(coordinates.getDouble(1), coordinates.getDouble(0))
                                    } else point
                                }.getOrDefault(point)
                                callback(featurePoint, name); true
                            } else false
                        }
                    }
                    map.setOnMarkerClickListener { marker ->
                        val uid = riderMarkerIds[marker.id]
                        val rider = latestRiders.firstOrNull { it.uid == uid }
                        if (rider != null && latestRiderTap != null) { latestRiderTap?.invoke(rider); true } else false
                    }
                    map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                        if (roadsOnly) applyRoadsOnlyStyle(style, darkTheme) else {
                            applyFullMapTheme(style, darkTheme)
                            applyRouteLayerVisibility(style, routeMapLayers)
                        }
                        map.uiSettings.isRotateGesturesEnabled = !compactUserMarker && !miniMapMode
                        map.uiSettings.isTiltGesturesEnabled = !compactUserMarker && !miniMapMode
                        map.uiSettings.isZoomGesturesEnabled = !miniMapMode
                        map.uiSettings.isScrollGesturesEnabled = !compactUserMarker && !miniMapMode
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = !miniMapMode
                        // Keep MapLibre attribution visible but make its info circle as compact
                        // as practical and lock it directly after the Smoker's Map footer.
                        if (!miniMapMode) mapView.post {
                            val attributionId = context.resources.getIdentifier("attributionView", "id", context.packageName)
                                .takeIf { it != 0 } ?: context.resources.getIdentifier("attributionView", "id", "org.maplibre.android")
                            if (attributionId != 0) {
                                mapView.findViewById<View>(attributionId)?.let { attribution ->
                                    val d = context.resources.displayMetrics.density
                                    val sizePx = (16f * d).roundToInt()
                                    val lp = (attribution.layoutParams as? FrameLayout.LayoutParams)
                                        ?: FrameLayout.LayoutParams(sizePx, sizePx)
                                    lp.width = sizePx; lp.height = sizePx
                                    lp.gravity = Gravity.BOTTOM or Gravity.START
                                    lp.leftMargin = (91f * d).roundToInt()
                                    lp.bottomMargin = (5f * d).roundToInt()
                                    attribution.layoutParams = lp
                                    attribution.minimumWidth = 0; attribution.minimumHeight = 0
                                    attribution.scaleX = .72f; attribution.scaleY = .72f
                                }
                            }
                        }
                        latestRender(map, true)
                        android.util.Log.i("CameraGuardMap", "Style ready: userMarker=${userMarkerHolder[0] != null}, cameras=${cameraMarkers.size}, riders=${riderMarkers.size}")
                        readyMap = map
                    }
                }
            }
        },
        update = {
            // No annotation operations before style completion.
            readyMap?.let { map -> latestRender(map, false) }
        }
    )
}

private fun updateDestinationMarker(
    map: MapLibreMap,
    destination: LatLng?,
    holder: Array<Marker?>,
    icon: Icon
) {
    holder[0]?.let { runCatching { map.removeMarker(it) } }
    holder[0] = null
    if (destination != null) {
        holder[0] = map.addMarker(
            MarkerOptions().position(destination).title("Destination").snippet("Selected destination").icon(icon)
        )
    }
}

/** Route snapping only when an active route supplies reliable road geometry.
 * Without a route, do not fabricate a road match from map tiles or a nearby parallel road.
 */
private fun snapDisplayLocationToRoute(location: Location, route: List<LatLng>): LatLng {
    val raw = LatLng(location.latitude, location.longitude)
    if (route.size < 2 || (location.hasAccuracy() && location.accuracy > 30f)) return raw
    val latitudeScale = 111_320.0
    val longitudeScale = latitudeScale * kotlin.math.cos(Math.toRadians(location.latitude)).coerceAtLeast(0.01)
    var bestDistance = Double.POSITIVE_INFINITY
    var best = raw
    for (i in 0 until route.size - 1) {
        val a = route[i]; val b = route[i + 1]
        val ax = (a.longitude - raw.longitude) * longitudeScale
        val ay = (a.latitude - raw.latitude) * latitudeScale
        val bx = (b.longitude - raw.longitude) * longitudeScale
        val by = (b.latitude - raw.latitude) * latitudeScale
        val dx = bx - ax; val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 0.01) continue
        val t = (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val x = ax + t * dx; val y = ay + t * dy
        val distance = kotlin.math.hypot(x, y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = LatLng(raw.latitude + y / latitudeScale, raw.longitude + x / longitudeScale)
        }
    }
    val tolerance = if (location.hasAccuracy()) (location.accuracy.toDouble() * 1.5).coerceIn(8.0, 20.0) else 12.0
    return if (bestDistance <= tolerance) best else raw
}

private fun updateUserLocationMarker(
    map: MapLibreMap,
    location: Location?,
    markerHolder: Array<Marker?>,
    animatorHolder: Array<ValueAnimator?>,
    stationaryIcon: Icon,
    movingIcons: Map<Int, Icon>,
    animated: Boolean,
    routePoints: List<LatLng>
) {

    if (location == null) {
        return
    }

    // Display-only route matching. Never feed snapped coordinates to warning or GPS logic.
    val target = snapDisplayLocationToRoute(location, routePoints)

    val moving = location.hasSpeed() && location.speed >= 0.8f
    val bearing = if (location.hasBearing()) location.bearing else 0f
    val relativeBearing = bearing - map.cameraPosition.bearing.toFloat()
    val bucket = (((relativeBearing % 360f + 360f) % 360f) / 15f).roundToInt() % 24
    val selectedIcon = if (moving) movingIcons[bucket] ?: stationaryIcon else stationaryIcon

    val existing =
        markerHolder[0]

    if (existing == null) {

        markerHolder[0] =
            map.addMarker(
                MarkerOptions()
                    .position(target)
                    .title("Your Location")
                    .snippet("CameraGuard live GPS")
                    .icon(selectedIcon)
            )

        return
    }

    if (existing.icon != selectedIcon) runCatching { existing.icon = selectedIcon }
    // Do not cancel an in-flight movement just because a UI recomposition occurred.
    if (existing.position == target && animatorHolder[0]?.isRunning != true) return

    val start =
        existing.position

    val movement =
        distanceBetweenLatLng(
            start,
            target
        )

    if (
        !animated ||
        movement < MIN_MARKER_ANIMATION_DISTANCE_METERS ||
        movement > MAX_MARKER_ANIMATION_DISTANCE_METERS
    ) {

        animatorHolder[0]?.cancel()
        animatorHolder[0] = null
        existing.position = target
        return
    }

    animatorHolder[0]?.cancel()

    val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {

            duration =
                USER_MARKER_ANIMATION_DURATION_MILLIS

            interpolator =
                LinearInterpolator()

            addUpdateListener { valueAnimator ->

                val fraction =
                    valueAnimator.animatedValue as Float

                existing.position =
                    LatLng(
                        start.latitude +
                            (target.latitude - start.latitude) * fraction,
                        start.longitude +
                            (target.longitude - start.longitude) * fraction
                    )
            }
        }

    animatorHolder[0] = animator
    animator.start()
}

private fun distanceBetweenLatLng(
    first: LatLng,
    second: LatLng
): Float {

    val results =
        FloatArray(1)

    Location.distanceBetween(
        first.latitude,
        first.longitude,
        second.latitude,
        second.longitude,
        results
    )

    return results[0]
}

private fun createBlueUserDotIcon(context: Context, sizeDp: Float): Icon {
    val d=context.resources.displayMetrics.density; val size=(sizeDp*d).roundToInt().coerceAtLeast(24)
    val b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888); val c=Canvas(b); val p=Paint(Paint.ANTI_ALIAS_FLAG); val center=size/2f
    p.color=Color.argb(58,24,175,255); c.drawCircle(center,center,center*.98f,p)
    p.color=Color.WHITE; c.drawCircle(center,center,center*.60f,p)
    p.color=Color.rgb(24,175,255); c.drawCircle(center,center,center*.48f,p)
    return IconFactory.getInstance(context).fromBitmap(b)
}

private fun createBlueUserArrowIcon(context: Context, bearingDegrees: Float, sizeDp: Float): Icon {
    val density=context.resources.displayMetrics.density
    val size=(sizeDp*density).roundToInt().coerceAtLeast(32)
    val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val center=size/2f
    paint.shader=android.graphics.RadialGradient(center,center,center,
        intArrayOf(Color.argb(110,0,174,255),Color.TRANSPARENT),null,android.graphics.Shader.TileMode.CLAMP)
    canvas.drawCircle(center,center,center,paint);paint.shader=null
    canvas.save();canvas.rotate(bearingDegrees,center,center)
    val arrow=Path().apply {
        moveTo(center,size*.10f);lineTo(size*.81f,size*.84f)
        quadTo(center,size*.65f,size*.19f,size*.84f);close()
    }
    paint.color=Color.rgb(4,24,45);paint.style=Paint.Style.STROKE;paint.strokeWidth=5*density
    paint.strokeJoin=Paint.Join.ROUND;canvas.drawPath(arrow,paint)
    paint.color=Color.WHITE;paint.strokeWidth=2.4f*density;canvas.drawPath(arrow,paint)
    paint.style=Paint.Style.FILL
    paint.shader=android.graphics.LinearGradient(0f,size*.1f,size.toFloat(),size*.85f,
        intArrayOf(Color.rgb(170,245,255),Color.rgb(0,170,255),Color.rgb(0,65,220)),null,android.graphics.Shader.TileMode.CLAMP)
    canvas.drawPath(arrow,paint);paint.shader=null
    val facet=Path().apply {moveTo(center,size*.15f);lineTo(center,size*.67f);lineTo(size*.25f,size*.77f);close()}
    paint.color=Color.argb(120,215,255,255);canvas.drawPath(facet,paint)
    canvas.restore()
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun createPremiumUserLocationIcon(
    context: Context,
    sizeDp: Float = 58f
): Icon {

    val density =
        context.resources
            .displayMetrics
            .density

    val size =
        (sizeDp * density)
            .toInt()

    val bitmap =
        Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

    val canvas =
        Canvas(bitmap)

    val center =
        size / 2f

    /*
     * Large translucent cyan halo.
     */
    val haloPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.argb(
                    55,
                    0,
                    174,
                    255
                )

            style =
                Paint.Style.FILL
        }

    /*
     * Premium dark outer bezel.
     */
    val bezelPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.rgb(
                    5,
                    18,
                    31
                )

            style =
                Paint.Style.FILL
        }

    val cyanPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.rgb(
                    0,
                    174,
                    255
                )

            style =
                Paint.Style.FILL
        }

    val innerPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.rgb(
                    10,
                    31,
                    48
                )

            style =
                Paint.Style.FILL
        }

    val arrowPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.rgb(24, 175, 255)

            style =
                Paint.Style.FILL
        }

    canvas.drawCircle(
        center,
        center,
        center * 0.98f,
        haloPaint
    )

    canvas.drawCircle(
        center,
        center,
        center * 0.76f,
        bezelPaint
    )

    canvas.drawCircle(
        center,
        center,
        center * 0.63f,
        cyanPaint
    )

    canvas.drawCircle(
        center,
        center,
        center * 0.48f,
        innerPaint
    )

    /*
     * Navigation arrow points toward the top of the screen.
     * In Follow Mode the map itself rotates with travel bearing,
     * so this behaves like a navigation puck.
     */
    val arrow =
        Path().apply {

            moveTo(
                center,
                center * 0.48f
            )

            lineTo(
                center * 1.30f,
                center * 1.42f
            )

            lineTo(
                center,
                center * 1.18f
            )

            lineTo(
                center * 0.70f,
                center * 1.42f
            )

            close()
        }

    canvas.drawPath(
        arrow,
        arrowPaint
    )

    /*
     * Small cyan centre jewel.
     */
    canvas.drawCircle(
        center,
        center * 1.18f,
        center * 0.08f,
        cyanPaint
    )

    return IconFactory
        .getInstance(
            context
        )
        .fromBitmap(
            bitmap
        )
}

private fun moveToCurrentLocation(
    map: MapLibreMap,
    location: Location,
    animated: Boolean,
    resetFraming: Boolean = false,
    miniMapMode: Boolean = false
) {
    // v1.6.5: true GPS-centred framing on both dashboard and route maps.
    // Do not project the camera target ahead of the rider; the blue marker stays
    // at the visual centre whenever follow/recenter is active.
    val heading = if (location.hasBearing() && location.speed > 1.5f) {
        location.bearing.toDouble()
    } else {
        map.cameraPosition.bearing
    }
    val cameraPosition = CameraPosition.Builder()
        .target(LatLng(location.latitude, location.longitude))
        .zoom(if (miniMapMode) 14.2 else if (resetFraming) 18.2 else map.cameraPosition.zoom)
        .bearing(heading)
        .tilt(if (miniMapMode) 0.0 else if (resetFraming) 48.0 else map.cameraPosition.tilt)
        .build()
    if (animated) map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 650)
    else map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
}

/**
 * CameraGuard road-only presentation used by the HUD mini-map and the
 * navigation Map tab.  The remote base style is still responsible for the
 * road geometry/labels, but non-road cartography is suppressed locally so
 * buildings and POIs cannot leak back into these surfaces.
 */
private fun applyRoadsOnlyStyle(style: Style, darkTheme: Boolean) {
    val keepTokens = listOf("road", "street", "highway", "motorway", "trunk", "primary", "secondary", "tertiary", "residential", "service", "path", "transportation", "bridge", "tunnel", "route")
    style.layers.forEach { layer ->
        val id = layer.id.lowercase()
        // SDK annotations carry ALL app markers; never treat them as basemap POIs.
        if (id.startsWith("org.maplibre.annotations.") ||
            id.startsWith("com.mapbox.annotations.") || id.startsWith("cameraguard-")) {
            layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(
                org.maplibre.android.style.layers.Property.VISIBLE))
            return@forEach
        }
        if (layer is org.maplibre.android.style.layers.BackgroundLayer) {
            layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.backgroundColor(if (darkTheme) "#040521" else "#F5F9FE")); return@forEach
        }
        // Footprints, land-use and area fills are not roads, even if a remote
        // style gives their layer a road-related ID (e.g. road-area polygons).
        if (layer is org.maplibre.android.style.layers.FillLayer ||
            layer is org.maplibre.android.style.layers.FillExtrusionLayer ||
            layer is org.maplibre.android.style.layers.RasterLayer ||
            layer is org.maplibre.android.style.layers.CircleLayer ||
            layer is org.maplibre.android.style.layers.HillshadeLayer) {
            layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE))
            return@forEach
        }
        val isRoadLayer = keepTokens.any(id::contains)
        if (!isRoadLayer) {
            layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE)); return@forEach
        }
        // Neutral slate road palette keeps electric blue exclusively for live GPS/route UI.
        if (layer is org.maplibre.android.style.layers.LineLayer) {
            val color = if (darkTheme) {
                when {
                    id.contains("motorway") || id.contains("trunk") -> "#6E8FB8"
                    id.contains("primary") -> "#587DAA"
                    id.contains("secondary") || id.contains("tertiary") -> "#466B98"
                    else -> "#2D527F"
                }
            } else {
                when {
                    id.contains("motorway") || id.contains("trunk") -> "#6F8FB0"
                    id.contains("primary") -> "#88A7C4"
                    id.contains("secondary") || id.contains("tertiary") -> "#A9BED2"
                    else -> "#C9D8E7"
                }
            }
            layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(color))
        }
        if (layer is org.maplibre.android.style.layers.SymbolLayer) {
            layer.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.textColor(if (darkTheme) "#B6C9E0" else "#243A5A"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloColor(if (darkTheme) "#040521" else "#F5F9FE"),
                org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(1.2f)
            )
        }
    }
}

private fun updateCameraMarkersIfNeeded(
    map: MapLibreMap,
    cameras: List<RealCamera>,
    existingMarkers: MutableList<Marker>,
    icons: Map<RealCameraType, Icon>,
    signatureHolder: LongArray,
    force: Boolean
) {

    val signature =
        cameraListSignature(cameras)

    if (!force && signatureHolder[0] == signature) {
        return
    }

    signatureHolder[0] = signature

    updateCameraMarkers(
        map = map,
        cameras = cameras,
        existingMarkers = existingMarkers,
        icons = icons
    )
}

private fun cameraListSignature(
    cameras: List<RealCamera>
): Long {

    var result =
        cameras.size.toLong()

    cameras.forEach { camera ->

        result = result * 31L + camera.id
        result = result * 31L + camera.type.ordinal
        result = result * 31L + camera.source.ordinal
        result = result * 31L + camera.latitude.toBits()
        result = result * 31L + camera.longitude.toBits()
    }

    return result
}

private fun updateCameraMarkers(
    map: MapLibreMap,
    cameras: List<RealCamera>,
    existingMarkers: MutableList<Marker>,
    icons: Map<RealCameraType, Icon>
) {

    existingMarkers.forEach { marker ->

        try {

            map.removeMarker(
                marker
            )

        } catch (
            _: Exception
        ) {
        }
    }

    existingMarkers.clear()

    val displayCameras =
        createPhysicalCameraDisplayList(
            cameras
        )

    displayCameras.forEach { camera ->

        val cameraName = camera.type.displayName()

        val description =
            buildString {

                if (
                    camera.source.name ==
                    "MANUAL"
                ) {

                    append(
                        "Manual camera"
                    )

                    if (
                        camera.speedLimit !=
                        null
                    ) {

                        append(
                            " • ${camera.speedLimit} km/h"
                        )
                    }

                } else {

                    if (camera.type.usesSpeedWarningDistance()) {
                        if (camera.speedLimit != null) append("Limit: ${camera.speedLimit} km/h")
                        else append("Speed limit not mapped")
                    } else {
                        append(camera.type.displayName())
                    }
                }
            }

        try {

            val marker =
                map.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                camera.latitude,
                                camera.longitude
                            )
                        )
                        .title(
                            cameraName
                        )
                        .snippet(
                            description
                        )
                        .icon(icons[camera.type])
                )

            existingMarkers.add(
                marker
            )

        } catch (
            _: Exception
        ) {
        }
    }
}

private fun createCameraTypeIcon(
    context: Context,
    type: RealCameraType
): Icon {
    val width = 88
    val height = 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = width / 2f

    fun stroke(color: Int, w: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
    }

    fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    fun text(value: String, x: Float, y: Float, size: Float, color: Int) {
        fill(color)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        canvas.drawText(value, x, y, paint)
    }

    when (type) {
        RealCameraType.SPEED -> {
            fill(Color.WHITE)
            canvas.drawCircle(cx, 39f, 29f, paint)
            stroke(Color.rgb(220, 38, 38), 9f)
            canvas.drawCircle(cx, 39f, 29f, paint)
            text("50", cx, 48f, 25f, Color.rgb(17, 24, 39))
            stroke(Color.rgb(220, 38, 38), 7f)
            canvas.drawLine(cx - 13f, 82f, cx + 13f, 82f, paint)
        }

        RealCameraType.RED_LIGHT -> {
            fill(Color.rgb(31, 41, 55))
            val body = RectF(cx - 19f, 8f, cx + 19f, 76f)
            canvas.drawRoundRect(body, 9f, 9f, paint)
            stroke(Color.WHITE, 6f)
            canvas.drawRoundRect(body, 9f, 9f, paint)
            fill(Color.rgb(239, 68, 68)); canvas.drawCircle(cx, 24f, 9f, paint)
            fill(Color.rgb(250, 204, 21)); canvas.drawCircle(cx, 42f, 9f, paint)
            fill(Color.rgb(34, 197, 94)); canvas.drawCircle(cx, 60f, 9f, paint)
            stroke(Color.rgb(100, 116, 139), 7f)
            canvas.drawLine(cx, 77f, cx, 91f, paint)
        }

        RealCameraType.BUS_LANE -> {
            fill(Color.rgb(2, 132, 199))
            val box = RectF(8f, 10f, 80f, 78f)
            canvas.drawRoundRect(box, 14f, 14f, paint)
            stroke(Color.WHITE, 6f)
            canvas.drawRoundRect(box, 14f, 14f, paint)
            fill(Color.rgb(224, 242, 254))
            canvas.drawRoundRect(RectF(18f, 20f, 70f, 54f), 6f, 6f, paint)
            fill(Color.rgb(2, 132, 199))
            canvas.drawRect(23f, 25f, 40f, 42f, paint)
            canvas.drawRect(48f, 25f, 65f, 42f, paint)
            fill(Color.WHITE)
            canvas.drawCircle(22f, 66f, 6f, paint)
            canvas.drawCircle(66f, 66f, 6f, paint)
            text("BUS", cx, 91f, 16f, Color.rgb(2, 132, 199))
        }

        RealCameraType.NO_ENTRY -> {
            fill(Color.rgb(220, 38, 38))
            canvas.drawCircle(cx, 42f, 32f, paint)
            stroke(Color.WHITE, 6f)
            canvas.drawCircle(cx, 42f, 32f, paint)
            fill(Color.WHITE)
            canvas.drawRoundRect(RectF(19f, 35f, 69f, 49f), 7f, 7f, paint)
            stroke(Color.rgb(220, 38, 38), 7f)
            canvas.drawLine(cx - 13f, 84f, cx + 13f, 84f, paint)
        }

        RealCameraType.ZTL -> {
            val path = Path().apply {
                moveTo(cx, 6f)
                lineTo(77f, 24f)
                lineTo(77f, 61f)
                lineTo(cx, 79f)
                lineTo(11f, 61f)
                lineTo(11f, 24f)
                close()
            }
            fill(Color.rgb(255, 247, 237)); canvas.drawPath(path, paint)
            stroke(Color.rgb(245, 158, 11), 7f); canvas.drawPath(path, paint)
            text("ZTL", cx, 49f, 24f, Color.rgb(124, 45, 18))
            stroke(Color.rgb(245, 158, 11), 6f)
            canvas.drawLine(cx - 13f, 88f, cx + 13f, 88f, paint)
        }

        RealCameraType.AVERAGE_SPEED -> {
            fill(Color.rgb(109, 40, 217))
            val box = RectF(6f, 18f, 82f, 72f)
            canvas.drawRoundRect(box, 13f, 13f, paint)
            stroke(Color.WHITE, 6f); canvas.drawRoundRect(box, 13f, 13f, paint)
            stroke(Color.WHITE, 5f)
            val left = Path().apply { moveTo(18f, 32f); lineTo(32f, 32f); lineTo(39f, 45f); lineTo(32f, 58f); lineTo(18f, 58f) }
            val right = Path().apply { moveTo(70f, 32f); lineTo(56f, 32f); lineTo(49f, 45f); lineTo(56f, 58f); lineTo(70f, 58f) }
            canvas.drawPath(left, paint); canvas.drawPath(right, paint)
            text("AVG", cx, 90f, 17f, Color.rgb(109, 40, 217))
        }

        RealCameraType.MOBILE_PHONE -> {
            fill(Color.rgb(190, 24, 93))
            val phone = RectF(19f, 5f, 69f, 86f)
            canvas.drawRoundRect(phone, 11f, 11f, paint)
            stroke(Color.WHITE, 6f); canvas.drawRoundRect(phone, 11f, 11f, paint)
            fill(Color.rgb(252, 231, 243))
            canvas.drawRoundRect(RectF(27f, 17f, 61f, 64f), 4f, 4f, paint)
            stroke(Color.rgb(190, 24, 93), 4f)
            val signal = Path().apply { moveTo(31f, 55f); lineTo(39f, 45f); lineTo(46f, 51f); lineTo(57f, 35f) }
            canvas.drawPath(signal, paint)
            fill(Color.WHITE); canvas.drawCircle(cx, 75f, 4f, paint)
        }

        RealCameraType.OTHER_ENFORCEMENT -> {
            val shield = Path().apply {
                moveTo(cx, 5f)
                lineTo(75f, 17f)
                lineTo(75f, 44f)
                cubicTo(75f, 66f, 62f, 80f, cx, 91f)
                cubicTo(26f, 80f, 13f, 66f, 13f, 44f)
                lineTo(13f, 17f)
                close()
            }
            fill(Color.rgb(51, 65, 85)); canvas.drawPath(shield, paint)
            stroke(Color.WHITE, 6f); canvas.drawPath(shield, paint)
            stroke(Color.WHITE, 7f); canvas.drawLine(cx, 25f, cx, 56f, paint)
            fill(Color.WHITE); canvas.drawCircle(cx, 69f, 5f, paint)
        }
    }

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun updateRiderMarkersIfNeeded(
    map: MapLibreMap,
    riders: List<CommunityRider>,
    icons: Map<Int, Icon>,
    plainIcon: Icon,
    sosIconCache: MutableMap<String, Pair<Icon, Icon>>,
    sosBlinkOn: Boolean,
    existingMarkers: MutableList<Marker>,
    signatureHolder: LongArray,
    force: Boolean,
    markerIds: MutableMap<Long, String>,
    animators: MutableMap<String, ValueAnimator>
) {

    val signature =
        riderListSignature(riders) * 31L + if (riders.any { it.sosActive } && sosBlinkOn) 1L else 0L

    if (!force && signatureHolder[0] == signature) return

    signatureHolder[0] = signature

    updateRiderMarkers(
        map = map,
        riders = riders,
        icons = icons,
        plainIcon = plainIcon,
        sosIconCache = sosIconCache,
        sosBlinkOn = sosBlinkOn,
        existingMarkers = existingMarkers,
        markerIds = markerIds,
        animators = animators
    )
}

private fun riderListSignature(
    riders: List<CommunityRider>
): Long {

    var result =
        riders.size.toLong()

    riders.forEach { rider ->
        result = result * 31L + rider.uid.hashCode()
        result = result * 31L + rider.latitude.toBits()
        result = result * 31L + rider.longitude.toBits()
        result = result * 31L + rider.speedKmh.roundToInt()
        result = result * 31L + if (rider.moving) 1L else 0L
        result = result * 31L + (rider.roadName ?: "").hashCode()
        result = result * 31L + rider.displayName.hashCode()
        result = result * 31L + if (rider.sosActive) 1L else 0L
        // Bucketed (not raw) heading, so tiny GPS-noise heading jitter
        // doesn't force a marker rebuild on every single update - only a
        // change big enough to actually change the drawn icon does.
        result = result * 31L + (rider.headingDegrees / RIDER_HEADING_BUCKET_DEGREES).toLong()
    }

    return result
}

/**
 * A rider dot on the map, either a single rider or a small cluster of
 * riders standing close together. Clustering keeps the map readable when
 * many riders overlap instead of stacking dozens of unlabeled pins.
 */
private data class RiderMapCluster(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val count: Int,
    // Only meaningful for a real, single (non-clustered) rider - a group of
    // riders has no single representative direction, so this stays null and
    // the plain non-directional icon is used for it.
    val headingDegrees: Double? = null,
    val speedKmh: Double? = null,
    val roadName: String? = null,
)

private const val RIDER_CLUSTER_RADIUS_METERS = 60.0
private const val MAX_RIDER_MARKERS_ON_MAP = 40

private fun clusterCommunityRidersForMap(
    riders: List<CommunityRider>
): List<RiderMapCluster> {

    val remaining = riders.toMutableList()
    val clusters = mutableListOf<RiderMapCluster>()

    while (remaining.isNotEmpty() && clusters.size < MAX_RIDER_MARKERS_ON_MAP) {

        val seed = remaining.removeAt(0)
        val group = mutableListOf(seed)
        val iterator = remaining.iterator()

        while (iterator.hasNext()) {
            val candidate = iterator.next()
            val distance =
                distanceBetweenLatLng(
                    LatLng(seed.latitude, seed.longitude),
                    LatLng(candidate.latitude, candidate.longitude)
                )
            if (distance <= RIDER_CLUSTER_RADIUS_METERS) {
                group.add(candidate)
                iterator.remove()
            }
        }

        val avgLat = group.sumOf { it.latitude } / group.size
        val avgLon = group.sumOf { it.longitude } / group.size
        val label =
            if (group.size == 1) {
                group[0].displayName
            } else {
                "${group.size} riders"
            }

        clusters.add(
            RiderMapCluster(
                latitude = avgLat,
                longitude = avgLon,
                label = label,
                count = group.size,
                headingDegrees = if (group.size == 1) group[0].headingDegrees else null,
                speedKmh = if (group.size == 1) group[0].speedKmh else null,
                roadName = if (group.size == 1) group[0].roadName else null
            )
        )
    }

    return clusters
}

private fun updateRiderMarkers(
    map: MapLibreMap,
    riders: List<CommunityRider>,
    icons: Map<Int, Icon>,
    plainIcon: Icon,
    sosIconCache: MutableMap<String, Pair<Icon, Icon>>,
    sosBlinkOn: Boolean,
    existingMarkers: MutableList<Marker>,
    markerIds: MutableMap<Long, String>,
    animators: MutableMap<String, ValueAnimator>
) {
    val activeUids = riders.mapTo(mutableSetOf()) { it.uid }
    val iterator = existingMarkers.iterator()
    while (iterator.hasNext()) {
        val marker = iterator.next()
        val uid = markerIds[marker.id]
        if (uid !in activeUids) {
            map.removeMarker(marker)
            markerIds.remove(marker.id)
            animators.remove(uid)?.cancel()
            iterator.remove()
        }
    }
    val byUid = existingMarkers.associateBy { markerIds[it.id] }
    riders.forEach { rider ->
        val label = rider.displayName.ifBlank { "Rider" }
        val road = rider.roadName?.takeIf { it.isNotBlank() } ?: "Road unavailable"
        val speed = if (rider.moving) "Moving" else "----"
        val icon = if (rider.sosActive) {
            val cached = sosIconCache.getOrPut(rider.displayName) {
                createSosRiderIcon(mapViewContext(map), bright = true, name = rider.displayName) to
                    createSosRiderIcon(mapViewContext(map), bright = false, name = rider.displayName)
            }
            if (sosBlinkOn) cached.first else cached.second
        } else if (rider.moving) {
            val relative = (rider.headingDegrees - map.cameraPosition.bearing + 360.0) % 360.0
            val bucket = ((relative / 15.0).roundToInt() * 15) % 360
            icons[bucket] ?: plainIcon
        } else plainIcon
        val target = LatLng(rider.latitude, rider.longitude)
        val snippet = if (rider.sosActive) "ACCIDENT ALERT • exact SOS location" else "$road • $speed"
        runCatching {
            val existing = byUid[rider.uid]
            if (existing == null) {
                val marker = map.addMarker(MarkerOptions().position(target).title(label).snippet(snippet).icon(icon))
                existingMarkers += marker
                markerIds[marker.id] = rider.uid
            } else {
                if (existing.title != label) existing.title = label
                if (existing.snippet != snippet) existing.snippet = snippet
                if (existing.icon != icon) existing.icon = icon

                // Animate between successive presence updates instead of snapping the
                // marker straight to the new position - this is what made another
                // rider's marker appear to "teleport" every ~5-20s update. Same
                // start/stop/cap logic as the current-user marker (updateUserLocationMarker
                // above), just with thresholds suited to a rider updating every few
                // seconds rather than a continuous GPS stream.
                if (existing.position == target) return@forEach
                val start = existing.position
                val movement = distanceBetweenLatLng(start, target)
                if (movement < MIN_RIDER_MARKER_ANIMATION_DISTANCE_METERS ||
                    movement > MAX_RIDER_MARKER_ANIMATION_DISTANCE_METERS
                ) {
                    // Too small to bother animating, or too large to be a plausible
                    // continuation of real movement (a stale/first read, a large
                    // accuracy jump) - snap directly rather than animate through it.
                    animators.remove(rider.uid)?.cancel()
                    existing.position = target
                    return@forEach
                }
                animators.remove(rider.uid)?.cancel()
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = RIDER_MARKER_ANIMATION_DURATION_MILLIS
                    interpolator = LinearInterpolator()
                    addUpdateListener { va ->
                        val fraction = va.animatedValue as Float
                        existing.position = LatLng(
                            start.latitude + (target.latitude - start.latitude) * fraction,
                            start.longitude + (target.longitude - start.longitude) * fraction
                        )
                    }
                }
                animators[rider.uid] = animator
                animator.start()
            }
        }.onFailure { error ->
            android.util.Log.e("CameraGuardMap", "Failed to update rider marker", error)
        }
    }
}

private fun createLabeledCommunityRiderIcon(
    map: MapLibreMap,
    name: String,
    road: String,
    speed: String,
    heading: Double
): Icon {
    val context = mapViewContext(map)
    val density = context.resources.displayMetrics.density
    val w = (190f * density).roundToInt().coerceAtLeast(190)
    val h = (62f * density).roundToInt().coerceAtLeast(62)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = Color.argb(235, 6, 21, 34)
    c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f*density, 16f*density, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f*density; p.color = Color.rgb(34,211,238)
    c.drawRoundRect(RectF(1f,1f,w-1f,h-1f),16f*density,16f*density,p)
    p.style = Paint.Style.FILL
    val cx=24f*density; val cy=31f*density
    p.color=Color.rgb(14,165,233); c.drawCircle(cx,cy,12f*density,p)
    p.color=Color.WHITE; p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.DEFAULT_BOLD; p.textSize=12f*density
    c.drawText("▲",cx,cy+4f*density,p)
    p.textAlign=Paint.Align.LEFT; p.typeface=Typeface.DEFAULT_BOLD; p.textSize=12f*density
    c.drawText(name.take(22),45f*density,23f*density,p)
    p.typeface=Typeface.DEFAULT; p.textSize=9.5f*density; p.color=Color.rgb(186,200,214)
    val sub=(road.take(24)+" · "+speed)
    c.drawText(sub,45f*density,43f*density,p)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

// MapLibreMap does not expose Context publicly; all maps in CameraGuard share
// the application context captured here by the latest RealMapView.
private lateinit var latestMapContext: Context
private fun mapViewContext(map: MapLibreMap): Context = latestMapContext

private fun createSosRiderIcon(context: Context, bright: Boolean, name: String = "Rider"): Icon {
    val d = context.resources.displayMetrics.density
    val w = (230f * d).roundToInt().coerceAtLeast(230)
    val h = (86f * d).roundToInt().coerceAtLeast(86)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = 40f*d; val cy = 45f*d
    p.color = Color.argb(if (bright) 92 else 28, 255, 22, 56); c.drawCircle(cx, cy, 35f*d, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = (if (bright) 4.5f else 2.6f)*d; p.color = Color.argb(if (bright) 255 else 120, 255, 45, 72); c.drawCircle(cx, cy, (if (bright) 26f else 21f)*d, p)
    p.style = Paint.Style.FILL; p.color = Color.rgb(220, 20, 52); c.drawCircle(cx, cy, 14f*d, p)
    p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 9.5f*d; c.drawText("SOS", cx, cy+3.5f*d, p)
    p.textAlign = Paint.Align.LEFT; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 13f*d; p.color = Color.WHITE
    c.drawText(name.ifBlank { "Rider" }.take(22), 82f*d, 36f*d, p)
    p.textSize = 9.5f*d; p.color = Color.rgb(255, 113, 132); c.drawText("ACCIDENT ALERT", 82f*d, 55f*d, p)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun createDestinationPinIcon(context: Context): Icon {
    val d=context.resources.displayMetrics.density
    val w=(42*d).roundToInt(); val h=(54*d).roundToInt()
    val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); val c=Canvas(b); val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val cx=w/2f; val cy=19*d
    p.setShadowLayer(5*d, 0f, 3*d, Color.argb(175,255,56,75))
    p.color=Color.rgb(255,76,91); c.drawCircle(cx,cy,14*d,p)
    p.clearShadowLayer()
    p.style=Paint.Style.STROKE; p.strokeWidth=2*d; p.color=Color.WHITE; c.drawCircle(cx,cy,13*d,p); p.style=Paint.Style.FILL
    val path=Path().apply { moveTo(cx-9*d,cy+8*d); lineTo(cx, h-3*d); lineTo(cx+9*d,cy+8*d); close() }
    c.drawPath(path,p); p.color=Color.WHITE; c.drawCircle(cx,cy,5*d,p)
    return IconFactory.getInstance(context).fromBitmap(b)
}

private const val RIDER_HEADING_BUCKET_DEGREES = 30



/**
 * Modern blue "current location" style rider marker: soft outer glow,
 * solid blue centre, and a small directional cone pointing toward
 * headingDegrees. One bitmap is pre-rendered per 30-degree bucket (12
 * total) and cached for the composable's lifetime, so real per-rider
 * heading is reflected on the map without rendering a bitmap on every
 * location update.
 */
private fun createCommunityRiderDirectionalIcons(
    context: Context
): Map<Int, Icon> {
    val density = context.resources.displayMetrics.density
    val size = (40f * density).toInt()
    val result = mutableMapOf<Int, Icon>()

    var bucket = 0
    while (bucket < 360) {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 64, 170, 255)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(8, 20, 35)
            style = Paint.Style.FILL
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(66, 165, 245)
            style = Paint.Style.FILL
        }
        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 200, 255)
            style = Paint.Style.FILL
        }

        canvas.drawCircle(center, center, center, glowPaint)
        canvas.drawCircle(center, center, center * 0.52f, borderPaint)
        canvas.drawCircle(center, center, center * 0.40f, dotPaint)

        canvas.save()
        canvas.rotate(bucket.toFloat(), center, center)
        val cone = android.graphics.Path().apply {
            moveTo(center, center - center * 0.92f)
            lineTo(center - center * 0.24f, center - center * 0.40f)
            lineTo(center + center * 0.24f, center - center * 0.40f)
            close()
        }
        canvas.drawPath(cone, conePaint)
        canvas.restore()

        result[bucket] = IconFactory.getInstance(context).fromBitmap(bitmap)
        bucket += RIDER_HEADING_BUCKET_DEGREES
    }

    return result
}

/**
 * Plain (non-directional) blue rider marker, used for multi-rider clusters
 * where no single heading is representative of the group. Individual,
 * single riders instead use the directional bucketed icons from
 * createCommunityRiderDirectionalIcons below.
 */
private fun createCommunityRiderIcon(
    context: Context
): Icon {

    val density =
        context.resources.displayMetrics.density

    val size =
        (34f * density).toInt()

    val bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    val canvas =
        Canvas(bitmap)

    val center =
        size / 2f

    val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 64, 170, 255)
            style = Paint.Style.FILL
        }

    val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(8, 20, 35)
            style = Paint.Style.FILL
        }

    val dotPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(66, 165, 245)
            style = Paint.Style.FILL
        }

    canvas.drawCircle(center, center, center, glowPaint)
    canvas.drawCircle(center, center, center * 0.62f, borderPaint)
    canvas.drawCircle(center, center, center * 0.46f, dotPaint)

    return IconFactory
        .getInstance(context)
        .fromBitmap(bitmap)
}

private fun createPhysicalCameraDisplayList(
    cameras: List<RealCamera>
): List<RealCamera> {

    if (
        cameras.isEmpty()
    ) {
        return emptyList()
    }

    val result =
        mutableListOf<RealCamera>()

    cameras.forEach { candidate ->

        if (
            candidate.source.name ==
            "MANUAL"
        ) {

            result.add(
                candidate
            )

            return@forEach
        }

        val existingIndex =
            result.indexOfFirst { existing ->

                if (
                    existing.source.name ==
                    "MANUAL"
                ) {

                    false

                } else if (
                    existing.type !=
                    candidate.type
                ) {

                    false

                } else {

                    distanceBetweenCameras(
                        first =
                            existing,

                        second =
                            candidate
                    ) <=
                            MAP_DUPLICATE_DISTANCE_METERS
                }
            }

        if (
            existingIndex ==
            -1
        ) {

            result.add(
                candidate
            )

        } else {

            val existing =
                result[
                    existingIndex
                ]

            result[
                existingIndex
            ] =
                chooseBetterDisplayCamera(
                    first =
                        existing,

                    second =
                        candidate
                )
        }
    }

    return result
}

private fun distanceBetweenCameras(
    first: RealCamera,
    second: RealCamera
): Float {

    val results =
        FloatArray(1)

    Location.distanceBetween(
        first.latitude,
        first.longitude,
        second.latitude,
        second.longitude,
        results
    )

    return results[0]
}

private fun chooseBetterDisplayCamera(
    first: RealCamera,
    second: RealCamera
): RealCamera {

    return if (
        cameraDisplayScore(
            second
        ) >
        cameraDisplayScore(
            first
        )
    ) {

        second

    } else {

        first
    }
}

private fun cameraDisplayScore(
    camera: RealCamera
): Int {

    var score =
        0

    if (
        camera.monitoredBearing !=
        null
    ) {
        score += 4
    }

    if (
        camera.speedLimit !=
        null
    ) {
        score += 2
    }

    if (
        !camera.direction
            .isNullOrBlank()
    ) {
        score += 1
    }

    return score
}

private const val
        MAP_DUPLICATE_DISTANCE_METERS =
    20f
private const val MIN_MARKER_ANIMATION_DISTANCE_METERS =
    0.7f

private const val MAX_MARKER_ANIMATION_DISTANCE_METERS =
    120f

// Community rider markers update far less often than the continuous GPS-driven user
// marker above (every few seconds via a Firebase listener, not every location callback),
// so they get their own, more generous animation window/thresholds.
private const val MIN_RIDER_MARKER_ANIMATION_DISTANCE_METERS = 1f
private const val MAX_RIDER_MARKER_ANIMATION_DISTANCE_METERS = 300f
private const val RIDER_MARKER_ANIMATION_DURATION_MILLIS = 900L

private const val USER_MARKER_ANIMATION_DURATION_MILLIS =
    900L

private const val MAP_FOLLOW_ANIMATION_DURATION_MILLIS =
    900L

/**
 * Route/Explore cartography. Main Map (roadsOnly=true) uses a completely separate
 * style function. Change paint properties only: retain all tile sources, layer
 * visibility, zoom ranges, filters, icons, route lines and interactive overlays.
 */
private fun applyFullMapTheme(style: Style, darkTheme: Boolean) {
    style.layers.forEach { layer ->
        val id = layer.id.lowercase()
        // App-added symbols/routes are rendered above the base style and must
        // keep their warning, position and navigation colors unchanged.
        if (id.contains("annotations") || id.startsWith("cameraguard-")) return@forEach
        when (layer) {
            is org.maplibre.android.style.layers.BackgroundLayer ->
                layer.setProperties(PropertyFactory.backgroundColor(if (darkTheme) "#0B1420" else "#F5F8FC"))
            is org.maplibre.android.style.layers.FillLayer -> {
                val color = when {
                    id.contains("water") -> if (darkTheme) "#12394E" else "#CFEAF5"
                    id.contains("park") || id.contains("wood") || id.contains("forest") -> if (darkTheme) "#173F37" else "#DCEEDB"
                    id.contains("grass") || id.contains("farm") || id.contains("landcover") -> if (darkTheme) "#243B36" else "#E7F1E2"
                    id.contains("building") -> if (darkTheme) "#33475C" else "#D7DEE6"
                    id.contains("industrial") -> if (darkTheme) "#2B3746" else "#E3E7EC"
                    id.contains("residential") -> if (darkTheme) "#1C2B3A" else "#EEF2F6"
                    id.contains("hospital") || id.contains("school") -> if (darkTheme) "#283D4C" else "#E6EDF4"
                    else -> if (darkTheme) "#1B2A39" else "#F0F3F6"
                }
                layer.setProperties(PropertyFactory.fillColor(color))
            }
            is org.maplibre.android.style.layers.LineLayer -> {
                val color = when {
                    id.contains("water") || id.contains("river") -> "#36738E"
                    id.contains("rail") || id.contains("transit") -> "#61758C"
                    id.contains("motorway") || id.contains("trunk") -> "#A5B7C7"
                    id.contains("primary") -> "#8CA7BF"
                    id.contains("secondary") || id.contains("tertiary") -> "#7794AE"
                    id.contains("street") || id.contains("road") || id.contains("transportation") -> "#617F9A"
                    id.contains("building") -> "#53677B"
                    id.contains("boundary") -> "#40576B"
                    else -> "#4C667C"
                }
                layer.setProperties(PropertyFactory.lineColor(color))
            }
            is org.maplibre.android.style.layers.FillExtrusionLayer -> {
                if (id.contains("building")) {
                    layer.setProperties(PropertyFactory.fillExtrusionColor(if (darkTheme) "#425970" else "#CAD4DE"))
                }
            }
            is org.maplibre.android.style.layers.SymbolLayer -> {
                val color = when {
                    id.contains("poi") || id.contains("amenity") -> if (darkTheme) "#A9E4D5" else "#245D52"
                    id.contains("road") || id.contains("street") || id.contains("transportation") -> if (darkTheme) "#F2F5F9" else "#253646"
                    id.contains("place") || id.contains("settlement") -> if (darkTheme) "#E3EAF2" else "#263746"
                    id.contains("water") -> if (darkTheme) "#8FCBDF" else "#2B6F86"
                    else -> if (darkTheme) "#CCD9E6" else "#3F5365"
                }
                layer.setProperties(
                    PropertyFactory.textColor(color),
                    PropertyFactory.textHaloColor(if (darkTheme) "#101C2B" else "#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.4f)
                )
            }
        }
    }
}

/** Existing OpenFreeMap vector layers only; no invented traffic, satellite or street-view data. */
private fun applyRouteLayerVisibility(style: Style, settings: RouteMapLayers) {
    style.layers.forEach { layer ->
        val id = layer.id.lowercase()
        if (id.contains("annotations") || id.startsWith("cameraguard-")) return@forEach
        val enabled = when {
            layer is org.maplibre.android.style.layers.FillExtrusionLayer && id.contains("building") ->
                settings.buildings && settings.threeDimensionalBuildings
            id.contains("building") -> settings.buildings
            id.contains("poi") || id.contains("amenity") -> settings.pointsOfInterest
            id.contains("transit") || id.contains("rail") -> settings.transit
            id.contains("park") || id.contains("landcover") || id.contains("water") || id.contains("river") ->
                settings.parksAndWater
            else -> return@forEach
        }
        layer.setProperties(PropertyFactory.visibility(if (enabled) org.maplibre.android.style.layers.Property.VISIBLE
            else org.maplibre.android.style.layers.Property.NONE))
    }
}

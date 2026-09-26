package com.boss.cameraguard.alerts

import android.location.Location
import com.boss.cameraguard.data.RealCamera
import com.boss.cameraguard.data.RealCameraSource
import com.boss.cameraguard.data.RealCameraType
import com.boss.cameraguard.data.AppSettings
import com.boss.cameraguard.data.usesSpeedWarningDistance
import com.boss.cameraguard.data.RoadPoint
import com.boss.cameraguard.data.DriveDiagnosticStore
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * CameraGuard warning engine.
 *
 * Design goals:
 * 1. A camera on a different/cross/parallel road must not become eligible
 *    only because it is geographically close.
 * 2. Two cameras on the same road within a short distance are treated as a
 *    directional pair. The camera encountered FIRST in the rider's current
 *    travel direction owns that approach; the second member is suppressed.
 * 3. Pair ownership does not flip immediately after passing the first camera.
 *    It changes only after a confirmed U-turn / travel-direction reversal.
 * 4. A single camera uses OSM approach geometry / monitored direction when
 *    available, with a conservative trajectory fallback when metadata is weak.
 * 5. Warning distances are user-configurable; defaults remain 300 m (speed) and 150 m (red light).
 */
data class RealCameraTarget(
    val camera: RealCamera,
    val distanceMeters: Float,
    val bearingToCamera: Float,
    val headingDifference: Float
)

class RealCameraWarningEngine {

    private data class WarnedState(
        val latitude: Double,
        val longitude: Double,
        var enteredRearmRadius: Boolean
    )

    private data class SegmentMatch(
        val distanceMeters: Float,
        val segmentBearing: Float,
        val approachBearing: Float
    )

    private data class CameraGeometry(
        val distanceMeters: Float,
        val bearingToCamera: Float,
        val headingDifference: Float,
        val forwardMeters: Float,
        val lateralMeters: Float
    )

    /**
     * Locked ownership for a close camera pair/cluster during one travel
     * direction.  Once locked, only ownerKey may warn.  This prevents the
     * opposite-facing camera from warning immediately after the rider passes
     * the first one.
     */
    private data class PairOwnership(
        val memberKeys: Set<String>,
        var ownerKey: String,
        val lockLatitude: Double,
        val lockLongitude: Double,
        var referenceHeading: Float,
        var reversalVotes: Int = 0,
        var createdAtMillis: Long = System.currentTimeMillis()
    )

    private val warnedCameras =
        mutableMapOf<String, WarnedState>()

    private val pairOwnerships =
        mutableListOf<PairOwnership>()

    private var lastReliableHeading: Float? = null
    private var previousMovementLocation: Location? = null

    /*
     * The most recently selected/monitored target key. Used to keep a pair
     * lock "sticky" to whichever camera the rider is actually approaching,
     * instead of letting a same-tick geometry snapshot silently hand
     * ownership to the untracked sibling camera (see establishPairOwnerships).
     */
    private var lastSelectedKey: String? = null

    fun findRelevantCamera(
        location: Location,
        cameras: List<RealCamera>
    ): RealCameraTarget? {

        if (cameras.isEmpty()) {
            return null
        }

        val heading =
            resolveTravelHeading(location)
                ?: run {
                    DriveDiagnosticStore.log(
                        "ENGINE",
                        "no_reliable_heading lat=${location.latitude} lon=${location.longitude} speed=${location.speed} accuracy=${location.accuracy}"
                    )
                    return null
                }

        updatePairOwnershipsForMovement(
            location = location,
            heading = heading
        )

        updateWarnedCameraStates(location)

        val geometryByKey =
            cameras.associate { camera ->
                cameraKey(camera) to
                    calculateGeometry(
                        location = location,
                        heading = heading,
                        camera = camera
                    )
            }

        /*
         * Detect close same-road camera pairs BEFORE directional filtering.
         * This is the key difference from the old engine.  Previously the
         * second camera could become eligible only after the first camera was
         * passed, so the engine never knew they were a pair.  We now establish
         * pair ownership from road geometry and physical ordering first.
         */
        establishPairOwnerships(
            location = location,
            heading = heading,
            cameras = cameras,
            geometryByKey = geometryByKey
        )

        val candidates =
            cameras.mapNotNull { camera ->

                val key = cameraKey(camera)

                val geometry =
                    geometryByKey[key]
                        ?: return@mapNotNull null

                val pairOwnership =
                    pairOwnershipFor(key)

                if (
                    pairOwnership != null &&
                    pairOwnership.ownerKey != key
                ) {
                    return@mapNotNull null
                }

                buildEligibleTarget(
                    location = location,
                    heading = heading,
                    camera = camera,
                    geometry = geometry,
                    isPairOwner = pairOwnership != null
                )
            }

        /*
         * Prefer the camera with the smallest positive distance along the
         * current travel axis.  This is more deterministic than pure radial
         * distance on multi-lane roads and junctions.
         */
        val selected = candidates.minWithOrNull(
            compareBy<RealCameraTarget> { target ->
                val geometry =
                    geometryByKey[cameraKey(target.camera)]

                geometry
                    ?.forwardMeters
                    ?.takeIf { it >= -PASSED_TOLERANCE_METERS }
                    ?: Float.MAX_VALUE
            }.thenBy {
                it.distanceMeters
            }.thenBy {
                it.headingDifference
            }
        )

        if (selected != null) {
            val g = geometryByKey[cameraKey(selected.camera)]
            DriveDiagnosticStore.log(
                "TARGET",
                "selected key=${cameraKey(selected.camera)} dist=${selected.distanceMeters.toInt()}m heading=${heading.toInt()} bearing=${selected.bearingToCamera.toInt()} diff=${selected.headingDifference.toInt()} forward=${g?.forwardMeters?.toInt()} lateral=${g?.lateralMeters?.toInt()} path=${selected.camera.approachPath.size} monitored=${selected.camera.monitoredBearing}"
            )
        } else {
            val near = geometryByKey.entries
                .filter { it.value.distanceMeters <= 400f }
                .sortedBy { it.value.distanceMeters }
                .take(4)
                .joinToString(";") { (key, g) ->
                    "$key:${g.distanceMeters.toInt()}m/f${g.forwardMeters.toInt()}/l${g.lateralMeters.toInt()}/d${g.headingDifference.toInt()}"
                }
            if (near.isNotBlank()) {
                DriveDiagnosticStore.log("TARGET", "none heading=${heading.toInt()} near=$near")
            }
        }

        /*
         * Preserve the last real selection across brief "none" gaps (for
         * example the single tick where a pair lock is being established)
         * so establishPairOwnerships can keep ownership on the camera that
         * was actually being approached. Only overwritten by a genuine new
         * selection, never cleared just because this tick had no candidate.
         */
        if (selected != null) {
            lastSelectedKey = cameraKey(selected.camera)
        }

        return selected
    }

    fun shouldWarn(
        target: RealCameraTarget,
        settings: AppSettings
    ): Boolean {

        val warningDistance = settings.warningDistanceFor(target.camera.type).toFloat()

        if (target.distanceMeters > warningDistance) {
            DriveDiagnosticStore.log(
                "WARN",
                "not_in_zone key=${cameraKey(target.camera)} dist=${target.distanceMeters.toInt()} limit=${warningDistance.toInt()}"
            )
            return false
        }

        val key =
            cameraKey(target.camera)

        val ownership =
            pairOwnershipFor(key)

        if (
            ownership != null &&
            ownership.ownerKey != key
        ) {
            DriveDiagnosticStore.log("WARN", "pair_suppressed key=$key owner=${ownership.ownerKey}")
            return false
        }

        if (warnedCameras.containsKey(key)) {
            DriveDiagnosticStore.log("WARN", "already_warned key=$key dist=${target.distanceMeters.toInt()}")
            return false
        }

        warnedCameras[key] =
            WarnedState(
                latitude = target.camera.latitude,
                longitude = target.camera.longitude,
                enteredRearmRadius = target.distanceMeters < CAMERA_REARM_DISTANCE_METERS
            )

        DriveDiagnosticStore.log(
            "WARN",
            "FIRED key=$key type=${target.camera.type} dist=${target.distanceMeters.toInt()} path=${target.camera.approachPath.size} monitored=${target.camera.monitoredBearing}"
        )
        return true
    }

    private fun matchesMonitoredDirection(camera: RealCamera, heading: Float): Boolean {
        val monitored = camera.monitoredBearing ?: return true
        return angleDifference(heading, monitored) <= MAX_MONITORED_DIRECTION_DIFFERENCE_DEGREES
    }

    private fun buildEligibleTarget(
        location: Location,
        heading: Float,
        camera: RealCamera,
        geometry: CameraGeometry,
        isPairOwner: Boolean
    ): RealCameraTarget? {

        if (
            geometry.distanceMeters >
            MAX_TARGET_DISTANCE_METERS
        ) {
            return null
        }

        /* Camera must still be generally in front of the rider. */
        if (
            geometry.forwardMeters <
            -PASSED_TOLERANCE_METERS
        ) {
            return null
        }

        if (!matchesMonitoredDirection(camera, heading)) return null

        val accuracy =
            if (location.hasAccuracy()) {
                location.accuracy
            } else {
                15f
            }

        if (camera.approachPath.size >= 2) {

            val pathMatch =
                matchApproachPath(
                    location = location,
                    path = camera.approachPath,
                    camera = camera
                ) ?: return null

            if (
                pathMatch.distanceMeters >
                mappedRoadWidth(accuracy)
            ) {
                return null
            }

            /*
             * First prove that the rider is travelling ALONG this road.  This
             * compares road AXIS rather than direction, so a close opposing
             * camera on the same physical road remains visible to the pair
             * classifier while a cross-road camera is rejected.
             */
            val roadAxisDifference =
                axisDifference(
                    heading,
                    pathMatch.segmentBearing
                )

            if (
                roadAxisDifference >
                MAX_ROAD_AXIS_DIFFERENCE_DEGREES
            ) {
                return null
            }

            if (angleDifference(heading, pathMatch.approachBearing) >
                MAX_SINGLE_CAMERA_DIRECTION_DIFFERENCE_DEGREES) return null

            if (isPairOwner) {
                /*
                 * Explicit direction and approach checks above apply to pairs too.
                 * Use the wider paired forward-angle tolerance only after those gates.
                 */
                if (
                    geometry.headingDifference >
                    MAX_FORWARD_ANGLE_PAIR_DEGREES
                ) {
                    return null
                }

            } else {
                /*
                 * Single camera: use the OSM approach direction when it is
                 * available.  approachBearing is derived from the local road
                 * segment toward the enforcement device.
                 */
                val approachDifference =
                    angleDifference(
                        heading,
                        pathMatch.approachBearing
                    )

                if (
                    approachDifference >
                    MAX_SINGLE_CAMERA_DIRECTION_DIFFERENCE_DEGREES
                ) {
                    return null
                }

                camera.monitoredBearing?.let { monitored ->
                    if (
                        angleDifference(
                            heading,
                            monitored
                        ) >
                        MAX_MONITORED_DIRECTION_DIFFERENCE_DEGREES
                    ) {
                        return null
                    }
                }

                if (
                    geometry.headingDifference >
                    MAX_FORWARD_ANGLE_MAPPED_SINGLE_DEGREES
                ) {
                    return null
                }
            }

        } else if (camera.monitoredBearing != null) {

            if (
                angleDifference(
                    heading,
                    camera.monitoredBearing
                ) >
                MAX_MONITORED_DIRECTION_DIFFERENCE_DEGREES
            ) {
                return null
            }

            if (
                geometry.lateralMeters >
                directionalCorridorWidth(accuracy)
            ) {
                return null
            }

            if (
                geometry.headingDifference >
                MAX_FORWARD_ANGLE_DEGREES
            ) {
                return null
            }

        } else {

            /*
             * Weak-metadata camera.  Be deliberately conservative so a
             * camera from another road does not trigger just because it falls
             * inside the radial warning circle.
             */
            if (
                geometry.headingDifference >
                MAX_UNKNOWN_CAMERA_FORWARD_ANGLE_DEGREES
            ) {
                return null
            }

            val genericAllowed =
                genericCorridorWidth(accuracy)

            val closeStrongApproach =
                geometry.distanceMeters <= 180f &&
                    geometry.headingDifference <= 24f &&
                    geometry.lateralMeters <=
                        CLOSE_STRONG_APPROACH_LATERAL_METERS

            if (
                geometry.lateralMeters >
                    genericAllowed &&
                !closeStrongApproach
            ) {
                return null
            }
        }

        return RealCameraTarget(
            camera = camera,
            distanceMeters = geometry.distanceMeters,
            bearingToCamera = geometry.bearingToCamera,
            headingDifference = geometry.headingDifference
        )
    }

    private fun establishPairOwnerships(
        location: Location,
        heading: Float,
        cameras: List<RealCamera>,
        geometryByKey: Map<String, CameraGeometry>
    ) {

        val nearby =
            cameras.filter { camera ->
                val geometry =
                    geometryByKey[cameraKey(camera)]
                        ?: return@filter false

                geometry.distanceMeters <=
                    PAIR_DISCOVERY_RADIUS_FROM_RIDER_METERS
            }

        for (firstIndex in nearby.indices) {

            val first = nearby[firstIndex]
            val firstKey = cameraKey(first)

            for (
                secondIndex in
                (firstIndex + 1) until nearby.size
            ) {

                val second = nearby[secondIndex]
                val secondKey = cameraKey(second)

                if (first.type != second.type) {
                    continue
                }

                val pairDistance =
                    distanceBetween(
                        first.latitude,
                        first.longitude,
                        second.latitude,
                        second.longitude
                    )

                if (
                    pairDistance >
                    OPPOSING_CAMERA_PAIR_DISTANCE_METERS
                ) {
                    continue
                }

                if (
                    !sameCurrentRoadCorridor(
                        location = location,
                        heading = heading,
                        first = first,
                        second = second,
                        firstGeometry =
                            geometryByKey[firstKey]
                                ?: continue,
                        secondGeometry =
                            geometryByKey[secondKey]
                                ?: continue
                    )
                ) {
                    continue
                }

                val members =
                    setOf(
                        firstKey,
                        secondKey
                    )

                val existing =
                    pairOwnerships.firstOrNull { ownership ->
                        ownership.memberKeys == members
                    }

                if (existing != null) {
                    continue
                }

                val firstGeometry =
                    geometryByKey[firstKey]
                        ?: continue

                val secondGeometry =
                    geometryByKey[secondKey]
                        ?: continue

                /*
                 * BUGFIX (confirmed by 28aug field log, ~13:52:20): when a
                 * pair lock first forms, chooseFirstEncounteredCamera picked
                 * whichever member had the smaller forwardMeters at that
                 * single instant. Two members of a close pair are frequently
                 * within 1-2m of each other on forwardMeters (GPS noise
                 * level), so this could crown the camera the rider was NOT
                 * tracking as owner - silently dropping the camera that had
                 * already been monitored/warned since ~118m and handing a
                 * brand new, unwarned, wrong-direction candidate a fresh
                 * WARN FIRED at single-digit-metre distance.
                 *
                 * Fix: if either member is the camera we were already
                 * tracking (lastSelectedKey) or already have an active
                 * warned state for, that member keeps/gets ownership.
                 * Only fall back to the momentary geometry snapshot when
                 * neither member has any tracking history yet.
                 */
                val eligibleKeys = listOf(first, second).filter { candidate ->
                    val geometry = geometryByKey[cameraKey(candidate)] ?: return@filter false
                    buildEligibleTarget(location, heading, candidate, geometry, isPairOwner = true) != null
                }.map { cameraKey(it) }
                val stickyKey = eligibleKeys.firstOrNull { it == lastSelectedKey || warnedCameras.containsKey(it) }
                val ownerKey = stickyKey ?: when (eligibleKeys.size) {
                    0 -> continue
                    1 -> eligibleKeys.first()
                    else -> chooseFirstEncounteredCamera(firstKey, firstGeometry, secondKey, secondGeometry)
                } ?: continue

                pairOwnerships.add(
                    PairOwnership(
                        memberKeys = members,
                        ownerKey = ownerKey,
                        lockLatitude =
                            (first.latitude + second.latitude) /
                                2.0,
                        lockLongitude =
                            (first.longitude + second.longitude) /
                                2.0,
                        referenceHeading = heading
                    )
                )
                DriveDiagnosticStore.log(
                    "PAIR",
                    "locked owner=$ownerKey members=${members.joinToString(",")} pairDistance=${pairDistance.toInt()}m heading=${heading.toInt()}"
                )
            }
        }
    }

    private fun sameCurrentRoadCorridor(
        location: Location,
        heading: Float,
        first: RealCamera,
        second: RealCamera,
        firstGeometry: CameraGeometry,
        secondGeometry: CameraGeometry
    ): Boolean {

        val accuracy =
            if (location.hasAccuracy()) {
                location.accuracy
            } else {
                15f
            }

        val firstMatch =
            if (first.approachPath.size >= 2) {
                matchApproachPath(
                    location = location,
                    path = first.approachPath,
                    camera = first
                )
            } else {
                null
            }

        val secondMatch =
            if (second.approachPath.size >= 2) {
                matchApproachPath(
                    location = location,
                    path = second.approachPath,
                    camera = second
                )
            } else {
                null
            }

        if (
            firstMatch != null &&
            secondMatch != null
        ) {

            val allowed =
                mappedRoadWidth(accuracy) +
                    PAIR_ROAD_MATCH_EXTRA_METERS

            if (
                firstMatch.distanceMeters > allowed ||
                secondMatch.distanceMeters > allowed
            ) {
                return false
            }

            val firstAxis =
                axisDifference(
                    heading,
                    firstMatch.segmentBearing
                )

            val secondAxis =
                axisDifference(
                    heading,
                    secondMatch.segmentBearing
                )

            return (
                firstAxis <=
                    MAX_PAIR_ROAD_AXIS_DIFFERENCE_DEGREES &&
                secondAxis <=
                    MAX_PAIR_ROAD_AXIS_DIFFERENCE_DEGREES
            )
        }

        /*
         * Fallback for cameras without usable OSM approach geometry.
         *
         * The diagnostic drive proved that genuine opposing camera pairs can
         * be a little more than 60 m apart, and the old fallback required both
         * cameras to be ahead of the rider. That meant the pair was never
         * locked, so after passing camera A the engine immediately selected
         * camera B and fired a second warning.
         *
         * We now validate the PAIR AXIS itself against the current travel
         * axis. This is much more stable: on a two-way road the physical line
         * between two close opposing cameras normally follows the same road
         * axis regardless of which direction the rider is travelling.
         */
        val allowedFallback =
            pairFallbackCorridorWidth(accuracy)

        val pairAxisBearing =
            bearingBetween(
                first.latitude,
                first.longitude,
                second.latitude,
                second.longitude
            )

        val pairAxisDifference =
            axisDifference(
                heading,
                pairAxisBearing
            )

        val atLeastOneCameraAhead =
            firstGeometry.forwardMeters >= -PASSED_TOLERANCE_METERS ||
                secondGeometry.forwardMeters >= -PASSED_TOLERANCE_METERS

        val bothNearTravelCorridor =
            firstGeometry.lateralMeters <= allowedFallback &&
                secondGeometry.lateralMeters <= allowedFallback

        val valid =
            pairAxisDifference <= MAX_PAIR_AXIS_DIFFERENCE_DEGREES &&
                bothNearTravelCorridor &&
                atLeastOneCameraAhead

        if (!valid) {
            DriveDiagnosticStore.log(
                "PAIR_CHECK",
                "rejected first=${cameraKey(first)} second=${cameraKey(second)} axisDiff=${pairAxisDifference.toInt()} firstLat=${firstGeometry.lateralMeters.toInt()} secondLat=${secondGeometry.lateralMeters.toInt()} allowed=${allowedFallback.toInt()}"
            )
        }

        return valid
    }

    private fun chooseFirstEncounteredCamera(
        firstKey: String,
        first: CameraGeometry,
        secondKey: String,
        second: CameraGeometry
    ): String? {

        val firstForward =
            first.forwardMeters

        val secondForward =
            second.forwardMeters

        val firstAhead =
            firstForward >=
                -PASSED_TOLERANCE_METERS

        val secondAhead =
            secondForward >=
                -PASSED_TOLERANCE_METERS

        return when {

            firstAhead && secondAhead ->
                if (firstForward <= secondForward) {
                    firstKey
                } else {
                    secondKey
                }

            firstAhead ->
                firstKey

            secondAhead ->
                secondKey

            else ->
                null
        }
    }

    private fun pairOwnershipFor(
        cameraKey: String
    ): PairOwnership? {

        return pairOwnerships.firstOrNull { ownership ->
            cameraKey in ownership.memberKeys
        }
    }

    private fun updatePairOwnershipsForMovement(
        location: Location,
        heading: Float
    ) {

        val now =
            System.currentTimeMillis()

        val iterator =
            pairOwnerships.iterator()

        while (iterator.hasNext()) {

            val ownership =
                iterator.next()

            val headingChange =
                angleDifference(
                    heading,
                    ownership.referenceHeading
                )

            if (
                headingChange >=
                CONFIRMED_REVERSAL_ANGLE_DEGREES
            ) {
                ownership.reversalVotes += 1
            } else {
                ownership.reversalVotes = 0
            }

            val confirmedReversal =
                ownership.reversalVotes >=
                    REVERSAL_CONFIRMATION_FIXES

            val distanceFromPair =
                distanceBetween(
                    location.latitude,
                    location.longitude,
                    ownership.lockLatitude,
                    ownership.lockLongitude
                )

            val leftArea =
                distanceFromPair >=
                    PAIR_OWNERSHIP_RELEASE_DISTANCE_METERS

            val expired =
                now - ownership.createdAtMillis >=
                    PAIR_OWNERSHIP_MAX_AGE_MILLIS

            if (
                confirmedReversal ||
                leftArea ||
                expired
            ) {
                DriveDiagnosticStore.log(
                    "PAIR",
                    "released owner=${ownership.ownerKey} reversal=$confirmedReversal leftArea=$leftArea expired=$expired heading=${heading.toInt()} ref=${ownership.referenceHeading.toInt()} votes=${ownership.reversalVotes} distance=${distanceFromPair.toInt()}m"
                )
                // Pair selection may reset on reversal/expiry. Warning suppression
                // remains until the rider has actually left the 500m camera radius.
                iterator.remove()
            }
        }
    }

    private fun updateWarnedCameraStates(location: Location) {

        if (warnedCameras.isEmpty()) {
            return
        }

        val iterator = warnedCameras.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val distance = distanceBetween(location.latitude, location.longitude,
                entry.value.latitude, entry.value.longitude)
            // Retain warned records even if a camera temporarily disappears from data.
            if (distance < CAMERA_REARM_DISTANCE_METERS && location.hasAccuracy() && location.accuracy <= 35f) entry.value.enteredRearmRadius = true
            if (entry.value.enteredRearmRadius && distance >= CAMERA_REARM_DISTANCE_METERS &&
                location.hasAccuracy() && location.accuracy <= 35f) {
                iterator.remove()
                DriveDiagnosticStore.log("WARN", "rearmed key=${entry.key} distance=${distance.toInt()}m")
            }
        }
    }

    private fun calculateGeometry(
        location: Location,
        heading: Float,
        camera: RealCamera
    ): CameraGeometry {

        val results =
            FloatArray(3)

        Location.distanceBetween(
            location.latitude,
            location.longitude,
            camera.latitude,
            camera.longitude,
            results
        )

        val distance =
            results[0]

        val bearingToCamera =
            normalizeBearing(results[1])

        val headingDifference =
            angleDifference(
                heading,
                bearingToCamera
            )

        val signedDifference =
            signedAngleDifference(
                heading,
                bearingToCamera
            )

        val radians =
            Math.toRadians(
                signedDifference.toDouble()
            )

        val forward =
            (distance * cos(radians))
                .toFloat()

        val lateral =
            abs(
                distance * sin(radians)
            ).toFloat()

        return CameraGeometry(
            distanceMeters = distance,
            bearingToCamera = bearingToCamera,
            headingDifference = headingDifference,
            forwardMeters = forward,
            lateralMeters = lateral
        )
    }

    private fun resolveTravelHeading(
        location: Location
    ): Float? {

        var candidate: Float? = null

        if (
            location.hasBearing() &&
            location.speed >=
                MIN_RELIABLE_SPEED_MPS
        ) {
            candidate =
                normalizeBearing(
                    location.bearing
                )
        }

        val previous =
            previousMovementLocation

        if (previous != null) {

            val movementResults =
                FloatArray(3)

            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                location.latitude,
                location.longitude,
                movementResults
            )

            val movedMeters =
                movementResults[0]

            if (
                movedMeters >=
                MIN_MOVEMENT_FOR_DERIVED_HEADING_METERS
            ) {

                val derived =
                    normalizeBearing(
                        movementResults[1]
                    )

                candidate =
                    if (candidate == null) {
                        derived
                    } else {
                        blendBearings(
                            first = candidate,
                            second = derived,
                            secondWeight = 0.65f
                        )
                    }

                previousMovementLocation =
                    Location(location)
            }

        } else {
            previousMovementLocation =
                Location(location)
        }

        if (candidate != null) {
            lastReliableHeading =
                if (lastReliableHeading == null) {
                    candidate
                } else {
                    blendBearings(
                        first = lastReliableHeading!!,
                        second = candidate,
                        secondWeight = 0.52f
                    )
                }
        }

        return lastReliableHeading
    }

    private fun blendBearings(
        first: Float,
        second: Float,
        secondWeight: Float
    ): Float {

        val firstWeight =
            1f - secondWeight

        val firstRadians =
            Math.toRadians(first.toDouble())

        val secondRadians =
            Math.toRadians(second.toDouble())

        val x =
            cos(firstRadians) * firstWeight +
                cos(secondRadians) * secondWeight

        val y =
            sin(firstRadians) * firstWeight +
                sin(secondRadians) * secondWeight

        var degrees =
            Math.toDegrees(
                kotlin.math.atan2(y, x)
            ).toFloat()

        if (degrees < 0f) {
            degrees += 360f
        }

        return degrees
    }

    fun resetCamera(
        camera: RealCamera
    ) {
        warnedCameras.remove(
            cameraKey(camera)
        )
    }

    fun resetAll() {
        warnedCameras.clear()
        pairOwnerships.clear()
        lastReliableHeading = null
        previousMovementLocation = null
    }

    private fun cameraKey(
        camera: RealCamera
    ): String {

        return when (camera.source) {

            RealCameraSource.ENFORCEMENT_RELATION ->
                "REL:${camera.type}:${camera.relationId ?: camera.id}"

            RealCameraSource.SPEED_CAMERA_NODE ->
                "NODE:${camera.type}:${camera.id}"

            RealCameraSource.MASTER ->
                "MASTER:${camera.type}:${camera.id}"

            RealCameraSource.MANUAL ->
                "MANUAL:${camera.type}:${camera.id}"
        }
    }

    private fun mappedRoadWidth(
        accuracyMeters: Float
    ): Float {

        return (
            10f +
                accuracyMeters
                    .coerceIn(0f, 28f) *
                0.55f
            ).coerceIn(
                15f,
                25f
            )
    }

    private fun directionalCorridorWidth(
        accuracyMeters: Float
    ): Float {

        return (
            10f +
                accuracyMeters
                    .coerceIn(0f, 28f) *
                0.50f
            ).coerceIn(
                14f,
                24f
            )
    }

    private fun genericCorridorWidth(
        accuracyMeters: Float
    ): Float {

        return (
            11f +
                accuracyMeters
                    .coerceIn(0f, 28f) *
                0.48f
            ).coerceIn(
                18f,
                28f
            )
    }

    private fun pairFallbackCorridorWidth(
        accuracyMeters: Float
    ): Float {

        return (
            12f +
                accuracyMeters
                    .coerceIn(0f, 28f) *
                0.55f
            ).coerceIn(
                24f,
                36f
            )
    }

    private fun matchApproachPath(
        location: Location,
        path: List<RoadPoint>,
        camera: RealCamera
    ): SegmentMatch? {

        if (path.size < 2) {
            return null
        }

        var bestDistance =
            Float.MAX_VALUE

        var bestSegmentBearing: Float? =
            null

        var bestApproachBearing: Float? =
            null

        for (index in 0 until path.lastIndex) {

            val first =
                path[index]

            val second =
                path[index + 1]

            val distance =
                distancePointToSegmentMeters(
                    pointLatitude = location.latitude,
                    pointLongitude = location.longitude,
                    first = first,
                    second = second
                )

            if (distance >= bestDistance) {
                continue
            }

            val segmentBearing =
                bearingBetween(
                    first.latitude,
                    first.longitude,
                    second.latitude,
                    second.longitude
                )

            val firstToCamera =
                distanceBetween(
                    first.latitude,
                    first.longitude,
                    camera.latitude,
                    camera.longitude
                )

            val secondToCamera =
                distanceBetween(
                    second.latitude,
                    second.longitude,
                    camera.latitude,
                    camera.longitude
                )

            val approachBearing =
                if (firstToCamera >= secondToCamera) {
                    segmentBearing
                } else {
                    normalizeBearing(
                        segmentBearing + 180f
                    )
                }

            bestDistance = distance
            bestSegmentBearing = segmentBearing
            bestApproachBearing = approachBearing
        }

        if (
            bestSegmentBearing == null ||
            bestApproachBearing == null
        ) {
            return null
        }

        return SegmentMatch(
            distanceMeters = bestDistance,
            segmentBearing = bestSegmentBearing,
            approachBearing = bestApproachBearing
        )
    }

    private fun distancePointToSegmentMeters(
        pointLatitude: Double,
        pointLongitude: Double,
        first: RoadPoint,
        second: RoadPoint
    ): Float {

        val referenceLatitudeRadians =
            Math.toRadians(pointLatitude)

        val metersPerDegreeLatitude =
            111_320.0

        val metersPerDegreeLongitude =
            111_320.0 *
                cos(referenceLatitudeRadians)

        val ax =
            (first.longitude - pointLongitude) *
                metersPerDegreeLongitude

        val ay =
            (first.latitude - pointLatitude) *
                metersPerDegreeLatitude

        val bx =
            (second.longitude - pointLongitude) *
                metersPerDegreeLongitude

        val by =
            (second.latitude - pointLatitude) *
                metersPerDegreeLatitude

        val dx = bx - ax
        val dy = by - ay

        val lengthSquared =
            dx * dx + dy * dy

        if (lengthSquared <= 0.0001) {
            return hypot(ax, ay).toFloat()
        }

        val projection =
            (-(ax * dx + ay * dy) /
                lengthSquared)
                .coerceIn(0.0, 1.0)

        val closestX =
            ax + projection * dx

        val closestY =
            ay + projection * dy

        return hypot(
            closestX,
            closestY
        ).toFloat()
    }

    private fun distanceBetween(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {

        val results =
            FloatArray(1)

        Location.distanceBetween(
            firstLatitude,
            firstLongitude,
            secondLatitude,
            secondLongitude,
            results
        )

        return results[0]
    }

    private fun bearingBetween(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Float {

        val results =
            FloatArray(3)

        Location.distanceBetween(
            firstLatitude,
            firstLongitude,
            secondLatitude,
            secondLongitude,
            results
        )

        return normalizeBearing(
            results[1]
        )
    }

    private fun normalizeBearing(
        bearing: Float
    ): Float {

        var result =
            bearing % 360f

        if (result < 0f) {
            result += 360f
        }

        return result
    }

    private fun angleDifference(
        first: Float,
        second: Float
    ): Float {

        val difference =
            abs(
                normalizeBearing(first) -
                    normalizeBearing(second)
            )

        return if (difference > 180f) {
            360f - difference
        } else {
            difference
        }
    }

    /** Difference between two road AXES, ignoring travel direction. */
    private fun axisDifference(
        first: Float,
        second: Float
    ): Float {

        val directional =
            angleDifference(first, second)

        return minOf(
            directional,
            abs(180f - directional)
        )
    }

    private fun signedAngleDifference(
        from: Float,
        to: Float
    ): Float {

        var difference =
            normalizeBearing(to) -
                normalizeBearing(from)

        while (difference > 180f) {
            difference -= 360f
        }

        while (difference < -180f) {
            difference += 360f
        }

        return difference
    }

    companion object {

        private const val MAX_TARGET_DISTANCE_METERS =
            5000f

        private const val MIN_RELIABLE_SPEED_MPS =
            0.9f

        private const val MIN_MOVEMENT_FOR_DERIVED_HEADING_METERS =
            4f

        private const val PASSED_TOLERANCE_METERS =
            7f

        /* Same-road validation. */
        private const val MAX_ROAD_AXIS_DIFFERENCE_DEGREES =
            30f

        private const val MAX_PAIR_ROAD_AXIS_DIFFERENCE_DEGREES =
            34f

        private const val MAX_PAIR_AXIS_DIFFERENCE_DEGREES =
            34f

        private const val PAIR_ROAD_MATCH_EXTRA_METERS =
            5f

        /* Direction gates for single cameras. */
        private const val MAX_SINGLE_CAMERA_DIRECTION_DIFFERENCE_DEGREES =
            46f

        private const val MAX_MONITORED_DIRECTION_DIFFERENCE_DEGREES =
            46f

        private const val MAX_FORWARD_ANGLE_MAPPED_SINGLE_DEGREES =
            62f

        private const val MAX_FORWARD_ANGLE_PAIR_DEGREES =
            72f

        private const val MAX_FORWARD_ANGLE_DEGREES =
            42f

        private const val MAX_UNKNOWN_CAMERA_FORWARD_ANGLE_DEGREES =
            32f

        private const val CLOSE_STRONG_APPROACH_LATERAL_METERS =
            34f

        /* Close directional pair behaviour. */
        private const val OPPOSING_CAMERA_PAIR_DISTANCE_METERS =
            90f

        private const val PAIR_DISCOVERY_RADIUS_FROM_RIDER_METERS =
            500f

        private const val CONFIRMED_REVERSAL_ANGLE_DEGREES =
            105f

        private const val REVERSAL_CONFIRMATION_FIXES =
            3

        private const val PAIR_OWNERSHIP_RELEASE_DISTANCE_METERS =
            420f

        private const val PAIR_OWNERSHIP_MAX_AGE_MILLIS =
            5L * 60L * 1000L

        private const val CAMERA_REARM_DISTANCE_METERS =
            500f
    }
}

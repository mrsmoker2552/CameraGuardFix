package com.boss.cameraguard.data

import android.location.Location
import com.google.android.gms.tasks.Tasks
import com.boss.cameraguard.CameraGuardAuthManager
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class CameraRepository {

    private data class GeoPoint(
        val latitude: Double,
        val longitude: Double
    )

    private val overpassServers =
        listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

    fun loadNearbyCameras(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 25000
    ): List<RealCamera> {

        /*
         * One Overpass request intentionally loads both standalone camera
         * nodes and enforcement relations. Relation members are expanded so
         * we can resolve the OSM from/device/to geometry and derive the real
         * monitored travel direction instead of relying only on a direction
         * tag or on the relation centre.
         */
        val query =
            """
            [out:json][timeout:40];

            node
              ["highway"="speed_camera"]
              (around:$radiusMeters,$latitude,$longitude)
              ->.speedcams;

            relation
              ["type"="enforcement"]
              ["enforcement"]
              (around:$radiusMeters,$latitude,$longitude)
              ->.enforcements;

            (
              .speedcams;
              .enforcements;
              way(bn.speedcams);
            );
            out body center;
            >;
            out body center qt;
            """.trimIndent()

        var overpassFailure: Exception? = null

        val osmCameras =
            try {
                parseCameraData(
                    executeQuery(query)
                )
            } catch (exception: Exception) {
                overpassFailure = exception
                emptyList()
            }

        val masterCameras =
            try {
                loadFirebaseMasterCameras(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters
                )
            } catch (exception: Exception) {
                emptyList()
            }

        val merged =
            mergeMasterWithOsm(
                osmCameras = osmCameras,
                masterCameras = masterCameras
            )

        if (merged.isNotEmpty()) {
            return merged
        }

        overpassFailure?.let { throw it }

        return emptyList()
    }

    /**
     * Loads centrally verified cameras maintained by the CameraGuard Admin Map.
     * The Android app keeps anonymous Firebase Authentication for ordinary users;
     * RTDB rules allow authenticated users to read /masterCameras while writes
     * remain admin-only. This method runs only from Dispatchers.IO callers.
     */
    fun loadFirebaseMasterCameras(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 25000
    ): List<RealCamera> {

        CameraGuardAuthManager.ensureAuthenticatedBlocking()

        val database =
            FirebaseDatabase.getInstance(
                MASTER_DATABASE_URL
            )

        val snapshot =
            Tasks.await(
                database.reference
                    .child("masterCameras")
                    .get()
            )

        if (!snapshot.exists()) {
            return emptyList()
        }

        val result = mutableListOf<RealCamera>()

        for (child in snapshot.children) {

            val status =
                child.child("status")
                    .getValue(String::class.java)
                    ?.trim()
                    ?.uppercase()

            if (status == "REMOVED" || status == "INACTIVE" || status == "DISABLED") {
                continue
            }

            val cameraLatitude =
                (child.child("latitude").value as? Number)
                    ?.toDouble()
                    ?: continue

            val cameraLongitude =
                (child.child("longitude").value as? Number)
                    ?.toDouble()
                    ?: continue

            if (cameraLatitude !in -90.0..90.0 || cameraLongitude !in -180.0..180.0) {
                continue
            }

            val distance = FloatArray(1)
            Location.distanceBetween(
                latitude,
                longitude,
                cameraLatitude,
                cameraLongitude,
                distance
            )

            if (distance[0] > radiusMeters.toFloat()) {
                continue
            }

            val cameraType =
                child.child("cameraType")
                    .getValue(String::class.java)
                    ?.trim()
                    ?.uppercase()
                    ?: continue

            val speedLimit =
                (child.child("speedLimit").value as? Number)
                    ?.toInt()
                    ?.takeIf { it in 10..200 }

            val monitoredBearing =
                (child.child("monitoredBearing").value as? Number)
                    ?.toFloat()
                    ?.let { ((it % 360f) + 360f) % 360f }

            val sourceNote =
                child.child("sourceNote")
                    .getValue(String::class.java)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            val notes =
                child.child("notes")
                    .getValue(String::class.java)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            val note = sourceNote ?: notes
            val firebaseKey = child.key ?: continue

            fun add(type: RealCameraType, suffix: String) {
                result +=
                    RealCamera(
                        id = stableMasterId("$firebaseKey:$suffix"),
                        latitude = cameraLatitude,
                        longitude = cameraLongitude,
                        type = type,
                        speedLimit = if (type == RealCameraType.SPEED || type == RealCameraType.AVERAGE_SPEED) speedLimit else null,
                        direction = child.child("directionStatus")
                            .getValue(String::class.java),
                        source = RealCameraSource.MASTER,
                        monitoredBearing = monitoredBearing,
                        userNote = note,
                        verifiedByUser = true
                    )
            }

            when (cameraType) {
                "SPEED" -> add(RealCameraType.SPEED, "SPEED")
                "RED_LIGHT" -> add(RealCameraType.RED_LIGHT, "RED_LIGHT")
                "BUS_LANE", "RESERVED_LANE" -> add(RealCameraType.BUS_LANE, "BUS_LANE")
                "NO_ENTRY" -> add(RealCameraType.NO_ENTRY, "NO_ENTRY")
                "ZTL", "RESTRICTED_ACCESS", "ACCESS" -> add(RealCameraType.ZTL, "ZTL")
                "AVERAGE_SPEED", "SECTION_CONTROL" -> add(RealCameraType.AVERAGE_SPEED, "AVERAGE_SPEED")
                "MOBILE_PHONE", "MOBILE_PHONE_AND_SEATBELT", "SEATBELT" -> add(RealCameraType.MOBILE_PHONE, "MOBILE_PHONE")
                "OTHER_ENFORCEMENT", "OTHER" -> add(RealCameraType.OTHER_ENFORCEMENT, "OTHER_ENFORCEMENT")
                "RED_LIGHT_AND_SPEED", "SPEED_AND_RED_LIGHT", "BOTH" -> {
                    add(RealCameraType.RED_LIGHT, "RED_LIGHT")
                    add(RealCameraType.SPEED, "SPEED")
                }
            }
        }

        return result
            .distinctBy { it.stableSourceKey() }
    }

    /** Master records take precedence over OSM records within 40 m of the same type. */
    private fun mergeMasterWithOsm(
        osmCameras: List<RealCamera>,
        masterCameras: List<RealCamera>
    ): List<RealCamera> {

        if (masterCameras.isEmpty()) {
            return osmCameras
        }

        val filteredOsm =
            osmCameras.filter { osm ->
                masterCameras.none { master ->
                    if (master.type != osm.type) {
                        false
                    } else {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            master.latitude,
                            master.longitude,
                            osm.latitude,
                            osm.longitude,
                            distance
                        )
                        distance[0] <= MASTER_DUPLICATE_RADIUS_METERS
                    }
                }
            }

        return (masterCameras + filteredOsm)
            .distinctBy { it.stableSourceKey() }
    }

    private fun stableMasterId(value: String): Long {
        var hash = 1125899906842597L
        for (character in value) {
            hash = 31L * hash + character.code.toLong()
        }
        return if (hash == Long.MIN_VALUE) 0L else kotlin.math.abs(hash)
    }

    private fun executeQuery(
        query: String
    ): String {

        var lastException:
            Exception? = null

        for (server in overpassServers) {

            try {

                val encoded =
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )

                val url =
                    URL(
                        "$server?data=$encoded"
                    )

                val connection =
                    url.openConnection()
                        as HttpURLConnection

                try {

                    connection.requestMethod =
                        "GET"

                    connection.connectTimeout =
                        15000

                    connection.readTimeout =
                        45000

                    connection.setRequestProperty(
                        "User-Agent",
                        "CameraGuard/1.0 Android"
                    )

                    connection.setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    val responseCode =
                        connection.responseCode

                    if (responseCode !in 200..299) {

                        throw IllegalStateException(
                            "Overpass HTTP $responseCode"
                        )
                    }

                    return connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                } finally {

                    connection.disconnect()
                }

            } catch (exception: Exception) {

                lastException =
                    exception
            }
        }

        throw lastException
            ?: IllegalStateException(
                "All Overpass servers failed"
            )
    }

    private fun parseCameraData(
        json: String
    ): List<RealCamera> {

        val root =
            JSONObject(json)

        val elements =
            root.getJSONArray(
                "elements"
            )

        /*
         * Expanded relation members can be nodes or ways. Store a usable
         * representative point for every element so relation roles can be
         * resolved by their OSM reference ID.
         */
        val elementPoints =
            mutableMapOf<String, GeoPoint>()

        for (index in 0 until elements.length()) {

            val element =
                elements.getJSONObject(index)

            val type =
                element.optString("type")

            val id =
                element.optLong("id")

            readElementPoint(element)
                ?.let { point ->

                    elementPoints[
                        elementKey(
                            type = type,
                            id = id
                        )
                    ] = point
                }
        }

        /*
         * Keep the full node geometry for expanded OSM ways as well.
         * For an enforcement relation whose "from" member is a way,
         * the way centre can be hundreds of metres away and can produce
         * a bad approach bearing. Using the local way geometry nearest
         * the enforcement device gives the actual road direction near
         * the camera/intersection.
         */
        val speedCameraNodeIds =
            mutableSetOf<Long>()

        for (index in 0 until elements.length()) {

            val element =
                elements.getJSONObject(index)

            if (element.optString("type") != "node") {
                continue
            }

            val tags =
                element.optJSONObject("tags")
                    ?: continue

            if (tags.optString("highway") == "speed_camera") {
                speedCameraNodeIds.add(
                    element.optLong("id")
                )
            }
        }

        val wayNodePoints =
            mutableMapOf<Long, List<GeoPoint>>()

        // Parent-road geometry for standalone speed-camera nodes.  This lets
        // the warning engine reject a camera that is close in straight-line
        // distance but belongs to a different/cross/parallel road.
        val standaloneCameraRoadPaths =
            mutableMapOf<Long, List<RoadPoint>>()

        for (index in 0 until elements.length()) {

            val element =
                elements.getJSONObject(index)

            if (element.optString("type") != "way") {
                continue
            }

            val wayId =
                element.optLong("id")

            val nodeRefs =
                element.optJSONArray("nodes")
                    ?: continue

            val points =
                mutableListOf<GeoPoint>()

            for (nodeIndex in 0 until nodeRefs.length()) {

                val nodeId =
                    nodeRefs.optLong(nodeIndex)

                val point =
                    elementPoints[
                        elementKey(
                            type = "node",
                            id = nodeId
                        )
                    ]
                        ?: continue

                points.add(point)
            }

            if (points.size >= 2) {
                wayNodePoints[wayId] = points

                for (nodeIndex in 0 until nodeRefs.length()) {
                    val nodeId = nodeRefs.optLong(nodeIndex)

                    if (!speedCameraNodeIds.contains(nodeId)) {
                        continue
                    }

                    val roadPath =
                        points.map { point ->
                            RoadPoint(
                                latitude = point.latitude,
                                longitude = point.longitude
                            )
                        }

                    val existing =
                        standaloneCameraRoadPaths[nodeId]

                    // Prefer the longer parent-way geometry if a camera node
                    // belongs to more than one mapped way at a junction.
                    if (existing == null || roadPath.size > existing.size) {
                        standaloneCameraRoadPaths[nodeId] = roadPath
                    }
                }
            }
        }

        val cameras =
            mutableListOf<RealCamera>()

        /*
         * Standalone fixed speed-camera nodes.
         */
        for (index in 0 until elements.length()) {

            val element =
                elements.getJSONObject(index)

            if (element.optString("type") != "node") {
                continue
            }

            val tags =
                element.optJSONObject("tags")
                    ?: continue

            if (tags.optString("highway") != "speed_camera") {
                continue
            }

            val point =
                readElementPoint(element)
                    ?: continue

            val direction =
                cleanTag(
                    tags.optString("direction")
                )

            cameras.add(
                RealCamera(
                    id =
                        element.optLong("id"),

                    latitude =
                        point.latitude,

                    longitude =
                        point.longitude,

                    type =
                        RealCameraType.SPEED,

                    speedLimit =
                        parseSpeedLimit(
                            tags.optString("maxspeed")
                        ),

                    direction =
                        direction,

                    source =
                        RealCameraSource.SPEED_CAMERA_NODE,

                    monitoredBearing =
                        parseDirectionBearing(direction),

                    relationId =
                        null,

                    approachPath =
                        standaloneCameraRoadPaths[
                            element.optLong("id")
                        ] ?: emptyList()
                )
            )
        }

        /*
         * Directional enforcement relations.
         */
        for (index in 0 until elements.length()) {

            val element =
                elements.getJSONObject(index)

            if (element.optString("type") != "relation") {
                continue
            }

            val tags =
                element.optJSONObject("tags")
                    ?: continue

            if (tags.optString("type") != "enforcement") {
                continue
            }

            val enforcementText =
                tags.optString("enforcement")
                    .trim()
                    .lowercase()

            if (enforcementText.isBlank()) {
                continue
            }

            val enforcementValues =
                enforcementText
                    .split(";")
                    .map {
                        it.trim()
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .toSet()

            val hasSpeedEnforcement = enforcementValues.contains("maxspeed")
            val hasRedLightEnforcement =
                enforcementValues.contains("traffic_signals") ||
                    enforcementValues.contains("red_light_camera")
            val hasAverageSpeedEnforcement = enforcementValues.contains("average_speed")
            val hasAccessEnforcement = enforcementValues.contains("access")
            val hasMobilePhoneEnforcement =
                enforcementValues.contains("mobile_phone") || enforcementValues.contains("seatbelt")
            val hasKnownEnforcement =
                hasSpeedEnforcement || hasRedLightEnforcement || hasAverageSpeedEnforcement ||
                    hasAccessEnforcement || hasMobilePhoneEnforcement

            if (!hasKnownEnforcement && enforcementValues.isEmpty()) {
                continue
            }

            val members =
                element.optJSONArray("members")

            var fromPoint:
                GeoPoint? = null

            var fromMemberType:
                String? = null

            var fromMemberRef:
                Long? = null

            var devicePoint:
                GeoPoint? = null

            var toPoint:
                GeoPoint? = null

            if (members != null) {

                for (memberIndex in 0 until members.length()) {

                    val member =
                        members.getJSONObject(
                            memberIndex
                        )

                    val memberType =
                        member.optString("type")

                    val memberRef =
                        member.optLong("ref")

                    val role =
                        member.optString("role")
                            .trim()
                            .lowercase()

                    val point =
                        elementPoints[
                            elementKey(
                                type = memberType,
                                id = memberRef
                            )
                        ]
                            ?: continue

                    when (role) {

                        "from" -> {
                            if (fromPoint == null) {
                                fromPoint = point
                                fromMemberType = memberType
                                fromMemberRef = memberRef
                            }
                        }

                        "device" -> {
                            if (devicePoint == null) {
                                devicePoint = point
                            }
                        }

                        "to" -> {
                            if (toPoint == null) {
                                toPoint = point
                            }
                        }
                    }
                }
            }

            val relationCenter =
                readElementPoint(element)

            val cameraPoint =
                devicePoint
                    ?: relationCenter
                    ?: toPoint
                    ?: continue

            val directionTag =
                cleanTag(
                    tags.optString("direction")
                )

            /*
             * OSM enforcement relation semantics provide the strongest
             * direction clue: travel goes from the "from" member toward the
             * enforcement device.
             *
             * If "from" is a way, using its geometric centre can make the
             * bearing badly wrong on curved/long roads. Resolve a local point
             * from that way close to the camera and calculate the approach
             * bearing from there. This lets CameraGuard recognise the correct
             * road well before the rider reaches the device.
             */
            val relationBearing =
                localApproachBearing(
                    fromMemberType = fromMemberType,
                    fromMemberRef = fromMemberRef,
                    fromPoint = fromPoint,
                    cameraPoint = cameraPoint,
                    wayNodePoints = wayNodePoints
                )
                    ?: when {

                        fromPoint != null &&
                            devicePoint != null -> {

                            bearingBetween(
                                from = fromPoint,
                                to = devicePoint
                            )
                        }

                        fromPoint != null &&
                            toPoint != null -> {

                            bearingBetween(
                                from = fromPoint,
                                to = toPoint
                            )
                        }

                        else -> {

                            parseDirectionBearing(
                                directionTag
                            )
                        }
                    }

            val approachPath =
                buildApproachPath(
                    fromMemberType = fromMemberType,
                    fromMemberRef = fromMemberRef,
                    fromPoint = fromPoint,
                    cameraPoint = cameraPoint,
                    wayNodePoints = wayNodePoints
                )

            val relationId =
                element.optLong("id")

            if (hasSpeedEnforcement) {

                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = RealCameraType.SPEED,
                        speedLimit =
                            parseSpeedLimit(
                                tags.optString("maxspeed")
                            ),
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (hasRedLightEnforcement) {

                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = RealCameraType.RED_LIGHT,
                        speedLimit = null,
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (hasAverageSpeedEnforcement) {
                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = RealCameraType.AVERAGE_SPEED,
                        speedLimit = parseSpeedLimit(tags.optString("maxspeed")),
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (hasAccessEnforcement) {
                val psv = tags.optString("psv").trim().lowercase()
                val bus = tags.optString("bus").trim().lowercase()
                val access = tags.optString("access").trim().lowercase()
                val motorVehicle = tags.optString("motor_vehicle").trim().lowercase()
                val trafficSign = tags.optString("traffic_sign").trim().lowercase()
                val accessType = when {
                    psv == "yes" || psv == "designated" || bus == "yes" || bus == "designated" -> RealCameraType.BUS_LANE
                    trafficSign.contains("no_entry") || access == "no" -> RealCameraType.NO_ENTRY
                    motorVehicle in setOf("no", "private", "permit", "destination") || access in setOf("private", "permit", "destination") -> RealCameraType.ZTL
                    else -> RealCameraType.ZTL
                }
                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = accessType,
                        speedLimit = null,
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (hasMobilePhoneEnforcement) {
                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = RealCameraType.MOBILE_PHONE,
                        speedLimit = null,
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }

            if (!hasKnownEnforcement) {
                cameras.add(
                    RealCamera(
                        id = relationId,
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude,
                        type = RealCameraType.OTHER_ENFORCEMENT,
                        speedLimit = null,
                        direction = directionTag,
                        source = RealCameraSource.ENFORCEMENT_RELATION,
                        monitoredBearing = relationBearing,
                        relationId = relationId,
                        approachPath = approachPath
                    )
                )
            }
        }

        val distinct =
            cameras.distinctBy { camera ->

                "${camera.source}:${camera.type}:${camera.id}"
            }

        return removeStandaloneSpeedNodeDuplicates(
            distinct
        )
    }

    private fun removeStandaloneSpeedNodeDuplicates(
        cameras: List<RealCamera>
    ): List<RealCamera> {

        val directionalSpeedRelations =
            cameras.filter { camera ->

                camera.type ==
                    RealCameraType.SPEED &&
                    camera.source ==
                    RealCameraSource.ENFORCEMENT_RELATION
            }

        if (directionalSpeedRelations.isEmpty()) {
            return cameras
        }

        return cameras.filter { camera ->

            if (
                camera.type != RealCameraType.SPEED ||
                camera.source != RealCameraSource.SPEED_CAMERA_NODE
            ) {
                return@filter true
            }

            val duplicateRelationExists =
                directionalSpeedRelations.any { relation ->

                    distanceBetween(
                        first = camera,
                        second = relation
                    ) <=
                        SPEED_NODE_RELATION_DUPLICATE_DISTANCE_METERS
                }

            !duplicateRelationExists
        }
    }

    private fun readElementPoint(
        element: JSONObject
    ): GeoPoint? {

        if (
            element.has("lat") &&
            element.has("lon")
        ) {

            val latitude =
                element.optDouble(
                    "lat",
                    Double.NaN
                )

            val longitude =
                element.optDouble(
                    "lon",
                    Double.NaN
                )

            if (
                !latitude.isNaN() &&
                !longitude.isNaN()
            ) {

                return GeoPoint(
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

        val center =
            element.optJSONObject("center")
                ?: return null

        val latitude =
            center.optDouble(
                "lat",
                Double.NaN
            )

        val longitude =
            center.optDouble(
                "lon",
                Double.NaN
            )

        if (
            latitude.isNaN() ||
            longitude.isNaN()
        ) {
            return null
        }

        return GeoPoint(
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun elementKey(
        type: String,
        id: Long
    ): String {

        return "$type:$id"
    }

    private fun buildApproachPath(
        fromMemberType: String?,
        fromMemberRef: Long?,
        fromPoint: GeoPoint?,
        cameraPoint: GeoPoint,
        wayNodePoints: Map<Long, List<GeoPoint>>
    ): List<RoadPoint> {

        if (
            fromMemberType == "way" &&
            fromMemberRef != null
        ) {

            val wayPoints =
                wayNodePoints[fromMemberRef]
                    ?: emptyList()

            val localPoints =
                wayPoints
                    .filter { point ->
                        distanceBetween(
                            point,
                            cameraPoint
                        ) <=
                            APPROACH_PATH_MAX_DISTANCE_METERS
                    }
                    .map { point ->
                        RoadPoint(
                            latitude = point.latitude,
                            longitude = point.longitude
                        )
                    }
                    .toMutableList()

            if (localPoints.isNotEmpty()) {

                val first =
                    localPoints.first()

                val last =
                    localPoints.last()

                val firstDistance =
                    distanceBetween(
                        GeoPoint(first.latitude, first.longitude),
                        cameraPoint
                    )

                val lastDistance =
                    distanceBetween(
                        GeoPoint(last.latitude, last.longitude),
                        cameraPoint
                    )

                val cameraRoadPoint =
                    RoadPoint(
                        latitude = cameraPoint.latitude,
                        longitude = cameraPoint.longitude
                    )

                val nearestEndpointDistance =
                    minOf(
                        firstDistance,
                        lastDistance
                    )

                if (nearestEndpointDistance > 5f) {

                    if (firstDistance <= lastDistance) {
                        localPoints.add(0, cameraRoadPoint)
                    } else {
                        localPoints.add(cameraRoadPoint)
                    }
                }

                if (localPoints.size >= 2) {
                    return localPoints
                }
            }
        }

        if (fromPoint != null) {

            return listOf(
                RoadPoint(
                    latitude = fromPoint.latitude,
                    longitude = fromPoint.longitude
                ),
                RoadPoint(
                    latitude = cameraPoint.latitude,
                    longitude = cameraPoint.longitude
                )
            )
        }

        return emptyList()
    }

    private fun localApproachBearing(
        fromMemberType: String?,
        fromMemberRef: Long?,
        fromPoint: GeoPoint?,
        cameraPoint: GeoPoint,
        wayNodePoints: Map<Long, List<GeoPoint>>
    ): Float? {

        if (
            fromMemberType == "way" &&
            fromMemberRef != null
        ) {

            val points =
                wayNodePoints[fromMemberRef]
                    ?: emptyList()

            /*
             * Prefer a real point 25-180 m upstream from the camera.
             * This produces a stable local road bearing without using a
             * potentially distant way centre.
             */
            val pointDistances =
                points.map { point ->
                    point to
                        distanceBetween(
                            point,
                            cameraPoint
                        )
                }

            val candidate =
                pointDistances
                    .filter { (_, distance) ->
                        distance >= 8f
                    }
                    .sortedBy {
                        it.second
                    }
                    .firstOrNull { (_, distance) ->
                        distance in
                            LOCAL_BEARING_PREFERRED_MIN_METERS..
                            LOCAL_BEARING_PREFERRED_MAX_METERS
                    }
                    ?.first
                    ?: pointDistances
                        .filter { (_, distance) ->
                            distance >= 8f
                        }
                        .minByOrNull {
                            it.second
                        }
                        ?.first

            if (candidate != null) {

                return bearingBetween(
                    from = candidate,
                    to = cameraPoint
                )
            }
        }

        if (
            fromMemberType == "node" &&
            fromPoint != null
        ) {

            return bearingBetween(
                from = fromPoint,
                to = cameraPoint
            )
        }

        return null
    }

    private fun distanceBetween(
        first: GeoPoint,
        second: GeoPoint
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

    private fun bearingBetween(
        from: GeoPoint,
        to: GeoPoint
    ): Float? {

        val results =
            FloatArray(3)

        Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            results
        )

        if (results[0] < 1f) {
            return null
        }

        return normalizeBearing(
            results[1]
        )
    }

    private fun distanceBetween(
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

    private fun cleanTag(
        value: String?
    ): String? {

        return value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
    }

    private fun parseSpeedLimit(
        value: String?
    ): Int? {

        if (value.isNullOrBlank()) {
            return null
        }

        return Regex(
            """\d+"""
        )
            .find(value)
            ?.value
            ?.toIntOrNull()
    }

    private fun parseDirectionBearing(
        value: String?
    ): Float? {

        if (value.isNullOrBlank()) {
            return null
        }

        val text =
            value
                .trim()
                .uppercase()

        text.toFloatOrNull()
            ?.let { numeric ->

                return normalizeBearing(
                    numeric
                )
            }

        return when (text) {

            "N" -> 0f
            "NE" -> 45f
            "E" -> 90f
            "SE" -> 135f
            "S" -> 180f
            "SW" -> 225f
            "W" -> 270f
            "NW" -> 315f

            else -> null
        }
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

    companion object {

        private const val
            SPEED_NODE_RELATION_DUPLICATE_DISTANCE_METERS =
            25f

        private const val
            LOCAL_BEARING_PREFERRED_MIN_METERS =
            25f

        private const val
            LOCAL_BEARING_PREFERRED_MAX_METERS =
            180f

        private const val
            APPROACH_PATH_MAX_DISTANCE_METERS =
            500f

        private const val MASTER_DATABASE_URL =
            "https://cameraguard-b1c5a-default-rtdb.europe-west1.firebasedatabase.app"

        private const val MASTER_DUPLICATE_RADIUS_METERS = 40f
    }

}

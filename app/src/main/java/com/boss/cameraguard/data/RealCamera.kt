package com.boss.cameraguard.data

enum class RealCameraType {
    SPEED,
    RED_LIGHT,
    BUS_LANE,
    NO_ENTRY,
    ZTL,
    AVERAGE_SPEED,
    MOBILE_PHONE,
    OTHER_ENFORCEMENT
}

fun RealCameraType.displayName(): String =
    when (this) {
        RealCameraType.SPEED -> "Speed Camera"
        RealCameraType.RED_LIGHT -> "Red Light Camera"
        RealCameraType.BUS_LANE -> "Reserved / Bus Lane Camera"
        RealCameraType.NO_ENTRY -> "No Entry Camera"
        RealCameraType.ZTL -> "ZTL / Restricted Access Camera"
        RealCameraType.AVERAGE_SPEED -> "Average Speed / Section Control"
        RealCameraType.MOBILE_PHONE -> "Mobile Phone / Seatbelt Camera"
        RealCameraType.OTHER_ENFORCEMENT -> "Other Enforcement Camera"
    }

fun RealCameraType.usesSpeedWarningDistance(): Boolean =
    this == RealCameraType.SPEED || this == RealCameraType.AVERAGE_SPEED

fun RealCameraType.warningVoiceLabel(): String =
    when (this) {
        RealCameraType.SPEED -> "speed camera"
        RealCameraType.RED_LIGHT -> "red light camera"
        RealCameraType.BUS_LANE -> "reserved or bus lane camera"
        RealCameraType.NO_ENTRY -> "no entry enforcement camera"
        RealCameraType.ZTL -> "ZTL restricted access camera"
        RealCameraType.AVERAGE_SPEED -> "average speed section control"
        RealCameraType.MOBILE_PHONE -> "mobile phone and seatbelt enforcement camera"
        RealCameraType.OTHER_ENFORCEMENT -> "enforcement camera"
    }

enum class RealCameraSource {
    SPEED_CAMERA_NODE,
    ENFORCEMENT_RELATION,
    MASTER,
    MANUAL
}

data class RoadPoint(
    val latitude: Double,
    val longitude: Double
)

data class RealCamera(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val type: RealCameraType,
    val speedLimit: Int?,
    val direction: String?,
    val source: RealCameraSource,
    val monitoredBearing: Float? = null,
    val relationId: Long? = null,
    val approachPath: List<RoadPoint> = emptyList(),

    // User-owned metadata. These fields are used only for manual/fixed cameras.
    val userNote: String? = null,
    val createdAtMillis: Long? = null,
    val verifiedByUser: Boolean = false,

    // If a user fixes an OSM camera, this points to the original OSM object.
    // The original is then suppressed locally while the verified manual copy is kept.
    val replacesSourceKey: String? = null
)

fun RealCamera.stableSourceKey(): String {
    return when (source) {
        RealCameraSource.ENFORCEMENT_RELATION ->
            "REL:${type}:${relationId ?: id}"
        RealCameraSource.SPEED_CAMERA_NODE ->
            "NODE:${type}:$id"
        RealCameraSource.MASTER ->
            "MASTER:${type}:$id"
        RealCameraSource.MANUAL ->
            "MANUAL:${type}:$id"
    }
}

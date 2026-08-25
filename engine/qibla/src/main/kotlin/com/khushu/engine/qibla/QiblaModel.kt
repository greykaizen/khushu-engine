package com.khushu.engine.qibla

import com.khushu.engine.astronomy.SubsolarAlignment
import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.core.geo.Location
import com.khushu.engine.core.units.Degrees
import java.time.Instant

/**
 * Provenance for qibla geometry — mirrors prayer/astronomy house pattern.
 */
data class QiblaAudit(
    /** Coordinates of the Kaaba used for all bearings (mainstream convention). */
    val kaabaConstantsOrigin: String = "Kaaba 21.4225 N, 39.8262 E — mainstream convention",
    /** Distance model: spherical great-circle on the mean Earth radius. */
    val distanceModel: String = "Spherical great-circle, mean radius 6371.0088 km " +
        "(deliberate; ellipsoidal geodesics differ negligibly for this purpose)",
    /** Bearing formula: initial great-circle bearing (atan2 form). */
    val bearingFormula: String = "Initial great-circle bearing (standard atan2 form)",
    val warnings: List<String> = emptyList(),
)

/**
 * One shadow-qibla verification window.
 *
 * Twice a year the sun stands directly over the Kaaba: anywhere the sun is up,
 * its azimuth equals the local qibla direction (shadow points AWAY from qibla).
 * Twice a year it stands over the antipode: use the OPPOSITE direction.
 */
enum class ShadowMethod { FACE_TOWARD_SUN, FACE_AWAY_FROM_SUN }

data class QiblaShadowEvent(
    val instant: Instant,
    val method: ShadowMethod,
    /** Sun altitude at [location] when the passage peaks (visibility gate). */
    val sunAltitudeDegAtLocation: Double?,
    val sunAzimuthDegAtLocation: Double?,
    /** True when the sun is above the horizon at [location] at that instant. */
    val visibleAtLocation: Boolean,
    /**
     * Bearing to walk/shadow-verify at [location]: toward the sun for zenith
     * passages over the Kaaba; opposite for antipodal passages.
     */
    val shadowBearingDeg: Degrees,
)

/** Relation of the sun's position to the local qibla bearing at one instant. */
data class SunQiblaRelation(
    val sunAzimuthDeg: Double,
    val sunAltitudeDeg: Double,
    /** Signed difference (sun − qibla) normalized to [−180, 180]. */
    val signedAngleToQiblaDeg: Double,
    val alignedWithinTolerance: Boolean,
)

package com.khushu.engine.qibla

import com.khushu.engine.core.geo.Location

/** Initial great-circle bearing from north, plus the remaining distance to the Kaaba. */
data class QiblaBearing(
    /** Compass bearing in degrees [0, 360). */
    val bearingDegFromNorth: Double,
    /** Great-circle distance to the Kaaba in kilometres. */
    val greatCircleDistanceKm: Double,
)

/**
 * Capability entry point for the qibla direction.
 *
 * Uses the standard initial-bearing formula on the spherical Earth toward the
 * Kaaba (21.4225 N, 39.8262 E — the donor's constants, matching mainstream
 * prayer-time libraries).
 */
object Qibla {

    const val KAABA_LATITUDE_DEG = 21.4225
    const val KAABA_LONGITUDE_DEG = 39.8262
    /** Mean Earth radius used for the distance figure. */
    private const val EARTH_RADIUS_KM = 6371.0088

    fun bearing(location: Location): QiblaBearing {
        val lat1 = Math.toRadians(location.latitude.degrees)
        val lon1 = Math.toRadians(location.longitude.degrees)
        val lat2 = Math.toRadians(KAABA_LATITUDE_DEG)
        val lon2 = Math.toRadians(KAABA_LONGITUDE_DEG)

        val dLon = lon2 - lon1
        val y = kotlin.math.sin(dLon)
        val x = kotlin.math.cos(lat1) * kotlin.math.tan(lat2) - kotlin.math.sin(lat1) * kotlin.math.cos(dLon)
        var bearing = Math.toDegrees(kotlin.math.atan2(y, x)) % 360.0
        if (bearing < 0) bearing += 360.0

        // Central angle via the spherical law of cosines.
        val centralAngleRad = kotlin.math.acos(
            (kotlin.math.sin(lat1) * kotlin.math.sin(lat2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon))
                .coerceIn(-1.0, 1.0),
        )

        return QiblaBearing(
            bearingDegFromNorth = bearing,
            greatCircleDistanceKm = EARTH_RADIUS_KM * centralAngleRad,
        )
    }
}

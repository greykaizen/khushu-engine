package com.khushu.engine.qibla

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.SubsolarAlignment
import com.khushu.engine.core.geo.Location
import com.khushu.engine.core.units.Degrees
import com.khushu.engine.core.units.Kilometers

/** Initial great-circle bearing from north, plus the remaining distance to the Kaaba. */
data class QiblaBearing(
    /** Compass bearing in degrees [0, 360). */
    val bearingDegFromNorth: Degrees,
    /** Great-circle distance to the Kaaba. */
    val greatCircleDistanceKm: Kilometers,
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

    /**
     * Generic initial great-circle bearing from [from] toward [to], plus the
     * distance between them. [bearing] delegates here with Kaaba constants.
     */
    fun bearingBetween(from: Location, to: Location): Pair<Double, Double> {
        val lat1 = Math.toRadians(from.latitude.degrees)
        val lon1 = Math.toRadians(from.longitude.degrees)
        val lat2 = Math.toRadians(to.latitude.degrees)
        val lon2 = Math.toRadians(to.longitude.degrees)

        val dLon = lon2 - lon1
        val y = kotlin.math.sin(dLon)
        val x = kotlin.math.cos(lat1) * kotlin.math.tan(lat2) - kotlin.math.sin(lat1) * kotlin.math.cos(dLon)
        var b = Math.toDegrees(kotlin.math.atan2(y, x)) % 360.0
        if (b < 0) b += 360.0

        val central = kotlin.math.acos(
            (kotlin.math.sin(lat1) * kotlin.math.sin(lat2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(dLon))
                .coerceIn(-1.0, 1.0),
        )
        return Pair(b, EARTH_RADIUS_KM * central)
    }

    /**
     * Shadow-qibla verification windows within [year]: zenith passages over the
     * Kaaba and over its antipode. Near the Kaaba itself the bearing is
     * numerically unstable — these events are for everywhere else.
     */
    fun shadowVerification(year: Int, location: Location): List<QiblaShadowEvent> {
        val kaaba = SubsolarAlignment.passagesOver(KAABA_LATITUDE_DEG, KAABA_LONGITUDE_DEG, year)
        // Antipode of the Kaaba.
        val antiLat = -KAABA_LATITUDE_DEG
        val antiLon = Math.IEEEremainder(KAABA_LONGITUDE_DEG + 180.0, 360.0) - 180.0
        val antipode = SubsolarAlignment.passagesOver(antiLat, antiLon, year)

        val out = mutableListOf<QiblaShadowEvent>()
        for (passage in kaaba) {
            val pos = Astronomy.sun.position(location, passage.instant)
            var shadowB = pos.azimuthDeg % 360.0 // face toward sun == qibla; shadow points opposite
            if (shadowB < 0) shadowB += 360.0
            out += QiblaShadowEvent(
                instant = passage.instant,
                method = ShadowMethod.FACE_TOWARD_SUN,
                sunAltitudeDegAtLocation = pos.altitudeDeg,
                sunAzimuthDegAtLocation = pos.azimuthDeg,
                visibleAtLocation = pos.altitudeDeg > 0.0,
                shadowBearingDeg = Degrees(shadowB),
            )
        }
        for (passage in antipode) {
            val pos = Astronomy.sun.position(location, passage.instant)
            var shadowB = (pos.azimuthDeg + 180.0) % 360.0 // face AWAY from sun
            if (shadowB < 0) shadowB += 360.0
            out += QiblaShadowEvent(
                instant = passage.instant,
                method = ShadowMethod.FACE_AWAY_FROM_SUN,
                sunAltitudeDegAtLocation = pos.altitudeDeg,
                sunAzimuthDegAtLocation = pos.azimuthDeg,
                visibleAtLocation = pos.altitudeDeg > 0.0,
                shadowBearingDeg = Degrees(shadowB),
            )
        }
        return out.sortedBy { it.instant }
    }

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
            bearingDegFromNorth = Degrees(bearing),
            greatCircleDistanceKm = Kilometers(EARTH_RADIUS_KM * centralAngleRad),
        )
    }
}

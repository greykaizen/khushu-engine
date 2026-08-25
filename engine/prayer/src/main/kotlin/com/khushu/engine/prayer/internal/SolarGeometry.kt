package com.khushu.engine.prayer.internal

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Exact solar geometry for derived daytime windows (Ishraq, Zawaal, Dhuhr entry),
 * and a polar-safe solar-transit fallback.
 *
 * Uses the standard NOAA solar calculator approximation (declination + equation
 * of time from the fractional year) — accuracy well under one minute, which is
 * finer than adhan2's whole-minute rounding.
 *
 * This is an interim single implementation: the astronomy module will become the
 * sole owner of these facts once it lands (see docs/divergences.md).
 */
internal object SolarGeometry {

    private const val RAD = Math.PI / 180.0

    /**
     * UTC epoch-ms of solar transit over [longitudeDeg] on [date].
     * Computable every day of the year, including polar day/night.
     */
    fun transitEpochMs(date: java.time.LocalDate, longitudeDeg: Double): Long {
        // Fractional year at UTC noon of this date.
        val doy = date.dayOfYear.toDouble()
        val leap = if (date.isLeapYear) 1.0 else 0.0
        val gamma = 2.0 * Math.PI / (365.0 + leap) * (doy - 1.0 + 0.5)

        val eqTimeMin = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )

        // Solar noon in minutes UTC.
        val noonMinutesUtc = 720.0 - 4.0 * longitudeDeg - eqTimeMin
        val noonUtcMillis = java.time.ZonedDateTime
            .of(date.year, date.monthValue, date.dayOfMonth, 12, 0, 0, 0, java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        return noonUtcMillis + (noonMinutesUtc - 720.0).toMinutesMsRound()
    }

    private fun Double.toMinutesMsRound(): Long = Math.round(this * 60_000.0)

    /**
     * Epoch-ms when the sun crosses [altitudeDeg] during the morning approach
     * (before transit) on [date]. Returns null when that altitude is never
     * reached (polar conditions).
     */
    fun morningAltitudeCrossingEpochMs(
        date: java.time.LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeDeg: Double,
    ): Long? {
        val declinationRad = declinationRad(date)
        val latRad = latitudeDeg * RAD
        val altRad = altitudeDeg * RAD

        val cosH =
            (sin(altRad) - sin(latRad) * sin(declinationRad)) /
                (cos(latRad) * cos(declinationRad))
        if (cosH < -1.0 || cosH > 1.0) return null // never reaches this altitude that day

        val hoursBeforeTransit = acos(cosH) / RAD / 15.0
        return transitEpochMs(date, longitudeDeg) - Math.round(hoursBeforeTransit * 3_600_000.0)
    }

    /** NOAA declination for the given date at UTC noon, in radians. */
    private fun declinationRad(date: java.time.LocalDate): Double {
        val doy = date.dayOfYear.toDouble()
        val leap = if (date.isLeapYear) 1.0 else 0.0
        val gamma = 2.0 * Math.PI / (365.0 + leap) * (doy - 1.0 + 0.5)
        return 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
    }

    /** (minimum, maximum) sun altitude in degrees over the whole day — polar detection. */
    fun sunAltitudeExtremesDeg(date: java.time.LocalDate, latitudeDeg: Double): Pair<Double, Double> {
        val decl = declinationRad(date)
        val lat = latitudeDeg * RAD
        val sinAltMid = sin(lat) * sin(decl)
        val cosAltAmp = cos(lat) * cos(decl)
        val maxRad = kotlin.math.asin((sinAltMid + cosAltAmp).coerceIn(-1.0, 1.0))
        val minRad = kotlin.math.asin((sinAltMid - cosAltAmp).coerceIn(-1.0, 1.0))
        return Pair(minRad / RAD, maxRad / RAD)
    }
}

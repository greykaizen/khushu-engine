package com.khushu.engine.astronomy

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import com.khushu.engine.core.geo.Latitude
import com.khushu.engine.core.geo.Location
import com.khushu.engine.core.geo.Longitude
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Generic subsolar-point alignment primitive: instants within [year] when the
 * subsolar point stands directly over geographic coordinates (targetLat,
 * targetLon) — i.e., the sun is at zenith there. Purely astronomical;
 * domains compose their own meanings on top.
 *
 * Input contract (v1.13): |targetLatDeg| ≤ 23.44 (Tropic envelope — the
 * subsolar point never leaves it, so out-of-range targets deterministically
 * yield an empty list and are rejected instead); targetLon ∈ [-180, 180];
 * toleranceDeg finite and > 0; year within the ephemeris envelope. NaN
 * inputs are rejected — never silently empty.
 */
object SubsolarAlignment {

    /** Tropics of Cancer/Capricorn — the subsolar latitude envelope. */
    const val MAX_SUNSUPPORTED_LAT_DEG = 23.44

    data class ZenithPassage(val instant: Instant, val separationDeg: Double)

    fun passagesOver(
        targetLat: Latitude,
        targetLon: Longitude,
        year: Int,
        toleranceDeg: Double = 1.0,
    ): List<ZenithPassage> {
        val targetLatDeg = targetLat.degrees
        val targetLonDeg = targetLon.degrees
        validate(targetLatDeg.isFinite() && targetLatDeg >= -MAX_SUNSUPPORTED_LAT_DEG && targetLatDeg <= MAX_SUNSUPPORTED_LAT_DEG) {
            InvalidParameterException("targetLat", "$targetLatDeg", "must be finite within ±23.44 (subsolar envelope)")
        }
        validate(targetLonDeg.isFinite() && targetLonDeg >= -180.0 && targetLonDeg <= 180.0) {
            InvalidParameterException("targetLon", "$targetLonDeg", "must be finite within [-180, 180]")
        }
        validate(toleranceDeg.isFinite() && toleranceDeg > 0.0) {
            InvalidParameterException("toleranceDeg", "$toleranceDeg", "must be finite and > 0")
        }
        validate(year in 1700..2199) {
            InvalidParameterException("year", "$year", "must be within 1700..2199 (cosinekitty envelope)")
        }
        val kLatRad = Math.toRadians(targetLatDeg)
        val kLonRad = Math.toRadians(targetLonDeg)
        val kx = kotlin.math.cos(kLatRad) * kotlin.math.cos(kLonRad)
        val ky = kotlin.math.cos(kLatRad) * kotlin.math.sin(kLonRad)
        val kz = kotlin.math.sin(kLatRad)

        fun separationCosine(instant: Instant): Double {
            val pos = Astronomy.sun.position(Location.of(0.0, 0.0), instant)
            val decRad = Math.toRadians(pos.declinationDeg)
            val gmstDeg = greenwichMeanSiderealDegrees(instant.toEpochMilli() / 86_400_000.0 + 2440587.5)
            var lonRad = Math.toRadians(pos.rightAscensionHours * 15.0) - Math.toRadians(gmstDeg)
            lonRad = Math.IEEEremainder(lonRad, 2 * Math.PI)
            return kotlin.math.cos(decRad) * kotlin.math.cos(lonRad) * kx +
                kotlin.math.cos(decRad) * kotlin.math.sin(lonRad) * ky +
                kotlin.math.sin(decRad) * kz
        }

        val startMs = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMs = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val stepMs = 3_600_000L

        data class Candidate(val t: Long, val cosSep: Double)
        val candidates = mutableListOf<Candidate>()
        var prevVal = separationCosine(Instant.ofEpochMilli(startMs))
        var prevT = startMs
        var t = startMs + stepMs
        while (t <= endMs) {
            val curVal = separationCosine(Instant.ofEpochMilli(t))
            if (curVal < prevVal) {
                var lo = prevT; var hi = t
                while (hi - lo > 1000L) {
                    val m1 = lo + (hi - lo) / 3
                    val m2 = hi - (hi - lo) / 3
                    if (separationCosine(Instant.ofEpochMilli(m1)) < separationCosine(Instant.ofEpochMilli(m2))) lo = m1 else hi = m2
                }
                val peak = (lo + hi) / 2
                val cosSep = separationCosine(Instant.ofEpochMilli(peak))
                val sepDeg = Math.toDegrees(kotlin.math.acos(cosSep.coerceIn(-1.0, 1.0)))
                if (sepDeg <= toleranceDeg) candidates += Candidate(peak, cosSep)
            }
            prevVal = curVal; prevT = t; t += stepMs
        }

        // Group consecutive-day passes of one event; return each event's peak.
        val out = mutableListOf<ZenithPassage>()
        var group = mutableListOf<Candidate>()
        fun flush() {
            if (group.isEmpty()) return
            val best = group.maxBy { it.cosSep }
            out += ZenithPassage(Instant.ofEpochMilli(best.t), Math.toDegrees(kotlin.math.acos(best.cosSep.coerceIn(-1.0, 1.0))))
            group = mutableListOf()
        }
        for (c in candidates) {
            if (group.isNotEmpty() && c.t - group.last().t > 3L * 86_400_000) flush()
            group += c
        }
        flush()
        return out
    }

    /** GMST in degrees from Julian date (IAU 1982 polynomial). */
    internal fun greenwichMeanSiderealDegrees(jd: Double): Double {
        val d = jd - 2451545.0
        val tu = d / 36_525.0
        val gmstDeg = Math.IEEEremainder(
            280.46061837 + 360.98564736629 * d + 0.000387933 * tu * tu - tu * tu * tu / 38_710_000.0,
            360.0,
        )
        return if (gmstDeg < 0) gmstDeg + 360.0 else gmstDeg
    }
}

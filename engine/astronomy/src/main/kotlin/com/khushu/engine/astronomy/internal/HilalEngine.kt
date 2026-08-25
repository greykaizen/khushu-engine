package com.khushu.engine.astronomy.internal

import com.khushu.engine.astronomy.HilalReport
import com.khushu.engine.astronomy.OdehZone
import com.khushu.engine.astronomy.SightingVerdict
import com.khushu.engine.astronomy.YallopGrade
import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Hilal (crescent sighting) engine — 1:1 port of the donor's CrescentVisibilityMath
 * (itself a port of the threeD reference calculator) plus the report assembly from
 * LunarEphemeris.getCrescentVisibility. All photometric quantities are evaluated
 * topocentrically at the Fotheringham best time Tb = sunset + 4/9·lag.
 */
internal object HilalEngine {

    private const val MOON_RADIUS_KM = 1737.4

    fun visibility(epochMs: Long, location: Location): HilalReport? {
        val sunsetMs = Ephemeris.riseSetMs(Body.Sun, Direction.Set, epochMs, location)
            ?: return null // sun never sets that day (polar)
        val moonsetRaw = Ephemeris.riseSetMs(Body.Moon, Direction.Set, sunsetMs, location)
        val moonsetMs = moonsetRaw ?: sunsetMs
        val lagMinutes = ((moonsetMs - sunsetMs) / 60_000.0).coerceAtLeast(0.0)

        val tBest = if (moonsetRaw != null && moonsetMs > sunsetMs) {
            sunsetMs + ((4.0 / 9.0) * (moonsetMs - sunsetMs)).toLong()
        } else {
            sunsetMs
        }

        val moonAltAtSunset = Ephemeris.horizontalPosition(Body.Moon, sunsetMs, location).altitudeDeg
        val sunTb = Ephemeris.horizontalPosition(Body.Sun, tBest, location)
        val moonTb = Ephemeris.horizontalPosition(Body.Moon, tBest, location)
        val arcv = moonTb.altitudeDeg - sunTb.altitudeDeg

        // ARCL: topocentric elongation as great-circle separation.
        val raS = Math.toRadians(sunTb.rightAscensionHours * 15.0)
        val decS = Math.toRadians(sunTb.declinationDeg)
        val raM = Math.toRadians(moonTb.rightAscensionHours * 15.0)
        val decM = Math.toRadians(moonTb.declinationDeg)
        val elongation = Math.toDegrees(
            acos((sin(decS) * sin(decM) + cos(decS) * cos(decM) * cos(raS - raM)).coerceIn(-1.0, 1.0)),
        )

        val distanceKm = moonTb.distanceAu * Ephemeris.AU_KM
        val semiDiameterArcmin = Math.toDegrees(asin(MOON_RADIUS_KM / distanceKm)) * 60.0
        val widthArcmin = semiDiameterArcmin * (1.0 - cos(Math.toRadians(elongation)))
        val illumination = Ephemeris.moonIlluminationFraction(tBest)

        // Most recent new moon before local sunset → moon age.
        var conjunctionMs = io.github.cosinekitty.astronomy.searchMoonPhase(
            0.0, Ephemeris.time(sunsetMs - 30L * 86_400_000L), 31.0,
        )?.toMillisecondsSince1970()
        if (conjunctionMs != null && conjunctionMs >= sunsetMs) {
            conjunctionMs = io.github.cosinekitty.astronomy.searchMoonPhase(
                0.0, Ephemeris.time(sunsetMs - 45L * 86_400_000L), 46.0,
            )?.toMillisecondsSince1970()
        }
        val conj = conjunctionMs ?: (sunsetMs - 24L * 3_600_000L)
        val moonAgeHours = ((sunsetMs - conj).coerceAtLeast(0L) / 3_600_000L).toInt()

        return HilalReport(
            sunsetEpochMs = sunsetMs,
            moonsetEpochMs = moonsetRaw,
            bestTimeEpochMs = tBest,
            lagMinutes = lagMinutes.toInt(),
            moonAgeHours = moonAgeHours,
            arcvDeg = arcv,
            elongationDeg = elongation,
            crescentWidthArcmin = widthArcmin,
            illuminationFraction = illumination,
            yallop = yallopGrade(arcv, widthArcmin),
            odeh = odehZone(arcv, widthArcmin),
            verdicts = listOf(
                pakistanPmd(elongation, illumination, moonAltAtSunset, lagMinutes, widthArcmin),
                mabims(elongation, moonAltAtSunset),
                turkeyDiyanet(elongation, moonAltAtSunset),
                wujudulHilal(moonAgeHours, moonAltAtSunset),
            ),
        )
    }

    // ── Criteria (ported verbatim from CrescentVisibilityMath) ───────────────

    fun yallopGrade(arcvDeg: Double, widthArcmin: Double): YallopGrade {
        val w = widthArcmin
        val q = (arcvDeg - (11.837 - 6.323 * w + 0.732 * w.pow(2) - 0.102 * w.pow(3))) / 10.0
        return when {
            q > 0.216 -> YallopGrade.A
            q > -0.014 -> YallopGrade.B
            q > -0.160 -> YallopGrade.C
            q > -0.293 -> YallopGrade.D
            else -> YallopGrade.F
        }
    }

    fun odehZone(arcvDeg: Double, widthArcmin: Double): OdehZone {
        val w = widthArcmin
        val v = arcvDeg - (7.165 - 6.323 * w + 0.732 * w.pow(2) - 0.102 * w.pow(3))
        return when {
            v >= 5.65 -> OdehZone.A
            v >= 2.00 -> OdehZone.B
            v >= -0.96 -> OdehZone.C
            else -> OdehZone.D
        }
    }

    private fun pakistanPmd(
        elongation: Double,
        illum: Double,
        altAtSunset: Double,
        lagMinutes: Double,
        widthArcmin: Double,
    ): SightingVerdict {
        val e9 = elongation >= 9.0
        val i08 = illum >= 0.008
        val checks = listOf(e9 || i08, altAtSunset >= 6.5, lagMinutes >= 38.0, widthArcmin >= 0.17)
        return SightingVerdict("Pakistan_PMD", checks.all { it }, checks.count { it }, 5)
    }

    private fun mabims(elongation: Double, altAtSunset: Double): SightingVerdict {
        val checks = listOf(elongation >= 6.4, altAtSunset >= 3.0)
        return SightingVerdict("MABIMS", checks.all { it }, checks.count { it }, checks.size)
    }

    private fun turkeyDiyanet(elongation: Double, altAtSunset: Double): SightingVerdict {
        val checks = listOf(elongation >= 8.0, altAtSunset >= 5.0)
        return SightingVerdict("Turkey_Diyanet", checks.all { it }, checks.count { it }, checks.size)
    }

    private fun wujudulHilal(moonAgeHours: Int, altAtSunset: Double): SightingVerdict {
        val checks = listOf(moonAgeHours > 0, altAtSunset > 0.0)
        return SightingVerdict("Wujudul_Hilal", checks.all { it }, checks.count { it }, checks.size)
    }
}

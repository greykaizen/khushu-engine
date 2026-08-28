package com.khushu.engine.astronomy.internal

import com.khushu.engine.astronomy.CelestialPathPoint
import com.khushu.engine.astronomy.DayMoon
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One-pass monthly lunar solver.
 *
 * Donor's calendar recomputed rise/set searches plus 96-sample paths on every
 * render, with hardcoded fallbacks when a search failed. This solver walks the
 * month exactly once: per civil day it issues the minimal set of root-finding
 * queries (rise, set, transit) and derives phase facts from the transit instant.
 * No invented fallback times — uncomputable events stay null.
 */
internal object MoonTrackSolver {

    fun solve(
        location: Location,
        yearMonth: YearMonth,
        zoneId: ZoneId,
        includePath: Boolean,
        pathSamplesPerDay: Int,
    ): Pair<List<DayMoon>, List<CelestialPathPoint>> {
        val days = mutableListOf<DayMoon>()
        val path = ArrayList<CelestialPathPoint>()

        // Start one day early: a moon that rose yesterday can set today.
        var cursorMs = dayStartEpochMs(yearMonth.atDay(1), zoneId)
        val endExclusiveMs = dayStartEpochMs(yearMonth.plusMonths(1).atDay(1), zoneId)

        while (cursorMs < endExclusiveMs) {
            val civilDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(cursorMs), zoneId)
            val nextDayStartMs = dayStartEpochMs(civilDate.plusDays(1), zoneId)

            val rise = Ephemeris.riseSetMs(Body.Moon, io.github.cosinekitty.astronomy.Direction.Rise, cursorMs, location)
                ?.takeIf { it < nextDayStartMs }
            val set = Ephemeris.riseSetMs(Body.Moon, io.github.cosinekitty.astronomy.Direction.Set, cursorMs, location)
                ?.takeIf { it < nextDayStartMs }

            // Transit: highest-altitude instant of the civil day.
            val transitRaw = try {
                Ephemeris.hourAngleTransitMs(Body.Moon, cursorMs, location)
            } catch (e: Exception) {
                null
            }
            val transit: Long? = transitRaw?.takeIf { it in cursorMs until nextDayStartMs }

            val illumination: Double?
            val phaseAngle: Double?
            if (transit != null) {
                illumination = Ephemeris.moonIlluminationFraction(transit)
                phaseAngle = Ephemeris.moonPhaseAngleDeg(transit)
            } else {
                illumination = null
                phaseAngle = null
            }

            days += DayMoon(
                date = civilDate,
                riseEpochMs = rise,
                setEpochMs = set,
                transitEpochMs = transit,
                illuminationAtTransit = illumination,
                phaseAngleDegAtTransit = phaseAngle,
            )

            if (includePath) {
                path += sampleDayPath(cursorMs, nextDayStartMs, pathSamplesPerDay, location)
            }

            cursorMs = nextDayStartMs
        }
        return Pair(days, path)
    }

    private fun sampleDayPath(
        dayStartMs: Long,
        nextDayStartMs: Long,
        samples: Int,
        location: Location,
    ): List<CelestialPathPoint> {
        validate(samples > 1) {
            InvalidParameterException("pathSamplesPerDay", "$samples", "must be > 1")
        }
        val span = nextDayStartMs - dayStartMs
        val step = span / (samples - 1).coerceAtLeast(1)
        return (0 until samples).mapNotNull { i ->
            val t = dayStartMs + i.toLong() * step
            val pos = Ephemeris.horizontalPosition(Body.Moon, t, location)
            CelestialPathPoint(
                epochMs = t,
                azimuthDeg = pos.azimuthDeg,
                altitudeDeg = pos.altitudeDeg,
                enu = enu(pos.azimuthDeg, pos.altitudeDeg),
                aboveHorizon = pos.altitudeDeg >= 0.0,
            )
        }
    }

    fun enu(azimuthDeg: Double, altitudeDeg: Double): FloatArray {
        val azRad = Math.toRadians(azimuthDeg)
        val altRad = Math.toRadians(altitudeDeg)
        return floatArrayOf(
            (kotlin.math.cos(altRad) * kotlin.math.sin(azRad)).toFloat(),
            (kotlin.math.cos(altRad) * kotlin.math.cos(azRad)).toFloat(),
            kotlin.math.sin(altRad).toFloat(),
        )
    }

    /** UTC-midnight-anchored local day start (deterministic, no system defaults). */
    fun dayStartEpochMs(date: LocalDate, zoneId: ZoneId): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}

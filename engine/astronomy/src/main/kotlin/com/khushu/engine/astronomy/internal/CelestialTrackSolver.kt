package com.khushu.engine.astronomy.internal

import com.khushu.engine.astronomy.CelestialPathPoint
import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Per-day trajectory facts for any celestial body. */
data class CelestialDayTrack(
    val date: LocalDate,
    val riseEpochMs: Long?,
    val setEpochMs: Long?,
    /** Culmination instant (hour angle 0) within this civil day. */
    val transitEpochMs: Long?,
)

/**
 * Body-agnostic one-pass trajectory solver. Moon and Sun tracks both delegate
 * here — one implementation of the day-walking/crossing logic.
 */
internal object CelestialTrackSolver {

    fun solve(
        body: Body,
        location: Location,
        yearMonth: YearMonth,
        zoneId: ZoneId,
    ): List<CelestialDayTrack> {
        val days = mutableListOf<CelestialDayTrack>()

        var cursorMs = MoonTrackSolver.dayStartEpochMs(yearMonth.atDay(1), zoneId)
        val endExclusiveMs = MoonTrackSolver.dayStartEpochMs(yearMonth.plusMonths(1).atDay(1), zoneId)

        while (cursorMs < endExclusiveMs) {
            val civilDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(cursorMs), zoneId)
            val nextDayStartMs = MoonTrackSolver.dayStartEpochMs(civilDate.plusDays(1), zoneId)

            val rise = Ephemeris.riseSetMs(body, io.github.cosinekitty.astronomy.Direction.Rise, cursorMs, location)
                ?.takeIf { it < nextDayStartMs }
            val set = Ephemeris.riseSetMs(body, io.github.cosinekitty.astronomy.Direction.Set, cursorMs, location)
                ?.takeIf { it < nextDayStartMs }

            val transitRaw = try {
                Ephemeris.hourAngleTransitMs(body, cursorMs, location)
            } catch (e: Exception) {
                null
            }
            val transit = transitRaw?.takeIf { it in cursorMs until nextDayStartMs }

            days += CelestialDayTrack(
                date = civilDate,
                riseEpochMs = rise,
                setEpochMs = set,
                transitEpochMs = transit,
            )

            cursorMs = nextDayStartMs
        }
        return days
    }

    fun pathSamples(body: Body, startMs: Long, endMs: Long, location: Location): List<CelestialPathPoint> {
        val out = mutableListOf<CelestialPathPoint>()
        var t = startMs
        while (t < endMs) {
            val pos = Ephemeris.horizontalPosition(body, t, location)
            out += CelestialPathPoint(
                epochMs = t,
                azimuthDeg = pos.azimuthDeg,
                altitudeDeg = pos.altitudeDeg,
                enu = MoonTrackSolver.enu(pos.azimuthDeg, pos.altitudeDeg),
                aboveHorizon = pos.altitudeDeg >= 0.0,
            )
            t += 3_600_000L
        }
        return out
    }
}

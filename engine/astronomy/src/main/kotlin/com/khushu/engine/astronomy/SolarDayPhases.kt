package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import com.khushu.engine.astronomy.internal.Ephemeris
import com.khushu.engine.astronomy.internal.MoonTrackSolver
import io.github.cosinekitty.astronomy.Body
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Raw altitude-crossing instants for one civil day — the primitives behind the
 * semantic bands. Retained deliberately so consumers are never trapped by
 * today's terminology.
 */
data class SolarAltitudeCrossings(
    val astronomicalDawn: Instant?,
    val nauticalDawn: Instant?,
    val civilDawn: Instant?,
    /** Sun rises through −4° (blue→golden morning edge). */
    val blueHourMorningEnd: Instant?,
    val sunrise: Instant?,
    /** Morning golden hour ends as sun climbs through +6°. */
    val daylightBegin: Instant?,
    /** Evening golden hour begins as sun descends through +6°. */
    val daylightEnd: Instant?,
    val sunset: Instant?,
    /** Sun sets through −4° (golden→blue evening edge). */
    val blueHourEveningStart: Instant?,
    val civilDusk: Instant?,
    val nauticalDusk: Instant?,
    val astronomicalDusk: Instant?,
)

/**
 * The semantic solar day for one civil date, with explicit segment ownership.
 *
 * Night ownership contract: a civil date owns TWO night segments —
 * [lateNight] is the tail of last night (day start → astronomical dawn) and
 * [earlyNight] is the head of tonight (astronomical dusk → day end). Night
 * therefore straddles midnight across two consecutive SolarDayPhases objects
 * by design; neither fabricates a cross-midnight range.
 *
 * All members are null when physically uncomputable (polar conditions).
 * Bands follow a THRESHOLD-SEGMENT model: each named band is the civil-day
 * segment between two consecutive altitude crossings
 * (astronomicalDawn→nauticalDawn→civilDawn→[blue −4°]→sunrise→… mirrored at
 * dusk). They are adjacent, not nested. Horizon-bounded bands fall back to
 * solar transit as their inner edge on polar days.
 * Night ownership: this date owns lateNight (day-start→dawn) and earlyNight
 * (dusk→day-end); night straddling midnight is shared across two objects.
 */
data class SolarDayPhases(
    val date: LocalDate,
    val sunrise: Instant?,
    val solarNoon: Instant,
    val sunset: Instant?,
    /** Opposite culmination — lowest-altitude transit. NOT prayer's astronomicalMidnight midpoint. */
    val solarMidnight: Instant,
    val lateNight: ClosedRange<Instant>?,
    val earlyNight: ClosedRange<Instant>?,
    val morningAstronomicalTwilight: ClosedRange<Instant>?,
    val morningNauticalTwilight: ClosedRange<Instant>?,
    val morningCivilTwilight: ClosedRange<Instant>?,
    val morningBlueHour: ClosedRange<Instant>?,
    val morningGoldenHour: ClosedRange<Instant>?,
    val daylight: ClosedRange<Instant>?,
    val eveningGoldenHour: ClosedRange<Instant>?,
    val eveningBlueHour: ClosedRange<Instant>?,
    val eveningCivilTwilight: ClosedRange<Instant>?,
    val eveningNauticalTwilight: ClosedRange<Instant>?,
    val eveningAstronomicalTwilight: ClosedRange<Instant>?,
    /** Daylight length when both edges exist. */
    val dayLength: Duration?,
    /** Tonight's night length: tomorrow's sunrise − today's sunset. Null-safe at poles. */
    val followingNightLength: Duration?,
    val crossings: SolarAltitudeCrossings,
    val audit: AstroAudit,
)

internal object SolarDaySolver {

    fun solve(location: Location, date: LocalDate, zoneId: ZoneId): SolarDayPhases {
        val startMs = MoonTrackSolver.dayStartEpochMs(date, zoneId)
        val endMs = MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId)
        val conv = AltitudeConventions()
        val warnings = mutableListOf<String>()

        fun crossing(deg: Double, rising: Boolean): Instant? {
            val ms = runCatching {
                Ephemeris.altitudeCrossingMs(Body.Sun, rising, startMs, deg, location)
            }.getOrElse {
                warnings += "crossing search failed at ${deg}°/${if (rising) "rise" else "set"}: ${it.message}"
                null
            }?.takeIf { it < endMs }
            return ms?.let(Instant::ofEpochMilli)
        }

        val astroDawn = crossing(conv.astronomicalTwilightDeg, true)
        val nautDawn = crossing(conv.nauticalTwilightDeg, true)
        val civDawn = crossing(conv.civilTwilightDeg, true)
        val blueEndM = crossing(conv.blueHourUpperDeg, true)
        val sunrise = crossing(conv.sunriseSunsetDeg, true)
        val daylightBegin = crossing(conv.goldenHourUpperDeg, true)
        val daylightEnd = crossing(conv.goldenHourUpperDeg, false)
        val sunset = crossing(conv.sunriseSunsetDeg, false)
        val blueStartE = crossing(conv.blueHourUpperDeg, false)
        val civDusk = crossing(conv.civilTwilightDeg, false)
        val nautDusk = crossing(conv.nauticalTwilightDeg, false)
        val astroDusk = crossing(conv.astronomicalTwilightDeg, false)

        // True transits: highest (+0° hour angle) and opposite culmination (180°).
        val noonMs = (runCatching {
            Ephemeris.hourAngleTransitMs(Body.Sun, startMs, location)
        }.getOrElse { SolarDayMidnight.transitFallback(location, startMs, endMs) })
            .coerceIn(startMs, endMs - 1)
        val noon = Instant.ofEpochMilli(noonMs)
        val midnightMs = runCatching {
            // 12 HOURS = 180° = lower meridian (v1.14: was 180.0, which
            // cosinekitty rejects — the range is [0, 24) HOURS. Every midnight
            // before v1.14 silently fell through to the noon+12h fallback.)
            Ephemeris.hourAngleTransitMs(Body.Sun, noonMs, location, hourAngleHours = 12.0)
        }.getOrElse { SolarDayMidnight.midnightFallback(noonMs) }
        val midnight = Instant.ofEpochMilli(midnightMs)

        val crossings = SolarAltitudeCrossings(
            astronomicalDawn = astroDawn, nauticalDawn = nautDawn, civilDawn = civDawn,
            blueHourMorningEnd = blueEndM, sunrise = sunrise, daylightBegin = daylightBegin,
            daylightEnd = daylightEnd, sunset = sunset, blueHourEveningStart = blueStartE,
            civilDusk = civDusk, nauticalDusk = nautDusk, astronomicalDusk = astroDusk,
        )

        fun range(a: Instant?, b: Instant?): ClosedRange<Instant>? =
            if (a != null && b != null && a <= b) a..b else null

        val lateNight = range(Instant.ofEpochMilli(startMs), astroDawn)
        val earlyNight = range(astroDusk, Instant.ofEpochMilli(endMs))

        // Horizon-bound bands fall back to SOLAR TRANSIT when the sun never
        // crosses the horizon (polar day/night): the day still owns its half of
        // each band, split at the culmination.
        val morningHorizonBound = sunrise ?: noon
        val eveningHorizonBound = sunset ?: noon

        return SolarDayPhases(
            date = date,
            sunrise = sunrise,
            solarNoon = noon,
            sunset = sunset,
            solarMidnight = midnight,
            lateNight = lateNight,
            earlyNight = earlyNight,
            morningAstronomicalTwilight = range(astroDawn, nautDawn),
            morningNauticalTwilight = range(nautDawn, civDawn),
            morningCivilTwilight = range(civDawn, morningHorizonBound),
            morningBlueHour = range(civDawn, blueEndM),
            morningGoldenHour = range(blueEndM, daylightBegin ?: noon),
            daylight = range(daylightBegin, daylightEnd),
            eveningGoldenHour = range(daylightEnd ?: noon, blueStartE),
            eveningBlueHour = range(blueStartE, civDusk),
            eveningCivilTwilight = range(eveningHorizonBound, civDusk),
            eveningNauticalTwilight = range(nautDusk, astroDusk),
            // This date owns the HEAD of tonight's astronomical twilight
            // ([astroDusk, dayEnd)); the tail belongs to tomorrow's object.
            eveningAstronomicalTwilight = range(astroDusk, Instant.ofEpochMilli(endMs)),
            dayLength = if (sunrise != null && sunset != null && sunset > sunrise) {
                Duration.ofMillis(sunset.toEpochMilli() - sunrise.toEpochMilli())
            } else null,
            followingNightLength = run {
                val nextRise = runCatching {
                    Ephemeris.riseSetMs(Body.Sun, io.github.cosinekitty.astronomy.Direction.Rise, endMs, location)
                }.getOrNull()?.takeIf { r -> r < MoonTrackSolver.dayStartEpochMs(date.plusDays(2), zoneId) }
                if (sunset != null && nextRise != null && nextRise > sunset.toEpochMilli()) {
                    Duration.ofMillis(nextRise - sunset.toEpochMilli())
                } else null
            },
            crossings = crossings,
            audit = AstroAudit(warnings = warnings),
        )
    }
}

internal object SolarDayMidnight {

    /** Coarse-to-refined altitude maximum when the hour-angle solver fails. */
    fun transitFallback(location: Location, startMs: Long, endMs: Long): Long {
        var bestT = startMs; var bestAlt = Double.NEGATIVE_INFINITY
        var t = startMs
        while (t <= endMs) {
            val alt = Ephemeris.altitude(Body.Sun, t, location)
            if (alt > bestAlt) { bestAlt = alt; bestT = t }
            t += 30L * 60_000
        }
        // Ternary refine around the best sample.
        var lo = (bestT - 30L * 60_000).coerceAtLeast(startMs)
        var hi = (bestT + 30L * 60_000).coerceAtMost(endMs)
        while (hi - lo > 1000L) {
            val m1 = lo + (hi - lo) / 3
            val m2 = hi - (hi - lo) / 3
            if (Ephemeris.altitude(Body.Sun, m1, location) < Ephemeris.altitude(Body.Sun, m2, location)) lo = m1 else hi = m2
        }
        return (lo + hi) / 2
    }

    fun midnightFallback(noonMs: Long): Long = noonMs + 12L * 3_600_000
}

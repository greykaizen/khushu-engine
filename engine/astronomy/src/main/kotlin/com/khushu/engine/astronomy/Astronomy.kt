package com.khushu.engine.astronomy

import com.khushu.engine.astronomy.internal.Ephemeris
import com.khushu.engine.astronomy.internal.HilalEngine
import com.khushu.engine.astronomy.internal.LunarOrientationMath
import com.khushu.engine.astronomy.internal.MoonTrackSolver
import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Astronomy capability surface. Consumers request facts
 * (`astronomy.sun.position(...)`); calculators stay internal.
 *
 * All functions are deterministic over explicit arguments — no Clock, no globals.
 */
object Astronomy {

    object sun {
        fun position(location: Location, instant: Instant): SolarPosition {
            val p = Ephemeris.horizontalPosition(Body.Sun, instant.toEpochMilli(), location)
            return SolarPosition(
                azimuthDeg = p.azimuthDeg,
                altitudeDeg = p.altitudeDeg,
                declinationDeg = p.declinationDeg,
                rightAscensionHours = p.rightAscensionHours,
                distanceAu = p.distanceAu,
            )
        }

        /** Rise/set within the civil day containing [date] at [zoneId]; null when absent (polar). */
        fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet {
            val startMs = MoonTrackSolver.dayStartEpochMs(date, zoneId)
            val endMs = MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId)
            val rise = Ephemeris.riseSetMs(Body.Sun, io.github.cosinekitty.astronomy.Direction.Rise, startMs, location)
                ?.takeIf { it < endMs }
            val set = Ephemeris.riseSetMs(Body.Sun, io.github.cosinekitty.astronomy.Direction.Set, startMs, location)
                ?.takeIf { it < endMs }
            return RiseSet(riseEpochMs = rise, setEpochMs = set)
        }

        /**
         * Canonical solar day schedule: twilight tiers (astronomical/nautical/civil),
         * blue/golden hour anchors, sunrise, solar noon, sunset — chronological.
         */
        fun events(location: Location, date: LocalDate, zoneId: ZoneId): SolarEvents {
            val startMs = MoonTrackSolver.dayStartEpochMs(date, zoneId)
            val endMs = MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId)

            fun crossing(degrees: Double, rising: Boolean): SolarEvent? =
                Ephemeris.altitudeCrossingMs(Body.Sun, rising, startMs, degrees, location)
                    ?.takeIf { it < endMs }
                    ?.let { SolarEvent(typeFor(degrees, rising), it) }

            val noon = Ephemeris.hourAngleTransitMs(Body.Sun, startMs, location)
            val events = buildList {
                crossing(-18.0, true)?.let { add(it) }
                crossing(-12.0, true)?.let { add(it) }
                crossing(-6.0, true)?.let { add(it) }
                crossing(-4.0, true)?.let { add(SolarEvent(SolarEventType.BLUE_HOUR_MORNING_START, it.epochMs)) }
                crossing(6.0, true)?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_MORNING_END, it.epochMs)) }
                crossing(-0.833, true)?.let { add(SolarEvent(SolarEventType.SUNRISE, it.epochMs)) }
                if (noon < endMs) add(SolarEvent(SolarEventType.SOLAR_NOON, noon))
                crossing(-0.833, false)?.let { add(SolarEvent(SolarEventType.SUNSET, it.epochMs)) }
                crossing(6.0, false)?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_EVENING_START, it.epochMs)) }
                crossing(-4.0, false)?.let { add(SolarEvent(SolarEventType.BLUE_HOUR_EVENING_START, it.epochMs)) }
                crossing(-6.0, false)?.let { add(it) }
                crossing(-12.0, false)?.let { add(it) }
                crossing(-18.0, false)?.let { add(it) }
            }.sortedBy { it.epochMs }
            return SolarEvents(date, events)
        }

        private fun typeFor(degrees: Double, rising: Boolean): SolarEventType = when {
            degrees == -18.0 && rising -> SolarEventType.ASTRONOMICAL_DAWN
            degrees == -12.0 && rising -> SolarEventType.NAUTICAL_DAWN
            degrees == -6.0 && rising -> SolarEventType.CIVIL_DAWN
            degrees == -18.0 -> SolarEventType.ASTRONOMICAL_DUSK
            degrees == -12.0 -> SolarEventType.NAUTICAL_DUSK
            else -> SolarEventType.CIVIL_DUSK
        }
    }

    object moon {
        fun position(location: Location, instant: Instant): LunarPosition {
            val p = Ephemeris.horizontalPosition(Body.Moon, instant.toEpochMilli(), location)
            return LunarPosition(
                azimuthDeg = p.azimuthDeg,
                altitudeDeg = p.altitudeDeg,
                declinationDeg = p.declinationDeg,
                rightAscensionHours = p.rightAscensionHours,
                distanceKm = p.distanceAu * Ephemeris.AU_KM,
            )
        }

        fun state(location: Location, instant: Instant): MoonState {
            val epochMs = instant.toEpochMilli()
            val phaseAngle = Ephemeris.moonPhaseAngleDeg(epochMs)
            return MoonState(
                phaseAngleDeg = phaseAngle,
                illuminationFraction = Ephemeris.moonIlluminationFraction(epochMs),
                elongationDeg = Ephemeris.moonElongationDeg(epochMs),
                phaseName = phaseName(phaseAngle),
                brightLimbTiltDeg = LunarOrientationMath.brightLimbTiltDeg(epochMs, location),
            )
        }

        fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet {
            val startMs = MoonTrackSolver.dayStartEpochMs(date, zoneId)
            val endMs = MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId)
            val rise = Ephemeris.riseSetMs(Body.Moon, io.github.cosinekitty.astronomy.Direction.Rise, startMs, location)
                ?.takeIf { it < endMs }
            val set = Ephemeris.riseSetMs(Body.Moon, io.github.cosinekitty.astronomy.Direction.Set, startMs, location)
                ?.takeIf { it < endMs }
            return RiseSet(riseEpochMs = rise, setEpochMs = set)
        }

        /**
         * Whole-month lunar computation in a single pass — per-day rise/set/
         * transit/phase plus optional render path samples. Calendar and AR should
         * both consume this instead of issuing independent queries.
         */
        fun track(
            location: Location,
            yearMonth: YearMonth,
            zoneId: ZoneId,
            includePath: Boolean = false,
            pathSamplesPerDay: Int = 48,
        ): MonthlyMoonTrack {
            val (days, path) = MoonTrackSolver.solve(location, yearMonth, zoneId, includePath, pathSamplesPerDay)
            return MonthlyMoonTrack(days = days, pathPoints = path)
        }

        /** Canonical phase-band table pinned by donor tests; shared by every consumer. */
        fun phaseName(phaseAngleDeg: Double): String = when {
            phaseAngleDeg in 15.0..75.0 -> "Waxing Crescent"
            phaseAngleDeg in 75.0..105.0 -> "First Quarter"
            phaseAngleDeg in 105.0..165.0 -> "Waxing Gibbous"
            phaseAngleDeg in 165.0..195.0 -> "Full Moon"
            phaseAngleDeg in 195.0..255.0 -> "Waning Gibbous"
            phaseAngleDeg in 255.0..285.0 -> "Third Quarter"
            phaseAngleDeg in 285.0..345.0 -> "Waning Crescent"
            else -> "New Moon"
        }
    }

    object hilal {
        /**
         * Crescent-sighting report anchored to local sunset of [date]'s day.
         * Null when the sun never sets that civil day.
         */
        fun visibility(location: Location, date: LocalDate, zoneId: ZoneId): HilalReport? =
            HilalEngine.visibility(MoonTrackSolver.dayStartEpochMs(date, zoneId), location)
    }
}

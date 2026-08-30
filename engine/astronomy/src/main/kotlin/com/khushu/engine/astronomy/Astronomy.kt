package com.khushu.engine.astronomy

import com.khushu.engine.astronomy.internal.CelestialTrackSolver
import com.khushu.engine.astronomy.internal.Ephemeris
import com.khushu.engine.astronomy.internal.MoonTrackSolver
import com.khushu.engine.astronomy.internal.HilalEngine
import com.khushu.engine.astronomy.internal.LunarOrientationMath
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.UpstreamComputationException
import com.khushu.engine.core.error.validate
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
 *
 * Error model (v1.13): cosinekitty is wrapped — upstream failures surface as
 * typed [UpstreamComputationException] (calendar-module precedent), never as
 * raw library exceptions. The supported epoch range is 1700-01-01T00:00Z …
 * 2200-01-01T00:00Z (cosinekitty's validated envelope); instants outside it
 * are rejected up front with [InvalidParameterException] instead of silently
 * degrading.
 */
object Astronomy {

    /** Cosinekitty's supported envelope; we pin the same range explicitly. */
    internal val EPOCH_MIN_MS: Long = -8520345600000L  // 1700-01-01T00:00:00Z
    internal val EPOCH_MAX_MS: Long = 7258118400000L   // 2200-01-01T00:00:00Z

    internal fun requireEpochMs(epochMs: Long, name: String) {
        if (epochMs < EPOCH_MIN_MS || epochMs > EPOCH_MAX_MS) {
            throw InvalidParameterException(
                name,
                java.time.Instant.ofEpochMilli(epochMs).toString(),
                "outside the supported ephemeris range 1700-01-01…2200-01-01 (cosinekitty envelope)",
            )
        }
    }

    internal inline fun <T> upstream(operation: String, block: () -> T): T = try {
        block()
    } catch (e: com.khushu.engine.core.error.KhushuInputFailure) {
        throw e
    } catch (e: com.khushu.engine.core.error.KhushuComputationFailure) {
        throw e
    } catch (e: Exception) {
        throw UpstreamComputationException("cosinekitty $operation failed: ${e.message}", e)
    }

    object sun {
        fun position(location: Location, instant: Instant): SolarPosition {
            val epochMs = instant.toEpochMilli()
            requireEpochMs(epochMs, "instant")
            val p = upstream("sun.position") {
                Ephemeris.horizontalPosition(Body.Sun, epochMs, location)
            }
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

        /** Day length between rise and set; null on polar days. */
        fun dayLength(location: Location, date: LocalDate, zoneId: ZoneId): java.time.Duration? {
            val rs = riseSet(location, date, zoneId)
            val rise = rs.riseEpochMs ?: return null
            val set = rs.setEpochMs ?: return null
            if (set <= rise) return null
            return java.time.Duration.ofMillis(set - rise)
        }

        /** Tonight's length: tomorrow's sunrise − today's sunset; null-safe at poles. */
        fun nightLength(location: Location, date: LocalDate, zoneId: ZoneId): java.time.Duration? {
            val todaySet = riseSet(location, date, zoneId).setEpochMs ?: return null
            val nextRise = Ephemeris.riseSetMs(Body.Sun, io.github.cosinekitty.astronomy.Direction.Rise, MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId), location)
                ?.takeIf { it < MoonTrackSolver.dayStartEpochMs(date.plusDays(2), zoneId) }
                ?: return null
            if (nextRise <= todaySet) return null
            return java.time.Duration.ofMillis(nextRise - todaySet)
        }

        /**
         * The full semantic solar day: raw altitude crossings + named bands with
         * explicit night-ownership (lateNight/earlyNight). See [SolarDayPhases].
         */
        fun phases(location: Location, date: LocalDate, zoneId: ZoneId): SolarDayPhases =
            SolarDaySolver.solve(location, date, zoneId)

        /**
         * The pinned altitude-threshold table (blue/golden/twilight boundaries).
         * Conventions — documented, not universal.
         */
        fun conventions(): AltitudeConventions = AltitudeConventions()

        fun audit(): AstroAudit = AstroAudit()

        /**
         * One-pass monthly solar trajectory: per-day rise/set/transit plus
         * optional hourly ENU path samples. Mirrors moon.track; composed from
         * the same body-agnostic solver internals.
         */
        fun track(
            location: Location,
            yearMonth: YearMonth,
            zoneId: ZoneId,
            includePath: Boolean = false,
        ): com.khushu.engine.astronomy.SunTrack {
            val days = CelestialTrackSolver.solve(Body.Sun, location, yearMonth, zoneId).map {
                com.khushu.engine.astronomy.SolarDayTrack(
                    date = it.date,
                    riseEpochMs = it.riseEpochMs,
                    setEpochMs = it.setEpochMs,
                    transitEpochMs = it.transitEpochMs,
                )
            }
            val path = if (includePath) {
                val start = MoonTrackSolver.dayStartEpochMs(yearMonth.atDay(1), zoneId)
                val end = MoonTrackSolver.dayStartEpochMs(yearMonth.plusMonths(1).atDay(1), zoneId)
                CelestialTrackSolver.pathSamples(Body.Sun, start, end, location)
            } else emptyList()
            return com.khushu.engine.astronomy.SunTrack(days = days, pathPoints = path)
        }

        /**
         * Canonical solar day schedule: twilight tiers (astronomical/nautical/civil),
         * blue/golden hour anchors, sunrise, solar noon, sunset — chronological.
         * Thresholds come from the pinned [AltitudeConventions] table — the same
         * table [phases] uses, so the two APIs can never drift apart.
         */
        fun events(location: Location, date: LocalDate, zoneId: ZoneId): SolarEvents {
            val startMs = MoonTrackSolver.dayStartEpochMs(date, zoneId)
            val endMs = MoonTrackSolver.dayStartEpochMs(date.plusDays(1), zoneId)
            val c = AltitudeConventions()

            fun crossing(degrees: Double, rising: Boolean): SolarEvent? =
                Ephemeris.altitudeCrossingMs(Body.Sun, rising, startMs, degrees, location)
                    ?.takeIf { it < endMs }
                    ?.let { SolarEvent(typeFor(degrees, rising), it) }

            val noon = Ephemeris.hourAngleTransitMs(Body.Sun, startMs, location)
            val events = buildList {
                crossing(c.astronomicalTwilightDeg, true)?.let { add(it) }
                crossing(c.nauticalTwilightDeg, true)?.let { add(it) }
                crossing(c.civilTwilightDeg, true)?.let { add(it) }
                crossing(c.blueHourUpperDeg, true)?.let { add(SolarEvent(SolarEventType.BLUE_HOUR_MORNING_START, it.epochMs)) }
                crossing(c.goldenHourUpperDeg, true)?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_MORNING_END, it.epochMs)) }
                crossing(c.sunriseSunsetDeg, true)?.let { add(SolarEvent(SolarEventType.SUNRISE, it.epochMs)) }
                if (noon < endMs) add(SolarEvent(SolarEventType.SOLAR_NOON, noon))
                crossing(c.sunriseSunsetDeg, false)?.let { add(SolarEvent(SolarEventType.SUNSET, it.epochMs)) }
                crossing(c.goldenHourUpperDeg, false)?.let { add(SolarEvent(SolarEventType.GOLDEN_HOUR_EVENING_START, it.epochMs)) }
                crossing(c.blueHourUpperDeg, false)?.let { add(SolarEvent(SolarEventType.BLUE_HOUR_EVENING_START, it.epochMs)) }
                crossing(c.civilTwilightDeg, false)?.let { add(it) }
                crossing(c.nauticalTwilightDeg, false)?.let { add(it) }
                crossing(c.astronomicalTwilightDeg, false)?.let { add(it) }
            }.sortedBy { it.epochMs }
            return SolarEvents(date, events)
        }

        private fun typeFor(degrees: Double, rising: Boolean): SolarEventType {
            val c = AltitudeConventions()
            return when {
                degrees == c.astronomicalTwilightDeg && rising -> SolarEventType.ASTRONOMICAL_DAWN
                degrees == c.nauticalTwilightDeg && rising -> SolarEventType.NAUTICAL_DAWN
                degrees == c.civilTwilightDeg && rising -> SolarEventType.CIVIL_DAWN
                degrees == c.astronomicalTwilightDeg -> SolarEventType.ASTRONOMICAL_DUSK
                degrees == c.nauticalTwilightDeg -> SolarEventType.NAUTICAL_DUSK
                else -> SolarEventType.CIVIL_DUSK
            }
        }
    }

    object moon {
        fun position(location: Location, instant: Instant): LunarPosition {
            val epochMs = instant.toEpochMilli()
            requireEpochMs(epochMs, "instant")
            val p = upstream("moon.position") {
                Ephemeris.horizontalPosition(Body.Moon, epochMs, location)
            }
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
            requireEpochMs(epochMs, "instant")
            val phaseAngle = upstream("moon.state") { Ephemeris.moonPhaseAngleDeg(epochMs) }
            val conjunctionMs = upstream("moon.state") {
                Ephemeris.nextMoonPhaseMs(0.0, epochMs - 30L * 86_400_000L, 31.0)
            }
            return MoonState(
                phaseAngleDeg = phaseAngle,
                illuminationFraction = Ephemeris.moonIlluminationFraction(epochMs),
                elongationDeg = Ephemeris.moonElongationDeg(epochMs),
                phaseName = phaseName(phaseAngle),
                brightLimbTiltDeg = LunarOrientationMath.brightLimbTiltDeg(epochMs, location),
                moonAgeDays = conjunctionMs?.let { c ->
                    ((epochMs - c).coerceAtLeast(0L)) / 86_400_000.0
                },
                isWaxing = phaseAngle < 180.0,
            )
        }

        /** First occurrence of [targetPhaseDeg] (0/90/180/270) at or after [after]. */
        fun nextPhase(targetPhaseDeg: Double, after: Instant): UpcomingMoonPhase? {
            validate(targetPhaseDeg in listOf(0.0, 90.0, 180.0, 270.0)) {
                InvalidParameterException("targetPhaseDeg", "$targetPhaseDeg", "must be one of 0/90/180/270")
            }
            requireEpochMs(after.toEpochMilli(), "after")
            return upstream("nextPhase") {
                Ephemeris.nextMoonPhaseMs(targetPhaseDeg, after.toEpochMilli())
            }?.let { UpcomingMoonPhase(targetPhaseDeg, it) }
        }

        fun nextNewMoon(after: Instant): UpcomingMoonPhase? = nextPhase(0.0, after)
        fun nextFullMoon(after: Instant): UpcomingMoonPhase? = nextPhase(180.0, after)

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
            validate(pathSamplesPerDay > 1) {
                InvalidParameterException(
                    "pathSamplesPerDay", "$pathSamplesPerDay",
                    "must be > 1 — validated regardless of includePath so a call never " +
                        "succeeds silently with one flag and throws with another",
                )
            }
            val (days, path) = MoonTrackSolver.solve(location, yearMonth, zoneId, includePath, pathSamplesPerDay)
            return MonthlyMoonTrack(days = days, pathPoints = path)
        }

        /**
         * Canonical phase-band table pinned by donor tests; shared by every consumer.
         * Band edges: each named band is INCLUSIVE of its lower bound and EXCLUSIVE
         * of its upper bound except where noted; angles outside 15°..<345° map to
         * "New Moon" (i.e. New Moon owns [345,360)+[0,15)). NaN/∞ are rejected —
         * never silently classified.
         */
        fun phaseName(phaseAngleDeg: Double): String {
            if (!phaseAngleDeg.isFinite()) {
                throw InvalidParameterException("phaseAngleDeg", "$phaseAngleDeg", "must be finite")
            }
            return when {
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

        /**
         * Geocentric lunar distance extremes (perigee/apogee) within the scanned
         * window [from, to]. Coarse daily sampling + parabolic refinement.
         * @throws InvalidParameterException when the window exceeds 50 years (resource guard)
         */
        fun distanceExtremes(from: Instant, to: Instant): List<MoonDistanceExtreme> {
            val fromMs = from.toEpochMilli()
            val toMs = to.toEpochMilli()
            validate(toMs > fromMs) {
                InvalidParameterException("from/to", "$from..$to", "to must be after from (empty scan window)")
            }
            requireEpochMs(fromMs, "from")
            requireEpochMs(toMs, "to")
            validate(toMs - fromMs <= 50L * 366 * 86_400_000L) {
                InvalidParameterException(
                    "from/to", "$from..$to",
                    "scan window exceeds 50 years — request a smaller window (resource guard)",
                )
            }
            val auKm = Ephemeris.AU_KM
            fun dist(tMs: Long): Double = upstream("geoVector") {
                io.github.cosinekitty.astronomy.geoVector(
                    Body.Moon,
                    Ephemeris.time(tMs),
                    io.github.cosinekitty.astronomy.Aberration.Corrected,
                ).length()
            } * auKm

            data class Sample(val t: Long, val d: Double)
            val stepMs = 6L * 3_600_000
            val samples = ArrayList<Sample>()
            var t = fromMs
            while (t <= toMs) {
                samples += Sample(t, dist(t))
                t += stepMs
            }
            // Extremum must dominate every sample within ±24 h — short-period
            // lunar perturbations create shallow false extrema otherwise.
            val dominanceRadius = 4
            val out = mutableListOf<MoonDistanceExtreme>()
            // Require the full dominance window inside the scan range to avoid
            // boundary artefacts.
            for (i in dominanceRadius until samples.size - dominanceRadius) {
                val c = samples[i]
                var lo = true; var hi = true
                for (j in maxOf(0, i - dominanceRadius)..minOf(samples.size - 1, i + dominanceRadius)) {
                    if (j == i) continue
                    if (samples[j].d <= c.d) lo = false
                    if (samples[j].d >= c.d) hi = false
                }
                if (!lo && !hi) continue

                val tStar: Long =
                    if (i in 1 until samples.size - 1) {
                        val p = samples[i - 1]; val n = samples[i + 1]
                        val denom = p.d - 2.0 * c.d + n.d
                        if (kotlin.math.abs(denom) < 1e-12) c.t else c.t + (0.5 * stepMs * (p.d - n.d) / denom).toLong()
                    } else {
                        c.t
                    }
                out += MoonDistanceExtreme(epochMs = tStar, distanceKm = dist(tStar), isPerigee = lo)
            }

            // Merge same-type survivors closer than half an anomalistic month,
            // keeping the dominant one.
            val merged = mutableListOf<MoonDistanceExtreme>()
            for (cand in out.sortedBy { it.epochMs }) {
                val last = merged.lastOrNull()
                if (last != null && last.isPerigee == cand.isPerigee &&
                    cand.epochMs - last.epochMs < 13L * 86_400_000
                ) {
                    val better = if (cand.isPerigee) cand.distanceKm < last.distanceKm else cand.distanceKm > last.distanceKm
                    if (better) merged[merged.size - 1] = cand
                } else {
                    merged += cand
                }
            }
            return merged
        }

        /** Next global solar eclipse at or after [after] (geocentric facts). */
        fun nextGlobalSolarEclipse(after: Instant): GlobalSolarEclipse? {
            requireEpochMs(after.toEpochMilli(), "after")
            val info = upstream("searchGlobalSolarEclipse") {
                io.github.cosinekitty.astronomy.searchGlobalSolarEclipse(Ephemeris.time(after.toEpochMilli()))
            } ?: return null
            return GlobalSolarEclipse(
                kind = eclipseKind(info.kind),
                peakEpochMs = info.peak.toMillisecondsSince1970(),
                obscuration = info.obscuration.takeIf { it > 0.0 },
                latitudeDeg = info.latitude,
                longitudeDeg = info.longitude,
            )
        }

        /** Next lunar eclipse visible somewhere on Earth at or after [after]. */
        fun nextLunarEclipse(after: Instant): LunarEclipse? {
            requireEpochMs(after.toEpochMilli(), "after")
            val info = upstream("searchLunarEclipse") {
                io.github.cosinekitty.astronomy.searchLunarEclipse(Ephemeris.time(after.toEpochMilli()))
            } ?: return null
            return LunarEclipse(
                kind = eclipseKind(info.kind),
                peakEpochMs = info.peak.toMillisecondsSince1970(),
                obscuration = info.obscuration.takeIf { it > 0.0 },
            )
        }

        private fun eclipseKind(k: io.github.cosinekitty.astronomy.EclipseKind): EclipseKind = when (k) {
            io.github.cosinekitty.astronomy.EclipseKind.Partial -> EclipseKind.PARTIAL
            io.github.cosinekitty.astronomy.EclipseKind.Annular -> EclipseKind.ANNULAR
            io.github.cosinekitty.astronomy.EclipseKind.Total -> EclipseKind.TOTAL
            else -> EclipseKind.NONE
        }
    }

    /** Equinoxes and solstices for [year] — passthrough over cosinekitty Seasons. */
    fun seasons(year: Int): com.khushu.engine.astronomy.Seasons {
        validate(year in 1700..2199) {
            InvalidParameterException("year", "$year", "must be within 1700..2199 (cosinekitty envelope)")
        }
        val info = upstream("seasons") { io.github.cosinekitty.astronomy.seasons(year) }
        return com.khushu.engine.astronomy.Seasons(
            marchEquinox = info.marchEquinox.toMillisecondsSince1970().let(Instant::ofEpochMilli),
            juneSolstice = info.juneSolstice.toMillisecondsSince1970().let(Instant::ofEpochMilli),
            septemberEquinox = info.septemberEquinox.toMillisecondsSince1970().let(Instant::ofEpochMilli),
            decemberSolstice = info.decemberSolstice.toMillisecondsSince1970().let(Instant::ofEpochMilli),
        )
    }

    object hilal {
        /**
         * Crescent-sighting report anchored to local sunset of [date]'s day.
         * Null when the sun never sets that civil day.
         */
        fun visibility(location: Location, date: LocalDate, zoneId: ZoneId): HilalReport? =
            HilalEngine.visibility(MoonTrackSolver.dayStartEpochMs(date, zoneId), location)

        /**
         * Evening-by-evening sighting outlook for [nights] consecutive civil
         * days starting [from]: each entry is the report anchored at that
         * day's sunset. Null entries mark days without sunset (polar).
         */
        fun forecast(
            location: Location,
            from: LocalDate,
            zoneId: ZoneId,
            nights: Int = 8,
        ): List<com.khushu.engine.astronomy.HilalForecastDay> {
            validate(nights in 1..60) {
                InvalidParameterException("nights", "$nights", "must be within 1..60")
            }
            return (0 until nights).map { i ->
                val d = from.plusDays(i.toLong())
                com.khushu.engine.astronomy.HilalForecastDay(d, visibility(location, d, zoneId))
            }
        }
    }
}

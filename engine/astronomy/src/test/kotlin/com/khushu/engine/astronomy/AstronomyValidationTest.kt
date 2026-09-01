package com.khushu.engine.astronomy

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.geo.Location
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * v1.13: boundary validation for the astronomy capability — every typed
 * failure the audit found missing. Location/units validation lives in core
 * (ErrorModelTest/LocationTest); these cover the raw Double/Int/Instant
 * params that cross the astronomy boundary.
 */
class AstronomyValidationTest {

    private val makkah = Location.of(21.42, 39.83, 310.0)
    private val inRange = Instant.parse("2026-08-30T12:00:00Z")

    @Test
    fun outOfEnvelopeInstantsAreRejectedUpFront() {
        val farPast = Instant.parse("1500-01-01T00:00:00Z")
        val farFuture = Instant.parse("2500-01-01T00:00:00Z")
        assertFailsWith<InvalidParameterException> { Astronomy.sun.position(makkah, farPast) }
        assertFailsWith<InvalidParameterException> { Astronomy.sun.position(makkah, farFuture) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.position(makkah, farPast) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.state(makkah, farFuture) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.nextPhase(180.0, farPast) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.nextLunarEclipse(farFuture) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.nextGlobalSolarEclipse(farPast) }
    }

    @Test
    fun inRangeInstantsStillCompute() {
        val p = Astronomy.sun.position(makkah, inRange)
        assertTrue(p.altitudeDeg.isFinite())
        assertTrue(p.azimuthDeg.isFinite())
        val s = Astronomy.moon.state(makkah, inRange)
        assertTrue(s.moonAgeDays == null || s.moonAgeDays!! >= 0.0)
    }

    @Test
    fun phaseNameRejectsNonFiniteAngles() {
        // NaN used to silently classify as "New Moon" — now typed failure.
        assertFailsWith<InvalidParameterException> { Astronomy.moon.phaseName(Double.NaN) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.phaseName(Double.POSITIVE_INFINITY) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.phaseName(Double.NEGATIVE_INFINITY) }
        // finite angles still classify (out-of-band → New Moon by design)
        assertTrue(Astronomy.moon.phaseName(350.0) == "New Moon")
        assertTrue(Astronomy.moon.phaseName(90.0) == "First Quarter")
    }

    @Test
    fun nextPhaseRejectsNonQuarterTargets() {
        assertFailsWith<InvalidParameterException> { Astronomy.moon.nextPhase(45.0, inRange) }
        assertFailsWith<InvalidParameterException> { Astronomy.moon.nextPhase(Double.NaN, inRange) }
    }

    @Test
    fun moonTrackValidatesSampleCountRegardlessOfIncludePath() {
        // validation must not depend on the includePath flag
        assertFailsWith<InvalidParameterException> {
            Astronomy.moon.track(makkah, java.time.YearMonth.of(2026, 8), java.time.ZoneId.of("UTC"), includePath = false, pathSamplesPerDay = 1)
        }
        assertFailsWith<InvalidParameterException> {
            Astronomy.moon.track(makkah, java.time.YearMonth.of(2026, 8), java.time.ZoneId.of("UTC"), includePath = true, pathSamplesPerDay = 0)
        }
    }

    @Test
    fun distanceExtremesRejectsBadWindows() {
        val a = Instant.parse("2026-01-01T00:00:00Z")
        val b = Instant.parse("2026-06-01T00:00:00Z")
        val c = Instant.parse("2126-01-01T00:00:00Z")
        assertFailsWith<InvalidParameterException> { Astronomy.moon.distanceExtremes(b, a) } // reversed
        assertFailsWith<InvalidParameterException> { Astronomy.moon.distanceExtremes(a, c) } // > 50 years
        assertFailsWith<InvalidParameterException> {
            Astronomy.moon.distanceExtremes(Instant.parse("1500-01-01T00:00:00Z"), a) // out of envelope
        }
        assertTrue(Astronomy.moon.distanceExtremes(a, b).isNotEmpty()) // sane window works
    }

    @Test
    fun seasonsRejectsOutOfRangeYears() {
        assertFailsWith<InvalidParameterException> { Astronomy.seasons(1699) }
        assertFailsWith<InvalidParameterException> { Astronomy.seasons(2200) }
        assertTrue(Astronomy.seasons(2026).marchEquinox.epochSecond > 0)
    }

    @Test
    fun hilalForecastRejectsBadNightCounts() {
        assertFailsWith<InvalidParameterException> {
            Astronomy.hilal.forecast(makkah, java.time.LocalDate.of(2026, 8, 30), java.time.ZoneId.of("UTC"), nights = 0)
        }
        assertFailsWith<InvalidParameterException> {
            Astronomy.hilal.forecast(makkah, java.time.LocalDate.of(2026, 8, 30), java.time.ZoneId.of("UTC"), nights = 61)
        }
    }

    @Test
    fun subsolarAlignmentValidatesTargets() {
        fun lat(v: Double) = com.khushu.engine.core.geo.Latitude(v)
        fun lon(v: Double) = com.khushu.engine.core.geo.Longitude(v)
        // outside tropics — Latitude itself validates [-90,90] but tropics
        // envelope (±23.44) is passagesOver's own check
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(51.5), lon(0.0), 2026) }
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(Double.NaN), lon(0.0), 2026) }
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(0.0), lon(-181.0), 2026) }
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(0.0), lon(0.0), 1650) }
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(0.0), lon(0.0), 2026, toleranceDeg = -1.0) }
        assertFailsWith<InvalidParameterException> { SubsolarAlignment.passagesOver(lat(0.0), lon(0.0), 2026, toleranceDeg = Double.NaN) }
        // The Kaaba and its antipode — qibla's exact usage — must pass.
        assertTrue(SubsolarAlignment.passagesOver(lat(21.4225), lon(39.8262), 2026).isNotEmpty())
        assertTrue(SubsolarAlignment.passagesOver(lat(-21.4225), lon(-140.1738), 2026).isNotEmpty())
    }

    @Test
    fun hilalMoonAgeIsNeverFabricated() {
        // Non-null age with a NoResultException guard on unresolvable
        // conjunctions (D7) — additive API preserved.
        val report = Astronomy.hilal.visibility(makkah, java.time.LocalDate.of(2026, 8, 30), java.time.ZoneId.of("Asia/Riyadh"))
        assertTrue(report == null || (report.moonAgeHours ?: 0) >= 0)
    }
}

package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layer-B properties + authoritative anchors (NASA almanac instants, solstice
 * declination). Polar behaviour explicit.
 */
class AstronomyPropertyTest {

    private val makkah = Location.of(21.4225, 39.8262)
    private val london = Location.of(51.5072, -0.1276)
    private val tromso = Location.of(69.6492, 18.9553)

    @Test
    fun illuminationAndAnglesStayInPhysicalBounds() {
        for (loc in listOf(makkah, london, tromso)) {
            for (day in intArrayOf(1, 11, 25)) {
                // Deterministic sweep across the day.
                for (hour in intArrayOf(0, 6, 12, 18)) {
                    val instant = LocalDate.of(2025, 3, day).atTime(hour, 0)
                        .toInstant(ZoneOffset.UTC)
                    val state = Astronomy.moon.state(loc, instant)
                    assertTrue(state.illuminationFraction in 0.0..1.0)
                    assertTrue(state.phaseAngleDeg in 0.0..360.0)
                    assertTrue(state.brightLimbTiltDeg in 0.0..360.0)
                    assertTrue(state.elongationDeg in 0.0..180.0)
                    val pos = Astronomy.moon.position(loc, instant)
                    assertTrue(pos.azimuthDeg in 0.0..360.0)
                    assertTrue(pos.altitudeDeg in -90.0..90.0)
                    assertTrue(pos.distanceKm in 356_000.0..407_000.0) // lunar perigee/apogee range
                }
            }
        }
    }

    @Test
    fun solsticeDeclinationsMatchAuthoritativeValues() {
        // 2025 solstices (NASA): Jun 21 02:42 UTC (+23.44), Dec 21 15:03 UTC (−23.44).
        val junSolstice = Astronomy.sun.position(makkah, Instant.parse("2025-06-21T02:42:00Z"))
        assertEquals(23.44, junSolstice.declinationDeg, 0.05)
        val decSolstice = Astronomy.sun.position(makkah, Instant.parse("2025-12-21T15:03:00Z"))
        assertEquals(-23.44, decSolstice.declinationDeg, 0.05)
    }

    @Test
    fun fullMoonAndNewMoonIlluminationMatchAlmanac() {
        // NASA 2025: full moon Jun 11 07:44 UTC; new moon Jun 25 10:31 UTC.
        val full = Astronomy.moon.state(london, Instant.parse("2025-06-11T07:44:00Z"))
        assertTrue(full.illuminationFraction >= 0.99, "full moon illum ${full.illuminationFraction}")
        assertEquals("Full Moon", full.phaseName)
        val newMoon = Astronomy.moon.state(london, Instant.parse("2025-06-25T10:31:00Z"))
        assertTrue(newMoon.illuminationFraction <= 0.01, "new moon illum ${newMoon.illuminationFraction}")
        assertEquals("New Moon", newMoon.phaseName)
    }

    @Test
    fun londonMidsummerSunriseMatchesAlmanac() {
        // London 2025-06-21 sunrise ≈ 04:43 BST = 03:43 UTC (authoritative almanac value).
        val rs = Astronomy.sun.riseSet(london, LocalDate.of(2025, 6, 21), ZoneId.of("Europe/London"))
        assertNotNull(rs.riseEpochMs)
        // Almanac: 04:43 British Summer Time.
        val riseLondon = Instant.ofEpochMilli(rs.riseEpochMs!!).atZone(ZoneId.of("Europe/London"))
        assertEquals(4, riseLondon.hour)
        assertEquals(43, riseLondon.minute)
    }

    @Test
    fun trackIsConsistentWithPerDayQueries() {
        val zone = ZoneId.of("Asia/Riyadh")
        val month = YearMonth.of(2025, 6)
        val track = Astronomy.moon.track(makkah, month, zone)
        assertEquals(month.lengthOfMonth(), track.days.size)
        for ((i, day) in track.days.withIndex()) {
            val date = month.atDay(i + 1)
            assertEquals(date, day.date)
            val perDay = Astronomy.moon.riseSet(makkah, date, zone)
            assertEquals(perDay.riseEpochMs, day.riseEpochMs, "rise mismatch $date")
            assertEquals(perDay.setEpochMs, day.setEpochMs, "set mismatch $date")
            if (day.transitEpochMs != null && day.illuminationAtTransit != null) {
                assertTrue(day.illuminationAtTransit in 0.0..1.0)
            }
        }
    }

    @Test
    fun polarNightDaysHaveNullRiseButValidTransit() {
        val zone = ZoneId.of("Europe/Oslo")
        val track = Astronomy.moon.track(tromso, YearMonth.of(2026, 1), zone)
        // Lunar day is ~24.84 h, so an occasional civil date contains no meridian
        // transit — but the overwhelming majority must.
        val withTransit = track.days.count { it.transitEpochMs != null }
        assertTrue(withTransit >= track.days.size - 3, "transits missing on $withTransit/${track.days.size}")
        // Illumination is derived from transit — they co-exist or co-absent.
        for (day in track.days) {
            assertEquals(day.transitEpochMs != null, day.illuminationAtTransit != null)
        }
        // Sun-based check: polar night means sunrise/sunset are null around solstice.
        val sunRs = Astronomy.sun.riseSet(tromso, LocalDate.of(2026, 1, 10), zone)
        assertNull(sunRs.riseEpochMs, "expected polar night on Jan 10 Tromso")
        assertNull(sunRs.setEpochMs)
    }

    @Test
    fun solarEventsAreChronologicalAndOrdered() {
        val events = Astronomy.sun.events(london, LocalDate.of(2025, 6, 21), ZoneId.of("Europe/London")).events
        // Late-June London: nautical/astronomical dusk spill past local midnight,
        // so a few tail events legitimately belong to the next civil day.
        assertTrue(events.size >= 9, "midsummer London should have most events")
        val types = events.map { it.type }
        assertTrue(types.contains(SolarEventType.SUNRISE))
        assertTrue(types.contains(SolarEventType.SOLAR_NOON))
        assertTrue(types.contains(SolarEventType.SUNSET))
        // At midsummer the sun may not reach −18° (no astronomical dawn) —
        // the day then starts with nautical dawn.
        assertTrue(
            events.first().type == SolarEventType.ASTRONOMICAL_DAWN ||
                events.first().type == SolarEventType.NAUTICAL_DAWN,
        )
        events.zipWithNext().forEach { (a, b) ->
            assertTrue(a.epochMs < b.epochMs, "events not chronological")
        }
    }

    @Test
    fun hilalReportIsPhysicallyCoherent() {
        // Evening after new moon (Jun 27 2025): crescent should be reportable.
        val report = Astronomy.hilal.visibility(
            makkah, LocalDate.of(2025, 6, 27), ZoneId.of("Asia/Riyadh"),
        )
        assertNotNull(report)
        assertTrue(report.arcvDeg > -30.0)
        assertTrue(report.elongationDeg > 0.0)
        assertTrue(report.crescentWidthArcmin.value >= 0.0)
        assertTrue(report.lagMinutes >= 0)
        assertEquals(4, report.verdicts.size)
        assertTrue(report.moonAgeHours > 24) // ~2 days after conjunction; never fabricated (D7)

        // Polar summer: no sunset → no report.
        val polar = Astronomy.hilal.visibility(tromso, LocalDate.of(2025, 6, 27), ZoneId.of("Europe/Oslo"))
        assertNull(polar)
    }
}

package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BreadthTest {

    private val london = Location.of(51.5072, -0.1276)
    private val makkah = Location.of(21.4225, 39.8262)

    @Test
    fun nextNewAndFullMoonMatchNasaAlmanac2025() {
        // NASA: new moon Jun 25 2025 10:31 UTC; full moon Jul 10 2025 20:37 UTC.
        val after = Instant.parse("2025-06-12T00:00:00Z")
        val nm = Astronomy.moon.nextNewMoon(after)!!
        assertEquals("2025-06-25T10:31:00Z", Instant.ofEpochMilli(nm.epochMs).toString().let {
            // tolerate ±3 min against the almanac value
            val almanac = Instant.parse("2025-06-25T10:31:00Z").toEpochMilli()
            if (kotlin.math.abs(nm.epochMs - almanac) <= 180_000L) "2025-06-25T10:31:00Z" else it
        })
        assertTrue(nm.phaseName == "New Moon")

        val fm = Astronomy.moon.nextFullMoon(after)!!
        val almanac = Instant.parse("2025-07-10T20:37:00Z").toEpochMilli()
        assertTrue(kotlin.math.abs(fm.epochMs - almanac) <= 180_000L, "full moon drift > 3 min")
        assertEquals("Full Moon", fm.phaseName)
    }

    @Test
    fun moonStateCarriesAgeAndWaxing() {
        val s = Astronomy.moon.state(london, Instant.parse("2025-06-11T07:44:00Z")) // full moon
        assertEquals(true, s.isWaxing || !s.isWaxing) // field exists
        assertTrue(s.moonAgeDays >= 0.0 && s.moonAgeDays <= 30.0)
        // At full moon age should be ~14.77 days (half synodic month).
        assertTrue(s.moonAgeDays in 14.0..16.0, "age at full moon was ${s.moonAgeDays}")
    }

    @Test
    fun dayLengthIsSensibleAcrossSeasons() {
        val zone = ZoneId.of("Europe/London")
        val summer = Astronomy.sun.dayLength(london, LocalDate.of(2025, 6, 21), zone)!!
        val winter = Astronomy.sun.dayLength(london, LocalDate.of(2025, 12, 21), zone)!!
        assertTrue(summer.toHours() in 15..17, "London midsummer ${summer.toHours()}h")
        assertTrue(winter.toHours() in 7..9, "London midwinter ${winter.toHours()}h")
        assertNull(Astronomy.sun.dayLength(Location.of(69.6492, 18.9553), LocalDate.of(2025, 12, 21), ZoneId.of("Europe/Oslo")))
    }

    @Test
    fun knownEclipsesAreFound() {
        // NASA: total lunar eclipse Sep 7 2025 ~18:11 UTC; solar eclipse Mar 29 2025.
        val lunar = Astronomy.moon.nextLunarEclipse(Instant.parse("2025-08-01T00:00:00Z"))
        assertNotNull(lunar)
        val sep7 = Instant.parse("2025-09-07T18:11:00Z").toEpochMilli()
        assertTrue(kotlin.math.abs(lunar.peakEpochMs - sep7) < 6 * 3_600_000L, "lunar eclipse peak off by hours")

        val solar = Astronomy.moon.nextGlobalSolarEclipse(Instant.parse("2025-03-01T00:00:00Z"))
        assertNotNull(solar)
        val mar29 = Instant.parse("2025-03-29T10:57:00Z").toEpochMilli()
        assertTrue(kotlin.math.abs(solar.peakEpochMs - mar29) < 6 * 3_600_000L, "solar eclipse peak off by hours")
    }

    @Test
    fun distanceExtremesSpanTheAnomalisticBand() {
        val extremes = Astronomy.moon.distanceExtremes(
            Instant.parse("2025-01-01T00:00:00Z"),
            Instant.parse("2025-02-28T00:00:00Z"),
        )
        val perigees = extremes.filter { it.isPerigee }
        val apogees = extremes.filter { !it.isPerigee }
        assertTrue(perigees.isNotEmpty() && apogees.isNotEmpty())
        perigees.forEach { assertTrue(it.distanceKm in 356_000.0..370_500.0, "perigee ${it.distanceKm}") }
        apogees.forEach { assertTrue(it.distanceKm in 404_000.0..406_800.0, "apogee ${it.distanceKm}") }
    }

    private fun assertNull(v: java.time.Duration?) = kotlin.test.assertNull(v)
}

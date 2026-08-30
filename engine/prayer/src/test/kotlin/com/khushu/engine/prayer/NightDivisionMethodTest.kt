package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * v1.14 NightDivisionMethod wiring — validated against Muwaqqit's documented
 * sharʿī-night convention (night fractions run Maghrib → next Fajr; their
 * Kohat 2026-08-30 fixture values are CC-BY attributed in the astronomy
 * fixture). The SUNSET_TO_FAJR variant is the alternate division.
 */
class NightDivisionMethodTest {

    private val kohat = Location.of(33.5888559, 71.4429286, 0.0)
    private val zone = ZoneId.of("Asia/Karachi")
    private val date = LocalDate.of(2026, 8, 30)

    private val config = PrayerConfiguration(
        convention = Convention.CUSTOM,
        fajrAngle = 19.0,
        ishaAngle = 18.0,
    )

    // Muwaqqit fixture (CC-BY): fractions of the night starting Aug 30 maghrib.
    private val thirdOne = Instant.parse("2026-08-30T21:53:46+05:00")
    private val half = Instant.parse("2026-08-30T23:29:11+05:00")
    private val thirdTwo = Instant.parse("2026-08-31T01:04:36+05:00")
    private val sixthsFive = Instant.parse("2026-08-31T02:40:02+05:00")

    @Test
    fun maghribToFajrIsTheDefaultAndMatchesMuwaqqit() {
        val nd = Prayer.nightDivisions(kohat, date, config)
        assertNotNull(nd)
        val t = { i: Instant -> i.toEpochMilli() }
        assertTrue(kotlin.math.abs(t(nd.firstThirdBegins) - t(thirdOne)) <= 120_000, "⅓: ${nd.firstThirdBegins}")
        assertTrue(kotlin.math.abs(t(nd.midpoint) - t(half)) <= 120_000, "½: ${nd.midpoint}")
        assertTrue(kotlin.math.abs(t(nd.lastThirdBegins) - t(thirdTwo)) <= 150_000, "⅔: ${nd.lastThirdBegins}")
        // night length = maghrib → fajr; five-sixths derived on the same span
        val durMs = t(nd.nightEnds) - t(nd.nightStarts)
        val fiveSixths = t(nd.nightStarts) + 5 * durMs / 6
        assertTrue(kotlin.math.abs(fiveSixths - t(sixthsFive)) <= 120_000, "⅚ on the same span")
    }

    @Test
    fun explicitMaghribMethodEqualsDefault() {
        val a = Prayer.nightDivisions(kohat, date, config)
        val b = Prayer.nightDivisions(kohat, date, config, Prayer.NightDivisionMethod.MAGHRIB_TO_FAJR)
        assertTrue(a == b, "default must be MAGHRIB_TO_FAJR")
    }

    @Test
    fun sunsetToFajrStartsAtSunsetAndStaysOrdered() {
        val maghrib = Prayer.nightDivisions(kohat, date, config, Prayer.NightDivisionMethod.MAGHRIB_TO_FAJR)!!
        val sunset = Prayer.nightDivisions(kohat, date, config, Prayer.NightDivisionMethod.SUNSET_TO_FAJR)!!
        // sunset ≤ maghrib-adjusted start; both end at tomorrow's fajr
        assertTrue(sunset.nightStarts <= maghrib.nightStarts, "sunset precedes maghrib")
        assertTrue(sunset.nightStarts < sunset.firstThirdBegins)
        assertTrue(sunset.firstThirdBegins < sunset.midpoint)
        assertTrue(sunset.midpoint < sunset.lastThirdBegins)
        assertTrue(sunset.lastThirdBegins < sunset.nightEnds)
        // the sunset-variant night is never shorter than the maghrib one
        val durSunset = sunset.nightEnds.toEpochMilli() - sunset.nightStarts.toEpochMilli()
        val durMaghrib = maghrib.nightEnds.toEpochMilli() - maghrib.nightStarts.toEpochMilli()
        assertTrue(durSunset >= durMaghrib)
    }

    @Test
    fun lastThirdIsTwoThirdsOfEitherSpan() {
        for (method in Prayer.NightDivisionMethod.entries) {
            val nd = Prayer.nightDivisions(kohat, date, config, method)!!
            val dur = nd.nightEnds.toEpochMilli() - nd.nightStarts.toEpochMilli()
            val expectedLastThird = nd.nightStarts.toEpochMilli() + 2 * dur / 3
            assertTrue(
                kotlin.math.abs(nd.lastThirdBegins.toEpochMilli() - expectedLastThird) <= 2_000,
                "$method lastThird at ⅔ of span",
            )
        }
    }
}

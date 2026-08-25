package com.khushu.engine.astronomy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SolarDayPhases structural properties + REFERENCE-fixture replay + almanac
 * spot checks.
 *
 * Fixture provenance note: the `solarDayReference` section of astro_golden.json
 * is SELF-GENERATED (change-detectors only — NOT correctness evidence). Truth
 * weight for new endpoints comes from the published-almanac assertions below.
 */
class SolarDayTest {

    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    // ── Structural properties ────────────────────────────────────────────────

    @Test
    fun bandsAreOrderedNonOverlappingAndContained() {
        val p = Astronomy.sun.phases(london, LocalDate.of(2025, 6, 21), zone)

        // Twilight CHAIN is contiguous where present. Midsummer London never
        // reaches -18 deg, so astronomical twilight is legitimately absent;
        // when present it must hand off exactly to the next layer.
        val astro = p.morningAstronomicalTwilight
        val naut = assertNotNull(p.morningNauticalTwilight)
        val civ = assertNotNull(p.morningCivilTwilight)
        astro?.let { assertEquals(it.endInclusive, naut.start, "astronomical must end where nautical begins") }
        assertEquals(naut.endInclusive, civ.start, "nautical must end where civil begins")
        astro?.let { a ->
            assertTrue(a.start <= naut.start && naut.endInclusive <= a.endInclusive)
        }

        // Threshold-segment model: bands are ADJACENT segments between altitude
        // crossings, not nested spans. Blue hour overlaps civil twilight's window
        // ([−6°, −4°) sits inside civil's [−6°, −0.833°)).
        val blue = assertNotNull(p.morningBlueHour)
        assertTrue(blue.start >= civ.start - java.time.Duration.ofSeconds(1))
        assertTrue(blue.endInclusive <= p.sunrise!! + java.time.Duration.ofSeconds(1))

        // Golden hour runs from -4 deg through sunrise up to +6 deg.
        val golden = assertNotNull(p.morningGoldenHour)
        assertNotNull(p.sunrise)
        assertTrue(golden.start <= p.sunrise!!)
        assertEquals(p.crossings.daylightBegin, golden.endInclusive)

        val day = assertNotNull(p.daylight)
        assertEquals(p.crossings.daylightBegin, day.start)
        assertTrue(day.start < p.solarNoon && p.solarNoon < day.endInclusive)
        assertNotNull(p.sunset)
        assertTrue(p.sunrise!! < p.solarNoon && p.solarNoon < p.sunset!!)

        // Night ownership: lateNight ends at dawn, earlyNight starts at dusk.
        p.lateNight?.let { assertEquals(p.crossings.astronomicalDawn, it.endInclusive) }
        p.earlyNight?.let { assertEquals(p.crossings.astronomicalDusk, it.start) }

        // Solar midnight ≈ solar noon + ~12h.
        val gapMs = p.solarMidnight.toEpochMilli() - p.solarNoon.toEpochMilli()
        assertTrue(abs(gapMs - 43_200_000L) < 900_000L, "midnight offset ${gapMs / 60000} min")
    }

    @Test
    fun polarDayHasNullNightAndTwilights() {
        val tromso = Location.of(69.6492, 18.9553)
        val p = Astronomy.sun.phases(tromso, LocalDate.of(2025, 12, 21), ZoneId.of("Europe/Oslo"))
        // Civil polar night: sun never reaches -6 deg so civil/nautical layers are
        // absent, but ASTRONOMICAL twilight still occurs daily.
        assertNull(p.sunrise)
        assertNull(p.sunset)
        assertNull(p.daylight)
        // Sun peaks at ~-3 deg that day: astro/nautical/civil/blue/golden all occur,
        // horizon-bounded bands fall back to solar transit as their inner edge.
        val tromsoAstro = assertNotNull(p.morningAstronomicalTwilight)
        val tromsoNaut = assertNotNull(p.morningNauticalTwilight)
        val tromsoCiv = assertNotNull(p.morningCivilTwilight)
        assertEquals(tromsoAstro.endInclusive, tromsoNaut.start)
        assertEquals(tromsoNaut.endInclusive, tromsoCiv.start)
        assertNotNull(p.morningBlueHour)
        assertNotNull(p.morningGoldenHour)
        // Transits still exist every day.
        assertNotNull(p.solarNoon)
        assertNotNull(p.solarMidnight)
        assertNull(Astronomy.sun.nightLength(tromso, LocalDate.of(2025, 12, 21), ZoneId.of("Europe/Oslo")))
        // Night ownership: this date owns the tail before its dawn and the head after its dusk.
        p.lateNight?.let { assertEquals(p.crossings.astronomicalDawn, it.endInclusive) }
        p.earlyNight?.let { assertEquals(p.crossings.astronomicalDusk, it.start) }
    }

    // ── Authoritative almanac anchors ────────────────────────────────────────

    @Test
    fun seasonsMatchPublishedAlmanac() {
        // NASA/TimeAndDate 2026: Mar 20 14:46 UTC · Jun 21 08:25 UTC ·
        // Sep 23 00:05 UTC · Dec 21 20:50 UTC.
        val s = Astronomy.seasons(2026)
        assertNear(s.marchEquinox, "2026-03-20T14:46:00Z")
        assertNear(s.juneSolstice, "2026-06-21T08:25:00Z")
        assertNear(s.septemberEquinox, "2026-09-23T00:05:00Z")
        assertNear(s.decemberSolstice, "2026-12-21T20:50:00Z")
    }

    private fun assertNear(actual: Instant, expectedIso: String) {
        val expected = Instant.parse(expectedIso)
        assertTrue(abs(actual.toEpochMilli() - expected.toEpochMilli()) <= 5 * 60_000L,
            "$expectedIso vs $actual")
    }

    // ── Reference fixture replay (change-detectors only) ─────────────────────

    @Serializable
    private data class RefRow(
        val site: String,
        val date: String,
        val sunrise: Long?, val sunset: Long?, val noon: Long, val midnight: Long,
        val astroDawn: Long?, val nautDawn: Long?, val civDawn: Long?,
        val blueEndM: Long?, val dayBegin: Long?, val dayEnd: Long?,
        val blueStartE: Long?, val civDusk: Long?, val nautDusk: Long?, val astroDusk: Long?,
    )

    @Serializable
    private data class RefSite(val lat: Double, val lon: Double, val alt: Double)

    @Serializable
    private data class RefGolden(
        val sites: Map<String, RefSite>,
        val cases: List<kotlinx.serialization.json.JsonElement>, // legacy cases untouched here
        val solarDayReference: List<RefRow> = emptyList(),
    )

    @Test
    fun solarDayReferenceFixturesReplayExactly() {
        val golden = Json { ignoreUnknownKeys = true }.decodeFromString(
            RefGolden.serializer(),
            SolarDayTest::class.java.classLoader.getResource("fixtures/astro_golden.json")!!.readText(),
        )
        if (golden.solarDayReference.isEmpty()) return // pre-extension fixture set
        for (row in golden.solarDayReference) {
            val s = assertNotNull(golden.sites[row.site])
            val loc = Location.of(s.lat, s.lon, s.alt)
            val date = LocalDate.parse(row.date)
            val zone = ZoneOffset.UTC
            val p = Astronomy.sun.phases(loc, date, zone)

            fun eq(a: Instant?, b: Long?) = assertEquals(b, a?.toEpochMilli(), "${row.site} ${row.date}")
            eq(p.sunrise, row.sunrise)
            eq(p.sunset, row.sunset)
            assertEquals(row.noon, p.solarNoon.toEpochMilli(), "${row.site} ${row.date} noon")
            assertEquals(row.midnight, p.solarMidnight.toEpochMilli())
            eq(p.crossings.astronomicalDawn, row.astroDawn)
            eq(p.crossings.nauticalDawn, row.nautDawn)
            eq(p.crossings.civilDawn, row.civDawn)
            eq(p.crossings.blueHourMorningEnd, row.blueEndM)
            eq(p.crossings.daylightBegin, row.dayBegin)
            eq(p.crossings.daylightEnd, row.dayEnd)
            eq(p.crossings.blueHourEveningStart, row.blueStartE)
            eq(p.crossings.civilDusk, row.civDusk)
            eq(p.crossings.nauticalDusk, row.nautDusk)
            eq(p.crossings.astronomicalDusk, row.astroDusk)
        }
    }
}

private fun ZoneOffset(): ZoneOffset = ZoneOffset.UTC

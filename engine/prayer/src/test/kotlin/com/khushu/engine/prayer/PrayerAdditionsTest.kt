package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerAdditionsTest {

    private val london = Location.of(51.5072, -0.1276)
    private val makkah = Location.of(21.4225, 39.8262)
    private val londonZone = ZoneId.of("Europe/London")
    private val makkahZone = ZoneId.of("Asia/Riyadh")

    @Test
    fun nightDivisionsSplitTheNightIntoStrictlyOrderedThirds() {
        val d = LocalDate.of(2026, 3, 14) // near equinox, ordinary night
        val nd = Prayer.nightDivisions(london, d)
        assertNotNull(nd)
        assertTrue(nd.nightStarts < nd.firstThirdBegins)
        assertTrue(nd.firstThirdBegins < nd.midpoint)
        assertTrue(nd.midpoint < nd.lastThirdBegins)
        assertTrue(nd.lastThirdBegins < nd.nightEnds)

        val durMs = nd.nightEnds.toEpochMilli() - nd.nightStarts.toEpochMilli()
        val expectedFirst = nd.nightStarts.toEpochMilli() + durMs / 3
        assertEquals(expectedFirst, nd.firstThirdBegins.toEpochMilli())
    }

    @Test
    fun nightDivisionsMidpointMatchesTahajjudWindowMidnight() {
        val d = LocalDate.of(2026, 1, 20)
        val nd = Prayer.nightDivisions(london, d)
        val tw = Prayer.tahajjudWindow(london, d)
        assertNotNull(nd)
        assertNotNull(tw)
        assertEquals(tw.windowOpens, nd.midpoint)
        assertEquals(tw.windowCloses, nd.nightEnds)
    }

    @Test
    fun nextOccurrenceOfReturnsStrictlyFutureInstanceOfRequestedKind() {
        val anchor = LocalDate.of(2026, 3, 10).atTime(13, 0).atZone(londonZone).toInstant()
        val nextFajr = Prayer.nextOccurrenceOf(PrayerStatus.Prayer.FAJR, london, anchor, londonZone)
        assertNotNull(nextFajr)
        assertEquals(PrayerStatus.Prayer.FAJR, nextFajr.kind)
        assertTrue(nextFajr.instant > anchor)

        // Must agree with the next day's computed fajr.
        val tomorrow = Prayer.times(london, LocalDate.ofInstant(nextFajr.instant, londonZone))
        assertEquals(tomorrow.fajr.adjusted, nextFajr.instant)
    }

    @Test
    fun nextJumuahIsAFridayStrictlyAfterTheAnchor() {
        // A known Wednesday.
        val anchor = LocalDate.of(2026, 8, 26).atTime(9, 0).atZone(londonZone).toInstant()
        val j = Prayer.nextJumuah(london, anchor, londonZone)
        assertNotNull(j)
        assertEquals(DayOfWeek.FRIDAY, j.date.dayOfWeek)
        assertTrue(j.dhuhr > anchor)
        assertEquals(LocalDate.of(2026, 8, 28), j.date)

        // Anchor on Friday AFTER dhuhr → must land on the following Friday.
        val fridayEvening = j.date.atTime(21, 0).atZone(londonZone).toInstant()
        val next = Prayer.nextJumuah(london, fridayEvening, londonZone)
        assertNotNull(next)
        assertEquals(j.date.plusDays(7), next.date)
    }

    @Test
    fun nextJumuahUsesTransitAnchorForDhuhr() {
        val anchor = Instant.parse("2026-08-26T09:00:00Z")
        val j = Prayer.nextJumuah(makkah, anchor, makkahZone)
        assertNotNull(j)
        val day = Prayer.times(makkah, j.date)
        assertEquals(day.dhuhr.adjusted ?: day.dhuhrEnters, j.dhuhr)
    }

    @Test
    fun fastingFactsForMonthCoversEveryDayInOrder() {
        val ym = YearMonth.of(2026, 2) // 28 days
        val facts = Prayer.fastingFactsForMonth(london, ym)
        assertEquals(28, facts.size)

        val leap = YearMonth.of(2028, 2) // 29 days
        assertEquals(29, Prayer.fastingFactsForMonth(london, leap).size)

        // Every entry equals the per-day computation.
        val first = Prayer.fastingFacts(london, ym.atDay(1))
        assertEquals(first, facts.first())

        // Imsak precedes fast start wherever both exist.
        facts.forEach { f ->
            if (f.imsak != null && f.fastStart != null) assertTrue(f.imsak < f.fastStart)
        }
    }

    @Test
    fun polarSummerNightDivisionsDegradeToNullNotBogusValues() {
        val tromso = Location.of(69.6492, 18.9553)
        // Polar day: fajr/maghrib adjusted may both be missing.
        val nd = Prayer.nightDivisions(tromso, LocalDate.of(2026, 6, 21))
        if (nd != null) {
            // If boundaries exist, ordering must still hold.
            assertTrue(nd.nightStarts < nd.nightEnds)
            assertTrue(nd.firstThirdBegins < nd.lastThirdBegins)
        }
        // nextJumuah must still resolve everywhere — dhuhr has a transit anchor.
        val j = Prayer.nextJumuah(tromso, Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Europe/Oslo"))
        assertNotNull(j)
        assertEquals(DayOfWeek.FRIDAY, j.date.dayOfWeek)
    }

    @Test
    fun upcomingAfterMaghribRotatesThroughTomorrowFajr() {
        val d = LocalDate.of(2026, 3, 14)
        val t = Prayer.times(london, d)
        val afterMaghrib = assertNotNull(t.maghrib.adjusted).plusSeconds(60)
        val entries = Prayer.upcoming(london, afterMaghrib, londonZone, count = 3)
        assertEquals(
            listOf(PrayerStatus.Prayer.MAGHRIB, PrayerStatus.Prayer.ISHA, PrayerStatus.Prayer.FAJR),
            entries.map { it.kind },
        )
        assertEquals(
            d.plusDays(1),
            entries.last().instant.atZone(londonZone).toLocalDate(),
            "rotation must span midnight into tomorrow's Fajr",
        )
    }

    @Test
    fun upcomingRejectsNonPositiveCount() {
        val now = Instant.parse("2026-03-14T12:00:00Z")
        assertFailsWith<com.khushu.engine.core.error.InvalidParameterException> {
            Prayer.upcoming(london, now, londonZone, count = 0)
        }
    }

    @Test
    fun occasionsOnCategoryFilterRestrictsResults() {
        val d = LocalDate.of(2026, 3, 14)
        val all = Prayer.occasionsOn(london, d)
        val voluntary = Prayer.occasionsOn(london, d, setOf(Prayer.OccasionCategory.VOLUNTARY))
        val boundary = Prayer.occasionsOn(london, d, setOf(Prayer.OccasionCategory.BOUNDARY_EVENT))
        val obligatory = Prayer.occasionsOn(london, d, setOf(Prayer.OccasionCategory.OBLIGATORY))
        assertTrue(all.isNotEmpty())
        assertTrue(voluntary.isNotEmpty())
        assertTrue(voluntary.all { it.category == Prayer.OccasionCategory.VOLUNTARY })
        assertTrue(boundary.all { it.category == Prayer.OccasionCategory.BOUNDARY_EVENT })
        assertTrue(obligatory.all { it.category == Prayer.OccasionCategory.OBLIGATORY })
        assertEquals(all.size, voluntary.size + boundary.size + obligatory.size)
        assertTrue(Prayer.occasionsOn(london, d, emptySet()).isEmpty())
        // results stay time-ordered after filtering
        assertEquals(voluntary, voluntary.sortedBy { it.instant })
    }
}

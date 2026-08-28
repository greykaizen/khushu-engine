package com.khushu.engine.facadetest

import com.khushu.engine.KhushuEngine
import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.CivilCalendarType
import com.khushu.engine.calendar.DateLine
import com.khushu.engine.calendar.FastRule
import com.khushu.engine.core.geo.Location
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarBreadthTest {

    private val engine = KhushuEngine()
    private val config = CalendarConfiguration(primary = CalendarConfiguration.Side.HIJRI)
    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    @Test
    fun upcomingEventsMatchesRangeScanAtDefaultAndNonZeroOffset() {
        for (offset in listOf(0, 1, -1)) {
            val cfg = CalendarConfiguration(
                primary = CalendarConfiguration.Side.HIJRI,
                hijriOffsetDays = offset,
            )
            val after = LocalDate.of(2026, 8, 20)
            val horizon = after.plusYears(3).minusDays(1)

            val scanned = engine.calendar.eventsInRange(after..horizon, offset)
                .map { it.first to it.second.title }
                .take(60)
            val fast = engine.calendar.facts.upcomingEvents(after, 60, cfg)

            assertEquals(scanned, fast, "offset $offset: definition-driven must equal the day scan")
        }
    }

    @Test
    fun upcomingEventsHonorsCountAndStaysAscending() {
        val after = LocalDate.of(2026, 1, 1)
        val five = engine.calendar.facts.upcomingEvents(after, 5, config)
        assertEquals(5, five.size)
        assertTrue(five.zipWithNext().all { (a, b) -> a.first <= b.first })
        assertEquals(five, engine.calendar.facts.upcomingEvents(after, 20, config).take(5))
    }

    @Test
    fun daysUntilMatchesNextOccurrenceArithmetic() {
        val after = LocalDate.of(2026, 2, 10)
        // Ramadan 1 and Eid al-Fitr (Shawwal 1)
        for ((m, d) in listOf(9 to 1, 10 to 1, 12 to 10, 1 to 10)) {
            val next = engine.calendar.nextOccurrence(m, d, after, config.hijriOffsetDays)
            assertEquals(
                java.time.temporal.ChronoUnit.DAYS.between(after, next),
                engine.calendar.facts.daysUntil(m, d, after, config),
            )
        }
        // zero on the day itself
        val eid = engine.calendar.facts.eidAlFitr(1447, config)
        assertEquals(0L, engine.calendar.facts.daysUntil(10, 1, eid, config))
    }

    @Test
    fun hijriDatesExposeChronologicalOrdering() {
        val today = engine.calendar.hijri(LocalDate.of(2026, 8, 28))
        val tomorrow = engine.calendar.hijri(LocalDate.of(2026, 8, 29))
        assertTrue(today < tomorrow || today == tomorrow) // month roll-over only moves forward
        assertTrue(today <= tomorrow)
    }

    @Test
    fun daysSinceIsZeroOnTheDayAndMirrorsPreviousOccurrence() {
        val eid = engine.calendar.facts.eidAlAdha(1447, config)
        assertEquals(0L, engine.calendar.facts.daysSince(12, 10, eid, config))
        assertEquals(1L, engine.calendar.facts.daysSince(12, 10, eid.plusDays(1), config))

        val before = LocalDate.of(2026, 5, 1)
        val prev = engine.calendar.facts.previousOccurrence(12, 10, before, config)
        assertEquals(
            java.time.temporal.ChronoUnit.DAYS.between(prev, before),
            engine.calendar.facts.daysSince(12, 10, before, config),
        )
        assertTrue(!prev.isAfter(before))
        assertTrue(!engine.calendar.nextOccurrence(12, 10, before).isBefore(before))
        // CalendarApi-level passthrough agrees with the facts namespace
        assertEquals(prev, engine.calendar.previousOccurrence(12, 10, before, config.hijriOffsetDays))
    }

    @Test
    fun regionalCivilCalendarRendersThroughDualDatesAndMatrices() {
        val persian = CalendarConfiguration(
            primary = CalendarConfiguration.Side.HIJRI,
            secondary = CalendarConfiguration.Side.GREGORIAN,
            civilCalendar = CivilCalendarType.PERSIAN,
        )
        val dual = engine.calendar.date.both(LocalDate.of(2026, 8, 28), persian)
        val secondary = dual.secondary as DateLine.Regional
        assertEquals(CivilCalendarType.PERSIAN, secondary.date.system)
        assertEquals(Triple(1405, 6, 6), Triple(secondary.date.year, secondary.date.month, secondary.date.day))

        val matrix = engine.calendar.month.monthMatrix(java.time.YearMonth.of(2026, 8), persian)
        assertEquals(31, matrix.size)
        assertTrue(matrix.values.all { (it.secondary as DateLine.Regional).date.system == CivilCalendarType.PERSIAN })

        // Default civil system stays the Gregorian branch (source compatibility).
        val gregorian = CalendarConfiguration(
            primary = CalendarConfiguration.Side.HIJRI,
            secondary = CalendarConfiguration.Side.GREGORIAN,
        )
        assertTrue(engine.calendar.date.both(LocalDate.of(2026, 8, 28), gregorian).secondary is DateLine.Gregorian)
    }

    @Test
    fun civilNamespacePivotsRegionalToGregorianAndBack() {
        for (system in CivilCalendarType.entries.filter { it != CivilCalendarType.JAPANESE }) {
            val d = LocalDate.of(2026, 8, 28)
            val r = engine.calendar.civil.toRegional(d, system)
            assertEquals(d, engine.calendar.civil.fromRegional(system, r.year, r.month, r.day), "$system pivot")
        }
        assertEquals(12, engine.calendar.civil.monthNames(CivilCalendarType.PERSIAN).size)
        assertEquals(13, engine.calendar.civil.monthNames(CivilCalendarType.ETHIOPIAN).size)
    }

    @Test
    fun monthSummaryIsAOnePassComposite() {
        // Muharram 1448 straddles June 2026: Tasu'a/Ashura fire there.
        val ym = java.time.YearMonth.of(2026, 6)
        val params = com.khushu.engine.calendar.CalendarParams(tasuaAshura = true)
        val summary = engine.calendar.month.summary(ym, london, zone, config, params)

        assertEquals(30, summary.days.size)
        assertEquals(ym, summary.yearMonth)
        assertTrue(summary.days.all { it.dual.primary is DateLine.Hijri })
        // Tasu'a and Ashura each fire exactly once under the flag.
        assertEquals(2, summary.days.count { it.fastRules == listOf(FastRule.TASUA_ASHURA) })
        assertTrue(summary.days.flatMap { it.events }.any { it.title == "Day of Ashura" })
        // Moon facts sampled for every day.
        assertTrue(summary.days.all { it.moonIllumination in 0.0..1.0 })
        // Civil dates advance one day at a time.
        assertEquals(summary.days.map { it.date }, (1..30).map { ym.atDay(it) })
    }
}

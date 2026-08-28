package com.khushu.engine.facadetest

import com.khushu.engine.KhushuEngine
import com.khushu.engine.calendar.CalendarConfiguration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarBreadthTest {

    private val engine = KhushuEngine()
    private val config = CalendarConfiguration(primary = CalendarConfiguration.Side.HIJRI)

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
}

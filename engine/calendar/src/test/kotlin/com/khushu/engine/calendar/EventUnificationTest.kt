package com.khushu.engine.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventUnificationTest {

    @Test
    fun seekNightsAndTashreeqComeFromDefinitionsWithUnchangedTitles() {
        val seek21 = HijriCalendar.hijriToGregorian(1447, 9, 21)
        val events21 = HijriCalendar.events(seek21)
        assertTrue(events21.any { it.title == "Laylat al-Qadr Seek-Night (Ramadan 21)" })

        val tashreeq = HijriCalendar.hijriToGregorian(1447, 12, 12)
        val events12 = HijriCalendar.events(tashreeq)
        assertTrue(events12.any { it.title == "Days of Tashreeq" })

        // every seek-night definition exists exactly once
        val seeks = HijriCalendar.builtInDefinitions().filter { it.id.startsWith("qadr_seek_") }
        assertEquals(listOf(21, 23, 25, 27, 29), seeks.map { it.hijriDay })
        assertTrue(seeks.all { it.confidence == Confidence.COMPUTATIONAL })
    }

    @Test
    fun registryOccurrencesNowMatchEventsOnEveryKindOfDay() {
        val registry = EventRegistry()
        val sample = listOf(
            HijriCalendar.hijriToGregorian(1447, 9, 21), // seek-night
            HijriCalendar.hijriToGregorian(1447, 9, 27), // seek-night
            HijriCalendar.hijriToGregorian(1447, 12, 11), // tashreeq
            HijriCalendar.hijriToGregorian(1447, 10, 1), // eid
            HijriCalendar.hijriToGregorian(1447, 1, 10), // ashura
            HijriCalendar.hijriToGregorian(1447, 6, 14), // ordinary day
        )
        for (date in sample) {
            val fromEvents = HijriCalendar.events(date).map { it.title }.sorted()
            val fromRegistry = registry.occurrencesOn(date).map { it.definition.title }.sorted()
            assertEquals(fromEvents, fromRegistry, "events() and registry must agree on $date")
        }
    }

    @Test
    fun daysUntilIsZeroOnTheDayAndWrapsTheHijriYear() {
        val ashura = HijriCalendar.hijriToGregorian(1447, 1, 10)
        assertEquals(0L, HijriCalendar.daysUntil(1, 10, ashura))
        assertEquals(1L, HijriCalendar.daysUntil(1, 10, ashura.minusDays(1)))

        // After this year's Ashura the countdown wraps into the next hijri year:
        // 354 or 355 days, never negative, never huge.
        val wrapped = HijriCalendar.daysUntil(1, 10, ashura.plusDays(1))
        assertTrue(wrapped in 353..355, "year-wrapped countdown was $wrapped")

        // Cross-check against nextOccurrence arithmetic.
        val after = LocalDate.of(2026, 3, 14)
        val next = HijriCalendar.nextOccurrence(9, 1, after)
        assertEquals(
            java.time.temporal.ChronoUnit.DAYS.between(after, next),
            HijriCalendar.daysUntil(9, 1, after),
        )
    }

    @Test
    fun hijriDatesCompareChronologically() {
        val a = HijriCalendar.hijri(LocalDate.of(2026, 1, 1))
        val b = HijriCalendar.hijri(LocalDate.of(2026, 1, 2))
        val aAgain = HijriCalendar.hijri(LocalDate.of(2026, 1, 1))
        assertTrue(a < b)
        assertTrue(b > a)
        assertEquals(0, a.compareTo(aAgain))

        val sorted = listOf(
            HijriCalendar.hijriToGregorian(1448, 1, 1),
            HijriCalendar.hijriToGregorian(1447, 12, 1),
            HijriCalendar.hijriToGregorian(1447, 1, 1),
        ).map { HijriCalendar.hijri(it) }.sorted()
        assertEquals(listOf(1447, 1447, 1448), sorted.map { it.year })
        assertEquals(listOf(1, 12, 1), sorted.map { it.month })
    }
}

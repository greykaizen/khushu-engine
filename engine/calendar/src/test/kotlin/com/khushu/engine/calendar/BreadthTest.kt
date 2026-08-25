package com.khushu.engine.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BreadthTest {

    @Test
    fun reverseConversionRoundTripsThroughForward() {
        // For every civil day of a sampled year: forward → reverse → same day.
        var d = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 12, 31)
        while (d <= end) {
            val h = Calendar.hijri(d)
            val back = Calendar.hijriToGregorian(h.year, h.month, h.day, 0)
            assertEquals(d, back, "round trip failed for $d")
            d = d.plusDays(1)
        }
    }

    @Test
    fun offsetRoundTripIsConsistent() {
        // hijri(date, +2) must equal hijri(date+2 civil days, 0) — the offset is
        // exactly a civil-day shift.
        for (day in intArrayOf(1, 15, 27)) {
            val base = LocalDate.of(2025, 5, day.coerceAtLeast(1))
            val shifted = Calendar.hijri(base, 2)
            val moved = Calendar.hijri(base.plusDays(2), 0)
            assertEquals(moved.year, shifted.year)
            assertEquals(moved.month, shifted.month)
            assertEquals(moved.day, shifted.day)
        }
    }

    @Test
    fun monthLengthsAre29Or30AndSumToYearLength() {
        val lengths = Calendar.hijriMonthLengths(1446)
        assertEquals(12, lengths.size)
        assertTrue(lengths.all { it == 29 || it == 30 }, "lengths $lengths")
        val sum = lengths.sum()
        assertTrue(sum in 354..355, "hijri year length $sum")
        // Ramadan 1446 began 2025-03-01 and Eid was 2025-03-30 → Ramadan had 29 days.
        assertEquals(29, lengths[8]) // index 8 = month 9 (Ramadan)
    }

    @Test
    fun nonExistentDay30Throws() {
        // Muharram 1447? Use a known 29-day month: Ramadan 1446 (verified above).
        assertFailsWith<IllegalArgumentException> {
            Calendar.hijriToGregorian(1446, 9, 30)
        }
    }

    @Test
    fun nextOccurrenceFindsFutureDates() {
        // Next 1 Ramadan after Jan 2025 is itself 2025-03-01; the one after that is 2026-02-18 (tabular).
        val first = Calendar.nextOccurrence(9, 1, LocalDate.of(2025, 1, 10))
        assertEquals(LocalDate.of(2025, 3, 1), first)
        val second = Calendar.nextOccurrence(9, 1, LocalDate.of(2025, 3, 2))
        // Verify via forward conversion rather than hardcoding the second year.
        val secondHijri = Calendar.hijri(second)
        assertEquals(9, secondHijri.month)
        assertEquals(1, secondHijri.day)
        assertTrue(second.isAfter(LocalDate.of(2025, 3, 2)))
    }

    @Test
    fun eventsInRangeCoversEidsOfTwoYears() {
        val events = Calendar.eventsInRange(LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 12, 31))
        val titles = events.map { it.second.title }.toSet()
        assertTrue("Ramadan Begins" in titles)
        assertTrue("Eid al-Fitr" in titles)
        assertTrue("Eid al-Adha" in titles)
        assertTrue(events.all { it.first.year == 2025 })
    }

    @Test
    fun sacredMonthsAreTheClassicalFour() {
        assertTrue(Calendar.isSacredMonth(1))
        assertTrue(Calendar.isSacredMonth(7))
        assertTrue(Calendar.isSacredMonth(11))
        assertTrue(Calendar.isSacredMonth(12))
        assertTrue(!Calendar.isSacredMonth(9)) // Ramadan is not one of the four sacred months
    }
}

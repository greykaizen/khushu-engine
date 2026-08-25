package com.khushu.engine.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTest {

    @Test
    fun hijriConversionMatchesKnownAnchorDates() {
        // 1 Ramadan 1446 AH began on 2025-03-01 (Umm al-Qura tabular).
        val ramadan = HijriCalendar.hijri(LocalDate.of(2025, 3, 1))
        assertEquals(9, ramadan.month)
        assertEquals(1, ramadan.day)
        assertEquals(1446, ramadan.year)

        // 10 Dhul-Hijjah 1445 per the Umm al-Qura *tabular* calendar = 2024-06-16
        // (official Saudi sighting announced June 17 — tabular vs observed is a
        // known, expected divergence; the offset parameter exists for exactly this).
        val eid = HijriCalendar.hijri(LocalDate.of(2024, 6, 16))
        assertEquals(12, eid.month)
        assertEquals(10, eid.day)
        assertEquals(1445, eid.year)
    }

    @Test
    fun offsetShiftsHijriDateByExactDays() {
        val base = HijriCalendar.hijri(LocalDate.of(2025, 3, 3))
        val shiftedBack = HijriCalendar.hijri(LocalDate.of(2025, 3, 3), -2)
        val shiftedForward = HijriCalendar.hijri(LocalDate.of(2025, 3, 3), 2)

        // Shifting the civil date ±2 days must equal shifting the hijri result ±2 days.
        val backViaDate = HijriCalendar.hijri(LocalDate.of(2025, 3, 1))
        assertEquals(backViaDate.day + 0, shiftedBack.day + 0)
        assertTrue(shiftedBack.offsetApplied == -2 && shiftedForward.offsetApplied == 2)
    }

    @Test
    fun hijriRoundTripsThroughMonthStructure() {
        // Every civil day of two sampled years converts to a valid hijri day,
        // and month lengths are always 29 or 30.
        var lastMonthKey: Pair<Int, Int>? = null
        var dayCounter = 0
        for (date in sequence {
            var d = LocalDate.of(2025, 1, 1)
            while (d <= LocalDate.of(2026, 12, 31)) {
                yield(d)
                d = d.plusDays(1)
            }
        }) {
            val h = HijriCalendar.hijri(date)
            assertTrue(h.month in 1..12)
            assertTrue(h.day in 1..30)
            if (lastMonthKey != null && Pair(h.year, h.month) != lastMonthKey) {
                assertTrue(dayCounter in 29..30, "month $lastMonthKey had $dayCounter days")
            }
            if (lastMonthKey == null || Pair(h.year, h.month) != lastMonthKey) {
                lastMonthKey = Pair(h.year, h.month)
                dayCounter = 0
            }
            dayCounter++
        }
    }

    @Test
    fun eventsLandOnKnownDates() {
        val label = HijriCalendar.hijri(LocalDate.of(2025, 3, 30)).label
        // Eid al-Fitr 1446 = 2025-03-30 (Umm al-Qura).
        val events = HijriCalendar.events(LocalDate.of(2025, 3, 30))
        assertTrue(events.any { it.title == "Eid al-Fitr" }, "expected Eid al-Fitr, got $events; today=$label")
    }

    @Test
    fun whiteDaysAndMonThuRespectProhibitedDays() {
        val allOn = CalendarParams(
            mondaysThursdays = true, whiteDays = true, shawwalSix = true,
            dhulHijjahFirstNine = true, tasuaAshura = true, shaban = true,
        )
        val days = HijriCalendar.fastDays(LocalDate.of(2025, 3, 25)..LocalDate.of(2025, 4, 15), allOn)
        // Eid al-Fitr (Shawwal 1) is never a fast day.
        assertTrue(days.none { it.date == LocalDate.of(2025, 3, 30) })
        // Shawwal six starts the day after Eid and runs six days.
        val shawwalSixDays = days.filter { it.rule == FastRule.SHAWWAL_SIX }.map { it.date }
        assertEquals(listOf("2025-03-31", "2025-04-01", "2025-04-02", "2025-04-03", "2025-04-04", "2025-04-05"),
            shawwalSixDays.map { it.toString() })

        // Arafah (9 Dhul-Hijjah 1446 = 2025-06-05 tabular) fasts; Eid (Jun 6) does not;
        // Tashreeq (Jun 7-9) are excluded from every rule including Mon/Thu.
        val june = HijriCalendar.fastDays(LocalDate.of(2025, 6, 1)..LocalDate.of(2025, 6, 12), allOn)
        assertTrue(june.any { it.date == LocalDate.of(2025, 6, 5) && it.rule == FastRule.DHUL_HIJJAH_FIRST_NINE })
        assertTrue(june.none { it.date == LocalDate.of(2025, 6, 6) })
        assertTrue(june.none { it.date in LocalDate.of(2025, 6, 7)..LocalDate.of(2025, 6, 9) })
    }

    @Test
    fun tasuaAndAshuraAreConsecutive() {
        val days = HijriCalendar.fastDays(
            LocalDate.of(2025, 7, 1)..LocalDate.of(2025, 7, 20),
            CalendarParams(tasuaAshura = true),
        ).filter { it.rule == FastRule.TASUA_ASHURA }
        assertEquals(2, days.size)
        assertEquals(days[0].date.plusDays(1), days[1].date) // 9th & 10th Muharram 1447
    }
}

package com.khushu.engine.calendar

import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * v2.0.1 regression: `hijri()` fed UTC-midnight millis to a calendar built
 * with `TimeZone.getDefault()`, so every device west of UTC read the gregorian
 * fields of the PREVIOUS civil day — hijri dates, events and fast-day flags
 * landed one day early for the Americas. The conversion must be identical no
 * matter what the default timezone is.
 */
class HijriTimezoneTest {

    private inline fun <T> runInTz(tz: TimeZone, block: () -> T): T {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(tz)
        try {
            return block()
        } finally {
            TimeZone.setDefault(old)
        }
    }

    @Test
    fun goldenAnchorsHold() {
        // 1 Muharram 1440 (the table anchor used by hijriToGregorian's estimate)
        val muharram = runInTz(TimeZone.getTimeZone("UTC")) {
            HijriCalendar.hijri(LocalDate.of(2018, 9, 11))
        }
        assertEquals(1440, muharram.year)
        assertEquals(1, muharram.month)
        assertEquals(1, muharram.day)

        // 1 Ramadan 1446 = 2025-03-01 (matches CalendarTest's golden)
        val ramadan = runInTz(TimeZone.getTimeZone("UTC")) {
            HijriCalendar.hijri(LocalDate.of(2025, 3, 1))
        }
        assertEquals(9, ramadan.month)
        assertEquals(1, ramadan.day)
        assertEquals(1446, ramadan.year)
    }

    @Test
    fun hijriIsDeviceTimezoneIndependent() {
        val d = LocalDate.of(2026, 9, 7)
        val utc = runInTz(TimeZone.getTimeZone("UTC")) { HijriCalendar.hijri(d) }
        for (tz in listOf("America/New_York", "America/Anchorage", "Pacific/Kiritimati", "Asia/Tokyo", "Etc/GMT+9")) {
            val h = runInTz(TimeZone.getTimeZone(tz)) { HijriCalendar.hijri(d) }
            assertEquals(utc, h, "hijri($d) must not depend on default TZ ($tz)")
        }
    }

    @Test
    fun hijriToGregorianRoundTripsUnderNonUtcDefault() {
        runInTz(TimeZone.getTimeZone("America/Chicago")) {
            for (d in listOf(LocalDate.of(2018, 9, 11), LocalDate.of(2026, 2, 18), LocalDate.of(2026, 6, 1))) {
                val h = HijriCalendar.hijri(d)
                assertEquals(
                    d,
                    HijriCalendar.hijriToGregorian(h.year, h.month, h.day, h.offsetApplied),
                    "round-trip failed for $d",
                )
            }
        }
    }
}

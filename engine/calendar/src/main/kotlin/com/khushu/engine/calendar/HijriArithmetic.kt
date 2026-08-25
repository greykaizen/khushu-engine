package com.khushu.engine.calendar

import java.time.LocalDate

/**
 * Hijri-space arithmetic and structural facts. All operations are tabular
 * (Umm al-Qura) and respect the same offset as their inputs.
 */
object HijriArithmetic {

    fun daysInMonth(hijriYear: Int, hijriMonth: Int, offsetDays: Int = 0): Int =
        HijriCalendar.hijriMonthLengths(hijriYear, offsetDays)[hijriMonth - 1]

    fun daysInYear(hijriYear: Int, offsetDays: Int = 0): Int =
        HijriCalendar.hijriMonthLengths(hijriYear, offsetDays).sum()

    /** A leap hijri year has 355 days (the extra day in the final month). */
    fun isLeapYear(hijriYear: Int, offsetDays: Int = 0): Boolean =
        daysInYear(hijriYear, offsetDays) == 355

    /** 1-indexed position of [date] within its hijri year. */
    fun dayOfYear(date: LocalDate, offsetDays: Int = 0): Int {
        val h = HijriCalendar.hijri(date, offsetDays)
        return HijriCalendar.hijriMonthLengths(h.year, offsetDays)
            .take(h.month - 1).sum() + h.day
    }

    fun remainingDaysInMonth(date: LocalDate, offsetDays: Int = 0): Int {
        val h = HijriCalendar.hijri(date, offsetDays)
        return daysInMonth(h.year, h.month, offsetDays) - h.day
    }

    fun addDays(date: LocalDate, days: Int, offsetDays: Int = 0): HijriDate =
        HijriCalendar.hijri(date.plusDays(days.toLong()), offsetDays)

    /**
     * Add [months] to the hijri month of [date], snapping the day back when
     * the target month is shorter. Returns the resulting civil date + hijri.
     */
    fun addMonths(date: LocalDate, months: Int, offsetDays: Int = 0): Pair<HijriDate, LocalDate> {
        val h = HijriCalendar.hijri(date, offsetDays)
        val totalMonths = (h.year * 12 + (h.month - 1)) + months
        val targetYear = totalMonths / 12
        val targetMonth = (totalMonths % 12) + 1
        val length = daysInMonth(targetYear, targetMonth, offsetDays)
        val targetDay = minOf(h.day, length)
        val civil = HijriCalendar.hijriToGregorian(targetYear, targetMonth, targetDay, offsetDays)
        return Pair(HijriCalendar.hijri(civil, offsetDays), civil)
    }

    fun monthBoundaries(hijriYear: Int, hijriMonth: Int, offsetDays: Int = 0): MonthBoundaries {
        val start = HijriCalendar.hijriToGregorian(hijriYear, hijriMonth, 1, offsetDays)
        val length = daysInMonth(hijriYear, hijriMonth, offsetDays)
        return MonthBoundaries(
            hijriYear = hijriYear,
            hijriMonth = hijriMonth,
            monthName = HijriCalendar.monthName(hijriMonth),
            firstCivilDate = start,
            lastCivilDate = start.plusDays((length - 1).toLong()),
            lengthDays = length,
        )
    }
}

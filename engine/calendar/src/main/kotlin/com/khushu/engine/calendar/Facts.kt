package com.khushu.engine.calendar

import java.time.LocalDate

/**
 * Islamic-calendar fact APIs. These expose DATES and STRUCTURE — never rulings.
 * Laylat al-Qadr is deliberately NOT identified; only seek-night candidates.
 */
object Facts {

    data class RamadanFacts(
        val hijriYear: Int,
        val firstDay: LocalDate,
        val lastDay: LocalDate,
        val lengthDays: Int,
    )

    fun ramadan(hijriYear: Int, offsetDays: Int = 0): RamadanFacts {
        val b = HijriArithmetic.monthBoundaries(hijriYear, 9, offsetDays)
        return RamadanFacts(b.hijriYear, b.firstCivilDate, b.lastCivilDate, b.lengthDays)
    }

    fun isRamadan(date: LocalDate, offsetDays: Int = 0): Boolean =
        HijriCalendar.hijri(date, offsetDays).month == 9

    /** Civil dates whose NIGHTS fall in the last ten nights of Ramadan (hijri days 21–30). */
    fun isLastTenNights(date: LocalDate, offsetDays: Int = 0): Boolean {
        val h = HijriCalendar.hijri(date, offsetDays)
        return h.month == 9 && h.day >= 21
    }

    /**
     * Odd-night seek candidates (hijri nights 21/23/25/27/29). The engine never
     * identifies one specific night as Laylat al-Qadr — that is not knowable
     * computationally and is deliberately out of scope.
     */
    fun oddNightSeekCandidates(hijriYear: Int, offsetDays: Int = 0): List<LocalDate> =
        listOf(21, 23, 25, 27, 29).mapNotNull { day ->
            runCatching { HijriCalendar.hijriToGregorian(hijriYear, 9, day, offsetDays) }.getOrNull()
        }

    /** The four sacred months as civil ranges for a given hijri year. */
    fun sacredMonths(hijriYear: Int, offsetDays: Int = 0): List<MonthBoundaries> =
        listOf(1, 7, 11, 12).map { HijriArithmetic.monthBoundaries(hijriYear, it, offsetDays) }

    /** Hajj season: the whole month of Dhul-Hijjah. */
    fun hajjSeason(hijriYear: Int, offsetDays: Int = 0): MonthBoundaries =
        HijriArithmetic.monthBoundaries(hijriYear, 12, offsetDays)

    private fun fixed(month: Int, day: Int, hijriYear: Int, offsetDays: Int): LocalDate =
        HijriCalendar.hijriToGregorian(hijriYear, month, day, offsetDays)

    fun firstTenDhulHijjah(hijriYear: Int, offsetDays: Int = 0): List<LocalDate> =
        (1..9).map { fixed(12, it, hijriYear, offsetDays) } // day 10 is Eid — fasting prohibited

    fun arafah(hijriYear: Int, offsetDays: Int = 0): LocalDate = fixed(12, 9, hijriYear, offsetDays)

    fun ashura(hijriYear: Int, offsetDays: Int = 0): LocalDate = fixed(1, 10, hijriYear, offsetDays)

    fun tasua(hijriYear: Int, offsetDays: Int = 0): LocalDate = fixed(1, 9, hijriYear, offsetDays)

    fun eidAlFitr(hijriYear: Int, offsetDays: Int = 0): LocalDate = fixed(10, 1, hijriYear, offsetDays)

    fun eidAlAdha(hijriYear: Int, offsetDays: Int = 0): LocalDate = fixed(12, 10, hijriYear, offsetDays)

    fun tashreeqDays(hijriYear: Int, offsetDays: Int = 0): List<LocalDate> =
        (11..13).map { fixed(12, it, hijriYear, offsetDays) }

    /**
     * White Days (13th–15th) of a hijri month. Classical practice applies them
     * to any lunar month; Ramadan's are superseded by the obligatory fast.
     */
    fun whiteDays(hijriYear: Int, hijriMonth: Int, offsetDays: Int = 0): List<LocalDate> =
        listOf(13, 14, 15).map { fixed(hijriMonth, it, hijriYear, offsetDays) }
}

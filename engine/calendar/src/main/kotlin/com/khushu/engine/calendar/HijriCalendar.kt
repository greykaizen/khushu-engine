package com.khushu.engine.calendar

import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar

/**
 * Hijri (Umm al-Qura) conversion, Islamic events, and optional fast-day rules.
 *
 * Conversion delegates to the ummalqura-calendar library — the same table the
 * donor used. Fiqh rules follow donor behavior with one documented correction
 * (Shawwal six days; see docs/divergences.md D10).
 */
object HijriCalendar {

    fun hijri(localDate: LocalDate, offsetDays: Int = 0): HijriDate {
        require(offsetDays in -2..2) { "offsetDays must be within −2..+2" }
        val cal = UmmalquraCalendar()
        cal.timeInMillis = epochOf(localDate) + offsetDays * 86_400_000L
        return HijriDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            monthName = monthName(cal.get(Calendar.MONTH)),
            offsetApplied = offsetDays,
        )
    }

    /**
     * Fixed-hijri-date Islamic events falling on this civil day.
     * Standard computational events only (see divergences D11 for scope).
     */
    fun events(localDate: LocalDate, offsetDays: Int = 0): List<IslamicEvent> {
        val h = hijri(localDate, offsetDays)
        val label = h.label
        val events = mutableListOf<IslamicEvent>()

        when (h.month to h.day) {
            1 to 1 -> events += IslamicEvent("Islamic New Year", label)
            1 to 9 -> events += IslamicEvent("Tasu'a", label)
            1 to 10 -> events += IslamicEvent("Day of Ashura", label)
            3 to 12 -> events += IslamicEvent("Mawlid an-Nabi", label)
            7 to 27 -> events += IslamicEvent("Isra and Mi'raj", label)
            8 to 15 -> events += IslamicEvent("Mid-Sha'ban", label)
            9 to 1 -> events += IslamicEvent("Ramadan Begins", label)
            10 to 1 -> events += IslamicEvent("Eid al-Fitr", label)
            12 to 8 -> events += IslamicEvent("Hajj Begins", label)
            12 to 9 -> events += IslamicEvent("Day of Arafah", label)
            12 to 10 -> events += IslamicEvent("Eid al-Adha", label)
        }
        // Convention pinned: a seek-night is marked on the civil day whose tabular
        // hijri date is the odd Ramadan night — i.e. the night BEGINS at the
        // maghrib preceding that civil date.
        if (h.month == 9 && h.day in 21..29 && h.day % 2 == 1) {
            events += IslamicEvent("Laylat al-Qadr Seek-Night (Ramadan ${h.day})", label)
        }
        if (h.month == 12 && h.day in 11..13) {
            events += IslamicEvent("Days of Tashreeq", label)
        }
        return events.sortedBy { it.title }
    }

    /** All events within the civil-date range (inclusive). */
    fun eventsInRange(range: ClosedRange<LocalDate>, offsetDays: Int = 0): List<Pair<LocalDate, IslamicEvent>> {
        val out = mutableListOf<Pair<LocalDate, IslamicEvent>>()
        var d = range.start
        while (d <= range.endInclusive) {
            for (e in events(d, offsetDays)) out += d to e
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Reverse conversion: the civil date whose hijri date equals [hijriYear]/[hijriMonth]/[hijriDay]
     * (with [offsetDays] applied on read-back). Hijri→civil mapping is monotonic,
     * so a bounded binary search around a tabular estimate finds it exactly.
     * Throws when no such date exists (e.g. day 30 in a 29-day month).
     */
    fun hijriToGregorian(hijriYear: Int, hijriMonth: Int, hijriDay: Int, offsetDays: Int = 0): LocalDate {
        require(hijriMonth in 1..12 && hijriDay in 1..30 && hijriYear > 0) { "invalid hijri date" }
        val estimate = LocalDate.of(2018, 9, 11)
            .plusDays(Math.round((hijriYear - 1440) * 354.36673))
            .plusDays(((hijriMonth - 1) * 29.530589).toLong())
            .plusDays((hijriDay - 1).toLong() - offsetDays.toLong())

        fun rank(date: LocalDate): Long {
            val h = hijri(date, offsetDays)
            return h.year.toLong() * 10000 + h.month * 100 + h.day
        }
        val targetRank = hijriYear.toLong() * 10000 + hijriMonth * 100 + hijriDay

        var lo = estimate.minusDays(50)
        var hi = estimate.plusDays(50)
        while (lo < hi) {
            val mid = lo.plusDays((java.time.temporal.ChronoUnit.DAYS.between(lo, hi)) / 2)
            if (rank(mid) < targetRank) lo = mid.plusDays(1) else hi = mid
        }
        val result = lo
        val h = hijri(result, offsetDays)
        require(h.year == hijriYear && h.month == hijriMonth && h.day == hijriDay) {
            "no civil date maps to $hijriDay-$hijriMonth-$hijriYear AH with offset $offsetDays (day may not exist)"
        }
        return result
    }

    /** Lengths (always 29 or 30) of all twelve months of a hijri year. */
    fun hijriMonthLengths(hijriYear: Int, offsetDays: Int = 0): List<Int> {
        fun start(year: Int, month: Int): LocalDate = hijriToGregorian(year, month, 1, offsetDays)
        return (1..12).map { m ->
            val thisStart = start(hijriYear, m)
            val nextStart = if (m < 12) start(hijriYear, m + 1) else start(hijriYear + 1, 1)
            java.time.temporal.ChronoUnit.DAYS.between(thisStart, nextStart).toInt()
        }
    }

    /** Next civil date on which the given fixed hijri [month]/[day] falls, at or after [after]. */
    fun nextOccurrence(month: Int, day: Int, after: LocalDate, offsetDays: Int = 0): LocalDate {
        require(month in 1..12 && day in 1..30) { "invalid hijri month/day" }
        var probe = after
        // Hijri year of `probe`, then walk forward at most two years.
        val hStart = hijri(probe, offsetDays)
        for (yearOffset in 0..1) {
            val target = runCatching {
                hijriToGregorian(hStart.year + yearOffset, month, day, offsetDays)
            }.getOrNull()
            if (target != null && !target.isBefore(after)) return target
        }
        throw IllegalArgumentException(
            "$day-$month does not occur in hijri years ${hStart.year} or ${hStart.year + 1} " +
                "(the day may not exist in either year)",
        )
    }

    /** True when this hijri month is one of the four sacred months. */
    fun isSacredMonth(hijriMonth: Int): Boolean =
        hijriMonth == 1 || hijriMonth == 7 || hijriMonth == 11 || hijriMonth == 12

    /**
     * Optional fast days within [range] per [params]. Prohibited days are always
     * excluded: Eid al-Fitr, Eid al-Adha, Days of Tashreeq (11–13 Dhul-Hijjah).
     */
    fun fastDays(range: ClosedRange<LocalDate>, params: CalendarParams): List<FastDay> {
        val out = mutableListOf<FastDay>()
        var d = range.start
        while (d <= range.endInclusive) {
            val h = hijri(d, params.hijriOffsetDays)
            val prohibited =
                (h.month == 10 && h.day == 1) ||
                    (h.month == 12 && h.day == 10) ||
                    (h.month == 12 && h.day in 11..13)

            if (!prohibited) {
                if (params.mondaysThursdays && d.dayOfWeek.let { it == DayOfWeek.MONDAY || it == DayOfWeek.THURSDAY } &&
                    h.month != 9 // Ramadan fasts are obligatory, not "optional"
                ) {
                    out += FastDay(d, FastRule.MONDAY_THURSDAY)
                }
                if (params.whiteDays && h.day in 13..15 && h.month != 9) {
                    out += FastDay(d, FastRule.WHITE_DAYS)
                }
                if (params.shaban && h.month == 8 && h.day <= 28) {
                    out += FastDay(d, FastRule.SHABAN)
                }
                if (params.shawwalSix && h.month == 10 && h.day in 2..7) {
                    out += FastDay(d, FastRule.SHAWWAL_SIX)
                }
                if (params.dhulHijjahFirstNine && h.month == 12 && h.day in 1..9) {
                    out += FastDay(d, FastRule.DHUL_HIJJAH_FIRST_NINE)
                }
                if (params.tasuaAshura && h.month == 1 && h.day in 9..10) {
                    out += FastDay(d, FastRule.TASUA_ASHURA)
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun monthName(zeroBased: Int): String = when (zeroBased + 1) {
        1 -> "Muharram"; 2 -> "Safar"; 3 -> "Rabi al-Awwal"; 4 -> "Rabi al-Thani"
        5 -> "Jumada al-Ula"; 6 -> "Jumada al-Akhirah"; 7 -> "Rajab"; 8 -> "Sha'ban"
        9 -> "Ramadan"; 10 -> "Shawwal"; 11 -> "Dhul-Qa'dah"; else -> "Dhul-Hijjah"
    }
}

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
object Calendar {

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
        if (h.month == 9 && h.day in 21..29 && h.day % 2 == 1) {
            events += IslamicEvent("Laylat al-Qadr Seek-Night (Ramadan ${h.day})", label)
        }
        if (h.month == 12 && h.day in 11..13) {
            events += IslamicEvent("Days of Tashreeq", label)
        }
        return events.sortedBy { it.title }
    }

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

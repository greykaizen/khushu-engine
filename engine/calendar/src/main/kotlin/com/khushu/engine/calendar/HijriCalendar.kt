package com.khushu.engine.calendar

import com.github.msarhan.ummalqura.calendar.UmmalquraCalendar
import com.khushu.engine.core.error.HijriDayDoesNotExistException
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.NoResultException
import com.khushu.engine.core.error.UpstreamComputationException
import com.khushu.engine.core.error.validate
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
        validate(offsetDays in -2..2) {
            InvalidParameterException("offsetDays", "$offsetDays", "must be within −2..+2")
        }
        return try {
            val cal = UmmalquraCalendar()
            cal.timeInMillis = epochOf(localDate) + offsetDays * 86_400_000L
            HijriDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH),
                monthName = monthName(cal.get(Calendar.MONTH) + 1),
                offsetApplied = offsetDays,
            )
        } catch (e: Exception) {
            throw UpstreamComputationException(
                "ummalqura conversion of $localDate (offset $offsetDays); the Umm al-Qura " +
                    "table covers 1300–1600 AH (≈ 1882–2174 CE)",
                e,
            )
        }
    }

    /**
     * Fixed-hijri-date Islamic events falling on this civil day.
     * Standard computational events only (see divergences D11 for scope).
     * Everything — Qadr seek-nights and Tashreeq included — comes from
     * [builtInDefinitions], so this and [EventRegistry.occurrencesOn] agree.
     */
    fun events(localDate: LocalDate, offsetDays: Int = 0): List<IslamicEvent> {
        val h = hijri(localDate, offsetDays)
        val label = h.label
        return builtInDefinitions()
            .asSequence()
            .filter { it.hijriMonth == h.month && it.hijriDay == h.day }
            .map { IslamicEvent(it.title, label) }
            .sortedBy { it.title }
            .toList()
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
        validate(hijriMonth in 1..12 && hijriDay in 1..30 && hijriYear > 0) {
            InvalidParameterException(
                "hijriDate",
                "$hijriDay-$hijriMonth-$hijriYear",
                "year > 0, month in 1..12, day in 1..30",
            )
        }
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
        validate(h.year == hijriYear && h.month == hijriMonth && h.day == hijriDay) {
            HijriDayDoesNotExistException(hijriYear, hijriMonth, hijriDay, offsetDays)
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
        validate(month in 1..12 && day in 1..30) {
            InvalidParameterException("hijriMonth/day", "$day-$month", "month in 1..12, day in 1..30")
        }
        var probe = after
        // Hijri year of `probe`, then walk forward at most two years.
        val hStart = hijri(probe, offsetDays)
        for (yearOffset in 0..1) {
            val target = runCatching {
                hijriToGregorian(hStart.year + yearOffset, month, day, offsetDays)
            }.getOrNull()
            if (target != null && !target.isBefore(after)) return target
        }
        throw NoResultException(
            "$day-$month does not occur in hijri years ${hStart.year} or ${hStart.year + 1} " +
                "(the day may not exist in either year)",
        )
    }

    /**
     * Whole civil days from [after] until the fixed hijri [month]/[day] next
     * falls — 0 when it falls on [after] itself. Wraps the hijri year; this is
     * the call behind "N days to Ramadan / Eid" countdowns.
     * @throws NoResultException when the day does not occur within the search window
     */
    fun daysUntil(month: Int, day: Int, after: LocalDate, offsetDays: Int = 0): Long =
        java.time.temporal.ChronoUnit.DAYS.between(after, nextOccurrence(month, day, after, offsetDays))

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

    fun monthName(hijriMonth1Indexed: Int): String = when (hijriMonth1Indexed) {
        1 -> "Muharram"; 2 -> "Safar"; 3 -> "Rabi al-Awwal"; 4 -> "Rabi al-Thani"
        5 -> "Jumada al-Ula"; 6 -> "Jumada al-Akhirah"; 7 -> "Rajab"; 8 -> "Sha'ban"
        9 -> "Ramadan"; 10 -> "Shawwal"; 11 -> "Dhul-Qa'dah"; else -> "Dhul-Hijjah"
    }

    private fun monthNameZeroBased(zeroBased: Int): String = monthName(zeroBased + 1)

    /**
     * The core fixed-hijri-date event pack — single source for [events] and the
     * canonical JSON export. Scope trimmed per divergences D11.
     *
     * Convention pinned for the seek-nights: a seek-night is marked on the
     * civil day whose tabular hijri date is the odd Ramadan night — i.e. the
     * night BEGINS at the maghrib preceding that civil date. The engine never
     * identifies one specific night as Laylat al-Qadr.
     */
    fun builtInDefinitions(): List<EventDefinition> = listOf(
        EventDefinition("new_year", "Islamic New Year", 1, 1, EventCategory.HISTORICAL, confidence = Confidence.ESTABLISHED),
        EventDefinition("tasua", "Tasu'a", 1, 9, EventCategory.FASTING, confidence = Confidence.ESTABLISHED),
        EventDefinition("ashura", "Day of Ashura", 1, 10, EventCategory.FASTING, confidence = Confidence.ESTABLISHED),
        EventDefinition("mawlid", "Mawlid an-Nabi", 3, 12, EventCategory.HISTORICAL),
        EventDefinition("isra_miraj", "Isra and Mi'raj", 7, 27, EventCategory.HISTORICAL),
        EventDefinition("mid_shaban", "Mid-Sha'ban", 8, 15, EventCategory.HISTORICAL),
        EventDefinition("ramadan_begins", "Ramadan Begins", 9, 1, EventCategory.MONTH_START, confidence = Confidence.ESTABLISHED),
        EventDefinition("qadr_seek_21", "Laylat al-Qadr Seek-Night (Ramadan 21)", 9, 21, confidence = Confidence.COMPUTATIONAL),
        EventDefinition("qadr_seek_23", "Laylat al-Qadr Seek-Night (Ramadan 23)", 9, 23, confidence = Confidence.COMPUTATIONAL),
        EventDefinition("qadr_seek_25", "Laylat al-Qadr Seek-Night (Ramadan 25)", 9, 25, confidence = Confidence.COMPUTATIONAL),
        EventDefinition("qadr_seek_27", "Laylat al-Qadr Seek-Night (Ramadan 27)", 9, 27, confidence = Confidence.COMPUTATIONAL),
        EventDefinition("qadr_seek_29", "Laylat al-Qadr Seek-Night (Ramadan 29)", 9, 29, confidence = Confidence.COMPUTATIONAL),
        EventDefinition("eid_al_fitr", "Eid al-Fitr", 10, 1, EventCategory.EID, confidence = Confidence.ESTABLISHED),
        EventDefinition("hajj_begins", "Hajj Begins", 12, 8, EventCategory.HAJJ),
        EventDefinition("arafah", "Day of Arafah", 12, 9, EventCategory.HAJJ, confidence = Confidence.ESTABLISHED),
        EventDefinition("eid_al_adha", "Eid al-Adha", 12, 10, EventCategory.EID, confidence = Confidence.ESTABLISHED),
        EventDefinition("tashreeq_11", "Days of Tashreeq", 12, 11, EventCategory.HAJJ, confidence = Confidence.ESTABLISHED),
        EventDefinition("tashreeq_12", "Days of Tashreeq", 12, 12, EventCategory.HAJJ, confidence = Confidence.ESTABLISHED),
        EventDefinition("tashreeq_13", "Days of Tashreeq", 12, 13, EventCategory.HAJJ, confidence = Confidence.ESTABLISHED),
    )
}

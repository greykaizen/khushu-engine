package com.khushu.engine

import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.DateLine
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import com.khushu.engine.calendar.DualDate
import com.khushu.engine.calendar.Facts as CalendarFacts
import com.khushu.engine.calendar.HijriArithmetic
import com.khushu.engine.calendar.HijriCalendar
import com.khushu.engine.calendar.HijriDate
import com.khushu.engine.calendar.MonthBoundaries
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Date/month capability namespaces for the calendar domain.
 *
 * Naming contract (deliberate):
 * - [DateApi.islamicDate] — tabular civil-calendar conversion (date-keyed).
 * - [DateApi.islamicDateAt] — LOCAL ISLAMIC-DAY CALCULATION USING SUNSET:
 *   after local sunset the Islamic date has already advanced to the next
 *   tabular day. These are different questions; both are real.
 */
class DateApi internal constructor() {

    /** Tabular hijri date for a civil date (midnight boundary). */
    fun islamicDate(civilDate: LocalDate, offsetDays: Int = 0): HijriDate =
        HijriCalendar.hijri(civilDate, offsetDays)

    /**
     * Hijri date at a wall-clock instant using the SUNSET boundary: after local
     * maghrib the Islamic date has already advanced. Distinct from [islamicDate].
     */
    fun islamicDateAt(instant: Instant, location: Location, zoneId: ZoneId, config: CalendarConfiguration): HijriDate {
        val civilDate = LocalDate.ofInstant(instant, zoneId)
        val maghrib = Prayer.times(location, civilDate).maghrib.adjusted
        val effectiveCivil = if (maghrib != null && !instant.isBefore(maghrib)) civilDate.plusDays(1) else civilDate
        return HijriCalendar.hijri(effectiveCivil, config.hijriOffsetDays)
    }

    /** The configured primary calendar line for a civil date. */
    fun primary(civilDate: LocalDate, config: CalendarConfiguration): DateLine =
        when (config.primary) {
            CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
            CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
        }

    /** The configured secondary calendar line, or null when none is configured. */
    fun secondary(civilDate: LocalDate, config: CalendarConfiguration): DateLine? =
        config.secondary?.let { side ->
            when (side) {
                CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
                CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
            }
        }

    /** Both calendar lines at once (primary + optional secondary). */
    fun both(civilDate: LocalDate, config: CalendarConfiguration): DualDate =
        bothDates(civilDate, config)

    /**
     * Reverse conversion passthrough (tabular).
     * @throws com.khushu.engine.core.error.HijriDayDoesNotExistException when the hijri day does not exist
     */
    fun convert(hijriYear: Int, hijriMonth: Int, hijriDay: Int, config: CalendarConfiguration): LocalDate =
        HijriCalendar.hijriToGregorian(hijriYear, hijriMonth, hijriDay, config.hijriOffsetDays)
}

internal fun bothDates(civilDate: LocalDate, config: CalendarConfiguration): DualDate {
    val primary = when (config.primary) {
        CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
        CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
    }
    val secondary = config.secondary?.let { side ->
        when (side) {
            CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
            CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
        }
    }
    return DualDate(primary, secondary)
}

/** Calendar arithmetic and dual-calendar matrices over civil months/years. */
class MonthApi internal constructor() {

    /** Dual-calendar day map for one civil month (calendar-grid UIs). */
    fun monthMatrix(civilMonth: YearMonth, config: CalendarConfiguration): Map<LocalDate, DualDate> {
        val out = LinkedHashMap<LocalDate, DualDate>()
        var d = civilMonth.atDay(1)
        while (!d.isAfter(civilMonth.atEndOfMonth())) {
            out[d] = bothDates(d, config)
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Eager full-year matrix — 365–366 DualDate entries (~tens of KB). Fine for
     * year views; for widgets or multi-year spans prefer [yearMatrixLazy].
     */
    fun yearMatrix(civilYear: Int, config: CalendarConfiguration): Map<LocalDate, DualDate> =
        (1..12).flatMap { m ->
            monthMatrix(YearMonth.of(civilYear, m), config).entries
        }.associate { it.key to it.value }

    /** Lazy variant: dates materialize on iteration; nothing is retained. */
    fun yearMatrixLazy(civilYear: Int, config: CalendarConfiguration): Sequence<Pair<LocalDate, DualDate>> =
        generateSequence(LocalDate.of(civilYear, 1, 1)) { it.plusDays(1) }
            .takeWhile { it.year == civilYear }
            .map { it to bothDates(it, config) }

    /** Hijri date → civil date map for a whole hijri year, sorted by hijri order. */
    fun hijriYearMap(hijriYear: Int, config: CalendarConfiguration): java.util.SortedMap<HijriDate, LocalDate> {
        val out = java.util.TreeMap<HijriDate, LocalDate>(compareBy({ it.year }, { it.month }, { it.day }))
        for (m in 1..12) {
            val b = HijriArithmetic.monthBoundaries(hijriYear, m, config.hijriOffsetDays)
            var d = b.firstCivilDate
            while (!d.isAfter(b.lastCivilDate)) {
                out[HijriCalendar.hijri(d, config.hijriOffsetDays)] = d
                d = d.plusDays(1)
            }
        }
        return out
    }

    /** Civil boundaries (first/last date, length) of a hijri month. */
    fun boundaries(hijriYear: Int, hijriMonth: Int, config: CalendarConfiguration): MonthBoundaries =
        HijriArithmetic.monthBoundaries(hijriYear, hijriMonth, config.hijriOffsetDays)

    /** Hijri date [days] civil days away from [date]. */
    fun addDays(date: LocalDate, days: Int, config: CalendarConfiguration): HijriDate =
        HijriArithmetic.addDays(date, days, config.hijriOffsetDays)

    /** Add hijri months, snapping the day back when the target month is shorter. */
    fun addMonths(date: LocalDate, months: Int, config: CalendarConfiguration): Pair<HijriDate, LocalDate> =
        HijriArithmetic.addMonths(date, months, config.hijriOffsetDays)
}

/** Islamic-season facts — dates and structure only, never rulings. */
class FactsApi internal constructor() {
    /** Ramadan boundaries and length for a hijri year. */
    fun ramadan(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.ramadan(hijriYear, config.hijriOffsetDays)
    /** Whether a civil date falls in Ramadan. */
    fun isRamadan(date: LocalDate, config: CalendarConfiguration) = CalendarFacts.isRamadan(date, config.hijriOffsetDays)
    /** Whether a civil date's night is one of Ramadan's last ten nights. */
    fun isLastTenNights(date: LocalDate, config: CalendarConfiguration) = CalendarFacts.isLastTenNights(date, config.hijriOffsetDays)
    /** Odd-night seek candidates (21/23/25/27/29); the engine never picks one Laylat al-Qadr. */
    fun oddNightSeekCandidates(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.oddNightSeekCandidates(hijriYear, config.hijriOffsetDays)
    /** The four sacred months as civil ranges. */
    fun sacredMonths(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.sacredMonths(hijriYear, config.hijriOffsetDays)
    /** Hajj season boundaries. */
    fun hajjSeason(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.hajjSeason(hijriYear, config.hijriOffsetDays)
    /** The first ten days of Dhul-Hijjah as civil dates. */
    fun firstTenDhulHijjah(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.firstTenDhulHijjah(hijriYear, config.hijriOffsetDays)
    /** Day of Arafah (9 Dhul-Hijjah). */
    fun arafah(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.arafah(hijriYear, config.hijriOffsetDays)
    /** Day of Ashura (10 Muharram). */
    fun ashura(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.ashura(hijriYear, config.hijriOffsetDays)
    /** Tasu'a (9 Muharram). */
    fun tasua(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.tasua(hijriYear, config.hijriOffsetDays)
    /** Eid al-Fitr (1 Shawwal). */
    fun eidAlFitr(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.eidAlFitr(hijriYear, config.hijriOffsetDays)
    /** Eid al-Adha (10 Dhul-Hijjah). */
    fun eidAlAdha(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.eidAlAdha(hijriYear, config.hijriOffsetDays)
    /** Days of Tashreeq (11–13 Dhul-Hijjah). */
    fun tashreeqDays(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.tashreeqDays(hijriYear, config.hijriOffsetDays)
    /** White days (13–15) of a hijri month as civil dates. */
    fun whiteDays(hijriYear: Int, hijriMonth: Int, config: CalendarConfiguration) = CalendarFacts.whiteDays(hijriYear, hijriMonth, config.hijriOffsetDays)

    /** A leap hijri year has 355 days. */
    fun isLeapHijriYear(hijriYear: Int, config: CalendarConfiguration) = HijriArithmetic.isLeapYear(hijriYear, config.hijriOffsetDays)

    /** 1-indexed position of [date] within its hijri year. */
    fun dayOfYear(date: LocalDate, config: CalendarConfiguration): Int =
        HijriArithmetic.dayOfYear(date, config.hijriOffsetDays)

    /** Days left in the hijri month after [date] (0 on its last day). */
    fun remainingDaysInMonth(date: LocalDate, config: CalendarConfiguration): Int =
        HijriArithmetic.remainingDaysInMonth(date, config.hijriOffsetDays)

    /** (day of Ramadan, length of Ramadan) on a Ramadan date; null outside Ramadan. */
    fun ramadanProgress(date: LocalDate, config: CalendarConfiguration): Pair<Int, Int>? {
        if (!isRamadan(date, config)) return null
        val h = HijriCalendar.hijri(date, config.hijriOffsetDays)
        return Pair(h.day, HijriArithmetic.daysInMonth(h.year, 9, config.hijriOffsetDays))
    }

    /**
     * Next [count] fixed-hijri events after [date] (widget/home-screen feeds).
     * @throws com.khushu.engine.core.error.InvalidParameterException when [count] < 1
     */
    fun upcomingEvents(after: LocalDate, count: Int, config: CalendarConfiguration): List<Pair<LocalDate, String>> {
        validate(count >= 1) { InvalidParameterException("count", "$count", "must be >= 1") }
        val out = mutableListOf<Pair<LocalDate, String>>()
        var probe = after
        while (out.size < count && probe < after.plusYears(3)) {
            for ((d, e) in HijriCalendar.eventsInRange(probe..probe)) {
                out += d to e.title
                if (out.size == count) break
            }
            probe = probe.plusDays(1)
        }
        return out
    }
}

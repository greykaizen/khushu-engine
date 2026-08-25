package com.khushu.engine

import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.DateLine
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

    fun islamicDate(civilDate: LocalDate, offsetDays: Int = 0): HijriDate =
        HijriCalendar.hijri(civilDate, offsetDays)

    fun islamicDateAt(instant: Instant, location: Location, zoneId: ZoneId, config: CalendarConfiguration): HijriDate {
        val civilDate = LocalDate.ofInstant(instant, zoneId)
        val maghrib = Prayer.times(location, civilDate).maghrib.adjusted
        val effectiveCivil = if (maghrib != null && !instant.isBefore(maghrib)) civilDate.plusDays(1) else civilDate
        return HijriCalendar.hijri(effectiveCivil, config.hijriOffsetDays)
    }

    fun primary(civilDate: LocalDate, config: CalendarConfiguration): DateLine =
        when (config.primary) {
            CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
            CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
        }

    fun secondary(civilDate: LocalDate, config: CalendarConfiguration): DateLine? =
        config.secondary?.let { side ->
            when (side) {
                CalendarConfiguration.Side.HIJRI -> DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
                CalendarConfiguration.Side.GREGORIAN -> DateLine.Gregorian(civilDate)
            }
        }

    fun both(civilDate: LocalDate, config: CalendarConfiguration): DualDate =
        bothDates(civilDate, config)

    /** Reverse conversion passthrough (tabular). */
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

class MonthApi internal constructor() {

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

    fun boundaries(hijriYear: Int, hijriMonth: Int, config: CalendarConfiguration): MonthBoundaries =
        HijriArithmetic.monthBoundaries(hijriYear, hijriMonth, config.hijriOffsetDays)

    fun addDays(date: LocalDate, days: Int, config: CalendarConfiguration): HijriDate =
        HijriArithmetic.addDays(date, days, config.hijriOffsetDays)

    fun addMonths(date: LocalDate, months: Int, config: CalendarConfiguration): Pair<HijriDate, LocalDate> =
        HijriArithmetic.addMonths(date, months, config.hijriOffsetDays)
}

class FactsApi internal constructor() {
    fun ramadan(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.ramadan(hijriYear, config.hijriOffsetDays)
    fun isRamadan(date: LocalDate, config: CalendarConfiguration) = CalendarFacts.isRamadan(date, config.hijriOffsetDays)
    fun isLastTenNights(date: LocalDate, config: CalendarConfiguration) = CalendarFacts.isLastTenNights(date, config.hijriOffsetDays)
    fun oddNightSeekCandidates(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.oddNightSeekCandidates(hijriYear, config.hijriOffsetDays)
    fun sacredMonths(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.sacredMonths(hijriYear, config.hijriOffsetDays)
    fun hajjSeason(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.hajjSeason(hijriYear, config.hijriOffsetDays)
    fun firstTenDhulHijjah(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.firstTenDhulHijjah(hijriYear, config.hijriOffsetDays)
    fun arafah(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.arafah(hijriYear, config.hijriOffsetDays)
    fun ashura(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.ashura(hijriYear, config.hijriOffsetDays)
    fun tasua(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.tasua(hijriYear, config.hijriOffsetDays)
    fun eidAlFitr(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.eidAlFitr(hijriYear, config.hijriOffsetDays)
    fun eidAlAdha(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.eidAlAdha(hijriYear, config.hijriOffsetDays)
    fun tashreeqDays(hijriYear: Int, config: CalendarConfiguration) = CalendarFacts.tashreeqDays(hijriYear, config.hijriOffsetDays)
    fun whiteDays(hijriYear: Int, hijriMonth: Int, config: CalendarConfiguration) = CalendarFacts.whiteDays(hijriYear, hijriMonth, config.hijriOffsetDays)

    fun isLeapHijriYear(hijriYear: Int, config: CalendarConfiguration) = HijriArithmetic.isLeapYear(hijriYear, config.hijriOffsetDays)
    fun ramadanProgress(date: LocalDate, config: CalendarConfiguration): Pair<Int, Int>? {
        if (!isRamadan(date, config)) return null
        val h = HijriCalendar.hijri(date, config.hijriOffsetDays)
        return Pair(h.day, HijriArithmetic.daysInMonth(h.year, 9, config.hijriOffsetDays))
    }

    fun upcomingEvents(after: LocalDate, count: Int, config: CalendarConfiguration): List<Pair<LocalDate, String>> {
        require(count >= 1)
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

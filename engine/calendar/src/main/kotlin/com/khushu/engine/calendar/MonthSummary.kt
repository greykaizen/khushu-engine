package com.khushu.engine.calendar

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** All calendar facts of one civil day, rendered against a dual-calendar configuration. */
data class MonthSummaryDay(
    val date: LocalDate,
    /** Configured calendar lines — at least one side is always Hijri (engine-enforced). */
    val dual: DualDate,
    val events: List<IslamicEvent>,
    /** Optional fast rules observed under [CalendarParams]; empty on prohibited days. */
    val fastRules: List<FastRule>,
    /** Moon illumination sampled at local sunset (18:00 fallback on polar days). */
    val moonIllumination: Double,
    /** Whether the moon was above the horizon at the sample instant. */
    val moonAboveHorizon: Boolean,
)

/** Every fact of a whole civil month: [MonthSummaryDay] per day, one computation pass. */
data class MonthSummary(
    val yearMonth: YearMonth,
    val days: List<MonthSummaryDay>,
)

/**
 * Whole-month composite for calendar home screens: dual dates, events, fast
 * rules, and per-evening moon facts — computed in a single pass (fast days
 * once for the range, one-pass lunar track, one tabular event lookup per day).
 */
object MonthSummaries {

    fun of(
        yearMonth: YearMonth,
        location: Location,
        zoneId: ZoneId,
        config: CalendarConfiguration,
        calendarParams: CalendarParams = CalendarParams(hijriOffsetDays = config.hijriOffsetDays),
    ): MonthSummary {
        val first = yearMonth.atDay(1)
        val last = yearMonth.atEndOfMonth()

        val fastRulesByDate = HijriCalendar.fastDays(first..last, calendarParams)
            .groupBy({ it.date }, { it.rule })

        data class MoonFacts(val illumination: Double, val aboveHorizon: Boolean)
        val moonByDate = HashMap<LocalDate, MoonFacts>()
        for (day in Astronomy.moon.track(location, yearMonth, zoneId).days) {
            val anchorMs = day.setEpochMs ?: day.transitEpochMs
            val instant = anchorMs?.let(Instant::ofEpochMilli)
                ?: day.date.atTime(18, 0).atZone(zoneId).toInstant()
            val state = Astronomy.moon.state(location, instant)
            val position = Astronomy.moon.position(location, instant)
            moonByDate[day.date] = MoonFacts(state.illuminationFraction, position.altitudeDeg > 0.0)
        }

        val days = mutableListOf<MonthSummaryDay>()
        var d = first
        while (!d.isAfter(last)) {
            val moon = moonByDate[d] ?: run {
                val fallback = d.atTime(18, 0).atZone(zoneId).toInstant()
                val state = Astronomy.moon.state(location, fallback)
                val position = Astronomy.moon.position(location, fallback)
                MoonFacts(state.illuminationFraction, position.altitudeDeg > 0.0)
            }
            days += MonthSummaryDay(
                date = d,
                dual = DualDates.of(d, config),
                events = HijriCalendar.events(d, config.hijriOffsetDays),
                fastRules = fastRulesByDate[d] ?: emptyList(),
                moonIllumination = moon.illumination,
                moonAboveHorizon = moon.aboveHorizon,
            )
            d = d.plusDays(1)
        }
        return MonthSummary(yearMonth, days)
    }
}

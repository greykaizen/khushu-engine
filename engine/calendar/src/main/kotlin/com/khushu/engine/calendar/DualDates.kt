package com.khushu.engine.calendar

import java.time.LocalDate

/**
 * Resolves the configured dual-calendar lines for one civil date — the single
 * source of truth for side rendering, shared by the date API, month matrices,
 * and month summaries.
 */
object DualDates {

    /** One side's rendered line for [civilDate] under [config]. */
    fun line(side: CalendarConfiguration.Side, civilDate: LocalDate, config: CalendarConfiguration): DateLine =
        when (side) {
            CalendarConfiguration.Side.HIJRI ->
                DateLine.Hijri(HijriCalendar.hijri(civilDate, config.hijriOffsetDays))
            CalendarConfiguration.Side.GREGORIAN -> when (config.civilCalendar) {
                CivilCalendarType.GREGORIAN -> DateLine.Gregorian(civilDate)
                else -> DateLine.Regional(RegionalCalendars.toRegional(civilDate, config.civilCalendar))
            }
        }

    /** Both sides resolved (primary + optional secondary). */
    fun of(civilDate: LocalDate, config: CalendarConfiguration): DualDate =
        DualDate(
            line(config.primary, civilDate, config),
            config.secondary?.let { line(it, civilDate, config) },
        )
}

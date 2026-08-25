package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.astronomy.SolarEvents
import com.khushu.engine.calendar.HijriCalendar
import com.khushu.engine.calendar.CalendarParams
import com.khushu.engine.calendar.FastRule
import com.khushu.engine.calendar.HijriDate
import com.khushu.engine.calendar.IslamicEvent
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import com.khushu.engine.prayer.PrayerConfiguration
import com.khushu.engine.prayer.PrayerTimesResult
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Everything a "today" screen needs for one civil day, computed in a single
 * internal pass — shared facts (hijri date, rise/set) are evaluated once and
 * reused across the summary instead of each feature recomputing independently.
 */
data class DaySummary(
    val date: LocalDate,
    val hijri: HijriDate,
    val sacredMonth: Boolean,
    val events: List<IslamicEvent>,
    /** Optional fast rules observed today under [CalendarParams]. */
    val fastRules: List<FastRule>,
    val prayerTimes: PrayerTimesResult,
    val sunrise: Instant?,
    val sunset: Instant?,
    val dayLength: Duration?,
    val solarEvents: SolarEvents,
    /** Moon state sampled at local solar noon. */
    val moonAtNoon: MoonState,
    val moonRiseSet: RiseSet,
    /** Hijri month length of the month containing this day. */
    val hijriMonthLength: Int,
)

class DayApi internal constructor() {

    fun summary(
        location: Location,
        date: LocalDate,
        zoneId: ZoneId,
        prayerParams: PrayerConfiguration = PrayerConfiguration(),
        calendarParams: CalendarParams = CalendarParams(),
    ): DaySummary {
        // Each fact below is computed exactly once; nothing delegates twice.
        val hijri = HijriCalendar.hijri(date, calendarParams.hijriOffsetDays)
        val events = HijriCalendar.events(date, calendarParams.hijriOffsetDays)
        val fastRules = HijriCalendar
            .fastDays(date..date, calendarParams)
            .map { it.rule }
        val prayerTimes = Prayer.times(location, date, prayerParams)
        val sunRiseSet = Astronomy.sun.riseSet(location, date, zoneId)
        val solarEvents = Astronomy.sun.events(location, date, zoneId)
        val moonRiseSet = Astronomy.moon.riseSet(location, date, zoneId)

        // Sample the moon at the day's midpoint between sunrise and sunset
        // when available; UTC noon otherwise (polar days).
        val riseMs = sunRiseSet.riseEpochMs
        val setMs = sunRiseSet.setEpochMs
        val noonInstant = if (riseMs != null && setMs != null) {
            Instant.ofEpochMilli((riseMs + setMs) / 2)
        } else {
            date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        }
        val moonState = Astronomy.moon.state(location, noonInstant)

        return DaySummary(
            date = date,
            hijri = hijri,
            sacredMonth = HijriCalendar.isSacredMonth(hijri.month),
            events = events,
            fastRules = fastRules,
            prayerTimes = prayerTimes,
            sunrise = riseMs?.let(Instant::ofEpochMilli),
            sunset = setMs?.let(Instant::ofEpochMilli),
            dayLength =
                if (riseMs != null && setMs != null && setMs > riseMs) {
                    Duration.ofMillis(setMs - riseMs)
                } else {
                    null
                },
            solarEvents = solarEvents,
            moonAtNoon = moonState,
            moonRiseSet = moonRiseSet,
            hijriMonthLength = HijriCalendar.hijriMonthLengths(hijri.year, calendarParams.hijriOffsetDays)[hijri.month - 1],
        )
    }
}

/**
 * Opt-in memoization decorator around any [KhushuEngine]. Deterministic
 * capabilities only, keyed by their own semantic keys (AGENTS.md §6):
 *   prayer times → (Location, LocalDate, PrayerConfiguration)
 *   sun position → (Location, Instant)
 *   moon state   → (Location, Instant)
 *   hijri        → (LocalDate, offsetDays)
 * Configuration that affects results is part of every key — caching can never
 * hide a dependency.
 */
class CachedEngine(val delegate: KhushuEngine = KhushuEngine()) {

    private val prayerCache = java.util.concurrent.ConcurrentHashMap<String, com.khushu.engine.prayer.PrayerTimesResult>()
    private val prayerMonthCache = java.util.concurrent.ConcurrentHashMap<String, List<com.khushu.engine.prayer.PrayerTimesResult>>()
    private val sunCache = java.util.concurrent.ConcurrentHashMap<String, com.khushu.engine.astronomy.SolarPosition>()
    private val moonStateCache = java.util.concurrent.ConcurrentHashMap<String, com.khushu.engine.astronomy.MoonState>()
    private val hijriCache = java.util.concurrent.ConcurrentHashMap<String, com.khushu.engine.calendar.HijriDate>()

    private fun Location.key() = "%.9f|%.9f|%.3f".format(
        latitude.degrees, longitude.degrees, altitudeMeters.meters,
    )

    fun prayerTimes(location: Location, date: LocalDate, params: PrayerConfiguration = PrayerConfiguration()) =
        prayerCache.getOrPut("${location.key()}|$date|${params.hashCode()}") {
            delegate.prayer.times(location, date, params)
        }

    /**
     * Whole-month memoization for calendar/scroll UIs: prefetch next month on
     * swipe; repeated swipes never recompute. Keyed (Location, YearMonth, Config).
     */
    fun prayerMonth(
        location: Location,
        yearMonth: java.time.YearMonth,
        params: PrayerConfiguration = PrayerConfiguration(),
    ): List<com.khushu.engine.prayer.PrayerTimesResult> =
        prayerMonthCache.getOrPut("${location.key()}|$yearMonth|${params.hashCode()}") {
            delegate.prayer.month(location, yearMonth, params)
        }

    fun sunPosition(location: Location, instant: Instant) =
        sunCache.getOrPut("${location.key()}|${instant.toEpochMilli()}") {
            delegate.astronomy.sun.position(location, instant)
        }

    fun moonState(location: Location, instant: Instant) =
        moonStateCache.getOrPut("${location.key()}|${instant.toEpochMilli()}") {
            delegate.astronomy.moon.state(location, instant)
        }

    fun hijri(date: LocalDate, offsetDays: Int = 0) =
        hijriCache.getOrPut("$date|$offsetDays") { delegate.calendar.hijri(date, offsetDays) }

    fun clearCaches() {
        prayerCache.clear(); prayerMonthCache.clear(); sunCache.clear(); moonStateCache.clear(); hijriCache.clear()
    }
}

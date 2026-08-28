package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.core.error.InvalidParameterException
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

    /**
     * The full "today screen" composite: hijri date, events, fast rules,
     * prayer times, sun/moon rise-set, solar events, and a noon moon sample —
     * every fact computed exactly once in a single call.
     */
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
 *   day summary  → (Location, LocalDate, ZoneId, PrayerConfiguration, CalendarParams)
 *   sun position → (Location, Instant)
 *   sun rise/set/events/phases → (Location, LocalDate, ZoneId)
 *   moon state   → (Location, Instant)
 *   moon rise/set → (Location, LocalDate, ZoneId)
 *   hijri        → (LocalDate, offsetDays)
 *   qibla        → (Location)
 * Configuration that affects results is part of every key — caching can never
 * hide a dependency. All caches are bounded LRU (GPS jitter must not grow
 * memory without bound in long-lived hosts).
 */
class CachedEngine(val delegate: KhushuEngine = KhushuEngine(), private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        const val DEFAULT_CAPACITY = 256
    }

    private class Lru<V>(private val cap: Int) {
        private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > cap
        }
        fun getOrPut(key: String, compute: () -> V): V = synchronized(map) {
            map[key] ?: compute().also { map[key] = it }
        }
        fun clear() = synchronized(map) { map.clear() }
        fun size() = synchronized(map) { map.size }
    }

    private val prayerCache = Lru<com.khushu.engine.prayer.PrayerTimesResult>(capacity)
    private val prayerMonthCache = Lru<List<com.khushu.engine.prayer.PrayerTimesResult>>(capacity)
    private val sunCache = Lru<com.khushu.engine.astronomy.SolarPosition>(capacity)
    private val sunRiseSetCache = Lru<com.khushu.engine.astronomy.RiseSet>(capacity)
    private val sunEventsCache = Lru<com.khushu.engine.astronomy.SolarEvents>(capacity)
    private val sunPhasesCache = Lru<com.khushu.engine.astronomy.SolarDayPhases>(capacity)
    private val moonStateCache = Lru<com.khushu.engine.astronomy.MoonState>(capacity)
    private val moonRiseSetCache = Lru<com.khushu.engine.astronomy.RiseSet>(capacity)
    private val hijriCache = Lru<com.khushu.engine.calendar.HijriDate>(capacity)
    private val daySummaryCache = Lru<DaySummary>(capacity)
    private val qiblaCache = Lru<com.khushu.engine.qibla.QiblaBearing>(capacity)

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

    /**
     * The full "today screen" composite, memoized — hosts hitting the summary
     * plus individual features for the same day pay each computation once.
     */
    fun daySummary(
        location: Location,
        date: LocalDate,
        zoneId: ZoneId,
        prayerParams: PrayerConfiguration = PrayerConfiguration(),
        calendarParams: com.khushu.engine.calendar.CalendarParams = com.khushu.engine.calendar.CalendarParams(),
    ): DaySummary = daySummaryCache.getOrPut(
        "${location.key()}|$date|$zoneId|${prayerParams.hashCode()}|${calendarParams.hashCode()}",
    ) {
        delegate.day.summary(location, date, zoneId, prayerParams, calendarParams)
    }

    fun sunPosition(location: Location, instant: Instant) =
        sunCache.getOrPut("${location.key()}|${instant.toEpochMilli()}") {
            delegate.astronomy.sun.position(location, instant)
        }

    fun sunRiseSet(location: Location, date: LocalDate, zoneId: ZoneId) =
        sunRiseSetCache.getOrPut("${location.key()}|$date|$zoneId") {
            delegate.astronomy.sun.riseSet(location, date, zoneId)
        }

    fun sunEvents(location: Location, date: LocalDate, zoneId: ZoneId) =
        sunEventsCache.getOrPut("${location.key()}|$date|$zoneId") {
            delegate.astronomy.sun.events(location, date, zoneId)
        }

    fun sunPhases(location: Location, date: LocalDate, zoneId: ZoneId) =
        sunPhasesCache.getOrPut("${location.key()}|$date|$zoneId") {
            delegate.astronomy.sun.phases(location, date, zoneId)
        }

    fun moonState(location: Location, instant: Instant) =
        moonStateCache.getOrPut("${location.key()}|${instant.toEpochMilli()}") {
            delegate.astronomy.moon.state(location, instant)
        }

    fun moonRiseSet(location: Location, date: LocalDate, zoneId: ZoneId) =
        moonRiseSetCache.getOrPut("${location.key()}|$date|$zoneId") {
            delegate.astronomy.moon.riseSet(location, date, zoneId)
        }

    fun hijri(date: LocalDate, offsetDays: Int = 0) =
        hijriCache.getOrPut("$date|$offsetDays") { delegate.calendar.hijri(date, offsetDays) }

    fun qibla(location: Location) =
        qiblaCache.getOrPut(location.key()) { delegate.qibla.bearing(location) }

    fun clearCaches() {
        prayerCache.clear(); prayerMonthCache.clear(); sunCache.clear()
        sunRiseSetCache.clear(); sunEventsCache.clear(); sunPhasesCache.clear()
        moonStateCache.clear(); moonRiseSetCache.clear(); hijriCache.clear()
        daySummaryCache.clear(); qiblaCache.clear()
    }

    /** Visible for tests: number of memoized entries in a named cache. */
    internal fun cacheSize(name: String): Int = when (name) {
        "sunRiseSet" -> sunRiseSetCache.size()
        "daySummary" -> daySummaryCache.size()
        else -> throw InvalidParameterException(
            "cacheName",
            name,
            "unknown cache; expected sunRiseSet or daySummary",
        )
    }
}

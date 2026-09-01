package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.astronomy.SolarEvents
import com.khushu.engine.calendar.HijriCalendar
import com.khushu.engine.calendar.CalendarConfiguration
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
 *   prayer times        → (Location, LocalDate, PrayerConfiguration)
 *   prayer month        → (Location, YearMonth, PrayerConfiguration)
 *   prayer occasions    → (Location, LocalDate, PrayerConfiguration)
 *   tahajjud/night div. → (Location, LocalDate, PrayerConfiguration)
 *   prayer windows      → (Location, LocalDate, PrayerConfiguration, margins/distances)
 *   day summary         → (Location, LocalDate, ZoneId, PrayerConfiguration, CalendarParams)
 *   sun position        → (Location, Instant)
 *   sun rise/set/events/phases → (Location, LocalDate, ZoneId)
 *   moon state          → (Location, Instant)
 *   moon rise/set       → (Location, LocalDate, ZoneId)
 *   hijri               → (LocalDate, offsetDays)
 *   calendar events     → (LocalDate | range, offsetDays)
 *   fast days           → (range, CalendarParams)
 *   month matrix        → (YearMonth, CalendarConfiguration)
 *   month summary       → (Location, YearMonth, ZoneId, CalendarConfiguration, CalendarParams)
 *   lunar month view    → (Location, YearMonth(hijri), ZoneId, CalendarConfiguration)
 *   qibla               → (Location)
 *   qibla shadow verify → (Year, Location)
 * Configuration that affects results is part of every key — caching can never
 * hide a dependency. All caches are bounded LRU (GPS jitter must not grow
 * memory without bound in long-lived hosts).
 *
 * `forceRecompute = true` on any method bypasses the lookup, recomputes, and
 * OVERWRITES the stored entry — use after deliberate host-side events
 * (settings import, time-travel debugging), never in hot paths.
 * Null results are cached too: a polar-day null computed once is not recomputed.
 */
class CachedEngine(val delegate: KhushuEngine = KhushuEngine(), private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        const val DEFAULT_CAPACITY = 256

        /** Valid domain names for [clearCaches]. */
        val DOMAINS: Set<String> = setOf(
            "prayer", "astronomySun", "astronomyMoon", "calendar", "qibla", "daySummary",
        )
    }

    private class Lru<V>(private val cap: Int) {
        private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?) = size > cap
        }
        fun getOrPut(key: String, forceRecompute: Boolean = false, compute: () -> V): V = synchronized(map) {
            if (!forceRecompute && map.containsKey(key)) map.getValue(key)
            else compute().also { map[key] = it }
        }
        fun clear() = synchronized(map) { map.clear() }
        fun size() = synchronized(map) { map.size }
    }

    private val prayerCache = Lru<com.khushu.engine.prayer.PrayerTimesResult>(capacity)
    private val prayerMonthCache = Lru<List<com.khushu.engine.prayer.PrayerTimesResult>>(capacity)
    private val occasionsCache = Lru<List<com.khushu.engine.prayer.Prayer.Occurrence>>(capacity)
    private val tahajjudCache = Lru<com.khushu.engine.prayer.TahajjudWindow?>(capacity)
    private val nightDivisionsCache = Lru<com.khushu.engine.prayer.Prayer.NightDivisions?>(capacity)
    private val imsakCache = Lru<Instant?>(capacity)
    private val duhaCache = Lru<ClosedRange<Instant>?>(capacity)
    private val forbiddenCache = Lru<com.khushu.engine.prayer.Prayer.ForbiddenWindows>(capacity)
    private val fastingFactsCache = Lru<com.khushu.engine.prayer.Prayer.FastingFacts>(capacity)
    private val fastingFactsMonthCache = Lru<List<com.khushu.engine.prayer.Prayer.FastingFacts>>(capacity)
    private val travelFactsCache = Lru<com.khushu.engine.prayer.Prayer.TravelFacts>(capacity)
    private val sunCache = Lru<com.khushu.engine.astronomy.SolarPosition>(capacity)
    private val sunRiseSetCache = Lru<com.khushu.engine.astronomy.RiseSet>(capacity)
    private val sunEventsCache = Lru<com.khushu.engine.astronomy.SolarEvents>(capacity)
    private val sunPhasesCache = Lru<com.khushu.engine.astronomy.SolarDayPhases>(capacity)
    private val moonStateCache = Lru<com.khushu.engine.astronomy.MoonState>(capacity)
    private val moonRiseSetCache = Lru<com.khushu.engine.astronomy.RiseSet>(capacity)
    private val hijriCache = Lru<com.khushu.engine.calendar.HijriDate>(capacity)
    private val calendarEventsCache = Lru<List<com.khushu.engine.calendar.IslamicEvent>>(capacity)
    private val calendarEventsRangeCache =
        Lru<List<Pair<LocalDate, com.khushu.engine.calendar.IslamicEvent>>>(capacity)
    private val fastDaysCache = Lru<List<com.khushu.engine.calendar.FastDay>>(capacity)
    private val monthMatrixCache = Lru<Map<LocalDate, com.khushu.engine.calendar.DualDate>>(capacity)
    private val monthSummaryCache = Lru<com.khushu.engine.calendar.MonthSummary>(capacity)
    private val lunarMonthViewCache =
        Lru<List<com.khushu.engine.calendar.LunarCalendarView.LunarDayFact>>(capacity)
    private val daySummaryCache = Lru<DaySummary>(capacity)
    private val qiblaCache = Lru<com.khushu.engine.qibla.QiblaBearing>(capacity)
    private val qiblaShadowCache = Lru<List<com.khushu.engine.qibla.QiblaShadowEvent>>(capacity)

    private fun Location.key() = "%.9f|%.9f|%.3f".format(
        latitude.degrees, longitude.degrees, altitudeMeters.meters,
    )

    fun prayerTimes(
        location: Location,
        date: LocalDate,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ) = prayerCache.getOrPut("${location.key()}|$date|${params.hashCode()}", forceRecompute) {
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
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.prayer.PrayerTimesResult> =
        prayerMonthCache.getOrPut("${location.key()}|$yearMonth|${params.hashCode()}", forceRecompute) {
            delegate.prayer.month(location, yearMonth, params)
        }

    /**
     * Category-labeled occurrences for a civil day, memoized. [categories] is
     * part of the cache key (order-insensitive) — different filters coexist.
     */
    fun prayerOccasions(
        location: Location,
        date: LocalDate,
        categories: Set<com.khushu.engine.prayer.Prayer.OccasionCategory> =
            com.khushu.engine.prayer.Prayer.OccasionCategory.entries.toSet(),
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.prayer.Prayer.Occurrence> {
        val catKey = categories.map { it.name }.sorted().joinToString(",")
        return occasionsCache.getOrPut("${location.key()}|$date|$catKey|${params.hashCode()}", forceRecompute) {
            delegate.prayer.occasionsOn(location, date, categories, params)
        }
    }

    fun tahajjudWindow(
        location: Location,
        date: LocalDate,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.prayer.TahajjudWindow? =
        tahajjudCache.getOrPut("${location.key()}|$date|${params.hashCode()}", forceRecompute) {
            delegate.prayer.tahajjudWindow(location, date, params)
        }

    fun nightDivisions(
        location: Location,
        date: LocalDate,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.prayer.Prayer.NightDivisions? =
        nightDivisionsCache.getOrPut("${location.key()}|$date|${params.hashCode()}", forceRecompute) {
            delegate.prayer.nightDivisions(location, date, params)
        }

    fun imsak(
        location: Location,
        date: LocalDate,
        minutesBeforeFajr: Int = 10,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): Instant? = imsakCache.getOrPut(
        "${location.key()}|$date|$minutesBeforeFajr|${params.hashCode()}",
        forceRecompute,
    ) { delegate.prayer.windows.imsak(location, date, minutesBeforeFajr, params) }

    fun duha(
        location: Location,
        date: LocalDate,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): ClosedRange<Instant>? =
        duhaCache.getOrPut("${location.key()}|$date|${params.hashCode()}", forceRecompute) {
            delegate.prayer.windows.duha(location, date, params)
        }

    fun forbiddenWindows(
        location: Location,
        date: LocalDate,
        lateAfternoonMarginMinutes: Int = 20,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.prayer.Prayer.ForbiddenWindows =
        forbiddenCache.getOrPut(
            "${location.key()}|$date|$lateAfternoonMarginMinutes|${params.hashCode()}",
            forceRecompute,
        ) { delegate.prayer.windows.forbidden(location, date, lateAfternoonMarginMinutes, params) }

    fun fastingFacts(
        location: Location,
        date: LocalDate,
        imsakMinutesBeforeFajr: Int = 10,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.prayer.Prayer.FastingFacts =
        fastingFactsCache.getOrPut(
            "${location.key()}|$date|$imsakMinutesBeforeFajr|${params.hashCode()}",
            forceRecompute,
        ) { delegate.prayer.windows.fastingFacts(location, date, imsakMinutesBeforeFajr, params) }

    fun fastingFactsForMonth(
        location: Location,
        yearMonth: java.time.YearMonth,
        imsakMinutesBeforeFajr: Int = 10,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.prayer.Prayer.FastingFacts> =
        fastingFactsMonthCache.getOrPut(
            "${location.key()}|$yearMonth|$imsakMinutesBeforeFajr|${params.hashCode()}",
            forceRecompute,
        ) { delegate.prayer.windows.fastingFactsForMonth(location, yearMonth, imsakMinutesBeforeFajr, params) }

    fun travelFacts(
        location: Location,
        date: LocalDate,
        travelledDistanceKm: com.khushu.engine.core.units.Kilometers,
        distanceThresholdKm: Double? = null,
        params: PrayerConfiguration = PrayerConfiguration(),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.prayer.Prayer.TravelFacts =
        travelFactsCache.getOrPut(
            "${location.key()}|$date|$travelledDistanceKm|$distanceThresholdKm|${params.hashCode()}",
            forceRecompute,
        ) { delegate.prayer.windows.travelFacts(location, date, travelledDistanceKm, distanceThresholdKm, params) }

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
        forceRecompute: Boolean = false,
    ): DaySummary = daySummaryCache.getOrPut(
        "${location.key()}|$date|$zoneId|${prayerParams.hashCode()}|${calendarParams.hashCode()}",
        forceRecompute,
    ) {
        delegate.day.summary(location, date, zoneId, prayerParams, calendarParams)
    }

    fun sunPosition(location: Location, instant: Instant, forceRecompute: Boolean = false) =
        sunCache.getOrPut("${location.key()}|${instant.toEpochMilli()}", forceRecompute) {
            delegate.astronomy.sun.position(location, instant)
        }

    fun sunRiseSet(location: Location, date: LocalDate, zoneId: ZoneId, forceRecompute: Boolean = false) =
        sunRiseSetCache.getOrPut("${location.key()}|$date|$zoneId", forceRecompute) {
            delegate.astronomy.sun.riseSet(location, date, zoneId)
        }

    fun sunEvents(location: Location, date: LocalDate, zoneId: ZoneId, forceRecompute: Boolean = false) =
        sunEventsCache.getOrPut("${location.key()}|$date|$zoneId", forceRecompute) {
            delegate.astronomy.sun.events(location, date, zoneId)
        }

    fun sunPhases(location: Location, date: LocalDate, zoneId: ZoneId, forceRecompute: Boolean = false) =
        sunPhasesCache.getOrPut("${location.key()}|$date|$zoneId", forceRecompute) {
            delegate.astronomy.sun.phases(location, date, zoneId)
        }

    fun moonState(location: Location, instant: Instant, forceRecompute: Boolean = false) =
        moonStateCache.getOrPut("${location.key()}|${instant.toEpochMilli()}", forceRecompute) {
            delegate.astronomy.moon.state(location, instant)
        }

    fun moonRiseSet(location: Location, date: LocalDate, zoneId: ZoneId, forceRecompute: Boolean = false) =
        moonRiseSetCache.getOrPut("${location.key()}|$date|$zoneId", forceRecompute) {
            delegate.astronomy.moon.riseSet(location, date, zoneId)
        }

    fun hijri(date: LocalDate, offsetDays: Int = 0, forceRecompute: Boolean = false) =
        hijriCache.getOrPut("$date|$offsetDays", forceRecompute) { delegate.calendar.hijri(date, offsetDays) }

    /** Fixed-hijri events for one civil day, memoized. */
    fun calendarEvents(
        date: LocalDate,
        offsetDays: Int = 0,
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.calendar.IslamicEvent> =
        calendarEventsCache.getOrPut("$date|$offsetDays", forceRecompute) {
            delegate.calendar.events(date, offsetDays)
        }

    /** Events across a civil range, memoized per (range, offset). */
    fun calendarEventsInRange(
        range: ClosedRange<LocalDate>,
        offsetDays: Int = 0,
        forceRecompute: Boolean = false,
    ): List<Pair<LocalDate, com.khushu.engine.calendar.IslamicEvent>> =
        calendarEventsRangeCache.getOrPut(
            "${range.start}|${range.endInclusive}|$offsetDays",
            forceRecompute,
        ) { delegate.calendar.eventsInRange(range, offsetDays) }

    /** Optional fast days across a civil range, memoized per (range, params). */
    fun calendarFastDays(
        range: ClosedRange<LocalDate>,
        params: CalendarParams,
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.calendar.FastDay> =
        fastDaysCache.getOrPut(
            "${range.start}|${range.endInclusive}|${params.hashCode()}",
            forceRecompute,
        ) { delegate.calendar.fastDays(range, params) }

    /** Dual-calendar month grid, memoized per (YearMonth, configuration). */
    fun calendarMonthMatrix(
        civilMonth: java.time.YearMonth,
        config: CalendarConfiguration,
        forceRecompute: Boolean = false,
    ): Map<LocalDate, com.khushu.engine.calendar.DualDate> =
        monthMatrixCache.getOrPut("$civilMonth|${config.hashCode()}", forceRecompute) {
            delegate.calendar.month.monthMatrix(civilMonth, config)
        }

    /**
     * Whole-month composite (dual dates + events + fast rules + moon facts),
     * memoized — the calendar home screen pays one computation per month.
     */
    fun monthSummary(
        civilMonth: java.time.YearMonth,
        location: Location,
        zoneId: ZoneId,
        config: CalendarConfiguration,
        calendarParams: com.khushu.engine.calendar.CalendarParams =
            com.khushu.engine.calendar.CalendarParams(hijriOffsetDays = config.hijriOffsetDays),
        forceRecompute: Boolean = false,
    ): com.khushu.engine.calendar.MonthSummary = monthSummaryCache.getOrPut(
        "${location.key()}|$zoneId|$civilMonth|${config.hashCode()}|${calendarParams.hashCode()}",
        forceRecompute,
    ) { delegate.calendar.month.summary(civilMonth, location, zoneId, config, calendarParams) }

    /**
     * Per-evening moon facts for a hijri month, memoized — the astronomically
     * expensive calendar op; repeated month swipes never recompute.
     */
    fun lunarMonthView(
        hijriYear: Int,
        hijriMonth: Int,
        location: Location,
        zoneId: ZoneId,
        config: CalendarConfiguration = CalendarConfiguration(primary = CalendarConfiguration.Side.HIJRI),
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.calendar.LunarCalendarView.LunarDayFact> =
        lunarMonthViewCache.getOrPut(
            "${location.key()}|$zoneId|$hijriYear|$hijriMonth|${config.hashCode()}",
            forceRecompute,
        ) { delegate.calendar.lunar.monthView(hijriYear, hijriMonth, location, zoneId, config) }

    fun qibla(location: Location, forceRecompute: Boolean = false) =
        qiblaCache.getOrPut(location.key(), forceRecompute) { delegate.qibla.bearing(location) }

    /**
     * Shadow-qibla verification windows for [year] at [location], memoized —
     * astronomy facts for a (year, location) never change.
     */
    fun qiblaShadowVerification(
        year: Int,
        location: Location,
        forceRecompute: Boolean = false,
    ): List<com.khushu.engine.qibla.QiblaShadowEvent> =
        qiblaShadowCache.getOrPut("$year|${location.key()}", forceRecompute) {
            delegate.qibla.shadowVerification(year, location)
        }

    /** Clear every cache. */
    fun clearCaches() {
        DOMAINS.forEach { clearCaches(it) }
    }

    /**
     * Clear one domain's caches. Valid names in [DOMAINS]: prayer,
     * astronomySun, astronomyMoon, calendar, qibla, daySummary.
     * @throws InvalidParameterException for an unknown domain name.
     */
    fun clearCaches(domain: String) {
        when (domain) {
            "prayer" -> {
                prayerCache.clear(); prayerMonthCache.clear(); occasionsCache.clear()
                tahajjudCache.clear(); nightDivisionsCache.clear(); imsakCache.clear()
                duhaCache.clear(); forbiddenCache.clear(); fastingFactsCache.clear()
                fastingFactsMonthCache.clear(); travelFactsCache.clear()
            }
            "astronomySun" -> {
                sunCache.clear(); sunRiseSetCache.clear(); sunEventsCache.clear(); sunPhasesCache.clear()
            }
            "astronomyMoon" -> { moonStateCache.clear(); moonRiseSetCache.clear() }
            "calendar" -> {
                hijriCache.clear(); calendarEventsCache.clear(); calendarEventsRangeCache.clear()
                fastDaysCache.clear(); monthMatrixCache.clear(); monthSummaryCache.clear()
                lunarMonthViewCache.clear()
            }
            "qibla" -> { qiblaCache.clear(); qiblaShadowCache.clear() }
            "daySummary" -> daySummaryCache.clear()
            else -> throw InvalidParameterException("domain", domain, "unknown cache domain; expected one of $DOMAINS")
        }
    }

    /** Visible for tests: number of memoized entries in a named cache. */
    internal fun cacheSize(name: String): Int = when (name) {
        "sunRiseSet" -> sunRiseSetCache.size()
        "daySummary" -> daySummaryCache.size()
        "prayerTimes" -> prayerCache.size()
        "hijri" -> hijriCache.size()
        "calendarEvents" -> calendarEventsCache.size()
        "calendarEventsInRange" -> calendarEventsRangeCache.size()
        "fastDays" -> fastDaysCache.size()
        "monthMatrix" -> monthMatrixCache.size()
        "monthSummary" -> monthSummaryCache.size()
        "lunarMonthView" -> lunarMonthViewCache.size()
        "qiblaShadow" -> qiblaShadowCache.size()
        else -> throw InvalidParameterException(
            "cacheName",
            name,
            "unknown cache; expected one of: sunRiseSet, daySummary, prayerTimes, hijri, " +
                "calendarEvents, calendarEventsInRange, fastDays, monthMatrix, monthSummary, " +
                "lunarMonthView, qiblaShadow",
        )
    }
}

package com.khushu.engine.facadetest

import com.khushu.engine.CachedEngine
import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** CachedEngine: extended coverage (rise/set, events, phases, summary, qibla) + LRU bound. */
class CacheCoverageTest {

    private val engine = KhushuEngine()
    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2025, 6, 21)

    @Test
    fun solarAndLunarDayFactsAreMemoized() {
        val cached = CachedEngine(engine)
        assertEquals(cached.sunRiseSet(london, date, zone), cached.sunRiseSet(london, date, zone))
        assertEquals(cached.sunEvents(london, date, zone), cached.sunEvents(london, date, zone))
        assertEquals(cached.sunPhases(london, date, zone), cached.sunPhases(london, date, zone))
        assertEquals(cached.moonRiseSet(london, date, zone), cached.moonRiseSet(london, date, zone))
        assertEquals(cached.qibla(london), engine.qibla.bearing(london))
        assertTrue(cached.cacheSize("sunRiseSet") <= 1)
    }

    @Test
    fun daySummaryIsMemoizedAndMatchesUncached() {
        val cached = CachedEngine(engine)
        val direct = engine.day.summary(london, date, zone)
        val first = cached.daySummary(london, date, zone)
        val second = cached.daySummary(london, date, zone)
        assertEquals(direct, first)
        assertEquals(first, second)
        assertTrue(cached.cacheSize("daySummary") <= 1)
        // different config must not collide with the default-config entry
        val hanafi = cached.daySummary(
            london, date, zone,
            com.khushu.engine.prayer.PrayerConfiguration(madhab = com.khushu.engine.prayer.Madhab.HANAFI),
        )
        assertEquals(
            engine.prayer.times(
                london, date,
                com.khushu.engine.prayer.PrayerConfiguration(madhab = com.khushu.engine.prayer.Madhab.HANAFI),
            ).asr,
            hanafi.prayerTimes.asr,
        )
        assertTrue(cached.cacheSize("daySummary") <= 2)
    }

    @Test
    fun cachesAreBoundedLru() {
        val cached = CachedEngine(engine, capacity = 8)
        repeat(30) { i -> cached.sunRiseSet(london, date.plusDays(i.toLong()), zone) }
        assertTrue(cached.cacheSize("sunRiseSet") <= 8, "LRU must evict beyond capacity")
        // eviction must not corrupt results: a recomputed entry stays correct
        val ref = engine.astronomy.sun.riseSet(london, date, zone)
        assertEquals(ref, cached.sunRiseSet(london, date, zone))
    }

    @Test
    fun facadeSunExposesPhasesTrackAndNightLength() {
        val phases = engine.astronomy.sun.phases(london, date, zone)
        assertTrue(phases.toString().isNotEmpty())
        val night = engine.astronomy.sun.nightLength(london, date, zone)
        assertTrue(night != null && night.toHours() in 6..10) // June in London: ~7h night
        val track = engine.astronomy.sun.track(london, java.time.YearMonth.of(2025, 6), zone)
        assertEquals(30, track.days.size)
        assertEquals(engine.astronomy.sun.conventions().toString().isNotEmpty(), true)
    }

    @Test
    fun forceRecomputeOverwritesTheStoredEntry() {
        val cached = CachedEngine(engine)
        val first = cached.prayerTimes(london, date)
        assertSame(first, cached.prayerTimes(london, date), "second call must hit the cache")
        val fresh = cached.prayerTimes(london, date, forceRecompute = true)
        assertEquals(first, fresh, "recompute must be value-identical (deterministic)")
        assertNotSame(first, fresh, "recompute must produce a new instance")
        assertSame(fresh, cached.prayerTimes(london, date), "fresh value must be stored, not discarded")
    }

    @Test
    fun clearCachesScopesByDomainAndRejectsUnknownNames() {
        val cached = CachedEngine(engine)
        cached.prayerTimes(london, date)
        cached.qibla(london)
        cached.hijri(date)
        assertEquals(1, cached.cacheSize("prayerTimes"))
        cached.clearCaches("prayer")
        assertEquals(0, cached.cacheSize("prayerTimes"))
        assertEquals(engine.prayer.times(london, date), cached.prayerTimes(london, date))
        assertFailsWith<InvalidParameterException> { cached.clearCaches("bogus") }
        cached.clearCaches()
        assertEquals(0, cached.cacheSize("prayerTimes"))
    }

    @Test
    fun occasionsAreMemoizedPerCategoryFilter() {
        val cached = CachedEngine(engine)
        val all = cached.prayerOccasions(london, date)
        assertSame(all, cached.prayerOccasions(london, date))
        val voluntary = cached.prayerOccasions(london, date, setOf(Prayer.OccasionCategory.VOLUNTARY))
        assertTrue(all.containsAll(voluntary))
        assertTrue(voluntary.all { it.category == Prayer.OccasionCategory.VOLUNTARY })
        // filter is part of the key; set order must not change the key
        val forward = cached.prayerOccasions(
            london, date,
            setOf(Prayer.OccasionCategory.OBLIGATORY, Prayer.OccasionCategory.BOUNDARY_EVENT),
        )
        val reverse = cached.prayerOccasions(
            london, date,
            setOf(Prayer.OccasionCategory.BOUNDARY_EVENT, Prayer.OccasionCategory.OBLIGATORY),
        )
        assertSame(forward, reverse)
        assertEquals(forward, reverse)
    }

    @Test
    fun facadeUpcomingRotatesCurrentFirstAcrossMidnight() {
        val t = engine.prayer.times(london, date)
        val afterMaghrib = (t.maghrib.adjusted ?: t.sunset.adjusted)!!.plusSeconds(60)
        val upcoming = engine.prayer.upcoming(london, afterMaghrib, zone, count = 3)
        assertEquals(
            listOf(
                com.khushu.engine.prayer.PrayerStatus.Prayer.MAGHRIB,
                com.khushu.engine.prayer.PrayerStatus.Prayer.ISHA,
                com.khushu.engine.prayer.PrayerStatus.Prayer.FAJR,
            ),
            upcoming.map { it.kind },
        )
        assertEquals(date.plusDays(1), upcoming.last().instant.atZone(zone).toLocalDate())
        // nextOccurrenceOf + forceRecompute independence: navigation never caches
        val fajr = engine.prayer.nextOccurrenceOf(
            com.khushu.engine.prayer.PrayerStatus.Prayer.FAJR, london, afterMaghrib, zone,
        )
        assertEquals(upcoming.last().instant, fajr?.instant)
    }

    @Test
    fun calendarDomainCachesHitOverwriteAndClearTogether() {
        val cached = CachedEngine(engine)
        val calendarConfig = com.khushu.engine.calendar.CalendarConfiguration(
            primary = com.khushu.engine.calendar.CalendarConfiguration.Side.HIJRI,
        )
        val ramadan = LocalDate.of(2026, 3, 1)
        val range = ramadan..ramadan.plusDays(29)

        val events1 = cached.calendarEvents(ramadan)
        assertSame(events1, cached.calendarEvents(ramadan))
        // different offset keys a separate entry (proven by cacheSize below,
        // since empty results share Kotlin's emptyList() singleton)
        cached.calendarEvents(ramadan, offsetDays = 1)

        val range1 = cached.calendarEventsInRange(range)
        assertSame(range1, cached.calendarEventsInRange(range))

        val params = com.khushu.engine.calendar.CalendarParams(whiteDays = true)
        val fast1 = cached.calendarFastDays(range, params)
        assertSame(fast1, cached.calendarFastDays(range, params))
        assertNotSame(fast1, cached.calendarFastDays(range, params.copy(whiteDays = false)))

        val ym = java.time.YearMonth.of(2026, 3)
        val matrix1 = cached.calendarMonthMatrix(ym, calendarConfig)
        assertSame(matrix1, cached.calendarMonthMatrix(ym, calendarConfig))
        assertEquals(31, matrix1.size)

        val lunar1 = cached.lunarMonthView(1447, 9, london, zone, calendarConfig)
        assertSame(lunar1, cached.lunarMonthView(1447, 9, london, zone, calendarConfig))
        assertTrue(lunar1.isNotEmpty())
        val lunarFresh = cached.lunarMonthView(1447, 9, london, zone, calendarConfig, forceRecompute = true)
        assertEquals(lunar1, lunarFresh)
        assertNotSame(lunar1, lunarFresh)
        assertSame(lunarFresh, cached.lunarMonthView(1447, 9, london, zone, calendarConfig))

        assertTrue(cached.cacheSize("calendarEvents") == 2)
        assertTrue(cached.cacheSize("lunarMonthView") == 1)
        cached.clearCaches("calendar")
        assertEquals(0, cached.cacheSize("hijri"))
        assertEquals(0, cached.cacheSize("calendarEvents"))
        assertEquals(0, cached.cacheSize("calendarEventsInRange"))
        assertEquals(0, cached.cacheSize("fastDays"))
        assertEquals(0, cached.cacheSize("monthMatrix"))
        assertEquals(0, cached.cacheSize("lunarMonthView"))
    }
}

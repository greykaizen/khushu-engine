package com.khushu.engine.facadetest

import com.khushu.engine.CachedEngine
import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

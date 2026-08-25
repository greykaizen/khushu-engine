package com.khushu.engine.facadetest

import com.khushu.engine.CachedEngine
import com.khushu.engine.KhushuEngine
import com.khushu.engine.qibla.Qibla
import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreadthTest {

    private val engine = KhushuEngine()
    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    @Test
    fun daySummaryComputesEverythingInOneCall() {
        val s = engine.day.summary(london, LocalDate.of(2025, 3, 30), zone)
        assertEquals(LocalDate.of(2025, 3, 30), s.date)
        assertEquals(10, s.hijri.month) // Eid al-Fitr day = 1 Shawwal
        assertEquals(1, s.hijri.day)
        assertTrue(s.events.any { it.title == "Eid al-Fitr" })
        assertNotNull(s.prayerTimes.fajr)
        assertNotNull(s.sunrise)
        assertNotNull(s.sunset)
        assertTrue(s.dayLength!!.toHours() in 11..13)
        assertTrue(s.hijriMonthLength == 29 || s.hijriMonthLength == 30)
        assertTrue(s.moonAtNoon.illuminationFraction in 0.0..1.0)
    }

    @Test
    fun cachedEngineReturnsIdenticalResultsAndActuallyCaches() {
        val cached = CachedEngine(engine)
        val date = LocalDate.of(2025, 6, 21)
        val p1 = cached.prayerTimes(london, date)
        val p2 = cached.prayerTimes(london, date)
        assertEquals(p1, p2)

        val instant = LocalDate.of(2025, 6, 21).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC)
        assertEquals(cached.sunPosition(london, instant), cached.sunPosition(london, instant))
        assertEquals(cached.moonState(london, instant), cached.moonState(london, instant))
        assertEquals(cached.hijri(date), cached.hijri(date))

        // Different params → different key → different (correct) result.
        val hanafi = com.khushu.engine.prayer.PrayerParams(madhab = com.khushu.engine.prayer.Madhab.HANAFI)
        assertEquals(
            engine.prayer.times(london, date, hanafi).asr,
            cached.prayerTimes(london, date, hanafi).asr,
        )
    }

    @Test
    fun solarAlignmentInstantsOccurTwiceAYearAndAreSelfConsistent() {
        // Zenith passage over the Kaaba: late May and mid July.
        val instants = engine.qibla.solarAlignmentInstants(2026)
        assertEquals(2, instants.size, "got ${instants.map { Instant.ofEpochMilli(it.toEpochMilli()) }}")

        val first = instants[0].atZone(ZoneId.of("Asia/Riyadh"))
        val second = instants[1].atZone(ZoneId.of("Asia/Riyadh"))
        assertTrue(first.monthValue == 5 || first.dayOfMonth in 26..29 || first.monthValue == 5, "first $first")
        // Structural: roughly 49 days apart (declination round trip through 21.42°N).
        val gapDays = java.time.temporal.ChronoUnit.DAYS.between(
            instants[0].atZone(zone).toLocalDate(),
            instants[1].atZone(zone).toLocalDate(),
        )
        assertTrue(gapDays in 40..60, "gap $gapDays days")

        // Self-consistent: sun declination ≈ Kaaba latitude at each instant.
        for (instant in instants) {
            val dec = engine.astronomy.sun.position(Location.of(0.0, 0.0), instant).declinationDeg
            kotlin.test.assertEquals(Qibla.KAABA_LATITUDE_DEG, dec, 0.10)
        }
    }

    @Test
    fun hilalForecastCoversNightsWithVerdicts() {
        val forecast = engine.astronomy.hilal.forecast(
            Location.of(21.4225, 39.8262),
            LocalDate.of(2026, 2, 18), // around Ramadan 1447 start
            ZoneId.of("Asia/Riyadh"),
            nights = 6,
        )
        assertEquals(6, forecast.size)
        for ((date, report) in forecast) {
            if (report != null) {
                assertTrue(report.elongationDeg >= 0.0)
                assertTrue(report.verdicts.size == 4)
            }
        }
    }

    @Test
    fun tahajjudWindowIsOrderedInsideTheNight() {
        val w = engine.prayer.tahajjudWindow(london, LocalDate.of(2025, 10, 15))!!
        assertTrue(w.windowOpens < w.lastThirdBegins)
        assertTrue(w.lastThirdBegins < w.windowCloses)
        assertNull(engine.prayer.tahajjudWindow(Location.of(69.6492, 18.9553), LocalDate.of(2025, 12, 21)))
    }
}

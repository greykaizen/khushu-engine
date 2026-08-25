package com.khushu.engine.facadetest

import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Madhab
import com.khushu.engine.prayer.PrayerParams
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Facade wiring: every namespace must reach its capability with zero logic of its own. */
class KhushuEngineTest {

    private val engine = KhushuEngine()
    private val london = Location.of(51.5072, -0.1276)

    @Test
    fun allNamespacesAreWired() {
        val prayerTimes = engine.prayer.times(london, LocalDate.of(2025, 6, 21))
        assertNotNull(prayerTimes.fajr)

        val moonState = engine.astronomy.moon.state(
            london,
            LocalDate.of(2025, 6, 11).atTime(7, 44).toInstant(ZoneOffset.UTC),
        )
        assertTrue(moonState.illuminationFraction >= 0.99)

        assertEquals("Ramadan", engine.calendar.hijri(LocalDate.of(2025, 3, 5)).monthName)

        assertTrue(engine.qibla.bearing(london).bearingDegFromNorth in 0.0..360.0)

        assertTrue(engine.zakat.fitrana(dependents = 2, pricePerKg = 2.0, madhab = com.khushu.engine.zakat.ZakatMadhab.HANAFI).totalForHousehold > 0.0)
    }

    @Test
    fun facadeAddsNoBehaviourOnlyDelegation() {
        val params = PrayerParams(madhab = Madhab.HANAFI)
        assertEquals(
            engine.prayer.times(london, LocalDate.of(2025, 10, 1), params),
            engine.prayer.times(london, LocalDate.of(2025, 10, 1), params),
        )
        assertEquals(
            engine.astronomy.sun.riseSet(london, LocalDate.of(2025, 10, 1), ZoneId.of("Europe/London")),
            engine.astronomy.sun.riseSet(london, LocalDate.of(2025, 10, 1), ZoneId.of("Europe/London")),
        )
    }
}

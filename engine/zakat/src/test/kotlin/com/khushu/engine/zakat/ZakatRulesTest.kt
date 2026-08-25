package com.khushu.engine.zakat

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZakatRulesTest {

    @Test
    fun hawlAnniversaryIsOneHijriYearLater() {
        // Owned since 2025-03-01 = 1 Ramadan 1446 → anniversary ≈ 1 Ramadan 1447.
        val anniversary = ZakatRules.hawlAnniversary(LocalDate.of(2025, 3, 1), 0) { d, off ->
            com.khushu.engine.calendar.HijriCalendar.hijri(d, off).let { Triple(it.year, it.month, it.day) }
        }
        val h = com.khushu.engine.calendar.HijriCalendar.hijri(anniversary)
        assertEquals(1447, h.year)
        assertEquals(9, h.month)
        assertEquals(1, h.day)
        assertTrue(anniversary.isAfter(LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun livestockNisabsMatchClassicalSchedule() {
        assertEquals(5, ZakatRules.livestockNisab(ZakatRules.LivestockKind.CAMELS))
        assertEquals(30, ZakatRules.livestockNisab(ZakatRules.LivestockKind.CATTLE))
        assertEquals(40, ZakatRules.livestockNisab(ZakatRules.LivestockKind.SHEEP_OR_GOATS))
    }

    @Test
    fun camelScheduleSlotsAreExact() {
        assertEquals(null, ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 4))
        assertEquals(
            "one sheep/goat",
            ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 5),
        )
        assertTrue(ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 25)!!.contains("bint makhad"))
        assertTrue(ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 36)!!.contains("bint labun"))
        // Above 121: formula-based. 150 → excess 29 (< 40) stays sheep.
        val due150 = ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 150)
        assertTrue(due150!!.contains("29 sheep"), "got: $due150")
        val due200 = ZakatRules.livestockDue(ZakatRules.LivestockKind.CAMELS, 200)
        assertTrue(due200!!.contains("1 cow"), "got: $due200")
    }

    @Test
    fun cattleAndSheepAreCountBased() {
        // 30 cattle → 1 tabi'; 60 → 2; 45 sheep → 1 sheep.
        assertTrue(ZakatRules.livestockDue(ZakatRules.LivestockKind.CATTLE, 30)!!.startsWith("1 "))
        assertTrue(ZakatRules.livestockDue(ZakatRules.LivestockKind.CATTLE, 60)!!.startsWith("2 "))
        assertEquals(null, ZakatRules.livestockDue(ZakatRules.LivestockKind.SHEEP_OR_GOATS, 39))
        assertTrue(ZakatRules.livestockDue(ZakatRules.LivestockKind.SHEEP_OR_GOATS, 80)!!.startsWith("2 "))
    }

    @Test
    fun ushrRatesAreTenAndFivePercent() {
        assertEquals(0.10, ZakatRules.ushrRate(ZakatRules.Irrigation.NATURAL))
        assertEquals(0.05, ZakatRules.ushrRate(ZakatRules.Irrigation.ARTIFICIAL))
        assertEquals(1000.0, ZakatRules.ushrDue(10_000.0, ZakatRules.Irrigation.NATURAL), 0.001)
        assertEquals(500.0, ZakatRules.ushrDue(10_000.0, ZakatRules.Irrigation.ARTIFICIAL), 0.001)
        // No nisab minimum on produce under the majority rule.
        assertEquals(10.0, ZakatRules.ushrDue(100.0, ZakatRules.Irrigation.NATURAL), 0.001)
    }

    @Test
    fun rikazIsFlatTwentyPercent() {
        assertEquals(200.0, ZakatRules.rikazDue(1_000.0), 0.001)
        assertEquals(0.20, ZakatRules.RIKAZ_RATE)
    }
}

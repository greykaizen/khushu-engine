package com.khushu.engine.facadetest

import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Facade passthroughs for the v1.10 zakat/qibla coherence additions. */
class ZakatQiblaBreadthTest {

    private val engine = KhushuEngine()
    private val london = Location.of(51.5072, -0.1276)

    @Test
    fun hawlFactsPassthroughMatchesModule() {
        val start = LocalDate.of(2026, 1, 1)
        val today = LocalDate.of(2026, 7, 1)
        assertEquals(
            com.khushu.engine.zakat.ZakatRules.hawlFacts(start, today),
            engine.zakat.hawlFacts(start, today),
        )
    }

    @Test
    fun malExposesDualThresholdsAndNotesThroughFacade() {
        val assets = com.khushu.engine.zakat.ZakatAssets(
            cash = 10_000.0, wornGoldGrams = 100.0,
            goldPricePerGram = 75.0, silverPricePerGram = 0.9,
        )
        val shafii = engine.zakat.mal(assets, com.khushu.engine.zakat.ZakatParams(madhab = com.khushu.engine.zakat.ZakatMadhab.SHAFII))
        assertTrue(shafii.goldNisabThreshold != null && shafii.silverNisabThreshold != null)
        assertTrue(shafii.notes.isNotEmpty())
    }

    @Test
    fun relativeSunAngleDelegatesToModuleAndAuditIsExposed() {
        val instant = LocalDate.of(2026, 6, 21).atTime(12, 0)
            .atZone(java.time.ZoneId.of("Europe/London")).toInstant()
        assertEquals(
            com.khushu.engine.qibla.Qibla.relativeSunAngle(london, instant),
            engine.qibla.relativeSunAngle(london, instant),
        )
        assertEquals(com.khushu.engine.qibla.Qibla.audit(), engine.qibla.audit())
    }
}

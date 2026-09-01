package com.khushu.engine.prayer

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ANTI_TRANSIT_FAJR — persistent-twilight substitution (v1.17).
 * Tested against London summer solstice (Fajr angle unreachable).
 */
class AntiTransitFajrTest {

    private val london = com.khushu.engine.core.geo.Location.of(51.5072, -0.1276, 11.0)

    // Summer solstice: the sun never reaches -18° in London
    private val date = LocalDate.parse("2026-06-21")

    @Test
    fun antiTransitSubstitutedWhenAngleUnreachable() {
        val config = PrayerConfiguration(
            convention = Convention.MUSLIM_WORLD_LEAGUE,
            highLatitudeRule = HighLatitudeRule.ANTI_TRANSIT_FAJR,
        )
        val times = Prayer.times(london, date, config)
        val fajr = times.fajr.adjusted
        assertNotNull(fajr, "anti-transit substitutes for persistent twilight Fajr")
        // audit carries the substitution
        assertEquals(
            HighLatitudeResolution.RESOLVED_BY_ANTI_TRANSIT,
            times.audit.highLatitudeResolution,
        )
        // warning note emitted
        assertTrue(
            times.audit.warnings.any { it.contains("anti-transit") },
            "audit carries the anti-transit substitution note",
        )
    }

    @Test
    fun normalLatitudeFajrUnaffectedByAntiTransitRule() {
        // In Makkah, Fajr angle is always reachable — ANTI_TRANSIT rule should
        // produce normal computation (no substitution, same as default rule)
        val makkah = com.khushu.engine.core.geo.Location.of(21.42, 39.83, 310.0)
        val makkahDate = LocalDate.parse("2026-09-01")
        val config = PrayerConfiguration(
            convention = Convention.MUSLIM_WORLD_LEAGUE,
            highLatitudeRule = HighLatitudeRule.ANTI_TRANSIT_FAJR,
        )
        val times = Prayer.times(makkah, makkahDate, config)
        assertNotNull(times.fajr.adjusted)
        assertEquals(
            HighLatitudeResolution.COMPUTED_WITH_REQUESTED_RULE,
            times.audit.highLatitudeResolution,
            "no substitution needed when angle is reachable",
        )
    }

    @Test
    fun antiTransitDuringLondonWinterIsNormalComputation() {
        // London in winter: the angle IS reachable (no persistent twilight)
        val winterDate = LocalDate.parse("2026-12-21")
        val config = PrayerConfiguration(
            convention = Convention.MUSLIM_WORLD_LEAGUE,
            highLatitudeRule = HighLatitudeRule.ANTI_TRANSIT_FAJR,
        )
        val times = Prayer.times(london, winterDate, config)
        assertNotNull(times.fajr.adjusted)
        assertEquals(
            HighLatitudeResolution.COMPUTED_WITH_REQUESTED_RULE,
            times.audit.highLatitudeResolution,
            "no anti-transit substitution during winter (angle reachable)",
        )
    }

    @Test
    fun auditCarriesHighLatitudeRule() {
        val config = PrayerConfiguration(
            convention = Convention.MUSLIM_WORLD_LEAGUE,
            highLatitudeRule = HighLatitudeRule.ANTI_TRANSIT_FAJR,
        )
        val times = Prayer.times(london, date, config)
        assertEquals(HighLatitudeRule.ANTI_TRANSIT_FAJR, times.audit.highLatitudeRule)
    }
}

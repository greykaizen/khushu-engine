package com.khushu.engine.zakat

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Livestock band expectations per IslamQA #71267 (classical an'aam schedule).
 * Each case cites the source inline so future audits can re-verify.
 */
class ZakatRulesTest {

    @Test
    fun hawlAnniversaryIsOneHijriYearLater() {
        // Owned 2025-03-01 = 1 Ramadan 1446 → anniversary = 1 Ramadan 1447.
        val period = ZakatRules.hawlPeriod(LocalDate.of(2025, 3, 1))
        val h = com.khushu.engine.calendar.HijriCalendar.hijri(period.anniversary)
        assertEquals(1447, h.year)
        assertEquals(9, h.month)
        assertEquals(1, h.day)
        assertEquals(0, period.snappedDays)
    }

    @Test
    fun hawlFactsTrackProgressToCompletion() {
        val start = LocalDate.of(2026, 1, 1)
        val atStart = ZakatRules.hawlFacts(start, start)
        assertEquals(0L, atStart.daysSinceStart)
        assertEquals(0.0, atStart.progress)
        assertEquals(false, atStart.complete)
        assertTrue(atStart.daysRemaining in 350..360, "a lunar year is ~354-355 days")

        val atEnd = ZakatRules.hawlFacts(start, atStart.period.anniversary)
        assertEquals(1.0, atEnd.progress)
        assertEquals(0L, atEnd.daysRemaining)
        assertEquals(true, atEnd.complete)

        val mid = ZakatRules.hawlFacts(start, start.plusDays(177))
        assertTrue(mid.progress > 0.4 && mid.progress < 0.6)
        // elapsed + remaining partitions the whole ownership year
        assertEquals(atStart.daysRemaining, mid.daysSinceStart + mid.daysRemaining)

        val after = ZakatRules.hawlFacts(start, atStart.period.anniversary.plusDays(10))
        assertEquals(1.0, after.progress)
        assertEquals(0L, after.daysRemaining)
    }

    @Test
    fun hawlFactsRejectDatesBeforeOwnership() {
        val start = LocalDate.of(2026, 1, 1)
        assertFailsWith<com.khushu.engine.core.error.InvalidParameterException> {
            ZakatRules.hawlFacts(start, start.minusDays(1))
        }
    }

    @Test
    fun livestockNisabsMatchClassicalSchedule() {
        assertEquals(5, ZakatRules.livestockNisab(ZakatRules.Species.CAMELS))
        assertEquals(30, ZakatRules.livestockNisab(ZakatRules.Species.CATTLE))
        assertEquals(40, ZakatRules.livestockNisab(ZakatRules.Species.SHEEP_OR_GOATS))
    }

    @Test
    fun camelFlatBandsMatchClassicalTable() {
        // Below nisab.
        assertNull(ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 4))
        // 5–24: one sheep for each five.
        val at5 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 5)!!
        assertEquals(1, at5.headcountDue)
        assertEquals(ZakatRules.AnimalClass.SHEEP_OR_GOAT, at5.animalClass)
        val at20 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 20)!!
        assertEquals(4, at20.headcountDue)
        // 25–35: bint makhad. (IslamQA #71267 table)
        val at25 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 25)!!
        assertEquals(1, at25.headcountDue)
        assertEquals(ZakatRules.AnimalClass.BINT_MAKHAD, at25.animalClass)
        // 36–45: bint labun.
        val at40 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 40)!!
        assertEquals(ZakatRules.AnimalClass.BINT_LABUN, at40.animalClass)
        // 46–60: hiqqah.
        val at50 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 50)!!
        assertEquals(ZakatRules.AnimalClass.HIQQAH, at50.animalClass)
        // 61–75: jadha'ah.
        val at70 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 70)!!
        assertEquals(ZakatRules.AnimalClass.JADHAAH, at70.animalClass)
        // 76–90: two bint labun.
        val at80 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 80)!!
        assertEquals(2, at80.headcountDue)
        assertEquals(ZakatRules.AnimalClass.BINT_LABUN, at80.animalClass)
        // 91–120: two hiqqah.
        val at100 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 100)!!
        assertEquals(2, at100.headcountDue)
        assertEquals(ZakatRules.AnimalClass.HIQQAH, at100.animalClass)
    }

    @Test
    fun camelsBeyond120UseFortyFiftyDecomposition() {
        // IslamQA #71267: beyond 120 — every forty a bint labun, every fifty a hiqqah.
        // 130: excess 10 → no clean 40/50 decomposition of 10 → scholar review flagged,
        // with base two hiqqah still reported.
        val d130 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 130)!!
        assertTrue(d130.requiresScholarReview)
        assertTrue(d130.headcountDue >= 2)

        // 140: excess 20 → also unresolvable cleanly (20 not divisible by 40/50).
        // 160: excess 40 → one bint labun + base two hiqqah = 3 head.
        val d160 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 160)!!
        assertEquals(3, d160.headcountDue)
        assertTrue(!d160.requiresScholarReview || d160.reviewReason != null)

        // 170: excess 50 → one hiqqah + base = 3 head, clean decomposition.
        val d170 = ZakatRules.livestockDue(ZakatRules.Species.CAMELS, 170)!!
        assertEquals(3, d170.headcountDue)
        assertTrue(!d170.requiresScholarReview, d170.reviewReason ?: "")
    }

    @Test
    fun cattleAndSheepAreCountBased() {
        val c30 = ZakatRules.livestockDue(ZakatRules.Species.CATTLE, 30)!!
        assertEquals(1, c30.headcountDue)
        assertEquals(ZakatRules.AnimalClass.TABI_OR_TAQIAH, c30.animalClass)
        val c60 = ZakatRules.livestockDue(ZakatRules.Species.CATTLE, 60)!!
        assertEquals(2, c60.headcountDue)
        assertNull(ZakatRules.livestockDue(ZakatRules.Species.CATTLE, 29))

        assertNull(ZakatRules.livestockDue(ZakatRules.Species.SHEEP_OR_GOATS, 39))
        val s80 = ZakatRules.livestockDue(ZakatRules.Species.SHEEP_OR_GOATS, 80)!!
        assertEquals(2, s80.headcountDue)
        assertEquals(ZakatRules.AnimalClass.SHEEP_OR_GOAT, s80.animalClass)
    }

    @Test
    fun ushrRatesAreTenAndFivePercent() {
        assertEquals(0.10, ZakatRules.ushrRate(ZakatRules.Irrigation.NATURAL))
        assertEquals(0.05, ZakatRules.ushrRate(ZakatRules.Irrigation.ARTIFICIAL))
        assertEquals(1000.0, ZakatRules.ushrDue(10_000.0, ZakatRules.Irrigation.NATURAL), 0.001)
        assertEquals(500.0, ZakatRules.ushrDue(10_000.0, ZakatRules.Irrigation.ARTIFICIAL), 0.001)
        assertEquals(10.0, ZakatRules.ushrDue(100.0, ZakatRules.Irrigation.NATURAL), 0.001)
    }

    // ── v1.11: mixed irrigation (al-Ikhtiyar predominance vs modern proportional) ──

    @Test
    fun predominantRuleFollowsTheDominantMethod() {
        // Mostly natural → 10%; mostly artificial → 5% (al-Ikhtiyar).
        assertEquals(0.10, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PREDOMINANT, 0.4))
        assertEquals(0.10, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PREDOMINANT, 0.49))
        assertEquals(0.05, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PREDOMINANT, 0.6))
        assertEquals(0.05, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PREDOMINANT, 0.51))
    }

    @Test
    fun predominantRuleRefusesAnExactlyEqualSplit() {
        // No predominant method at 0.5 — honest refusal, not a guessed 7.5%.
        assertFailsWith<IllegalArgumentException> {
            ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PREDOMINANT, 0.5)
        }
    }

    @Test
    fun proportionalRuleInterpolatesAndHitsSevenPointFiveAtHalf() {
        assertEquals(0.075, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 0.5), 1e-9)
        assertEquals(0.10, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 0.0 + 1e-9), 1e-9)
        assertEquals(0.05, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 1.0 - 1e-9), 1e-9)
        assertEquals(0.08, ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 0.4), 1e-9)
        assertEquals(750.0, ZakatRules.ushrDue(10_000.0, ZakatRules.MixedIrrigationRule.PROPORTIONAL, 0.5), 0.001)
    }

    @Test
    fun mixedIrrigationBoundsAreValidated() {
        assertFailsWith<IllegalArgumentException> { ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 0.0) }
        assertFailsWith<IllegalArgumentException> { ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 1.0) }
        assertFailsWith<IllegalArgumentException> { ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, -0.1) }
        assertFailsWith<IllegalArgumentException> { ZakatRules.ushrRate(ZakatRules.MixedIrrigationRule.PROPORTIONAL, 1.1) }
    }

    // ── v1.11: ushr nisab policies ─────────────────────────────────────────

    @Test
    fun fiveWasaqPolicyGatesAtDocumentedConversion() {
        assertTrue(ZakatRules.ushrNisabReached(653.0, ZakatRules.UshrNisabPolicy.FIVE_WASAQ))
        assertTrue(ZakatRules.ushrNisabReached(1_000.0, ZakatRules.UshrNisabPolicy.FIVE_WASAQ))
        assertTrue(!ZakatRules.ushrNisabReached(652.9, ZakatRules.UshrNisabPolicy.FIVE_WASAQ))
        assertEquals(653.0, ZakatRules.FIVE_WASAQ_KG)
    }

    @Test
    fun noNisabPolicyDueOnAnyHarvest() {
        assertTrue(ZakatRules.ushrNisabReached(0.5, ZakatRules.UshrNisabPolicy.NO_NISAB))
        assertTrue(ZakatRules.ushrNisabReached(1.0, ZakatRules.UshrNisabPolicy.NO_NISAB))
    }

    @Test
    fun rikazIsFlatTwentyPercent() {
        assertEquals(200.0, ZakatRules.rikazDue(1_000.0), 0.001)
        assertEquals(0.20, ZakatRules.RIKAZ_RATE)
    }
}

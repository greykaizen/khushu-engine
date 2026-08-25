package com.khushu.engine.zakat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Authoritative fiqh spot cases + structural properties. No donor goldens exist
 * (donor logic lives inside Composables); these cases are hand-curated against
 * published fiqh rules (nisab weights, 1/40 rate, madhab treatment).
 */
class ZakatTest {

    private val goldPrice = 75.0  // per gram
    private val silverPrice = 0.9 // per gram

    @Test
    fun rateIsExactlyOneFortiethWhenNisabAndHawlMet() {
        val result = Zakat.mal(
            ZakatAssets(cash = 10_000.0, silverPricePerGram = silverPrice),
            ZakatParams(nisabSource = NisabSource.SILVER, hawlComplete = true),
        )
        assertEquals(595.0 * silverPrice, result.nisabThreshold, 0.01)
        assertTrue(result.nisabReached)
        assertEquals(250.0, result.zakatDue, 0.01) // exactly 2.5%
        assertEquals(0.025, result.rate)
    }

    @Test
    fun belowNisabYieldsZero() {
        val result = Zakat.mal(
            ZakatAssets(cash = 100.0, silverPricePerGram = silverPrice), // far below 595g×0.9 = 535.5? 100 < 535.5 ✓
            ZakatParams(nisabSource = NisabSource.SILVER),
        )
        assertEquals(false, result.nisabReached)
        assertEquals(0.0, result.zakatDue)
    }

    @Test
    fun nisabBoundaryIsInclusive() {
        val thresholdCash = 595.0 * silverPrice // exactly the nisab amount in cash
        val at = Zakat.mal(ZakatAssets(cash = thresholdCash, silverPricePerGram = silverPrice), ZakatParams())
        assertTrue(at.nisabReached, "wealth exactly at nisab must be zakatable")
        val justBelow = Zakat.mal(ZakatAssets(cash = thresholdCash - 0.01, silverPricePerGram = silverPrice), ZakatParams())
        assertTrue(!justBelow.nisabReached)
    }

    @Test
    fun incompleteHawlYieldsZeroEvenAboveNisab() {
        val result = Zakat.mal(
            ZakatAssets(cash = 50_000.0, silverPricePerGram = silverPrice),
            ZakatParams(hawlComplete = false),
        )
        assertTrue(result.nisabReached)
        assertEquals(0.0, result.zakatDue)
    }

    @Test
    fun wornJewelryZakatableOnlyPerHanafi() {
        val assets = ZakatAssets(cash = 10_000.0, wornGoldGrams = 100.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice)

        val hanafi = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.HANAFI))
        assertEquals(10_000.0 + 7_500.0, hanafi.netWealth, 0.01)

        for (m in listOf(ZakatMadhab.SHAFII, ZakatMadhab.MALIKI, ZakatMadhab.HANBALI)) {
            val r = Zakat.mal(assets, ZakatParams(madhab = m))
            assertEquals(10_000.0, r.netWealth, 0.01, "$m must exempt worn jewelry")
        }
    }

    @Test
    fun liabilitiesDeductedEverywhereExceptShafii() {
        val assets = ZakatAssets(cash = 10_000.0, liabilities = 2_000.0, silverPricePerGram = silverPrice)
        for (m in listOf(ZakatMadhab.HANAFI, ZakatMadhab.MALIKI, ZakatMadhab.HANBALI)) {
            val r = Zakat.mal(assets, ZakatParams(madhab = m))
            assertEquals(8_000.0, r.netWealth, 0.01, "$m must deduct debts")
        }
        val shafii = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.SHAFII))
        assertEquals(10_000.0, shafii.netWealth, 0.01)
    }

    @Test
    fun classicalWeightsExceedCommonWeights() {
        val assets = ZakatAssets(silverGrams = 600.0, silverPricePerGram = silverPrice, cash = 0.0)
        val common = Zakat.mal(assets, ZakatParams(nisabSource = NisabSource.SILVER, weightConvention = NisabWeightConvention.COMMON))
        val classical = Zakat.mal(assets, ZakatParams(nisabSource = NisabSource.SILVER, weightConvention = NisabWeightConvention.CLASSICAL))
        assertEquals(595.0 * silverPrice, common.nisabThreshold, 0.01)
        assertEquals(612.36 * silverPrice, classical.nisabThreshold, 0.01)
        // 600g of silver: above common nisab, below classical.
        assertTrue(common.nisabReached && !classical.nisabReached)
    }

    @Test
    fun goldNisabUsesGoldPrice() {
        val assets = ZakatAssets(goldGrams = 90.0, goldPricePerGram = goldPrice)
        val r = Zakat.mal(assets, ZakatParams(nisabSource = NisabSource.GOLD, weightConvention = NisabWeightConvention.COMMON))
        assertEquals(85.0 * goldPrice, r.nisabThreshold, 0.01)
        assertTrue(r.nisabReached) // 6750 >= 6375
    }

    @Test
    fun breakdownSumsToNetWealth() {
        val assets = ZakatAssets(
            cash = 3_000.0, investments = 4_500.0, receivables = 500.0,
            inventoryValue = 250.0, goldGrams = 20.0, goldPricePerGram = goldPrice,
            liabilities = 750.0, silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.MALIKI))
        val sum = r.breakdown.sumOf { it.amount }
        assertEquals(r.netWealth, sum, 0.02)
    }

    @Test
    fun fitranaMajorityAndHanafiConventions() {
        val majority = Zakat.fitrana(dependents = 4, pricePerKg = 2.0, madhab = ZakatMadhab.SHAFII)
        assertEquals(2.175, majority.saKg)
        assertEquals(4 * 2.175 * 2.0, majority.totalForHousehold, 0.01)

        val hanafi = Zakat.fitrana(dependents = 4, pricePerKg = 2.0, madhab = ZakatMadhab.HANAFI)
        assertEquals(3.0, hanafi.saKg)
        assertEquals(24.0, hanafi.totalForHousehold, 0.01)
    }

    @Test
    fun invalidInputsRejected() {
        assertFailsWith<IllegalArgumentException> { Zakat.mal(ZakatAssets(cash = -1.0)) }
        assertFailsWith<IllegalArgumentException> { Zakat.mal(ZakatAssets(silverPricePerGram = silverPrice), ZakatParams(nisabSource = NisabSource.GOLD)) } // no gold price
        assertFailsWith<IllegalArgumentException> { Zakat.fitrana(dependents = 0, pricePerKg = 1.0) }
    }
}

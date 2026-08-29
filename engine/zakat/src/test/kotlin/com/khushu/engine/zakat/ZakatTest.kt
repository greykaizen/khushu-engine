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
    fun bothMetalThresholdsAreReturnedAlongsideSelected() {
        val assets = ZakatAssets(cash = 10_000.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice)
        val silver = Zakat.mal(assets, ZakatParams(nisabSource = NisabSource.SILVER))
        assertEquals(595.0 * silverPrice, silver.nisabThreshold, 0.01)
        assertEquals(silver.nisabThreshold, silver.silverNisabThreshold)
        assertEquals(85.0 * goldPrice, silver.goldNisabThreshold!!, 0.01)

        val gold = Zakat.mal(assets, ZakatParams(nisabSource = NisabSource.GOLD))
        assertEquals(85.0 * goldPrice, gold.nisabThreshold, 0.01)
        assertEquals(gold.nisabThreshold, gold.goldNisabThreshold)
    }

    @Test
    fun missingMetalPriceIsNullThresholdNotFalseZero() {
        // No gold price provided → gold threshold is null, not a misleading 0.
        val r = Zakat.mal(
            ZakatAssets(cash = 10_000.0, silverPricePerGram = silverPrice),
            ZakatParams(nisabSource = NisabSource.SILVER),
        )
        assertEquals(null, r.goldNisabThreshold)
        assertEquals(595.0 * silverPrice, r.silverNisabThreshold!!, 0.01)
    }

    @Test
    fun madhabRuleNotesExplainExemptions() {
        val shafii = Zakat.mal(
            ZakatAssets(cash = 10_000.0, wornGoldGrams = 100.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice),
            ZakatParams(madhab = ZakatMadhab.SHAFII),
        )
        assertTrue(shafii.notes.any { it.contains("worn jewelry", ignoreCase = true) })

        val hanafi = Zakat.mal(
            ZakatAssets(cash = 10_000.0, wornGoldGrams = 100.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice),
            ZakatParams(madhab = ZakatMadhab.HANAFI),
        )
        assertTrue(hanafi.notes.none { it.contains("worn jewelry", ignoreCase = true) })

        val shafiiDebt = Zakat.mal(
            ZakatAssets(cash = 10_000.0, liabilities = 1_000.0, silverPricePerGram = silverPrice),
            ZakatParams(madhab = ZakatMadhab.SHAFII),
        )
        assertTrue(shafiiDebt.notes.any { it.contains("liabilities", ignoreCase = true) })
    }

    @Test
    fun incompleteHawlProducesANote() {
        val r = Zakat.mal(
            ZakatAssets(cash = 50_000.0, silverPricePerGram = silverPrice),
            ZakatParams(hawlComplete = false),
        )
        assertTrue(r.nisabReached)
        assertEquals(0.0, r.zakatDue)
        assertTrue(r.notes.any { it.contains("hawl", ignoreCase = true) })
    }

    @Test
    fun breakdownCarriesStableAssetKeys() {
        val r = Zakat.mal(
            ZakatAssets(cash = 3_000.0, goldGrams = 20.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice),
            ZakatParams(),
        )
        assertTrue(AssetKey.CASH in r.breakdown.map { it.key })
        assertTrue(AssetKey.GOLD in r.breakdown.map { it.key })
        // Every contribution's label is a non-empty display string.
        assertTrue(r.breakdown.all { it.label.isNotBlank() })
    }

    @Test
    fun fitranaExposesTotalKilograms() {
        val majority = Zakat.fitrana(dependents = 4, pricePerKg = 2.0, madhab = ZakatMadhab.SHAFII)
        assertEquals(4 * 2.175, majority.totalKg, 0.001)
        val hanafi = Zakat.fitrana(dependents = 3, pricePerKg = 2.0, madhab = ZakatMadhab.HANAFI)
        assertEquals(9.0, hanafi.totalKg, 0.001)
    }

    @Test
    fun invalidInputsRejected() {
        assertFailsWith<IllegalArgumentException> { Zakat.mal(ZakatAssets(cash = -1.0)) }
        assertFailsWith<IllegalArgumentException> { Zakat.mal(ZakatAssets(silverPricePerGram = silverPrice), ZakatParams(nisabSource = NisabSource.GOLD)) } // no gold price
        assertFailsWith<IllegalArgumentException> { Zakat.fitrana(dependents = 0, pricePerKg = 1.0) }
    }

    // ── v1.11: debt policy matrix ──────────────────────────────────────────

    @Test
    fun hanafiDefaultDeductsImmediateOnlyWhenSplitProvided() {
        val assets = ZakatAssets(
            cash = 50_000.0, liabilities = 20_000.0, immediateLiabilities = 5_000.0,
            silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.HANAFI))
        // 50_000 − 5_000 immediate portion only (Daruliftaa #8395).
        assertEquals(45_000.0, r.netWealth, 0.01)
    }

    @Test
    fun hanafiNullSplitKeepsV110FullDeduction() {
        // immediateLiabilities == null → the whole liability is immediate
        // → v1.10 numeric behavior preserved exactly.
        val legacy = ZakatAssets(cash = 50_000.0, liabilities = 20_000.0, silverPricePerGram = silverPrice)
        val r = Zakat.mal(legacy, ZakatParams(madhab = ZakatMadhab.HANAFI))
        assertEquals(30_000.0, r.netWealth, 0.01)
    }

    @Test
    fun allDebtsOverrideDeductsEverything() {
        val assets = ZakatAssets(
            cash = 50_000.0, liabilities = 20_000.0, immediateLiabilities = 5_000.0,
            silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.HANAFI, debtTreatment = DebtTreatment.ALL_DEBTS))
        assertEquals(30_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("debt policy override") })
    }

    @Test
    fun shafiiNeverDeductsEvenWithSplit() {
        val assets = ZakatAssets(
            cash = 50_000.0, liabilities = 20_000.0, immediateLiabilities = 5_000.0,
            silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.SHAFII))
        assertEquals(50_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("Shafi") })
    }

    @Test
    fun malikiDeductsAsHiddenWealthWithScopeNote() {
        val assets = ZakatAssets(cash = 50_000.0, liabilities = 20_000.0, silverPricePerGram = silverPrice)
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.MALIKI))
        assertEquals(30_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("hidden wealth") })
    }

    @Test
    fun immediateOnlyWithZeroImmediateDeductsNothing() {
        val assets = ZakatAssets(
            cash = 50_000.0, liabilities = 20_000.0, immediateLiabilities = 0.0,
            silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.HANAFI))
        assertEquals(50_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("immediate-due portion is zero") })
    }

    // ── v1.11: receivable tiers ─────────────────────────────────────────────

    @Test
    fun mediumAndWeakReceivablesAreExcludedWithNotes() {
        val r = Zakat.mal(
            ZakatAssets(
                cash = 10_000.0, receivables = 1_000.0,
                mediumReceivables = 800.0, weakReceivables = 500.0,
                silverPricePerGram = silverPrice,
            ),
        )
        // Only the strong receivable is inside; medium/weak excluded.
        assertEquals(11_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("medium receivables excluded") })
        assertTrue(r.notes.any { it.contains("weak receivables excluded") })
    }

    // ── v1.11: share holdings ───────────────────────────────────────────────

    @Test
    fun tradingSharesAssessedAtFullMarketValue() {
        val assets = ZakatAssets(
            cash = 10_000.0, silverPricePerGram = silverPrice,
            shareHoldings = listOf(ShareHolding(shares = 100, marketValue = 30_000.0, intent = ShareIntent.TRADING)),
        )
        val r = Zakat.mal(assets)
        assertEquals(40_000.0, r.netWealth, 0.01)
        assertTrue(r.breakdown.any { it.key == AssetKey.EQUITY_TRADING && it.amount == 30_000.0 })
    }

    @Test
    fun longTermSharesAssessedOnZakatableFraction() {
        val assets = ZakatAssets(
            cash = 10_000.0, silverPricePerGram = silverPrice,
            shareHoldings = listOf(
                ShareHolding(
                    shares = 100, marketValue = 30_000.0, intent = ShareIntent.LONG_TERM,
                    zakatablePortionFraction = 0.30, basisSource = ZakatableBasisSource.EXACT,
                ),
            ),
        )
        val r = Zakat.mal(assets)
        assertEquals(10_000.0 + 30_000.0 * 0.30, r.netWealth, 0.01)
    }

    @Test
    fun longTermSharesWithoutFractionFallBackConservatively() {
        val assets = ZakatAssets(
            cash = 10_000.0, silverPricePerGram = silverPrice,
            shareHoldings = listOf(
                ShareHolding(shares = 100, marketValue = 30_000.0, intent = ShareIntent.LONG_TERM),
            ),
        )
        val r = Zakat.mal(assets)
        assertEquals(40_000.0, r.netWealth, 0.01)
        assertTrue(r.notes.any { it.contains("conservative full-value fallback") })
    }

    @Test
    fun estimatedFractionIsCitedInNotes() {
        val assets = ZakatAssets(
            cash = 10_000.0, silverPricePerGram = silverPrice,
            shareHoldings = listOf(
                ShareHolding(
                    shares = 100, marketValue = 30_000.0, intent = ShareIntent.LONG_TERM,
                    zakatablePortionFraction = 0.25, basisSource = ZakatableBasisSource.ESTIMATED,
                ),
            ),
        )
        val r = Zakat.mal(assets)
        assertTrue(r.notes.any { it.contains("estimated zakatable fraction") })
    }

    // ── v1.11: jewelry realizable-value basis ───────────────────────────────

    @Test
    fun wornJewelryUsesBuybackQuoteWhenProvided() {
        val assets = ZakatAssets(
            cash = 10_000.0, wornGoldGrams = 100.0,
            goldPricePerGram = goldPrice, wornGoldPricePerGram = goldPrice * 0.85,
            silverPricePerGram = silverPrice,
        )
        val r = Zakat.mal(assets, ZakatParams(madhab = ZakatMadhab.HANAFI))
        // Realizable value (buyback) — NOT the market price, NOT the nisab threshold basis.
        assertEquals(10_000.0 + 100.0 * goldPrice * 0.85, r.netWealth, 0.01)
        assertEquals(85.0 * goldPrice, r.goldNisabThreshold!!, 0.01)
    }

    @Test
    fun realizableBasisWithoutQuoteWarns() {
        val r = Zakat.mal(
            ZakatAssets(cash = 10_000.0, wornGoldGrams = 100.0, goldPricePerGram = goldPrice, silverPricePerGram = silverPrice),
            ZakatParams(madhab = ZakatMadhab.HANAFI, jewelryValuationBasis = JewelryValuationBasis.REALIZABLE_VALUE),
        )
        assertTrue(r.notes.any { it.contains("no buyback quote provided") })
        // Falls back to market price but says so.
        assertEquals(10_000.0 + 100.0 * goldPrice, r.netWealth, 0.01)
    }

    // ── v1.11: fitr mode ────────────────────────────────────────────────────

    @Test
    fun fitrModesProduceSameNumbersButDistinctNotes() {
        val food = Zakat.fitrana(4, 2.0, ZakatMadhab.HANAFI, FitrPaymentMode.FOOD)
        val cash = Zakat.fitrana(4, 2.0, ZakatMadhab.HANAFI, FitrPaymentMode.CASH_EQUIVALENT)
        assertEquals(food.totalForHousehold, cash.totalForHousehold, 0.001)
        assertEquals(food.totalKg, cash.totalKg, 0.001)
        assertTrue(food.notes.any { it.contains("majority position") })
        assertTrue(cash.notes.any { it.contains("cash equivalent") && it.contains("Hanafi") })
    }

    @Test
    fun invalidShareInputsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ShareHolding(shares = -1, marketValue = 100.0, intent = ShareIntent.TRADING)
        }
        assertFailsWith<IllegalArgumentException> {
            ShareHolding(shares = 1, marketValue = -5.0, intent = ShareIntent.TRADING)
        }
        assertFailsWith<IllegalArgumentException> {
            ShareHolding(shares = 1, marketValue = 100.0, intent = ShareIntent.LONG_TERM, zakatablePortionFraction = 1.5)
        }
    }

    @Test
    fun immediateLiabilitiesValidation() {
        assertFailsWith<IllegalArgumentException> {
            ZakatAssets(cash = 1_000.0, liabilities = 100.0, immediateLiabilities = 200.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ZakatAssets(cash = 1_000.0, liabilities = 100.0, immediateLiabilities = -1.0)
        }
    }
}

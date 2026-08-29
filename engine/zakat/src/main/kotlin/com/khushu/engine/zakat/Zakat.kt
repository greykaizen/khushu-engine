package com.khushu.engine.zakat

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import kotlin.math.roundToLong

/**
 * Zakat al-mal and fitrana computation — pure extraction of the donor's
 * calculator logic (ZakatAlMalCalculator / FitranaCalculator), stripped of UI.
 *
 * Fiqh rules:
 * - rate: 2.5% (1/40) on net zakatable wealth
 * - nisab: metal weight × current market price of that metal
 * - worn jewelry: zakatable only in the Hanafi madhab; exempt otherwise;
 *   valued at realizable (buyback) price when a quote is provided (v1.11)
 * - liabilities: deducted per [DebtTreatment] policy; Hanafi default is
 *   immediate-only (Daruliftaa #8395 / Radd al-Muhtar 2/260–261), Shafiʿi
 *   deducts nothing — overrides cite their source in result notes (v1.11)
 * - shares: TRADING = full market value, LONG_TERM = zakatable-asset
 *   portion per AAOIFI Std 35, with conservative fallback + note (v1.11)
 */
object Zakat {

    const val RATE = 0.025

    /** Saʿ convention for fitrana, in kilograms per person. */
    const val SA_KG_MAJORITY = 2.175
    const val SA_KG_HANAFI = 3.0

    fun mal(assets: ZakatAssets, params: ZakatParams = ZakatParams()): ZakatResult {
        val wornJewelryZakatable = params.madhab == ZakatMadhab.HANAFI
        val debtTreatment =
            params.debtTreatment ?: ZakatMadhabDefaults.debtTreatment(params.madhab)
        val jewelryBasis =
            params.jewelryValuationBasis ?: ZakatMadhabDefaults.jewelryValuationBasis(params.madhab)

        val goldValue = assets.goldGrams * assets.goldPricePerGram
        val silverValue = assets.silverGrams * assets.silverPricePerGram

        // Full precision internally; only the payable (and currency-display
        // figures) are rounded — never intermediate sums (v1.3 principle).
        val breakdown = mutableListOf<AssetContribution>()
        val notes = mutableListOf<String>()
        fun add(key: AssetKey, label: String, amount: Double) {
            if (amount != 0.0) breakdown += AssetContribution(key, label, round(amount))
        }

        add(AssetKey.CASH, "cash", assets.cash)
        add(AssetKey.INVESTMENTS, "investments", assets.investments)
        add(AssetKey.RECEIVABLES, "receivables (strong debts)", assets.receivables)
        add(AssetKey.INVENTORY, "inventory", assets.inventoryValue)
        add(AssetKey.GOLD, "gold", goldValue)
        add(AssetKey.SILVER, "silver", silverValue)

        var net = assets.cash + assets.investments + assets.receivables +
            assets.inventoryValue + goldValue + silverValue

        // ── Share holdings (v1.11): intent-driven basis ────────────────────
        var tradingTotal = 0.0
        var longTermTotal = 0.0
        for (h in assets.shareHoldings) {
            when (h.intent) {
                ShareIntent.TRADING -> tradingTotal += h.marketValue
                ShareIntent.LONG_TERM -> {
                    val f = h.zakatablePortionFraction
                    if (f != null) {
                        longTermTotal += h.marketValue * f
                        if (h.basisSource == ZakatableBasisSource.ESTIMATED) {
                            notes += "share holding assessed on estimated zakatable fraction ($f): AAOIFI Std 35 permits estimation"
                        }
                    } else {
                        longTermTotal += h.marketValue
                        notes +=
                            "long-term holding without zakatable fraction: conservative full-value fallback applied (AAOIFI Std 35)"
                    }
                }
            }
        }
        add(AssetKey.EQUITY_TRADING, "shares (trading)", tradingTotal)
        add(AssetKey.EQUITY_LONG_TERM, "shares (long-term, zakatable portion)", longTermTotal)
        net += tradingTotal + longTermTotal

        // ── Worn jewelry: madhab exemption + valuation basis ───────────────
        val wornGoldPrice = assets.wornGoldPricePerGram ?: assets.goldPricePerGram
        val wornSilverPrice = assets.wornSilverPricePerGram ?: assets.silverPricePerGram
        val wornValue = assets.wornGoldGrams * wornGoldPrice +
            assets.wornSilverGrams * wornSilverPrice
        if (wornJewelryZakatable && wornValue > 0.0) {
            add(AssetKey.WORN_JEWELRY, "worn jewelry", wornValue)
            net += wornValue
            if (jewelryBasis == JewelryValuationBasis.REALIZABLE_VALUE &&
                (assets.wornGoldPricePerGram == null && assets.wornSilverPricePerGram == null) &&
                (assets.wornGoldGrams + assets.wornSilverGrams > 0.0)
            ) {
                notes += "jewelry valued at market price: realizable-value basis selected but no buyback quote provided (SeekersGuidance/Radd al-Muhtar)"
            }
        } else if (assets.wornGoldGrams + assets.wornSilverGrams > 0.0) {
            notes += "worn jewelry exempt: zakatable only in the Hanafi madhab"
        }

        // ── Receivables: medium/weak excluded with classification notes ────
        if (assets.mediumReceivables > 0.0) {
            notes += "medium receivables excluded (non-trade property proceeds): Hanafi classification — inclusion timing requires scholar review"
        }
        if (assets.weakReceivables > 0.0) {
            notes += "weak receivables excluded (mahr/inheritance claims): typically not zakatable until collected"
        }

        // ── Liabilities: policy-driven deduction ───────────────────────────
        val totalLiabilities = assets.liabilities
        val deduction = when (debtTreatment) {
            DebtTreatment.NONE -> 0.0
            DebtTreatment.IMMEDIATE_ONLY ->
                assets.immediateLiabilities ?: totalLiabilities
            DebtTreatment.ALL_DEBTS, DebtTreatment.HIDDEN_WEALTH_ONLY -> totalLiabilities
        }
        if (debtTreatment == DebtTreatment.HIDDEN_WEALTH_ONLY) {
            notes += "Maliki debt scope: all wealth assessed in mal() is hidden wealth; livestock/crops (visible) are assessed separately via ZakatRules and unaffected by debt"
        }
        if (deduction > 0.0) {
            add(AssetKey.LIABILITIES, "liabilities (deducted)", -deduction)
            net -= deduction
        } else if (totalLiabilities > 0.0 && debtTreatment == DebtTreatment.NONE) {
            notes += "liabilities not deducted: Shafiʿi position (al-Nawawi)"
        } else if (totalLiabilities > 0.0) {
            notes += "liabilities not deducted: immediate-due portion is zero"
        }
        if (params.debtTreatment != null && params.debtTreatment != ZakatMadhabDefaults.debtTreatment(params.madhab)) {
            notes += "debt policy override: ${params.debtTreatment} (default for ${params.madhab} is ${ZakatMadhabDefaults.debtTreatment(params.madhab)})"
        }

        val convention = params.weightConvention
        val goldThreshold =
            if (assets.goldPricePerGram > 0.0) round(convention.goldGrams * assets.goldPricePerGram) else null
        val silverThreshold =
            if (assets.silverPricePerGram > 0.0) round(convention.silverGrams * assets.silverPricePerGram) else null
        val threshold = when (params.nisabSource) {
            NisabSource.GOLD -> goldThreshold
            NisabSource.SILVER -> silverThreshold
        } ?: throw InvalidParameterException(
            when (params.nisabSource) {
                NisabSource.GOLD -> "goldPricePerGram"
                else -> "silverPricePerGram"
            },
            "0.0",
            "nisab source is ${params.nisabSource} but its market price was not provided",
        )
        // Single rounding of the computed total (currency presentation); the
        // comparison runs on rounded figures so the nisab boundary is stable.
        val netWealth = round(net)
        val reached = netWealth >= threshold && netWealth > 0.0

        val due = if (reached && params.hawlComplete) round(netWealth * RATE) else 0.0
        if (reached && !params.hawlComplete) {
            notes += "no zakat due: hawl (lunar year of ownership) incomplete"
        }

        return ZakatResult(
            netWealth = netWealth,
            nisabThreshold = threshold,
            nisabReached = reached,
            hawlComplete = params.hawlComplete,
            rate = RATE,
            zakatDue = due,
            breakdown = breakdown.toList(),
            goldNisabThreshold = goldThreshold,
            silverNisabThreshold = silverThreshold,
            notes = notes.toList(),
        )
    }

    /**
     * Fitrana (zakat al-fitr). Majority convention: 2.175 kg saʿ per person;
     * Hanafi convention: 3.0 kg.
     *
     * The default [madhab] is deliberately [ZakatMadhab.HANAFI] — the 3.0 kg
     * figure is the most widely published fitrana quantity — even though
     * prayer defaults elsewhere are Shafi'i; pass the madhab explicitly when
     * the two should agree.
     */
    fun fitrana(
        dependents: Int,
        pricePerKg: Double,
        madhab: ZakatMadhab = ZakatMadhab.HANAFI,
        mode: FitrPaymentMode = FitrPaymentMode.FOOD,
    ): FitranaResult {
        validate(dependents > 0) {
            InvalidParameterException("dependents", "$dependents", "must be positive")
        }
        validate(pricePerKg > 0.0) {
            InvalidParameterException("pricePerKg", "$pricePerKg", "staple price must be positive")
        }
        val saKg = if (madhab == ZakatMadhab.HANAFI) SA_KG_HANAFI else SA_KG_MAJORITY
        val perPerson = round(saKg * pricePerKg)
        val notes = mutableListOf<String>()
        when (mode) {
            FitrPaymentMode.FOOD -> notes +=
                "food form: majority position (al-Nawawi, al-Majmuʿ 6/113) requires staple food"
            FitrPaymentMode.CASH_EQUIVALENT -> notes +=
                "cash equivalent: Hanafi position (Abu Hanifa, Abu Yusuf; also reported from al-Hasan al-Basri and ʿUmar ibn ʿAbd al-ʿAziz) — other schools require food except for necessity-based exceptions"
        }
        return FitranaResult(
            saKg = saKg,
            dependents = dependents,
            pricePerKg = pricePerKg,
            perPersonAmount = perPerson,
            totalForHousehold = round(perPerson * dependents),
            totalKg = round3(saKg * dependents),
            mode = mode,
            notes = notes,
        )
    }

    private fun round(v: Double): Double = (v * 100).roundToLong() / 100.0

    private fun round3(v: Double): Double = (v * 1000).roundToLong() / 1000.0
}

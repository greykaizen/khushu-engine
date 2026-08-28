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
 * - worn jewelry: zakatable only in the Hanafi madhab; exempt otherwise
 * - liabilities: deducted from wealth in every madhab except Shafi'i
 */
object Zakat {

    const val RATE = 0.025

    /** Saʿ convention for fitrana, in kilograms per person. */
    const val SA_KG_MAJORITY = 2.175
    const val SA_KG_HANAFI = 3.0

    fun mal(assets: ZakatAssets, params: ZakatParams = ZakatParams()): ZakatResult {
        val wornJewelryZakatable = params.madhab == ZakatMadhab.HANAFI
        val liabilitiesDeductible = params.madhab != ZakatMadhab.SHAFII

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

        val wornValue = assets.wornGoldGrams * assets.goldPricePerGram +
            assets.wornSilverGrams * assets.silverPricePerGram
        if (wornJewelryZakatable && wornValue > 0.0) {
            add(AssetKey.WORN_JEWELRY, "worn jewelry", wornValue)
            net += wornValue
        } else if (assets.wornGoldGrams + assets.wornSilverGrams > 0.0) {
            notes += "worn jewelry exempt: zakatable only in the Hanafi madhab"
        }

        if (liabilitiesDeductible && assets.liabilities > 0.0) {
            add(AssetKey.LIABILITIES, "liabilities (deducted)", -assets.liabilities)
            net -= assets.liabilities
        } else if (assets.liabilities > 0.0) {
            notes += "liabilities not deducted: Shafi'i position"
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
    ): FitranaResult {
        validate(dependents > 0) {
            InvalidParameterException("dependents", "$dependents", "must be positive")
        }
        validate(pricePerKg > 0.0) {
            InvalidParameterException("pricePerKg", "$pricePerKg", "staple price must be positive")
        }
        val saKg = if (madhab == ZakatMadhab.HANAFI) SA_KG_HANAFI else SA_KG_MAJORITY
        val perPerson = round(saKg * pricePerKg)
        return FitranaResult(
            saKg = saKg,
            dependents = dependents,
            pricePerKg = pricePerKg,
            perPersonAmount = perPerson,
            totalForHousehold = round(perPerson * dependents),
            totalKg = round3(saKg * dependents),
        )
    }

    private fun round(v: Double): Double = (v * 100).roundToLong() / 100.0

    private fun round3(v: Double): Double = (v * 1000).roundToLong() / 1000.0
}

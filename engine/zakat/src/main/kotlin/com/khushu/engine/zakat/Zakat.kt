package com.khushu.engine.zakat

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

        val breakdown = mutableListOf<AssetContribution>()
        fun add(label: String, amount: Double) {
            if (amount != 0.0) breakdown += AssetContribution(label, round(amount))
        }

        add("cash", assets.cash)
        add("investments", assets.investments)
        add("receivables (strong debts)", assets.receivables)
        add("inventory", assets.inventoryValue)
        add("gold", goldValue)
        add("silver", silverValue)

        var net = assets.cash + assets.investments + assets.receivables +
            assets.inventoryValue + goldValue + silverValue

        val wornGoldValue = assets.wornGoldGrams * assets.goldPricePerGram
        val wornSilverValue = assets.wornSilverGrams * assets.silverPricePerGram
        if (wornJewelryZakatable && (wornGoldValue + wornSilverValue) > 0.0) {
            val worn = wornGoldValue + wornSilverValue
            add("worn jewelry (zakatable per madhab)", worn)
            net += worn
        } else {
            add("worn jewelry (exempt per madhab)", 0.0)
        }

        if (liabilitiesDeductible && assets.liabilities > 0.0) {
            add("liabilities (deducted)", -round(assets.liabilities))
            net -= assets.liabilities
        } else {
            add("liabilities (not deducted per madhab)", 0.0)
        }
        net = round(net)

        val convention = params.weightConvention
        val nisabWeight = when (params.nisabSource) {
            NisabSource.GOLD -> convention.goldGrams
            NisabSource.SILVER -> convention.silverGrams
        }
        val priceOfNisabMetal = when (params.nisabSource) {
            NisabSource.GOLD -> assets.goldPricePerGram
            NisabSource.SILVER -> assets.silverPricePerGram
        }
        require(priceOfNisabMetal > 0.0) {
            "nisab source is ${params.nisabSource} but its market price was not provided"
        }
        val threshold = round(nisabWeight * priceOfNisabMetal)
        val reached = net >= threshold && net > 0.0

        val due = if (reached && params.hawlComplete) round(net * RATE) else 0.0

        return ZakatResult(
            netWealth = net,
            nisabThreshold = threshold,
            nisabReached = reached,
            hawlComplete = params.hawlComplete,
            rate = RATE,
            zakatDue = due,
            breakdown = breakdown.toList(),
        )
    }

    /**
     * Fitrana (zakat al-fitr). Majority convention: 2.175 kg saʿ per person;
     * Hanafi convention: 3.0 kg.
     */
    fun fitrana(
        dependents: Int,
        pricePerKg: Double,
        madhab: ZakatMadhab = ZakatMadhab.HANAFI,
    ): FitranaResult {
        require(dependents > 0) { "dependents must be positive" }
        require(pricePerKg > 0.0) { "staple price must be positive" }
        val saKg = if (madhab == ZakatMadhab.HANAFI) SA_KG_HANAFI else SA_KG_MAJORITY
        val perPerson = round(saKg * pricePerKg)
        return FitranaResult(
            saKg = saKg,
            dependents = dependents,
            pricePerKg = pricePerKg,
            perPersonAmount = perPerson,
            totalForHousehold = round(perPerson * dependents),
        )
    }

    private fun round(v: Double): Double = (v * 100).roundToLong() / 100.0
}

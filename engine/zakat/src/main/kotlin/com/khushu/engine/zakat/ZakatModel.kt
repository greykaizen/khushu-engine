package com.khushu.engine.zakat

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate

/** Asr-school rules affecting what is zakatable and what is deductible. */
enum class ZakatMadhab {
    /** Worn jewelry is zakatable · debts are deducted. */
    HANAFI,
    /** Worn jewelry is exempt · debts are NOT deducted. */
    SHAFII,
    /** Worn jewelry is exempt · debts are deducted. */
    MALIKI,
    /** Worn jewelry is exempt · debts are deducted. */
    HANBALI,
}

/** Which metal defines the nisab threshold. */
enum class NisabSource { GOLD, SILVER }

/**
 * Weight conventions:
 * - COMMON (widely used today): 85 g gold / 595 g silver
 * - CLASSICAL (historical dinar/dirham measure): 87.48 g / 612.36 g
 */
enum class NisabWeightConvention(val goldGrams: Double, val silverGrams: Double) {
    COMMON(85.0, 595.0),
    CLASSICAL(87.48, 612.36),
}

data class ZakatAssets(
    val cash: Double = 0.0,
    val investments: Double = 0.0,
    /** Receivables you realistically expect to collect ("strong" debts). */
    val receivables: Double = 0.0,
    val inventoryValue: Double = 0.0,
    val goldGrams: Double = 0.0,
    val goldPricePerGram: Double = 0.0,
    val silverGrams: Double = 0.0,
    val silverPricePerGram: Double = 0.0,
    /** Jewelry worn day-to-day; treated per madhab. */
    val wornGoldGrams: Double = 0.0,
    val wornSilverGrams: Double = 0.0,
    /** Liabilities owed to others (deducted per madhab rule). */
    val liabilities: Double = 0.0,
) {
    init {
        mapOf(
            "cash" to cash,
            "investments" to investments,
            "receivables" to receivables,
            "inventoryValue" to inventoryValue,
            "goldGrams" to goldGrams,
            "silverGrams" to silverGrams,
            "wornGoldGrams" to wornGoldGrams,
            "wornSilverGrams" to wornSilverGrams,
            "liabilities" to liabilities,
        ).forEach { (name, v) ->
            validate(v >= 0.0 && v.isFinite()) {
                InvalidParameterException(name, "$v", "must be finite and >= 0")
            }
        }
        mapOf("goldPricePerGram" to goldPricePerGram, "silverPricePerGram" to silverPricePerGram)
            .forEach { (name, v) ->
                validate(v >= 0.0) { InvalidParameterException(name, "$v", "must be >= 0") }
            }
    }
}

data class ZakatParams(
    val madhab: ZakatMadhab = ZakatMadhab.HANAFI,
    val nisabSource: NisabSource = NisabSource.SILVER,
    val weightConvention: NisabWeightConvention = NisabWeightConvention.COMMON,
    /** The lunar year of ownership must be complete for zakat to fall due. */
    val hawlComplete: Boolean = true,
)

/** Stable i18n key for a breakdown entry; [AssetContribution.label] carries the English display default. */
enum class AssetKey {
    CASH, INVESTMENTS, RECEIVABLES, INVENTORY, GOLD, SILVER, WORN_JEWELRY, LIABILITIES,
}

data class AssetContribution(
    /** Stable key for localization — hosts map this to their own strings. */
    val key: AssetKey,
    /** English display label (host may override via [key]). */
    val label: String,
    val amount: Double,
)

data class ZakatResult(
    val netWealth: Double,
    /** Threshold under [ZakatParams.nisabSource]; the gate applied to [netWealth]. */
    val nisabThreshold: Double,
    val nisabReached: Boolean,
    val hawlComplete: Boolean,
    /** Always 0.025 — exposed so callers never hardcode the rate. */
    val rate: Double,
    val zakatDue: Double,
    val breakdown: List<AssetContribution>,
    /**
     * Gold nisab threshold under the chosen weight convention; null when no
     * gold price was provided. Both metal thresholds always shown so hosts can
     * render "above silver, below gold" without a second call.
     */
    val goldNisabThreshold: Double? = null,
    /** Silver nisab threshold under the chosen weight convention; null when no silver price was provided. */
    val silverNisabThreshold: Double? = null,
    /**
     * Madhab-rule facts applied to THIS assessment (e.g. "worn jewelry
     * exempt: zakatable only in the Hanafi madhab") — the "why" behind the
     * number. Empty when no rule altered the computation.
     */
    val notes: List<String> = emptyList(),
)

data class FitranaResult(
    /** Kilograms of staple food per person under the chosen convention. */
    val saKg: Double,
    val dependents: Int,
    val pricePerKg: Double,
    val perPersonAmount: Double,
    val totalForHousehold: Double,
    /** Total food quantity for the household (saKg × dependents). */
    val totalKg: Double,
)

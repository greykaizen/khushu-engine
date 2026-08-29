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
 * Debt-deduction policy (docs/v1.11-zakat-design.md D1–D3).
 *
 * Sources: Hanafi preferred position (immediate-only) per Daruliftaa #8395
 * citing Radd al-Muhtar 2/260–261; alternate Hanafi full-deduction position
 * per IslamicPortal #117950; Maliki hidden-wealth scope per Ibn Abi Zayd,
 * al-Risala; Shafiʿi no-deduction per al-Nawawi (Rawdat al-Talibin).
 */
enum class DebtTreatment {
    /** Only immediately-due liabilities are deducted (preferred Hanafi; Hanbali default). */
    IMMEDIATE_ONLY,
    /** All liabilities deducted (alternate Hanafi position). */
    ALL_DEBTS,
    /** Deducted from hidden wealth only (Maliki); in [Zakat.mal] all assessed wealth is hidden wealth. */
    HIDDEN_WEALTH_ONLY,
    /** No deduction (Shafiʿi relied-upon position). */
    NONE,
}

/** Hanafi tripartite receivable classification (Radd al-Muhtar; al-Mabsut). */
enum class ReceivableStrength {
    /** Loans and trade-goods proceeds — included in zakatable wealth. */
    STRONG,
    /** Non-trade property proceeds — excluded here; scholar review for inclusion timing. */
    MEDIUM,
    /** Mahr / inheritance claims — excluded; typically not zakatable until collected. */
    WEAK,
}

/** Shareholding intent — determines the zakatable basis (AAOIFI-style). */
enum class ShareIntent {
    /** Held for resale within the zakat year: full market value is zakatable. */
    TRADING,
    /** Held long-term: only the underlying zakatable-asset portion is assessed. */
    LONG_TERM,
}

/** How the zakatable portion of a long-term holding was established (AAOIFI Std 35 permits estimation). */
enum class ZakatableBasisSource {
    /** Fraction derived from the company's published zakatable-asset figures. */
    EXACT,
    /** Fraction from a screening provider / fund's published estimate. */
    ESTIMATED,
    /** No reliable fraction: full market value used as a documented conservative fallback. */
    CONSERVATIVE_FULL,
}

/**
 * A shareholding assessed per intent (docs/v1.11-zakat-design.md D7).
 *
 * TRADING holdings are zakatable at full [marketValue]. LONG_TERM holdings
 * are zakatable at [marketValue] × [zakatablePortionFraction] when a
 * fraction is known; without one the engine applies the conservative
 * full-value fallback with a warning note — never a silent guess.
 */
data class ShareHolding(
    val shares: Int,
    val marketValue: Double,
    val intent: ShareIntent,
    /** Zakatable-asset fraction in [0, 1]; null = unknown. */
    val zakatablePortionFraction: Double? = null,
    val basisSource: ZakatableBasisSource = ZakatableBasisSource.CONSERVATIVE_FULL,
) {
    init {
        if (shares < 0) throw InvalidParameterException("shares", "$shares", "must be >= 0")
        if (!marketValue.isFinite() || marketValue < 0.0) {
            throw InvalidParameterException("marketValue", "$marketValue", "must be finite and >= 0")
        }
        val f = zakatablePortionFraction
        if (f != null && (f < 0.0 || f > 1.0 || !f.isFinite())) {
            throw InvalidParameterException("zakatablePortionFraction", "$f", "must be within [0, 1]")
        }
    }
}

/** Basis used to value worn jewelry (docs/v1.11-zakat-design.md D8). */
enum class JewelryValuationBasis {
    /** What the item could actually be sold for (Hanafi realizable value; buyback quote is today's practical input). */
    REALIZABLE_VALUE,
    /** Full market/retail price — conservative option; includes craftsmanship/markup. */
    MARKET_RETAIL,
}

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
    /** Realizable (e.g. jeweler buyback) price per gram for worn GOLD; null = use [goldPricePerGram]. */
    val wornGoldPricePerGram: Double? = null,
    /** Realizable (e.g. jeweler buyback) price per gram for worn SILVER; null = use [silverPricePerGram]. */
    val wornSilverPricePerGram: Double? = null,
    /** Liabilities owed to others (deducted per madhab rule). */
    val liabilities: Double = 0.0,
    /**
     * Portion of [liabilities] that is immediately due (this installment
     * period). Null = the full liability is immediate (v1.10 behavior);
     * an explicit split activates [DebtTreatment.IMMEDIATE_ONLY].
     */
    val immediateLiabilities: Double? = null,
    /** Receivables of medium strength (non-trade property proceeds) — excluded with a note. */
    val mediumReceivables: Double = 0.0,
    /** Weak receivables (mahr, inheritance claims) — excluded with a note. */
    val weakReceivables: Double = 0.0,
    /** Shareholdings assessed per intent; empty = none (use [investments] for the simple catch-all). */
    val shareHoldings: List<ShareHolding> = emptyList(),
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
            "mediumReceivables" to mediumReceivables,
            "weakReceivables" to weakReceivables,
        ).forEach { (name, v) ->
            validate(v >= 0.0 && v.isFinite()) {
                InvalidParameterException(name, "$v", "must be finite and >= 0")
            }
        }
        mapOf("goldPricePerGram" to goldPricePerGram, "silverPricePerGram" to silverPricePerGram)
            .forEach { (name, v) ->
                validate(v >= 0.0) { InvalidParameterException(name, "$v", "must be >= 0") }
            }
        val imm = immediateLiabilities
        if (imm != null && (imm < 0.0 || !imm.isFinite() || imm > liabilities)) {
            throw InvalidParameterException(
                "immediateLiabilities", "$imm", "must be >= 0 and <= liabilities ($liabilities)",
            )
        }
        val wg = wornGoldPricePerGram
        if (wg != null && wg < 0.0) {
            throw InvalidParameterException("wornGoldPricePerGram", "$wg", "must be >= 0")
        }
        val ws = wornSilverPricePerGram
        if (ws != null && ws < 0.0) {
            throw InvalidParameterException("wornSilverPricePerGram", "$ws", "must be >= 0")
        }
    }
}

data class ZakatParams(
    val madhab: ZakatMadhab = ZakatMadhab.HANAFI,
    val nisabSource: NisabSource = NisabSource.SILVER,
    val weightConvention: NisabWeightConvention = NisabWeightConvention.COMMON,
    /** The lunar year of ownership must be complete for zakat to fall due. */
    val hawlComplete: Boolean = true,
    /**
     * Debt-deduction policy; null = madhab default (see [ZakatMadhabDefaults.debtTreatment]).
     * Non-default selections are surfaced in [ZakatResult.notes] with their citation.
     */
    val debtTreatment: DebtTreatment? = null,
    /** Worn-jewelry valuation basis; null = madhab default (REALIZABLE_VALUE under Hanafi, MARKET_RETAIL otherwise). */
    val jewelryValuationBasis: JewelryValuationBasis? = null,
)

/** Stable i18n key for a breakdown entry; [AssetContribution.label] carries the English display default. */
enum class AssetKey {
    CASH, INVESTMENTS, RECEIVABLES, INVENTORY, GOLD, SILVER, WORN_JEWELRY, LIABILITIES,
    EQUITY_TRADING, EQUITY_LONG_TERM, RECEIVABLES_MEDIUM, RECEIVABLES_WEAK,
}

/**
 * Per-madhab policy defaults (docs/v1.11-zakat-design.md D1–D8). Explicit
 * [ZakatParams] overrides replace these and are cited in result notes.
 */
object ZakatMadhabDefaults {
    /**
     * Hanafi: IMMEDIATE_ONLY — preferred position, Daruliftaa #8395 citing
     * Radd al-Muhtar 2/260–261 ("only short-term loans will be deducted…
     * restrict it to only that payment which is immediate"). The alternate
     * full-deduction position (IslamicPortal #117950) stays selectable.
     * Maliki: hidden-wealth scope. Shafiʿi: no deduction. Hanbali: immediate-only
     * (al-Mughni treats debt as affecting the nisab).
     */
    fun debtTreatment(madhab: ZakatMadhab): DebtTreatment = when (madhab) {
        ZakatMadhab.HANAFI -> DebtTreatment.IMMEDIATE_ONLY
        ZakatMadhab.SHAFII -> DebtTreatment.NONE
        ZakatMadhab.MALIKI -> DebtTreatment.HIDDEN_WEALTH_ONLY
        ZakatMadhab.HANBALI -> DebtTreatment.IMMEDIATE_ONLY
    }

    /**
     * Worn-jewelry valuation basis. Hanafi assesses at realizable value
     * (SeekersGuidance/Faraz Rabbani citing Ibn ʿAbidin, Radd al-Muhtar);
     * other madhabs exempt worn jewelry entirely, so the basis only
     * matters for Hanafi — MARKET_RETAIL is the conservative fallback there.
     */
    fun jewelryValuationBasis(madhab: ZakatMadhab): JewelryValuationBasis = when (madhab) {
        ZakatMadhab.HANAFI -> JewelryValuationBasis.REALIZABLE_VALUE
        else -> JewelryValuationBasis.MARKET_RETAIL
    }
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

/**
 * Form in which zakat al-fitr is discharged (docs/v1.11-zakat-design.md D9).
 * Majority (Maliki/Shafiʿi/Hanbali per al-Nawawi, al-Majmuʿ 6/113) require
 * food; the Hanafi school permits the monetary equivalent (also reported
 * from al-Hasan al-Basri and ʿUmar ibn ʿAbd al-ʿAziz). The engine computes
 * identical quantities either way; [mode] drives provenance notes only.
 */
enum class FitrPaymentMode {
    /** Staple food in the prescribed measure (majority relied-upon position). */
    FOOD,
    /** Monetary equivalent of the food value (Hanafi position; necessity-based exceptions in other schools). */
    CASH_EQUIVALENT,
}

data class FitranaResult(
    /** Kilograms of staple food per person under the chosen convention. */
    val saKg: Double,
    val dependents: Int,
    val pricePerKg: Double,
    val perPersonAmount: Double,
    val totalForHousehold: Double,
    /** Total food quantity for the household (saKg × dependents). */
    val totalKg: Double,
    /** Form the caller intends to discharge in — provenance for the notes below. */
    val mode: FitrPaymentMode = FitrPaymentMode.FOOD,
    /** Position citations applied to this assessment (e.g. which school's form/mode was chosen). */
    val notes: List<String> = emptyList(),
)

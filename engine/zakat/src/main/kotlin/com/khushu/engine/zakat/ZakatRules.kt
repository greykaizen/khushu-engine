package com.khushu.engine.zakat

import java.time.LocalDate
import kotlin.math.floor

/**
 * Fiqh rule engines beyond zakat al-mal: hawl anniversaries, livestock nisab
 * tables, agricultural ushr and rikaz. All tabular/pure computation.
 */
object ZakatRules {

    // ── Hawl ─────────────────────────────────────────────────────────────────

    /**
     * The hijri-anniversary date on which the hawl completes: same hijri
     * (month/day) one year later, snapped to an existing date when the target
     * year's month is shorter. Uses [com.khushu.engine.calendar.Calendar] via a
     * forward-conversion probe so no reverse table is needed.
     */
    fun hawlAnniversary(
        ownershipStart: LocalDate,
        offsetDays: Int = 0,
        hijri: (LocalDate, Int) -> Triple<Int, Int, Int>,
    ): LocalDate {
        val (y, m, d) = hijri(ownershipStart, offsetDays)
        val targetYear = y + 1
        // Same hijri day one year later; if that day doesn't exist in the
        // target year's month (29-day month), snap back up to 2 days.
        for (day in d downTo maxOf(1, d - 2)) {
            val candidate = findCivilDate(targetYear, m, day, offsetDays, hijri)
            if (candidate != null && !candidate.isBefore(ownershipStart)) return candidate
        }
        throw IllegalStateException("hawl anniversary not resolvable for $ownershipStart")
    }

    private fun findCivilDate(
        year: Int, month: Int, day: Int, offsetDays: Int,
        hijri: (LocalDate, Int) -> Triple<Int, Int, Int>,
    ): LocalDate? {
        fun rank(date: LocalDate): Long {
            val (hy, hm, hd) = hijri(date, offsetDays)
            return hy.toLong() * 10000 + hm * 100 + hd
        }
        val target = year.toLong() * 10000 + month * 100 + day

        var lo = ownershipEpochEstimate(year, month, day).minusDays(45)
        var hi = ownershipEpochEstimate(year, month, day).plusDays(45)
        while (lo < hi) {
            val mid = lo.plusDays(java.time.temporal.ChronoUnit.DAYS.between(lo, hi) / 2)
            if (rank(mid) < target) lo = mid.plusDays(1) else hi = mid
        }
        return if (rank(lo) == target) lo else null
    }

    private fun ownershipEpochEstimate(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.of(2018, 9, 11)
            .plusDays(Math.round((year - 1440) * 354.36673))
            .plusDays(((month - 1) * 29.530589).toLong())
            .plusDays((day - 1).toLong())

    // ── Livestock ────────────────────────────────────────────────────────────

    enum class LivestockKind { CAMELS, CATTLE, SHEEP_OR_GOATS }

    /**
     * One slot of the species' nisab schedule: count threshold, what is due at
     * or beyond it, and whether the due animal is taken per [count] head.
     */
    data class LivestockSlot(val threshold: Int, val description: String)

    private val CAMEL_SLOTS = listOf( // classical fiqh schedule (Sahih Bukhari, Mu'adh hadith)
        LivestockSlot(5, "one sheep/goat"),
        LivestockSlot(10, "two sheep/goats"),
        LivestockSlot(15, "three sheep/goats"),
        LivestockSlot(20, "four sheep/goats"),
        LivestockSlot(25, "one bint makhad (she-camel aged ~1 year)"),
        LivestockSlot(36, "one bint labun (she-camel aged ~2 years)"),
        LivestockSlot(46, "one hiqqah (she-camel aged ~3 years)"),
        LivestockSlot(61, "one jadha'ah (she-camel aged ~4 years)"),
        LivestockSlot(76, "two bint labun"),
        LivestockSlot(91, "two hiqqah"),
        LivestockSlot(121, "two bint makhad; then one cow per 40 or one sheep per head beyond"),
    )

    private val CATTLE_SLOTS = listOf(
        LivestockSlot(30, "one tabi' (bull/buffalo calf ~1 year) or taqi'ah (~2 years)"),
    )

    private val SHEEP_SLOTS = listOf(
        LivestockSlot(40, "one sheep/goat"),
    )

    fun livestockSlots(kind: LivestockKind): List<LivestockSlot> = when (kind) {
        LivestockKind.CAMELS -> CAMEL_SLOTS
        LivestockKind.CATTLE -> CATTLE_SLOTS
        LivestockKind.SHEEP_OR_GOATS -> SHEEP_SLOTS
    }

    /** What is due for [count] animals of [kind]; null below the first nisab. */
    fun livestockDue(kind: LivestockKind, count: Int): String? {
        require(count >= 0)
        var slot: LivestockSlot? = null
        for (s in livestockSlots(kind)) if (count >= s.threshold) slot = s else break
        return slot?.description?.let { desc ->
            when {
                kind == LivestockKind.CAMELS && count >= 121 ->
                    "per the 121+ schedule: two bint makhad base, plus ${extraCamelDue(count)} for the excess"
                kind == LivestockKind.CATTLE && count >= 30 ->
                    "${floor(count / 30.0).toInt()} tabi'/taqi'ah (1 per 30)"
                kind == LivestockKind.SHEEP_OR_GOATS && count >= 40 ->
                    "${floor(count / 40.0).toInt()} sheep (1 per 40)"
                else -> desc
            }
        }
    }

    private fun extraCamelDue(count: Int): String {
        val excess = count - 121
        val cows = floor(excess / 40.0).toInt()
        val remainderAfterCows = excess - cows * 40
        return "$cows cow(s) plus $remainderAfterCows sheep/goats"
    }

    /** Nisab threshold for the species (first slot). */
    fun livestockNisab(kind: LivestockKind): Int = livestockSlots(kind).first().threshold

    // ── Ushr (agricultural) ──────────────────────────────────────────────────

    /**
     * Ushr rate on produce: 10% for rain/land naturally irrigated, 5% for
     * artificially irrigated land (cost of irrigation borne by farmer).
     */
    enum class Irrigation { NATURAL, ARTIFICIAL }

    fun ushrRate(irrigation: Irrigation): Double = when (irrigation) {
        Irrigation.NATURAL -> 0.10
        Irrigation.ARTIFICIAL -> 0.05
    }

    fun ushrDue(harvestValue: Double, irrigation: Irrigation): Double {
        require(harvestValue >= 0.0)
        // Classical fiqh: no nisab minimum on produce (some schools require ≥ 5 wasq);
        // engine applies the simple majority rule — due on all harvests.
        return harvestValue * ushrRate(irrigation)
    }

    // ── Rikaz ────────────────────────────────────────────────────────────────

    /** Buried treasure (rikaz): flat 20% (khums), no nisab, no hawl. */
    const val RIKAZ_RATE = 0.20
    fun rikazDue(treasureValue: Double): Double {
        require(treasureValue >= 0.0)
        return treasureValue * RIKAZ_RATE
    }
}

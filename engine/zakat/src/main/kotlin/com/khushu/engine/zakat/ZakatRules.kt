package com.khushu.engine.zakat

import java.time.LocalDate
import kotlin.math.floor

/**
 * Livestock zakat rule engine. The classical schedules are DECLARATIVE DATA —
 * auditable, citable, never a clever hidden formula.
 *
 * Sources: band structure per IslamQA #71267 (classical anʿām schedule);
 * beyond-120 rule per the same source: "for every forty a bint labun and for
 * every fifty a hiqqah". Grazing/trade-purpose distinctions are caller policy
 * (see docs/v1.3-zakat-design.md).
 */
object ZakatRules {

    // ── Hawl ─────────────────────────────────────────────────────────────────

    /**
     * The hijri-anniversary date on which the hawl completes: same hijri
     * (month/day) one year later, snapping back up to 2 days when the target
     * month is shorter. Snap is EXPLICIT in the returned [HawlPeriod].
     *
     * Hijri conversion is injected by the CALLER (facade wires it to the
     * calendar capability) — this module stays independent of calendar.
     */
    fun hawlPeriod(
        ownershipStart: LocalDate,
        offsetDays: Int = 0,
        hijri: (LocalDate, Int) -> Triple<Int, Int, Int>,
    ): HawlPeriod {
        val (y, m, d) = hijri(ownershipStart, offsetDays)
        val targetYear = y + 1
        for (day in d downTo maxOf(1, d - 2)) {
            val candidate = findCivilDate(targetYear, m, day, offsetDays, hijri)
            if (candidate != null && !candidate.isBefore(ownershipStart)) {
                return HawlPeriod(
                    starts = ownershipStart,
                    anniversary = candidate,
                    ends = candidate,
                    hijriYearCompleted = targetYear,
                    snappedDays = d - day,
                )
            }
        }
        throw IllegalStateException("hawl anniversary not resolvable for $ownershipStart")
    }

    data class HawlPeriod(
        val starts: LocalDate,
        /** The hijri anniversary civil date (may be snapped back ≤2 days). */
        val anniversary: LocalDate,
        val ends: LocalDate,
        val hijriYearCompleted: Int,
        /** How many days the anniversary was snapped back (0 = exact). */
        val snappedDays: Int,
    )

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

    enum class Species { CAMELS, CATTLE, SHEEP_OR_GOATS }

    /** Age/species class of the animal actually owed. Labels are the UI's job. */
    enum class AnimalClass(val key: String) {
        SHEEP_OR_GOAT("sheep_or_goat"),
        BINT_MAKHAD("bint_makhad"),   // she-camel ~1 year
        BINT_LABUN("bint_labun"),     // she-camel ~2 years
        HIQQAH("hiqqah"),             // she-camel ~3 years
        JADHAAH("jadhaah"),           // she-camel ~4 years
        TABI_OR_TAQIAH("tabi_or_taqiah"), // cattle bull/cow ~1–2 years (owner's choice)
    }

    enum class BandKind { PER_GROUP, FLAT }

    data class LivestockBand(
        val fromCount: Int,
        val toCount: Int?, // null = open-ended
        val kind: BandKind,
        val groupSize: Int?,
        val headcountDue: Int?,
        val animalClass: AnimalClass,
        val provenance: String,
    )

    data class LivestockSchedule(
        val species: Species,
        val nisabThreshold: Int,
        val bands: List<LivestockBand>,
        /** Citation for the beyond-table continuation rule (open-ended band). */
        val beyondTableProvenance: String?,
    )

    private const val P_ISLAMQA = "IslamQA #71267 — classical an'aam schedule"

    val CAMEL_SCHEDULE = LivestockSchedule(
        species = Species.CAMELS,
        nisabThreshold = 5,
        bands = listOf(
            LivestockBand(5, 24, BandKind.PER_GROUP, 5, 1, AnimalClass.SHEEP_OR_GOAT, P_ISLAMQA),
            LivestockBand(25, 35, BandKind.FLAT, null, 1, AnimalClass.BINT_MAKHAD, P_ISLAMQA),
            LivestockBand(36, 45, BandKind.FLAT, null, 1, AnimalClass.BINT_LABUN, P_ISLAMQA),
            LivestockBand(46, 60, BandKind.FLAT, null, 1, AnimalClass.HIQQAH, P_ISLAMQA),
            LivestockBand(61, 75, BandKind.FLAT, null, 1, AnimalClass.JADHAAH, P_ISLAMQA),
            LivestockBand(76, 90, BandKind.FLAT, null, 2, AnimalClass.BINT_LABUN, P_ISLAMQA),
            LivestockBand(91, 120, BandKind.FLAT, null, 2, AnimalClass.HIQQAH, P_ISLAMQA),
            LivestockBand(121, null, BandKind.PER_GROUP, null, 1, AnimalClass.BINT_LABUN,
                "Beyond 120: for every forty a bint labun, for every fifty a hiqqah ($P_ISLAMQA); combination minimizing total headcount preferred"),
        ),
        beyondTableProvenance = P_ISLAMQA,
    )

    val CATTLE_SCHEDULE = LivestockSchedule(
        species = Species.CATTLE,
        nisabThreshold = 30,
        bands = listOf(
            LivestockBand(30, null, BandKind.PER_GROUP, 30, 1, AnimalClass.TABI_OR_TAQIAH, P_ISLAMQA),
        ),
        beyondTableProvenance = P_ISLAMQA,
    )

    val SHEEP_SCHEDULE = LivestockSchedule(
        species = Species.SHEEP_OR_GOATS,
        nisabThreshold = 40,
        bands = listOf(
            LivestockBand(40, null, BandKind.PER_GROUP, 40, 1, AnimalClass.SHEEP_OR_GOAT, P_ISLAMQA),
        ),
        beyondTableProvenance = P_ISLAMQA,
    )

    fun scheduleFor(kind: Species): LivestockSchedule = when (kind) {
        Species.CAMELS -> CAMEL_SCHEDULE
        Species.CATTLE -> CATTLE_SCHEDULE
        Species.SHEEP_OR_GOATS -> SHEEP_SCHEDULE
    }

    data class LivestockDue(
        val species: Species,
        val countAssessed: Int,
        val nisabThreshold: Int,
        val headcountDue: Int,
        val animalClass: AnimalClass,
        val provenance: String,
        /** True when the selected methodology cannot fully resolve the case. */
        val requiresScholarReview: Boolean = false,
        val reviewReason: String? = null,
    )

    /** Structured obligation for [count] animals of [kind]; null below nisab. */
    fun livestockDue(kind: Species, count: Int): LivestockDue? {
        require(count >= 0)
        val schedule = scheduleFor(kind)
        if (count < schedule.nisabThreshold) return null

        val band = schedule.bands.last { count >= it.fromCount }

        return when {
            // Camels beyond the explicit table: classical 40/50 decomposition.
            band.kind == BandKind.PER_GROUP && band.groupSize == null ->
                decomposeCamels(count, band)
            band.kind == BandKind.PER_GROUP ->
                LivestockDue(
                    kind, count, schedule.nisabThreshold,
                    floor(count / band.groupSize!!.toDouble()).toInt(),
                    band.animalClass, band.provenance,
                )
            else -> LivestockDue(
                kind, count, schedule.nisabThreshold,
                band.headcountDue!!, band.animalClass, band.provenance,
            )
        }
    }

    /**
     * Beyond 120: base obligation (two hiqqah) plus, over the excess, every
     * complete fifty → one hiqqah and every complete forty → one bint labun;
     * among valid decompositions the one minimizing total headcount is
     * preferred (documented practice). When the excess leaves a remainder that
     * neither division resolves cleanly, the result flags scholar review
     * rather than guessing.
     */
    private fun decomposeCamels(count: Int, band: LivestockBand): LivestockDue {
        val excess = count - 120
        val roundedExcess = (excess / 10) * 10
        var best: Pair<Int, Int>? = null // k50 to k40
        for (k50 in 0..roundedExcess / 50) {
            val remainder = roundedExcess - k50 * 50
            if (remainder % 40 == 0) {
                val k40 = remainder / 40
                if (best == null || (k50 + k40) < (best.first + best.second)) best = Pair(k50, k40)
            }
        }
        val (k50, k40) = best ?: Pair(floor(excess / 50.0).toInt(), 0)
        val unresolved = best == null || excess % 10 != 0
        val headcount = 2 /*base*/ + k50 /*hiqqah*/ + k40 /*bint labun*/
        val primaryClass = if (k50 > 0 || headcount == 2) AnimalClass.HIQQAH else AnimalClass.BINT_LABUN
        return LivestockDue(
            species = Species.CAMELS,
            countAssessed = count,
            nisabThreshold = scheduleFor(Species.CAMELS).nisabThreshold,
            headcountDue = headcount,
            animalClass = primaryClass,
            provenance = band.provenance,
            requiresScholarReview = unresolved,
            reviewReason = if (unresolved) "excess $excess does not decompose cleanly into 40/50 groups" else null,
        )
    }

    /** Nisab threshold for the species. */
    fun livestockNisab(kind: Species): Int = scheduleFor(kind).nisabThreshold

    // ── Ushr (agricultural) ──────────────────────────────────────────────────

    enum class Irrigation { NATURAL, ARTIFICIAL }

    fun ushrRate(irrigation: Irrigation): Double = when (irrigation) {
        Irrigation.NATURAL -> 0.10
        Irrigation.ARTIFICIAL -> 0.05
    }

    fun ushrDue(harvestValue: Double, irrigation: Irrigation): Double {
        require(harvestValue >= 0.0)
        return harvestValue * ushrRate(irrigation)
    }

    // ── Rikaz ────────────────────────────────────────────────────────────────

    const val RIKAZ_RATE = 0.20

    fun rikazDue(treasureValue: Double): Double {
        require(treasureValue >= 0.0)
        return treasureValue * RIKAZ_RATE
    }
}

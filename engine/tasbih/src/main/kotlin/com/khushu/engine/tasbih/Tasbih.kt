package com.khushu.engine.tasbih

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.observance.DayStreakMath
import com.khushu.engine.core.observance.DayStreakMath.StreakFacts
import java.time.LocalDate

/**
 * One tasbih (dhikr counter) the user counts against.
 *
 * [targetCount] is one SESSION's completion count (the canonical number of
 * the dhikr — e.g. 33 for SubhanAllah). [dailyGoal] is the per-day repetition
 * target used for STRICT goal streaks; null = freeform (totals only, no
 * streak). The engine never invents goals — presets prefill [targetCount]
 * only and leave [dailyGoal] to the user.
 */
data class TasbihDefinition(
    /** Host-stable id (preset ids are fixed strings; custom ids host-chosen). */
    val id: String,
    val name: String,
    val arabic: String? = null,
    val targetCount: Int,
    val dailyGoal: Int? = null,
    val source: TasbihSource = TasbihSource.CUSTOM,
    /** DUA → dua id; ASMA_NAME → name number; null for PRESET/CUSTOM. */
    val sourceRef: String? = null,
) {
    init {
        if (targetCount <= 0) {
            throw InvalidParameterException("targetCount", "$targetCount", "must be > 0")
        }
        if (dailyGoal != null && dailyGoal <= 0) {
            throw InvalidParameterException("dailyGoal", "$dailyGoal", "must be > 0 when set")
        }
        if (name.isBlank()) {
            throw InvalidParameterException("name", name, "must not be blank")
        }
    }
}

/** Where a definition came from — drives host UI affordances. */
enum class TasbihSource { PRESET, DUA, ASMA_NAME, CUSTOM }

/** One counted increment (host logs; multiple per day sum). */
data class TasbihCountLog(
    val definitionId: String,
    val date: LocalDate,
    val count: Int,
) {
    init {
        if (count <= 0) {
            throw InvalidParameterException("count", "$count", "must be > 0")
        }
    }
}

/** Per-definition statistics over the logged span. */
data class DhikrStats(
    val definitionId: String,
    /** Total counted today (summed across that date's logs). */
    val todayCount: Int,
    /** True when todayCount met/exceeded the daily goal (false when freeform). */
    val goalMetToday: Boolean,
    /** STRICT streak of goal-met-or-excused days (0 when freeform or nothing logged today). */
    val goalStreakDays: Int,
    val longestGoalStreakDays: Int,
    /** Lifetime total across all logged days. */
    val lifetimeTotal: Int,
    val lastCountedDate: LocalDate?,
)

/**
 * Tasbih counting capability — canonical presets (engine-seeded, fiqh-sourced)
 * + factories over data-api content + STRICT goal streaks via the shared
 * core day math. Host persists definitions and logs (Room); the engine is
 * stateless and computes over what it is given (observance-logs doctrine).
 */
object Tasbih {

    // ── Presets (D1: canonical devotional conventions, KDoc-cited) ──────────

    /** Post-prayer set — Muslim #597 (33/33/34 after each prayer). */
    const val PRESET_SUBHANALLAH = "preset_subhanallah_33"
    const val PRESET_ALHAMDULILLAH = "preset_alhamdulillah_33"
    const val PRESET_ALLAHU_AKBAR = "preset_allahu_akbar_34"

    /** Tirmidhi / Abu Dawud: "Astaghfirullah" 100 times daily. */
    const val PRESET_ISTIGHFAR = "preset_istighfar_100"

    /** Muslim #408: salawat 100 times daily. */
    const val PRESET_SALAWAT = "preset_salawat_100"

    /** Tirmidhi: Tahleel (La ilaha illa Allah) 100 times daily. */
    const val PRESET_TAHLEEL = "preset_tahleel_100"

    /** Bukhari/Muslim: "SubhanAllahi wa bihamdihi" 100 times daily. */
    const val PRESET_SUBHANALLAHI_WA_BIHAMDIHI = "preset_subhanallahi_wa_bihamdihi_100"

    /** The 7 canonical seeds. dailyGoal stays null — the user sets goals. */
    val PRESETS: List<TasbihDefinition> = listOf(
        TasbihDefinition(PRESET_SUBHANALLAH, "SubhanAllah", "سُبْحَانَ اللَّهِ", 33, source = TasbihSource.PRESET),
        TasbihDefinition(PRESET_ALHAMDULILLAH, "Alhamdulillah", "الْحَمْدُ لِلَّهِ", 33, source = TasbihSource.PRESET),
        TasbihDefinition(PRESET_ALLAHU_AKBAR, "Allahu Akbar", "اللَّهُ أَكْبَرُ", 34, source = TasbihSource.PRESET),
        TasbihDefinition(PRESET_ISTIGHFAR, "Istighfar (Astaghfirullah)", "أَسْتَغْفِرُ اللَّهَ", 100, source = TasbihSource.PRESET),
        TasbihDefinition(PRESET_SALAWAT, "Salawat", "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ", 100, source = TasbihSource.PRESET),
        TasbihDefinition(PRESET_TAHLEEL, "Tahleel (La ilaha illa Allah)", "لَا إِلَٰهَ إِلَّا اللَّهُ", 100, source = TasbihSource.PRESET),
        TasbihDefinition(
            PRESET_SUBHANALLAHI_WA_BIHAMDIHI, "SubhanAllahi wa bihamdihi",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", 100, source = TasbihSource.PRESET,
        ),
    )

    // ── Factories (host flow: select dua/dhikr/name in data-api → create) ──

    /** Create a tasbih from a data-api dua entry (`content.dua.dua(id)`). */
    fun ofDua(duaId: Int, name: String, arabic: String?, targetCount: Int): TasbihDefinition =
        TasbihDefinition(
            id = "dua_$duaId",
            name = name,
            arabic = arabic,
            targetCount = targetCount,
            source = TasbihSource.DUA,
            sourceRef = duaId.toString(),
        )

    /** Create a tasbih from one of the 99 Names (`content.dua.asmaName(...)`). */
    fun ofName(nameNumber: Int, name: String, arabic: String, targetCount: Int): TasbihDefinition =
        TasbihDefinition(
            id = "asma_$nameNumber",
            name = name,
            arabic = arabic,
            targetCount = targetCount,
            source = TasbihSource.ASMA_NAME,
            sourceRef = nameNumber.toString(),
        )

    /** Fully host-defined tasbih (custom dhikr not in any corpus). */
    fun custom(id: String, name: String, targetCount: Int, dailyGoal: Int? = null): TasbihDefinition =
        TasbihDefinition(id, name, targetCount = targetCount, dailyGoal = dailyGoal, source = TasbihSource.CUSTOM)

    // ── Statistics (STRICT via core math; excused ≠ grace) ──────────────────

    /** Total counted on the definition's most relevant "today" ([date]). */
    fun progressOn(logs: List<TasbihCountLog>, definition: TasbihDefinition, date: LocalDate): Int =
        logs.filter { it.definitionId == definition.id && it.date == date }.sumOf { it.count }

    /**
     * Per-definition stats over the logged span (first..last logged date for
     * that definition). Freeform definitions (null goal) get totals only —
     * goalMet/goalStreak are false/0. Excused days are neutral for streaks.
     */
    fun dhikrStats(
        logs: List<TasbihCountLog>,
        definitions: List<TasbihDefinition>,
        today: LocalDate,
        excusedRanges: List<ClosedRange<LocalDate>> = emptyList(),
    ): List<DhikrStats> = definitions.map { def ->
        val mine = logs.filter { it.definitionId == def.id }
        val todayCount = mine.filter { it.date == today }.sumOf { it.count }
        val lifetime = mine.sumOf { it.count }
        val lastCounted = mine.maxOfOrNull { it.date }
        if (def.dailyGoal == null || mine.isEmpty()) {
            DhikrStats(
                definitionId = def.id, todayCount = todayCount,
                goalMetToday = false, goalStreakDays = 0, longestGoalStreakDays = 0,
                lifetimeTotal = lifetime, lastCountedDate = lastCounted,
            )
        } else {
            val perDay = mine.groupBy { it.date }
            val first = mine.minOf { it.date }
            val last = mine.maxOf { it.date }
            val days = DayStreakMath.dayClasses(
                first, last,
                isComplete = { d -> (perDay[d]?.sumOf { it.count } ?: 0) >= def.dailyGoal!! },
                excusedRanges = excusedRanges,
            )
            val facts: StreakFacts = DayStreakMath.streakFacts(days)
            // A current streak that includes days after `today` cannot exist —
            // logs are host-supplied history; trailing run ends at last log.
            DhikrStats(
                definitionId = def.id, todayCount = todayCount,
                goalMetToday = todayCount >= def.dailyGoal!!,
                goalStreakDays = facts.currentStreakDays,
                longestGoalStreakDays = facts.longestStreakDays,
                lifetimeTotal = lifetime, lastCountedDate = lastCounted,
            )
        }
    }
}

package com.khushu.engine.tasbih

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasbihTest {

    private val today = LocalDate.parse("2026-09-05")

    @Test
    fun presetsAreSevenCanonicalSeedsWithSources() {
        assertEquals(7, Tasbih.PRESETS.size)
        // post-prayer set 33/33/34
        assertEquals(33, Tasbih.PRESETS.first { it.id == Tasbih.PRESET_SUBHANALLAH }.targetCount)
        assertEquals(33, Tasbih.PRESETS.first { it.id == Tasbih.PRESET_ALHAMDULILLAH }.targetCount)
        assertEquals(34, Tasbih.PRESETS.first { it.id == Tasbih.PRESET_ALLAHU_AKBAR }.targetCount)
        // 100-dhikr set
        listOf(Tasbih.PRESET_ISTIGHFAR, Tasbih.PRESET_SALAWAT, Tasbih.PRESET_TAHLEEL, Tasbih.PRESET_SUBHANALLAHI_WA_BIHAMDIHI)
            .forEach { id -> assertEquals(100, Tasbih.PRESETS.first { it.id == id }.targetCount) }
        // all engine seeds, never goal-prefilled
        assertTrue(Tasbih.PRESETS.all { it.source == TasbihSource.PRESET })
        assertTrue(Tasbih.PRESETS.all { it.dailyGoal == null })
        assertTrue(Tasbih.PRESETS.all { !it.arabic.isNullOrBlank() })
        assertTrue(Tasbih.PRESETS.map { it.id }.distinct().size == 7)
    }

    @Test
    fun factoriesTagTheirSource() {
        val fromDua = Tasbih.ofDua(47, "Evening dhikr", "ar", 3)
        assertEquals(TasbihSource.DUA, fromDua.source)
        assertEquals("dua_47", fromDua.id)
        assertEquals("47", fromDua.sourceRef)

        val fromName = Tasbih.ofName(1, "Ar-Rahman", "الرَّحْمَٰنُ", 33)
        assertEquals(TasbihSource.ASMA_NAME, fromName.source)
        assertEquals("asma_1", fromName.id)
        assertEquals("1", fromName.sourceRef)

        val custom = Tasbih.custom("my_dhikr", "My dhikr", 40, dailyGoal = 3)
        assertEquals(TasbihSource.CUSTOM, custom.source)
        assertNull(custom.sourceRef)
    }

    @Test
    fun invalidDefinitionsRejected() {
        assertFailsWith<IllegalArgumentException> { Tasbih.custom("x", "", 10) }
        assertFailsWith<IllegalArgumentException> { Tasbih.custom("x", "ok", 0) }
        assertFailsWith<IllegalArgumentException> { Tasbih.custom("x", "ok", 10, dailyGoal = 0) }
        assertFailsWith<IllegalArgumentException> { TasbihCountLog("x", today, 0) }
    }

    @Test
    fun multipleLogsOneDaySum() {
        val def = Tasbih.custom("x", "ok", 10)
        val logs = listOf(
            TasbihCountLog("x", today, 3),
            TasbihCountLog("x", today, 4),
            TasbihCountLog("other", today, 100),
        )
        assertEquals(7, Tasbih.progressOn(logs, def, today))
    }

    @Test
    fun freeformDefinitionsGetTotalsOnly() {
        val def = Tasbih.custom("x", "ok", 10, dailyGoal = null)
        val logs = listOf(
            TasbihCountLog("x", LocalDate.parse("2026-09-04"), 50),
            TasbihCountLog("x", today, 5),
        )
        val stats = Tasbih.dhikrStats(logs, listOf(def), today).single()
        assertEquals(5, stats.todayCount)
        assertEquals(55, stats.lifetimeTotal)
        assertEquals(false, stats.goalMetToday)
        assertEquals(0, stats.goalStreakDays)
        assertEquals(0, stats.longestGoalStreakDays)
        assertEquals(today, stats.lastCountedDate)
    }

    @Test
    fun strictGoalStreaksBreakOnMissedGoalDays() {
        val def = Tasbih.custom("x", "ok", 10, dailyGoal = 10)
        // Sep 1: 10 (met), Sep 2: 5 (missed → breaks), Sep 3: 10, Sep 4: 10, Sep 5: 10 → current 3
        val logs = listOf(
            TasbihCountLog("x", LocalDate.parse("2026-09-01"), 10),
            TasbihCountLog("x", LocalDate.parse("2026-09-02"), 5),
            TasbihCountLog("x", LocalDate.parse("2026-09-03"), 10),
            TasbihCountLog("x", LocalDate.parse("2026-09-04"), 10),
            TasbihCountLog("x", today, 10),
        )
        val stats = Tasbih.dhikrStats(logs, listOf(def), today).single()
        assertEquals(true, stats.goalMetToday)
        assertEquals(3, stats.goalStreakDays)
        assertEquals(3, stats.longestGoalStreakDays)
        assertEquals(45, stats.lifetimeTotal)
    }

    @Test
    fun excusedDaysAreNeutralForGoalStreaks() {
        val def = Tasbih.custom("x", "ok", 10, dailyGoal = 10)
        val excused = LocalDate.parse("2026-09-02")..LocalDate.parse("2026-09-03")
        val logs = listOf(
            TasbihCountLog("x", LocalDate.parse("2026-09-01"), 10),
            TasbihCountLog("x", LocalDate.parse("2026-09-04"), 10),
            TasbihCountLog("x", today, 10),
        )
        val stats = Tasbih.dhikrStats(logs, listOf(def), today, excusedRanges = listOf(excused)).single()
        // complete + excused×2 + complete + complete → streak 5
        assertEquals(5, stats.goalStreakDays)
        assertEquals(5, stats.longestGoalStreakDays)
    }

    @Test
    fun statsIsolateDefinitions() {
        val a = Tasbih.custom("a", "A", 10, dailyGoal = 5)
        val b = Tasbih.custom("b", "B", 10, dailyGoal = 5)
        val logs = listOf(
            TasbihCountLog("a", today, 7),
            TasbihCountLog("b", today, 1),
        )
        val stats = Tasbih.dhikrStats(logs, listOf(a, b), today)
        assertEquals(true, stats.first { it.definitionId == "a" }.goalMetToday)
        assertEquals(false, stats.first { it.definitionId == "b" }.goalMetToday)
    }
}

package com.khushu.engine.core.observance

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DayStreakMathTest {

    private val d = LocalDate.of(2026, 9, 1)!!

    private fun range(from: String, to: String): ClosedRange<LocalDate> =
        LocalDate.parse(from)..LocalDate.parse(to)

    @Test
    fun emptySpanProducesZeroFacts() {
        val facts = DayStreakMath.streakFacts(emptyList())
        assertEquals(0, facts.currentStreakDays)
        assertEquals(0, facts.longestStreakDays)
        assertEquals(0, facts.spanDays)
    }

    @Test
    fun completeDaysExtendAndSilentDaysBreak() {
        val days = DayStreakMath.dayClasses(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-04"),
            isComplete = { it != LocalDate.parse("2026-09-03") },
        )
        // complete, complete, silent, complete
        val facts = DayStreakMath.streakFacts(days)
        assertEquals(1, facts.currentStreakDays)
        assertEquals(2, facts.longestStreakDays)
        assertEquals(3, facts.completedDays)
        assertEquals(4, facts.spanDays)
    }

    @Test
    fun excusedDaysAreNeutralNotBreaking() {
        val days = DayStreakMath.dayClasses(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03"),
            isComplete = { it == LocalDate.parse("2026-09-01") || it == LocalDate.parse("2026-09-03") },
            excusedRanges = listOf(range("2026-09-02", "2026-09-02")),
        )
        // complete, EXCUSED, complete → one streak of 3
        val facts = DayStreakMath.streakFacts(days)
        assertEquals(3, facts.currentStreakDays)
        assertEquals(3, facts.longestStreakDays)
        assertEquals(2, facts.completedDays)
        assertEquals(1, facts.excusedDays)
    }

    @Test
    fun trailingIncompleteDayCapsCurrentStreak() {
        val days = DayStreakMath.dayClasses(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03"),
            isComplete = { it != LocalDate.parse("2026-09-03") },
        )
        val facts = DayStreakMath.streakFacts(days)
        assertEquals(0, facts.currentStreakDays, "incomplete last day → no trailing run")
        assertEquals(2, facts.longestStreakDays)
    }

    @Test
    fun longExcusedRangeKeepsStreakAliveWithoutCompleting() {
        // logged 1 day, excused the next 5 — streak survives as 6 but only 1 completed
        val days = DayStreakMath.dayClasses(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-06"),
            isComplete = { it == LocalDate.parse("2026-09-01") },
            excusedRanges = listOf(range("2026-09-02", "2026-09-06")),
        )
        val facts = DayStreakMath.streakFacts(days)
        assertEquals(6, facts.currentStreakDays)
        assertEquals(1, facts.completedDays)
        assertEquals(5, facts.excusedDays)
    }

    @Test
    fun invalidSpanRejected() {
        assertFailsWith<IllegalArgumentException> {
            DayStreakMath.dayClasses(
                LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-01"),
                isComplete = { true },
            )
        }
    }

    @Test
    fun silentDaysInsideSpanBreak() {
        // no isComplete ever true → every day breaks; facts all zero
        val days = DayStreakMath.dayClasses(
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03"),
            isComplete = { false },
        )
        val facts = DayStreakMath.streakFacts(days)
        assertEquals(0, facts.currentStreakDays)
        assertEquals(0, facts.longestStreakDays)
        assertEquals(0, facts.completedDays)
        assertTrue(days.all { !it.complete && !it.excused })
    }
}

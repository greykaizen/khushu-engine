package com.khushu.engine.core.observance

import com.khushu.engine.core.error.InvalidParameterException
import java.time.LocalDate

/**
 * Domain-agnostic day-classification + streak math shared by every
 * observance domain (prayer semantics, tasbih goals, reading targets).
 *
 * Documented day contract (mirrors prayer.streakStats verbatim):
 * - A COMPLETE day extends the streak.
 * - An EXCUSED day (host-declared travel/illness/menes exemption) is
 *   NEUTRAL: neither extends nor breaks.
 * - A silent/uncompleted day BREAKS the streak — silence is not success.
 * - Days outside the first..last span are ignored entirely.
 *
 * CRITICAL DISTINCTION: excused ≠ grace. Excused ranges are legitimate
 * host-declared fiqh exemptions; "grace"/streak-freeze (engine-invented
 * excuse) does not exist anywhere in this engine.
 */
object DayStreakMath {

    /** Classification of one civil day within the logged span. */
    data class DayClass(val complete: Boolean, val excused: Boolean)

    /** Streak facts over a classified day sequence. */
    data class StreakFacts(
        val currentStreakDays: Int,
        val longestStreakDays: Int,
        val completedDays: Int,
        val excusedDays: Int,
        val spanDays: Int,
    )

    /**
     * Classify every civil day in [first]..[last].
     *
     * @param isComplete whether the day met its domain-specific target.
     * @param excusedRanges host-declared exemption ranges; a day inside any
     *   range is EXCUSED regardless of completion (the host's fiqh call).
     * @throws InvalidParameterException when [first] is after [last].
     */
    fun dayClasses(
        first: LocalDate,
        last: LocalDate,
        isComplete: (LocalDate) -> Boolean,
        excusedRanges: List<ClosedRange<LocalDate>> = emptyList(),
    ): List<DayClass> {
        if (last.isBefore(first)) {
            throw InvalidParameterException("first/last", "$first..$last", "last must not be before first")
        }
        fun isExcused(date: LocalDate) = excusedRanges.any { date in it }
        val out = mutableListOf<DayClass>()
        var d = first
        while (!d.isAfter(last)) {
            out += if (isExcused(d)) {
                DayClass(complete = false, excused = true)
            } else {
                DayClass(complete = isComplete(d), excused = false)
            }
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Longest run of complete-or-excused days + the trailing such run ending
     * at the last day (current streak). Empty list → all-zero facts.
     */
    fun streakFacts(days: List<DayClass>): StreakFacts {
        var longest = 0
        var running = 0
        for (day in days) {
            if (day.complete || day.excused) {
                running++
                if (running > longest) longest = running
            } else {
                running = 0
            }
        }
        var current = 0
        for (day in days.asReversed()) {
            if (day.complete || day.excused) current++ else break
        }
        return StreakFacts(
            currentStreakDays = current,
            longestStreakDays = longest,
            completedDays = days.count { it.complete },
            excusedDays = days.count { it.excused },
            spanDays = days.size,
        )
    }
}

package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObservanceTest {

    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    @Test
    fun navigationIsOrderedAndCompleteForMiddayQuery() {
        val date = LocalDate.of(2025, 10, 15)
        val t = Prayer.times(london, date)
        val midAsr = t.asr.adjusted!!.plusSeconds(60)

        // before=2, after=2 must return the full 5-entry window (no truncation
        // needed at mid-day — plenty of occurrences on both sides).
        val seq = Prayer.navigation(london, midAsr, zone, before = 2, after = 2)
        assertEquals(5, seq.size)
        assertEquals(PrayerStatus.Prayer.ASR, seq[2].kind)
        seq.zipWithNext().forEach { (a, b) -> assertTrue(a.instant < b.instant) }
    }

    @Test
    fun streaksCountCompleteDaysAndExcusedPauses() {
        val start = LocalDate.of(2025, 6, 1)
        fun rec(date: LocalDate, kind: PrayerStatus.Prayer, completed: Boolean) =
            Prayer.PrayerLogRecord(date, kind, completed)

        val obligatory = listOf(
            PrayerStatus.Prayer.FAJR, PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB, PrayerStatus.Prayer.ISHA,
        )

        // 3 complete days, 1 excused day, 1 day with a missed Asr, 2 complete.
        val records = buildList {
            for (offset in 0..2) {
                val d = start.plusDays(offset.toLong())
                obligatory.forEach { add(rec(d, it, true)) }
            }
            // Day 4: fully excused — no records at all.
            for (offset in 4..5) {
                val d = start.plusDays(offset.toLong())
                obligatory.forEach { add(rec(d, it, offset == 5)) } // day 6 misses Asr
            }
            for (offset in 6..7) {
                val d = start.plusDays(offset.toLong())
                obligatory.forEach { add(rec(d, it, true)) }
            }
        }

        val stats = Prayer.streakStats(
            records,
            excusedRanges = listOf(start.plusDays(3)..start.plusDays(3)),
        )

        assertEquals(7, stats.spanDaysNonExcused) // 8-day span minus 1 excused
        assertEquals(1, stats.excusedDaysCount)
        assertEquals(3, stats.currentStreakDays) // days 6(excused? no—complete),7 complete; wait see below
        assertTrue(stats.longestStreakDays >= stats.currentStreakDays)

        // Day 4 logs every prayer as missed: all obligatory rates are 6/7.
        for (kind in obligatory) {
            assertEquals(6.0 / 7.0, stats.perPrayerCompletionRate[kind]!!, 1e-9, "$kind")
        }
    }

    @Test
    fun silenceWithinSpanBreaksButOutsideDoesNot() {
        val start = LocalDate.of(2025, 8, 1)
        val obligatory = listOf(
            PrayerStatus.Prayer.FAJR, PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB, PrayerStatus.Prayer.ISHA,
        )
        fun fullDay(d: LocalDate) = obligatory.map { Prayer.PrayerLogRecord(d, it, true) }

        // Logged days 1 and 10 only — the silent gap breaks any run between them.
        val stats = Prayer.streakStats(fullDay(start) + fullDay(start.plusDays(9)))
        assertEquals(1, stats.longestStreakDays)
        assertEquals(1, stats.currentStreakDays)
        assertEquals(10, stats.spanDaysNonExcused) // silent gap still counts against completeness
    }

    @Test
    fun randomizedInvariantsHold() {
        val rng = Random(42)
        val kinds = listOf(
            PrayerStatus.Prayer.FAJR, PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB, PrayerStatus.Prayer.ISHA, PrayerStatus.Prayer.SUNRISE,
        )
        repeat(20) { trial ->
            val base = LocalDate.of(2025, 3, 1)
            val records = buildList {
                repeat(rng.nextInt(30, 120)) {
                    val d = base.plusDays(rng.nextLong(0, 90))
                    add(Prayer.PrayerLogRecord(d, kinds.random(rng), rng.nextDouble() < 0.85))
                }
            }
            val stats = Prayer.streakStats(records)
            assertTrue(stats.currentStreakDays >= 0 && stats.longestStreakDays >= stats.currentStreakDays)
            stats.perPrayerCompletionRate.values.forEach {
                assertTrue(it.isNaN() || it in 0.0..1.0)
            }
        }
    }
}

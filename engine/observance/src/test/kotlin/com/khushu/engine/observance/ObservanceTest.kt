package com.khushu.engine.observance

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservanceTest {

    private val today = LocalDate.parse("2026-09-05")

    // ── bookmarks ──────────────────────────────────────────────────────────

    @Test
    fun toggleAddsThenRemoves() {
        var list = Observance.toggle(emptyList(), BookmarkDomain.QURAN, "2:255", nowEpochMs = 100)
        assertEquals(1, list.size)
        list = Observance.toggle(list, BookmarkDomain.QURAN, "2:255")
        assertTrue(list.isEmpty())
    }

    @Test
    fun toggleDeduplicatesSameDomainRefAcrossPriorEntries() {
        val pre = listOf(
            Bookmark(BookmarkDomain.QURAN, "2:255", createdAtEpochMs = 50),
            Bookmark(BookmarkDomain.QURAN, "2:255", label = "dupe", createdAtEpochMs = 60),
            Bookmark(BookmarkDomain.HADITH, "bukhari_urn_100010", createdAtEpochMs = 70),
        )
        val out = Observance.toggle(pre, BookmarkDomain.QURAN, "2:255")
        assertEquals(1, out.size)
        assertEquals(BookmarkDomain.HADITH, out.single().domain)
    }

    @Test
    fun upsertMergesAnchorButPreservesCreatedAt() {
        val original = Bookmark(BookmarkDomain.QURAN, "18:10", createdAtEpochMs = 123)
        val updated = Bookmark(BookmarkDomain.QURAN, "18:10", label = "Kahf", anchor = "18:10 word 4", createdAtEpochMs = 999)
        val out = Observance.upsert(listOf(original), updated)
        val merged = out.single()
        assertEquals(123, merged.createdAtEpochMs, "createdAt is sentimental — preserved")
        assertEquals("18:10 word 4", merged.anchor)
        assertEquals("Kahf", merged.label)
    }

    @Test
    fun upsertAppendsWhenNew() {
        val out = Observance.upsert(emptyList(), Bookmark(BookmarkDomain.DUA, "dua_47", createdAtEpochMs = 5))
        assertEquals(1, out.size)
    }

    @Test
    fun bookmarkViewsOrderAndGroup() {
        val list = listOf(
            Bookmark(BookmarkDomain.DUA, "dua_2", createdAtEpochMs = 2),
            Bookmark(BookmarkDomain.QURAN, "2:255", createdAtEpochMs = 3),
            Bookmark(BookmarkDomain.QURAN, "1:1", createdAtEpochMs = 1),
        )
        val views = Observance.bookmarkViews(list)
        assertEquals(2, views.byDomain[BookmarkDomain.QURAN]!!.size)
        assertEquals("2:255", views.recent.first().ref, "newest first")
        assertEquals("1:1", views.all.first { it.domain == BookmarkDomain.QURAN }.ref)
    }

    @Test
    fun invalidBookmarksRejected() {
        assertFailsWith<IllegalArgumentException> { Bookmark(BookmarkDomain.QURAN, "  ", createdAtEpochMs = 1) }
        assertFailsWith<IllegalArgumentException> { Bookmark(BookmarkDomain.QURAN, "2:255", createdAtEpochMs = -1) }
    }

    // ── reading ────────────────────────────────────────────────────────────

    private fun session(
        domain: ReadingDomain, ref: String, date: String, marked: Boolean = false,
        verses: Int? = null, seconds: Int = 600,
    ) = ReadingSession(domain, ref, LocalDate.parse(date), seconds, verses, marked)

    @Test
    fun streaksCountExplicitMarksOnly() {
        // Sep 1-2 marked, Sep 3 scrolled (sessions, no marks → BREAKS), Sep 4-5 marked
        val sessions = listOf(
            session(ReadingDomain.QURAN, "2:1", "2026-09-01", marked = true),
            session(ReadingDomain.QURAN, "2:5", "2026-09-02", marked = true),
            session(ReadingDomain.QURAN, "2:9", "2026-09-03", marked = false, verses = 4),
            session(ReadingDomain.QURAN, "18:1", "2026-09-04", marked = true),
            session(ReadingDomain.QURAN, "18:10", "2026-09-05", marked = true),
        )
        val stats = Observance.readStats(sessions, emptyList(), today)
        val quran = stats.byDomain.getValue(ReadingDomain.QURAN)
        assertEquals(2, quran.streakDays, "unmarked middle day breaks the streak (trailing run = Sep 4–5)")
        assertEquals(2, quran.longestStreakDays)
        assertTrue(quran.markedCompletedToday)
    }

    @Test
    fun domainsIsolateCompletely() {
        val sessions = listOf(
            session(ReadingDomain.QURAN, "2:255", "2026-09-05", marked = true),
            session(ReadingDomain.SUNNAH, "bukhari_urn_1", "2026-09-05", marked = false),
        )
        val stats = Observance.readStats(sessions, emptyList(), today)
        assertTrue(stats.byDomain.getValue(ReadingDomain.QURAN).markedCompletedToday)
        assertTrue(!stats.byDomain.getValue(ReadingDomain.SUNNAH).markedCompletedToday)
        assertNull(stats.continueReading[ReadingDomain.DUA], "no dua sessions → null continue")
        stats.continueReading.getValue(ReadingDomain.QURAN)?.let {
            assertEquals("2:255", it.ref)
        } ?: kotlin.test.fail("QURAN continue-reading must be non-null when sessions exist")
    }

    @Test
    fun excusedRangesApplyToReadingStreaks() {
        val excused = LocalDate.parse("2026-09-03")..LocalDate.parse("2026-09-04")
        val sessions = listOf(
            session(ReadingDomain.SUNNAH, "h1", "2026-09-01", marked = true),
            session(ReadingDomain.SUNNAH, "h2", "2026-09-02", marked = true),
            session(ReadingDomain.SUNNAH, "h5", "2026-09-05", marked = true),
        )
        val stats = Observance.readStats(sessions, emptyList(), today, excusedRanges = listOf(excused))
        assertEquals(5, stats.byDomain.getValue(ReadingDomain.SUNNAH).streakDays)
    }

    @Test
    fun invalidSessionsAndTargetsRejected() {
        assertFailsWith<IllegalArgumentException> { ReadingSession(ReadingDomain.QURAN, "", today, 10) }
        assertFailsWith<IllegalArgumentException> { ReadingSession(ReadingDomain.QURAN, "2:1", today, -1) }
        assertFailsWith<IllegalArgumentException> { ReadingTarget(ReadingDomain.QURAN) }
        assertFailsWith<IllegalArgumentException> { ReadingTarget(ReadingDomain.QURAN, ayahsPerDay = 0) }
    }

    @Test
    fun todayProgressAggregatesVersesAndMinutes() {
        val sessions = listOf(
            session(ReadingDomain.QURAN, "2:1", "2026-09-05", verses = 5, seconds = 120),
            session(ReadingDomain.QURAN, "2:6", "2026-09-05", verses = 7, seconds = 180),
            session(ReadingDomain.QURAN, "2:9", "2026-09-04", verses = 99, seconds = 600),
        )
        val target = ReadingTarget(ReadingDomain.QURAN, ayahsPerDay = 10)
        val stats = Observance.readStats(sessions, listOf(target), today)
        val quran = stats.byDomain.getValue(ReadingDomain.QURAN)
        assertEquals(12, quran.versesViewedToday)
        assertEquals(5, quran.minutesActiveToday)
        assertEquals(15, quran.totalActiveMinutes)
    }

    @Test
    fun historyWindowsAndAggregation() {
        val sessions = listOf(
            session(ReadingDomain.QURAN, "2:1", "2026-09-05", seconds = 60),
            session(ReadingDomain.QURAN, "2:2", "2026-09-05", seconds = 60),
            session(ReadingDomain.DUA, "dua_1", "2026-09-04", seconds = 60),
            session(ReadingDomain.QURAN, "19:1", "2026-08-08", seconds = 600), // in month (−29d), not week (−6d)
        )
        val week = Observance.history(sessions, HistoryWindow.WEEK, today)
        assertEquals(2, week.byDay[today]!!.size)
        assertEquals(3, week.distinctRefs)
        assertEquals(3, week.totalActiveMinutes)

        val month = Observance.history(sessions, HistoryWindow.MONTH, today)
        assertEquals(4, month.distinctRefs)
    }

    // ── khatm ──────────────────────────────────────────────────────────────

    private fun juzTable(): List<JuzRange> {
        // simplified 5-juz test table over 100 ayahs
        return listOf(
            JuzRange(1, AyahRange(1, 20)),
            JuzRange(2, AyahRange(21, 40)),
            JuzRange(3, AyahRange(41, 60)),
            JuzRange(4, AyahRange(61, 80)),
            JuzRange(5, AyahRange(81, 100)),
        )
    }

    @Test
    fun khatmNormalizesOutofOrderOverlapAndAdjacent() {
        val facts = Observance.khatmFacts(
            completed = listOf(
                AyahRange(50, 60), AyahRange(1, 10), AyahRange(55, 65),
                AyahRange(11, 11),
            ),
            juzRanges = juzTable(),
            today = today,
            totalAyahs = 100,
        )
        // 1-11 + 50-65 → two normalized ranges; 27 covered
        assertEquals(listOf(AyahRange(1, 11), AyahRange(50, 65)), facts.normalizedRanges)
        assertEquals(27, facts.coveredAyahs)
        assertEquals(0.27, facts.progressFraction, 1e-9)
        assertEquals(73, facts.remainingAyahs)
    }

    @Test
    fun khatmFrontierFindsFirstGapJuz() {
        // cover 1..25 contiguously → frontier ayah 26 in juz 2
        val facts = Observance.khatmFacts(
            listOf(AyahRange(1, 25)), juzTable(), today, totalAyahs = 100,
        )
        assertEquals(2, facts.currentJuz)
    }

    @Test
    fun khatmDisjointCoverageUsesContiguousFrontierNotMaxCovered() {
        // cover 60..100 but nothing before → frontier is ayah 1 (juz 1), even
        // though 41 ayahs are covered
        val facts = Observance.khatmFacts(
            listOf(AyahRange(60, 100)), juzTable(), today, totalAyahs = 100,
        )
        assertEquals(1, facts.currentJuz)
        assertEquals(41, facts.coveredAyahs)
    }

    @Test
    fun khatmProjectionAndEdges() {
        // empty → nothing covered, juz 1, no projection without rate
        val empty = Observance.khatmFacts(emptyList(), juzTable(), today, totalAyahs = 100)
        assertEquals(0, empty.coveredAyahs)
        assertNull(empty.projectedCompletionDate)

        // complete → progress 1.0, remaining 0, projection = today at any rate
        val done = Observance.khatmFacts(listOf(AyahRange(1, 100)), juzTable(), today, totalAyahs = 100, dailyRateAyahs = 5.0)
        assertEquals(1.0, done.progressFraction, 1e-9)
        assertEquals(0, done.remainingAyahs)
        assertEquals(today, done.projectedCompletionDate)

        // rate projection: 50 remaining at 5/day → 10 days out
        val half = Observance.khatmFacts(listOf(AyahRange(1, 50)), juzTable(), today, totalAyahs = 100, dailyRateAyahs = 5.0)
        assertEquals(today.plusDays(10), half.projectedCompletionDate)
    }

    @Test
    fun khatmRejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { AyahRange(5, 1) }
        assertFailsWith<IllegalArgumentException> { JuzRange(31, AyahRange(1, 10)) }
        assertFailsWith<IllegalArgumentException> {
            Observance.khatmFacts(emptyList(), juzTable(), today, totalAyahs = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            val dup = juzTable() + JuzRange(1, AyahRange(1, 5))
            Observance.khatmFacts(emptyList(), dup, today, totalAyahs = 100)
        }
    }

    @Test
    fun canonicalTotalConstantDocumented() {
        assertEquals(6236, Observance.TOTAL_AYAHS)
        // the real Quran total against data-api ayahs.json rows (verified
        // 2026-08-31 live against inventory/quran_metadata/ayahs.json)
    }
}

package com.khushu.engine.observance

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.observance.DayStreakMath
import java.time.LocalDate

/**
 * Observance capability — CENTRALIZED bookmarking algebra for every content
 * domain + reading-session statistics (STRICT streaks, targets,
 * continue-reading) + khatm progress math. All functions are stateless over
 * caller-supplied logs: the host persists (Room/DataStore), the engine
 * computes, the optional cloud sync mirrors the LOGS — never derived facts.
 *
 * Bookmarks are the SINGLE system for quran/sunnah/dua/names/articles — no
 * per-mod or per-host bookmark implementations. Refs are opaque strings
 * validated against data-api content by the host at creation time.
 */
object Observance {

    // ── Bookmarks ──────────────────────────────────────────────────────────

    /**
     * Add-or-remove by (domain, ref): absent → appended with now as
     * createdAt; present → removed. The host persists the returned list.
     */
    fun toggle(
        bookmarks: List<Bookmark>,
        domain: BookmarkDomain,
        ref: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<Bookmark> {
        val existing = bookmarks.find { it.domain == domain && it.ref == ref }
        return if (existing != null) {
            bookmarks.filterNot { it === existing || (it.domain == domain && it.ref == ref) }
        } else {
            bookmarks + Bookmark(domain, ref, createdAtEpochMs = nowEpochMs)
        }
    }

    /**
     * Insert or merge [candidate]: a bookmark with the same (domain, ref)
     * keeps its ORIGINAL createdAt (sentimental value) and takes the
     * candidate's label/anchor; otherwise the candidate is appended.
     */
    fun upsert(bookmarks: List<Bookmark>, candidate: Bookmark): List<Bookmark> {
        val idx = bookmarks.indexOfFirst { it.domain == candidate.domain && it.ref == candidate.ref }
        return if (idx < 0) {
            bookmarks + candidate
        } else {
            bookmarks.toMutableList().apply {
                set(idx, candidate.copy(createdAtEpochMs = bookmarks[idx].createdAtEpochMs))
            }
        }
    }

    /** Grouped/recent/all views — stateless list-in → list-out. */
    fun bookmarkViews(bookmarks: List<Bookmark>): BookmarkViews = BookmarkViews(
        byDomain = bookmarks.groupBy { it.domain },
        recent = bookmarks.sortedByDescending { it.createdAtEpochMs },
        all = bookmarks.sortedWith(compareBy({ it.domain }, { it.ref })),
    )

    // ── Reading ────────────────────────────────────────────────────────────

    /**
     * Per-domain statistics over logged sessions. Streak semantics are
     * STRICT and identical across domains (design D5): a day counts for the
     * domain's streak when at least one session that day carries the
     * EXPLICIT [ReadingSession.completedToday] mark (scroll-past never
     * completes); excused days are neutral; silent days break.
     */
    fun readStats(
        sessions: List<ReadingSession>,
        targets: List<ReadingTarget>,
        today: LocalDate,
        excusedRanges: List<ClosedRange<LocalDate>> = emptyList(),
    ): ReadStats {
        val domains = sessions.map { it.domain }.distinct()
        val byDomain = domains.associateWith { domain ->
            val mine = sessions.filter { it.domain == domain }
            val todaySessions = mine.filter { it.openedAt == today }
            val markedDates = mine.filter { it.completedToday }.map { it.openedAt }.distinct()
            val (streak, longest) = if (markedDates.isEmpty()) {
                0 to 0
            } else {
                val first = mine.minOf { it.openedAt }
                val last = mine.maxOf { it.openedAt }
                val facts = DayStreakMath.streakFacts(
                    DayStreakMath.dayClasses(
                        first, last,
                        isComplete = { d -> d in markedDates },
                        excusedRanges = excusedRanges,
                    ),
                )
                facts.currentStreakDays to facts.longestStreakDays
            }
            DomainReadStats(
                domain = domain,
                versesViewedToday = todaySessions.sumOf { it.versesViewed ?: 0 },
                minutesActiveToday = todaySessions.sumOf { it.secondsActive } / 60,
                markedCompletedToday = todaySessions.any { it.completedToday },
                streakDays = streak,
                longestStreakDays = longest,
                totalSessions = mine.size,
                totalActiveMinutes = mine.sumOf { it.secondsActive } / 60,
                lastRead = mine.maxByOrNull { it.openedAt },
            )
        }
        // Target progress is surfaced via DomainReadStats' today fields;
        // targets param exists for call-shape stability and future per-axis
        // goal views. Validate targets here so malformed input surfaces early.
        targets.forEach { t ->
            if (t.domain !in ReadingDomain.entries.map { it }) {
                throw InvalidParameterException("target.domain", "$t", "unknown domain")
            }
        }
        return ReadStats(byDomain = byDomain, continueReading = byDomain.mapValues { it.value.lastRead })
    }

    /**
     * Profile history feed: sessions within [window] of [today], grouped by
     * day, with distinct-ref count and active minutes.
     */
    fun history(
        sessions: List<ReadingSession>,
        window: HistoryWindow,
        today: LocalDate,
    ): ReadingHistory {
        val start = when (window) {
            HistoryWindow.WEEK -> today.minusDays(6)
            HistoryWindow.MONTH -> today.minusDays(29)
        }
        val inWindow = sessions.filter { !it.openedAt.isBefore(start) && !it.openedAt.isAfter(today) }
        return ReadingHistory(
            window = window,
            byDay = inWindow.groupBy { it.openedAt }
                .mapValues { (_, s) -> s.sortedWith(compareBy({ it.openedAt }, { it.ref })) },
            distinctRefs = inWindow.map { it.ref }.distinct().size,
            totalActiveMinutes = inWindow.sumOf { it.secondsActive } / 60,
        )
    }

    // ── Khatm ──────────────────────────────────────────────────────────────

    /**
     * Canonical ayah total — a documented constant following the
     * `FIVE_WASAQ_KG` precedent (parameterizable default; hosts with
     * different mushaf counts pass their own).
     */
    const val TOTAL_AYAHS = 6236

    /**
     * Khatm progress over host-logged completed ranges. The engine
     * normalizes (sorts, merges overlapping/adjacent ranges), computes
     * covered/remaining/fraction, finds the juz frontier (the juz containing
     * the first un-covered ayah after the highest covered prefix), and
     * linearly projects completion from [dailyRateAyahs] (null → no
     * projection).
     */
    fun khatmFacts(
        completed: List<AyahRange>,
        juzRanges: List<JuzRange>,
        today: LocalDate,
        totalAyahs: Int = TOTAL_AYAHS,
        dailyRateAyahs: Double? = null,
    ): KhatmFacts {
        if (totalAyahs <= 0) {
            throw InvalidParameterException("totalAyahs", "$totalAyahs", "must be > 0")
        }
        if (juzRanges.map { it.juz }.distinct().size != juzRanges.size) {
            throw InvalidParameterException("juzRanges", "${juzRanges.map { it.juz }}", "duplicate juz numbers")
        }
        val normalized = normalize(completed)
        var covered = 0
        for (r in normalized) {
            val lo = r.startAyahId.coerceAtLeast(1)
            val hi = r.endAyahId.coerceAtMost(totalAyahs)
            if (hi >= lo) covered += hi - lo + 1
        }
        covered = covered.coerceAtMost(totalAyahs)
        // Frontier: first un-covered ayah after the contiguous prefix that
        // starts at ayah 1 (reading order). When everything from 1 is
        // covered, the frontier is the total itself (or first gap).
        var frontier = 1
        for (r in normalized) {
            if (r.startAyahId <= frontier) {
                frontier = r.endAyahId + 1
            } else break
        }
        frontier = frontier.coerceAtMost(totalAyahs + 1)
        val frontierAyah = (frontier - 1).coerceAtLeast(1).coerceAtMost(totalAyahs)
        val currentJuz = juzRanges
            .sortedBy { it.juz }
            .firstOrNull { frontierAyah <= it.range.endAyahId && frontierAyah >= it.range.startAyahId }
            ?.juz
            ?: 1
        val remaining = (totalAyahs - covered).coerceAtLeast(0)
        val projection = dailyRateAyahs?.takeIf { it > 0.0 }
            ?.let { today.plusDays((remaining / it).toLong().coerceAtLeast(0)) }
        return KhatmFacts(
            normalizedRanges = normalized,
            coveredAyahs = covered,
            totalAyahs = totalAyahs,
            progressFraction = if (totalAyahs == 0) 0.0 else covered.toDouble() / totalAyahs,
            currentJuz = currentJuz,
            remainingAyahs = remaining,
            projectedCompletionDate = projection,
        )
    }

    /** Sort + merge overlapping and adjacent ranges (engine-owned math). */
    internal fun normalize(ranges: List<AyahRange>): List<AyahRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.startAyahId }
        val out = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val last = out.last()
            if (r.startAyahId <= last.endAyahId + 1) {
                out[out.lastIndex] = AyahRange(last.startAyahId, maxOf(last.endAyahId, r.endAyahId))
            } else {
                out += r
            }
        }
        return out
    }
}

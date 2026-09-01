package com.khushu.engine.observance

import com.khushu.engine.core.error.InvalidParameterException
import java.time.LocalDate

/** The content domain a bookmark points into. */
enum class BookmarkDomain { QURAN, HADITH, DUA, ASMA_NAME, ARTICLE, CUSTOM }

/**
 * One saved place. [ref] is OPAQUE to the engine — a stable content id the
 * HOST validated against data-api at creation time (`"2:255"`,
 * `"bukhari_urn_100010"`, `"dua_47"`, `"ar-rahman"`, article slug).
 * [anchor] is an optional cross-device position hint (`"18:10 word 4"`).
 * [createdAtEpochMs] carries sentimental value and is PRESERVED on upsert.
 */
data class Bookmark(
    val domain: BookmarkDomain,
    val ref: String,
    val label: String? = null,
    val anchor: String? = null,
    val createdAtEpochMs: Long,
) {
    init {
        if (ref.isBlank()) {
            throw InvalidParameterException("ref", ref, "must not be blank")
        }
        if (createdAtEpochMs < 0) {
            throw InvalidParameterException("createdAtEpochMs", "$createdAtEpochMs", "must be >= 0")
        }
    }
}

/** Reading domains — one session shape covers all of them. */
enum class ReadingDomain { QURAN, SUNNAH, DUA, ARTICLE }

/**
 * One reading session, host-logged. [secondsActive] is screen-visible time
 * (resume/pause), NOT open-time — honest signal only. [completedToday] is
 * the EXPLICIT user mark against their target; scroll-past never completes.
 */
data class ReadingSession(
    val domain: ReadingDomain,
    /** Opaque content ref (`"2:255"`, `"bukhari_urn_100240"`, `"dua_47"`, slug). */
    val ref: String,
    val openedAt: LocalDate,
    val secondsActive: Int,
    val versesViewed: Int? = null,
    val completedToday: Boolean = false,
) {
    init {
        if (ref.isBlank()) {
            throw InvalidParameterException("ref", ref, "must not be blank")
        }
        if (secondsActive < 0) {
            throw InvalidParameterException("secondsActive", "$secondsActive", "must be >= 0")
        }
        if (versesViewed != null && versesViewed < 0) {
            throw InvalidParameterException("versesViewed", "$versesViewed", "must be >= 0 when set")
        }
    }
}

/** A user's daily reading target for one domain (at least one axis set). */
data class ReadingTarget(
    val domain: ReadingDomain,
    val ayahsPerDay: Int? = null,
    val minutesPerDay: Int? = null,
) {
    init {
        if (ayahsPerDay == null && minutesPerDay == null) {
            throw InvalidParameterException("ayahsPerDay/minutesPerDay", "null/null", "at least one target axis must be set")
        }
        if (ayahsPerDay != null && ayahsPerDay <= 0) {
            throw InvalidParameterException("ayahsPerDay", "$ayahsPerDay", "must be > 0 when set")
        }
        if (minutesPerDay != null && minutesPerDay <= 0) {
            throw InvalidParameterException("minutesPerDay", "$minutesPerDay", "must be > 0 when set")
        }
    }
}

/** Aggregated ayah range the host logs as completed reading (khatm input). */
data class AyahRange(val startAyahId: Int, val endAyahId: Int) {
    init {
        if (startAyahId <= 0 || endAyahId < startAyahId) {
            throw InvalidParameterException("startAyahId/endAyahId", "$startAyahId..$endAyahId", "require 0 < start <= end")
        }
    }
}

/** One juz's ayah span — host-supplied from data-api navigation_ranges. */
data class JuzRange(val juz: Int, val range: AyahRange) {
    init {
        if (juz !in 1..30) {
            throw InvalidParameterException("juz", "$juz", "must be within 1..30")
        }
    }
}

/** Per-domain reading statistics for the Profile screen. */
data class DomainReadStats(
    val domain: ReadingDomain,
    /** Verses viewed today (QURAN sessions summed) — null when no ayah axis. */
    val versesViewedToday: Int,
    /** Active minutes today (all sessions summed). */
    val minutesActiveToday: Int,
    /** The explicit user mark for today. */
    val markedCompletedToday: Boolean,
    /** STRICT streak over explicit-mark (or excused) days; 0 with no marks. */
    val streakDays: Int,
    val longestStreakDays: Int,
    val totalSessions: Int,
    val totalActiveMinutes: Int,
    /** The most recent session (continue-reading candidate). */
    val lastRead: ReadingSession?,
)

/** ReadStats grouped by domain + the continue-reading map. */
data class ReadStats(
    val byDomain: Map<ReadingDomain, DomainReadStats>,
    /** Latest session per domain (roadmap Rank-1: continue reading). Null value = nothing logged yet. */
    val continueReading: Map<ReadingDomain, ReadingSession?>,
)

/** History aggregation window. */
enum class HistoryWindow { WEEK, MONTH }

/** "What was read" — the Profile history feed over logged sessions. */
data class ReadingHistory(
    val window: HistoryWindow,
    /** Day → sessions that day (sorted by openedAt, then ref). */
    val byDay: Map<LocalDate, List<ReadingSession>>,
    val distinctRefs: Int,
    val totalActiveMinutes: Int,
)

/** Khatm progress facts — pure math over host-supplied ranges (design D4). */
data class KhatmFacts(
    /** Engine-normalized completed ranges: sorted, overlapping/adjacent merged. */
    val normalizedRanges: List<AyahRange>,
    val coveredAyahs: Int,
    val totalAyahs: Int,
    /** coveredAyahs / totalAyahs. */
    val progressFraction: Double,
    /** The juz containing the first un-read ayah (1 when nothing covered). */
    val currentJuz: Int,
    val remainingAyahs: Int,
    /** Linear projection from a host-computed daily rate (null when rate null/0). */
    val projectedCompletionDate: LocalDate?,
)

/** Bookmark list views. */
data class BookmarkViews(
    val byDomain: Map<BookmarkDomain, List<Bookmark>>,
    val recent: List<Bookmark>,
    val all: List<Bookmark>,
)

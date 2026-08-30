package com.khushu.engine.calendar

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import java.time.LocalDate

/**
 * OBSERVED (moonsighted) calendar mode — deterministic derivation over
 * HOST-SUPPLIED month-start announcements (design: docs/sighting-mode-design.md,
 * un-gated 2026-08-30 per the operational reasoning: astronomy certifies
 * plausibility (hilal.visibility), humans confirm/deny on the moon-track path,
 * the committee's announcement records the outcome, and THIS type derives
 * every date of that month from the anchor).
 *
 * The engine NEVER decides a sighting — committees do. No networking, no
 * persistence, no fiqh verdicts: announcements enter by value.
 *
 * Semantics (v1.15):
 * - An announcement fixes day 1 of its hijri month at [AnnouncedMonthStart.observedFirstDay].
 * - Days inside an announced month count from the anchor: date = anchor + (day − 1).
 * - Months without announcements fall back to the tabular Umm al-Qura
 *   calendar ([HijriCalendar.hijri], same offsetDays as params).
 * - [SightedCalendarParams.localOffsetDays] is the user's global correction,
 *   applied LAST (after anchoring/fallback): the "everything went south"
 *   escape hatch. Validated −2..+2. It shifts which TABULAR month backs an
 *   un-announced date AND shifts day-of-month derivation for announced
 *   months by moving the effective civil date one step before derivation.
 * - Contradictory announcements (same month, different anchors) throw
 *   [InvalidParameterException] — the engine refuses to pick a winner.
 */
object SightedCalendar {

    /** A committee-announced first day of a hijri month. */
    data class AnnouncedMonthStart(
        val hijriYear: Int,
        /** 1-indexed hijri month (1 = Muharram). */
        val hijriMonth: Int,
        /** The committee-announced civil date of day 1. */
        val observedFirstDay: LocalDate,
        /** Free-form authority label — "MoonsightingCommittee", local mosque name… (display only). */
        val authority: String,
    )

    /** Host-supplied sighted-calendar configuration. */
    data class SightedCalendarParams(
        val announcements: List<AnnouncedMonthStart> = emptyList(),
        /** Global user correction applied after all derivation (−2..+2). */
        val localOffsetDays: Int = 0,
        /** Tabular offset used for fallback months (−2..+2, same domain as CalendarParams.hijriOffsetDays). */
        val tabularOffsetDays: Int = 0,
    ) {
        init {
            validate(localOffsetDays in -2..2) {
                InvalidParameterException("localOffsetDays", "$localOffsetDays", "must be within −2..+2")
            }
            validate(tabularOffsetDays in -2..2) {
                InvalidParameterException("tabularOffsetDays", "$tabularOffsetDays", "must be within −2..+2")
            }
            val byMonth = announcements.groupBy { it.hijriYear to it.hijriMonth }
            for ((key, group) in byMonth) {
                val distinctAnchors = group.map { it.observedFirstDay }.distinct()
                if (distinctAnchors.size > 1) {
                    throw InvalidParameterException(
                        "announcements[hijri ${key.first}-${key.second}]",
                        distinctAnchors.joinToString(),
                        "contradictory month-start announcements — the engine refuses to pick a winner " +
                            "(split them by authority and let the host choose)",
                    )
                }
            }
        }
    }

    /** Derivation outcome for one civil date — provenance included. */
    data class SightedHijriDate(
        val date: HijriDate,
        /** True when [date]'s month was announced (anchor-derived); false = tabular fallback. */
        val fromAnnouncement: Boolean,
        /** The authority whose announcement anchored this month (null on fallback). */
        val anchoredBy: String?,
    )

    /**
     * Hijri date for [civilDate] under sighted mode. Derivation:
     * 1. apply [SightedCalendarParams.localOffsetDays] to the civil date;
     * 2. the LATEST announcement whose anchor is at/before the effective
     *    date governs, provided the date is within its month span (until the
     *    next announcement's anchor, or 30 days max);
     * 3. otherwise tabular fallback ([SightedCalendarParams.tabularOffsetDays]).
     */
    fun hijriSighted(civilDate: LocalDate, params: SightedCalendarParams): SightedHijriDate {
        val effective = civilDate.plusDays(params.localOffsetDays.toLong())
        val governing = params.announcements
            .filter { !effective.isBefore(it.observedFirstDay) }
            .maxByOrNull { it.observedFirstDay }
        if (governing != null) {
            val derived = deriveIfInside(governing, params, effective)
            if (derived != null) {
                return SightedHijriDate(derived, fromAnnouncement = true, anchoredBy = governing.authority)
            }
        }
        val tabular = HijriCalendar.hijri(effective, params.tabularOffsetDays)
        return SightedHijriDate(tabular, fromAnnouncement = false, anchoredBy = null)
    }

    /**
     * Civil date of a hijri (year, month, day) under sighted mode. When the
     * month is announced, the result is anchor + (day − 1); otherwise the
     * tabular conversion. `NoResultException`-style typed failures keep the
     * same contract as [HijriCalendar.hijriToGregorian] — out-of-table or
     * out-of-month day numbers throw [InvalidParameterException].
     */
    fun civilOf(
        hijriYear: Int,
        hijriMonth: Int,
        hijriDay: Int,
        params: SightedCalendarParams,
    ): LocalDate {
        validate(hijriDay in 1..30) {
            InvalidParameterException("hijriDay", "$hijriDay", "must be within 1..30")
        }
        val announcement = params.announcements
            .firstOrNull { it.hijriYear == hijriYear && it.hijriMonth == hijriMonth }
        return if (announcement != null) {
            announcement.observedFirstDay.plusDays((hijriDay - 1).toLong())
                .minusDays(params.localOffsetDays.toLong())
        } else {
            val tabular = HijriCalendar.hijriToGregorian(hijriYear, hijriMonth, hijriDay, params.tabularOffsetDays)
            tabular.minusDays(params.localOffsetDays.toLong())
        }
    }

    /**
     * Islamic events under sighted mode: iterate the range civil-day by
     * civil-day, derive each day's SIGHTED hijri date, and emit every
     * built-in definition whose (hijriMonth, hijriDay) matches that sighted
     * date. Announced months therefore carry events shifted with their
     * anchor; unannounced months keep tabular occurrences.
     */
    fun eventsSighted(
        range: ClosedRange<LocalDate>,
        params: SightedCalendarParams,
    ): List<Pair<LocalDate, EventDefinition>> {
        val registry = EventRegistry(params.tabularOffsetDays)
        val defs = registry.all
        if (range.endInclusive.toEpochDay() - range.start.toEpochDay() > 400) {
            throw InvalidParameterException(
                "range", "${range.start}..${range.endInclusive}",
                "span exceeds 400 days — query narrower windows",
            )
        }
        val out = mutableListOf<Pair<LocalDate, EventDefinition>>()
        var d = range.start
        while (!d.isAfter(range.endInclusive)) {
            val sighted = hijriSighted(d, params)
            for (def in defs) {
                if (def.hijriMonth == sighted.date.month && def.hijriDay == sighted.date.day) {
                    out += d to def
                }
            }
            d = d.plusDays(1)
        }
        return out
    }

    // ── internals ──────────────────────────────────────────────────────────

    /**
     * Day count inside the announced month of [a], or null when [civilDate]
     * falls outside it. The month spans [observedFirstDay, nextAnchor) when
     * a following announcement exists (which naturally bounds a 29-day
     * month), else 30 days (the hijri maximum).
     */
    private fun deriveIfInside(
        a: AnnouncedMonthStart,
        params: SightedCalendarParams,
        civilDate: LocalDate,
    ): HijriDate? {
        val start = a.observedFirstDay
        if (civilDate.isBefore(start)) return null
        val dayOffset = civilDate.toEpochDay() - start.toEpochDay()
        val endExclusive = params.announcements
            .map { it.observedFirstDay }
            .filter { it.isAfter(start) }
            .minOrNull()
            ?: start.plusDays(30)
        if (!civilDate.isBefore(endExclusive)) return null
        if (dayOffset >= 30) return null
        return HijriDate(
            year = a.hijriYear,
            month = a.hijriMonth,
            day = (dayOffset + 1).toInt(),
            monthName = HijriCalendar.hijri(start).monthName,
            offsetApplied = 0,
        )
    }
}

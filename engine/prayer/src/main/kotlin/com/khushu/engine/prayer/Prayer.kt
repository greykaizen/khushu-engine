package com.khushu.engine.prayer

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.internal.PrayerCalculator
import java.time.Instant
import java.time.LocalDate

/** Asr shadow-length school. MALIKI and HANBALI follow the standard (Shafi'i) factor. */
enum class Madhab { SHAFII, HANAFI, MALIKI, HANBALI }

/** Fajr/Isha angle presets. CUSTOM uses [PrayerConfiguration.fajrAngle]/[PrayerConfiguration.ishaAngle]. */
enum class Convention {
    MUSLIM_WORLD_LEAGUE,
    ISNA,
    EGYPTIAN,
    UMM_AL_QURA,
    KARACHI,
    DUBAI,
    KUWAIT,
    QATAR,
    SINGAPORE,
    TURKEY,
    MOON_SIGHTING_COMMITTEE,
    CUSTOM,
}

/** Rule for resolving Fajr/Isha when twilight is weak or absent at high latitudes. */
enum class HighLatitudeRule {
    MIDDLE_OF_NIGHT,
    SEVENTH_OF_NIGHT,
    TWILIGHT_ANGLE,
}

/** How Fajr/Isha were resolved for a given day — never lie about a time's origin. */
enum class HighLatitudeResolution {
    /** Both times computed directly under the requested rule. */
    COMPUTED_WITH_REQUESTED_RULE,
    RESOLVED_BY_MIDDLE_OF_NIGHT,
    RESOLVED_BY_SEVENTH_OF_NIGHT,
    RESOLVED_BY_TWILIGHT_ANGLE,
    /** Physically uncomputable (polar day/night). */
    UNAVAILABLE,
}

/**
 * Per-prayer minute adjustments applied to the computed instant.
 * `sunset` offsets the astronomical sunset independently of maghrib.
 */
data class PrayerOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val sunset: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

data class PrayerConfiguration(
    val madhab: Madhab = Madhab.SHAFII,
    val convention: Convention = Convention.MUSLIM_WORLD_LEAGUE,
    /** Only meaningful when convention == CUSTOM. */
    val fajrAngle: Double = 18.0,
    /** Only meaningful when convention == CUSTOM. */
    val ishaAngle: Double = 18.0,
    /** > 0 switches Isha to a fixed interval after maghrib instead of an angle. */
    val ishaIntervalMinutes: Int = 0,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val offsets: PrayerOffsets = PrayerOffsets(),
    /**
     * Twilight-color model of the Moon Sighting Committee method.
     * EXPERIMENTAL pass-through (divergences D13): null inherits the method
     * preset. Only meaningful with MOON_SIGHTING_COMMITTEE.
     */
    val shafaq: Shafaq? = null,
    /**
     * Minute-rounding policy the solver applies to adjusted values.
     * null inherits the method preset. Never reinterpret raw values with a
     * different policy silently.
     */
    val rounding: RoundingPolicy? = null,
) {
    init {
        validate(ishaIntervalMinutes >= 0) {
            InvalidParameterException("ishaIntervalMinutes", "$ishaIntervalMinutes", "must be >= 0")
        }
        validate(fajrAngle in -60.0..60.0 && ishaAngle in -60.0..60.0) {
            InvalidParameterException(
                "fajrAngle/ishaAngle",
                "$fajrAngle/$ishaAngle",
                "twilight angles must be within [-60, 60] degrees",
            )
        }
    }
}

/** A prayer instant in two honest layers: the astronomical fact and what you'll actually use. */
data class PrayerTiming(
    /** Computed without user offsets — the pure calculation output. */
    val raw: Instant?,
    /** Raw plus configured per-prayer offset (solver-rounded). */
    val adjusted: Instant?,
)

/**
 * Why the times look the way they do — reproducibility for a sacred domain.
 */
data class PrayerAudit(
    val conventionInfo: ConventionInfo?,
    val madhab: Madhab,
    val highLatitudeRule: HighLatitudeRule,
    val highLatitudeResolution: HighLatitudeResolution,
    val offsetsApplied: PrayerOffsets,
    val warnings: List<String>,
)

/**
 * Computed prayer timings for one civil date at one location, in absolute time.
 * Null entries are physically uncomputable (polar day/night) — never errors.
 */
data class PrayerTimesResult(
    val date: LocalDate,
    val fajr: PrayerTiming,
    val sunrise: PrayerTiming,
    /** Solar transit (true solar noon). */
    val dhuhr: PrayerTiming,
    val asr: PrayerTiming,
    /** Maghrib prayer time. */
    val maghrib: PrayerTiming,
    /** Astronomical sunset — independent display offset from maghrib (D3). */
    val sunset: PrayerTiming,
    /**
     * Isha prayer time. Note: the span from Isha until the next day's Fajr is
     * also the Witr (odd-numbered prayer) window — no separate computation.
     */
    val isha: PrayerTiming,
    /** Midpoint of (maghrib today, fajr tomorrow) — the Islamic midnight. */
    val midnight: Instant?,
    /** Midpoint of (sunset today, sunrise tomorrow). */
    val astronomicalMidnight: Instant?,
    /** Last third of the night between maghrib today and fajr tomorrow (Tahajjud threshold). */
    val lastThirdOfNight: Instant?,
    /** Sun reaches 4.5 degrees rising — end of Fajr / start of Duha (Ishraq). */
    val ishraq: Instant?,
    /** Start of the prohibited zawaal window: dhuhr minus 5 minutes ihtiyat. */
    val zawaalStart: Instant?,
    /** Dhuhr becomes obligatory: transit plus 1 minute ihtiyat. */
    val dhuhrEnters: Instant?,
    val isIntervalIsha: Boolean,
    val audit: PrayerAudit,
)

data class PrayerStatus(
    val current: Prayer?,
    val next: Prayer?,
    /** Start of the window [current] opened; null when no window covers [now]. */
    val currentStart: Instant?,
    val nextStart: Instant?,
    val now: Instant,
) {
    enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

    /** Time remaining until the next prayer begins. */
    fun countdown(): java.time.Duration? =
        nextStart?.let { java.time.Duration.between(now, it) }

    /** Elapsed fraction of the current prayer window; null when no window covers [now]. */
    fun progress(): Float? {
        val start = currentStart ?: return null
        val end = nextStart ?: return null
        if (end <= start) return null
        val elapsed = (now.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0L)
        return (elapsed.toDouble() / (end.toEpochMilli() - start.toEpochMilli()))
            .toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Tahajjud night-prayer window for the night following [date]'s maghrib.
 * Null when uncomputable. This window is simultaneously the Witr / qiyam
 * window — hosts need no separate witr computation.
 */
data class TahajjudWindow(
    /** Islamic midnight — midpoint of (maghrib today, fajr tomorrow). */
    val windowOpens: Instant,
    /** Last third of the night begins — the preferred Tahajjud time. */
    val lastThirdBegins: Instant,
    /** Fajr of the following day ends the window. */
    val windowCloses: Instant,
)

/** Capability entry point: request prayer facts, never calculator classes. */
object Prayer {

    /**
     * Compute all prayer-related timings for the civil [date] at [location].
     * Deterministic: same inputs always produce identical output.
     */
    fun times(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): PrayerTimesResult =
        PrayerCalculator.compute(location, date, config)

    /**
     * Timings for every civil date from [start] through [endInclusive] inclusive.
     * Convenience over [times] — one deterministic engine, batched for callers.
     */
    fun timesForRange(
        location: Location,
        start: LocalDate,
        endInclusive: LocalDate,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<PrayerTimesResult> =
        generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endInclusive) }
            .map { times(location, it, config) }
            .toList()

    /**
     * Whole civil month — convenience over [timesForRange].
     */
    fun month(location: Location, yearMonth: java.time.YearMonth, config: PrayerConfiguration = PrayerConfiguration()): List<PrayerTimesResult> =
        timesForRange(location, yearMonth.atDay(1), yearMonth.atEndOfMonth(), config)

    /**
     * Which obligatory prayer window covers [now], and what comes next.
     * [zoneId] selects the civil date [now] falls on at the location.
     *
     * Midnight semantics (explicit contract):
     * - Before today's Fajr (deep night): `current` = YESTERDAY's Isha — the Isha
     *   obligation spans midnight until Fajr. `next` = today's Fajr.
     * - After today's Isha (late evening): `current` = Isha; `next` = TOMORROW's Fajr.
     * - At high latitudes where a boundary is uncomputable, that entry is null.
     * The current window NEVER resets to "none" mid-day; there is always an
     * enclosing window whenever any time exists.
     */
    fun status(
        location: Location,
        now: Instant,
        zoneId: java.time.ZoneId,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): PrayerStatus = PrayerCalculator.status(location, now, zoneId, config)

    /**
     * Tahajjud window for the night beginning at [date]'s maghrib.
     * Null when any boundary is uncomputable (polar).
     */
    fun tahajjudWindow(
        location: Location,
        date: LocalDate,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): TahajjudWindow? {
        val today = times(location, date, config)
        val tomorrow = times(location, date.plusDays(1), config)
        val midnight = today.midnight ?: return null
        val lastThird = today.lastThirdOfNight ?: return null
        val fajrTomorrow = tomorrow.fajr.adjusted ?: return null
        return TahajjudWindow(midnight, lastThird, fajrTomorrow)
    }

    // ── Navigation: compact sequences around an anchor ──────────────────────

    /** One prayer instant in a navigation sequence. */
    data class Entry(val kind: PrayerStatus.Prayer, val instant: Instant)

    /**
     * Up to `before` prayers before, the covering/current one, and up to `after`
     * prayers after [now] — e.g. before=1, after=2 gives previous, current,
     * next, next+1. Spans midnight boundaries transparently.
     *
     * Edge contract: near uncomputable boundaries (polar) the result may contain
     * FEWER than `before + after + 1` entries — truncation is silent by design;
     * callers needing strict sizes should check `size`.
     */
    fun navigation(
        location: Location,
        now: Instant,
        zoneId: java.time.ZoneId,
        before: Int = 0,
        after: Int = 1,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<Entry> {
        validate(before >= 0 && after >= 0) {
            InvalidParameterException("before/after", "$before/$after", "must both be >= 0")
        }
        val civilToday = LocalDate.ofInstant(now, zoneId)

        fun dayOccurrences(date: LocalDate): List<Entry> {
            val t = times(location, date, config)
            return buildList {
                t.fajr.adjusted?.let { add(Entry(PrayerStatus.Prayer.FAJR, it)) }
                t.sunrise.adjusted?.let { add(Entry(PrayerStatus.Prayer.SUNRISE, it)) }
                t.dhuhrEnters?.let { add(Entry(PrayerStatus.Prayer.DHUHR, it)) }
                t.asr.adjusted?.let { add(Entry(PrayerStatus.Prayer.ASR, it)) }
                t.maghrib.adjusted?.let { add(Entry(PrayerStatus.Prayer.MAGHRIB, it)) }
                t.isha.adjusted?.let { add(Entry(PrayerStatus.Prayer.ISHA, it)) }
            }.sortedBy { it.instant }
        }

        // Grow the day-window only as far as the requested slice demands.
        val wanted = before + after + 1
        var radius = 1
        var all = emptyList<Entry>()
        var currentIdx = -1
        while (radius <= MAX_NAVIGATION_RADIUS_DAYS) {
            all = (-radius..radius).flatMap { off -> dayOccurrences(civilToday.plusDays(off.toLong())) }
                .sortedBy { it.instant }
            if (all.isEmpty()) return emptyList()
            // Current = last occurrence at-or-before now (an exact hit IS current).
            currentIdx = all.indexOfLast { it.instant <= now }
            if (currentIdx < 0) currentIdx = 0
            val satisfied = (currentIdx >= before || currentIdx == 0) &&
                (all.size - 1 - currentIdx >= after || currentIdx == all.size - 1)
            if (all.size >= wanted && satisfied) break
            radius++
        }

        val from = (currentIdx - before).coerceAtLeast(0)
        val to = (currentIdx + after).coerceAtMost(all.size - 1)
        if (from > to) return emptyList()
        return all.subList(from, to + 1)
    }

    private const val MAX_NAVIGATION_RADIUS_DAYS = 15

    /**
     * Current-first rotation: the in-progress (or most recently entered)
     * prayer plus [count] - 1 following occurrences after [now]. Convenience
     * over [navigation] with before=0 — the call behind "what's next" widgets
     * and swipe rotation. Spans midnight transparently: after today's Isha,
     * tomorrow's Fajr is next.
     */
    fun upcoming(
        location: Location,
        now: Instant,
        zoneId: java.time.ZoneId,
        count: Int = 5,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<Entry> {
        validate(count > 0) { InvalidParameterException("count", "$count", "must be > 0") }
        return navigation(location, now, zoneId, before = 0, after = count - 1, config)
    }

    // ── Observance statistics: pure functions over caller-supplied logs ────

    /** One logged prayer check-mark. Persistence lives entirely in the host. */
    data class PrayerLogRecord(
        val date: LocalDate,
        val kind: PrayerStatus.Prayer,
        val completed: Boolean,
    )

    /**
     * Streak/rate statistics computed over explicitly-passed logs.
     *
     * Day semantics (documented contract):
     * - Applicable prayers per day: FAJR, DHUHR, ASR, MAGHRIB, ISHA
     *   (SUNRISE is a boundary event and never affects day completeness).
     * - A day is COMPLETE when all applicable kinds are logged completed.
     * - A day within any [excusedRanges] range (travel/illness/menses — host
     *   decides) is EXCUSED: it neither extends nor breaks a streak.
     * - A day with any uncompleted obligatory record and not excused BREAKS.
     * - A day inside the logged span with NO records counts as incomplete
     *   (breaks) unless excused — silence is not success.
     * - Days outside the logged span are ignored entirely.
     *
     * No persistence, no Clock: the same inputs always yield the same output.
     */
    fun streakStats(
        records: List<PrayerLogRecord>,
        excusedRanges: List<ClosedRange<LocalDate>> = emptyList(),
        config: PrayerConfiguration = PrayerConfiguration(),
    ): StreakStats {
        validate(records.isNotEmpty()) {
            InvalidParameterException("records", "empty", "must not be empty")
        }
        val applicable = setOf(
            PrayerStatus.Prayer.FAJR,
            PrayerStatus.Prayer.DHUHR,
            PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB,
            PrayerStatus.Prayer.ISHA,
        )

        data class DayState(val complete: Boolean, val excused: Boolean)

        val byDate = records.groupBy { it.date }
        val firstDate = byDate.keys.min()
        val lastDate = byDate.keys.max()

        fun isExcused(date: LocalDate) = excusedRanges.any { date in it }

        // Classify every civil day in the logged span.
        val days = mutableListOf<DayState>()
        var d = firstDate
        while (!d.isAfter(lastDate)) {
            if (isExcused(d)) {
                days += DayState(complete = false, excused = true)
            } else {
                val recs = byDate[d].orEmpty().filter { it.kind in applicable }
                val complete = recs.isNotEmpty() &&
                    applicable.all { kind -> recs.any { it.kind == kind && it.completed } }
                days += DayState(complete = complete, excused = false)
            }
            d = d.plusDays(1)
        }

        // Longest run of complete-or-excused days; current = trailing such run
        // ending at the last logged date.
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
        for (day in days.reversed()) {
            if (day.complete || day.excused) current++ else break
        }

        // Per-prayer completion rates over non-excused days only.
        val rates = applicable.associateWith { kind ->
            val logged = records.filter { it.kind == kind && !isExcused(it.date) }
            if (logged.isEmpty()) {
                Double.NaN
            } else {
                logged.count { it.completed }.toDouble() / logged.size
            }
        }

        return StreakStats(
            currentStreakDays = current,
            longestStreakDays = longest,
            perPrayerCompletionRate = rates,
            spanDaysNonExcused = days.count { !it.excused },
            excusedDaysCount = days.count { it.excused },
        )
    }

    data class StreakStats(
        val currentStreakDays: Int,
        val longestStreakDays: Int,
        /** Fraction completed among non-excused logged days; NaN when never logged. */
        val perPrayerCompletionRate: Map<PrayerStatus.Prayer, Double>,
        /** Civil days within the logged span that were not excused (silent days included). */
    val spanDaysNonExcused: Int,
        val excusedDaysCount: Int,
    )

    // ── Windows: fiqh-relevant intervals as raw facts ───────────────────────

    /** Night-division strategy for Tahajjud/third-of-night computations. */
    enum class NightDivisionMethod { SUNSET_TO_FAJR, MAGHRIB_TO_FAJR }

    /** Prohibited (karahat) prayer windows with configurable margins. */
    data class ForbiddenWindows(
        /** From Fajr entry until sunrise. */
        val afterFajrUntilSunrise: ClosedRange<Instant>?,
        /** Zawaal: shortly before transit until Dhuhr enters. */
        val zawaal: ClosedRange<Instant>?,
        /** Late-afternoon prohibition until sunset (margin convention, see KDoc). */
        val beforeSunset: ClosedRange<Instant>?,
    )

    /**
     * Imsak — preparatory fast boundary. Convention-dependent: the common
     * practice is a fixed margin (often 10 minutes) BEFORE Fajr; it is a
     * configuration default, not a religiously universal constant.
     */
    fun imsak(
        location: Location,
        date: LocalDate,
        minutesBeforeFajr: Int = 10,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): Instant? = times(location, date, config).fajr.adjusted?.minusSeconds(minutesBeforeFajr * 60L)

    /** Duha window: opens at Ishraq (sun at +4.5°), closes when Dhuhr enters. Null when polar. */
    fun duhaWindow(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): ClosedRange<Instant>? {
        val t = times(location, date, config)
        val start = t.ishraq ?: return null
        return start..t.dhuhrEnters!!
    }

    /**
     * Karahat windows. Margins are documented CONVENTIONS, not universal fiqh:
     * - Fajr window: Fajr entry → sunrise (classical).
     * - Zawaal: transit − margin → Dhuhr entry (default 5 min ihtiyat).
     * - Late afternoon: sun-yellowing onset approximated as [sunset − margin];
       default margin 20 minutes; schools differ — configure explicitly.
     */
    fun forbiddenWindows(
        location: Location,
        date: LocalDate,
        lateAfternoonMarginMinutes: Int = 20,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): ForbiddenWindows {
        val t = times(location, date, config)
        val w1 = if (t.fajr.adjusted != null && t.sunrise.adjusted != null && t.fajr.adjusted < t.sunrise.adjusted) {
            t.fajr.adjusted!!..t.sunrise.adjusted!!
        } else null
        val w2 = if (t.dhuhrEnters != null && t.zawaalStart != null) t.zawaalStart!!..t.dhuhrEnters!! else null
        val w3 = t.sunset.adjusted?.let { sunset ->
            val start = sunset.minusSeconds(lateAfternoonMarginMinutes * 60L)
            start..sunset
        }
        return ForbiddenWindows(w1, w2, w3)
    }

    /** Objective fasting-day facts for [date]: imsak, fast start (Fajr), fast end (Maghrib). */
    data class FastingFacts(val imsak: Instant?, val fastStart: Instant?, val fastEnd: Instant?)

    fun fastingFacts(
        location: Location,
        date: LocalDate,
        imsakMinutesBeforeFajr: Int = 10,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): FastingFacts {
        val t = times(location, date, config)
        return FastingFacts(
            imsak = imsak(location, date, imsakMinutesBeforeFajr, config),
            fastStart = t.fajr.adjusted,
            fastEnd = t.maghrib.adjusted,
        )
    }

    /**
     * Travel (qasr) objective facts. Thresholds are DOCUMENTED CONVENTIONS:
     * the widely-used majority practice is 48 miles (~77.25 km); Hanafi
     * practice uses a longer three-day-journey measure — supply your own
     * value rather than assuming a preset. Times are never mutated.
     */
    data class TravelFacts(
        val qasrEligibleByDistance: Boolean?,
        val shortenable: Set<PrayerStatus.Prayer>,
        /** Combination anchor: Dhuhr joins Asr at this instant. */
        val dhuhrJoinsAsrAt: Instant?,
        /** Combination anchor: Maghrib joins Isha at this instant. */
        val maghribJoinsIshaAt: Instant?,
    )

    fun travelFacts(
        location: Location,
        date: LocalDate,
        travelledDistanceKm: Double,
        distanceThresholdKm: Double? = null,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): TravelFacts {
        val threshold = distanceThresholdKm ?: DEFAULT_MAJORITY_THRESHOLD_KM
        val t = times(location, date, config)
        return TravelFacts(
            qasrEligibleByDistance = travelledDistanceKm >= threshold,
            shortenable = setOf(PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR, PrayerStatus.Prayer.ISHA),
            dhuhrJoinsAsrAt = t.asr.adjusted,
            maghribJoinsIshaAt = t.isha.adjusted,
        )
    }

    private const val DEFAULT_MAJORITY_THRESHOLD_KM = 77.25

    /**
     * Objective fasting facts for every civil day of [yearMonth] — convenience
     * over [fastingFacts] for Ramadan/seasonal screens. List order is day order.
     */
    fun fastingFactsForMonth(
        location: Location,
        yearMonth: java.time.YearMonth,
        imsakMinutesBeforeFajr: Int = 10,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<FastingFacts> =
        generateSequence(yearMonth.atDay(1)) { it.plusDays(1) }
            .takeWhile { java.time.YearMonth.from(it) == yearMonth }
            .map { fastingFacts(location, it, imsakMinutesBeforeFajr, config) }
            .toList()

    // ── Night divisions ─────────────────────────────────────────────────────

    /**
     * The night following [date]'s sunset split into its fiqh divisions.
     * Null when either boundary is uncomputable (polar).
     */
    data class NightDivisions(
        /** Night begins: maghrib of [date]. */
        val nightStarts: Instant,
        /** First third of the night begins. */
        val firstThirdBegins: Instant,
        /** Islamic midnight — midpoint of the night (matches tahajjudWindow). */
        val midpoint: Instant,
        /** Last third of the night begins — the preferred Tahajjud time. */
        val lastThirdBegins: Instant,
        /** Night ends: fajr of the following day. */
        val nightEnds: Instant,
    )

    /**
     * Full night-division set for the night beginning at [date]'s maghrib.
     * `midpoint` and `lastThirdBegins` reuse the adhan2-derived values when
     * available; the first third is the exact one-third point of the night span.
     */
    fun nightDivisions(
        location: Location,
        date: LocalDate,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): NightDivisions? {
        val today = times(location, date, config)
        val tomorrow = times(location, date.plusDays(1), config)
        val start = today.maghrib.adjusted ?: return null
        val end = tomorrow.fajr.adjusted ?: return null
        val durMs = end.toEpochMilli() - start.toEpochMilli()
        if (durMs <= 0) return null
        return NightDivisions(
            nightStarts = start,
            firstThirdBegins = Instant.ofEpochMilli(start.toEpochMilli() + durMs / 3),
            midpoint = today.midnight ?: Instant.ofEpochMilli(start.toEpochMilli() + durMs / 2),
            lastThirdBegins = today.lastThirdOfNight
                ?: Instant.ofEpochMilli(start.toEpochMilli() + 2 * durMs / 3),
            nightEnds = end,
        )
    }

    // ── Next-of-kind & Jumu'ah ──────────────────────────────────────────────

    /**
     * The next occurrence of a specific prayer [kind] strictly after [after].
     * Returns null only when no such occurrence exists within the navigation
     * horizon (extreme polar cases).
     */
    fun nextOccurrenceOf(
        kind: PrayerStatus.Prayer,
        location: Location,
        after: Instant,
        zoneId: java.time.ZoneId,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): Entry? =
        navigation(location, after, zoneId, before = 0, after = 35, config)
            .firstOrNull { it.kind == kind }

    /** The next Friday congregational prayer at or after an instant. */
    data class Jumuah(val date: LocalDate, val dhuhr: Instant)

    /**
     * Next Jumu'ah (Friday dhuhr) strictly after [after]. Scans consecutive
     * Fridays in [zoneId]; dhuhr falls back to the transit-anchored entry when
     * the adjusted time is uncomputable.
     */
    fun nextJumuah(
        location: Location,
        after: Instant,
        zoneId: java.time.ZoneId,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): Jumuah? {
        var d = LocalDate.ofInstant(after, zoneId)
        while (d.dayOfWeek != java.time.DayOfWeek.FRIDAY) d = d.plusDays(1)
        repeat(MAX_JUMUAH_SCAN_WEEKS) {
            val t = times(location, d, config)
            val dhuhr = t.dhuhr.adjusted ?: t.dhuhrEnters
            if (dhuhr != null && dhuhr > after) return Jumuah(d, dhuhr)
            d = d.plusDays(7)
        }
        return null
    }

    private const val MAX_JUMUAH_SCAN_WEEKS = 4

    // ── Occasions: category-labeled occurrences for a civil day ────────────

    enum class OccasionCategory { OBLIGATORY, VOLUNTARY, BOUNDARY_EVENT }

    /** One labeled worship-time occurrence. */
    data class Occurrence(
        val label: String,
        val kind: PrayerStatus.Prayer?,
        val instant: Instant?,
        val category: OccasionCategory,
    )

    /**
     * Base occasions for [date]: the five obligatory prayers, sunrise as a
     * boundary event, and voluntary Duha/Tahajjud references. Eid/Jumu'ah
     * enrichment is composed at the facade with the calendar capability.
     * Restrict with [categories] (default: all); an empty set yields an
     * empty list.
     */
    fun occasionsOn(
        location: Location,
        date: LocalDate,
        categories: Set<OccasionCategory> = OccasionCategory.entries.toSet(),
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<Occurrence> {
        val t = times(location, date, config)
        return buildList {
            t.fajr.adjusted?.let { add(Occurrence("Fajr", PrayerStatus.Prayer.FAJR, it, OccasionCategory.OBLIGATORY)) }
            t.sunrise.adjusted?.let { add(Occurrence("Sunrise", PrayerStatus.Prayer.SUNRISE, it, OccasionCategory.BOUNDARY_EVENT)) }
            t.dhuhrEnters?.let { add(Occurrence("Dhuhr", PrayerStatus.Prayer.DHUHR, it, OccasionCategory.OBLIGATORY)) }
            t.asr.adjusted?.let { add(Occurrence("Asr", PrayerStatus.Prayer.ASR, it, OccasionCategory.OBLIGATORY)) }
            t.maghrib.adjusted?.let { add(Occurrence("Maghrib", PrayerStatus.Prayer.MAGHRIB, it, OccasionCategory.OBLIGATORY)) }
            t.isha.adjusted?.let { add(Occurrence("Isha", PrayerStatus.Prayer.ISHA, it, OccasionCategory.OBLIGATORY)) }
            t.ishraq?.let { add(Occurrence("Duha (voluntary)", null, it, OccasionCategory.VOLUNTARY)) }
            t.lastThirdOfNight?.let { add(Occurrence("Tahajjud preferred from", null, it, OccasionCategory.VOLUNTARY)) }
        }.filter { it.category in categories }.sortedBy { it.instant }
    }
}

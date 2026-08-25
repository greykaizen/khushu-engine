package com.khushu.engine.prayer

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
        require(ishaIntervalMinutes >= 0) { "ishaIntervalMinutes must be >= 0" }
        require(fajrAngle in -60.0..60.0 && ishaAngle in -60.0..60.0) {
            "twilight angles must be within [-60, 60] degrees"
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

/** Tahajjud night-prayer window for the night following [date]'s maghrib. Null when uncomputable. */
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
     */
    fun navigation(
        location: Location,
        now: Instant,
        zoneId: java.time.ZoneId,
        before: Int = 0,
        after: Int = 1,
        config: PrayerConfiguration = PrayerConfiguration(),
    ): List<Entry> {
        require(before >= 0 && after >= 0)
        val civilToday = LocalDate.ofInstant(now, zoneId)

        // Collect occurrences across a window of days around `now`.
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

        val all = (-2..2).flatMap { off -> dayOccurrences(civilToday.plusDays(off.toLong())) }
            .sortedBy { it.instant }
        if (all.isEmpty()) return emptyList()

        // Index of the last occurrence at-or-before now; -1 when now precedes all.
        var currentIdx = all.indexOfLast { it.instant <= now }
        if (currentIdx < 0) currentIdx = 0 else if (all[currentIdx].instant < now && currentIdx + 1 < all.size && all[currentIdx].instant.toEpochMilli() == now.toEpochMilli()) currentIdx += 1

        val from = (currentIdx - before).coerceAtLeast(0)
        val to = (currentIdx + after).coerceAtMost(all.size - 1)
        if (from > to) return emptyList()
        return all.subList(from, to + 1)
    }

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
     */
    fun occasionsOn(
        location: Location,
        date: LocalDate,
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
        }.sortedBy { it.instant }
    }
}

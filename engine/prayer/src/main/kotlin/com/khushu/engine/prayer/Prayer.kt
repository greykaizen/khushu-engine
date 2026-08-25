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
}

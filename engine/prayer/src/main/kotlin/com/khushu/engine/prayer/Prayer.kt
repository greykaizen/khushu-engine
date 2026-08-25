package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.internal.PrayerCalculator
import java.time.Instant
import java.time.LocalDate

/** Asr shadow-length school. MALIKI and HANBALI follow the standard (Shafi'i) factor. */
enum class Madhab { SHAFII, HANAFI, MALIKI, HANBALI }

/** Fajr/Isha angle presets. CUSTOM uses [PrayerParams.fajrAngle]/[PrayerParams.ishaAngle]. */
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

data class PrayerParams(
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
) {
    init {
        require(ishaIntervalMinutes >= 0) { "ishaIntervalMinutes must be >= 0" }
        require(fajrAngle in -60.0..60.0 && ishaAngle in -60.0..60.0) {
            "twilight angles must be within [-60, 60] degrees"
        }
    }
}

/**
 * Computed prayer timings for one civil date at one location, in absolute time.
 * Null entries are physically uncomputable (polar day/night) — never errors.
 */
data class PrayerTimesResult(
    val date: LocalDate,
    val fajr: Instant?,
    val sunrise: Instant?,
    /** Solar transit (true solar noon). */
    val dhuhr: Instant?,
    val asr: Instant?,
    /** Maghrib prayer time (adhan2's maghrib anchor + its offset). */
    val maghrib: Instant?,
    /** Astronomical sunset (+ its own independent offset). */
    val sunset: Instant?,
    val isha: Instant?,
    /** Midpoint of (maghrib today, fajr tomorrow) — the Islamic midnight. */
    val midnight: Instant?,
    /** Midpoint of (sunset today, sunrise tomorrow). */
    val astronomicalMidnight: Instant?,
    /** Last third of the night between maghrib today and fajr tomorrow (Tahajjud window). */
    val lastThirdOfNight: Instant?,
    /** Sun reaches 4.5 degrees rising — end of Fajr / start of Duha (Ishraq). */
    val ishraq: Instant?,
    /** Start of the prohibited zawaal window: dhuhr minus 5 minutes ihtiyat. */
    val zawaalStart: Instant?,
    /** Dhuhr becomes obligatory: transit plus 1 minute ihtiyat. */
    val dhuhrEnters: Instant?,
    /** True when Isha was computed as a fixed interval after maghrib. */
    val isIntervalIsha: Boolean,
    /** True when polar day/night made some timings uncomputable. */
    val polarAnomaly: Boolean,
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
    fun times(location: Location, date: LocalDate, params: PrayerParams = PrayerParams()): PrayerTimesResult =
        PrayerCalculator.compute(location, date, params)

    /**
     * Which obligatory prayer window covers [now], and what comes next.
     * [zoneId] selects the civil date [now] falls on at the location.
     */
    fun status(
        location: Location,
        now: Instant,
        zoneId: java.time.ZoneId,
        params: PrayerParams = PrayerParams(),
    ): PrayerStatus = PrayerCalculator.status(location, now, zoneId, params)

    /**
     * Tahajjud window for the night beginning at [date]'s maghrib.
     * Null when any boundary is uncomputable (polar).
     */
    fun tahajjudWindow(
        location: Location,
        date: LocalDate,
        params: PrayerParams = PrayerParams(),
    ): TahajjudWindow? {
        val today = times(location, date, params)
        val tomorrow = times(location, date.plusDays(1), params)
        val midnight = today.midnight ?: return null
        val lastThird = today.lastThirdOfNight ?: return null
        val fajrTomorrow = tomorrow.fajr ?: return null
        return TahajjudWindow(midnight, lastThird, fajrTomorrow)
    }
}

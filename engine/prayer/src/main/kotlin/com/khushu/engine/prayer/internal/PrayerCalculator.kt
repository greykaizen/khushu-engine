@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.khushu.engine.prayer.internal

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Convention
import com.khushu.engine.prayer.HighLatitudeRule as EngineHighLatitudeRule
import com.khushu.engine.prayer.Madhab as EngineMadhab
import com.khushu.engine.prayer.PrayerParams
import com.khushu.engine.prayer.PrayerStatus
import com.khushu.engine.prayer.PrayerTimesResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun kotlin.time.Instant.toJavaInstant(): Instant = Instant.ofEpochMilli(this.toEpochMilliseconds())

@OptIn(kotlin.time.ExperimentalTime::class)
internal object PrayerCalculator {

    fun compute(location: Location, date: LocalDate, params: PrayerParams): PrayerTimesResult {
        val adhanParams = toAdhanParams(params)
        val coords = Coordinates(location.latitude.degrees, location.longitude.degrees)
        val dc = DateComponents(date.year, date.monthValue, date.dayOfMonth)

        val primary = runCatching { PrayerTimes(coords, dc, adhanParams) }.getOrNull()
        val nextDay = runCatching {
            val nd = date.plusDays(1)
            PrayerTimes(coords, DateComponents(nd.year, nd.monthValue, nd.dayOfMonth), adhanParams)
        }.getOrNull()

        val sunnah = if (primary != null) runCatching { SunnahTimes(primary) }.getOrNull() else null

        // Astronomical midnight needs sunset today and sunrise tomorrow.
        val astronomicalMidnight: Instant? =
            if (primary?.maghrib != null && nextDay?.sunrise != null) {
                roundedMiddle(primary.maghrib!!.toEpochMilliseconds(), nextDay.sunrise!!.toEpochMilliseconds())
                    .let(Instant::ofEpochMilli)
            } else {
                null
            }

        val transitMs = SolarGeometry.transitEpochMs(date, location.longitude.degrees)
        // Anchor derived fiqh windows on the best available transit:
        // adhan2's (minute-rounded, golden-locked) when present, exact NOAA otherwise.
        val anchorTransitMs = primary?.dhuhr?.toEpochMilliseconds()
            ?.let { it - params.offsets.dhuhr * 60_000L } // undo the internal adjustment
            ?: transitMs

        val ishraq = SolarGeometry.morningAltitudeCrossingEpochMs(
            date, location.latitude.degrees, location.longitude.degrees, ISHRAQ_ALTITUDE_DEG,
        )?.let(Instant::ofEpochMilli)

        val maghribBaseMs = primary?.maghrib?.toEpochMilliseconds()

        return PrayerTimesResult(
            date = date,
            // adhan2 applies prayerAdjustments internally before rounding — do not re-apply.
            fajr = primary?.fajr?.toJavaInstant(),
            sunrise = primary?.sunrise?.toJavaInstant(),
            dhuhr = Instant.ofEpochMilli(applyOffset(anchorTransitMs, params.offsets.dhuhr)),
            asr = primary?.asr?.toJavaInstant(),
            maghrib = maghribBaseMs?.let(Instant::ofEpochMilli),
            // Sunset anchors on the UNADJUSTED maghrib instant so display offsets stay independent.
            sunset = maghribBaseMs?.let {
                Instant.ofEpochMilli(applyOffset(it - params.offsets.maghrib * 60_000L, params.offsets.sunset))
            },
            isha = primary?.isha?.toJavaInstant(),
            midnight = sunnah?.middleOfTheNight?.toEpochMilliseconds()?.let(Instant::ofEpochMilli),
            astronomicalMidnight = astronomicalMidnight,
            lastThirdOfNight = sunnah?.lastThirdOfTheNight?.toEpochMilliseconds()?.let(Instant::ofEpochMilli),
            ishraq = ishraq,
            zawaalStart = Instant.ofEpochMilli(anchorTransitMs - ZAWAAL_MARGIN_MS),
            dhuhrEnters = Instant.ofEpochMilli(anchorTransitMs + DHUHR_IHTIYAT_MS),
            isIntervalIsha = params.ishaIntervalMinutes > 0,
            // Polar day (adhan2 throws), polar night (adhan2 returns unphysical
            // times, e.g. Asr before Dhuhr) and partial failures all count as
            // anomalies. Extremes are grounded in real solar geometry.
            polarAnomaly = primary == null || nextDay == null ||
                primary.sunrise == null || primary.maghrib == null ||
                run {
                    val (minAltDeg, maxAltDeg) = SolarGeometry.sunAltitudeExtremesDeg(
                        date, location.latitude.degrees,
                    )
                    val midnightSun = minAltDeg >= 0.0
                    val polarNight = maxAltDeg <= 0.0
                    // Asr is degenerate when its required altitude sits within
                    // rounding noise of the day's maximum solar altitude.
                    val shadowFactor = if (params.madhab == EngineMadhab.HANAFI) 2.0 else 1.0
                    val maxAltRad = Math.toRadians(maxAltDeg)
                    val asrAltRad = Math.atan(1.0 / (shadowFactor + 1.0 / Math.tan(maxAltRad)))
                    val asrDegenerate = maxAltDeg > 0.0 &&
                        (maxAltRad - asrAltRad) < Math.toRadians(ASR_DEGENERACY_TOLERANCE_DEG)
                    midnightSun || polarNight || asrDegenerate
                },
        )
    }

    fun status(
        location: Location,
        now: Instant,
        zoneId: ZoneId,
        params: PrayerParams,
    ): PrayerStatus {
        val today = compute(location, LocalDate.ofInstant(now, zoneId), params)
        val windows: List<Pair<PrayerStatus.Prayer, Long>> = buildList {
            today.fajr?.let { add(PrayerStatus.Prayer.FAJR to it.toEpochMilli()) }
            today.sunrise?.let { add(PrayerStatus.Prayer.SUNRISE to it.toEpochMilli()) }
            today.dhuhrEnters?.let { add(PrayerStatus.Prayer.DHUHR to it.toEpochMilli()) }
            today.asr?.let { add(PrayerStatus.Prayer.ASR to it.toEpochMilli()) }
            today.maghrib?.let { add(PrayerStatus.Prayer.MAGHRIB to it.toEpochMilli()) }
            today.isha?.let { add(PrayerStatus.Prayer.ISHA to it.toEpochMilli()) }
        }.sortedBy { it.second }

        val nowMs = now.toEpochMilli()
        val currentIdx = windows.indexOfLast { it.second <= nowMs }
        if (currentIdx < 0) {
            // Before Fajr: the current window belongs to yesterday's Isha.
            val civilToday = LocalDate.ofInstant(now, zoneId)
            val yesterday = compute(location, civilToday.minusDays(1), params)
            return PrayerStatus(
                current = if (yesterday.isha != null) PrayerStatus.Prayer.ISHA else null,
                next = windows.firstOrNull()?.first,
                currentStart = yesterday.isha,
                nextStart = windows.firstOrNull()?.second?.let(Instant::ofEpochMilli),
                now = now,
            )
        }
        val (currentPrayer, currentStartMs) = windows[currentIdx]
        val nextEntry = windows.getOrNull(currentIdx + 1)
        var nextPrayer: PrayerStatus.Prayer? = nextEntry?.first
        var nextStart: Instant? = nextEntry?.second?.let(Instant::ofEpochMilli)
        if (nextEntry == null) {
            // After Isha today: the next prayer is tomorrow's Fajr.
            val tomorrow = compute(location, LocalDate.ofInstant(now, zoneId).plusDays(1), params)
            nextPrayer = if (tomorrow.fajr != null) PrayerStatus.Prayer.FAJR else null
            nextStart = tomorrow.fajr
        }
        return PrayerStatus(
            current = currentPrayer,
            next = nextPrayer,
            currentStart = Instant.ofEpochMilli(currentStartMs),
            nextStart = nextStart,
            now = now,
        )
    }

    private fun applyOffset(baseMs: Long, offsetMinutes: Int): Long =
        baseMs + offsetMinutes * 60_000L

    /** Whole-minute midpoint, matching adhan2's rounding convention. */
    private fun roundedMiddle(aMs: Long, bMs: Long): Long {
        val middle = (aMs + bMs) / 2.0
        val minuteMs = 60_000.0
        return Math.round(middle / minuteMs).toLong() * 60_000L
    }

    private fun toAdhanParams(params: PrayerParams): CalculationParameters {
        val base = when (params.convention) {
            Convention.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            Convention.ISNA -> CalculationMethod.NORTH_AMERICA
            Convention.EGYPTIAN -> CalculationMethod.EGYPTIAN
            Convention.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
            Convention.KARACHI -> CalculationMethod.KARACHI
            Convention.DUBAI -> CalculationMethod.DUBAI
            Convention.KUWAIT -> CalculationMethod.KUWAIT
            Convention.QATAR -> CalculationMethod.QATAR
            Convention.SINGAPORE -> CalculationMethod.SINGAPORE
            Convention.TURKEY -> CalculationMethod.TURKEY
            Convention.MOON_SIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE
            Convention.CUSTOM -> CalculationMethod.OTHER
        }.parameters

        val angles = if (params.convention == Convention.CUSTOM) {
            base.copy(fajrAngle = params.fajrAngle, ishaAngle = params.ishaAngle)
        } else {
            base
        }

        return angles.copy(
            madhab = when (params.madhab) {
                EngineMadhab.HANAFI -> Madhab.HANAFI
                EngineMadhab.SHAFII, EngineMadhab.MALIKI, EngineMadhab.HANBALI -> Madhab.SHAFI
            },
            highLatitudeRule = when (params.highLatitudeRule) {
                EngineHighLatitudeRule.MIDDLE_OF_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
                EngineHighLatitudeRule.SEVENTH_OF_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
                EngineHighLatitudeRule.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
            },
            prayerAdjustments = PrayerAdjustments(
                params.offsets.fajr,
                params.offsets.sunrise,
                params.offsets.dhuhr,
                params.offsets.asr,
                params.offsets.maghrib,
                params.offsets.isha,
            ),
            ishaInterval = params.ishaIntervalMinutes,
        )
    }

    private const val ISHRAQ_ALTITUDE_DEG = 4.5
    private const val ZAWAAL_MARGIN_MS = 5L * 60_000
    private const val DHUHR_IHTIYAT_MS = 1L * 60_000
    private const val ASR_DEGENERACY_TOLERANCE_DEG = 1.0
}

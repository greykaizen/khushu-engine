@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.khushu.engine.prayer.internal

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab as AdhanMadhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Convention
import com.khushu.engine.prayer.ConventionInfo
import com.khushu.engine.prayer.HighLatitudeResolution
import com.khushu.engine.prayer.HighLatitudeRule as EngineHighLatitudeRule
import com.khushu.engine.prayer.Madhab as EngineMadhab
import com.khushu.engine.prayer.PrayerAudit
import com.khushu.engine.prayer.PrayerConfiguration
import com.khushu.engine.prayer.PrayerStatus
import com.khushu.engine.prayer.PrayerTiming
import com.khushu.engine.prayer.PrayerTimesResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun kotlin.time.Instant.toJavaInstant(): Instant = Instant.ofEpochMilli(this.toEpochMilliseconds())

internal object PrayerCalculator {

    fun compute(location: Location, date: LocalDate, config: PrayerConfiguration): PrayerTimesResult {
        val adhanParams = toAdhanParams(config)
        // Raw pass: identical configuration minus USER offsets — the pure output.
        val rawParams = adhanParams.copy(prayerAdjustments = PrayerAdjustments())
        val coords = Coordinates(location.latitude.degrees, location.longitude.degrees)
        val dc = DateComponents(date.year, date.monthValue, date.dayOfMonth)

        val adjustedDay = runCatching { PrayerTimes(coords, dc, adhanParams) }.getOrNull()
        val rawDay = runCatching { PrayerTimes(coords, dc, rawParams) }.getOrNull()
        val nextAdjusted = runCatching {
            val nd = date.plusDays(1)
            PrayerTimes(coords, DateComponents(nd.year, nd.monthValue, nd.dayOfMonth), adhanParams)
        }.getOrNull()

        val sunnah = if (adjustedDay != null) runCatching { SunnahTimes(adjustedDay) }.getOrNull() else null

        // Astronomical midnight needs sunset today and sunrise tomorrow.
        val astronomicalMidnight: Instant? =
            if (adjustedDay?.maghrib != null && nextAdjusted?.sunrise != null) {
                roundedMiddle(adjustedDay.maghrib!!.toEpochMilliseconds(), nextAdjusted.sunrise!!.toEpochMilliseconds())
                    .let(Instant::ofEpochMilli)
            } else {
                null
            }

        // Transit anchor for derived fiqh windows: best available transit,
        // exact NOAA geometry when adhan2 refused to compute (polar).
        val anchorTransitMs = rawDay?.dhuhr?.toEpochMilliseconds()
            ?: SolarGeometry.transitEpochMs(date, location.longitude.degrees)

        val ishraq = SolarGeometry.morningAltitudeCrossingEpochMs(
            date, location.latitude.degrees, location.longitude.degrees, ISHRAQ_ALTITUDE_DEG,
        )?.let(Instant::ofEpochMilli)

        fun timing(raw: Instant?, adjusted: Instant?, sunsetOffsetFallbackMs: Long? = null): PrayerTiming =
            PrayerTiming(
                raw = raw,
                adjusted = adjusted ?: sunsetOffsetFallbackMs?.let { Instant.ofEpochMilli(it) },
            )

        val maghribRaw = rawDay?.maghrib?.toJavaInstant()
        val maghribAdj = adjustedDay?.maghrib?.toJavaInstant()
        // Sunset anchors on the raw maghrib instant so display offsets stay independent (D3).
        val sunsetAdjusted = maghribRaw?.let {
            Instant.ofEpochMilli(applyOffset(it.toEpochMilli(), config.offsets.sunset))
        }

        // ANTI_TRANSIT_FAJR (v1.17): when persistent twilight prevents the
        // Fajr depression angle from being reached (the sun's minimum
        // altitude stays above the convention's Fajr angle), substitute the
        // solar midnight (anti-transit) time per the Muwaqqit-documented
        // position (docs/aqrab-al-ayyam-review.md). Normal-latitude dates
        // are unaffected — the angle is reachable and normal computation
        // applies.
        val (minAlt, _) = SolarGeometry.sunAltitudeExtremesDeg(date, location.latitude.degrees)
        val fajrAngleDeg = -(adhanParams.fajrAngle) // convention's angle as depression (e.g., -18.0)
        val persistentTwilightForFajr = minAlt > fajrAngleDeg

        val antiTransitFajr: Instant? = if (
            config.highLatitudeRule == EngineHighLatitudeRule.ANTI_TRANSIT_FAJR &&
            persistentTwilightForFajr
        ) {
            // Anti-transit of the night ending at this date's sunrise —
            // the transit of the PREVIOUS day + 12h.
            val prevDay = date.minusDays(1)
            Instant.ofEpochMilli(SolarGeometry.antiTransitEpochMs(prevDay, location.longitude.degrees))
        } else {
            null
        }

        val warnings = buildList {
            if (anchorTransitMs == SolarGeometry.transitEpochMs(date, location.longitude.degrees) && adjustedDay == null) {
                add("polar day/night: solar-noon-derived windows computed via NOAA interim geometry (see divergences D1/D2)")
            }
            if (antiTransitFajr != null) {
                add("fajr: anti-transit substitution (persistent twilight) — Muwaqqit-documented position, see docs/aqrab-al-ayyam-review.md")
            }
        }

        // Anti-transit Fajr timing: raw and adjusted both carry the substituted
        // value so display offsets apply naturally on top.
        val fajrTiming = if (antiTransitFajr != null) {
            val adjustedAt = Instant.ofEpochMilli(
                applyOffset(antiTransitFajr.toEpochMilli(), config.offsets.fajr)
            )
            PrayerTiming(raw = antiTransitFajr, adjusted = adjustedAt)
        } else {
            timing(rawDay?.fajr?.toJavaInstant(), adjustedDay?.fajr?.toJavaInstant())
        }

        return PrayerTimesResult(
            date = date,
            fajr = fajrTiming,
            sunrise = timing(rawDay?.sunrise?.toJavaInstant(), adjustedDay?.sunrise?.toJavaInstant()),
            dhuhr = timing(
                rawDay?.dhuhr?.toJavaInstant(),
                adjustedDay?.dhuhr?.toJavaInstant(),
                sunsetOffsetFallbackMs = applyOffset(anchorTransitMs, config.offsets.dhuhr),
            ),
            asr = timing(rawDay?.asr?.toJavaInstant(), adjustedDay?.asr?.toJavaInstant()),
            maghrib = timing(maghribRaw, maghribAdj),
            sunset = PrayerTiming(
                raw = maghribRaw,
                adjusted = sunsetAdjusted,
            ),
            isha = timing(rawDay?.isha?.toJavaInstant(), adjustedDay?.isha?.toJavaInstant()),
            midnight = sunnah?.middleOfTheNight?.toEpochMilliseconds()?.let(Instant::ofEpochMilli),
            astronomicalMidnight = astronomicalMidnight,
            lastThirdOfNight = sunnah?.lastThirdOfTheNight?.toEpochMilliseconds()?.let(Instant::ofEpochMilli),
            ishraq = ishraq,
            zawaalStart = Instant.ofEpochMilli(anchorTransitMs - ZAWAAL_MARGIN_MS),
            dhuhrEnters = Instant.ofEpochMilli(anchorTransitMs + DHUHR_IHTIYAT_MS),
            isIntervalIsha = config.ishaIntervalMinutes > 0,
            audit = PrayerAudit(
                conventionInfo = ConventionMetadata.info(config.convention),
                madhab = config.madhab,
                highLatitudeRule = config.highLatitudeRule,
                highLatitudeResolution = resolution(config, adjustedDay, date, location),
                offsetsApplied = config.offsets,
                warnings = warnings,
            ),
        )
    }

    private fun resolution(
        config: PrayerConfiguration,
        day: PrayerTimes?,
        date: LocalDate,
        location: Location,
    ): HighLatitudeResolution {
        val fajrNull = day?.fajr == null
        val ishaNull = day?.isha == null
        val (minAlt, maxAlt) = SolarGeometry.sunAltitudeExtremesDeg(date, location.latitude.degrees)
        val degenerate = minAlt >= 0.0 || maxAlt <= 0.0 || asrDegenerate(config, maxAlt)

        // ANTI_TRANSIT_FAJR: resolution depends on whether persistent twilight
        // is actually happening (sun's min altitude above the Fajr angle)
        if (config.highLatitudeRule == EngineHighLatitudeRule.ANTI_TRANSIT_FAJR) {
            val fajrAngleDeg = -(config.fajrAngle)
            return if (minAlt > fajrAngleDeg || (degenerate && fajrNull)) {
                HighLatitudeResolution.RESOLVED_BY_ANTI_TRANSIT
            } else {
                HighLatitudeResolution.COMPUTED_WITH_REQUESTED_RULE
            }
        }

        return if (day == null || fajrNull || ishaNull || degenerate) {
            HighLatitudeResolution.UNAVAILABLE
        } else {
            when (config.highLatitudeRule) {
                EngineHighLatitudeRule.MIDDLE_OF_NIGHT -> HighLatitudeResolution.RESOLVED_BY_MIDDLE_OF_NIGHT
                EngineHighLatitudeRule.SEVENTH_OF_NIGHT -> HighLatitudeResolution.RESOLVED_BY_SEVENTH_OF_NIGHT
                EngineHighLatitudeRule.TWILIGHT_ANGLE -> HighLatitudeResolution.RESOLVED_BY_TWILIGHT_ANGLE
            }
        }
    }

    private fun asrDegenerate(config: PrayerConfiguration, maxAltDeg: Double): Boolean {
        val shadowFactor = if (config.madhab == EngineMadhab.HANAFI) 2.0 else 1.0
        val maxAltRad = Math.toRadians(maxAltDeg)
        val asrAltRad = Math.atan(1.0 / (shadowFactor + 1.0 / Math.tan(maxAltRad)))
        return maxAltDeg > 0.0 && (maxAltRad - asrAltRad) < Math.toRadians(ASR_DEGENERACY_TOLERANCE_DEG)
    }

    fun status(
        location: Location,
        now: Instant,
        zoneId: ZoneId,
        params: PrayerConfiguration,
    ): PrayerStatus {
        val today = compute(location, LocalDate.ofInstant(now, zoneId), params)
        val windows: List<Pair<PrayerStatus.Prayer, Long>> = buildList {
            today.fajr.adjusted?.let { add(PrayerStatus.Prayer.FAJR to it.toEpochMilli()) }
            today.sunrise.adjusted?.let { add(PrayerStatus.Prayer.SUNRISE to it.toEpochMilli()) }
            today.dhuhrEnters?.let { add(PrayerStatus.Prayer.DHUHR to it.toEpochMilli()) }
            today.asr.adjusted?.let { add(PrayerStatus.Prayer.ASR to it.toEpochMilli()) }
            today.maghrib.adjusted?.let { add(PrayerStatus.Prayer.MAGHRIB to it.toEpochMilli()) }
            today.isha.adjusted?.let { add(PrayerStatus.Prayer.ISHA to it.toEpochMilli()) }
        }.sortedBy { it.second }

        val nowMs = now.toEpochMilli()
        val currentIdx = windows.indexOfLast { it.second <= nowMs }
        if (currentIdx < 0) {
            // Before Fajr: the current window belongs to yesterday's Isha.
            val civilToday = LocalDate.ofInstant(now, zoneId)
            val yesterday = compute(location, civilToday.minusDays(1), params)
            return PrayerStatus(
                current = if (yesterday.isha.adjusted != null) PrayerStatus.Prayer.ISHA else null,
                next = windows.firstOrNull()?.first,
                currentStart = yesterday.isha.adjusted,
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
            nextPrayer = if (tomorrow.fajr.adjusted != null) PrayerStatus.Prayer.FAJR else null
            nextStart = tomorrow.fajr.adjusted
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

    private fun toAdhanParams(params: PrayerConfiguration): CalculationParameters {
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
                EngineMadhab.HANAFI -> AdhanMadhab.HANAFI
                EngineMadhab.SHAFII, EngineMadhab.MALIKI, EngineMadhab.HANBALI -> AdhanMadhab.SHAFI
            },
            highLatitudeRule = when (params.highLatitudeRule) {
                EngineHighLatitudeRule.MIDDLE_OF_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
                EngineHighLatitudeRule.SEVENTH_OF_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
                EngineHighLatitudeRule.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
                // ANTI_TRANSIT_FAJR is engine-level — adhan2 gets a fallback rule
                // and the engine substitutes anti-transit post-computation when
                // persistent twilight is detected (see compute()).
                EngineHighLatitudeRule.ANTI_TRANSIT_FAJR -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
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
            shafaq = params.shafaq?.let {
                when (it) {
                    com.khushu.engine.prayer.Shafaq.GENERAL -> com.batoulapps.adhan2.model.Shafaq.GENERAL
                    com.khushu.engine.prayer.Shafaq.AHMER -> com.batoulapps.adhan2.model.Shafaq.AHMER
                    com.khushu.engine.prayer.Shafaq.ABYAD -> com.batoulapps.adhan2.model.Shafaq.ABYAD
                }
            } ?: base.shafaq,
            rounding = params.rounding?.let {
                when (it) {
                    com.khushu.engine.prayer.RoundingPolicy.NEAREST -> com.batoulapps.adhan2.model.Rounding.NEAREST
                    com.khushu.engine.prayer.RoundingPolicy.UP -> com.batoulapps.adhan2.model.Rounding.UP
                    com.khushu.engine.prayer.RoundingPolicy.NONE -> com.batoulapps.adhan2.model.Rounding.NONE
                }
            } ?: base.rounding,
        )
    }

    private const val ISHRAQ_ALTITUDE_DEG = 4.5
    private const val ZAWAAL_MARGIN_MS = 5L * 60_000
    private const val DHUHR_IHTIYAT_MS = 1L * 60_000
    private const val ASR_DEGENERACY_TOLERANCE_DEG = 1.0
}

/** Provenance records for each convention — a method is an institution, not a ruling. */
internal object ConventionMetadata {
    fun info(convention: Convention): ConventionInfo? = when (convention) {
        Convention.MUSLIM_WORLD_LEAGUE -> ConventionInfo("MWL", "Muslim World League", "18.0°", "17.0°")
        Convention.ISNA -> ConventionInfo("ISNA", "Islamic Society of North America", "15.0°", "15.0°")
        Convention.EGYPTIAN -> ConventionInfo("EGYPT", "Egyptian General Authority of Survey", "19.5°", "17.5°")
        Convention.UMM_AL_QURA -> ConventionInfo("MAKKAH", "Umm al-Qura University, Makkah", "18.0°", "90 minutes after maghrib")
        Convention.KARACHI -> ConventionInfo("KARACHI", "University of Islamic Sciences, Karachi", "18.0°", "18.0°")
        Convention.DUBAI -> ConventionInfo("DUBAI", "Dubai (UAE)", "18.2°", "18.2°")
        Convention.KUWAIT -> ConventionInfo("KUWAIT", "Kuwait", "18.0°", "17.5°")
        Convention.QATAR -> ConventionInfo("QATAR", "Qatar", "18.0°", "90 minutes after maghrib")
        Convention.SINGAPORE -> ConventionInfo("SINGAPORE", "Majlis Ugama Islam Singapura", "20.0°", "18.0°")
        Convention.TURKEY -> ConventionInfo("TURKEY", "Diyanet İşleri Başkanlığı, Turkey", "18.0°", "17.0°")
        Convention.MOON_SIGHTING_COMMITTEE -> ConventionInfo("MOON_SIGHTING", "Moon Sighting Committee", "18.0°", "18.0°")
        Convention.CUSTOM -> null
    }
}

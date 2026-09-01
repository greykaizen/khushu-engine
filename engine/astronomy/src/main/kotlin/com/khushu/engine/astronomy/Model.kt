package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import com.khushu.engine.core.units.ArcMinutes

/** Topocentric horizontal position with equatorial detail attached. */
data class SolarPosition(
    val azimuthDeg: Double,
    val altitudeDeg: Double,
    val declinationDeg: Double,
    val rightAscensionHours: Double,
    val distanceAu: Double,
)

/** As [SolarPosition], with lunar distance in kilometres. */
data class LunarPosition(
    val azimuthDeg: Double,
    val altitudeDeg: Double,
    val declinationDeg: Double,
    val rightAscensionHours: Double,
    val distanceKm: Double,
)

/** Phase state of the Moon for an observer (tilt drives AR/calendar rendering). */
data class MoonState(
    val phaseAngleDeg: Double,
    val illuminationFraction: Double,
    val elongationDeg: Double,
    val phaseName: String,
    /** Bright-limb tilt at the observer (χ − q), degrees in [0, 360). */
    val brightLimbTiltDeg: Double,
    /**
     * Days since the most recent new moon. Null when the conjunction search
     * failed — the engine never fabricates an age.
     */
    val moonAgeDays: Double?,
    val isWaxing: Boolean,
)

/** Next lunar quarter-phase event. */
data class UpcomingMoonPhase(
    val targetPhaseDeg: Double,
    val epochMs: Long,
) {
    val phaseName: String
        get() = when (targetPhaseDeg) {
            0.0 -> "New Moon"; 90.0 -> "First Quarter"
            180.0 -> "Full Moon"; else -> "Third Quarter"
        }
}

enum class EclipseKind { NONE, PARTIAL, ANNULAR, TOTAL }

/** Geocentric global solar eclipse. Peak instant + ground coordinates of greatest eclipse. */
data class GlobalSolarEclipse(
    val kind: EclipseKind,
    val peakEpochMs: Long,
    val obscuration: Double?,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
)

/** Lunar (Earth-shadow) eclipse. */
data class LunarEclipse(
    val kind: EclipseKind,
    val peakEpochMs: Long,
    val obscuration: Double?,
)

/** Extreme geocentric lunar distance within a scanned window (supermoon aid). */
data class MoonDistanceExtreme(
    val epochMs: Long,
    val distanceKm: Double,
    val isPerigee: Boolean,
)

/** Rise/set instants; null means no event that civil day (polar conditions). */
data class RiseSet(
    val riseEpochMs: Long?,
    val setEpochMs: Long?,
)

enum class SolarEventType {
    ASTRONOMICAL_DAWN, NAUTICAL_DAWN, CIVIL_DAWN,
    BLUE_HOUR_MORNING_START, GOLDEN_HOUR_MORNING_END,
    SUNRISE, SOLAR_NOON, SUNSET,
    GOLDEN_HOUR_EVENING_START, BLUE_HOUR_EVENING_START,
    CIVIL_DUSK, NAUTICAL_DUSK, ASTRONOMICAL_DUSK,
    /** Anti-transit: the Sun at the observer's LOWER meridian (solar midnight) —
     *  the persistent-twilight Fajr anchor per Muwaqqit's sourced position. */
    ANTI_TRANSIT,
    /** Istiwa period START: the Sun's LEADING limb begins crossing the upper meridian. */
    ISTIWA_START,
    /** Istiwa period END: the Sun's TRAILING limb has fully crossed the upper meridian
     *  (Ẓuhr enters after this). */
    ISTIWA_END,
    /** Sun descends to [AltitudeConventions.karahahDeg] (default 4.5° rumḥ) in the west —
     *  karāhah (disliked time) for delaying ʿAṣr per the Ḥanafi position. */
    KARAHAH,
    /** Ishtibāk al-Nujūm: stars blend into the sky at [AltitudeConventions.ishtibakAlNujumDeg]
     *  (default −10°, King 2003) — Ḥanafi upper bound for delaying Maghrib/fast-breaking. */
    ISHTIBAK_AL_NUJUM,
}

data class SolarEvent(val type: SolarEventType, val epochMs: Long)

/** All canonical solar events of one civil day, chronological. */
data class SolarEvents(val date: java.time.LocalDate, val events: List<SolarEvent>)

/**
 * The Istiwā period — the Sun crossing the observer's upper meridian, measured
 * limb-to-limb (leading limb starts, trailing limb completes). Prayer is
 * prohibited (karahah) during this interval; Ẓuhr enters after it.
 *
 * Limb convention per Muwaqqit docs: istiwā starts when the leading edge of the
 * solar disc begins crossing the meridian and ends when the trailing edge has
 * fully crossed. The [leadingLimbStartEpochMs]..[trailingLimbEndEpochMs] span
 * approximates the semidiameter crossing time via consecutive altitude crossings.
 */
data class IstiwaPeriod(
    val leadingLimbStartEpochMs: Long,
    val trailingLimbEndEpochMs: Long,
    /** The instant the Sun's CENTER is on the meridian (true solar noon/transit). */
    val centerEpochMs: Long,
) {
    val durationMs: Long get() = trailingLimbEndEpochMs - leadingLimbStartEpochMs
}

/** One discrete point along a celestial trajectory (render-ready ENU included). */
data class CelestialPathPoint(
    val epochMs: Long,
    val azimuthDeg: Double,
    val altitudeDeg: Double,
    /** Unit vector [East, North, Up]. */
    val enu: FloatArray,
    val aboveHorizon: Boolean,
)

/** Per-day lunar summary for a whole tracked month. */
data class DayMoon(
    val date: java.time.LocalDate,
    val riseEpochMs: Long?,
    val setEpochMs: Long?,
    /** Instant of highest altitude that civil day. */
    val transitEpochMs: Long?,
    val illuminationAtTransit: Double?,
    val phaseAngleDegAtTransit: Double?,
)

/**
 * One-call monthly lunar computation: per-day rise/set/transit/phase plus an
 * optional clipped sky-path for rendering. Single pass — designed so the
 * calendar and AR views converge on this instead of recomputing separately.
 */
data class MonthlyMoonTrack(
    val days: List<DayMoon>,
    /** Present when requested: coarse-to-fine trajectory samples, horizon-clipped flag set. */
    val pathPoints: List<CelestialPathPoint> = emptyList(),
)

// ── Hilal (crescent sighting) ────────────────────────────────────────────────

enum class YallopGrade(val code: Char, val description: String) {
    A('A', "Easily visible to the naked eye"),
    B('B', "Visible to the naked eye under perfect sky conditions"),
    C('C', "May need optical aid to locate the crescent"),
    D('D', "Optical aid needed, visibility improbable"),
    F('F', "Below the Danjon limit — not visible"),
}

enum class OdehZone(val code: Char, val description: String) {
    A('A', "Crescent visible to the naked eye"),
    B('B', "Visible to the naked eye under perfect conditions"),
    C('C', "Visible with optical aid only"),
    D('D', "Not visible even with optical aid"),
}

data class SightingVerdict(
    val authority: String,
    val passed: Boolean,
    val criteriaMet: Int,
    val criteriaTotal: Int,
)

/** Full crescent-sighting report anchored to local sunset of a civil day. Null when sun never sets. */
data class HilalReport(
    val sunsetEpochMs: Long,
    val moonsetEpochMs: Long?,
    val bestTimeEpochMs: Long,
    val lagMinutes: Int,
    /** Hours since the most recent conjunction; null when unresolvable (D7 — no fabrication). */
    val moonAgeHours: Int?,
    val arcvDeg: Double,
    val elongationDeg: Double,
    val crescentWidthArcmin: ArcMinutes,
    val illuminationFraction: Double,
    val yallop: YallopGrade,
    val odeh: OdehZone,
    val verdicts: List<SightingVerdict>,
)

/** Equinox/solstice instants for one calendar year. */
data class Seasons(
    val marchEquinox: java.time.Instant,
    val juneSolstice: java.time.Instant,
    val septemberEquinox: java.time.Instant,
    val decemberSolstice: java.time.Instant,
)

/** Per-day solar trajectory facts. */
data class SolarDayTrack(
    val date: java.time.LocalDate,
    val riseEpochMs: Long?,
    val setEpochMs: Long?,
    /** Culmination (solar noon) within this civil day. */
    val transitEpochMs: Long?,
)

/** One-pass monthly solar track with optional render-path samples. */
data class SunTrack(
    val days: List<SolarDayTrack>,
    val pathPoints: List<CelestialPathPoint> = emptyList(),
)

/** One evening of a hilal sighting outlook. */
data class HilalForecastDay(
    val date: java.time.LocalDate,
    /** Null when the sun never sets that civil day. */
    val report: HilalReport?,
)

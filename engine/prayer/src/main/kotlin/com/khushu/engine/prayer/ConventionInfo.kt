package com.khushu.engine.prayer

/**
 * Provenance for every calculation convention. A "method" is an institutional
 * astronomical convention, NOT a fiqh ruling — madhab remains a separate
 * dimension ([PrayerConfiguration.madhab]).
 */
data class ConventionInfo(
    val identifier: String,
    val authority: String,
    /** Fajr rule as published by the authority (angle or description). */
    val fajrRule: String,
    /** Isha rule as published by the authority. */
    val ishaRule: String,
    /**
     * Source this engine records as implementing authority. Where references
     * disagree in details (e.g. Umm al-Qura Fajr 18° vs 18.5°), the value
     * actually implemented is the adhan2 0.0.7 preset — recorded here rather
     * than silently "corrected".
     */
    val implementedVia: String = "adhan-kotlin 0.0.7 preset",
)

/** Twilight-color model used by the Moon Sighting Committee method. */
enum class Shafaq { GENERAL, AHMER, ABYAD }

/**
 * Minute-rounding policy applied by the underlying solver to ADJUSTED values.
 * Raw values are rounded identically (the solver rounds unconditionally);
 * callers must never re-round raw results with a different policy without
 * saying so in their own UI.
 */
enum class RoundingPolicy { NEAREST, UP, NONE }

/** Convenience for callers still checking polar availability. */
fun PrayerTimesResult.polarAnomalyFlag(): Boolean =
    audit.highLatitudeResolution == HighLatitudeResolution.UNAVAILABLE

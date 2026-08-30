package com.khushu.engine.astronomy

/**
 * The altitude-threshold table this engine pins. These are DOCUMENTED
 * CONVENTIONS, not universal constants — published definitions vary (e.g.
 * some sources define blue hour as −4…−8°, golden hour as 0…+6°). The engine
 * pins exactly one table so every consumer means the same thing:
 *
 * ```
 * NIGHT             alt <  −18°
 * ASTRONOMICAL_TWILIGHT  [−18, −12)
 * NAUTICAL_TWILIGHT      [−12, −6)
 * BLUE_HOUR              [−6, −4)
 * GOLDEN_HOUR            [−4, +6)   // one continuous band containing sunrise/sunset
 * SUNRISE/SUNSET         −0.833°    // standard refraction horizon
 * DAYLIGHT               ≥ +6°
 * ```
 */
data class AltitudeConventions(
    val sunriseSunsetDeg: Double = -0.833,
    val blueHourLowerDeg: Double = -6.0,
    val blueHourUpperDeg: Double = -4.0,
    val goldenHourUpperDeg: Double = 6.0,
    val civilTwilightDeg: Double = -6.0,
    val nauticalTwilightDeg: Double = -12.0,
    val astronomicalTwilightDeg: Double = -18.0,
    /**
     * Karāhah / Ḍuḥā altitude: the Sun at one rumḥ (spear = 12 spans ≈ 4.5°)
     * above the horizon — Muwaqqit docs, King 2003; variations of 5° documented.
     * NULL = not computed (opt-in named convention — parameters, never defaults).
     */
    val karahahDeg: Double? = null,
    /**
     * Ishtibāk al-Nujūm altitude (−10°, King 2003): stars blend into the sky —
     * the Ḥanafi reprehensible-late bound for Maghrib/fast-breaking.
     * NULL = not computed (opt-in named convention).
     */
    val ishtibakAlNujumDeg: Double? = null,
)

/**
 * Provenance attached to results where computational choices were made.
 * Mirrors the prayer module's audit pattern.
 */
data class AstroAudit(
    /** Ephemeris implementation and pinned version. */
    val ephemeris: String = "cosinekitty astronomy 2.1.19",
    /** Frame + aberration handling for positions. */
    val aberrationModel: String = "EquatorEpoch.OfDate, Aberration.Corrected",
    /** Refraction/horizon model used for rise/set/crossing searches. */
    val refractionModel: String = "Refraction.Normal; horizon at −0.833°",
    /** The pinned altitude-band table (see [AltitudeConventions]). */
    val conventions: AltitudeConventions = AltitudeConventions(),
    val warnings: List<String> = emptyList(),
)

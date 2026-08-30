package com.khushu.engine.astronomy.internal

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchHourAngle
import io.github.cosinekitty.astronomy.searchMoonPhase
import io.github.cosinekitty.astronomy.searchRiseSet

/**
 * Thin, stateless wrapper over Astronomy Engine (cosinekitty) — the single
 * computational source for every astronomical fact in the engine.
 * Behavior-identical to donor Osprey `AstroEphemerisEngine` (see divergences D7).
 */
internal object Ephemeris {

    const val AU_KM = 149_597_870.7

    data class HorizontalPosition(
        val azimuthDeg: Double,
        val altitudeDeg: Double,
        val rightAscensionHours: Double,
        val declinationDeg: Double,
        val distanceAu: Double,
    )

    fun observer(location: com.khushu.engine.core.geo.Location): Observer =
        Observer(location.latitude.degrees, location.longitude.degrees, location.altitudeMeters.meters)

    fun time(epochMs: Long): Time = Time.fromMillisecondsSince1970(epochMs)

    fun horizontalPosition(body: Body, epochMs: Long, location: com.khushu.engine.core.geo.Location): HorizontalPosition {
        val t = time(epochMs)
        val obs = observer(location)
        val eq = equator(body, t, obs, EquatorEpoch.OfDate, Aberration.Corrected)
        val hor = horizon(t, obs, eq.ra, eq.dec, Refraction.Normal)
        return HorizontalPosition(
            azimuthDeg = AstroMath.normalizeDeg(hor.azimuth),
            altitudeDeg = hor.altitude,
            rightAscensionHours = eq.ra,
            declinationDeg = eq.dec,
            distanceAu = eq.dist,
        )
    }

    fun altitude(body: Body, epochMs: Long, location: com.khushu.engine.core.geo.Location): Double =
        horizontalPosition(body, epochMs, location).altitudeDeg

    fun riseSetMs(
        body: Body,
        direction: Direction,
        afterEpochMs: Long,
        location: com.khushu.engine.core.geo.Location,
        windowDays: Double = 1.0,
    ): Long? =
        searchRiseSet(body, observer(location), direction, time(afterEpochMs), windowDays)
            ?.toMillisecondsSince1970()

    fun altitudeCrossingMs(
        body: Body,
        rising: Boolean,
        afterEpochMs: Long,
        degrees: Double,
        location: com.khushu.engine.core.geo.Location,
        windowDays: Double = 1.0,
    ): Long? =
        searchAltitude(
            body, observer(location),
            if (rising) Direction.Rise else Direction.Set,
            time(afterEpochMs), windowDays, degrees,
        )?.toMillisecondsSince1970()

    fun hourAngleTransitMs(
        body: Body,
        afterEpochMs: Long,
        location: com.khushu.engine.core.geo.Location,
        hourAngleDeg: Double = 0.0,
    ): Long =
        searchHourAngle(body, observer(location), hourAngleDeg, time(afterEpochMs)).time.toMillisecondsSince1970()

    fun moonPhaseAngleDeg(epochMs: Long): Double = moonPhase(time(epochMs))

    fun moonIlluminationFraction(epochMs: Long): Double =
        illumination(Body.Moon, time(epochMs)).phaseFraction

    fun moonElongationDeg(epochMs: Long): Double =
        illumination(Body.Moon, time(epochMs)).phaseAngle

    /**
     * First occurrence of the target lunar phase angle at or after [epochMs].
     * 0.0 = new moon, 90 = first quarter, 180 = full, 270 = third quarter.
     */
    fun nextMoonPhaseMs(targetPhaseDeg: Double, epochMs: Long, limitDays: Double = 35.0): Long? =
        searchMoonPhase(targetPhaseDeg, time(epochMs), limitDays)?.toMillisecondsSince1970()
}

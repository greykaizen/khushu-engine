package com.khushu.engine.astronomy.internal

import com.khushu.engine.core.geo.Location
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Bright-limb orientation of the Moon for a topocentric observer.
 * Port of donor `LunarOrientation` (behavior-identical): χ − q tilt used by AR
 * and calendar renderers to rotate the moon disc so its lit side matches the sky.
 */
internal object LunarOrientationMath {

    /** Render-facing tilt in degrees, normalized to [0, 360). */
    fun brightLimbTiltDeg(epochMs: Long, location: Location): Double {
        val time = Ephemeris.time(epochMs)
        val observer = Ephemeris.observer(location)

        // Position angle of the bright limb (χ) from real Sun/Moon equatorial coords.
        val moonEq = equator(Body.Moon, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val sunEq = equator(Body.Sun, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val raS = Math.toRadians(sunEq.ra * 15.0)
        val decS = Math.toRadians(sunEq.dec)
        val raM = Math.toRadians(moonEq.ra * 15.0)
        val decM = Math.toRadians(moonEq.dec)
        val dy = cos(decS) * sin(raS - raM)
        val dx = sin(decS) * cos(decM) - cos(decS) * sin(decM) * cos(raS - raM)
        val posAngleNorth = atan2(dy, dx)

        // Parallactic angle (q): celestial north vs local zenith at the moon's position.
        val hor = horizon(time, observer, moonEq.ra, moonEq.dec, io.github.cosinekitty.astronomy.Refraction.Normal)
        val azRad = Math.toRadians(hor.azimuth)
        val altRad = Math.toRadians(hor.altitude)
        val latRad = Math.toRadians(location.latitude.degrees)
        val sinQ = sin(azRad)
        val cosQ = tan(latRad) * cos(altRad) - sin(altRad) * cos(azRad)
        val parallacticAngle = atan2(sinQ, cosQ)

        return AstroMath.normalizeDeg(Math.toDegrees(posAngleNorth - parallacticAngle))
    }
}

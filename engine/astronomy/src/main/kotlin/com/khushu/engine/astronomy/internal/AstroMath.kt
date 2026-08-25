package com.khushu.engine.astronomy.internal

/** Shared spherical/angle math. Port of donor `AstroMath` (behavior-identical). */
internal object AstroMath {
    /** Normalize an angle to [0, 360). */
    fun normalizeDeg(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}

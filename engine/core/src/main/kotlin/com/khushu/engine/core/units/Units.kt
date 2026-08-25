package com.khushu.engine.core.units

/**
 * Dimensional units for boundaries where confusion between degrees, radians,
 * kilometres and arc-minutes is a real hazard. Raw Double still flows inside
 * calculators; these types guard the public surface.
 */

@JvmInline
value class Degrees(val value: Double) {
    init {
        require(value.isFinite()) { "angle must be finite" }
    }
}

@JvmInline
value class Radians(val value: Double) {
    init {
        require(value.isFinite()) { "angle must be finite" }

    }

    companion object {
        fun ofDegrees(degrees: Degrees): Radians = Radians(Math.toRadians(degrees.value))
    }

    fun toDegrees(): Degrees = Degrees(Math.toDegrees(value))
}

@JvmInline
value class Kilometers(val value: Double) {
    init {
        require(value.isFinite() && value >= 0.0) { "distance must be finite and >= 0" }
    }
}

/** Arc-minutes: 1/60 of a degree; used for crescent width and lunar semi-diameter. */
@JvmInline
value class ArcMinutes(val value: Double) {
    init {
        require(value.isFinite()) { "arc length must be finite" }
    }
}

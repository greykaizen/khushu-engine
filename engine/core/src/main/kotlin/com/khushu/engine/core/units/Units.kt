package com.khushu.engine.core.units

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate

/**
 * Dimensional units for boundaries where confusion between degrees, radians,
 * kilometres and arc-minutes is a real hazard. Raw Double still flows inside
 * calculators; these types guard the public surface.
 */

@JvmInline
value class Degrees(val value: Double) {
    init {
        validate(value.isFinite()) {
            InvalidParameterException("degrees", "$value", "must be finite")
        }
    }
}

@JvmInline
value class Radians(val value: Double) {
    init {
        validate(value.isFinite()) {
            InvalidParameterException("radians", "$value", "must be finite")
        }
    }

    companion object {
        fun ofDegrees(degrees: Degrees): Radians = Radians(Math.toRadians(degrees.value))
    }

    fun toDegrees(): Degrees = Degrees(Math.toDegrees(value))
}

@JvmInline
value class Kilometers(val value: Double) {
    init {
        validate(value.isFinite() && value >= 0.0) {
            InvalidParameterException("kilometers", "$value", "must be finite and >= 0")
        }
    }
}

/** Arc-minutes: 1/60 of a degree; used for crescent width and lunar semi-diameter. */
@JvmInline
value class ArcMinutes(val value: Double) {
    init {
        validate(value.isFinite()) {
            InvalidParameterException("arcMinutes", "$value", "must be finite")
        }
    }
}

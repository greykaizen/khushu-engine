package com.khushu.engine.core.geo

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate

/**
 * Typed geographic primitives shared by every domain.
 *
 * Raw Doubles may flow inside calculators; they may not cross an API
 * boundary untyped (AGENTS.md §8).
 */

@JvmInline
value class Latitude(val degrees: Double) {
    init {
        validate(degrees in -90.0..90.0) {
            InvalidParameterException("latitude", "$degrees", "must be in [-90, 90]")
        }
    }
}

@JvmInline
value class Longitude(val degrees: Double) {
    init {
        validate(degrees in -180.0..180.0) {
            InvalidParameterException("longitude", "$degrees", "must be in [-180, 180]")
        }
    }
}

@JvmInline
value class AltitudeMeters(val meters: Double) {
    init {
        validate(meters.isFinite()) {
            InvalidParameterException("altitudeMeters", "$meters", "must be finite")
        }
        validate(meters >= -430.0) {
            InvalidParameterException("altitudeMeters", "$meters", "must not be below Dead Sea shore (-430)")
        }
    }
}

data class Location(
    val latitude: Latitude,
    val longitude: Longitude,
    val altitudeMeters: AltitudeMeters = AltitudeMeters(0.0),
) {
    companion object {
        fun of(latitudeDegrees: Double, longitudeDegrees: Double, altitude: Double = 0.0): Location =
            Location(Latitude(latitudeDegrees), Longitude(longitudeDegrees), AltitudeMeters(altitude))
    }
}

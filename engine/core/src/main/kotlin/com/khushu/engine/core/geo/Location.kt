package com.khushu.engine.core.geo

/**
 * Typed geographic primitives shared by every domain.
 *
 * Raw Doubles may flow inside calculators; they may not cross an API
 * boundary untyped (AGENTS.md §8).
 */

@JvmInline
value class Latitude(val degrees: Double) {
    init {
        require(degrees in -90.0..90.0) { "latitude must be in [-90, 90], was $degrees" }
    }
}

@JvmInline
value class Longitude(val degrees: Double) {
    init {
        require(degrees in -180.0..180.0) { "longitude must be in [-180, 180], was $degrees" }
    }
}

@JvmInline
value class AltitudeMeters(val meters: Double) {
    init {
        require(meters.isFinite()) { "altitude must be finite" }
        require(meters >= -430.0) { "altitude below Dead Sea shore (-430 m), was $meters" }
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

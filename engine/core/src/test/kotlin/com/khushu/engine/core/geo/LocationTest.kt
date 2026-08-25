package com.khushu.engine.core.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocationTest {

    @Test
    fun validExtremesAreAccepted() {
        assertEquals(90.0, Latitude(90.0).degrees)
        assertEquals(-90.0, Latitude(-90.0).degrees)
        assertEquals(180.0, Longitude(180.0).degrees)
        assertEquals(-180.0, Longitude(-180.0).degrees)
        assertEquals(-430.0, AltitudeMeters(-430.0).meters)
    }

    @Test
    fun outOfRangeLatitudeIsRejected() {
        assertFailsWith<IllegalArgumentException> { Latitude(90.0001) }
        assertFailsWith<IllegalArgumentException> { Latitude(-90.0001) }
        assertFailsWith<IllegalArgumentException> { Latitude(Double.NaN) }
    }

    @Test
    fun outOfRangeLongitudeIsRejected() {
        assertFailsWith<IllegalArgumentException> { Longitude(180.0001) }
        assertFailsWith<IllegalArgumentException> { Longitude(-180.0001) }
    }

    @Test
    fun implausibleAltitudeIsRejected() {
        assertFailsWith<IllegalArgumentException> { AltitudeMeters(-431.0) }
        assertFailsWith<IllegalArgumentException> { AltitudeMeters(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun defaultAltitudeIsSeaLevel() {
        val loc = Location(Latitude(51.5), Longitude(-0.12))
        assertEquals(0.0, loc.altitudeMeters.meters)
    }
}

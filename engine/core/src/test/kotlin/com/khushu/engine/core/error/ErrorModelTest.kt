package com.khushu.engine.core.error

import com.khushu.engine.core.geo.AltitudeMeters
import com.khushu.engine.core.geo.Latitude
import com.khushu.engine.core.geo.Location
import com.khushu.engine.core.geo.Longitude
import com.khushu.engine.core.units.Degrees
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ErrorModelTest {

    @Test
    fun invalidLatitudeThrowsStructuredInputFailure() {
        val e = assertFailsWith<InvalidParameterException> { Latitude(100.0) }
        assertEquals("latitude", e.parameter)
        assertEquals("100.0", e.value)
        assertContains(e.constraint, "[-90, 90]")
        assertContains(e.message!!, "khushu: invalid latitude = 100.0")
        // catch-compat: still an IllegalArgumentException and a KhushuInputFailure
        assertIs<IllegalArgumentException>(e)
        assertIs<KhushuInputFailure>(e)
    }

    @Test
    fun invalidLongitudeThrowsStructuredInputFailure() {
        val e = assertFailsWith<InvalidParameterException> { Longitude(200.0) }
        assertEquals("longitude", e.parameter)
        assertIs<KhushuInputFailure>(e)
    }

    @Test
    fun altitudeBelowDeadSeaThrowsTypedFailure() {
        val e = assertFailsWith<InvalidParameterException> { AltitudeMeters(-500.0) }
        assertEquals("altitudeMeters", e.parameter)
        assertContains(e.constraint, "Dead Sea")
    }

    @Test
    fun nonFiniteAngleThrowsTypedFailure() {
        assertFailsWith<InvalidParameterException> { Degrees(Double.NaN) }
    }

    @Test
    fun locationCompanionFactorySurfacesTheSameTypedFailures() {
        val e = assertFailsWith<KhushuInputFailure> { Location.of(91.0, 0.0) }
        assertIs<InvalidParameterException>(e)
    }

    @Test
    fun hijriDayDoesNotExistCarriesStructuredFields() {
        val e = HijriDayDoesNotExistException(1447, 2, 30, 0)
        assertEquals(1447, e.hijriYear)
        assertEquals(2, e.hijriMonth)
        assertEquals(30, e.hijriDay)
        assertContains(e.message!!, "30-2-1447")
        assertIs<KhushuInputFailure>(e)
    }

    @Test
    fun noResultAndUpstreamAreComputationFailuresWithStateCompat() {
        val nr = NoResultException("x does not occur")
        assertIs<IllegalStateException>(nr)
        assertIs<KhushuComputationFailure>(nr)
        assertContains(nr.message!!, "no result")

        val cause = RuntimeException("boom")
        val up = UpstreamComputationException("conversion of 2300-01-01", cause)
        assertIs<IllegalStateException>(up)
        assertNotNull(up.cause)
        assertEquals(cause, up.cause)
        assertContains(up.message!!, "upstream computation failed")
    }
}

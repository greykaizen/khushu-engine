package com.khushu.engine.qibla

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Qibla as AdhanQibla
import com.khushu.engine.core.geo.Location
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaTest {

    private val sweep = listOf(
        Location.of(51.5072, -0.1276), // London
        Location.of(40.7128, -74.0060), // New York
        Location.of(-33.8688, 151.2093), // Sydney
        Location.of(35.6895, 139.6917), // Tokyo
        Location.of(-1.2921, 36.8219), // Nairobi
        Location.of(55.7558, 37.6173), // Moscow
        Location.of(3.1390, 101.6869), // Kuala Lumpur
        Location.of(-34.6037, -58.3816), // Buenos Aires
        Location.of(64.1466, -21.9426), // Reykjavik
    )

    @Test
    fun bearingMatchesAdhan2ReferenceEverywhere() {
        for (loc in sweep) {
            val ours = Qibla.bearing(loc).bearingDegFromNorth.value
            val adhan = AdhanQibla(Coordinates(loc.latitude.degrees, loc.longitude.degrees)).direction.let {
                if (it < 0) it + 360.0 else it % 360.0
            }
            // Equivalent formulations agree to <1e-5 degrees (~metres of arc);
            // ours is authoritative, adhan2 is the cross-check.
            assertEquals(adhan, ours, 1e-4, "qibla mismatch at $loc")
        }
    }

    @Test
    fun bearingAlwaysWithinCompassRange() {
        for (loc in sweep) {
            val b = Qibla.bearing(loc)
            assertTrue(b.bearingDegFromNorth.value in 0.0..360.0)
            assertTrue(b.greatCircleDistanceKm.value > 0.0)
        }
    }

    @Test
    fun authoritativeSpotValues() {
        // Published qibla values (rounded to the nearest degree):
        assertEquals(119.0, Qibla.bearing(Location.of(51.5072, -0.1276)).bearingDegFromNorth.value, 0.5, "London")
        assertEquals(58.0, Qibla.bearing(Location.of(40.7128, -74.0060)).bearingDegFromNorth.value, 0.5, "New York")
        // New York to Makkah is roughly 10 200 km across the Atlantic.
        assertTrue(abs(Qibla.bearing(Location.of(40.7128, -74.0060)).greatCircleDistanceKm.value - 10_200.0) < 150.0)
    }

    @Test
    fun distanceShrinksAsYouApproachTheKaaba() {
        val far = Qibla.bearing(Location.of(51.5072, -0.1276)).greatCircleDistanceKm.value
        val near = Qibla.bearing(Location.of(21.0, 39.8)).greatCircleDistanceKm.value
        assertTrue(near < far / 100.0)
    }

    @Test
    fun auditExposesProvenance() {
        val audit = Qibla.audit()
        assertTrue(audit.kaabaConstantsOrigin.contains("21.4225"))
        assertTrue(audit.distanceModel.isNotBlank())
        assertTrue(audit.bearingFormula.isNotBlank())
        assertTrue(audit.warnings.isEmpty())
    }

    @Test
    fun relativeSunAngleIsSignedNormalizedAndMatchesManualComputation() {
        val loc = Location.of(51.5072, -0.1276)
        val instant = java.time.LocalDate.of(2026, 6, 21).atTime(12, 0)
            .atZone(java.time.ZoneId.of("Europe/London")).toInstant()
        val relation = Qibla.relativeSunAngle(loc, instant)
        assertTrue(relation.signedAngleToQiblaDeg in -180.0..180.0)
        val bearing = Qibla.bearing(loc).bearingDegFromNorth.value
        var diff = (relation.sunAzimuthDeg - bearing) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        assertEquals(diff, relation.signedAngleToQiblaDeg, 1e-9)
        assertEquals(relation.alignedWithinTolerance, abs(diff) <= 2.0)
    }

    @Test
    fun shadowVerificationEventsAlignWithRelativeSunAngle() {
        // Cohesion bridge: when the sun stands over the Kaaba, its azimuth at
        // any daylight location equals that location's qibla bearing exactly.
        val loc = Location.of(51.5072, -0.1276) // London: both zenith passages fall in daylight
        val events = Qibla.shadowVerification(2026, loc)
        assertTrue(events.size >= 4) // two toward + two away passages per year

        val visibleToward = events.filter {
            it.method == ShadowMethod.FACE_TOWARD_SUN && it.visibleAtLocation
        }
        assertTrue(visibleToward.isNotEmpty(), "London daylight must include a Kaaba zenith passage")
        val qibla = Qibla.bearing(loc).bearingDegFromNorth.value
        for (e in visibleToward) {
            val relation = Qibla.relativeSunAngle(loc, e.instant)
            assertTrue(
                relation.alignedWithinTolerance,
                "sun must stand on the qibla at a Kaaba zenith passage, got ${relation.signedAngleToQiblaDeg}",
            )
            assertEquals(qibla, e.shadowBearingDeg.value, 0.1)
        }
    }
}

    @Test
    fun bearingMatchesMuwaqqitKohatReference() {
        // Muwaqqit (CC-BY attribution) publishes 254.6° true bearing for
        // Kohat (33.5888559, 71.4429286) — external cross-check alongside
        // the adhan2 golden matrix (divergences D5).
        val kohat = com.khushu.engine.core.geo.Location.of(33.5888559, 71.4429286)
        val bearing = Qibla.bearing(kohat).bearingDegFromNorth.value
        assertTrue(
            kotlin.math.abs(bearing - 254.6) <= 0.15,
            "qibla bearing $bearing vs Muwaqqit 254.6°",
        )
    }

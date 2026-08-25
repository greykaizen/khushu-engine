package com.khushu.engine.astronomy

import com.khushu.engine.core.geo.Location
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Layer-A regression lock against donor Osprey's core/astronomy call sequences
 * (cosinekitty 2.1.19, identical formulas). Inputs live entirely in fixtures.
 */
class AstroGoldenRegressionTest {

    @Serializable
    private data class Site(val lat: Double, val lon: Double, val alt: Double)

    @Serializable
    private data class Case(
        val site: String,
        val epochMs: Long,
        val sunAz: Double, val sunAlt: Double, val sunRa: Double,
        val sunDec: Double, val sunDist: Double,
        val moonAz: Double, val moonAlt: Double, val moonRa: Double,
        val moonDec: Double, val moonDistKm: Double,
        val phaseDeg: Double, val illum: Double, val elongDeg: Double, val tiltDeg: Double,
    )

    @Serializable
    private data class Golden(val sites: Map<String, Site>, val cases: List<Case>)

    private val golden: Golden by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(
            Golden.serializer(),
            Golden::class.java.classLoader.getResource("fixtures/astro_golden.json")!!.readText(),
        )
    }

    @Test
    fun everyAstroGoldenCaseReproducesExactly() {
        val tol = 1e-9 // same library, same machine: agreement should be near-bit-exact
        for (case in golden.cases) {
            val s = assertNotNull(golden.sites[case.site])
            val loc = Location.of(s.lat, s.lon, s.alt)
            val instant = Instant.ofEpochMilli(case.epochMs)

            val sun = Astronomy.sun.position(loc, instant)
            assertEquals(case.sunAz, sun.azimuthDeg, tol, "${case.site} ${case.epochMs} sunAz")
            assertEquals(case.sunAlt, sun.altitudeDeg, tol, "${case.site} ${case.epochMs} sunAlt")
            assertEquals(case.sunRa, sun.rightAscensionHours, tol)
            assertEquals(case.sunDec, sun.declinationDeg, tol)
            assertEquals(case.sunDist, sun.distanceAu, tol)

            val moon = Astronomy.moon.position(loc, instant)
            assertEquals(case.moonAz, moon.azimuthDeg, tol)
            assertEquals(case.moonAlt, moon.altitudeDeg, tol)
            assertEquals(case.moonRa, moon.rightAscensionHours, tol)
            assertEquals(case.moonDec, moon.declinationDeg, tol)
            assertEquals(case.moonDistKm, moon.distanceKm, tol)

            val state = Astronomy.moon.state(loc, instant)
            assertEquals(case.phaseDeg, state.phaseAngleDeg, tol)
            assertEquals(case.illum, state.illuminationFraction, tol)
            assertEquals(case.elongDeg, state.elongationDeg, tol)
            assertEquals(case.tiltDeg, state.brightLimbTiltDeg, tol)
        }
        assertTrue(golden.cases.size > 500, "corpus unexpectedly small")
    }

    private fun assertNotNull(v: Site?): Site = v ?: throw AssertionError("missing site")
}

@file:OptIn(kotlin.time.ExperimentalTime::class)

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import java.time.LocalDate

// Golden-master dumper for the astronomy module: replicates donor Osprey's
// core/astronomy call sequences verbatim (AstroEphemerisEngine + LunarOrientation).

data class Site(val key: String, val lat: Double, val lon: Double, val alt: Double, val tz: String)

val SITES = listOf(
    Site("makkah", 21.4225, 39.8262, 277.0, "Asia/Riyadh"),
    Site("london", 51.5072, -0.1276, 11.0, "Europe/London"),
    Site("newyork", 40.7128, -74.0060, 10.0, "America/New_York"),
    Site("sydney", -33.8688, 151.2093, 58.0, "Australia/Sydney"),
    Site("tromso", 69.6492, 18.9553, 20.0, "Europe/Oslo"),
    Site("apia", -13.8333, -171.7667, 10.0, "Pacific/Apia"),
)

fun normalizeDeg(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

data class Row(
    val siteKey: String,
    val epochMs: Long,
    val sunAzDeg: Double, val sunAltDeg: Double, val sunRaHours: Double,
    val sunDecDeg: Double, val sunDistAu: Double,
    val moonAzDeg: Double, val moonAltDeg: Double, val moonRaHours: Double,
    val moonDecDeg: Double, val moonDistKm: Double,
    val phaseAngleDeg: Double, val illumFraction: Double, val elongationDeg: Double,
    val brightLimbTiltDeg: Double,
)

fun horizontalPosition(body: Body, epochMs: Long, s: Site): io.github.cosinekitty.astronomy.Equatorial {
    val time = Time.fromMillisecondsSince1970(epochMs)
    val observer = Observer(s.lat, s.lon, s.alt)
    return equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
}

fun brightLimbTilt(epochMs: Long, s: Site): Double {
    val time = Time.fromMillisecondsSince1970(epochMs)
    val observer = Observer(s.lat, s.lon, s.alt)
    val moonEq = horizontalPosition(Body.Moon, epochMs, s)
    val sunEq = horizontalPosition(Body.Sun, epochMs, s)
    val raS = Math.toRadians(sunEq.ra * 15.0)
    val decS = Math.toRadians(sunEq.dec)
    val raM = Math.toRadians(moonEq.ra * 15.0)
    val decM = Math.toRadians(moonEq.dec)
    val dy = kotlin.math.cos(decS) * kotlin.math.sin(raS - raM)
    val dx = kotlin.math.sin(decS) * kotlin.math.cos(decM) - kotlin.math.cos(decS) * kotlin.math.sin(decM) * kotlin.math.cos(raS - raM)
    val posAngleNorth = kotlin.math.atan2(dy, dx)

    val hor = horizon(time, observer, moonEq.ra, moonEq.dec, Refraction.Normal)
    val azRad = Math.toRadians(hor.azimuth)
    val altRad = Math.toRadians(hor.altitude)
    val latRad = Math.toRadians(s.lat)
    val sinQ = kotlin.math.sin(azRad)
    val cosQ = kotlin.math.tan(latRad) * kotlin.math.cos(altRad) - kotlin.math.sin(altRad) * kotlin.math.cos(azRad)
    val q = kotlin.math.atan2(sinQ, cosQ)
    return normalizeDeg(Math.toDegrees(posAngleNorth - q))
}

fun main(args: Array<String>) {
    val rows = mutableListOf<Row>()
    val year = 2025
    for (site in SITES) {
        for (month in 1..12) {
            for (day in intArrayOf(1, 8, 15, 22)) {
                // Two samples per day: pre-dawn and mid-day UTC.
                for (hourUtc in intArrayOf(4, 12)) {
                    val instant = java.time.ZonedDateTime.of(year, month, day, hourUtc, 0, 0, 0, java.time.ZoneOffset.UTC)
                    val epochMs = instant.toInstant().toEpochMilli()
                    val sunEq = horizontalPosition(Body.Sun, epochMs, site)
                    val moonEq = horizontalPosition(Body.Moon, epochMs, site)
                    val sunTime = Time.fromMillisecondsSince1970(epochMs)
                    val obs = Observer(site.lat, site.lon, site.alt)
                    val sunHor = horizon(sunTime, obs, sunEq.ra, sunEq.dec, Refraction.Normal)
                    val moonHor = horizon(sunTime, obs, moonEq.ra, moonEq.dec, Refraction.Normal)
                    rows += Row(
                        siteKey = site.key,
                        epochMs = epochMs,
                        sunAzDeg = normalizeDeg(sunHor.azimuth),
                        sunAltDeg = sunHor.altitude,
                        sunRaHours = sunEq.ra,
                        sunDecDeg = sunEq.dec,
                        sunDistAu = sunEq.dist,
                        moonAzDeg = normalizeDeg(moonHor.azimuth),
                        moonAltDeg = moonHor.altitude,
                        moonRaHours = moonEq.ra,
                        moonDecDeg = moonEq.dec,
                        moonDistKm = moonEq.dist * 149_597_870.7,
                        phaseAngleDeg = moonPhase(Time.fromMillisecondsSince1970(epochMs)),
                        illumFraction = illumination(Body.Moon, Time.fromMillisecondsSince1970(epochMs)).phaseFraction,
                        elongationDeg = illumination(Body.Moon, Time.fromMillisecondsSince1970(epochMs)).phaseAngle,
                        brightLimbTiltDeg = brightLimbTilt(epochMs, site),
                    )
                }
            }
        }
    }

    // ── v1.2 reference section (NOT donor-derived; change-detectors only) ──
    data class PhaseRow(
        val siteKey: String, val date: String,
        val sunriseMs: Long?, val sunsetMs: Long?, val noonMs: Long, val midnightMs: Long,
        val astroDawnMs: Long?, val nautDawnMs: Long?, val civDawnMs: Long?,
        val blueEndM: Long?, val dayBegin: Long?, val dayEnd: Long?,
        val blueStartE: Long?, val civDuskMs: Long?, val nautDuskMs: Long?, val astroDuskMs: Long?,
    )
    val phaseRows = mutableListOf<PhaseRow>()
    for (site in SITES) {
        val obs = Observer(site.lat, site.lon, site.alt)
        for ((y, m, d) in listOf(Triple(2026, 1, 15), Triple(2026, 3, 20), Triple(2026, 6, 21), Triple(2026, 9, 23), Triple(2026, 12, 21))) {
            val dayStart = java.time.ZonedDateTime.of(y, m, d, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000L
            fun x2(deg: Double, rise: Boolean): Long? = try {
                io.github.cosinekitty.astronomy.searchAltitude(Body.Sun, obs, if (rise) Direction.Rise else Direction.Set, Time.fromMillisecondsSince1970(dayStart), 1.0, deg)?.toMillisecondsSince1970()?.takeIf { it < dayEnd }
            } catch (e: Exception) { null }
            val noon = io.github.cosinekitty.astronomy.searchHourAngle(Body.Sun, obs, 0.0, Time.fromMillisecondsSince1970(dayStart)).time.toMillisecondsSince1970().coerceIn(dayStart, dayEnd - 1)
            val mid = try {
                // cosinekitty hour angle is in HOURS [0,24): 12 = 180° = lower
                // meridian (v1.14 — the old 180.0 literal always threw, so the
                // fixture silently recorded the noon+12h fallback).
                io.github.cosinekitty.astronomy.searchHourAngle(Body.Sun, obs, 12.0, Time.fromMillisecondsSince1970(noon)).time.toMillisecondsSince1970()
            } catch (e: Exception) { noon + 43_200_000L }
            phaseRows += PhaseRow(
                site.key, "$y-%02d-%02d".format(m, d),
                x2(-0.833, true), x2(-0.833, false), noon, mid,
                x2(-18.0, true), x2(-12.0, true), x2(-6.0, true),
                x2(-4.0, true), x2(6.0, true), x2(6.0, false),
                x2(-4.0, false), x2(-6.0, false), x2(-12.0, false), x2(-18.0, false),
            )
        }
    }

    val json = buildString {
        append("{\n")
        append("  \"meta\": {\"generator\": \"astro-golden-dumper\", \"cosinekitty\": \"2.1.19\",\n")
        append("    \"note\": \"cases=donor-regression lock; solarDay=REFERENCE fixtures (self-generated change-detectors, not correctness evidence)\",\n")
        append("    \"donorPath\": \"Osprey app/src/main/java/com/kaizen/osprey/core/astronomy\"},\n")
        append("  \"sites\": {\n")
        append(SITES.joinToString(",\n") {
            "    \"${it.key}\": {\"lat\":${it.lat},\"lon\":${it.lon},\"alt\":${it.alt}}"
        })
        append("\n  },\n")
        append("  \"cases\": [\n")
        append(rows.joinToString(",\n") { r ->
            "    {\"site\":\"${r.siteKey}\",\"epochMs\":${r.epochMs}," +
                "\"sunAz\":${r.sunAzDeg},\"sunAlt\":${r.sunAltDeg},\"sunRa\":${r.sunRaHours}," +
                "\"sunDec\":${r.sunDecDeg},\"sunDist\":${r.sunDistAu}," +
                "\"moonAz\":${r.moonAzDeg},\"moonAlt\":${r.moonAltDeg},\"moonRa\":${r.moonRaHours}," +
                "\"moonDec\":${r.moonDecDeg},\"moonDistKm\":${r.moonDistKm}," +
                "\"phaseDeg\":${r.phaseAngleDeg},\"illum\":${r.illumFraction}," +
                "\"elongDeg\":${r.elongationDeg},\"tiltDeg\":${r.brightLimbTiltDeg}}"
        })
        append("\n  ],\n")
        append("  \"solarDayReference\": [\n")
        append(phaseRows.joinToString(",\n") { r ->
            "    {\"site\":\"${r.siteKey}\",\"date\":\"${r.date}\"," +
                "\"sunrise\":${r.sunriseMs ?: "null"},\"sunset\":${r.sunsetMs ?: "null"}," +
                "\"noon\":${r.noonMs},\"midnight\":${r.midnightMs}," +
                "\"astroDawn\":${r.astroDawnMs ?: "null"},\"nautDawn\":${r.nautDawnMs ?: "null"},\"civDawn\":${r.civDawnMs ?: "null"}," +
                "\"blueEndM\":${r.blueEndM ?: "null"},\"dayBegin\":${r.dayBegin ?: "null"},\"dayEnd\":${r.dayEnd ?: "null"}," +
                "\"blueStartE\":${r.blueStartE ?: "null"},\"civDusk\":${r.civDuskMs ?: "null"},\"nautDusk\":${r.nautDuskMs ?: "null"},\"astroDusk\":${r.astroDuskMs ?: "null"}}"
        })
        append("\n  ]\n}\n")
    }

    val out = java.io.File(args.getOrElse(0) { "astro_golden.json" })
    out.parentFile?.mkdirs()
    out.writeText(json)
    println("wrote ${rows.size} cases to ${out.absolutePath}")
}

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

    val json = buildString {
        append("{\n")
        append("  \"meta\": {\"generator\": \"astro-golden-dumper\", \"cosinekitty\": \"2.1.19\",\n")
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
        append("\n  ]\n}\n")
    }

    val out = java.io.File(args.getOrElse(0) { "astro_golden.json" })
    out.parentFile?.mkdirs()
    out.writeText(json)
    println("wrote ${rows.size} cases to ${out.absolutePath}")
}

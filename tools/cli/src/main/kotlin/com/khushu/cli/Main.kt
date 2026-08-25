package com.khushu.cli

import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.PrayerParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.system.exitProcess

// Terminal harness — human spot-checks ONLY. Never part of the public API (AGENTS.md §7).

private val engine = KhushuEngine()

@Serializable private data class PSite(val lat: Double, val lon: Double, val alt: Double, val tz: String)
@Serializable private data class PCase(
    val site: String, val date: String, val madhab: String, val convention: String? = null,
    val fajrAngle: Double? = null, val ishaAngle: Double? = null, val ishaInterval: Int = 0,
    val hlr: String, val offsets: List<Int>? = null, val error: String? = null,
    val times: Map<String, Long?>,
)
@Serializable private data class PGolden(val sites: Map<String, PSite>, val cases: List<PCase>)

@Serializable private data class ASite(val lat: Double, val lon: Double, val alt: Double)
@Serializable private data class ACase(
    val site: String, val epochMs: Long,
    val sunAz: Double, val sunAlt: Double, val sunRa: Double, val sunDec: Double, val sunDist: Double,
    val moonAz: Double, val moonAlt: Double, val moonRa: Double, val moonDec: Double, val moonDistKm: Double,
    val phaseDeg: Double, val illum: Double, val elongDeg: Double, val tiltDeg: Double,
)
@Serializable private data class AGolden(val sites: Map<String, ASite>, val cases: List<ACase>)

private val KNOWN_FLAGS = setOf(
    "lat", "lon", "alt", "tz", "date", "madhab", "ym", "offset", "month", "year",
    "prayer", "astro", "help",
)

private class Args(args: Array<String>) {
    private val map = HashMap<String, String>()
    init {
        var i = 0
        while (i < args.size) {
            if (!args[i].startsWith("--")) {
                i++
                continue
            }
            val flag = args[i].substring(2)
            if (flag !in KNOWN_FLAGS) die("unknown flag --$flag (known: ${KNOWN_FLAGS.joinToString(", ")})")
            if (flag in booleanFlags) {
                map[flag] = "true"
                i += 1
            } else {
                if (i + 1 >= args.size) die("flag --$flag expects a value")
                map[flag] = args[i + 1]
                i += 2
            }
        }
    }

    companion object {
        private val booleanFlags = setOf("prayer", "astro")
    }
    operator fun get(k: String): String? = map[k]
}

private fun location(a: Args): Location {
    val lat = a["lat"]?.toDoubleOrNull() ?: die("missing --lat")
    val lon = a["lon"]?.toDoubleOrNull() ?: die("missing --lon")
    return Location.of(lat, lon, a["alt"]?.toDoubleOrNull() ?: 0.0)
}

private fun date(a: Args, key: String = "date"): LocalDate =
    a[key]?.let(LocalDate::parse) ?: LocalDate.now()

private fun zone(a: Args): ZoneId = ZoneId.of(a["tz"] ?: ZoneId.systemDefault().id)

private fun die(msg: String): Nothing {
    System.err.println(msg)
    exitProcess(1)
}

private fun fmtTime(epochMs: Long?, zoneId: ZoneId): String =
    epochMs?.let { java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalTime().toString() } ?: "—"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(
            """
            khushu engine cli
              prayer --lat --lon [--alt] [--tz] [--date] [--madhab hanafi|shafii|maliki|hanbali]
              sun    --lat --lon [--alt] --date [--tz]
              moon   --lat --lon [--alt] --date [--tz] | month --ym 2026-01
              hijri  --date [--offset -2..2]
              events --month 1..12 --year
              qibla  --lat --lon
              verify [--prayer] [--astro]   (default: both)
            """.trimIndent(),
        )
        return
    }
    val command = args[0]
    val rest = Args(args.copyOfRange(1, args.size))
    when (command) {
        "prayer" -> prayer(rest)
        "sun" -> sun(rest)
        "moon" -> if (rest["ym"] != null) moonMonth(rest) else moonDay(rest)
        "hijri" -> hijri(rest)
        "events" -> events(rest)
        "qibla" -> qibla(rest)
        "verify" -> verify(rest)
        else -> die("unknown command $command")
    }
}

private fun prayer(a: Args) {
    val loc = location(a)
    val zoneId = zone(a)
    val params = PrayerParams(
        madhab = when (a["madhab"]?.lowercase()) {
            "hanafi" -> com.khushu.engine.prayer.Madhab.HANAFI
            "maliki" -> com.khushu.engine.prayer.Madhab.MALIKI
            "hanbali" -> com.khushu.engine.prayer.Madhab.HANBALI
            else -> com.khushu.engine.prayer.Madhab.SHAFII
        },
    )
    val t = engine.prayer.times(loc, date(a), params)
    println("prayer times ${t.date} @ (${loc.latitude.degrees}, ${loc.longitude.degrees}) [${params.madhab}]")
    val rows = listOf(
        "Fajr" to t.fajr, "Sunrise" to t.sunrise, "Dhuhr" to t.dhuhrEnters,
        "Asr" to t.asr, "Sunset" to t.sunset, "Maghrib" to t.maghrib, "Isha" to t.isha,
        "Midnight" to t.midnight, "Last third" to t.lastThirdOfNight,
        "Ishraq (Duha)" to t.ishraq, "Zawaal from" to t.zawaalStart,
    )
    for ((name, instant) in rows) {
        println("  %-14s %s".format(name, instant?.let { fmtTime(it.toEpochMilli(), zoneId) } ?: "(uncomputable)"))
    }
    if (t.polarAnomaly) println("  ⚠ polar anomaly — some timings uncomputable")
}

private fun sun(a: Args) {
    val loc = location(a)
    val d = date(a)
    val rs = engine.astronomy.sun.riseSet(loc, d, zone(a))
    val noon = engine.astronomy.sun.position(loc, d.atTime(12, 0).toInstant(ZoneOffset.UTC))
    println("sun ${d} @ (${loc.latitude.degrees}, ${loc.longitude.degrees})")
    println("  rise %s  set %s (local)".format(fmtTime(rs.riseEpochMs, zone(a)), fmtTime(rs.setEpochMs, zone(a))))
    println("  @12:00Z az=%.2f° alt=%.2f° decl=%.2f°".format(noon.azimuthDeg, noon.altitudeDeg, noon.declinationDeg))
}

private fun moonDay(a: Args) {
    val loc = location(a)
    val d = date(a)
    val rs = engine.astronomy.moon.riseSet(loc, d, zone(a))
    val state = engine.astronomy.moon.state(loc, d.atTime(12, 0).toInstant(ZoneOffset.UTC))
    println("moon ${d} @ (${loc.latitude.degrees}, ${loc.longitude.degrees})")
    println("  rise %s  set %s (local)".format(fmtTime(rs.riseEpochMs, zone(a)), fmtTime(rs.setEpochMs, zone(a))))
    println("  %.1f%% illuminated · %s · tilt %.1f° · dist %.0f km".format(
        state.illuminationFraction * 100, state.phaseName, state.brightLimbTiltDeg,
        engine.astronomy.moon.position(loc, d.atTime(12, 0).toInstant(ZoneOffset.UTC)).distanceKm,
    ))
}

private fun moonMonth(a: Args) {
    val loc = location(a)
    val ym = YearMonth.parse(a["ym"]!!)
    val track = engine.astronomy.moon.track(loc, ym, zone(a), includePath = false)
    println("lunar month $ym @ (${loc.latitude.degrees}, ${loc.longitude.degrees})")
    for (day in track.days) {
        println(
            "  %s rise=%s set=%s transit=%s illum=%s".format(
                day.date,
                day.riseEpochMs?.let { fmtTime(it, zone(a)) } ?: "—",
                day.setEpochMs?.let { fmtTime(it, zone(a)) } ?: "—",
                day.transitEpochMs?.let { fmtTime(it, zone(a)) } ?: "—",
                day.illuminationAtTransit?.let { "%.0f%%".format(it * 100) } ?: "—",
            ),
        )
    }
}

private fun hijri(a: Args) {
    val offset = a["offset"]?.toIntOrNull() ?: 0
    val h = engine.calendar.hijri(date(a), offset)
    println("${h.label} (offset applied: ${h.offsetApplied})")
}

private fun events(a: Args) {
    val month = a["month"]?.toIntOrNull() ?: die("missing --month")
    val year = a["year"]?.toIntOrNull() ?: die("missing --year")
    var d = YearMonth.of(year, month).atDay(1)
    while (d <= YearMonth.of(year, month).atEndOfMonth()) {
        for (event in engine.calendar.events(d)) {
            println("  $d · ${event.title} · ${event.hijriDateLabel}")
        }
        d = d.plusDays(1)
    }
}

private fun qibla(a: Args) {
    val b = engine.qibla.bearing(location(a))
    println("qibla bearing %.2f° from north · distance %.0f km".format(b.bearingDegFromNorth.value, b.greatCircleDistanceKm.value))
}

private fun repoRoot(): java.nio.file.Path {
    var dir = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    while (dir != null) {
        if (java.nio.file.Files.exists(dir.resolve("engine/prayer/src/test/resources/fixtures/prayer_golden.json"))) {
            return dir
        }
        dir = dir.parent
    }
    die("repo root not found — run from inside khushu-engine")
}

private fun verify(a: Args) {
    val checkPrayer = a["prayer"] != null || (a["prayer"] == null && a["astro"] == null)
    val checkAstro = a["astro"] != null || (a["prayer"] == null && a["astro"] == null)
    var totalFail = 0
    val root = repoRoot()

    if (checkPrayer) {
        val golden = Json { ignoreUnknownKeys = true }.decodeFromString<PGolden>(
            PGolden.serializer(),
            java.nio.file.Files.readString(root.resolve("engine/prayer/src/test/resources/fixtures/prayer_golden.json")),
        )
        var checked = 0
        var failed = 0
        for (case in golden.cases) {
            val s = golden.sites.getValue(case.site)
            val loc = Location.of(s.lat, s.lon, s.alt)
            val params = PrayerParams(
                madhab = if (case.madhab == "HANAFI") com.khushu.engine.prayer.Madhab.HANAFI else com.khushu.engine.prayer.Madhab.SHAFII,
                convention = conventionOf(case.convention),
                fajrAngle = case.fajrAngle ?: 18.0,
                ishaAngle = case.ishaAngle ?: 18.0,
                ishaIntervalMinutes = case.ishaInterval,
                highLatitudeRule = hlrOf(case.hlr),
                offsets = case.offsets?.let { o ->
                    com.khushu.engine.prayer.PrayerOffsets(fajr = o[0], sunrise = o[1], dhuhr = o[2], asr = o[3], sunset = 0, maghrib = o[4], isha = o[5])
                } ?: com.khushu.engine.prayer.PrayerOffsets(),
            )
            val result = engine.prayer.times(loc, LocalDate.parse(case.date), params)
            val actual = linkedMapOf(
                "fajr" to result.fajr, "sunrise" to result.sunrise, "dhuhr" to result.dhuhr,
                "asr" to result.asr, "maghrib" to result.maghrib, "isha" to result.isha,
                "middleOfNight" to result.midnight, "lastThirdOfNight" to result.lastThirdOfNight,
            )
            if (case.error == "UNCOMPUTABLE") {
                // Engine degrades gracefully: adhan2-sourced facts are null,
                // transit-derived facts (dhuhr/zawaal/dhuhrEnters) survive.
                if (!result.polarAnomaly ||
                    result.fajr != null || result.sunrise != null || result.asr != null ||
                    result.maghrib != null || result.isha != null ||
                    result.midnight != null || result.lastThirdOfNight != null
                ) failed++
            } else {
                for ((k, expected) in case.times) {
                    val got = actual[k]?.toEpochMilli()
                    if (expected != got) failed++
                }
            }
            checked++
        }
        println("prayer goldens : $checked cases, $failed mismatches → ${if (failed == 0) "PASS" else "FAIL"}")
        totalFail += failed
    }

    if (checkAstro) {
        val golden = Json { ignoreUnknownKeys = true }.decodeFromString<AGolden>(
            AGolden.serializer(),
            java.nio.file.Files.readString(root.resolve("engine/astronomy/src/test/resources/fixtures/astro_golden.json")),
        )
        var failed = 0
        for (case in golden.cases) {
            val s = golden.sites.getValue(case.site)
            val loc = Location.of(s.lat, s.lon, s.alt)
            val instant = java.time.Instant.ofEpochMilli(case.epochMs)
            val sun = engine.astronomy.sun.position(loc, instant)
            val moon = engine.astronomy.moon.position(loc, instant)
            val st = engine.astronomy.moon.state(loc, instant)
            val tol = 1e-9
            fun near(x: Double, y: Double) = kotlin.math.abs(x - y) <= tol
            if (!near(sun.azimuthDeg, case.sunAz) || !near(sun.altitudeDeg, case.sunAlt) ||
                !near(moon.azimuthDeg, case.moonAz) || !near(moon.altitudeDeg, case.moonAlt) ||
                !near(moon.distanceKm, case.moonDistKm) ||
                !near(st.illuminationFraction, case.illum) || !near(st.brightLimbTiltDeg, case.tiltDeg)
            ) failed++
        }
        println("astronomy goldens: ${golden.cases.size} cases, $failed mismatches → ${if (failed == 0) "PASS" else "FAIL"}")
        totalFail += failed
    }

    if (totalFail > 0) exitProcess(1)
}

private fun conventionOf(c: String?) = when (c) {
    null -> com.khushu.engine.prayer.Convention.CUSTOM
    "MWL" -> com.khushu.engine.prayer.Convention.MUSLIM_WORLD_LEAGUE
    "ISNA" -> com.khushu.engine.prayer.Convention.ISNA
    "EGYPT" -> com.khushu.engine.prayer.Convention.EGYPTIAN
    "MAKKAH" -> com.khushu.engine.prayer.Convention.UMM_AL_QURA
    "KARACHI" -> com.khushu.engine.prayer.Convention.KARACHI
    "DUBAI" -> com.khushu.engine.prayer.Convention.DUBAI
    "KUWAIT" -> com.khushu.engine.prayer.Convention.KUWAIT
    "QATAR" -> com.khushu.engine.prayer.Convention.QATAR
    "SINGAPORE" -> com.khushu.engine.prayer.Convention.SINGAPORE
    "TURKEY" -> com.khushu.engine.prayer.Convention.TURKEY
    "MOON_SIGHTING" -> com.khushu.engine.prayer.Convention.MOON_SIGHTING_COMMITTEE
    else -> die("unknown convention $c")
}

private fun hlrOf(h: String) = when (h) {
    "SEVENTH_OF_THE_NIGHT" -> com.khushu.engine.prayer.HighLatitudeRule.SEVENTH_OF_NIGHT
    "TWILIGHT_ANGLE" -> com.khushu.engine.prayer.HighLatitudeRule.TWILIGHT_ANGLE
    else -> com.khushu.engine.prayer.HighLatitudeRule.MIDDLE_OF_NIGHT
}

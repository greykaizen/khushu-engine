@file:OptIn(kotlin.time.ExperimentalTime::class)

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.HighLatitudeRule
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.SunnahTimes
import com.batoulapps.adhan2.data.DateComponents
import java.time.LocalDate

// Golden-master dumper: reproduces Osprey's exact adhan2 call path
// (PrayerCalculation.calculatePrayersForDate) with typed params instead of
// donor's UI strings. Same library, same version, same mapping -> identical math.

data class Site(
    val key: String,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val tz: String,
)

val SITES = listOf(
    Site("makkah", 21.4225, 39.8262, 277.0, "Asia/Riyadh"),
    Site("london", 51.5072, -0.1276, 11.0, "Europe/London"),
    Site("newyork", 40.7128, -74.0060, 10.0, "America/New_York"),
    Site("karachi", 24.8607, 67.0011, 10.0, "Asia/Karachi"),
    Site("sydney", -33.8688, 151.2093, 58.0, "Australia/Sydney"),
    Site("tromso", 69.6492, 18.9553, 20.0, "Europe/Oslo"),
    Site("apia", -13.8333, -171.7667, 10.0, "Pacific/Apia"), // international date line stress
)

val CONVENTIONS = mapOf(
    "MWL" to CalculationMethod.MUSLIM_WORLD_LEAGUE,
    "ISNA" to CalculationMethod.NORTH_AMERICA,
    "EGYPT" to CalculationMethod.EGYPTIAN,
    "MAKKAH" to CalculationMethod.UMM_AL_QURA,
    "KARACHI" to CalculationMethod.KARACHI,
    "DUBAI" to CalculationMethod.DUBAI,
    "KUWAIT" to CalculationMethod.KUWAIT,
    "QATAR" to CalculationMethod.QATAR,
    "SINGAPORE" to CalculationMethod.SINGAPORE,
    "TURKEY" to CalculationMethod.TURKEY,
    "MOON_SIGHTING" to CalculationMethod.MOON_SIGHTING_COMMITTEE,
)

fun buildParams(
    convention: String?,
    fajrAngle: Double? = null,
    ishaAngle: Double? = null,
    ishaIntervalMin: Int = 0,
    madhab: Madhab,
    hlr: HighLatitudeRule,
): CalculationParameters {
    val base = if (convention != null) {
        CONVENTIONS.getValue(convention).parameters
    } else {
        CalculationMethod.OTHER.parameters
    }
    return base.copy(
        fajrAngle = fajrAngle ?: base.fajrAngle,
        ishaAngle = ishaAngle ?: base.ishaAngle,
        ishaInterval = ishaIntervalMin,
        madhab = madhab,
        highLatitudeRule = hlr,
    )
}

data class Row(
    val siteKey: String,
    val date: LocalDate,
    val madhab: String,
    val convention: String?, // null => OTHER (custom angles)
    val fajrAngle: Double?,
    val ishaAngle: Double?,
    val ishaIntervalMin: Int,
    val hlr: String,
    val offsets: IntArray?, // [fajr,sunrise,dhuhr,asr,maghrib,isha] minutes or null
    val times: Map<String, Long?>,
    val error: String?, // non-null => adhan2 refused to compute (polar day/night)
)

val rows = mutableListOf<Row>()

fun compute(
    site: Site,
    date: LocalDate,
    madhabStr: String,
    convention: String?,
    fajrAngle: Double?,
    ishaAngle: Double?,
    ishaIntervalMin: Int,
    hlrStr: String,
    offsets: IntArray?,
) {
    val madhab = if (madhabStr == "HANAFI") Madhab.HANAFI else Madhab.SHAFI
    val hlr = when (hlrStr) {
        "SEVENTH_OF_THE_NIGHT" -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
        "TWILIGHT_ANGLE" -> HighLatitudeRule.TWILIGHT_ANGLE
        else -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
    }
    val params0 = buildParams(convention, fajrAngle, ishaAngle, ishaIntervalMin, madhab, hlr)
    // Apply offsets the way the engine will: via adhan2 PrayerAdjustments.
    val params = if (offsets != null) {
        params0.copy(prayerAdjustments = PrayerAdjustments(offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5]))
    } else {
        params0
    }
    val coords = Coordinates(site.lat, site.lon)
    val dc = DateComponents(date.year, date.monthValue, date.dayOfMonth)

    // Stage 1: obligatory times. Failure => fully uncomputable day.
    var pt: PrayerTimes? = null
    var ptError = false
    try {
        pt = PrayerTimes(coords, dc, params)
    } catch (e: IllegalStateException) {
        ptError = true
    }

    // Stage 2: sunnah windows (need next-day times too).
    var middleMs: Long? = null
    var lastThirdMs: Long? = null
    var stError = false
    if (pt != null) {
        try {
            val st = SunnahTimes(pt)
            middleMs = st.middleOfTheNight.toEpochMilliseconds()
            lastThirdMs = st.lastThirdOfTheNight.toEpochMilliseconds()
        } catch (e: IllegalStateException) {
            stError = true
        }
    }

    val times = linkedMapOf<String, Long?>(
        "fajr" to pt?.fajr?.toEpochMilliseconds(),
        "sunrise" to pt?.sunrise?.toEpochMilliseconds(),
        "dhuhr" to pt?.dhuhr?.toEpochMilliseconds(),
        "asr" to pt?.asr?.toEpochMilliseconds(),
        "maghrib" to pt?.maghrib?.toEpochMilliseconds(),
        "isha" to pt?.isha?.toEpochMilliseconds(),
        "middleOfNight" to middleMs,
        "lastThirdOfNight" to lastThirdMs,
    )
    val error = when {
        ptError -> "UNCOMPUTABLE"
        stError -> "SUNNAH_UNCOMPUTABLE"
        else -> null
    }
    rows.add(Row(site.key, date, madhabStr, convention, fajrAngle, ishaAngle, ishaIntervalMin, hlrStr, offsets, times, error))
}

fun esc(s: String?) =
    if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun num(v: Double?): String = if (v == null) "null" else v.toString()

fun rowJson(r: Row): String = buildString {
    append("    {\"site\":\"").append(r.siteKey).append("\",\"date\":\"").append(r.date).append('"')
    append(",\"madhab\":\"").append(r.madhab).append('"')
    append(",\"convention\":").append(esc(r.convention))
    append(",\"fajrAngle\":").append(num(r.fajrAngle))
    append(",\"ishaAngle\":").append(num(r.ishaAngle))
    append(",\"ishaInterval\":").append(r.ishaIntervalMin)
    append(",\"hlr\":\"").append(r.hlr).append('"')
    if (r.error != null) append(",\"error\":\"").append(r.error).append('"')
    if (r.offsets != null) {
        append(",\"offsets\":[").append(r.offsets.joinToString(",")).append(']')
    }
    append(",\"times\":{")
    append(r.times.entries.joinToString(",") { "\"${it.key}\":${it.value ?: "null"}" })
    append("}}")
}

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "--probe") {
        // Sanity probe: London midsummer sunrise should be ~04:43 BST (~03:43 UTC).
        val pt = PrayerTimes(
            Coordinates(51.5072, -0.1276),
            DateComponents(2026, 6, 21),
            buildParams("MWL", madhab = Madhab.SHAFI, hlr = HighLatitudeRule.MIDDLE_OF_THE_NIGHT),
        )
        println("fajr   utc=" + pt.fajr + "  epochMs=" + pt.fajr?.toEpochMilliseconds())
        println("sunrise utc=" + pt.sunrise + "  epochMs=" + pt.sunrise?.toEpochMilliseconds())
        println("dhuhr  utc=" + pt.dhuhr + "  epochMs=" + pt.dhuhr?.toEpochMilliseconds())
        return
    }

    val year = 2025
    val start = LocalDate.of(year, 1, 1)
    val days = if (start.isLeapYear) 366 else 365

    // Matrix A: all sites x full year x {SHAFI,HANAFI} x MWL x middle-of-night
    for (site in SITES) {
        for (d in 0 until days) {
            for (madhab in listOf("SHAFI", "HANAFI")) {
                compute(site, start.plusDays(d.toLong()), madhab, "MWL", null, null, 0, "MIDDLE_OF_THE_NIGHT", null)
            }
        }
    }

    // Matrix B: polar edge weeks at Tromso x all three high-latitude rules
    for (month in listOf(6, 12)) {
        for (day in 15..25) {
            for (hlr in listOf("MIDDLE_OF_THE_NIGHT", "SEVENTH_OF_THE_NIGHT", "TWILIGHT_ANGLE")) {
                compute(SITES.last(), LocalDate.of(year, month, day), "SHAFI", "MWL", null, null, 0, hlr, null)
            }
        }
    }

    // Matrix C: every convention sampled on the 1st of each month, all sites
    for (site in SITES) {
        for (month in 1..12) {
            for ((name, _) in CONVENTIONS) {
                compute(site, LocalDate.of(year, month, 1), "SHAFI", name, null, null, 0, "MIDDLE_OF_THE_NIGHT", null)
            }
        }
    }

    // Matrix D: custom angles, interval Isha, and minute offsets (spot checks)
    for (site in SITES) {
        for (month in listOf(1, 4, 7, 10)) {
            compute(site, LocalDate.of(year, month, 15), "HANAFI", null, 18.5, 17.0, 0, "MIDDLE_OF_THE_NIGHT", null)
            compute(site, LocalDate.of(year, month, 15), "SHAFI", null, 19.0, 17.0, 90, "SEVENTH_OF_THE_NIGHT", null)
            compute(
                site, LocalDate.of(year, month, 15), "SHAFI", "MWL", null, null, 0, "MIDDLE_OF_THE_NIGHT",
                intArrayOf(-2, 0, 3, 1, 0, 5),
            )
        }
    }

    val json = buildString {
        append("{\n")
        append("  \"meta\": {\n")
        append("    \"generator\": \"prayer-golden-dumper\",\n")
        append("    \"adhan\": \"com.batoulapps.adhan:adhan2-jvm:0.0.7\",\n")
        append("    \"donorPath\": \"Osprey app/src/main/java/com/kaizen/osprey/domain/prayer/PrayerCalculation.kt\",\n")
        append("    \"year\": ").append(year).append(",\n")
        append("    \"epochUnit\": \"millisecondsSinceUnixEpochUTC\"\n")
        append("  },\n")
        append("  \"sites\": {\n")
        append(SITES.joinToString(",\n") {
            "    \"${it.key}\": {\"lat\":${it.lat},\"lon\":${it.lon},\"alt\":${it.alt},\"tz\":\"${it.tz}\"}"
        })
        append("\n  },\n")
        append("  \"cases\": [\n")
        append(rows.joinToString(",\n") { rowJson(it) })
        append("\n  ]\n}\n")
    }

    val out = java.io.File(args.getOrElse(0) { "prayer_golden.json" })
    out.parentFile?.mkdirs()
    out.writeText(json)
    System.out.printf("wrote %d cases (%d bytes) to %s%n", rows.size, json.length, out.absolutePath)
}

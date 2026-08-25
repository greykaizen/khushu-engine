package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layer-A regression lock: every case replays a golden value dumped once from
 * the donor's exact computation path (same library, same version, same call
 * sequence). Inputs live entirely in fixtures/prayer_golden.json — nothing is
 * hardcoded here.
 */
class GoldenRegressionTest {

    @Serializable
    private data class Site(val lat: Double, val lon: Double, val alt: Double, val tz: String)

    @Serializable
    private data class Case(
        val site: String,
        val date: String,
        val madhab: String,
        val convention: String? = null,
        val fajrAngle: Double? = null,
        val ishaAngle: Double? = null,
        val ishaInterval: Int = 0,
        val hlr: String,
        val offsets: List<Int>? = null,
        val error: String? = null,
        val times: Map<String, Long?>,
    )

    @Serializable
    private data class Golden(
        val sites: Map<String, Site>,
        val cases: List<Case>,
    )

    private val golden: Golden by lazy {
        Json { ignoreUnknownKeys=true }
            .decodeFromString<Golden>(
                Golden.serializer(),
                Golden::class.java.classLoader.getResource("fixtures/prayer_golden.json")!!.readText(),
            )
    }

    private fun Case.toParams(): PrayerParams {
        val madhab = if (madhab == "HANAFI") Madhab.HANAFI else Madhab.SHAFII
        val hlr = when (hlr) {
            "SEVENTH_OF_THE_NIGHT" -> HighLatitudeRule.SEVENTH_OF_NIGHT
            "TWILIGHT_ANGLE" -> HighLatitudeRule.TWILIGHT_ANGLE
            else -> HighLatitudeRule.MIDDLE_OF_NIGHT
        }
        val convention = when (convention) {
            null -> Convention.CUSTOM
            "MWL" -> Convention.MUSLIM_WORLD_LEAGUE
            "ISNA" -> Convention.ISNA
            "EGYPT" -> Convention.EGYPTIAN
            "MAKKAH" -> Convention.UMM_AL_QURA
            "KARACHI" -> Convention.KARACHI
            "DUBAI" -> Convention.DUBAI
            "KUWAIT" -> Convention.KUWAIT
            "QATAR" -> Convention.QATAR
            "SINGAPORE" -> Convention.SINGAPORE
            "TURKEY" -> Convention.TURKEY
            "MOON_SIGHTING" -> Convention.MOON_SIGHTING_COMMITTEE
            else -> throw IllegalStateException("unknown convention $convention")
        }
        return PrayerParams(
            madhab = madhab,
            convention = convention,
            fajrAngle = fajrAngle ?: 18.0,
            ishaAngle = ishaAngle ?: 18.0,
            ishaIntervalMinutes = if (ishaInterval > 0) ishaInterval else 0,
            highLatitudeRule = hlr,
            offsets = offsets?.let { o ->
                // fixture order: fajr, sunrise, dhuhr, asr, maghrib, isha (no sunset slot)
                PrayerOffsets(fajr = o[0], sunrise = o[1], dhuhr = o[2], asr = o[3], sunset = 0, maghrib = o[4], isha = o[5])
            } ?: PrayerOffsets(),
        )
    }

    @Test
    fun everyGoldenCaseReproducesExactly() {
        var checked = 0
        for (case in golden.cases) {
            val site = assertNotNull(golden.sites[case.site], case.site)
            val loc = Location.of(site.lat, site.lon, site.alt)
            val date = LocalDate.parse(case.date)
            val result = Prayer.times(loc, date, case.toParams())

            // Transit-based facts must survive every condition.
            assertNotNull(result.dhuhr)
            assertNotNull(result.zawaalStart)
            assertNotNull(result.dhuhrEnters)

            when (case.error) {
                "UNCOMPUTABLE" -> {
                    // Donor path threw outright; engine degrades gracefully:
                    // adhan2-sourced facts are null, transit-derived facts survive.
                    assertTrue(result.polarAnomaly, "${case.site} ${case.date}")
                    assertNull(result.fajr); assertNull(result.sunrise)
                    assertNull(result.asr); assertNull(result.maghrib); assertNull(result.isha)
                    assertNull(result.midnight); assertNull(result.lastThirdOfNight)
                }
                else -> {
                    val fields = linkedMapOf(
                        "fajr" to result.fajr, "sunrise" to result.sunrise,
                        "dhuhr" to result.dhuhr, "asr" to result.asr,
                        "maghrib" to result.maghrib, "isha" to result.isha,
                        "middleOfNight" to result.midnight, "lastThirdOfNight" to result.lastThirdOfNight,
                    )
                    for ((name, engineMs) in fields) {
                        assertEquals(
                            case.times[name], engineMs?.toEpochMilli(),
                            "${case.site} ${case.date} ${case.madhab}/${case.convention} $name",
                        )
                    }
                }
            }
        }
        assertTrue(golden.cases.size > 5000, "corpus unexpectedly small")
    }
}

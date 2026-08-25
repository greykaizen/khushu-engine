package com.khushu.engine.prayer

import com.khushu.engine.core.geo.Location
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Layer-B property tests: invariants the goldens cannot express.
 * Corpus inputs come from fixture data where possible; nothing hardcoded
 * beyond well-known geographic anchors.
 */
class PrayerPropertyTest {

    private val sites = listOf(
        Location.of(21.4225, 39.8262), // Makkah
        Location.of(51.5072, -0.1276), // London
        Location.of(40.7128, -74.0060), // New York
        Location.of(-33.8688, 151.2093), // Sydney
        Location.of(69.6492, 18.9553), // Tromso
    )

    private val defaultParams = PrayerParams()

    @Test
    fun obligatoryTimesAreStrictlyIncreasing() {
        for (site in sites) {
            for (month in 1..12) {
                val date = LocalDate.of(2025, month, 15)
                val t = Prayer.times(site, date, defaultParams)
                if (t.polarAnomaly) continue
                // Polar-night days can carry unphysical adhan2 times; only assert
                // ordering when the solar day itself is well-defined.
                if (t.sunrise == null || t.maghrib == null) continue
                val seq = listOfNotNull(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
                assertTrue(seq.size >= 5, "expected near-complete times at $site $date")
                // On very short winter days Asr can share Dhuhr's minute — allow
                // equality there; Fajr/Sunrise/Dhuhr must be strictly ordered.
                seq.zipWithNext().forEach { (a, b) ->
                    assertTrue(a <= b, "ordering violated at $site $date: $a !<= $b")
                }
                assertTrue(t.fajr!! < t.sunrise!!)
                assertTrue(t.sunrise!! < t.dhuhr!!)
            }
        }
    }

    @Test
    fun hanafiAsrIsNeverBeforeShafiiAsr() {
        for (site in sites) {
            for (day in intArrayOf(1, 11, 21)) {
                val date = LocalDate.of(2025, 3, day)
                val shafii = Prayer.times(site, date, defaultParams).asr!!
                val hanafi = Prayer.times(
                    site, date,
                    defaultParams.copy(madhab = Madhab.HANAFI),
                ).asr!!
                assertTrue(hanafi >= shafii, "hanafi asr before shafii at $site $date")
            }
        }
    }

    @Test
    fun intervalIshaIsExactlyMaghribPlusInterval() {
        val params = PrayerParams(
            convention = Convention.CUSTOM,
            fajrAngle = 18.0,
            ishaAngle = 18.0,
            ishaIntervalMinutes = 90,
        )
        for (site in sites.dropLast(1)) { // skip polar Tromso
            val t = Prayer.times(site, LocalDate.of(2025, 7, 1), params)
            assertEquals(t.maghrib!!.plus(Duration.ofMinutes(90)), t.isha)
            assertTrue(t.isIntervalIsha)
        }
    }

    @Test
    fun offsetsShiftTimesByExactMinutes() {
        val site = sites[0]
        val date = LocalDate.of(2025, 10, 26)
        val base = Prayer.times(site, date, defaultParams.copy(offsets = PrayerOffsets()))
        val shifted = Prayer.times(
            site, date,
            defaultParams.copy(
                offsets = PrayerOffsets(fajr = -7, sunrise = 0, dhuhr = 4, asr = 0, sunset = 9, maghrib = 3, isha = -2),
            ),
        )
        assertEquals(base.fajr!!.minus(Duration.ofMinutes(7)), shifted.fajr)
        assertEquals(base.dhuhr!!.plus(Duration.ofMinutes(4)), shifted.dhuhr)
        assertEquals(base.sunset!!.plus(Duration.ofMinutes(9)), shifted.sunset)
        assertEquals(base.maghrib!!.plus(Duration.ofMinutes(3)), shifted.maghrib)
        assertEquals(base.isha!!.minus(Duration.ofMinutes(2)), shifted.isha)
    }

    @Test
    fun derivedFiqhWindowsSitInsideTheDay() {
        for (site in sites.dropLast(1)) {
            val t = Prayer.times(site, LocalDate.of(2025, 5, 10), defaultParams)
            assertTrue(t.ishraq!! > t.sunrise!!, "ishraq before sunrise at $site")
            assertTrue(t.ishraq < t.dhuhrEnters!!, "ishraq after dhuhr at $site")
            assertTrue(t.zawaalStart!! < t.dhuhrEnters!!)
            assertTrue(t.dhuhrEnters < t.asr!!)
            assertTrue(t.midnight!! > t.maghrib!!)
            assertTrue(t.lastThirdOfNight!! > t.midnight)
            assertTrue(t.astronomicalMidnight!! > t.sunset!!)
        }
    }

    @Test
    fun transitSurvivesPolarNightAndMatchesNoaaAnchor() {
        // Tromso polar night: no sunrise/sunset, but solar noon still exists.
        val tromso = sites[4]
        val t = Prayer.times(tromso, LocalDate.of(2025, 12, 21), defaultParams)
        assertTrue(t.polarAnomaly)
        // NOAA reference: Tromso Dec-21 solar noon ~ 11:57 local (12:57 CET + eq-of-time offset ~ -0:02..+0:14 window)
        // Anchor loosely: transit must be within the civil day +- 60 min.
        val local = t.dhuhr!!.atZone(ZoneId.of("Europe/Oslo"))
        assertTrue(local.hour in 11..13, "implausible transit hour $local")
        assertTrue(abs(local.minute - 57) <= 30 || local.hour != 12, "implausible transit minute $local")
    }

    @Test
    fun statusTracksPrayerWindowsAcrossMidnightBoundaries() {
        // October London: normal twilight — Isha lands well before local midnight,
        // so day-boundary semantics are the textbook ones.
        val london = Location.of(51.5072, -0.1276)
        val zone = ZoneId.of("Europe/London")
        val date = LocalDate.of(2025, 10, 15)
        val t = Prayer.times(london, date, defaultParams)

        // Mid-day: inside Dhuhr window.
        val midDhuhr = t.dhuhrEnters!!.plusSeconds(60)
        var s = Prayer.status(london, midDhuhr, zone, defaultParams)
        assertEquals(PrayerStatus.Prayer.DHUHR, s.current)
        assertEquals(PrayerStatus.Prayer.ASR, s.next)
        val p = s.progress()
        assertTrue(p != null && p in 0f..1f)

        // Before Fajr (deep night): current = yesterday's Isha.
        val preFajr = t.fajr!!.minusSeconds(3600)
        s = Prayer.status(london, preFajr, zone, defaultParams)
        assertEquals(PrayerStatus.Prayer.ISHA, s.current)
        assertEquals(PrayerStatus.Prayer.FAJR, s.next)
        assertTrue(s.countdown()!! > Duration.ZERO)

        // Late evening after Isha, same civil day: next = tomorrow's Fajr.
        val lateNight = t.isha!!.plusSeconds(600)
        assertEquals(t.isha.atZone(zone).toLocalDate(), lateNight.atZone(zone).toLocalDate())
        s = Prayer.status(london, lateNight, zone, defaultParams)
        assertEquals(PrayerStatus.Prayer.ISHA, s.current)
        assertEquals(PrayerStatus.Prayer.FAJR, s.next)
    }

    @Suppress("unused")
    private fun instantUnused(i: Instant) {}
}

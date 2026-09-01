package com.khushu.engine.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * v1.17 qadāʾ boundary computation — paired/unpaired per school, ḥayḍ
 * suppression, ghusl, provenance. Anchored to Darul Iftaa #8301 (granularity),
 * Sahih Muslim 335 (prayers not made up), and the design doc rev 3.
 */
class QadaTest {

    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.parse("2026-09-01")

    // A simplified but realistic prayer schedule for London in September.
    // Fajr 04:45, Dhuhr 13:05, Asr 16:50, Maghrib 19:35, Isha 21:05
    // (all UTC+1 = BST for September).
    private fun schedule(d: LocalDate): Qada.DaySchedule {
        fun at(h: Int, m: Int) = Instant.parse("2026-%02d-%02dT%02d:%02d:00Z".format(d.monthValue, d.dayOfMonth, h, m))
            .atZone(zone)!! // dummy — replaced below for BST
        // Use UTC directly for test simplicity (zone = UTC)
        fun utc(h: Int, m: Int) = d.atTime(h, m).atZone(java.time.ZoneOffset.UTC).toInstant()
        return Qada.DaySchedule(
            date = d,
            fajr = utc(4, 0),
            dhuhr = utc(12, 30),
            asr = utc(16, 0),
            maghrib = utc(19, 0),
            isha = utc(20, 30),
            nextDayFajr = d.plusDays(1).atTime(4, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            zoneId = java.time.ZoneOffset.UTC,
        )
    }

    private val schedule = schedule(date)
    private val schedules = listOf(schedule, schedule(date.plusDays(1)))

    private fun period(startH: Int, startM: Int, endH: Int, endM: Int, type: Qada.FiqhPeriodType = Qada.FiqhPeriodType.HAYD) =
        Qada.FiqhPeriod(
            start = date.atTime(startH, startM).atZone(java.time.ZoneOffset.UTC).toInstant(),
            end = date.plusDays(1).atTime(endH, endM).atZone(java.time.ZoneOffset.UTC).toInstant(),
            type = type,
        )

    // ── suppression ────────────────────────────────────────────────────────

    @Test
    fun allPrayersDuringFullDayPeriodAreSuppressed() {
        val p = period(0, 0, 23, 59)
        val report = Qada.qadaReport(listOf(p), schedules, Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        assertEquals(10, report.suppressedPrayers.size, "5 prayers × 2 days all suppressed")
        assertTrue(report.provenance.any { it.contains("Ḥanafī") })
    }

    @Test
    fun noPrayersSuppressedWhenNoPeriodOverlaps() {
        // Period ends before fajr
        val p = Qada.FiqhPeriod(
            start = date.minusDays(1).atTime(20, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            end = date.atTime(3, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            type = Qada.FiqhPeriodType.HAYD,
        )
        val report = Qada.qadaReport(listOf(p), schedules, Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        assertTrue(report.suppressedPrayers.none { it.date == date })
    }

    // ── boundary obligations ───────────────────────────────────────────────

    @Test
    fun purityDuringAsr_MajorityPairedOwesDhuhrAndAsr() {
        // Period ends at 15:00 (during Asr window 13:00–16:00... wait, Asr
        // starts at 16:00 in this schedule; purity at 15:00 = during Dhuhr)
        // Test with purity at 16:30 (during Asr time)
        val p = Qada.FiqhPeriod(
            start = date.minusDays(1).atTime(20, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            end = date.atTime(16, 30).atZone(java.time.ZoneOffset.UTC).toInstant(),
            type = Qada.FiqhPeriodType.HAYD,
        )
        val report = Qada.qadaReport(
            listOf(p), schedules,
            Qada.QadaConfig(Qada.QadaSchool.MALIKI, pairingOverride = Qada.BoundaryPairing.PAIRED),
        )
        // Asr owed (current prayer)
        assertTrue(report.boundaryObligations.any {
            it.kind == PrayerStatus.Prayer.ASR && it.date == date
        }, "Asr owed (current prayer when purity during Asr)")
        // Dhuhr owed (paired earlier prayer, majority)
        assertTrue(report.boundaryObligations.any {
            it.kind == PrayerStatus.Prayer.DHUHR && it.date == date
        }, "Dhuhr owed (paired earlier prayer)")
        // provenance
        assertTrue(report.provenance.any { it.contains("PAIRED") })
    }

    @Test
    fun purityDuringAsr_HanafiUnpairedOwesOnlyAsr() {
        val p = Qada.FiqhPeriod(
            start = date.minusDays(1).atTime(20, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            end = date.atTime(16, 30).atZone(java.time.ZoneOffset.UTC).toInstant(),
            type = Qada.FiqhPeriodType.HAYD,
        )
        val report = Qada.qadaReport(listOf(p), schedules, Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        assertTrue(report.boundaryObligations.any { it.kind == PrayerStatus.Prayer.ASR })
        assertTrue(
            report.boundaryObligations.none { it.kind == PrayerStatus.Prayer.DHUHR },
            "Hanafi UNPAIRED: Dhuhr NOT owed (only the prayer in whose time purity occurred)",
        )
        assertTrue(report.provenance.any { it.contains("UNPAIRED") })
    }

    @Test
    fun purityDuringIsha_MajorityOwesMaghribAndIsha() {
        val p = Qada.FiqhPeriod(
            start = date.minusDays(1).atTime(20, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            end = date.atTime(21, 0).atZone(java.time.ZoneOffset.UTC).toInstant(),
            type = Qada.FiqhPeriodType.HAYD,
        )
        val report = Qada.qadaReport(listOf(p), schedules, Qada.QadaConfig(Qada.QadaSchool.SHAFII))
        assertTrue(report.boundaryObligations.any { it.kind == PrayerStatus.Prayer.ISHA })
        assertTrue(report.boundaryObligations.any { it.kind == PrayerStatus.Prayer.MAGHRIB }, "Maghrib owed (paired)")
    }

    // ── school defaults ────────────────────────────────────────────────────

    @Test
    fun schoolDefaultsAreCorrect() {
        assertEquals(Qada.BoundaryPairing.UNPAIRED, Qada.defaultPairing(Qada.QadaSchool.HANAFI))
        assertEquals(Qada.BoundaryPairing.PAIRED, Qada.defaultPairing(Qada.QadaSchool.MALIKI))
        assertEquals(Qada.BoundaryPairing.PAIRED, Qada.defaultPairing(Qada.QadaSchool.SHAFII))
        assertEquals(Qada.BoundaryPairing.PAIRED, Qada.defaultPairing(Qada.QadaSchool.HANBALI))
        assertEquals(true, Qada.defaultIncludeWitr(Qada.QadaSchool.HANAFI))
        assertEquals(false, Qada.defaultIncludeWitr(Qada.QadaSchool.SHAFII))
    }

    // ── ghusl ──────────────────────────────────────────────────────────────

    @Test
    fun ghuslRequiredAtPeriodEnd() {
        val p = period(0, 0, 15, 0)
        val report = Qada.qadaReport(listOf(p), schedules, Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        assertTrue(report.ghuslRequiredAt.isNotEmpty(), "ghusl required at period end")
    }

    // ── validation ────────────────────────────────────────────────────────

    @Test
    fun emptyPeriodsRejected() {
        assertFailsWith<IllegalArgumentException> {
            Qada.qadaReport(emptyList(), schedules, Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        }
    }

    @Test
    fun emptySchedulesRejected() {
        assertFailsWith<IllegalArgumentException> {
            Qada.qadaReport(listOf(period(0, 0, 23, 59)), emptyList(), Qada.QadaConfig(Qada.QadaSchool.HANAFI))
        }
    }
}

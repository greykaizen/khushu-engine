package com.khushu.engine.calendar

import com.khushu.engine.core.error.InvalidParameterException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Authoritative anchor tests. Provenance per rule set:
 * - Persian: official 1354–1419 SH correspondence table (Wikipedia, "Solar
 *   Hijri calendar") including leap marks; today-anchor 2026-08-28 = 6 Shahrivar 1405.
 * - Indian national: Calendar Reform Committee month-start table + adoption
 *   anchor 1 Chaitra 1879 SE = 22 March 1957 (Wikipedia, "Indian national calendar").
 * - Bangla: Bangladesh 2019-present revision table (Wikipedia, "Bangladeshi
 *   national calendar"); today-anchor 2026-08-28 = 13 Bhadro 1433.
 * - Coptic/Ethiopian: Wikipedia articles; today-anchors 22 Mesori 1742 /
 *   22 Nähase 2018; leap years ≡ 3 mod 4 (Neyrouz/Enkutatash Sep 11/12).
 */
class RegionalCalendarsTest {

    // ── Persian ────────────────────────────────────────────────────────────────

    // Official Nowruz dates: SH year → Gregorian start date, * = leap.
    // Scattered anchors across the table (Wikipedia, "Solar Hijri calendar").
    private val nowruzAnchors = listOf(
        Triple(1354, LocalDate.of(1975, 3, 21), true),
        Triple(1370, LocalDate.of(1991, 3, 21), true),
        Triple(1375, LocalDate.of(1996, 3, 20), true),
        Triple(1379, LocalDate.of(2000, 3, 20), true),
        Triple(1416, LocalDate.of(2037, 3, 20), true),
        Triple(1419, LocalDate.of(2040, 3, 20), false),
    )

    // A fully consecutive run 1395–1409 covering both 4-gap and 5-gap leap
    // intervals (1403→1408), for year-length and Esfand-length verification.
    private val nowruzConsecutive = listOf(
        Triple(1395, LocalDate.of(2016, 3, 20), true),
        Triple(1396, LocalDate.of(2017, 3, 21), false),
        Triple(1397, LocalDate.of(2018, 3, 21), false),
        Triple(1398, LocalDate.of(2019, 3, 21), false),
        Triple(1399, LocalDate.of(2020, 3, 20), true),
        Triple(1400, LocalDate.of(2021, 3, 21), false),
        Triple(1401, LocalDate.of(2022, 3, 21), false),
        Triple(1402, LocalDate.of(2023, 3, 21), false),
        Triple(1403, LocalDate.of(2024, 3, 20), true),
        Triple(1404, LocalDate.of(2025, 3, 21), false),
        Triple(1405, LocalDate.of(2026, 3, 21), false),
        Triple(1406, LocalDate.of(2027, 3, 21), false),
        Triple(1407, LocalDate.of(2028, 3, 20), false),
        Triple(1408, LocalDate.of(2029, 3, 20), true),
        Triple(1409, LocalDate.of(2030, 3, 21), false),
    )

    @Test
    fun persianNowruzMatchesOfficialCorrespondenceTable() {
        for ((jy, start, _) in nowruzAnchors + nowruzConsecutive) {
            val r = RegionalCalendars.toRegional(start, CivilCalendarType.PERSIAN)
            assertEquals(Triple(jy, 1, 1), Triple(r.year, r.month, r.day), "Nowruz $jy")
        }
        // Consecutive entries: year length is 366 iff the year is marked
        // leap, which also fixes the Esfand length of that year.
        for ((a, b) in nowruzConsecutive.zipWithNext()) {
            val (jy, start, leap) = a
            val gap = java.time.temporal.ChronoUnit.DAYS.between(start, b.second)
            assertEquals(if (leap) 366L else 365L, gap, "length of SH year $jy")
            if (leap) {
                RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, jy, 12, 30)
            } else {
                assertFailsWith<InvalidParameterException> {
                    RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, jy, 12, 30)
                }
            }
        }
    }

    @Test
    fun persianTodayAnchorAndMonthStructure() {
        // Wikipedia today-box anchor: 2026-08-28 CE = 6 Shahrivar 1405 SH.
        val r = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.PERSIAN)
        assertEquals(Triple(1405, 6, 6), Triple(r.year, r.month, r.day))
        assertEquals("Shahrivar", r.monthName)

        // months 1-6 have 31 days: 31 Ordibehesht exists, 32 does not
        RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, 1405, 2, 31)
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, 1405, 2, 32)
        }
        // Esfand: 1399 is leap (30 days), 1400 is common (29 days)
        RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, 1399, 12, 30)
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, 1400, 12, 30)
        }
    }

    @Test
    fun persianRoundTripsAcrossYears() {
        var d = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2050, 12, 31)
        while (!d.isAfter(end)) {
            val r = RegionalCalendars.toRegional(d, CivilCalendarType.PERSIAN)
            assertEquals(d, RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, r.year, r.month, r.day))
            d = d.plusDays(1)
        }
    }

    // ── Indian national (Śaka) ─────────────────────────────────────────────────

    @Test
    fun sakaAdoptionAndEraAnchors() {
        // Adoption anchor: 1 Chaitra 1879 SE = 22 March 1957.
        val adopted = RegionalCalendars.toRegional(LocalDate.of(1957, 3, 22), CivilCalendarType.INDIAN_NATIONAL)
        assertEquals(Triple(1879, 1, 1), Triple(adopted.year, adopted.month, adopted.day))

        // Independent headline anchor: Saka New Year 1941 = 22 March 2019.
        val y1941 = RegionalCalendars.toRegional(LocalDate.of(2019, 3, 22), CivilCalendarType.INDIAN_NATIONAL)
        assertEquals(Triple(1941, 1, 1), Triple(y1941.year, y1941.month, y1941.day))

        // Leap-year Chaitra: 2024 is a Gregorian leap year → Saka 1946 starts 21 March with a 31-day Chaitra.
        val y1946 = RegionalCalendars.toRegional(LocalDate.of(2024, 3, 21), CivilCalendarType.INDIAN_NATIONAL)
        assertEquals(Triple(1946, 1, 1), Triple(y1946.year, y1946.month, y1946.day))
        val dayBefore = RegionalCalendars.toRegional(LocalDate.of(2024, 3, 20), CivilCalendarType.INDIAN_NATIONAL)
        assertEquals(Triple(1945, 12, 30), Triple(dayBefore.year, dayBefore.month, dayBefore.day))

        // January–March before the new year belong to the previous Saka year.
        assertEquals(1947, RegionalCalendars.toRegional(LocalDate.of(2026, 1, 1), CivilCalendarType.INDIAN_NATIONAL).year)
    }

    @Test
    fun sakaMonthStartTableForCommonYear() {
        // Official month-start table (common year), applied to Saka 1948 / CE 2026:
        val starts = listOf(
            Triple(2, LocalDate.of(2026, 4, 21), "Vaishakha"),
            Triple(3, LocalDate.of(2026, 5, 22), "Jyeshtha"),
            Triple(4, LocalDate.of(2026, 6, 22), "Ashadha"),
            Triple(5, LocalDate.of(2026, 7, 23), "Shravana"),
            Triple(6, LocalDate.of(2026, 8, 23), "Bhadrapada"),
            Triple(7, LocalDate.of(2026, 9, 23), "Ashvina"),
            Triple(8, LocalDate.of(2026, 10, 23), "Kartika"),
            Triple(9, LocalDate.of(2026, 11, 22), "Agrahayana"),
            Triple(10, LocalDate.of(2026, 12, 22), "Pausha"),
            Triple(11, LocalDate.of(2027, 1, 21), "Magha"),
            Triple(12, LocalDate.of(2027, 2, 20), "Phalguna"),
        )
        for ((month, date, name) in starts) {
            val r = RegionalCalendars.toRegional(date, CivilCalendarType.INDIAN_NATIONAL)
            assertEquals(Triple(1948, month, 1), Triple(r.year, r.month, r.day), "$name start")
            assertEquals(name, r.monthName)
        }
        // Chaitra 31 exists only in leap years.
        RegionalCalendars.fromRegional(CivilCalendarType.INDIAN_NATIONAL, 1946, 1, 31)
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.INDIAN_NATIONAL, 1947, 1, 31)
        }
    }

    @Test
    fun sakaRoundTripsAcrossYears() {
        var d = LocalDate.of(1970, 1, 1)
        val end = LocalDate.of(2090, 12, 31)
        while (!d.isAfter(end)) {
            val r = RegionalCalendars.toRegional(d, CivilCalendarType.INDIAN_NATIONAL)
            assertEquals(d, RegionalCalendars.fromRegional(CivilCalendarType.INDIAN_NATIONAL, r.year, r.month, r.day))
            d = d.plusDays(1)
        }
    }

    // ── Bangla (Bangladesh, 2019-present revision) ─────────────────────────────

    @Test
    fun banglaAnchorsFromThe2019Revision() {
        // Boishakh 1 = 14 April: year 1433 began 2026-04-14.
        val newYear = RegionalCalendars.toRegional(LocalDate.of(2026, 4, 14), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1433, 1, 1), Triple(newYear.year, newYear.month, newYear.day))
        assertEquals("Boishakh", newYear.monthName)

        // Wikipedia today-box anchor: 2026-08-28 CE = 13 Bhadro BS 1433 (Bhadro = month 5).
        val today = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1433, 5, 13), Triple(today.year, today.month, today.day))

        // The 2019 revision's sixth 31-day month: Ashbin runs 16 Sep – 16 Oct.
        val ashbin31 = RegionalCalendars.toRegional(LocalDate.of(2026, 10, 16), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1433, 6, 31), Triple(ashbin31.year, ashbin31.month, ashbin31.day))
        val kartik1 = RegionalCalendars.toRegional(LocalDate.of(2026, 10, 17), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1433, 7, 1), Triple(kartik1.year, kartik1.month, kartik1.day))

        // Revision effective from 16 October 2019: Kartik started Thursday 17 October 2019.
        val kartik2019 = RegionalCalendars.toRegional(LocalDate.of(2019, 10, 17), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1426, 7, 1), Triple(kartik2019.year, kartik2019.month, kartik2019.day))
    }

    @Test
    fun banglaLeapYearIsBoundToTheGregorianLeapYearContainingFalgun() {
        // Year 1430 ran 14 Apr 2023 – 13 Apr 2024: it spans 29 Feb 2024, so its
        // Falgun (Feb–Mar 2024) has 30 days.
        val falgun1 = RegionalCalendars.toRegional(LocalDate.of(2024, 2, 14), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1430, 11, 1), Triple(falgun1.year, falgun1.month, falgun1.day))
        val falgun30 = RegionalCalendars.toRegional(LocalDate.of(2024, 3, 14), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1430, 11, 30), Triple(falgun30.year, falgun30.month, falgun30.day))
        val chaitra1 = RegionalCalendars.toRegional(LocalDate.of(2024, 3, 15), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1430, 12, 1), Triple(chaitra1.year, chaitra1.month, chaitra1.day))
        val yearEnd = RegionalCalendars.toRegional(LocalDate.of(2024, 4, 13), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1430, 12, 30), Triple(yearEnd.year, yearEnd.month, yearEnd.day))

        // Common year 1433: Falgun has 29 days.
        val falgun29 = RegionalCalendars.toRegional(LocalDate.of(2027, 3, 14), CivilCalendarType.BANGLA_BANGLADESH)
        assertEquals(Triple(1433, 11, 29), Triple(falgun29.year, falgun29.month, falgun29.day))
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.BANGLA_BANGLADESH, 1433, 11, 30)
        }
        RegionalCalendars.fromRegional(CivilCalendarType.BANGLA_BANGLADESH, 1430, 11, 30)
    }

    @Test
    fun banglaRoundTripsAcrossYears() {
        var d = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2090, 12, 31)
        while (!d.isAfter(end)) {
            val r = RegionalCalendars.toRegional(d, CivilCalendarType.BANGLA_BANGLADESH)
            assertEquals(d, RegionalCalendars.fromRegional(CivilCalendarType.BANGLA_BANGLADESH, r.year, r.month, r.day))
            d = d.plusDays(1)
        }
    }

    // ── Coptic ─────────────────────────────────────────────────────────────────

    @Test
    fun copticAnchorsAndLeapCycle() {
        // Today-box anchor: 2026-08-28 CE = 22 Mesori AM 1742.
        val today = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.COPTIC)
        assertEquals(Triple(1742, 12, 22), Triple(today.year, today.month, today.day))
        assertEquals("Mesori", today.monthName)

        // Neyrouz: 11 September, or 12 September in the year before a Gregorian leap year.
        assertEquals(Triple(1742, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2025, 9, 11), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1743, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2026, 9, 11), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        // 2027 precedes Gregorian leap 2028 and AM 1743 ≡ 3 mod 4 is leap (6-day little month).
        assertEquals(Triple(1744, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 12), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1743, 13, 1), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 6), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1743, 13, 6), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 11), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        // AM 1742 is common: little month has 5 days.
        assertEquals(Triple(1742, 13, 1), RegionalCalendars.toRegional(LocalDate.of(2026, 9, 6), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1742, 13, 5), RegionalCalendars.toRegional(LocalDate.of(2026, 9, 10), CivilCalendarType.COPTIC).let { Triple(it.year, it.month, it.day) })

        // Reverse conversion agrees.
        assertEquals(LocalDate.of(2026, 8, 28), RegionalCalendars.fromRegional(CivilCalendarType.COPTIC, 1742, 12, 22))
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.COPTIC, 1742, 13, 6)
        }
        assertEquals("Pi Kogi Enavot", RegionalCalendars.monthNames(CivilCalendarType.COPTIC)[12])
    }

    // ── Ethiopian ──────────────────────────────────────────────────────────────

    @Test
    fun ethiopianAnchorsAndLeapCycle() {
        // Today-box anchor: 2026-08-28 CE = 22 Nähase 2018 Amätä Məhrät.
        val today = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.ETHIOPIAN)
        assertEquals(Triple(2018, 12, 22), Triple(today.year, today.month, today.day))
        assertEquals("Nehase", today.monthName)

        // Enkutatash (New Year): year 2018 is common (≡ 2 mod 4), so 2019 begins 11 Sep 2026.
        assertEquals(Triple(2019, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2026, 9, 11), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })
        // Year 2019 ≡ 3 mod 4 is leap: 6-day Pagume, so 2020 begins 12 Sep 2027.
        assertEquals(Triple(2019, 13, 1), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 6), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(2019, 13, 6), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 11), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(2020, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2027, 9, 12), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })

        // Wikipedia new-year anchors: 1998 → 11 Sep 2005; 1992 → 12 Sep 1999; 1996 → 12 Sep 2003.
        assertEquals(Triple(1998, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2005, 9, 11), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1992, 1, 1), RegionalCalendars.toRegional(LocalDate.of(1999, 9, 12), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })
        assertEquals(Triple(1996, 1, 1), RegionalCalendars.toRegional(LocalDate.of(2003, 9, 12), CivilCalendarType.ETHIOPIAN).let { Triple(it.year, it.month, it.day) })

        // Reverse conversion agrees; year numbering matches (2026−8 between Jan 1 and Enkutatash).
        assertEquals(LocalDate.of(2026, 8, 28), RegionalCalendars.fromRegional(CivilCalendarType.ETHIOPIAN, 2018, 12, 22))
        assertEquals(2018, RegionalCalendars.toRegional(LocalDate.of(2026, 1, 1), CivilCalendarType.ETHIOPIAN).year)
    }

    // ── Era-based systems (JDK chronologies) ───────────────────────────────────

    @Test
    fun japaneseEraBoundaries() {
        val heisei = RegionalCalendars.toRegional(LocalDate.of(2019, 4, 30), CivilCalendarType.JAPANESE)
        assertEquals("Heisei", heisei.eraLabel)
        assertEquals(31, heisei.year)
        val reiwa = RegionalCalendars.toRegional(LocalDate.of(2019, 5, 1), CivilCalendarType.JAPANESE)
        assertEquals("Reiwa", reiwa.eraLabel)
        assertEquals(Triple(1, 5, 1), Triple(reiwa.year, reiwa.month, reiwa.day))
        // today-box anchor: 2026-08-28 = Reiwa 8-08-28.
        assertEquals(8, RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.JAPANESE).year)
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.JAPANESE, 8, 8, 28)
        }
    }

    @Test
    fun minguoAndThaiBuddhistYearOffsets() {
        val minguo = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.MINGUO)
        assertEquals(115, minguo.year) // 2026 − 1911
        assertEquals(LocalDate.of(2026, 8, 28), RegionalCalendars.fromRegional(CivilCalendarType.MINGUO, 115, 8, 28))

        val thai = RegionalCalendars.toRegional(LocalDate.of(2026, 8, 28), CivilCalendarType.THAI_BUDDHIST)
        assertEquals(2569, thai.year) // 2026 + 543
        assertEquals(LocalDate.of(2026, 8, 28), RegionalCalendars.fromRegional(CivilCalendarType.THAI_BUDDHIST, 2569, 8, 28))
    }

    @Test
    fun gregorianIsTheIdentitySystem() {
        val d = LocalDate.of(2026, 8, 28)
        val r = RegionalCalendars.toRegional(d, CivilCalendarType.GREGORIAN)
        assertEquals(Triple(2026, 8, 28), Triple(r.year, r.month, r.day))
        assertEquals(d, RegionalCalendars.fromRegional(CivilCalendarType.GREGORIAN, 2026, 8, 28))
    }

    @Test
    fun monthNameListsHaveCorrectShapes() {
        assertEquals(12, RegionalCalendars.monthNames(CivilCalendarType.PERSIAN).size)
        assertEquals(12, RegionalCalendars.monthNames(CivilCalendarType.INDIAN_NATIONAL).size)
        assertEquals(12, RegionalCalendars.monthNames(CivilCalendarType.BANGLA_BANGLADESH).size)
        assertEquals(13, RegionalCalendars.monthNames(CivilCalendarType.COPTIC).size)
        assertEquals(13, RegionalCalendars.monthNames(CivilCalendarType.ETHIOPIAN).size)
        assertEquals("Farvardin", RegionalCalendars.monthNames(CivilCalendarType.PERSIAN).first())
        assertEquals("Esfand", RegionalCalendars.monthNames(CivilCalendarType.PERSIAN).last())
    }

    @Test
    fun roundTripsHoldForAllChronologyBackedSystems() {
        val systems = listOf(CivilCalendarType.COPTIC, CivilCalendarType.ETHIOPIAN)
        for (system in systems) {
            var d = LocalDate.of(1950, 1, 1)
            val end = LocalDate.of(2080, 12, 31)
            while (!d.isAfter(end)) {
                val r = RegionalCalendars.toRegional(d, system)
                assertEquals(d, RegionalCalendars.fromRegional(system, r.year, r.month, r.day), "$system at $d")
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun persianRangeOutsideSupportedWindowIsRejected() {
        assertFailsWith<InvalidParameterException> {
            RegionalCalendars.fromRegional(CivilCalendarType.PERSIAN, 3200, 1, 1)
        }
    }

    @Test
    fun consecutiveDaysAdvanceMonotonically() {
        val systems = CivilCalendarType.entries.filter { it != CivilCalendarType.JAPANESE }
        val d = LocalDate.of(2026, 2, 27)
        for (system in systems) {
            val seq = (0..5).map { RegionalCalendars.toRegional(d.plusDays(it.toLong()), system) }
            val ranks = seq.map { it.year * 10000 + it.month * 100 + it.day }
            assertTrue(ranks.zipWithNext().all { (a, b) -> b > a }, "$system did not advance: $ranks")
        }
    }
}

package com.khushu.engine.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * v1.15 sighted-calendar — anchored derivation over host-supplied
 * announcements (docs/sighting-mode-design.md, un-gated per the
 * astronomy-plausibility + human-confirmation model).
 *
 * Tabular ground truth (Umm al-Qura, verified): 2025-12-30 = 1447-07-10,
 * 2026-01-29 = 1447-08-10, 2026-03-01 = 1447-09-12, 2026-06-15 = 1447-12-29.
 * Announcements deliberately DIVERGE from tabular (that's the feature).
 */
class SightedCalendarTest {

    // Committee announces Ramadan 1447 starting 2026-01-08 (tabular 1447-07-19) — diverges
    // from tabular 1447-09 (starting 2026-03-12).
    // Realism note: real divergences are ±1 day; this exaggerated gap makes
    // every assertion unambiguous about WHICH calendar a day came from.
    private val announcedRamadan = SightedCalendar.AnnouncedMonthStart(
        hijriYear = 1447,
        hijriMonth = 9,
        observedFirstDay = LocalDate.of(2026, 1, 8),
        authority = "TestMoonsightingCommittee",
    )
    private val ramadanStart = LocalDate.of(2026, 1, 8)

    private fun params(
        vararg announcements: SightedCalendar.AnnouncedMonthStart,
        localOffsetDays: Int = 0,
        tabularOffsetDays: Int = 0,
    ) = SightedCalendar.SightedCalendarParams(
        announcements = announcements.toList(),
        localOffsetDays = localOffsetDays,
        tabularOffsetDays = tabularOffsetDays,
    )

    // ── anchor derivation ──────────────────────────────────────────────────

    @Test
    fun announcedMonthDerivesFromAnchor() {
        val p = params(announcedRamadan)
        val d1 = SightedCalendar.hijriSighted(ramadanStart, p)
        assertTrue(d1.fromAnnouncement)
        assertEquals("TestMoonsightingCommittee", d1.anchoredBy)
        assertEquals(1447, d1.date.year)
        assertEquals(9, d1.date.month)
        assertEquals(1, d1.date.day)

        // Day 15 = anchor + 14
        val d15 = SightedCalendar.hijriSighted(ramadanStart.plusDays(14), p)
        assertTrue(d15.fromAnnouncement)
        assertEquals(15, d15.date.day)

        // Day 30 = anchor + 29
        val d30 = SightedCalendar.hijriSighted(ramadanStart.plusDays(29), p)
        assertTrue(d30.fromAnnouncement)
        assertEquals(30, d30.date.day)
    }

    @Test
    fun dayBeforeAnchorFallsBackToTabular() {
        val p = params(announcedRamadan)
        val before = SightedCalendar.hijriSighted(ramadanStart.minusDays(1), p)
        assertTrue(!before.fromAnnouncement)
        assertEquals(null, before.anchoredBy)
        // 2026-01-07 = tabular 1447-07-18 (verified against the Umm al-Qura table)
        assertEquals(7, before.date.month)
        assertEquals(18, before.date.day)
    }

    @Test
    fun unannouncedMonthsStayTabular() {
        val p = params(announcedRamadan)
        val date = LocalDate.of(2026, 6, 15)
        val mid = SightedCalendar.hijriSighted(date, p)
        assertTrue(!mid.fromAnnouncement)
        assertEquals(HijriCalendar.hijri(date), mid.date)
    }

    @Test
    fun nextAnnouncementBoundsPreviousMonth() {
        // Shawwal announced 29 days after the Ramadan anchor (month ran 29
        // days) → Ramadan day 30 must not exist; the anchor+29 civil day is
        // already Shawwal 1.
        val shawwal = SightedCalendar.AnnouncedMonthStart(
            hijriYear = 1447, hijriMonth = 10,
            observedFirstDay = ramadanStart.plusDays(29),
            authority = "TestMoonsightingCommittee",
        )
        val p = params(announcedRamadan, shawwal)

        val lastRamadan = SightedCalendar.hijriSighted(ramadanStart.plusDays(28), p)
        assertEquals(9, lastRamadan.date.month)
        assertEquals(29, lastRamadan.date.day)

        val shawwalD1 = SightedCalendar.hijriSighted(ramadanStart.plusDays(29), p)
        assertEquals(10, shawwalD1.date.month)
        assertEquals(1, shawwalD1.date.day)
        assertTrue(shawwalD1.fromAnnouncement)
    }

    // ── localOffsetDays ───────────────────────────────────────────────────

    @Test
    fun localOffsetShiftsWhichDayDerives() {
        val p = params(announcedRamadan, localOffsetDays = 1)
        // effective = civil + 1 → 01-07 behaves as 01-08 = Ramadan 1
        val d = SightedCalendar.hijriSighted(ramadanStart.minusDays(1), p)
        assertTrue(d.fromAnnouncement)
        assertEquals(1, d.date.day)
    }

    @Test
    fun localOffsetValidatedRange() {
        assertFailsWith<IllegalArgumentException> { params(announcedRamadan, localOffsetDays = 3) }
        assertFailsWith<IllegalArgumentException> { params(announcedRamadan, localOffsetDays = -3) }
    }

    // ── contradiction rejection ────────────────────────────────────────────

    @Test
    fun contradictoryAnnouncementsAreRejectedAtConstruction() {
        val rival = announcedRamadan.copy(
            observedFirstDay = ramadanStart.plusDays(1),
            authority = "RivalCommittee",
        )
        val ex = assertFailsWith<IllegalArgumentException> { params(announcedRamadan, rival) }
        assertTrue(ex.message!!.contains("contradictory"), ex.message)
    }

    @Test
    fun sameAnchorFromMultipleAuthoritiesIsFine() {
        val echo = announcedRamadan.copy(authority = "SecondWitness")
        val p = params(announcedRamadan, echo)
        val d = SightedCalendar.hijriSighted(ramadanStart, p)
        assertEquals(1, d.date.day)
    }

    // ── civilOf inverse ───────────────────────────────────────────────────

    @Test
    fun civilOfInvertsAnnouncedMonths() {
        val p = params(announcedRamadan)
        assertEquals(ramadanStart, SightedCalendar.civilOf(1447, 9, 1, p))
        assertEquals(ramadanStart.plusDays(14), SightedCalendar.civilOf(1447, 9, 15, p))
        assertEquals(ramadanStart.plusDays(29), SightedCalendar.civilOf(1447, 9, 30, p))
        // unannounced month → tabular
        val tabular = HijriCalendar.hijriToGregorian(1447, 7, 15, 0)
        assertEquals(tabular, SightedCalendar.civilOf(1447, 7, 15, p))
    }

    @Test
    fun civilOfRoundTripsWithLocalOffset() {
        val p = params(announcedRamadan, localOffsetDays = 1)
        // civilOf applies the offset back: hijri day 1 answers the civil day
        // whose effective+1 is the anchor → anchor − 1
        assertEquals(ramadanStart.minusDays(1), SightedCalendar.civilOf(1447, 9, 1, p))
    }

    @Test
    fun civilOfValidatesDayRange() {
        assertFailsWith<IllegalArgumentException> { SightedCalendar.civilOf(1447, 9, 0, params(announcedRamadan)) }
        assertFailsWith<IllegalArgumentException> { SightedCalendar.civilOf(1447, 9, 31, params(announcedRamadan)) }
    }

    // ── events under sighted mode ─────────────────────────────────────────

    @Test
    fun eidAlFitrFollowsAnnouncedShawwal() {
        val shawwal = SightedCalendar.AnnouncedMonthStart(
            hijriYear = 1447, hijriMonth = 10,
            observedFirstDay = ramadanStart.plusDays(29),
            authority = "TestMoonsightingCommittee",
        )
        val p = params(announcedRamadan, shawwal)
        val events = SightedCalendar.eventsSighted(
            ramadanStart.plusDays(25)..ramadanStart.plusDays(40),
            p,
        )
        val eid = events.firstOrNull { it.second.id == "eid_al_fitr" }
        assertTrue(eid != null, "eid_al_fitr present in announced Shawwal")
        assertEquals(ramadanStart.plusDays(29), eid!!.first, "Eid on announced Shawwal day 1")
    }

    @Test
    fun eventsRangeValidated() {
        assertFailsWith<IllegalArgumentException> {
            SightedCalendar.eventsSighted(
                LocalDate.of(2026, 1, 1)..LocalDate.of(2028, 1, 1),
                params(announcedRamadan),
            )
        }
    }

    // ── announcements never mutate tabular facts ───────────────────────────

    @Test
    fun tabularOutputUnchangedByAnnouncements() {
        val p = params(announcedRamadan)
        val date = LocalDate.of(2026, 3, 1)
        assertEquals(HijriCalendar.hijri(date), HijriCalendar.hijri(date))
        // sighted mode for an unannounced month equals tabular exactly
        assertEquals(HijriCalendar.hijri(date), SightedCalendar.hijriSighted(date, p).date)
    }
}

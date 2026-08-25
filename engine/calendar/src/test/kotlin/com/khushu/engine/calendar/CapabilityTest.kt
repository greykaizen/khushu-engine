package com.khushu.engine.calendar

import com.khushu.engine.core.geo.Location
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapabilityTest {

    private val config = CalendarConfiguration(
        primary = CalendarConfiguration.Side.HIJRI,
        secondary = CalendarConfiguration.Side.GREGORIAN,
    )

    @Test
    fun configurationRequiresExactlyOneIslamicSide() {
        // Both GREGORIAN → rejected.
        assertFailsWith<IllegalArgumentException> {
            CalendarConfiguration(CalendarConfiguration.Side.GREGORIAN, CalendarConfiguration.Side.GREGORIAN)
        }
        // HIJRI primary ✓ / HIJRI secondary ✓ both legal.
        CalendarConfiguration(CalendarConfiguration.Side.HIJRI)
        CalendarConfiguration(CalendarConfiguration.Side.GREGORIAN, CalendarConfiguration.Side.HIJRI)
    }

    @Test
    fun arithmeticHelpersAreConsistent() {
        assertEquals(29, HijriArithmetic.daysInMonth(1446, 9))
        assertTrue(HijriArithmetic.daysInYear(1446) in 354..355)
        assertEquals(HijriArithmetic.daysInYear(1446) == 355, HijriArithmetic.isLeapYear(1446))

        val midRamadan = HijriCalendar.hijriToGregorian(1446, 9, 15)
        assertEquals(15, HijriArithmetic.dayOfYear(midRamadan) - HijriCalendar.hijriMonthLengths(1446).take(8).sum())
        assertEquals(HijriArithmetic.daysInMonth(1446, 9) - 15, HijriArithmetic.remainingDaysInMonth(midRamadan))

        // addDays round-trips through forward conversion.
        val plus40 = HijriArithmetic.addDays(midRamadan, 40)
        val expectedCivil = midRamadan.plusDays(40)
        assertEquals(expectedCivil, HijriCalendar.hijriToGregorian(plus40.year, plus40.month, plus40.day))

        // addMonths snaps day when target month is shorter.
        val thirtyDay = HijriCalendar.hijriMonthLengths(1446).withIndex().first { it.value == 30 }
        val startCivil = HijriCalendar.hijriToGregorian(1446, thirtyDay.index + 1, 30)
        val (h, _) = HijriArithmetic.addMonths(startCivil, 1)
        assertEquals(thirtyDay.index + 2, h.month)
        assertTrue(h.day <= 30)
    }

    @Test
    fun monthBoundariesSpanExactLength() {
        val b = HijriArithmetic.monthBoundaries(1446, 9)
        assertEquals(29, b.lengthDays) // Ramadan 1446 = 29 days (Eid on Mar 30)
        assertEquals(LocalDate.of(2025, 3, 1), b.firstCivilDate)
        assertEquals(b.firstCivilDate.plusDays((b.lengthDays - 1).toLong()), b.lastCivilDate)
    }

    @Test
    fun ramadanAndSeasonFactsMatchAnchors() {
        val r = Facts.ramadan(1446)
        assertEquals(LocalDate.of(2025, 3, 1), r.firstDay)
        assertEquals(29, r.lengthDays)

        assertEquals(LocalDate.of(2025, 3, 30), Facts.eidAlFitr(1446))
        assertEquals(LocalDate.of(2025, 6, 5), Facts.arafah(1446))
        assertEquals(LocalDate.of(2025, 7, 5), Facts.ashura(1447))
        assertEquals(listOf(LocalDate.of(2025, 6, 7), LocalDate.of(2025, 6, 8), LocalDate.of(2025, 6, 9)), Facts.tashreeqDays(1446))

        val whites = Facts.whiteDays(1446, 9)
        assertEquals(3, whites.size)
        assertTrue(Facts.isLastTenNights(LocalDate.of(2025, 3, 23))) // 23 Ramadan
        assertTrue(!Facts.isLastTenNights(LocalDate.of(2025, 3, 10)))
    }

    @Test
    fun eventRegistryParsesPacksAndResolvesOccurrences() {
        val registry = EventRegistry()
        val added = registry.registerPack(
            """{"pack":"test","events":[
                {"id":"custom_day","title":"Custom Community Day","hijriMonth":3,"hijriDay":14,
                 "category":"HISTORICAL","source":"unit-test","confidence":"COMMUNITY"}
            ]}""",
        )
        assertEquals(1, added)
        // Duplicate registration is idempotent.
        assertEquals(0, registry.registerPack(
            """{"events":[{"id":"custom_day","title":"x","hijriMonth":3,"hijriDay":14}]}""",
        ))

        val mawlidDate = HijriCalendar.hijriToGregorian(1447, 3, 12)
        assertTrue(registry.occurrencesOn(mawlidDate).any { it.definition.id == "mawlid" })
        // custom_day lives on 3/14 — its own date, not Mawlid's.
        val customDate = HijriCalendar.hijriToGregorian(1447, 3, 14)
        assertTrue(registry.occurrencesOn(customDate).any { it.definition.id == "custom_day" })

        assertFailsWith<EventPackException> {
            registry.registerPack("""{"events":[{"id":"bad","title":"Bad","hijriMonth":13,"hijriDay":2}]}""")
        }
    }

    @Test
    fun aladhanAdapterMapsHolidayShape() {
        val registry = EventRegistry()
        val json = """
            {"data":[
              {"hijri":{"day":"10","month":{"number":1},"year":"1447"},
               "holidays":["Ashura"]},
              {"hijri":{"day":"01","month":{"number":10},"year":"1447"},
               "holidays":["Eid al-Fitr"]}
            ]}
        """.trimIndent()
        assertEquals(2, registry.registerAladhan(json))
        val ashura = HijriCalendar.hijriToGregorian(1447, 1, 10)
        assertTrue(registry.occurrencesOn(ashura).any { it.definition.id == "aladhan_ashura" })
    }

    @Test
    fun observanceOverridesApplyOnlyThroughExplicitContext() {
        val registry = EventRegistry(offsetDays = 0)
        val eidCivil = HijriCalendar.hijriToGregorian(1447, 10, 1)
        val observedElsewhere = eidCivil.plusDays(1)

        // Without context: pure calculated date.
        assertTrue(registry.occurrencesOn(eidCivil).any { it.definition.id == "eid_al_fitr" })

        // With context selecting an override for a DIFFERENT year — no effect here.
        val ctx = ObservanceContext(ObservedDateOverride("eid_al_fitr", 1499, observedElsewhere))
        assertTrue(registry.occurrencesOn(eidCivil, ctx).any { it.definition.id == "eid_al_fitr" })

        // With context for THIS year: the tabular day is annotated away.
        val thisYearCtx = ObservanceContext(ObservedDateOverride("eid_al_fitr", 1447, observedElsewhere))
        assertTrue(registry.occurrencesOn(eidCivil, thisYearCtx).none { it.definition.id == "eid_al_fitr" })
        assertTrue(registry.occurrencesOn(observedElsewhere, thisYearCtx).any {
            it.definition.id == "eid_al_fitr" && it.civilDate == observedElsewhere
        })
    }

    @Test
    fun lunarMonthViewCoversTheHijriMonthWithRenderableFacts() {
        val facts = LunarCalendarView.monthView(
            1447, 9, Location.of(21.4225, 39.8262), ZoneId.of("Asia/Riyadh"),
        )
        assertTrue(facts.size == 29 || facts.size == 30)
        assertEquals(1, facts.first().hijriDay)
        facts.forEach { f ->
            assertTrue(f.illuminationFraction in 0.0..1.0)
            assertTrue(f.brightLimbAngleDeg in 0.0..360.0)
            assertNotNull(f.eveningInstant)
        }
        // Ramadan begins near new moon: first evening's illumination should be small.
        assertTrue(facts.first().illuminationFraction < 0.25, "first evening illum ${facts.first().illuminationFraction}")
    }
}

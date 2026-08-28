package com.khushu.engine.calendar

import com.khushu.engine.core.error.HijriDayDoesNotExistException
import com.khushu.engine.core.error.KhushuInputFailure
import com.khushu.engine.core.error.NoResultException
import com.khushu.engine.core.error.UpstreamComputationException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorContractTest {

    @Test
    fun hijriOfDay30InA29DayMonthThrowsTypedDayDoesNotExist() {
        val year = 1447
        val lengths = HijriCalendar.hijriMonthLengths(year)
        val shortMonth = lengths.indexOfFirst { it == 29 } + 1
        assertTrue(shortMonth in 1..12, "expected at least one 29-day month in $year AH")
        val e = assertFailsWith<HijriDayDoesNotExistException> {
            HijriCalendar.hijriToGregorian(year, shortMonth, 30)
        }
        kotlin.test.assertEquals(30, e.hijriDay)
        kotlin.test.assertEquals(shortMonth, e.hijriMonth)
    }

    @Test
    fun outOfTableCivilDatesThrowUpstreamFailureBothDirections() {
        // Umm al-Qura table spans 1300–1600 AH (≈ 1882–2174 CE).
        val future = assertFailsWith<UpstreamComputationException> {
            HijriCalendar.hijri(LocalDate.of(2300, 1, 1))
        }
        assertNotNull(future.cause)
        assertTrue(future.detail.contains("1300"))

        val past = assertFailsWith<UpstreamComputationException> {
            HijriCalendar.hijri(LocalDate.of(1500, 1, 1))
        }
        assertNotNull(past.cause)
    }

    @Test
    fun nextOccurrenceOfImpossibleDayThrowsNoResult() {
        // 30-2 (Sha'ban 30) does not exist in either of the next two years
        // for any plausible `after` in the current era — pick after such that
        // both candidate years' month 2 have 29 days; otherwise this specific
        // assertion is meaningless, so derive it from the table itself.
        val after = LocalDate.of(2026, 1, 1)
        val h = HijriCalendar.hijri(after)
        val l0 = HijriCalendar.hijriMonthLengths(h.year)[1]
        val l1 = HijriCalendar.hijriMonthLengths(h.year + 1)[1]
        if (l0 == 29 && l1 == 29) {
            val e = assertFailsWith<NoResultException> {
                HijriCalendar.nextOccurrence(2, 30, after)
            }
            assertTrue(e.detail.contains("30-2"))
        } else {
            // Table happens to allow 30-2 in one of the candidate years:
            // the API must then return a real date, not throw.
            val result = HijriCalendar.nextOccurrence(2, 30, after)
            assertTrue(!result.isBefore(after))
        }
    }

    @Test
    fun offsetDaysOutOfRangeThrowsTypedInputFailure() {
        val e = assertFailsWith<KhushuInputFailure> {
            HijriCalendar.hijri(LocalDate.of(2026, 7, 1), offsetDays = 5)
        }
        assertIs<IllegalArgumentException>(e)
    }

    @Test
    fun invalidHijriComponentsThrowStructuredFieldReport() {
        val e = assertFailsWith<com.khushu.engine.core.error.InvalidParameterException> {
            HijriCalendar.hijriToGregorian(1447, 13, 1)
        }
        // month 13 is structurally impossible — reported via the typed path
        kotlin.test.assertEquals("hijriDate", e.parameter)
        kotlin.test.assertTrue(e.value.contains("13"))
    }
}

package com.khushu.engine.calendar

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import java.time.LocalDate
import java.time.chrono.JapaneseDate
import java.time.chrono.MinguoDate
import java.time.chrono.ThaiBuddhistDate
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One date rendered in a region-based civil calendar.
 *
 * [eraLabel] is non-null only for era-named systems (Japanese); for those
 * [year] is the year-of-era. All other systems carry an absolute year.
 * [label] is pre-formatted for direct display; hosts may re-format from the
 * structured fields.
 */
data class RegionalDate(
    val system: CivilCalendarType,
    val year: Int,
    val month: Int,
    val day: Int,
    val monthName: String,
    val eraLabel: String? = null,
    val label: String,
)

/**
 * Region-based civil calendars people live by day-to-day (non-religious;
 * the Islamic side stays Umm al-Qura via [HijriCalendar]).
 *
 * Three systems are exact adapters over `java.time.chrono` (Japanese, Minguo,
 * Thai Buddhist); five use engine-owned deterministic math:
 *
 * - PERSIAN — official calendar of Iran; the standard 33-year-cycle algorithm
 *   (jalaali-js / Borkowski). Nowruz dates verified in tests against the
 *   official 1354–1419 SH correspondence table (Wikipedia, Solar Hijri).
 * - INDIAN_NATIONAL — India's national Śaka calendar (Calendar Reform
 *   Committee, adopted 22 March 1957); fixed month-start table: Chaitra 30/31
 *   from 22/21 March, leap iff Śaka year + 78 is a Gregorian leap year.
 * - BANGLA_BANGLADESH — Bangladesh national calendar, 2019-present revision:
 *   first six months 31 days from 14 April, then five months of 30, Falgun
 *   29/30 (30 when the Gregorian year containing it is a leap year).
 * - COPTIC / ETHIOPIAN — the Alexandrian structure (12×30 + a little month of
 *   5/6 days, leap year ≡ 3 mod 4, Julian-style with no century skips), with
 *   epochs pinned to published new-year anchors; arithmetically identical to
 *   `java.time.chrono`'s chronologies but JDK-8-portable (no Ethiopic
 *   dependency).
 */
object RegionalCalendars {

    private const val JDN_UNIX_EPOCH = 2_440_588L

    // ── public API ────────────────────────────────────────────────────────────

    /** Render [date] in [system]. */
    fun toRegional(date: LocalDate, system: CivilCalendarType): RegionalDate = when (system) {
        CivilCalendarType.GREGORIAN -> RegionalDate(
            system, date.year, date.monthValue, date.dayOfMonth,
            monthNameOf(system, date.monthValue),
            label = date.toString(),
        )
        CivilCalendarType.PERSIAN -> {
            val (jy, jm, jd) = persianFromJdn(date.toEpochDay() + JDN_UNIX_EPOCH)
            RegionalDate(
                system, jy, jm, jd, monthNameOf(system, jm),
                label = "$jd ${monthNameOf(system, jm)} $jy",
            )
        }
        CivilCalendarType.INDIAN_NATIONAL -> {
            val sakaYear = sakaYearOf(date)
            val dayOfYear = ChronoUnit.DAYS.between(sakaNewYear(sakaYear), date).toInt()
            val (m, d) = walkMonths(dayOfYear, sakaMonthLengths(sakaYear))
            RegionalDate(
                system, sakaYear, m, d, monthNameOf(system, m),
                label = "$d ${monthNameOf(system, m)} $sakaYear",
            )
        }
        CivilCalendarType.BANGLA_BANGLADESH -> {
            val banglaYear = banglaYearOf(date)
            val dayOfYear = ChronoUnit.DAYS.between(banglaNewYear(banglaYear), date).toInt()
            val (m, d) = walkMonths(dayOfYear, banglaMonthLengths(banglaYear))
            RegionalDate(
                system, banglaYear, m, d, monthNameOf(system, m),
                label = "$d ${monthNameOf(system, m)} $banglaYear",
            )
        }
        CivilCalendarType.COPTIC -> alexandrianDate(system, date, copticEpochDay)
        CivilCalendarType.ETHIOPIAN -> alexandrianDate(system, date, ethiopianEpochDay)
        CivilCalendarType.JAPANESE -> {
            val j = JapaneseDate.from(date)
            val eraName = j.era.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val yearOfEra = j.get(ChronoField.YEAR_OF_ERA)
            val month = j.get(ChronoField.MONTH_OF_YEAR)
            val day = j.get(ChronoField.DAY_OF_MONTH)
            RegionalDate(
                system, yearOfEra, month, day,
                monthNameOf(system, month), eraLabel = eraName,
                label = "$eraName $yearOfEra-${"%02d".format(month)}-${"%02d".format(day)}",
            )
        }
        CivilCalendarType.MINGUO -> {
            val m = MinguoDate.from(date)
            val year = m.get(ChronoField.YEAR)
            val month = m.get(ChronoField.MONTH_OF_YEAR)
            val day = m.get(ChronoField.DAY_OF_MONTH)
            RegionalDate(
                system, year, month, day,
                monthNameOf(system, month), eraLabel = "Minguo",
                label = "Minguo $year-${"%02d".format(month)}-${"%02d".format(day)}",
            )
        }
        CivilCalendarType.THAI_BUDDHIST -> {
            val t = ThaiBuddhistDate.from(date)
            val year = t.get(ChronoField.YEAR)
            val month = t.get(ChronoField.MONTH_OF_YEAR)
            val day = t.get(ChronoField.DAY_OF_MONTH)
            RegionalDate(
                system, year, month, day,
                monthNameOf(system, month), eraLabel = "BE",
                label = "BE $year-${"%02d".format(month)}-${"%02d".format(day)}",
            )
        }
    }

    /**
     * Reverse conversion: civil date of a regional date.
     * @throws InvalidParameterException on structurally invalid components, on a
     *   day that does not exist in that year (e.g. Bangla Falgun 30 in a common
     *   year), or for JAPANESE — era disambiguation (e.g. Showa 1 vs Taisho 1)
     *   is intentionally unsupported.
     */
    fun fromRegional(system: CivilCalendarType, year: Int, month: Int, day: Int): LocalDate = when (system) {
        CivilCalendarType.GREGORIAN -> LocalDate.of(year, month, day)
        CivilCalendarType.PERSIAN -> {
            validate(jyIsValid(year)) {
                InvalidParameterException("year", "$year", "Persian year must be within −61..3177 SH")
            }
            validateMonthDay(system, year, month, day, persianMonthLengths(year))
            LocalDate.ofEpochDay(persianToJdn(year, month, day) - JDN_UNIX_EPOCH)
        }
        CivilCalendarType.INDIAN_NATIONAL -> {
            validate(year > 0) { InvalidParameterException("year", "$year", "must be > 0") }
            validateMonthDay(system, year, month, day, sakaMonthLengths(year))
            sakaNewYear(year).plusDays(dayOffset(month, day, sakaMonthLengths(year)))
        }
        CivilCalendarType.BANGLA_BANGLADESH -> {
            validate(year > 0) { InvalidParameterException("year", "$year", "must be > 0") }
            validateMonthDay(system, year, month, day, banglaMonthLengths(year))
            banglaNewYear(year).plusDays(dayOffset(month, day, banglaMonthLengths(year)))
        }
        CivilCalendarType.COPTIC -> fromAlexandrian(system, year, month, day, copticEpochDay)
        CivilCalendarType.ETHIOPIAN -> fromAlexandrian(system, year, month, day, ethiopianEpochDay)
        CivilCalendarType.MINGUO -> LocalDate.of(year + 1911, month, day)
        CivilCalendarType.THAI_BUDDHIST -> LocalDate.of(year - 543, month, day)
        CivilCalendarType.JAPANESE -> throw InvalidParameterException(
            "system", system.name,
            "era disambiguation is not supported (year-of-era is ambiguous across eras); use the Gregorian date",
        )
    }

    /** Month name for [system] by 1-indexed month. */
    fun monthNames(system: CivilCalendarType): List<String> = when (system) {
        CivilCalendarType.PERSIAN -> listOf(
            "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
        )
        CivilCalendarType.INDIAN_NATIONAL -> listOf(
            "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashvina", "Kartika", "Agrahayana", "Pausha", "Magha", "Phalguna",
        )
        CivilCalendarType.BANGLA_BANGLADESH -> listOf(
            "Boishakh", "Joishtho", "Asharh", "Srabon", "Bhadro", "Ashbin",
            "Kartik", "Agrahayon", "Poush", "Magh", "Falgun", "Chaitra",
        )
        CivilCalendarType.COPTIC -> listOf(
            "Thout", "Paopi", "Hathor", "Koiak", "Tobi", "Meshir", "Paremhat",
            "Parmouti", "Pashons", "Paoni", "Epip", "Mesori", "Pi Kogi Enavot",
        )
        CivilCalendarType.ETHIOPIAN -> listOf(
            "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit",
            "Megabit", "Miyazya", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume",
        )
        CivilCalendarType.GREGORIAN, CivilCalendarType.JAPANESE, CivilCalendarType.MINGUO,
        CivilCalendarType.THAI_BUDDHIST,
        -> java.time.Month.entries.map { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
    }

    private fun monthNameOf(system: CivilCalendarType, month: Int): String = monthNames(system)[month - 1]

    private fun validateMonthDay(system: CivilCalendarType, year: Int, month: Int, day: Int, lengths: List<Int>) {
        validate(month in 1..lengths.size) {
            InvalidParameterException("month", "$month", "must be within 1..${lengths.size} for $system")
        }
        validate(day in 1..lengths[month - 1]) {
            InvalidParameterException(
                "day", "$day", "must be within 1..${lengths[month - 1]} for ${monthNames(system)[month - 1]} $year",
            )
        }
    }

    /** Day offset from the start of the year for month/day (day 1 → 0). */
    private fun dayOffset(month: Int, day: Int, lengths: List<Int>): Long {
        var offset = 0L
        for (m in 1 until month) offset += lengths[m - 1]
        return offset + (day - 1)
    }

    private fun walkMonths(dayOfYear: Int, lengths: List<Int>): Pair<Int, Int> {
        var remaining = dayOfYear
        for (m in lengths.indices) {
            if (remaining < lengths[m]) return (m + 1) to (remaining + 1)
            remaining -= lengths[m]
        }
        throw InvalidParameterException("date", "dayOfYear=$dayOfYear", "outside the regional year")
    }

    private fun isGregorianLeap(year: Int): Boolean = java.time.Year.isLeap(year.toLong())

    // Coptic and Ethiopian share the Alexandrian structure: 12×30 + a little
    // month of 5 days, 6 in leap years (year ≡ 3 mod 4).
    private fun alexandrianMonthLengths(year: Int): List<Int> =
        List(12) { 30 } + if (Math.floorMod(year, 4) == 3) 6 else 5

    // Epochs pinned to published new-year anchors (both fall on 2025-09-11):
    // Neyrouz AM 1742 (Coptic) and Enkutatash 2018 (Ethiopian) — Wikipedia
    // Coptic/Ethiopian calendar articles. Pure four-year-cycle arithmetic
    // thereafter (Julian-style: no century skipped).
    // Coptic and Ethiopian share the Alexandrian structure: 12×30 + a little
    // month of 5 days, 6 in leap years (year ≡ 3 mod 4, Julian-style: no
    // century skips). Within each four-year cycle the lengths run
    // 365, 365, 366, 365 — the third year of the cycle (≡ 3 mod 4) is leap.
    private val alexandrianBlockOffsets = longArrayOf(0, 365, 730, 1096)

    private fun alexandrianYearStartOffset(year: Int): Long {
        val complete = (year - 1).toLong()
        return complete / 4 * 1461 + alexandrianBlockOffsets[Math.floorMod(complete, 4L).toInt()]
    }

    private fun alexandrianEpochDay(anchorNewYear: LocalDate, anchorYear: Int): Long =
        anchorNewYear.toEpochDay() - alexandrianYearStartOffset(anchorYear)

    private val copticEpochDay = alexandrianEpochDay(LocalDate.of(2025, 9, 11), 1742)
    private val ethiopianEpochDay = alexandrianEpochDay(LocalDate.of(2025, 9, 11), 2018)

    private fun alexandrianDate(system: CivilCalendarType, date: LocalDate, epochDay: Long): RegionalDate {
        val delta = date.toEpochDay() - epochDay
        val cycle4 = Math.floorDiv(delta, 1461L)
        val rem = Math.floorMod(delta, 1461L)
        val yearInBlock = when {
            rem < 365 -> 0
            rem < 730 -> 1
            rem < 1096 -> 2
            else -> 3
        }
        val dayOfYear = (rem - alexandrianBlockOffsets[yearInBlock]).toInt()
        val year = (cycle4 * 4 + yearInBlock + 1).toInt()
        val month = if (dayOfYear < 360) dayOfYear / 30 + 1 else 13
        val day = if (dayOfYear < 360) dayOfYear % 30 + 1 else dayOfYear - 360 + 1
        return RegionalDate(
            system, year, month, day, monthNameOf(system, month),
            label = "$day ${monthNameOf(system, month)} $year",
        )
    }

    private fun fromAlexandrian(system: CivilCalendarType, year: Int, month: Int, day: Int, epochDay: Long): LocalDate {
        validate(year >= 1) { InvalidParameterException("year", "$year", "must be >= 1") }
        validateMonthDay(system, year, month, day, alexandrianMonthLengths(year))
        val yearStart = epochDay + alexandrianYearStartOffset(year)
        return LocalDate.ofEpochDay(yearStart + dayOffset(month, day, alexandrianMonthLengths(year)))
    }

    // ── Persian (Solar Hijri): jalaali-js / Borkowski 33-year-cycle algorithm ─

    private val persianBreaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class PersianYearFacts(val leap: Int, val gregorianYear: Int, val marchDay: Int)

    private fun jyIsValid(jy: Int): Boolean = jy >= -61 && jy <= 3177

    private fun persianYearFacts(jy: Int): PersianYearFacts {
        val gy = jy + 621
        var leapJ = -14
        var jp = persianBreaks[0]
        var jump = 0
        var i = 1
        while (i < persianBreaks.size) {
            val jm = persianBreaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return PersianYearFacts(leap, gy, march)
    }

    private fun persianMonthLengths(jy: Int): List<Int> = (1..12).map { jm ->
        when {
            jm <= 6 -> 31
            jm <= 11 -> 30
            else -> if (persianYearFacts(jy).leap == 0) 30 else 29
        }
    }

    private fun persianToJdn(jy: Int, jm: Int, jd: Int): Long {
        val r = persianYearFacts(jy)
        return gregorianToJdn(r.gregorianYear, 3, r.marchDay) +
            (jm - 1) * 31L - (jm / 7).toLong() * (jm - 7) + jd - 1
    }

    private fun persianFromJdn(jdn: Long): Triple<Int, Int, Int> {
        val gy = jdnToGregorian(jdn).year
        var jy = gy - 621
        val r = persianYearFacts(jy)
        val jdn1f = gregorianToJdn(gy, 3, r.marchDay)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return Triple(jy, (1 + k / 31).toInt(), (k % 31 + 1).toInt())
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        validate(jyIsValid(jy)) {
            InvalidParameterException("date", jdn.toString(), "outside the supported Persian range −61..3177 SH")
        }
        return Triple(jy, (7 + k / 30).toInt(), (k % 30 + 1).toInt())
    }

    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long {
        val base = gy + (gm - 8) / 6 + 100100
        var d = base * 1461L / 4 + (153L * ((gm + 9) % 12) + 2) / 5 + gd - 34_840_408L
        d -= (base / 100) * 3L / 4
        return d + 752
    }

    private fun jdnToGregorian(jdn: Long): LocalDate {
        var j = 4 * jdn + 139_361_631L
        j += ((4 * jdn + 183_187_720L) / 146_097 * 3) / 4 * 4 - 3908
        val i = (j % 1461) / 4 * 5 + 308
        val gd = ((i % 153) / 5 + 1).toInt()
        val gm = ((i / 153) % 12 + 1).toInt()
        val gy = (j / 1461 - 100_100 + (8 - gm) / 6).toInt()
        return LocalDate.of(gy, gm, gd)
    }

    // ── Indian national (Śaka) ─────────────────────────────────────────────────
    // Chaitra 30 (31 in leap years) from 22 (21) March; all other month starts
    // are fixed — Calendar Reform Committee table, adopted 22 March 1957
    // (1 Chaitra 1879 SE). Leap iff Śaka year + 78 is a Gregorian leap year.

    private val sakaCommonLengths = intArrayOf(30, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30)

    private fun sakaNewYear(sakaYear: Int): LocalDate =
        LocalDate.of(sakaYear + 78, 3, if (isGregorianLeap(sakaYear + 78)) 21 else 22)

    private fun sakaYearOf(date: LocalDate): Int {
        val provisional = date.year - 78
        return if (!date.isBefore(sakaNewYear(provisional))) provisional else provisional - 1
    }

    private fun sakaMonthLengths(sakaYear: Int): List<Int> =
        sakaCommonLengths.mapIndexed { i, len -> if (i == 0 && isGregorianLeap(sakaYear + 78)) 31 else len }

    // ── Bangla (Bangladesh, 2019-present revision) ─────────────────────────────
    // Boishakh 1 = 14 April; first six months 31 days, then five of 30; Falgun
    // is 29 days, 30 when the Gregorian year containing Falgun is a leap year
    // (equivalently: Bangla year + 594 is a Gregorian leap year, which is also
    // when the year's 14-April boundaries span a 29 February).

    private val banglaCommonLengths = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 29, 30)

    private fun banglaNewYear(banglaYear: Int): LocalDate = LocalDate.of(banglaYear + 593, 4, 14)

    private fun banglaYearOf(date: LocalDate): Int {
        val provisional = if (!date.isBefore(LocalDate.of(date.year, 4, 14))) date.year - 593 else date.year - 594
        return provisional
    }

    private fun banglaMonthLengths(banglaYear: Int): List<Int> =
        banglaCommonLengths.mapIndexed { i, len -> if (i == 10 && isGregorianLeap(banglaYear + 594)) 30 else len }
}

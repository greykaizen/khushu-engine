package com.khushu.engine.calendar

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate

/**
 * Calendar-system strategy: extensible without rewriting the API.
 */
enum class CalendarSystem { UMM_AL_QURA }

/** The Islamic side is always tabular in this engine; civil calendars are regional/civil only. */
enum class CivilCalendarType {
    /** Baseline proleptic Gregorian (ISO-8601). */
    GREGORIAN,

    /** Solar Hijri — official calendar of Iran, most widely used in Afghanistan. */
    PERSIAN,

    /** India's national Śaka calendar (Calendar Reform Committee, 1957). */
    INDIAN_NATIONAL,

    /** Bangladesh national calendar — the 2019-present revision. */
    BANGLA_BANGLADESH,

    /** Coptic (Alexandrian) calendar — used in Egypt. */
    COPTIC,

    /** Ethiopian (Geʽez) calendar — official state civil calendar of Ethiopia. */
    ETHIOPIAN,

    /** Japanese eras (Reiwa/Heisei/…) over Gregorian months and days. */
    JAPANESE,

    /** Republic-of-China calendar (year = CE − 1911). */
    MINGUO,

    /** Thai solar Buddhist calendar (year = CE + 543). */
    THAI_BUDDHIST,
}

data class CalendarConfiguration(
    /** Exactly one side must be HIJRI (enforced). */
    val primary: Side,
    val secondary: Side? = null,
    val system: CalendarSystem = CalendarSystem.UMM_AL_QURA,
    val hijriOffsetDays: Int = 0,
    /**
     * Civil system rendered for every [Side.GREGORIAN] line. Default is the
     * baseline Gregorian; pick a regional system (Persian, Śaka, Bangla, …)
     * to render that civil calendar in dual-calendar UIs — see
     * [RegionalCalendars]. The Hijri side is unaffected.
     */
    val civilCalendar: CivilCalendarType = CivilCalendarType.GREGORIAN,
) {
    enum class Side { HIJRI, GREGORIAN }

    init {
        validate(primary == Side.HIJRI || secondary == Side.HIJRI) {
            InvalidParameterException(
                "primary/secondary",
                "$primary/$secondary",
                "at least one calendar must be Hijri — this engine's calendar capability is Islamic-first",
            )
        }
        validate(hijriOffsetDays in -2..2) {
            InvalidParameterException("hijriOffsetDays", "$hijriOffsetDays", "must be within −2..+2")
        }
    }
}

/** One rendered date line of a calendar side. */
sealed interface DateLine {
    data class Hijri(val date: HijriDate) : DateLine
    data class Gregorian(val date: java.time.LocalDate) : DateLine

    /**
     * A non-Gregorian civil-calendar line (Persian, Śaka, Bangla, Coptic,
     * Ethiopian, Japanese, Minguo, Thai Buddhist) — produced whenever the
     * side is GREGORIAN and [CalendarConfiguration.civilCalendar] is not
     * [CivilCalendarType.GREGORIAN].
     */
    data class Regional(val date: RegionalDate) : DateLine
}

/** Both sides resolved for one civil date. */
data class DualDate(val primary: DateLine, val secondary: DateLine?)

/** Civil start/end facts of one hijri month. */
data class MonthBoundaries(
    val hijriYear: Int,
    val hijriMonth: Int,
    val monthName: String,
    val firstCivilDate: java.time.LocalDate,
    val lastCivilDate: java.time.LocalDate,
    val lengthDays: Int,
)

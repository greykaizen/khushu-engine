package com.khushu.engine.calendar

/**
 * Calendar-system strategy: extensible without rewriting the API.
 */
enum class CalendarSystem { UMM_AL_QURA }

/** The Islamic side is always tabular in this engine; civil calendars are regional/civil only. */
enum class CivilCalendarType { GREGORIAN }

data class CalendarConfiguration(
    /** Exactly one side must be HIJRI (enforced). */
    val primary: Side,
    val secondary: Side? = null,
    val system: CalendarSystem = CalendarSystem.UMM_AL_QURA,
    val hijriOffsetDays: Int = 0,
) {
    enum class Side { HIJRI, GREGORIAN }

    init {
        require(primary == Side.HIJRI || secondary == Side.HIJRI) {
            "at least one calendar must be Hijri — this engine's calendar capability is Islamic-first"
        }
        require(hijriOffsetDays in -2..2) { "hijriOffsetDays must be within −2..+2" }
    }
}

/** One rendered date line of a calendar side. */
sealed interface DateLine {
    data class Hijri(val date: HijriDate) : DateLine
    data class Gregorian(val date: java.time.LocalDate) : DateLine
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

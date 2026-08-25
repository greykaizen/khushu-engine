package com.khushu.engine.calendar

import java.time.LocalDate

/**
 * Calendar-domain inputs. The engine never reads persisted settings.
 */
data class CalendarParams(
    /** Applied to hijri conversion; valid range −2..+2 (enforced). */
    val hijriOffsetDays: Int = 0,
    val mondaysThursdays: Boolean = false,
    val whiteDays: Boolean = false,
    val shawwalSix: Boolean = false,
    val shaban: Boolean = false,
    val dhulHijjahFirstNine: Boolean = false,
    val tasuaAshura: Boolean = false,
) {
    init {
        require(hijriOffsetDays in -2..2) { "hijriOffsetDays must be within −2..+2, was $hijriOffsetDays" }
    }
}

data class HijriDate(
    val year: Int,
    /** 1-indexed month (1 = Muharram … 12 = Dhul-Hijjah). */
    val month: Int,
    val day: Int,
    val monthName: String,
    /** Offset that was applied during conversion (echoes [CalendarParams.hijriOffsetDays]). */
    val offsetApplied: Int,
) {
    val label: String get() = "$day $monthName $year AH"
}

enum class FastRule {
    MONDAY_THURSDAY,
    WHITE_DAYS,
    SHABAN,
    SHAWWAL_SIX,
    DHUL_HIJJAH_FIRST_NINE,
    TASUA_ASHURA,
}

/** A civil date on which one optional fast is observed, with the governing rule. */
data class FastDay(val date: LocalDate, val rule: FastRule)

data class IslamicEvent(val title: String, val hijriDateLabel: String)

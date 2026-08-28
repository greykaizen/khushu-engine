package com.khushu.engine.calendar

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
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
        validate(hijriOffsetDays in -2..2) {
            InvalidParameterException("hijriOffsetDays", "$hijriOffsetDays", "must be within −2..+2")
        }
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
) : Comparable<HijriDate> {
    val label: String get() = "$day $monthName $year AH"

    /** Chronological order: year, then month, then day. */
    override fun compareTo(other: HijriDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })
}

enum class FastRule(val provenance: String) {
    MONDAY_THURSDAY("Prophet's regular practice — Sunan Abi Dawud, Sahih Muslim"),
    WHITE_DAYS("13th–15th of each lunar month — classical practice"),
    SHABAN("Voluntary fasting through Sha'ban, excluding its last days — Sahih Bukhari/Nasa'i"),
    SHAWWAL_SIX("Six days of Shawwal after Eid — Sahih Muslim; days are distributable, engine marks 2–7 as candidates"),
    DHUL_HIJJAH_FIRST_NINE("First nine days of Dhul-Hijjah — Sahih Bukhari"),
    TASUA_ASHURA("9th with the 10th of Muharram — Sahih Muslim"),
}

/** A civil date on which one optional fast is observed, with the governing rule. */
data class FastDay(val date: LocalDate, val rule: FastRule)

data class IslamicEvent(val title: String, val hijriDateLabel: String)

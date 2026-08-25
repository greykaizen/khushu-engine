package com.khushu.engine.calendar

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Lunar presentation facts mapped onto a hijri month. The engine returns FACTS —
 * phase, illumination, bright-limb rotation, position — so the host can clip and
 * rotate ANY moon asset. No images live here.
 */
object LunarCalendarView {

    data class LunarDayFact(
        val hijriDay: Int,
        val civilDate: LocalDate,
        /** Sample instant: local sunset of that civil evening (the Islamic-day boundary). */
        val eveningInstant: Instant,
        val phaseAngleDeg: Double,
        val illuminationFraction: Double,
        /** Bright-limb tilt at the observer (χ − q): rotate your asset by this. */
        val brightLimbAngleDeg: Double,
        val altitudeDeg: Double,
        val azimuthDeg: Double,
        val aboveHorizon: Boolean,
        /** True when sunset was uncomputable and sampling fell back to 18:00 local. */
        val sampledAtFallback: Boolean,
    )

    /**
     * One fact per civil evening across the hijri month's civil span, sampled at
     * local sunset (the Islamic-day boundary). Composed from astronomy's public
     * facts; no duplicated ephemeris.
     */
    fun monthView(
        hijriYear: Int,
        hijriMonth: Int,
        location: Location,
        zoneId: ZoneId,
        offsetDays: Int = 0,
    ): List<LunarDayFact> {
        val boundaries = HijriArithmetic.monthBoundaries(hijriYear, hijriMonth, offsetDays)

        // ONE-PASS lunar track per involved civil month: rise/set come from the
        // track; only per-evening state/position are sampled individually.
        data class DayFacts(
            val setEpochMs: Long?, val state: com.khushu.engine.astronomy.MoonState,
            val position: com.khushu.engine.astronomy.LunarPosition,
        )
        val byDate = HashMap<LocalDate, DayFacts>()
        var monthCursor = YearMonth.from(boundaries.firstCivilDate)
        val lastMonth = YearMonth.from(boundaries.lastCivilDate)
        while (!monthCursor.isAfter(lastMonth)) {
            for (day in Astronomy.moon.track(location, monthCursor, zoneId).days) {
                val sunsetMs = day.setEpochMs
                    ?: day.transitEpochMs // polar fallback anchor for sampling only
                    ?: continue
                val instant = Instant.ofEpochMilli(sunsetMs)
                byDate[day.date] = DayFacts(
                    day.setEpochMs,
                    Astronomy.moon.state(location, instant),
                    Astronomy.moon.position(location, instant),
                )
            }
            monthCursor = monthCursor.plusMonths(1)
        }

        val facts = mutableListOf<LunarDayFact>()
        var civilDate = boundaries.firstCivilDate
        var hijriDay = 1
        while (!civilDate.isAfter(boundaries.lastCivilDate)) {
            val df = byDate[civilDate]
            if (df == null) {
                civilDate = civilDate.plusDays(1)
                hijriDay++
                continue
            }
            val fallback = df.setEpochMs == null
            val sunset: Instant =
                df.setEpochMs?.let(Instant::ofEpochMilli)
                    ?: civilDate.atTime(18, 0).atZone(zoneId).toInstant()
            facts += LunarDayFact(
                hijriDay = hijriDay,
                civilDate = civilDate,
                eveningInstant = sunset,
                phaseAngleDeg = df.state.phaseAngleDeg,
                illuminationFraction = df.state.illuminationFraction,
                brightLimbAngleDeg = df.state.brightLimbTiltDeg,
                altitudeDeg = df.position.altitudeDeg,
                azimuthDeg = df.position.azimuthDeg,
                aboveHorizon = df.position.altitudeDeg > 0.0,
                sampledAtFallback = fallback,
            )
            civilDate = civilDate.plusDays(1)
            hijriDay++
        }
        return facts
    }
}

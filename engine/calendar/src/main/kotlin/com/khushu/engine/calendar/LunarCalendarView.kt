package com.khushu.engine.calendar

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.core.geo.Location
import java.time.Instant
import java.time.LocalDate
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
        val facts = mutableListOf<LunarDayFact>()
        var civilDate = boundaries.firstCivilDate
        var hijriDay = 1
        while (!civilDate.isAfter(boundaries.lastCivilDate)) {
            // Sunset of this civil evening = the Islamic-day boundary.
            val sunsetMs = Astronomy.sun.riseSet(location, civilDate, zoneId).setEpochMs
            val sunset: Instant = sunsetMs?.let(Instant::ofEpochMilli)
                ?: civilDate.atTime(18, 0).atZone(zoneId).toInstant() // polar fallback for sampling only
            val state = Astronomy.moon.state(location, sunset)
            val pos = Astronomy.moon.position(location, sunset)
            facts += LunarDayFact(
                hijriDay = hijriDay,
                civilDate = civilDate,
                eveningInstant = sunset,
                phaseAngleDeg = state.phaseAngleDeg,
                illuminationFraction = state.illuminationFraction,
                brightLimbAngleDeg = state.brightLimbTiltDeg,
                altitudeDeg = pos.altitudeDeg,
                azimuthDeg = pos.azimuthDeg,
                aboveHorizon = pos.altitudeDeg > 0.0,
            )
            civilDate = civilDate.plusDays(1)
            hijriDay++
        }
        return facts
    }
}

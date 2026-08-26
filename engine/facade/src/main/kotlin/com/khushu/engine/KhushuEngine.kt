package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.HilalReport
import com.khushu.engine.astronomy.LunarPosition
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.MonthlyMoonTrack
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.astronomy.SolarEvents
import com.khushu.engine.astronomy.SolarPosition
import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.EventRegistry
import com.khushu.engine.calendar.HijriCalendar
import com.khushu.engine.calendar.CalendarParams
import com.khushu.engine.calendar.FastDay
import com.khushu.engine.calendar.HijriDate
import com.khushu.engine.calendar.IslamicEvent
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import com.khushu.engine.prayer.PrayerConfiguration
import com.khushu.engine.prayer.PrayerStatus
import com.khushu.engine.prayer.PrayerTimesResult
import com.khushu.engine.prayer.TahajjudWindow
import com.khushu.engine.qibla.Qibla
import com.khushu.engine.qibla.QiblaShadowEvent
import com.khushu.engine.qibla.SunQiblaRelation
import com.khushu.engine.qibla.QiblaBearing
import com.khushu.engine.zakat.FitranaResult
import com.khushu.engine.zakat.Zakat
import com.khushu.engine.zakat.ZakatAssets
import com.khushu.engine.zakat.ZakatMadhab
import com.khushu.engine.zakat.ZakatParams
import com.khushu.engine.zakat.ZakatResult
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Single entry point for host applications. No Clock, no cache, no state —
 * every call delegates straight to the deterministic capability layer.
 *
 * ```
 * val engine = KhushuEngine()
 * engine.prayer.times(location, localDate, params)
 * engine.astronomy.sun.position(location, instant)
 * engine.calendar.hijri(localDate, offsetDays)
 * engine.qibla.bearing(location)
 * engine.zakat.mal(assets, params)
 * ```
 */
class KhushuEngine {

    val prayer = PrayerApi()
    val astronomy = AstronomyApi()
    val calendar = CalendarApi()
    val qibla = QiblaApi()
    val zakat = ZakatApi()

    /** Whole-day composite: every fact of one civil date in a single pass. */
    val day = com.khushu.engine.DayApi()

    // ── Namespaces: pure delegation, zero logic ─────────────────────────────

    class PrayerApi internal constructor() {
        fun times(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): PrayerTimesResult =
            Prayer.times(location, date, config)

        fun timesForRange(
            location: Location,
            start: LocalDate,
            endInclusive: LocalDate,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): List<PrayerTimesResult> = Prayer.timesForRange(location, start, endInclusive, config)

        fun month(location: Location, yearMonth: YearMonth, config: PrayerConfiguration = PrayerConfiguration()): List<PrayerTimesResult> =
            Prayer.month(location, yearMonth, config)

        fun status(location: Location, now: Instant, zoneId: ZoneId, config: PrayerConfiguration = PrayerConfiguration()): PrayerStatus =
            Prayer.status(location, now, zoneId, config)

        /** Compact sequence around [now]: previous/current/next by radius. */
        fun navigationSequence(
            location: Location,
            now: Instant,
            zoneId: ZoneId,
            before: Int = 0,
            after: Int = 1,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): List<Prayer.Entry> = Prayer.navigation(location, now, zoneId, before, after, config)

        /** Category-labeled worship occurrences for a civil day. */
        fun occasionsOn(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): List<Prayer.Occurrence> =
            Prayer.occasionsOn(location, date, config)

        fun tahajjudWindow(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): TahajjudWindow? =
            Prayer.tahajjudWindow(location, date, config)

        val windows = WindowsApi()

        inner class WindowsApi internal constructor() {
            fun imsak(location: Location, date: LocalDate, minutesBeforeFajr: Int = 10, config: PrayerConfiguration = PrayerConfiguration()): Instant? =
                Prayer.imsak(location, date, minutesBeforeFajr, config)

            fun duha(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): ClosedRange<Instant>? =
                Prayer.duhaWindow(location, date, config)

            fun forbidden(location: Location, date: LocalDate, lateAfternoonMarginMinutes: Int = 20, config: PrayerConfiguration = PrayerConfiguration()): Prayer.ForbiddenWindows =
                Prayer.forbiddenWindows(location, date, lateAfternoonMarginMinutes, config)

            fun fastingFacts(location: Location, date: LocalDate, imsakMinutesBeforeFajr: Int = 10, config: PrayerConfiguration = PrayerConfiguration()): Prayer.FastingFacts =
                Prayer.fastingFacts(location, date, imsakMinutesBeforeFajr, config)

            fun travelFacts(
                location: Location,
                date: LocalDate,
                travelledDistanceKm: Double,
                distanceThresholdKm: Double? = null,
                config: PrayerConfiguration = PrayerConfiguration(),
            ): Prayer.TravelFacts = Prayer.travelFacts(location, date, travelledDistanceKm, distanceThresholdKm, config)
        }
    }

    class AstronomyApi internal constructor() {
        val sun = SunApi()
        val moon = MoonApi()
        val hilal = HilalApi()

        class SunApi internal constructor() {
            fun position(location: Location, instant: Instant): SolarPosition =
                Astronomy.sun.position(location, instant)

            fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet =
                Astronomy.sun.riseSet(location, date, zoneId)

            fun dayLength(location: Location, date: LocalDate, zoneId: ZoneId): java.time.Duration? =
                Astronomy.sun.dayLength(location, date, zoneId)

            fun events(location: Location, date: LocalDate, zoneId: ZoneId): SolarEvents =
                Astronomy.sun.events(location, date, zoneId)
        }

        class MoonApi internal constructor() {
            fun position(location: Location, instant: Instant): LunarPosition =
                Astronomy.moon.position(location, instant)

            fun state(location: Location, instant: Instant): MoonState =
                Astronomy.moon.state(location, instant)

            fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet =
                Astronomy.moon.riseSet(location, date, zoneId)

            fun track(
                location: Location,
                yearMonth: YearMonth,
                zoneId: ZoneId,
                includePath: Boolean = false,
                pathSamplesPerDay: Int = 48,
            ): MonthlyMoonTrack = Astronomy.moon.track(location, yearMonth, zoneId, includePath, pathSamplesPerDay)

            fun phaseName(phaseAngleDeg: Double): String = Astronomy.moon.phaseName(phaseAngleDeg)

            fun nextNewMoon(after: Instant) = Astronomy.moon.nextNewMoon(after)
            fun nextFullMoon(after: Instant) = Astronomy.moon.nextFullMoon(after)
            fun nextPhase(targetPhaseDeg: Double, after: Instant) = Astronomy.moon.nextPhase(targetPhaseDeg, after)

            fun distanceExtremes(from: Instant, to: Instant) = Astronomy.moon.distanceExtremes(from, to)
            fun nextGlobalSolarEclipse(after: Instant) = Astronomy.moon.nextGlobalSolarEclipse(after)
            fun nextLunarEclipse(after: Instant) = Astronomy.moon.nextLunarEclipse(after)
        }

        class HilalApi internal constructor() {
            fun visibility(location: Location, date: LocalDate, zoneId: ZoneId): HilalReport? =
                Astronomy.hilal.visibility(location, date, zoneId)

            fun forecast(
                location: Location,
                from: LocalDate,
                zoneId: ZoneId,
                nights: Int = 8,
            ): List<com.khushu.engine.astronomy.HilalForecastDay> =
                Astronomy.hilal.forecast(location, from, zoneId, nights)
        }
    }

    class CalendarApi internal constructor() {
        fun hijri(localDate: LocalDate, offsetDays: Int = 0): HijriDate =
            HijriCalendar.hijri(localDate, offsetDays)

        fun hijriToGregorian(hijriYear: Int, hijriMonth: Int, hijriDay: Int, offsetDays: Int = 0): LocalDate =
            HijriCalendar.hijriToGregorian(hijriYear, hijriMonth, hijriDay, offsetDays)

        fun hijriMonthLengths(hijriYear: Int, offsetDays: Int = 0): List<Int> =
            HijriCalendar.hijriMonthLengths(hijriYear, offsetDays)

        fun events(localDate: LocalDate, offsetDays: Int = 0): List<IslamicEvent> =
            HijriCalendar.events(localDate, offsetDays)

        fun eventsInRange(range: ClosedRange<LocalDate>, offsetDays: Int = 0): List<Pair<LocalDate, IslamicEvent>> =
            HijriCalendar.eventsInRange(range, offsetDays)

        fun nextOccurrence(month: Int, day: Int, after: LocalDate, offsetDays: Int = 0): LocalDate =
            HijriCalendar.nextOccurrence(month, day, after, offsetDays)

        fun isSacredMonth(hijriMonth: Int): Boolean = HijriCalendar.isSacredMonth(hijriMonth)

        fun fastDays(range: ClosedRange<LocalDate>, params: CalendarParams): List<FastDay> =
            HijriCalendar.fastDays(range, params)

        // ── Namespaced capability tree ──────────────────────────────────────

        /** Local Islamic-day calculation using SUNSET — distinct from tabular conversion. */
        val date = DateApi()
        val month = MonthApi()
        val facts = FactsApi()

        /**
         * Event registry: built-in core pack + host-registered packs
         * (engine schema or Aladhan shape) merged into per-date occurrences.
         */
        fun events(config: CalendarConfiguration): EventRegistry = EventRegistry(config.hijriOffsetDays)

        val observance = ObservanceApi()
        val lunar = LunarApi()

        inner class ObservanceApi internal constructor() {
            /** Occurrences with explicitly selected observed-date overrides applied. */
            fun on(
                date: LocalDate,
                config: CalendarConfiguration,
                context: com.khushu.engine.calendar.ObservanceContext,
                registry: EventRegistry,
            ): List<com.khushu.engine.calendar.EventOccurrence> = registry.occurrencesOn(date, context)
        }

        inner class LunarApi internal constructor() {
            fun monthView(
                hijriYear: Int,
                hijriMonth: Int,
                location: Location,
                zoneId: ZoneId,
                config: CalendarConfiguration = CalendarConfiguration(primary = CalendarConfiguration.Side.HIJRI),
            ): List<com.khushu.engine.calendar.LunarCalendarView.LunarDayFact> =
                com.khushu.engine.calendar.LunarCalendarView.monthView(
                    hijriYear, hijriMonth, location, zoneId, config.hijriOffsetDays,
                )
        }
    }

    class QiblaApi internal constructor() {
        fun bearing(location: Location): QiblaBearing = Qibla.bearing(location)

        /** Generic great-circle initial bearing + distance between two locations. */
        fun bearingBetween(from: Location, to: Location): com.khushu.engine.qibla.BearingAndDistance =
            Qibla.bearingBetween(from, to)

        /**
         * Shadow-qibla verification windows for [year]: the sun stands over the
         * Kaaba (face toward it) and over its antipode (face away from it).
         * Purely geometric facts + mainstream method hints; no fiqh ruling.
         */
        fun shadowVerification(
            year: Int,
            location: Location,
        ): List<QiblaShadowEvent> = Qibla.shadowVerification(year, location)

        /**
         * Signed angle between the sun's azimuth and the local qibla bearing at
         * [instant] ([−180, 180]); plus whether they align within [toleranceDeg].
         */
        fun relativeSunAngle(
            location: Location,
            instant: Instant,
            toleranceDeg: Double = 2.0,
        ): SunQiblaRelation {
            val bearing = Qibla.bearing(location).bearingDegFromNorth.value
            val pos = Astronomy.sun.position(location, instant)
            var diff = (pos.azimuthDeg - bearing) % 360.0
            if (diff > 180.0) diff -= 360.0
            if (diff < -180.0) diff += 360.0
            return SunQiblaRelation(
                sunAzimuthDeg = pos.azimuthDeg,
                sunAltitudeDeg = pos.altitudeDeg,
                signedAngleToQiblaDeg = diff,
                alignedWithinTolerance = kotlin.math.abs(diff) <= toleranceDeg,
            )
        }
    }

    class ZakatApi internal constructor() {
        fun mal(assets: ZakatAssets, params: ZakatParams = ZakatParams()): ZakatResult =
            Zakat.mal(assets, params)

        fun fitrana(dependents: Int, pricePerKg: Double, madhab: ZakatMadhab): FitranaResult =
            Zakat.fitrana(dependents, pricePerKg, madhab)

        /**
         * Full hawl period for wealth owned since [ownershipStart]: hijri
         * anniversary with explicit snap provenance. Composed with the calendar
         * capability — the zakat module stays independent of calendar.
         */
        fun hawlPeriod(ownershipStart: LocalDate, offsetDays: Int = 0): com.khushu.engine.zakat.ZakatRules.HawlPeriod =
            com.khushu.engine.zakat.ZakatRules.hawlPeriod(ownershipStart, offsetDays)

        fun livestockSchedule(kind: com.khushu.engine.zakat.ZakatRules.Species) =
            com.khushu.engine.zakat.ZakatRules.scheduleFor(kind)

        fun livestockDue(kind: com.khushu.engine.zakat.ZakatRules.Species, count: Int) =
            com.khushu.engine.zakat.ZakatRules.livestockDue(kind, count)

        fun livestockNisab(kind: com.khushu.engine.zakat.ZakatRules.Species) =
            com.khushu.engine.zakat.ZakatRules.livestockNisab(kind)

        fun ushrDue(harvestValue: Double, irrigation: com.khushu.engine.zakat.ZakatRules.Irrigation): Double =
            com.khushu.engine.zakat.ZakatRules.ushrDue(harvestValue, irrigation)

        fun rikazDue(treasureValue: Double): Double =
            com.khushu.engine.zakat.ZakatRules.rikazDue(treasureValue)
    }
}

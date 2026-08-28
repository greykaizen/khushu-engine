package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.AltitudeConventions
import com.khushu.engine.astronomy.HilalReport
import com.khushu.engine.astronomy.LunarPosition
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.MonthlyMoonTrack
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.astronomy.SolarDayPhases
import com.khushu.engine.astronomy.SolarEvents
import com.khushu.engine.astronomy.SolarPosition
import com.khushu.engine.astronomy.SunTrack
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
import com.khushu.engine.mushaf.AtlasSpec
import com.khushu.engine.mushaf.AyahLayout
import com.khushu.engine.mushaf.GlyphPlacement
import com.khushu.engine.mushaf.LineLayout
import com.khushu.engine.mushaf.LineMeasure
import com.khushu.engine.mushaf.Mushaf
import com.khushu.engine.mushaf.WordLayout
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
 * engine.mushaf.layoutAyah(atlasSpec, words, fontSizePx, lineHeightPx, wordGapPx)
 * ```
 */
class KhushuEngine {

    val prayer = PrayerApi()
    val astronomy = AstronomyApi()
    val calendar = CalendarApi()
    val qibla = QiblaApi()
    val zakat = ZakatApi()
    val mushaf = MushafApi()

    /** Whole-day composite: every fact of one civil date in a single pass. */
    val day = com.khushu.engine.DayApi()

    // ── Namespaces: pure delegation, zero logic ─────────────────────────────

    class PrayerApi internal constructor() {
        /**
         * All prayer-related timings for one civil [date]: the five prayers plus
         * sunrise/sunset, midnight, night thirds, ishraq, zawaal, and a full
         * provenance [PrayerTimesResult.audit]. Deterministic.
         */
        fun times(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): PrayerTimesResult =
            Prayer.times(location, date, config)

        /** [times] for every civil date from [start] through [endInclusive]. */
        fun timesForRange(
            location: Location,
            start: LocalDate,
            endInclusive: LocalDate,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): List<PrayerTimesResult> = Prayer.timesForRange(location, start, endInclusive, config)

        /** [times] for a whole civil month — month-view / table UIs. */
        fun month(location: Location, yearMonth: YearMonth, config: PrayerConfiguration = PrayerConfiguration()): List<PrayerTimesResult> =
            Prayer.month(location, yearMonth, config)

        /**
         * Which obligatory prayer window covers [now] and what comes next, with
         * countdown/progress helpers on the result. See [Prayer.status] for the
         * midnight-spanning contract.
         */
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

        /** Category-labeled worship occurrences for a civil day; restrict with [categories] (default: all). */
        fun occasionsOn(
            location: Location,
            date: LocalDate,
            categories: Set<Prayer.OccasionCategory> = Prayer.OccasionCategory.entries.toSet(),
            config: PrayerConfiguration = PrayerConfiguration(),
        ): List<Prayer.Occurrence> =
            Prayer.occasionsOn(location, date, categories, config)

        /**
         * Current-first rotation around [now]: the in-progress prayer plus the
         * next [count] - 1 occurrences — "what's next" widgets and swipe
         * rotation. Spans midnight (after Isha, tomorrow's Fajr follows).
         */
        fun upcoming(
            location: Location,
            now: Instant,
            zoneId: ZoneId,
            count: Int = 5,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): List<Prayer.Entry> = Prayer.upcoming(location, now, zoneId, count, config)

        /**
         * Tahajjud window for the night beginning at [date]'s maghrib; also
         * the Witr window (see [TahajjudWindow]). Null when polar.
         */
        fun tahajjudWindow(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): TahajjudWindow? =
            Prayer.tahajjudWindow(location, date, config)

        /**
         * Full night-division set for the night beginning at [date]'s maghrib:
         * start, first third, midpoint, last third, end. Null when polar.
         */
        fun nightDivisions(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): Prayer.NightDivisions? =
            Prayer.nightDivisions(location, date, config)

        /**
         * Next occurrence of a specific prayer [kind] strictly after [after] —
         * the call behind "remind me at the next Fajr" style features.
         */
        fun nextOccurrenceOf(
            kind: PrayerStatus.Prayer,
            location: Location,
            after: Instant,
            zoneId: ZoneId,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): Prayer.Entry? = Prayer.nextOccurrenceOf(kind, location, after, zoneId, config)

        /** Next Friday congregational prayer strictly after [after]. */
        fun nextJumuah(
            location: Location,
            after: Instant,
            zoneId: ZoneId,
            config: PrayerConfiguration = PrayerConfiguration(),
        ): Prayer.Jumuah? = Prayer.nextJumuah(location, after, zoneId, config)

        val windows = WindowsApi()

        /**
         * Observance statistics over caller-supplied logs. The engine stays
         * persistence-free: the host passes its own records in.
         */
        val stats = StatsApi()

        inner class StatsApi internal constructor() {
            /** Streak and per-prayer completion statistics; see Prayer.streakStats day semantics. */
            fun streak(
                records: List<Prayer.PrayerLogRecord>,
                excusedRanges: List<ClosedRange<LocalDate>> = emptyList(),
                config: PrayerConfiguration = PrayerConfiguration(),
            ): Prayer.StreakStats = Prayer.streakStats(records, excusedRanges, config)
        }

        inner class WindowsApi internal constructor() {
            /** Preparatory fast boundary: a fixed margin before Fajr (configuration, not scripture). Null when polar. */
            fun imsak(location: Location, date: LocalDate, minutesBeforeFajr: Int = 10, config: PrayerConfiguration = PrayerConfiguration()): Instant? =
                Prayer.imsak(location, date, minutesBeforeFajr, config)

            /** Duha window: ishraq (sun at +4.5°) until Dhuhr enters. Null when polar. */
            fun duha(location: Location, date: LocalDate, config: PrayerConfiguration = PrayerConfiguration()): ClosedRange<Instant>? =
                Prayer.duhaWindow(location, date, config)

            /** Prohibited (karahat) windows; margins are documented conventions. */
            fun forbidden(location: Location, date: LocalDate, lateAfternoonMarginMinutes: Int = 20, config: PrayerConfiguration = PrayerConfiguration()): Prayer.ForbiddenWindows =
                Prayer.forbiddenWindows(location, date, lateAfternoonMarginMinutes, config)

            /** Objective fasting-day facts: imsak, fast start (Fajr), fast end (Maghrib). */
            fun fastingFacts(location: Location, date: LocalDate, imsakMinutesBeforeFajr: Int = 10, config: PrayerConfiguration = PrayerConfiguration()): Prayer.FastingFacts =
                Prayer.fastingFacts(location, date, imsakMinutesBeforeFajr, config)

            /** Whole-month convenience over [fastingFacts] for Ramadan/seasonal screens. */
            fun fastingFactsForMonth(
                location: Location,
                yearMonth: YearMonth,
                imsakMinutesBeforeFajr: Int = 10,
                config: PrayerConfiguration = PrayerConfiguration(),
            ): List<Prayer.FastingFacts> =
                Prayer.fastingFactsForMonth(location, yearMonth, imsakMinutesBeforeFajr, config)

            /** Travel (qasr) facts: distance eligibility, shortenable prayers, joining anchors. Times are never mutated. */
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
            /** Topocentric solar position (azimuth/altitude/declination/right ascension, degrees) at one instant. */
            fun position(location: Location, instant: Instant): SolarPosition =
                Astronomy.sun.position(location, instant)

            /** Sunrise/sunset for a civil date; nulls mean the event never happens that day (polar). */
            fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet =
                Astronomy.sun.riseSet(location, date, zoneId)

            /** Daylight length; null on polar days with no sunrise/sunset pair. */
            fun dayLength(location: Location, date: LocalDate, zoneId: ZoneId): java.time.Duration? =
                Astronomy.sun.dayLength(location, date, zoneId)

            /** Per-altitude solar events of a day (dawn, golden hour, dusk, …) per [conventions]. */
            fun events(location: Location, date: LocalDate, zoneId: ZoneId): SolarEvents =
                Astronomy.sun.events(location, date, zoneId)

            /** Solar phases (twilight bands) of a day. */
            fun phases(location: Location, date: LocalDate, zoneId: ZoneId): SolarDayPhases =
                Astronomy.sun.phases(location, date, zoneId)

            /** Night length (sunset → next sunrise); null on polar days. */
            fun nightLength(location: Location, date: LocalDate, zoneId: ZoneId): java.time.Duration? =
                Astronomy.sun.nightLength(location, date, zoneId)

            /** The altitude conventions (degrees) used by [events]/[phases]. */
            fun conventions(): AltitudeConventions = Astronomy.sun.conventions()

            /** One-pass monthly solar track (rise/set + optional sampled sky path) for AR/sky UIs. */
            fun track(
                location: Location,
                yearMonth: YearMonth,
                zoneId: ZoneId,
                includePath: Boolean = false,
            ): SunTrack = Astronomy.sun.track(location, yearMonth, zoneId, includePath)
        }

        class MoonApi internal constructor() {
            /** Topocentric lunar position (+ distance) at one instant. */
            fun position(location: Location, instant: Instant): LunarPosition =
                Astronomy.moon.position(location, instant)

            /** Moon phase state: phase angle, illumination, elongation, phase name, bright-limb tilt. */
            fun state(location: Location, instant: Instant): MoonState =
                Astronomy.moon.state(location, instant)

            /** Moonrise/moonset for a civil date; nulls mean no event that day. */
            fun riseSet(location: Location, date: LocalDate, zoneId: ZoneId): RiseSet =
                Astronomy.moon.riseSet(location, date, zoneId)

            /** One-pass monthly lunar track (rise/set, phases, illumination + optional path). */
            fun track(
                location: Location,
                yearMonth: YearMonth,
                zoneId: ZoneId,
                includePath: Boolean = false,
                pathSamplesPerDay: Int = 48,
            ): MonthlyMoonTrack = Astronomy.moon.track(location, yearMonth, zoneId, includePath, pathSamplesPerDay)

            /** Human-readable phase name for a phase angle in degrees. */
            fun phaseName(phaseAngleDeg: Double): String = Astronomy.moon.phaseName(phaseAngleDeg)

            /** First new moon at or after [after]. */
            fun nextNewMoon(after: Instant) = Astronomy.moon.nextNewMoon(after)
            /** First full moon at or after [after]. */
            fun nextFullMoon(after: Instant) = Astronomy.moon.nextFullMoon(after)
            /** First occurrence of a quarter phase (0/90/180/270 degrees) at or after [after]. */
            fun nextPhase(targetPhaseDeg: Double, after: Instant) = Astronomy.moon.nextPhase(targetPhaseDeg, after)

            /** Perigee/apogee extremes within a scan window; [to] must be after [from]. */
            fun distanceExtremes(from: Instant, to: Instant) = Astronomy.moon.distanceExtremes(from, to)
            /** Next solar eclipse visible somewhere on Earth. */
            fun nextGlobalSolarEclipse(after: Instant) = Astronomy.moon.nextGlobalSolarEclipse(after)
            /** Next lunar eclipse. */
            fun nextLunarEclipse(after: Instant) = Astronomy.moon.nextLunarEclipse(after)
        }

        class HilalApi internal constructor() {
            /** Crescent-visibility report for the evening of [date]; null when the moon is uncomputable. */
            fun visibility(location: Location, date: LocalDate, zoneId: ZoneId): HilalReport? =
                Astronomy.hilal.visibility(location, date, zoneId)

            /** Multi-night visibility forecast (up to 60 nights) starting at [from]. */
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
        /**
         * Tabular Umm al-Qura hijri date for a civil date. [offsetDays] (−2..+2)
         * applies a sighting-style adjustment. Throws UpstreamComputationException
         * outside the table's 1300–1600 AH span.
         */
        fun hijri(localDate: LocalDate, offsetDays: Int = 0): HijriDate =
            HijriCalendar.hijri(localDate, offsetDays)

        /**
         * Reverse conversion: civil date of a hijri date.
         * @throws com.khushu.engine.core.error.InvalidParameterException structurally invalid components
         * @throws com.khushu.engine.core.error.HijriDayDoesNotExistException the day does not exist (e.g. 30 in a 29-day month)
         */
        fun hijriToGregorian(hijriYear: Int, hijriMonth: Int, hijriDay: Int, offsetDays: Int = 0): LocalDate =
            HijriCalendar.hijriToGregorian(hijriYear, hijriMonth, hijriDay, offsetDays)

        /** Lengths (29 or 30) of all twelve months of a hijri year. */
        fun hijriMonthLengths(hijriYear: Int, offsetDays: Int = 0): List<Int> =
            HijriCalendar.hijriMonthLengths(hijriYear, offsetDays)

        /** Fixed-hijri-date Islamic events falling on this civil day. */
        fun events(localDate: LocalDate, offsetDays: Int = 0): List<IslamicEvent> =
            HijriCalendar.events(localDate, offsetDays)

        /** All events within a civil-date range (inclusive), each paired with its date. */
        fun eventsInRange(range: ClosedRange<LocalDate>, offsetDays: Int = 0): List<Pair<LocalDate, IslamicEvent>> =
            HijriCalendar.eventsInRange(range, offsetDays)

        /**
         * Next civil date on which the fixed hijri [month]/[day] falls, at or after [after].
         * @throws com.khushu.engine.core.error.NoResultException when the day does not occur within the search window
         */
        fun nextOccurrence(month: Int, day: Int, after: LocalDate, offsetDays: Int = 0): LocalDate =
            HijriCalendar.nextOccurrence(month, day, after, offsetDays)

        /**
         * Most recent civil date on which the fixed hijri [month]/[day] fell,
         * at or before [before] — mirror of [nextOccurrence].
         * @throws com.khushu.engine.core.error.NoResultException when the day does not occur within the search window
         */
        fun previousOccurrence(month: Int, day: Int, before: LocalDate, offsetDays: Int = 0): LocalDate =
            HijriCalendar.previousOccurrence(month, day, before, offsetDays)

        /** True when the 1-indexed hijri month is one of the four sacred months. */
        fun isSacredMonth(hijriMonth: Int): Boolean = HijriCalendar.isSacredMonth(hijriMonth)

        /**
         * Optional fast days within [range] per [params]. Prohibited days
         * (both Eids, Tashreeq) are always excluded.
         */
        fun fastDays(range: ClosedRange<LocalDate>, params: CalendarParams): List<FastDay> =
            HijriCalendar.fastDays(range, params)

        // ── Namespaced capability tree ──────────────────────────────────────

        /** Local Islamic-day calculation using SUNSET — distinct from tabular conversion. */
        val date = DateApi()
        /** Civil-month/hijri-month matrix, boundaries, and hijri date arithmetic. */
        val month = MonthApi()
        /** Ramadan/sacred-month/hajj-season facts and per-date boolean helpers. */
        val facts = FactsApi()

        /**
         * Event registry: built-in core pack + host-registered packs
         * (engine schema or Aladhan shape) merged into per-date occurrences.
         */
        fun events(config: CalendarConfiguration): EventRegistry = EventRegistry(config.hijriOffsetDays)

        val observance = ObservanceApi()
        /** Astronomy-informed lunar month rendering (moon facts at each evening). */
        val lunar = LunarApi()
        /** Region-based civil calendars (Persian, Śaka, Bangla, Coptic, …) — pivot regional↔Gregorian↔Hijri. */
        val civil = CivilCalendarApi()

        /**
         * Region-based civil calendars people live by day-to-day. Rendering in
         * dual-calendar UIs is automatic: set
         * [CalendarConfiguration.civilCalendar] and every GREGORIAN side line
         * becomes the chosen system ([DateLine.Regional]). The Hijri side is
         * always Umm al-Qura and unaffected.
         */
        class CivilCalendarApi internal constructor() {
            /** Render a civil date in [system]. */
            fun toRegional(date: LocalDate, system: com.khushu.engine.calendar.CivilCalendarType): com.khushu.engine.calendar.RegionalDate =
                com.khushu.engine.calendar.RegionalCalendars.toRegional(date, system)

            /**
             * Reverse conversion: civil date of a regional date.
             * @throws com.khushu.engine.core.error.InvalidParameterException structurally invalid components,
             *   a day that does not exist in that year, or JAPANESE (era disambiguation unsupported)
             */
            fun fromRegional(system: com.khushu.engine.calendar.CivilCalendarType, year: Int, month: Int, day: Int): LocalDate =
                com.khushu.engine.calendar.RegionalCalendars.fromRegional(system, year, month, day)

            /** Month names for [system] (1-indexed). */
            fun monthNames(system: com.khushu.engine.calendar.CivilCalendarType): List<String> =
                com.khushu.engine.calendar.RegionalCalendars.monthNames(system)
        }

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
            /**
             * Per-evening moon facts (phase, illumination, altitude, visibility
             * sampling) for one hijri month — the lunar-calendar view.
             */
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
        /** Great-circle bearing from north toward the Kaaba, plus the distance to it. */
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
        /**
         * Zakat al-mal over [assets]: nisab valuation (gold or silver per
         * [ZakatParams.nisabSource]), 2.5% rate, madhab rules, full breakdown.
         * @throws com.khushu.engine.core.error.InvalidParameterException when a value is negative/NaN or the nisab metal price is missing
         */
        fun mal(assets: ZakatAssets, params: ZakatParams = ZakatParams()): ZakatResult =
            Zakat.mal(assets, params)

        /** Zakat al-fitr per household: saʿ-based kg convention × staple price. */
        fun fitrana(dependents: Int, pricePerKg: Double, madhab: ZakatMadhab): FitranaResult =
            Zakat.fitrana(dependents, pricePerKg, madhab)

        /**
         * Full hawl period for wealth owned since [ownershipStart]: hijri
         * anniversary with explicit snap provenance. Composed with the calendar
         * capability — the zakat module stays independent of calendar.
         */
        fun hawlPeriod(ownershipStart: LocalDate, offsetDays: Int = 0): com.khushu.engine.zakat.ZakatRules.HawlPeriod =
            com.khushu.engine.zakat.ZakatRules.hawlPeriod(ownershipStart, offsetDays)

        /** Full nisab/band schedule for a livestock species. */
        fun livestockSchedule(kind: com.khushu.engine.zakat.ZakatRules.Species) =
            com.khushu.engine.zakat.ZakatRules.scheduleFor(kind)

        /** Structured livestock obligation for [count] animals; null below nisab. */
        fun livestockDue(kind: com.khushu.engine.zakat.ZakatRules.Species, count: Int) =
            com.khushu.engine.zakat.ZakatRules.livestockDue(kind, count)

        /** Minimum head count before zakat applies to a species. */
        fun livestockNisab(kind: com.khushu.engine.zakat.ZakatRules.Species) =
            com.khushu.engine.zakat.ZakatRules.livestockNisab(kind)

        /** Ushr due on a harvest given its irrigation kind. */
        fun ushrDue(harvestValue: Double, irrigation: com.khushu.engine.zakat.ZakatRules.Irrigation): Double =
            com.khushu.engine.zakat.ZakatRules.ushrDue(harvestValue, irrigation)

        /** Ushr rate by irrigation: NATURAL (rain-fed) 10%, ARTIFICIAL 5%. */
        fun ushrRate(irrigation: com.khushu.engine.zakat.ZakatRules.Irrigation): Double =
            com.khushu.engine.zakat.ZakatRules.ushrRate(irrigation)

        /** Zakat due on buried treasure (rikaz): 20%. */
        fun rikazDue(treasureValue: Double): Double =
            com.khushu.engine.zakat.ZakatRules.rikazDue(treasureValue)
    }

    /**
     * Glyph-atlas layout math for the mushaf renderer: measure, fit, and place
     * words/lines/ayahs into absolute pixel positions (fu = font units).
     * All functions are pure; see module `Mushaf` KDoc for full contracts.
     */
    class MushafApi internal constructor() {
        /** Word width in font units from its glyph placements. */
        fun wordWidthFu(placements: List<GlyphPlacement>): Double = Mushaf.wordWidthFu(placements)

        /** Rendered word width in pixels for a given atlas spec and font size. */
        fun measureWordWidthPx(spec: AtlasSpec, placements: List<GlyphPlacement>, fontSizePx: Float): Float =
            Mushaf.measureWordWidthPx(spec, placements, fontSizePx)

        /** Line width in pixels from per-word widths (centered vs justified modes). */
        fun measureLineWidthPx(wordWidthsPx: List<Float>, centered: Boolean, baseFontSizePx: Float): Float =
            Mushaf.measureLineWidthPx(wordWidthsPx, centered, baseFontSizePx)

        /** Device screen-width scaling factor for a content width in dp. */
        fun screenWidthScale(lineInnerWidthDp: Float): Float = Mushaf.screenWidthScale(lineInnerWidthDp)

        /** One uniform font scale per page (median-fill; see `Mushaf.fitPageScale`). */
        fun fitPageScale(
            lines: List<LineMeasure>,
            contentWidthPx: Float,
            baseFontSizePx: Float,
            fallbackScale: Float,
        ): Float = Mushaf.fitPageScale(lines, contentWidthPx, baseFontSizePx, fallbackScale)

        /** Bounded per-line shrink factor when a measured line overflows. */
        fun fitLineShrink(measuredWidthPx: Float, maxLineWidthPx: Float, bounded: Boolean): Float =
            Mushaf.fitLineShrink(measuredWidthPx, maxLineWidthPx, bounded)

        /** Line height in pixels for a font size (fixed mushaf line metrics). */
        fun lineHeightPx(fontSizePx: Float): Float = Mushaf.lineHeightPx(fontSizePx)

        /** Place one word's glyphs; RTL origin at the right. */
        fun layoutWord(spec: AtlasSpec, placements: List<GlyphPlacement>, fontSizePx: Float): WordLayout =
            Mushaf.layoutWord(spec, placements, fontSizePx)

        /** Place one line of words (single line, no wrapping). */
        fun layoutLine(
            spec: AtlasSpec,
            words: List<List<GlyphPlacement>>,
            fontSizePx: Float,
            lineHeightPx: Float,
            wordGapPx: Float,
        ): LineLayout = Mushaf.layoutLine(spec, words, fontSizePx, lineHeightPx, wordGapPx)

        /**
         * Full ayah layout with optional greedy wrapping — absolute pixel
         * destinations for every glyph (hosts blit atlas texture rects there).
         */
        fun layoutAyah(
            spec: AtlasSpec,
            words: List<List<GlyphPlacement>>,
            fontSizePx: Float,
            lineHeightPx: Float,
            wordGapPx: Float,
            maxLineWidthPx: Float = 0f,
        ): AyahLayout = Mushaf.layoutAyah(spec, words, fontSizePx, lineHeightPx, wordGapPx, maxLineWidthPx)
    }
}

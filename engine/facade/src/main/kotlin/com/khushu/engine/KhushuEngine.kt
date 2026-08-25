package com.khushu.engine

import com.khushu.engine.astronomy.Astronomy
import com.khushu.engine.astronomy.HilalReport
import com.khushu.engine.astronomy.LunarPosition
import com.khushu.engine.astronomy.MoonState
import com.khushu.engine.astronomy.MonthlyMoonTrack
import com.khushu.engine.astronomy.RiseSet
import com.khushu.engine.astronomy.SolarEvents
import com.khushu.engine.astronomy.SolarPosition
import com.khushu.engine.calendar.Calendar as CalendarCapability
import com.khushu.engine.calendar.CalendarParams
import com.khushu.engine.calendar.FastDay
import com.khushu.engine.calendar.HijriDate
import com.khushu.engine.calendar.IslamicEvent
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import com.khushu.engine.prayer.PrayerParams
import com.khushu.engine.prayer.PrayerStatus
import com.khushu.engine.prayer.PrayerTimesResult
import com.khushu.engine.qibla.Qibla
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

    // ── Namespaces: pure delegation, zero logic ─────────────────────────────

    class PrayerApi internal constructor() {
        fun times(location: Location, date: LocalDate, params: PrayerParams = PrayerParams()): PrayerTimesResult =
            Prayer.times(location, date, params)

        fun status(location: Location, now: Instant, zoneId: ZoneId, params: PrayerParams = PrayerParams()): PrayerStatus =
            Prayer.status(location, now, zoneId, params)
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
        }

        class HilalApi internal constructor() {
            fun visibility(location: Location, date: LocalDate, zoneId: ZoneId): HilalReport? =
                Astronomy.hilal.visibility(location, date, zoneId)
        }
    }

    class CalendarApi internal constructor() {
        fun hijri(localDate: LocalDate, offsetDays: Int = 0): HijriDate =
            CalendarCapability.hijri(localDate, offsetDays)

        fun events(localDate: LocalDate, offsetDays: Int = 0): List<IslamicEvent> =
            CalendarCapability.events(localDate, offsetDays)

        fun fastDays(range: ClosedRange<LocalDate>, params: CalendarParams): List<FastDay> =
            CalendarCapability.fastDays(range, params)
    }

    class QiblaApi internal constructor() {
        fun bearing(location: Location): QiblaBearing = Qibla.bearing(location)
    }

    class ZakatApi internal constructor() {
        fun mal(assets: ZakatAssets, params: ZakatParams = ZakatParams()): ZakatResult =
            Zakat.mal(assets, params)

        fun fitrana(dependents: Int, pricePerKg: Double, madhab: ZakatMadhab): FitranaResult =
            Zakat.fitrana(dependents, pricePerKg, madhab)
    }
}

# Public API Snapshot — v1.0 lock candidate (2026-08)

Frozen surfaces. Internal calculators (`internal/` packages) are explicitly NOT
part of this contract and may be rewritten freely. Any change to the signatures
below requires a new entry in docs/divergences.md and a minor/major version bump.

## com.khushu.engine.KhushuEngine  (facade)
```
KhushuEngine()
.engine.prayer.times(location, date, params = PrayerParams()): PrayerTimesResult
.engine.prayer.status(location, now, zoneId, params = PrayerParams()): PrayerStatus
.engine.astronomy.sun.position(location, instant): SolarPosition
.engine.astronomy.sun.riseSet(location, date, zoneId): RiseSet
.engine.astronomy.sun.events(location, date, zoneId): SolarEvents
.engine.astronomy.moon.position(location, instant): LunarPosition
.engine.astronomy.moon.state(location, instant): MoonState
.engine.astronomy.moon.riseSet(location, date, zoneId): RiseSet
.engine.astronomy.moon.track(location, yearMonth, zoneId,
        includePath = false, pathSamplesPerDay = 48): MonthlyMoonTrack
.engine.astronomy.moon.phaseName(phaseAngleDeg): String
.engine.astronomy.hilal.visibility(location, date, zoneId): HilalReport?
.engine.calendar.hijri(localDate, offsetDays = 0): HijriDate
.engine.calendar.events(localDate, offsetDays = 0): List<IslamicEvent>
.engine.calendar.fastDays(range, params: CalendarParams): List<FastDay>
.engine.qibla.bearing(location): QiblaBearing
.engine.zakat.mal(assets, params = ZakatParams()): ZakatResult
.engine.zakat.fitrana(dependents, pricePerKg, madhab): FitranaResult
```

## core — com.khushu.engine.core.geo
`Latitude(deg)` · `Longitude(deg)` · `AltitudeMeters(m)` — validated value classes
`Location(latitude, longitude, altitudeMeters = 0)` · `Location.of(latDeg, lonDeg, altDeg = 0)`

## prayer — com.khushu.engine.prayer
- Enums: `Madhab{SHAFII,HANAFI,MALIKI,HANBALI}` · `Convention{12 presets + CUSTOM}` ·
  `HighLatitudeRule{MIDDLE_OF_NIGHT,SEVENTH_OF_NIGHT,TWILIGHT_ANGLE}`
- Data: `PrayerOffsets(fajr,sunrise,dhuhr,asr,sunset,maghrib,isha: Int)` ·
  `PrayerParams` (defaults: SHAFII / MWL / 18° / middle-of-night / no offsets) ·
  `PrayerTimesResult(date, fajr..isha nullable Instants, midnight, astronomicalMidnight,
  lastThirdOfNight, ishraq, zawaalStart, dhuhrEnters, isIntervalIsha, polarAnomaly)` ·
  `PrayerStatus(current, next, currentStart, nextStart, now; countdown(); progress())`
- Invariants locked by goldens + properties: exact adhan2-0.0.7 parity on the
  golden matrix; nulls only where physically uncomputable.

## astronomy — com.khushu.engine.astronomy
- Result types: `SolarPosition` · `LunarPosition` · `MoonState(phaseAngleDeg,
  illuminationFraction, elongationDeg, phaseName, brightLimbTiltDeg)` · `RiseSet` ·
  `SolarEvent(SolarEventType, epochMs)` · `SolarEvents(date, events chronological)` ·
  `CelestialPathPoint(epochMs, az, alt, enu[3], aboveHorizon)` ·
  `MonthlyMoonTrack(days: List<DayMoon>, pathPoints)` ·
  `HilalReport(sunset, moonset?, bestTime, lagMinutes, moonAgeHours, arcv, elongation,
  crescentWidthArcmin, illumination, yallop: YallopGrade, odeh: OdehZone,
  verdicts: List<SightingVerdict>)`
- Invariants: cosinekitty 2.1.19 parity (golden matrix); illumination ∈ [0,1];
  tilt ∈ [0,360); lunar distance ∈ perigee/apogee band; events bounded to civil day;
  transit survives polar day/night.

## calendar — com.khushu.engine.calendar
- `CalendarParams(hijriOffsetDays −2..+2 enforced, six optionalFast flags)`
- `HijriDate(year, month 1..12, day, monthName, offsetApplied; label)` — Umm al-Qura tabular
- `IslamicEvent(title, hijriDateLabel)` · `FastRule` · `FastDay(date, rule)`

## qibla — com.khushu.engine.qibla
- `QiblaBearing(bearingDegFromNorth [0,360), greatCircleDistanceKm)`
- Constants: `KAABA_LATITUDE_DEG`, `KAABA_LONGITUDE_DEG`

## zakat — com.khushu.engine.zakat
- Enums: `ZakatMadhab` · `NisabSource{GOLD,SILVER}` ·
  `NisabWeightConvention{COMMON(85g/595g), CLASSICAL(87.48g/612.36g)}`
- `ZakatAssets` (validated ≥ 0) · `ZakatParams` · `AssetContribution(label, amount)` ·
  `ZakatResult(netWealth, nisabThreshold, nisabReached, hawlComplete, rate = 0.025,
  zakatDue, breakdown)` · `FitranaResult(saKg, dependents, pricePerKg,
  perPersonAmount, totalForHousehold)` — saʿ conventions 2.175 kg majority / 3.0 kg Hanafi

## tools (NOT public API)
- `tools/cli`: prayer | sun | moon [--ym] | hijri | events | qibla | verify
- `tools/golden-dumper/{prayer,astro}`: fixture regeneration (donor replicas)

## v1.1 breadth additions (2026-08)

### astronomy
```
moon.state(...) → MoonState now includes moonAgeDays, isWaxing
sun.dayLength(location, date, zoneId): Duration?
moon.nextPhase(0|90|180|270, after): UpcomingMoonPhase?
moon.nextNewMoon(after) / moon.nextFullMoon(after): UpcomingMoonPhase?
moon.distanceExtremes(from, to): List<MoonDistanceExtreme>   // geocentric perigee/apogee
moon.nextGlobalSolarEclipse(after): GlobalSolarEclipse?
moon.nextLunarEclipse(after): LunarEclipse?
hilal.forecast(location, from, zoneId, nights = 8): List<Pair<LocalDate, HilalReport?>>
```

### calendar
```
calendar.hijriToGregorian(y, m, d, offsetDays = 0): LocalDate      // reverse conversion
calendar.hijriMonthLengths(hijriYear, offsetDays = 0): List<Int>   // twelve 29/30 lengths
calendar.eventsInRange(range, offsetDays = 0): List<Pair<LocalDate, IslamicEvent>>
calendar.nextOccurrence(month, day, after, offsetDays = 0): LocalDate
calendar.isSacredMonth(hijriMonth): Boolean
```

### prayer
```
prayer.tahajjudWindow(location, date, params): TahajjudWindow?     // midnight → lastThird → fajr
```

### qibla / zakat / day composite (facade)
```
engine.qibla.solarAlignmentInstants(year): List<Instant>           // sun over the Kaaba, 2×/year
engine.zakat.hawlAnniversary(ownershipStart, offsetDays = 0): LocalDate
engine.zakat.livestockNisab(kind) / livestockDue(kind, count)
engine.zakat.ushrDue(harvestValue, irrigation) · rikazDue(value)
engine.day.summary(location, date, zoneId, prayerParams, calendarParams): DaySummary
    // one-pass whole-day composite — shared facts computed once internally

CachedEngine(delegate)  // opt-in memoization; semantic keys per AGENTS §6:
                        // prayer→(Location,date,params) sun→(Location,instant)
                        // moonState→(Location,instant) hijri→(date,offset)
```

### fixtures
- calendar: `hijri_golden.json` (3655 cases, donor ummalqura call path), regenerable via tools/golden-dumper/calendar.

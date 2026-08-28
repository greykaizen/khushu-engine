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

## mushaf — com.khushu.engine.mushaf  (v1.4)
Pure glyph-atlas layout computation for pixel-perfect mushaf rendering.
Content-free: specs (font metrics, glyph table, word placements) are fed in
by the host from khushu-data-api `inventory/atlas/` bundles.
- Data: `AtlasFontMetrics(unitsPerEm, ascenderFu, descenderFu, heightFu, lineGapFu)` ·
  `GlyphSrcRect(textureIndex, x, y, w, h)` · `GlyphMetrics(rect, bearingX, bearingY, advance)` ·
  `AtlasSpec(font, ppem, glyphs)` · `GlyphPlacement(glyphId, xAdvanceFu, yAdvanceFu, xOffsetFu, yOffsetFu)` ·
  `PlacedGlyph` · `WordLayout` · `LineLayout`/`LineWord` · `AyahLine`/`AyahLayout` · `LineMeasure`
- `object Mushaf`:
  - Tuning constants: `LINE_HEIGHT_MULTIPLIER 2f` · `FONT_SCALE_AT_MIN_WIDTH 0.85f` ·
    `FONT_SCALE_AT_MAX_WIDTH 1.5f` · `SCREEN_WIDTH_DP_MIN 260f` · `SCREEN_WIDTH_DP_MAX 600f` ·
    `CENTERED_GAP_FRACTION 0.22f` · `MIN_INTER_WORD_GAP_FRACTION 0.1f` ·
    `LINE_SHRINK_MIN 0.16f` · `WIDE_LINE_FILL_THRESHOLD 0.82f`
  - Measurement: `wordWidthFu(placements)` · `measureWordWidthPx(spec, placements, fontSizePx)` ·
    `measureLineWidthPx(wordWidthsPx, centered, baseFontSizePx)` · `screenWidthScale(lineInnerWidthDp)`
  - Fitting: `fitPageScale(lines, contentWidthPx, baseFontSizePx, fallbackScale)` ·
    `fitLineShrink(measuredWidthPx, maxLineWidthPx, bounded)` · `lineHeightPx(fontSizePx)`
  - Placement (RTL): `layoutWord(spec, placements, fontSizePx)` ·
    `layoutLine(spec, words, fontSizePx, lineHeightPx, wordGapPx)` ·
    `layoutAyah(spec, words, fontSizePx, lineHeightPx, wordGapPx, maxLineWidthPx=0)`

## tools (NOT public API)
- `tools/cli`: prayer | sun | moon [--ym] | hijri | events | qibla | verify
- `tools/golden-dumper/{prayer,astro}`: fixture regeneration (donor replicas)

## v1.1 breadth additions (2026-08)

### astronomy
```
sun.position/riseSet/events/dayLength/nightLength/phases/track/conventions/audit
sun.phases(location, date, zoneId): SolarDayPhases   // threshold-segment bands:
  lateNight/earlyNight/morning{Astro,Nautical,Civil,Blue,Golden}/daylight/
  evening{...} + SolarAltitudeCrossings raw primitives + AstroAudit
sun.track(location, yearMonth, zoneId, includePath=false): SunTrack
moon.state → MoonState(moonAgeDays: Double? — null when conjunction search fails)
moon.track / nextPhase / nextNewMoon / nextFullMoon / distanceExtremes /
  nextGlobalSolarEclipse / nextLunarEclipse
seasons(year): Seasons (equinoxes/solstices)
hilal.visibility / hilal.forecast → List<HilalForecastDay>
AltitudeConventions: pinned threshold table (blue [−6,−4), golden [−4,+6),
  twilights −6/−12/−18, horizon −0.833) — documented conventions
Dual sunset policy: prayer-convention time (prayer module) and astronomical
  ephemeris time (astronomy module) are DISTINCT named facts (divergences D16)
```

### calendar
```
HijriCalendar (was `Calendar`): hijri · hijriToGregorian · hijriMonthLengths ·
  events · eventsInRange · nextOccurrence · isSacredMonth · fastDays · builtInDefinitions
calendar.date.islamicDate(civil) / islamicDateAt(instant, location, zone, config)
  // islamicDateAt = local Islamic-day calculation using SUNSET boundary
calendar.date.primary/secondary/both/convert(civil, config): DateLine/DualDate
calendar.month.monthMatrix/yearMatrix/hijriYearMap/boundaries/addDays/addMonths
HijriArithmetic: daysInMonth/daysInYear/isLeapYear/dayOfYear/remainingDaysInMonth
Facts: ramadan/isRamadan/isLastTenNights/oddNightSeekCandidates/sacredMonths/
  hajjSeason/firstTenDhulHijjah/arafah/ashura/tasua/eidAlFitr/eidAlAdha/
  tashreeqDays/whiteDays
EventRegistry: registerPack(json)/registerAladhan(json)/occurrencesOn(date,
  observance?)/occurrencesInRange/upcoming(id, after, count) + builtInPackJson()
ObservanceContext(ObservedDateOverride...)   // applies only when explicitly passed
LunarCalendarView.monthView(y, m, location, zoneId): LunarDayFact*

### prayer
```
prayer.tahajjudWindow(location, date, params): TahajjudWindow?     // midnight → lastThird → fajr
```

### qibla (module + facade)
```
qibla.bearing(location): QiblaBearing(bearingDegFromNorth: Degrees, greatCircleDistanceKm: Kilometers)
qibla.bearingBetween(from, to): Pair<Double, Double>       // generic great-circle
qibla.shadowVerification(year, location): List<QiblaShadowEvent>
  // Kaaba-zenith + antipodal passages; FACE_TOWARD/FACE_AWAY; visibility + shadow bearing
engine.qibla.relativeSunAngle(location, instant, toleranceDeg = 2.0): SunQiblaRelation
engine.qibla.solarAlignmentInstants(year): List<Instant>   // convenience passthrough
QiblaAudit: Kaaba constants origin, spherical model, formula — provenance

### zakat rules (ZakatRules)
```
hawlPeriod(ownershipStart, offsetDays, hijriBridge): HawlPeriod(starts, anniversary,
  ends, hijriYearCompleted, snappedDays)   // facade wires the calendar bridge
LivestockSchedule tables per Species — declarative bands with provenance
livestockDue(species, count): LivestockDue?(species, countAssessed, nisabThreshold,
  headcountDue, animalClass, provenance, requiresScholarReview?, reviewReason?)
livestockNisab(species) · ushrRate/ushrDue(Irrigation) · rikazDue(value)
Camel >120: classical forty/fifty decomposition (IslamQA #71267); unresolved
  excess → requiresScholarReview instead of a guess (D19)

### day composite (facade)
```
engine.day.summary(location, date, zoneId, prayerParams, calendarParams): DaySummary
    // one-pass whole-day composite — shared facts computed once internally

CachedEngine(delegate)  // opt-in memoization; semantic keys per AGENTS §6:
                        // prayer→(Location,date,params) sun→(Location,instant)
                        // moonState→(Location,instant) hijri→(date,offset)
```

### fixtures
- calendar: `hijri_golden.json` (3655 cases, donor ummalqura call path), regenerable via tools/golden-dumper/calendar.

## v1.5 additions (2026-08)

### core — error model (graceful failures)
```
KhushuInputFailure : IllegalArgumentException        // bad caller argument
KhushuComputationFailure : IllegalStateException     // no legitimate answer / upstream
InvalidParameterException(parameter, value, constraint) : KhushuInputFailure
HijriDayDoesNotExistException(hijriYear, hijriMonth, hijriDay, offsetDays) : KhushuInputFailure
NoResultException(detail) : KhushuComputationFailure
UpstreamComputationException(detail, cause) : KhushuComputationFailure
validate(condition) { failure }                       // structured require()
```
All engine entry points migrated from bare `require()`; ummalqura out-of-table
dates (outside 1300–1600 AH) surface as typed UpstreamComputationException.
Catch-compat: IAE/ISE catches keep working. See README §Error handling.

### prayer
```
prayer.stats.streak(records, excusedRanges = [], config): StreakStats
  // observance statistics over caller-supplied logs (streaks, per-prayer rates)
prayer.nextOccurrenceOf(kind, location, after, zoneId, config): Entry?
  // next occurrence of a specific prayer strictly after an instant
prayer.nextJumuah(location, after, zoneId, config): Jumuah?   // (date, dhuhr)
prayer.nightDivisions(location, date, config): NightDivisions?
  // nightStarts/firstThirdBegins/midpoint/lastThirdBegins/nightEnds
prayer.windows.fastingFactsForMonth(location, yearMonth, imsakMinutesBeforeFajr = 10, config)
  : List<FastingFacts>
```
`NightDivisionMethod` enum: kept, intentionally unwired (deferred queue).

### zakat / calendar gaps
```
zakat.ushrRate(irrigation): Double              // 0.10 natural / 0.05 artificial
calendar.facts.dayOfYear(date, config): Int
calendar.facts.remainingDaysInMonth(date, config): Int
```

### hygiene
- KDoc on every public facade member (incl. @throws error contracts).
- Removed dead `gradle/libs.versions.toml`; removed stale test-only @Suppress.
- Dependency-bump runbook: docs/dependency-bumps.md. Deprecation policy: README.

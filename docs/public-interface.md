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
    // prayer times/month/occasions/tahajjud/nightDivisions/windows
    //   → (Location, date|YearMonth, params, margins/categories)
    // daySummary → (Location, date, zone, prayer+calendar params)
    // sun→(Location,instant|date+zone) moonState→(Location,instant)
    // moonRiseSet→(Location,date+zone) hijri→(date,offset) qibla→(Location)
    // calendar events→(date|range,offset) fastDays→(range,params)
    // monthMatrix→(YearMonth,config) lunarMonthView→(Location,zone,hijriY/M,config)
    // forceRecompute=true on any method → bypass + recompute + OVERWRITE entry
    // clearCaches() all; clearCaches(domain) per CachedEngine.DOMAINS
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

## v1.6 additions (2026-08)

### prayer
```
prayer.upcoming(location, now, zoneId, count = 5, config): List<Entry>
  // current-first rotation: in-progress prayer + next count-1 occurrences;
  // spans midnight (after Isha → tomorrow's Fajr). Convenience over
  // navigationSequence(before=0, after=count-1) — throws InvalidParameterException
  // for count <= 0.
prayer.occasionsOn(location, date, categories = all, config): List<Occurrence>
  // categories: Set<OccasionCategory> filter (default all); empty set → empty
  // list; time-ordered. Facade + CachedEngine take the same parameter.
```
Witr: no separate API — the window from Isha to next Fajr is also the Witr
window; documented on `PrayerTimesResult.isha` and `TahajjudWindow` (KDoc only).

### CachedEngine++
```
forceRecompute: Boolean = false   // every cached method; bypasses the lookup,
                                  // recomputes, and OVERWRITES the stored entry
CachedEngine.DOMAINS              // "prayer", "astronomySun", "astronomyMoon",
                                  // "calendar", "qibla", "daySummary"
clearCaches()                     // everything
clearCaches(domain)               // one domain; unknown name → InvalidParameterException
```
Newly memoized: prayerOccasions (categories in key, order-insensitive),
tahajjudWindow, nightDivisions, imsak, duha, forbiddenWindows, fastingFacts,
fastingFactsForMonth, travelFacts — all date/month-keyed with margins in key.
Instant-anchored navigation (status, navigationSequence, upcoming,
nextOccurrenceOf, nextJumuah) stays uncached by design.
Null results are cached too: a polar-day null computed once is not recomputed.

### tests
- upcoming rotation after maghrib: [MAGHRIB, ISHA, FAJR(tomorrow)].
- forceRecompute: cache hit is same-ref; recompute is new-ref, value-identical,
  and stored (subsequent reads return the overwritten entry).
- clearCaches scoping + unknown-domain rejection.
- occasions category filter coexistence + order-insensitive cache key.

## v1.7 additions (2026-08) — `store` companion module

New artifact `com.khushu:store` (JitPack:
`com.github.greykaizen.khushu-engine:store`). Host-facing settings
persistence, intentionally OUTSIDE `engine/*` — the zero-persistence purity
lock still applies to every engine module. Deps: DataStore core (JVM variant,
Android-compatible) + kotlinx-serialization-json + coroutines.

```
SettingsSnapshot(schemaVersion, prayer, calendar, zakat, location)
  // DTO mirrors with engine defaults: PrayerSettingsDto, CalendarSettingsDto,
  // ZakatSettingsDto, LocationDto, PrayerOffsetsDto — every tunable covered.
  // Engine enums (Madhab, Convention, …) serialized by name; snapshot-level
  // SettingsSnapshot.of(prayer, calendar, zakat, location) builds from engine types.

DTO ↔ engine mappers (each to-engine conversion runs engine init validation):
  PrayerSettingsDto.toConfiguration() / PrayerConfiguration.toDto()
  CalendarSettingsDto.toParams()      / CalendarParams.toDto()
  ZakatSettingsDto.toParams()         / ZakatParams.toDto()
  LocationDto.toLocation()            / Location.toDto()

SettingsCodec — pure JSON codec + schemaVersion migration seam (no DataStore
  dep); lenient decode: unknown keys ignored, missing fields default,
  unknown enums coerce to field default (forward/backward compatible).

KhushuSettingsSerializer : Serializer<SettingsSnapshot>
  // DataStore bridge; empty file → defaults; corrupt bytes → CorruptionException
  // (never silent reset — defaults would silently change madhab/convention).

KhushuSettingsStore(dataStore: DataStore<SettingsSnapshot>)
  settings: Flow<SettingsSnapshot>
  prayerConfiguration / calendarParams / zakatParams: Flow<engine types>
  suspend update {} · updatePrayer · updateCalendar · updateZakat ·
  updateLocation · resetSettings (keeps location)

KhushuSettingsStores.file(file, codec = SettingsCodec())
  // one-liner factory; one DataStore per file (DataStore contract).
```

### tests
- Round-trip of every tweak; DTO↔engine mapper round-trips.
- Empty/{} decode → defaults; unknown keys + unknown enum leniency.
- Corrupt bytes → SerializationException (codec) / CorruptionException (serializer, DataStore read).
- File-backed store: update → bytes on disk decode back identical; reset keeps location.

### docs
- README §Host settings persistence — Android/Compose wiring, every tunable,
  migration rules, corruption behavior.
- README §Observance logs (host-owned) — PrayerLogRecord persistence split;
  host feeds logs + excusedRanges into stats.streak.
- README §Disk caching guidance — in-memory default, forceRecompute after
  settings changes, semantic-key recipe if a measured need ever appears.

## v1.8 additions (2026-08) — calendar module hardening

### event unification (one source of truth)
- Qadr seek-nights (Ramadan 9/21,23,25,27,29 — COMPUTATIONAL) and Tashreeq
  (12/11–13 — ESTABLISHED/HAJJ) are now real entries in
  `HijriCalendar.builtInDefinitions()`; `events()` no longer special-cases
  them, so `events()` and `EventRegistry.occurrencesOn` agree exactly and
  `builtInPackJson()` gains the 8 definitions additively.

### upcomingEvents fix + speedup (facade `calendar.facts`)
- BUG FIXED: previously ignored `config.hijriOffsetDays` (silently used 0).
- Rewritten definition-driven: each definition resolved via `nextOccurrence`
  (offset applied), earliest merged via priority queue — O(defs) not O(days);
  same return shape, same-day events keep title order.

### daysUntil countdowns
```
calendar.daysUntil(month, day, after, offsetDays = 0): Long   // module
calendar.facts.daysUntil(month, day, after, config): Long     // facade
```
Whole civil days until the fixed-hijri day next falls (0 on the day itself);
wraps the hijri year. Powers "N days to Ramadan/Eid" widgets.

### HijriDate : Comparable<HijriDate>
Chronological (year, month, day) ordering — hosts no longer hand-roll
comparators for sorting/range checks.

### CachedEngine calendar extension
New cached methods (forceRecompute on each; all cleared by `clearCaches("calendar")`):
```
calendarEvents(date, offsetDays)            → (date, offset)
calendarEventsInRange(range, offsetDays)    → (range, offset)
calendarFastDays(range, params)             → (range, CalendarParams)
calendarMonthMatrix(yearMonth, config)      → (YearMonth, CalendarConfiguration)
lunarMonthView(hijriY, hijriM, location, zone, config)
                                            → (Location, ZoneId, hijriY/M, config)
```
`lunarMonthView` is the astronomically expensive op — repeated month swipes
never recompute. Instant-anchored facts stay uncached by design.

### store — calendar display sides
`CalendarSettingsDto` gains `primarySide` (default GREGORIAN) /
`secondarySide` (default HIJRI) as `CalendarConfiguration.Side`;
`CalendarSettingsDto.toConfiguration()` rebuilds the engine config
(at-least-one-Hijri rule surfaces as typed InvalidParameterException);
`KhushuSettingsStore.updateCalendarConfiguration(config)` updates sides +
offset preserving fast flags, and `updateCalendar(params)` now preserves
persisted sides. Codec: explicit nulls now round-trip (secondarySide=null
stays null; missing keys still default).

### tests
- events()↔registry equivalence across seek/ordinary/tashreeq/eid days.
- daysUntil zero-on-day + hijri-year wrap; HijriDate ordering.
- upcomingEvents equals the day-scan at offsets 0/+1/−1; count/ascending.
- cache hit/overwrite/clear for all five calendar caches.
- store sides round-trip + flag/side preservation + invalid-combo typed error.

## v1.9 additions (2026-08) — regional civil calendars + month composite

### previousOccurrence / daysSince (mirror of nextOccurrence / daysUntil)
```
calendar.previousOccurrence(month, day, before, offsetDays = 0): LocalDate  // module
calendar.facts.previousOccurrence(month, day, before, config): LocalDate    // facade
calendar.daysSince(month, day, before, offsetDays = 0): Long                // module
calendar.facts.daysSince(month, day, before, config): Long                  // facade
```
Most-recent occurrence at-or-before `before`; 0 on the day itself. Powers
"N days since Eid" counters. NoResultException outside the 2-year window.

### Regional civil calendars (CivilCalendarType — 9 systems)
```
CalendarConfiguration(..., civilCalendar: CivilCalendarType = GREGORIAN)
DateLine.Regional(date: RegionalDate(system, year, month, day, monthName,
                                     eraLabel?, label))
RegionalCalendars.toRegional(date, system) / fromRegional(system, y, m, d) /
                    monthNames(system)          // calendar module object
engine.calendar.civil.toRegional / fromRegional / monthNames   // facade
```
GREGORIAN · PERSIAN · INDIAN_NATIONAL · BANGLA_BANGLADESH · COPTIC ·
ETHIOPIAN (engine-owned deterministic math, anchor-tested) · JAPANESE ·
MINGUO · THAI_BUDDHIST (`java.time.chrono` adapters). Setting `civilCalendar`
switches every GREGORIAN-side line in `date.*`, `monthMatrix`, and
`month.summary` to `DateLine.Regional`; Hijri side unaffected. JAPANESE
reverse conversion throws (era disambiguation unsupported).
Region-based only: no liturgical/lunisolar systems.

### month.summary — whole-month composite
```
calendar.month.summary(yearMonth, location, zoneId, config,
                       calendarParams): MonthSummary
  // MonthSummaryDay(date, dual, events, fastRules, moonIllumination,
  //                 moonAboveHorizon) per civil day; one pass: fast days
  //                 once per range, one lunar track, one event lookup/day
CachedEngine.monthSummary(...)   // cached; key = (Location, YearMonth, ZoneId,
                                 // CalendarConfiguration, CalendarParams)
```

### store — civil calendar side
`CalendarSettingsDto` gains `civilCalendar: CivilCalendarType` (default
GREGORIAN; unknown values coerce to the default). `updateCalendarConfiguration`
persists it; `updateCalendar(params)` preserves it.

### i18n note (EventRegistry)
`registerPack` is by definition id — registering an id that already exists
(including built-ins like `eid_al_fitr`) REPLACES that definition: a
translated pack reusing built-in ids gives zero-code title localization.

### docs
- `docs/calendar.md` §Regional civil calendars — systems table + provenance.
- `docs/sighting-mode-design.md` — DESIGN ONLY (house rule; review gate).

### tests
- Persian: 1354–1419 official Nowruz table + leap marks, year lengths,
  Esfand 29/30, round-trip 1990–2050.
- Śaka: adoption anchor 1957 + month-start table + leap Chaitra; Bangla:
  2019-revision anchors + Falgun 29/30 leap binding; both round-trip
  1990–2090. Coptic/Ethiopian: anchors + 4-year leap cycle + round-trip
  1950–2080. Japanese era boundary; Minguo/Thai offsets.
- daysSince zero-on-day + previousOccurrence facade/module parity.
- month.summary one-pass composite (Tasu'a/Ashura flag, moon bounds, dates).

## v1.10 additions (2026-08) — zakat/qibla coherence

### zakat — dual thresholds, notes, hawl facts, i18n keys
```
ZakatResult gains:
  goldNisabThreshold: Double?   // both metal thresholds always returned
  silverNisabThreshold: Double? // null = that metal's price was not provided
  notes: List<String>           // madhab-rule facts applied to this assessment
                                // (e.g. "worn jewelry exempt: Hanafi only",
                                //  "liabilities not deducted: Shafi'i position",
                                //  "no zakat due: hawl incomplete")
AssetContribution(key: AssetKey, label: String, amount: Double)
  // AssetKey{CASH,INVESTMENTS,RECEIVABLES,INVENTORY,GOLD,SILVER,
  //          WORN_JEWELRY,LIABILITIES} — stable i18n keys; label is the
  //          English display default.
FitranaResult.totalKg           // saKg × dependents (household food quantity)

ZakatRules.hawlFacts(ownershipStart, today, offsetDays = 0): HawlFacts
ZakatRules$HawlFacts(period, daysSinceStart, daysRemaining, progress, complete)
  // countdown/progress for the lunar ownership year; date-only —
  // pair with Zakat.mal for the amount. InvalidParameterException when
  // today < ownershipStart. hawlPeriod's unresolvable case now throws
  // typed NoResultException (was bare IllegalStateException).
```
`Zakat.mal` rounds each currency figure once (single rounding of the total);
intermediate sums stay full-precision. fitrana default madhab stays HANAFI
(most-published 3.0 kg figure) — documented on `Zakat.fitrana`.

### qibla — module-level sun relation, audit, shadow cache
```
Qibla.relativeSunAngle(location, instant, toleranceDeg = 2.0): SunQiblaRelation
  // MOVED from the facade into the qibla module (facade delegates; facade
  // zero-logic rule restored). Signed sun-azimuth→qibla angle [−180,180].
Qibla.audit(): QiblaAudit       // provenance (Kaaba constants, distance model,
                                // bearing formula) — mirrors astronomy.audit()
CachedEngine.qiblaShadowVerification(year, location, forceRecompute = false)
  // key = (Year, Location); cleared by clearCaches("qibla").
```

### facade passthroughs
```
engine.zakat.hawlFacts(ownershipStart, today, offsetDays = 0)
engine.qibla.audit()            // engine.qibla.relativeSunAngle unchanged
```

### tests
- Zakat: dual thresholds, null-when-unpriced, madhab-rule notes, hawl note,
  AssetKey presence, fitrana totalKg, breakdown sum.
- ZakatRules: hawlFacts progress/partition/completion + invalid-date rejection.
- Qibla: audit provenance; relativeSunAngle signed/normalized; cohesion —
  every visible Kaaba-zenith passage aligns with the qibla bearing.
- Facade: qiblaShadowVerification memoize/overwrite/clear + cache-key year;
  zakat/qibla facade↔module parity.

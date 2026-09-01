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

## v1.11 additions (2026-08) — zakat deferred-queue topics

Design: `docs/v1.11-zakat-design.md` (second source pass + independent
cross-check; D1–D9). Everything is ADDITIVE; v1.10 callers get byte-identical
numbers (the debt split defaults to "all liabilities immediate").

### zakat — debt policy, receivable tiers, shares, jewelry basis
```
DebtTreatment { IMMEDIATE_ONLY, ALL_DEBTS, HIDDEN_WEALTH_ONLY, NONE }
  // ZakatParams.debtTreatment: DebtTreatment? = null → madhab default:
  //   HANAFI/HANBALI → IMMEDIATE_ONLY (Daruliftaa #8395, Radd al-Muhtar
  //   2/260–261 preferred position), MALIKI → HIDDEN_WEALTH_ONLY,
  //   SHAFII → NONE (al-Nawawi). Non-default overrides emit a citation note.
ZakatAssets.immediateLiabilities: Double? = null
  // null = entire liability is immediate (v1.10 behavior); explicit split
  // activates IMMEDIATE_ONLY. Validated 0 ≤ immediate ≤ liabilities.
ZakatAssets.mediumReceivables / weakReceivables: Double = 0.0
  // Hanafi tripartite classification: medium (non-trade property proceeds)
  // and weak (mahr/inheritance) receivables are EXCLUDED with notes;
  // retro-assessment on collection stays a RequiresScholarReview note.
ShareHolding(shares, marketValue, intent: ShareIntent{TRADING,LONG_TERM},
             zakatablePortionFraction: Double? = null,
             basisSource: ZakatableBasisSource{EXACT,ESTIMATED,CONSERVATIVE_FULL})
ZakatAssets.shareHoldings: List<ShareHolding> = emptyList()
  // TRADING → full market value; LONG_TERM → value × fraction; missing
  // fraction → conservative full-value fallback + warning note (AAOIFI
  // Std 35 estimation allowance; ESTIMATED fractions are cited in notes).
ZakatAssets.wornGoldPricePerGram / wornSilverPricePerGram: Double? = null
  // realizable (jeweler buyback) price for worn jewelry — null = market
  // price (v1.10 behavior). Nisab thresholds stay market-based.
JewelryValuationBasis { REALIZABLE_VALUE, MARKET_RETAIL }
  // ZakatParams.jewelryValuationBasis: null → madhab default (HANAFI →
  // REALIZABLE_VALUE). Selecting REALIZABLE_VALUE without a quote emits a
  // "no buyback quote provided" note and falls back to market price.
AssetKey += EQUITY_TRADING, EQUITY_LONG_TERM, RECEIVABLES_MEDIUM, RECEIVABLES_WEAK
ZakatMadhabDefaults.debtTreatment(madhab) / .jewelryValuationBasis(madhab)
```

### zakat — ushr mixed irrigation + nisab policies
```
ZakatRules.MixedIrrigationRule { PREDOMINANT, PROPORTIONAL }
ZakatRules.ushrRate(rule, artificialShare) / ushrDue(harvestValue, rule, artificialShare)
  // artificialShare ∈ (0, 1) strictly. PREDOMINANT = classical Hanafi rule
  // (al-Mawsili, al-Ikhtiyar): >0.5 → 5%, <0.5 → 10%, ==0.5 throws
  // (no predominant method — pick PROPORTIONAL explicitly). PROPORTIONAL =
  // 0.10 − 0.05·ratio (7.5% at half) — labeled MODERN convention, not the
  // classical position. Plain Irrigation overloads unchanged.
ZakatRules.UshrNisabPolicy { NO_NISAB, FIVE_WASAQ }
ZakatRules.ushrNisabReached(harvestKg, policy)
ZakatRules.FIVE_WASAQ_KG = 653.0
  // modern measured conversion of 5 awsuq = 300 saʿ — documented
  // provenance, not a revealed constant.
```

### zakat — fitr payment form
```
FitrPaymentMode { FOOD, CASH_EQUIVALENT }
Zakat.fitrana(dependents, pricePerKg, madhab, mode = FOOD)
FitranaResult.mode + .notes
  // identical numbers either way; mode drives provenance notes (majority
  // food position / Hanafi cash-equivalent position + reported precedents).
```

### store
```
ZakatSettingsDto += debtTreatment, jewelryValuationBasis, fitrPaymentMode,
                    mixedIrrigationRule, ushrNisabPolicy   // all defaulted
```
Schema version UNCHANGED (1): v1.10 files decode with the v1.11 defaults;
v1.11 files decode on v1.10 (unknown keys ignored).

### facade passthroughs
```
engine.zakat.ushrRate(rule, artificialShare)
engine.zakat.ushrDue(harvestValue, rule, artificialShare)
engine.zakat.ushrNisabReached(harvestKg, policy)
engine.zakat.fitrana(..., mode = FOOD)   // existing mal/fitrana unchanged
```

### tests
- Debt: 4-madhab default matrix + IMMEDIATE_ONLY/ALL_DEBTS/NONE overrides +
  v1.10 numeric back-compat (null split = full deduction).
- Receivables: medium/weak exclusion + classification notes.
- Shares: trading full-value, long-term fraction, conservative fallback,
  ESTIMATED citation, invalid-input rejection.
- Jewelry: buyback quote valuation + market-based nisab separation,
  quote-missing warning.
- Ushr: predominance (>0.5/<0.5/==0.5-throws), proportional interpolation
  incl. 7.5% at half, bounds validation, both nisab policies at 653 kg.
- Fitr: FOOD/CASH_EQUIVALENT same numbers, distinct notes.
- Store: v1.11 field round-trip; v1.10-file forward-compat with defaults.

## v1.12 additions (2026-08) — mushaf hardening

Design gap closed from the mushaf module audit (docs/v1.3-zakat-design.md
sibling review round): mushaf was the only engine module without typed
input validation.

```
AtlasFontMetrics / AtlasSpec / GlyphSrcRect / GlyphMetrics / GlyphPlacement:
  init-time InvalidParameterException — unitsPerEm > 0, ppem > 0, rect w/h
  > 0, atlas coords >= 0, finite non-negative advance/placements
Mushaf.measureWordWidthPx / layoutWord / layoutLine / layoutAyah:
  fontSizePx must be finite & > 0; lineHeight/wordGap/maxWidth finite &
  >= 0 — NaN/Infinity can never leak into a layout (typed failure, not
  silent garbage coordinates)
WordLayout.skippedGlyphIds: List<Int> = []   // per-word missing glyphs
AyahLayout.skippedGlyphIds: List<Int> = []  // aggregated + distinct
  // missing glyphs still advance the pen (donor parity) but are now
  // diagnosable — a non-empty list usually means a malformed bundle
```

Additive only: golden/property tests unchanged and green (7 new validation
tests); API diff is data-class constructor shifts + defaults, source-compatible.
`engine:core` dependency added for the shared error model (mushaf now uses
the same InvalidParameterException as zakat/prayer/qibla).

## v1.13 additions (2026-08) — astronomy + core cohesion round

Module review findings fixed (audit: 10 findings; the last unreviewed
engine module pair).

### astronomy — boundary validation + error model
```
Epoch envelope: instants/years outside 1700-01-01…2200-01-01 are rejected
  up front with InvalidParameterException (cosinekitty's validated range,
  pinned by the engine) — applies to sun/moon position/state, nextPhase,
  eclipses, distanceExtremes, seasons(1700..2199).
moon.phaseName: NaN/∞ rejected — previously NaN silently classified as
  "New Moon".
moon.track(pathSamplesPerDay): validated > 1 REGARDLESS of includePath —
  previously the same call threw or succeeded depending on an unrelated flag.
moon.distanceExtremes: scan window capped at 50 years (resource guard);
  out-of-envelope endpoints rejected.
SubsolarAlignment.passagesOver: targetLat must be finite within ±23.44
  (subsolar envelope — out-of-range targets deterministically yield
  nothing, so they are rejected), targetLon within [-180,180],
  toleranceDeg finite > 0, year 1700..2199.
Upstream wrapping: cosinekitty failures surface as typed
  UpstreamComputationException (calendar-module precedent) — raw library
  exceptions no longer cross the capability boundary.
sun.events: hardcoded altitude literals replaced by the pinned
  AltitudeConventions table — events() and phases() can never drift.
```

### bug fixes surfaced by the new validation
```
Qibla.shadowVerification: Kaaba-antipode longitude was computed as
  IEEEremainder(lon+180, 360) − 180 → −320.17° (out of range, latent
  donor bug masked by periodic trig). Now IEEEremainder(lon+180, 360).
  Divergence D21.
HilalReport.moonAgeHours: conjunction-search failure no longer fabricates
  sunset−24h as the conjunction (D7); unresolvable → typed
  NoResultException. Divergence D22.
```

### facade
```
engine.astronomy.seasons(year)    // newly delegated (was capability-only)
engine.astronomy.sun.audit()      // newly delegated (qibla.audit parity)
```

### tests
- AstronomyValidationTest (9): envelope rejections, phaseName NaN,
  nextPhase quarters, track sample-count flag-independence, extremes
  window caps, seasons bounds, hilal nights, subsolar target validation
  (incl. Kaaba + antipode pass), no-fabrication invariant.
- Core validation was already exemplary (ErrorModelTest/LocationTest).

## v1.14 additions (2026-08) — Muwaqqit-parity round (parameters, not defaults)

External reference: muwaqqit.com (CC-BY API + docs; attribution in
`fixtures/muwaqqit_kohat_2026.json`). Implemented from documented formulas +
published models (VSOP87D, King 2003, Odeh 2005) — no Muwaqqit code. Their
parameter sets are host-supplied options; engine defaults are unchanged.

### astronomy — new named facts (all pure hour-angle/crossing math)
```
Astronomy.sun.antiTransit(location, date, zoneId): Long
  // lower-meridian crossing = solar midnight (Nisf qaws al-layl al-ḥaqīqī);
  // the persistent-twilight Fajr anchor per the (separately reviewed,
  // still-parked) Muwaqqit aqrab-al-ayyam position. Validated vs Muwaqqit <2s.
Astronomy.sun.istiwaPeriod(location, date, zoneId): IstiwaPeriod
  // limb-to-limb upper-meridian crossing (leading start, center, trailing
  // end) — prayer-prohibited window; Zuhr enters after. NoResultException on
  // polar days without transit.
Astronomy.sun.zuhrShadowIncrease(location, date, zoneId, shadowIncreaseMm = 1.0): Long?
  // 2m-gnomon noon shadow grown by N mm — King 2003 convention (Muwaqqit
  // default 1 mm). InvalidParameterException on non-positive input.
SolarEventType += ANTI_TRANSIT, ISTIWA_START, ISTIWA_END, KARAHAH, ISHTIBAK_AL_NUJUM
AltitudeConventions += karahahDeg: Double? = null, ishtibakAlNujumDeg: Double? = null
  // nullable named conventions (rumḥ 4.5°, −10° King 2003) — opt-in via
  // sun.events(location, date, zoneId, conventions); null = not computed.
  // Parameters, never defaults.
```

### prayer — NightDivisionMethod wired (was deferred since v1.5.0)
```
Prayer.nightDivisions(location, date, config, method = MAGHRIB_TO_FAJR)
  // MAGHRIB_TO_FAJR (default, sharʿī night — Muwaqqit-documented) or
  // SUNSET_TO_FAJR. All divisions are now EXACT fractions of the chosen
  // span (the old partial reuse of adhan2 midnight/lastThird differed from
  // exact span-fractions by seconds; Muwaqqit fixture anchors ⅓/½/⅔/⅚).
```

### bug fix surfaced by this round (D23)
```
sun.phases().midnight: was ALWAYS noon+12h fallback — the underlying
searchHourAngle call passed 180.0 where cosinekitty expects HOURS [0,24);
it threw every time and runCatching masked it. Fixed (12.0); all 30 golden
solarDayReference rows verified-as-fallback and regenerated (corrections
±15s = equation-of-time drift). golden-dumper fixed identically.
```

### deferred (with rationale)
- AtmosphereModel (k-refraction coefficient, temp/pressure scaling, horizon
  dip, ±uncertainty bands): cosinekitty bakes Refraction.Normal into every
  crossing call — a full atmosphere seam needs our own refraction-corrected
  solver + full golden revalidation; its own round, not a bolt-on. Low
  altitude Muwaqqit-parity tolerance (±390s) documents the current bound.
- Fajr-at-anti-transit persistent-twilight policy: parked behind fiqh review
  (see AGENTS deferred queue); antiTransit fact shipped makes it implementable
  on approval.
- Magnetic qibla (IGRF-12), light-pollution data: stays out (dataset/data
  concerns — host-side like weather).

### tests
- MuwaqqitParityTest (8): antiTransit <90s, transit, fajr −19°/ishā −16°/−19°
  inside Muwaqqit's own ±1° band, named-convention events opt-in + absent by
  default, istiwa brackets transit, zuhr +1mm ordering + rejection, duhā 4.5°.
- NightDivisionMethodTest (4): default=maghrib + Muwaqqit ⅓/½/⅔/⅚ anchors,
  sunset-variant ordering/monotonicity, exact ⅔ for both methods.
- QiblaTest: Kohat bearing vs Muwaqqit 254.6° cross-check.
- Golden: 30 solarDayReference rows regenerated post-D23.

## v1.15 additions (2026-08) — observed (moonsighted) calendar mode

Un-gated per the operational model: astronomy certifies plausibility
(`hilal.visibility`), humans confirm/deny on the moon-track path, the
committee's announcement records the outcome, and THIS derives every date of
that month from the anchor. The engine never decides a sighting; no
networking, no persistence, no fiqh verdicts (design:
docs/sighting-mode-design.md).

```
SightedCalendar.AnnouncedMonthStart(hijriYear, hijriMonth, observedFirstDay, authority)
SightedCalendar.SightedCalendarParams(
    announcements,                          // host-supplied, by value
    localOffsetDays = 0,                    // global user correction, applied LAST (−2..+2) — the
                                            // "everything went south" escape hatch
    tabularOffsetDays = 0,                  // fallback-month offset (CalendarParams domain)
)                                           // contradictions throw InvalidParameterException
SightedCalendar.hijriSighted(civilDate, params): SightedHijriDate
  // governing anchor = latest announcement at/before the (offset-adjusted)
  // date, bounded by the next anchor or 30 days; unannounced → tabular.
  // SightedHijriDate carries fromAnnouncement + anchoredBy provenance.
SightedCalendar.civilOf(year, month, day, params): LocalDate   // inverse mapping
SightedCalendar.eventsSighted(range, params): List<Pair<LocalDate, EventDefinition>>
  // built-in events keyed on each day's SIGHTED hijri date (≤400-day ranges)
```
ObservanceContext/ObservedDateOverride (v1.8) remain for per-event host
statements; announcements govern whole months. 15 tests: anchor derivation,
29-day bounding by next announcement, tabular fallback + identity, offset
shifts + range validation, contradiction rejection, multi-authority agreement,
civilOf round-trip incl. offset, eid-follows-announced-Shawwal, tabular
immutability.

## v1.16 additions (2026-08) — tasbih + observance (streaks/bookmarks/reading/khatm)

Two new capability modules + a shared core day-streak math namespace. Both
are stateless over caller-supplied logs — the observance-logs doctrine
(README §Observance logs) generalized to every domain: the host persists
(Room/DataStore), the engine computes, optional cloud sync mirrors the
LOGS never the derived facts.

### core — shared day math (second-consumer extraction, AGENTS §2)
```
core.observance.DayStreakMath
  dayClasses(first, last, isComplete, excusedRanges): List<DayClass>
  streakFacts(days): StreakFacts(current/longest/completed/excused/span)
  // Contract mirrors prayer.streakStats verbatim: complete-or-excused
  // extends; silent/uncompleted BREAKS; span-bounded. excused ≠ grace —
  // excused ranges are host-declared fiqh exemptions; no engine-invented
  // streak-freeze anywhere. (prayer.streakStats delegates to this in 2.0.0.)
```

### tasbih — new module (counting/goals domain)
```
TasbihDefinition(id, name, arabic?, targetCount, dailyGoal? = null,
                 source: PRESET|DUA|ASMA_NAME|CUSTOM, sourceRef?)
TasbihCountLog(definitionId, date, count)
Tasbih.PRESETS                 // 7 canonical seeds, hadith-cited KDoc:
                               // SubhanAllah/Alhamdulillah 33, Allahu Akbar 34
                               // (Muslim #597); Istighfar/Salawat/Tahleel/
                               // SubhanAllahi-wa-bihamdihi 100 (Tirmidhi/
                               // Muslim #408/Bukhari). dailyGoal null — the
                               // engine never invents goals.
Tasbih.ofDua(duaId, name, arabic, target)   // data-api dua → tasbih
Tasbih.ofName(number, name, arabic, target) // 99 Names → tasbih
Tasbih.custom(id, name, target, dailyGoal?)
Tasbih.progressOn(logs, definition, date)
Tasbih.dhikrStats(logs, definitions, today, excusedRanges): List<DhikrStats>
  // todayCount, goalMetToday, goalStreak (STRICT), longest, lifetimeTotal,
  // lastCountedDate. Freeform (null goal) → totals only.
```

### observance — new module (bookmarks/reading/khatm domain)
```
// CENTRALIZED bookmarking — one system for quran/sunnah/dua/names/articles:
Bookmark(domain: QURAN|HADITH|DUA|ASMA_NAME|ARTICLE|CUSTOM,
         ref /* opaque: "2:255", "bukhari_urn_100010", "dua_47", slug */,
         label?, anchor? /* "18:10 word 4" */, createdAtEpochMs)
Observance.toggle(bookmarks, domain, ref)     // add/remove/dedup
Observance.upsert(bookmarks, candidate)       // anchor/label merge, createdAt PRESERVED
Observance.bookmarkViews(bookmarks): byDomain/recent/all

// Reading sessions (explicit marks only — scroll-past NEVER completes):
ReadingSession(domain: QURAN|SUNNAH|DUA|ARTICLE, ref, openedAt,
               secondsActive, versesViewed?, completedToday)
ReadingTarget(domain, ayahsPerDay?, minutesPerDay?)
Observance.readStats(sessions, targets, today, excusedRanges): ReadStats
  // per-domain: versesViewedToday, minutesActiveToday, markedCompletedToday,
  // STRICT streak (over explicit-mark days), totals, lastRead
Observance.continueReading → Map<domain, lastSession?>   // roadmap RANK-1
Observance.history(sessions, WEEK|MONTH, today): byDay/distinctRefs/minutes

// Khatm — pure math over host-supplied tables (D4: no engine content copy):
Observance.khatmFacts(completed: List<AyahRange>, juzRanges: List<JuzRange>,
    today, totalAyahs = 6236, dailyRateAyahs?): KhatmFacts
  // normalized (sorted/coalesced) ranges, coveredAyahs, progressFraction,
  // currentJuz (frontier containment), remainingAyahs, linear projection
```

### facade
```
engine.tasbih.presets/ofDua/ofName/custom/progressOn/dhikrStats
engine.observance.toggleBookmark/upsertBookmark/bookmarkViews/
            readStats/history/khatmFacts
```

### semantics locked (roadmap §6 carried into code)
- secondsActive (screen-visible) is the honest signal — never open-time
- completedToday is an EXPLICIT user mark; scroll-past never completes
- STRICT grace policy: a missed day breaks; excused ranges are the only
  neutral state and are host-declared fiqh exemptions
- streaks inform, never shame — hosts own presentation

### tests (35+)
core DayStreakMath (7): gaps/excused-neutral/trailing-break/silent-breaks/
long-excused/invalid-span/zero-span; tasbih (9): presets shape, factories,
validation, summation, freeform-vs-goal, strict break, excused-neutral,
definition isolation; observance (16): toggle/dedup/upsert-preserves-createdAt/
views, mark-only streaks, domain isolation, excused reading streaks,
progress aggregation, history windows, khatm normalization/frontier-gap/
disjoint/projection/edges, canonical 6236; facade parity throughout.

## v1.17 additions (2026-08) — qada engine + ANTI_TRANSIT_FAJR (gate dissolution)

Both former deferred-queue gates dissolved by the parameter model: the
engine implements documented scholarly positions as selectable parameters;
the host selects per their fiqh; provenance carried in KDoc + result notes.

### prayer — ANTI_TRANSIT_FAJR (HighLatitudeRule new value)
```
HighLatitudeRule += ANTI_TRANSIT_FAJR
  // Opt-in: when the Fajr depression angle is unreachable (persistent
  // twilight — sun's minimum altitude stays above the convention's Fajr
  // angle), substitute sun.antiTransit (solar midnight) for Fajr.
  // Muwaqqit-documented position (Ibn ʿĀbidīn, al-Ṭaḥṭāwī, Ibn Ḥajar,
  // Quṭb al-Dīn). docs/aqrab-al-ayyam-review.md is the full dossier.
  // Normal-latitude dates: no substitution (angle reachable).
HighLatitudeResolution += RESOLVED_BY_ANTI_TRANSIT
  // Audit field signals when anti-transit substitution was applied.
Prayer.audit.warnings carries: "fajr: anti-transit substitution
  (persistent twilight) — Muwaqqit-documented position"
```

### prayer — Qada & excused-range computation (docs/qada-design.md rev 3)
```
Qada.FiqhPeriod(start: Instant, end: Instant, type: HAYD | NIFAS)
  // The host PRE-CLASSIFIES periods — the engine NEVER interprets raw
  // bleeding. Instant-based (not LocalDate) so boundary cases during
  // prayer windows resolve correctly.
Qada.QadaConfig(school: QadaSchool, pairingOverride?, includeWitr?)
  // HANAFI → UNPAIRED default + Witr included (Darul Iftaa #8301)
  // MALIKI/SHAFII/HANBALI → PAIRED default, Witr not in qada
  // Override any parameter — provenance emitted into result
Qada.DaySchedule(date, fajr, dhuhr, asr, maghrib, isha, nextDayFajr, zoneId)
Qada.qadaReport(periods, schedules, config): QadaReport
  // suppressedPrayers + boundaryObligations + ghuslRequiredAt + provenance
  // Boundary rules: pure during Asr → Dhuhr+Asr owed (majority PAIRED);
  // Hanafi → only Asr owed (UNPAIRED). Host overrides via pairingOverride.
Qada.BoundaryPairing { PAIRED, UNPAIRED }
Qada.BoundaryRuling(prayer, owed, rule)   // per-ruling provenance
Qada.PrayerInstance(date, kind, enteredAt)
```

### facade
```
engine.qada.defaultPairing(school)       // school→pairing default
engine.qada.defaultIncludeWitr(school)   // school→witr default
engine.qada.qadaReport(periods, schedules, config)
```

### tests (13 new)
- AntiTransitFajrTest (4): substitution at London solstice, normal-latitude
  unchanged, winter normal, audit carries rule+resolution+warning
- QadaTest (9): full-day suppression, no-overlap, paired/unpaired boundary,
  school defaults, ghusl, validation

## 2.0.0 — BREAKING: typed boundaries + dead-API removal (parameter model final)

All items from the deferred-queue 2.0.0 inventory shipped. Khushu is the
only consumer; the recompile burden is absorbed by one coordinated upgrade.

### BREAKING changes
```
prayer.travelFacts: travelledDistanceKm: Double → Kilometers (core typed unit)
SubsolarAlignment.passagesOver: targetLatDeg/targetLonDeg: Double →
    Latitude/Longitude (core typed geo units)
HighLatitudeResolution += RESOLVED_BY_ANTI_TRANSIT
HighLatitudeRule += ANTI_TRANSIT_FAJR (v1.17 additive, listed here for
    migration reference)
SolarEventType: GOLDEN_HOUR_MORNING_START REMOVED (never constructed)
core.units: Radians REMOVED (unused; Math.toRadians is the stdlib path)
AltitudeConventions: blueHourLowerDeg REMOVED (duplicates civilTwilightDeg)
HilalReport.moonAgeHours: Int → Int? (null = conjunction unresolvable;
    D7 no-fabrication enforced at the TYPE level, not just runtime)
```

### Cleanup (non-breaking)
```
prayer.streakStats delegates to core DayStreakMath (v1.16 extraction);
    the inlined v1.6 algorithm is retired
qibla.normalizeDeg: 4 inline copies → core observance.AngleMath.normalizeDeg
    (AGENTS §2 extraction — qibla was the second consumer)
```

### Migration guide (Khushu host)
- `travelFacts(location, date, 120.0, ...)` → `travelFacts(location, date, Kilometers(120.0), ...)`
- `passagesOver(latDeg, lonDeg, year)` → `passagesOver(Latitude(latDeg), Longitude(lonDeg), year)`
- Any reference to `Radians` → use `Math.toRadians()` directly
- Any reference to `GOLDEN_HOUR_MORNING_START` → use `GOLDEN_HOUR_MORNING_END`
- `report.moonAgeHours` → now nullable: `report.moonAgeHours ?: 0` for legacy behavior

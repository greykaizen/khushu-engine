# Khushu Engine — Agent-Ready Documentation

> **Drop this file into the future repo root** (e.g. as `AGENTS.md` or `docs/ENGINE.md`)
> once the project is scaffolded. It is written so any AI agent (or human) can
> understand what the engine exposes, what feeds it, what is done, and what the
> rules are — without reading the host apps.

---

## 1. Identity & naming

| Item | Value |
|---|---|
| Project name | **khushu** (engine) |
| Recommended directory | `~/AndroidStudioProjects/khushu-engine` |
| Base package | `com.khushu.engine` |
| Language/target | Pure Kotlin/JVM. **No Android, no Compose, no Context anywhere under `engine/`.** |

⚠️ **Directory collision warning:** `~/AndroidStudioProjects/` already contains
`khushu/`, `Khushu/`, `Khushu2/`, `Khushu3/`, `Khushu4/`, `Khushu5/`, plus
`khushu-quran-data/`, `Osprey/`, and others. Do **not** create another bare
`khushu`/`Khushu` directory. Use `khushu-engine`. Never assume which older
directory an agent means by "khushu" — always resolve the full path first.

## 2. Donor projects & donor policy

| Donor | Path | Role |
|---|---|---|
| **Osprey** | `~/AndroidStudioProjects/Osprey` | Computational reference: astronomy, prayer times, calendar/hijri, events, zakat, qibla math lives here today. |
| **khushu-quran-data** | `~/AndroidStudioProjects/khushu-quran-data` | Content/data pipeline (Python generators): Quran scripts, tafsirs, hadith inventory, asma-ul-husna, dua/dhikr content, adhan audio. Supplies *data*, not engine logic. |

**Donor policy (non-negotiable):**

1. Donors are **references, not gospel**. Donor logic may contain ingested bugs
   or bad assumptions. Porting code does not certify it.
2. Every ported algorithm must be checked against an **authoritative source**
   (astronomical almanac values, established prayer-time implementations,
   published fiqh rules for madhab angles/nisab) before it is considered done.
3. Where a donor's result disagrees with the authoritative source, the engine
   follows the source and records the divergence in
   `docs/divergences.md` (donor file:line → what changed → why).
4. From khushu-quran-data, take the *pipeline outputs* (generated assets) —
   never port its Python scripts into the engine.

## 3. Module layout & dependency direction

**`core` stays boring:** domain primitives only — units/coordinates, time
abstractions, errors/results, shared trivial math. If anything becomes
domain-specific, it moves OUT of core. No preemptive `math` module: astronomy
owns interpolation/angle-normalization until a second consumer actually needs
them, then extract.

**Capabilities, not implementation (hard API rule):** consumers call
`astronomy.sun.position(...)`, never import `AstroMath.kt` or any calculator
class. Internal implementations may be rewritten freely without breaking hosts.

```
khushu-engine/
├── engine/
│   ├── core         ← time primitives, Location/Clock abstractions, Result types
│   ├── astronomy    ← Julian date, solar/lunar position, rise/set, moon phase, hilal
│   ├── calendar     ← Hijri conversion, Islamic events, fast-day rules
│   ├── prayer       ← prayer time rules, madhab/convention/high-latitude params
│   ├── qibla        ← great-circle bearing to the Kaaba
│   ├── zakat        ← nisab/rate rules (mal + fitrana)
│   └── mushaf       ← glyph-atlas layout math: word/line/page fitting, RTL placement
├── tools/cli        ← human spot-check harness. NOT part of the public API.
└── docs/            ← this file, divergences.md, api snapshots
```

```
core ↑ astronomy ↑ {calendar, prayer}      qibla → core+astronomy
zakat → core+calendar (hawl hijri-day math; acyclic)
```

Hard rules, enforced by Gradle (undeclared/reverse deps fail the build):
- `core → NOTHING`. Core never knows astronomy/prayer/qibla/zakat exist.
- No module may depend on a sibling unless listed above.

## 4. Settings — what the engine accepts

The engine does **not** read persisted app settings. Callers pass explicit
parameter objects. The canonical inputs (extracted from donor Osprey
`AppSettings`; only the ~25 calculation-relevant fields — the other ~80 fields
are UI concerns and stay out of the engine):

### `PrayerParams`
| Setting | Type | Default | Notes |
|---|---|---|---|
| madhab | enum | SHAFII | Asr shadow factor (Shafi'i/Hanafi/Maliki/Hanbali) |
| convention | enum | MUSLIM_WORLD_LEAGUE | Fajr/Isha angle preset |
| fajrAngle / ishaAngle | Double | 18.0 / 18.0 | Only used with CUSTOM convention |
| ishaIntervalMinutes | Int | 0 | Interval-based Isha instead of angle |
| highLatitudeRule | enum | MIDDLE_OF_NIGHT | Night portion / angle-based / one-seventh |
| per-prayer offsets | Int ×7 | 0 | Fajr…Isha minute adjustments |

### `Location` (purely spatial) + explicit temporal arguments
Spatial and temporal concepts are separated; APIs request exactly what they
need — no sum-type "context" object that can be in different states:

| Type | Fields | Notes |
|---|---|---|
| `Location` | `Latitude`, `Longitude`, `AltitudeMeters` | GPS resolution is the caller's job |
| `Instant` | point-in-time physics | astronomy facts |
| `LocalDate` + `ZoneId` (possibly wrapped as `PrayerDate` if it earns domain status) | date+zone semantics | prayer, calendar |
| `ZoneId` always typed | never a raw "GMT+5" string | parse at the boundary |

Signatures:
- `astronomy.sun.position(location, instant)`
- `prayer.times(location, localDate, zoneId, params)`
- `calendar.hijri(localDate, offsetDays)`

No `Clock` in the engine core. All computation is deterministic over explicit
time arguments; "current prayer" style convenience takes an explicit instant
(`engine.prayer.status(location, now, params)`). Clock injection, if ever
needed, lives in a convenience layer above the facade.

### `CalendarParams`
| Setting | Default |
|---|---|
| hijriOffsetDays | 0 (−2..+2) |
| primaryCalendar / secondaryCalendar | GREGORIAN / ISLAMIC |
| optionalFasts* flags | mon-thu, white days, shawwal, shaban, dhul-hijjah, tasua/ashura |

### `ZakatParams`
nisab source (gold/silver valuation input), rate (default 2.5%), asset entries,
fitrana per-person quantity (saʿ → kg convention must be documented).

Modes like fasting/menstruation/travel/qasr are **state, not params**: they
affect which prayers are obligatory (UI/checkmark concern), not the clock
times the engine returns.

## 5. Results — what the engine exposes

All results are immutable data classes; times are epoch-millis or
`java.time` types; angles are degrees unless suffixed `Rad`.

### astronomy
```kotlin
SolarPosition(azimuthDeg, altitudeDeg, declinationDeg, rightAscensionDeg)
LunarPosition(/* same shape */ + distanceKm)
MoonState(phaseAngleDeg, illuminationFraction, elongationDeg, phaseName, brightLimbTiltDeg)
RiseSet(riseEpochMs: Long?, setEpochMs: Long?)          // null = no event that day
SolarEvents(dawn, sunrise, solarNoon, sunset, dusk, goldenHour…)  // per-altitude events
CelestialPath(points: List<CelestialPathPoint>)          // for sky rendering
HilalVisibility(criterion, ageHours, arcOfLight…)        // moonsighting aid
```

### prayer
```kotlin
PrayerTimes(fajr, sunrise, dhuhr, asr, maghrib, sunset, isha, // Instant?
            midnightMethod1, midnightMethod2, lastThird)      // extra timings
// each entry: epochMs + computed flag (e.g. interval-Isha, polar day handling)
PrayerStatus(current: Prayer?, next: Prayer?, countdown: Duration, progress: Float)
```

### calendar
```kotlin
HijriDate(year, month, day, monthName, offsetApplied: Int)
IslamicEvents(date → List<IslamicEvent>)                 // Ramadan start, Eid, Ashura…
FastDays(date range → List<FastDay>)                     // driven by optionalFasts* flags
```

### qibla
```kotlin
QiblaBearing(bearingDegFromNorth, greatCircleDistanceKm)
```

### zakat
```kotlin
ZakatResult(nisabValue, zakatDue, breakdown: List<AssetContribution>)
FitranaResult(perPersonAmount, totalForHousehold)
```

## 6. Facade (single entry point, no god object)

```kotlin
val engine = KhushuEngine()            // no Clock, no cache param — deterministic;
                                       // cache is a decorator around services
engine.prayer.times(location, localDate, zoneId, params): PrayerTimes
engine.prayer.status(location, now, params): PrayerStatus
engine.astronomy.sun.position(location, instant): SolarPosition
engine.calendar.hijri(localDate, offsetDays): HijriDate
engine.qibla.bearing(location): QiblaBearing
engine.zakat.mal(assets, nisabSource): ZakatResult
```

Every call takes typed units (`Latitude`, `Degrees`…) at the boundary; every
domain exposes capabilities (`astronomy.sun.*`), never calculator classes.

Caching is a **decorator around deterministic computation, added last** — the
facade does not own it:

```
PrayerService → AstronomyService → CachedAstronomyService? → AstronomyCalculator
```

- Calculators stay pure and testable without cache contamination.
- Cache **intermediate facts** (`SolarPosition`, `RiseSet`), not just final
  feature results, so AR/prayer/calendar converge on one computation per
  `(Location, Instant)` instead of recomputing independently.
- Each cacheable capability defines its OWN semantic key — there is no
  universal key scheme:
  - solar position → `(Location, Instant)`
  - prayer times → `(Location, LocalDate, ZoneId, PrayerParams)`
  - hijri → `(LocalDate, CalendarParams)`
- Computation identity must include configuration that affects results
  (ephemeris/refraction/ΔT model selection) — caching must never hide those
  dependencies.
- No location rounding by default; sharing entries across nearby coordinates
  would be an explicit optimization with documented error bounds.
- Do NOT build the decorator until profiling shows need; the seam above is its
  reservation.

## 7. CLI harness (`tools/cli`, not public API)

```
cli prayer --lat 51.5 --lon -0.12 --alt 11 --tz Europe/London --madhab hanafi --month 12
cli sun --lat --lon --date            # position + rise/set
cli moon --lat --lon --date           # phase, illumination, hilal
cli hijri --date --offset 0
cli events --month --year
cli qibla --lat --lon
cli verify                            # run golden-master diff vs fixtures/
```

## 8. Verification strategy

## 8. Verification strategy

Two layers.

**Layer A — fixture hierarchy.** Donors prove "behavior unchanged", NEVER
"behavior is correct":

1. **Regression fixtures** — generated ONCE from donor Osprey output (~10
   locations × 365 days × madhab × high-latitude combos, JSON). Generate early
   while Osprey builds. `cli verify` diffs against them.
2. **Reference fixtures** — hand-curated expected values for known inputs,
   maintained by us, independent of any donor.
3. **Authoritative validation cases** — spot tables from established external
   sources (almanacs, reference implementations) for Makkah, London,
   Tromsø (polar), New York. These decide correctness; regression fixtures
   only decide drift.

**Layer B — property tests:** prayer times strictly increasing; sun never above
horizon at Fajr end; qibla bearing ∈ [0°, 360°); hijri round-trips;
moon illumination ∈ [0, 1]. High-latitude polar cases explicit.

**Units as types at high-risk boundaries** — astronomy's classic degree/radian
disasters are prevented with lightweight value classes at every public API
edge: `Latitude`, `Longitude`, `Degrees`/`Radians`, `DistanceKm`,
`AltitudeMeters`. Raw `Double` may flow internally; it may not cross an API
boundary untyped.

**Module rules enforced by Gradle, not prose:** `prayer → astronomy, core`;
`calendar → astronomy, core`; `astronomy/qibla/zakat → core`. Reverse or
undeclared dependencies fail the build.

**Lock criteria:** regression fixtures green ∩ property tests green ∩
authoritative validation accepted ∩ API frozen ∩ dependency audit clean:
`engine/*` must have zero Android SDK, AndroidX, Compose, Kotlin-Android,
persistence, networking, filesystem, or UI dependencies — build-failing, not
convention. A portable computational library, not an Android library in
disguise.

## 8.5 API stability pipeline (before any UI)

```
implementation → correctness → benchmark → API review → API freeze
→ publish artifact → host app integration
```

The API is the product; internals are replaceable.

| Module | Status | Donor source (Osprey path) |
|---|---|---|
| core | ✅ locked | typed geo units (`Latitude`/`Longitude`/`AltitudeMeters`/`Location`) |
| astronomy | ✅ locked | cosinekitty 2.1.19 (JitPack) behind capability API; sun/moon/hilal + one-pass `moon.track`; 576-case golden matrix green; divergences D7-D8 |
| calendar | ✅ locked | ummalqura-calendar 2.0.2 behind capability API; hijri/events/fastDays; authoritative anchor dates + round-trip properties; D10-D11 |
| prayer | ✅ locked | wraps `com.batoulapps.adhan:adhan2-jvm:0.0.7`; 6184-case golden matrix green; polar-safe; see docs/divergences.md D1–D4 |
| qibla | ✅ locked | bearing + distance owned by engine; cross-checked vs adhan2 (docs/divergences.md D5) |
| zakat | ✅ locked | pure extraction from donor UI; fiqh spot cases + property tests; rate 0.025, madhab rules, saʿ conventions documented |
| mushaf | ✅ locked | glyph-atlas layout math ported from donor QuranApp rasterizer/measurer; `Mushaf` capability (fit/measure/layout); golden + property tests green; atlas SPEC DATA lives in khushu-data-api (`inventory/atlas/`), engine stays content-free |
| facade | ✅ locked | thin delegation namespaces (`engine.astronomy.sun.*` etc.), zero logic |
| cli | ✅ locked | all spec §7 commands incl. `verify` (both golden suites PASS); not part of public API |
| goldens | ✅ dumped | prayer 6184 cases (adhan2 0.0.7, incl. date-line site); astronomy 576 cases (donor AR path); regenerable in-repo via tools/golden-dumper |

Legend: ⬜ planned · 🟨 in progress · ✅ locked (all verification green)

Note: donors contain **duplicate implementations** (e.g. three separate
sun-position codes: `AstroEphemerisEngine`, `SolarEphemeris`,
`SunMoonCalculator`). The engine keeps ONE implementation per fact;
reconcile duplicates during the port and record which was authoritative in
`docs/divergences.md`.

## 10. Explicit non-goals

No Quran/hadith/dua content, no audio, no notifications/scheduling
(caller-side), no persistence, no Android UI, no network. The engine computes
facts; features request facts.

Content/data domains belong in a **future sibling project** if ever needed:

```
khushu-engine   ← computation (this repo): astronomy, prayer, calendar, qibla, zakat
khushu-data     ← content pipeline outputs (quran, hadith, dua, asma) — from khushu-quran-data
```

Host apps may depend on both. Never grow the engine into an Islamic-data
warehouse.

**Anti-overengineering gate:** scaffold boundaries → define domain APIs →
migrate ONE domain (astronomy) → prove with tests → only then build out the
remaining modules. Ten modules of speculative architecture before one proven
domain is how this regrows the bloat we're escaping.

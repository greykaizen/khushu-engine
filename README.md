# khushu-engine

[![Release](https://jitpack.io/v/greykaizen/khushu-engine.svg)](https://jitpack.io/#greykaizen/khushu-engine)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**Pure Kotlin/JVM Islamic computation engine** — prayer times, solar/lunar
astronomy, Hijri calendar, qibla, zakat, tasbih, qada, and mushaf
glyph-atlas layout. Deterministic, dependency-light, unit-tested against
golden matrices.

Part of the [Khushu](https://github.com/greykaizen/khushu) project — see
[the family](#the-khushu-project-family) below.

- **No Android.** No Compose, no `Context`, no persistence, no network, no
  UI anywhere in `engine/*`. A portable computational library, not an
  Android library in disguise.
- **No content.** The engine computes *facts*; Quran/hadith text, atlases
  and audio indexes live in the sibling
  [khushu-data-api](https://github.com/greykaizen/khushu-data-api) repo.
- **Every call is pure** over explicit arguments (location, date, params) —
  same inputs, same outputs, forever.

## Modules

| Module | Artifact | What it does | Key APIs |
|---|---|---|---|
| **prayer** | `engine-prayer` | Prayer times for any location/date — 18 conventions, 4 madhabs, high-latitude rules (incl. `ANTI_TRANSIT_FAJR`), polar-day safe, per-prayer offsets, shafaq, rounding. Golden matrix: 6,184 cases | `times`, `status`, `navigationSequence`, `stats.streak`, `windows.imsak/qiyam/fasting/travel`, `nightDivisions`, `nextOccurrenceOf`, `nextJumuah`, `qadaReport` |
| **astronomy** | `engine-astronomy` | Sun/moon position, daily sky tracks (hourly ENU samples), moon phase + bright-limb tilt, rise/set, hilal visibility (Yallop/Odeh/PMD/MABIMS/Turkey/Wujudul-Hilal), eclipses, seasons, anti-transit, istiwa, subsolar alignment | `sun.position/track/phases/events/antiTransit/istiwaPeriod/zuhrShadowIncrease`, `moon.state/track/distanceExtremes/eclipses`, `hilal.visibility/forecast`, `seasons`, `sun.audit` |
| **calendar** | `engine-calendar` | Hijri ⇄ Gregorian conversion (Umm al-Qura), Islamic events, optional fast-day rules, region-based civil calendars (Persian, Śaka, Bangla, Coptic, Ethiopian, Japanese, Minguo, Thai Buddhist), observed/moonsighted mode with announcement anchors | `hijri`, `hijriToGregorian`, `events`, `upcoming`, `daysUntil`, `fastDays`, `month.summary`, `sightedCalendar.hijriSighted`, `regionalCalendars` |
| **qibla** | `engine-qibla` | Great-circle bearing + distance to the Kaaba, shadow-verification events (when the sun/shadow aligns with qibla), sun-qibla relation | `bearing`, `relativeSunAngle`, `shadowVerification`, `audit` |
| **zakat** | `engine-zakat` | Mal zakat (dual nisab thresholds, madhab rules, per-asset breakdown with provenance notes), fitrana (food/cash modes), hawl countdown, ushr (irrigation-rule-aware), livestock schedules, rikaz | `mal`, `fitrana`, `hawlFacts`, `ushrDue`, `livestockDue`, `rikazDue` |
| **tasbih** | `engine-tasbih` | Dhikr counting: 7 canonical fiqh-sourced presets, factories over data-api duas/Names, STRICT daily-goal streaks, per-definition lifetime stats | `PRESETS`, `ofDua`, `ofName`, `custom`, `progressOn`, `dhikrStats` |
| **observance** | `engine-observance` | Centralized bookmarks for all content domains, reading-session statistics (explicit-mark STRICT streaks, targets, continue-reading, history), khatm progress math | `toggleBookmark`, `upsertBookmark`, `bookmarkViews`, `readStats`, `continueReading`, `history`, `khatmFacts` |
| **mushaf** | `engine-mushaf` | Glyph-atlas layout math: page fitting (median-fill), line shrink, RTL glyph placement, ayah wrapping (content-free; atlas specs enter by value from khushu-data-api) | `measureWordWidthPx`, `fitPageScale`, `fitLineShrink`, `layoutAyah`, `layoutWord`, `layoutLine`, `antiTransit`, `istiwaPeriod`, `zuhrShadowIncrease` |
| **core** | `engine-core` | Typed geo units (`Latitude`, `Longitude`, `Kilometers`, `ArcMinutes`), typed errors (`InvalidParameterException`, `NoResultException`, …), shared day-streak math, angle normalization | `DayStreakMath.dayClasses/streakFacts`, `AngleMath.normalizeDeg` |
| **facade** | `engine-facade` | `KhushuEngine()` — single entry point with thin delegation namespaces (`engine.prayer.*`, `engine.astronomy.sun.*`, `engine.tasbih.*`, …), zero logic. Recommended entry point | Everything, via one import |

## Quick start

### JitPack (recommended)

```kotlin
// settings.gradle.kts or build.gradle.kts
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // The facade pulls every module transitively — one line is enough.
    implementation("com.github.greykaizen.khushu-engine:engine-facade:2.0.0")
}
```

### Maven Local (offline)

```bash
git clone https://github.com/greykaizen/khushu-engine
cd khushu-engine && ./gradlew publishToMavenLocal
```

```kotlin
dependencies { implementation("com.khushu:engine-facade:2.0.0") }
```

### Composite build (developing the engine alongside your app)

```kotlin
// host settings.gradle.kts
includeBuild("/path/to/khushu-engine")
```

then depend on `com.khushu:engine-facade:2.0.0` — Gradle substitutes the
local project.

## Usage

```kotlin
import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Madhab
import com.khushu.engine.prayer.PrayerConfiguration
import java.time.LocalDate
import java.time.ZoneId

val engine = KhushuEngine()
val london = Location.of(51.5072, -0.1276)
val config = PrayerConfiguration(madhab = Madhab.HANAFI)

// Prayer times (raw + adjusted per-prayer, with provenance audit)
val times = engine.prayer.times(london, LocalDate.of(2026, 6, 21), config)
times.fajr.adjusted                 // Instant
times.audit.highLatitudeResolution  // how polar cases were resolved

// Astronomy facts
val sun = engine.astronomy.sun.position(london, instant)   // az/alt/RA/dec
val moon = engine.astronomy.moon.state(london, instant)     // phase, illumination, limb tilt

// Calendar & qibla
val hijri = engine.calendar.hijri(LocalDate.of(2026, 3, 30))   // 1 Shawwal 1447 AH
val qibla = engine.qibla.bearing(london)                        // bearing + distance to Kaaba

// One-call day composite
val day = engine.day.summary(london, LocalDate.of(2026, 3, 30), ZoneId.of("Europe/London"))
```

## Settings persistence (`store` module)

The engine itself is persistence-free. The opt-in `store` companion artifact
(host glue, outside the purity lock) gives every host the same versioned,
crash-safe settings store:

```kotlin
implementation("com.github.greykaizen.khushu-engine:store:2.0.0")
```

```kotlin
val store = KhushuSettingsStores.file(File(context.filesDir, "khushu_settings.json"))
val snapshot by store.settings.collectAsState(initial = SettingsSnapshot())

// Feed persisted settings into any engine call
val times = engine.prayer.times(location, date, snapshot.prayer.toConfiguration())

// Save — atomic, survives process death
scope.launch { store.updatePrayer(newConfig) }
```

Covers every tunable: prayer (madhab, convention, angles, offsets, shafaq,
rounding, high-latitude rule), calendar (hijri offset, fast flags, display
sides, civil calendar, sighted-mode offset), zakat (madhab, nisab source,
weight convention, debt policy, jewelry valuation, fitr mode, irrigation
rule, ushr nisab), plus location. Schema-versioned JSON with lenient
decoding — files from newer releases load on older code and vice versa.

## Observance logs (host-owned)

Prayer checkmarks, tasbih counts, reading sessions, bookmarks — the user's
religious data — live in the host by design (privacy + the engine stays
persistence-free). Persist them in your own storage, feed them in per query:

```kotlin
// Prayer streaks
val stats = engine.prayer.stats.streak(records, excusedRanges = hostRanges)

// Tasbih goal streaks
val tasbih = engine.tasbih.dhikrStats(countLogs, definitions, today)

// Reading streaks + targets
val reading = engine.observance.readStats(sessions, targets, today)

// Khatm progress
val khatm = engine.observance.khatmFacts(completedRanges, juzRanges, today)
```

## Facade vs individual modules

Every module is published as its **own artifact** — a qibla-only tool needs
just `engine-qibla:2.0.0` (it drags in only core). The **facade is the
recommended entry point** for full-featured apps.

## Guarantees

- **API validated** — binary compatibility validator (`apiCheck`) gates
  every build
- **Golden-matrix tested** — 7,000+ fixture cases plus property tests
  (prayer monotonicity, Hijri round-trips, qibla bounds, …), regenerable
  via `tools/golden-dumper`
- **Typed units at the boundary** — degrees, latitudes, distances are
  distinct types; raw doubles never cross an API edge
- **Structured failures** — typed, fielded exceptions, never bare throws
- **Deterministic** — no clocks, no caches, no ambient state inside the
  engine

## Error handling

Every failure is a subtype of `KhushuInputFailure` (bad argument) or
`KhushuComputationFailure` (no legitimate answer / upstream failure):

| Type | Meaning | Carries |
|---|---|---|
| `InvalidParameterException` | one named parameter violated the contract | `parameter`, `value`, `constraint` |
| `HijriDayDoesNotExistException` | the hijri date does not exist | `hijriYear/Month/Day`, `offsetDays` |
| `NoResultException` | requested fact absent | `detail` |
| `UpstreamComputationException` | adhan2 / cosinekitty / ummalqura rejected the input | `detail`, `cause` |

Input failures extend `IllegalArgumentException`, computation failures
extend `IllegalStateException` — existing catches keep working. Message
format: `khushu: invalid <parameter> = <value> (<constraint>)`.

## Docs

- [`docs/integration.md`](docs/integration.md) — host-app integration patterns
- [`docs/public-interface.md`](docs/public-interface.md) — full API snapshot
- [`docs/divergences.md`](docs/divergences.md) — every deliberate deviation from donor code
- [`docs/dependency-bumps.md`](docs/dependency-bumps.md) — upgrade runbook

## The Khushu project family

| Repo | Role |
|---|---|
| [khushu-engine](https://github.com/greykaizen/khushu-engine) | **You are here** — computation: prayer times, astronomy, calendar, qibla, zakat, tasbih, observance, qada |
| [khushu-data-api](https://github.com/greykaizen/khushu-data-api) | Content: Quran text, hadith corpora, duas, adhan audio, bookmarks, download tracking |
| [khushu](https://github.com/greykaizen/khushu) | The app — Android (Kotlin/Compose), consuming both libraries |

## License

GPL-3.0 — see [LICENSE](LICENSE). Quran/hadith *content* is not in this repo.

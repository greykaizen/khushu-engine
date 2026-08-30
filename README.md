# khushu-engine

[![Release](https://jitpack.io/v/greykaizen/khushu-engine.svg)](https://jitpack.io/#greykaizen/khushu-engine)

**Pure Kotlin/JVM Islamic computation engine** — prayer times, solar/lunar
astronomy, Hijri calendar, qibla, zakat, and mushaf glyph-atlas layout.
Deterministic, dependency-light, unit-tested against golden matrices.

- **No Android.** No Compose, no `Context`, no persistence, no network, no
  UI anywhere in `engine/*`. A portable computational library, not an Android
  library in disguise.
- **No content.** The engine computes *facts*; Quran/hadith text, atlases and
  audio indexes live in the sibling
  [khushu-data-api](https://github.com/greykaizen/khushu-data-api) repo.
- **Every call is pure** over explicit arguments (location, date, params) —
  same inputs, same outputs, forever.

## What it provides

| Module | Artifact | Highlights |
|---|---|---|
| `prayer` | `engine-prayer` | Prayer times, current/next prayer, navigation sequence, imsak/qiyam windows. 18 conventions, 4 madhabs, high-latitude rules, polar-day safe. Golden matrix: 6,184 cases |
| `astronomy` | `engine-astronomy` | Sun/moon position (az/alt/RA/dec), daily sky tracks, moon phase + bright-limb tilt, rise/set, hilal visibility, eclipses. Golden matrix: 576 cases |
| `calendar` | `engine-calendar` | Hijri ⇄ Gregorian conversion, Islamic events, optional fast-day rules, region-based civil calendars (Persian, Śaka, Bangla, Coptic, Ethiopian, Japanese, Minguo, Thai Buddhist) |
| `qibla` | `engine-qibla` | Great-circle bearing + distance to the Kaaba |
| `zakat` | `engine-zakat` | Mal zakat (dual nisab/madhab rules/breakdown), fitrana, hawl countdown, ushr, livestock schedules |
| `mushaf` | `engine-mushaf` | Glyph-atlas layout math: page fitting, line shrink, RTL glyph placement (content-free; atlas specs enter by value) |
| `core` | `engine-core` | Typed geo units (`Latitude`, `Longitude`, `Location`), shared primitives |
| **facade** | `engine-facade` | `KhushuEngine()` — single entry point, thin delegation namespaces (`engine.prayer.*`, `engine.astronomy.sun.*`, …), zero logic |

## Adding it to your project

### JitPack (recommended — no account setup anywhere)

```kotlin
// settings.gradle.kts or build.gradle.kts
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // The facade pulls every module transitively — one line is enough.
    implementation("com.github.greykaizen.khushu-engine:engine-facade:1.14.0")
}
```

### Maven Local (offline / machine-local)

```bash
git clone https://github.com/greykaizen/khushu-engine
cd khushu-engine && ./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral(); maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.khushu:engine-facade:1.14.0") }
```

### Composite build (developing the engine alongside your app)

```kotlin
// host settings.gradle.kts
includeBuild("/path/to/khushu-engine")
```

then depend on `com.khushu:engine-facade:1.14.0` — Gradle substitutes the
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

// Prayer times (raw + adjusted per-prayer)
val times = engine.prayer.times(london, LocalDate.of(2025, 6, 21), config)
times.fajr.adjusted                 // Instant
times.audit.highLatitudeResolution  // how polar cases were resolved

// Astronomy facts
val sun = engine.astronomy.sun.position(london, instant)   // az/alt/RA/dec
val moon = engine.astronomy.moon.state(instant)            // phase, illumination, limb tilt

// Calendar & qibla
val hijri = engine.calendar.hijri(LocalDate.of(2025, 3, 30))   // 1 Shawwal 1446 AH
val qibla = engine.qibla.bearing(london)                       // bearing + distance to Kaaba

// One-call day composite
val day = engine.day.summary(london, LocalDate.of(2025, 3, 30), ZoneId.of("Europe/London"))
```

`CachedEngine(engine)` adds cross-feature memoization so each fact is
computed once and reused: prayer times (day and whole-month), occasions,
tahajjud/night divisions, day-summary windows (imsak, duha, forbidden,
fasting facts, travel facts), day summary, sun position/rise-set/events/
phases, moon state/rise-set, Hijri date and qibla. Every cache is keyed by
its semantic key *including configuration* and bounded as LRU (GPS jitter
never grows memory without bound). Pass `forceRecompute = true` to bypass
the lookup, recompute, and overwrite the stored entry (settings import,
time-travel debugging — never hot paths); `clearCaches(domain)` drops one
domain's caches (`CachedEngine.DOMAINS`), `clearCaches()` drops everything.

## Host settings persistence (`store` module)

The engine itself is persistence-free: every call takes explicit settings.
The opt-in `store` companion artifact — host glue that lives **outside** the
computational engine (the purity lock applies to `engine/*` only) — gives
every host the same versioned, crash-safe settings store instead of each
re-implementing persistence. Built on Jetpack DataStore + kotlinx-serialization
JSON; pure JVM compatible, so it works on Android and plain JVM hosts alike.

```kotlin
implementation("com.github.greykaizen.khushu-engine:store:1.14.0")   // JitPack
// or mavenLocal: implementation("com.khushu:store:1.14.0")
```

```kotlin
// Android host (Compose)
val store = remember {
    KhushuSettingsStores.file(File(context.filesDir, "khushu_settings.json"))
}
val snapshot by store.settings.collectAsState(initial = SettingsSnapshot())

// Feed persisted settings into any engine call
val times = engine.prayer.times(location, date, snapshot.prayer.toConfiguration())
val hijri = engine.calendar.hijri(date, snapshot.calendar.toParams().hijriOffsetDays)

// Save from a settings screen — atomic, survives process death, re-emits via Flow
scope.launch { store.updatePrayer(newConfig) }
```

- `SettingsSnapshot` covers **every tunable**, grouped per domain: `prayer`
  (madhab, convention, angles, isha interval, high-latitude rule, all seven
  offsets, shafaq, rounding), `calendar` (hijri offset, every optional-fast
  flag), `zakat` (madhab, nisab source, weight convention, hawl) plus
  `location`. Typed helpers: `updatePrayer/updateCalendar/updateZakat/
  updateLocation/resetSettings`; engine-ready flows `prayerConfiguration`,
  `calendarParams`, `zakatParams`.
- `schemaVersion` + lenient decoding: unknown keys ignored, missing fields
  default, unknown enum values coerce — files written by newer releases load
  on older code and vice versa; migration branches land in `SettingsCodec`.
- Corrupt files throw `CorruptionException` instead of silently resetting —
  a silent fallback would change religiously-relevant settings unnoticed.
- Invalid persisted values surface as the engine's typed
  `InvalidParameterException` when converting back (`toConfiguration()` etc.).
- `SettingsCodec` is DataStore-free — alternative bindings (plain files,
  SQLDelight, iOS NSUserDefaults) can reuse the same wire format later.

### Observance logs (host-owned)

Prayer checkmarks/completions are the *user's* religious data, so they live
in the host by design (privacy + the engine stays persistence-free):

1. Persist `Prayer.PrayerLogRecord(date, kind, completed)` rows in the host's
   own storage (Room, DataStore-Preferences, SQLite — host's choice).
2. Feed them in per query, statelessly:
   ```kotlin
   val stats = engine.prayer.stats.streak(records, excusedRanges = hostRanges)
   ```

`excusedRanges` (travel/illness/menses…) is host state too — the engine never
decides who is excused; it computes statistics over the ranges it is given.

### Disk caching guidance

`CachedEngine` is in-memory by design — prayer/astronomy math is fast and
deterministic, so a disk-backed cache rarely earns its keep:

- **Default**: in-memory `CachedEngine` LRU. Lives for the session, zero
  staleness maintenance.
- **After the user changes settings**: call affected reads with
  `forceRecompute = true`, or `clearCaches(domain)` once
  (`CachedEngine.DOMAINS`) — facts are keyed *including configuration*, so
  nothing stale can leak into new settings.
- **If a cold-start warm cache ever becomes a measured need** (profile
  first): persist `DaySummary` / `PrayerTimesResult` JSON yourself keyed by
  the fact's stable semantic key — `(location, date, zone, params)` — and
  re-verify with `forceRecompute` after settings imports. Keep such a layer
  in the host or a companion artifact, never in `engine/*`.

## Facade vs individual modules

Every module above is published as its **own artifact** and can be consumed
directly — e.g. a qibla-only tool needs just
`com.github.greykaizen.khushu-engine:engine-qibla:1.11.0` (it drags in only
what it uses). The **facade is the recommended entry point** for
full-featured apps: one dependency, capability namespaces, and room to grow
without build-file churn.

## Guarantees

- **API is locked** — public surface validated by the Kotlin binary
  compatibility validator (`apiCheck` gates every build).
- **Golden-matrix tested** — 7,000+ fixture cases plus property tests
  (prayer monotonicity, Hijri round-trips, qibla bounds, …), all in-repo,
  regenerable via `tools/golden-dumper`.
- **Typed units at the boundary** — degrees/radians, latitudes, distances are
  distinct types; raw doubles never cross an API edge.
- **Structured failures** — when a call fails you get a typed, fielded
  exception, never a guess (see below).
- Deterministic: no clocks, no caches, no ambient state inside the engine
  (caching is an opt-in decorator).

## Error handling

The engine never throws bare, unstructured exceptions. Every failure is a
subtype of `com.khushu.engine.core.error.KhushuInputFailure` (bad argument) or
`KhushuComputationFailure` (no legitimate answer / upstream library failure):

| Type | Meaning | Carries |
|---|---|---|
| `InvalidParameterException` | one named parameter violated the contract | `parameter`, `value`, `constraint` |
| `HijriDayDoesNotExistException` | the hijri date does not exist (e.g. 30 in a 29-day month) | `hijriYear/Month/Day`, `offsetDays` |
| `NoResultException` | requested fact absent (no occurrence in the search window, empty scan window) | `detail` |
| `UpstreamComputationException` | adhan2 / cosinekitty / ummalqura rejected the input (message names the valid range) | `detail`, original `cause` |

Compatibility: input failures extend `IllegalArgumentException`, computation
failures extend `IllegalStateException` — existing catches keep working. Catch
`KhushuInputFailure` / `KhushuComputationFailure` for broad handling, or the
concrete class to read the structured fields. Message format is stable and
log-greppable: `khushu: invalid <parameter> = <value> (<constraint>)`.
Legitimate absence is never an exception: polar days return null fields /
empty lists, and `PrayerTimesResult.audit.warnings` explains why.

## Deprecation policy

- Removals/renames are **breaking** and only happen in a major version, with at
  least one minor version of `@Deprecated(WARNING, ReplaceWith=…)` beforehand.
- Additions are minor versions (the API today is addition-only).
- Every deprecation updates the `api/*.api` snapshots and the README in the
  same change. Third-party dependency bumps follow `docs/dependency-bumps.md`.

## Requirements

- JDK 21 (Gradle toolchain), Kotlin 2.4.x — matches Android toolchains on
  current AGP; JVM-only library, usable from any Kotlin/JVM or Android host.

## Docs

- `AGENTS.md` — architecture, module boundaries, dependency rules
- `docs/integration.md` — host-app integration patterns (prayer lifecycle,
  mushaf rendering recipe, AR fact mapping)
- `docs/public-interface.md` — full API snapshot
- `docs/divergences.md` — every deliberate deviation from donor code, with
  provenance
- `docs/dependency-bumps.md` — runbook for upgrading pinned libraries without
  silent behavior drift

## Content pairing

For Quran text, translations, tafsirs, hadith corpora, mushaf layouts and
glyph atlases, combine this engine with
[khushu-data-api](https://github.com/greykaizen/khushu-data-api) — its
`ContentRepository` feeds typed specs into `engine.mushaf.*`.

## License

GPL-3.0 — see [LICENSE](LICENSE). Quran/hadith *content* is not in this repo.

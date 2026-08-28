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
| `calendar` | `engine-calendar` | Hijri ⇄ Gregorian conversion, Islamic events, optional fast-day rules |
| `qibla` | `engine-qibla` | Great-circle bearing + distance to the Kaaba |
| `zakat` | `engine-zakat` | Mal zakat (nisab/rate/madhab rules), fitrana, ushr, livestock schedules |
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
    implementation("com.github.greykaizen.khushu-engine:engine-facade:1.5.0")
}
```

### Maven Local (offline / machine-local)

```bash
git clone https://github.com/greykaizen/khushu-engine
cd khushu-engine && ./gradlew publishToMavenLocal
```

```kotlin
repositories { mavenLocal(); mavenCentral(); maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.khushu:engine-facade:1.5.0") }
```

### Composite build (developing the engine alongside your app)

```kotlin
// host settings.gradle.kts
includeBuild("/path/to/khushu-engine")
```

then depend on `com.khushu:engine-facade:1.5.0` — Gradle substitutes the
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
computed once and reused: prayer times (day and whole-month), day summary,
sun position/rise-set/events/phases, moon state/rise-set, Hijri date and
qibla. Every cache is keyed by its semantic key *including configuration*
and bounded as LRU (GPS jitter never grows memory without bound).

## Facade vs individual modules

Every module above is published as its **own artifact** and can be consumed
directly — e.g. a qibla-only tool needs just
`com.github.greykaizen.khushu-engine:engine-qibla:1.5.0` (it drags in only
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

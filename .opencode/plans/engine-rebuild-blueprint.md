# Osprey Engine Rebuild Blueprint

Status: draft · Strategy: **terminal-first, UI-last**

## Decision record

- New standalone project (fresh start); engine built and **locked before any UI work**.
- Pure Kotlin/JVM — no Android Gradle Plugin, no emulator, no device in the loop.
  Fast compile/test cycle in terminal only.
- All computational domains in scope: core, astronomy, calendar, prayer, qibla, zakat.
- Data/UI domains (Quran, Sunnah, Dua, Tasbih state) are explicitly OUT of the
  engine — they are data or UI state, not computation. Including them would
  recreate the god-object problem.
- UI will consume the frozen API once locked; screens get rewired to request
  facts instead of computing inline (kills the HomepageContent over-computation
  class of bugs by construction).

## Project layout

```
osprey-engine/                 ← standalone Kotlin/JVM Gradle project
├── engine/
│   ├── core                   ← time primitives, Location/Clock abstractions,
│   │                            Result types, math utils. No domain logic.
│   ├── astronomy              ← Julian date, solar/lunar position, rise/set,
│   │                            moon phase (port AstroMath / AstroEphemerisEngine)
│   ├── calendar               ← Hijri conversion, calendar math (port CalendarHelper)
│   ├── prayer                 ← prayer rules, madhab params, high-latitude rules
│   │                            (port from CalendarHelper / HomeViewModel)
│   ├── qibla                  ← bearing math (~30 lines)
│   └── zakat                  ← nisab/rate rules (port ZakatCalculator logic)
├── tools/
│   └── cli                    ← human spot-check harness. NOT public API.
└── settings.gradle.kts        ← pure JVM; includeBuild later if app repo wants it
```

Dependency direction:

```
core ↑ astronomy ↑ {calendar, prayer}   ; qibla, zakat → core only
app (later) → engine facade
```

## Engine design rules

1. Zero Android/Compose imports anywhere under `engine/` — enforced by module
   boundaries (impossible to cheat).
2. Small explicit API: request/result data classes in/out
   (`PrayerTimesRequest → PrayerTimesResult`, `SolarPosition(location, instant)`).
3. Clock/location injected as parameters — no globals, no singletons.
4. At most one thin facade (`OspreyEngine.prayer.times(...)`); no god object.
5. Port, don't redesign: move existing math as-is first, lock against goldens,
   refactor internals only after tests are green.
6. Cache last: behind the facade, only where profiling shows cost.

## Three layers of terminal verification

1. **Unit tests** — per-function checks; ported `AstroMath` passes identical
   assertions to current behavior.
2. **Golden-master suite** — fixtures dumped ONCE from the current Osprey code:
   ~10 locations × 365 days × madhab/high-latitude combos as JSON.
   New engine must reproduce them exactly. Green matrix = "locked."
3. **Property tests** — invariants goldens can't cover: sun never rises before
   Fajr ends; prayer times strictly increasing; Qibla bearing ∈ [0°, 360°);
   Hijri round-trip conversions. Catches high-latitude edge cases.

CLI for eyeballing without writing a test:

```
cli prayer --lat 51.5 --lon -0.1 --madhab hanafi --month 12   # prints table
cli verify                                                    # diffs vs goldens
```

## Sequencing

1. Scaffold project + `:engine:core` + `:engine:astronomy` (mostly file moves
   from Osprey `core/astronomy`) + golden fixtures generated EARLY while old
   repo still builds cleanly.
2. `prayer` + `qibla` → harness prints correct times → first usable milestone.
3. `calendar` + `zakat`.
4. Lock gate: golden matrix green + property tests green + API surface frozen +
   no Android imports verified.
5. Only then: create Android app shell (copy `core/designsystem`, `ui/shell`,
   `app/navigation` from Osprey — audited animation architecture moves intact),
   transplant feature screens one at a time onto the engine API.

## Definition of "engine locked"

- Golden-master matrix green across all six domains
- Property tests green
- Public API data classes reviewed and frozen
- Module boundary audit confirms zero Android/Compose dependencies in engine/

## Risks / notes

- Generate golden fixtures early — don't let the old app rot first.
- Prayer-time regressions are the highest-stakes failure; goldens + property
  tests exist precisely to prevent silent drift.
- Keep CLI out of any published/consumed API surface.

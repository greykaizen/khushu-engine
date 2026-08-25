# Divergences & Decisions

Record of where the engine deliberately differs from donor code, which
duplicate implementations were reconciled, and why. Donor policy: donors are
references, never gospel (AGENTS.md §2).

## Session 1 — core / prayer / qibla

### D1. Ishraq/Duha computed with exact solar geometry, not donor's approximation
- **Donor**: `Osprey .../domain/prayer/PrayerCalculation.kt:153-164` computes the
  Ishraq hour angle using the coarse declination approximation
  `23.45·sin(360·(284+doy)/365)` and ignores the equation of time.
- **Engine**: `engine/prayer/internal/SolarGeometry.kt` uses the NOAA fractional-year
  declination + equation-of-time formulas (accuracy well under one minute) and
  anchors on adhan2's transit when available.
- **Why**: donor formula can drift minutes from true sun position; fiqh windows
  deserve real ephemeris. Re-anchor onto `astronomy.sun` facts when the astronomy
  module lands.

### D2. Polar day/night handled instead of thrown away
- **Donor/library**: adhan2 0.0.6 throws `IllegalStateException` from the
  `PrayerTimes` constructor when a time is uncomputable (midnight sun), and
  returns numerically degenerate times during polar night (e.g. Asr before
  transit at Tromsø in November).
- **Engine**:
  - Solar transit, zawaal start and dhuhr entry are always computed (exact NOAA
    geometry) — solar noon exists even when the sun never crosses the horizon.
  - `polarAnomaly` flag is grounded in physical detection (`sunAltitudeExtremesDeg`):
    midnight sun, polar night, Asr-degenerate days (required Asr altitude within
    rounding noise of the day's maximum altitude), plus library failures.
- **Goldens**: fixtures record donor failures as `"error":"UNCOMPUTABLE"` /
  `"SUNNAH_UNCOMPUTABLE"` rows so drift in failure behaviour stays detectable.

### D3. Sunset decoupled from maghrib's display offset
- **Donor**: applied `offsetMaghrib` and `offsetSunset` independently to the same
  raw instant — correct intent, but implemented outside the library.
- **Engine**: maghrib offset flows through adhan2 `PrayerAdjustments` (single
  source, applied pre-rounding); sunset anchors on the *unadjusted* instant plus
  its own offset. Prevents sunset silently inheriting maghrib's shift.

### D4. UI concerns stripped from the calculation boundary
- **Donor**: `calculatePrayersForDate(year…use24Hour)` takes string angles
  (`"18.5"`), string timezone (`"GMT+5"`) and formatting flags.
- **Engine**: typed `PrayerParams` (enums, Double angles, Int intervals);
  results are absolute `java.time.Instant`s — formatting/timezone rendering is
  the caller's concern. `zoneId` appears only where civil-date resolution is
  required (`status`). This intentionally narrows the spec §4 signature
  `prayer.times(location, localDate, zoneId, params)` — no parameter is accepted
  without being used.

### D5. Qibla duplicate implementations reconciled
- **Donors**: hand-rolled bearing in `domain/qibla/QiblaDirection.kt` AND
  `adhan2.Qibla.direction` (used by `QiblaScreen.kt`).
- **Engine**: owns the standard initial-bearing formula with the same Kaaba
  constants (21.4225 N, 39.8262 E). Cross-checked against adhan2 across nine
  global locations: agreement < 1e-5 degrees (~metres of arc). Engine chosen as
  authoritative; adds great-circle distance which both donors lacked.

### D6. Toolchain raised to JVM 21
- adhan2-jvm 0.0.6 ships Java 21 bytecode; the scaffold targeted 17. All engine
  modules now use `jvmToolchain(21)`.

### D7. Calendar moon: invented fallbacks and default observers eliminated
- **Donor**: `LunarEphemeris.getMoonTransit` substituted fake times when a search
  failed (`rise = midnight + 8h`, `set = rise + 12h`), and
  `SunMoonCalculator.calculateMoonPhase` defaulted the observer to hardcoded
  `(30°, 70°)` — silently wrong bright-limb tilts for any real user.
- **Engine**: uncomputable events stay `null` (polar/edge cases are explicit);
  every lunar fact requires an explicit `Location`. The AR path's trusted math
  (χ−q bright-limb tilt, ENU vectors) is ported behavior-identically
  (`internal/LunarOrientationMath.kt`) and locked by `astro_golden.json`.

### D8. Month-moon computed in one pass; events bounded to the civil day
- **Donor**: calendar recomputed per-render — repeated rise/set searches plus a
  96-sample path per draw, with no fact reuse.
- **Engine**: `moon.track(location, yearMonth, zoneId)` walks the month once
  (per day: rise, set, transit, phase@transit, optional render path).
  Benchmarked ≈1.5× **faster** than the donor call pattern (65 ms vs 95 ms per
  month, including a 48-sample/day render path) while returning strictly more
  facts (`MoonTrackBenchmarkTest`). Solar/lunar events are additionally bounded
  to their civil day — donor could return next-day dusk as today's event.

### D10. Shawwal six-day fast bounded to six days
- **Donor**: `IslamicEventCalculator.kt` marks Shawwal days **2..30** as voluntary
  fasting opportunities under the `showShawwal` flag.
- **Engine**: `FastRule.SHAWWAL_SIX` returns days **2..7** only — the hadith
  specifies *six* days of Shawwal (Sahih Muslim), conventionally taken right
  after Eid. Donor's month-long block overstated the rule.

### D11. Islamic events trimmed to computational core
- **Donor**: ~85% of the 1019-line event list is saint commemorations (urs /
  birthdays of Sufi-chain figures) with long descriptions — content, not computation.
- **Engine**: fixed-hijri-date standard events only (New Year, Tasua/Ashura,
  Mawlid, Isra & Mi'raj, Mid-Sha'ban, Ramadan start, Laylat al-Qadr seek-nights,
  Eid al-Fitr, Hajj begins, Arafah, Eid al-Adha, Tashreeq). The dropped tail
  belongs in a future data project if ever needed.
- Also noted: Umm al-Qura is a **tabular** calendar; it can disagree by one day
  with announced moon-sighting dates (e.g. tabular Eid al-Adha 1445 = Jun 16 vs
  official Jun 17). The `hijriOffsetDays` parameter exists precisely for this.

### D12. AR rendering helpers deferred to the host/AR layer
- **Donor**: `EarthCoordinates.findClosestPointOnSphericalPath`,
  `interpolatePathPoint` (nlerp), `angularSeparationDegrees`, and
  `AstroEphemerisEngine.generateHorizonRing` serve AR boresight→path matching
  and scene scaffolding.
- **Engine**: `CelestialPathPoint` exposes the raw ENU facts; projection,
  closest-point search and ring meshes are rendering concerns and stay out of
  the computational engine (AGENTS.md §10: the engine computes facts). Hosts
  may re-add these as presentation utilities over `MonthlyMoonTrack.pathPoints`.

### D13. Shafaq/Rounding exposure is a documented pass-through, not a claim
- adhan2 0.0.7 exposes `Shafaq{GENERAL,AHMER,ABYAD}` (twilight-color model used
  by the Moon Sighting Committee method) and `Rounding{NEAREST,UP,NONE}`.
- Engine exposes both as nullable configuration fields: `null` inherits the
  method preset; explicit values pass through unchanged. Provenance of the MSC
  twilight-color convention itself is not re-derived here — treated as
  EXPERIMENTAL until independently documented.

### D14. Lunar view polar sampling is flagged, not hidden
- When sunset is uncomputable (polar regions) `LunarCalendarView` samples at
  18:00 local and sets `LunarDayFact.sampledAtFallback = true`. The phase shown
  for such evenings is an approximation anchored mid-evening; hosts should
  surface the flag rather than silently trusting the row.

### D15. Event-pack curation ownership
- The engine EXPORTS the canonical core pack (`EventRegistry.builtInPackJson()`)
  into khushu-quran-data (`assets/islamic_calendar/islamic_events.json`). That
  file's content review belongs to the data-project maintainers; the engine
  treats whatever JSON it is given as input. Community/Aladhan-derived
  definitions always carry `confidence = COMMUNITY` so UIs can distinguish.

## Maintenance log

(Not divergences — environment and dependency decisions recorded for traceability.)

### M1. Dependency currency (2026-08)
- Kotlin 2.1.20 → **2.4.10**; kotlinx-serialization-json 1.8.0 → **1.11.0**;
  adhan2 0.0.6 → **0.0.7** (includes upstream international-date-line fix;
  prayer goldens regenerated on 0.0.7 with an added Apia/Samoa date-line site;
  6184 cases). cosinekitty astronomy stays at 2.1.19 — the latest published
  Maven/JitPack release.

### M2. Golden dumpers live in-repo
- `tools/golden-dumper/{prayer,astro}` are committed standalone Gradle projects
  (donor replicas — see tools/golden-dumper/README.md). They were previously
  ephemeral under /tmp; fixture regeneration must never depend on machine state
  outside this repository.

## Pending reconciliations
- adhan2's internal solar model vs cosinekitty (astronomy session): sunrise/sunset
  may differ ~1–2 min between `prayer.times()` anchors and future
  `astronomy.sun.riseSet`. Unification decision deferred until astronomy is
  golden-locked.
- `SolarEphemeris.kt`, `SunMoonCalculator.kt`, `AstroEphemerisEngine.kt`
  (three donor sun-position codes) — reconcile during astronomy port;
  `AstroEphemerisEngine`/cosinekitty presumed authoritative.

## Regenerating goldens
Both dumpers are committed under `tools/golden-dumper/` as standalone Gradle
projects that replicate the donor's exact call path and dependency set
(see `tools/golden-dumper/README.md`). Regenerate with:

```
./gradlew -p tools/golden-dumper/prayer run --args="<repo-root>/engine/prayer/src/test/resources/fixtures/prayer_golden.json"
./gradlew -p tools/golden-dumper/astro   run --args="<repo-root>/engine/astronomy/src/test/resources/fixtures/astro_golden.json"
```

Fixtures are inputs only — tests treat them as immutable expectations.

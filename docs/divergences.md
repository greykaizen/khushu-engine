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

### Pending reconciliations
- adhan2's internal solar model vs cosinekitty (astronomy session): sunrise/sunset
  may differ ~1–2 min between `prayer.times()` anchors and future
  `astronomy.sun.riseSet`. Unification decision deferred until astronomy is
  golden-locked.
- `SolarEphemeris.kt`, `SunMoonCalculator.kt`, `AstroEphemerisEngine.kt`
  (three donor sun-position codes) — reconcile during astronomy port;
  `AstroEphemerisEngine`/cosinekitty presumed authoritative.

## Regenerating goldens
The dumper is a standalone Gradle project reproducing the donor's exact call
path (same library version, typed params). Location: `/tmp/opencode/prayer-golden-dumper`.
Regenerate with:

```
gradlew -p <dumper> run --args="<repo>/engine/prayer/src/test/resources/fixtures/prayer_golden.json"
```

Fixtures are inputs only — tests treat them as immutable expectations.

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

### D16. Dual sunset facts — resolved by labeling, not merging
- The long-pending adhan2-vs-cosinekitty sunrise/sunset divergence is RESOLVED
  as a product decision: both computations remain, each a distinct named fact —
  prayer times anchor on the **prayer-convention calculation** (adhan2 0.0.7);
  astronomy exposes the **astronomical ephemeris calculation** (cosinekitty
  2.1.19). Consumers pick explicitly; nothing silently overrides. Measured
  deltas across golden sites are ~0–2 minutes (model difference: Meeus-based
  solar equations vs Astronomy Engine's VSOP87-derivations).
- Altitude-band thresholds (blue [−6,−4), golden [−4,+6), twilights −6/−12/−18,
  horizon −0.833) are pinned in `AltitudeConventions` and labeled CONVENTIONS
  in every KDoc/audit.

### M3. Reference vs regression fixtures
- astro_golden.json now has TWO sections: `cases` = donor-derived REGRESSION
  lock (Osprey call path); `solarDayReference` = SELF-GENERATED REFERENCE
  change-detectors for v1.2 endpoints (solar day phases, solar midnight). Only
  the former proves donor-parity; correctness of the latter rests on almanac
  spot-checks (seasons ±5 min vs NASA/TimeAndDate 2026 values).

### D17. Fitr saʿ weight conventions — cited, not canonical
- Engine stores VOLUME (1 ṣāʿ/person) as the primary fact; kg conversions are
  labeled conventions: 2.175 kg (widely-published general estimate), 3.0 kg
  (common Hanafi practice), ~2.1 kg rice (approximation associated with Ibn
  ʿUthaymeen, via IslamQA #49793). No single figure is treated as universal.
- Monetary payment representation: Hanafi methodology permits cash value;
  Shafiʿi/Maliki/Hanbali positions commonly require food (IslamQA #109734).
  `PaymentMode` is representation only — the engine does not recommend.

### D18. Ushr nisab threshold — majority rule recorded
- Engine applies ushr to all harvests without a minimum (majority practice);
  the ≥5 wasq minimum position is NOT implemented. Recorded per IslamQA and
  SeekersGuidance ushr material. Mixed-irrigation methodology deferred to
  v1.3 design.

### D19. Camel schedule beyond 120 — classical decomposition implemented
- Explicit bands end at 120 (two hiqqah). Beyond: "for every forty a bint
  labun, for every fifty a hiqqah" (IslamQA #71267); combination minimizing
  total headcount preferred; non-decomposable excess flags
  `requiresScholarReview` instead of guessing. Grazing/trade-purpose
  distinctions remain caller policy (v1.3).

### D20. Mushaf layout engine — port provenance, license, and placement
- Glyph-atlas layout math (word measuring, median-fill page fitting,
  line-shrink fitting, RTL glyph placement, tune constants) ported from
  QuranApp's `AtlasAyahRasterizer.layoutWord`, `QuranTextMeasurer`
  (fitPageScale/fitLineShrink) and `TextDecorator` constants. QuranApp is
  GPLv3 family — compatible with the engine's GPLv3 LICENSE; no relicensing
  involved.
- Placement decision: the MATH lives in the engine
  (`com.khushu.engine.mushaf`, deterministic, content-free); all atlas and
  mushaf SPEC DATA (font metrics, glyph tables, page/line layouts, textures)
  lives in khushu-data-api (`inventory/atlas/`, `inventory/mushaf_layout/`)
  and enters the engine by value. The engine still ships zero Quran content.
- Donor golden cases (uthmani 2:255 placements) plus invariant property
  tests guard the port; donor tune constants kept verbatim.

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

### D21. Qibla antipode longitude normalization bug (found by v1.13 validation)
- **Donor→v1.10 engine**: `shadowVerification` computed the Kaaba's antipode
  as `IEEEremainder(lon + 180, 360) − 180`, producing **−320.17°** for the
  Kaaba (outside longitude range). The wrapped call path tolerated the
  out-of-range longitude silently — the events were still computed from
  trig functions that are periodic, masking the defect.
- **v1.13 engine**: antipode is `IEEEremainder(lon + 180, 360)` (already
  normalized to [−180, 180]). The fix was *forced* by the new
  `SubsolarAlignment.passagesOver` input validation (typed
  InvalidParameterException on out-of-range longitudes) — the validation
  caught a real latent bug in a locked module on its first full test run.

### D22. Hilal moon-age fabrication removed (D7 enforcement)
- **Donor→v1.12 engine**: `HilalEngine` fell back to `conjunction =
  sunset − 24h` when the conjunction search failed, fabricating
  `moonAgeHours = 24` — contradicting D7 ("uncomputable events stay
  explicit") and the MoonState no-fabrication contract.
- **v1.13 engine**: unresolvable conjunctions throw typed
  `NoResultException`. With a 31-day search window over a 29.53-day
  synodic cycle this is a can't-happen guard; the non-null `Int` field
  keeps the published API additive (nullable retyping parked for 2.0.0
  with the other breaking cleanups).

### D23. Solar-midnight was always the fallback — hour-angle unit bug (found by v1.14 Muwaqqit parity)
- **Donor→v1.13 engine**: `SolarDayPhases` computed the day's midnight via
  `searchHourAngle(..., hourAngleDeg = 180.0)` — but cosinekitty's hour
  angle parameter is in **HOURS [0, 24)**, not degrees. The call threw
  `IllegalArgumentException` every single time and the surrounding
  `runCatching` silently substituted the noon+12h fallback. Every
  `sun.phases().midnight` since the SolarDayPhases introduction was
  therefore noon+12h, not the true lower-meridian anti-transit.
- **v1.14 engine**: hour angle 12.0 (hours) — the true search runs; the
  noon+12h fallback remains only as the polar guard it was designed to be.
  All 30 `solarDayReference` golden rows were verified to have recorded the
  fallback (|row − (noon+12h)| < 100 ms for every row) and were regenerated;
  corrections range −10.5…+14.9 s — exactly the equation-of-time drift
  between noon+12h and the true anti-transit, confirming the fix is the
  real event. `sun.antiTransit` (new) validated against Muwaqqit's
  published Kohat solar-midnight to <2 s. The golden-dumper replica carried
  the same bug and was fixed identically.

## Regenerating goldens
Both dumpers are committed under `tools/golden-dumper/` as standalone Gradle
projects that replicate the donor's exact call path and dependency set
(see `tools/golden-dumper/README.md`). Regenerate with:

```
./gradlew -p tools/golden-dumper/prayer run --args="<repo-root>/engine/prayer/src/test/resources/fixtures/prayer_golden.json"
./gradlew -p tools/golden-dumper/astro   run --args="<repo-root>/engine/astronomy/src/test/resources/fixtures/astro_golden.json"
```

Fixtures are inputs only — tests treat them as immutable expectations.

# Golden dumpers

Standalone Gradle projects that regenerate the golden-master regression
fixtures. Each one replicates the **donor's** dependency set and call sequence
verbatim — they are donor replicas, not engine code. Never add engine modules
to them.

## Regenerate prayer goldens

Writes `engine/prayer/src/test/resources/fixtures/prayer_golden.json`
(6184 cases: 7 sites × 365 days × madhab × conventions × high-lat rules × offsets).

```
./gradlew -p tools/golden-dumper/prayer run --args="<repo-root>/engine/prayer/src/test/resources/fixtures/prayer_golden.json"
```

## Regenerate astronomy goldens

Writes `engine/astronomy/src/test/resources/fixtures/astro_golden.json`
(576 cases: 6 sites × monthly samples × two instants/day, replicating the donor
AR call path — `AstroEphemerisEngine` + `LunarOrientation` on cosinekitty 2.1.19).

```
./gradlew -p tools/golden-dumper/astro run --args="<repo-root>/engine/astronomy/src/test/resources/fixtures/astro_golden.json"
```

## Rules

- Fixture files are **immutable expectations** once committed; tests treat them as inputs only.
- If a regeneration is required (library upgrade, deliberate divergence), regenerate,
  review the diff carefully, and record why in `docs/divergences.md`.
- Both projects pin their library versions in `build.gradle.kts` — bump them only
  together with the corresponding engine module and a divergences entry.

## Regenerate hijri goldens

Writes `engine/calendar/src/test/resources/fixtures/hijri_golden.json`
(3655 cases: 2024–2025 daily × offsets −2..+2, replicating the donor's
UmmalquraCalendar call path).

```
./gradlew -p tools/golden-dumper/calendar run --args="<repo-root>/engine/calendar/src/test/resources/fixtures/hijri_golden.json"
```

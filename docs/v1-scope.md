# v1.0.0 Scope — explicit in/out

## IN (frozen at v1.0.0)

- **core**: validated geo primitives (`Latitude/Longitude/AltitudeMeters/Location`),
  dimensional units (`Degrees/Radians/Kilometers/ArcMinutes`)
- **astronomy**: sun/moon topocentric positions, rise/set, day length, solar event
  schedule (twilight tiers, golden/blue hours), monthly lunar track (+render path),
  moon phase state (incl. age/waxing), next-phase search, geocentric distance
  extremes, solar/lunar eclipse facts, hilal visibility reports + forecasts
- **prayer**: raw/adjusted timings for six prayers + sunset anchor; fiqh windows
  (Islamic/astronomical midnight, last-third, ishraq/Duha, zawaal); imsak;
  karahat windows; Tahajjud window; navigation sequences; occasions with
  consensus-safe categories; fasting & travel objective facts; full audit/
  provenance per result; 12 conventions + CUSTOM with recorded metadata
- **calendar**: Umm al-Qura hijri conversion (±2 offset), reverse conversion,
  month lengths, standard events, range queries, next occurrence, sacred months,
  optional fast-day rules
- **qibla**: bearing (Degrees) + great-circle distance (Kilometers)
- **zakat**: zakat al-mal with madhab rules and nisab conventions, fitrana,
  hawl anniversary, livestock schedules, ushr, rikaz
- **facade**: `KhushuEngine` capability tree incl. `day.summary` composite;
  opt-in `CachedEngine` multi-granularity memoization

## OUT of v1 (deliberate)

- Notifications/scheduling/audio — host-side (see docs/integration.md §1)
- Prayer check-marks, history, streaks — host-side persistence (§2)
- Observance/inference of obligation state (incl. menstruation) — host fiqh layer (§4)
- Jumu'ah jamaat time derivation — Friday + Dhuhr-anchor fact only; jamaat is local
- Observed/moonsighting calendar mode — tabular Umm al-Qura only in v1; a
  distinct model must be designed on paper before code
- AR rendering helpers (closest-point-on-path, horizon ring meshes) — presentation
- Quran/hadith/dua content, audio files — data domain, sibling project
- Clock injection / global state — deterministic explicit arguments only

## Quality gates satisfied at freeze

- Goldens: prayer 6184 · astronomy 576 · calendar 3655 — all green through CLI verify
- Property tests green; authoritative anchors verified (NASA/almanac/classical dates)
- `.api` binary surface committed and CI-enforced
- Zero Android/UI/filesystem dependencies under `engine/` (grep-audited)

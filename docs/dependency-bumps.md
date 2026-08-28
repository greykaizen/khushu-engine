# Dependency-bump runbook

All third-party computation libraries are pinned and golden-tested. A bump is
never a one-line change: behavior may shift, and the golden matrix is what
detects it. Follow this procedure for every bump.

## Current pins (verify before bumping)

| Library | Used by | Pinned | As of |
|---|---|---|---|
| `com.batoulapps.adhan:adhan2-jvm` | prayer, qibla | 0.0.7 (latest, Jun 2026) | v1.5.0 |
| `io.github.cosinekitty:astronomy` (JitPack) | astronomy | 2.1.19 (latest release) | v1.5.0 |
| `com.github.msarhan:ummalqura-calendar` | calendar | 2.0.2 (latest) | v1.5.0 |

## Procedure

1. **Bump the engine module** dependency version (and the matching dumper's
   `build.gradle.kts` — the dumpers replicate the library the engine uses).
2. **Regenerate the affected goldens** (exact commands in
   `tools/golden-dumper/README.md`):
   - adhan2 → `tools/golden-dumper/prayer`
   - cosinekitty → `tools/golden-dumper/astro`
   - ummalqura → `tools/golden-dumper/calendar`
3. **Diff before accepting.** Run the golden regression tests; inspect every
   changed value. Any numeric shift must be checked against an authoritative
   source (almanac / established implementation) before it is accepted.
4. **Record the divergence** in `docs/divergences.md`: old version → new
   version, which cases moved, by how much, and why the new values are right.
5. Full gate: `./gradlew clean test apiDump apiCheck` — API must remain
   additive; goldens green; then commit dumper bump + fixtures + divergences
   together.

## Rules

- Never bump a library without regenerating its golden suite in the same change.
- Never accept golden drift without an authoritative-source justification.
- Kotlin/toolchain bumps (`kotlin("jvm")` in the root build) follow the normal
  full test gate; no golden regeneration needed unless a library also moves.

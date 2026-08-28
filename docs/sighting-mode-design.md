# Design-on-Paper: Observed (Moonsighting) Calendar Mode

Status: DESIGN ONLY — deliberately unimplemented. House rule (same as
qada/excused ranges): sensitive calendar-fiqh domains get a reviewed design
before any code ships. Remaining gate: knowledgeable-reader review.

## Problem

The engine ships tabular Umm al-Qura only. Announced moon-sighting dates
diverge from it by ±1 day around Ramadan, Shawwal, Dhul-Hijjah (divergences
D11/D16). A host asking "what date did MY community actually observe?"
currently has two crude options:

1. `hijriOffsetDays` — a constant global shift; wrong whenever the divergence
   is month-specific or mid-year.
2. Nothing — silently show tabular dates as if they were observed ones.

Neither is "observed/moonsighting mode". The existing building blocks are:

| Already shipped | Where |
|---|---|
| `ObservedDateOverride` + `ObservanceContext` — caller-supplied observed dates injected into event occurrences (never mutate tabular math) | `calendar.EventRegistry` |
| `hilal.visibility(location, date, zoneId): HilalReport?` — Yallop grade, Odeh zone, Pakistan-PMD/MABIMS/Turkey-Diyanet/Wujudul-Hilal verdicts | `astronomy` |
| `hilal.forecast(...)` — per-day visibility run for a range | `astronomy` |
| `CalendarConfiguration.hijriOffsetDays` — static shift, already validated −2..+2 | `calendar` |

## Proposed semantics (facts + host-supplied announcements, no fiqh verdict)

The engine must never *decide* a sighting — committees do. The engine's role:

1. **Forecast**: for a given civil date + location, answer "is a sighting
   astronomically plausible tonight?" — already covered by `hilal.*` (no
   change needed).
2. **Announce**: host supplies authoritative month-start announcements as
   data; engine derives the consequences deterministically.

```kotlin
// Proposed — NOT yet implemented.
data class AnnouncedMonthStart(
    val hijriYear: Int,
    val hijriMonth: Int,
    val observedFirstDay: LocalDate,      // committee-announced 1st of the month
    val authority: String,               // free-form: "MoonsightingCommittee", local mosque name…
)

data class SightedCalendarParams(
    val announcements: List<AnnouncedMonthStart>,  // host-supplied, engine stays persistence-free
    val fallback: CalendarSystem = UMM_AL_QURA,    // months without an announcement stay tabular
)

// Pure derivations — no persistence, no network, no implied fiqh:
fun hijriSighted(date: LocalDate, params: SightedCalendarParams): HijriDate
fun eventsSighted(date: LocalDate, params: SightedCalendarParams): List<IslamicEvent>
```

Derivation rule (month m announced): days inside that month use the
announced anchor (`observedFirstDay` = day 1); days before it, and months
without announcements, fall back to the tabular calendar. Overlapping
announcements (committee said month 9 started 2 days off the month-8
announcement) are caller data — engine validates non-overlap/monotonicity
and throws `InvalidParameterException` on contradiction rather than picking
a winner.

## What the design deliberately excludes

- **Committee feeds/network** — AGENTS §10: no networking. Announcements
  enter by value; the host owns sourcing (API, user entry, local DB).
- **Persistence of announcements** — host-owned (see README §Observance
  logs pattern; same split as `stats.streak` input).
- **Per-madhab "was the sighting acceptable" verdicts** — the astronomy
  criteria (Yallop/Odeh/PMD/MABIMS) are *facts about plausibility*; turning
  them into "you may/may not break your fast" rulings is fiqh, not
  computation. Same posture as zakat `requiresScholarReview`.
- **Retroactive rewriting of history** — sightings only shift dates from the
  announcement forward within the announced month; past rendered output is
  never mutated.

## Interaction points (audit when implementation resumes)

- `ObservanceContext.occurrencesOn` already injects observed dates for
  *events*; a sighted mode must decide precedence when both an announcement
  and an override exist (proposed: explicit override wins — it's the more
  specific host statement).
- `islamicDateAt(instant, location, zoneId, config)` sunset-boundary math is
  anchor-relative and survives sighted mode unchanged (anchor shifts, not
  boundary rule).
- `CachedEngine` key must include announcement-set identity (hash of sorted
  announcements) — caching must never hide configuration that affects
  results (AGENTS §6).
- `month.summary` / `monthMatrix` must take the params object through the
  same seam without touching their current tabular defaults.

## Acceptance gate to resume

1. Knowledgeable-reader review of this document (does the
   announcement-anchor model match how local committees actually operate?).
2. At least one real-world announcement dataset captured as a fixture
   (e.g. Ramadan 1445 / 1446 announcements from a named committee).
3. Then implement behind the seam above; tabular path stays the default —
   zero-change upgrade for existing hosts.

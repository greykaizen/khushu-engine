# Design-on-Paper: Qada & Excused-Range Fiqh Helpers

Status: DESIGN ONLY — deliberately unimplemented. Per house rule (same as
observed calendars): sensitive fiqh domains get a reviewed design before code.

## Problem

Apps tracking prayer observance need to answer, after an excused period
(menses/lochia being the primary case; travel/sickness partially overlaps with
qasr rules already shipped):

> "Given logged excused ranges, which missed prayers must be made up (qada),
> and does the makeup carry only ghusl obligations or full prayer obligations?"

This differs by school and interacts with duration minimums — exactly why it
must not be hardcoded into generic prayer arithmetic.

## Proposed surface (pure functions over caller-supplied state)

```kotlin
data class ExcusedPeriod(
    val start: LocalDate,
    val end: LocalDate,            // inclusive
    val reason: ExcuseReason,      // MENSTRUATION, LOCHIA, ILLNESS, TRAVEL, UNKNOWN
)

enum class QadaSchool { HANAFI, SHAFII, MALIKI, HANBALI }   // reuse Madhab? separate enum — semantics differ

prayer.qadaReport(
    periods: List<ExcusedPeriod>,          // caller's logged ranges
    school: QadaSchool,
    calendar: CalendarConfiguration,       // hijri-day boundary checks (min-duration rules are hijri-based)
): QadaReport
```

## Known madhab differences to encode as DATA (provenance required)

| Question | Hanafi | Shafi'i | Maliki | Hanbali |
|---|---|---|---|---|
| Minimum menses duration before counting | ≥ 3 days | none fixed | none fixed | none fixed |
| Maximum duration (beyond which treated as istihada) | 10 days | 15 days | 15 days (most) | 15 days |
| Prayers during istihada | owed normally | owed normally | owed normally | owed normally |
| Makeup granularity | per missed fard | per missed fard | per missed fard | per missed fard |

**These table values MUST be re-verified against cited sources before any code
exists.** They are placeholders marking the SHAPE of the data, not rulings.

## Explicit non-goals

- No cycle prediction/estimation — biological modeling is out of scope forever
- No persistence — caller supplies logged ranges
- No inference of a woman's current obligation state — reporting over given
  history only
- The engine reports day classifications and counts; presenting them remains
  the host's responsibility, with the provenance string surfaced in UI

## Acceptance criteria before implementation is allowed

1. Each table cell carries a citation reviewed by a knowledgeable reader
2. Property tests over synthetic ranges (overlapping, adjacent, hijri-boundary-crossing)
3. Golden parity of existing modules unaffected

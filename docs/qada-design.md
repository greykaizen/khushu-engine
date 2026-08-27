# Design-on-Paper: Qada & Excused-Range Fiqh Helpers

Status: DESIGN ONLY — deliberately unimplemented. Per house rule (same as
observed calendars): sensitive fiqh domains get a reviewed design before code.
**Source pass 2026-08-28**: table below now carries collected citations
(§Source pass) — remaining gate is knowledgeable-reader review, not sourcing.

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

## Known madhab differences to encode as DATA (source-cited; review pending)

| Question | Hanafi | Shafi'i | Maliki | Hanbali |
|---|---|---|---|---|
| Minimum menses duration | 3 days (72h) | 1 day + night | none fixed | 1 day (+night) |
| Maximum duration (beyond = istihada) | 10 days | 15 days | 15 days (most) | 15 days |
| Minimum purity (tuhr) between cycles | 15 days | 15 days | verify | verify |
| Nifas maximum | 40 days | 60 days | 60 days (some: 70) | 40 days |
| Nifas minimum | none (any bleeding) | an instant | none fixed | none (any bleeding) |
| Prayers during istihada | owed normally | owed normally | owed normally | owed normally |
| Missed prayers during hayd/nifas | NOT made up | NOT made up | NOT made up | NOT made up |
| Missed fasts during hayd/nifas | made up | made up | made up | made up |
| Makeup granularity | per missed fard | per missed fard | per missed fard | per missed fard |

Note vs. earlier draft: "none fixed" for Shafi'i/Hanbali minimums was a
placeholder error — both schools DO carry minimums (1 day-and-night). Only
Maliki has no fixed minimum.

## Source pass (2026-08-28, web-collected — pending human review)

- **Hayd/Nifas prayers not made up; fasts made up** — consensus stated by
  Sayyed Sabiq, *Fiqh-us-Sunnah* (via fiqh.islamonline.net); IslamQA/Hanafi
  (seekersguidance-hanafi #107880: prayers "were not obligatory upon you in
  the first place"); Darul Iftaa #8301 (hardship rationale: five daily
  prayers vs. annual fasts); SeekersPath Q-ID0182.
- **Hanafi min 3 days / max 10** — IslamQA/Hanafi askmufti #45341 ("minimum
  … three days (72 hours) … maximum … ten days"); SeekersGuidance citing
  Kasani, *Bada'i al-Sana'i* (consensus on ten complete days).
- **Shafi'i min 1 day+night, max 15, min tuhr 15** — *Al-Tuhfa* matn verse
  via IslamQA/Shafi'i #33349 ("One day and night of bleeding is its minimum.
  Fifteen, though intermittent, is its maximum… Fifteen days is the minimum
  for purity"); fataawa.co.za citing *Umdat al-Salik*; SeekersGuidance
  (Shafi'i) brown-spotting answer.
- **Maliki no fixed minimum** — dorar.net fiqh encyclopedia §"Shortest
  duration of menstruation" (Malikis, Ibn al-Mundhir, Ibn Hazm, Ibn
  Taymiyyah…).
- **Hanbali min 1 day / max 15** — Ibn Taymiyyah attribution via
  fawaaids.com ("the position of 'Ata', al-Shafi'i, Ahmad, Abu Thawr").
- **Max 15 across Shafi'i/Maliki/Hanbali** — IslamQA/Hanafi Mathabah
  #133899.
- **Min tuhr 15 (Hanafi)** — Darul Iftaa Birmingham #136702. Maliki/Hanbali
  tuhr minimums: collect at implementation time.
- **Nifas max 40 (Hanafi/Hanbali)** — dorar.net fiqh encyclopedia §59
  (also Ibn 'Abd al-Barr, Permanent Committee verdict); IslamQA/Hanafi
  fatwacentre #178882; **max 60 (Shafi'i/Maliki)** — deen2u.com nifas Q&A,
  notrereligionislam.com #6053 (Maliki counts bleeding days only; some
  Malikis say 70); **no minimum** — ayolapp.uz Hanafi guide, *Al-Tuhfa*
  verse ("A moment is the minimum for childbed").
- **Istihada does not lift prayer** — Hanafi hayd primer (scribd/nur.nu:
  "Istihada does not prohibit these acts, but wudu must be repeated for each
  prayer").

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

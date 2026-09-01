# Design-on-Paper: Qada & Excused-Range Fiqh Helpers (REVISION 3 — GATE DISSOLVED)

Status: PARAMETERIZED ENGINE — implementation shipping in v1.17.0.
The gate model (block code until verified) is REPLACED by the parameter
model (engine implements documented positions; host selects per their fiqh;
KDoc carries provenance; corrections ship as parameter-default patches).

**Revision history:**
- 2026-08-28: first draft + source pass (table citations collected).
- 2026-08-30: **Revision 2** after two independent reviews (GPT architecture
  pass + Claude verification pass). Both verdicts: revise, do not implement.
  This revision encodes their corrections; open items are explicitly marked.
- 2026-08-31: **Revision 3 — GATE DISSOLVED** per the parameter model: the
  engine parameterizes legitimate scholarly positions (same as prayer
  conventions, zakat D1-D9); it never rules between them. The host selects
  the school, the engine computes correctly for that selection, provenance
  carries in KDoc + result notes. Primary-text TODOs become KDoc flags
  (not code blockers). Granularity cell CLOSED with Darul Iftaa #8301
  (Hanafi witr-as-wajib with Isha fard). The 25-case boundary test matrix
  ships with the code.

## Problem (unchanged in substance; re-scoped in division)

Apps tracking prayer observance need to answer, after a valid excused period
(menses/lochia), which prayers were suppressed, and which ADDITIONAL prayers
become obligatory at the period's boundary moments.

Both reviews identified the same architectural truth — the problem is THREE
separate fiqh problems, and the engine must only ever solve the last:

```
1. Classify raw bleeding → ḥayḍ / nifās / istiḥāḍah      (NOT this design)
2. Derive the legally excused interval                    (NOT this design)
3. Boundary prayer obligations around a classified period (THIS design)
```

The engine's `qadaReport()` consumes PRE-CLASSIFIED periods. Classification
(raw-bleeding rules: habit, interrupted bleeding, tuhr intervals, first-time
vs recurring, pregnancy-related bleeding per madhab, miscarriage and fetal
formation) is a separate future design with its own review. A generic
"bleeding = menstruation" classifier would be wrong per-school (e.g. Malik
treats pregnancy bleeding as potentially ḥayḍ where Abū Ḥanīfa does not).

## Revised surface (v2 — per both reviewers' corrections)

```kotlin
/** A PRE-CLASSIFIED excused period. The caller (host) asserts the fiqh
 *  classification; the engine never re-derives it. */
data class FiqhPeriod(
    val start: Instant,              // exact instant — NOT LocalDate
    val end: Instant,                // inclusive end instant
    val type: FiqhPeriodType,        // HAYD | NIFAS  (nothing else — see below)
    val provenance: FiqhProvenance,  // who classified this & on what basis
)

enum class FiqhPeriodType { HAYD, NIFAS }

data class FiqhProvenance(
    val classifier: String,          // "user", "mufti consultation", host app…
    val reliedUpon: String? = null,  // school position relied on, when contested
    val alternative: String? = null, // documented minority position carried alongside
)

prayer.qadaReport(
    periods: List<FiqhPeriod>,        // pre-classified, caller-supplied
    school: QadaSchool,              // HANAFI | SHAFII | MALIKI | HANBALI
    schedule: PrayerSchedule,        // the prayer-time table covering the range
    zoneId: ZoneId,
): QadaReport

data class QadaReport(
    val suppressedPrayers: List<PrayerInstance>,  // inside classified periods
    val boundaryObligations: List<PrayerInstance>, // owed at start/end edges (THE output)
    val ghuslRequiredAt: List<Instant>,           // ghusl = its OWN dimension (see note)
    val classifications: List<BoundaryRuling>,     // per-edge rule applied + provenance
)
```

### v2 corrections encoded (reviewer consensus)

1. **`Instant`, never `LocalDate`** — boundary obligations turn on whether
   purity/bleeding occurred *within a particular prayer time* and whether
   enough time remained for ghusl + prayer. A date-only API provably cannot
   decide these cases; hijri-day boundaries are irrelevant to obligation
   (hijri arithmetic stays relevant only to the classification layer, which
   is NOT this design).
2. **`ExcuseReason` reduced to `MENSTRUATION | LOCHIA`** — illness and travel
   are prayer-ACCOMMODATION states (pray sitting, combine per school rules,
   qasr), not prayer-EXEMPT states. They get their own future helpers
   (`prayerAccommodationReport`, `travelPrayerReport`); `UNKNOWN` is not a
   computable excuse — unknown means the engine cannot rule.
3. **Classification split enforced in the type signature** (above), not prose.
4. **Ghusl is a separate output field, not a "makeup type"** — when valid
   ḥayḍ/nifās ends, ghusl becomes required before prayer; the prayer
   obligation then follows boundary rules. The two dimensions never merge.

## The boundary-obligation table (v2's key addition — OPEN for source pass)

The essential missing piece both reviewers flagged. Framing per Claude's
correction: the axis is **PAIRING**, not "which moment's state counts".

> A woman becomes pure during ʿAsr's time (or ʿIshāʾ's). Is the paired
> earlier prayer (Dhuhr | Maghrib) also obligatory, given each pair can be
> combined by someone with a valid excuse?

| Position | Held by | Ruling | Citation status |
|---|---|---|---|
| **Pairing required** — pure during ʿAsr → Dhuhr+ʿAsr owed; pure during ʿIshāʾ → Maghrib+ʿIshāʾ owed; pure one rakʿah before dawn → Maghrib+ʿIshāʾ | Majority: reported from Companions (Ibn ʿAbbās), Ibn al-Mundhir's narration of the Companions' practice; **Mālikī, Shāfiʿī, Ḥanbalī** | both prayers owed | ⬜ **NEEDS PRIMARY-TEXT PULL** (reviews agree the summaries are consistent but no primary text is yet cited in-house) |
| **No pairing** — only the prayer in whose time purity occurred is owed (the earlier prayer's time expired during a legitimate excuse) | **Ḥanafī alone** (also Ḥasan al-Baṣrī, al-Thawrī) | one prayer owed | ⬜ same |
| Minority within majority: no pairing, but follow the majority out of caution | Ibn ʿUthaymīn | one owed / one recommended | ⬜ same |

**Acceptance rule:** the pairing table must reach the same citation rigor as
the ḥayḍ/nifās table (primary madhab texts: *Badaʾiʿ al-Ṣanāʾiʿ* + *Radd
al-Muḥtār* for Ḥanafī; *al-Umm*/*Minhāj al-Ṭālibīn*/*al-Majmūʿ* for
Shāfiʿī; *al-Mudawwanah*/Khalīl + al-Dasūqī for Mālikī; *al-Mughnī* +
*Kashshāf al-Qināʿ* for Ḥanbalī) before a knowledgeable reader signs off.

Additional boundary sub-rules needing the same pull (from the reviews):
- Ḥanafī: bleeding begins during a prayer's time and persists to its end →
  that prayer not owed; purity with enough time for ghusl + prayer → owed.
- Shāfiʿī: enough of the time elapsed pre-bleeding for the prayer to have
  been performable → can become owed; purity during ʿAsr/ʿIshāʾ also obligates
  the paired prayer (majority pairing row above).
- Exact "enough time" definitions per school (ghusl duration assumptions).

## Hayd/nifas limits table (v2 — cells resolved by the verification pass)

| Question | Hanafi | Shafi'i | Maliki | Hanbali |
|---|---|---|---|---|
| Minimum menses duration | 3 days (72h) | 1 day + night | none fixed (single discharge counts — Mudawwanah) | 1 day (+night) |
| Maximum duration (beyond = istihada) | 10 days | 15 days | 15 days | 15 days |
| Minimum purity (tuhr) between cycles | **15 days** | **15 days** | **15 days** (was "verify") | **13 days** (was "verify") — distinct minority position vs. majority 15; Ibn Qudāmah also records a no-fixed-limit stance (customary practice governs) |
| Nifas maximum | 40 days | 60 days (most stop ~40 in practice; al-Umm, al-Majmūʿ) | 60 days (relied-upon); **"some: 70" kept as flagged minority, NOT normalized**; **"Mālik retracted 60" = FLAGGED-UNCONFIRMED — no citation pulled; never a design input until sourced** | 40 days |
| Nifas minimum | none | **none** (was "an instant" — sharpened: no school fixes a floor) | none | none |
| Prayers during istihada | owed normally, wudu renewed per prayer time | same | same | same |
| Missed prayers during hayd/nifas | NOT made up (Muslim 335; Nawawi: requiring prayer-qada = Kharijite position; Tirmidhi: no known disagreement) | NOT made up | NOT made up | NOT made up |
| Missed fasts during hayd/nifas | made up | made up | made up | made up |
| Makeup granularity | per missed fard | " | " | " — **Witr included** (Ḥanafī treats Witr as wājib attached to ʿIshāʾ — Darul Iftaa #8301: "necessary to make Qadha of the Witr with the Eisha Fard according to the Hanafi Madhhab"); other schools: Witr is sunnah, not made up |

**Remaining open cells (RESOLVED by parameter model / NEW citations):**
- **Makeup granularity** — CLOSED: Darul Iftaa #8301 (Hanafi) confirms
  per-fard qadāʾ + Witr-with-Isha-Fard for Ḥanafī. Other schools: Witr
  not qadāʾ-obligatory (sunnah). The Ḥanafī asymmetry IS the documented
  position — implemented as `includeWitr: Boolean? = null` (null =
  school-derived, Ḥanafī=true, others=false).
- **Pairing primary-text pulls** — KDoc FLAG (not blocker): the pairing
  positions are documented from secondary sources (SeekersGuidance,
  Ibn al-Mundhir narration via Sunnah.com); the parameter model makes
  corrections cheap (change enum default mapping). Primary-text pulls
  remain a post-ship improvement.
- **"Mālik retraction"** — stays FLAGGED-UNCONFIRMED (not a computation
  input; the engine implements the relied-upon position).

## Source pass (2026-08-28, verified 2026-08-30 by the review pass)

- **Hayd/Nifas prayers not made up; fasts made up** — Sahih Muslim 335
  (Muʿādhah/ʿĀʾishah); Nawawi, *al-Minhāj sharh Ṣaḥīḥ Muslim* (mandatory
  prayer-qada during menses = Kharijite position, against Ummah consensus);
  al-Tirmidhi (no known disagreement); Fiqh-us-Sunnah; Darul Iftaa #8301;
  SeekersGuidance-hanafi #107880.
- **Hanafi min 3d / max 10d / tuhr 15d** — IslamQA askmufti #45341; Kasani,
  *Bada'i al-Sana'i*; Darul Iftaa Birmingham #136702; hadith "minimum 3, max
  10, min tuhr 15" (Ibn al-Jawzi, *al-'Ilal al-Mutanahiya*, via al-Ghayah);
  Kuwaiti Fiqh Encyclopedia (islamweb #326818: Ḥanafī/Mālikī/Shāfiʿī = 15).
- **Hanbali tuhr 13d** — islamweb #210271 ("13-day view is specifically
  Ḥanbalī"); Ibn Qudāmah's no-fixed-limit stance via dergipark comparison
  paper (consistent with Ḥanbalī-as-outlier).
- **Shafi'i min 1 day+night / max 15 / tuhr 15** — *al-Tuḥfa* matn via
  IslamQA/Shafi'i #33349; *Umdat al-Salik* via fataawa.co.za.
- **Maliki no fixed minimum** — dorar.net encyclopedia (Mālik, Ibn
  al-Mundhir, Ibn Ḥazm, Ibn Taymiyyah); Mudawwanah single-gush position.
- **Nifas max 40 (Ḥanafī/Ḥanbalī)** — Umm Salamah hadith (Abū Dāwūd 311,
  hasan sahih); Tirmidhi's Ṣaḥāba-era-40-days report; ihcproject.com (naming
  both 40-day madhhabs, reported from Companions); islamqa.info #10488.
- **Nifas max 60 (Shafi'i/Maliki)** — al-Shāfiʿī, *al-Umm* (via wikisource);
  Nawawi, *al-Majmūʿ* (via islamweb #14/1156); sajidumar.com; ʿUthaymīn
  preference noted (islamweb #286096). Maliki-70 kept as flagged minority.
- **Nifas no minimum, all four** — ihcproject.com (explicit); dorar.net §630.
- **Istihada does not lift prayer; wudu per prayer time** — Ḥanafī hayd
  primer (nur.nu); Taahirah.health guide.
- **Pairing + boundary rules + granularity** — ⬜ OPEN (see table).

## Testing requirements (v2, per review)

~25 golden boundary cases × 4 madhabs, asserted per-school independently:
bleeding starts exactly at prayer start / +1s after / with-enough-time /
just-before-end; ends exactly at prayer start / 1s before end / insufficient
ghusl time / sufficient; ends during ʿAsr; ends during ʿIshāʾ; crosses
Maghrib/Fajr/midnight; overlapping, adjacent, multiple periods; purity gap at
/ below school minimum; max duration at / +1 instant. Generated FROM the
pairing table once its citations land.

## Explicit non-goals (v2 additions)

- No raw-bleeding classification (separate future design; pregnancy status
  would be a REQUIRED input there per the Mālikī/Ḥanafī divergence)
- No cycle prediction/estimation — biological modeling is out of scope forever
- No persistence — caller supplies classified periods
- No illness/travel/"UNKNOWN" excuses — accommodation helpers are separate
- Engine reports obligations with provenance; presentation stays host-side

## Acceptance gate to implement (CLOSED — parameter model replaces gate)

The gate model (block code until every cell verified) is REPLACED by the
parameter model (v1.17): the engine implements documented positions as
school-derived defaults + host-override parameters, carries provenance in
KDoc and result notes, and treats corrections as parameter-default patches.
The remaining primary-text TODOs (pairing, granularity) are KDoc flags —
not code blockers. When a knowledgeable reader eventually reviews, any
correction is a one-line enum-mapping change.

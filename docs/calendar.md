# Calendar Module — Deep Dive

Conventions, contracts, and extension points for `engine/calendar`.

## Calendar systems & provenance

The engine ships the **tabular Umm al-Qura** calendar (`CalendarSystem.UMM_AL_QURA`).
Every result is reproducible; provenance is always:

```
system = UMM_AL_QURA · offset = hijriOffsetDays · observedOverride = none|applied
```

Tabular dates can differ by one day from locally announced moon sightings —
especially Ramadan, Shawwal, Dhul-Hijjah. The engine never implies its dates are
the religiously observed ones:

- **calculated date** — what this module returns
- **observed date** — host-supplied via `ObservedDateOverride`, applied ONLY
  through an explicit `ObservanceContext`

Overrides never mutate the tabular math. They suppress the calculated occurrence
and inject one at the observed civil date.

## Islamic-day boundary (sunset semantics)

A tabular conversion answers "hijri date of civil date D". The fiqh question —
"what hijri date is it RIGHT NOW?" — differs, because the Islamic day begins at
maghrib, not midnight:

```kotlin
engine.calendar.date.islamicDateAt(instant, location, zoneId, config)
// after local sunset of civil date D → next tabular day (D+1)
// before local sunset              → tabular day D
```

Tested around the sunset boundary in facade tests.

## Exactly-one-Hijri rule

```kotlin
CalendarConfiguration(primary = HIJRI, secondary = GREGORIAN)   // ✓
CalendarConfiguration(primary = GREGORIAN)                      // throws
```

The civil side renders region-based civil calendars only — the calendars
people live by day-to-day (Gregorian, Persian, Śaka, Bangla, Coptic,
Ethiopian, Japanese, Minguo, Thai Buddhist); no liturgical/religious systems
(Hebrew, Hindu panchang, Church calendars as such). `CalendarSystem` is a
strategy enum so future Islamic systems won't rewrite the API.

## Regional civil calendars

`CalendarConfiguration.civilCalendar: CivilCalendarType` picks the civil
system every `Side.GREGORIAN` line renders; the Hijri side is always Umm
al-Qura and unaffected. Rendering flows through one seam — `DualDates` —
shared by `calendar.date.*`, `monthMatrix`, and `month.summary`, so a
regional choice appears everywhere consistently.

| System | Implementation | Provenance (anchor tests carry the citation) |
|---|---|---|
| `GREGORIAN` | identity (`java.time.LocalDate`) | ISO-8601 |
| `PERSIAN` | engine-owned jalaali-js / Borkowski 33-year-cycle algorithm | official 1354–1419 SH correspondence table (Wikipedia, Solar Hijri), incl. leap marks; range −61..3177 SH |
| `INDIAN_NATIONAL` | engine-owned fixed month-start table | Calendar Reform Committee 1957 (1 Chaitra 1879 SE = 22 Mar 1957); leap ⇔ Śaka+78 Gregorian-leap |
| `BANGLA_BANGLADESH` | engine-owned 2019-present revision table | Boishakh 1 = 14 Apr; 6×31 + 5×30 + Falgun 29/30 (30 when the year spans 29 Feb) |
| `COPTIC` / `ETHIOPIAN` | engine-owned Alexandrian arithmetic (12×30 + little month 5/6; leap ≡ 3 mod 4, Julian-style) | new-year anchors pinned to published dates (Neyrouz/Enkutatash Sep 11/12) |
| `JAPANESE` / `MINGUO` / `THAI_BUDDHIST` | exact `java.time.chrono` adapters | JDK era tables |

Conversions are exact in both directions except `JAPANESE` reverse
(year-of-era is ambiguous across eras — throws by design). Round-trip and
anchor tests pin every system; out-of-range Persian years throw the typed
`InvalidParameterException`.

## Event registry

Three concepts, never conflated:

| Type | Meaning |
|---|---|
| `EventDefinition` | recurring hijri rule (id/month/day/category/source/confidence) |
| `EventOccurrence` | a definition resolved onto a concrete civil date |
| `ObservedDateOverride` | community observation, applied via explicit context |

```kotlin
val registry = engine.calendar.events(config)
registry.registerPack(jsonFromAsset)     // our schema
registry.registerAladhan(jsonFetchedByHost) // Aladhan adapter — hosts fetch, engine parses
registry.occurrencesOn(LocalDate.now())
registry.occurrencesOn(date, ObservanceContext(ObservedDateOverride("eid_al_fitr", 1447, observed)))
registry.upcoming("ramadan_begins", LocalDate.now(), count = 3)
```

Schema rules: ids stable and unique (duplicate registration is idempotent);
malformed entries throw `EventPackException` naming the offending id;
categories describe date-type only (`FASTING/EID/HAJJ/HISTORICAL/MONTH_START/OTHER`)
— theological content belongs to the data project.

Built-in pack export (canonical file lives in khushu-quran-data):
`EventRegistry.builtInPackJson()`.

## Lunar month view

`LunarCalendarView.monthView(hijriYear, hijriMonth, location, zoneId)` returns
one fact per civil evening (sampled at local sunset):

`hijriDay · civilDate · eveningInstant · phaseAngleDeg · illuminationFraction ·
brightLimbAngleDeg (χ−q rotation) · altitudeDeg · azimuthDeg · aboveHorizon`

The host supplies any moon asset and rotates/clips using these numbers.
Composed from astronomy's public facts — no duplicated ephemeris.

## Qadr convention

Seek-nights are marked on the civil day whose tabular hijri date is the odd
Ramadan night (21/23/25/27/29); that night *begins at the preceding maghrib*.
The engine never identifies one night as Laylat al-Qadr.

## Fast-day provenance

Each `FastRule` carries its citation (`FastRule.provenance`). Shawwal Six is
marked as candidate days 2–7; the six days are distributable across the month —
prayer module owns actual fast boundaries (Fajr/Maghrib).

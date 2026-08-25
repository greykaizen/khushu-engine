# Host Integration Guide

How to build app features on top of the engine without polluting it.
The engine computes **facts**; scheduling, persistence, audio and observance
state belong to your host application. Every example below is pure Kotlin and
works on JVM/Android unchanged unless marked otherwise.

---

## Setup

```kotlin
val engine = KhushuEngine()
// Optional: memoization for repeat queries (calendar UIs, home screens).
val cached = CachedEngine(engine)
```

Configuration is an immutable value object — pass it around, never mutate it:

```kotlin
val config = PrayerConfiguration(
    madhab = Madhab.HANAFI,
    convention = Convention.KARACHI,
    highLatitudeRule = HighLatitudeRule.SEVENTH_OF_NIGHT,
    offsets = PrayerOffsets(fajr = -2, maghrib = 3),
)
```

---

## 1. Prayer notifications & pre-notifications

The engine gives you exact instants; you derive triggers and schedule them with
platform tools (WorkManager / AlarmManager on Android).

```kotlin
val today = engine.prayer.times(location, LocalDate.now(zone), config)

data class NotificationPlan(val label: String, val triggerAt: Instant)

fun plans(preAlertMinutes: Long = 10): List<NotificationPlan> =
    listOfNotNull(
        today.fajr.adjusted,
        today.dhuhrEnters,
        today.asr.adjusted,
        today.maghrib.adjusted,
        today.isha.adjusted,
    ).map { at ->
        NotificationPlan("prayer", triggerAt = at.minusSeconds(preAlertMinutes * 60))
    }

// Android-side (sketch): schedule each plan with AlarmManager;
// play system sound or bundled adhan when it fires.
```

Adhan audio selection (system sound / bundled adhan / custom file) is a
notification-channel concern of the host — the engine never touches audio.

## 2. Marking prayers done, history, streaks

Persist check-marks in your app database keyed by a stable identifier:

```kotlin
data class PrayerOccurrenceId(val date: LocalDate, val kind: PrayerStatus.Prayer) {
    override fun toString() = "$date|$kind"
}

// Strict top-to-bottom marking: only allow checking `kind` if all obligatory
// prayers before it are already checked for that date.
val order = listOf(FAJR, SUNRISE /*boundary, optional*/, DHUHR, ASR, MAGHRIB, ISHA)
fun canMark(done: Set<PrayerOccurrenceId>, date: LocalDate, kind: PrayerStatus.Prayer): Boolean {
    val idx = order.indexOf(kind)
    return order.take(idx).all { prev ->
        prev == SUNRISE || done.contains(PrayerOccurrenceId(date, prev))
    }
}
```

For non-strict mode simply skip the `canMark` gate. Streaks/history are plain
queries over your persistence layer — nothing engine-related.

## 3. Fasting mode

```kotlin
val f = engine.prayer.windows.fastingFacts(location, date, imsakMinutesBeforeFajr = 10, config)
// f.imsak      — preparatory stop-eating boundary (convention-dependent margin)
// f.fastStart  = Fajr
// f.fastEnd    = Maghrib
```

Imsak is NOT silently Fajr — it is Fajr minus your configured margin, because
the margin itself is a documented convention that varies by community.

## 4. Travel mode (qasr) — facts only, times never change

```kotlin
val tf = engine.prayer.windows.travelFacts(
    location, date,
    travelledDistanceKm = 120.0,
    // distanceThresholdKm: supply your own threshold or omit for the
    // 48-mile (~77.25 km) majority convention preset.
)
if (tf.qasrEligibleByDistance == true) {
    // Dhuhr shortens to 2 rak'ah and may be joined with Asr AT tf.dhuhrJoinsAsrAt
    // Maghrib stays full; Isha shortens and may be joined at tf.maghribJoinsIshaAt
}
```

Thresholds are jurisprudential conventions with real madhab differences —
configure explicitly rather than assuming one universal number.

**Menstruation/observance state:** intentionally NOT modelled in the engine.
No library can (or should) infer a woman's religious obligation state — that is
a documented fiqh/observance layer of your application. The engine supplies the
unchanged objective times; your observance layer decides what to display.

## 5. Calendar scrolling with month prefetch

```kotlin
class MonthViewModel(private val cached: CachedEngine) {
    fun onMonthVisible(month: YearMonth, location: Location) {
        val days = cached.prayerMonth(location, month)   // computed once, reused
        // render rows from `days`
        cached.prayerMonth(location, month.plusMonths(1)) // warm next month
    }
}
```

Day-level and month-level caches share keys per configuration hash, so a day
pinned from a month view never recomputes.

## 6. Compact "previous / current / next" reveals

```kotlin
val seq = engine.prayer.navigationSequence(location, Instant.now(), zone, before = 1, after = 2)
// [prev, current, next, next+1] — crosses midnight transparently:
// late night returns [... ISHA(today), FAJR(tomorrow), SUNRISE(tomorrow)]
```

## 7. Whole-day home screen

One call, every fact, shared internal computation:

```kotlin
val summary = engine.day.summary(location, LocalDate.now(), zone, config, calendarParams)
summary.hijri.label            // "15 Shawwal 1447 AH"
summary.events                 // Islamic events falling today
summary.fastRules              // optional fast rules observed today
summary.prayerTimes.asr.raw    // astronomical fact
summary.prayerTimes.asr.adjusted // fact + configured offset
summary.dayLength              // e.g. PT9H43M in Tromsø winter
summary.moonAtNoon.phaseName   // moon card without extra computation
```

## 8. Provenance & diagnostics

Every result explains itself — invaluable when mosques disagree:

```kotlin
val r = engine.prayer.times(london, LocalDate.of(2025, 12, 21), config)
println(r.audit.conventionInfo?.authority)          // "Muslim World League"
println(r.audit.highLatitudeResolution)             // RESOLVED_BY_MIDDLE_OF_NIGHT
r.audit.warnings.forEach(::println)                 // polar/NOAA fallback notes
println(r.audit.offsetsApplied.maghrib)             // +3 (your mosque adjustment)
```

Raw vs adjusted exists precisely so a +5 minute mosque adjustment can never
overwrite the astronomical fact.

## 9. Qibla-by-sun moments

Twice a year the sun stands over the Kaaba — facing it means facing qibla:

```kotlin
engine.qibla.solarAlignmentInstants(2026).forEach { instant ->
    // schedule a local notification ~10 minutes before `instant`
}
```

## What the engine deliberately does NOT do

| Feature | Where it belongs | How |
|---|---|---|
| Scheduling/alarm firing | Host | derive `triggerAt` from instants |
| Adhan/audio playback | Host | notification channels / media player |
| Mark-done, history, streaks | Host DB | `PrayerOccurrenceId` stable keys above |
| Observance state (incl. menstruation) | Host fiqh/observance layer | engine supplies unchanged times |
| Jumu'ah jamaat time | Host config | Friday fact = Dhuhr anchor; local jamaat differs |
| Display formatting/rounding | Host UI | format raw/adjusted instants as you wish |

---

## Dependency setup for consumers

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // transitive: cosinekitty astronomy (via :engine:astronomy)
}
dependencies {
    implementation("com.khushu:engine-facade:1.0.0") // pulls all engine modules transitively
}
```

Reference consumer project: `~/AndroidStudioProjects/khushu-engine-consumer-test`
(validates the published artifact outside this repository).

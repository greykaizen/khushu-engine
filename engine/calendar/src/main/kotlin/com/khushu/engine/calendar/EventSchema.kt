package com.khushu.engine.calendar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Event-pack schema. Packs describe DATES and FACTS; textual/theological
 * content belongs to the data project, never here.
 */
enum class EventCategory { FASTING, EID, HAJJ, HISTORICAL, MONTH_START, OTHER }
enum class Recurrence { ANNUAL }
enum class Confidence { ESTABLISHED, COMPUTATIONAL, COMMUNITY }

@Serializable
data class EventDefinition(
    val id: String,
    val title: String,
    val hijriMonth: Int,
    val hijriDay: Int,
    val category: EventCategory = EventCategory.OTHER,
    val recurrence: Recurrence = Recurrence.ANNUAL,
    /** Empty when the day does not exist in shorter years (e.g. 30th). */
    val source: String = "",
    val sourceVersion: String = "",
    val confidence: Confidence = Confidence.COMMUNITY,
)

/** A definition resolved onto a concrete civil date. */
data class EventOccurrence(
    val definition: EventDefinition,
    val civilDate: LocalDate,
)

/**
 * "My community observed this on this date." Overrides NEVER mutate the
 * tabular calendar — they annotate occurrences and apply only through an
 * explicitly selected [ObservanceContext].
 */
data class ObservedDateOverride(
    val definitionId: String,
    val hijriYear: Int,
    val observedCivilDate: LocalDate,
)

class ObservanceContext(vararg overrides: ObservedDateOverride) {
    val byKey: Map<Pair<String, Int>, LocalDate> =
        overrides.associate { it.definitionId to it.hijriYear to it.observedCivilDate }

    fun observedDate(definitionId: String, hijriYear: Int): LocalDate? =
        byKey[definitionId to hijriYear]
}

/** Thrown for malformed packs — always names the offending entry. */
class EventPackException(message: String) : IllegalArgumentException(message)

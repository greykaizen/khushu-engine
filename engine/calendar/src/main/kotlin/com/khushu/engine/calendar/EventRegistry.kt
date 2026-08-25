package com.khushu.engine.calendar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Event registry: built-in core definitions + any number of registered packs
 * (our schema or Aladhan's holiday shape), merged into per-date occurrences.
 * Registration is additive and idempotent by definition id.
 */
class EventRegistry(private val offsetDays: Int = 0) {

    private val definitions = LinkedHashMap<String, EventDefinition>(
        HijriCalendar.builtInDefinitions().associateBy { it.id }.mapValues { it.value },
    )

    val all: List<EventDefinition> get() = definitions.values.toList()

    /** Register a pack in the engine's own schema. Returns count of newly added. */
    fun registerPack(json: String): Int {
        val pack = EventPackParser.parse(json)
        var added = 0
        for (def in pack) if (definitions.put(def.id, def) == null) added++
        return added
    }

    /** Register a pack from Aladhan's Islamic-Calendar API response shape. */
    fun registerAladhan(json: String): Int {
        val pack = AladhanAdapter.parse(json)
        var added = 0
        for (def in pack) if (definitions.put(def.id, def) == null) added++
        return added
    }

    /**
     * Occurrences falling on [civilDate]. Purely calculated — pass an
     * [observance] context to substitute observed dates for specific ids.
     */
    fun occurrencesOn(
        civilDate: LocalDate,
        observance: ObservanceContext? = null,
    ): List<EventOccurrence> {
        val h = HijriCalendar.hijri(civilDate, offsetDays)
        val result = mutableListOf<EventOccurrence>()
        val overriddenHere = mutableSetOf<String>()
        for (def in definitions.values) {
            val observed = observance?.observedDate(def.id, h.year)
            if (observed != null) {
                // Overridden for this definition+year: calculated date suppressed;
                // the occurrence is injected at the observed civil date instead.
                overriddenHere += def.id
                if (observed == civilDate) result += EventOccurrence(def, civilDate)
                continue
            }
            if (def.hijriMonth == h.month && def.hijriDay == h.day) {
                result += EventOccurrence(def, civilDate)
            }
        }
        // An override may land on a civil date whose OWN tabular hijri day does
        // not match the definition (e.g. Eid observed one day later) — inject it.
        if (observance != null) {
            for (entry in observance.byKey.entries) {
                val defId = entry.key.first
                if (entry.value == civilDate && defId !in overriddenHere) {
                    definitions[defId]?.let { result += EventOccurrence(it, civilDate) }
                    overriddenHere += defId
                }
            }
        }
        return result.sortedBy { it.definition.title }
    }

    fun occurrencesInRange(range: ClosedRange<LocalDate>, observance: ObservanceContext? = null): List<EventOccurrence> =
        generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.endInclusive) }
            .flatMap { occurrencesOn(it, observance) }
            .toList()

    /** Next occurrence dates of a definition id at or after [after]. */
    fun upcoming(definitionId: String, after: LocalDate, count: Int = 1): List<EventOccurrence> {
        require(count >= 1)
        val def = definitions[definitionId] ?: throw IllegalArgumentException("unknown event id $definitionId")
        val out = mutableListOf<EventOccurrence>()
        var probeYear = HijriCalendar.hijri(after, offsetDays).year
        while (out.size < count && probeYear < after.year + 5) {
            runCatching { HijriCalendar.hijriToGregorian(probeYear, def.hijriMonth, def.hijriDay, offsetDays) }
                .getOrNull()
                ?.takeIf { !it.isBefore(after) }
                ?.let { out += EventOccurrence(def, it) }
            probeYear++
        }
        return out
    }

    companion object {
        /** The engine's built-in core pack as canonical JSON (see khushu-quran-data). */
        fun builtInPackJson(): String {
            val defs = HijriCalendar.builtInDefinitions()
            val entries = defs.joinToString(",\n") { d ->
                """{"id":"${d.id}","title":"${d.title.replace("\"", "\\\"")}",""" +
                    """"hijriMonth":${d.hijriMonth},"hijriDay":${d.hijriDay},""" +
                    """"category":"${d.category.name}","recurrence":"ANNUAL",""" +
                    """"source":"khushu-engine core","confidence":"ESTABLISHED"}"""
            }
            return """{"pack":"khushu-core-islamic-events","events":[$entries]}"""
        }
    }
}

internal object EventPackParser {

    @Serializable
    private data class RawDef(
        val id: String,
        val title: String,
        val hijriMonth: Int,
        val hijriDay: Int,
        val category: String = "OTHER",
        val recurrence: String = "ANNUAL",
        val source: String = "",
        val sourceVersion: String = "",
        val confidence: String = "COMMUNITY",
    )

    @Serializable
    private data class Pack(val events: List<RawDef>, val pack: String? = null)

    fun parse(json: String): List<EventDefinition> {
        val pack = Json { ignoreUnknownKeys = true }.decodeFromString<Pack>(json)
        return pack.events.map { raw ->
            EventDefinition(
                id = raw.id,
                title = raw.title,
                hijriMonth = requireMonth(raw.hijriMonth, raw.id),
                hijriDay = requireDay(raw.hijriDay, raw.id),
                category = parseEnum(EventCategory.OTHER, raw.category, raw.id),
                recurrence = when (raw.recurrence.uppercase()) {
                    "ANNUAL" -> Recurrence.ANNUAL
                    else -> throw EventPackException("${raw.id}: unsupported recurrence '${raw.recurrence}'")
                },
                source = raw.source,
                sourceVersion = raw.sourceVersion,
                confidence = parseEnum(Confidence.COMMUNITY, raw.confidence, raw.id),
            )
        }
    }

    fun <T : Enum<T>> parseEnum(fallback: T, name: String, id: String): T =
        try {
            java.lang.Enum.valueOf(fallback.declaringJavaClass, name.uppercase())
        } catch (e: IllegalArgumentException) {
            throw EventPackException("$id: unknown enum value '$name'")
        }

    private fun requireMonth(m: Int, id: String): Int =
        m.takeIf { it in 1..12 } ?: throw EventPackException("$id: hijriMonth $m out of range")

    private fun requireDay(d: Int, id: String): Int =
        d.takeIf { it in 1..30 } ?: throw EventPackException("$id: hijriDay $d out of range")
}

/**
 * Adapter for Aladhan's Islamic-Calendar API response shape — kept OUT of the
 * core domain: hosts fetch; this only reshapes their JSON into definitions.
 *
 * Accepts `data[]` entries carrying `hijri{day,month{number}}` + `holidays[]`
 * (or a top-level array of such entries).
 */
object AladhanAdapter {

    @Serializable
    private data class HijriMeta(val day: String = "", val month: AladhanMonth = AladhanMonth())
    @Serializable
    private data class AladhanMonth(val number: Int = 0)
    @Serializable
    private data class Entry(val hijri: HijriMeta = HijriMeta(), val holidays: List<String> = emptyList())
    @Serializable
    private data class Response(val data: List<Entry> = emptyList())

    private val knownCategories = mapOf(
        "eid" to EventCategory.EID,
        "ashura" to EventCategory.FASTING,
        "arafah" to EventCategory.HAJJ,
        "arafa" to EventCategory.HAJJ,
        "ramadan" to EventCategory.FASTING,
    )

    fun parse(json: String): List<EventDefinition> {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<Response>(json)
        val out = LinkedHashMap<String, EventDefinition>()
        for ((index, entry) in response.data.withIndex()) {
            val month = entry.hijri.month.number
            val day = entry.hijri.day.trim().toIntOrNull() ?: continue
            for (holiday in entry.holidays) {
                val slug = holiday.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                if (slug.isEmpty()) continue
                val category = knownCategories.entries.firstOrNull { slug.contains(it.key) }?.value ?: EventCategory.OTHER
                out.putIfAbsent(
                    "aladhan_$slug",
                    EventDefinition(
                        id = "aladhan_$slug",
                        title = holiday,
                        hijriMonth = month,
                        hijriDay = day,
                        category = category,
                        source = "aladhan.com Islamic Calendar API",
                        confidence = Confidence.COMMUNITY,
                    ),
                ) ?: continue
            }
        }
        return out.values.toList()
    }
}

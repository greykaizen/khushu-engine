package com.khushu.engine.core.error

/**
 * Base for every failure caused by an argument violating a documented API
 * contract. Extends [IllegalArgumentException] so pre-existing catch sites
 * keep working while hosts can now catch this type (or a concrete subclass
 * with structured fields) for precise "what went wrong" handling.
 */
open class KhushuInputFailure(message: String) : IllegalArgumentException(message)

/**
 * Base for failures where the computation itself legitimately produced no
 * answer (a date that does not exist, an event that never occurs inside the
 * search window) and for failures of the underlying astronomy/calendar
 * libraries. Extends [IllegalStateException] for the same compatibility
 * reason as [KhushuInputFailure].
 */
open class KhushuComputationFailure(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * A single named parameter violated the API contract.
 *
 * Message format is always
 * `khushu: invalid <parameter> = <value> (<constraint>)`, so logs are
 * greppable even without catching the type.
 */
class InvalidParameterException(
    val parameter: String,
    val value: String,
    val constraint: String,
) : KhushuInputFailure("khushu: invalid $parameter = $value ($constraint)")

/**
 * The requested fact does not exist: a hijri day outside its month's length,
 * the next occurrence of a date beyond the search window, a rise/set search
 * over an empty window, and so on.
 */
class NoResultException(
    val detail: String,
) : KhushuComputationFailure("khushu: no result — $detail")

/**
 * The requested hijri date does not exist — e.g. day 30 of a 29-day month.
 */
class HijriDayDoesNotExistException(
    val hijriYear: Int,
    val hijriMonth: Int,
    val hijriDay: Int,
    val offsetDays: Int,
) : KhushuInputFailure(
    "khushu: invalid hijriDate = $hijriDay-$hijriMonth-$hijriYear AH " +
        "(no civil date maps to this day with offset $offsetDays; the month may have only 29 days)",
)

/**
 * An underlying astronomy/calendar library (adhan2, cosinekitty,
 * ummalqura-calendar) rejected the input or failed. The original exception is
 * preserved as [cause] for diagnostics; [detail] names the operation.
 */
class UpstreamComputationException(
    val detail: String,
    cause: Throwable,
) : KhushuComputationFailure("khushu: upstream computation failed — $detail", cause)

/**
 * Drop-in replacement for [require] that throws structured
 * [KhushuInputFailure] subtypes instead of bare [IllegalArgumentException].
 */
inline fun validate(condition: Boolean, failure: () -> KhushuInputFailure) {
    if (!condition) throw failure()
}

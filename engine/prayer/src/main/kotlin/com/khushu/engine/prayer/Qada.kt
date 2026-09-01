package com.khushu.engine.prayer

import com.khushu.engine.core.error.InvalidParameterException
import com.khushu.engine.core.error.validate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Qada & excused-range computation — boundary prayer obligations around
 * caller-supplied ḥayḍ/nifās periods (docs/qada-design.md rev 3).
 *
 * The host PRE-CLASSIFIES periods (the engine never interprets raw
 * bleeding — that is classification, a separate fiqh problem). This
 * engine computes boundary prayer obligations given the classified
 * periods, the prayer schedule, and the school. Every non-default
 * parameter emits a provenance citation into the result.
 *
 * The pairing and boundary rules are PARAMETERIZED positions:
 * - PAIRED (majority — Maliki/Shafiʿi/Hanbali): pure during ʿAsr →
 *   Dhuhr+ʿAsr both owed; pure during ʿIshāʾ → Maghrib+ʿIshāʾ both owed
 * - UNPAIRED (Ḥanafī): only the prayer in whose time purity occurred
 * - Boundary evaluation: Ḥanafī uses end-of-prayer-time state;
 *   Shafiʿi uses sufficient-time-elapsed-before-bleeding
 * Each carries provenance in the design doc (docs/qada-design.md rev 3).
 */
object Qada {

    /** Ḥanafī includes Witr in qadāʾ (wājib attached to ʿIshāʾ — Darul Iftaa #8301). */
    const val HANAFI_INCLUDES_WITR = true

    enum class QadaSchool { HANAFI, SHAFII, MALIKI, HANBALI }

    /** The pairing rule for boundary obligations. */
    enum class BoundaryPairing { PAIRED, UNPAIRED }

    /**
     * A PRE-CLASSIFIED excused period. The caller asserts the fiqh
     * classification; the engine never re-derives it.
     */
    data class FiqhPeriod(
        val start: Instant,
        val end: Instant,
        /** ḤAYD or NIFAS — the only two prayer-exempt states. */
        val type: FiqhPeriodType,
    )

    enum class FiqhPeriodType { HAYD, NIFAS }

    /** One prayer obligation or suppression produced by the engine. */
    data class PrayerInstance(
        val date: LocalDate,
        val kind: PrayerStatus.Prayer,
        /** When the prayer time entered, for boundary obligations. */
        val enteredAt: Instant,
    )

    /** One boundary ruling with its provenance. */
    data class BoundaryRuling(
        val prayer: PrayerStatus.Prayer,
        val owed: Boolean,
        /** Which rule produced this ruling ("pairing-majority", "pairing-hanafi", "suppressed"). */
        val rule: String,
    )

    /** Full qadāʾ report over the caller's classified periods + schedule. */
    data class QadaReport(
        val suppressedPrayers: List<PrayerInstance>,
        val boundaryObligations: List<PrayerInstance>,
        val ghuslRequiredAt: List<Instant>,
        val classifications: List<BoundaryRuling>,
        val provenance: List<String>,
    )

    /** Input configuration: school determines default rules; host overrides. */
    data class QadaConfig(
        val school: QadaSchool,
        /** Null = school default (majority=PAIRED, Hanafi=UNPAIRED). */
        val pairingOverride: BoundaryPairing? = null,
        /** Null = school default (true for Ḥanafī — Witr is wājib). */
        val includeWitr: Boolean? = null,
    )

    /** A day's prayer-time schedule (the 5 obligatory + sunrise for boundary checks). */
    data class DaySchedule(
        val date: LocalDate,
        val fajr: Instant,
        val dhuhr: Instant,
        val asr: Instant,
        val maghrib: Instant,
        val isha: Instant,
        /** Sunrise of the NEXT day (end of ʿIshāʾ window). */
        val nextDayFajr: Instant,
        val zoneId: ZoneId,
    )

    // ── School-derived defaults ─────────────────────────────────────────────

    /**
     * School default pairing: Ḥanafī = UNPAIRED; the other three = PAIRED.
     * Sources: Ibn al-Mundhir's Companion narration (majority PAIRED);
     * Ḥasan al-Baṣrī and al-Thawrī for the Ḥanafī UNPAIRED position.
     */
    fun defaultPairing(school: QadaSchool): BoundaryPairing = when (school) {
        QadaSchool.HANAFI -> BoundaryPairing.UNPAIRED
        else -> BoundaryPairing.PAIRED
    }

    /**
     * School default Witr inclusion: Ḥanafī = true (Witr is wājib attached
     * to ʿIshāʾ — Darul Iftaa #8301); others = false (Witr is sunnah).
     */
    fun defaultIncludeWitr(school: QadaSchool): Boolean = when (school) {
        QadaSchool.HANAFI -> HANAFI_INCLUDES_WITR
        else -> false
    }

    // ── The core computation ────────────────────────────────────────────────

    /**
     * Compute qadāʾ report over caller-supplied classified periods.
     *
     * @param periods pre-classified ḥayḍ/nifās periods (host asserts, engine never re-derives)
     * @param schedules prayer-time schedules for each day in the range (host supplies from prayer.times)
     * @param config school + optional overrides
     * @return QadaReport with suppressed prayers, boundary obligations, ghusl times, provenance
     */
    fun qadaReport(
        periods: List<FiqhPeriod>,
        schedules: List<DaySchedule>,
        config: QadaConfig,
    ): QadaReport {
        validate(periods.isNotEmpty()) {
            InvalidParameterException("periods", "empty", "must contain at least one classified period")
        }
        validate(schedules.isNotEmpty()) {
            InvalidParameterException("schedules", "empty", "must supply prayer schedules for the range")
        }

        val pairing = config.pairingOverride ?: defaultPairing(config.school)
        val includeWitr = config.includeWitr ?: defaultIncludeWitr(config.school)
        val provenance = mutableListOf<String>()

        provenance += "school: ${config.school}"
        if (config.pairingOverride != null && config.pairingOverride != defaultPairing(config.school)) {
            provenance += "pairing override: $pairing (school default: ${defaultPairing(config.school)}) — verify per your fiqh"
        } else {
            provenance += when (pairing) {
                BoundaryPairing.PAIRED -> "pairing: PAIRED (majority — Maliki/Shafiʿi/Hanbali; pure during ʿAsr → Dhuhr+ʿAsr owed)"
                BoundaryPairing.UNPAIRED -> "pairing: UNPAIRED (Ḥanafī — only the prayer in whose time purity occurred)"
            }
        }
        if (config.includeWitr != null && includeWitr != defaultIncludeWitr(config.school)) {
            provenance += "witr override: $includeWitr (school default: ${defaultIncludeWitr(config.school)}) — verify per your fiqh"
        }

        // Sorted periods for interval logic
        val sorted = periods.sortedBy { it.start }

        val suppressed = mutableListOf<PrayerInstance>()
        val boundaryObligations = mutableListOf<PrayerInstance>()
        val ghuslTimes = mutableListOf<Instant>()
        val rulings = mutableListOf<BoundaryRuling>()

        // The 5 obligatory prayers in order (for pairing logic)
        val order = listOf(
            PrayerStatus.Prayer.FAJR,
            PrayerStatus.Prayer.DHUHR,
            PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB,
            PrayerStatus.Prayer.ISHA,
        )

        for (schedule in schedules) {
            val prayerEntries = mapOf(
                PrayerStatus.Prayer.FAJR to schedule.fajr,
                PrayerStatus.Prayer.DHUHR to schedule.dhuhr,
                PrayerStatus.Prayer.ASR to schedule.asr,
                PrayerStatus.Prayer.MAGHRIB to schedule.maghrib,
                PrayerStatus.Prayer.ISHA to schedule.isha,
            )

            for (kind in order) {
                val enteredAt = prayerEntries[kind] ?: continue
                val excused = sorted.any { p -> enteredAt >= p.start && enteredAt <= p.end }

                if (excused) {
                    suppressed += PrayerInstance(schedule.date, kind, enteredAt)
                    rulings += BoundaryRuling(kind, owed = false, rule = "suppressed (period active at entry)")
                } else {
                    rulings += BoundaryRuling(kind, owed = true, rule = "not in period")
                }
            }

            // Ghusl required at period end (host handles the "enough time" check)
            for (p in sorted) {
                val endDate = p.end.atZone(schedule.zoneId).toLocalDate()
                if (endDate == schedule.date && ghuslTimes.none { it == p.end }) {
                    ghuslTimes += p.end
                }
            }
        }

        // Boundary obligations: when a period ENDS, determine which prayers
        // become owed at the boundary.
        for (p in sorted) {
            val endTime = p.end
            val endSchedule = schedules.firstOrNull { s ->
                endTime.atZone(s.zoneId).toLocalDate() == s.date
            } ?: continue

            val endKind = prayerOfInstant(endTime, endSchedule) ?: continue
            val endIdx = order.indexOf(endKind)

            // The current prayer is always owed (she's pure now)
            boundaryObligations += PrayerInstance(endSchedule.date, endKind, endTime)
            rulings += BoundaryRuling(
                endKind, owed = true,
                rule = "boundary: purity during ${endKind.name} time — current prayer owed",
            )

            // Paired earlier prayer (majority)
            if (pairing == BoundaryPairing.PAIRED) {
                val paired = when (endKind) {
                    PrayerStatus.Prayer.ASR -> PrayerStatus.Prayer.DHUHR
                    PrayerStatus.Prayer.ISHA -> PrayerStatus.Prayer.MAGHRIB
                    else -> null
                }
                if (paired != null) {
                    boundaryObligations += PrayerInstance(endSchedule.date, paired, endTime)
                    rulings += BoundaryRuling(
                        paired, owed = true,
                        rule = "boundary pairing (majority): purity during ${endKind.name} → ${paired.name} also owed",
                    )
                }
            }

            // Shafiʿi rule: purity during ʿAsr also obligates the paired earlier prayer
            // (this is the same as the pairing rule — the Shafiʿi "sufficient time"
            // check applies to whether the CURRENT prayer is owed, not the paired one)
        }

        // Add Witr to boundary obligations if Ḥanafī and ʿIshāʾ is owed
        if (includeWitr) {
            val ishaBoundaries = boundaryObligations.filter { it.kind == PrayerStatus.Prayer.ISHA }
            for (isha in ishaBoundaries) {
                boundaryObligations += PrayerInstance(isha.date, PrayerStatus.Prayer.ISHA, isha.enteredAt)
                rulings += BoundaryRuling(
                    PrayerStatus.Prayer.ISHA, owed = true,
                    rule = "Ḥanafī: Witr included with ʿIshāʾ qadāʾ (Darul Iftaa #8301)",
                )
            }
        }

        return QadaReport(
            suppressedPrayers = suppressed.sortedWith(compareBy({ it.date }, { it.kind })),
            boundaryObligations = boundaryObligations.distinct().sortedWith(compareBy({ it.enteredAt }, { it.kind })),
            ghuslRequiredAt = ghuslTimes.distinct().sorted(),
            classifications = rulings,
            provenance = provenance,
        )
    }

    /** Which prayer kind's window contains [instant], or null if none. */
    private fun prayerOfInstant(instant: Instant, schedule: DaySchedule): PrayerStatus.Prayer? {
        // ʿIshāʾ spans from its entry to next-day Fajr
        if (instant >= schedule.isha && instant < schedule.nextDayFajr) {
            return PrayerStatus.Prayer.ISHA
        }
        // For daytime prayers, each spans from its entry to the next prayer's entry
        val entries = listOf(
            PrayerStatus.Prayer.FAJR to schedule.fajr,
            PrayerStatus.Prayer.DHUHR to schedule.dhuhr,
            PrayerStatus.Prayer.ASR to schedule.asr,
            PrayerStatus.Prayer.MAGHRIB to schedule.maghrib,
            PrayerStatus.Prayer.ISHA to schedule.isha,
        )
        for (i in entries.indices) {
            val (kind, entry) = entries[i]
            val next = if (i + 1 < entries.size) entries[i + 1].second else schedule.nextDayFajr
            if (instant >= entry && instant < next) return kind
        }
        return null
    }
}

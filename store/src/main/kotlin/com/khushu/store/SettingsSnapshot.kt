package com.khushu.store

import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.CalendarParams
import com.khushu.engine.calendar.CivilCalendarType
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Convention
import com.khushu.engine.prayer.HighLatitudeRule
import com.khushu.engine.prayer.Madhab
import com.khushu.engine.prayer.PrayerConfiguration
import com.khushu.engine.prayer.PrayerOffsets
import com.khushu.engine.prayer.RoundingPolicy
import com.khushu.engine.prayer.Shafaq
import com.khushu.engine.zakat.DebtTreatment
import com.khushu.engine.zakat.FitrPaymentMode
import com.khushu.engine.zakat.JewelryValuationBasis
import com.khushu.engine.zakat.NisabSource
import com.khushu.engine.zakat.NisabWeightConvention
import com.khushu.engine.zakat.ZakatMadhab
import com.khushu.engine.zakat.ZakatParams
import com.khushu.engine.zakat.ZakatRules
import kotlinx.serialization.Serializable

/** Schema version of [SettingsSnapshot]; bumped only on breaking field changes. */
const val SETTINGS_SCHEMA_VERSION: Int = 1

/** Serializable mirror of [PrayerOffsets] — all seven per-prayer minute adjustments. */
@Serializable
data class PrayerOffsetsDto(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val sunset: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

/**
 * Serializable mirror of [PrayerConfiguration] — every tunable prayer
 * parameter, grouped and defaulted exactly like the engine type.
 */
@Serializable
data class PrayerSettingsDto(
    val madhab: Madhab = Madhab.SHAFII,
    val convention: Convention = Convention.MUSLIM_WORLD_LEAGUE,
    /** Only meaningful when convention == CUSTOM. */
    val fajrAngle: Double = 18.0,
    /** Only meaningful when convention == CUSTOM. */
    val ishaAngle: Double = 18.0,
    /** > 0 switches Isha to a fixed interval after maghrib instead of an angle. */
    val ishaIntervalMinutes: Int = 0,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val offsets: PrayerOffsetsDto = PrayerOffsetsDto(),
    /** Only meaningful with MOON_SIGHTING_COMMITTEE; null inherits the method preset. */
    val shafaq: Shafaq? = null,
    /** null inherits the method preset. */
    val rounding: RoundingPolicy? = null,
)

/** Serializable mirror of [CalendarParams] — hijri offset plus optional-fast flags and display sides. */
@Serializable
data class CalendarSettingsDto(
    val hijriOffsetDays: Int = 0,
    val mondaysThursdays: Boolean = false,
    val whiteDays: Boolean = false,
    val shawwalSix: Boolean = false,
    val shaban: Boolean = false,
    val dhulHijjahFirstNine: Boolean = false,
    val tasuaAshura: Boolean = false,
    /** Primary calendar line for dual-calendar UIs (display setting). */
    val primarySide: CalendarConfiguration.Side = CalendarConfiguration.Side.GREGORIAN,
    /** Secondary calendar line; null hides it. At least one side must be HIJRI (engine-enforced). */
    val secondarySide: CalendarConfiguration.Side? = CalendarConfiguration.Side.HIJRI,
    /** Civil system the GREGORIAN side renders (region-based civil calendars); unknown values coerce to the default. */
    val civilCalendar: CivilCalendarType = CivilCalendarType.GREGORIAN,
    /**
     * User's global correction for the sighted (moonsighted) calendar mode —
     * verbatim mirror of [com.khushu.engine.calendar.SightedCalendar.SightedCalendarParams.localOffsetDays]
     * (−2..+2, applied after anchor derivation; the "everything went south"
     * escape hatch). Hosts surface it as "Moon sighting offset" alongside the
     * distinct tabular [hijriOffsetDays]. 0 = unset (tabular-identical).
     */
    val localOffsetDays: Int = 0,
)

/** Serializable mirror of [ZakatParams] — madhab rules and nisab valuation inputs. */
@Serializable
data class ZakatSettingsDto(
    val madhab: ZakatMadhab = ZakatMadhab.HANAFI,
    val nisabSource: NisabSource = NisabSource.SILVER,
    val weightConvention: NisabWeightConvention = NisabWeightConvention.COMMON,
    val hawlComplete: Boolean = true,
    /** Debt-deduction policy; null = madhab default (v1.11). */
    val debtTreatment: DebtTreatment? = null,
    /** Worn-jewelry valuation basis; null = madhab default (v1.11). */
    val jewelryValuationBasis: JewelryValuationBasis? = null,
    /** Fitr form the household discharges in (v1.11). */
    val fitrPaymentMode: FitrPaymentMode = FitrPaymentMode.FOOD,
    /** Mixed-irrigation rate rule (v1.11). */
    val mixedIrrigationRule: ZakatRules.MixedIrrigationRule = ZakatRules.MixedIrrigationRule.PREDOMINANT,
    /** Agricultural nisab policy (v1.11). */
    val ushrNisabPolicy: ZakatRules.UshrNisabPolicy = ZakatRules.UshrNisabPolicy.FIVE_WASAQ,
)

/** Serializable mirror of the engine's typed [Location]. */
@Serializable
data class LocationDto(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMeters: Double = 0.0,
)

/** Which instant opens the evening adhkar window. Mirrors the orchestrator's
 * `EveningStart` without depending on it (this module sits below the
 * orchestrator in the dependency order). */
@Serializable
enum class EveningStartMode { MAGHRIB, ASR }

/**
 * The dua/adhkar display and window settings — the host-facing half of the
 * orchestrator's `AdaptiveContext`. Every field here changes which adaptive
 * sections the dua card surfaces and when their windows are open; none of
 * them is cosmetic.
 */
@Serializable
data class DuaSettingsDto(
    /** Which instant opens the evening adhkar window (maghrib vs asr). */
    val eveningStart: EveningStartMode = EveningStartMode.MAGHRIB,
    /** Length of the after-salah adhkar window, in minutes (0..120). */
    val postPrayerMinutes: Int = 20,
    /** How many adaptive sections to surface at once (1..6). */
    val maxSections: Int = 2,
    /** Local bedtime as "HH:mm"; null leaves the before-sleep window inactive. */
    val bedtime: String? = null,
    /** Local wake time as "HH:mm"; null leaves the waking-up window inactive. */
    val wakeTime: String? = null,
) {
    init {
        require(postPrayerMinutes in 0..120) { "postPrayerMinutes must be 0..120" }
        require(maxSections in 1..6) { "maxSections must be 1..6" }
    }
}

/**
 * How the Qur'an reader presents the text. Every field is an input to what
 * the reader renders, so the corpus-agnostic half (size, edition, translation)
 * lives with the other persisted settings instead of in a UI-side store.
 */
@Serializable
data class QuranSettingsDto(
    /** Ayah text size in sp, 16..48. */
    val arabicSizeSp: Int = 28,
    /**
     * Mushaf edition code from `quran_mushafs` (`qpc`, `indopak_15`, …). It
     * selects the page layout, not just the script.
     */
    val mushaf: String = "qpc",
    /**
     * Translation pack id; null or blank renders the text alone. Defaults to
     * the Sahih International English pack the reader has always opened with,
     * so "translation off" stays a choice the reader makes rather than a
     * silent regression for existing installs.
     */
    val translationPack: String? = "en_saheeh-international",
) {
    init {
        require(arabicSizeSp in 16..48) { "arabicSizeSp must be 16..48" }
    }
}

/**
 * Everything a host persists about engine usage: the grouped settings for
 * every computational domain plus the user's location. Versioned via
 * [schemaVersion]; forward-compatible — unknown keys are ignored and missing
 * fields fall back to these defaults when decoding older/newer files.
 */
@Serializable
data class SettingsSnapshot(
    val prayer: PrayerSettingsDto = PrayerSettingsDto(),
    val calendar: CalendarSettingsDto = CalendarSettingsDto(),
    val zakat: ZakatSettingsDto = ZakatSettingsDto(),
    val dua: DuaSettingsDto = DuaSettingsDto(),
    val quran: QuranSettingsDto = QuranSettingsDto(),
    val location: LocationDto? = null,
    val schemaVersion: Int = SETTINGS_SCHEMA_VERSION,
) {
    companion object {
        /** Build a snapshot straight from engine parameter objects. */
        fun of(
            prayer: PrayerConfiguration = PrayerConfiguration(),
            calendar: CalendarParams = CalendarParams(),
            zakat: ZakatParams = ZakatParams(),
            location: Location? = null,
        ): SettingsSnapshot = SettingsSnapshot(
            prayer = prayer.toDto(),
            calendar = calendar.toDto(),
            zakat = zakat.toDto(),
            location = location?.toDto(),
        )
    }
}

// ── DTO ↔ engine mappers ─────────────────────────────────────────────────────
// Conversion to engine types runs the engine's own init validation: an
// out-of-range persisted value surfaces as a typed InvalidParameterException
// at conversion time, never as a silently-wrong computation.

fun PrayerOffsetsDto.toEngine(): PrayerOffsets =
    PrayerOffsets(fajr, sunrise, dhuhr, asr, sunset, maghrib, isha)

fun PrayerOffsets.toDto(): PrayerOffsetsDto =
    PrayerOffsetsDto(fajr, sunrise, dhuhr, asr, sunset, maghrib, isha)

fun PrayerSettingsDto.toConfiguration(): PrayerConfiguration = PrayerConfiguration(
    madhab = madhab,
    convention = convention,
    fajrAngle = fajrAngle,
    ishaAngle = ishaAngle,
    ishaIntervalMinutes = ishaIntervalMinutes,
    highLatitudeRule = highLatitudeRule,
    offsets = offsets.toEngine(),
    shafaq = shafaq,
    rounding = rounding,
)

fun PrayerConfiguration.toDto(): PrayerSettingsDto = PrayerSettingsDto(
    madhab = madhab,
    convention = convention,
    fajrAngle = fajrAngle,
    ishaAngle = ishaAngle,
    ishaIntervalMinutes = ishaIntervalMinutes,
    highLatitudeRule = highLatitudeRule,
    offsets = offsets.toDto(),
    shafaq = shafaq,
    rounding = rounding,
)

fun CalendarSettingsDto.toParams(): CalendarParams = CalendarParams(
    hijriOffsetDays = hijriOffsetDays,
    mondaysThursdays = mondaysThursdays,
    whiteDays = whiteDays,
    shawwalSix = shawwalSix,
    shaban = shaban,
    dhulHijjahFirstNine = dhulHijjahFirstNine,
    tasuaAshura = tasuaAshura,
)

fun CalendarParams.toDto(): CalendarSettingsDto = CalendarSettingsDto(
    hijriOffsetDays = hijriOffsetDays,
    mondaysThursdays = mondaysThursdays,
    whiteDays = whiteDays,
    shawwalSix = shawwalSix,
    shaban = shaban,
    dhulHijjahFirstNine = dhulHijjahFirstNine,
    tasuaAshura = tasuaAshura,
)

/**
 * Rebuild the sighted-calendar [com.khushu.engine.calendar.SightedCalendar.SightedCalendarParams]
 * (announcement list + this DTO's [CalendarSettingsDto.localOffsetDays] and
 * [CalendarSettingsDto.hijriOffsetDays] as the tabular fallback). Throws the
 * engine's typed InvalidParameterException when the persisted offset is out
 * of −2..+2 — same validation as the engine's own constructor, surfaced at
 * conversion time rather than as a silently-wrong derivation.
 */
fun CalendarSettingsDto.toSightedParams(
    announcements: List<com.khushu.engine.calendar.SightedCalendar.AnnouncedMonthStart>,
): com.khushu.engine.calendar.SightedCalendar.SightedCalendarParams =
    com.khushu.engine.calendar.SightedCalendar.SightedCalendarParams(
        announcements = announcements,
        localOffsetDays = localOffsetDays,
        tabularOffsetDays = hijriOffsetDays,
    )

/**
 * Rebuild the engine's [CalendarConfiguration] (dual-calendar display +
 * offset). Throws the engine's typed InvalidParameterException when a
 * persisted combination violates the at-least-one-Hijri rule.
 */
fun CalendarSettingsDto.toConfiguration(): CalendarConfiguration = CalendarConfiguration(
    primary = primarySide,
    secondary = secondarySide,
    hijriOffsetDays = hijriOffsetDays,
    civilCalendar = civilCalendar,
)

fun ZakatSettingsDto.toParams(): ZakatParams = ZakatParams(
    madhab = madhab,
    nisabSource = nisabSource,
    weightConvention = weightConvention,
    hawlComplete = hawlComplete,
    debtTreatment = debtTreatment,
    jewelryValuationBasis = jewelryValuationBasis,
)

fun ZakatParams.toDto(): ZakatSettingsDto = ZakatSettingsDto(
    madhab = madhab,
    nisabSource = nisabSource,
    weightConvention = weightConvention,
    hawlComplete = hawlComplete,
    debtTreatment = debtTreatment,
    jewelryValuationBasis = jewelryValuationBasis,
)

fun LocationDto.toLocation(): Location =
    Location.of(latitudeDegrees, longitudeDegrees, altitudeMeters)

fun Location.toDto(): LocationDto =
    LocationDto(latitude.degrees, longitude.degrees, altitudeMeters.meters)

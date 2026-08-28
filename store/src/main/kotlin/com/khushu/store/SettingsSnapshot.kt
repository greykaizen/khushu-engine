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
import com.khushu.engine.zakat.NisabSource
import com.khushu.engine.zakat.NisabWeightConvention
import com.khushu.engine.zakat.ZakatMadhab
import com.khushu.engine.zakat.ZakatParams
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
)

/** Serializable mirror of [ZakatParams] — madhab rules and nisab valuation inputs. */
@Serializable
data class ZakatSettingsDto(
    val madhab: ZakatMadhab = ZakatMadhab.HANAFI,
    val nisabSource: NisabSource = NisabSource.SILVER,
    val weightConvention: NisabWeightConvention = NisabWeightConvention.COMMON,
    val hawlComplete: Boolean = true,
)

/** Serializable mirror of the engine's typed [Location]. */
@Serializable
data class LocationDto(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMeters: Double = 0.0,
)

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
)

fun ZakatParams.toDto(): ZakatSettingsDto = ZakatSettingsDto(
    madhab = madhab,
    nisabSource = nisabSource,
    weightConvention = weightConvention,
    hawlComplete = hawlComplete,
)

fun LocationDto.toLocation(): Location =
    Location.of(latitudeDegrees, longitudeDegrees, altitudeMeters)

fun Location.toDto(): LocationDto =
    LocationDto(latitude.degrees, longitude.degrees, altitudeMeters.meters)

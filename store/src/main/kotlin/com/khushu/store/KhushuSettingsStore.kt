package com.khushu.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.khushu.engine.calendar.CalendarConfiguration
import com.khushu.engine.calendar.CalendarParams
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.PrayerConfiguration
import com.khushu.engine.zakat.ZakatParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore [Serializer] bridge for [SettingsSnapshot]. Empty files decode to
 * defaults; corrupt files raise [CorruptionException] instead of silently
 * resetting — a silent fallback would change religiously-relevant settings
 * (madhab, convention) without the user knowing.
 */
class KhushuSettingsSerializer(
    private val codec: SettingsCodec = SettingsCodec(),
) : Serializer<SettingsSnapshot> {

    override val defaultValue: SettingsSnapshot = SettingsSnapshot()

    override suspend fun readFrom(input: InputStream): SettingsSnapshot {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        try {
            return codec.decode(bytes)
        } catch (cause: kotlinx.serialization.SerializationException) {
            throw CorruptionException("khushu settings file is not a valid settings document", cause)
        }
    }

    override suspend fun writeTo(t: SettingsSnapshot, output: OutputStream) {
        output.write(codec.encode(t))
    }
}

/**
 * Typed host-side access to persisted settings. Atomic: concurrent updates
 * are serialized by DataStore; every read observes the last completed write.
 *
 * Typical Android host wiring (Compose):
 * ```kotlin
 * val store = remember {
 *     KhushuSettingsStores.file(File(context.filesDir, "khushu_settings.json"))
 * }
 * val snapshot by store.settings.collectAsState(initial = SettingsSnapshot())
 * val times = engine.prayer.times(location, date, snapshot.prayer.toConfiguration())
 * scope.launch { store.updatePrayer(newConfig) }
 * ```
 */
class KhushuSettingsStore(private val dataStore: DataStore<SettingsSnapshot>) {

    /** Cold flow of the persisted snapshot; emits defaults for an empty store. */
    val settings: Flow<SettingsSnapshot> = dataStore.data

    /** Engine-ready flows — one per domain. */
    val prayerConfiguration: Flow<PrayerConfiguration> = settings.map { it.prayer.toConfiguration() }
    val calendarParams: Flow<CalendarParams> = settings.map { it.calendar.toParams() }
    val zakatParams: Flow<ZakatParams> = settings.map { it.zakat.toParams() }

    /** Atomic read-modify-write; returns the snapshot actually stored. */
    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot): SettingsSnapshot =
        dataStore.updateData(transform)

    suspend fun updatePrayer(config: PrayerConfiguration): SettingsSnapshot =
        update { it.copy(prayer = config.toDto()) }

    /** Updates fast flags + offset; display settings (sides + civil calendar) are preserved. */
    suspend fun updateCalendar(params: CalendarParams): SettingsSnapshot =
        update {
            it.copy(
                calendar = params.toDto().copy(
                    primarySide = it.calendar.primarySide,
                    secondarySide = it.calendar.secondarySide,
                    civilCalendar = it.calendar.civilCalendar,
                ),
            )
        }

    /**
     * Updates dual-calendar display settings (sides, civil calendar, hijri
     * offset) from an engine [CalendarConfiguration]; optional-fast flags are
     * preserved.
     */
    suspend fun updateCalendarConfiguration(config: CalendarConfiguration): SettingsSnapshot =
        update {
            it.copy(
                calendar = it.calendar.copy(
                    primarySide = config.primary,
                    secondarySide = config.secondary,
                    hijriOffsetDays = config.hijriOffsetDays,
                    civilCalendar = config.civilCalendar,
                ),
            )
        }

    suspend fun updateZakat(params: ZakatParams): SettingsSnapshot =
        update { it.copy(zakat = params.toDto()) }

    suspend fun updateLocation(location: Location?): SettingsSnapshot =
        update { it.copy(location = location?.toDto()) }

    /** Reset prayer/calendar/zakat to engine defaults; [SettingsSnapshot.location] is preserved. */
    suspend fun resetSettings(): SettingsSnapshot =
        update { SettingsSnapshot(location = it.location) }
}

/** File-backed store factories. */
object KhushuSettingsStores {

    /**
     * A [KhushuSettingsStore] persisted at [file]. The file must be dedicated
     * to this store and only ONE DataStore instance may point at it per
     * process (DataStore contract).
     */
    fun file(file: File, codec: SettingsCodec = SettingsCodec()): KhushuSettingsStore =
        KhushuSettingsStore(DataStoreFactory.create(KhushuSettingsSerializer(codec)) { file })
}

package com.khushu.store

import androidx.datastore.core.CorruptionException
import com.khushu.engine.calendar.CalendarParams
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoreTest {

    private val codec = SettingsCodec()

    private val tweaked = SettingsSnapshot.of(
        prayer = PrayerConfiguration(
            madhab = Madhab.HANAFI,
            convention = Convention.MOON_SIGHTING_COMMITTEE,
            fajrAngle = 19.5,
            ishaAngle = 17.0,
            ishaIntervalMinutes = 90,
            highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE,
            offsets = PrayerOffsets(fajr = 2, sunrise = -1, dhuhr = 1, sunset = 4, maghrib = 3, isha = -2),
            shafaq = Shafaq.ABYAD,
            rounding = RoundingPolicy.NONE,
        ),
        calendar = CalendarParams(hijriOffsetDays = 1, whiteDays = true, shawwalSix = true, tasuaAshura = true),
        zakat = ZakatParams(
            madhab = ZakatMadhab.MALIKI,
            nisabSource = NisabSource.GOLD,
            weightConvention = NisabWeightConvention.CLASSICAL,
            hawlComplete = false,
        ),
        location = Location.of(51.5072, -0.1276, 11.0),
    )

    @Test
    fun roundTripPreservesEveryTweak() {
        assertEquals(tweaked, codec.decode(codec.encode(tweaked)))
    }

    @Test
    fun mappersRoundTripEngineTypes() {
        val prayer = tweaked.prayer.toConfiguration()
        assertEquals(prayer, prayer.toDto().toConfiguration())
        val calendar = tweaked.calendar.toParams()
        assertEquals(calendar, calendar.toDto().toParams())
        val zakat = tweaked.zakat.toParams()
        assertEquals(zakat, zakat.toDto().toParams())
        val location = tweaked.location!!.toLocation()
        assertEquals(location, location.toDto().toLocation())
    }

    @Test
    fun emptyObjectDecodesToDefaults() {
        assertEquals(SettingsSnapshot(), codec.decode("{}".encodeToByteArray()))
    }

    @Test
    fun unknownKeysAreIgnoredForForwardCompatibility() {
        val json = """{"prayer":{"madhab":"HANAFI","futureKnob":true},"neverHeardOfIt":1}"""
        val decoded = codec.decode(json.encodeToByteArray())
        assertEquals(Madhab.HANAFI, decoded.prayer.madhab)
        assertEquals(Madhab.SHAFII, SettingsSnapshot().prayer.madhab)
    }

    @Test
    fun unknownEnumCoercesToFieldDefault() {
        val json = """{"prayer":{"madhab":"MARTIAN"}}"""
        assertEquals(Madhab.SHAFII, codec.decode(json.encodeToByteArray()).prayer.madhab)
    }

    @Test
    fun corruptBytesRejected() {
        assertFailsWith<kotlinx.serialization.SerializationException> {
            codec.decode("not json at all".encodeToByteArray())
        }
    }

    @Test
    fun serializerWrapsCorruptionAndAcceptsEmptyFiles(): Unit = runBlocking {
        val serializer = KhushuSettingsSerializer()
        assertFailsWith<CorruptionException> {
            serializer.readFrom(ByteArrayInputStream("garbage".encodeToByteArray()))
        }
        assertEquals(SettingsSnapshot(), serializer.readFrom(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun fileBackedStorePersistsUpdates(): Unit = runBlocking {
        val file = Files.createTempFile("khushu-store-roundtrip", ".json").toFile()
        try {
            val store = KhushuSettingsStores.file(file)
            assertEquals(SettingsSnapshot(), store.settings.first())
            val afterPrayer = store.updatePrayer(tweaked.prayer.toConfiguration())
            assertEquals(tweaked.prayer, afterPrayer.prayer)
            store.update { tweaked }
            assertEquals(tweaked, store.settings.first())
            // durability: the bytes on disk decode to exactly what was stored
            assertEquals(tweaked, codec.decode(file.readBytes()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptFileSurfacesAsCorruptionOnRead(): Unit = runBlocking {
        val file = Files.createTempFile("khushu-store-corrupt", ".json").toFile()
        file.writeText("{{{ not json")
        try {
            val store = KhushuSettingsStores.file(file)
            assertFailsWith<CorruptionException> { runBlocking { store.settings.first() } }
        } finally {
            file.delete()
        }
    }

    @Test
    fun resetSettingsKeepsLocationAndRestoresDefaults(): Unit = runBlocking {
        val file = Files.createTempFile("khushu-store-reset", ".json").toFile()
        try {
            val store = KhushuSettingsStores.file(file)
            store.update { tweaked }
            val afterReset = store.resetSettings()
            assertEquals(SettingsSnapshot(location = tweaked.location), afterReset)
        } finally {
            file.delete()
        }
    }
}

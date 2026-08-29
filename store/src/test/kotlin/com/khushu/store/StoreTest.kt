package com.khushu.store

import androidx.datastore.core.CorruptionException
import com.khushu.engine.calendar.CalendarConfiguration
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
import kotlin.test.assertTrue

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

    @Test
    fun defaultSnapshotDecodesToGregorianPrimaryWithHijriSecondary() {
        val config = SettingsSnapshot().calendar.toConfiguration()
        assertEquals(CalendarConfiguration.Side.GREGORIAN, config.primary)
        assertEquals(CalendarConfiguration.Side.HIJRI, config.secondary)
        assertEquals(0, config.hijriOffsetDays)
    }

    @Test
    fun calendarSidesRoundTripThroughJson() {
        val s = SettingsSnapshot().copy(
            calendar = SettingsSnapshot().calendar.copy(
                primarySide = CalendarConfiguration.Side.HIJRI,
                secondarySide = null,
                hijriOffsetDays = 1,
            ),
        )
        assertEquals(s, codec.decode(codec.encode(s)))
        val restored = codec.decode(codec.encode(s)).calendar.toConfiguration()
        assertEquals(CalendarConfiguration.Side.HIJRI, restored.primary)
        assertEquals(null, restored.secondary)
        assertEquals(1, restored.hijriOffsetDays)
    }

    @Test
    fun sideAndFlagUpdatesPreserveEachOther(): Unit = runBlocking {
        val file = Files.createTempFile("khushu-store-sides", ".json").toFile()
        try {
            val store = KhushuSettingsStores.file(file)
            store.updateCalendar(tweaked.calendar.toParams())
            val afterSides = store.updateCalendarConfiguration(
                CalendarConfiguration(
                    primary = CalendarConfiguration.Side.HIJRI,
                    secondary = null,
                    hijriOffsetDays = -1,
                ),
            )
            assertEquals(CalendarConfiguration.Side.HIJRI, afterSides.calendar.primarySide)
            assertEquals(null, afterSides.calendar.secondarySide)
            assertEquals(-1, afterSides.calendar.hijriOffsetDays)
            assertTrue(afterSides.calendar.whiteDays, "fast flags must survive side updates")
            // updateCalendar must not clobber the persisted sides
            val afterParams = store.updateCalendar(CalendarParams())
            assertEquals(CalendarConfiguration.Side.HIJRI, afterParams.calendar.primarySide)
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidSideCombinationFailsWithTypedEngineError() {
        val json = """{"calendar":{"primarySide":"GREGORIAN","secondarySide":"GREGORIAN"}}"""
        val decoded = codec.decode(json.encodeToByteArray())
        assertFailsWith<com.khushu.engine.core.error.InvalidParameterException> {
            decoded.calendar.toConfiguration()
        }
    }

    // ── v1.11: zakat policy fields ─────────────────────────────────────────

    @Test
    fun zakatPolicyOverridesRoundTripThroughJson() {
        val snapshot = SettingsSnapshot(
            zakat = ZakatSettingsDto(
                madhab = ZakatMadhab.HANAFI,
                debtTreatment = com.khushu.engine.zakat.DebtTreatment.ALL_DEBTS,
                jewelryValuationBasis = com.khushu.engine.zakat.JewelryValuationBasis.REALIZABLE_VALUE,
                fitrPaymentMode = com.khushu.engine.zakat.FitrPaymentMode.CASH_EQUIVALENT,
                mixedIrrigationRule = com.khushu.engine.zakat.ZakatRules.MixedIrrigationRule.PROPORTIONAL,
                ushrNisabPolicy = com.khushu.engine.zakat.ZakatRules.UshrNisabPolicy.NO_NISAB,
            ),
        )
        val decoded = codec.decode(codec.encode(snapshot))
        assertEquals(snapshot.zakat, decoded.zakat)
        // Engine params carry the overrides through the mapper.
        val params = decoded.zakat.toParams()
        assertEquals(com.khushu.engine.zakat.DebtTreatment.ALL_DEBTS, params.debtTreatment)
        assertEquals(
            com.khushu.engine.zakat.JewelryValuationBasis.REALIZABLE_VALUE,
            params.jewelryValuationBasis,
        )
    }

    @Test
    fun v110ZakatFileDecodesWithV111Defaults() {
        // A file written by v1.10 (no v1.11 keys) decodes with the additive defaults.
        val v110Json = """{"zakat":{"madhab":"HANAFI","nisabSource":"SILVER","weightConvention":"COMMON","hawlComplete":true}}"""
        val decoded = codec.decode(v110Json.encodeToByteArray())
        assertEquals(null, decoded.zakat.debtTreatment)
        assertEquals(com.khushu.engine.zakat.FitrPaymentMode.FOOD, decoded.zakat.fitrPaymentMode)
        assertEquals(
            com.khushu.engine.zakat.ZakatRules.MixedIrrigationRule.PREDOMINANT,
            decoded.zakat.mixedIrrigationRule,
        )
        assertEquals(
            com.khushu.engine.zakat.ZakatRules.UshrNisabPolicy.FIVE_WASAQ,
            decoded.zakat.ushrNisabPolicy,
        )
    }
}

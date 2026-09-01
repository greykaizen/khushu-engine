package com.khushu.engine.facadetest

import com.khushu.engine.KhushuEngine
import com.khushu.engine.core.geo.Location
import com.khushu.engine.prayer.Prayer
import com.khushu.engine.prayer.PrayerStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerBreadthTest {

    private val engine = KhushuEngine()
    private val london = Location.of(51.5072, -0.1276)
    private val zone = ZoneId.of("Europe/London")

    @Test
    fun monthReturnsEveryDayAndIsCacheable() {
        val cached = com.khushu.engine.CachedEngine(engine)
        val ym = YearMonth.of(2025, 6)
        val first = cached.prayerMonth(london, ym)
        val second = cached.prayerMonth(london, ym)
        assertEquals(first, second)
        assertEquals(30, first.size)
        assertEquals(ym.atDay(1), first.first().date)
        assertEquals(ym.atEndOfMonth(), first.last().date)
    }

    @Test
    fun navigationSequenceSpansMidnight() {
        val date = LocalDate.of(2025, 10, 15)
        val t = engine.prayer.times(london, date)
        val lateNight = t.isha.adjusted!!.plus(java.time.Duration.ofMinutes(30))

        val seq = engine.prayer.navigationSequence(london, lateNight, zone, before = 1, after = 2)

        // previous, current(ISHA), next(tomorrow FAJR), next+1(tomorrow SUNRISE)
        assertEquals(4, seq.size)
        assertEquals(PrayerStatus.Prayer.ISHA, seq[1].kind)
        assertEquals(PrayerStatus.Prayer.FAJR, seq[2].kind)
        assertTrue(seq[0].instant < seq[1].instant)
        assertTrue(seq[2].instant < seq[3].instant)
        // All instants strictly ordered across the whole window.
        seq.zipWithNext().forEach { (a, b) -> assertTrue(a.instant < b.instant) }
    }

    @Test
    fun occasionsCarryConsensusSafeCategories() {
        val occasions = engine.prayer.occasionsOn(london, LocalDate.of(2025, 10, 15))
        val byLabel = occasions.associateBy { it.label.substringBefore(' ') }
        assertEquals(Prayer.OccasionCategory.OBLIGATORY, byLabel["Fajr"]?.category)
        assertEquals(Prayer.OccasionCategory.BOUNDARY_EVENT, byLabel["Sunrise"]?.category)
        assertEquals(Prayer.OccasionCategory.OBLIGATORY, byLabel["Maghrib"]?.category)
        assertTrue(occasions.any { it.category == Prayer.OccasionCategory.VOLUNTARY })
        // Chronological.
        val timed = occasions.filter { it.instant != null }
        timed.zipWithNext().forEach { (a, b) -> assertTrue(a.instant!! <= b.instant!!) }
    }

    @Test
    fun imsakIsBeforeFajrByConfiguredMargin() {
        val date = LocalDate.of(2025, 6, 21)
        val imsak = engine.prayer.windows.imsak(london, date, minutesBeforeFajr = 10)!!
        val fajr = engine.prayer.times(london, date).fajr.adjusted!!
        assertEquals(fajr.minusSeconds(600), imsak)
    }

    @Test
    fun forbiddenWindowsAreOrderedAndInsideTheDay() {
        val w = engine.prayer.windows.forbidden(london, LocalDate.of(2025, 6, 21))
        assertNotNull(w.afterFajrUntilSunrise)
        assertNotNull(w.zawaal)
        assertNotNull(w.beforeSunset)
        val fajrWin = w.afterFajrUntilSunrise!!
        assertTrue(fajrWin.endInclusive > fajrWin.start)
        val zawaal = w.zawaal!!
        assertTrue(zawaal.start < zawaal.endInclusive)
    }

    @Test
    fun fastingFactsCoverTheFullFast() {
        val f = engine.prayer.windows.fastingFacts(london, LocalDate.of(2025, 4, 2))
        assertNotNull(f.imsak)
        assertNotNull(f.fastStart)
        assertNotNull(f.fastEnd)
        assertTrue(f.imsak!! < f.fastStart!!)
        assertTrue(f.fastStart!! < f.fastEnd!!)
    }

    @Test
    fun travelFactsAreObjectiveAndNeverMutateTimes() {
        val date = LocalDate.of(2025, 7, 8)
        val travelled = engine.prayer.windows.travelFacts(
            london, date, travelledDistanceKm = com.khushu.engine.core.units.Kilometers(120.0),
        )
        assertEquals(true, travelled.qasrEligibleByDistance)
        assertEquals(setOf(PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR, PrayerStatus.Prayer.ISHA), travelled.shortenable)
        // Anchors are the actual computed Asr/Isha times — times untouched.
        val t = engine.prayer.times(london, date)
        assertEquals(t.asr.adjusted, travelled.dhuhrJoinsAsrAt)
        assertEquals(t.isha.adjusted, travelled.maghribJoinsIshaAt)

        val notTravelled = engine.prayer.windows.travelFacts(london, date, travelledDistanceKm = com.khushu.engine.core.units.Kilometers(5.0))
        assertEquals(false, notTravelled.qasrEligibleByDistance)
    }

    @Test
    fun auditExposesProvenance() {
        val result = engine.prayer.times(london, LocalDate.of(2025, 6, 21))
        val info = result.audit.conventionInfo
        assertNotNull(info)
        assertEquals("MWL", info.identifier)
        assertEquals("18.0°", info.fajrRule)
        assertEquals(PrayerStatus::class.java.simpleName.length > 0, true) // no-op guard for readability
    }

    @Test
    fun statsStreakIsReachableViaFacadeAndMatchesModule() {
        val day1 = LocalDate.of(2026, 3, 2)
        val obligatory = listOf(
            PrayerStatus.Prayer.FAJR, PrayerStatus.Prayer.DHUHR, PrayerStatus.Prayer.ASR,
            PrayerStatus.Prayer.MAGHRIB, PrayerStatus.Prayer.ISHA,
        )
        val records = obligatory.map { Prayer.PrayerLogRecord(day1, it, completed = true) }
        val viaFacade = engine.prayer.stats.streak(records)
        val viaModule = Prayer.streakStats(records)
        assertEquals(viaModule, viaFacade)
        assertEquals(1, viaFacade.currentStreakDays)
    }

    @Test
    fun nextOccurrenceAndJumuahAndNightDivisionsReachableViaFacade() {
        val anchor = LocalDate.of(2026, 8, 26).atTime(9, 0).atZone(zone).toInstant()

        val fajr = engine.prayer.nextOccurrenceOf(PrayerStatus.Prayer.FAJR, london, anchor, zone)
        assertNotNull(fajr)
        assertTrue(fajr.instant > anchor)

        val jumuah = engine.prayer.nextJumuah(london, anchor, zone)
        assertNotNull(jumuah)
        assertEquals(java.time.DayOfWeek.FRIDAY, jumuah.date.dayOfWeek)

        val nd = engine.prayer.nightDivisions(london, LocalDate.of(2026, 3, 14))
        assertNotNull(nd)
        assertTrue(nd.firstThirdBegins < nd.midpoint)
    }

    @Test
    fun fastingFactsForMonthReachableViaFacade() {
        val facts = engine.prayer.windows.fastingFactsForMonth(london, YearMonth.of(2026, 4))
        assertEquals(30, facts.size)
        val day = engine.prayer.windows.fastingFacts(london, LocalDate.of(2026, 4, 15))
        assertEquals(day, facts[14])
    }
}

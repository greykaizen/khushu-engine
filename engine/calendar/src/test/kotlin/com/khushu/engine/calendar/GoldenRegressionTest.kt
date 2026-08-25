package com.khushu.engine.calendar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Layer-A regression lock against the donor's exact UmmalquraCalendar call path. */
class GoldenRegressionTest {

    @Serializable private data class Case(
        val date: String, val offset: Int, val hy: Int, val hm: Int, val hd: Int,
    )

    @Serializable private data class Golden(val meta: Map<String, String> = emptyMap(), val cases: List<Case>)

    private val golden: Golden by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(
            Golden.serializer(),
            Golden::class.java.classLoader.getResource("fixtures/hijri_golden.json")!!.readText(),
        )
    }

    @Test
    fun everyHijriGoldenCaseReproducesExactly() {
        for (case in golden.cases) {
            val h = HijriCalendar.hijri(LocalDate.parse(case.date), case.offset)
            assertEquals(case.hy, h.year, "${case.date} offset=${case.offset} year")
            assertEquals(case.hm, h.month, "${case.date} offset=${case.offset} month")
            assertEquals(case.hd, h.day, "${case.date} offset=${case.offset} day")
        }
        assertTrue(golden.cases.size > 3000, "corpus unexpectedly small")
    }
}

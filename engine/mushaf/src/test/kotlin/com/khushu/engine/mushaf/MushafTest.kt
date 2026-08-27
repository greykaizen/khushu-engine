package com.khushu.engine.mushaf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MushafGoldenTest {

    // Synthetic spec mirroring donor bundle metrics (uthmani/6x: units_per_em
    // 2048, ascender 2400fu, descender -1200fu, raster ppem 192).
    private val spec = AtlasSpec(
        font = AtlasFontMetrics(
            unitsPerEm = 2048,
            ascenderFu = 2400,
            descenderFu = -1200,
            heightFu = 3600,
        ),
        ppem = 192,
        glyphs = mapOf(
            1 to GlyphMetrics(GlyphSrcRect(0, 0, 0, 100, 80), bearingX = 0, bearingY = 80, advance = 150.0),
            2 to GlyphMetrics(GlyphSrcRect(0, 100, 0, 60, 40), bearingX = 10, bearingY = 40, advance = 70.0),
        ),
    )

    private val word = listOf(
        GlyphPlacement(glyphId = 1, xAdvanceFu = 1024.0),
        GlyphPlacement(glyphId = 2, xAdvanceFu = 512.0),
    )

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.01f, label: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$label: expected $expected got $actual")

    @Test
    fun wordWidthScalesAdvances() {
        // (1024+512)fu * 32px / 2048upem = 24px
        assertClose(24f, Mushaf.measureWordWidthPx(spec, word, fontSizePx = 32f), label = "word width")
        assertClose(1536f, Mushaf.wordWidthFu(word).toFloat(), label = "word width fu")
    }

    @Test
    fun wordPlacementMatchesDonorFormula() {
        // fontScale = 32/2048 = 0.015625, glyphScale = 32/192, baseline = 2400*fontScale = 37.5
        val layout = Mushaf.layoutWord(spec, word, fontSizePx = 32f)
        assertClose(24f, layout.widthPx, label = "pen width")
        assertEquals(2, layout.glyphs.size)

        val g1 = layout.glyphs[0]
        assertClose(0f, g1.dstX, label = "g1 x")
        assertClose(37.5f - 80f / 6f, g1.dstY, label = "g1 y")
        assertClose(100f / 6f, g1.dstW, label = "g1 w")
        assertClose(80f / 6f, g1.dstH, label = "g1 h")
        assertEquals(0, g1.srcX)
        assertEquals(100, g1.srcW)

        // pen advanced 1024fu*fontScale = 16px before glyph 2
        val g2 = layout.glyphs[1]
        assertClose(16f + 10f / 6f, g2.dstX, label = "g2 x")
        assertClose(37.5f - 40f / 6f, g2.dstY, label = "g2 y")

        // tight box = glyph 1 top (24.17) to glyph 2 bottom (37.5)
        assertClose(37.5f - 80f / 6f, layout.tightMinYPx, label = "tight minY")
        assertClose(80f / 6f, layout.tightHeightPx, label = "tight height")
    }

    @Test
    fun missingGlyphIdsAdvancePenButAreNotDrawn() {
        val placements = listOf(
            GlyphPlacement(glyphId = 999, xAdvanceFu = 1024.0),
            GlyphPlacement(glyphId = 1, xAdvanceFu = 1024.0),
        )
        val layout = Mushaf.layoutWord(spec, placements, fontSizePx = 32f)
        assertEquals(1, layout.glyphs.size)
        assertClose(32f, layout.widthPx, label = "pen still advances")
        assertClose(16f, layout.glyphs[0].dstX, label = "drawn glyph shifted by skipped advance")
    }

    @Test
    fun lineWidthAddsGapsPerDonorRules() {
        val base = 32f
        val widths = listOf(24f, 24f, 24f)
        // justified: min-gap only -> 32*0.1 = 3.2px per pair
        assertClose(24f * 3 + 3.2f * 2, Mushaf.measureLineWidthPx(widths, centered = false, base), label = "justified")
        // centered: max(centered 22%, min 10%) = 7.04px per pair
        assertClose(24f * 3 + 7.04f * 2, Mushaf.measureLineWidthPx(widths, centered = true, base), label = "centered")
        assertEquals(24f, Mushaf.measureLineWidthPx(widths.take(1), centered = true, base))
    }

    @Test
    fun pageScaleUsesMedianOfWideLineRatios() {
        // content width 100px; wide lines fill 100/0.9, 100/0.85 -> ratios 0.9, 0.85
        val lines = listOf(
            LineMeasure(centered = false, wordWidthsPx = listOf(111.111f)),
            LineMeasure(centered = false, wordWidthsPx = listOf(117.647f)),
            LineMeasure(centered = true, wordWidthsPx = listOf(500f)), // centered: ignored
            LineMeasure(centered = false, wordWidthsPx = listOf(10f)), // fill < 0.82: ignored
        )
        val scale = Mushaf.fitPageScale(lines, contentWidthPx = 100f, baseFontSizePx = 32f, fallbackScale = 1.5f)
        assertClose((0.9f + 0.85f) / 2f, scale, tolerance = 0.01f, label = "median scale")
    }

    @Test
    fun pageScaleFallsBackAndClamps() {
        assertEquals(1.3f, Mushaf.fitPageScale(emptyList(), 100f, 32f, fallbackScale = 1.3f))
        val overflowing = List(3) { LineMeasure(centered = false, wordWidthsPx = listOf(400f)) }
        assertClose(Mushaf.FONT_SCALE_AT_MIN_WIDTH, Mushaf.fitPageScale(overflowing, 100f, 32f, 1.5f), label = "floor")
        // fallback below the ceiling caps growth
        val tight = List(3) { LineMeasure(centered = false, wordWidthsPx = listOf(83f)) }
        assertTrue(Mushaf.fitPageScale(tight, 100f, 32f, fallbackScale = 1.1f) <= 1.1f)
    }

    @Test
    fun lineShrinkOnlyNeverEnlarges() {
        assertEquals(1f, Mushaf.fitLineShrink(50f, 100f, bounded = true))
        assertEquals(1f, Mushaf.fitLineShrink(200f, 100f, bounded = false))
        assertClose(0.5f, Mushaf.fitLineShrink(200f, 100f, bounded = true), label = "half")
        // floor at 16%
        assertClose(Mushaf.LINE_SHRINK_MIN, Mushaf.fitLineShrink(10_000f, 100f, bounded = true), label = "floor")
    }

    @Test
    fun screenWidthScaleInterpolatesAndClamps() {
        assertClose(Mushaf.FONT_SCALE_AT_MIN_WIDTH, Mushaf.screenWidthScale(100f), label = "narrow")
        assertClose(Mushaf.FONT_SCALE_AT_MAX_WIDTH, Mushaf.screenWidthScale(1000f), label = "wide")
        val mid = Mushaf.screenWidthScale(430f) // halfway 260..600
        assertClose((Mushaf.FONT_SCALE_AT_MIN_WIDTH + Mushaf.FONT_SCALE_AT_MAX_WIDTH) / 2f, mid, label = "mid")
    }
}

class MushafPropertyTest {

    private val spec = AtlasSpec(
        font = AtlasFontMetrics(unitsPerEm = 2048, ascenderFu = 2400, descenderFu = -1200),
        ppem = 192,
        glyphs = (1..50).associate { id ->
            id to GlyphMetrics(
                GlyphSrcRect(0, (id * 7) % 512, (id * 13) % 512, 20 + id % 40, 30 + id % 25),
                bearingX = id % 9,
                bearingY = 20 + id % 30,
                advance = (40 + id % 60).toDouble(),
            )
        },
    )

    private fun pseudoWord(seed: Int): List<GlyphPlacement> {
        var s = seed.toLong() * 2654435761L
        fun next(): Int { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 33) % 50).toInt() + 1 }
        val n = 2 + (seed % 5)
        return List(n) {
            val g = next()
            GlyphPlacement(
                glyphId = g,
                xAdvanceFu = (spec.glyphs.getValue(g).advance * 14).coerceAtLeast(200.0),
                xOffsetFu = (next() % 200 - 100).toDouble(),
                yOffsetFu = (next() % 400 - 200).toDouble(),
            )
        }
    }

    @Test
    fun ayahGlyphsStayInsideCanvas() {
        // Glyphs may hang slightly beyond the advance box (bearings + x/y
        // offsets) — the donor rasterizer clips them at the canvas edge.
        // Allow exactly the worst-case overhang derivable from the spec.
        val fontSizePx = 28f
        val glyphScale = fontSizePx / spec.ppem
        val fontScale = fontSizePx / spec.font.unitsPerEm
        val allowance = spec.glyphs.values.maxOf { it.rect.w * glyphScale } +
            100f * fontScale + // max |xOffsetFu| in pseudoWord
            spec.glyphs.values.maxOf { it.bearingX } * glyphScale + 0.51f

        for (seed in 1..60) {
            val words = List(3 + seed % 6) { pseudoWord(seed * 31 + it) }
            val layout = Mushaf.layoutAyah(
                spec, words, fontSizePx = fontSizePx,
                lineHeightPx = Mushaf.lineHeightPx(fontSizePx), wordGapPx = 3f,
                maxLineWidthPx = 220f,
            )
            for (g in layout.glyphs) {
                assertTrue(g.dstX >= -allowance && g.dstX + g.dstW <= layout.widthPx + allowance,
                    "x overflow seed=$seed: $g")
                assertTrue(g.dstY >= -0.51f && g.dstY + g.dstH <= layout.heightPx + 0.51f,
                    "y overflow seed=$seed: $g")
            }
        }
    }

    @Test
    fun wrappedLinesObeyMaxWidth() {
        val fontSizePx = 24f
        for (seed in 1..40) {
            val words = List(4 + seed % 8) { pseudoWord(seed * 17 + it) }
            // max width comfortably above any single word so wrapping (not
            // single-word overflow) is what gets exercised
            val maxWord = words.maxOf { Mushaf.measureWordWidthPx(spec, it, fontSizePx) }
            val maxW = maxWord + 60f
            val layout = Mushaf.layoutAyah(
                spec, words, fontSizePx = fontSizePx,
                lineHeightPx = Mushaf.lineHeightPx(fontSizePx), wordGapPx = 2.5f,
                maxLineWidthPx = maxW,
            )
            for (line in layout.lines) {
                assertTrue(
                    line.widthPx <= maxW + 0.51f,
                    "line wider than max seed=$seed: ${line.widthPx} > $maxW",
                )
            }
        }
    }

    @Test
    fun wordsArePlacedRightToLeftWithoutOverlap() {
        val words = List(5) { pseudoWord(7 * (it + 1)) }
        val line = Mushaf.layoutLine(
            spec, words, fontSizePx = 30f,
            lineHeightPx = Mushaf.lineHeightPx(30f), wordGapPx = 4f,
        )
        assertEquals(5, line.words.size)
        for (i in 0 until line.words.lastIndex) {
            val rightWord = line.words[i]
            val leftWord = line.words[i + 1]
            assertTrue(
                leftWord.leftPx + leftWord.widthPx <= rightWord.leftPx + 0.51f,
                "RTL order broken at $i",
            )
        }
        // rightmost word ends exactly at the line's right edge
        assert(line.words.first().let { kotlin.math.abs(it.leftPx + it.widthPx - line.widthPx) < 0.51f })
    }

    @Test
    fun pageScaleAlwaysInsideBounds() {
        for (seed in 1..50) {
            val lines = List(3 + seed % 10) {
                LineMeasure(
                    centered = (seed + it) % 3 == 0,
                    wordWidthsPx = List(2 + (seed + it) % 6) { 20f + ((seed * 13 + it * 7) % 120).toFloat() },
                )
            }
            val fallback = 1f + (seed % 10) / 10f
            val scale = Mushaf.fitPageScale(lines, contentWidthPx = 300f, baseFontSizePx = 26f, fallback)
            assertTrue(scale >= Mushaf.FONT_SCALE_AT_MIN_WIDTH)
            assertTrue(scale <= minOf(fallback, Mushaf.FONT_SCALE_AT_MAX_WIDTH) + 1e-6f)
        }
    }
}
